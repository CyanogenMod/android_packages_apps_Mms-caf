/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.ui;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import com.android.mms.MmsConfig;
import com.android.mms.R;

/**
 * An adapter to store icons and strings for attachment type list.
 */
public class AttachmentTypeSelectorAdapter extends IconListAdapter {
    public final static int MODE_WITH_SLIDESHOW    = 0;
    public final static int MODE_WITHOUT_SLIDESHOW = 1;

    public final static int ADD_IMAGE               = 0;
    public final static int TAKE_PICTURE            = 1;
    public final static int ADD_VIDEO               = 2;
    public final static int RECORD_VIDEO            = 3;
    public final static int ADD_SOUND               = 4;
    public final static int RECORD_SOUND            = 5;
    public final static int ADD_SLIDESHOW           = 6;
    public final static int ADD_CONTACT_AS_VCARD    = 7;
    public final static int ADD_CONTACT_AS_TEXT     = 8;
    public final static int ADD_CALENDAR_EVENTS     = 9;

    private boolean mShowMediaOnly = false;
    private static int mMediaCount;

    public AttachmentTypeSelectorAdapter(Context context, int mode) {
        super(context, getData(mode, context));
    }

    public int buttonToCommand(int whichButton) {
        AttachmentListItem item = (AttachmentListItem)getItem(whichButton);
        return item.getCommand();
    }

    protected static List<IconListItem> getData(int mode, Context context) {
        mMediaCount = 0;
        List<IconListItem> data = new ArrayList<IconListItem>(7);
        addItem(data, context.getString(R.string.attach_image),
                R.drawable.ic_attach_picture, ADD_IMAGE);
        mMediaCount ++;

        addItem(data, context.getString(R.string.attach_take_photo),
                R.drawable.ic_attach_capture_photo, TAKE_PICTURE);
        mMediaCount ++;

        addItem(data, context.getString(R.string.attach_video),
                R.drawable.ic_attach_video, ADD_VIDEO);
        mMediaCount ++;

        addItem(data, context.getString(R.string.attach_record_video),
                R.drawable.ic_attach_capture_video, RECORD_VIDEO);
        mMediaCount ++;

        if (MmsConfig.getAllowAttachAudio()) {
            addItem(data, context.getString(R.string.attach_sound),
                    R.drawable.ic_attach_audio, ADD_SOUND);
            mMediaCount ++;
        }

        addItem(data, context.getString(R.string.attach_record_sound),
                R.drawable.ic_attach_capture_audio, RECORD_SOUND);
        mMediaCount ++;

        if (mode == MODE_WITH_SLIDESHOW) {
            addItem(data, context.getString(R.string.attach_slideshow),
                    R.drawable.ic_attach_slideshow, ADD_SLIDESHOW);
        }
        if (context.getResources().getBoolean(R.bool.config_vcard)) {
            addItem(data, context.getString(R.string.attach_add_contact_as_vcard),
                    R.drawable.ic_attach_vcard, ADD_CONTACT_AS_VCARD);

            addItem(data, context.getString(R.string.attach_add_contact_as_text),
                    R.drawable.ic_attach_contact_info, ADD_CONTACT_AS_TEXT);
        }

        // show Calendar Event attachment type only if an activity can respond to the intent
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities
                (MessageUtils.getSelectCalendarEventIntent(), 0);
        if (resolveInfos.size() > 0) {
            // add calendar event attachment type
            addItem(data, context.getResources().getString(R.string.attach_add_calendar_events),
                    R.drawable.ic_attach_event, ADD_CALENDAR_EVENTS);
        }

        return data;
    }

    public void setShowMedia(boolean isShowMediaOnly) {
        mShowMediaOnly = isShowMediaOnly;
    }

    @Override
    public int getCount() {
        return mShowMediaOnly ? mMediaCount : super.getCount();
    }

    protected static void addItem(List<IconListItem> data, String title,
            int resource, int command) {
        AttachmentListItem temp = new AttachmentListItem(title, resource, command);
        data.add(temp);
    }

    public static class AttachmentListItem extends IconListAdapter.IconListItem {
        private int mCommand;

        public AttachmentListItem(String title, int resource, int command) {
            super(title, resource);

            mCommand = command;
        }

        public int getCommand() {
            return mCommand;
        }
    }
}
