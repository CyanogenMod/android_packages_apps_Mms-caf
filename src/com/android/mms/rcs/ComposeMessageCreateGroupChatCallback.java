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

import com.android.mms.R;
import com.android.mms.ui.ComposeMessageActivity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.widget.Toast;

public class ComposeMessageCreateGroupChatCallback {
    private Activity mContext;
    private ProgressDialog mProgressDialog;

    public ComposeMessageCreateGroupChatCallback(Activity context) {
        this.mContext = context;
    }

    public void onBegin() {
        if (mProgressDialog == null || !mProgressDialog.isShowing()) {
            String title = mContext.getString(R.string.please_wait);
            String message = mContext.getString(R.string.creating_group_chat);
            mProgressDialog = ProgressDialog.show(mContext, title, message, false, true);
        }
    }

    public void onDone(boolean isSuccess) {
        int resId = isSuccess ? R.string.create_group_chat_successfully
                : R.string.create_group_chat_failed;
        Toast.makeText(mContext, resId, Toast.LENGTH_LONG).show();
    }

    public void onEnd() {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }
}
