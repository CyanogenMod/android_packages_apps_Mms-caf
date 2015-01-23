/*
 * Copyright (c) 2014 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.android.mms.rcs;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.suntek.mway.rcs.client.aidl.plugin.entity.emoticon.EmoticonConstant;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Message;
import android.widget.ImageView;

public class RcsEmojiStoreUtil {

    public static final int EMO_STATIC_FILE = EmoticonConstant.EMO_STATIC_FILE;

    public static final int EMO_DYNAMIC_FILE = EmoticonConstant.EMO_DYNAMIC_FILE;

    public static final int EMO_PACKAGE_FILE = EmoticonConstant.EMO_PACKAGE_FILE;

    private Map<String, SoftReference<Bitmap>> mCaches;

    private List<LoaderImageTask> mTaskQueue;

    private boolean mIsRuning = false;

    private static RcsEmojiStoreUtil mInstance;

    public static RcsEmojiStoreUtil getInstance() {
        if (mInstance == null) {
            mInstance = new RcsEmojiStoreUtil();
        }
        return mInstance;
    }

    private RcsEmojiStoreUtil() {
        mCaches = new HashMap<String, SoftReference<Bitmap>>();
        mTaskQueue = new ArrayList<RcsEmojiStoreUtil.LoaderImageTask>();
        mIsRuning = true;
        new Thread(runnable).start();
    }

    public void loadImageAsynById(ImageView imageView, String imageId, int loaderType) {
        if (mCaches.containsKey(imageId)) {
            SoftReference<Bitmap> rf = mCaches.get(imageId);
            Bitmap bitmap = rf.get();
            if (bitmap == null) {
                mCaches.remove(imageId);
            } else {
                imageView.setImageBitmap(bitmap);
                return;
            }
        }
        LoaderImageTask loaderImageTask = new LoaderImageTask(imageId, imageView, loaderType);
        if (!mTaskQueue.contains(loaderImageTask)) {
            mTaskQueue.add(loaderImageTask);
            synchronized (runnable) {
                runnable.notify();
            }
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            LoaderImageTask task = (LoaderImageTask)msg.obj;
            task.imageView.setImageBitmap(task.bitmap);
        }
    };

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            while (mIsRuning) {
                while (mTaskQueue.size() > 0) {
                    LoaderImageTask task = mTaskQueue.remove(0);
                    task.bitmap = getbitmap(task.loaderType, task.imageId);
                    if (mHandler != null) {
                        Message msg = mHandler.obtainMessage();
                        msg.obj = task;
                        mHandler.sendMessage(msg);
                    }
                }
                synchronized (this) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    private Bitmap getbitmap(int loaderType, String emoticonId) {
        byte[] imageByte = null;
        Bitmap bitmap = null;
        try {
            if (loaderType == EMO_STATIC_FILE) {
                imageByte = RcsApiManager.getEmoticonApi().decrypt2Bytes(emoticonId,
                        EMO_STATIC_FILE);
            } else if (loaderType == EMO_PACKAGE_FILE) {
                imageByte = RcsApiManager.getEmoticonApi().decrypt2Bytes(emoticonId,
                        EMO_PACKAGE_FILE);
            }
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        }
        if (imageByte != null) {
            bitmap = BitmapFactory.decodeByteArray(imageByte, 0, imageByte.length);
        }
        if (bitmap != null) {
            mCaches.put(emoticonId, new SoftReference<Bitmap>(bitmap));
        }
        return bitmap;
    }

    public class LoaderImageTask {
        String imageId;

        ImageView imageView;

        Bitmap bitmap;

        int loaderType;

        public LoaderImageTask(String imageId, ImageView imageView, int loaderType) {
            this.imageId = imageId;
            this.imageView = imageView;
            this.loaderType = loaderType;
        }

        @Override
        public boolean equals(Object o) {
            LoaderImageTask task = (LoaderImageTask)o;
            return task.imageId.equals(imageId);
        }
    }

}
