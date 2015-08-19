/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.sqlite.SqliteWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ScaleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract.Profile;
import android.provider.Telephony.Sms;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.LineHeightSpan;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.model.ContactBuilder;
import com.android.internal.telephony.PhoneConstants;
import com.android.mms.LogTag;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.MessageInfoCache;
import com.android.mms.data.WorkingMessage;
import com.android.mms.presenters.SlideShowPresenter;
import com.android.mms.transaction.SmsReceiverService;
import com.android.mms.transaction.Transaction;
import com.android.mms.transaction.TransactionBundle;
import com.android.mms.transaction.TransactionService;
import com.android.mms.ui.zoom.ZoomMessageListItem;
import com.android.mms.util.AddressUtils;
import com.android.mms.util.DownloadManager;
import com.android.mms.util.ImageUtils;
import com.android.mms.util.IntentUtils;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.PrettyTime;
import com.android.mms.util.SmileyParser;
import com.android.mms.widget.ContactBadgeWithAttribution;
import com.cyanogen.lookup.phonenumber.response.LookupResponse;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.NotificationInd;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduPersister;

/**
 * This class provides view of a message in the messages list.
 */
public class MessageListItem extends ZoomMessageListItem implements
        OnClickListener, Checkable {
    public static final String EXTRA_URLS = "com.android.mms.ExtraUrls";

    private static final String TAG = LogTag.TAG;
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_DONT_LOAD_IMAGES = false;
    // The message is from Browser
    private static final String BROWSER_ADDRESS = "Browser Information";
    private static final String CANCEL_URI = "canceluri";
    // transparent background
    private static final int ALPHA_TRANSPARENT = 0;

    static final int MSG_LIST_EDIT    = 1;
    static final int MSG_LIST_PLAY    = 2;
    static final int MSG_LIST_DETAILS = 3;

    private boolean mIsCheck = false;

    private View mTopSpacer;
    private ViewGroup mMmsView;
    private ImageButton mMessageBubbleArrowhead;
    private ImageView mLockedIndicator;
    private ImageView mDeliveredIndicator;
    private ImageView mDetailsIndicator;
    private ImageView mSimIndicatorView;
    private TextView mSimMessageAddress;
    private TextView mBodyTextView;
    private TextView mMessageSizeView;
    private View mDownloadControls;
    private LinearLayout mMmsLayout;
    private Handler mHandler;
    private MessageItem mMessageItem;
    private String mDefaultCountryIso;
    private TextView mMessageStatus;
    private TextView mSimNameView;
    public View mMessageBlock;
    private ContactBadgeWithAttribution mAvatar;
    static private RoundedBitmapDrawable sDefaultContactImage;
    private SlideShowPresenter mPresenter;
    private int mPosition;      // for debugging
    private boolean mMultiRecipients;
    private int mManageMode;
    private Drawable mGroupedMessageBackground;
    private boolean mIsIncoming;
    private int mAccentColor;
    private MessageListView mListView;
    private MessageInfoCache mMessageInfoCache;
    private View mDownloadIcon;
    private TextView mDownloadTitle;
    private ProgressBar mDownloadProgressBar;
    private TextView mDownloadSubTitle;
    private PrettyTime mPrettyTime;
    private boolean mShowNewMessagesHeader;
    private MessageListAdapter.Metadata mMetadata;

    private ContactBadgeWithAttribution.ContactBadgeClickListener mContactBadgeClickListener =
            new ContactBadgeWithAttribution.ContactBadgeClickListener() {
                @Override
                public boolean handleContactBadgeClick(View v, Uri contactUri) {
                    if (mManageMode == MessageUtils.SELECTION_MODE) {
                        return true;
                    }
                    if (contactUri != null) {
                        try {
                            Intent intent =
                                    IntentUtils.getQuickContactForLookupIntent(mContext,
                                            v, contactUri);
                            mContext.startActivity(intent);
                            return true;
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(mContext,
                                    com.android.internal.R.string.quick_contacts_not_available,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                    return false;
                }
            };

    public MessageListItem(Context context) {
        this(context, null);
    }

    public MessageListItem(Context context, AttributeSet attrs) {
        super(context, attrs);

        Resources res = context.getResources();
        mColorSpan = new ForegroundColorSpan(res.getColor(R.color.timestamp_color));
        mDefaultCountryIso = MmsApp.getApplication().getCurrentCountryIso();

        if (sDefaultContactImage == null) {
            Bitmap defaultImage = BitmapFactory.decodeResource(res, R.drawable.ic_contact_picture);
            sDefaultContactImage = RoundedBitmapDrawableFactory.create(res, defaultImage);
            sDefaultContactImage.setAntiAlias(true);
            sDefaultContactImage.setCornerRadius(
                    Math.max(defaultImage.getWidth() / 2, defaultImage.getHeight() / 2));
        }

        mGroupedMessageBackground = context.getResources().getDrawable(R.drawable.grouped_msg);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mBodyTextView = (TextView) findViewById(R.id.text_view);
        mMessageStatus = (TextView) findViewById(R.id.message_status);
        mLockedIndicator = (ImageView) findViewById(R.id.locked_indicator);
        mDeliveredIndicator = (ImageView) findViewById(R.id.delivered_indicator);
        mDetailsIndicator = (ImageView) findViewById(R.id.details_indicator);
        mAvatar = (ContactBadgeWithAttribution) findViewById(R.id.avatar);
        mSimIndicatorView = (ImageView) findViewById(R.id.sim_indicator_icon);
        mSimNameView = (TextView) findViewById(R.id.sim_name);
        mMessageBlock = findViewById(R.id.message_block);
        mSimMessageAddress = (TextView) findViewById(R.id.sim_message_address);
        mMessageSizeView = (TextView) findViewById(R.id.mms_msg_size_view);
        mMmsLayout = (LinearLayout) findViewById(R.id.mms_layout_view_parent);
        mTopSpacer = findViewById(R.id.top_spacer);
        mMessageBubbleArrowhead = (ImageButton) findViewById(R.id.message_bubble_arrowhead);

        mAvatar.setOverlay(null);

        // Add the views to be managed by the zoom control
        addZoomableTextView(mBodyTextView);
        addZoomableTextView(mMessageStatus);
        addZoomableTextView(mSimMessageAddress);
        addZoomableTextView(mSimNameView);
        addZoomableTextView(mMessageSizeView);
    }

    public void adjustMessageBlockSpacing(int topSpace) {
        LinearLayout.LayoutParams layoutParams =
                (LinearLayout.LayoutParams) mTopSpacer.getLayoutParams();
        layoutParams.height = topSpace;
        mTopSpacer.setVisibility(View.VISIBLE);
    }

    public void bind(MessageItem msgItem, int accentColor, boolean convHasMultiRecipients,
            int position, ListView listView, MessageListAdapter.Metadata metadata,
            PrettyTime prettyTime) {
        if (DEBUG) {
            Log.v(TAG, "bind for item: " + position + " old: " +
                   (mMessageItem != null ? mMessageItem.toString() : "NULL" ) +
                    " new " + msgItem.toString());
        }
        boolean sameItem = mMessageItem != null && mMessageItem.mMsgId == msgItem.mMsgId;
        mMessageItem = msgItem;
        mPosition = position;
        mMultiRecipients = convHasMultiRecipients;
        mAccentColor = accentColor;
        mIsIncoming = metadata.isIncoming;
        mListView = (MessageListView) listView;
        mPrettyTime = prettyTime;

        if (!sameItem) {
            unbind();
        }

        setChecked(false);
        setLongClickable(false);
        setClickable(false);    // let the list view handle clicks on the item normally. When
                                // clickable is true, clicks bypass the listview and go straight
                                // to this listitem. We always want the listview to handle the
                                // clicks first.

        switch (msgItem.mMessageType) {
            case PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND:
                bindNotifInd();
                break;
            default:
                bindCommonMessage(sameItem);
                break;
        }
        MessageUtils.tintBackground(mMessageBlock, accentColor);
        setMessageBubbleArrowheadColor((StateListDrawable) mMessageBubbleArrowhead.getBackground(),
                accentColor);
        setChecked(mListView.isItemChecked(position));
        customSIMSmsView();

        toggleTimeStamp(hasCustomSpans() ? true : metadata.shouldShowTimestamp);
        mMetadata = metadata;
    }

    private void setMessageBubbleArrowheadColor(StateListDrawable sld, int accentColor) {
        sld.getStateDrawable(sld.getStateDrawableIndex(null)).setTint(accentColor);

        Drawable selector = sld.getStateDrawable(sld.getStateDrawableIndex(
                new int[] { android.R.attr.state_selected }));
        selector.setTint(MessageUtils.getDarkerColor(accentColor));
    }

    public TextView getTimestampView() {
        return mMessageStatus;
    }

    public void unbind() {
        // leave the presenter in case it's needed when rebound to a different MessageItem.
        if (mPresenter != null) {
            mPresenter.unbind();
        }
    }

    public MessageItem getMessageItem() {
        return mMessageItem;
    }

    public void setMsgListItemHandler(Handler handler) {
        mHandler = handler;
    }

    private void bindNotifInd() {
        showMmsView(false, false);

        String msgSize = String.valueOf((mMessageItem.mMessageSize + 1023) / 1024)
                + mContext.getString(R.string.kilobyte);
        String downloadTitle = getResources().getString(R.string.mms_start_download, msgSize);
        mBodyTextView.setText("");
        mBodyTextView.setVisibility(View.GONE);
        mMessageSizeView.setVisibility(View.GONE);
        mMessageStatus.setText(buildTimestampLine(mPrettyTime.format(mMessageItem.mDate)));
        mMessageStatus.setTag((Long) mMessageItem.mDate);

        updateSimIndicatorView(mMessageItem.mSubId);

        switch (mMessageItem.getMmsDownloadStatus()) {
            case DownloadManager.STATE_PRE_DOWNLOADING:
            case DownloadManager.STATE_DOWNLOADING:
                showDownloadingAttachment();
                break;
            case DownloadManager.STATE_UNKNOWN:
            case DownloadManager.STATE_UNSTARTED:
                DownloadManager downloadManager = DownloadManager.getInstance();
                boolean autoDownload = downloadManager.isAuto();
                boolean dataSuspended = (MmsApp.getApplication().getTelephonyManager()
                        .getDataState() == TelephonyManager.DATA_SUSPENDED);

                // If we're going to automatically start downloading the mms attachment, then
                // don't bother showing the download button for an instant before the actual
                // download begins. Instead, show downloading as taking place.
                if (autoDownload && !dataSuspended) {
                    showDownloadingAttachment();
                    break;
                }
            case DownloadManager.STATE_TRANSIENT_FAILURE:
            case DownloadManager.STATE_PERMANENT_FAILURE:
            default:
                mDeliveredIndicator.setVisibility(View.VISIBLE);
                setLongClickable(true);
                inflateDownloadControls();
                mDownloadIcon.setVisibility(View.VISIBLE);
                mDownloadTitle.setVisibility(View.VISIBLE);
                mDownloadProgressBar.setVisibility(View.GONE);
                mDownloadTitle.setText(downloadTitle);
                mDownloadControls.setVisibility(View.VISIBLE);
                mDownloadSubTitle.setText(R.string.mms_start_download_hint);
                mDownloadControls.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showDownloadingAttachment();
                        try {
                            NotificationInd nInd = (NotificationInd) PduPersister.getPduPersister(
                                    mContext).load(mMessageItem.mMessageUri);
                            Log.d(TAG, "Download notify Uri = " + mMessageItem.mMessageUri);
                            AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                            builder.setTitle(R.string.download);
                            builder.setCancelable(true);
                            // Judge notification weather is expired
                            if (nInd.getExpiry() < System.currentTimeMillis() / 1000L) {
                                // builder.setIcon(R.drawable.ic_dialog_alert_holo_light);
                                builder.setMessage(mContext
                                        .getString(R.string.service_message_not_found));
                                builder.show();
                                SqliteWrapper.delete(mContext, mContext.getContentResolver(),
                                        mMessageItem.mMessageUri, null, null);
                                return;
                            }
                            // Judge whether memory is full
                            else if (MessageUtils.isMmsMemoryFull()) {
                                builder.setMessage(mContext.getString(R.string.sms_full_body));
                                builder.show();
                                return;
                            }
                            // Judge whether message size is too large
                            else if ((int) nInd.getMessageSize() >
                                    MmsConfig.getMaxMessageSize()) {
                                builder.setMessage(mContext.getString(R.string.mms_too_large));
                                builder.show();
                                return;
                            }
                        } catch (MmsException e) {
                            Log.e(TAG, e.getMessage(), e);
                            return;
                        }
                        mDownloadControls.setVisibility(View.GONE);
                        Intent intent = new Intent(mContext, TransactionService.class);
                        intent.putExtra(TransactionBundle.URI, mMessageItem.mMessageUri.toString());
                        intent.putExtra(TransactionBundle.TRANSACTION_TYPE,
                                Transaction.RETRIEVE_TRANSACTION);
                        intent.putExtra(PhoneConstants.SUBSCRIPTION_KEY, mMessageItem.mSubId);

                        mContext.startService(intent);

                        DownloadManager.getInstance().markState(
                                 mMessageItem.mMessageUri, DownloadManager.STATE_PRE_DOWNLOADING);
                    }
                });
                break;
        }

        // Hide the indicators.
        mLockedIndicator.setVisibility(View.GONE);
        mDetailsIndicator.setVisibility(View.GONE);
        updateAvatarView(mMessageItem.mAddress, false);
    }

    private void updateSimIndicatorView(int subId) {
        if (MessageUtils.isMsimIccCardActive() && subId >= 0) {
            Drawable mSimIndicatorIcon = MessageUtils.getMultiSimIcon(mContext, subId);
            mSimIndicatorView.setImageDrawable(mSimIndicatorIcon);
            mSimIndicatorView.setVisibility(View.VISIBLE);

            CharSequence simName = MessageUtils.getSimName(mContext, subId);
            if (simName != null) {
                mSimNameView.setText(simName);
                mSimNameView.setVisibility(View.VISIBLE);
            } else {
                mSimNameView.setVisibility(View.GONE);
            }
        }
    }

    private String buildTimestampLine(String timestamp) {
        if (!mMultiRecipients || mMessageItem.isMe() || TextUtils.isEmpty(mMessageItem.mContact)) {
            // Never show "Me" for messages I sent.
            return timestamp;
        }
        // This is a group conversation, show the sender's name on the same line as the timestamp.
        return mContext.getString(R.string.message_timestamp_format, mMessageItem.mContact,
                timestamp);
    }

    private void showDownloadingAttachment() {
        inflateDownloadControls();
        mDownloadProgressBar.setVisibility(View.VISIBLE);
        mDownloadIcon.setVisibility(View.GONE);
        mDownloadTitle.setVisibility(View.GONE);
        mDownloadSubTitle.setText(R.string.downloading);
    }

    private void updateAvatarView(String addr, boolean isSelf) {
        Drawable avatarDrawable;
        if (isSelf || !TextUtils.isEmpty(addr)) {
            Contact contact = isSelf ? Contact.getMe(false) : Contact.get(addr, true);
            contact.bindAvatar(mAvatar);

            if (isSelf) {
                mAvatar.assignContactUri(Profile.CONTENT_URI);
            } else {
                if (contact.existsInDatabase()) {
                    mAvatar.assignContactUri(contact.getUri());
                } else if (MessageUtils.isWapPushNumber(contact.getNumber())) {
                    mAvatar.assignContactFromPhone(
                            MessageUtils.getWapPushNumber(contact.getNumber()), true);
                } else {
                    mAvatar.assignContactFromPhone(contact.getNumber(), true);
                }
            }
            checkForUnknownContact(contact);
        } else {
            mAvatar.setImageDrawable(sDefaultContactImage);
        }
        mAvatar.setClickable(mManageMode != MessageUtils.SELECTION_MODE);
        mAvatar.setContactBadgeClickListener(mContactBadgeClickListener);
    }

    private void checkForUnknownContact(Contact contact) {
        if (!contact.existsInDatabase()) {
            String number = AddressUtils.normalizePhoneNumber(contact.getNumber());
            LookupResponse lookupResponse = MmsApp.getApplication().
                    getPhoneNumberLookupResponse(number);
            if (lookupResponse != null) {
                updateView(lookupResponse);
            } else {
                // request info for this contact
                MmsApp.getApplication().lookupInfoForPhoneNumber(number);
            }
        }
    }

    private void updateView(LookupResponse lookupResponse) {

        ContactBuilder builder =
                new ContactBuilder(ContactBuilder.REVERSE_LOOKUP, lookupResponse.mNumber,
                        lookupResponse.mNumber);
        builder.setName(ContactBuilder.Name.createDisplayName(lookupResponse.mName));
        builder.setIsBusiness(false);
        builder.addPhoneNumber(
                ContactBuilder.PhoneNumber.createMainNumber(lookupResponse.mNumber));
        builder.addAddress(ContactBuilder.Address.createFormattedHome(lookupResponse.mAddress));
        builder.setPhotoUrl(lookupResponse.mPhotoUrl);
        builder.setSpamCount(lookupResponse.mSpamCount);
        builder.setInfoProviderName(lookupResponse.mProviderName);
        com.android.contacts.common.model.Contact contact = builder.build();
        mAvatar.setContactUri(contact.getLookupUri());
        mAvatar.setContactPhone(lookupResponse.mNumber);

        // if url exists load into image
        if (!TextUtils.isEmpty(lookupResponse.mPhotoUrl)) {
            ImageUtils.loadBitampFromUrl(getContext(), lookupResponse.mPhotoUrl, mAvatar);
        }

        // add attribution to the contact image
        ScaleDrawable attributionDrawable = new ScaleDrawable(lookupResponse.mAttributionLogo,
                Gravity.BOTTOM | Gravity.RIGHT, 1.0f, 1.0f);
        attributionDrawable.setLevel(3200);
        mAvatar.setOverlay(attributionDrawable);
    }

    public TextView getBodyTextView() {
        return mBodyTextView;
    }

    public void setNewMessagesHeaderVisiblity(boolean showHeader) {
        mShowNewMessagesHeader = showHeader;
    }

    public boolean isNewMessagesHeaderVisibile() {
        return mShowNewMessagesHeader;
    }

    private void updateNewMessagesHeader() {
        // Update the date in-case the header, if it has changed or has been asynchronously loaded.
        // This is likely for an MMS msg where the pdu is asynchronously loaded
        if (!mShowNewMessagesHeader) return;
        TextView newMsgDateTime = (TextView) findViewById(R.id.new_msgs_date_time);
        newMsgDateTime.setVisibility(View.VISIBLE);
        newMsgDateTime.setText(mPrettyTime.format(mMessageItem.mDate));
        newMsgDateTime.setTag(mMessageItem.mDate);
    }

    public TextView getNewMessagessHeaderTimestamp() {
        if (!mShowNewMessagesHeader) return null;
        return (TextView) findViewById(R.id.new_msgs_date_time);
    }

    private void bindCommonMessage(final boolean sameItem) {
        if (mDownloadControls != null) {
            mDownloadControls.setVisibility(View.GONE);
            mBodyTextView.setVisibility(View.VISIBLE);
        }

        // Since the message text should be concatenated with the sender's
        // address(or name), I have to display it here instead of
        // displaying it by the Presenter.
        mBodyTextView.setTransformationMethod(HideReturnsTransformationMethod.getInstance());

        boolean haveLoadedPdu = mMessageItem.isSms() || mMessageItem.mSlideshow != null;
        // Here we're avoiding reseting the avatar to the empty avatar when we're rebinding
        // to the same item. This happens when there's a DB change which causes the message item
        // cache in the MessageListAdapter to get cleared. When an mms MessageItem is newly
        // created, it has no info in it except the message id. The info is eventually loaded
        // and bindCommonMessage is called again (see onPduLoaded below). When we haven't loaded
        // the pdu, we don't want to call updateAvatarView because it
        // will set the avatar to the generic avatar then when this method is called again
        // from onPduLoaded, it will reset to the real avatar. This test is to avoid that flash.
        if (!sameItem || haveLoadedPdu) {
            boolean isSelf = Sms.isOutgoingFolder(mMessageItem.mBoxId);
            String addr = isSelf ? null : mMessageItem.mAddress;
            updateAvatarView(addr, isSelf);
        }

        if (haveLoadedPdu) {
            updateNewMessagesHeader();
        }

        // Add SIM sms address above body.
        if (isSimCardMessage()) {
            mSimMessageAddress.setVisibility(VISIBLE);
            SpannableStringBuilder buf = new SpannableStringBuilder();
            if (mMessageItem.mBoxId == Sms.MESSAGE_TYPE_INBOX) {
                buf.append(mContext.getString(R.string.from_label));
            } else {
                buf.append(mContext.getString(R.string.to_address_label));
            }
            buf.append(Contact.get(mMessageItem.mAddress, true).getName());
            mSimMessageAddress.setText(buf);
        }

        // Get and/or lazily set the formatted message from/on the
        // MessageItem.  Because the MessageItem instances come from a
        // cache (currently of size ~50), the hit rate on avoiding the
        // expensive formatMessage() call is very high.
        CharSequence formattedMessage = mMessageItem.getCachedFormattedMessage();
        if (formattedMessage == null) {
            formattedMessage = formatMessage(mMessageItem,
                                             mMessageItem.mBody,
                                             mMessageItem.mSubject,
                                             mMessageItem.mHighlight,
                                             mMessageItem.mTextContentType);
            mMessageItem.setCachedFormattedMessage(formattedMessage);
        }
        if (!sameItem || haveLoadedPdu) {
            mBodyTextView.setText(formattedMessage);
        }
        updateSimIndicatorView(mMessageItem.mSubId);

        // If we're in the process of sending a message (i.e. pending), then we show a "SENDING..."
        // string in place of the timestamp.
        if (!sameItem || haveLoadedPdu) {
            boolean isCountingDown = mMessageItem.getCountDown() > 0 &&
                MessagingPreferenceActivity.getMessageSendDelayDuration(mContext) > 0;
            int sendingTextResId = isCountingDown
                    ? R.string.sent_countdown : R.string.sending_message;
            mMessageStatus.setText(buildTimestampLine(mMessageItem.isSending() ?
                    mContext.getResources().getString(sendingTextResId) :
                    mPrettyTime.format(mMessageItem.mDate)));
            mMessageStatus.setTag((Long) mMessageItem.mDate);
        }
        if (mMessageItem.isSms() ||
                ((mMessageItem.mAttachmentType == WorkingMessage.TEXT || mMessageItem.mAttachmentType
                        == MessageItem.ATTACHMENT_TYPE_NOT_LOADED) && haveLoadedPdu)) {
            showMmsView(false, false);
            mMessageItem.setOnPduLoaded(null);
        } else {
            showMmsView(true, sameItem);
            if (DEBUG) {
                Log.v(TAG, "bindCommonMessage for item: " + mPosition + " " +
                        mMessageItem.toString() +
                        " mMessageItem.mAttachmentType: " + mMessageItem.mAttachmentType +
                        " sameItem: " + sameItem);
            }
            if (mMessageItem.mSlideshow == null) {
                final int mCurrentAttachmentType = mMessageItem.mAttachmentType;
                mMessageItem.setOnPduLoaded(new MessageItem.PduLoadedCallback() {
                    public void onPduLoaded(MessageItem messageItem) {
                        if (DEBUG) {
                            Log.v(TAG, "PduLoadedCallback in MessageListItem for item: " + mPosition +
                                    " " + (mMessageItem == null ? "NULL" : mMessageItem.toString()) +
                                    " passed in item: " +
                                    (messageItem == null ? "NULL" : messageItem.toString()));
                        }
                        if (messageItem != null && mMessageItem != null &&
                                messageItem.getMessageId() == mMessageItem.getMessageId()) {
                            mMessageItem.setCachedFormattedMessage(null);
                            bindCommonMessage(
                                    mCurrentAttachmentType == messageItem.mAttachmentType);
                        }
                    }
                });
            } else {
                mPresenter = (SlideShowPresenter) mMessageItem.getSlideshow().getPresenter();
                mPresenter.presentSlides(this, mMmsView);
            }
        }
        drawRightStatusIndicator(mMessageItem);
        requestLayout();
    }

    public void setAvatarVisbility(int visibility) {
        mAvatar.setVisibility(visibility);
    }

    public void setBubbleArrowheadVisibility(int visibility) {
        mMessageBubbleArrowhead.setVisibility(visibility);
    }

    public boolean isBubbleArrowheadVisibile() {
        return mMessageBubbleArrowhead.getVisibility() == View.VISIBLE;
    }

    public void toggleTimeStamp(boolean showTimeStamp) {
        mMessageStatus.setVisibility(showTimeStamp ? View.VISIBLE : View.GONE);
    }

    public void toggleTimeStamp() {
        boolean isVisible = mMessageStatus.getVisibility() == View.VISIBLE;
        toggleTimeStamp(isVisible ? false : true);
    }

    public boolean hasCustomSpans() {
        return mBodyTextView.getUrls().length > 0;
    }

    public void showMmsView(boolean visible, boolean sameItem) {
        if (mMmsView == null) {
            mMmsView = (ViewGroup) findViewById(R.id.message_attachment_root);
            // if mMmsView is still null here, that mean the mms section hasn't been inflated

            if (visible && mMmsView == null) {
                //inflate the mms view_stub
                View mmsStub = findViewById(R.id.message_attachment_stub);
                mmsStub.setVisibility(View.VISIBLE);
                mMmsView = (ViewGroup) findViewById(R.id.message_attachment_root);
            }
        }
        if (mMmsView != null) {
            mMmsView.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (!sameItem) {
                int totalHeight = mMessageInfoCache.getCachedTotalHeight(
                        mContext, getMessageItem().getMessageId());
                ViewGroup.LayoutParams params = mMmsView.getLayoutParams();
                if (totalHeight != MessageInfoCache.HEIGHT_UNDETERMINED) {
                    params.height = totalHeight;
                    params.height += mMmsView.getPaddingTop() +
                            mMmsView.getPaddingBottom();
                } else {
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                }
                mMmsView.setLayoutParams(params);
            }
        }
        if (visible) {
            boolean hasSubject = !TextUtils.isEmpty(mMessageItem.mSubject);
            mMessageBlock.setVisibility(hasSubject ? View.VISIBLE : View.GONE);
        } else {
            mMessageBlock.setVisibility(View.VISIBLE);
        }
    }

    private void inflateDownloadControls() {
        if (mDownloadControls == null) {
            //inflate the download controls
            findViewById(R.id.mms_downloading_view_stub).setVisibility(VISIBLE);
            mDownloadControls = findViewById(R.id.mms_download_controls);
            mDownloadIcon = mDownloadControls.findViewById(R.id.download_icon);
            mDownloadTitle = (TextView) mDownloadControls.findViewById(R.id.download_title);
            mDownloadSubTitle = (TextView) mDownloadControls.findViewById(R.id.download_subtitle);
            mDownloadProgressBar = (ProgressBar) mDownloadControls.findViewById(R.id.download_progress);
            if (getResources().getBoolean(R.bool.config_mms_cancelable)) {
//                mDownloading = (Button) findViewById(R.id.btn_cancel_download);
//                mDownloading.setOnClickListener(new OnClickListener() {
//                    @Override
//                    public void onClick(View v) {
//                        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
//                        builder.setTitle(R.string.cancel_downloading)
//                                .setIconAttribute(android.R.attr.alertDialogIcon)
//                                .setCancelable(true)
//                                .setPositiveButton(R.string.yes, mCancelLinstener)
//                                .setNegativeButton(R.string.no, null)
//                                .setMessage(R.string.confirm_cancel_downloading)
//                                .show();
//                    }
//                });
            } else {
//                mDownloading = (TextView) findViewById(R.id.label_downloading);
            }
        }
    }


    private LineHeightSpan mSpan = new LineHeightSpan() {
        @Override
        public void chooseHeight(CharSequence text, int start,
                int end, int spanstartv, int v, FontMetricsInt fm) {
            fm.ascent -= 10;
        }
    };

    TextAppearanceSpan mTextSmallSpan =
        new TextAppearanceSpan(mContext, android.R.style.TextAppearance_Small);

    ForegroundColorSpan mColorSpan = null;  // set in ctor

    private CharSequence formatMessage(MessageItem msgItem, String body,
                                       String subject, Pattern highlight,
                                       String contentType) {
        SpannableStringBuilder buf = new SpannableStringBuilder();

        boolean hasSubject = !TextUtils.isEmpty(subject);
        SmileyParser parser = SmileyParser.getInstance();
        if (hasSubject) {
            CharSequence smilizedSubject = parser.addSmileySpans(subject);
            // Can't use the normal getString() with extra arguments for string replacement
            // because it doesn't preserve the SpannableText returned by addSmileySpans.
            // We have to manually replace the %s with our text.
            buf.append(TextUtils.replace(mContext.getResources().getString(R.string.inline_subject),
                    new String[]{"%s"}, new CharSequence[]{smilizedSubject}));
        }

        if (!TextUtils.isEmpty(body)) {
            // Converts html to spannable if ContentType is "text/html".
            if (contentType != null && ContentType.TEXT_HTML.equals(contentType)) {
                buf.append("\n");
                buf.append(Html.fromHtml(body));
            } else {
                if (hasSubject) {
                    buf.append(" - ");
                    int currentColor = mBodyTextView.getCurrentTextColor();
                    currentColor = MessageUtils.getColorAtAlpha(currentColor, 0.50f);
                    buf.setSpan(new ForegroundColorSpan(currentColor), 0, buf.length(), 0);
                }
                buf.append(parser.addSmileySpans(body));
            }
        }

        if (highlight != null) {
            Matcher m = highlight.matcher(buf.toString());
            while (m.find()) {
                buf.setSpan(new StyleSpan(Typeface.BOLD), m.start(), m.end(), 0);
            }
        }
        return buf;
    }

    private boolean isSimCardMessage() {
        return mContext instanceof ManageSimMessages;
    }

    public void setManageSelectMode(int manageMode) {
        mManageMode = manageMode;
    }

    // OnClick Listener for the playback button
    @Override
    public void onClick(View v) {
        sendMessage(mMessageItem, MSG_LIST_PLAY);
    }

    private void sendMessage(MessageItem messageItem, int message) {
        if (mHandler != null) {
            Message msg = Message.obtain(mHandler, message);
            msg.obj = messageItem;
            msg.sendToTarget(); // See ComposeMessageActivity.mMessageListItemHandler.handleMessage
        }
    }

    public void onMessageListItemClick() {
        if (mMessageItem != null && mMessageItem.isSending() && mMessageItem.isSms()) {
            SmsReceiverService.cancelSendingMessage(mMessageItem.mMessageUri);
            return;
        }
        // If the message is a failed one, clicking it should reload it in the compose view,
        // regardless of whether it has links in it
        if (mMessageItem != null &&
                mMessageItem.isOutgoingMessage() &&
                mMessageItem.isFailedMessage() ) {

            // Assuming the current message is a failed one, reload it into the compose view so
            // the user can resend it.
            sendMessage(mMessageItem, MSG_LIST_EDIT);
            return;
        }

        // Check for links. If none, do nothing; if 1, open it; if >1, ask user to pick one
        final URLSpan[] spans = mBodyTextView.getUrls();
        if (spans.length != 0) {
            MessageUtils.onMessageContentClick(mContext, mBodyTextView);
        } else {
            toggleTimeStamp();
        }
    }

    private void drawRightStatusIndicator(MessageItem msgItem) {
        // Locked icon
        if (msgItem.mLocked) {
            mLockedIndicator.setImageResource(R.drawable.ic_lock_message_sms);
            mLockedIndicator.setVisibility(View.VISIBLE);
        } else {
            mLockedIndicator.setVisibility(View.GONE);
        }

        // Delivery icon - we can show a failed icon for both sms and mms, but for an actual
        // delivery, we only show the icon for sms. We don't have the information here in mms to
        // know whether the message has been delivered. For mms, msgItem.mDeliveryStatus set
        // to MessageItem.DeliveryStatus.RECEIVED simply means the setting requesting a
        // delivery report was turned on when the message was sent. Yes, it's confusing!
        if ((msgItem.isOutgoingMessage() && msgItem.isFailedMessage()) ||
                msgItem.mDeliveryStatus == MessageItem.DeliveryStatus.FAILED) {
            mDeliveredIndicator.setVisibility(View.VISIBLE);
            mMessageStatus.setText(mContext.getResources().getString(
                    R.string.tap_to_retry_sending_msg));
            mMessageStatus.setTag((Long) 0L);
        } else if (msgItem.isSms() &&
                msgItem.mDeliveryStatus == MessageItem.DeliveryStatus.RECEIVED) {
            mDeliveredIndicator.setImageResource(R.drawable.ic_sms_mms_delivered);
            mDeliveredIndicator.setVisibility(View.VISIBLE);
        } else {
            mDeliveredIndicator.setVisibility(View.GONE);
        }

        // Message details icon - this icon is shown both for sms and mms messages. For mms,
        // we show the icon if the read report or delivery report setting was set when the
        // message was sent. Showing the icon tells the user there's more information
        // by selecting the "View report" menu.
        if (msgItem.mDeliveryStatus == MessageItem.DeliveryStatus.INFO
                || (msgItem.isMms() && !msgItem.isSending() &&
                        msgItem.mDeliveryStatus == MessageItem.DeliveryStatus.PENDING)) {
            mDetailsIndicator.setImageResource(R.drawable.ic_sms_mms_details);
            mDetailsIndicator.setVisibility(View.VISIBLE);
        } else if (msgItem.isMms() && !msgItem.isSending() &&
                msgItem.mDeliveryStatus == MessageItem.DeliveryStatus.RECEIVED) {
            mDetailsIndicator.setImageResource(R.drawable.ic_sms_mms_delivered);
            mDetailsIndicator.setVisibility(View.VISIBLE);
        } else if (msgItem.mReadReport) {
            mDetailsIndicator.setImageResource(R.drawable.ic_sms_mms_details);
            mDetailsIndicator.setVisibility(View.VISIBLE);
        } else {
            mDetailsIndicator.setVisibility(View.GONE);
        }
    }

    public void updateDelayCountDown() {
        if (mMessageItem.isSms() && mMessageItem.getCountDown() > 0 && mMessageItem.isSending()) {
            String content = mContext.getResources().getQuantityString(
                R.plurals.remaining_delay_time,
                mMessageItem.getCountDown(), mMessageItem.getCountDown());
            Spanned spanned = Html.fromHtml(buildTimestampLine(content));
            mMessageStatus.setText(spanned);
            mMessageStatus.setTag((Long) 0L);
        } else {
            mMessageStatus.setText(buildTimestampLine(mMessageItem.isSending()
                    ? mContext.getResources().getString(R.string.sending_message)
                    : mPrettyTime.format(mMessageItem.mDate)));
            mMessageStatus.setTag((Long) mMessageItem.mDate);
        }
    }

    @Override
    public boolean isChecked() {
        return mIsCheck;
    }

    @Override
    public void setChecked(boolean checked) {
        mIsCheck = checked;
        mMessageBlock.setSelected(checked);
        mMessageBubbleArrowhead.setSelected(checked);
        if (mMmsView != null) {
            mMmsView.setSelected(checked);
        }
    }

    @Override
    public void toggle() {
    }

    protected void customSIMSmsView() {
        if (isSimCardMessage()) {
            // Hide delivery indicator for SIM message
            mDeliveredIndicator.setVisibility(GONE);
            // Hide date view because SIM message does not contain sent date.
            if (mMessageItem.isOutgoingMessage() || mMessageItem.mBoxId == Sms.MESSAGE_TYPE_SENT) {
                mMessageStatus.setVisibility(View.GONE);
            }
        }
    }

    public void setMessageCache(MessageInfoCache messageCache) {
        mMessageInfoCache = messageCache;
    }

    public int getAccentColor() {
        return mAccentColor;
    }

    public boolean isIncoming() {
        return mIsIncoming;
    }

    public void onItemLongClick() {
        mListView.setItemChecked(mPosition, true);
    }

    public MessageInfoCache getMessageInfoCache() {
        return mMessageInfoCache;
    }

    public boolean isInActionMode() {
        return mListView.isInActionMode();
    }

    public MessageListAdapter.Metadata recycleMeatada() {
        MessageListAdapter.Metadata metadata = mMetadata;
        mMetadata = null;
        return metadata;
    }
}
