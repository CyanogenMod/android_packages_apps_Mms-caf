/**
 * Copyright (c) 2009, Google Inc.
 * Copyright (c) 2013, The Linux Foundation. All Rights Reserved.
 * Not a Contribution.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.ui;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.ActionBar;
import android.app.ListActivity;
import android.app.SearchManager;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.SearchRecentSuggestions;
import android.provider.Telephony;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.mms.MmsApp;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;

/***
 * Presents a List of search results.  Each item in the list represents a thread which
 * matches.  The item contains the contact (or phone number) as the "title" and a
 * snippet of what matches, below.  The snippet is taken from the most recent part of
 * the conversation that has a match.  Each match within the visible portion of the
 * snippet is highlighted.
 */

public class SearchActivity extends ListActivity
{
    private static final int REQUEST_VIEW_MESSAGE = 1;
    public static final int RESULT_SAVE_MMS_DRAFT = 2;
    public static final int RESULT_SAVE_SMS_DRAFT = 3;
    public static final int RESULT_MSG_HAS_CHANGED = 4;

    private AsyncQueryHandler mQueryHandler;
    private String mSearchString;
    private boolean mNeedUpdate = false;
    public static final String ACTION_QUERY_MMS = "com.android.search.QUERY_MMS";

    // Track which TextView's show which Contact objects so that we can update
    // appropriately when the Contact gets fully loaded.
    private HashMap<Contact, TextView> mContactMap = new HashMap<Contact, TextView>();


    /*
     * Subclass of TextView which displays a snippet of text which matches the full text and
     * highlights the matches within the snippet.
     */
    public static class TextViewSnippet extends TextView {
        private static String sEllipsis = "\u2026";

        private static int sTypefaceHighlight = Typeface.BOLD;

        private String mFullText;
        private String mTargetString;
        private Pattern mPattern;

        public TextViewSnippet(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public TextViewSnippet(Context context) {
            super(context);
        }

        public TextViewSnippet(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        /**
         * We have to know our width before we can compute the snippet string.  Do that
         * here and then defer to super for whatever work is normally done.
         */
        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            String fullTextLower = mFullText.toLowerCase();
            String targetStringLower = mTargetString.toLowerCase();

            int startPos = 0;
            int searchStringLength = targetStringLower.length();
            int bodyLength = fullTextLower.length();

            Matcher m = mPattern.matcher(mFullText);
            if (m.find(0)) {
                startPos = m.start();
            }

            TextPaint tp = getPaint();

            float searchStringWidth = tp.measureText(mTargetString);
            float textFieldWidth = getWidth();

            float ellipsisWidth = tp.measureText(sEllipsis);
            textFieldWidth -= (2F * ellipsisWidth); // assume we'll need one on both ends

            String snippetString = null;
            if (searchStringWidth > textFieldWidth) {
                snippetString = mFullText.substring(startPos, startPos + searchStringLength);
            } else {

                int offset = -1;
                int start = -1;
                int end = -1;
                /* TODO: this code could be made more efficient by only measuring the additional
                 * characters as we widen the string rather than measuring the whole new
                 * string each time.
                 */
                while (true) {
                    offset += 1;

                    int newstart = Math.max(0, startPos - offset);
                    int newend = Math.min(bodyLength, startPos + searchStringLength + offset);

                    if (newstart == start && newend == end) {
                        // if we couldn't expand out any further then we're done
                        break;
                    }
                    start = newstart;
                    end = newend;

                    // pull the candidate string out of the full text rather than body
                    // because body has been toLower()'ed
                    String candidate = mFullText.substring(start, end);
                    if (tp.measureText(candidate) > textFieldWidth) {
                        // if the newly computed width would exceed our bounds then we're done
                        // do not use this "candidate"
                        break;
                    }

                    snippetString = String.format(
                            "%s%s%s",
                            start == 0 ? "" : sEllipsis,
                            candidate,
                            end == bodyLength ? "" : sEllipsis);
                }
            }

            SpannableString spannable = new SpannableString(snippetString);
            int start = 0;

            m = mPattern.matcher(snippetString);
            while (m.find(start)) {
                spannable.setSpan(new StyleSpan(sTypefaceHighlight), m.start(), m.end(), 0);
                start = m.end();
            }
            setText(spannable);

            // do this after the call to setText() above
            super.onLayout(changed, left, top, right, bottom);
        }

        public void setText(String fullText, String target) {
            // Use a regular expression to locate the target string
            // within the full text.  The target string must be
            // found as a word start so we use \b which matches
            // word boundaries.
            String patternString = "\\b" + Pattern.quote(target);
            mPattern = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);

            mFullText = fullText;
            mTargetString = target;
            requestLayout();
        }
    }

    Contact.UpdateListener mContactListener = new Contact.UpdateListener() {
        public void onUpdate(Contact updated) {
            TextView tv = mContactMap.get(updated);
            if (tv != null) {
                tv.setText(updated.getNameAndNumber());
            }
        }
    };

    @Override
    public void onStop() {
        super.onStop();
        Contact.removeListener(mContactListener);
    }

    private long getThreadId(long sourceId, long which) {
        Uri.Builder b = Uri.parse("content://mms-sms/messageIdToThread").buildUpon();
        b = b.appendQueryParameter("row_id", String.valueOf(sourceId));
        b = b.appendQueryParameter("table_to_use", String.valueOf(which));
        String s = b.build().toString();

        Cursor c = getContentResolver().query(
                Uri.parse(s),
                null,
                null,
                null,
                null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    return c.getLong(c.getColumnIndex("thread_id"));
                }
            } finally {
                c.close();
            }
        }
        return -1;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        String searchStringParameter = getIntent().getStringExtra(SearchManager.QUERY);
        if (searchStringParameter == null) {
            searchStringParameter = getIntent().getStringExtra("intent_extra_data_key" /*SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA*/);
        }
        final String searchString =
            searchStringParameter != null ? searchStringParameter.trim() : searchStringParameter;
        mSearchString = searchString;
        // If we're being launched with a source_id then just go to that particular thread.
        // Work around the fact that suggestions can only launch the search activity, not some
        // arbitrary activity (such as ComposeMessageActivity).
        final Uri u = getIntent().getData();
        if (u != null && u.getQueryParameter("source_id") != null) {
            Thread t = new Thread(new Runnable() {
                public void run() {
                    try {
                        long sourceId = Long.parseLong(u.getQueryParameter("source_id"));
                        long whichTable = Long.parseLong(u.getQueryParameter("which_table"));
                        long threadId = getThreadId(sourceId, whichTable);

                        final Intent onClickIntent = new Intent(SearchActivity.this, ComposeMessageActivity.class);
                        onClickIntent.putExtra("highlight", searchString);
                        onClickIntent.putExtra("select_id", sourceId);
                        onClickIntent.putExtra("thread_id", threadId);
                        onClickIntent.putExtra("from_search", true);
                        startActivityForResult(onClickIntent, REQUEST_VIEW_MESSAGE);
                        finish();
                        return;
                    } catch (NumberFormatException ex) {
                        // ok, we do not have a thread id so continue
                    }
                }
            }, "Search thread");
            t.start();
            return;
        }

        setContentView(R.layout.search_activity);
        ContentResolver cr = getContentResolver();

        searchStringParameter = searchStringParameter.trim();
        final ListView listView = getListView();
        listView.setItemsCanFocus(true);
        listView.setFocusable(true);
        listView.setClickable(true);

        // I considered something like "searching..." but typically it will
        // flash on the screen briefly which I found to be more distracting
        // than beneficial.
        // This gets updated when the query completes.
        setTitle("");

        Contact.addListener(mContactListener);

        // When the query completes cons up a new adapter and set our list adapter to that.
        mQueryHandler = new AsyncQueryHandler(cr) {
            protected void onQueryComplete(int token, Object cookie, Cursor c) {
                if (c == null) {
                    setTitle(getResources().getQuantityString(
                        R.plurals.search_results_title,
                        0,
                        0,
                        searchString));
                    return;
                }
                final int threadIdPos = c.getColumnIndex("thread_id");
                final int addressPos  = c.getColumnIndex("address");
                final int bodyPos     = c.getColumnIndex("body");
                final int rowidPos    = c.getColumnIndex("_id");

                int cursorCount = c.getCount();
                setTitle(getResources().getQuantityString(
                        R.plurals.search_results_title,
                        cursorCount,
                        cursorCount,
                        searchString));

                // Note that we're telling the CursorAdapter not to do auto-requeries. If we
                // want to dynamically respond to changes in the search results,
                // we'll have have to add a setOnDataSetChangedListener().
                setListAdapter(new CursorAdapter(SearchActivity.this,
                        c, false /* no auto-requery */) {
                    @Override
                    public void bindView(View view, Context context, Cursor cursor) {
                        final TextView title = (TextView)(view.findViewById(R.id.title));
                        final TextViewSnippet snippet = (TextViewSnippet)(view.findViewById(R.id.subtitle));

                        String address = cursor.getString(addressPos);
                        // if the user touches the item then launch the compose message
                        // activity with some extra parameters to highlight the search
                        // results and scroll to the latest part of the conversation
                        // that has a match.
                        final long threadId = cursor.getLong(threadIdPos);
                        final long rowid = cursor.getLong(rowidPos);

                        // The address of draft is null, but we should
                        // show the numberAndName for it also, so we need
                        // to use the thread_id to get the contactList.
                        if (TextUtils.isEmpty(address)) {
                            Conversation conversation = Conversation.get(SearchActivity.this,
                                    threadId, false);
                            if (null != conversation) {
                                ContactList list = conversation.getRecipients();
                                String sep = ", ";
                                StringBuffer titleBuffer = new StringBuffer();
                                for (Contact c : list) {
                                    CharSequence charSequence = RecipientsEditor.contactToToken(c);
                                    if (!TextUtils.isEmpty(charSequence)) {
                                        titleBuffer.append(charSequence).append(sep);
                                    }
                                }
                                // remove the last separator if there is
                                int lastSepIndex = titleBuffer.lastIndexOf(sep);
                                if (lastSepIndex != -1) {
                                    titleBuffer.delete(lastSepIndex, titleBuffer.length());
                                }
                                title.setText(titleBuffer);
                            }
                        } else {
                            Contact contact = Contact.get(address, false);
                            String titleString = contact.getNameAndNumber();
                            title.setText(titleString);
                        }

                        snippet.setText(cursor.getString(bodyPos), searchString);

                        view.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                final Intent onClickIntent = new Intent(SearchActivity.this, ComposeMessageActivity.class);
                                onClickIntent.putExtra("thread_id", threadId);
                                onClickIntent.putExtra("highlight", searchString);
                                onClickIntent.putExtra("select_id", rowid);
                                onClickIntent.putExtra("from_search", true);
                                startActivityForResult(onClickIntent, REQUEST_VIEW_MESSAGE);
                            }
                        });
                    }

                    @Override
                    public View newView(Context context, Cursor cursor, ViewGroup parent) {
                        LayoutInflater inflater = LayoutInflater.from(context);
                        View v = inflater.inflate(R.layout.search_item, parent, false);
                        return v;
                    }

                });

                // ListView seems to want to reject the setFocusable until such time
                // as the list is not empty.  Set it here and request focus.  Without
                // this the arrow keys (and trackball) fail to move the selection.
                listView.setFocusable(true);
                listView.setFocusableInTouchMode(true);
                listView.requestFocus();

                // Remember the query if there are actual results
                if (cursorCount > 0) {
                    SearchRecentSuggestions recent = ((MmsApp)getApplication()).getRecentSuggestions();
                    if (recent != null) {
                        recent.saveRecentQuery(
                                searchString,
                                getString(R.string.search_history,
                                        cursorCount, searchString));
                    }
                }
            }
        };

        startQuery();

        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        // Register a ContentObserver to monitor Sms.CONTENT_URI of database update
        getContentResolver().registerContentObserver(
                android.provider.Telephony.MmsSms.CONTENT_URI, true, mMessagesObserver);
        IntentFilter filter = new IntentFilter(ACTION_QUERY_MMS);
        registerReceiver(mBroadcastReceiver, filter);
    }

    private void startQuery() {
        // don't pass a projection since the search uri ignores it
        Uri uri = Telephony.MmsSms.SEARCH_URI.buildUpon()
                    .appendQueryParameter("pattern", mSearchString).build();

        // kick off a query for the threads which match the search string
        mQueryHandler.startQuery(0, null, uri, null, null, null, null);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // The user clicked on the Messaging icon in the action bar. Take them back from
                // wherever they came from
                finish();
                return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_VIEW_MESSAGE:
                if ((resultCode == RESULT_SAVE_SMS_DRAFT) || (resultCode == RESULT_SAVE_MMS_DRAFT)) {
                    // As this operation is delay, so should clear the ListView,
                    // otherwise the ListView will use the old data before query completed
                    setListAdapter(null);

                    if (resultCode == RESULT_SAVE_MMS_DRAFT) {
                        mNeedUpdate = true;
                    } else {
                        mNeedUpdate = false;
                    }
                } else if (resultCode == RESULT_MSG_HAS_CHANGED) {
                    // If the msg database has changed (eg. delete or new message),
                    // we should startQuery and refresh the ListView
                    setListAdapter(null);
                    startQuery();
                }
                break;
        }
    }

    private ContentObserver mMessagesObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfUpdate) {
            // If the msg update success, we should startQuery and refresh the ListView
            startQuery();
        }
    };

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            // When convert sms to Mms,contentProvider notify MmsSms.CONTENT_URI to update,
            // but sometime can't not find the new Mms. so need update again.
            if (mNeedUpdate) {
                startQuery();
                mNeedUpdate = false;
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver);
        // Unregister the ContentObserver of Sms.CONTENT_URI
        getContentResolver().unregisterContentObserver(mMessagesObserver);
    }
}
