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
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
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
    private static final int MENU_CALL = 2;
    private static final int MENU_REPLY = 3;
    private static final int MENU_FORWARD = 4;

    private int mMailboxId = -1;

    private FrameLayout mSlideView;
    private SlideshowModel mSlideModel;
    private SlideshowPresenter mPresenter;
    private LinearLayout mRootView;
    private TextView mDetailsText;
    private String mNumber;
    private Intent mIntent;
    private Uri mUri;
    private Uri mTempMmsUri;
    private long mTempThreadId;
    private ScaleGestureDetector mScaleDetector;
    private ScrollView mScrollViewPort;
    private Cursor mCursor;
    private String mSubject;

    private float mFontSizeForSave = MessageUtils.FONT_SIZE_DEFAULT;
    private Handler mHandler;
    private ArrayList<TextView> mSlidePaperItemTextViews;
    private boolean mOnScale;

    private static final int QUERY_MESSAGE_TOKEN = 6702;
    private BackgroundQueryHandler mAsyncQueryHandler;
    private static final String MAILBOX_URI = "content://mms-sms/mailbox/";
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
        mDetailsText = (TextView)findViewById(R.id.message_details);
        mNumber = AddressUtils.getFrom(this, mUri);
        mSlideView = (FrameLayout) findViewById(R.id.view_scroll);
        mScaleDetector = new ScaleGestureDetector(this, new MyScaleListener());
        initSlideModel();
        markAsReadIfNeed();

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setTitle(getString(R.string.message_details_title));
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

            if (mMailboxId != MailBoxMessageList.TYPE_DRAFTBOX) {
                String mailboxUri = MAILBOX_URI + mMailboxId;
                mAsyncQueryHandler = new BackgroundQueryHandler(
                        getContentResolver());
                mAsyncQueryHandler.startQuery(QUERY_MESSAGE_TOKEN, 0,
                        Uri.parse(mailboxUri),
                        MessageListAdapter.MAILBOX_PROJECTION, "pdu._id= "
                                + mUri.getLastPathSegment(), null,
                        "normalized_date DESC");
            } else {
                completeLoad();
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
            ContentValues values = new ContentValues(2);
            values.put(Mms.SEEN, MessageUtils.MESSAGE_SEEN);
            values.put(Mms.READ, MessageUtils.MESSAGE_READ);
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

    private void setDetailsView() {
        // Add message details.
        String messageDetails = MessageUtils.getMessageDetails(
                MobilePaperShowActivity.this, mCursor,
                mSlideModel.getTotalMessageSize());
        mDetailsText.setText(messageDetails);
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
            view.setLayoutModel(mSlideModel.getLayout().getLayoutType());
            mPresenter = (SlideshowPresenter) PresenterFactory.getPresenter("SlideshowPresenter",
                    this, (SlideViewInterface) view, mSlideModel);
            TextView contentText = view.getContentText();
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

        // Add message details.
        setDetailsView();

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
                            currentX = x2;
                            currentY = y2;
                            break;
                        }
                    }
                    super.onTouchEvent(ev);
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
                            currentX = x2;
                            currentY = y2;
                            break;
                        }
                    }
                    super.onInterceptTouchEvent(ev);
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
            if (MessageUtils.isRecipientCallable(mNumber)) {
                menu.add(0, MENU_CALL, 0, R.string.menu_call)
                        .setIcon(R.drawable.ic_menu_call)
                        .setTitle(R.string.menu_call)
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
        }
        menu.add(0, MENU_FORWARD, 0, R.string.menu_forward);
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
                replyMessage(this, mNumber);
                finish();
                break;
            }
            case MENU_CALL:
                MessageUtils.dialNumber(this,mNumber);
                break;
            case MENU_FORWARD:
                forwardMms();
                break;
            case android.R.id.home:
                finish();
                break;
            default:
                break;
        }
        return true;
    }

    private final class BackgroundQueryHandler extends AsyncQueryHandler {
        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (cursor != null) {
                if (mCursor != null) {
                    mCursor.close();
                }
                mCursor = cursor;
                mCursor.moveToFirst();
            }
            completeLoad();
        }
    }

    private void completeLoad() {
        drawRootView();
        invalidateOptionsMenu();
    }

    private void replyMessage(Context context, String number) {
        Intent intent = new Intent(context, ComposeMessageActivity.class);
        intent.putExtra("address", number);
        intent.putExtra("msg_reply", true);
        intent.putExtra("reply_message", true);
        intent.putExtra("exit_on_sent", true);
        context.startActivity(intent);
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

    private void forwardMms() {
        new AsyncDialog(this).runAsync(new Runnable() {
            @Override
            public void run() {
                SendReq sendReq = new SendReq();
                String subject = getString(R.string.forward_prefix);
                if (!TextUtils.isEmpty(mSubject)) {
                    subject += mSubject;
                }
                sendReq.setSubject(new EncodedStringValue(subject));
                sendReq.setBody(mSlideModel.makeCopy());
                mTempMmsUri = null;
                try {
                    PduPersister persister =
                            PduPersister.getPduPersister(MobilePaperShowActivity.this);
                    mTempMmsUri = persister.persist(sendReq, Mms.Draft.CONTENT_URI, true,
                            MessagingPreferenceActivity
                                    .getIsGroupMmsEnabled(MobilePaperShowActivity.this), null);
                    mTempThreadId = MessagingNotification.getThreadId(
                            MobilePaperShowActivity.this, mTempMmsUri);
                } catch (MmsException e) {
                    Log.e(TAG, "Failed to forward message: " + mTempMmsUri);
                    Toast.makeText(MobilePaperShowActivity.this,
                            R.string.cannot_save_message, Toast.LENGTH_SHORT).show();
                }

            }
        }, new Runnable() {
            @Override
            public void run() {
                Intent intentForward = new Intent(MobilePaperShowActivity.this,
                        ComposeMessageActivity.class);
                intentForward.putExtra("exit_on_sent", true);
                intentForward.putExtra("forwarded_message", true);
                String subject = getString(R.string.forward_prefix);
                if (!TextUtils.isEmpty(mSubject)) {
                    subject += mSubject;
                }
                intentForward.putExtra("subject", subject);
                intentForward.putExtra("msg_uri", mTempMmsUri);
                if (mTempThreadId > 0) {
                    intentForward.putExtra(Mms.THREAD_ID, mTempThreadId);
                }
                MobilePaperShowActivity.this.startActivity(intentForward);
            }
        }, R.string.building_slideshow_title);
    }
}