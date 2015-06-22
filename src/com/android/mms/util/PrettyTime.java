package com.android.mms.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.text.format.DateFormat;
import org.joda.time.DateTime;

import com.android.mms.R;
import org.joda.time.DateTimeConstants;

public class PrettyTime {

    public static enum WeekBucket {TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, OLDER};
    private static int[] WEEK_TEXT = {
            R.string.conversation_list_today_header,
            R.string.conversation_list_yesterday_header,
            R.string.conversation_list_this_week_header,
            R.string.conversation_list_last_week_header,
            R.string.conversation_list_older_header
    };

    private SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a");
    private SimpleDateFormat dateFormat =
            new SimpleDateFormat(
                    DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd"));

    private SimpleDateFormat fullDateFormat =
            new SimpleDateFormat(
                    DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMM dd, yyyy"));

    private SimpleDateFormat eventFormat =
            new SimpleDateFormat("yyyy-MM-dd");

    public String format(Context context, long epoch) {
        return format(context, new Date(epoch));
    }

    /**
     * Attempt to convert string date from content provider to a better format
     *
     * @param date
     * @return formatted date, or original string if could not format
     */
    public String formatEvent(String date) {
        Date parsedDate = null;
        try {
            parsedDate = eventFormat.parse(date);
        }
        catch (ParseException e) {
            return date;
        }

        return fullDateFormat.format(parsedDate);
    }

    /**
     * Formats a timestamp relative to today
     *
     * @param context
     * @param date
     * @return formatted date string
     */
    public String format(Context context, Date date) {
        DateTime callDate = new DateTime(date);
        DateTime now = getNow();

        DateTime startOfToday = now.withTimeAtStartOfDay();
        DateTime yesterday = startOfToday.minusDays(1);
        DateTime twoDaysAgo = startOfToday.minusDays(2);
        DateTime threeDaysAgo = startOfToday.minusDays(3);

        if (callDate.isAfter(startOfToday)) {
            // 9:13 am
            return getTimeFormat(date);
        } else if (callDate.isAfter(yesterday)) {
            // yesterday at 12:30 pm
            String time = getTimeFormat(date);
            return context.getResources().getString(R.string.pretty_format_yesterday, time);
        } else if (callDate.isAfter(twoDaysAgo)) {
            // 2 days ago
            return context.getResources().getString(R.string.pretty_format_x_days_ago, 2);
        } else if (callDate.isAfter(threeDaysAgo)) {
            // 3 days ago
            return context.getResources().getString(R.string.pretty_format_x_days_ago, 3);
        } else {
            // July 12
            return dateFormat.format(date);
        }
    }

    /**
     * Buckets a week relative to today
     *
     * @param timestamp
     * @return bucket enum
     */
    public WeekBucket getWeekBucket(long timestamp) {
        // monday is ISO first day of week
        DateTime dateTime = new DateTime(timestamp);
        DateTime now = getNow();
        DateTime startOfToday = now.withTimeAtStartOfDay();
        DateTime yesterday = startOfToday.minusDays(1);
        DateTime startOfWeek =
                now.withDayOfWeek(DateTimeConstants.MONDAY).dayOfMonth().roundFloorCopy();
        DateTime startOfPreviousWeek = startOfWeek.minusWeeks(1);

        if (dateTime.isAfter(startOfToday)) {
            return WeekBucket.TODAY;
        } else if (dateTime.isAfter(yesterday)) {
            return WeekBucket.YESTERDAY;
        } else if (dateTime.isAfter(startOfWeek)) {
            return WeekBucket.THIS_WEEK;
        } else if (dateTime.isAfter(startOfPreviousWeek)) {
            return WeekBucket.LAST_WEEK;
        } else {
            return WeekBucket.OLDER;
        }
    }

    /**
     * String representation of a week bucket
     *
     * @param context
     * @param bucket
     * @return formatted string
     */
    public String formatWeekBucket(Context context, WeekBucket bucket) {
        return context.getResources().getString(WEEK_TEXT[bucket.ordinal()]);
    }

    private String getTimeFormat(Date date) {
        String time = timeFormat.format(date);
        return time.toLowerCase();
    }

    protected DateTime getNow() {
        return new DateTime();
    }
}
