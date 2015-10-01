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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TextAppearanceSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Checkable;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.model.ContactBuilder;
import com.android.mms.LogTag;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.Conversation;
import com.android.mms.util.ImageUtils;
import com.android.mms.util.IntentUtils;
import com.android.mms.util.SmileyParser;
import com.android.mms.widget.ContactBadgeWithAttribution;
import com.cyanogen.lookup.phonenumber.response.LookupResponse;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class manages the view for given conversation.
 */
public class ConversationListItem extends RelativeLayout implements Contact.UpdateListener,
            Checkable {
    private static final String TAG = LogTag.TAG;
    private static final boolean DEBUG = false;

    private TextView mSubjectView;
    private TextView mFromView;
    private TextView mDateView;
    private TextView mUnreadCount;
    private View mAttachmentView;
    private View mErrorIndicator;
    private ContactBadgeWithAttribution mAvatarView;
    private View mInfoRow, mInfoRoot;
    private TextView mFromCount;
    private View mBlock;

    static RoundedBitmapDrawable sDefaultContactImage;

    // For posting UI update Runnables from other threads:
    private Handler mHandler = new Handler();

    private Conversation mConversation;

    public static final StyleSpan STYLE_BOLD = new StyleSpan(Typeface.BOLD);
    public static final Pattern RTL_CHARACTERS =
        Pattern.compile("[\u0600-\u06FF\u0750-\u077F\u0590-\u05FF\uFE70-\uFEFF]");

    private static ForegroundColorSpan sUnreadColorSpan;
    private static ForegroundColorSpan sBlockColorSpan;
    private boolean mBlocked;

    private ContactBadgeWithAttribution.ContactBadgeClickListener mContactBadgeClickListener =
            new ContactBadgeWithAttribution.ContactBadgeClickListener() {
                @Override
                public boolean handleContactBadgeClick(View v, Uri contactUri) {
                    if (contactUri != null) {
                        try {
                            if (mContext != null) {
                                Intent intent =
                                        IntentUtils.getQuickContactForLookupIntent(mContext,
                                                v, contactUri);
                                mContext.startActivity(intent);
                                return true;
                            }
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(mContext,
                                    com.android.internal.R.string.quick_contacts_not_available,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                    return false;
                }
            };

    public ConversationListItem(Context context) {
        super(context);
    }

    public ConversationListItem(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (sDefaultContactImage == null) {
            Resources res = context.getResources();
            Bitmap defaultImage = BitmapFactory.decodeResource(res, R.drawable.ic_contact_picture);
            sDefaultContactImage = RoundedBitmapDrawableFactory.create(res, defaultImage);
            sDefaultContactImage.setAntiAlias(true);
            sDefaultContactImage.setCornerRadius(
                    Math.max(defaultImage.getWidth() / 2, defaultImage.getHeight() / 2));
        }
        int highlight_color = getResources().getColor(R.color.mms_next_theme_color_check);
        sUnreadColorSpan = new ForegroundColorSpan(highlight_color);
        sBlockColorSpan = new ForegroundColorSpan(Color.RED);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mUnreadCount = (TextView) findViewById(R.id.unread_count);
        mFromView = (TextView) findViewById(R.id.from);
        mFromCount = (TextView) findViewById(R.id.from_count);
        mSubjectView = (TextView) findViewById(R.id.subject);
        mInfoRow = findViewById(R.id.info_row);
        mInfoRoot = findViewById(R.id.info_root);
        mDateView = (TextView) findViewById(R.id.date);
        mAttachmentView = findViewById(R.id.attachment);
        mErrorIndicator = findViewById(R.id.error);
        mAvatarView = (ContactBadgeWithAttribution) findViewById(R.id.avatar);
        mAvatarView.setOverlay(null);
        mBlock = findViewById(R.id.block);
    }

    public Conversation getConversation() {
        return mConversation;
    }

    private CharSequence formatMessage(String recipientNames) {
        final int color = android.R.styleable.Theme_textColorSecondary;
        String from = recipientNames;
        if (MessageUtils.isWapPushNumber(from)) {
            String[] mAddresses = from.split(":");
            from = mAddresses[mContext.getResources().getInteger(
                    R.integer.wap_push_address_index)];
        }

        /**
         * Add boolean to know that the "from" haven't the Arabic and '+'.
         * Make sure the "from" display normally for RTL.
         */
        boolean isEnName = false;
        boolean isLayoutRtl = (TextUtils.getLayoutDirectionFromLocale(Locale.getDefault())
                == View.LAYOUT_DIRECTION_RTL);
        if (isLayoutRtl && from != null) {
            if (from.length() >= 1) {
                Matcher matcher = RTL_CHARACTERS.matcher(from);
                isEnName = !matcher.find();
                if (isEnName && from.charAt(0) != '\u202D') {
                    from = "\u202D" + from + "\u202C";
                }
            }
        }

        SpannableStringBuilder buf = new SpannableStringBuilder(from);
        if (!mBlocked && mConversation.hasDraft()) {
            if (isLayoutRtl && isEnName) {
                int before = buf.length();
                buf.insert(1,'\u202E'
                        + mContext.getResources().getString(R.string.draft_separator)
                        + '\u202C');
                buf.setSpan(new ForegroundColorSpan(
                        mContext.getResources().getColor(R.drawable.text_color_black)),
                        1, buf.length() - before + 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                before = buf.length();
                int size;
                buf.insert(1, mContext.getResources().getString(R.string.has_draft));
                size = android.R.style.TextAppearance_Small;
                buf.setSpan(new TextAppearanceSpan(mContext, size), 1,
                        buf.length() - before + 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                buf.setSpan(new ForegroundColorSpan(
                        mContext.getResources().getColor(R.drawable.text_color_red)),
                        1, buf.length() - before + 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            } else {
                buf.append(mContext.getResources().getString(R.string.draft_separator));
                int before = buf.length();
                int size;
                buf.append(mContext.getResources().getString(R.string.has_draft));
                size = android.R.style.TextAppearance_Small;
                buf.setSpan(new TextAppearanceSpan(mContext, size, color), before,
                        buf.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                buf.setSpan(new ForegroundColorSpan(
                        mContext.getResources().getColor(R.drawable.text_color_red)),
                        before, buf.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
              }
        }
        if (!mBlocked && mConversation.hasUnreadMessages()) {
            buf.setSpan(STYLE_BOLD, 0, buf.length(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            buf.setSpan(sUnreadColorSpan, 0, buf.length(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        if (mBlocked) {
            buf.setSpan(sBlockColorSpan, 0, buf.length(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        return buf;
    }

    private void updateAvatarView(LookupResponse lookupResponse) {
        if (lookupResponse != null) {
            // if url exists load into image
            if (!TextUtils.isEmpty(lookupResponse.mPhotoUrl)) {
                ImageUtils.loadBitampFromUrl(getContext(), lookupResponse.mPhotoUrl, mAvatarView);
            }
            ContactBuilder builder =
                    new ContactBuilder(ContactBuilder.REVERSE_LOOKUP, lookupResponse.mNumber,
                            lookupResponse.mNumber);
            builder.setName(ContactBuilder.Name.createDisplayName(lookupResponse.mName));
            builder.addPhoneNumber(
                    ContactBuilder.PhoneNumber.createMainNumber(lookupResponse.mNumber));
            builder.addAddress(ContactBuilder.Address.createFormattedHome(lookupResponse.mAddress));
            builder.setPhotoUrl(lookupResponse.mPhotoUrl);
            builder.setSpamCount(lookupResponse.mSpamCount);
            builder.setInfoProviderName(lookupResponse.mProviderName);
            com.android.contacts.common.model.Contact contact = builder.build();
            mAvatarView.setContactUri(contact.getLookupUri());
            mAvatarView.setContactPhone(lookupResponse.mNumber);
            return;
        }
        if (mConversation.getRecipients().size() == 1) {
            Contact contact = mConversation.getRecipients().get(0);
            contact.bindAvatar(mAvatarView);

            if (contact.existsInDatabase()) {
                mAvatarView.assignContactUri(contact.getUri());
            } else if (MessageUtils.isWapPushNumber(contact.getNumber())) {
                mAvatarView.assignContactFromPhone(
                        MessageUtils.getWapPushNumber(contact.getNumber()), true);
            } else {
                mAvatarView.assignContactFromPhone(contact.getNumber(), true);
            }
        } else {
            // TODO get a multiple recipients asset (or do something else)
            mAvatarView.setImageDrawable(sDefaultContactImage);
            mAvatarView.assignContactUri(null);
        }
        mAvatarView.setVisibility(View.VISIBLE);
    }

    private void updateFromView() {
        String recipientNames = mConversation.getRecipients().formatNames(", ");
        mFromView.setText(formatMessage(recipientNames));
        updateFromCount();
        updateAvatarView(null);
    }

    private void updateFromCount() {
        mFromView.post(new Runnable() {
            @Override
            public void run() {
                Layout layout = mFromView.getLayout();
                CharSequence fromText = mFromView.getText();
                int ellipsisCount = layout.getEllipsisCount(0);
                if (ellipsisCount == 0) {
                    mFromCount.setVisibility(View.GONE);
                } else {
                    String displayed =
                            fromText.toString().substring(0, fromText.length() - ellipsisCount);
                    int recipientsPending =
                            fromText.toString().split(",").length - displayed.split(",").length;
                    if (recipientsPending > 0) {
                        mFromCount.setVisibility(View.VISIBLE);
                        String unreadLabel = "+";
                        unreadLabel += String.valueOf(Math.min(recipientsPending, 10));
                        mFromCount.setText(unreadLabel);
                    }
                }
            }
        });
    }

    public void onUpdate(Contact updated) {
        if (Log.isLoggable(LogTag.CONTACT, Log.DEBUG)) {
            Log.v(TAG, "onUpdate: " + this + " contact: " + updated);
        }
        mHandler.post(new Runnable() {
            public void run() {
                updateFromView();
            }
        });
    }

    private void updateSubjectView() {
        // Subject
        SmileyParser parser = SmileyParser.getInstance();
        final String snippet = mConversation.getSnippet();
        if (mBlocked) {
            SpannableStringBuilder buf = new SpannableStringBuilder(parser.addSmileySpans(snippet));
            buf.setSpan(sBlockColorSpan, 0, buf.length(),
                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            mSubjectView.setText(buf);
        } else {
            mSubjectView.setText(parser.addSmileySpans(snippet));
        }
    }

    public final void bind(Context context, final Conversation conversation,
                           LookupResponse lookupResponse, boolean inActionMode) {
        //if (DEBUG) Log.v(TAG, "bind()");
        boolean sameItem = mConversation != null
                && mConversation.getThreadId() == conversation.getThreadId();

        mConversation = conversation;

        boolean hasAttachment = conversation.hasAttachment();
        mAttachmentView.setVisibility(hasAttachment ? VISIBLE : GONE);

        // Date
        mDateView.setText(MessageUtils.formatTimeStampString(context, conversation.getDate()));

        String recipientNames;
        if (lookupResponse != null && !TextUtils.isEmpty(lookupResponse.mName)) {
            recipientNames = lookupResponse.mName;
        } else {
            recipientNames = mConversation.getRecipients().formatNames(", ");
        }
        // From.
        mFromView.setText(formatMessage(recipientNames));
        updateFromCount();

        if (Log.isLoggable(LogTag.CONTACT, Log.DEBUG)) {
            Log.v(TAG, "bind: contacts.addListeners " + this);
        }
        Contact.addListener(this);

        // Subject
        updateSubjectView();

        // Transmission error indicator.
        mErrorIndicator.setVisibility(conversation.hasError() ? VISIBLE : GONE);

        updateAvatarView(lookupResponse);
        mAvatarView.setChecked(isChecked(), sameItem);
        mAvatarView.setClickable(!isChecked() && !inActionMode);
        mAvatarView.setContactBadgeClickListener(mContactBadgeClickListener);
        if (lookupResponse != null) {
            // add attribution to avatar
            mAvatarView.setAttributionDrawable(lookupResponse.mAttributionLogo);
        } else {
            mAvatarView.setAttributionDrawable(null);
        }

        if (mConversation.hasUnreadMessages() && mConversation.getUnreadMessageCount() > 0) {
            int unreadCount = mConversation.getUnreadMessageCount();
            String unreadLabel = String.valueOf(Math.min(unreadCount, 10));
            if (unreadCount > 10) {
                unreadLabel += "+";
            }
            mUnreadCount.setText(unreadLabel);
            mUnreadCount.setVisibility(View.VISIBLE);
        } else {
            mUnreadCount.setVisibility(View.GONE);
        }

        updateBlockVisibility();
    }

    private void updateBlockVisibility() {
        if (mBlocked) {
            mBlock.setVisibility(View.VISIBLE);
        } else {
            mBlock.setVisibility(View.GONE);
        }
    }

    public final void unbind() {
        if (Log.isLoggable(LogTag.CONTACT, Log.DEBUG)) {
            Log.v(TAG, "unbind: contacts.removeListeners " + this);
        }
        // Unregister contact update callbacks.
        Contact.removeListener(this);
    }

    @Override
    public void setChecked(boolean checked) {
        mConversation.setIsChecked(checked);
        mAvatarView.setChecked(isChecked(), true);
    }

    @Override
    public boolean isChecked() {
        return mConversation.isChecked();
    }

    @Override
    public void toggle() {
        mConversation.setIsChecked(!mConversation.isChecked());
    }

    public void updateBlockedState(Boolean isBlocked) {
        if (mBlocked != isBlocked) {
            mBlocked = isBlocked;
            updateFromView();
            updateSubjectView();
            updateBlockVisibility();
        }
    }

    public boolean isBlocked() {
        return mBlocked;
    }

    public void setContactBadgeClickListener(
            ContactBadgeWithAttribution.ContactBadgeClickListener contactBadgeClickListener) {
        if (mAvatarView != null) {
            mAvatarView.setContactBadgeClickListener(contactBadgeClickListener);
        }
    }
}
