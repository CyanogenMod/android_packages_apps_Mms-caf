/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
       * Redistributions of source code must retain the above copyright
         notice, this list of conditions and the following disclaimer.
       * Redistributions in binary form must reproduce the above
         copyright notice, this list of conditions and the following
         disclaimer in the documentation and/or other materials provided
         with the distribution.
       * Neither the name of The Linux Foundation nor the names of its
         contributors may be used to endorse or promote products derived
         from this software without specific prior written permission.

   THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
   WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
   MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
   ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
   BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
   CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
   SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
   BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
   WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
   OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
   IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.mms.model;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.w3c.dom.events.Event;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Telephony.Mms.Part;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.vcard.ImportRequest;
import com.android.mms.data.Contact;
import com.android.mms.presenters.SimpleAttachmentPresenter;
import com.android.mms.presenters.SimpleAttachmentPresenter.SimpleAttachmentLoaded;
import com.android.mms.presenters.SimplePresenterModel;
import com.android.mms.ui.Presenter;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ItemLoadedFuture;
import com.android.vcard.VCardEntry;
import com.android.vcard.VCardEntryConstructor;
import com.android.vcard.VCardEntryCounter;
import com.android.vcard.VCardEntryHandler;
import com.android.vcard.VCardParser;
import com.android.vcard.VCardParser_V21;
import com.android.vcard.VCardParser_V30;
import com.android.vcard.VCardSourceDetector;
import com.android.vcard.exception.VCardException;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.android.mms.R;
import com.android.vcard.exception.VCardNestedException;
import com.android.vcard.exception.VCardVersionException;

public class VcardModel extends MediaModel implements SimplePresenterModel {
    private static final String TAG = MediaModel.TAG;

    private String mLookupUri = null;

    public VcardModel(Context context, Uri uri) throws MmsException {
        this(context, ContentType.TEXT_VCARD, null, uri);
        System.out.println("1 " + uri);
        initModelFromUri(uri);
    }

    public VcardModel(Context context, String contentType, String src, Uri uri)
            throws MmsException {
        super(context, SmilHelper.ELEMENT_TAG_REF, contentType, src, uri);
        System.out.println("2 " + src + " " + uri);
        if (!TextUtils.isEmpty(src)) {
            initLookupUri(uri);
        }
    }

    private void initModelFromUri(Uri uri) throws MmsException {
        String scheme = uri.getScheme();
        if (scheme == null) {
            Log.e(TAG, "The uri's scheme is null.");
            return;
        }

        if (scheme.equals("file")) {
            mSrc = getFileSrc(uri);
            System.out.println("Src " + mSrc);
        } else if (scheme.equals("content")){
            Cursor c = null;
            try {
                c = getContentCursor(uri);
                System.out.println("Dump cursor " + DatabaseUtils.dumpCursorToString(c));
                mLookupUri = getLookupUri(uri,c);
                mSrc = getLookupSrc(uri,c);
            } finally {
                if (c != null) {
                    c.close();
                    c = null;
                } else {
                    throw new MmsException("Bad URI: " + uri);
                }
            }
        }
        initMediaDuration();
    }

    private String getFileSrc(Uri uri) {
        String path = uri.toString();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private Cursor getContentCursor(Uri uri) {
        if(isMmsUri(uri)) {
            return mContext.getContentResolver().query(uri, null, null, null, null);
        }
        return mContext.getContentResolver()
                .query(getExtraLookupUri(uri), null, null, null, null);
    }

    private String getLookupUri(Uri uri, Cursor c) {
        if(isMmsUri(uri))
            return getMmsLookupUri(uri,c);
        return getExtraLookupUri(uri).toString();
    }

    private String getMmsLookupUri(Uri uri, Cursor c) {
        if (c != null && c.moveToFirst())
            return c.getString(c.getColumnIndexOrThrow(Part.CONTENT_DISPOSITION));
        return "";
    }

    private Uri getExtraLookupUri(Uri uri) {
        String lookup = uri.getLastPathSegment();
        Uri lookupUri =
            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookup);
        return lookupUri;
    }

    private String getLookupSrc(Uri uri, Cursor c) throws MmsException {
        if(isMmsUri(uri))
            return getMmsLookupSrc(c);
        return getExtraSrc(uri,c);
    }

    private String getMmsLookupSrc(Cursor c) {
        if (c != null && c.moveToFirst()) {
            String path = c.getString(c.getColumnIndexOrThrow(Part._DATA));
            return path.substring(path.lastIndexOf('/') + 1);
        }
        return "";
    }

    private String getExtraSrc(Uri uri, Cursor c) throws MmsException {
        // Do we have a multi-contact uri...
        String[] contacts = uri.getLastPathSegment().split(":");
        if (contacts.length > 1) {
            // several contacts
            return "contacts.vcf";
        }
        if (c != null && c.moveToFirst()) {
            if (c.getCount() == 1) {
                String displayName = c.getString(c.getColumnIndexOrThrow(ContactsContract
                    .Contacts.DISPLAY_NAME));
                if (displayName != null) {
                    return displayName + ".vcf";
                } else {
                    // Contact has no name, so we'll call it "Unknown"
                    // TODO: make this a translateable string (like in Contacts)
                    return "Unknown.vcf";
                }
            }
            // several contacts
            return "contacts.vcf";
        }
        throw new MmsException("Type of media is unknown.");
    }

    private void initLookupUri(Uri uri) {
        if (isMmsUri(uri)) {
            ContentResolver cr = mContext.getContentResolver();
            Cursor c = cr.query(uri, null, null, null, null);
            try {
                if (c != null && c.moveToFirst()) {
                    mLookupUri = c.getString(c.getColumnIndexOrThrow(Part.CONTENT_DISPOSITION));
                }
            } finally {
                if (c != null) {
                    c.close();
                    c = null;
                }
            }
        }
    }

    public String getLookupUri() {
        return mLookupUri;
    }

    @Override
    public void handleEvent(Event evt) {
    }

    @Override
    protected boolean isPlayable() {
        return false;
    }

    @Override
    protected void initMediaDuration() throws MmsException {
        mDuration = 0;
    }

    public static Bitmap getRoundedCornerBitmap(Bitmap bitmap) {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
            bitmap.getHeight(), Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
       
        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
       
        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawCircle(bitmap.getWidth() / 2,  bitmap.getHeight() / 2, bitmap.getWidth() / 2, paint);
       
        paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
       
        return output;
   }

    @Override
    public void loadData(
            final ItemLoadedCallback<SimpleAttachmentLoaded> itemLoadedCallback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ContentResolver resolver = mContext.getContentResolver();
                VCardEntryConstructor constructor = new VCardEntryConstructor();
                constructor.addEntryHandler(new VCardEntryHandler() {
                    @Override
                    public void onStart() {
                    }

                    @Override
                    public void onEntryCreated(VCardEntry entry) {
                        SimpleAttachmentLoaded loaded = new SimpleAttachmentLoaded();
                        List<VCardEntry.PhotoData> photoList = entry.getPhotoList();
                        loaded.icon = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.ic_attach_contact_info);
                        if (photoList != null && photoList.size() == 1) {
                            byte[] photo = entry.getPhotoList().get(0).getBytes();
                            Bitmap bitmap = BitmapFactory.decodeByteArray(photo, 0, photo.length);
                            loaded.icon = getRoundedCornerBitmap(bitmap);
                        }
                        loaded.title = entry.getDisplayName();
                        loaded.subtitle = "Tap to view";
                        itemLoadedCallback.onItemLoaded(loaded, null);
                    }

                    @Override
                    public void onEnd() {

                    }
                });
                try {
                    InputStream is = resolver.openInputStream(getUri());
                    VCardParser mVCardParser = new VCardParser_V21();
                    try {
                        mVCardParser.addInterpreter(constructor);
                        mVCardParser.parseOne(is);
                    } catch (VCardVersionException e1) {
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (is != null) {
                            try {
                                is.close();
                            } catch (IOException e) {
                            }
                        }
                    }
                } catch (VCardNestedException e) {
                } catch (FileNotFoundException e) {
                } catch (VCardException e) {
                }
            }
        }).start();
    }

    @Override
    public Presenter getPresenter() {
        return new SimpleAttachmentPresenter(mContext, this);
    }
}
