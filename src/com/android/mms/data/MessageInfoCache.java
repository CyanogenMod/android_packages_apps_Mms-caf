package com.android.mms.data;

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
import com.android.mms.presenters.ImagePresenter;
import com.android.mms.presenters.SimpleAttachmentPresenter;
import com.android.mms.presenters.VideoPresenter;
import com.android.mms.util.ThumbnailManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class MessageInfoCache {

    private static final String TAG = MessageInfoCache.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static MessageInfoCache sInstance;

    private int mMaxWidth = ThumbnailManager.THUMBNAIL_SIZE;
    public static final int HEIGHT_UNDETERMINED = -1;
    private static final int PRIME_CACHE_WINDOW = 4;

    /**
     * Stores msgIds -> cursorPosition (in parts cursor)
     */
    private SortedMap<Long, Integer> mMessageIdPositionMap;

    /**
     *
     */
    private ArrayList<Long> mMessageIds;

    /**
     * Stores msgIds -> mimeType
     */
    private LruCache<Long, List<String>> mMimeTypeCache;

    private Paint mPaint;
    public Cursor mMessagePartsCursor;
    private PopulateCacheTask mPopulateTask;
    private final MessageInfoDiskCache mDiskCache;

    /**
     * Called from Application context to initialize
     * cache
     */
    public static void init(MmsApp app) {
        if (sInstance == null) {
            sInstance = new MessageInfoCache(app);
        }
    }

    public static MessageInfoCache getInstance() {
        return sInstance;
    }

    private MessageInfoCache(Context context) {
        initTextPaint(context);
        mMessageIdPositionMap = Collections.synchronizedSortedMap(
                new TreeMap<Long, Integer>());
        mMimeTypeCache = new LruCache<Long, List<String>>(30);
        mMessageIds = new ArrayList<Long>();
        mDiskCache = new MessageInfoDiskCache(context);
    }

    private void initTextPaint(Context context) {
        // The attributes you want retrieved
        int[] attrs = {android.R.attr.textSize, android.R.attr.fontFamily,
                android.R.attr.typeface, android.R.attr.textStyle};

        Resources.Theme theme = context.getResources().newTheme();
        theme.applyStyle(android.R.style.TextAppearance_Material_Body1, true);
        TypedArray ta = theme.obtainStyledAttributes(android.R.style.TextAppearance, attrs);
        int textSize = ta.getDimensionPixelSize(
                com.android.internal.R.styleable.TextAppearance_textSize, 0);
        ta.recycle();

        mPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        mPaint.setTextSize(textSize);
        mPaint.setTypeface(Typeface.SANS_SERIF);
    }


    /**
     * Called to populate cache from cursor
     */
    public void populateCache(Cursor cursor) {
        cleanup();
        mMessageIds.clear();
        mMessageIdPositionMap.clear();
        mPopulateTask = new PopulateCacheTask();
        mPopulateTask.execute(cursor);
    }

    public void cleanup() {
        if (mPopulateTask != null) {
            mPopulateTask.cancel(true);
            mPopulateTask = null;
        }
        if (mMessagePartsCursor != null) {
            mMessagePartsCursor.close();
            mMessagePartsCursor = null;
        }
    }

    /**
     * Caches a window around the msgId
     */
    public void primeCache(long msgId) {
        if (mMessageIds.contains(msgId)) {
            int index = mMessageIds.indexOf(msgId);
            List<Long> subList = mMessageIds.subList(Math.max(index - PRIME_CACHE_WINDOW, 0),
                    Math.min(index + PRIME_CACHE_WINDOW + 1, mMessageIds.size()));
            mDiskCache.primeCache(subList);
        }
    }

    public void onLowMemory() {
        mMimeTypeCache.evictAll();
        mDiskCache.onLowMemory();
    }

    public void closeCache(MmsApp app) {
        mDiskCache.closeCache();
    }


    private class PopulateCacheTask extends AsyncTask<Cursor, Void, Void> {
        @Override
        protected void onPreExecute() {
            if (mMessagePartsCursor != null) {
                mMessagePartsCursor.close();
                mMessagePartsCursor = null;
            }
        }

        @Override
        protected Void doInBackground(Cursor... params) {
            mMessagePartsCursor = params[0];
            if (isCancelled()) {
                return null;
            }
            if (mMessagePartsCursor != null && mMessagePartsCursor.moveToFirst()) {
                long prevId = -1;
                int mimeTypeCacheIter = 0;
                do {
                    long id = mMessagePartsCursor.getLong(0);
                    if (prevId == id) {
                        continue;
                    }
                    prevId = id;
                    mMessageIdPositionMap.put(id, mMessagePartsCursor.getPosition());
                    mMessageIds.add(id);

                    // We don't clear cache when exiting conversations.
                    // Make sure to only add an entry into the lru if
                    // 1) Its the nth item in the cursor where N < cacheSize. This
                    // is so that we don't trash the cache
                    // 2) Not already in the cache. Avoids additional cursor
                    // iterations.
                    if (mimeTypeCacheIter < mMimeTypeCache.maxSize() &&
                            mMimeTypeCache.get(id) == null) {
                        int cursorPosition = mMessagePartsCursor.getPosition();
                        List<String> mimeTypes = getMimeTypesForCursorAtPosition(
                                mMessagePartsCursor, cursorPosition);
                        mMimeTypeCache.put(id, mimeTypes);

                        // Single getMimeTypesForCursorAtPosition moves the cursor
                        // one past the old msgId, we need to move it one back for
                        // next iteration
                        mMessagePartsCursor.moveToPrevious();
                    }
                    mimeTypeCacheIter++;

                    if (isCancelled()) {
                        return null;
                    }
                } while (mMessagePartsCursor.moveToNext());
            }
            return null;
        }
    }

    public int getCachedTotalHeight(Context context, long msgId) {
        String msgIdString = String.valueOf(msgId);
        MessageInfoDiskCache.CacheInfo cached = mDiskCache.getCacheInfo(msgIdString);
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
                totalHeight += ImagePresenter.getStaticHeight(context);
            } else if (mimeType.startsWith("video")) {
                totalHeight += VideoPresenter.getStaticHeight(context);
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
                // Skip smil mimeType since messages with only a
                // smil part are treated as sms
                if (mimeType.equalsIgnoreCase("application/smil")) {
                    continue;
                }
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
        // Check two level cache first
        List<String> mimeTypes = mMimeTypeCache.get(msgId);
        if (mimeTypes != null) {
            return mimeTypes;
        }
        // Fall back to getting mimetypes from cursor
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
        List<String> mimeTypes = getCachedMimeTypes(messageId);
        if (mimeTypes != null) {
            MessageInfoDiskCache.CacheInfo cacheInfo =
                    new MessageInfoDiskCache.CacheInfo(height, mimeTypes);
            mDiskCache.persistCacheInfo(mid, cacheInfo);
        }
    }

    public void setMaxItemWidth(int width) {
        mMaxWidth = width;
    }

    public int getMaxItemWidth() {
        return mMaxWidth;
    }

}
