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

import android.app.ActionBar;
import android.app.ListActivity;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.mms.R;
import com.android.mms.data.Group;
import com.android.mms.data.PhoneNumber;
import com.android.mms.data.RecipientsListLoader;

import java.util.ArrayList;
import java.util.HashSet;

public class SelectRecipientsList extends ListActivity implements
        AdapterView.OnItemClickListener,
        LoaderManager.LoaderCallbacks<ArrayList<RecipientsListLoader.Result>> {
    private static final int MENU_DONE = 0;
    private static final int MENU_MOBILE = 1;
    private static final int MENU_GROUPS = 2;

    public static final String MODE = "mode";
    public static final int MODE_DEFAULT = 0;
    public static final int MODE_INFO = 1;
    public static final int MODE_VCARD = 2;

    public static final String EXTRA_INFO = "info";
    public static final String EXTRA_VCARD = "vcard";
    public static final String EXTRA_RECIPIENTS = "recipients";
    public static final String PREF_MOBILE_NUMBERS_ONLY = "pref_key_mobile_numbers_only";
    public static final String PREF_SHOW_GROUPS = "pref_key_show_groups";

    private static final String KEY_SEP = ",";
    private static final String ITEM_SEP = ", ";
    private static final String CONTACT_SEP_LEFT = "[";
    private static final String CONTACT_SEP_RIGHT = "]";

    private static final String DATA_JOIN_MIMETYPES = "data "
            + "JOIN mimetypes ON (data.mimetype_id = mimetypes._id)";

    private static final String QUERY_PHONE_ID_IN_LOCAL_GROUP = ContactsContract.RawContacts.Data.MIMETYPE
            + "='" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "'"
            + " AND "
            + " raw_contact_id"
            + " IN "
            + "(SELECT "
            + "data.raw_contact_id"
            + " FROM "
            + DATA_JOIN_MIMETYPES
            + " WHERE "
            + ContactsContract.RawContacts.Data.MIMETYPE
            + "='"
            + ContactsContract.CommonDataKinds.LocalGroup.CONTENT_ITEM_TYPE
            + "'";

    private SelectRecipientsListAdapter mListAdapter;
    private HashSet<PhoneNumber> mCheckedPhoneNumbers;
    private HashSet<Group> mLocalGroups;
    private PhoneNumber mVCardNumber = null;
    private boolean mMobileOnly = true;
    private boolean mShowGroups = true;
    private View mProgressSpinner;
    private int mMode = MODE_DEFAULT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.select_recipients_list_screen);

        if (savedInstanceState != null) {
            mMode = savedInstanceState.getInt(MODE);
        } else {
            mMode = getIntent().getIntExtra(MODE, MODE_INFO);
        }

        if (mMode == MODE_INFO || mMode == MODE_VCARD) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putBoolean(PREF_SHOW_GROUPS, false).commit();
        } else {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putBoolean(PREF_SHOW_GROUPS, true).commit();
        }

        switch (mMode) {
            case MODE_INFO:
                setTitle(R.string.attach_add_contact_as_text);
                break;
            case MODE_VCARD:
                setTitle(R.string.attach_add_contact_as_vcard);
                break;
            default:
                // leave it be
        }

        mProgressSpinner = findViewById(R.id.progress_spinner);

        // List view
        ListView listView = getListView();
        if (mMode == MODE_VCARD) {
            listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        } else {
            listView.setChoiceMode(ListView.CHOICE_MODE_NONE);
        }
        listView.setFastScrollEnabled(true);
        listView.setFastScrollAlwaysVisible(true);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setEmptyView(findViewById(R.id.empty));
        listView.setOnItemClickListener(this);

        // Get things ready
        mCheckedPhoneNumbers = new HashSet<PhoneNumber>();
        mLocalGroups = new HashSet<Group>();
        getLoaderManager().initLoader(0, null, this);

        ActionBar mActionBar = getActionBar();
        if (mActionBar != null) {
            mActionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mListAdapter != null) {
            unbindListItems();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Load the required preference values
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mMobileOnly = sharedPreferences.getBoolean(PREF_MOBILE_NUMBERS_ONLY, true);
        mShowGroups = sharedPreferences.getBoolean(PREF_SHOW_GROUPS, true);

        menu.add(0, MENU_DONE, 0, R.string.menu_done)
             .setIcon(R.drawable.ic_menu_done)
             .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS | MenuItem.SHOW_AS_ACTION_WITH_TEXT)
             .setVisible(false);

        menu.add(0, MENU_MOBILE, 0, R.string.menu_mobile)
             .setCheckable(true)
             .setChecked(mMobileOnly)
             .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER
                     | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        if (mMode == MODE_DEFAULT) {
            menu.add(0, MENU_GROUPS, 0, R.string.menu_groups)
                    .setCheckable(true)
                    .setChecked(mShowGroups)
                    .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER
                            | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mCheckedPhoneNumbers.size() > 0 || mLocalGroups.size() > 0 || mVCardNumber != null) {
            menu.findItem(MENU_DONE).setVisible(true);
        } else {
            menu.findItem(MENU_DONE).setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        switch (item.getItemId()) {
            case MENU_DONE:
                Intent intent = new Intent();
                putExtraWithContact(intent);
                setResult(RESULT_OK, intent);
                finish();
                return true;
            case MENU_MOBILE:
                // If it was checked before it should be unchecked now and vice versa
                mMobileOnly = !mMobileOnly;
                item.setChecked(mMobileOnly);
                prefs.edit().putBoolean(PREF_MOBILE_NUMBERS_ONLY, mMobileOnly).commit();

                // Restart the loader to reflect the change
                getLoaderManager().restartLoader(0, null, this);
                return true;
            case MENU_GROUPS:
                // If it was checked before it should be unchecked now and vice versa
                mShowGroups = !mShowGroups;
                item.setChecked(mShowGroups);
                prefs.edit().putBoolean(PREF_SHOW_GROUPS, mShowGroups).commit();

                // Restart the loader to reflect the change
                getLoaderManager().restartLoader(0, null, this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapter, View view, int position, long arg) {
        RecipientsListLoader.Result item =
                (RecipientsListLoader.Result) adapter.getItemAtPosition(position);

        if (mMode == MODE_VCARD) {
            flipVCardNumberState(item.phoneNumber);
        } else {
            if (item.group != null) {
                checkGroup(item.group, !item.group.isChecked());
            } else {
                checkPhoneNumber(item.phoneNumber, !item.phoneNumber.isChecked());
                updateGroupCheckStateForNumber(item.phoneNumber, null);
            }
        }

        invalidateOptionsMenu();
        mListAdapter.notifyDataSetChanged();
    }

    private void flipVCardNumberState(PhoneNumber number) {
        if (mVCardNumber != null && mVCardNumber.isChecked()) {
            mVCardNumber.setChecked(false);
            mVCardNumber = null;
        }
        mVCardNumber = number;
        mVCardNumber.setChecked(true);
    }

    private void checkPhoneNumber(PhoneNumber phoneNumber, boolean check) {
        phoneNumber.setChecked(check);
        if (check) {
            mCheckedPhoneNumbers.add(phoneNumber);
        } else {
            mCheckedPhoneNumbers.remove(phoneNumber);
        }
    }

    private void updateGroupCheckStateForNumber(PhoneNumber phoneNumber, Group excludedGroup) {
        ArrayList<Group> phoneGroups = phoneNumber.getGroups();
        if (phoneGroups == null) {
            return;
        }

        if (phoneNumber.isChecked() && phoneNumber.isDefault()) {
            for (Group group : phoneGroups) {
                if (group == excludedGroup || group.isLocal()) {
                    continue;
                }
                boolean checked = true;
                for (PhoneNumber number : group.getPhoneNumbers()) {
                    if (number.isDefault() && !number.isChecked()) {
                        checked = false;
                        break;
                    }
                }
                group.setChecked(checked);
            }
        } else if (!phoneNumber.isChecked()) {
            for (Group group : phoneGroups) {
                if (group != excludedGroup) {
                    group.setChecked(false);
                }
            }
        }
    }

    private void checkGroup(Group group, boolean check) {
        group.setChecked(check);
        if (group.isLocal()) {
            if (group.isChecked()) {
                mLocalGroups.add(group);
            } else {
                mLocalGroups.remove(group);
            }
            return;
        }
        ArrayList<PhoneNumber> phoneNumbers = group.getPhoneNumbers();

        if (phoneNumbers != null) {
            for (PhoneNumber phoneNumber : phoneNumbers) {
                if (phoneNumber.isDefault()) {
                    checkPhoneNumber(phoneNumber, check);
                    updateGroupCheckStateForNumber(phoneNumber, group);
                }
            }
        }
    }

    private void unbindListItems() {
        final ListView listView = getListView();
        final int count = listView.getChildCount();
        for (int i = 0; i < count; i++) {
            mListAdapter.unbindView(listView.getChildAt(i));
        }
    }

    @Override
    public Loader<ArrayList<RecipientsListLoader.Result>> onCreateLoader(int id, Bundle args) {
        // Show the progress indicator
        mProgressSpinner.setVisibility(View.VISIBLE);
        return new RecipientsListLoader(this);
    }

    @Override
    public void onLoadFinished(Loader<ArrayList<RecipientsListLoader.Result>> loader,
            ArrayList<RecipientsListLoader.Result> data) {
        // We have an old list, get rid of it before we start again
        if (mListAdapter != null) {
            mListAdapter.notifyDataSetInvalidated();
            unbindListItems();
        }

        // Hide the progress indicator
        mProgressSpinner.setVisibility(View.GONE);

        // Create and set the list adapter
        mListAdapter = new SelectRecipientsListAdapter(this, data);

        if (getIntent() != null) {
            String[] initialRecipients = getIntent().getStringArrayExtra(EXTRA_RECIPIENTS);
            if (initialRecipients != null && mMode == MODE_DEFAULT) {
                for (String recipient : initialRecipients) {
                    for (RecipientsListLoader.Result result : data) {
                        if (result.phoneNumber != null && result.phoneNumber.equals(recipient)) {
                            checkPhoneNumber(result.phoneNumber, true);
                            updateGroupCheckStateForNumber(result.phoneNumber, null);
                            break;
                        }
                    }
                }
                invalidateOptionsMenu();
            }
            setIntent(null);
        }

        if (mListAdapter == null) {
            // We have no numbers to show, indicate it
            TextView emptyText = (TextView) getListView().getEmptyView();
            emptyText.setText(mMobileOnly ?
                    R.string.no_recipients_mobile_only : R.string.no_recipients);
        } else {
            setListAdapter(mListAdapter);
            getListView().setRecyclerListener(mListAdapter);
        }
    }

    @Override
    public void onLoaderReset(Loader<ArrayList<RecipientsListLoader.Result>> data) {
        mListAdapter.notifyDataSetInvalidated();
    }

    private void putExtraWithContact(Intent intent) {
        if (mMode == MODE_DEFAULT) {
            ArrayList<String> numbers = new ArrayList<String>();
            for (PhoneNumber phoneNumber : mCheckedPhoneNumbers) {
                if (phoneNumber.isChecked()) {
                    numbers.add(phoneNumber.getNumber());
                }
            }

            // We have to append any local group contacts which may have been checked
            if (mLocalGroups.size() > 0) {
                appendLocalGroupContacts(numbers);
            }

            intent.putExtra(EXTRA_RECIPIENTS, numbers);
        } else if (mMode == MODE_INFO) {
            intent.putExtra(EXTRA_INFO, getCheckedNumbersAsText());
        } else if (mMode == MODE_VCARD) {
            if (mVCardNumber != null) {
                intent.putExtra(EXTRA_VCARD, getSelectedAsVcard(mVCardNumber).toString());
            }
        }
    }


    private String getCheckedNumbersAsText() {
        StringBuilder result = new StringBuilder();

        for (PhoneNumber number : mCheckedPhoneNumbers) {
            result.append(CONTACT_SEP_LEFT);
            result.append(getString(R.string.contact_info_text_as_name));
            result.append(number.getName());
            Cursor cursor = getContactsDetailCursor(number.getContactId());
            try {
                if (cursor != null) {
                    int mimeIndex = cursor
                            .getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE);
                    int phoneIndex = cursor
                            .getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    int emailIndex = cursor
                            .getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Email.ADDRESS);
                    while (cursor.moveToNext()) {
                        result.append(ITEM_SEP);
                        String mimeType = cursor.getString(mimeIndex);
                        if (mimeType.equals(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)) {
                            result.append(getString(R.string.contact_info_text_as_phone));
                            result.append(cursor.getString(phoneIndex));
                        } else if (mimeType.equals(
                                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)) {
                            result.append(getString(R.string.contact_info_text_as_email));
                            result.append(cursor.getString(emailIndex));
                        }
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            result.append(CONTACT_SEP_RIGHT);
        }

        return result.toString();
    }

    private void appendLocalGroupContacts(ArrayList<String> numbers) {
        Cursor cursor = getContentResolver()
                .query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        PhoneNumber.PROJECTION, getContactsForCheckedGroupsSelectionQuery(),
                        getContactsForCheckedGroupsSelectionArgs(), null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                PhoneNumber number = new PhoneNumber(cursor);
                numbers.add(number.getNumber());
            }
        }
        if (cursor != null) {
            cursor.close();
        }
    }

    private Cursor getContactsDetailCursor(long contactId) {
        StringBuilder selection = new StringBuilder();
        selection.append(ContactsContract.Data.CONTACT_ID + "=" + contactId)
                .append(" AND (")
                .append(ContactsContract.Data.MIMETYPE + "='"
                        + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "'")
                .append(" OR ")
                .append(ContactsContract.Data.MIMETYPE + "='"
                        + ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE + "')");

        Cursor cursor = getContentResolver().query(
                ContactsContract.Data.CONTENT_URI, null, selection.toString(), null, null);

        return cursor;
    }

    private Uri getSelectedAsVcard(PhoneNumber number) {
        if (mMode == MODE_VCARD && !TextUtils.isEmpty(number.getLookupKey())) {
            return Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI,
                    number.getLookupKey());
        }
        return null;
    }

    private String getContactsForCheckedGroupsSelectionQuery() {
        StringBuilder buf = new StringBuilder();
        buf.append(QUERY_PHONE_ID_IN_LOCAL_GROUP).append(" AND (");
        for (int i = 0; i < mLocalGroups.size(); i++) {
            if (i > 0) {
                buf.append(" OR ");
            }
            buf.append(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID)
                    .append("=?");
        }
        return buf.append("))").toString();
    }

    private String[] getContactsForCheckedGroupsSelectionArgs() {
        if (mLocalGroups != null) {
            String[] selectionArgs = new String[mLocalGroups.size()];
            int i = 0;
            for (Group group : mLocalGroups) {
                selectionArgs[i++] = String.valueOf(group.getId());
            }
            return selectionArgs;
        }
        return null;
    }
}
