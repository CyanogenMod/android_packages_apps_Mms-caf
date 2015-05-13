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
import android.app.Activity;
import android.app.Fragment;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toolbar;

import com.android.contacts.common.list.ViewPagerTabs;
import com.android.contacts.common.preference.ContactsPreferences;
import com.android.mms.R;
import com.android.mms.data.Group;
import com.android.mms.data.PhoneNumber;
import com.android.mms.data.RecipientsListLoader;

import java.util.ArrayList;
import java.util.HashSet;

public class SelectRecipientsList extends Activity implements
        LoaderManager.LoaderCallbacks<RecipientsListLoader.Result> {
    private static final int MENU_DONE = 0;
    private static final int MENU_MOBILE = 1;
    private static final int MENU_NAME_ORDER = 2;

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

    private ContactsPreferences mContactsPreferences;
    private HashSet<PhoneNumber> mCheckedPhoneNumbers;
    private HashSet<Group> mLocalGroups;
    private PhoneNumber mVCardNumber = null;
    private boolean mMobileOnly = true;
    private int mMode = MODE_DEFAULT;
    private boolean mDataLoaded;

    private ViewPager mTabPager;
    private ViewPagerTabs mViewPagerTabs;

    private ItemListFragment mContactFragment;
    private ItemListFragment mGroupFragment;

    private SelectRecipientsListAdapter mContactListAdapter;
    private SelectRecipientsGroupListAdapter mGroupListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.select_recipients_list_screen);

        if (savedInstanceState != null) {
            mMode = savedInstanceState.getInt(MODE);
        } else {
            mMode = getIntent().getIntExtra(MODE, MODE_INFO);
        }

        mContactsPreferences = new ContactsPreferences(this);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);

        mTabPager = (ViewPager) findViewById(R.id.tab_pager);
        mTabPager.setAdapter(new ListTabAdapter());
        mTabPager.setOnPageChangeListener(new TabPagerListener());
        mViewPagerTabs = (ViewPagerTabs) findViewById(R.id.lists_pager_header);
        mViewPagerTabs.setViewPager(mTabPager);
        mViewPagerTabs.setVisibility(mMode == MODE_DEFAULT ? View.VISIBLE : View.GONE);

        mContactListAdapter = new SelectRecipientsListAdapter(this);
        mGroupListAdapter = new SelectRecipientsGroupListAdapter(this);

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

        // Get things ready
        mCheckedPhoneNumbers = new HashSet<PhoneNumber>();
        mLocalGroups = new HashSet<Group>();
        getLoaderManager().initLoader(0, null, this);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mContactFragment != null && mContactListAdapter != null) {
            unbindListItems();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Load the required preference values
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mMobileOnly = sharedPreferences.getBoolean(PREF_MOBILE_NUMBERS_ONLY, true);

        menu.add(0, MENU_DONE, 0, R.string.menu_done)
             .setIcon(R.drawable.ic_menu_done)
             .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS
                     | MenuItem.SHOW_AS_ACTION_WITH_TEXT)
             .setVisible(false);

        menu.add(0, MENU_MOBILE, 0, R.string.menu_mobile)
             .setCheckable(true)
             .setChecked(mMobileOnly)
             .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER
                     | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        boolean useAltNameOrdering = mContactsPreferences.getSortOrder()
                == ContactsPreferences.SORT_ORDER_ALTERNATIVE;
        int nameOrderItemTitleRes = useAltNameOrdering
                ? R.string.menu_order_by_given_name : R.string.menu_order_by_last_name;

        menu.add(0, MENU_NAME_ORDER, 0, nameOrderItemTitleRes)
             .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_NEVER
                     | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean hasSelection = mCheckedPhoneNumbers.size() > 0
                || mLocalGroups.size() > 0 || mVCardNumber != null;
        menu.findItem(MENU_DONE).setVisible(hasSelection);
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

                if (mContactFragment != null) {
                    mContactFragment.setIsMobileOnly(mMobileOnly);
                }

                // Restart the loader to reflect the change
                getLoaderManager().restartLoader(0, null, this);
                return true;
            case MENU_NAME_ORDER:
                int currOrder = mContactsPreferences.getSortOrder();
                int newOrder = currOrder == ContactsPreferences.SORT_ORDER_ALTERNATIVE
                        ? ContactsPreferences.SORT_ORDER_PRIMARY
                        : ContactsPreferences.SORT_ORDER_ALTERNATIVE;
                mContactsPreferences.setSortOrder(newOrder);
                invalidateOptionsMenu();

                // Restart the loader to reflect the change
                getLoaderManager().restartLoader(0, null, this);
                return true;
            case android.R.id.home:
                //avoid start compose screen with empty intent.
                setResult(RESULT_CANCELED, null);
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onListItemClick(ListAdapter adapter, int position) {
        if (adapter == mGroupListAdapter) {
            Group group = mGroupListAdapter.getItem(position);
            checkGroup(group, !group.isChecked());
            mGroupListAdapter.notifyDataSetChanged();
            mContactListAdapter.notifyDataSetChanged();
        } else {
            PhoneNumber number = mContactListAdapter.getItem(position);
            if (mMode == MODE_VCARD) {
                flipVCardNumberState(number);
            } else {
                checkPhoneNumber(number, !number.isChecked());
            }
            mContactListAdapter.notifyDataSetChanged();
        }
        invalidateOptionsMenu();
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
        final ListView listView = mContactFragment.getListView();
        final int count = listView.getChildCount();
        for (int i = 0; i < count; i++) {
            mContactListAdapter.unbindView(listView.getChildAt(i));
        }
    }

    @Override
    public Loader<RecipientsListLoader.Result> onCreateLoader(int id, Bundle args) {
        return new RecipientsListLoader(this, mContactsPreferences);
    }

    @Override
    public void onLoadFinished(Loader<RecipientsListLoader.Result> loader,
            RecipientsListLoader.Result data) {
        if (getIntent() != null) {
            String[] initialRecipients = getIntent().getStringArrayExtra(EXTRA_RECIPIENTS);
            if (initialRecipients != null && mMode == MODE_DEFAULT) {
                boolean found;
                for (String recipient : initialRecipients) {
                    found = false;
                    // if statement to check if there are any contacts with phone numbers first
                    if (data.phoneNumbers != null) {
                        for (PhoneNumber number : data.phoneNumbers) {
                            if (number.equals(recipient)) {
                                found = true;
                                checkPhoneNumber(number, true);
                                updateGroupCheckStateForNumber(number, null);
                                break;
                            }
                        }
                    }
                    if (!found) {
                        mCheckedPhoneNumbers.add(new PhoneNumber(recipient, true));
                    }
                }
                invalidateOptionsMenu();
            }
            setIntent(null);
        } else {
            HashSet<PhoneNumber> old = mCheckedPhoneNumbers;
            mCheckedPhoneNumbers = new HashSet<PhoneNumber>();
            boolean found;
            for (PhoneNumber checked : old) {
                found = false;
                if (data.phoneNumbers != null) {
                    for (PhoneNumber number : data.phoneNumbers) {
                        if (number.equals(checked)) {
                            checkPhoneNumber(number, true);
                            updateGroupCheckStateForNumber(number, null);
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    mCheckedPhoneNumbers.add(checked);
                }
            }
        }

        mContactListAdapter.setNotifyOnChange(false);
        mContactListAdapter.clear();
        if (data.phoneNumbers != null) {
            mContactListAdapter.addAll(data.phoneNumbers);
        }
        mContactListAdapter.notifyDataSetChanged();

        mGroupListAdapter.clear();
        if (data.groups != null) {
            mGroupListAdapter.addAll(data.groups);
        }
        mDataLoaded = true;
        applyListAdapters();
    }

    @Override
    public void onLoaderReset(Loader<RecipientsListLoader.Result> data) {
        mContactListAdapter.notifyDataSetInvalidated();
        mGroupListAdapter.notifyDataSetInvalidated();
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
                    int mimeIndex = cursor.getColumnIndexOrThrow(
                            ContactsContract.Data.MIMETYPE);
                    int phoneIndex = cursor.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Phone.NUMBER);
                    int emailIndex = cursor.getColumnIndexOrThrow(
                            ContactsContract.CommonDataKinds.Email.ADDRESS);
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
        Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
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

        return getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                null, selection.toString(), null, null);
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

    private void applyListAdapters() {
        if (!mDataLoaded) {
            return;
        }
        if (mContactFragment != null && mContactFragment.getListAdapter() == null) {
            mContactFragment.setListAdapter(mContactListAdapter);
        }
        if (mGroupFragment != null && mGroupFragment.getListAdapter() == null) {
            mGroupFragment.setListAdapter(mGroupListAdapter);
        }
    }

    private class ListTabAdapter extends FragmentPagerAdapter {
        public ListTabAdapter() {
            super(getFragmentManager());
        }

        @Override
        public int getCount() {
            return mMode == MODE_DEFAULT ? 2 : 1;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            ItemListFragment result =
                    (ItemListFragment) super.instantiateItem(container, position);
            if (position == 1) {
                mGroupFragment = result;
            } else {
                mContactFragment = result;
                result.setIsMobileOnly(mMobileOnly);
            }

            applyListAdapters();
            return result;
        }

        @Override
        public Fragment getItem(int position) {
            Bundle args = new Bundle();

            args.putBoolean(ItemListFragment.IS_GROUP, position == 1);
            args.putInt(ItemListFragment.MODE, mMode);

            ItemListFragment f = new ItemListFragment();
            f.setArguments(args);

            return f;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return getString(position == 0 ? R.string.contactsList : R.string.groupsLabel);
        }
    }

    private class TabPagerListener implements ViewPager.OnPageChangeListener {
        @Override
        public void onPageScrollStateChanged(int state) {
            mViewPagerTabs.onPageScrollStateChanged(state);
        }
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            mViewPagerTabs.onPageScrolled(position, positionOffset, positionOffsetPixels);
        }
        @Override
        public void onPageSelected(int position) {
            mViewPagerTabs.onPageSelected(position);
        }
    }

    public static class ItemListFragment extends ListFragment implements
            AdapterView.OnItemClickListener {
        public static final String IS_GROUP = "is_group";
        public static final String MODE = "mode";

        private boolean mMobileOnly;

        public void setIsMobileOnly(boolean mobileOnly) {
            mMobileOnly = mobileOnly;
            updateEmptyText();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            return inflater.inflate(R.layout.select_recipients_list_list, container, false);
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            ListView listView = getListView();
            boolean isGroup = getArguments().getBoolean(IS_GROUP, false);
            int mode = getArguments().getInt(MODE, -1);

            listView.setChoiceMode(mode == MODE_VCARD
                    ? ListView.CHOICE_MODE_SINGLE : ListView.CHOICE_MODE_NONE);
            listView.setFastScrollEnabled(!isGroup);
            listView.setFastScrollAlwaysVisible(!isGroup);
            listView.setEmptyView(getView().findViewById(android.R.id.empty));
            listView.setOnItemClickListener(this);

            updateEmptyText();
        }

        @Override
        public void setListAdapter(ListAdapter adapter) {
            super.setListAdapter(adapter);
            if (getView() != null && adapter instanceof ListView.RecyclerListener) {
                ListView.RecyclerListener l = (ListView.RecyclerListener) adapter;
                getListView().setRecyclerListener(l);
            }
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            SelectRecipientsList activity = (SelectRecipientsList) getActivity();
            activity.onListItemClick(getListAdapter(), position);
        }

        private void updateEmptyText() {
            if (getView() != null && !getArguments().getBoolean(IS_GROUP, false)) {
                TextView emptyView = (TextView) getListView().getEmptyView();
                emptyView.setText(getString(mMobileOnly
                        ? R.string.no_recipients_mobile_only : R.string.no_recipients));
            }
        }
    }
}
