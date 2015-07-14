package com.android.mms.blacklist;

import android.telephony.PhoneNumberUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Data structure to track blacklist status for a set of numbers
 */
public class BlacklistData {

    private Map<String, Boolean> mBlacklist = new HashMap<String, Boolean>();

    public void setBlacklist(String number, boolean isBlacklist) {
        String nn = PhoneNumberUtils.normalizeNumber(number);
        mBlacklist.put(nn, isBlacklist);
    }

    public boolean isBlacklisted(String number) {
        String nn = PhoneNumberUtils.normalizeNumber(number);
        Boolean result = mBlacklist.get(nn);
        return result != null ? result : false; // default to false
    }

    public void setFrom(BlacklistData other) {
        mBlacklist = other.mBlacklist;
    }
}
