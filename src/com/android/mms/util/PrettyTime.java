package com.android.mms.util;

import java.text.FieldPosition;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import android.text.format.DateUtils;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import android.content.Context;
import android.text.format.DateFormat;
import org.joda.time.DateTime;

import com.android.mms.R;
import org.joda.time.DateTimeConstants;

public class PrettyTime {

    private Context mContext;
    private DateTime mReferenceTime;

    public PrettyTime() {
        //
    }

    public PrettyTime(Context context) {
        this();
        if (context != null) {
            mContext = context;
            updateReferenceTime();
        }
    }

    public void updateReferenceTime() {
        mReferenceTime = getNow();
    }

    public static enum WeekBucket {TODAY, YESTERDAY, THIS_WEEK, LAST_WEEK, OLDER};
    private static int[] WEEK_TEXT = {
            R.string.conversation_list_today_header,
            R.string.conversation_list_yesterday_header,
            R.string.conversation_list_this_week_header,
            R.string.conversation_list_last_week_header,
            R.string.conversation_list_older_header
    };

    private java.text.DateFormat timeFormat =
            SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT, Locale.getDefault());
    private SimpleDateFormat dateFormat =
            new SimpleDateFormat(
                    DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMdK:mma"));

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

    public String format(long millis) {
        return format(mContext, new Date(millis));
    }

    public String format(Date date) {
        if (mContext == null) {
            throw new IllegalArgumentException("Not a managed instance : construct one using prover");
        }
        return format(mContext, date);
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
        DateTime now = mReferenceTime != null ? mReferenceTime : getNow();
        DateTime justNow = now.minusMinutes(1);
        DateTime halfDay = now.minusHours(12);
        DateTime startOfToday = now.withTimeAtStartOfDay();
        DateTime yesterday = startOfToday.minusDays(1);
        DateTime weekAgo = startOfToday.minusDays(7);

        if (callDate.isAfter(justNow)) {
            // Just now
            return context.getResources().getString(R.string.pretty_format_just_now);
        } else if (callDate.isAfter(halfDay) ) {
            //  1 minute ago
            return getRelativeTime(date);
        } else if (callDate.isAfter(startOfToday)) {
            // 9:13 am
            return getTimeFormat(date);
        } else if (callDate.isAfter(yesterday)) {
            // yesterday at 12:30 pm
            String time = getTimeFormat(date);
            return context.getResources().getString(R.string.pretty_format_yesterday, time);
        } else if (callDate.isAfter(weekAgo)) {
            // Friday , 12:43 pm
            return DateUtils.formatDateTime(context, date.getTime(), DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_WEEKDAY);
        } else {
            // July 12
            return dateFormat.format(date);
        }
    }

    private String getRelativeTime(Date date) {
        return DateUtils.getRelativeTimeSpanString(date.getTime(), System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString();
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

    private static StringBuffer sBuffer1 = new StringBuffer();
    private static StringBuffer sBuffer2 = new StringBuffer();

    public static boolean isWithinSameDay(long millis1, long millis2) {
        return isWithinSameDay(Calendar.getInstance(), millis1, millis2);
    }

    public static boolean isWithinSameDay(Calendar calendar, long millis1, long millis2) {
        // clear buffers
        sBuffer1.setLength(0);
        sBuffer2.setLength(0);

        Date d1 = new Date(millis1);
        Date d2 = new Date(millis2);

        calendar.setTimeInMillis(millis1);
        int date1 = 0;
        date1 = calendar.get(Calendar.DAY_OF_MONTH) << 4 +
                calendar.get(Calendar.DAY_OF_MONTH) << 12 +
                calendar.get(Calendar.YEAR);

        calendar.setTimeInMillis(millis2);
        int date2 = 0;
        date2 = calendar.get(Calendar.DAY_OF_MONTH) << 4 +
                calendar.get(Calendar.DAY_OF_MONTH) << 12 +
                calendar.get(Calendar.YEAR);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String fmt1 = sdf.format(d1, sBuffer1, new FieldPosition(0)).toString();
        String fmt2 = sdf.format(d2, sBuffer2, new FieldPosition(0)).toString();
        // System.out.println("fmt1 : " + fmt1 + " fmt 2: " + fmt2);
        return date1 == date2;
    }

}
