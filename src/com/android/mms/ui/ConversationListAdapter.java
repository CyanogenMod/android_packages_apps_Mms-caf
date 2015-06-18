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

import android.content.Context;
import android.database.Cursor;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.CursorAdapter;

import com.android.mms.LogTag;
import com.android.mms.MmsApp;
import com.android.mms.R;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;
import com.cyanogen.lookup.phonenumber.response.LookupResponse;

/**
 * The back-end data adapter for ConversationList.
 */
// TODO: This should be public class ConversationListAdapter extends ArrayAdapter<Conversation>
public class ConversationListAdapter extends CursorAdapter implements AbsListView.RecyclerListener,
        MmsApp.PhoneNumberLookupListener {
    private static final String TAG = LogTag.TAG;
    private static final boolean LOCAL_LOGV = false;

    private final LayoutInflater mFactory;
    private OnContentChangedListener mOnContentChangedListener;

    public ConversationListAdapter(Context context, Cursor cursor) {
        super(context, cursor, false /* auto-requery */);
        mFactory = LayoutInflater.from(context);
        // register w/ Mms app to facilitate contact info lookup
        MmsApp.getApplication().addPhoneNumberLookupListener(this);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (!(view instanceof ConversationListItem)) {
            Log.e(TAG, "Unexpected bound view: " + view);
            return;
        }

        ConversationListItem headerView = (ConversationListItem) view;
        Conversation conv = Conversation.from(context, cursor);
        headerView.bind(context, conv);

        // check if contact is unknown and request info if applicable
        checkForUnknownContact(conv, headerView);
    }

    private String normalizePhoneNumber(String number) {
        System.out.println("number : " + PhoneNumberUtils.normalizeNumber(number));
        System.out.println("current country iso code : " + MmsApp.getApplication().getCurrentCountryIso());
        return PhoneNumberUtils.formatNumberToE164(PhoneNumberUtils.normalizeNumber(number),
                MmsApp.getApplication().getCurrentCountryIso());
    }

    private void checkForUnknownContact(Conversation conv, ConversationListItem view) {
        // if recipients list has contact not in DB , request cache for info
        ContactList recipients = conv.getRecipients();
        if (recipients.size() == 1 && !recipients.get(0).existsInDatabase()) {
            // String number = normalizePhoneNumber(recipients.get(0).getNumber());
            String number = recipients.get(0).getE164Number();
            System.out.println("requesting info for : " + number);

            LookupResponse lookupResponse =
                    MmsApp.getApplication().getPhoneNumberLookupResonse(number);
            if (lookupResponse != null) {
                view.updateView(lookupResponse);
            } else {
                // request info for this contact
                MmsApp.getApplication().lookupInfoForPhoneNumber(number);
            }
        }
    }

    public void onMovedToScrapHeap(View view) {
        ConversationListItem headerView = (ConversationListItem)view;
        headerView.unbind();
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        if (LOCAL_LOGV) Log.v(TAG, "inflating new view");
        return mFactory.inflate(R.layout.conversation_list_item, parent, false);
    }

    @Override
    public void onNewInfoAvailable() {
        // doing a mass refresh rather than a targeted one
        // TODO : change ^
        notifyDataSetChanged();
    }

    public void destroy() {
        // clean up
        MmsApp.getApplication().removePhoneNumberLookupListener(this);
    }

    public interface OnContentChangedListener {
        void onContentChanged(ConversationListAdapter adapter);
    }

    public void setOnContentChangedListener(OnContentChangedListener l) {
        mOnContentChangedListener = l;
    }

    @Override
    protected void onContentChanged() {
        if (mCursor != null && !mCursor.isClosed()) {
            if (mOnContentChangedListener != null) {
                mOnContentChangedListener.onContentChanged(this);
            }
        }
    }

    public void uncheckAll() {
        int count = getCount();
        for (int i = 0; i < count; i++) {
            Cursor cursor = (Cursor)getItem(i);
            Conversation conv = Conversation.from(mContext, cursor);
            conv.setIsChecked(false);
        }
    }

}
