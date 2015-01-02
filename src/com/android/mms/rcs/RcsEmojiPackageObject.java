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


import android.graphics.Bitmap;

import java.util.ArrayList;

public class RcsEmojiPackageObject {

    public static final int BIG_HORIZONTAL_LINE_SIZE = 4;

    public static final int SMALL_HORIZONTAL_LINE_SIZE = 7;

    public static final int SMALL_EMOJI_TYPE = 0;

    public static final int BIG_EMOJI_TYPE = 1;

    private String packageId;

    private int emojiType;

    private int packageResId;

    private Bitmap packageBitmap;

    private int emojiCount;

    private int horizontalLineSize;

    private ArrayList<EmojiObject> emojiObjectList;

    private boolean carryDeleteSign;

    public Bitmap getPackageBitmap() {
        return packageBitmap;
    }

    public void setPackageBitmap(Bitmap packageBitmap) {
        this.packageBitmap = packageBitmap;
    }

    public String getPackageId() {
        return packageId;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
    }

    public int getPackageResId() {
        return packageResId;
    }

    public int getEmojiType() {
        return emojiType;
    }

    public void setEmojiType(int emojiType) {
        this.emojiType = emojiType;
    }

    public void setPackageResId(int resId) {
        this.packageResId = resId;
    }

    public int getEmojiCount() {
        return emojiCount;
    }

    public void setEmojiCount(int emojiCount) {
        this.emojiCount = emojiCount;
    }

    public int getHorizontalLineSize() {
        return horizontalLineSize;
    }

    public void setHorizontalLineSize(int horizontalLineSize) {
        this.horizontalLineSize = horizontalLineSize;
    }

    public ArrayList<EmojiObject> getEmojiList() {
        if (emojiObjectList == null)
            emojiObjectList = new ArrayList<EmojiObject>();
        return emojiObjectList;
    }

    public void setEmojiList(ArrayList<EmojiObject> jonyemojiList) {
        this.emojiObjectList = jonyemojiList;
    }

    public boolean getCarryDeleteSign() {
        return carryDeleteSign;
    }

    public void setCarryDeleteSign(boolean isCarryDeleteSign) {
        this.carryDeleteSign = isCarryDeleteSign;
    }

    public static class EmojiObject {

        private String emojiId;

        private String packageId;

        private int emojiResId;

        private String emojiName;

        private int emojiType;

        public int getEmojiType() {
            return emojiType;
        }

        public void setEmojiType(int emojiType) {
            this.emojiType = emojiType;
        }

        public String getEmojiId() {
            return emojiId;
        }

        public void setEmojiId(String emojiId) {
            this.emojiId = emojiId;
        }

        public String getPackageId() {
            return packageId;
        }

        public void setPackageId(String packageId) {
            this.packageId = packageId;
        }

        public int getEmojiResId() {
            return emojiResId;
        }

        public void setEmojiResId(int emojiResId) {
            this.emojiResId = emojiResId;
        }

        public String getEmojiName() {
            return emojiName;
        }

        public void setEmojiName(String emojiName) {
            this.emojiName = emojiName;
        }
    }

}
