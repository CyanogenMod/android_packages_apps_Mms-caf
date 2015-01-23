/*
 * Copyright (c) 2014 pci-suntektech Technologies, Inc.  All Rights Reserved.
 * pci-suntektech Technologies Proprietary and Confidential.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

package com.android.mms.rcs;

import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;
import com.android.mms.ui.ComposeMessageActivity;
import com.android.mms.ui.MessageItem;
import com.android.mms.ui.MessageListItem;
import com.android.mms.util.Recycler;
import com.android.mms.widget.MmsWidgetProvider;
import com.suntek.mway.rcs.client.aidl.constant.BroadcastConstants;
import com.suntek.mway.rcs.client.aidl.plugin.callback.IEmoticonCallbackApi;
import com.suntek.mway.rcs.client.aidl.plugin.entity.emoticon.EmoticonBO;
import com.suntek.mway.rcs.client.aidl.plugin.entity.emoticon.EmoticonConstant;
import com.suntek.mway.rcs.client.aidl.provider.SuntekMessageData;
import com.suntek.mway.rcs.client.aidl.provider.model.ChatMessage;
import com.suntek.mway.rcs.client.aidl.provider.model.GroupChatModel;
import com.suntek.mway.rcs.client.aidl.provider.model.GroupChatUser;
import com.suntek.mway.rcs.client.api.autoconfig.RcsAccountApi;
import com.suntek.mway.rcs.client.api.im.impl.MessageApi;
import com.suntek.mway.rcs.client.api.specialnumber.impl.SpecialServiceNumApi;
import com.suntek.mway.rcs.client.api.util.FileDurationException;
import com.suntek.mway.rcs.client.api.util.FileSuffixException;
import com.suntek.mway.rcs.client.api.util.FileTransferException;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.util.log.LogHelper;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.graphics.Matrix;
import android.graphics.NinePatch;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.BaseColumns;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Telephony;
import android.provider.ContactsContract.Groups;
import android.provider.Telephony.CanonicalAddressesColumns;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Inbox;
import android.provider.Telephony.Sms.Outbox;
import android.provider.Telephony.Threads;
import android.provider.Telephony.Sms.Sent;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.lang.ref.SoftReference;

public class RcsUtils {
    public static final int IS_RCS_TRUE = 1;
    public static final int IS_RCS_FALSE = 0;
    public static final int RCS_IS_BURN_TRUE = 1;
    public static final int RCS_IS_BURN_FALSE = 0;
    public static final int RCS_IS_DOWNLOAD_FALSE = 0;
    public static final int RCS_IS_DOWNLOAD_OK = 1;
    public static final int SMS_DEFAULT_RCS_ID = -1;
    public static final int RCS_MSG_TYPE_TEXT = SuntekMessageData.MSG_TYPE_TEXT;
    public static final int RCS_MSG_TYPE_IMAGE = SuntekMessageData.MSG_TYPE_IMAGE;
    public static final int RCS_MSG_TYPE_VIDEO = SuntekMessageData.MSG_TYPE_VIDEO;
    public static final int RCS_MSG_TYPE_AUDIO = SuntekMessageData.MSG_TYPE_AUDIO;
    public static final int RCS_MSG_TYPE_MAP = SuntekMessageData.MSG_TYPE_MAP;
    public static final int RCS_MSG_TYPE_VCARD = SuntekMessageData.MSG_TYPE_CONTACT;
    public static final int RCS_MSG_TYPE_NOTIFICATION = SuntekMessageData.MSG_TYPE_NOTIFICATION;
    public static final int RCS_MSG_TYPE_CAIYUNFILE = SuntekMessageData.MSG_TYPE_CLOUD_FILE;
    public static final int RCS_MSG_TYPE_PAID_EMO = SuntekMessageData.MSG_TYPE_PAID_EMO;

    public static final String GROUP_CHAT_NOTIFICATION_KEY_WORDS_CREATED = "create_not_active";
    public static final String GROUP_CHAT_NOTIFICATION_KEY_WORDS_ACTIVE = "create";
    public static final String GROUP_CHAT_NOTIFICATION_KEY_WORDS_JOIN = "join";
    public static final String GROUP_CHAT_NOTIFICATION_KEY_WORDS_SUBJECT = "subject";
    public static final String GROUP_CHAT_NOTIFICATION_KEY_WORDS_ALIAS = "alias";
    public static final String GROUP_CHAT_NOTIFICATION_KEY_WORDS_REMARK = "remark";
    public static final String GROUP_CHAT_NOTIFICATION_KEY_WORDS_CHAIRMAN = "chairman";
    public static final String GROUP_CHAT_NOTIFICATION_KEY_WORDS_TICK = "tick";
    public static final String GROUP_CHAT_NOTIFICATION_KEY_WORDS_QUIT = "quit";
    public static final String GROUP_CHAT_NOTIFICATION_KEY_WORDS_DISBAND = "disband";
    public static final String GROUP_CHAT_NOTIFICATION_KEY_WORDS_POLICY = "policy";

    // message status
    public static final int MESSAGE_SENDING = 64;
    public static final int MESSAGE_HAS_SENDED = 32;
    public static final int MESSAGE_SENDED = -1;
    public static final int MESSAGE_FAIL = 128;
    public static final int MESSAGE_HAS_BURNED = 2;
    public static final int MESSAGE_SEND_RECEIVE = 99;//delivered
    public static final int MESSAGE_HAS_READ = 100;//displayed
    public static final int MESSAGE_HAS_SEND_SERVER = 0;//send to server

    private static final String FIREWALL_APK_NAME = "com.android.firewall";
    public static final Uri WHITELIST_CONTENT_URI = Uri
            .parse("content://com.android.firewall/whitelistitems");
    public static final Uri BLACKLIST_CONTENT_URI = Uri
            .parse("content://com.android.firewall/blacklistitems");

    private static final String LOG_TAG = "RCS_UI";

    public static final int MSG_RECEIVE = SuntekMessageData.MSG_RECEIVE;
    public static final String IM_ONLY = "1";
    public static final String SMS_ONLY = "2";
    public static final String RCS_MMS_VCARD_PATH = "sdcard/rcs/" + "mms.vcf";
    static boolean mIsSupportRcs = true; // true for test

    public static boolean isSupportRcs() {
        return mIsSupportRcs;
    }

    public static void setIsSupportRcs(boolean mIsSupportRcs) {
        RcsUtils.mIsSupportRcs = mIsSupportRcs;
    }

    public static GeoLocation readMapXml(String filepath) {
        GeoLocation geo = null;
        try {
            GeoLocationParser handler = new GeoLocationParser(new FileInputStream(
                    new File(filepath)));
            geo = handler.getGeoLocation();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return geo;
    }

    public static void burnMessageAtLocal(final Context context, final long id) {
        String smsId = String.valueOf(id);
        ContentValues values = new ContentValues();
        values.put("rcs_is_burn", 1);
        context.getContentResolver().update(Uri.parse("content://sms/"), values, " _id = ? ",
                new String[] {
                    smsId
                });
    }

    public static void deleteMessageById(Context context, long id) {
        String smsId = String.valueOf(id);
        ContentValues values = new ContentValues();
        context.getContentResolver().delete(Uri.parse("content://sms/"), "_id=?", new String[] {
            smsId
        });
    }

    public static void updateState(Context context, String rcs_id, int rcsMsgState) {
        ContentValues values = new ContentValues();

        switch (rcsMsgState) {
            case SuntekMessageData.MSG_STATE_SEND_FAIL:
                values.put(Sms.TYPE, Sms.MESSAGE_TYPE_FAILED);
                break;
            case SuntekMessageData.MSG_STATE_SEND_ING:
                values.put(Sms.TYPE, Sms.MESSAGE_TYPE_OUTBOX);
                break;
            case SuntekMessageData.MSG_STATE_SENDED:
                values.put(Sms.TYPE, Sms.MESSAGE_TYPE_SENT);
                break;
            case SuntekMessageData.MSG_STATE_DOWNLOAD_FAIL:
                values.put(Sms.TYPE, Sms.MESSAGE_TYPE_INBOX);
                break;
        }

        String selection;
        String[] selectionArgs;

        values.put("rcs_msg_state", rcsMsgState);
        if (rcsMsgState == -1) {
            selection = "rcs_id=? and rcs_chat_type=?";
            selectionArgs = new String[] {
                    rcs_id, "1"
            };
        } else {
            selection = "rcs_id=?";
            selectionArgs = new String[] {
                rcs_id
            };
        }

        ContentResolver resolver = context.getContentResolver();
        int result = resolver.update(Sms.CONTENT_URI, values, selection, selectionArgs);
        if (result == 0) {
            try {
                Thread.sleep(3000);
                int reresult = resolver.update(Sms.CONTENT_URI, values, selection, selectionArgs);
            } catch (Exception e) {
                // TODO: handle exception
            }
        }
    }

    public static void updateManyState(Context context, String rcs_id, String number,
            int rcs_msg_state) {
        ContentValues values = new ContentValues();

        values.put("rcs_msg_state", rcs_msg_state);

        number = number.replaceAll(" ", "");
        String numberW86;
        if (!number.startsWith("+86")) {
            numberW86 = "+86" + number;
        } else {
            numberW86 = number;
            number = number.substring(3);
        }
        String formatNumber = getAndroidFormatNumber(number);
        ContentResolver resolver = context.getContentResolver();
        String selection = "rcs_message_id = ? and ( address = ? OR address = ? OR address = ? )";
        String[] selectionArgs = new String[] {
                rcs_id, number, numberW86, formatNumber
        };
        resolver.update(Sms.CONTENT_URI, values, selection, selectionArgs);
    }

    public static String getAndroidFormatNumber(String number) {
        if (TextUtils.isEmpty(number)) {
            return number;
        }

        number = number.replaceAll(" ", "");

        if (number.startsWith("+86")) {
            number = number.substring(3);
        }

        if (number.length() != 11) {
            return number;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("+86 ");
        builder.append(number.substring(0, 3));
        builder.append(" ");
        builder.append(number.substring(3, 7));
        builder.append(" ");
        builder.append(number.substring(7));
        return builder.toString();
    }

    public static void topSms(Context context, long smsId) {
        ContentValues values = new ContentValues();
        values.put("rcs_top_time", System.currentTimeMillis());
        final Uri THREAD_ID_CONTENT_URI = Uri.parse("content://mms-sms/update-sms-top");
        Uri uri = ContentUris.withAppendedId(THREAD_ID_CONTENT_URI, smsId);
        context.getContentResolver().update(THREAD_ID_CONTENT_URI, values, "_id=?", new String[] {
            String.valueOf(smsId)
        });
    }

    public static void cancelTopSms(Context context, long smsId) {
        ContentValues values = new ContentValues();
        values.put("rcs_top_time", 0);
        final Uri THREAD_ID_CONTENT_URI = Uri.parse("content://mms-sms/update-sms-top");
        Uri uri = ContentUris.withAppendedId(THREAD_ID_CONTENT_URI, smsId);
        context.getContentResolver().update(THREAD_ID_CONTENT_URI, values, "_id=?", new String[] {
            String.valueOf(smsId)
        });
    }

    public static void topConversion(Context context, long mThreadId) {
        ContentValues values = new ContentValues();
        values.put("top", 1);
        values.put("top_time", System.currentTimeMillis());
        final Uri THREAD_ID_CONTENT_URI = Uri.parse("content://mms-sms/update-top");
        Uri uri = ContentUris.withAppendedId(THREAD_ID_CONTENT_URI, mThreadId);
        context.getContentResolver().update(THREAD_ID_CONTENT_URI, values, "_id=?", new String[] {
            mThreadId + ""
        });
    }

    public static void cancelTopConversion(Context context, long mThreadId) {
        ContentValues values = new ContentValues();
        values.put("top", 0);
        values.put("top_time", 0);
        final Uri THREAD_ID_CONTENT_URI = Uri.parse("content://mms-sms/update-top");
        Uri uri = ContentUris.withAppendedId(THREAD_ID_CONTENT_URI, mThreadId);
        context.getContentResolver().update(THREAD_ID_CONTENT_URI, values, "_id=?", new String[] {
            mThreadId + ""
        });
    }

    public static void updateFileDownloadState(Context context, String rcs_message_id) {
        ContentValues values = new ContentValues();
        values.put("rcs_is_download", 1);
        context.getContentResolver().update(Sms.CONTENT_URI, values, "rcs_message_id=?",
                new String[] {
                    rcs_message_id
                });
    }

    public static String getFilePath(int id, String str) {
        try {
            ChatMessage cMsg = RcsApiManager.getMessageApi().getMessageById(String.valueOf(id));
            String imagePath = RcsApiManager.getMessageApi().getFilepath(cMsg);
            if (imagePath != null && new File(imagePath).exists()) {
                return imagePath;
            } else {
                String path = RcsApiManager.getMessageApi().getFilepath(cMsg);
                if (path != null && path.lastIndexOf("/") != -1) {
                    path = path.substring(0, path.lastIndexOf("/") + 1);
                    return path + cMsg.getFilename();
                } else {
                    return str;
                }
            }
        } catch (Exception e) {
            // TODO: handle exception
        }
        return str;

    }

    /**
     * @param context
     * @param body
     * @param address
     * @param is_rcs
     * @param rcs_msg_type
     * @param rcs_mime_type
     * @param rcs_have_attach
     * @param rcs_path
     */
    public static void rcsInsertInbox(Context context, String body, String address, int is_rcs,
            int rcs_msg_type, String rcs_mime_type, int rcs_have_attach, String rcs_path) {
        ContentValues values = new ContentValues();
        values.put(Inbox.BODY, body);
        values.put(Inbox.ADDRESS, address);
        values.put("is_rcs", is_rcs);

        /*
         * rcs_msg_type:
         * 0 text,
         * 1 image,
         * 2 video,
         * 3 audio,
         * 4 map,
         * 5 vcard
         */
        values.put("rcs_msg_type", rcs_msg_type);
        values.put("rcs_mime_type", rcs_mime_type);
        values.put("rcs_have_attach", rcs_have_attach);
        values.put("rcs_path", rcs_path);
        Long threadId = Conversation.getOrCreateThreadId(context, address);
        ContentResolver resolver = context.getContentResolver();
        Uri insertedUri = SqliteWrapper.insert(context, resolver, Inbox.CONTENT_URI, values);
        // Now make sure we're not over the limit in stored messages
        Recycler.getSmsRecycler().deleteOldMessagesByThreadId(context, threadId);
        MmsWidgetProvider.notifyDatasetChanged(context);
    }

    public static long rcsInsert(Context context, ChatMessage chatMessage)
            throws ServiceDisconnectedException {
        MessageApi messageApi = RcsApiManager.getMessageApi();

        String address = chatMessage.getContact();
        String body = chatMessage.getData();
        int rcs_msg_type = chatMessage.getMsgType();
        int send_receive = chatMessage.getSendReceive();
        String rcs_mime_type = chatMessage.getMimeType();
        int rcs_have_attach = 1;
        if (SuntekMessageData.MSG_TYPE_IMAGE == rcs_msg_type && body != null) {
            if (body.toLowerCase().endsWith("gif")) {
                rcs_mime_type = "image/gif";
            } else if (body.toLowerCase().endsWith("bmp")) {
                rcs_mime_type = "image/bmp";
            } else if (body.toLowerCase().endsWith("jpg")) {
                rcs_mime_type = "image/*";
            } else if (body.toLowerCase().endsWith("jpeg")) {
                rcs_mime_type = "image/*";
            } else if (body.toLowerCase().endsWith("png")) {
                rcs_mime_type = "image/png";
            }
        }
        String rcs_message_id = chatMessage.getMessageId();
        int rcs_burn_flag = chatMessage.getMsgBurnAfterReadFlag();
        int rcs_chat_type = chatMessage.getChatType();
        long rcsThreadId = chatMessage.getThreadId();
        long fileSize = chatMessage.getFilesize();

        int playTime = 0;
        String rcs_path = "";
        String rcs_thumb_path = "";
        switch (rcs_msg_type) {
            case SuntekMessageData.MSG_TYPE_TEXT:
                break;
            case SuntekMessageData.MSG_TYPE_MAP:
                rcs_path = getFilePath(chatMessage);
                break;
            case SuntekMessageData.MSG_TYPE_VIDEO:
                rcs_path = getFilePath(chatMessage);
                rcs_thumb_path = messageApi.getThumbFilepath(chatMessage);
                playTime = messageApi.getPlayTime(rcs_msg_type, chatMessage.getData());
                break;
            case SuntekMessageData.MSG_TYPE_AUDIO:
                rcs_path = getFilePath(chatMessage);
                playTime = messageApi.getPlayTime(rcs_msg_type, chatMessage.getData());
                break;
            case SuntekMessageData.MSG_TYPE_IMAGE:
            case SuntekMessageData.MSG_TYPE_GIF:
            case SuntekMessageData.MSG_TYPE_CONTACT:
                rcs_path = getFilePath(chatMessage);
                rcs_thumb_path = messageApi.getThumbFilepath(chatMessage);
                break;
            case SuntekMessageData.MSG_TYPE_PAID_EMO:
                body = chatMessage.getData() + "," + chatMessage.getFilename();
                break;
        }

        int rcs_msg_state = chatMessage.getMsgState();

        if (send_receive == 2 && rcs_msg_type == RcsUtils.RCS_MSG_TYPE_IMAGE) {
            rcs_thumb_path = rcs_path;
        }

        if (rcs_msg_type == SuntekMessageData.MSG_TYPE_NOTIFICATION && TextUtils.isEmpty(address)) {
            address = String.valueOf(rcsThreadId);
        }

        Uri uri;
        if (send_receive == 1) {
            uri = Inbox.CONTENT_URI;
        } else if (rcs_msg_type == SuntekMessageData.MSG_TYPE_NOTIFICATION) {
            // Group chat notification message.
            uri = Inbox.CONTENT_URI;
        } else {
            uri = Outbox.CONTENT_URI;
        }

        if (address != null && address.contains(",")) {
            String[] addresslist = address.split(",");

            HashSet<String> recipients = new HashSet<String>();
            for (int i = 0; i < addresslist.length; i++) {
                recipients.add(addresslist[i]);
            }
            Long threadId = Threads.getOrCreateThreadId(context, recipients);

            ContentResolver resolver = context.getContentResolver();
            for (int i = 0; i < addresslist.length; i++) {
                ContentValues values = new ContentValues();
                if (rcs_burn_flag == SuntekMessageData.MSG_BURN_AFTER_READ_FLAG) {
                    values.put(Sms.BODY, "burnMessage");
                    values.put("rcs_burn_body", body);
                } else {
                    values.put(Sms.BODY, body);
                }
                values.put(Sms.ADDRESS, addresslist[i]);
                if (send_receive == 2) {
                    values.put("type", 2);
                }
                values.put("is_rcs", 1);
                values.put("rcs_msg_type", rcs_msg_type);
                values.put("rcs_mime_type", rcs_mime_type); // text or image
                values.put("rcs_have_attach", rcs_have_attach);
                values.put("rcs_path", rcs_path);
                values.put("rcs_thumb_path", rcs_thumb_path);
                values.put("thread_id", threadId);
                values.put("rcs_id", chatMessage.getId());
                values.put("rcs_burn_flag", rcs_burn_flag);
                values.put("rcs_message_id", rcs_message_id);
                values.put("rcs_chat_type", rcs_chat_type);
                values.put("rcs_file_size", fileSize);
                values.put("rcs_msg_state", rcs_msg_state);
                values.put("rcs_play_time", playTime);

                Uri insertedUri = SqliteWrapper.insert(context, resolver, uri, values);
                // Now make sure we're not over the limit in stored messages
                Recycler.getSmsRecycler().deleteOldMessagesByThreadId(context, threadId);
                MmsWidgetProvider.notifyDatasetChanged(context);
            }

            return threadId;
        } else {
            ContentValues values = new ContentValues();
            if (rcs_burn_flag == SuntekMessageData.MSG_BURN_AFTER_READ_FLAG) {
                values.put(Sms.BODY, "burnMessage");
                values.put("rcs_burn_body", body);
            } else {
                values.put(Sms.BODY, body);
            }
            values.put(Sms.ADDRESS, address);
            values.put("is_rcs", 1);
            values.put("rcs_msg_type", rcs_msg_type);
            values.put("rcs_mime_type", rcs_mime_type); // text or image
            values.put("rcs_have_attach", rcs_have_attach);
            values.put("rcs_path", rcs_path);
            values.put("rcs_thumb_path", rcs_thumb_path);
            values.put("rcs_id", chatMessage.getId());
            values.put("rcs_burn_flag", rcs_burn_flag);
            values.put("rcs_message_id", rcs_message_id);
            values.put("rcs_chat_type", rcs_chat_type);
            values.put("rcs_file_size", fileSize);
            values.put("rcs_play_time", playTime);
            values.put("rcs_msg_state", rcs_msg_state);
            if (send_receive == 2) {
                values.put("type", 2);
            }

            long threadId;
            if (rcs_chat_type == SuntekMessageData.CHAT_TYPE_GROUP) {
                HashSet<String> recipients = new HashSet<String>();
                recipients.add(String.valueOf(rcsThreadId));
                threadId = getOrCreateThreadId(context, recipients);
            } else {
                ArrayList<String> numbers = new ArrayList<String>();
                numbers.add(String.valueOf(address));
                ContactList recipients = ContactList.getByNumbers(numbers, false);
                Conversation conversation = Conversation.get(context, recipients, true);
                if (conversation == null) {
                    threadId = Conversation.getOrCreateThreadId(context, address);
                } else {
                    threadId = conversation.getThreadId();
                }
            }
            values.put("thread_id", threadId);

            ContentResolver resolver = context.getContentResolver();
            Uri insertedUri = SqliteWrapper.insert(context, resolver, uri, values);
            // Now make sure we're not over the limit in stored messages
            Recycler.getSmsRecycler().deleteOldMessagesByThreadId(context, threadId);
            MmsWidgetProvider.notifyDatasetChanged(context);
            return threadId;
        }
    }

    public static String getFilePath(ChatMessage cMsg) throws ServiceDisconnectedException {
        String imagePath = RcsApiManager.getMessageApi().getFilepath(cMsg);
        if (imagePath != null && new File(imagePath).exists()) {
            return imagePath;
        } else {
            String path = RcsApiManager.getMessageApi().getFilepath(cMsg);
            if (path != null && path.lastIndexOf("/") != -1) {
                path = path.substring(0, path.lastIndexOf("/") + 1);
                return path + cMsg.getFilename();
            } else {
                return null;
            }
        }
    }

    public static long getThreadIdByRcsMesssageId(Context context, long rcs_id) {
        long threadId = 0;
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = SqliteWrapper.query(context, resolver, Sms.CONTENT_URI, new String[] {
            Sms.THREAD_ID
        }, "rcs_id=?", new String[] {
            String.valueOf(rcs_id)
        }, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    threadId = cursor.getLong(0);
                }
            } finally {
                cursor.close();
            }
        }

        Log.d(LOG_TAG, "getThreadIdByRcsMessageId(): rcs_id=" + rcs_id + ", threadId=" + threadId);

        return threadId;
    }

    public static long getRcsThreadIdByThreadId(Context context, long threadId) {
        long rcsThreadId = 0;

        ContentResolver resolver = context.getContentResolver();
        Uri uri = Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build();
        Cursor cursor = SqliteWrapper.query(context, resolver, uri, new String[] {
            Telephony.Threads.RECIPIENT_IDS
        }, Telephony.Threads._ID + "=?", new String[] {
            String.valueOf(threadId)
        }, null);

        String recipientId = "";
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    recipientId = cursor.getString(0);
                }
            } finally {
                cursor.close();
            }
        }

        Log.d(LOG_TAG, "getRcsThreadIdByThreadId(): threadId=" + threadId + ", recipientId="
                + recipientId);

        if (TextUtils.isEmpty(recipientId)) {
            return rcsThreadId;
        }

        uri = Uri.withAppendedPath(Telephony.MmsSms.CONTENT_URI, "canonical-addresses");
        cursor = SqliteWrapper.query(context, resolver, uri, new String[] {
                CanonicalAddressesColumns._ID, CanonicalAddressesColumns.ADDRESS
        }, Telephony.CanonicalAddressesColumns._ID + "=?", new String[] {
            String.valueOf(recipientId)
        }, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    rcsThreadId = cursor.getLong(1);
                }
            } catch (Exception e) {
                // Just let the exception to if it's not an rcsThreadId.
            } finally {
                cursor.close();
            }
        }

        Log.d(LOG_TAG, "getRcsThreadIdByThreadId(): threadId=" + threadId + ", recipientId="
                + recipientId + ", rcsThreadId=" + rcsThreadId);

        return rcsThreadId;
    }

    public static long getThreadIdByGroupId(Context context, String groupId) {
        long threadId = 0;

        if (groupId == null) {
            return threadId;
        }
        GroupChatModel groupChat = null;
        try {
            groupChat = RcsApiManager.getMessageApi().getGroupChatById(groupId);
        } catch (ServiceDisconnectedException e) {
            Log.w(LOG_TAG, e);
        }

        if (groupChat == null) {
            return threadId;
        }

        long rcsThreadId = groupChat.getThreadId();

        ContentResolver resolver = context.getContentResolver();

        Uri uri = Uri.withAppendedPath(Telephony.MmsSms.CONTENT_URI, "canonical-addresses");
        Cursor cursor = SqliteWrapper.query(context, resolver, uri, new String[] {
            Telephony.CanonicalAddressesColumns._ID
        }, Telephony.CanonicalAddressesColumns.ADDRESS + "=?", new String[] {
            String.valueOf(rcsThreadId)
        }, null);

        int recipientId = 0;
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    recipientId = cursor.getInt(0);
                }
            } finally {
                cursor.close();
            }
        }

        Log.d(LOG_TAG, "getThreadIdByRcsMessageId(): groupId=" + groupId + ", recipientId="
                + recipientId);

        if (recipientId > 0) {
            uri = Threads.CONTENT_URI.buildUpon().appendQueryParameter("simple", "true").build();
            cursor = SqliteWrapper.query(context, resolver, uri, new String[] {
                Telephony.Threads._ID
            }, Telephony.Threads.RECIPIENT_IDS + "=?", new String[] {
                String.valueOf(recipientId)
            }, null);

            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        threadId = cursor.getLong(0);
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        Log.d(LOG_TAG, "getThreadIdByRcsMessageId(): groupId=" + groupId + ", recipientId="
                + recipientId + ", threadId=" + threadId);

        return threadId;
    }

    public static int getDuration(Context context, Uri uri) {
        MediaPlayer player = MediaPlayer.create(context, uri);
        if (player == null) {
            return 0;
        }
        return player.getDuration() / 1000;
    }

    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @author paulburke
     */
    public static String getPath(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[] {
                    split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri The Uri to query.
     * @param selection (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     */
    public static String getDataColumn(Context context, Uri uri, String selection,
            String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
            column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * Launch the RCS group chat detail activity.
     */
    public static void startGroupChatDetailActivity(Context context, GroupChatModel groupChat) {
        if (groupChat != null) {
            String groupId = String.valueOf(groupChat.getId());
            RcsUtils.startGroupChatDetailActivity(context, groupId);
        }
    }

    /**
     * Launch the RCS group chat detail activity.
     */
    public static void startGroupChatDetailActivity(Context context, String groupId) {
        Intent intent = new Intent("com.suntek.mway.rcs.nativeui.ui.RcsGroupChatDetailActivity");
        intent.putExtra("groupId", groupId);
        if (isActivityIntentAvailable(context, intent)) {
            context.startActivity(intent);
        }
    }

    /**
     * Launch the RCS notify list activity.
     */
    public static void startNotificationListActivity(Context context) {
        Intent intent = new Intent("com.suntek.mway.rcs.nativeui.ui.RcsNotificationListActivity");
        if (isActivityIntentAvailable(context, intent)) {
            context.startActivity(intent);
        }
    }

    /**
     * This method is temporally copied from /framework/opt/telephone for RCS
     * group chat debug purpose.
     *
     * @hide
     */
    public static long getOrCreateThreadId(Context context, Set<String> recipients) {
        Uri.Builder uriBuilder = Uri.parse("content://mms-sms/threadID").buildUpon();

        for (String recipient : recipients) {
            if (Mms.isEmailAddress(recipient)) {
                recipient = Mms.extractAddrSpec(recipient);
            }

            uriBuilder.appendQueryParameter("recipient", recipient);
        }
        Log.d(LOG_TAG, "uriBuilder.appendQueryParameter(\"isGroupChat\", \"1\");");
        uriBuilder.appendQueryParameter("isGroupChat", "1");

        Uri uri = uriBuilder.build();
        // if (DEBUG) Rlog.v(TAG, "getOrCreateThreadId uri: " + uri);

        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(), uri,
                new String[] {
                    BaseColumns._ID
                }, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0);
                } else {
                    Log.e(LOG_TAG, "getOrCreateThreadId returned no rows!");
                }
            } finally {
                cursor.close();
            }
        }

        Log.e(LOG_TAG, "getOrCreateThreadId failed with uri " + uri.toString());
        throw new IllegalArgumentException("Unable to find or allocate a thread ID.");
    }

    public static boolean isActivityIntentAvailable(Context context, Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    public static boolean isPackageInstalled(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> installedApps = pm
                .getInstalledApplications(PackageManager.GET_UNINSTALLED_PACKAGES);
        for (ApplicationInfo info : installedApps) {
            if (packageName.equals(info.packageName)) {
                return true;
            }
        }
        return false;
    }

    public static void onShowConferenceCallStartScreen(Context context) {
        onShowConferenceCallStartScreen(context, null);
    }

    public static void onShowConferenceCallStartScreen(Context context, String number) {
        Intent intent = new Intent("android.intent.action.ADDPARTICIPANT");
        if (!TextUtils.isEmpty(number)) {
            intent.putExtra("confernece_number_key", number);
        }
        if (isActivityIntentAvailable(context, intent)) {
            context.startActivity(intent);
        } else {
            Toast.makeText(context, "Activity not found.", Toast.LENGTH_LONG).show();
        }
    }

    public static void dumpCursorRows(Cursor cursor) {
        int count = cursor.getColumnCount();
        Log.d(LOG_TAG, "------ dump cursor row ------");
        for (int i = 0; i < count; i++) {
            Log.d(LOG_TAG, cursor.getColumnName(i) + "=" + cursor.getString(i));
        }
    }

    public static void dumpIntent(Intent intent) {
        String action = intent.getAction();
        Bundle extras = intent.getExtras();

        Log.d(LOG_TAG, "============ onReceive ============");
        Log.d(LOG_TAG, "action=" + action);
        if (extras != null) {
            for (String key : extras.keySet()) {
                Log.d(LOG_TAG, key + "=" + extras.get(key));
            }
        }
    }

    /**
     * Get the chat group name for display. Return 'subject' if the 'remark' is
     * empry.
     */
    public static String getDisplayName(GroupChatModel groupChat) {
        if (groupChat == null) {
            return "";
        }

        String remark = groupChat.getRemark();
        if (!TextUtils.isEmpty(remark)) {
            return remark;
        } else {
            String subject = groupChat.getSubject();
            if (!TextUtils.isEmpty(subject)) {
                return subject;
            } else {
                return "";
            }
        }
    }

    /**
     * Launch the activity for creating rcs group chat.
     *
     * @param context
     * @param number numbers, split by ";". For example: 13800138000;10086
     * @param message
     */
    public static void startCreateGroupChatActivity(Context context, String number,
            String message) {
        Intent sendIntent = new Intent(Intent.ACTION_VIEW);
        if (!TextUtils.isEmpty(number)) {
            sendIntent.putExtra("address", number);
        }
        if (!TextUtils.isEmpty(message)) {
            sendIntent.putExtra("sms_body", message);
        }
        sendIntent.putExtra("isGroupChat", true);
        sendIntent.setType("vnd.android-dir/mms-sms");
        context.startActivity(sendIntent);
    }

    public static String getStringOfNotificationBody(Context context, String body) {
        if (body != null) {
            if (body.equals(GROUP_CHAT_NOTIFICATION_KEY_WORDS_CREATED)) {
                body = context.getString(R.string.group_chat_created);
            } else if (body.startsWith(GROUP_CHAT_NOTIFICATION_KEY_WORDS_CHAIRMAN)) {
                String chairmanNumber = body.substring(body.indexOf(",") + 1);
                body = context.getString(R.string.group_chat_update_chairman, chairmanNumber);
            } else if (body.startsWith(GROUP_CHAT_NOTIFICATION_KEY_WORDS_JOIN)) {
                String joinNumber = body.substring(body.indexOf(",") + 1);
                body = context.getString(R.string.group_chat_join, joinNumber);
            } else if (body.startsWith(GROUP_CHAT_NOTIFICATION_KEY_WORDS_SUBJECT)) {
                String subject = body.substring(body.indexOf(",") + 1);
                body = context.getString(R.string.group_chat_subject, subject);
            } else if (body.startsWith(GROUP_CHAT_NOTIFICATION_KEY_WORDS_REMARK)) {
                String remark = body.substring(body.indexOf(",") + 1);
                body = context.getString(R.string.group_chat_remark, remark);
            } else if (body.equals(GROUP_CHAT_NOTIFICATION_KEY_WORDS_ACTIVE)) {
                body = context.getString(R.string.group_chat_active);
            } else if (body.startsWith(GROUP_CHAT_NOTIFICATION_KEY_WORDS_ALIAS)) {
                String[] params = body.split(",");
                if (params.length == 3) {
                    body = context.getString(R.string.group_chat_alias, params[1], params[2]);
                }
            } else if (body.startsWith(GROUP_CHAT_NOTIFICATION_KEY_WORDS_TICK)) {
                String number = body.substring(body.indexOf(",") + 1);
                body = context.getString(R.string.group_chat_kick, number);
            } else if (body.startsWith(GROUP_CHAT_NOTIFICATION_KEY_WORDS_QUIT)) {
                String number = body.substring(body.indexOf(",") + 1);
                body = context.getString(R.string.group_chat_quit, number);
            } else if (body.startsWith(GROUP_CHAT_NOTIFICATION_KEY_WORDS_DISBAND)) {
                body = context.getString(R.string.group_chat_disbanded);
            } else if (body.startsWith(GROUP_CHAT_NOTIFICATION_KEY_WORDS_POLICY)) {
                body = context.getString(R.string.group_chat_policy);
            }
        }
        return body;
    }

    public static void UpdateGroupChatSubject(Context context,GroupChatModel groupChatModel){
        if(context == null) return;
        final ContentResolver resolver = context.getContentResolver();
        String thread_id = String.valueOf(groupChatModel.getThreadId());
        String group_id = String.valueOf(groupChatModel.getId());
        String groupTitle = TextUtils.isEmpty(groupChatModel
                .getRemark()) ? groupChatModel.getSubject()
                : groupChatModel.getRemark();
        final ContentValues values = new ContentValues();
        values.put(Groups.TITLE, groupTitle);
        values.put(Groups.SYSTEM_ID, group_id);
        values.put(Groups.SOURCE_ID, "RCS");
        StringBuilder where = new StringBuilder();
        where.append(Groups.SYSTEM_ID);
        where.append("=" + group_id);
        final String selection = where.toString();
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    resolver.update(Groups.CONTENT_URI, values, selection, null);
                    return true;
                } catch(Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean value) {
                super.onPostExecute(value);
            }
        }.execute();
    }

    public static void createGroupChat(Context context, final String group_id,
            final long rcsThreadId) {
        if (context == null) return;
        final ContentResolver resolver = context.getContentResolver();

        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    Cursor groupCount = resolver.query(Groups.CONTENT_URI, null,
                    Groups.SYSTEM_ID + " = " + group_id, null, null);
                    if (null != groupCount) {
                        if (groupCount.getCount() > 0) {
                            groupCount.close();
                            return true;
                        }
                        groupCount.close();
                    }
                    final ContentValues values = new ContentValues();
                    MessageApi messageApi = RcsApiManager.getMessageApi();
                    String groupTitle = "";
                    try {
                        GroupChatModel model = messageApi.getGroupChatByThreadId(rcsThreadId);

                        if (model != null) {
                            groupTitle = TextUtils.isEmpty(model
                                    .getRemark()) ? model.getSubject()
                                    : model.getRemark();
                        }
                    } catch (ServiceDisconnectedException e) {
                        Log.i(LOG_TAG, "GroupChatMessage" + e);
                    }
                    values.put(Groups.TITLE, groupTitle);
                    values.put(Groups.SYSTEM_ID, group_id);
                    values.put(Groups.SOURCE_ID, "RCS");
                    resolver.insert(Groups.CONTENT_URI, values);
                    return true;
                } catch(Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean value) {
                super.onPostExecute(value);
            }
        }.execute();
    }

    public static void disbandGroupChat(Context context,GroupChatModel groupChatModel){
        if(context == null) return;
        final ContentResolver resolver = context.getContentResolver();
        String thread_id = String.valueOf(groupChatModel.getThreadId());
        String group_id = String.valueOf(groupChatModel.getId());
        StringBuilder where = new StringBuilder();
        where.append(Groups.SYSTEM_ID);
        where.append("="+group_id);
        final String selection = where.toString();

        new AsyncTask<Void, Void, Boolean>(){
            @Override
            protected Boolean doInBackground(Void... params) {
                try{
                    resolver.delete(Groups.CONTENT_URI, selection, null);
                    return true;
                } catch(Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean value) {
                super.onPostExecute(value);
            }
        }.execute();
    }

    /**
     * Make sure the bytes length of <b>src</b> is less than <b>bytesLength</b>.
     */
    public static String trimToSpecificBytesLength(String src, int bytesLength) {
        String dst = "";
        if (src != null) {
            int subjectBytesLength = src.getBytes().length;
            if (subjectBytesLength > bytesLength) {
                int subjectCharCount = src.length();
                for (int i = 0; i < subjectCharCount; i++) {
                    char c = src.charAt(i);
                    if ((dst + c).getBytes().length > bytesLength) {
                        break;
                    } else {
                        dst = dst + c;
                    }
                }

                src = dst;
            } else {
                dst = src;
            }
        } else {
            dst = src;
        }

        return dst;
    }

    public static boolean setVcard(final Context context, Uri uri) {
        InputStream instream = null;

        FileOutputStream fout = null;
        try {

            AssetFileDescriptor fd = context.getContentResolver().openAssetFileDescriptor(uri, "r");
            instream = fd.createInputStream();
            File file = new File(RCS_MMS_VCARD_PATH);

            fout = new FileOutputStream(file);

            byte[] buffer = new byte[8000];
            int size = 0;
            while ((size = instream.read(buffer)) != -1) {
                fout.write(buffer, 0, size);
            }

            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri
                    .fromFile(file)));

        } catch (IOException e) {

        } finally {
            if (null != instream) {
                try {
                    instream.close();
                } catch (IOException e) {

                    return false;
                }
            }
            if (null != fout) {
                try {
                    fout.close();
                } catch (IOException e) {

                    return false;
                }
            }
            return true;
        }
    }

    public static Bitmap createBitmap_Compress(String absFilePath) {
        try {
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            options.inPurgeable = true;
            BitmapFactory.decodeFile(absFilePath, options);

            options.inSampleSize = calculateInSampleSize(options, 480, 800);
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(absFilePath, options);
        } catch (Exception e) {
            e.printStackTrace();
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
        }
        return null;
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth,
            int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }
        return inSampleSize;
    }

    public static Bitmap decodeInSampleSizeBitmap(String imageFilePath) {
        Bitmap bitmap;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        bitmap = BitmapFactory.decodeFile(imageFilePath, options);
        options.inJustDecodeBounds = false;

        int inSampleSize = (int) (options.outHeight / (float) 200);
        if (inSampleSize <= 0)
            inSampleSize = 1;
        options.inSampleSize = inSampleSize;

        bitmap = BitmapFactory.decodeFile(imageFilePath, options);

        return bitmap;
    }

    @SuppressWarnings("deprecation")
    public static Drawable createDrawable(Context context, Bitmap bitmap) {
        if (bitmap == null)
            return null;

        byte[] ninePatch = bitmap.getNinePatchChunk();
        if (ninePatch != null && ninePatch.length > 0) {
            NinePatch np = new NinePatch(bitmap, ninePatch, null);
            return new NinePatchDrawable(context.getResources(), np);
        }
        return new BitmapDrawable(bitmap);
    }

    public static long getImageFtMaxSize(){
        try {
            return RcsApiManager.getMessageApi().getImageFtMaxSize();
        } catch (ServiceDisconnectedException exception) {
            exception.printStackTrace();
            return 0;
        }
    }

    public static long getAudioMaxTime() {
        try {
            return RcsApiManager.getMessageApi().getAudioMaxTime();
        } catch (ServiceDisconnectedException exception) {
            exception.printStackTrace();
            return 0;
        }
    }

    public static long getVideoMaxTime() {
        try {
            return RcsApiManager.getMessageApi().getVideoMaxTime();
        } catch (ServiceDisconnectedException exception) {
            exception.printStackTrace();
            return 0;
        }
    }

    public static long getVideoFtMaxSize() {
        try {
            return RcsApiManager.getMessageApi().getVideoFtMaxSize();
        } catch (ServiceDisconnectedException exception) {
            exception.printStackTrace();
            return 0;
        }
    }

    public static boolean isLoading(String filePath, long fileSize) {
        if (TextUtils.isEmpty(filePath)) {
            return false;
        }
        File file = new File(filePath);
        if (file.exists() && file.length() < fileSize) {
            return true;
        } else {
            return false;
        }
    }

    public static void addNumberToFirewall(Context context, ContactList list, boolean isBlacklist) {
        String number = list.get(0).getNumber();
        if (null == number || number.length() <= 0) {
            // number length is not allowed 0-
            Toast.makeText(context, context.getString(R.string.firewall_number_len_not_valid),
                    Toast.LENGTH_SHORT).show();

            return;
        }

        ContentValues values = new ContentValues();

        number = number.replaceAll(" ", "");
        number = number.replaceAll("-", "");
        String comparenNumber = number;
        int len = comparenNumber.length();
        if (len > 11) {
            comparenNumber = number.substring(len - 11, len);
        }
        Uri blockUri = isBlacklist ? RcsUtils.BLACKLIST_CONTENT_URI
                : RcsUtils.WHITELIST_CONTENT_URI;
        ContentResolver contentResolver = context.getContentResolver();
        Cursor cu = contentResolver.query(blockUri, new String[] {
                "_id", "number", "person_id", "name"
        }, "number" + " LIKE '%" + comparenNumber + "'", null, null);
        if (cu != null) {
            if (cu.getCount() > 0) {
                cu.close();
                cu = null;
                String Stoast = isBlacklist ? context.getString(R.string.firewall_number_in_black)
                        : context.getString(R.string.firewall_number_in_white);
                Toast.makeText(context, Stoast, Toast.LENGTH_SHORT).show();
                return;
            }
            cu.close();
            cu = null;
        }

        values.put("number", comparenNumber);
        Uri mUri = contentResolver.insert(blockUri, values);

        Toast.makeText(context, context.getString(R.string.firewall_save_success),
                Toast.LENGTH_SHORT).show();
    }

    public static Intent createGroupChatIntent(Context context, long threadId) {
        Intent intent = ComposeMessageActivity.createIntent(context, threadId);
        intent.putExtra("isGroupChat", true);
        return intent;
    }

    public static boolean isFireWallInstalled(Context context) {
        boolean installed = false;
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(
                    FIREWALL_APK_NAME, PackageManager.GET_PROVIDERS);
            installed = (info != null);
        } catch (NameNotFoundException e) {
        }
        return installed;
    }

    public static String getNumbersExceptMe(ContactList recipients)
            throws ServiceDisconnectedException {
        RcsAccountApi accountApi = RcsApiManager.getRcsAccountApi();
        String myPhoneNumber = accountApi.getRcsUserProfileInfo().getUserName();
        String numbers = "";

        int size = recipients.size();
        for (int i = 0; i < size; i++) {
            String number = recipients.get(i).getNumber();

            // Skip my phone number.
            if (myPhoneNumber != null && myPhoneNumber.endsWith(number)) {
                continue;
            }

            numbers += number;
            if (i + 1 < size) {
                numbers += ";";
            }
        }

        return numbers;
    }

    public static String getGroupChatDialNumbers(GroupChatModel groupChat)
            throws ServiceDisconnectedException {
        String numbers = "";
        if (groupChat != null) {
            List<GroupChatUser> users = groupChat.getUserList();
            if (users != null) {
                RcsAccountApi accountApi = RcsApiManager.getRcsAccountApi();
                String myPhoneNumber = accountApi.getRcsUserProfileInfo().getUserName();

                int size = users.size();
                for (int i = 0; i < size; i++) {
                    String number = users.get(i).getNumber();

                    // Skip my phone number.
                    if (myPhoneNumber != null && myPhoneNumber.endsWith(number)) {
                        continue;
                    }

                    numbers += number;
                    if (i + 1 < size) {
                        numbers += ";";
                    }
                }
            }
        }

        return numbers;
    }

    public static IntentFilter createIntentFilterForComposeMessage(boolean isGroupChat) {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BroadcastConstants.UI_ALERT_FILE_SUFFIX_INVALID);
        filter.addAction(BroadcastConstants.UI_ALERT_FILE_TOO_LARGE);
        filter.addAction(BroadcastConstants.UI_DOWNLOADING_FILE_CHANGE);
        filter.addAction(BroadcastConstants.UI_MESSAGE_ADD_DATABASE);
        filter.addAction(BroadcastConstants.UI_MESSAGE_STATUS_CHANGE_NOTIFY);
        filter.addAction(BroadcastConstants.UI_REFRESH_MESSAGE_LIST);
        filter.addAction(BroadcastConstants.UI_SHOW_MESSAGE_NOTIFY);
        filter.addAction(BroadcastConstants.UI_SHOW_MESSAGE_SEND_ERROR);
        if (isGroupChat) {
            filter.addAction(BroadcastConstants.UI_GROUP_MANAGE_NOTIFY);
            filter.addAction(BroadcastConstants.UI_GROUP_CHAT_SUBJECT_CHANGE);
            filter.addAction(BroadcastConstants.UI_INVITE_TO_JOIN_GROUP);
            filter.addAction(BroadcastConstants.UI_SHOW_GROUP_MESSAGE_NOTIFY);
            filter.addAction(BroadcastConstants.UI_SHOW_GROUP_REFER_ERROR);
        }

        return filter;
    }

    public static void dialGroupChat(Context context, GroupChatModel groupChat) {
        try {
            String dialNumbers = RcsUtils.getGroupChatDialNumbers(groupChat);
            RcsUtils.onShowConferenceCallStartScreen(context, dialNumbers);
        } catch (Exception e) {
            RcsUtils.onShowConferenceCallStartScreen(context);
        }
    }

    public static void dialConferenceCall(Context context, ContactList recipients) {
        try {
            String dialNumbers = RcsUtils.getNumbersExceptMe(recipients);
            RcsUtils.onShowConferenceCallStartScreen(context, dialNumbers);
        } catch (Exception e) {
            RcsUtils.onShowConferenceCallStartScreen(context);
        }
    }

    public static void disposeRcsSendMessageException(Context context, Exception exception,
            int msgType) {
        exception.printStackTrace();
        if (exception instanceof FileSuffixException) {
            Looper.prepare();
            Toast.makeText(context, R.string.file_suffix_vaild_tip, Toast.LENGTH_LONG).show();
            Looper.loop();
        } else if (exception instanceof FileTransferException) {
            Looper.prepare();
            Toast.makeText(context, R.string.file_size_over, Toast.LENGTH_LONG).show();
            Looper.loop();
        } else if (exception instanceof FileDurationException) {
            Looper.prepare();
            if (msgType == RcsUtils.RCS_MSG_TYPE_VIDEO) {
                Toast.makeText(context,
                        context.getString(R.string.video_record_out_time, getVideoMaxTime()),
                        Toast.LENGTH_SHORT).show();
            } else if (msgType == RcsUtils.RCS_MSG_TYPE_AUDIO) {
                Toast.makeText(context,
                        context.getString(R.string.audio_record_out_time, getAudioMaxTime()),
                        Toast.LENGTH_SHORT).show();
            }
            Looper.loop();
        } else if (exception instanceof NumberFormatException) {
            exception.printStackTrace();
        } else {
            exception.printStackTrace();
        }
    }

    public static void setThumbnailForMessageItem(Context context, ImageView imageView,
            MessageItem messageItem) {
        if (messageItem.mRcsType == RcsUtils.RCS_MSG_TYPE_PAID_EMO) {
            String[] body = messageItem.mBody.split(",");
            RcsEmojiStoreUtil.getInstance().loadImageAsynById(imageView, body[0],
                    RcsEmojiStoreUtil.EMO_STATIC_FILE);
            return;
        }
        Bitmap bitmap = null;
        switch (messageItem.mRcsType) {
            case RcsUtils.RCS_MSG_TYPE_CAIYUNFILE: {
                bitmap = BitmapFactory.decodeResource(context.getResources(),
                        R.drawable.rcs_caiyun_sharefile);
                break;
            }
            case RcsUtils.RCS_MSG_TYPE_IMAGE: {
                if (messageItem.mRcsThumbPath != null
                        && new File(messageItem.mRcsThumbPath).exists()) {
                } else if (messageItem.mRcsThumbPath != null
                        && messageItem.mRcsThumbPath.contains(".")) {
                    messageItem.mRcsThumbPath = messageItem.mRcsThumbPath.substring(0,
                            messageItem.mRcsThumbPath.lastIndexOf("."));
                }
                bitmap = RcsUtils.decodeInSampleSizeBitmap(messageItem.mRcsThumbPath);
                break;
            }
            case RcsUtils.RCS_MSG_TYPE_VIDEO: {
                bitmap = BitmapFactory.decodeFile(messageItem.mRcsThumbPath);
                break;
            }
            case RcsUtils.RCS_MSG_TYPE_VCARD: {
                String vcardFilePath = getFilePath(messageItem.mRcsId, messageItem.mRcsPath);
                ArrayList<PropertyNode> propList = RcsMessageOpenUtils.openRcsVcardDetail(
                        context, vcardFilePath);
                for (PropertyNode propertyNode : propList) {
                    if (propertyNode.propValue_bytes != null) {
                        byte[] bytes = propertyNode.propValue_bytes;
                        bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    } else {
                        bitmap = BitmapFactory.decodeResource(context.getResources(),
                                R.drawable.ic_attach_vcard);
                    }
                }
                break;
            }
            case RcsUtils.RCS_MSG_TYPE_AUDIO: {
                bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.rcs_voice);
                break;
            }
            case RcsUtils.RCS_MSG_TYPE_MAP: {
                bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.rcs_map);
                break;
            }
        }
        if (bitmap != null) {
            Matrix matrix = new Matrix();
            matrix.postScale(1.5f, 1.5f);
            Bitmap resizeBmp = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                    bitmap.getHeight(), matrix, true);
        }
        imageView.setImageBitmap(bitmap);
    }

    public static String getContentTypeForMessageItem(MessageItem messageItem) {
        String contentType = "";
        switch (messageItem.mRcsType) {
            case RcsUtils.RCS_MSG_TYPE_IMAGE: {
                contentType = messageItem.mRcsMimeType;
                if (contentType == null) {
                    contentType = "image/*";
                }
                break;
            }
            case RcsUtils.RCS_MSG_TYPE_VIDEO: {
                contentType = "video/*";
                break;
            }
            case RcsUtils.RCS_MSG_TYPE_VCARD: {
                contentType = "text/x-vCard";
                break;
            }
            case RcsUtils.RCS_MSG_TYPE_AUDIO: {
                contentType = "audio/*";
                break;
            }
            case RcsUtils.RCS_MSG_TYPE_MAP: {
                contentType = "map/*";
                break;
            }
        }

        return contentType;
    }

    public static String getRcsMessageStatusText(Context context, MessageItem mMessageItem) {
        String text = "";
        switch (mMessageItem.mRcsMsgState) {
            case RcsUtils.MESSAGE_SENDING:
                if ((mMessageItem.mRcsType == RcsUtils.RCS_MSG_TYPE_IMAGE ||
                        mMessageItem.mRcsType == RcsUtils.RCS_MSG_TYPE_VIDEO)) {
                    if (MessageListItem.sFileTrasnfer != null) {
                        Long percent = MessageListItem.sFileTrasnfer
                                .get(mMessageItem.mRcsMessageId);
                        if (percent != null) {
                            text = context.getString(R.string.uploading_percent,
                                    percent.intValue());
                        }
                    }
                } else {
                    text = context.getString(R.string.message_adapte_sening);
                }
                break;
            case RcsUtils.MESSAGE_HAS_SENDED:
                text = context.getString(R.string.message_adapter_has_send)
                        + "  " + mMessageItem.getTimestamp();
                break;
            case RcsUtils.MESSAGE_SENDED:
                text = context.getString(R.string.message_received)
                        + "  " + mMessageItem.getTimestamp();
                break;
            case RcsUtils.MESSAGE_FAIL:
                if (mMessageItem.mRcsType == RcsUtils.RCS_MSG_TYPE_TEXT) {
                    text = context.getString(R.string.message_send_fail);
                } else {
                    text = context.getString(R.string.message_send_fail_resend);
                }
                break;
            case RcsUtils.MESSAGE_SEND_RECEIVE:
                text = context.getString(R.string.message_received) + "  "
                        + mMessageItem.getTimestamp();
                break;
            case RcsUtils.MESSAGE_HAS_BURNED:
                text = context.getString(R.string.message_has_been_burned);

                if (mMessageItem.mRcsIsBurn != 1)
                    RcsUtils.burnMessageAtLocal(context, mMessageItem.getMessageId());
                break;
            default:
                text = context.getString(R.string.message_adapte_sening);
                break;
        }

        return text;
    }

    public static void startEmojiStore(Context context) {
        if (RcsUtils.isPackageInstalled(context, "com.temobi.dm.emoji.store")) {
            Intent mIntent = new Intent();
            ComponentName comp = new ComponentName("com.temobi.dm.emoji.store",
                    "com.temobi.dm.emoji.store.activity.EmojiActivity");
            mIntent.setComponent(comp);
            context.startActivity(mIntent);
        } else {
            Toast.makeText(context, R.string.install_emoj_store, Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean isDeletePrefixSpecailNumberAvailable(Context context){
        boolean isDeleSpecailNumber =context.getResources()
            .getBoolean(R.bool.config_mms_delete_prefix_special_number);
            SpecialServiceNumApi specailNumApi = RcsApiManager
                 .getSpecialServiceNumApi();
        try{
            if(!isDeleSpecailNumber){
                specailNumApi.closeFunction();
            } else {
                specailNumApi.openFunction();
                List<String> specailNum = new ArrayList<String>();
                specailNum = specailNumApi.getList();
                Log.i(LOG_TAG, "specailNum:" + specailNum.toString());
                if(0 == specailNum.size()) {
                    String[] specialNumberItems = context.getResources()
                        .getStringArray(R.array.special_prefix_number);
                    for (int i = 0; i < specialNumberItems.length; i++)
                        specailNumApi.add(specialNumberItems[i]);
                }
            }
        } catch (ServiceDisconnectedException e){
            Log.i(LOG_TAG,"delete Special Number funtion error");
        }
        return isDeleSpecailNumber;
    }

    public static String getAudioBodyText(MessageItem messageItem) {
        String body = messageItem.mBody;
        String fileName = body.substring(7, body.length());
        String bodyText = fileName + " / " + messageItem.mRcsPlayTime + "''";
        return bodyText;
    }

   public static boolean saveRcsMassage(Context context, long msgId) {
       InputStream input = null;
       FileOutputStream fout = null;
       try {
           ChatMessage chatMessage = RcsApiManager.getMessageApi().getMessageById(msgId + "");
           if ( chatMessage == null) {
                return false;
            }
           int msgType = chatMessage.getMsgType();
            if (msgType != SuntekMessageData.MSG_TYPE_AUDIO
                    && msgType != SuntekMessageData.MSG_TYPE_VIDEO
                    && msgType != SuntekMessageData.MSG_TYPE_IMAGE) {
                return true;    // we only save pictures, videos, and sounds.
            }
            String filePath = getFilePath(chatMessage);
            if(isLoading(filePath,chatMessage.getFilesize())){
                return false;
            }
            String fileName = chatMessage.getFilename();
            input = new FileInputStream(filePath);

            String dir = Environment.getExternalStorageDirectory() + "/"
                                + Environment.DIRECTORY_DOWNLOADS  + "/";
            String extension;
            int index;
            index = fileName.lastIndexOf('.');
            extension = fileName.substring(index + 1, fileName.length());
            fileName = fileName.substring(0, index);
            // Remove leading periods. The gallery ignores files starting with a period.
            fileName = fileName.replaceAll("^.", "");

            File file = getUniqueDestination(dir + fileName, extension);

            fout = new FileOutputStream(file);

            byte[] buffer = new byte[8000];
            int size = 0;
            while ((size=input.read(buffer)) != -1) {
                fout.write(buffer, 0, size);
            }
            // Notify other applications listening to scanner events
            // that a media file has been added to the sd card
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                    Uri.fromFile(file)));
        } catch (IOException e) {
            // Ignore
            Log.e(LOG_TAG, "IOException caught while opening or reading stream", e);
            return false;
        } catch (ServiceDisconnectedException e) {
            Log.e(LOG_TAG, "ServiceDisconnectedException" +
                    " caught while opening or reading stream", e);
            return false;
        } finally {
            if (null != input) {
                try {
                    input.close();
                } catch (IOException e) {
                    // Ignore
                    Log.e(LOG_TAG, "IOException caught while closing stream", e);
                    return false;
                }
            }
            if (null != fout) {
                try {
                    fout.close();
                } catch (IOException e) {
                    // Ignore
                    Log.e(LOG_TAG, "IOException caught while closing stream", e);
                    return false;
                }
            }
        }
        return true;
    }

    private static File getUniqueDestination(String base, String extension) {
        File file = new File(base + "." + extension);

        for (int i = 2; file.exists(); i++) {
            file = new File(base + "_" + i + "." + extension);
        }
        return file;
    }

    @SuppressWarnings("static-access")
    public static void closeKB(Activity activity) {
        if (activity.getCurrentFocus() != null) {
            ((InputMethodManager)activity.getSystemService(activity.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(),
                            InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    public static void openKB(Context context) {
        InputMethodManager inputMethodManager = (InputMethodManager)context
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS);
    }

    public static int dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int)(dipValue * scale + 0.5f);
    }

    public static void openPopupWindow(Context context, View view, byte[] data) {
        LinearLayout.LayoutParams mGifParam = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        int windowWidth = bitmap.getWidth() + RcsUtils.dip2px(context, 40);
        int windowHeight = bitmap.getHeight() + RcsUtils.dip2px(context, 40);
        ColorDrawable transparent = new ColorDrawable(Color.TRANSPARENT);
        RelativeLayout relativeLayout = new RelativeLayout(context);
        relativeLayout
                .setLayoutParams(new LinearLayout.LayoutParams(windowWidth, windowHeight));
        relativeLayout.setBackgroundResource(R.drawable.rcs_emoji_popup_bg);
        relativeLayout.setGravity(Gravity.CENTER);
        RcsEmojiGifView emojiGifView = new RcsEmojiGifView(context);
        emojiGifView.setLayoutParams(mGifParam);
        emojiGifView.setBackground(transparent);
        emojiGifView.setMonieByteData(data);
        relativeLayout.addView(emojiGifView);
        PopupWindow popupWindow = new PopupWindow(view, windowWidth, windowHeight);
        popupWindow.setBackgroundDrawable(transparent);
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setContentView(relativeLayout);
        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);
        popupWindow.update();
    }

}
