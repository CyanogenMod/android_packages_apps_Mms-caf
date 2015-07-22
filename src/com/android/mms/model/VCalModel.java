/*
 * Copyright (c) 2014, CyanogenMod
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

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import org.w3c.dom.events.Event;

public class VCalModel extends MediaModel {
    private static final String TAG = MediaModel.TAG;

    public VCalModel(Context context, Uri uri) throws MmsException {
        this(context, ContentType.TEXT_VCALENDAR, null, uri);
        initModelFromUri(uri);
    }

    public VCalModel(Context context, String contentType, String src, Uri uri)
            throws MmsException {
        super(context, SmilHelper.ELEMENT_TAG_REF, contentType, src, uri);
    }

    private void initModelFromUri(Uri uri) throws MmsException {
        String scheme = uri.getScheme();
        if (scheme == null) {
            Log.e(TAG, "The uri's scheme is null.");
            return;
        }

        if (scheme.equals("file")) {
            mSrc = getFileSrc(uri);
        }
        initMediaDuration();
    }

    private String getFileSrc(Uri uri) {
        String path = Uri.decode(uri.toString());
        return path.substring(path.lastIndexOf('/') + 1);
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

}
