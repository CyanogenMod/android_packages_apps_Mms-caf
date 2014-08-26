/*
   Copyright (c) 2014, The Linux Foundation. All Rights Reserved.
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
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Telephony.Mms;
import android.text.TextUtils;
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

import com.android.mms.data.WorkingMessage;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
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
import com.google.android.mms.pdu.SendReq;
import com.google.android.mms.util.SqliteWrapper;

public class MobilePaperShowActivity extends Activity {
    private static final String TAG = "MobilePaperShowActivity";
    private static final int MENU_SLIDESHOW = 1;

    // If the finger move over 100px, we don't think it's for click.
    private static final int CLICK_LIMIT = 100;
    private static final int MESSAGE_READ = 1;

    private static final int FONTSIZE_DEFAULT = 27;

    private FrameLayout mSlideView;
    private SlideshowModel mSlideModel;
    private SlideshowPresenter mPresenter;
    private LinearLayout mRootView;
    private Uri mUri;
    private Uri mTempMmsUri;
    private long mTempThreadId;
    private ScrollView mScrollViewPort;
    private String mSubject;

    private ArrayList<TextView> mSlidePaperItemTextViews;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Intent intent = getIntent();
        Uri msg = intent.getData();
        setContentView(R.layout.mobile_paper_view);
        mUri = msg;
        MultimediaMessagePdu msgPdu;
        mRootView = (LinearLayout) findViewById(R.id.view_root);
        mSlideView = (FrameLayout) findViewById(R.id.view_scroll);

        try {
            mSlideModel = SlideshowModel.createFromMessageUri(this, msg);
            //add 11.4.27 for add a slide when preview && slide is 0
            if (0 == mSlideModel.size()) {
                SlideModel slModel = new SlideModel(mSlideModel);
                mSlideModel.add(slModel);
            }

            msgPdu = (MultimediaMessagePdu) PduPersister.getPduPersister(this).load(msg);
            mSubject = "";
            if (msgPdu != null) {
                EncodedStringValue subject = msgPdu.getSubject();
                if (subject != null) {
                    String subStr = subject.getString();
                    mSubject = subStr;
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

        drawRootView();

        boolean unread = intent.getBooleanExtra("unread", false);
        if (unread) {
            ContentValues values = new ContentValues(1);
            values.put(Mms.READ, MESSAGE_READ);
            SqliteWrapper.update(this, getContentResolver(),
                    mUri, values, null, null);

            MessagingNotification.blockingUpdateNewMessageIndicator(
                    this, MessagingNotification.THREAD_NONE, false);

        }

        // Register a BroadcastReceiver to listen on HTTP I/O process.
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    private void drawRootView() {
        if (mSlidePaperItemTextViews == null) {
            mSlidePaperItemTextViews = new ArrayList<TextView>();
        } else {
            mSlidePaperItemTextViews.clear();
        }
        LayoutInflater mInflater = LayoutInflater.from(this);
        for (int index = 0; index < mSlideModel.size(); index++) {
            SlideListItemView view = (SlideListItemView) mInflater
                    .inflate(R.layout.mobile_paper_item, null);
            view.setLayoutModel(mSlideModel.getLayout().getLayoutType());
            mPresenter = (SlideshowPresenter) PresenterFactory
                    .getPresenter("SlideshowPresenter",
                            this, (SlideViewInterface) view, mSlideModel);
            TextView contentText = view.getContentText();
            contentText.setTextIsSelectable(true);
            mPresenter.presentSlide((SlideViewInterface) view, mSlideModel.get(index));
            contentText.setTextSize(TypedValue.COMPLEX_UNIT_PX, FONTSIZE_DEFAULT);

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
                private int move;

                @Override
                public boolean onTouchEvent(MotionEvent ev) {
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
                            /* To ensure that no conflict between zoom and scroll */
                            mScrollViewPort.scrollBy(currentX - x2, currentY - y2);
                            currentX = x2;
                            currentY = y2;
                            break;
                        }
                    }
                    return true;
                }

                @Override
                public boolean onInterceptTouchEvent(MotionEvent ev) {
                    final int action = ev.getAction();
                    switch (action) {
                        case MotionEvent.ACTION_DOWN: {
                            currentX = (int) ev.getRawX();
                            currentY = (int) ev.getRawY();
                            move = 0;
                            break;
                        }
                        case MotionEvent.ACTION_MOVE: {
                            int x2 = (int) ev.getRawX();
                            int y2 = (int) ev.getRawY();
                            /* To ensure that no conflict between zoom and scroll */
                            mScrollViewPort.scrollBy(currentX - x2, currentY - y2);
                            move += Math.abs(currentY - y2);
                            currentX = x2;
                            currentY = y2;
                            break;
                        }
                    }
                    return move > CLICK_LIMIT;
                }
            };

            mSlideView.removeAllViews();
            mScrollViewPort.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
            mScrollViewPort.addView(mRootView, new FrameLayout
                    .LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT));
            mSlideView.addView(mScrollViewPort);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_SLIDESHOW, 0, R.string.view_slideshow);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SLIDESHOW:
                Intent intent = getIntent();
                Uri msg = intent.getData();
                viewMmsMessageAttachmentSliderShow(this, msg, null, null,
                        intent.getStringArrayListExtra("sms_id_list"),
                        intent.getBooleanExtra("mms_report", false));
                break;
            case android.R.id.home:
                finish();
                break;
            default:
                break;
        }
        return true;
    }

    public static void viewMmsMessageAttachmentSliderShow(Context context,
            Uri msgUri, SlideshowModel slideshow, PduPersister persister,
            ArrayList<String> allIdList,boolean report) {

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
