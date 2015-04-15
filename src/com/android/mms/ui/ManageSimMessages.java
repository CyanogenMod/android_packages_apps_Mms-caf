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

import static com.android.mms.ui.MessageListAdapter.COLUMN_ID;
import static com.android.mms.ui.MessageListAdapter.COLUMN_MSG_TYPE;
import static com.android.mms.ui.MessageListAdapter.COLUMN_SMS_BODY;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.SpannableString;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.IccCardConstants;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;
import com.android.mms.LogTag;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.transaction.MessagingNotification;

import java.util.ArrayList;

/**
 * Displays a list of the SMS messages stored on the ICC.
 */
public class ManageSimMessages extends Activity
        implements View.OnCreateContextMenuListener {
    private static final String TAG = LogTag.TAG;
    private static final int MENU_COPY_TO_PHONE_MEMORY = 0;
    private static final int MENU_DELETE_FROM_SIM = 1;
    private static final int MENU_VIEW = 2;
    private static final int MENU_REPLY = 3;
    private static final int MENU_FORWARD = 4;
    private static final int MENU_CALL_BACK = 5;
    private static final int MENU_ADD_ADDRESS_TO_CONTACTS = 6;
    private static final int MENU_SEND_EMAIL = 7;
    private static final int OPTION_MENU_DELETE_ALL = 0;
    private static final int OPTION_MENU_SIM_CAPACITY = 1;

    private static final int SHOW_LIST = 0;
    private static final int SHOW_EMPTY = 1;
    private static final int SHOW_BUSY = 2;
    private int mState;
    private int mSlot;
    private int mSubscription;

    private Uri mIccUri;
    private ContentResolver mContentResolver;
    private Cursor mCursor = null;
    private ListView mSimList;
    private TextView mMessage;
    private MessageListAdapter mListAdapter = null;
    private AsyncQueryHandler mQueryHandler = null;
    private boolean mIsDeleteAll = false;
    private boolean mIsQuery = false;

    public static final int SIM_FULL_NOTIFICATION_ID = 234;

    public static final int BATCH_DELETE = 100;
    public static final int TYPE_INBOX = 1;
    public static final String FORWARD_MESSAGE_ACTIVITY_NAME =
            "com.android.mms.ui.ForwardMessageActivity";

    private final ContentObserver simChangeObserver =
            new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfUpdate) {
            refreshMessageList();
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TelephonyIntents.ACTION_SIM_STATE_CHANGED.equals(action)) {
                refreshMessageList();
            }
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        mContentResolver = getContentResolver();
        mQueryHandler = new QueryHandler(mContentResolver, this);
        setContentView(R.layout.sim_list);
        mSimList = (ListView) findViewById(R.id.messages);
        mMessage = (TextView) findViewById(R.id.empty_message);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        registerReceiver(mReceiver, filter);

        init();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);

        init();
    }

    private void init() {
        MessagingNotification.cancelNotification(getApplicationContext(),
                SIM_FULL_NOTIFICATION_ID);
        mSlot = getIntent().getIntExtra(PhoneConstants.PHONE_KEY, MessageUtils.SUB_INVALID);

        mSubscription = MessageUtils.SUB_INVALID;
        int[] subIds = SubscriptionManager.getSubId(mSlot);
        if (subIds != null && subIds.length > 0) {
            mSubscription = subIds[0];
        }

        mIccUri = MessageUtils.getIccUriBySlot(mSlot);
        updateState(SHOW_BUSY);
        startQuery();
    }

    private class QueryHandler extends AsyncQueryHandler {
        private final ManageSimMessages mParent;

        public QueryHandler(
                ContentResolver contentResolver, ManageSimMessages parent) {
            super(contentResolver);
            mParent = parent;
        }

        @Override
        protected void onQueryComplete(
                int token, Object cookie, Cursor cursor) {
            if (mCursor != null) {
                stopManagingCursor(mCursor);
            }
            mCursor = cursor;
            if (mCursor != null) {
                if (!mCursor.moveToFirst()) {
                    // Let user know the SIM is empty
                    updateState(SHOW_EMPTY);
                } else if (mListAdapter == null) {
                    // Note that the MessageListAdapter doesn't support auto-requeries. If we
                    // want to respond to changes we'd need to add a line like:
                    //   mListAdapter.setOnDataSetChangedListener(mDataSetChangedListener);
                    // See ComposeMessageActivity for an example.
                    mListAdapter = new MessageListAdapter(
                            mParent, mCursor, mSimList, false, null);
                    mSimList.setAdapter(mListAdapter);
                    mSimList.setOnCreateContextMenuListener(mParent);
                    mSimList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view,
                                                int position, long id) {
                            if (view != null) {
                                ((MessageListItem) view).onMessageListItemClick();
                            }
                        }
                    });
                    updateState(SHOW_LIST);
                } else {
                    mListAdapter.changeCursor(mCursor);
                    updateState(SHOW_LIST);
                }
                startManagingCursor(mCursor);
            } else {
                // Let user know the SIM is empty
                updateState(SHOW_EMPTY);
            }
            // Show option menu when query complete.
            invalidateOptionsMenu();
            mSimList.setMultiChoiceModeListener(new ModeCallback());
            mSimList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
            mIsQuery = false;
        }
    }

    private void startQuery() {
        try {
            if (mIsQuery) {
                return;
            }
            mIsQuery = true;
            mQueryHandler.startQuery(0, null, mIccUri, null, null, null, null);
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    private void refreshMessageList() {
        updateState(SHOW_BUSY);
        startQuery();
    }

    // Refs to ComposeMessageActivity.java
    private final void addCallAndContactMenuItems(
            ContextMenu menu, Cursor cursor) {
        String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
        String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
        // Add all possible links in the address & message
        StringBuilder textToSpannify = new StringBuilder();
        if (address != null) {
            textToSpannify.append(address + ": ");
        }
        textToSpannify.append(body);

        SpannableString msg = new SpannableString(textToSpannify.toString());
        Linkify.addLinks(msg, Linkify.ALL);
        ArrayList<String> uris =
            MessageUtils.extractUris(msg.getSpans(0, msg.length(), URLSpan.class));

        while (uris.size() > 0) {
            String uriString = uris.remove(0);
            // Remove any dupes so they don't get added to the menu multiple times
            while (uris.contains(uriString)) {
                uris.remove(uriString);
            }

            int sep = uriString.indexOf(":");
            String prefix = null;
            if (sep >= 0) {
                prefix = uriString.substring(0, sep);
                uriString = uriString.substring(sep + 1);
            }
            boolean addToContacts = false;
            if ("mailto".equalsIgnoreCase(prefix))  {
                String sendEmailString = getString(
                        R.string.menu_send_email).replace("%s", uriString);
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("mailto:" + uriString));
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                menu.add(0, MENU_SEND_EMAIL, 0, sendEmailString)
                    .setIntent(intent);
                addToContacts = !haveEmailContact(uriString);
            } else if ("tel".equalsIgnoreCase(prefix)) {
                String callBackString = getString(
                        R.string.menu_call_back).replace("%s", uriString);
                Intent intent = new Intent(Intent.ACTION_CALL,
                        Uri.parse("tel:" + uriString));
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                addToContacts = !isNumberInContacts(uriString);
                if (addToContacts) {
                    menu.add(0, MENU_CALL_BACK, 0, callBackString).setIntent(intent);
                } else {
                    // Get the name by the number if the number has been saved
                    // in Contacts.
                    String name = Contact.get(uriString, false).getName();
                    menu.add(0, MENU_CALL_BACK, 0,
                            getString(R.string.menu_call_back).replace("%s", name)).setIntent(
                            intent);
                }
            }
            if (addToContacts) {
                Intent intent = ConversationList.createAddContactIntent(uriString);
                String addContactString = getString(
                        R.string.menu_add_address_to_contacts).replace("%s", uriString);
                menu.add(0, MENU_ADD_ADDRESS_TO_CONTACTS, 0, addContactString)
                    .setIntent(intent);
            }
        }
    }

    private boolean haveEmailContact(String emailAddress) {
        Cursor cursor = SqliteWrapper.query(this, getContentResolver(),
                Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, Uri.encode(emailAddress)),
                new String[] { Contacts.DISPLAY_NAME }, null, null, null);

        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    String name = cursor.getString(0);
                    if (!TextUtils.isEmpty(name)) {
                        return true;
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return false;
    }

    private boolean isNumberInContacts(String phoneNumber) {
        return Contact.get(phoneNumber, true).existsInDatabase();
    }

    @Override
    public void onResume() {
        super.onResume();
        registerSimChangeObserver();
        // Clean up the notification according to the SIM number.
        MessagingNotification.blockingRemoveIccNotifications(this, mSubscription);
    }

    @Override
    public void onPause() {
        super.onPause();
        mContentResolver.unregisterContentObserver(simChangeObserver);
    }

    @Override
    public void onBackPressed() {
        if (mIsDeleteAll) {
            mIsDeleteAll = false;
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);
        super.onDestroy();
    }

    private void registerSimChangeObserver() {
        mContentResolver.registerContentObserver(
                mIccUri, true, simChangeObserver);
    }

    private void copyToPhoneMemory(Cursor cursor) {
        String address = cursor.getString(
                cursor.getColumnIndexOrThrow("address"));
        String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
        Long date = cursor.getLong(cursor.getColumnIndexOrThrow("date"));
        int subId;
        // the regular ICC_URI does not return a sub_id, so we need to populate it ourselves.
        if (mIccUri.equals(MessageUtils.ICC_URI)) {
            subId = SubscriptionManager.getDefaultDataSubId();
        } else {
            subId = cursor.getInt(cursor.getColumnIndexOrThrow("sub_id"));
        }
        boolean success = true;
        try {
            if (isIncomingMessage(cursor)) {
                Sms.Inbox.addMessage(subId, mContentResolver, address, body, null,
                        date, true /* read */);
            } else {
                Sms.Sent.addMessage(subId, mContentResolver, address, body, null, date);
            }
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
            success = false;
        }
        String toast;
        if (success){
            toast = getString(R.string.copy_to_phone_success);
        } else {
            toast = getString(R.string.copy_to_phone_fail);
        }
        Toast.makeText(getContext(), toast, Toast.LENGTH_SHORT).show();
    }

    private boolean isIncomingMessage(Cursor cursor) {
        int messageStatus = cursor.getInt(
                cursor.getColumnIndexOrThrow("status"));

        return (messageStatus == SmsManager.STATUS_ON_ICC_READ) ||
               (messageStatus == SmsManager.STATUS_ON_ICC_UNREAD);
    }

    private void deleteFromSim(Cursor cursor) {
        String messageIndexString =
                cursor.getString(cursor.getColumnIndexOrThrow("index_on_icc"));
        Uri simUri = mIccUri.buildUpon().appendPath(messageIndexString).build();

        SqliteWrapper.delete(this, mContentResolver, simUri, null, null);
    }

    private void deleteAllFromSim() {
        mIsDeleteAll = true;
        Cursor cursor = (Cursor) mListAdapter.getCursor();

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                mContentResolver.unregisterContentObserver(simChangeObserver);
                int count = cursor.getCount();

                for (int i = 0; i < count; ++i) {
                    if (!mIsDeleteAll || cursor.isClosed()) {
                        break;
                    }
                    cursor.moveToPosition(i);
                    deleteFromSim(cursor);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshMessageList();
                        registerSimChangeObserver();
                    }
                });
            }
        }
        mIsDeleteAll = false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();

        if (null != mCursor) {
            if(mState != SHOW_BUSY) {
                menu.add(0, OPTION_MENU_SIM_CAPACITY, 0, R.string.sim_capacity_title);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case OPTION_MENU_SIM_CAPACITY:
                showSimCapacityDialog();
                break;

            case android.R.id.home:
                // The user clicked on the Messaging icon in the action bar. Take them back from
                // wherever they came from
                finish();
                break;
        }

        return true;
    }

    private void confirmDeleteDialog(OnClickListener listener, int messageId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.confirm_dialog_title);
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setCancelable(true);
        builder.setPositiveButton(R.string.yes, listener);
        builder.setNegativeButton(R.string.no, null);
        builder.setMessage(messageId);

        builder.show();
    }

    private void showSimCapacityDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.sim_capacity_title);
        builder.setCancelable(true);
        builder.setPositiveButton(R.string.yes, null);
        int iccCapacityAll = -1;
        if (TelephonyManager.getDefault().isMultiSimEnabled()) {
            iccCapacityAll = SmsManager.getSmsManagerForSubscriptionId(mSubscription)
                    .getSmsCapacityOnIcc();
        } else {
            iccCapacityAll = SmsManager.getDefault().getSmsCapacityOnIcc();
        }
        String capacityMessage = getString(R.string.sim_capacity, mCursor.getCount(),
                iccCapacityAll);
        builder.setMessage(capacityMessage);

        builder.show();
    }

    private void updateState(int state) {
        if (mState == state) {
            return;
        }

        mState = state;
        switch (state) {
            case SHOW_LIST:
                mSimList.setVisibility(View.VISIBLE);
                mMessage.setVisibility(View.GONE);
                setTitle(getString(R.string.sim_manage_messages_title));
                setProgressBarIndeterminateVisibility(false);
                mSimList.requestFocus();
                break;
            case SHOW_EMPTY:
                mSimList.setVisibility(View.GONE);
                mMessage.setVisibility(View.VISIBLE);
                setTitle(getString(R.string.sim_manage_messages_title));
                setProgressBarIndeterminateVisibility(false);
                break;
            case SHOW_BUSY:
                mSimList.setVisibility(View.GONE);
                mMessage.setVisibility(View.GONE);
                setTitle(getString(R.string.refreshing));
                setProgressBarIndeterminateVisibility(true);
                break;
            default:
                Log.e(TAG, "Invalid State");
        }
    }

    private void viewMessage(Cursor cursor) {
        // TODO: Add this.
    }

    private Uri getUriStrByCursor(Cursor cursor) {
        String messageIndexString = cursor.getString(cursor.getColumnIndexOrThrow("index_on_icc"));
        return mIccUri.buildUpon().appendPath(messageIndexString).build();
    }

    public Context getContext() {
        return ManageSimMessages.this;
    }

    public ListView getListView() {
        return mSimList;
    }

    private class ModeCallback implements ListView.MultiChoiceModeListener {
        ArrayList<Integer> mSelectedPos = new ArrayList<Integer>();
        ArrayList<Uri> mSelectedMsg = new ArrayList<Uri>();

        public void checkAll(boolean isCheck) {
            Log.d(TAG, "check all:" + isCheck);
            for (int i = 0; i < getListView().getCount(); i++) {
                getListView().setItemChecked(i, isCheck);
            }
        }

        private void confirmDeleteDialog(OnClickListener listener) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(R.string.confirm_dialog_title);
            builder.setIconAttribute(android.R.attr.alertDialogIcon);
            builder.setCancelable(true);
            builder.setPositiveButton(R.string.yes, listener);
            builder.setNegativeButton(R.string.no, null);
            builder.setMessage(R.string.confirm_delete_selected_messages);
            builder.show();
        }

        private class MultiMessagesListener implements OnClickListener {
            public void onClick(DialogInterface dialog, int whichButton) {
                deleteMessages();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshMessageList();
                    }
                });
            }
        }

        private void deleteMessages() {
            for (Uri uri : mSelectedMsg) {
                Log.d(TAG, "uri:" +uri.toString());
                SqliteWrapper.delete(getContext(), mContentResolver, uri, null, null);
            }
        }

        private void forwardMessage() {
            int pos = mSelectedPos.get(0);
            Cursor cursor = (Cursor) getListView().getAdapter().getItem(pos);
            String smsBody = cursor.getString(cursor.getColumnIndexOrThrow("body"));
            Intent intent = new Intent();
            intent.putExtra("exit_on_sent", true);
            intent.putExtra("forwarded_message", true);
            intent.putExtra("sms_body", smsBody);
            intent.setClassName(getContext(), FORWARD_MESSAGE_ACTIVITY_NAME);
            startActivity(intent);
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            recordAllSelectedItems();
            switch (item.getItemId()) {
                case R.id.forward:
                    forwardMessage();
                    break;
                case R.id.selection_toggle:
                    checkAll(!allItemsSelected());
                    return true;
                case R.id.delete:
                    confirmDeleteDialog(new MultiMessagesListener());
                    break;
                case R.id.copy_to_phone:
                    int pos = mSelectedPos.get(0);
                    Cursor cursor = (Cursor) getListView().getAdapter().getItem(pos);
                    if (!MessageUtils.checkIsPhoneMessageFull(getContext())) {
                        copyToPhoneMemory(cursor);
                    }
                    break;
                case R.id.reply:
                    replyMessage();
                    break;
                default:
                    break;
            }
            mode.finish();
            return true;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.sim_msg_multi_select_menu, menu);
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode arg0) {
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu arg1) {
            return true;
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int arg1, long arg2, boolean arg3) {
            final int checkedCount = getListView().getCheckedItemCount();

            mode.setTitle(getString(R.string.selected_count, checkedCount));
            if (checkedCount == 1) {
                recoredCheckedItemPositions();
            }
            customMenuVisibility(mode, checkedCount);

            MenuItem item = mode.getMenu().findItem(R.id.selection_toggle);
            if (item != null) {
                item.setTitle(getString(
                        allItemsSelected() ? R.string.deselected_all : R.string.selected_all));
            }
        }

        private boolean allItemsSelected() {
            final ListView lv = getListView();
            return lv.getCount() == lv.getCheckedItemCount();
        }

        private void customMenuVisibility(ActionMode mode, int checkedCount) {
            Menu menu = mode.getMenu();
            if (checkedCount > 1) {
                menu.findItem(R.id.forward).setVisible(false);
                menu.findItem(R.id.reply).setVisible(false);
                menu.findItem(R.id.copy_to_phone).setVisible(false);
            } else if(checkedCount == 1) {
                menu.findItem(R.id.forward).setVisible(true);
                menu.findItem(R.id.copy_to_phone).setVisible(true);
                menu.findItem(R.id.reply).setVisible(isInboxSms());
            }
        }

        private void recoredCheckedItemPositions(){
            SparseBooleanArray booleanArray = getListView().getCheckedItemPositions();
            mSelectedPos.clear();
            for (int i = 0; i < booleanArray.size(); i++) {
                int pos = booleanArray.keyAt(i);
                boolean checked = booleanArray.get(pos);
                if (checked) {
                    mSelectedPos.add(pos);
                }
            }
        }

        private void recordAllSelectedItems() {
            // must calculate all checked msg before multi selection done.
            recoredCheckedItemPositions();
            calculateSelectedMsgUri();
        }

        private void calculateSelectedMsgUri() {
            mSelectedMsg.clear();
            for (Integer pos : mSelectedPos) {
                Cursor c = (Cursor) getListView().getAdapter().getItem(pos);
                mSelectedMsg.add(getUriStrByCursor(c));
            }
        }

        private boolean isInboxSms() {
            int pos = mSelectedPos.get(0);
            Cursor cursor = (Cursor) getListView().getAdapter().getItem(pos);
            long type = cursor.getLong(cursor.getColumnIndexOrThrow("type"));
            Log.d(TAG, "sms type is: " + type);
            return type == TYPE_INBOX;
        }

        private void replyMessage() {
            int pos = mSelectedPos.get(0);
            Cursor cursor = (Cursor) getListView().getAdapter().getItem(pos);
            String address = cursor.getString(cursor.getColumnIndexOrThrow("address"));
            Intent replyIntent = new Intent(getContext(), ComposeMessageActivity.class);
            replyIntent.putExtra("reply_message", true);
            replyIntent.putExtra("address", address);
            replyIntent.putExtra("exit_on_sent", true);
            getContext().startActivity(replyIntent);
        }
    }
}

