package com.android.mms.data;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import com.android.mms.MmsApp;
import com.wuman.twolevellrucache.TwoLevelLruCache;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.List;

/**
 * Don't interact with directly, only to be used by
 * MessageInfoCache
 */
class MessageInfoDiskCache {

    private static final int MEM_CACHE_SIZE = 30;
    private static final int DISK_CACHE_SIZE = 5 * 1000 * 1000;

    public TwoLevelLruCache<CacheInfo> mLruCache;
    private HandlerThread mHandlerThread;
    private Handler mHandler;

    MessageInfoDiskCache(Context context) {
        if (mLruCache == null) {
            mHandlerThread = new HandlerThread("MessageInfoDiskCache");
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper(), new Handler.Callback() {
                @Override
                public boolean handleMessage(Message msg) {
                    if (msg.obj != null) {
                        //noinspection unchecked
                        for (long key : (long[]) msg.obj) {
                            getCacheInfo(String.valueOf(key));
                        }
                    }
                    return true;
                }
            });
            File path = new File(context.getCacheDir(), "metadata-cache");
            try {
                // Warning: If # of args is changed, existing cache will be dropped
                mLruCache = new TwoLevelLruCache<CacheInfo>(path, 0, MEM_CACHE_SIZE, DISK_CACHE_SIZE,
                        mLruCacheConverter);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    CacheInfo getCacheInfo(String msgId) {
        return mLruCache.get(msgId);
    }

    void persistCacheInfo(String messageId, CacheInfo cacheInfo) {
        CacheInfo existingInfo = getCacheInfo(messageId);
        // Allow overriding of cache if mimeTypes are different
        if (existingInfo != null && existingInfo.mimeTypes.equals(cacheInfo.mimeTypes)) {
            return;
        }
        mLruCache.put(messageId, cacheInfo);
    }

    void onLowMemory() {
        mLruCache.evictAllMem();
    }

    static final class CacheInfo implements Serializable {
        public final int totalHeight;
        public final String mimeTypes;

        public CacheInfo(int totalHeight, List<String> mimeTypes) {
            this(totalHeight, TextUtils.join(",", mimeTypes));
        }

        public CacheInfo(int totalHeight, String mimeTypes) {
            this.totalHeight = totalHeight;
            this.mimeTypes = mimeTypes;
        }
    }

    void closeCache() {
        if (mLruCache != null) {
            try {
                mLruCache.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (mHandlerThread != null) {
            mHandlerThread.quit();
        }
    }

    void clearQueue() {
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    void primeCache(long[] msgIds) {
        Message msg = mHandler.obtainMessage();
        msg.obj = msgIds;
        mHandler.sendMessage(msg);
    }

    private static TwoLevelLruCache.Converter<CacheInfo> mLruCacheConverter =
            new TwoLevelLruCache.Converter<CacheInfo>() {
        @Override
        public CacheInfo from(byte[] bytes) throws IOException {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInput in = null;
            try {
                in = new ObjectInputStream(bis);
                return (CacheInfo) in.readObject();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } finally {
                try {
                    bis.close();
                } catch (IOException ex) {
                }
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException ex) {
                }
            }
            return null;
        }

        @Override
        public void toStream(CacheInfo cacheInfo, OutputStream outputStream) throws IOException {
            ObjectOutput out = null;
            try {
                out = new ObjectOutputStream(outputStream);
                out.writeObject(cacheInfo);
            } finally {
                if (out != null) {
                    out.close();
                }
            }
        }
    };
}