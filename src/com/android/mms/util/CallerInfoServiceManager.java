package com.android.mms.util;

import android.content.Context;
import android.util.Log;
import com.cyanogen.ambient.callerinfo.util.CallerInfoHelper;
import com.cyanogen.ambient.callerinfo.CallerInfoServices;
import com.cyanogen.ambient.common.CyanogenAmbientUtil;
import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyanogen.ambient.common.api.AmbientApiClient.OnDisconnectionListener;
import com.cyanogen.ambient.common.api.AmbientApiClient.OnConnectionFailedListener;
import com.cyanogen.ambient.common.api.AmbientApiClient.ConnectionCallbacks;
import com.cyanogen.ambient.common.ConnectionResult;
import android.os.Bundle;

/**
 * Utility class to interact with the Delegator of the installed caller info services
 */
public class CallerInfoServiceManager {

    private static final String TAG = "CallerInfoServiceManager";
    private static AmbientApiClient sClient;

    public static AmbientApiClient getClient(Context context) {
        if (sClient == null) {
            sClient =
                    new AmbientApiClient.Builder(context.getApplicationContext())
                            .addApi(CallerInfoServices.API).build();
            sClient.registerConnectionFailedListener(
                    new OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(ConnectionResult result) {
                            Log.w(TAG, "CallerInfo connection failed: " + result);
                        }
                    });
            sClient.registerDisconnectionListener(
                    new OnDisconnectionListener() {
                        @Override
                        public void onDisconnection() {
                            Log.d(TAG, "CallerInfo connection disconnected");
                        }
                    });
            sClient.registerConnectionCallbacks(
                    new ConnectionCallbacks() {
                        @Override
                        public void onConnected(Bundle connectionHint) {
                            Log.d(TAG, "CallerInfo connection established");
                        }

                        @Override
                        public void onConnectionSuspended(int cause) {
                            Log.d(TAG, "CallerInfo connection suspended");
                        }
                    });

        }
        return sClient;
    }

    public static boolean isAvailable(Context context) {
        boolean ambientAvailable = CyanogenAmbientUtil.isCyanogenAmbientAvailable(context) == CyanogenAmbientUtil.SUCCESS;
        boolean activeProvider = CallerInfoHelper.hasActiveProvider(context);
        Log.d(TAG, "ambientAvailable=" + ambientAvailable +
                ", activeProvider=" + activeProvider);
        return ambientAvailable && activeProvider;
    }
}

