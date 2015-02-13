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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;
import android.util.Base64;

import com.android.mms.data.Conversation;
import com.suntek.mway.rcs.client.aidl.contacts.RCSContact;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.QRCardImg;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.QRCardInfo;
import com.suntek.mway.rcs.client.api.profile.callback.QRImgListener;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.Avatar;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.Avatar.IMAGE_TYPE;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.TelephoneModel;
import com.suntek.mway.rcs.client.api.profile.callback.ProfileListener;
import com.suntek.mway.rcs.client.api.profile.impl.ProfileApi;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.Profile;
import com.suntek.mway.rcs.client.aidl.plugin.entity.profile.TelephoneModel;
import com.suntek.mway.rcs.client.aidl.provider.SuntekMessageData;
import com.suntek.mway.rcs.client.aidl.provider.model.GroupChatModel;
import com.suntek.mway.rcs.client.aidl.provider.model.GroupChatUser;
import com.suntek.mway.rcs.client.api.util.ServiceDisconnectedException;
import com.suntek.mway.rcs.client.aidl.contacts.RCSContact;

public class RcsContactsUtils {
    public static final String NOTIFY_CONTACT_PHOTO_CHANGE = "com.suntek.mway.rcs.NOTIFY_CONTACT_PHOTO_CHANGE";
    public static final String LOCAL_PHOTO_SETTED = "local_photo_setted";
    public static final String MIMETYPE_RCS = "vnd.android.cursor.item/rcs";
    public static final String PHONE_PRE_CODE = "+86";

    public static String getMyRcsRawContactId(Context context){
        String rawContactId = null;
        Uri uri = Uri.parse("content://com.android.contacts/profile/data/");
        Cursor cursor = context.getContentResolver().query(
                uri,
                new String[] {"raw_contact_id"}, null,null, null);
        if(cursor != null){
            if(cursor.moveToNext()){
                rawContactId = cursor.getString(0);
                cursor.close();
                cursor = null;
            }
        }
       return rawContactId;
    }

    public static String getRawContactId(Context context,String contactId) {
        String rawContactId = null;
        Cursor cursor = context.getContentResolver()
                .query(RawContacts.CONTENT_URI,
                        new String[] { RawContacts._ID },
                        RawContacts.CONTACT_ID + "=?",
                        new String[] { contactId }, null);
        if (null != cursor) {
            if (cursor.moveToNext())
                rawContactId = cursor.getString(0);
            cursor.close();
            cursor = null;
        }
        return rawContactId;
    }

    public static RCSContact getMyRcsContact(Context context, String rawContactId){
        if(TextUtils.isEmpty(rawContactId))
            return null;
        RCSContact rcsContact = null;
        ArrayList<TelephoneModel> teleList = null;
        Uri uri = Uri.parse("content://com.android.contacts/profile/data/");
        Cursor cursor = context.getContentResolver().query(
                uri,
                new String[] {
                        "_id", "mimetype", "data1", "data2", "data3",
                        "data4", "data15"
                }, " raw_contact_id = ?  ",
                new String[] {
                    rawContactId
                }, null);

        try {
            if (cursor != null && cursor.moveToFirst()) {
                rcsContact = new RCSContact();
                teleList = new ArrayList<TelephoneModel>();
                while (!cursor.isAfterLast()) {
                    String mimetype = cursor.getString(cursor
                            .getColumnIndexOrThrow("mimetype"));
                    String data1 = cursor.getString(cursor
                            .getColumnIndexOrThrow("data1"));
                    if ("vnd.android.cursor.item/phone_v2".equals(mimetype)) {
                        String numberType = cursor.getString(cursor
                                .getColumnIndexOrThrow("data2"));
                        if ("4".equals(numberType)) {
                            rcsContact.setCompanyFax(data1);
                        } else if ("17".equals(numberType)) {
                            rcsContact.setCompanyTel(data1);
                        } else if ("2".equals(numberType)) {
                            rcsContact.setAccount(data1);
                        } else {
                            TelephoneModel model = new TelephoneModel();
                            model.setTelephone(data1);
                            model.setType(Integer.parseInt(numberType));
                            teleList.add(model);
                        }
                    } else if ("vnd.android.cursor.item/postal-address_v2"
                            .equals(mimetype)) {
                        String data2 = cursor.getString(cursor
                                .getColumnIndexOrThrow("data2"));

                        if ("1".equals(data2)) {
                            rcsContact.setHomeAddress(data1);
                        } else if ("2".equals(data2)) {
                            rcsContact.setCompanyAddress(data1);
                        }
                    } else if ("vnd.android.cursor.item/name".equals(mimetype)) {
                        String fristName = cursor.getString(cursor
                                .getColumnIndexOrThrow("data2"));
                        String lastName = cursor.getString(cursor
                                .getColumnIndexOrThrow("data3"));
                        rcsContact.setFirstName(fristName);
                        rcsContact.setLastName(lastName);
                    } else if ("vnd.android.cursor.item/email_v2"
                            .equals(mimetype)) {
                        rcsContact.setEmail(data1);
                    } else if ("vnd.android.cursor.item/organization"
                            .equals(mimetype)) {
                        String data4 = cursor.getString(cursor
                                .getColumnIndexOrThrow("data4"));
                        rcsContact.setCompanyName(data1);
                        rcsContact.setCompanyDuty(data4);
                    } else if (MIMETYPE_RCS.equals(mimetype)) {
                        String data2 = cursor.getString(cursor
                                .getColumnIndexOrThrow("data2"));
                        rcsContact.setEtag(data1);
                        rcsContact.setBirthday(data2);
                    }
                    cursor.moveToNext();
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (rcsContact != null) {
            rcsContact.setOtherTels(teleList);
        }
        return rcsContact;
    }

    public  static RCSContact getContactProfileOnDbByRawContactId(Context context,
            String rawContactId) {
        if (TextUtils.isEmpty(rawContactId)) {
            return null;
        }
        RCSContact rcsContact = null;
        ArrayList<TelephoneModel> teleList = null;
        Uri uri = Uri.parse("content://com.android.contacts/data/");
        Cursor cursor = context.getContentResolver().query(
                uri,
                new String[] {
                        "_id", "mimetype", "data1", "data2", "data3",
                        "data4", "data15"
                }, " raw_contact_id = ?  ",
                new String[] {
                    rawContactId
                }, null);

        try {
            if (cursor != null && cursor.moveToFirst()) {
                rcsContact = new RCSContact();
                teleList = new ArrayList<TelephoneModel>();
                while (!cursor.isAfterLast()) {
                    String mimetype = cursor.getString(cursor
                            .getColumnIndexOrThrow("mimetype"));
                    String data1 = cursor.getString(cursor
                            .getColumnIndexOrThrow("data1"));
                    if ("vnd.android.cursor.item/phone_v2".equals(mimetype)) {
                        String numberType = cursor.getString(cursor
                                .getColumnIndexOrThrow("data2"));
                        if ("4".equals(numberType)) {
                            rcsContact.setCompanyFax(data1);
                        } else if ("17".equals(numberType)) {
                            rcsContact.setCompanyTel(data1);
                        } else if ("2".equals(numberType)) {
                            rcsContact.setAccount(data1);
                        } else {
                            TelephoneModel model = new TelephoneModel();
                            model.setTelephone(data1);
                            model.setType(Integer.parseInt(numberType));
                            teleList.add(model);
                        }
                    } else if ("vnd.android.cursor.item/postal-address_v2"
                            .equals(mimetype)) {
                        String data2 = cursor.getString(cursor
                                .getColumnIndexOrThrow("data2"));

                        if ("1".equals(data2)) {
                            rcsContact.setHomeAddress(data1);
                        } else if ("2".equals(data2)) {
                            rcsContact.setCompanyAddress(data1);
                        }
                    } else if ("vnd.android.cursor.item/name".equals(mimetype)) {
                        String fristName = cursor.getString(cursor
                                .getColumnIndexOrThrow("data2"));
                        String lastName = cursor.getString(cursor
                                .getColumnIndexOrThrow("data3"));
                        rcsContact.setFirstName(fristName);
                        rcsContact.setLastName(lastName);
                    } else if ("vnd.android.cursor.item/email_v2"
                            .equals(mimetype)) {
                        rcsContact.setEmail(data1);
                    } else if ("vnd.android.cursor.item/organization"
                            .equals(mimetype)) {
                        String data4 = cursor.getString(cursor
                                .getColumnIndexOrThrow("data4"));
                        rcsContact.setCompanyName(data1);
                        rcsContact.setCompanyDuty(data4);
                    } else if (MIMETYPE_RCS.equals(mimetype)) {
                        String data2 = cursor.getString(cursor
                                .getColumnIndexOrThrow("data2"));
                        rcsContact.setEtag(data1);
                        rcsContact.setBirthday(data2);
                    }
                    cursor.moveToNext();
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (rcsContact != null) {
            rcsContact.setOtherTels(teleList);
        }
        return rcsContact;
    }

    public static String getGroupChatMemberDisplayName(Context context, int groupId,
            String number) {
        GroupChatModel model = null;
        try {
            model = RcsApiManager.getMessageApi().getGroupChatById(String.valueOf(groupId));
        } catch (ServiceDisconnectedException e) {
            e.printStackTrace();
        }
        if (model == null)
            return number;
        List<GroupChatUser> list = model.getUserList();
        if (list == null || list.size() == 0)
            return number;
        for (GroupChatUser groupChatUser : list) {
            if (groupChatUser.getNumber().equals(number)) {
                if (!TextUtils.isEmpty(groupChatUser.getAlias())) {
                    return groupChatUser.getAlias();
                } else {
                    return getContactNameFromPhoneBook(context, number);
                }
            }
        }
        return number;
    }

    public static String getContactNameFromPhoneBook(Context context, String phoneNum) {
        String contactName = phoneNum;
        String numberW86;
        if (!phoneNum.startsWith(PHONE_PRE_CODE)) {
            numberW86 = PHONE_PRE_CODE + phoneNum;
        } else {
            numberW86 = phoneNum;
            phoneNum = phoneNum.substring(3);
        }
        String formatNumber = getAndroidFormatNumber(phoneNum);

        ContentResolver cr = context.getContentResolver();
        Cursor pCur = cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, new String[] {
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                },
                ContactsContract.CommonDataKinds.Phone.NUMBER + " = ? OR "
                        + ContactsContract.CommonDataKinds.Phone.NUMBER + " = ? OR "
                        + ContactsContract.CommonDataKinds.Phone.NUMBER + " = ? ",
                new String[] {
                        phoneNum, numberW86, formatNumber
                }, null);
        try {
            if (pCur != null && pCur.moveToFirst()) {
                contactName = pCur.getString(pCur
                        .getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            }
        } finally {
            if (pCur != null) {
                pCur.close();
            }
        }
        return contactName;
    }

    public static String getAndroidFormatNumber(String number) {
        if (TextUtils.isEmpty(number)) {
            return number;
        }

        number = number.replaceAll(" ", "");

        if (number.startsWith(PHONE_PRE_CODE)) {
            number = number.substring(3);
        }

        if (number.length() != 11) {
            return number;
        }

        StringBuilder builder = new StringBuilder();
        // builder.append("+86 ");
        builder.append(number.substring(0, 3));
        builder.append(" ");
        builder.append(number.substring(3, 7));
        builder.append(" ");
        builder.append(number.substring(7));
        return builder.toString();
    }

    private static class UpdatePhotosTask extends AsyncTask<Void, Void, Void> {

        private Context mContext;
        private String mNumber;

        private Handler mHandler = new Handler();

        UpdatePhotosTask(Context context, String number) {
            mContext = context;
            mNumber = number;
        }

        @Override
        protected Void doInBackground(Void... params) {
            long aContactId =getContactIdByNumber(mContext, mNumber);
            ContentResolver resolver= mContext.getContentResolver();
            Cursor c = resolver.query(RawContacts.CONTENT_URI, new String[] {
                    RawContacts._ID
            }, RawContacts.CONTACT_ID + "=" + String.valueOf(aContactId), null, null);
            final ArrayList<Long> rawContactIdList = new ArrayList<Long>();
            if(c != null){
                try {
                    if (c.moveToFirst()) {
                        // boolean hasTryToGet = false;
                        do {
                            long rawContactId = c.getLong(0);
                            if (!hasLocalSetted(resolver, rawContactId)) {
                                rawContactIdList.add(rawContactId);
                            }
                        } while (c.moveToNext());
                    }
                } finally {
                    if(c != null)
                        c.close();
                }
            }
            if (rawContactIdList.size() > 0) {
                try {
                    RcsApiManager.getProfileApi().getHeadPicByContact(aContactId,
                            new ProfileListener() {

                                @Override
                                public void onAvatarGet(final Avatar photo,
                                        final int resultCode, final String resultDesc)
                                        throws RemoteException {
                                    mHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (resultCode == 0) {
                                                if (photo != null) {
                                                    byte[] contactPhoto = Base64.decode(
                                                            photo.getImgBase64Str(),
                                                            android.util.Base64.DEFAULT);
                                                    for (long rawContactId : rawContactIdList) {
                                                        final Uri outputUri = Uri.withAppendedPath(
                                                                ContentUris
                                                                        .withAppendedId(
                                                                                RawContacts.CONTENT_URI,
                                                                                rawContactId),
                                                                RawContacts.DisplayPhoto.CONTENT_DIRECTORY);
                                                        setContactPhoto(mContext,
                                                                contactPhoto, outputUri);
                                                    }
                                                    //notify mms list
                                                    mContext.sendBroadcast(new Intent(NOTIFY_CONTACT_PHOTO_CHANGE));
                                                }
                                            } else {
                                            }
                                        }
                                    });
                                }

                                @Override
                                public void onAvatarUpdated(int arg0, String arg1)
                                        throws RemoteException {
                                }

                                @Override
                                public void onProfileGet(Profile arg0, int arg1, String arg2)
                                        throws RemoteException {
                                }

                                @Override
                                public void onProfileUpdated(int arg0, String arg1)
                                        throws RemoteException {
                                }

                                @Override
                                public void onQRImgDecode(QRCardInfo imgObj, int resultCode,
                                        String arg2) throws RemoteException {
                                }
                            });
                } catch (ServiceDisconnectedException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

    }

    public static void updateContactPhotosByNumber(Context context,String number) {
        new UpdatePhotosTask(context,number).execute();
    }

    public static void setContactPhoto(Context context, byte[] input,
            Uri outputUri) {
        FileOutputStream outputStream = null;

        try {
            outputStream = context.getContentResolver().openAssetFileDescriptor(outputUri, "rw")
                    .createOutputStream();
            outputStream.write(input);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try{
                outputStream.close();
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean hasLocalSetted(ContentResolver resolver, long rawContactId) {
        Cursor c = resolver.query(ContactsContract.RawContacts.CONTENT_URI, new String[] {
                LOCAL_PHOTO_SETTED
        }, RawContacts._ID + " = ? ", new String[] {
                String.valueOf(rawContactId)
        }, null);
        long localSetted = 0;
        try {
            if (c != null && c.moveToFirst()) {
                localSetted = c.getLong(0);
            }
        } finally {
            c.close();
        }
        return (localSetted == 1) ? true : false;
    }

    public static long getContactIdByNumber(Context context, String number) {
        if (TextUtils.isEmpty(number)) {
            return -1;
        }
        String numberW86 = number;
        if (!number.startsWith("+86")) {
            numberW86 = "+86" + number;
        } else {
            numberW86 = number.substring(3);
        }
        Cursor cursor = context.getContentResolver().query(Phone.CONTENT_URI, new String[] {
                Phone.CONTACT_ID
        }, Phone.NUMBER + "=? OR " + Phone.NUMBER + "=?", new String[] {
                number, numberW86
        }, null);
        if (cursor != null) {
            try{
                if (cursor.moveToFirst()) {
                    return cursor.getInt(0);
                }
            } finally {
                cursor.close();
            }
        }
        return -1;
    }
}
