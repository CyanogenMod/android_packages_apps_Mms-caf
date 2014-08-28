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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.graphics.drawable.Drawable;
import android.media.CamcorderProfile;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Threads;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.style.URLSpan;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.android.internal.telephony.EncodeException;
import com.android.internal.telephony.GsmAlphabet;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.SmsHeader;
import com.android.internal.telephony.cdma.sms.BearerData;
import com.android.internal.telephony.cdma.sms.CdmaSmsAddress;
import com.android.internal.telephony.cdma.sms.SmsEnvelope;
import com.android.internal.telephony.cdma.sms.UserData;
import com.android.internal.telephony.uicc.IccUtils;

import com.android.mms.LogTag;
import com.android.mms.MmsApp;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.TempFileProvider;
import com.android.mms.data.WorkingMessage;
import com.android.mms.model.MediaModel;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.transaction.MmsMessageSender;
import com.android.mms.util.AddressUtils;
import com.android.mms.util.DownloadManager;
import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu.CharacterSets;
import com.google.android.mms.pdu.EncodedStringValue;
import com.google.android.mms.pdu.MultimediaMessagePdu;
import com.google.android.mms.pdu.NotificationInd;
import com.google.android.mms.pdu.PduBody;
import com.google.android.mms.pdu.PduHeaders;
import com.google.android.mms.pdu.PduPart;
import com.google.android.mms.pdu.PduPersister;
import com.google.android.mms.pdu.RetrieveConf;
import com.google.android.mms.pdu.SendReq;

import static android.telephony.SmsMessage.ENCODING_7BIT;
import static android.telephony.SmsMessage.ENCODING_8BIT;
import static android.telephony.SmsMessage.ENCODING_16BIT;
import static android.telephony.SmsMessage.ENCODING_UNKNOWN;
import static android.telephony.SmsMessage.MAX_USER_DATA_BYTES;
import static android.telephony.SmsMessage.MAX_USER_DATA_BYTES_WITH_HEADER;
import static android.telephony.SmsMessage.MAX_USER_DATA_SEPTETS;

/**
 * An utility class for managing messages.
 */
public class MessageUtils {
    interface ResizeImageResultCallback {
        void onResizeResult(PduPart part, boolean append);
    }

    private static final int SELECT_SYSTEM = 0;
    private static final int SELECT_EXTERNAL = 1;
    private static final boolean DEBUG = false;
    public static final int SUB_INVALID = -1;  //  for single card product
    public static final int SUB1 = 0;  // for DSDS product of slot one
    public static final int SUB2 = 1;  // for DSDS product of slot two
    public static final String SUBSCRIPTION_KEY = "subscription";
    public static final int MESSAGE_READ = 1;
    public static final int MESSAGE_SEEN = 1;
    public static final Uri ICC_URI = Uri.parse("content://sms/icc");
    public static final Uri ICC1_URI = Uri.parse("content://sms/icc1");
    public static final Uri ICC2_URI = Uri.parse("content://sms/icc2");
    private static final int TIMESTAMP_LENGTH = 7;  // See TS 23.040 9.2.3.11
    private static final String TAG = LogTag.TAG;
    private static final String PREFERRED_SIM_ICON_INDEX = "preferred_sim_icon_index";
    private static String sLocalNumber;
    private static String[] sNoSubjectStrings;
    public static String MULTI_SIM_NAME = "perferred_name_sub";
    private static final String VIEW_MODE_NAME = "current_view";

    public static final int PREFER_SMS_STORE_PHONE = 0;
    public static final int PREFER_SMS_STORE_CARD = 1;

    // Cache of both groups of space-separated ids to their full
    // comma-separated display names, as well as individual ids to
    // display names.
    // TODO: is it possible for canonical address ID keys to be
    // re-used?  SQLite does reuse IDs on NULL id_ insert, but does
    // anything ever delete from the mmssms.db canonical_addresses
    // table?  Nothing that I could find.
    private static final Map<String, String> sRecipientAddress =
            new ConcurrentHashMap<String, String>(20 /* initial capacity */);

    // When we pass a video record duration to the video recorder, use one of these values.
    private static final int[] sVideoDuration =
            new int[] {0, 5, 10, 15, 20, 30, 40, 50, 60, 90, 120};

    /**
     * MMS address parsing data structures
     */
    // allowable phone number separators
    private static final char[] NUMERIC_CHARS_SUGAR = {
        '-', '.', ',', '(', ')', ' ', '/', '\\', '*', '#', '+'
    };

    private static HashMap numericSugarMap = new HashMap (NUMERIC_CHARS_SUGAR.length);
    public static final String EXTRA_KEY_NEW_MESSAGE_NEED_RELOAD = "reload";

    public static final int ALL_RECIPIENTS_VALID   = 0;
    public static final int ALL_RECIPIENTS_INVALID = -1;
    // Indentify RECIPIENT editText is empty
    public static final int ALL_RECIPIENTS_EMPTY   = -2;
    // Add phone feature uri
    private static final Uri URI_PHONE_FEATURE = Uri
            .parse("content://com.qualcomm.qti.phonefeature.FEATURE_PROVIDER");
    // Add keys for supporting SMSC
    private static final String METHOD_GET_SMSC = "get_smsc";
    private static final String METHOD_SET_SMSC = "set_smsc";
    public static final String EXTRA_SMSC = "smsc";

    static {
        for (int i = 0; i < NUMERIC_CHARS_SUGAR.length; i++) {
            numericSugarMap.put(NUMERIC_CHARS_SUGAR[i], NUMERIC_CHARS_SUGAR[i]);
        }
    }


    private MessageUtils() {
        // Forbidden being instantiated.
    }

    /**
     * cleanseMmsSubject will take a subject that's says, "<Subject: no subject>", and return
     * a null string. Otherwise it will return the original subject string.
     * @param context a regular context so the function can grab string resources
     * @param subject the raw subject
     * @return
     */
    public static String cleanseMmsSubject(Context context, String subject) {
        if (TextUtils.isEmpty(subject)) {
            return subject;
        }
        if (sNoSubjectStrings == null) {
            sNoSubjectStrings =
                    context.getResources().getStringArray(R.array.empty_subject_strings);

        }
        final int len = sNoSubjectStrings.length;
        for (int i = 0; i < len; i++) {
            if (subject.equalsIgnoreCase(sNoSubjectStrings[i])) {
                return null;
            }
        }
        return subject;
    }

    public static String getMessageDetails(Context context, Cursor cursor, int size) {
        if (cursor == null) {
            return null;
        }

        if ("mms".equals(cursor.getString(MessageListAdapter.COLUMN_MSG_TYPE))) {
            int type = cursor.getInt(MessageListAdapter.COLUMN_MMS_MESSAGE_TYPE);
            switch (type) {
                case PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND:
                    return getNotificationIndDetails(context, cursor);
                case PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF:
                case PduHeaders.MESSAGE_TYPE_SEND_REQ:
                    return getMultimediaMessageDetails(context, cursor, size);
                default:
                    Log.w(TAG, "No details could be retrieved.");
                    return "";
            }
        } else {
            return getTextMessageDetails(context, cursor);
        }
    }

    private static String getNotificationIndDetails(Context context, Cursor cursor) {
        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();

        long id = cursor.getLong(MessageListAdapter.COLUMN_ID);
        Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, id);
        NotificationInd nInd;

        try {
            nInd = (NotificationInd) PduPersister.getPduPersister(
                    context).load(uri);
        } catch (MmsException e) {
            Log.e(TAG, "Failed to load the message: " + uri, e);
            return context.getResources().getString(R.string.cannot_get_details);
        }

        // Message Type: Mms Notification.
        details.append(res.getString(R.string.message_type_label));
        details.append(res.getString(R.string.multimedia_notification));

        // From: ***
        String from = extractEncStr(context, nInd.getFrom());
        details.append('\n');
        details.append(res.getString(R.string.from_label));
        details.append(!TextUtils.isEmpty(from)? from:
                                 res.getString(R.string.hidden_sender_address));

        // Date: ***
        details.append('\n');
        details.append(res.getString(
                                R.string.expire_on,
                                MessageUtils.formatTimeStampString(
                                        context, nInd.getExpiry() * 1000L, true)));

        // Subject: ***
        details.append('\n');
        details.append(res.getString(R.string.subject_label));

        EncodedStringValue subject = nInd.getSubject();
        if (subject != null) {
            details.append(subject.getString());
        }

        // Message class: Personal/Advertisement/Infomational/Auto
        details.append('\n');
        details.append(res.getString(R.string.message_class_label));
        details.append(new String(nInd.getMessageClass()));

        // Message size: *** KB
        details.append('\n');
        details.append(res.getString(R.string.message_size_label));
        details.append(String.valueOf((nInd.getMessageSize() + 1023) / 1024));
        details.append(context.getString(R.string.kilobyte));

        return details.toString();
    }

    private static String getMultimediaMessageDetails(
            Context context, Cursor cursor, int size) {
        int type = cursor.getInt(MessageListAdapter.COLUMN_MMS_MESSAGE_TYPE);
        if (type == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
            return getNotificationIndDetails(context, cursor);
        }

        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();

        long id = cursor.getLong(MessageListAdapter.COLUMN_ID);
        Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, id);
        MultimediaMessagePdu msg;

        try {
            msg = (MultimediaMessagePdu) PduPersister.getPduPersister(
                    context).load(uri);
        } catch (MmsException e) {
            Log.e(TAG, "Failed to load the message: " + uri, e);
            return context.getResources().getString(R.string.cannot_get_details);
        }

        // Message Type: Text message.
        details.append(res.getString(R.string.message_type_label));
        details.append(res.getString(R.string.multimedia_message));

        if (msg instanceof RetrieveConf) {
            // From: ***
            String from = extractEncStr(context, ((RetrieveConf) msg).getFrom());
            details.append('\n');
            details.append(res.getString(R.string.from_label));
            details.append(!TextUtils.isEmpty(from)? from:
                                  res.getString(R.string.hidden_sender_address));
        }

        // To: ***
        details.append('\n');
        details.append(res.getString(R.string.to_address_label));
        EncodedStringValue[] to = msg.getTo();
        if (to != null) {
            details.append(EncodedStringValue.concat(to));
        }
        else {
            Log.w(TAG, "recipient list is empty!");
        }


        // Bcc: ***
        if (msg instanceof SendReq) {
            EncodedStringValue[] values = ((SendReq) msg).getBcc();
            if ((values != null) && (values.length > 0)) {
                details.append('\n');
                details.append(res.getString(R.string.bcc_label));
                details.append(EncodedStringValue.concat(values));
            }
        }

        // Date: ***
        details.append('\n');
        int msgBox = cursor.getInt(MessageListAdapter.COLUMN_MMS_MESSAGE_BOX);
        if (msgBox == Mms.MESSAGE_BOX_DRAFTS) {
            details.append(res.getString(R.string.saved_label));
        } else if (msgBox == Mms.MESSAGE_BOX_INBOX) {
            details.append(res.getString(R.string.received_label));
        } else {
            details.append(res.getString(R.string.sent_label));
        }

        details.append(MessageUtils.formatTimeStampString(
                context, msg.getDate() * 1000L, true));

        // Subject: ***
        details.append('\n');
        details.append(res.getString(R.string.subject_label));

        EncodedStringValue subject = msg.getSubject();
        if (subject != null) {
            String subStr = subject.getString();
            // Message size should include size of subject.
            size += subStr.length();
            details.append(subStr);
        }

        // Priority: High/Normal/Low
        details.append('\n');
        details.append(res.getString(R.string.priority_label));
        details.append(getPriorityDescription(context, msg.getPriority()));

        // Message size: *** KB
        details.append('\n');
        details.append(res.getString(R.string.message_size_label));
        details.append((size - 1)/1000 + 1);
        details.append(" KB");

        return details.toString();
    }

    private static String getTextMessageDetails(Context context, Cursor cursor) {
        Log.d(TAG, "getTextMessageDetails");

        StringBuilder details = new StringBuilder();
        Resources res = context.getResources();

        // Message Type: Text message.
        details.append(res.getString(R.string.message_type_label));
        details.append(res.getString(R.string.text_message));

        // Address: ***
        details.append('\n');
        int smsType = cursor.getInt(MessageListAdapter.COLUMN_SMS_TYPE);
        if (Sms.isOutgoingFolder(smsType)) {
            details.append(res.getString(R.string.to_address_label));
        } else {
            details.append(res.getString(R.string.from_label));
        }
        details.append(cursor.getString(MessageListAdapter.COLUMN_SMS_ADDRESS));

        // Sent: ***
        if (smsType == Sms.MESSAGE_TYPE_INBOX) {
            long date_sent = cursor.getLong(MessageListAdapter.COLUMN_SMS_DATE_SENT);
            if (date_sent > 0) {
                details.append('\n');
                details.append(res.getString(R.string.sent_label));
                details.append(MessageUtils.formatTimeStampString(context, date_sent, true));
            }
        }

        // Received: ***
        details.append('\n');
        if (smsType == Sms.MESSAGE_TYPE_DRAFT) {
            details.append(res.getString(R.string.saved_label));
        } else if (smsType == Sms.MESSAGE_TYPE_INBOX) {
            details.append(res.getString(R.string.received_label));
        } else {
            details.append(res.getString(R.string.sent_label));
        }

        long date = cursor.getLong(MessageListAdapter.COLUMN_SMS_DATE);
        details.append(MessageUtils.formatTimeStampString(context, date, true));

        // Delivered: ***
        if (smsType == Sms.MESSAGE_TYPE_SENT) {
            // For sent messages with delivery reports, we stick the delivery time in the
            // date_sent column (see MessageStatusReceiver).
            long dateDelivered = cursor.getLong(MessageListAdapter.COLUMN_SMS_DATE_SENT);
            if (dateDelivered > 0) {
                details.append('\n');
                details.append(res.getString(R.string.delivered_label));
                details.append(MessageUtils.formatTimeStampString(context, dateDelivered, true));
            }
        }

        // Error code: ***
        int errorCode = cursor.getInt(MessageListAdapter.COLUMN_SMS_ERROR_CODE);
        if (errorCode != 0) {
            details.append('\n')
                .append(res.getString(R.string.error_code_label))
                .append(errorCode);
        }

        return details.toString();
    }

    static private String getPriorityDescription(Context context, int PriorityValue) {
        Resources res = context.getResources();
        switch(PriorityValue) {
            case PduHeaders.PRIORITY_HIGH:
                return res.getString(R.string.priority_high);
            case PduHeaders.PRIORITY_LOW:
                return res.getString(R.string.priority_low);
            case PduHeaders.PRIORITY_NORMAL:
            default:
                return res.getString(R.string.priority_normal);
        }
    }

    public static int getAttachmentType(SlideshowModel model, MultimediaMessagePdu mmp) {
        if (model == null || mmp == null) {
            return MessageItem.ATTACHMENT_TYPE_NOT_LOADED;
        }

        int numberOfSlides = model.size();
        if (numberOfSlides > 1) {
            return WorkingMessage.SLIDESHOW;
        } else if (numberOfSlides == 1) {
            // Only one slide in the slide-show.
            SlideModel slide = model.get(0);
            if (slide.hasVideo()) {
                return WorkingMessage.VIDEO;
            }

            if (slide.hasAudio() && slide.hasImage()) {
                return WorkingMessage.SLIDESHOW;
            }

            if (slide.hasAudio()) {
                return WorkingMessage.AUDIO;
            }

            if (slide.hasImage()) {
                return WorkingMessage.IMAGE;
            }

            if (slide.hasText()) {
                return WorkingMessage.TEXT;
            }

            // Handle the multimedia message only has subject
            String subject = mmp.getSubject() != null ? mmp.getSubject().getString() : null;
            if (!TextUtils.isEmpty(subject)) {
                return WorkingMessage.TEXT;
            }
        }

        return MessageItem.ATTACHMENT_TYPE_NOT_LOADED;
    }

    public static String formatTimeStampString(Context context, long when) {
        return formatTimeStampString(context, when, false);
    }

    public static String formatTimeStampString(Context context, long when, boolean fullFormat) {
        Time then = new Time();
        then.set(when);
        Time now = new Time();
        now.setToNow();

        // Basic settings for formatDateTime() we want for all cases.
        int format_flags = DateUtils.FORMAT_NO_NOON_MIDNIGHT |
                           DateUtils.FORMAT_ABBREV_ALL |
                           DateUtils.FORMAT_CAP_AMPM;

        // If the message is from a different year, show the date and year.
        if (then.year != now.year) {
            format_flags |= DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE;
        } else if (then.yearDay != now.yearDay) {
            // If it is from a different day than today, show only the date.
            format_flags |= DateUtils.FORMAT_SHOW_DATE;
        } else {
            // Otherwise, if the message is from today, show the time.
            format_flags |= DateUtils.FORMAT_SHOW_TIME;
        }

        // If the caller has asked for full details, make sure to show the date
        // and time no matter what we've determined above (but still make showing
        // the year only happen if it is a different year from today).
        if (fullFormat) {
            format_flags |= (DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME);
        }

        return DateUtils.formatDateTime(context, when, format_flags);
    }

    public static void selectAudio(final Activity activity, final int requestCode) {
        // We are not only displaying default RingtonePicker to add, we could have
        // other choices like external audio and system audio. Allow user to select
        // an audio from particular storage (Internal or External) and return it.
        String[] items = new String[2];
        items[SELECT_SYSTEM] = activity.getString(R.string.system_audio_item);
        items[SELECT_EXTERNAL] = activity.getString(R.string.external_audio_item);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity,
                android.R.layout.simple_list_item_1, android.R.id.text1, items);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        AlertDialog dialog = builder.setTitle(activity.getString(R.string.select_audio))
                .setAdapter(adapter, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent audioIntent = null;
                        switch (which) {
                            case SELECT_SYSTEM:
                                audioIntent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
                                audioIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,
                                        false);
                                audioIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,
                                        false);
                                audioIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_INCLUDE_DRM,
                                        false);
                                audioIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
                                        activity.getString(R.string.select_audio));
                                break;
                            case SELECT_EXTERNAL:
                                audioIntent = new Intent();
                                audioIntent.setAction(Intent.ACTION_PICK);
                                audioIntent.setData(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
                                break;
                        }
                        activity.startActivityForResult(audioIntent, requestCode);
                    }
                })
                .create();
        dialog.show();
    }

    public static void recordSound(Activity activity, int requestCode, long sizeLimit) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(ContentType.AUDIO_AMR);
        intent.setClassName("com.android.soundrecorder",
                "com.android.soundrecorder.SoundRecorder");
        intent.putExtra(android.provider.MediaStore.Audio.Media.EXTRA_MAX_BYTES, sizeLimit);
        activity.startActivityForResult(intent, requestCode);
    }

    public static void recordVideo(Activity activity, int requestCode, long sizeLimit) {
        // The video recorder can sometimes return a file that's larger than the max we
        // say we can handle. Try to handle that overshoot by specifying an 85% limit.
        sizeLimit *= .85F;

        int durationLimit = getVideoCaptureDurationLimit(sizeLimit);

        if (Log.isLoggable(LogTag.APP, Log.VERBOSE)) {
            log("recordVideo: durationLimit: " + durationLimit +
                    " sizeLimit: " + sizeLimit);
        }

        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0);
        intent.putExtra("android.intent.extra.sizeLimit", sizeLimit);
        intent.putExtra("android.intent.extra.durationLimit", durationLimit);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, TempFileProvider.SCRAP_CONTENT_URI);
        activity.startActivityForResult(intent, requestCode);
    }

    public static void capturePicture(Activity activity, int requestCode) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, TempFileProvider.SCRAP_CONTENT_URI);
        activity.startActivityForResult(intent, requestCode);
    }

    // Public for until tests
    public static int getVideoCaptureDurationLimit(long bytesAvailable) {
        CamcorderProfile camcorder = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
        if (camcorder == null) {
            return 0;
        }
        bytesAvailable *= 8;        // convert to bits
        long seconds = bytesAvailable / (camcorder.audioBitRate + camcorder.videoBitRate);

        // Find the best match for one of the fixed durations
        for (int i = sVideoDuration.length - 1; i >= 0; i--) {
            if (seconds >= sVideoDuration[i]) {
                return sVideoDuration[i];
            }
        }
        return 0;
    }

    public static void selectVideo(Context context, int requestCode) {
        selectMediaByType(context, requestCode, ContentType.VIDEO_UNSPECIFIED, true);
    }

    public static void selectImage(Context context, int requestCode) {
        selectMediaByType(context, requestCode, ContentType.IMAGE_UNSPECIFIED, false);
    }

    private static void selectMediaByType(
            Context context, int requestCode, String contentType, boolean localFilesOnly) {
         if (context instanceof Activity) {

            Intent innerIntent = new Intent(Intent.ACTION_GET_CONTENT);

            innerIntent.setType(contentType);
            if (localFilesOnly) {
                innerIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            }

            Intent wrapperIntent = Intent.createChooser(innerIntent, null);

            ((Activity) context).startActivityForResult(wrapperIntent, requestCode);
        }
    }

    public static void viewSimpleSlideshow(Context context, SlideshowModel slideshow) {
        if (!slideshow.isSimple()) {
            throw new IllegalArgumentException(
                    "viewSimpleSlideshow() called on a non-simple slideshow");
        }
        SlideModel slide = slideshow.get(0);
        MediaModel mm = null;
        if (slide.hasImage()) {
            mm = slide.getImage();
        } else if (slide.hasVideo()) {
            mm = slide.getVideo();
        }

        if (mm == null) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.putExtra("SingleItemOnly", true); // So we don't see "surrounding" images in Gallery

        String contentType;
        contentType = mm.getContentType();
        intent.setDataAndType(mm.getUri(), contentType);
        context.startActivity(intent);
    }

    public static void showErrorDialog(Activity activity,
            String title, String message) {
        if (activity.isFinishing()) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        builder.setIcon(R.drawable.ic_sms_mms_not_delivered);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    dialog.dismiss();
                }
            }
        });
        builder.show();
    }

    /**
     * The quality parameter which is used to compress JPEG images.
     */
    public static final int IMAGE_COMPRESSION_QUALITY = 95;
    /**
     * The minimum quality parameter which is used to compress JPEG images.
     */
    public static final int MINIMUM_IMAGE_COMPRESSION_QUALITY = 50;

    /**
     * Message overhead that reduces the maximum image byte size.
     * 5000 is a realistic overhead number that allows for user to also include
     * a small MIDI file or a couple pages of text along with the picture.
     */
    public static final int MESSAGE_OVERHEAD = 5000;

    public static void resizeImageAsync(final Context context,
            final Uri imageUri, final Handler handler,
            final ResizeImageResultCallback cb,
            final boolean append) {

        // Show a progress toast if the resize hasn't finished
        // within one second.
        // Stash the runnable for showing it away so we can cancel
        // it later if the resize completes ahead of the deadline.
        final Runnable showProgress = new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, R.string.compressing, Toast.LENGTH_SHORT).show();
            }
        };
        // Schedule it for one second from now.
        handler.postDelayed(showProgress, 1000);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final PduPart part;
                try {
                    UriImage image = new UriImage(context, imageUri);
                    int widthLimit = MmsConfig.getMaxImageWidth();
                    int heightLimit = MmsConfig.getMaxImageHeight();
                    // In mms_config.xml, the max width has always been declared larger than the max
                    // height. Swap the width and height limits if necessary so we scale the picture
                    // as little as possible.
                    if (image.getHeight() > image.getWidth()) {
                        int temp = widthLimit;
                        widthLimit = heightLimit;
                        heightLimit = temp;
                    }

                    part = image.getResizedImageAsPart(
                        widthLimit,
                        heightLimit,
                        MmsConfig.getMaxMessageSize() - MESSAGE_OVERHEAD);
                } finally {
                    // Cancel pending show of the progress toast if necessary.
                    handler.removeCallbacks(showProgress);
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onResizeResult(part, append);
                    }
                });
            }
        }, "MessageUtils.resizeImageAsync").start();
    }

    public static void showDiscardDraftConfirmDialog(Context context,
            OnClickListener listener, int validNum) {
        // the alert icon shoud has black triangle and white exclamation mark in white background.
        new AlertDialog.Builder(context)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setTitle(R.string.discard_message)
                .setMessage(getDiscardMessageId(validNum))
                .setPositiveButton(R.string.yes, listener)
                .setNegativeButton(R.string.no, null)
                .show();
    }

    /**
    * Return discard message id.
    */
    private static int getDiscardMessageId(int validNum) {
        int msgId = R.string.discard_message_reason;
        // If validNum != ALL_RECIPIENTS_EMPTY, means recipient is not empty.
        // If validNum ==  ALL_RECIPIENTS_VALID, means all of the recipients are valid.
        // If validNum > ALL_RECIPIENTS_VALID, means there some recipients are invalid.
        // If validNum == ALL_RECIPIENTS_INVALID, means all of the recipients are invalid.
        if (ALL_RECIPIENTS_EMPTY != validNum) {
            msgId = validNum > ALL_RECIPIENTS_VALID ? R.string.discard_message_reason_some_invalid
                : R.string.discard_message_reason_all_invalid;
        }
        return msgId;
    }

    public static String getLocalNumber() {
        return getLocalNumber(SubscriptionManager.getDefaultDataSubId());
    }

    public static String getLocalNumber(long subId) {
        sLocalNumber = MmsApp.getApplication().getTelephonyManager()
            .getLine1NumberForSubscriber(subId);
        return sLocalNumber;
    }

    public static boolean isLocalNumber(String number) {
        if (number == null) {
            return false;
        }

        // we don't use Mms.isEmailAddress() because it is too strict for comparing addresses like
        // "foo+caf_=6505551212=tmomail.net@gmail.com", which is the 'from' address from a forwarded email
        // message from Gmail. We don't want to treat "foo+caf_=6505551212=tmomail.net@gmail.com" and
        // "6505551212" to be the same.
        if (number.indexOf('@') >= 0) {
            return false;
        }

        return PhoneNumberUtils.compare(number, getLocalNumber());
    }

    public static void handleReadReport(final Context context,
            final Collection<Long> threadIds,
            final int status,
            final Runnable callback) {
        StringBuilder selectionBuilder = new StringBuilder(Mms.MESSAGE_TYPE + " = "
                + PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF
                + " AND " + Mms.READ + " = 0"
                + " AND " + Mms.READ_REPORT + " = " + PduHeaders.VALUE_YES);

        String[] selectionArgs = null;
        if (threadIds != null) {
            String threadIdSelection = null;
            StringBuilder buf = new StringBuilder();
            selectionArgs = new String[threadIds.size()];
            int i = 0;

            for (long threadId : threadIds) {
                if (i > 0) {
                    buf.append(" OR ");
                }
                buf.append(Mms.THREAD_ID).append("=?");
                selectionArgs[i++] = Long.toString(threadId);
            }
            threadIdSelection = buf.toString();

            selectionBuilder.append(" AND (" + threadIdSelection + ")");
        }

        final Cursor c = SqliteWrapper.query(context, context.getContentResolver(),
                        Mms.Inbox.CONTENT_URI, new String[] {Mms._ID, Mms.MESSAGE_ID},
                        selectionBuilder.toString(), selectionArgs, null);

        if (c == null) {
            return;
        }

        final Map<String, String> map = new HashMap<String, String>();
        try {
            if (c.getCount() == 0) {
                if (callback != null) {
                    callback.run();
                }
                return;
            }

            while (c.moveToNext()) {
                Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, c.getLong(0));
                map.put(c.getString(1), AddressUtils.getFrom(context, uri));
            }
        } finally {
            c.close();
        }

        OnClickListener positiveListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                for (final Map.Entry<String, String> entry : map.entrySet()) {
                    MmsMessageSender.sendReadRec(context, entry.getValue(),
                                                 entry.getKey(), status);
                }

                if (callback != null) {
                    callback.run();
                }
                dialog.dismiss();
            }
        };

        OnClickListener negativeListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (callback != null) {
                    callback.run();
                }
                dialog.dismiss();
            }
        };

        OnCancelListener cancelListener = new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (callback != null) {
                    callback.run();
                }
                dialog.dismiss();
            }
        };

        confirmReadReportDialog(context, positiveListener,
                                         negativeListener,
                                         cancelListener);
    }

    private static void confirmReadReportDialog(Context context,
            OnClickListener positiveListener, OnClickListener negativeListener,
            OnCancelListener cancelListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(true);
        builder.setTitle(R.string.confirm);
        builder.setMessage(R.string.message_send_read_report);
        builder.setPositiveButton(R.string.yes, positiveListener);
        builder.setNegativeButton(R.string.no, negativeListener);
        builder.setOnCancelListener(cancelListener);
        builder.show();
    }

    public static String extractEncStrFromCursor(Cursor cursor,
            int columnRawBytes, int columnCharset) {
        String rawBytes = cursor.getString(columnRawBytes);
        int charset = cursor.getInt(columnCharset);

        if (TextUtils.isEmpty(rawBytes)) {
            return "";
        } else if (charset == CharacterSets.ANY_CHARSET) {
            return rawBytes;
        } else {
            return new EncodedStringValue(charset, PduPersister.getBytes(rawBytes)).getString();
        }
    }

    private static String extractEncStr(Context context, EncodedStringValue value) {
        if (value != null) {
            return value.getString();
        } else {
            return "";
        }
    }

    public static ArrayList<String> extractUris(URLSpan[] spans) {
        int size = spans.length;
        ArrayList<String> accumulator = new ArrayList<String>();

        for (int i = 0; i < size; i++) {
            accumulator.add(spans[i].getURL());
        }
        return accumulator;
    }

    public static String getRecipientsByIds(Context context,
            String recipientIds, boolean allowQuery) {
        String value = sRecipientAddress.get(recipientIds);
        if (value != null) {
            return value;
        }
        if (!TextUtils.isEmpty(recipientIds)) {
            StringBuilder addressBuf = extractIdsToAddresses(context, recipientIds, allowQuery);
            if (addressBuf == null) {
                // temporary error? Don't memoize.
                return "";
            }
            value = addressBuf.toString();
        } else {
            value = "";
        }
        sRecipientAddress.put(recipientIds, value);
        return value;
    }

    private static StringBuilder extractIdsToAddresses(Context context, String recipients,
            boolean allowQuery) {
        StringBuilder addressBuf = new StringBuilder();
        String[] recipientIds = recipients.split(" ");
        boolean firstItem = true;
        for (String recipientId : recipientIds) {
            String value = sRecipientAddress.get(recipientId);

            if (value == null) {
                if (!allowQuery) {
                    // when allowQuery is false, if any value from
                    // sRecipientAddress.get() is null,
                    // return null for the whole thing. We don't want to stick
                    // partial result
                    // into sRecipientAddress for multiple recipient ids.
                    return null;
                }

                Uri uri = Uri.parse("content://mms-sms/canonical-address/" + recipientId);
                Cursor c = SqliteWrapper.query(context, context.getContentResolver(), uri, null,
                        null, null, null);
                if (c != null) {
                    try {
                        if (c.moveToFirst()) {
                            value = c.getString(0);
                            sRecipientAddress.put(recipientId, value);
                        }
                    } finally {
                        c.close();
                    }
                }
            }
            if (value == null) {
                continue;
            }
            if (firstItem) {
                firstItem = false;
            } else {
                addressBuf.append(";");
            }
            addressBuf.append(value);
        }

        return (addressBuf.length() == 0) ? null : addressBuf;
    }

    public static String getAddressByThreadId(Context context, long threadId) {
        String[] projection = new String[] {
                Threads.RECIPIENT_IDS
        };

        Uri.Builder builder = Threads.CONTENT_URI.buildUpon();
        builder.appendQueryParameter("simple", "true");
        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(), builder.build(),
                projection, Threads._ID + "=" + threadId, null, null);

        if (cursor != null) {
            try {
                if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
                    String address = getRecipientsByIds(context, cursor.getString(0),
                            true /* allow query */);
                    if (!TextUtils.isEmpty(address)) {
                        return address;
                    }
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    /**
     * Play/view the message attachments.
     * TOOD: We need to save the draft before launching another activity to view the attachments.
     *       This is hacky though since we will do saveDraft twice and slow down the UI.
     *       We should pass the slideshow in intent extra to the view activity instead of
     *       asking it to read attachments from database.
     * @param activity
     * @param msgUri the MMS message URI in database
     * @param slideshow the slideshow to save
     * @param persister the PDU persister for updating the database
     * @param sendReq the SendReq for updating the database
     */
    public static void viewMmsMessageAttachment(Activity activity, Uri msgUri,
            SlideshowModel slideshow, AsyncDialog asyncDialog) {
        viewMmsMessageAttachment(activity, msgUri, slideshow, 0, asyncDialog);
    }

    public static void viewMmsMessageAttachment(final Activity activity, final Uri msgUri,
            final SlideshowModel slideshow, final int requestCode, AsyncDialog asyncDialog) {
        boolean isSimple = (slideshow == null) ? false : slideshow.isSimple();
        if (isSimple) {
            // In attachment-editor mode, we only ever have one slide.
            MessageUtils.viewSimpleSlideshow(activity, slideshow);
        } else {
            // The user wants to view the slideshow. We have to persist the slideshow parts
            // in a background task. If the task takes longer than a half second, a progress dialog
            // is displayed. Once the PDU persisting is done, another runnable on the UI thread get
            // executed to start the SlideshowActivity.
            asyncDialog.runAsync(new Runnable() {
                @Override
                public void run() {
                    // If a slideshow was provided, save it to disk first.
                    if (slideshow != null) {
                        PduPersister persister = PduPersister.getPduPersister(activity);
                        try {
                            PduBody pb = slideshow.toPduBody();
                            persister.updateParts(msgUri, pb, null);
                            slideshow.sync(pb);
                        } catch (MmsException e) {
                            Log.e(TAG, "Unable to save message for preview");
                            return;
                        }
                    }
                }
            }, new Runnable() {
                @Override
                public void run() {
                    // Once the above background thread is complete, this runnable is run
                    // on the UI thread to launch the slideshow activity.
                    launchSlideshowActivity(activity, msgUri, requestCode);
                }
            }, R.string.building_slideshow_title);
        }
    }

    public static void launchSlideshowActivity(Context context, Uri msgUri, int requestCode) {
        // Launch the slideshow activity to play/view.
        Intent intent = new Intent(context, SlideshowActivity.class);
        intent.setData(msgUri);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (requestCode > 0 && context instanceof Activity) {
            ((Activity)context).startActivityForResult(intent, requestCode);
        } else {
            context.startActivity(intent);
        }

    }

    /**
     * Debugging
     */
    public static void writeHprofDataToFile(){
        String filename = Environment.getExternalStorageDirectory() + "/mms_oom_hprof_data";
        try {
            android.os.Debug.dumpHprofData(filename);
            Log.i(TAG, "##### written hprof data to " + filename);
        } catch (IOException ex) {
            Log.e(TAG, "writeHprofDataToFile: caught " + ex);
        }
    }

    // An alias (or commonly called "nickname") is:
    // Nickname must begin with a letter.
    // Only letters a-z, numbers 0-9, or . are allowed in Nickname field.
    public static boolean isAlias(String string) {
        if (!MmsConfig.isAliasEnabled()) {
            return false;
        }

        int len = string == null ? 0 : string.length();

        if (len < MmsConfig.getAliasMinChars() || len > MmsConfig.getAliasMaxChars()) {
            return false;
        }

        if (!Character.isLetter(string.charAt(0))) {    // Nickname begins with a letter
            return false;
        }
        for (int i = 1; i < len; i++) {
            char c = string.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '.')) {
                return false;
            }
        }

        return true;
    }

    /**
     * Given a phone number, return the string without syntactic sugar, meaning parens,
     * spaces, slashes, dots, dashes, etc. If the input string contains non-numeric
     * non-punctuation characters, return null.
     */
    private static String parsePhoneNumberForMms(String address) {
        StringBuilder builder = new StringBuilder();
        int len = address.length();

        for (int i = 0; i < len; i++) {
            char c = address.charAt(i);

            // accept the first '+' in the address
            if (c == '+' && builder.length() == 0) {
                builder.append(c);
                continue;
            }

            if (Character.isDigit(c)) {
                builder.append(c);
                continue;
            }

            if (numericSugarMap.get(c) == null) {
                return null;
            }
        }
        return builder.toString();
    }

    /**
     * Returns true if the address passed in is a valid MMS address.
     */
    public static boolean isValidMmsAddress(String address) {
        String retVal = parseMmsAddress(address);
        return (retVal != null);
    }

    /**
     * parse the input address to be a valid MMS address.
     * - if the address is an email address, leave it as is.
     * - if the address can be parsed into a valid MMS phone number, return the parsed number.
     * - if the address is a compliant alias address, leave it as is.
     */
    public static String parseMmsAddress(String address) {
        // if it's a valid Email address, use that.
        if (Mms.isEmailAddress(address)) {
            return address;
        }

        // if we are able to parse the address to a MMS compliant phone number, take that.
        String retVal = parsePhoneNumberForMms(address);
        if (retVal != null && retVal.length() != 0) {
            return retVal;
        }

        // if it's an alias compliant address, use that.
        if (isAlias(address)) {
            return address;
        }

        // it's not a valid MMS address, return null
        return null;
    }

    public static void dialRecipient(Context context, String address, int subscription) {
        if (!Mms.isEmailAddress(address)) {
            Intent dialIntent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + address));
            if (isMultiSimEnabledMms()) {
                dialIntent.putExtra(SUBSCRIPTION_KEY, subscription);
            }
            context.startActivity(dialIntent);
        }
    }

    /**
     * Return whether it has card in according slot -the input subscription is 0
     * or 1 -It is only used in DSDS
     */
    public static boolean hasIccCard(int subscription) {
        boolean hasCard = false;
        TelephonyManager telephonyManager = TelephonyManager.getDefault();
        if (isMultiSimEnabledMms()) {
            hasCard = telephonyManager.hasIccCard(subscription);
        } else {
            if (subscription == SubscriptionManager.getDefaultSubId()) {
                hasCard = telephonyManager.hasIccCard();
            }
        }
        return hasCard;
    }

    /**
     * Return whether it has card no matter in DSDS or not
     */
    public static boolean hasIccCard() {
        return TelephonyManager.getDefault().hasIccCard();
    }

    public static boolean isPhoneFeatureEnabled(Context context) {
        return context.getContentResolver().acquireProvider(URI_PHONE_FEATURE) != null;
    }

    private static Bundle callBinder(Context context, String method, Bundle extras) {
        if (!isPhoneFeatureEnabled(context)) {
            return null;
        }
        return context.getContentResolver().call(URI_PHONE_FEATURE, method, null, extras);
    }

    public static void setSmscForSub(Context context, int sub, String smsc, Message callback) {
        if (callback != null) {
            callback.replyTo = new Messenger(callback.getTarget());
        }
        log("Set: sub = " + sub + " smsc= " + smsc);
        Bundle params = new Bundle();
        params.putInt(PhoneConstants.SUBSCRIPTION_KEY, sub);
        params.putString(EXTRA_SMSC, smsc);
        params.putParcelable("callback", callback);
        callBinder(context, METHOD_SET_SMSC, params);
    }

    public static void getSmscFromSub(Context context, int sub, Message callback) {
        if (callback == null) {
            return;
        }
        log("Get: sub = " + sub);
        callback.replyTo = new Messenger(callback.getTarget());
        Bundle params = new Bundle();
        params.putInt(PhoneConstants.SUBSCRIPTION_KEY, sub);
        params.putParcelable("callback", callback);
        callBinder(context, METHOD_GET_SMSC, params);
    }

    private static void log(String msg) {
        Log.d(TAG, "[MsgUtils] " + msg);
    }

    public static boolean isMailboxMode() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(MmsApp
                .getApplication());
        boolean ViewMode = sp.getBoolean(VIEW_MODE_NAME, false);
        return ViewMode;
    }

    /**
     * Return the sim name of subscription.
     */
    public static String getMultiSimName(Context context, long subscription) {
        if (subscription >= TelephonyManager.getDefault().getPhoneCount() || subscription < 0) {
            return null;
        }
        //String multiSimName = Settings.System.getString(context.getContentResolver(),
        //        MULTI_SIM_NAME + (subscription + 1));
        //if (multiSimName == null) {
            if (subscription == SUB1) {
                return context.getString(R.string.slot1);
            } else if (subscription == SUB2) {
                return context.getString(R.string.slot2);
            }
        //}
        return context.getString(R.string.slot1);
    }

    /**
     * Return the activated card number
     */
    public static int getActivatedIccCardCount() {
        TelephonyManager tm = TelephonyManager.getDefault();
        int phoneCount = tm.getPhoneCount();
        if(DEBUG) Log.d(TAG, "isIccCardActivated phoneCount " + phoneCount);
        int count = 0;
        for (int i = 0; i < phoneCount; i++) {
            if(DEBUG) Log.d(TAG, "isIccCardActivated subscription " + tm.getSimState(i));
            // Because the status of slot1/2 will return SIM_STATE_UNKNOWN under airplane mode.
            // So we add check about SIM_STATE_UNKNOWN.
            if (isIccCardActivated(i)) {
                count++;
            }
        }
        return count;
   }

    /**
     * Decide whether the current product  is DSDS in MMS
     */
    public static boolean isMultiSimEnabledMms() {
        return TelephonyManager.getDefault().isMultiSimEnabled();
    }

    /**
     * Return whether the card is activated according to Subscription
     * used for DSDS
     */
    public static boolean isIccCardActivated(int subscription) {
        TelephonyManager tm = TelephonyManager.getDefault();
        if (DEBUG) {
            Log.d(TAG, "isIccCardActivated subscription " + tm.getSimState(subscription));
        }
        return (tm.getSimState(subscription) != TelephonyManager.SIM_STATE_ABSENT)
                    //&& (tm.getSimState(subscription) != TelephonyManager.SIM_STATE_DEACTIVATED)
                    && (tm.getSimState(subscription) != TelephonyManager.SIM_STATE_UNKNOWN);
    }

    /*
     * @return the SIM icon for the special subscription.
     */
    public static Drawable getMultiSimIcon(Context context, int subscription) {
        if (context == null) {
            // If the context is null, return 0 as no resource found.
            return null;
        }

        /*TypedArray icons = context.getResources().obtainTypedArray(
            com.android.internal.R.array.sim_icons);
        String simIconIndex = Settings.System.getString(
                context.getContentResolver(), PREFERRED_SIM_ICON_INDEX);
        if (TextUtils.isEmpty(simIconIndex)) {
            return icons.getDrawable(subscription);
        } else {
            String[] indexs = simIconIndex.split(",");
            if (subscription >= indexs.length) {
                return null;
            }
            return icons.getDrawable(Integer.parseInt(indexs[subscription]));
        }*/
        return null;
    }

    private static boolean isCDMAPhone(int subscription) {
        boolean isCDMA = false;
        int activePhone = isMultiSimEnabledMms()
                ? TelephonyManager.getDefault().getCurrentPhoneType(subscription)
                        : TelephonyManager.getDefault().getPhoneType();
        if (TelephonyManager.PHONE_TYPE_CDMA == activePhone) {
            isCDMA = true;
        }
        return isCDMA;
    }

    private static boolean isNetworkRoaming(int subscription) {
        return isMultiSimEnabledMms()
                ? TelephonyManager.getDefault().isNetworkRoaming(subscription)
                : TelephonyManager.getDefault().isNetworkRoaming();
    }

    public static boolean isCDMAInternationalRoaming(int subscription) {
        return isCDMAPhone(subscription) && isNetworkRoaming(subscription);
    }

    public static boolean isMsimIccCardActive() {
        if (isMultiSimEnabledMms()) {
            if (isIccCardActivated(MessageUtils.SUB1) && isIccCardActivated(MessageUtils.SUB2)) {
                return true;
            }
        }
        return false;
    }

    public static int getMmsDownloadStatus(int mmsStatus) {
        if(DownloadManager.STATE_PERMANENT_FAILURE == mmsStatus) {
            return mmsStatus;
        } else if (!DownloadManager.getInstance().isAuto()
                && DownloadManager.STATE_PRE_DOWNLOADING != mmsStatus) {
            return mmsStatus & ~DownloadManager.DEFERRED_MASK;
        }
        return mmsStatus;
    }

    /**
     * Return the icc uri according to subscription
     */
    public static Uri getIccUriBySubscription(long subscription) {
        switch ((int)subscription) {
            case (int)SUB1:
                return ICC1_URI;
            case (int)SUB2:
                return ICC2_URI;
            default:
                return ICC_URI;
        }
    }

    private static boolean isCDMAPhone(long subscription) {
        boolean isCDMA = false;
        int activePhone = isMultiSimEnabledMms()
                ? TelephonyManager.getDefault().getCurrentPhoneType(subscription)
                        : TelephonyManager.getDefault().getPhoneType();
        if (TelephonyManager.PHONE_TYPE_CDMA == activePhone) {
            isCDMA = true;
        }
        return isCDMA;
    }

    /**
     * Generate a Delivery PDU byte array. see getSubmitPdu for reference.
     */
    public static byte[] getDeliveryPdu(String scAddress, String destinationAddress, String message,
            long date, long subscription) {
        if (isCDMAPhone(subscription)) {
            return getCDMADeliveryPdu(scAddress, destinationAddress, message, date);
        } else {
            return getDeliveryPdu(scAddress, destinationAddress, message, date, null,
                    ENCODING_UNKNOWN);
        }
    }

    public static byte[] getCDMADeliveryPdu(String scAddress, String destinationAddress,
            String message, long date) {
        // Perform null parameter checks.
        if (message == null || destinationAddress == null) {
            Log.d(TAG, "getCDMADeliveryPdu,message =null");
            return null;
        }

        // according to submit pdu encoding as written in privateGetSubmitPdu

        // MTI = SMS-DELIVERY, UDHI = header != null
        byte[] header = null;
        byte mtiByte = (byte) (0x00 | (header != null ? 0x40 : 0x00));
        ByteArrayOutputStream headerStream = getDeliveryPduHeader(destinationAddress, mtiByte);

        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(MAX_USER_DATA_BYTES + 40);

        DataOutputStream dos = new DataOutputStream(byteStream);
        // int status,Status of message. See TS 27.005 3.1, "<stat>"

        /* 0 = "REC UNREAD" */
        /* 1 = "REC READ" */
        /* 2 = "STO UNSENT" */
        /* 3 = "STO SENT" */

        try {
            // int uTeleserviceID;
            int uTeleserviceID = 0; //.TELESERVICE_CT_WAP;// int
            dos.writeInt(uTeleserviceID);

            // unsigned char bIsServicePresent
            byte bIsServicePresent = 0;// byte
            dos.writeInt(bIsServicePresent);

            // uServicecategory
            int uServicecategory = 0;// int
            dos.writeInt(uServicecategory);

            // RIL_CDMA_SMS_Address
            // digit_mode
            // number_mode
            // number_type
            // number_plan
            // number_of_digits
            // digits[]
            CdmaSmsAddress destAddr = CdmaSmsAddress.parse(PhoneNumberUtils
                    .cdmaCheckAndProcessPlusCode(destinationAddress));
            if (destAddr == null)
                return null;
            dos.writeByte(destAddr.digitMode);// int
            dos.writeByte(destAddr.numberMode);// int
            dos.writeByte(destAddr.ton);// int
            Log.d(TAG, "message type=" + destAddr.ton + "destination add=" + destinationAddress
                    + "message=" + message);
            dos.writeByte(destAddr.numberPlan);// int
            dos.writeByte(destAddr.numberOfDigits);// byte
            dos.write(destAddr.origBytes, 0, destAddr.origBytes.length); // digits

            // RIL_CDMA_SMS_Subaddress
            // Subaddress is not supported.
            dos.writeByte(0); // subaddressType int
            dos.writeByte(0); // subaddr_odd byte
            dos.writeByte(0); // subaddr_nbr_of_digits byte

            SmsHeader smsHeader = new SmsHeader().fromByteArray(headerStream.toByteArray());
            UserData uData = new UserData();
            uData.payloadStr = message;
            // uData.userDataHeader = smsHeader;
            uData.msgEncodingSet = true;
            uData.msgEncoding = UserData.ENCODING_UNICODE_16;

            BearerData bearerData = new BearerData();
            bearerData.messageType = BearerData.MESSAGE_TYPE_DELIVER;

            bearerData.deliveryAckReq = false;
            bearerData.userAckReq = false;
            bearerData.readAckReq = false;
            bearerData.reportReq = false;

            bearerData.userData = uData;

            byte[] encodedBearerData = BearerData.encode(bearerData);
            if (null != encodedBearerData) {
                // bearer data len
                dos.writeByte(encodedBearerData.length);// int
                Log.d(TAG, "encodedBearerData length=" + encodedBearerData.length);

                // aBearerData
                dos.write(encodedBearerData, 0, encodedBearerData.length);
            } else {
                dos.writeByte(0);
            }

        } catch (IOException e) {
            Log.e(TAG, "Error writing dos", e);
        } finally {
            try {
                if (null != byteStream) {
                    byteStream.close();
                }

                if (null != dos) {
                    dos.close();
                }

                if (null != headerStream) {
                    headerStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error close dos", e);
            }
        }

        return byteStream.toByteArray();
    }

    /**
     * Generate a Delivery PDU byte array. see getSubmitPdu for reference.
     */
    public static byte[] getDeliveryPdu(String scAddress, String destinationAddress, String message,
            long date, byte[] header, int encoding) {
        // Perform null parameter checks.
        if (message == null || destinationAddress == null) {
            return null;
        }

        // MTI = SMS-DELIVERY, UDHI = header != null
        byte mtiByte = (byte)(0x00 | (header != null ? 0x40 : 0x00));
        ByteArrayOutputStream bo = getDeliveryPduHeader(destinationAddress, mtiByte);
        // User Data (and length)
        byte[] userData;
        if (encoding == ENCODING_UNKNOWN) {
            // First, try encoding it with the GSM alphabet
            encoding = ENCODING_7BIT;
        }
        try {
            if (encoding == ENCODING_7BIT) {
                userData = GsmAlphabet.stringToGsm7BitPackedWithHeader(message, header, 0, 0);
            } else { //assume UCS-2
                try {
                    userData = encodeUCS2(message, header);
                } catch (UnsupportedEncodingException uex) {
                    Log.e("GSM", "Implausible UnsupportedEncodingException ",
                            uex);
                    return null;
                }
            }
        } catch (EncodeException ex) {
            // Encoding to the 7-bit alphabet failed. Let's see if we can
            // encode it as a UCS-2 encoded message
            try {
                userData = encodeUCS2(message, header);
                encoding = ENCODING_16BIT;
            } catch (UnsupportedEncodingException uex) {
                Log.e("GSM", "Implausible UnsupportedEncodingException ",
                            uex);
                return null;
            }
        }

        if (encoding == ENCODING_7BIT) {
            if ((0xff & userData[0]) > MAX_USER_DATA_SEPTETS) {
                // Message too long
                return null;
            }
            bo.write(0x00);
        } else { //assume UCS-2
            if ((0xff & userData[0]) > MAX_USER_DATA_BYTES) {
                // Message too long
                return null;
            }
            // TP-Data-Coding-Scheme
            // Class 3, UCS-2 encoding, uncompressed
            bo.write(0x0b);
        }
        byte[] timestamp = getTimestamp(date);
        bo.write(timestamp, 0, timestamp.length);

        bo.write(userData, 0, userData.length);
        return bo.toByteArray();
    }

    private static ByteArrayOutputStream getDeliveryPduHeader(
            String destinationAddress, byte mtiByte) {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(
                MAX_USER_DATA_BYTES + 40);
        bo.write(mtiByte);

        byte[] daBytes;
        daBytes = PhoneNumberUtils.networkPortionToCalledPartyBCD(destinationAddress);

        // destination address length in BCD digits, ignoring TON byte and pad
        // TODO Should be better.
        bo.write((daBytes.length - 1) * 2
                - ((daBytes[daBytes.length - 1] & 0xf0) == 0xf0 ? 1 : 0));

        // destination address
        bo.write(daBytes, 0, daBytes.length);

        // TP-Protocol-Identifier
        bo.write(0);
        return bo;
    }

    private static byte[] encodeUCS2(String message, byte[] header)
        throws UnsupportedEncodingException {
        byte[] userData, textPart;
        textPart = message.getBytes("utf-16be");

        if (header != null) {
            // Need 1 byte for UDHL
            userData = new byte[header.length + textPart.length + 1];

            userData[0] = (byte)header.length;
            System.arraycopy(header, 0, userData, 1, header.length);
            System.arraycopy(textPart, 0, userData, header.length + 1, textPart.length);
        }
        else {
            userData = textPart;
        }
        byte[] ret = new byte[userData.length+1];
        ret[0] = (byte) (userData.length & 0xff );
        System.arraycopy(userData, 0, ret, 1, userData.length);
        return ret;
    }

    private static byte[] getTimestamp(long time) {
        // See TS 23.040 9.2.3.11
        byte[] timestamp = new byte[TIMESTAMP_LENGTH];
        SimpleDateFormat sdf = new SimpleDateFormat("yyMMddkkmmss:Z", Locale.US);
        String[] date = sdf.format(time).split(":");
        // generate timezone value
        String timezone = date[date.length - 1];
        String signMark = timezone.substring(0, 1);
        int hour = Integer.parseInt(timezone.substring(1, 3));
        int min = Integer.parseInt(timezone.substring(3));
        int timezoneValue = hour * 4 + min / 15;
        // append timezone value to date[0] (time string)
        String timestampStr = date[0] + timezoneValue;

        int digitCount = 0;
        for (int i = 0; i < timestampStr.length(); i++) {
            char c = timestampStr.charAt(i);
            int shift = ((digitCount & 0x01) == 1) ? 4 : 0;
            timestamp[(digitCount >> 1)] |= (byte)((charToBCD(c) & 0x0F) << shift);
            digitCount++;
        }

        if (signMark.equals("-")) {
            timestamp[timestamp.length - 1] = (byte) (timestamp[timestamp.length - 1] | 0x08);
        }

        return timestamp;
    }

    private static int charToBCD(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        } else {
            throw new RuntimeException ("invalid char for BCD " + c);
        }
    }

    public static int getSmsPreferStoreLocation(Context context, long subscription) {
        SharedPreferences prefsms = PreferenceManager.getDefaultSharedPreferences(context);
        int preferStore = PREFER_SMS_STORE_PHONE;

        if (isMultiSimEnabledMms()) {
            if (subscription == SUB1) {
                preferStore = Integer.parseInt(prefsms.getString("pref_key_sms_store_card1", "0"));
            } else {
                preferStore = Integer.parseInt(prefsms.getString("pref_key_sms_store_card2", "0"));
            }
        } else {
            preferStore = Integer.parseInt(prefsms.getString("pref_key_sms_store", "0"));
        }

        return preferStore;
    }
}
