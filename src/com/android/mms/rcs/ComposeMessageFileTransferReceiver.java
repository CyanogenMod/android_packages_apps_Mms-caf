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

import com.android.mms.ui.MessageItem;
import com.android.mms.ui.MessageListAdapter;
import com.android.mms.ui.MessageListItem;
import com.suntek.mway.rcs.client.aidl.constant.BroadcastConstants;
import com.suntek.mway.rcs.client.api.util.log.LogHelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import java.util.HashMap;

public class ComposeMessageFileTransferReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "RCS_UI";
    private MessageListAdapter mMsgListAdapter;

    public ComposeMessageFileTransferReceiver(MessageListAdapter msgListAdapter) {
        this.mMsgListAdapter = msgListAdapter;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager manager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo gprs = manager.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        NetworkInfo wifi = manager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (!gprs.isConnected() && !wifi.isConnected()) {
            MessageListItem.setRcsIsStopDown(true);
            mMsgListAdapter.notifyDataSetChanged();
            return;
        }
        long start = intent.getLongExtra(BroadcastConstants.BC_VAR_TRANSFER_PRG_START, -1);
        long end = intent.getLongExtra(BroadcastConstants.BC_VAR_TRANSFER_PRG_END, -1);
        long total = intent.getLongExtra(BroadcastConstants.BC_VAR_TRANSFER_PRG_TOTAL, -1);
        String notifyMessageId = intent
                .getStringExtra(BroadcastConstants.BC_VAR_TRANSFER_PRG_MESSAGE_ID);
        LogHelper.trace("messageId =" + notifyMessageId + "start =" + start + ";end =" + end
                + ";total =" + total);
        HashMap<String, Long> fileProgressHashMap = MessageListItem.getFileTrasnferHashMap();
        if (notifyMessageId != null && start == end) {
            LogHelper.trace("download finish ");
            fileProgressHashMap.remove(notifyMessageId);
            mMsgListAdapter.notifyDataSetChanged();
            return;
        }
        if (fileProgressHashMap != null && total != 0) {
            Long lastProgress = fileProgressHashMap.get(notifyMessageId);
            long temp = start * 100 / total;
            if (temp == 100) {
                fileProgressHashMap.remove(notifyMessageId);
                MessageItem.setRcsIsDownload(RcsUtils.RCS_IS_DOWNLOAD_OK);
                Log.i(LOG_TAG, "100");
                return;
            }
            if (lastProgress != null) {
                LogHelper.trace("file tranfer progress = " + temp + "% ; lastprogress = "
                        + lastProgress + "% .");
            }
            if (lastProgress == null || temp - lastProgress >= 5) {
                lastProgress = temp;
                fileProgressHashMap.put(notifyMessageId, Long.valueOf(temp));
                MessageListItem.setsFileTrasnfer(fileProgressHashMap);
                mMsgListAdapter.notifyDataSetChanged();
            }
        }
    }
}
