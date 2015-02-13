
package com.android.mms.rcs;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import com.android.mms.rcs.RcsMessageThread;

public class RcsMessageThreadMng {

    public static final int handlerSize = 30;

    private List<RcsMessageThread> mRcsMessageThreadList;

    private static RcsMessageThreadMng instance;

    private boolean isHandlerStarted = false;

    private RcsMessageThreadMng() {
        init();
    }

    public static RcsMessageThreadMng getInstance() {
        if (instance == null)
            instance = new RcsMessageThreadMng();

        return instance;
    }

    private void init() {

        mRcsMessageThreadList = new ArrayList<RcsMessageThread>(handlerSize);

        for (int i = 1; i <= handlerSize; i++) {

            mRcsMessageThreadList.add(new RcsMessageThread(i));
        }

    }

    public void start() {

        isHandlerStarted = true;

        for (RcsMessageThread reportHandler : mRcsMessageThreadList) {
            if (reportHandler != null)
                reportHandler.start();
        }

    }

    public void stop() {

        for (RcsMessageThread reportHandler : mRcsMessageThreadList) {
            if (reportHandler != null)
                reportHandler.notifyExit();
        }

        isHandlerStarted = false;
    }

    public RcsMessageThread getRcsMessageThread(int index) {
        return mRcsMessageThreadList.get(index);
    }

}
