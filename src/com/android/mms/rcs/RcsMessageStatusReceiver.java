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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class RcsMessageStatusReceiver extends BroadcastReceiver {
    public static final String MESSAGE_STATUS_RECEIVED_ACTION =
            "com.suntek.mway.rcs.ACTION_UI_MESSAGE_ADD_TO_DATABASE";

    public static final String UI_MESSAGE_STATUS_CHANGE_NOTIFY =
            "com.suntek.mway.rcs.ACTION_UI_MESSAGE_STATUS_CHANGE_NOTIFY";

    @Override
    public void onReceive(Context context, Intent intent) {
        intent.setClass(context, RcsMessageStatusService.class);
        context.startService(intent);
    }
}
