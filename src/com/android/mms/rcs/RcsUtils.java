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
import com.android.mms.RcsApiManager;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;
import com.android.mms.util.Recycler;
import com.android.mms.widget.MmsWidgetProvider;
import com.suntek.mway.rcs.client.api.constant.BroadcastConstants;
import com.suntek.mway.rcs.client.api.im.impl.MessageApi;
import com.suntek.mway.rcs.client.api.provider.SuntekMessageData;
import com.suntek.mway.rcs.client.api.provider.model.ChatMessage;
import com.suntek.mway.rcs.client.api.provider.model.GroupChatModel;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.api.util.log.LogHelper;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SqliteWrapper;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.BaseColumns;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Telephony;
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
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.io.FileInputStream;

import android.database.sqlite.SqliteWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.NinePatch;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.content.res.AssetFileDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

public class RcsUtils {
    public static final int IS_RCS_TRUE     = 1;
    public static final int IS_RCS_FALSE    = 0;
    public static final int RCS_IS_BURN_TRUE     = 1;
    public static final int RCS_IS_BURN_FALSE    = 0;
    public static final int RCS_IS_DOWNLOAD_FALSE  = 0;
    public static final int RCS_IS_DOWNLOAD_OK     =1;
    public static final int SMS_DEFAULT_RCS_ID    = -1;
    public static final int RCS_MSG_TYPE_TEXT    = SuntekMessageData.MSG_TYPE_TEXT;
    public static final int RCS_MSG_TYPE_IMAGE    = SuntekMessageData.MSG_TYPE_IMAGE;
    public static final int RCS_MSG_TYPE_VIDEO    = SuntekMessageData.MSG_TYPE_VIDEO;
    public static final int RCS_MSG_TYPE_AUDIO    = SuntekMessageData.MSG_TYPE_AUDIO;
    public static final int RCS_MSG_TYPE_MAP    = SuntekMessageData.MSG_TYPE_MAP;
    public static final int RCS_MSG_TYPE_VCARD    = SuntekMessageData.MSG_TYPE_CONTACT;
    public static final int RCS_MSG_TYPE_NOTIFICATION    = SuntekMessageData.MSG_TYPE_NOTIFICATION;

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

    public static final int MSG_RECEIVE =   SuntekMessageData.MSG_RECEIVE;
    public static final String IM_ONLY     = "1";
    public static final String SMS_ONLY     ="2";
    public static final String RCS_MMS_VCARD_PATH="sdcard/rcs/" + "mms.vcf";
    static  boolean mIsSupportRcs=true ;//true for test

    public static boolean isSupportRcs() {
        return mIsSupportRcs;
    }

    public  static  void setIsSupportRcs(boolean mIsSupportRcs) {
        RcsUtils.mIsSupportRcs = mIsSupportRcs;
    }

    public static GeoLocation readMapXml(String filepath) {
        GeoLocation geo = null;
        try {
            GeoLocationParser handler = new GeoLocationParser(
                    new FileInputStream(new File(filepath)));
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
    public static void deleteMessageById(Context context, long id){
        String smsId = String.valueOf(id);
        ContentValues values = new ContentValues();
        context.getContentResolver().delete(Uri.parse("content://sms/"), "_id=?", new String[]{smsId});
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
            //case SuntekMessageData.MSG_STATE_SEND_REC:
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
      int result=  resolver.update(Sms.CONTENT_URI, values, selection, selectionArgs);
        if (result == 0) {
            try {
                Thread.sleep(3000);
                int reresult = resolver.update(Sms.CONTENT_URI, values, selection, selectionArgs);
            } catch (Exception e) {
                // TODO: handle exception
            }
          
        }

    }

    public static void updateManyState(Context context, String rcs_id,
            String number, int rcs_msg_state) {
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
           context.getContentResolver().update(Sms.CONTENT_URI, values,
                "rcs_message_id = ? and ( address = ? OR address = ? OR address = ? )", new String[] {
                        rcs_id, number, numberW86, formatNumber
                });
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
        values.put("top_time",0);
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
            ChatMessage cMsg = RcsApiManager.getMessageApi()
                    .getMessageById(String.valueOf(id));
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
    public static void rcsInsertInbox(Context context,String body,String address ,int is_rcs,
            int rcs_msg_type ,String rcs_mime_type ,int rcs_have_attach,String rcs_path) {
        ContentValues values = new ContentValues();
        values.put(Inbox.BODY,body);
        values.put(Inbox.ADDRESS,address);
        values.put("is_rcs", is_rcs);
        values.put("rcs_msg_type", rcs_msg_type); //text or image  //0 text,1 image ,2 video ,3 audio ,4 map,5 vcard
        values.put("rcs_mime_type",rcs_mime_type); //text or image
        values.put("rcs_have_attach",rcs_have_attach);
        values.put("rcs_path", rcs_path);
        Long threadId = Conversation.getOrCreateThreadId(context,address);
        ContentResolver resolver = context.getContentResolver();
        Uri insertedUri = SqliteWrapper.insert(context, resolver,
                Inbox.CONTENT_URI, values);
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
            if (body.endsWith("gif")) {
                rcs_mime_type = "image/gif";
            } else if (body.endsWith("bmp")) {
                rcs_mime_type = "image/bmp";
            } else if (body.endsWith("jpg")) {
                rcs_mime_type = "image/*";
            } else if (body.endsWith("jpeg")) {
                rcs_mime_type = "image/*";
            } else if (body.endsWith("png")) {
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
            case SuntekMessageData.MSG_TYPE_VIDEO:
            case SuntekMessageData.MSG_TYPE_AUDIO:
                playTime = messageApi.getPlayTime(rcs_msg_type, chatMessage.getData());
            case SuntekMessageData.MSG_TYPE_IMAGE:
            case SuntekMessageData.MSG_TYPE_GIF:
            case SuntekMessageData.MSG_TYPE_CONTACT:
                rcs_path = getFilePath(chatMessage);
                rcs_thumb_path = messageApi.getThumbFilepath(chatMessage);
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

//            ContactList recipients = ContactList.getContactByNumbers(numbers, false);
//            Log.i("RCS_UI","recipients="+recipients.size());
//            Conversation conversation = Conversation.get(context, recipients, true);
//            Log.i("RCS_UI","CONVERSATION="+(conversation == null));
//
//
//            long threadId;
//            if (conversation == null) {
//                HashSet<String> addresses = new HashSet<String>();
//                for (int i = 0; i < addresslist.length; i++) {
//                    Log.i("RCS_UI","ADDRESS="+addresslist[i]);
//                    addresses.add(addresslist[i]);
//                }
//                threadId = getOrCreateThreadId(context, addresses);
//            } else {
//                threadId = conversation.getThreadId();
//            }

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
                    values.put("type",2);
                }
                values.put("is_rcs", 1);
                values.put("rcs_msg_type", rcs_msg_type); // text or image //0 text,1 image ,2 video ,3 audio ,4 map,5 vcard
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
            //values.put("type",2); //send sucsess;
            values.put("is_rcs", 1);
            values.put("rcs_msg_type", rcs_msg_type); //text or image  //0 text,1 image ,2 video ,3 audio ,4 map,5 vcard
            values.put("rcs_mime_type", rcs_mime_type); //text or image
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

            long t0, t1;
            t0 = System.currentTimeMillis();
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
            t1 = System.currentTimeMillis();
            Log.d("Demo", "getOrCreateThreadId, threadId=" + threadId + ", cost: " + (t1 - t0));

            t0 = System.currentTimeMillis();
            ContentResolver resolver = context.getContentResolver();
            Uri insertedUri = SqliteWrapper.insert(context, resolver, uri, values);
            t1 = System.currentTimeMillis();
            Log.d("Demo", "insert cost: " + (t1 - t0));
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

        Log.d("RCS_UI", "getThreadIdByRcsMessageId(): rcs_id=" + rcs_id + ", threadId=" + threadId);

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

        Log.d("RCS_UI", "getRcsThreadIdByThreadId(): threadId=" + threadId + ", recipientId="
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

        Log.d("RCS_UI", "getRcsThreadIdByThreadId(): threadId=" + threadId + ", recipientId="
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
            Log.w("RCS_UI", e);
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

        Log.d("RCS_UI", "getThreadIdByRcsMessageId(): groupId=" + groupId + ", recipientId="
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

        Log.d("RCS_UI", "getThreadIdByRcsMessageId(): groupId=" + groupId + ", recipientId="
                + recipientId + ", threadId=" + threadId);

        return threadId;
    }

    public static int getDuration(final Context context, final Uri uri) {
        MediaPlayer mPlayer = MediaPlayer.create(context, uri);
        if (mPlayer == null) {
            return 0;
        }
        int duration = mPlayer.getDuration() / 1000;
        return duration;
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
     * This method is temporally copied from /framework/opt/telephone for RCS group chat debug purpose.
     * @hide
     */
    public static long getOrCreateThreadId(
            Context context, Set<String> recipients) {
        Uri.Builder uriBuilder = Uri.parse("content://mms-sms/threadID").buildUpon();

        for (String recipient : recipients) {
            if (Mms.isEmailAddress(recipient)) {
                recipient = Mms.extractAddrSpec(recipient);
            }

            uriBuilder.appendQueryParameter("recipient", recipient);
        }
        Log.d("RCS_UI", "uriBuilder.appendQueryParameter(\"isGroupChat\", \"1\");");
        uriBuilder.appendQueryParameter("isGroupChat", "1");

        Uri uri = uriBuilder.build();
        //if (DEBUG) Rlog.v(TAG, "getOrCreateThreadId uri: " + uri);

        Cursor cursor = SqliteWrapper.query(context, context.getContentResolver(),
                uri, new String[] { BaseColumns._ID }, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0);
                } else {
                    Log.e("RCS_UI", "getOrCreateThreadId returned no rows!");
                }
            } finally {
                cursor.close();
            }
        }

        Log.e("RCS_UI", "getOrCreateThreadId failed with uri " + uri.toString());
        throw new IllegalArgumentException("Unable to find or allocate a thread ID.");
    }

    public static boolean isActivityIntentAvailable(Context context, Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
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
        Log.d("RCS_UI", "------ dump cursor row ------");
        for (int i = 0; i < count; i++) {
            Log.d("RCS_UI", cursor.getColumnName(i) + "=" + cursor.getString(i));
        }
    }

    public static void dumpIntent(Intent intent) {
        String action = intent.getAction();
        Bundle extras = intent.getExtras();

        Log.d("RCS_UI", "============ onReceive ============");
        Log.d("RCS_UI", "action=" + action);
        if (extras != null) {
            for (String key : extras.keySet()) {
                Log.d("RCS_UI", key + "=" + extras.get(key));
            }
        }
    }

    /**
     * Get the chat group name for display. Return 'subject' if the 'remark' is empry.
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
     * @param context
     * @param number numbers, split by ";". For example: 13800138000;10086
     * @param message
     */
    public static void startCreateGroupChatActivity(Context context, String number, String message) {
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
            } else if (body.equals(GROUP_CHAT_NOTIFICATION_KEY_WORDS_POLICY)) {
                body = context.getString(R.string.group_chat_policy);
            }
        }

        return body;
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

    public  static boolean setVcard(final Context context,Uri uri) {
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

            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));

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

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
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

    public static long getAudioMaxTime(){
        try {
            return RcsApiManager.getMessageApi().getAudioMaxTime();
        } catch (ServiceDisconnectedException exception) {
            exception.printStackTrace();
            return 0;
        }
    }

    public static long getVideoMaxTime(){
        try {
            return RcsApiManager.getMessageApi().getVideoMaxTime();
        } catch (ServiceDisconnectedException exception) {
            exception.printStackTrace();
            return 0;
        }
    }

    public static long getVideoFtMaxSize(){
        try {
            return RcsApiManager.getMessageApi().getVideoFtMaxSize();
        } catch (ServiceDisconnectedException exception) {
            exception.printStackTrace();
            return 0;
        }
    }

    public static boolean isLoading(String filePath,long fileSize){
        if(TextUtils.isEmpty(filePath)){
            return false;
        }
        File file = new File(filePath);
        if (file.exists() && file.length() < fileSize){
            return true;
        } else {
            return false;
        }
    }
    
}
