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

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import com.android.mms.rcs.RcsMessageThread;

public class RcsMessageThreadMng {

    public static final int HANDLER_SIZE = 30;

    private List<RcsMessageThread> mRcsMessageThreadList;

    private static RcsMessageThreadMng sInstance;

    private RcsMessageThreadMng() {
        init();
    }

    public static RcsMessageThreadMng getInstance() {
        if (sInstance == null) {
            sInstance = new RcsMessageThreadMng();
        }
        return sInstance;
    }

    private void init() {

        mRcsMessageThreadList = new ArrayList<RcsMessageThread>(HANDLER_SIZE);

        for (int i = 0; i < HANDLER_SIZE; i++) {
            mRcsMessageThreadList.add(new RcsMessageThread(i));
        }
    }

    public void start() {

        for (RcsMessageThread reportHandler : mRcsMessageThreadList) {
            if (reportHandler != null) {
                reportHandler.start();
            }
        }
    }

    public void stop() {

        for (RcsMessageThread reportHandler : mRcsMessageThreadList) {
            if (reportHandler != null) {
                reportHandler.notifyExit();
            }
        }
    }

    public RcsMessageThread getRcsMessageThread(int index) {
        return mRcsMessageThreadList.get(index);
    }

}
