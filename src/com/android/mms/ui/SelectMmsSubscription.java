/*
 * Copyright (c) 2012-2013, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.ui;

import com.android.mms.R;
import com.android.mms.util.MultiSimUtility;
import com.android.mms.transaction.TransactionService;

import com.android.internal.telephony.MSimConstants;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemProperties;
import android.provider.Telephony.Mms;

import android.telephony.TelephonyManager;
import android.telephony.MSimTelephonyManager;

import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

public class SelectMmsSubscription extends Service {
    static private final String TAG = "SelectMmsSubscription";

    private Context mContext;
    private SwitchSubscriptionTask switchSubscriptionTask;;
    private TransactionService mTransactionService;
    private ArrayList<TxnRequest> mQueue = new ArrayList();

    private final String ACTION_ALARM = "android.intent.action.ACTION_ALARM";

    public class SwitchSubscriptionTask extends AsyncTask<TxnRequest, Void, TxnSwitchResult> {


        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }

        @Override
        protected TxnSwitchResult doInBackground(TxnRequest... params) {
            Log.d(TAG, "doInBackground(), Thread="+
                    Thread.currentThread().getName());

            TxnSwitchResult txnSwitchResult = new TxnSwitchResult(params[0], -1);

            if (MultiSimUtility.getCurrentDataSubscription(mContext) != params[0].destSub) {
                int result = switchSubscriptionTo(params[0]);
                txnSwitchResult.setResult(result);
                return txnSwitchResult;
            }

            return txnSwitchResult; //no change.
        }

        @Override
            protected void onPostExecute(TxnSwitchResult resultObj) {
                super.onPostExecute(resultObj);
                Log.d(TAG, "onPostExecute(), Thread="+Thread.currentThread().getName());

                int result = resultObj.result;

                if (result == -1) {
                    Log.d(TAG, "No DDS switch required.");
                } else {
                    String status = "Data subscription switch " +
                                    ((result ==1)? "was success.": "failed.");
                    Toast.makeText(mContext, status, Toast.LENGTH_SHORT).show();
                }

                //TODO: Below set of nested conditions are dirty, need a better
                //way.
                if (result == -1 || result == 1) {
                    if (resultObj.req.triggerSwitchOnly == true) {
                        removeAbortNotification(resultObj.req);
                        return;

                    }
                    if(result == -1) {
                        //no change in sub and the trigger was not switch only,
                        //start transaction service without any UI.
                        Log.d(TAG, "Starting transaction service");
                        triggerTransactionService(resultObj.req);
                    } else {
                        //Switch was real and it succeeded, start transaction
                        //service with all UI hoopla
                        removeStatusBarNotification(resultObj.req);
                        showNotificationMmsInProgress(resultObj.req);
                        showNotificationAbortAndSwitchBack(resultObj.req);
                        Log.d(TAG, "Starting transaction service without waiting for PdpUp");
                        triggerTransactionService(resultObj.req);
                    }
                }
            }

        private void removeAbortNotification(TxnRequest req) {
            Log.d(TAG, "removeAbortNotification");
            String ns = Context.NOTIFICATION_SERVICE;
            NotificationManager mNotificationManager = (NotificationManager)
                    mContext.getSystemService(ns);
            mNotificationManager.cancel("ABORT", 2); //ID=2, abort notification
            mNotificationManager.cancel(req.originSub);

        }

        private void showNotificationAbortAndSwitchBack(TxnRequest req) {
            Log.d(TAG, "showNotificationAbortAndSwitchBack");
            String ns = Context.NOTIFICATION_SERVICE;
            NotificationManager mNotificationManager = (NotificationManager)
                    mContext.getSystemService(ns);
            //TODO: use the proper messaging icon
            int icon = android.R.drawable.stat_notify_chat;

            long when = System.currentTimeMillis();

            Notification notification = new Notification(icon, null, when);

            Intent src = new Intent();
            src.putExtra("TRIGGER_SWITCH_ONLY", 1);
            src.putExtra(Mms.SUB_ID, req.originSub); /* since it is abort, we want to switch
                                                 to where we came from.*/
            src.putExtra(MultiSimUtility.ORIGIN_SUB_ID, -1); /* since it is trigger_switch_only,
                                                 origin is irrelevant.*/

            Intent notificationIntent = new Intent(mContext,
                    com.android.mms.ui.SelectMmsSubscription.class);
            notificationIntent.putExtras(src);
            PendingIntent contentIntent = PendingIntent.getService(mContext, 0,
                    notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            notification.setLatestEventInfo(mContext, mContext.getString(R.string.abort_mms),
                    mContext.getString(R.string.abort_mms_text), contentIntent);

            mNotificationManager.notify("ABORT", 2, notification); //ID=2 for the abort.

        }

        private void showNotificationMmsInProgress(TxnRequest req) {
            Log.d(TAG, "showNotificationMmsInProgress");
            String ns = Context.NOTIFICATION_SERVICE;
            NotificationManager mNotificationManager = (NotificationManager)
                    mContext.getSystemService(ns);
            //TODO: use the proper messaging icon
            int icon = android.R.drawable.stat_notify_chat;

            long when = System.currentTimeMillis();

            Notification notification = new Notification(icon,
                    mContext.getString(R.string.progress_mms_title), when);

            Intent notificationIntent = new Intent(mContext,
                    com.android.mms.transaction.TransactionService.class);
            Bundle tempBundle = req.startUpIntent.getExtras();

            notificationIntent.putExtras(tempBundle); //copy all extras

            PendingIntent contentIntent = PendingIntent.getService(mContext, 0,
                    notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            notification.setLatestEventInfo(mContext, mContext.getString(R.string.progress_mms),
                    mContext.getString(R.string.progress_mms_text), contentIntent);

            mNotificationManager.notify(req.destSub, notification);

        }

        void sleep(int ms) {
            try {
                Log.d(TAG, "Sleeping for "+ms+"(ms)...");
                Thread.currentThread().sleep(ms);
                Log.d(TAG, "Sleeping...Done!");
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

        private int switchSubscriptionTo(TxnRequest req) {
            TelephonyManager tmgr = (TelephonyManager)
                    mContext.getSystemService(Context.TELEPHONY_SERVICE);
            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                Log.d(TAG, "DSDS enabled");
                MSimTelephonyManager mtmgr = (MSimTelephonyManager)
                    mContext.getSystemService (Context.MSIM_TELEPHONY_SERVICE);
                int result = (mtmgr.setPreferredDataSubscription(req.destSub))? 1: 0;
                if (result == 1) { //Success.
                    Log.d(TAG, "Subscription switch done.");

                    while(!isNetworkAvailable()) {
                        Log.d(TAG, "isNetworkAvailable = false, sleep..");
                        sleep(1000);
                    }
                }
                return result;
            }
            return 1;
        }

    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE_MMS);

        return (ni == null ? false : ni.isAvailable());

    }

    private void triggerTransactionService(TxnRequest req) {
        Log.d(TAG, "triggerTransactionService() for "+req);
        Intent svc = new Intent(mContext, com.android.mms.transaction.TransactionService.class);

        Bundle tempBundle = req.startUpIntent.getExtras();
        if (tempBundle != null) {
            svc.putExtras(tempBundle); //copy all extras
        }
        mContext.startService(svc);

    }

    private void removeStatusBarNotification(TxnRequest req) {
        Log.d(TAG, "removeStatusBarNotification");
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager)
                mContext.getSystemService(ns);
        mNotificationManager.cancel(req.destSub);

    }

    public void onCreate() {
        super.onCreate();

        Log.d (TAG, "Create()");
        mContext = getApplicationContext();

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    class TxnRequest {
        Intent startUpIntent;
        int destSub;
        int originSub;
        boolean triggerSwitchOnly;

        TxnRequest(Intent intent, int destSub, int originSub, boolean trigger) {
            this.startUpIntent = intent;
            this.destSub = destSub;
            this.originSub = originSub;
            this.triggerSwitchOnly = trigger;
        }

        TxnRequest(TxnRequest t) {
            this.startUpIntent = t.startUpIntent;
            this.destSub = t.destSub;
            this.originSub = t.originSub;
            this.triggerSwitchOnly = t.triggerSwitchOnly;
        }

        public String toString() {
            return "TxnReq [intent=" + startUpIntent
                + ", destSub=" + destSub
                + ", originSub=" + originSub
                + ", triggerSwitchOnly=" + triggerSwitchOnly
                + "]";
        }

    };

    class TxnSwitchResult {
        TxnRequest req;
        int result;

        TxnSwitchResult(TxnRequest req, int result) {
            this.req = req;
            this.result = result;
        }

        public void setResult(int result) {
            this.result = result;
        }
    };

    private void dumpQ() {
        synchronized (mQueue) {
            for (TxnRequest t : mQueue) {
                Log.d(TAG, "Dump: txn=" + t);
            }
        }
    }
    private void enqueueTxnReq(TxnRequest req) {
        Log.d(TAG, "enqueueTxnReq() = " + req);
        synchronized (mQueue) {
            mQueue.add(req);
        }
    }

    private void setAlarm() {
        Intent service = new Intent(ACTION_ALARM,
                null, mContext, SelectMmsSubscription.class);

        PendingIntent operation = PendingIntent.getService(
                mContext, 0, service, PendingIntent.FLAG_ONE_SHOT);

        AlarmManager am = (AlarmManager) mContext.getSystemService(
                Context.ALARM_SERVICE);

        long delay = SystemProperties.getInt("msim.switch.delay", 30*1000);
        Log.d(TAG, "setAlarm for =" + delay);
        delay = System.currentTimeMillis() + delay;
        am.set(AlarmManager.RTC, delay, operation);
    }

    private void processQ() {
        int currentDds = MultiSimUtility.getCurrentDataSubscription(mContext);

        TxnRequest switchReq = null;
        TxnRequest otherSubReq = null;

        int count = 0;

        synchronized (mQueue) {
            for(int i=0;i<mQueue.size();i++) {
                TxnRequest t = mQueue.get(i);
                if (t.destSub == currentDds) {
                    //Process the real MMS requests or "switch only" requests for
                    //current Dds.
                    if (t.triggerSwitchOnly == false) {
                        //Trigger txnServ for all the Queued transaction for the current Dds.
                        triggerTransactionService(t);
                    } else {
                        //switch only request for current Dds. Ignore. We are already in
                        //correct Dds.
                        Log.d(TAG, "processQ: ignore req="+t);
                    }
                    mQueue.remove(t);
                } else {
                    if (t.triggerSwitchOnly == false) {
                        count++;
                        if (otherSubReq == null) {
                            otherSubReq = t;
                        }
                    } else if (switchReq == null) {
                        //the very first switch to other sub request.
                        switchReq = t;
                    }
                }
            }

            Log.d(TAG, "processQ: count=" + count + ", switchReq=" + switchReq);
            //we are here means, there is no transaction left for current DDS.
            //but there are few for other sub as well as there is a switch request
            //too. Honour it only if TxnServ is idle else start the timer again.
            if (mTransactionService.isIdle()) {
                Log.d(TAG, "TxnServ is idle");
                if (count > 0 ) {
                    if (switchReq != null) {
                        Log.d(TAG, "processQ: requesting Dds switch to switchReq="
                                + switchReq.destSub);
                        switchSubscriptionTask = new SwitchSubscriptionTask();
                        switchSubscriptionTask.execute(switchReq);

                        mQueue.remove(switchReq);
                    } else {
                        //we have MMS request for other sub but switch request
                        //is missing. Lets switch to the first pending MMS destSub.
                        Log.d(TAG, "processQ: requesting Dds swith to otherSubReq="
                                + otherSubReq.destSub);
                        switchSubscriptionTask = new SwitchSubscriptionTask();
                        switchSubscriptionTask.execute(otherSubReq);
                        mQueue.remove(otherSubReq);

                    }
                } else if (switchReq != null){
                    //There is a DdsSwitch request for no real MMS for that
                    //subscription. why to bother about the switch then, ignore
                    //it.
                    Log.e(TAG, "Ignored, DDS switch req without MMS for the sub="
                            + switchReq.destSub);
                    mQueue.remove(switchReq);
                }
            } else if (count>0) {
                Log.d(TAG, "processQ: Bad luck, TxnServ is busy, need to retry again.");
            }

            if (mQueue.size() >0) {
                setAlarm();
            }
        }

    }



    public int onStartCommand(Intent intent, int flags, int startId) {

        if (ACTION_ALARM.equals(intent.getAction())) {
            Log.d(TAG, "Intent=" + intent);
            synchronized (mQueue) {
                if (mQueue.size() >0) {
                    Log.d(TAG, "dumpQ before processQ");
                    dumpQ();
                    processQ();
                    Log.d(TAG, "dumpQ after processQ");
                    dumpQ();
                    return Service.START_NOT_STICKY;
                }
            }
        }

        int currentDds = MultiSimUtility.getCurrentDataSubscription(mContext);

        int destSub = intent.getIntExtra(Mms.SUB_ID, currentDds);
        int originSub = intent.getIntExtra(MultiSimUtility.ORIGIN_SUB_ID, currentDds);
        int triggerSwitchOnly = intent.getIntExtra("TRIGGER_SWITCH_ONLY", 0);

        mTransactionService = TransactionService.getInstance();

        //TODO: enqueu the request when transactionService is not running.

        Log.d(TAG, "Origin sub = " + originSub);
        Log.d(TAG, "Destination sub = " + destSub);
        Log.d(TAG, "triggerSwitchOnly = " + triggerSwitchOnly);
        Log.d(TAG, "currentDds = " + currentDds);

        TxnRequest req = new TxnRequest(intent, destSub, originSub,
                (triggerSwitchOnly == 1)? true : false);

        if (req.destSub == currentDds && req.triggerSwitchOnly != true) {
            Log.d(TAG, "This txn is for current sub, triggerTransactionService, txn=" + req);
            triggerTransactionService(req);
        } else if (!mTransactionService.isIdle()) {
            Log.d(TAG, "This txn is for different sub and txnServ is not idle, Q it, txn="
                    + req);
            synchronized (mQueue) {
                enqueueTxnReq(req);
                dumpQ();
                if (mQueue.size() == 1) {
                    //set alarm only when the very first record is added in Q.
                    setAlarm();
                }
            }
        } else {
            if (req.triggerSwitchOnly == true) {
                Log.d(TAG, "This txn is just for dds switch. txn=" + req);
            } else {
                Log.d(TAG, "This txn is for diff sub and txnServ is idle, init dds switch, txn="
                        + req);
            }
            switchSubscriptionTask = new SwitchSubscriptionTask();
            switchSubscriptionTask.execute(req);
        }

        return Service.START_NOT_STICKY;
    }


    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }

}
