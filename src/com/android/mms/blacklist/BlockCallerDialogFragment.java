package com.android.mms.blacklist;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import com.android.mms.MmsApp;
import com.android.mms.R;
import com.android.mms.util.MetricsHelper;
//import com.cyanogen.ambient.callerinfo.CallerInfoServices;
//import com.cyanogen.ambient.callerinfo.util.CallerInfoHelper;
//import com.cyanogen.ambient.callerinfo.util.ProviderInfo;
//import com.cyanogen.ambient.common.CyanogenAmbientUtil;
//import com.cyanogen.ambient.common.api.AmbientApiClient;
//import com.cyanogen.ambient.common.api.PendingResult;
//import com.cyanogen.ambient.common.api.Result;
//import com.cyanogen.ambient.common.api.ResultCallback;

public class BlockCallerDialogFragment extends DialogFragment 
        implements DialogInterface.OnClickListener {

    public static final String NUMBERS_EXTRA = "numbers";

    public static final String ORIGIN_EXTRA = "origin";
    private static final int ORIGIN_UNKNOWN = -1;
    public static final int ORIGIN_INCALL_NOTIFICATION = 0;
    public static final int ORIGIN_INCALL_ACTIVITY = 1;
    public static final int ORIGIN_CONTACT_CARD = 2;

    private CheckBox mCheckBox;
    private String[] mNumbers;
//    private AmbientApiClient mClient;
    private FinishListener mFinishListener;
    private int mOrigin;
//    private ProviderInfo mProviderInfo;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (getArguments() != null) {
            Bundle bundle = getArguments();
            mNumbers = bundle.getStringArray(NUMBERS_EXTRA);
            mOrigin = bundle.getInt(ORIGIN_EXTRA, ORIGIN_UNKNOWN);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = (LayoutInflater)getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View v = inflater.inflate(R.layout.block_dialog_view, null);
        mCheckBox = (CheckBox)v.findViewById(R.id.spamCheckbox);

//        mProviderInfo = CallerInfoHelper.getActiveProviderInfo(getActivity());
//        if (CyanogenAmbientUtil.isCyanogenAmbientAvailable(getActivity())
//                == CyanogenAmbientUtil.SUCCESS && mProviderInfo != null) {
//            mCheckBox.setText(getActivity().getResources().getString(
//                    R.string.block_dialog_report_spam, mProviderInfo.getTitle()));
//            mCheckBox.setVisibility(View.VISIBLE);
//        } else {
//            mCheckBox.setVisibility(View.GONE);
//        }
        mCheckBox.setVisibility(View.GONE);

        builder
            .setTitle(R.string.block_dialog_title)
            .setView(v)
            .setPositiveButton(R.string.block_dialog_positive, this)
            .setNegativeButton(R.string.block_dialog_negative, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (mFinishListener != null) {
                        mFinishListener.onFinish();
                    }
                }
            });

        MetricsHelper.init(MmsApp.getApplication());

        return builder.create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        MetricsHelper.Actions action;
        if (mCheckBox.isChecked()) {
//            AmbientApiClient.Builder builder = new AmbientApiClient.Builder(getActivity());
//            builder.addApi(CallerInfoServices.API);
//            builder.addConnectionCallbacks(new AmbientApiClient.ConnectionCallbacks() {
//                @Override
//                public void onConnected(Bundle connectionHint) {
//                    PendingResult<Result> result = null;
//                    for (String number : mNumbers) {
//                        result = CallerInfoServices.CallerInfoApi.markAsSpam(mClient, number);
//                    }
//                    result.setResultCallback(new ResultCallback<Result>() {
//                        @Override
//                        public void onResult(final Result lookupByNumberResult) {
//                            mClient.disconnect();
//                            if (mFinishListener != null) {
//                                mFinishListener.onFinish();
//                            }
//                        }
//                    });
//                }
//
//                @Override
//                public void onConnectionSuspended(int cause) {
//                }
//            });
//            mClient = builder.build();
//            mClient.connect();
            action = MetricsHelper.Actions.BLOCK_SPAM_CALL;
        } else {
            action = MetricsHelper.Actions.BLOCK_CALL;
        }
        MetricsHelper.State state = null;
        if (mOrigin == ORIGIN_CONTACT_CARD) {
            state = MetricsHelper.State.CONTACT_CARD;
        } else if (mOrigin == ORIGIN_INCALL_ACTIVITY) {
            state = MetricsHelper.State.INCALL_SCREEN;
        } else if (mOrigin == ORIGIN_INCALL_NOTIFICATION) {
            state = MetricsHelper.State.INCALL_NOTIFICATION;
        }
        MetricsHelper.Field field = null;
//        if (mProviderInfo != null) {
//            field = new MetricsHelper.Field(MetricsHelper.Fields.PROVIDER_PACKAGE_NAME,
//                    mProviderInfo.getPackageName());
//        }
        MetricsHelper.sendEvent(MetricsHelper.Categories.BLOCKED_CALLS,
                action, state, field);
        CallBlacklistHelper helper = new CallBlacklistHelper(getActivity());
        for (String number : mNumbers) {
            helper.addToBlacklist(number);
        }
        dismiss();
    }

    public interface FinishListener {
        public void onFinish();
    }

    public void setFinishListener(FinishListener listener) {
        mFinishListener = listener;
    }
}
