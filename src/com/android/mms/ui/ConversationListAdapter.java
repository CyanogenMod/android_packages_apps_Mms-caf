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
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.CursorAdapter;

import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.util.BlacklistUtils;
import com.android.mms.LogTag;
import com.android.mms.MmsApp;
import com.android.mms.R;
import com.android.mms.blacklist.CallBlacklistHelper;
import com.android.mms.data.Contact;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;

import com.android.mms.util.AddressUtils;
import com.android.mms.util.IntentUtils;
import com.android.mms.util.PrettyTime;
import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;
import se.emilsjolander.stickylistheaders.WrapperView;

import com.android.mms.widget.ContactBadgeWithAttribution;
import com.cyanogen.lookup.phonenumber.response.LookupResponse;

import java.lang.ref.WeakReference;

/**
 * The back-end data adapter for ConversationList.
 */
//TODO: This should be public class ConversationListAdapter extends ArrayAdapter<Conversation>
public class ConversationListAdapter extends CursorAdapter implements AbsListView
        .RecyclerListener, StickyListHeadersAdapter, MmsApp.PhoneNumberLookupListener {

    private static final String TAG = LogTag.TAG;
    private static final boolean LOCAL_LOGV = false;
    private static final int DATE = 1;

    private final Context mContext;

    private PrettyTime mPrettyTime = new PrettyTime();
    private final LayoutInflater mFactory;
    private OnContentChangedListener mOnContentChangedListener;

    private boolean mIsInActionMode = false;

    public ConversationListAdapter(Context context, Cursor cursor) {
        super(context, cursor, false /* auto-requery */);
        mContext = context;
        mFactory = LayoutInflater.from(context);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (!(view instanceof ConversationListItem)) {
            Log.e(TAG, "Unexpected bound view: " + view);
            return;
        }

        ConversationListItem headerView = (ConversationListItem) view;
        Conversation conv = Conversation.from(context, cursor);

        FetchBlacklistInfo blacklistInfo = (FetchBlacklistInfo) view.getTag();
        if (blacklistInfo != null) {
            blacklistInfo.cancel(true);
        }
        blacklistInfo = new FetchBlacklistInfo(headerView, conv.getRecipients());
        view.setTag(blacklistInfo);
        blacklistInfo.execute();

        // if unknown contact, request info
        LookupResponse lookupResponse = checkForUnknownContact(conv);
        headerView.bind(context, conv, lookupResponse, mIsInActionMode);
    }

    private static class FetchBlacklistInfo extends AsyncTask<Void, Void, Boolean> {
        private final WeakReference<ConversationListItem> mViewReference;
        private final ContactList mRecipientList;
        private final CallBlacklistHelper mHelper;

        FetchBlacklistInfo(ConversationListItem view, ContactList recipientList) {
            mViewReference = new WeakReference<ConversationListItem>(view);
            mRecipientList = recipientList;
            mHelper = new CallBlacklistHelper(view.getContext());
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            View view = mViewReference.get();
            boolean result = true;
            if (view != null) {
                for (Contact contact : mRecipientList) {
                    if (!mHelper.isBlacklisted(contact.getNumber())) {
                        result = false;
                        break;
                    }
                }
            }
            return result;
        }

        @Override
        protected void onPostExecute(Boolean isBlocked) {
            if (isCancelled()) {
                return;
            }
            ConversationListItem view = mViewReference.get();
            if (view != null) {
                FetchBlacklistInfo fetchBlacklistInfo = (FetchBlacklistInfo) view.getTag();
                if (fetchBlacklistInfo == this) {
                    view.updateBlockedState(isBlocked);
                }
            }
        }
    }

    private LookupResponse checkForUnknownContact(Conversation conv) {
        ContactList recipients = conv.getRecipients();
        if (recipients.size() == 1 && !recipients.get(0).existsInDatabase()) {
            String number = AddressUtils.normalizePhoneNumber(recipients.get(0).getNumber());
            LookupResponse lookupResponse = MmsApp.getApplication().
                    getPhoneNumberLookupResponse(number);
            if (lookupResponse != null) {
                return lookupResponse;
            } else {
                // request info for this contact
                MmsApp.getApplication().lookupInfoForPhoneNumber(number);
            }
        }
        return null;
    }

    public void onMovedToScrapHeap(View view) {
        WrapperView wrappedHeader = (WrapperView) view;
        ConversationListItem headerView = (ConversationListItem) wrappedHeader.getItem();
        headerView.unbind();
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        if (LOCAL_LOGV) Log.v(TAG, "inflating new view");
        return mFactory.inflate(R.layout.conversation_list_item, parent, false);
    }

    @Override
    public void onNewInfoAvailable() {
        notifyDataSetChanged();
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

    @Override
    public long getHeaderId(int position) {
        // peek to position passed in to get date
        Cursor cursor = getCursor();
        int curPos = cursor.getPosition();
        cursor.moveToPosition(position);
        long dateTime = cursor.getLong(DATE);
        cursor.moveToPosition(curPos);

        return mPrettyTime.getWeekBucket(dateTime).ordinal();
    }

    @Override
    public View getHeaderView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater =
                    (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.conversation_list_header, parent, false);
        }

        // get week for this position
        int headerId = (int) getHeaderId(position);
        PrettyTime.WeekBucket week = PrettyTime.WeekBucket.values()[headerId];

        // convert to string and bind
        TextView headerText = (TextView) convertView.findViewById(R.id.headerText);
        String headerStr = new PrettyTime().formatWeekBucket(mContext, week);
        if (headerText != null) {
            headerText.setText(headerStr);
        }

        return convertView;
    }

    public void setIsInActionMode(boolean isInActionMode) {
        mIsInActionMode = isInActionMode;
        notifyDataSetChanged();
    }
}
