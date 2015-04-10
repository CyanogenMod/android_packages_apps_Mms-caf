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

import com.suntek.mway.rcs.client.aidl.constant.BroadcastConstants;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class GroupChatManagerReceiver extends BroadcastReceiver {

    private GroupChatNotifyCallback mCallback;

    public GroupChatManagerReceiver(GroupChatNotifyCallback callback) {
        this.mCallback = callback;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (BroadcastConstants.UI_GROUP_MANAGE_NOTIFY.equals(intent.getAction())) {
            Bundle extras = intent.getExtras();
            String actionType = intent.getStringExtra(BroadcastConstants.BC_VAR_MSG_ACTION_TYPE);
            if (BroadcastConstants.ACTION_TYPE_CREATE.equals(actionType)) {
                if (mCallback != null) {
                    mCallback.onNewSubject(extras);
                }
            } else if (BroadcastConstants.ACTION_TYPE_UPDATE_ALIAS.equals(actionType)) {
                if (mCallback != null) {
                    mCallback.onMemberAliasChange(extras);
                }
            } else if (BroadcastConstants.ACTION_TYPE_DELETED.equals(actionType)) {
                if (mCallback != null) {
                    mCallback.onDisband(extras);
                }
            } else if (BroadcastConstants.ACTION_TYPE_DEPARTED.equals(actionType)) {
                if (mCallback != null) {
                    mCallback.onDeparted(extras);
                }
            } else if (BroadcastConstants.ACTION_TYPE_UPDATE_SUBJECT.equals(actionType)) {
                if (mCallback != null) {
                    mCallback.onUpdateSubject(extras);
                }
            } else if (BroadcastConstants.ACTION_TYPE_UPDATE_REMARK.equals(actionType)) {
                if (mCallback != null) {
                    mCallback.onUpdateRemark(extras);
                }
            } else if (BroadcastConstants.ACTION_TYPE_CREATE_NOT_ACTIVE.equals(actionType)) {
                if (mCallback != null) {
                    mCallback.onCreateNotActive(extras);
                }
            } else if (BroadcastConstants.ACTION_TYPE_BOOTED.equals(actionType)) {
                if (mCallback != null) {
                    mCallback.onBootMe(extras);
                }
            }
        }
    }

    public interface GroupChatNotifyCallback {
        void onNewSubject(Bundle extras);

        void onMemberAliasChange(Bundle extras);

        void onDisband(Bundle extras);

        void onDeparted(Bundle extras);

        void onUpdateSubject(Bundle extras);

        void onUpdateRemark(Bundle extras);

        void onCreateNotActive(Bundle extras);

        void onBootMe(Bundle extras);
    }

}
