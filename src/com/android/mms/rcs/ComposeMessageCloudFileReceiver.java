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

import java.util.HashMap;

import com.android.mms.ui.ComposeMessageActivity;
import com.android.mms.ui.MessageListAdapter;
import com.android.mms.ui.MessageListItem;
import com.suntek.mway.rcs.client.aidl.constant.BroadcastConstants;
import com.android.mms.R;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class ComposeMessageCloudFileReceiver extends BroadcastReceiver {

    private MessageListAdapter mMsgListAdapter;
    private ListView mListView;

    public ComposeMessageCloudFileReceiver(MessageListAdapter msgListAdapter,
            ListView listView) {
        this.mMsgListAdapter = msgListAdapter;
        this.mListView = listView;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager manager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo gprs = manager
                .getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        NetworkInfo wifi = manager
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (!gprs.isConnected() && !wifi.isConnected()) {
            mMsgListAdapter.setRcsIsStopDown(true);
            mMsgListAdapter.notifyDataSetChanged();
            return;
        }
        String eventType = intent
                .getStringExtra(BroadcastConstants.BC_VAR_MC_ENENTTYPE);
        int messageId = intent.getIntExtra(
                BroadcastConstants.BC_VAR_MC_CHATMESSAGE_ID, -1);
        HashMap<String, Long> fileProgressHashMap = mMsgListAdapter
                .getFileTrasnferHashMap();

        TextView textDataView = (TextView) mListView
                .findViewWithTag("tag_file_" + messageId);

        if (!TextUtils.isEmpty(eventType) && eventType.
                equals(BroadcastConstants.BC_V_MC_EVENTTYPE_ERROR)) {
            Toast.makeText(context, R.string.download_mcloud_file_fail,
                    Toast.LENGTH_SHORT).show();
            fileProgressHashMap.remove(String.valueOf(messageId));
            if (textDataView != null) {
                textDataView
                        .setText(context.getString(R.string.stop_down_load));
            }
        } else if (!TextUtils.isEmpty(eventType) && eventType
                .equals(BroadcastConstants.BC_V_MC_EVENTTYPE_PROGRESS)) {
            float process = (int) intent.getLongExtra(
                    BroadcastConstants.BC_VAR_MC_PROCESS_SIZE, 0);
            float total = (int) intent.getLongExtra(
                    BroadcastConstants.BC_VAR_MC_TOTAL_SIZE, 0);
            long percent = (long) ((process / total) * 100);
            fileProgressHashMap.put(String.valueOf(messageId), percent);
            mMsgListAdapter.setsFileTrasnfer(fileProgressHashMap);
            if (textDataView != null) {
                textDataView.setText(context.getString(
                        R.string.downloading_percent, percent));
            }
            mMsgListAdapter.notifyDataSetChanged();
        } else if (!TextUtils.isEmpty(eventType) && eventType
                .equals(BroadcastConstants.BC_V_MC_EVENTTYPE_SUCCESS)) {
            fileProgressHashMap.remove(String.valueOf(messageId));
            if (textDataView != null) {
                textDataView.setText(context
                        .getString(R.string.downloading_finish));
            }
            mMsgListAdapter.notifyDataSetChanged();
        } else if(!TextUtils.isEmpty(eventType) && eventType
                .equals(BroadcastConstants.BC_V_MC_EVENTTYPE_FILE_TOO_LARGE)){
          Toast.makeText(context,R.string.file_is_too_larger,
                  Toast.LENGTH_LONG).show();
        } else if(!TextUtils.isEmpty(eventType) && eventType.
                equals(BroadcastConstants.BC_V_MC_EVENTTYPE_SUFFIX_NOT_ALLOWED)){
            Toast.makeText(context,R.string.name_not_fix,
                    Toast.LENGTH_LONG).show();
        }
    }

}
