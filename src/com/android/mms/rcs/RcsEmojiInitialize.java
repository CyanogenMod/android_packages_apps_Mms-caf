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

import com.android.mms.rcs.RcsEmojiPackageObject.EmojiObject;
import com.suntek.mway.rcs.client.api.plugin.entity.emoticon.EmoticonConstant;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewStub;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.android.mms.R;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;

public class RcsEmojiInitialize {

    private Context mContext;

    private ViewStub mViewStub;

    private View mEmojiView = null;

    private GridView mEmojiGridView;

    private GirdViewAdapter mGirdViewAdapter;

    private LinearLayout mLinearLayout;

    private ImageButton mDeleteBtn;

    private String mSelectPackageId = "";

    private ArrayList<RcsEmojiPackageObject> mEmojiPackages = new ArrayList<RcsEmojiPackageObject>();

    private ViewOnClickListener mViewOnClickListener;

    private int mSmallEmojiHigth = 0;

    private int mBigEmojiHigth = 0;

    public interface ViewOnClickListener {

        public void viewOpenOrCloseListener(boolean isOpen);

        public void emojiSelectListener(EmojiObject emojiObject);

        public void onEmojiDeleteListener();

        public void addEmojiPackageListener();
    }

    public RcsEmojiInitialize(Context context, ViewStub viewStub,
            ViewOnClickListener viewOnClickListener) {
        this.mContext = context;
        this.mViewStub = viewStub;
        this.mViewOnClickListener = viewOnClickListener;
        mSmallEmojiHigth = RcsEmojiStoreUtil.dip2px(mContext, 40);
        mBigEmojiHigth = RcsEmojiStoreUtil.dip2px(mContext, 80);
    }

    public void closeOrOpenView() {
        if (mEmojiView == null) {
            RcsEmojiStoreUtil.closeKB((Activity) mContext);
            initEmojiView();
            mViewOnClickListener.viewOpenOrCloseListener(true);
            return;
        }
        if (mEmojiView != null && mEmojiView.getVisibility() == View.GONE) {
            RcsEmojiStoreUtil.closeKB((Activity) mContext);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    mEmojiView.setVisibility(View.VISIBLE);
                    mViewOnClickListener.viewOpenOrCloseListener(true);
                }
            }, 200);
        } else {
            mEmojiView.setVisibility(View.GONE);
            RcsEmojiStoreUtil.openKB(mContext);
            mViewOnClickListener.viewOpenOrCloseListener(false);
        }
    }

    public void closeViewAndKB() {
        mEmojiView.setVisibility(View.GONE);
        mViewOnClickListener.viewOpenOrCloseListener(false);
    }

    private void initEmojiView() {
        mEmojiView = mViewStub.inflate();
        mEmojiGridView = (GridView) mEmojiView.findViewById(R.id.emoji_grid_view);
        mLinearLayout = (LinearLayout) mEmojiView
                .findViewById(R.id.content_linear_layout);
        mDeleteBtn = (ImageButton) mEmojiView.findViewById(R.id.delete_emoji_btn);
        mDeleteBtn.setVisibility(View.GONE);
        mEmojiView.findViewById(R.id.add_emoji_btn).setOnClickListener(
                mClickListener);
        mEmojiPackages.clear();
        mEmojiPackages.addAll(RcsEmojiStoreUtil.getStorePackageList());
        if (mEmojiPackages.size() == 0)
            return;
        mDeleteBtn.setOnClickListener(mClickListener);
        RcsEmojiPackageObject selectPackageBean = mEmojiPackages.get(0);
        mSelectPackageId = selectPackageBean.getPackageId();
        initPackageView(selectPackageBean);
        mEmojiGridView.setNumColumns(selectPackageBean.getHorizontalLineSize());
        mGirdViewAdapter = new GirdViewAdapter(mContext, mViewOnClickListener);
        mEmojiGridView.setAdapter(mGirdViewAdapter);
        if (selectPackageBean.getCarryDeleteSign()) {
            mGirdViewAdapter.setEmojiData(selectPackageBean.getEmojiList(),
                    mSmallEmojiHigth);
            mDeleteBtn.setVisibility(View.VISIBLE);
        } else {
            mGirdViewAdapter.setEmojiData(selectPackageBean.getEmojiList(),
                    mBigEmojiHigth);
            mDeleteBtn.setVisibility(View.GONE);
        }
    }

    private void initPackageView(RcsEmojiPackageObject emojiPackageObject) {
        mLinearLayout.removeAllViews();
        for (int i = 0; i < mEmojiPackages.size(); i++) {
            RcsEmojiPackageObject emojiPackage = mEmojiPackages.get(i);
            if (emojiPackage.getPackageId() == emojiPackageObject
                    .getPackageId()) {
                ImageButton imageButton = createImageView(
                        emojiPackageObject.getPackageId(), emojiPackage);
                mLinearLayout.addView(imageButton);
            } else {
                ImageButton imageButton = createImageView(null, emojiPackage);
                mLinearLayout.addView(imageButton);
            }
        }
    }

    private ImageButton createImageView(String checkId,
            RcsEmojiPackageObject emojiPackageObject) {
        LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(
                RcsEmojiStoreUtil.dip2px(mContext, 45),
                LinearLayout.LayoutParams.MATCH_PARENT);
        param.leftMargin = RcsEmojiStoreUtil.dip2px(mContext, 1);
        ImageButton imageButton = new ImageButton(mContext);
        imageButton.setLayoutParams(param);
        imageButton.setScaleType(ScaleType.CENTER_INSIDE);
        imageButton.setPadding(2, 2, 2, 2);
        if (emojiPackageObject.getEmojiType() == RcsEmojiPackageObject.BIG_EMOJI_TYPE) {
            imageButton.setImageBitmap(emojiPackageObject.getPackageBitmap());
        } else {
            imageButton.setImageResource(emojiPackageObject.getPackageResId());
        }
        if (TextUtils.isEmpty(checkId))
            imageButton.setBackgroundResource(R.color.gray5);
        else
            imageButton.setBackgroundResource(R.color.white);
        imageButton.setTag(emojiPackageObject);
        imageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                RcsEmojiPackageObject emojiPackageObject = (RcsEmojiPackageObject) view
                        .getTag();
                if (emojiPackageObject == null)
                    return;
                if (mSelectPackageId == emojiPackageObject.getPackageId())
                    return;
                mSelectPackageId = emojiPackageObject.getPackageId();
                initPackageView(emojiPackageObject);
                mEmojiGridView.setNumColumns(emojiPackageObject
                        .getHorizontalLineSize());
                if (emojiPackageObject.getCarryDeleteSign()) {
                    mGirdViewAdapter.setEmojiData(
                            emojiPackageObject.getEmojiList(), mSmallEmojiHigth);
                    mDeleteBtn.setVisibility(View.VISIBLE);
                } else {
                    mGirdViewAdapter.setEmojiData(
                            emojiPackageObject.getEmojiList(), mBigEmojiHigth);
                    mDeleteBtn.setVisibility(View.GONE);
                }
            }
        });
        return imageButton;
    }

    private OnClickListener mClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
            case R.id.delete_emoji_btn:
                mViewOnClickListener.onEmojiDeleteListener();
                break;
            case R.id.add_emoji_btn:
                mViewOnClickListener.addEmojiPackageListener();
                break;
            default:
                break;
            }

        }
    };

    private HashMap<String, SoftReference<Bitmap>> mImageCache = new HashMap<String, SoftReference<Bitmap>>();

    public class GirdViewAdapter extends BaseAdapter {

        private LinearLayout.LayoutParams mGifParam = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        private ArrayList<EmojiObject> mEmojiObjects = new ArrayList<EmojiObject>();

        private Context mContext;

        private int mItemHeight = 0;

        private ViewOnClickListener mViewOnClickListener;

        public GirdViewAdapter(Context context,
                ViewOnClickListener viewOnClickListener) {
            this.mContext = context;
            this.mViewOnClickListener = viewOnClickListener;
        }

        public void setEmojiData(ArrayList<EmojiObject> emojiObjects,
                int itemHeight) {
            this.mEmojiObjects.clear();
            this.mEmojiObjects.addAll(emojiObjects);
            this.mItemHeight = itemHeight;
            this.notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mEmojiObjects.size();
        }

        @Override
        public Object getItem(int position) {
            if (position < mEmojiObjects.size())
                return mEmojiObjects.get(position);
            return null;
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(
                        R.layout.rcs_emoji_grid_view_item, null);
                holder = new ViewHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            RelativeLayout.LayoutParams param = (RelativeLayout.LayoutParams) holder.mItemView
                    .getLayoutParams();
            param.height = mItemHeight;
            EmojiObject bean = mEmojiObjects.get(position);
            if (TextUtils.isEmpty(bean.getEmojiName()))
                holder.title.setVisibility(View.GONE);
            else {
                holder.title.setVisibility(View.VISIBLE);
            }
            holder.title.setText(bean.getEmojiName());
            if (bean.getEmojiType() == RcsEmojiPackageObject.BIG_EMOJI_TYPE) {
                holder.icon.setImageBitmap(loadEmojiBitmap(bean.getEmojiId()));
            } else {
                holder.icon.setImageResource(bean.getEmojiResId());
            }
            holder.mItemView.setTag(bean);
            holder.mItemView.setOnClickListener(mClickListener);
            holder.mItemView.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View arg0) {
                    EmojiObject bean = (EmojiObject) arg0.getTag();
                    if (bean.getEmojiType() == RcsEmojiPackageObject.BIG_HORIZONTAL_LINE_SIZE) {
                        byte[] data = null;
                        try {
                            data = RcsApiManager
                                    .getEmoticonApi()
                                    .decrypt2Bytes(bean.getEmojiId(),
                                            EmoticonConstant.EMO_DYNAMIC_FILE);
                        } catch (ServiceDisconnectedException e) {
                            e.printStackTrace();
                        }
                        openPopupwin(arg0, data);
                    } else {
                        InputStream is = mContext.getResources()
                                .openRawResource(bean.getEmojiResId());
                        byte[] data = RcsEmojiStoreUtil.inputToByte(is);
                        openPopupwin(arg0, data);
                    }
                    return false;
                }
            });
            return convertView;
        }

        private void openPopupwin(View root, byte[] data) {
            RcsEmojiGifView gifImageView = new RcsEmojiGifView(mContext);
            gifImageView.setLayoutParams(mGifParam);
            gifImageView.setBackgroundResource(R.drawable.rcs_emoji_popup_bg);
            gifImageView.setAutoPlay(true);
            gifImageView.setGifData(data);
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            PopupWindow popupWindow = new PopupWindow(root,
                    bitmap.getWidth() + 10, bitmap.getHeight() + 10);
            popupWindow.setBackgroundDrawable(new ColorDrawable(
                    Color.TRANSPARENT));
            popupWindow.setFocusable(true);
            popupWindow.setOutsideTouchable(true);
            popupWindow.setContentView(gifImageView);
            popupWindow.showAtLocation(root, Gravity.CENTER, 0, 0);
            popupWindow.update();
        }

        private OnClickListener mClickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                EmojiObject bean = (EmojiObject) v.getTag();
                mViewOnClickListener.emojiSelectListener(bean);
            }
        };

        private class ViewHolder {
            RelativeLayout mItemView;

            TextView title;

            ImageView icon;

            public ViewHolder(View convertView) {
                this.title = (TextView) convertView.findViewById(R.id.title);
                this.icon = (ImageView) convertView.findViewById(R.id.icon);
                this.mItemView = (RelativeLayout) convertView
                        .findViewById(R.id.item);
                this.mItemView.setBackgroundResource(R.drawable.rcs_emoji_button_bg);
            }
        }

        public Bitmap loadEmojiBitmap(String emoticonId) {
            if (TextUtils.isEmpty(emoticonId)) {
                return null;
            }
            if (mImageCache.containsKey(emoticonId)) {
                SoftReference<Bitmap> softReference = mImageCache
                        .get(emoticonId);
                Bitmap bitmap = softReference.get();
                if (bitmap != null) {
                    return bitmap;
                } else {
                    mImageCache.remove(emoticonId);
                }
            }
            byte[] imageByte = null;
            Bitmap bitmap = null;
            try {
                imageByte = RcsApiManager
                        .getEmoticonApi()
                        .decrypt2Bytes(emoticonId,
                                EmoticonConstant.EMO_STATIC_FILE);
            } catch (ServiceDisconnectedException e) {
                e.printStackTrace();
            }
            if (imageByte != null)
                bitmap = BitmapFactory.decodeByteArray(imageByte, 0,
                        imageByte.length);
            if (bitmap != null) {
                mImageCache.put(emoticonId, new SoftReference<Bitmap>(bitmap));
            }
            return bitmap;
        }

    }

}
