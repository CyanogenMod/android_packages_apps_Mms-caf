package com.android.mms.util;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.android.mms.model.ContactInfo;
import com.cyanogen.ambient.callerinfo.CallerInfoServices;
import com.cyanogen.ambient.callerinfo.extension.CallerInfo;
import com.cyanogen.ambient.callerinfo.extension.LookupRequest;
import com.cyanogen.ambient.callerinfo.results.LookupByNumberResult;
import com.cyanogen.ambient.callerinfo.util.CallerInfoHelper;
import com.cyanogen.ambient.callerinfo.util.ProviderInfo;
import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyanogen.ambient.common.api.PendingResult;
import com.cyanogen.ambient.common.api.ResultCallback;

/**
 * @author Rohit Yengisetty
 */
public class ContactInfoUpdater extends HandlerThread implements Handler.Callback {

    private static final int MSG_FETCH_INFO = 0;
    private AmbientApiClient mAmbientClient;
    private Context mContext;
    private Handler mHandler;

    public ContactInfoUpdater(String name, Context ctx) {
        super(name);
        mContext = ctx;
    }

    public ContactInfoUpdater(String name, int priority, Context ctx) {
        super(name, priority);
        mContext = ctx;
    }

//    private Handler mHandler = new Handler() {
//        @Override
//        public void handleMessage(Message msg) {
//            int request = msg.what;
//            switch (request) {
//                case MSG_FETCH_INFO:
//                    if (!TextUtils.isEmpty(number) && ambientClient != null &&
//                            (ambientClient.isConnecting() || ambientClient.isConnected())) {
//
//                        final String normalizedNumber = PhoneNumberUtils.formatNumberToE164(number, countryIso);
//                        final String formattedNumber = formatPhoneNumber(number, null, countryIso);
//                        LookupRequest lookupRequest = new LookupRequest(normalizedNumber,
//                                LookupRequest.ORIGIN_CODE_HISTORY);
//                        PendingResult<LookupByNumberResult> result = CallerInfoServices.CallerInfoApi.
//                                lookupByNumber(ambientClient, lookupRequest);
//                        result.setResultCallback(new ResultCallback<LookupByNumberResult>() {
//                            @Override
//                            public void onResult(LookupByNumberResult lookupByNumberResult) {
//                                if (!lookupByNumberResult.getStatus().isSuccess()) {
//                                    return;
//                                }
//
//                                CallerInfo callerInfo = lookupByNumberResult.getCallerInfo();
//
//                                // exit if caller info search didn't yield usable results
//                                if (callerInfo == null || !hasUsableInfo(callerInfo)) {
//                                    return;
//                                }
//
//                                ContactBuilder contactBuilder = new ContactBuilder(
//                                        ContactBuilder.REVERSE_LOOKUP, normalizedNumber, formattedNumber);
//                                contactBuilder.setName(ContactBuilder.Name.
//                                        createDisplayName(callerInfo.getName()));
//            }
//        }
//    };

    public void init() {
        start();
        mHandler = new Handler(getLooper(), this);
        // Delegate lookup calls to a caller-info-provider, if available
        if (CallerInfoServiceManager.isAvailable(mContext)) {
            mAmbientClient = CallerInfoServiceManager.getClient(mContext);
            mAmbientClient.connect();
        }
    }

    public void fetchInfo(String number, InfoUpdateListener callback) {
        Message msg = Message.obtain();
        msg.what = MSG_FETCH_INFO;
        msg.obj = new InfoRequest(number, callback);
        mHandler.sendMessage(msg);
    }

    @Override
    public boolean handleMessage(Message msg) {
        int what = msg.what;
        final InfoRequest request = (InfoRequest) msg.obj;
        String number = request.mPhoneNumber;

        switch (what) {
            case MSG_FETCH_INFO:
                
                if (!TextUtils.isEmpty(number) && mAmbientClient != null &&
                        (mAmbientClient.isConnecting() || mAmbientClient.isConnected())) {

                    final String normalizedNumber = number; // PhoneNumberUtils.formatNumberToE164(number, countryIso);
                    LookupRequest lookupRequest = new LookupRequest(normalizedNumber,
                            LookupRequest.ORIGIN_CODE_HISTORY);
                    PendingResult<LookupByNumberResult> result = CallerInfoServices.CallerInfoApi.
                            lookupByNumber(mAmbientClient, lookupRequest);
                    result.setResultCallback(new ResultCallback<LookupByNumberResult>() {
                        @Override
                        public void onResult(LookupByNumberResult lookupByNumberResult) {
                            if (!lookupByNumberResult.getStatus().isSuccess()) {
                                return;
                            }

                            CallerInfo callerInfo = lookupByNumberResult.getCallerInfo();

                            // exit if caller info search didn't yield usable results
//                            if (callerInfo == null || !hasUsableInfo(callerInfo)) {
                            if (callerInfo == null) {
                                return;
                            }

                            // Construct contact info object
                            ContactInfo contactInfo = new ContactInfo();
                            contactInfo.mName = callerInfo.getName();
                            // call listener w/ callback
                            request.mCallback.onNewInfo(normalizedNumber, contactInfo);
                        }
                    });
                }
                break;
        }
        return true;
    }

    private static class InfoRequest {
        private String mPhoneNumber;
        private InfoUpdateListener mCallback;

        public InfoRequest(String phoneNumber, InfoUpdateListener callback) {

        }
    }

    public interface InfoUpdateListener {
        void onNewInfo(String phoneNumber, ContactInfo contactInfo);
    }

}
