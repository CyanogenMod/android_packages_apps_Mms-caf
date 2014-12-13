/*<!-- Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 *Redistribution and use in source and binary forms, with or without
 *modification, are permitted provided that the following conditions are
 *met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *    * Neither the name of The Linux Foundation nor the names of its
 *      contributors may be used to endorse or promote products derived
 *      from this software without specific prior written permission.
 *
 *THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 *WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 *ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 *BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 *BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 *WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 *OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 *IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * -->*/

package com.android.mms.ui;

import static com.android.mms.ui.MessageListAdapter.COLUMN_ID;
import static com.android.mms.ui.MessageListAdapter.COLUMN_MSG_TYPE;
import static com.android.mms.ui.MessageListAdapter.FORWARD_PROJECTION;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SqliteWrapper;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Conversations;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.mms.data.Contact;
import com.android.mms.R;
import com.android.mms.ui.PopupList;

import java.util.ArrayList;

/**
 * Displays a list of the SMS messages with checkbox and support multi select action.
 */

public class ManageMultiSelectAction extends Activity {
    private static final String TAG = "ManageMultiSelectAction";

    private Cursor mCursor;
    private ListView mMsgListView;
    private TextView mMessage;
    private MessageListAdapter mMsgListAdapter;
    private ContentResolver mContentResolver;
    private BackgroundQueryHandler mBackgroundQueryHandler;
    private int mSubscription; // add for DSDS
    private Uri mIccUri;
    private ProgressDialog mProgressDialog = null;
    private OperateThread mOperateThread = null;
    ArrayList<Cursor> mSelectedCursors = new ArrayList<Cursor>();
    ArrayList<String> mSelectedUris = new ArrayList<String>();
    ArrayList<MessageItem> mMessageItems = new ArrayList<MessageItem>();

    private int mManageMode;
    private long mThreadId;
    private static final int SUB_INVALID = -1;
    private static final int INVALID_THREAD = -1;
    private static final int SHOW_TOAST = 1;
    private static final int FOWARD_DONE = 2;

    private static final int DELAY_TIME = 500;
    private static final String SORT_ORDER = "date ASC";
    private static final String MESSAGE_CONTENT = "sms_body";
    // add for merge messages
    private static final String COLON = ":";
    private static final String LEFT_PARENTHESES = "(";
    private static final String RIGHT_PARENTHESES = ")";
    private static final String LINE_BREAK = "\n";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.sim_list);

        Intent intent = getIntent();
        mManageMode = intent.getIntExtra(ComposeMessageActivity.MANAGE_MODE,
                MessageUtils.INVALID_MODE);
        if (mManageMode == MessageUtils.FORWARD_MODE) {
            mThreadId = intent.getLongExtra(ComposeMessageActivity.THREAD_ID, INVALID_THREAD);
        } else if (mManageMode == MessageUtils.SIM_MESSAGE_MODE){
            mSubscription = intent.getIntExtra(MessageUtils.SUBSCRIPTION_KEY, SUB_INVALID);
            mIccUri = MessageUtils.getIccUriBySubscription(mSubscription);
        }

        mMsgListView = (ListView) findViewById(R.id.messages);
        mMessage = (TextView) findViewById(R.id.empty_message);

        mContentResolver = getContentResolver();
        mBackgroundQueryHandler = new BackgroundQueryHandler(mContentResolver);

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        startMsgListQuery();
    }

    private Handler mUiHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SHOW_TOAST: {
                    String toastStr = (String) msg.obj;
                    Toast.makeText(ManageMultiSelectAction.this, toastStr,
                            Toast.LENGTH_SHORT).show();
                    clearSelect();
                    if (mProgressDialog != null) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                    }
                    finish();
                    break;
                }
                case FOWARD_DONE: {
                    clearSelect();
                    if (mProgressDialog != null) {
                        mProgressDialog.dismiss();
                        mProgressDialog = null;
                    }
                    finish();
                    break;
                }
                default:
                    break;
            }
        }
    };

    final Runnable mShowProgress = new Runnable() {
            @Override
            public void run() {
                if (mProgressDialog != null) {
                    mProgressDialog.show();
                }
            }
        };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCursor != null) {
            mCursor.close();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (areItemsSelected()) {
            MenuItem item = null;
            if (mManageMode == MessageUtils.FORWARD_MODE) {
                item = menu.add(Menu.NONE, Menu.FIRST, Menu.NONE, R.string.menu_forward);
            } else if (mManageMode == MessageUtils.SIM_MESSAGE_MODE) {
                item = menu.add(Menu.NONE, Menu.FIRST, Menu.NONE, R.string.done_delete);
            }
            if (item != null) {
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            }
            menu.add(Menu.NONE, Menu.FIRST + 1, Menu.NONE,
                    areAllItemsSelected() ? R.string.deselected_all : R.string.selected_all);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case Menu.FIRST:
                confirmMultiAction();
                break;
            case Menu.FIRST + 1:
                if (areAllItemsSelected()) {
                    clearSelect();
                } else {
                    allSelect();
                }
                break;
            case android.R.id.home:
                finish();
                break;
        }
        return true;
    }

    private void confirmMultiAction() {
        SparseBooleanArray booleanArray = mMsgListView.getCheckedItemPositions();
        int size = booleanArray.size();

        if (size > 0) {
            for (int j = 0; j < size; j++) {
                int position = booleanArray.keyAt(j);
                if (!mMsgListView.isItemChecked(position)) {
                    continue;
                }
                Cursor c = (Cursor) mMsgListAdapter.getItem(position);
                if (c == null) {
                    return;
                }
                mSelectedCursors.add(c);
                if (mManageMode == MessageUtils.FORWARD_MODE) {
                    String type = c.getString(COLUMN_MSG_TYPE);
                    long msgId = c.getLong(COLUMN_ID);
                    mMessageItems.add(mMsgListAdapter.getCachedMessageItem(type, msgId, c));
                } else if (mManageMode == MessageUtils.SIM_MESSAGE_MODE) {
                    mSelectedUris.add(getUriStrByCursor(c));
                }
            }
        }

        if (mManageMode == MessageUtils.FORWARD_MODE) {
            forwardSmsConversation();
        } else if (mManageMode == MessageUtils.SIM_MESSAGE_MODE) {
            MultiMessagesListener l = new MultiMessagesListener();
            confirmDeleteDialog(l);
        }
    }

    private void forwardSmsConversation() {
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setCancelable(false);
        //mProgressDialog.setTitle(R.string.merging_sms_title);
        mProgressDialog.setMessage(getString(R.string.please_wait));
        mUiHandler.postDelayed(mShowProgress, DELAY_TIME);
        if (mOperateThread == null) {
            mOperateThread = new OperateThread();
        }
        Thread thread = new Thread(mOperateThread);
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    private void mergeMessagesAndForward() {
        StringBuilder forwardContent = new StringBuilder();
        if (mMessageItems.size() > 1) {
            for (MessageItem msgItem : mMessageItems) {
                if (Sms.isOutgoingFolder(msgItem.mBoxId)) {
                    forwardContent.append(msgItem.mContact + COLON + LINE_BREAK);
                } else {
                    if (Contact.get(msgItem.mAddress, false).existsInDatabase()) {
                        forwardContent.append(msgItem.mContact + LEFT_PARENTHESES +
                                msgItem.mAddress + RIGHT_PARENTHESES + COLON + LINE_BREAK);
                    } else {
                        forwardContent.append(msgItem.mAddress + COLON + LINE_BREAK);
                    }
                }
                forwardContent.append(msgItem.mBody);
                forwardContent.append(LINE_BREAK);
            }
        } else if (mMessageItems.size() == 1) {
            // we don't add the recipient's information if only forward one sms.
            forwardContent.append(mMessageItems.get(0).mBody);
        }

        forwardMessage(forwardContent.toString());
    }

    private void forwardMessage(String forwardContent) {
        Intent intent = new Intent(this, ComposeMessageActivity.class);
        intent.putExtra(ComposeMessageActivity.KEY_EXIT_ON_SENT, true);
        intent.putExtra(ComposeMessageActivity.KEY_FORWARDED_MESSAGE, true);
        intent.putExtra(MESSAGE_CONTENT, forwardContent);
        startActivity(intent);

        Message msg = Message.obtain();
        msg.what = FOWARD_DONE;
        mUiHandler.sendMessage(msg);
    }

    private class MultiMessagesListener implements OnClickListener {
        public void onClick(DialogInterface dialog, int whichButton) {
            deleteSelectedMessages();
        }
    }

    private void deleteSelectedMessages() {
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setTitle(R.string.deleting_title);
        mProgressDialog.setMessage(getString(R.string.please_wait));
        mProgressDialog.show();
        if (mOperateThread == null) {
            mOperateThread = new OperateThread();
        }
        Thread thread = new Thread(mOperateThread);
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    private void deleteMessages() {
        for (String uri : mSelectedUris) {
            SqliteWrapper.delete(ManageMultiSelectAction.this, mContentResolver,
                    Uri.parse(uri), null, null);
        }

        Message msg = Message.obtain();
        msg.what = SHOW_TOAST;
        msg.obj = getString(R.string.operate_success);
        mUiHandler.sendMessage(msg);
    }

    private String getUriStrByCursor(Cursor cursor) {
        String messageIndexString =
                cursor.getString(cursor.getColumnIndexOrThrow("index_on_icc"));
        Uri simUri = mIccUri.buildUpon().appendPath(messageIndexString).build();

        return simUri.toString();
    }

    private void confirmDeleteDialog(OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.confirm_dialog_title);
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setCancelable(true);
        builder.setPositiveButton(R.string.yes, listener);
        builder.setNegativeButton(R.string.no, null);
        builder.setMessage(R.string.confirm_delete_selected_messages);
        builder.show();
    }

    private void startMsgListQuery() {
        try {
            mMsgListView.setVisibility(View.GONE);
            mMessage.setVisibility(View.GONE);
            setTitle(getString(R.string.refreshing));
            setProgressBarIndeterminateVisibility(true);
            if (mManageMode == MessageUtils.FORWARD_MODE) {
                mBackgroundQueryHandler.startQuery(0, null, Sms.CONTENT_URI,
                        FORWARD_PROJECTION, Conversations.THREAD_ID + "=?",
                        new String[]{String.valueOf(mThreadId)}, SORT_ORDER);
            } else if (mManageMode == MessageUtils.SIM_MESSAGE_MODE) {
                mBackgroundQueryHandler.startQuery(0, null, mIccUri, null, null, null, null);
            }
        } catch (SQLiteException e) {
            SqliteWrapper.checkSQLiteException(this, e);
        }
    }

    private void setupActionBar() {
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(
                    ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);
            actionBar.setTitle("");
        }
    }

    private void clearSelect() {
        mMsgListView.clearChoices();
        updateSelectionState();
        mMsgListView.invalidateViews();
    }

    private void allSelect() {
        int count = mMsgListAdapter.getCount();
        for (int i = 0; i < count; i++) {
            mMsgListView.setItemChecked(i, true);
        }
        updateSelectionState();
        mMsgListView.invalidateViews();
    }

    private void updateSelectionState() {
        final int checkedCount = mMsgListView.getCheckedItemCount();
        getActionBar().setTitle(getString(R.string.selected_count, checkedCount));
        invalidateOptionsMenu();
    }

    private boolean areItemsSelected() {
        return mMsgListView.getCheckedItemCount() != 0;
    }

    private boolean areAllItemsSelected() {
        return mMsgListView.getCheckedItemCount() == mMsgListView.getCount();
    }

    private class OperateThread extends Thread {
        public OperateThread() {
            super("OperateThread");
        }

        public void run() {
            if (mManageMode == MessageUtils.FORWARD_MODE) {
                mergeMessagesAndForward();
            } else if (mManageMode == MessageUtils.SIM_MESSAGE_MODE) {
                deleteMessages();
            }
        }
    }

    private final class BackgroundQueryHandler extends AsyncQueryHandler {
        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            setProgressBarVisibility(false);
            if (mCursor != null) {
                mCursor.close();
            }
            mCursor = cursor;

            if (mCursor != null) {
                if (!mCursor.moveToFirst()) {
                    finish();
                } else if (mMsgListAdapter == null) {
                    setupActionBar();
                    mMsgListAdapter = new MessageListAdapter(
                            ManageMultiSelectAction.this, mCursor, mMsgListView, false, null);
                    mMsgListAdapter.setMultiManageMode(mManageMode);
                    mMsgListView.setAdapter(mMsgListAdapter);
                    mMsgListView.setVisibility(View.VISIBLE);
                    mMessage.setVisibility(View.GONE);
                    mMsgListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                    mMsgListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        public void onItemClick(AdapterView<?> parent, View view, int position,
                                long id) {
                            mMsgListView.invalidateViews();
                            updateSelectionState();
                        }
                    });
                    mMsgListView.requestFocus();
                    setProgressBarIndeterminateVisibility(false);
                } else {
                    mMsgListAdapter.changeCursor(mCursor);
                }
            } else {
                finish();
            }
            return;
        }
    }
}
