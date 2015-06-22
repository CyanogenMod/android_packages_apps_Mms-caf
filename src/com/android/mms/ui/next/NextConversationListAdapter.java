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

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import com.android.mms.R;
import com.android.mms.ui.ConversationListAdapter;
import com.android.mms.util.PrettyTime;
import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;

/**
 * NextConversationListAdapter
 * <pre>
 *     This is an extension of the existing adapter to provide header information
 * </pre>
 *
 * @see {@link ConversationListAdapter}
 * @see {@link StickyListHeadersAdapter}
 */
public class NextConversationListAdapter extends ConversationListAdapter implements
        StickyListHeadersAdapter {

    // Constants
    private static final int DATE = 1;

    // Members
    private PrettyTime mPrettyTime = new PrettyTime();

    /**
     * Constructor
     *
     * @param context {@link Context}
     * @param cursor {@link Cursor}
     */
    public NextConversationListAdapter(Context context, Cursor cursor) {
        super(context, cursor);
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
            convertView = inflater.inflate(R.layout.conversation_list_header1, parent, false);
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
}
