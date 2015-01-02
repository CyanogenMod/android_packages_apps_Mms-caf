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
import com.android.mms.ui.MessageItem;
import com.android.mms.ui.MessageListItem;
import com.suntek.mway.rcs.client.api.im.impl.MessageApi;
import com.suntek.mway.rcs.client.api.provider.model.ChatMessage;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import java.io.File;

public class RcsMessageOpenUtils {
    public static void openRcsSlideShowMessage(MessageListItem messageListItem) {
        MessageItem messageItem = messageListItem.getMessageItem();

        String filepath = RcsUtils.getFilePath(messageItem.mRcsId, messageItem.mRcsPath);
        File File = new File(filepath);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(File), messageListItem.getRcsContentType().toLowerCase());
        if (!messageItem.isMe() && MessageItem.mRcsIsDownload == 0) {
            try {
                messageListItem.setDateViewText(R.string.rcs_downloading);
                MessageApi messageApi = RcsApiManager.getMessageApi();
                ChatMessage message = messageApi.getMessageById(String.valueOf(messageItem.mRcsId));
                if (messageListItem.isDownloading() && !MessageListItem.rcsIsStopDown()) {
                    MessageListItem.setRcsIsStopDown(true);
                    messageApi.interruptFile(message);
                    Log.i("RCS_UI", "STOP LOAD");
                } else {
                    MessageListItem.setRcsIsStopDown(false);
                    messageApi.acceptFile(message);
                }
            } catch (Exception e) {
                Log.w("RCS_UI", e);
            }
        } else {
            messageListItem.getContext().startActivity(intent);
        }
    }

    public static void retransmisMessage(MessageItem messageItem) {
        try {
            RcsApiManager.getMessageApi().retransmitMessageById(String.valueOf(messageItem.mRcsId));
        } catch (ServiceDisconnectedException e) {
            Log.w("RCS_UI", e);
        }
    }

    public static void resendOrOpenRcsMessage(MessageListItem messageListItem) {
        MessageItem messageItem = messageListItem.getMessageItem();
        if (messageItem.mRcsMsgState == RcsUtils.MESSAGE_FAIL
                && messageItem.mRcsType != RcsUtils.RCS_MSG_TYPE_TEXT) {
            retransmisMessage(messageItem);
        } else {
            openRcsMessage(messageListItem);
        }
    }

    private static void openRcsMessage(MessageListItem messageListItem) {
        MessageItem messageItem = messageListItem.getMessageItem();
        switch (messageItem.mRcsType) {
            case RcsUtils.RCS_MSG_TYPE_AUDIO:
                openRcsAudioMessage(messageListItem);
                break;
            case RcsUtils.RCS_MSG_TYPE_VIDEO:
                openRcsVideoMessage(messageListItem);
            case RcsUtils.RCS_MSG_TYPE_IMAGE:
                openRcsImageMessage(messageListItem);
                break;
            case RcsUtils.RCS_MSG_TYPE_VCARD:
                openRcsVCardMessage(messageListItem);
                break;
            case RcsUtils.RCS_MSG_TYPE_MAP:
                openRcsLocationMessage(messageListItem);
                break;
            default:
                break;
        }
    }

    private static void openRcsAudioMessage(MessageListItem messageListItem) {
        try {
            MessageItem messageItem = messageListItem.getMessageItem();
            String rcsContentType = messageListItem.getRcsContentType();
            String filePath = RcsUtils.getFilePath(messageItem.mRcsId, messageItem.mRcsPath);
            File file = new File(filePath);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(file), rcsContentType.toLowerCase());
            intent.setDataAndType(Uri.parse("file://" + messageItem.mRcsPath), "audio/*");
            messageListItem.getContext().startActivity(intent);
        } catch (Exception e) {
            Log.w("RCS_UI", e);
        }
    }

    private static void openRcsVideoMessage(MessageListItem messageListItem) {
        MessageItem messageItem = messageListItem.getMessageItem();
        String rcsContentType = messageListItem.getRcsContentType();
        String filePath = RcsUtils.getFilePath(messageItem.mRcsId, messageItem.mRcsPath);
        File file = new File(filePath);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(file), rcsContentType.toLowerCase());
        if (!messageItem.isMe() && MessageItem.mRcsIsDownload == 0) {
            try {
                messageListItem.setDateViewText(R.string.rcs_downloading);
                MessageApi messageApi = RcsApiManager.getMessageApi();
                ChatMessage message = messageApi.getMessageById(String.valueOf(messageItem.mRcsId));
                if (messageListItem.isDownloading() && !MessageListItem.rcsIsStopDown()) {
                    MessageListItem.setRcsIsStopDown(true);
                    messageApi.interruptFile(message);
                    Log.i("RCS_UI", "STOP LOAD");
                } else {
                    MessageListItem.setRcsIsStopDown(false);
                    messageApi.acceptFile(message);
                }
            } catch (Exception e) {
                Log.w("RCS_UI", e);
            }
        } else {
            messageListItem.getContext().startActivity(intent);
        }
    }

    private static void openRcsImageMessage(MessageListItem messageListItem) {
        MessageItem messageItem = messageListItem.getMessageItem();

        String filePath = RcsUtils.getFilePath(messageItem.mRcsId, messageItem.mRcsPath);
        File file = new File(filePath);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(file), "image/*");
        if (messageItem.mRcsMimeType != null && messageItem.mRcsMimeType.endsWith("image/gif")) {
            intent.setAction("com.android.gallery3d.VIEW_GIF");
        }
        ChatMessage msg = null;
        boolean isFileDownload = false;
        try {
            msg = RcsApiManager.getMessageApi().getMessageById(String.valueOf(messageItem.mRcsId));
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (msg != null)
            isFileDownload = RcsChatMessageUtils.isFileDownload(filePath, msg.getFilesize());
        if (!messageItem.isMe() && !isFileDownload) {
            try {
                messageListItem.setDateViewText(R.string.rcs_downloading);
                MessageApi messageApi = RcsApiManager.getMessageApi();
                ChatMessage message = messageApi.getMessageById(String.valueOf(messageItem.mRcsId));
                if (messageListItem.isDownloading() && !MessageListItem.rcsIsStopDown()) {
                    MessageListItem.setRcsIsStopDown(true);
                    messageApi.interruptFile(message);
                    messageListItem.setDateViewText(R.string.stop_down_load);
                    Log.i("RCS_UI", "STOP LOAD");
                } else {
                    MessageListItem.setRcsIsStopDown(false);
                    messageApi.acceptFile(message);
                }
            } catch (Exception e) {
                Log.w("RCS_UI", e);
            }
            return;
        }
        if (messageItem.isMe() || isFileDownload) {
            messageListItem.getContext().startActivity(intent);
        }
    }

    private static void openRcsVCardMessage(MessageListItem messageListItem) {
        MessageItem messageItem = messageListItem.getMessageItem();
        try {
            String filePath = RcsUtils.getFilePath(messageItem.mRcsId, messageItem.mRcsPath);
            File file = new File(filePath);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(file), messageListItem.getRcsContentType()
                    .toLowerCase());
            intent.putExtra("VIEW_VCARD_FROM_MMS", true);
            messageListItem.getContext().startActivity(intent);
        } catch (Exception e) {
            Log.w("RCS_UI", e);
        }
    }

    private static void openRcsLocationMessage(MessageListItem messageListItem) {
        MessageItem messageItem = messageListItem.getMessageItem();
        String filePath = RcsUtils.getFilePath(messageItem.mRcsId, messageItem.mRcsPath);
        try {
            GeoLocation geo = RcsUtils.readMapXml(filePath);
            String geourl = "geo:" + geo.getLat() + "," + geo.getLng();
            Uri uri = Uri.parse(geourl);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(geourl));
            messageListItem.getContext().startActivity(intent);
        } catch (NullPointerException e) {
            Log.w("RCS_UI", e);
        } catch (Exception e) {
            Log.w("RCS_UI", e);
        }
    }
}
