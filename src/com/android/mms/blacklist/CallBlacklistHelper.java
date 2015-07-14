package com.android.mms.blacklist;

import android.content.Context;
import android.telephony.PhoneNumberUtils;
import com.android.internal.telephony.util.BlacklistUtils;

public class CallBlacklistHelper {

    private Context mContext;

    public CallBlacklistHelper(Context context) {
        mContext = context;
    }

    public void addToBlacklist(String number) {
        setBlacklisted(number, true);
    }

    public void removeFromBlacklist(String number) {
        setBlacklisted(number, false);
    }

    public void setBlacklisted(String number, boolean isBlacklisted) {
        String nn = PhoneNumberUtils.normalizeNumber(number);
        BlacklistUtils.addOrUpdate(mContext, nn,
                isBlacklisted ? BlacklistUtils.BLOCK_CALLS | BlacklistUtils.BLOCK_MESSAGES
                        : 0, BlacklistUtils.BLOCK_CALLS | BlacklistUtils.BLOCK_MESSAGES);
    }

    public boolean isBlacklisted(String number) {
        String nn = PhoneNumberUtils.normalizeNumber(number);
        return BlacklistUtils.isListed(mContext, nn, BlacklistUtils.BLOCK_CALLS)
                != BlacklistUtils.MATCH_NONE;
    }
}
