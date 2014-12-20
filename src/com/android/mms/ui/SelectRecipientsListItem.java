/*
 * Copyright (C) 2013 The CyanogenMod Project
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

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.contacts.common.widget.CheckableQuickContactBadge;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.Group;
import com.android.mms.data.PhoneNumber;

public class SelectRecipientsListItem extends LinearLayout implements Contact.UpdateListener {

    private static final int MSG_UPDATE_AVATAR = 1;

    private static Handler sHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE_AVATAR:
                    SelectRecipientsListItem item = (SelectRecipientsListItem) msg.obj;
                    item.updateAvatarView();
                    break;
            }
        }
    };

    private TextView mSectionHeader;
    private TextView mNameView;
    private TextView mNumberView;
    private TextView mLabelView;
    private CheckableQuickContactBadge mAvatarView;

    private Contact mContact;

    public SelectRecipientsListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mSectionHeader = (TextView) findViewById(R.id.section_header);
        mNameView = (TextView) findViewById(R.id.name);
        mNumberView = (TextView) findViewById(R.id.number);
        mLabelView = (TextView) findViewById(R.id.label);
        mAvatarView = (CheckableQuickContactBadge) findViewById(R.id.avatar);

        mAvatarView.setOverlay(null);
    }

    @Override
    public void onUpdate(Contact updated) {
        if (updated == mContact) {
            sHandler.obtainMessage(MSG_UPDATE_AVATAR, this).sendToTarget();
        }
    }

    private void updateAvatarView() {
        if (mContact == null) {
            // we were unbound in the meantime
            return;
        }

        if (mContact.existsInDatabase()) {
            mAvatarView.assignContactUri(mContact.getUri());
        } else {
            mAvatarView.assignContactFromPhone(mContact.getNumber(), true);
        }

        mContact.bindAvatar(mAvatarView);
        mAvatarView.setVisibility(View.VISIBLE);
    }

    public final void bind(Context context, final PhoneNumber phoneNumber,
            boolean showHeader, boolean isFirst) {
        if (showHeader) {
            String index = phoneNumber.getSectionIndex();
            mSectionHeader.setVisibility(View.VISIBLE);
            mSectionHeader.setText(index != null ? index.toUpperCase() : "");
        } else {
            mSectionHeader.setVisibility(View.INVISIBLE);
        }

        if (isFirst) {
            mNameView.setVisibility(View.VISIBLE);

            if (mContact == null) {
                mContact = Contact.get(phoneNumber.getNumber(), false);
                Contact.addListener(this);
            }
            updateAvatarView();
        } else {
            mAvatarView.setImageDrawable(new ColorDrawable(android.R.color.transparent));
            mNameView.setVisibility(View.GONE);
        }

        String lastNumber = (String) mAvatarView.getTag();
        String newNumber = phoneNumber.getNumber();
        boolean sameItem = lastNumber != null && lastNumber.equals(newNumber);

        mAvatarView.setChecked(phoneNumber.isChecked(), sameItem);
        mAvatarView.setTag(newNumber);
        mAvatarView.setVisibility(View.VISIBLE);

        mNumberView.setText(newNumber);
        mNameView.setText(phoneNumber.getName());
        mLabelView.setText(Phone.getTypeLabel(getResources(),
                phoneNumber.getType(), phoneNumber.getLabel()));
        mLabelView.setVisibility(View.VISIBLE);
    }

    public void unbind() {
        Contact.removeListener(this);
        mContact = null;
    }
}
