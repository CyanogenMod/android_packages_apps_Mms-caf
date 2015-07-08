package com.android.mms.util;

import android.database.Cursor;

public class CursorUtils {

    public static boolean moveToPosition(Cursor cursor, int position) {
        if (cursor != null) {
            return cursor.moveToPosition(position);
        }

        return false;
    }
}