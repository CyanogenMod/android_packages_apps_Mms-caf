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

import com.android.mms.MmsApp;
import com.android.mms.R;
import com.android.mms.ui.MessageItem;
import com.android.mms.ui.MessageListItem;
import com.android.vcard.VCardParser;
import com.android.vcard.VCardParser_V21;
import com.android.vcard.VCardParser;
import com.android.vcard.VCardParser_V21;
import com.android.vcard.exception.VCardException;
import com.suntek.mway.rcs.client.aidl.provider.model.ChatMessage;
import com.suntek.mway.rcs.client.aidl.plugin.entity.emoticon.EmoticonConstant;
import com.suntek.mway.rcs.client.api.im.impl.MessageApi;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class RcsMessageOpenUtils {
    private static final String LOG_TAG = "RCS_UI";

    public static void openRcsSlideShowMessage(MessageListItem messageListItem) {
        MessageItem messageItem = messageListItem.getMessageItem();

        String filepath = RcsUtils.getFilePath(messageItem.mRcsId, messageItem.mRcsPath);
        File File = new File(filepath);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(File),
                messageListItem.getRcsContentType().toLowerCase());
        if (!messageItem.isMe() && MessageItem.mRcsIsDownload == 0) {
            try {
                messageListItem.setDateViewText(R.string.rcs_downloading);
                MessageApi messageApi = RcsApiManager.getMessageApi();
                ChatMessage message = messageApi.getMessageById(String.valueOf(messageItem.mRcsId));
                if (messageListItem.isDownloading() && !MessageListItem.rcsIsStopDown()) {
                    MessageListItem.setRcsIsStopDown(true);
                    messageApi.interruptFile(message);
                    Log.i(LOG_TAG, "STOP LOAD");
                } else {
                    MessageListItem.setRcsIsStopDown(false);
                    messageApi.acceptFile(message);
                }
            } catch (Exception e) {
                Log.w(LOG_TAG, e);
            }
        } else {
            messageListItem.getContext().startActivity(intent);
        }
    }

    public static void retransmisMessage(MessageItem messageItem) {
        try {
            RcsApiManager.getMessageApi().retransmitMessageById(String.valueOf(messageItem.mRcsId));
        } catch (ServiceDisconnectedException e) {
            Log.w(LOG_TAG, e);
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
            case RcsUtils.RCS_MSG_TYPE_PAID_EMO:
                openRcsEmojiMessage(messageListItem);
                break;
            default:
                break;
        }
    }

    private static void openRcsEmojiMessage(MessageListItem messageListItem){
        MessageItem messageItem = messageListItem.getMessageItem();
        String[] body = messageItem.mBody.split(",");
        byte[] data = null;
        try {
            data = RcsApiManager
                    .getEmoticonApi()
                    .decrypt2Bytes(body[0],
                    EmoticonConstant.EMO_DYNAMIC_FILE);
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
            return;
        }
        Context context = messageListItem.getContext();
        View view = messageListItem.getImageView();
        RcsUtils.openPopupWindow(context, view, data);
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
            Log.w(LOG_TAG, e);
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
                    Log.i(LOG_TAG, "STOP LOAD");
                } else {
                    MessageListItem.setRcsIsStopDown(false);
                    messageApi.acceptFile(message);
                }
            } catch (Exception e) {
                Log.w(LOG_TAG, e);
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
        if (messageItem.mRcsMimeType != null && messageItem.mRcsMimeType.endsWith("image/bmp")) {
            intent.setDataAndType(Uri.fromFile(file), "image/bmp");
        } else {
            intent.setDataAndType(Uri.fromFile(file), "image/*");
        }
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
                    Log.i(LOG_TAG, "STOP LOAD");
                } else {
                    MessageListItem.setRcsIsStopDown(false);
                    messageApi.acceptFile(message);
                }
            } catch (Exception e) {
                Log.w(LOG_TAG, e);
            }
            return;
        }
        if (messageItem.isMe() || isFileDownload) {
            messageListItem.getContext().startActivity(intent);
        }
    }

    private static void openRcsVCardMessage(MessageListItem messageListItem) {
            Context context = messageListItem.getContext();
            showOpenRcsVcardDialog(context,messageListItem);
    }

    private static void showOpenRcsVcardDialog(final Context context,
            final MessageListItem messageListItem){
        final String[] openVcardItems = new String[] {
                context.getString(R.string.vcard_detail_info),
                context.getString(R.string.vcard_import)
        };
       final MessageItem messageItem = messageListItem.getMessageItem();
        AlertDialog.Builder builder = new AlertDialog.Builder(messageListItem.getContext());
        builder.setItems(openVcardItems, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        String vcardFilePath = RcsUtils.getFilePath(messageItem.mRcsId,
                                messageItem.mRcsPath);
                        ArrayList<PropertyNode> propList = openRcsVcardDetail(context,
                                vcardFilePath);
                        showDetailVcard(context,propList);
                        break;
                    case 1:
                        try {
                          String filePath = RcsUtils.getFilePath(messageItem.mRcsId,
                                  messageItem.mRcsPath);
                          File file = new File(filePath);
                          Intent intent = new Intent(Intent.ACTION_VIEW);
                          intent.setDataAndType(Uri.fromFile(file), messageListItem
                                  .getRcsContentType().toLowerCase());
                          intent.putExtra("VIEW_VCARD_FROM_MMS", true);
                          messageListItem.getContext().startActivity(intent);
                      } catch (Exception e) {
                          Log.w(LOG_TAG, e);
                      }
                        break;
                    default:
                        break;
                }
            }
        });
        builder.create().show();
    }

    public static ArrayList<PropertyNode> openRcsVcardDetail(Context context,String filePath){
        if (TextUtils.isEmpty(filePath)){
            return null;
        }
        try {
            File file = new File(filePath);
            FileInputStream fis = new FileInputStream(file);
            VNodeBuilder builder = new VNodeBuilder();
            VCardParser parser = new VCardParser_V21();
            parser.addInterpreter(builder);
            parser.parse(fis);
            List<VNode> vNodeList = builder.getVNodeList();
            ArrayList<PropertyNode> propList = vNodeList.get(0).propList;
            return propList;
        } catch (Exception e) {
            Log.w(LOG_TAG,e);
            return null;
        }
    }

    private static void showDetailVcard(Context context,ArrayList<PropertyNode> propList){
        AlertDialog.Builder builder = new Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View vcardView = inflater.inflate(R.layout.rcs_vcard_detail, null);

        ImageView photoView = (ImageView)vcardView.findViewById(R.id.vcard_photo);
        TextView nameView, priNumber, firNumber, senNumber,
                thrNumber, addrText,comName, positionText;
        nameView = (TextView)vcardView.findViewById(R.id.vcard_name);
        priNumber = (TextView)vcardView.findViewById(R.id.vcard_number);
       // ArrayList<PropertyNode> propList = vNodeList.get(0).propList;
       firNumber = (TextView)vcardView.findViewById(R.id.vcard_number_1);
       senNumber = (TextView)vcardView.findViewById(R.id.vcard_number_2);
       thrNumber = (TextView)vcardView.findViewById(R.id.vcard_number_3);
       addrText = (TextView) vcardView.findViewById(R.id.vcard_addre);
       positionText = (TextView)vcardView.findViewById(R.id.vcard_position);
       comName = (TextView) vcardView.findViewById(R.id.vcard_com_name);

       ArrayList<String> numberList = new ArrayList<String>();
        for (PropertyNode propertyNode : propList) {
            if ("FN".equals(propertyNode.propName)) {
                if(!TextUtils.isEmpty(propertyNode.propValue)){
                nameView.setText(context.getString(R.string.vcard_name)
                        + propertyNode.propValue);
                }
            } else if ("TEL".equals(propertyNode.propName)) {
                if(!TextUtils.isEmpty(propertyNode.propValue)){
                    numberList.add(context.getString(R.string.vcard_number)
                            + propertyNode.propValue);
                }
            } else if("ADR".equals(propertyNode.propName)){
                if(!TextUtils.isEmpty(propertyNode.propValue)){
                    addrText.setText(context.getString(R.string.vcard_compony_addre)
                            + ":" + propertyNode.propValue);
                }
            } else if("ORG".equals(propertyNode.propName)){
                if(!TextUtils.isEmpty(propertyNode.propValue)){
                    comName.setText(context.getString(R.string.vcard_compony_name)
                            + ":" + propertyNode.propValue);
                }
            } else if("TITLE".equals(propertyNode.propName)){
                if(!TextUtils.isEmpty(propertyNode.propValue)){
                    positionText.setText(context.getString(R.string.vcard_compony_position)
                            + ":" + propertyNode.propValue);
                }
            } else if("PHOTO".equals(propertyNode.propName)){
                if(propertyNode.propValue_bytes != null){
                    byte[] bytes = propertyNode.propValue_bytes;
                    final Bitmap vcardBitmap = BitmapFactory
                            .decodeByteArray(bytes, 0, bytes.length);
                    photoView.setImageBitmap(vcardBitmap) ;
                }
            }
        }
        if ( numberList.size() >= 1 && !TextUtils.isEmpty(numberList.get(0))) {
            priNumber.setText(numberList.get(0));
        }
        if (numberList.size() >= 2 && !TextUtils.isEmpty(numberList.get(1))) {
            firNumber.setText(numberList.get(1));
        }
        if (numberList.size() >= 3 && !TextUtils.isEmpty(numberList.get(2))) {
            senNumber.setText(numberList.get(2));
        }
        if (numberList.size() >= 4 && !TextUtils.isEmpty(numberList.get(3))) {
            thrNumber.setText(numberList.get(3));
        }
        builder.setTitle(R.string.vcard_detail_info);
        builder.setView(vcardView);
        builder.create();
        builder.show();
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
            Log.w(LOG_TAG, e);
        } catch (Exception e) {
            Log.w(LOG_TAG, e);
        }
    }
}
