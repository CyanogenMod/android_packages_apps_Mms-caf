package com.android.mms.blacklist;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import com.android.mms.MmsApp;
import com.android.mms.R;
import com.android.mms.util.MetricsHelper;

public class BlockCallerDialogFragment extends DialogFragment 
        implements DialogInterface.OnClickListener {

    public static final String NUMBERS_EXTRA = "numbers";

    public static final String ORIGIN_EXTRA = "origin";
    private static final int ORIGIN_UNKNOWN = -1;
    public static final int ORIGIN_INCALL_NOTIFICATION = 0;
    public static final int ORIGIN_INCALL_ACTIVITY = 1;
    public static final int ORIGIN_CONTACT_CARD = 2;
    private String mProgressMessage;

    private CheckBox mCheckBox;
    private String[] mNumbers;
    private FinishListener mFinishListener;
    private int mOrigin;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (getArguments() != null) {
            Bundle bundle = getArguments();
            mNumbers = bundle.getStringArray(NUMBERS_EXTRA);
            mOrigin = bundle.getInt(ORIGIN_EXTRA, ORIGIN_UNKNOWN);
        }

        mProgressMessage = getResources().getString(R.string.block_dialog_progress_message);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        LayoutInflater inflater = (LayoutInflater)getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View v = inflater.inflate(R.layout.block_dialog_view, null);
        mCheckBox = (CheckBox)v.findViewById(R.id.spamCheckbox);

        // Should be provider.getTitle();
        String providerTitle = "Truecaller"; // [TODO][MSB]: Get provider title somehow
        mCheckBox.setText(getActivity().getResources().getString(
                R.string.block_dialog_report_spam, providerTitle));
        mCheckBox.setVisibility(View.VISIBLE);

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
            for (String number : mNumbers) {
                MmsApp.getApplication().markAsSpam(number);
            }
            if (mFinishListener != null) {
                mFinishListener.onFinish();
            }
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
        new BlacklistAsyncTask(mNumbers.length).execute(mNumbers);
    }

    public interface FinishListener {
        public void onFinish();
    }

    public void setFinishListener(FinishListener listener) {
        mFinishListener = listener;
    }

    private class BlacklistAsyncTask extends AsyncTask<String, Integer, Void> {

        private ProgressDialog mProgressDialog;
        private int mCount;
        private String mCurrentNumber;
        private BlacklistAsyncTask(int count) {
            mCount = count;
        }

        @Override
        public void onPreExecute() {
            mProgressDialog = new ProgressDialog(getActivity());
            mProgressDialog.setTitle(R.string.block_dialog_title);
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setMessage(String.format(mProgressMessage, "..."));
            mProgressDialog.setMax(mCount);
            mProgressDialog.show();
        }

        @Override
        public void onProgressUpdate(Integer... args) {
            if (args.length > 0) {
                int progress = args[0];
                mProgressDialog.setProgress(progress);
                mProgressDialog.setMessage(String.format(mProgressMessage, mCurrentNumber));
            }
        }

        @Override
        protected Void doInBackground(String... numbers) {
            CallBlacklistHelper helper = new CallBlacklistHelper(getActivity());
            int i = 0;
            for (String number : numbers) {
                helper.addToBlacklist(number);
                i++;
                mCurrentNumber = number;
                this.publishProgress(i);
            }
            return null;
        }

        @Override
        public void onPostExecute(Void result) {
            mProgressDialog.dismiss();
            dismiss();
        }

    }
}
