/*
 * Copyright(C) 2013-2014, The Linux Foundation. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.mms.ui;

import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.ui.MessageUtils;
import com.android.mms.util.Recycler;
import com.google.android.mms.pdu.PduHeaders;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.provider.Telephony;
import android.provider.Telephony.Sms;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;
import android.text.InputFilter;
import android.text.InputFilter.LengthFilter;
import android.text.TextUtils;

/**
 * This activity provides extend search mode by content ,number and name in mailbox mode.
 */
public class SearchActivityExtend extends Activity {
    private static final String TAG = "SearchActivityExtend";
    private static final int MENU_SEARCH = 0;
    private EditText mSearchStringEdit;
    private Spinner  mSpinSearchMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_dialog);

        initUi();
    }

    private void initUi() {
        mSpinSearchMode = (Spinner) findViewById(R.id.search_mode);
        mSpinSearchMode.setPromptId(R.string.search_mode);
        mSearchStringEdit = (EditText) findViewById(R.id.search_key_edit);
        mSearchStringEdit.requestFocus();
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case MENU_SEARCH:
            doSearch();
            break;
        case android.R.id.home:
            finish();
            break;
        default:
            return true;
        }

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_SEARCH, 0, R.string.menu_search)
            .setIcon(R.drawable.ic_menu_search_white)
            .setAlphabeticShortcut(android.app.SearchManager.MENU_KEY)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    private void doSearch() {
        String keyStr = mSearchStringEdit.getText().toString();
        String displayStr = keyStr;
        if (TextUtils.isEmpty(keyStr)) {
            return;
        }

        int modePosition = mSpinSearchMode.getSelectedItemPosition();
        int matchWhole = MessageUtils.MATCH_BY_ADDRESS;

        if (modePosition == MessageUtils.SEARCH_MODE_NAME) {
            keyStr = MessageUtils.getAddressByName(this, keyStr);
            if (TextUtils.isEmpty(keyStr)) {
                Toast.makeText(SearchActivityExtend.this, getString(R.string.invalid_name_toast),
                        Toast.LENGTH_LONG).show();
                return;
            }
            modePosition = MessageUtils.SEARCH_MODE_NUMBER;
            matchWhole = MessageUtils.MATCH_BY_THREAD_ID;
        }

        Intent i = new Intent(this, MailBoxMessageList.class);
        i.putExtra(MessageUtils.SEARCH_KEY, true);
        i.putExtra(MessageUtils.SEARCH_KEY_TITLE, getString(R.string.search_title));
        i.putExtra(MessageUtils.SEARCH_KEY_MODE_POSITION, modePosition);
        i.putExtra(MessageUtils.SEARCH_KEY_KEY_STRING, keyStr);
        i.putExtra(MessageUtils.SEARCH_KEY_DISPLAY_STRING, displayStr);
        i.putExtra(MessageUtils.SEARCH_KEY_MATCH_WHOLE, matchWhole);
        startActivity(i);

        finish();
    }
}
