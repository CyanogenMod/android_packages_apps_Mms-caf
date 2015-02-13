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

import com.android.mms.rcs.RcsApiManager;
//import com.android.mms.ui.QuickContactDivot;
import com.android.contacts.common.widget.CheckableQuickContactBadge;
import com.suntek.mway.rcs.client.api.impl.callback.ConferenceCallback;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.Avatar;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Base64;

import java.lang.ref.SoftReference;
import java.util.HashMap;

public class GroupMemberPhotoCache {
    private final static int IMAGE_PIXEL = 120;
    private HashMap<String, SoftReference<Bitmap>> mImageCache;

    private static GroupMemberPhotoCache sInstance;

    private GroupMemberPhotoCache() {
        mImageCache = new HashMap<String, SoftReference<Bitmap>>();
    }
    
    public static void loadGroupMemberPhoto(String rcsGroupId,String addr,final CheckableQuickContactBadge mAvatar,final Drawable sDefaultContactImage){
        getInstance().loadGroupMemberPhoto(rcsGroupId, addr, new GroupMemberPhotoCache.ImageCallback() {
            
            @Override
            public void loadImageCallback(Bitmap bitmap) {
                if( bitmap != null){
                    mAvatar.setImageBitmap(bitmap);
                }else{
                    mAvatar.setImageDrawable(sDefaultContactImage);
                }
            }
        });
    }

    public static GroupMemberPhotoCache getInstance() {
        if (sInstance == null) {
            sInstance = new GroupMemberPhotoCache();
        }
        return sInstance;
    }

    public synchronized void loadGroupMemberPhoto(String groupId, String number,
            final ImageCallback callback) {
        if (TextUtils.isEmpty(number)) {
            callback.loadImageCallback(null);
            return;
        }

        if (mImageCache.containsKey(number)) {
            SoftReference<Bitmap> softReference = mImageCache.get(number);
            Bitmap bitmap = softReference.get();
            if (bitmap != null) {
                callback.loadImageCallback(bitmap);
                return;
            } else {
                mImageCache.remove(number);
            }
        }
        try {
            RcsApiManager.getConfApi().queryMemberHeadPic(groupId, number, IMAGE_PIXEL,
                    new ConferenceCallback() {

                        @Override
                        public void onRefreshAvatar(Avatar avatar, int resultCode, String resultDesc)
                                throws RemoteException {
                            super.onRefreshAvatar(avatar, resultCode, resultDesc);
                            if (avatar != null) {
                                String str = avatar.getImgBase64Str();
                                byte[] imageByte = Base64.decode(str,
                                        Base64.DEFAULT);
                                Bitmap bitmap = BitmapFactory.decodeByteArray(imageByte, 0,
                                        imageByte.length);
                                callback.loadImageCallback(bitmap);
                                return;
                            }
                        }
                    });
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        }
    }

    public interface ImageCallback {
        public void loadImageCallback(Bitmap bitmap);
    }

}
