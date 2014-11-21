package com.android.mms.ui;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.widget.ArrayAdapter;
import com.android.mms.R;

public class MSimSpinnerAdapter extends ArrayAdapter {

    public MSimSpinnerAdapter(Context context) {
        super(context, android.R.layout.simple_spinner_dropdown_item);

        TelephonyManager mgr =
                (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);

        int count = mgr.getPhoneCount();
        add(context.getString(R.string.all_spinner_option));

        for (int i = 0; i < count; i++) {
            String simName = MessageUtils.getMultiSimName(context, i);
            add(simName);
        }
    }
}
