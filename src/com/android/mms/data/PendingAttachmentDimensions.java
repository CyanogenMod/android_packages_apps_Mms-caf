package com.android.mms.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;

import java.util.ArrayList;

public class PendingAttachmentDimensions {

    private static final ArrayList<AttachmentDimensions> mAttachmentDimensionses
            = new ArrayList<AttachmentDimensions>();

    private static class AttachmentDimensions {
        Uri uri;
        int width, height;
    }

    public static void queue(Uri uri, int width, int height) {
        synchronized (mAttachmentDimensionses) {
            AttachmentDimensions attachmentDimensions = new AttachmentDimensions();
            attachmentDimensions.uri = uri;
            attachmentDimensions.width = width;
            attachmentDimensions.height = height;
            mAttachmentDimensionses.add(attachmentDimensions);
        }
    }

    public static void processQueue(final Context context) {
        final ContentResolver contentResolver = context.getContentResolver();
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (mAttachmentDimensionses) {
                  ContentValues contentValues = new ContentValues();
                  for (AttachmentDimensions a : mAttachmentDimensionses) {
                      contentValues.clear();
                      contentValues.put("width", a.width);
                      contentValues.put("height", a.height);
                      contentResolver.update(a.uri, contentValues, null, null);
                      System.out.println(a.uri + " " + contentValues.toString());
                  }
                }
            }
        });
        t.start();
    }
}
