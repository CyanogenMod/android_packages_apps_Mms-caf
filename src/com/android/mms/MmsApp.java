/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.mms;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.drm.DrmManagerClient;
import android.location.Country;
import android.location.CountryDetector;
import android.location.CountryListener;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.provider.SearchRecentSuggestions;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.contacts.common.ContactPhotoManager;
import com.android.mms.data.Contact;
import com.android.mms.data.Conversation;
import com.android.mms.data.cm.CMMmsDatabaseHelper;
import com.android.mms.layout.LayoutManager;
import com.android.mms.transaction.MessagingNotification;
import com.android.mms.transaction.MmsSystemEventReceiver;
import com.android.mms.transaction.SmsReceiver;
import com.android.mms.transaction.SmsReceiverService;
import com.android.mms.util.DownloadManager;
import com.android.mms.util.DraftCache;
import com.android.mms.util.PduLoaderManager;
import com.android.mms.util.RateController;
import com.android.mms.util.SmileyParser;
import com.android.mms.util.ThumbnailManager;
import com.cyanogen.lookup.phonenumber.LookupHandlerThread;
import com.cyanogen.lookup.phonenumber.provider.LookupProvider;
import com.cyanogen.lookup.phonenumber.request.LookupRequest;
import com.cyanogen.lookup.phonenumber.response.LookupResponse;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class MmsApp extends Application implements Application.ActivityLifecycleCallbacks, LookupRequest.Callback {
    public static final String LOG_TAG = LogTag.TAG;

    private SearchRecentSuggestions mRecentSuggestions;
    private TelephonyManager mTelephonyManager;
    private CountryDetector mCountryDetector;
    private CountryListener mCountryListener;
    private String mCountryIso;
    private static MmsApp sMmsApp = null;
    private PduLoaderManager mPduLoaderManager;
    private ThumbnailManager mThumbnailManager;
    private ContactPhotoManager mContactPhotoManager;
    private DrmManagerClient mDrmManagerClient;

    private short mActivityCount = 0;
    private ConcurrentHashMap<String, LookupResponse> mPhoneNumberLookupCache;
    private LookupHandlerThread mLookupHandlerThread;
    private HashSet<PhoneNumberLookupListener> mLookupListeners;

    @Override
    public void onCreate() {
        super.onCreate();

        if (Log.isLoggable(LogTag.STRICT_MODE_TAG, Log.DEBUG)) {
            // Log tag for enabling/disabling StrictMode violation log. This will dump a stack
            // in the log that shows the StrictMode violator.
            // To enable: adb shell setprop log.tag.Mms:strictmode DEBUG
            StrictMode.setThreadPolicy(
                    new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());
        }

        sMmsApp = this;

        // Load the default preference values
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        // Figure out the country *before* loading contacts and formatting numbers
        mCountryDetector = (CountryDetector) getSystemService(Context.COUNTRY_DETECTOR);
        mCountryListener = new CountryListener() {
            @Override
            public synchronized void onCountryDetected(Country country) {
                mCountryIso = country.getCountryIso();
            }
        };
        mCountryDetector.addCountryListener(mCountryListener, getMainLooper());

        Context context = getApplicationContext();
        mPduLoaderManager = new PduLoaderManager(context);
        mThumbnailManager = new ThumbnailManager(context);

        MmsConfig.init(this);
        Contact.init(this);
        DraftCache.init(this);
        Conversation.init(this);
        DownloadManager.init(this);
        RateController.init(this);
        LayoutManager.init(this);
        SmileyParser.init(this);
        MessagingNotification.init(this);

        activePendingMessages();

        // go through the intial setup for Contact Info lookup
        // TODO: need ctx in contructor ?
        mLookupHandlerThread = new LookupHandlerThread("PhoneNumberLookupHandler", this);
        mLookupHandlerThread.initialize();
        mPhoneNumberLookupCache = new ConcurrentHashMap<>();
        mLookupListeners = new HashSet<>();
    }

    /**
     * Try to process all pending messages(which were interrupted by user, OOM, Mms crashing,
     * etc...) when Mms app is (re)launched.
     */
    private void activePendingMessages() {
        // For Mms: try to process all pending transactions if possible
        MmsSystemEventReceiver.wakeUpService(this);

        // For Sms: retry to send smses in outbox and queued box
        sendBroadcast(new Intent(SmsReceiverService.ACTION_SEND_INACTIVE_MESSAGE,
                null,
                this,
                SmsReceiver.class));
    }

    synchronized public static MmsApp getApplication() {
        return sMmsApp;
    }

    @Override
    public void onTerminate() {
        mCountryDetector.removeCountryListener(mCountryListener);
        CMMmsDatabaseHelper.closeIfNecessary();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        mPduLoaderManager.onLowMemory();
        mThumbnailManager.onLowMemory();
    }

    @Override
    public Object getSystemService(String name) {
        if (ContactPhotoManager.CONTACT_PHOTO_SERVICE.equals(name)) {
            if (mContactPhotoManager == null) {
                mContactPhotoManager = ContactPhotoManager.createContactPhotoManager(this);
                registerComponentCallbacks(mContactPhotoManager);
                mContactPhotoManager.preloadPhotosInBackground();
            }
            return mContactPhotoManager;
        }

        return super.getSystemService(name);
    }

    public PduLoaderManager getPduLoaderManager() {
        return mPduLoaderManager;
    }

    public ThumbnailManager getThumbnailManager() {
        return mThumbnailManager;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        LayoutManager.getInstance().onConfigurationChanged(newConfig);
    }

    /**
     * @return Returns the TelephonyManager.
     */
    public TelephonyManager getTelephonyManager() {
        if (mTelephonyManager == null) {
            mTelephonyManager = (TelephonyManager)getApplicationContext()
                    .getSystemService(Context.TELEPHONY_SERVICE);
        }
        return mTelephonyManager;
    }

    /**
     * Returns the content provider wrapper that allows access to recent searches.
     * @return Returns the content provider wrapper that allows access to recent searches.
     */
    public SearchRecentSuggestions getRecentSuggestions() {
        /*
        if (mRecentSuggestions == null) {
            mRecentSuggestions = new SearchRecentSuggestions(this,
                    SuggestionsProvider.AUTHORITY, SuggestionsProvider.MODE);
        }
        */
        return mRecentSuggestions;
    }

    // This function CAN return null.
    public String getCurrentCountryIso() {
        if (mCountryIso == null) {
            Country country = mCountryDetector.detectCountry();

            if (country == null) {
                // Fallback to Locale if there are issues with CountryDetector
                return Locale.getDefault().getCountry();
            }

            mCountryIso = country.getCountryIso();
        }
        return mCountryIso;
    }

    public DrmManagerClient getDrmManagerClient() {
        if (mDrmManagerClient == null) {
            mDrmManagerClient = new DrmManagerClient(getApplicationContext());
        }
        return mDrmManagerClient;
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (mActivityCount == 0) {
            // moved setup into onCreate of Application
        }

        mActivityCount++;
        System.out.println("Activity Count : " + mActivityCount);
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        --mActivityCount;
        if (mActivityCount == 0) {
            // tear down the phone number lookup cache and handler thread
            mLookupHandlerThread.quit();
            mLookupHandlerThread = null;
            mPhoneNumberLookupCache.clear();
            mPhoneNumberLookupCache = null;
            mLookupListeners.clear();
            mLookupListeners = null;
        }

        System.out.println("Activity Count : " + mActivityCount);
    }

    /**
     * Get contact info in the form of {@link LookupResponse}, if information
     * has previously been requested and was available
     * @param phoneNumber should be E-164 formatted
     */
    public LookupResponse getPhoneNumberLookupResonse(String phoneNumber) {
        if (phoneNumber == null) return null;
        return mPhoneNumberLookupCache.get(phoneNumber);
    }

    /**
     * Request contact info for a given phone number
     * @param phoneNumber should be E-164 formatted
     */
    public boolean lookupInfoForPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) return false;
        LookupRequest lookupRequest = new LookupRequest(phoneNumber, this);
        return mLookupHandlerThread.fetchInfoForPhoneNumber(lookupRequest);
    }

    /**
     * Registration mechanism for anyone interested in new contact info
     * being available from an external provider. The updates aren't granular
     * as of now - you will be notified of updates to all contact info requests
     */
    public void addPhoneNumberLookupListener(PhoneNumberLookupListener listener) {
        mLookupListeners.add(listener);
    }

    /**
     * Stop getting updates about newly added contact info
     */
    public void removePhoneNumberLookupListener(PhoneNumberLookupListener listener) {
        mLookupListeners.remove(listener);
    }

    @Override
    public void onNewInfo(LookupRequest lookupRequest, LookupResponse response) {
        // called from a another thread
        if (mPhoneNumberLookupCache != null) {
            mPhoneNumberLookupCache.put(lookupRequest.mPhoneNumber, response);
        }

        // notify listeners of new entry
        for (PhoneNumberLookupListener listener : mLookupListeners) {
            listener.onNewInfoAvailable();
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {}

    @Override
    public void onActivityResumed(Activity activity) {}

    @Override
    public void onActivityPaused(Activity activity) {}

    @Override
    public void onActivityStopped(Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

    /**
     * Callbacks for client requesting phone number lookups
     */
    public interface PhoneNumberLookupListener {
        // generic call back when new info is available
        void onNewInfoAvailable();
    }

}
