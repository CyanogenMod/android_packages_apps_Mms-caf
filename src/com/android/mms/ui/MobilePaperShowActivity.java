/*
   Copyright (c) 2013-2014, The Linux Foundation. All Rights Reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.android.mms.ui;

import java.util.ArrayList;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Telephony.Mms;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.mms.model.LayoutModel;
import com.android.mms.model.RegionModel;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.R;
import com.android.mms.util.AddressUtils;

import com.android.mms.transaction.MessagingNotification;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.MultimediaMessagePdu;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.util.SqliteWrapper;

public class MobilePaperShowActivity extends Activity {
    private static final String TAG = "MobilePaperShowActivity";
    private static final int MENU_SLIDESHOW = 1;
    private static final int MENU_CALL = 2;
    private static final int MENU_REPLY = 3;

    private static final int MESSAGE_READ = 1;

    private int mMailboxId = -1;

    private FrameLayout mSlideView;
    private SlideshowModel mSlideModel;
    private SlideshowPresenter mPresenter;
    private LinearLayout mRootView;
    private Intent mIntent;
    private Uri mUri;
    private ScaleGestureDetector mScaleDetector;
    private ScrollView mScrollViewPort;

    private float mFontSizeForSave = MessageUtils.FONT_SIZE_DEFAULT;
    private Handler mHandler;
    private ArrayList<TextView> mSlidePaperItemTextViews;
    private boolean mOnScale;

    private Runnable mStopScaleRunnable = new Runnable() {
        @Override
        public void run() {
            // Delay the execution to ensure scroll and zoom no conflict
            mOnScale = false;
        }
    };

    private class MyScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mFontSizeForSave = MessageUtils.onFontSizeScale(mSlidePaperItemTextViews,
                    detector.getScaleFactor(), mFontSizeForSave);
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mHandler.removeCallbacks(mStopScaleRunnable);
            mOnScale = true;
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {
            mHandler.postDelayed(mStopScaleRunnable, MessageUtils.DELAY_TIME);
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.mobile_paper_view);
        mHandler = new Handler();
        mIntent = getIntent();
        mUri = mIntent.getData();
        mMailboxId = getMmsMessageBoxID(this, mUri);
        mRootView = (LinearLayout) findViewById(R.id.view_root);
        mSlideView = (FrameLayout) findViewById(R.id.view_scroll);
        mScaleDetector = new ScaleGestureDetector(this, new MyScaleListener());
        initSlideModel();
        drawRootView();
        markAsReadIfNeed();

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    private void initSlideModel() {
        try {
            mSlideModel = SlideshowModel.createFromMessageUri(this, mUri);
            if (0 == mSlideModel.size()) {
                SlideModel slModel = new SlideModel(mSlideModel);
                mSlideModel.add(slModel);
            }

            MultimediaMessagePdu msgPdu = (MultimediaMessagePdu) PduPersister
                    .getPduPersister(this).load(mUri);
            if (msgPdu != null) {
                EncodedStringValue subject = msgPdu.getSubject();
                if (subject != null) {
                    String subStr = subject.getString();
                    setTitle(subStr);
                } else {
                    setTitle("");
                }
            } else {
                setTitle("");
            }

        } catch (MmsException e) {
            Log.e(TAG, "Cannot present the slide show.", e);
            Toast.makeText(this, R.string.cannot_play, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }

    private void markAsReadIfNeed() {
        boolean unread = mIntent.getBooleanExtra("unread", false);
        if (unread) {
            ContentValues values = new ContentValues(1);
            values.put(Mms.READ, MESSAGE_READ);
            SqliteWrapper.update(this, getContentResolver(),
                    mUri, values, null, null);

            MessagingNotification.blockingUpdateNewMessageIndicator(
                    this, MessagingNotification.THREAD_NONE, false);

        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        MessageUtils.saveTextFontSize(this, mFontSizeForSave);
    }

    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getPointerCount() > 1) {
            mScaleDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    private void drawRootView() {
        if (mSlidePaperItemTextViews == null) {
            mSlidePaperItemTextViews = new ArrayList<TextView>();
        } else {
            mSlidePaperItemTextViews.clear();
        }
        LayoutInflater mInflater = LayoutInflater.from(this);
        for (int index = 0; index < mSlideModel.size(); index++) {
            SlideListItemView view = (SlideListItemView) mInflater.inflate(
                    R.layout.mobile_paper_item, null);
            mPresenter = (SlideshowPresenter) PresenterFactory.getPresenter("SlideshowPresenter",
                    this, (SlideViewInterface) view, mSlideModel);
            TextView contentText = (TextView) view.findViewById(R.id.text_preview);
            contentText.setTextIsSelectable(true);
            mPresenter.presentSlide((SlideViewInterface) view, mSlideModel.get(index));
            contentText.setTextSize(TypedValue.COMPLEX_UNIT_PX, MessageUtils.getTextFontSize(this));
            contentText.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    MessageUtils.onMessageContentClick(MobilePaperShowActivity.this, (TextView) v);
                }
            });
            TextView text = (TextView) view.findViewById(R.id.slide_number_text);
            text.setFocusable(false);
            text.setFocusableInTouchMode(false);
            text.setText(getString(R.string.slide_number, index + 1));
            mRootView.addView(view);
            mSlidePaperItemTextViews.add(contentText);
        }

        if (mScrollViewPort == null) {
            mScrollViewPort = new ScrollView(this) {
                private int currentX;
                private int currentY;

                @Override
                public boolean onTouchEvent(MotionEvent ev) {
                    mScaleDetector.onTouchEvent(ev);
                    final int action = ev.getAction();
                    switch (action) {
                        case MotionEvent.ACTION_DOWN: {
                            currentX = (int) ev.getRawX();
                            currentY = (int) ev.getRawY();
                            break;
                        }
                        case MotionEvent.ACTION_MOVE: {
                            int x2 = (int) ev.getRawX();
                            int y2 = (int) ev.getRawY();
                            // To ensure that no conflict between zoom and scroll
                            if (!mOnScale) {
                                mScrollViewPort.scrollBy(currentX - x2, currentY - y2);
                            }
                            currentX = x2;
                            currentY = y2;
                            break;
                        }
                    }
                    return true;
                }

                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    mScaleDetector.onTouchEvent(ev);
                    final int action = ev.getAction();
                    switch (action) {
                        case MotionEvent.ACTION_DOWN: {
                            currentX = (int) ev.getRawX();
                            currentY = (int) ev.getRawY();
                            break;
                        }
                        case MotionEvent.ACTION_MOVE: {
                            int x2 = (int) ev.getRawX();
                            int y2 = (int) ev.getRawY();
                            // To ensure that no conflict between zoom and scroll
                            if (!mOnScale) {
                                mScrollViewPort.scrollBy(currentX - x2, currentY - y2);
                            }
                            currentX = x2;
                            currentY = y2;
                            break;
                        }
                    }
                    return false;
                }
            };

            mSlideView.removeAllViews();
            mScrollViewPort.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
            mScrollViewPort.addView(mRootView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
            mSlideView.addView(mScrollViewPort);
        }
    }

    private void redrawPaper() {
        mRootView.removeAllViews();
        drawRootView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (Mms.MESSAGE_BOX_INBOX == mMailboxId) {
            menu.add(0, MENU_REPLY, 0, R.string.menu_reply);
            menu.add(0, MENU_CALL, 0, R.string.menu_call);
        }
        menu.add(0, MENU_SLIDESHOW, 0, R.string.view_slideshow);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SLIDESHOW:
                viewMmsMessageAttachmentSliderShow(this, mUri, null, null,
                        mIntent.getStringArrayListExtra("sms_id_list"),
                        mIntent.getBooleanExtra("mms_report", false));
                break;
            case MENU_REPLY: {
                replyMessage(this, AddressUtils.getFrom(this, mUri));
                finish();
                break;
            }
            case MENU_CALL:
                call();
                break;
            case android.R.id.home:
                finish();
                break;
            default:
                break;
        }
        return true;
    }

    private void replyMessage(Context context, String number) {
        Intent intent = new Intent(context, ComposeMessageActivity.class);
        intent.putExtra("address", number);
        intent.putExtra("msg_reply", true);
        context.startActivity(intent);
    }

    private void call() {
        String msgFromTo = null;
        if (mMailboxId == Mms.MESSAGE_BOX_INBOX) {
            msgFromTo = AddressUtils.getFrom(this, mUri);
        }
        if (msgFromTo == null) {
            return;
        }

        if (MessageUtils.isMultiSimEnabledMms()) {
            if (MessageUtils.getActivatedIccCardCount() > 1) {
                showCallSelectDialog(msgFromTo);
            } else {
                if (MessageUtils.isIccCardActivated(MessageUtils.SUB1)) {
                    MessageUtils.dialRecipient(this, msgFromTo, MessageUtils.SUB1);
                } else if (MessageUtils.isIccCardActivated(MessageUtils.SUB2)) {
                    MessageUtils.dialRecipient(this, msgFromTo, MessageUtils.SUB2);
                }
            }
        } else {
            MessageUtils.dialRecipient(this, msgFromTo, MessageUtils.SUB_INVALID);
        }
    }

    private void showCallSelectDialog(final String msgFromTo) {
        String[] items = new String[MessageUtils.getActivatedIccCardCount()];
        for (int i = 0; i < items.length; i++) {
            items[i] = MessageUtils.getMultiSimName(this, i);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.menu_call));
        builder.setCancelable(true);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public final void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    new Thread(new Runnable() {
                        public void run() {
                            MessageUtils.dialRecipient(MobilePaperShowActivity.this, msgFromTo,
                                    MessageUtils.SUB1);
                        }
                    }).start();
                } else {
                    new Thread(new Runnable() {
                        public void run() {
                            MessageUtils.dialRecipient(MobilePaperShowActivity.this, msgFromTo,
                                    MessageUtils.SUB2);
                        }
                    }).start();
                }
                dialog.dismiss();
            }
        });
        builder.show();
    }

    private int getMmsMessageBoxID(Context context, Uri uri) {
        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(), uri,
                new String[] {Mms.MESSAGE_BOX}, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getInt(0);
                }
            } finally {
                cursor.close();
            }
        }
        return -1;
    }

    public static void viewMmsMessageAttachmentSliderShow(Context context,
            Uri msgUri, SlideshowModel slideshow, PduPersister persister,
            ArrayList<String> allIdList, boolean report) {

        boolean isSimple = (slideshow == null) ? false : slideshow.isSimple();
        if (isSimple || msgUri == null) {
            MessageUtils.viewSimpleSlideshow(context, slideshow);
        } else {
            Intent intent = new Intent(context, SlideshowActivity.class);
            intent.setData(msgUri);
            intent.putExtra("mms_report", report);
            intent.putStringArrayListExtra("sms_id_list", allIdList);
            context.startActivity(intent);
        }
    }
}