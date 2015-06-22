/*
* Copyright (C) 2015 The CyanogenMod Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package com.android.mms.ui.next;

import android.content.ContentResolver;
import android.database.Cursor;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import com.android.mms.R;
import com.android.mms.data.Conversation;
import com.android.mms.data.Conversation.ConversationQueryHandler;
import com.android.mms.ui.ConversationList;
import com.android.mms.ui.ConversationListAdapter;
import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;
import se.emilsjolander.stickylistheaders.StickyListHeadersListView;

import java.util.HashSet;

/**
 * ThreadListQueryHandler
 * <pre>
 *
 * </pre>
 */
/* package */ class ThreadListQueryHandler extends ConversationQueryHandler {

    // Constants

    /* package */ static final int THREAD_LIST_QUERY_TOKEN = 1701;
    /* package */ static final int UNREAD_THREADS_QUERY_TOKEN = 1702;
    public static final int DELETE_CONVERSATION_TOKEN = 1801;
    public static final int HAVE_LOCKED_MESSAGES_TOKEN = 1802;
    /* package */ static final int DELETE_OBSOLETE_THREADS_TOKEN = 1803;
    /* package */ static final int MARK_CONVERSATION_UNREAD_TOKEN = 1804;
    /* package */ static final int MARK_CONVERSATION_READ_TOKEN = 1805;

    // Members
    private NextConversationListActivity mActivity;
    private StickyListHeadersListView mListView;
    private NextConversationListAdapter mListAdapter;

    /**
     * Constructor
     *
     * @param activity {@link NextConversationListActivity}
     */
    public ThreadListQueryHandler(NextConversationListActivity activity) {
        super(activity.getContentResolver());
        mActivity = activity;
        mListView = mActivity.getListView();
        mListAdapter = mActivity.getAdapter();
    }

    protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
        switch (token) {
            case THREAD_LIST_QUERY_TOKEN:
                if (mListAdapter != null) {
                    mListAdapter.changeCursor(cursor);
                    if (mListAdapter.getCount() == 0) {
                        ((TextView) (mListView.getEmptyView())).setText(R.string.no_conversations);
                        // [TODO][MSB]: Handle using callback?
                    }
                }
                // [TODO][MSB]: if do once after first query
                break;
            case UNREAD_THREADS_QUERY_TOKEN:
                int count = 0;
                if (cursor != null) {
                    count = cursor.getCount();
                    cursor.close();
                }
                // [TODO][MSB]: Handle UI callback
                // mSavedFirstVisiblePosition
                break;
            case HAVE_LOCKED_MESSAGES_TOKEN:
                break;
            default:
                NextConversationListActivity.loge("onQueryComplete called with unknown token " +
                        token);
                break;
        }
    }

    protected void onDeleteComplete(int token, Object cookie, int result) {
        switch (token) {
            case DELETE_CONVERSATION_TOKEN:
                break;
            case DELETE_OBSOLETE_THREADS_TOKEN:
                NextConversationListActivity.logd("onQueryComplete finished " +
                        "DELETE_OBSOLETE_THREADS_TOKEN");
                break;
            default:
                break;
        }
        super.onDeleteComplete(token, cookie, result);
    }

}
