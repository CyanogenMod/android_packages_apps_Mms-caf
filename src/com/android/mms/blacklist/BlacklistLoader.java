package com.android.mms.blacklist;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.provider.Telephony;

/**
 * Loader for blacklist results that can be used with a LoaderManager
 */
public class BlacklistLoader extends AsyncTaskLoader<BlacklistData> {

    private String[] mNumberList;
    private CallBlacklistHelper mBlacklistHelper;
    private ForceLoadContentObserver mObserver;

    public BlacklistLoader(Context context, String[] numberList) {
        super(context);
        mNumberList = numberList;
        mBlacklistHelper = new CallBlacklistHelper(context);

        mObserver = new ForceLoadContentObserver();
        context.getContentResolver().registerContentObserver(
                Telephony.Blacklist.CONTENT_URI, true, mObserver);
    }

    @Override
    public BlacklistData loadInBackground() {
        BlacklistData result = new BlacklistData();
        if (mNumberList != null) {
            for (String number : mNumberList) {
                result.setBlacklist(number,
                        mBlacklistHelper.isBlacklisted(number));
            }
        }

        return result;
    }

    @Override
    protected void onReset() {
        super.onReset();
        getContext().getContentResolver().unregisterContentObserver(mObserver);
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();
        forceLoad();
    }

    @Override
    protected void onStopLoading() {
        super.onStopLoading();
        cancelLoad();
    }
}
