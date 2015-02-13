
package com.android.mms.rcs;

import com.android.mms.rcs.RcsMessageThread.MessageThreadOption;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import android.content.Intent;

public class RecvMessageQueue {

    private static final RecvMessageQueue instance = new RecvMessageQueue();

    private RecvMessageQueue() {

    }

    public static RecvMessageQueue getInstance() {
        return instance;
    }

    public boolean addReport(int sessionId, Intent option) {
        int mod = (int)(sessionId % RcsMessageThreadMng.handlerSize);
        RcsMessageThread reportHandler = RcsMessageThreadMng.getInstance().getRcsMessageThread(mod);

        if (reportHandler == null) {

            return false;
        }

        return reportHandler.addReport(option);
    }

}
