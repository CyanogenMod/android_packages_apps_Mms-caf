/*
 * Copyright (c) 2014 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.android.mms.rcs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import android.R.array;
import android.app.ActionBar;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.android.mms.rcs.GroupChatManagerReceiver.GroupChatNotifyCallback;
import com.android.mms.ui.ChipsRecipientAdapter;
import com.android.mms.LogTag;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.ContactList;
import com.android.mms.ui.ComposeMessageActivity;
import com.android.mms.ui.RecipientsEditor;
import com.android.mms.ui.SelectRecipientsList;
import com.suntek.mway.rcs.client.aidl.constant.BroadcastConstants;
import com.suntek.mway.rcs.client.aidl.provider.model.GroupChatModel;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;

public class RcsCreateGroupChatActivity extends Activity implements
        OnClickListener {

    public static final String EXTRA_RECIPIENTS = "recipients";
    private static final int MENU_DONE = 0;
    public static final int REQUEST_CODE_CONTACTS_PICK = 100;
    private EditText mSubjectEdit;
    private RecipientsEditor mRecipientsEditor;
    private ContactList mRecipientList = new ContactList();
    private ComposeMessageCreateGroupChatCallback mCreateGroupChatCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rcs_new_group_chat_activity);
        getIntentData();
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        initView();
        registerReceiver(mRcsGroupChatReceiver,
                new IntentFilter(BroadcastConstants.UI_GROUP_MANAGE_NOTIFY));
    }

    private void getIntentData(){
        String numbers = getIntent().getStringExtra(EXTRA_RECIPIENTS);
        if(!TextUtils.isEmpty(numbers)){
            String[] numberList = numbers.split(";");
            ContactList list = ContactList.getByNumbers(Arrays.asList(numberList), true);
            mRecipientList.clear();
            mRecipientList.addAll(list);
        }
    }

    private void initView() {
        mSubjectEdit = (EditText) findViewById(R.id.group_chat_subject);
        InputFilter[] filters = { new RcsEditTextInputFilter(30) };
        mSubjectEdit.setFilters(filters);
        findViewById(R.id.recipients_selector).setOnClickListener(this);
        findViewById(R.id.create_group_chat).setOnClickListener(this);

        mRecipientsEditor = (RecipientsEditor) findViewById(R.id.recipients_editor);
        mRecipientsEditor.setAdapter(new ChipsRecipientAdapter(this));
        mRecipientsEditor.populate(mRecipientList);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            return;
        }
        switch (requestCode) {
        case REQUEST_CODE_CONTACTS_PICK:
            ArrayList<String> numbers = data
                    .getStringArrayListExtra(SelectRecipientsList.EXTRA_RECIPIENTS);
            numbers.addAll(mRecipientsEditor.getNumbers());
            ArrayList<String> recipientsNumbers =
                    RcsUtils.removeDuplicateNumber(numbers);
            insertNumbersIntoRecipientsEditor(recipientsNumbers);
            break;

        default:
            break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void insertNumbersIntoRecipientsEditor(ArrayList<String> numbers) {
        ContactList list = ContactList.getByNumbers(numbers, true);
        ContactList existing = mRecipientsEditor
                .constructContactsFromInput(true);
        for (Contact contact : existing) {
            if (!contact.existsInDatabase()) {
                list.add(contact);
            }
        }
        mRecipientsEditor.setText(null);
        mRecipientsEditor.populate(list);
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();
        switch (viewId) {
        case R.id.recipients_selector:
            launchMultiplePhonePicker();
            break;
        case R.id.create_group_chat:
            createGroupChat();
            break;
        default:
            break;
        }
    }

    private void launchMultiplePhonePicker() {
        Intent intent = new Intent(this, SelectRecipientsList.class);
        ContactList contacts = mRecipientsEditor
                .constructContactsFromInput(false);
        intent.putExtra(SelectRecipientsList.EXTRA_RECIPIENTS,
                contacts.getNumbers());
        intent.putExtra(SelectRecipientsList.MODE,
                SelectRecipientsList.MODE_DEFAULT);
        startActivityForResult(intent, REQUEST_CODE_CONTACTS_PICK);
    }

    private void createGroupChat() {
        List<String> list = mRecipientsEditor.getNumbers();
        if (list != null && list.size() > 0) {
            String subject = mSubjectEdit.getText().toString();
            if (TextUtils.isEmpty(subject)) {
                subject = "";
            }
            try {
                RcsApiManager.getConfApi().createGroupChat(subject, list);
                if (mCreateGroupChatCallback == null) {
                    mCreateGroupChatCallback = new ComposeMessageCreateGroupChatCallback(
                            this);
                }
                mCreateGroupChatCallback.onBegin();
            } catch (ServiceDisconnectedException e) {
                toast(R.string.rcs_service_is_not_available);
                Log.w("RCS_UI", e);
            }
        }
    }

    private GroupChatManagerReceiver mRcsGroupChatReceiver = new GroupChatManagerReceiver(
            new GroupChatNotifyCallback() {

                @Override
                public void onNewSubject(Bundle extras) {
                }

                @Override
                public void onMemberAliasChange(Bundle extras) {
                }

                @Override
                public void onDisband(Bundle extras) {
                }

                @Override
                public void onDeparted(Bundle extras) {
                }

                @Override
                public void onUpdateSubject(Bundle extras) {
                }

                @Override
                public void onUpdateRemark(Bundle extras) {
                }

                @Override
                public void onCreateNotActive(Bundle extras) {
                    handleRcsGroupChatCreateNotActive(extras);
                }
            });

    private void handleRcsGroupChatCreateNotActive(final Bundle extras) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if(mCreateGroupChatCallback != null){
                    mCreateGroupChatCallback.onDone(true);
                    mCreateGroupChatCallback.onEnd();
                }
                String groupId = extras
                        .getString(BroadcastConstants.BC_VAR_GROUP_ID, "-1");
                long threadId = RcsUtils.getThreadIdByGroupId(
                        RcsCreateGroupChatActivity.this, groupId);
                startActivity(ComposeMessageActivity.createIntent(
                        RcsCreateGroupChatActivity.this, threadId));
                RcsCreateGroupChatActivity.this.finish();
            }
        }, 2000);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mRcsGroupChatReceiver);
        super.onDestroy();
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            break;
        default:
            break;
        }
        return super.onOptionsItemSelected(item);
    }
}
