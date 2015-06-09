package com.android.mms.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IntentUtils {
    /**
     * The number of activities that can handle the baseIntent after filtering
     *
     * @param ctx
     * @param baseIntent
     * @param pkgNamesToFilter
     * @return
     */
    public static int getTargetActivityCount(
            Context ctx, Intent baseIntent, String... pkgNamesToFilter) {
        return getTargetSharedIntents(ctx, baseIntent, pkgNamesToFilter).size();
    }

    private static List<Intent> getTargetSharedIntents(
            Context ctx, Intent baseIntent, String... pkgNamesToFilter) {
        List<Intent> targetedShareIntents = new ArrayList<Intent>();
        List<ResolveInfo> resInfo = ctx.getPackageManager().queryIntentActivities(baseIntent, 0);
        for (ResolveInfo resolveInfo : resInfo) {
            String packageName = resolveInfo.activityInfo.packageName;
            if (pkgNamesToFilter == null ||
                    Arrays.binarySearch(pkgNamesToFilter, packageName) < 0) {
                Intent newIntent = new Intent(baseIntent);
                newIntent.setPackage(packageName);
                targetedShareIntents.add(newIntent);
            }
        }
        return targetedShareIntents;
    }
}
