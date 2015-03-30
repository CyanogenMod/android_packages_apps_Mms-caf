package com.android.mms.ui;

import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.os.Bundle;
import android.telephony.SubscriptionManager;
import android.widget.SimpleCursorAdapter;

import com.android.mms.R;

public class MSimSpinnerAdapter extends SimpleCursorAdapter {
    private static final String[] PROJECTION = {
        SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID,
        SubscriptionManager.DISPLAY_NAME
    };
    private static final String SORT_ORDER = SubscriptionManager.SIM_SLOT_INDEX + " ASC";

    private static final String[] FROM = {
        SubscriptionManager.DISPLAY_NAME
    };
    private static final int[] TO = {
        android.R.id.text1
    };

    private Context mContext;

    public MSimSpinnerAdapter(Context context) {
        super(context, android.R.layout.simple_spinner_item, null, FROM, TO);
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mContext = context;
    }

    public static class LoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {
        private MSimSpinnerAdapter mAdapter;

        public LoaderCallbacks(MSimSpinnerAdapter adapter) {
            mAdapter = adapter;
        }

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return new SimLoader(mAdapter.mContext);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            mAdapter.swapCursor(data);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            mAdapter.swapCursor(null);
        }
    }

    private static class SimLoader extends CursorLoader {
        public SimLoader(Context context) {
            super(context, SubscriptionManager.CONTENT_URI, PROJECTION, null, null, SORT_ORDER);
        }

        @Override
        public Cursor loadInBackground() {
            final Cursor cursor = super.loadInBackground();
            if (cursor == null) {
                return null;
            }

            MatrixCursor allSimsCursor = new MatrixCursor(PROJECTION);
            allSimsCursor.addRow(new Object[] {
                    Integer.valueOf(SubscriptionManager.INVALID_SUBSCRIPTION_ID),
                    getContext().getString(R.string.all_spinner_option)
            });

            return new MergeCursor(new Cursor[] { allSimsCursor, cursor });
        }
    }
}
