package com.android.mms.ui;

import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.os.Bundle;
import android.telephony.SubscriptionManager;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.view.View;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.android.mms.R;

public class MSimSpinnerAdapter extends ResourceCursorAdapter {
    private static final String[] PROJECTION = {
        SubscriptionManager.UNIQUE_KEY_SUBSCRIPTION_ID,
        SubscriptionManager.DISPLAY_NAME,
        SubscriptionManager.SIM_SLOT_INDEX
    };
    private static final String SORT_ORDER = SubscriptionManager.SIM_SLOT_INDEX + " ASC";
    private static final int INACTIVE_CARD_ALPHA = 128; // 50%
    private static final RelativeSizeSpan POSTFIX_SPAN = new RelativeSizeSpan(0.7f);

    private Context mContext;

    public MSimSpinnerAdapter(Context context) {
        super(context, android.R.layout.simple_spinner_item, null, 0);
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mContext = context;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        TextView textView = (TextView) view.findViewById(android.R.id.text1);
        int subId = cursor.getInt(0);
        String name = cursor.getString(1);
        int slotIndex = cursor.getInt(2);

        if (slotIndex >= 0) {
            SpannableStringBuilder builder = new SpannableStringBuilder(name);
            final String postfix = mContext.getString(R.string.sim_spinner_active_item_postfix,
                    slotIndex + 1);
            builder.append("   ");
            builder.append(postfix, POSTFIX_SPAN, 0);
            textView.setText(builder);
        } else {
            textView.setText(name);
        }

        ColorStateList origColors = (ColorStateList) view.getTag();
        if (subId == SubscriptionManager.DEFAULT_SUBSCRIPTION_ID || slotIndex >= 0) {
            if (origColors != null) {
                textView.setTextColor(origColors);
                textView.setTag(null);
            }
        } else {
            if (origColors == null) {
                origColors = textView.getTextColors();
                textView.setTag(origColors);
                textView.setTextColor(origColors.withAlpha(INACTIVE_CARD_ALPHA));
            }
        }
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
            setSelection(SubscriptionManager.SIM_SLOT_INDEX + " >= 0");
            final Cursor activeCursor = super.loadInBackground();
            if (activeCursor == null) {
                return null;
            }

            setSelection(SubscriptionManager.SIM_SLOT_INDEX + " < 0");
            final Cursor inactiveCursor = super.loadInBackground();

            MatrixCursor allSimsCursor = new MatrixCursor(PROJECTION);
            allSimsCursor.addRow(new Object[] {
                    Integer.valueOf(SubscriptionManager.DEFAULT_SUBSCRIPTION_ID),
                    getContext().getString(R.string.all_spinner_option),
                    Integer.valueOf(SubscriptionManager.INVALID_SIM_SLOT_INDEX)
            });

            final Cursor[] cursors;
            if (inactiveCursor != null) {
                cursors = new Cursor[] { allSimsCursor, activeCursor, inactiveCursor };
            } else {
                cursors = new Cursor[] { allSimsCursor, activeCursor };
            }

            return new MergeCursor(cursors);
        }
    }
}
