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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import com.android.mms.rcs.RcsEmojiPackageObject.EmojiObject;
import com.suntek.mway.rcs.client.api.plugin.entity.emoticon.EmojiPackageBO;
import com.suntek.mway.rcs.client.api.plugin.entity.emoticon.EmoticonBO;
import com.suntek.mway.rcs.client.api.plugin.entity.emoticon.EmoticonConstant;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.inputmethod.InputMethodManager;

public class RcsEmojiStoreUtil {

    public static ArrayList<RcsEmojiPackageObject> getStorePackageList() {
        List<EmojiPackageBO> storelist = null;
        ArrayList<RcsEmojiPackageObject> packageList = new ArrayList<RcsEmojiPackageObject>();
        try {
            storelist = RcsApiManager.getEmoticonApi().queryEmojiPackages();
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        }
        if (storelist != null)
            for (EmojiPackageBO emojiPackageBO : storelist) {
                packageList.add(initEmojiPackageBean(emojiPackageBO));
            }
        return packageList;
    }

    private static RcsEmojiPackageObject initEmojiPackageBean(EmojiPackageBO emojiPackageBO) {
        RcsEmojiPackageObject emojiPackageObject = new RcsEmojiPackageObject();
        emojiPackageObject.setPackageId(emojiPackageBO.getPackageId());

        emojiPackageObject.setPackageBitmap(getEmojiStorePackageBitmap(emojiPackageBO
                .getPackageId()));
        emojiPackageObject.setHorizontalLineSize(RcsEmojiPackageObject.BIG_HORIZONTAL_LINE_SIZE);
        emojiPackageObject.setCarryDeleteSign(false);
        emojiPackageObject.setEmojiList(getStoreEmojiListByPackageId(emojiPackageBO
                .getPackageId()));
        emojiPackageObject.setEmojiType(RcsEmojiPackageObject.BIG_EMOJI_TYPE);
        emojiPackageObject.setEmojiCount(emojiPackageObject.getEmojiList().size());
        return emojiPackageObject;
    }

    private static ArrayList<EmojiObject> getStoreEmojiListByPackageId(String packageId) {
        List<EmoticonBO> list = null;
        ArrayList<EmojiObject> emojiList = new ArrayList<EmojiObject>();
        try {
            list = RcsApiManager.getEmoticonApi()
                    .queryEmoticons(packageId);
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        }
        if (list != null)
            for (EmoticonBO emoticonBO : list) {
                emojiList.add(initEmojiBean(packageId, emoticonBO));
            }
        return emojiList;
    }

    private static EmojiObject initEmojiBean(String packageId, EmoticonBO emoticonBO) {
        EmojiObject emojiObject = new EmojiObject();
        emojiObject.setEmojiId(emoticonBO.getEmoticonId());
        emojiObject.setPackageId(packageId);
        emojiObject.setEmojiName(emoticonBO.getEmoticonName());
        emojiObject.setEmojiType(RcsEmojiPackageObject.BIG_EMOJI_TYPE);
        return emojiObject;
    }

    private static Bitmap getEmojiStorePackageBitmap(String packageId) {
        Bitmap bitmap = null;
        byte[] imageByte = null;
        try {
            imageByte = RcsApiManager
                    .getEmoticonApi()
                    .decrypt2Bytes(packageId,
                            EmoticonConstant.EMO_PACKAGE_FILE);
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        }
        if (imageByte != null)
            bitmap = BitmapFactory.decodeByteArray(imageByte, 0,
                    imageByte.length);
        return bitmap;
    }

    @SuppressWarnings("static-access")
    public static void closeKB(Activity activity) {
        if (activity.getCurrentFocus() != null) {
            ((InputMethodManager)activity.getSystemService(activity.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(),
                            InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    public static void openKB(Context context) {
        InputMethodManager inputMethodManager = (InputMethodManager)context
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS);
    }

    public static int dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int)(dipValue * scale + 0.5f);
    }

    public static int px2dip(Context context, float pxValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int)(pxValue / scale + 0.5f);
    }

    public static final byte[] inputToByte(InputStream inStream) {
        try {
            ByteArrayOutputStream swapStream = new ByteArrayOutputStream();
            byte[] buff = new byte[100];
            int rc = 0;
            while ((rc = inStream.read(buff, 0, 100)) > 0) {
                swapStream.write(buff, 0, rc);
            }
            byte[] in2b = swapStream.toByteArray();
            return in2b;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
