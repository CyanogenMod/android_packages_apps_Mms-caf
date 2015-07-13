package com.android.mms.ui;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.LruCache;
import com.android.mms.MmsApp;
import com.android.mms.R;
import com.android.mms.okhttp.DiskLruCache;
import com.android.mms.presenters.ImagePresenter;
import com.android.mms.presenters.SimpleAttachmentPresenter;
import com.android.mms.presenters.VideoPresenter;
import com.android.mms.util.ThumbnailManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class MessageInfoCache {

    private static final String TAG = MessageInfoCache.class.getSimpleName();
    private static final boolean DEBUG = false;
    private int mMaxWidth = ThumbnailManager.THUMBNAIL_SIZE;
    public static final int HEIGHT_UNDETERMINED = -1;

    private final Context mContext;
    private final long mThreadId;
    private final Paint mPaint;
    public ConcurrentHashMap<Long, Integer> mMessageIdPositionMap =
            new ConcurrentHashMap<Long, Integer>();
    private LruCache<Long, List<String>> mMimeTypeCache =
            new LruCache<Long, List<String>>(100);
    public Cursor mMessagePartsCursor;
    private PopulateCacheTask mPopulateTask;

    public static class DiskCache {
        private static DiskLruCache sLruCache;
        private static final int TOTAL_HEIGHT_INDEX = 0;

        private static CacheInfo getCacheInfo(long msgId) {
            try {
                String mid = String.valueOf(msgId);
                DiskLruCache.Snapshot item = sLruCache.get(mid);
                if (item != null) {
                    int totalHeight = Integer.parseInt(item.getString(TOTAL_HEIGHT_INDEX));
                    return new CacheInfo(totalHeight);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        public static void persistCacheInfo(String messageId, CacheInfo cacheInfo) {
            try {
                if (sLruCache.contains(messageId)) {
                    return;
                }
                DiskLruCache.Editor edit = sLruCache.edit(messageId);
                edit.set(TOTAL_HEIGHT_INDEX, String.valueOf(cacheInfo.totalHeight));
                edit.commit();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }

        private static final class CacheInfo {
            public final int totalHeight;
            private CacheInfo(int totalHeight) {
                this.totalHeight = totalHeight;
            }
        }

        public static void closeCache() {
            if (sLruCache != null) {
                try {
                    sLruCache.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public static void initCache(MmsApp mmsApp) {
            if (sLruCache == null) {
                File path = new File(mmsApp.getCacheDir(), "metadata-cache");
                try {
                    // Warning: If # of args is changed, existing cache will be dropped
                    sLruCache = DiskLruCache.open(path, 0, 1, 5 * 1000 * 1000);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public MessageInfoCache(Context context, long threadId) {
        mContext = context;
        mThreadId = threadId;

        // The attributes you want retrieved
        int[] attrs = {android.R.attr.textSize, android.R.attr.fontFamily,
                android.R.attr.typeface, android.R.attr.textStyle};

        Resources.Theme theme = mContext.getResources().newTheme();
        theme.applyStyle(android.R.style.TextAppearance_Material_Body1, true);
        TypedArray ta = theme.obtainStyledAttributes(android.R.style.TextAppearance, attrs);
        int textSize = ta.getDimensionPixelSize(com.android.internal.R.styleable.TextAppearance_textSize, 0);
        ta.recycle();

        mPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        mPaint.setTextSize(textSize);
        mPaint.setTypeface(Typeface.SANS_SERIF);
    }

    public AsyncTask populateCache(Cursor cursor) {
        cleanup();
        mPopulateTask = new PopulateCacheTask();
        mPopulateTask.execute(cursor);
        return mPopulateTask;
    }

    public void cleanup() {
        if (mPopulateTask != null) {
            mPopulateTask.cancel(true);
            mPopulateTask = null;
        }
        mMimeTypeCache.evictAll();
        mMessageIdPositionMap.clear();
        if (mMessagePartsCursor != null) {
            mMessagePartsCursor.close();
            mMessagePartsCursor = null;
        }
    }

    private class PopulateCacheTask extends AsyncTask<Cursor, Void, Cursor> {
        @Override
        protected void onPreExecute() {
            if (mMessagePartsCursor != null) {
                mMessagePartsCursor.close();
                mMessagePartsCursor = null;
            }
        }

        @Override
        protected Cursor doInBackground(Cursor... params) {
            mMessagePartsCursor = params[0];
            if (isCancelled()) {
                return null;
            }
            if (mMessagePartsCursor != null && mMessagePartsCursor.moveToFirst()) {
                long prevId = -1;
                do {
                    long id = mMessagePartsCursor.getLong(0);
                    if (prevId == id) {
                        continue;
                    }
                    prevId = id;
                    mMessageIdPositionMap.put(id, mMessagePartsCursor.getPosition());

                    if (mMimeTypeCache.size() != mMimeTypeCache.maxSize()) {
                        int cursorPosition = mMessagePartsCursor.getPosition();
                        List<String> mimeTypes = getMimeTypesForCursorAtPosition(
                                mMessagePartsCursor, cursorPosition);
                        mMimeTypeCache.put(id, mimeTypes);
                        mMessagePartsCursor.moveToPrevious();
                    }

                    if (isCancelled()) {
                        return null;
                    }
                } while (mMessagePartsCursor.moveToNext());
            }
            return mMessagePartsCursor;
        }

        @Override
        protected void onPostExecute(Cursor cursor) {
            mMessagePartsCursor = cursor;
        }
    }

    public int getCachedTotalHeight(Context context, long msgId) {
        DiskCache.CacheInfo cached = DiskCache.getCacheInfo(msgId);
        if (cached != null) {
            return cached.totalHeight;
        }

        if (mMessagePartsCursor == null || !mMessageIdPositionMap.containsKey(msgId)) {
            return HEIGHT_UNDETERMINED;
        }

        int totalHeight = HEIGHT_UNDETERMINED;

        int cursorPosition = mMessageIdPositionMap.get(msgId);
        if (!mMessagePartsCursor.moveToPosition(cursorPosition)) {
            return HEIGHT_UNDETERMINED;
        }

        int padding = context.getResources().getDimensionPixelSize(
                R.dimen.message_attachment_padding);
        do {
            long iterMsgId = mMessagePartsCursor.getLong(0);
            if (msgId != iterMsgId) {
                break;
            }

            if (totalHeight != HEIGHT_UNDETERMINED) {
                totalHeight += padding;
            }

            String mimeType = mMessagePartsCursor.getString(1);
            if (mimeType.startsWith("image")) {
                totalHeight += ImagePresenter.getStaticHeight();
            } else if (mimeType.startsWith("video")) {
                totalHeight += VideoPresenter.getStaticHeight();
            } else if (mimeType.startsWith("text/plain")) {
                String text = mMessagePartsCursor.getString(2);
                totalHeight += getTextHeight(text);
            } else {
                totalHeight += SimpleAttachmentPresenter.getStaticHeight(context);
            }
        } while (mMessagePartsCursor.moveToNext());
        return totalHeight;
    }

    /**
     * Iterates through cursor and gathers mimetypes for messageId at given
     * position. Returns cursor at next msgId
     * @param cursor expected to be in correct position
     * @return
     */
    private static List<String> getMimeTypesForCursorAtPosition(Cursor cursor, int position) {
        if (cursor != null && cursor.moveToPosition(position)) {
            long targetId = cursor.getLong(0);
            ArrayList<String> mimeTypes = new ArrayList<String>();
            do {
                long iterMsgId = cursor.getLong(0);
                if (targetId != iterMsgId) {
                    break;
                }

                String mimeType = cursor.getString(1);
                mimeTypes.add(mimeType);
            } while (cursor.moveToNext());
            mimeTypes.trimToSize();
            return mimeTypes;
        }
        return null;
    }

    private int getCursorRowIdForMsgId(long msgId) {
        if (mMessageIdPositionMap.containsKey(msgId)) {
            return mMessageIdPositionMap.get(msgId);
        } else {
            return -1;
        }
    }

    public List<String> getCachedMimeTypes(long msgId) {
        List<String> mimeTypes = mMimeTypeCache.get(msgId);
        if (mimeTypes != null) {
            return mimeTypes;
        }
        int position = getCursorRowIdForMsgId(msgId);
        if (position != -1) {
            mimeTypes = getMimeTypesForCursorAtPosition(
                    mMessagePartsCursor, position);
            mMimeTypeCache.put(msgId, mimeTypes);
        }
        return mimeTypes;
    }

    private int getTextHeight(String text) {
        int lineCount = 0;

        int index = 0;
        int length = text.length();

        while(index < length - 1) {
            index += mPaint.breakText(text, index, length, true, mMaxWidth, null);
            lineCount++;
        }

        Rect bounds = new Rect();
        mPaint.getTextBounds("Py", 0, 2, bounds);
        return (int)Math.floor(lineCount * bounds.height());
    }

    public void addMessageItemInfo(long messageId, int height) {
        String mid = String.valueOf(messageId);
        DiskCache.CacheInfo cacheInfo = new DiskCache.CacheInfo(height);
        DiskCache.persistCacheInfo(mid, cacheInfo);
    }

    public void setMaxItemWidth(int width) {
        mMaxWidth = width;
    }

    public int getMaxItemWidth() {
        return mMaxWidth;
    }

}
