package com.android.mms.ui;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.text.TextPaint;
import android.util.LruCache;
import com.android.mms.MmsApp;
import com.android.mms.R;
import com.android.mms.okhttp.DiskLruCache;
import com.android.mms.presenters.ImagePresenter;
import com.android.mms.presenters.SimpleAttachmentPresenter;
import com.android.mms.presenters.VideoPresenter;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

public class MessageInfoCache {

    private static final String TAG = MessageInfoCache.class.getSimpleName();
    private static final boolean DEBUG = false;
    private int mMaxWidth;

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

    private final Context mContext;
    private final long mThreadId;
    private final Paint mPaint;
    public HashMap<Long, Integer> mMessageIdPositionMap =
            new HashMap<Long, Integer>();
    private LruCache<Long, String[]> mMimeTypeCache =
            new LruCache<Long, String[]>(100);
    public Cursor mMessagePartsCursor;
    private PopulateCacheTask mPopulateTask;

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

    public void populateCache(Cursor cursor) {
        mMessageIdPositionMap.clear();
        mPopulateTask = new PopulateCacheTask();
        mPopulateTask.execute(cursor);
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
        }
    }

    private class PopulateCacheTask extends AsyncTask<Cursor, Void, Void> {
        @Override
        protected Void doInBackground(Cursor... params) {
            if (mMessagePartsCursor != null) {
                mMessagePartsCursor.close();
            }
            mMessagePartsCursor = params[0];
            if (isCancelled()) {
                return null;
            }
            if (mMessagePartsCursor != null && mMessagePartsCursor.moveToLast()) {
                StringBuilder stringBuilder = new StringBuilder();
                long lastId = -1;
                // TODO optimize to start cache from where the user is
                do {
                    long id = mMessagePartsCursor.getLong(0);
                    stringBuilder.append(mMessagePartsCursor.getString(1));
                    if (lastId != -1 && lastId == id) {
                        continue;
                    }
                    mMessageIdPositionMap.put(id, mMessagePartsCursor.getPosition());
                    // Only care about the pre-caching the last X entry mimetypes
                    if (lastId != -1 && mMimeTypeCache.size() != mMimeTypeCache.maxSize()) {
                        mMimeTypeCache.put(id, stringBuilder.toString().split(","));
                    }
                    stringBuilder.setLength(0);
                    lastId = id;
                    if (isCancelled()) {
                        return null;
                    }
                } while (mMessagePartsCursor.moveToPrevious());
            }
            return null;
        }
    }

    public int getCachedTotalHeight(Context context, long messageId) {
        DiskCache.CacheInfo cached = DiskCache.getCacheInfo(messageId);
        if (cached != null) {
            return cached.totalHeight;
        }

        String[] mimeTypes = getCachedMimeTypes(messageId);
        int totalHeight = -1;
        if (mimeTypes != null) {
            int padding = context.getResources().getDimensionPixelSize(
                    R.dimen.message_attachment_padding);
            for (int i = 0; i < mimeTypes.length; i++) {
                String mimeType = mimeTypes[i];
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
                if (i != mimeType.length() - 1) {
                    totalHeight += padding;
                }
            }
        }
        return totalHeight;
    }

    public String[] getCachedMimeTypes(long msgId) {
        String[] mimeTypes = mMimeTypeCache.get(msgId);
        if (mMessagePartsCursor != null && mMessageIdPositionMap.containsKey(msgId)) {
            int cursorPosition = mMessageIdPositionMap.get(msgId);
            if (!mMessagePartsCursor.moveToPosition(cursorPosition)) {
                return null;
            }
            StringBuilder stringBuilder = new StringBuilder();
            do {
                long iterMsgId = mMessagePartsCursor.getLong(0);
                if (msgId != iterMsgId) {
                    break;
                }

                String mimeType = mMessagePartsCursor.getString(1);
                stringBuilder.append(mimeType);
                if (!mMessagePartsCursor.isLast()) {
                    stringBuilder.append(",");
                }
            } while (mMessagePartsCursor.moveToNext());
            mimeTypes = stringBuilder.toString().split(",");
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
