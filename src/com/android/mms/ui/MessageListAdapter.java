/*
 * Copyright (C) 2010-2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
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

import java.util.regex.Pattern;
import java.util.HashMap;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Handler;
import android.provider.BaseColumns;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.MmsSms.PendingMessages;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Conversations;
import android.provider.Telephony.Threads;
import android.provider.Telephony.TextBasedSmsColumns;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.CursorAdapter;
import android.widget.ListView;

import com.android.mms.LogTag;
import com.android.mms.R;
import com.android.mms.ui.zoom.ZoomMessageListItem;
import com.android.mms.ui.zoom.ZoomMessageListView;
import com.google.android.mms.MmsException;

/**
 * The back-end data adapter of a message list.
 */
public class MessageListAdapter extends CursorAdapter {
    private static final String TAG = LogTag.TAG;
    private static final boolean LOCAL_LOGV = false;

    public static final String MMS_TYPE = "mms";
    public static final String SMS_TYPE = "sms";

    static final String[] PROJECTION = new String[] {
        // TODO: should move this symbol into com.android.mms.telephony.Telephony.
        MmsSms.TYPE_DISCRIMINATOR_COLUMN,
        BaseColumns._ID,
        Conversations.THREAD_ID,
        // For SMS
        Sms.ADDRESS,
        Sms.BODY,
        Sms.PHONE_ID,
        Sms.DATE,
        Sms.DATE_SENT,
        Sms.READ,
        Sms.TYPE,
        Sms.STATUS,
        Sms.LOCKED,
        Sms.ERROR_CODE,
        // For MMS
        Mms.SUBJECT,
        Mms.SUBJECT_CHARSET,
        Mms.DATE,
        Mms.DATE_SENT,
        Mms.READ,
        Mms.MESSAGE_TYPE,
        Mms.MESSAGE_BOX,
        Mms.DELIVERY_REPORT,
        Mms.READ_REPORT,
        PendingMessages.ERROR_TYPE,
        Mms.LOCKED,
        Mms.STATUS,
        Mms.TEXT_ONLY
    };

    static final String[] MAILBOX_PROJECTION = new String[] {
        // TODO: should move this symbol into android.provider.Telephony.
        MmsSms.TYPE_DISCRIMINATOR_COLUMN,
        BaseColumns._ID,
        Conversations.THREAD_ID,
        // For SMS
        Sms.ADDRESS,
        Sms.BODY,
        Sms.PHONE_ID,
        Sms.DATE,
        Sms.DATE_SENT,
        Sms.READ,
        Sms.TYPE,
        Sms.STATUS,
        Sms.LOCKED,
        Sms.ERROR_CODE,
        // For MMS
        Mms.SUBJECT,
        Mms.SUBJECT_CHARSET,
        Mms.DATE,
        Mms.DATE_SENT,
        Mms.READ,
        Mms.MESSAGE_TYPE,
        Mms.MESSAGE_BOX,
        Mms.DELIVERY_REPORT,
        Mms.READ_REPORT,
        PendingMessages.ERROR_TYPE,
        Mms.LOCKED,
        Mms.STATUS,
        Mms.TEXT_ONLY,
        Mms.PHONE_ID,   // add for DSDS
        Threads.RECIPIENT_IDS  // add for obtaining address of MMS
    };

    static final String[] FORWARD_PROJECTION = new String[] {
        "'sms' AS " + MmsSms.TYPE_DISCRIMINATOR_COLUMN,
        BaseColumns._ID,
        Conversations.THREAD_ID,
        Sms.ADDRESS,
        Sms.BODY,
        Sms.PHONE_ID,
        Sms.DATE,
        Sms.DATE_SENT,
        Sms.READ,
        Sms.TYPE,
        Sms.STATUS,
        Sms.LOCKED,
        Sms.ERROR_CODE
    };

    // The indexes of the default columns which must be consistent
    // with above PROJECTION.
    static final int COLUMN_MSG_TYPE            = 0;
    static final int COLUMN_ID                  = 1;
    static final int COLUMN_THREAD_ID           = 2;
    static final int COLUMN_SMS_ADDRESS         = 3;
    static final int COLUMN_SMS_BODY            = 4;
    static final int COLUMN_PHONE_ID            = 5;
    static final int COLUMN_SMS_DATE            = 6;
    static final int COLUMN_SMS_DATE_SENT       = 7;
    static final int COLUMN_SMS_READ            = 8;
    static final int COLUMN_SMS_TYPE            = 9;
    static final int COLUMN_SMS_STATUS          = 10;
    static final int COLUMN_SMS_LOCKED          = 11;
    static final int COLUMN_SMS_ERROR_CODE      = 12;
    static final int COLUMN_MMS_SUBJECT         = 13;
    static final int COLUMN_MMS_SUBJECT_CHARSET = 14;
    static final int COLUMN_MMS_DATE            = 15;
    static final int COLUMN_MMS_DATE_SENT       = 16;
    static final int COLUMN_MMS_READ            = 17;
    static final int COLUMN_MMS_MESSAGE_TYPE    = 18;
    static final int COLUMN_MMS_MESSAGE_BOX     = 19;
    static final int COLUMN_MMS_DELIVERY_REPORT = 20;
    static final int COLUMN_MMS_READ_REPORT     = 21;
    static final int COLUMN_MMS_ERROR_TYPE      = 22;
    static final int COLUMN_MMS_LOCKED          = 23;
    static final int COLUMN_MMS_STATUS          = 24;
    static final int COLUMN_MMS_TEXT_ONLY       = 25;
    static final int COLUMN_MMS_SUB_ID          = 26;
    static final int COLUMN_RECIPIENT_IDS       = 27;

    private static final int CACHE_SIZE         = 50;

    public static final int INCOMING_ITEM_TYPE_SMS = 0;
    public static final int OUTGOING_ITEM_TYPE_SMS = 1;
    public static final int INCOMING_ITEM_TYPE_MMS = 2;
    public static final int OUTGOING_ITEM_TYPE_MMS = 3;

    protected LayoutInflater mInflater;
    private final ListView mListView;
    private final MessageItemCache mMessageItemCache;
    private final ColumnsMap mColumnsMap;
    private OnDataSetChangedListener mOnDataSetChangedListener;
    private Handler mMsgListItemHandler;
    private Pattern mHighlight;
    private Context mContext;
    private boolean mIsGroupConversation;
    // for multi delete sim messages or forward merged message
    private int mMultiManageMode = MessageUtils.INVALID_MODE;
    private int mAccentColor = 0;

    private HashMap<Integer, String> mBodyCache;

    public MessageListAdapter(
            Context context, Cursor c, ListView listView,
            boolean useDefaultColumnsMap, Pattern highlight) {
        super(context, c, FLAG_REGISTER_CONTENT_OBSERVER);
        mContext = context;
        mHighlight = highlight;

        mInflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        mMessageItemCache = new MessageItemCache(CACHE_SIZE);
        mListView = listView;

        if (useDefaultColumnsMap) {
            mColumnsMap = new ColumnsMap();
        } else {
            mColumnsMap = new ColumnsMap(c);
        }

        listView.setRecyclerListener(new AbsListView.RecyclerListener() {
            @Override
            public void onMovedToScrapHeap(View view) {
                if (view instanceof MessageListItem) {
                    MessageListItem mli = (MessageListItem) view;
                    // Clear references to resources
                    mli.unbind();
                }
            }
        });
        mBodyCache = new HashMap<Integer, String>();
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof MessageListItem) {
            String type = cursor.getString(mColumnsMap.mColumnMsgType);
            long msgId = cursor.getLong(mColumnsMap.mColumnMsgId);

            MessageItem msgItem = getCachedMessageItem(type, msgId, cursor);
            if (msgItem != null) {
                MessageListItem mli = (MessageListItem) view;
                int position = cursor.getPosition();
                if (mMultiManageMode != MessageUtils.INVALID_MODE) {
                    mli.setManageSelectMode(mMultiManageMode);
                }

                int boxType = getItemViewType(cursor);
                final Resources res = context.getResources();
                final int accentColor;

                if (boxType == OUTGOING_ITEM_TYPE_SMS || boxType == OUTGOING_ITEM_TYPE_MMS) {
                    accentColor = res.getColor(R.color.outgoing_message_bg);
                } else if (mAccentColor != 0) {
                    accentColor = mAccentColor;
                } else {
                    accentColor = res.getColor(R.color.incoming_message_bg_default);
                }

                mli.bind(msgItem, accentColor, mIsGroupConversation, position,
                        mListView.isItemChecked(position));
                mli.setMsgListItemHandler(mMsgListItemHandler);

                mBodyCache.put(position, msgItem.mBody);
            }

            handleZoomForItem(view);
        }
    }

    public interface OnDataSetChangedListener {
        void onDataSetChanged(MessageListAdapter adapter);
        void onContentChanged(MessageListAdapter adapter);
    }

    public void setOnDataSetChangedListener(OnDataSetChangedListener l) {
        mOnDataSetChangedListener = l;
    }

    public void setMsgListItemHandler(Handler handler) {
        mMsgListItemHandler = handler;
    }

    public void setIsGroupConversation(boolean isGroup) {
        mIsGroupConversation = isGroup;
    }

    public void setAccentColor(int accentColor) {
        float[] hsv = new float[3];
        Color.colorToHSV(accentColor, hsv);
        hsv[2] = Math.min(1F, hsv[2] + 0.1F);
        mAccentColor = Color.HSVToColor(Color.alpha(accentColor), hsv);
        notifyDataSetChanged();
    }

    public void cancelBackgroundLoading() {
        mMessageItemCache.evictAll();   // causes entryRemoved to be called for each MessageItem
                                        // in the cache which causes us to cancel loading of
                                        // background pdu's and images.
    }

    public void setMultiManageMode(int manageMode) {
        mMultiManageMode = manageMode;
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
        if (LOCAL_LOGV) {
            Log.v(TAG, "MessageListAdapter.notifyDataSetChanged().");
        }

        mMessageItemCache.evictAll();

        if (mOnDataSetChangedListener != null) {
            mOnDataSetChangedListener.onDataSetChanged(this);
        }
    }

    @Override
    protected void onContentChanged() {
        if (getCursor() != null && !getCursor().isClosed()) {
            if (mOnDataSetChangedListener != null) {
                mOnDataSetChangedListener.onContentChanged(this);
            }
        }
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        int boxType = getItemViewType(cursor);
        boolean isIncoming =
                boxType == INCOMING_ITEM_TYPE_SMS || boxType == INCOMING_ITEM_TYPE_MMS;
        int layoutResourceId = isIncoming
                ? R.layout.message_list_item_recv : R.layout.message_list_item_send;
        View view = mInflater.inflate(layoutResourceId, parent, false);

        if (boxType == INCOMING_ITEM_TYPE_MMS || boxType == OUTGOING_ITEM_TYPE_MMS) {
            // We've got an mms item, pre-inflate the mms portion of the view
            view.findViewById(R.id.mms_layout_view_stub).setVisibility(View.VISIBLE);
        }
        handleZoomForItem(view);
        return view;
    }

    public MessageItem getCachedMessageItem(String type, long msgId, Cursor c) {
        MessageItem item = mMessageItemCache.get(getKey(type, msgId));
        if (item == null && c != null && isCursorValid(c)) {
            try {
                item = new MessageItem(mContext, type, c, mColumnsMap, mHighlight);
                mMessageItemCache.put(getKey(item.mType, item.mMsgId), item);
            } catch (MmsException e) {
                Log.e(TAG, "getCachedMessageItem: ", e);
            }
        }
        return item;
    }

    /**
     * Handle zoom for zoomable list items.
     * @param view A view that should be zoomed, if it is a ZoomMessageListItem
     */
    private void handleZoomForItem(View view) {
        if (mListView != null
                && mListView instanceof ZoomMessageListView
                && view instanceof ZoomMessageListItem) {
            int zoomFontSize = ((ZoomMessageListView) mListView).getZoomFontSize();
            ((ZoomMessageListItem) view).setZoomFontSize(zoomFontSize);
        }
    }

    private boolean isCursorValid(Cursor cursor) {
        // Check whether the cursor is valid or not.
        if (cursor == null || cursor.isClosed() || cursor.isBeforeFirst() || cursor.isAfterLast()) {
            return false;
        }
        return true;
    }

    private static long getKey(String type, long id) {
        if (type.equals("mms")) {
            return -id;
        } else {
            return id;
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    /* MessageListAdapter says that it contains four types of views. Really, it just contains
     * a single type, a MessageListItem. Depending upon whether the message is an incoming or
     * outgoing message, the avatar and text and other items are laid out either left or right
     * justified. That works fine for everything but the message text. When views are recycled,
     * there's a greater than zero chance that the right-justified text on outgoing messages
     * will remain left-justified. The best solution at this point is to tell the adapter we've
     * got two different types of views. That way we won't recycle views between the two types.
     * @see android.widget.BaseAdapter#getViewTypeCount()
     */
    @Override
    public int getViewTypeCount() {
        return 4;   // Incoming and outgoing messages, both sms and mms
    }

    @Override
    public int getItemViewType(int position) {
        Cursor cursor = (Cursor)getItem(position);
        return getItemViewType(cursor);
    }

    private int getItemViewType(Cursor cursor) {
        String type = cursor.getString(mColumnsMap.mColumnMsgType);
        int boxId;
        if ("sms".equals(type)) {
            boxId = cursor.getInt(mColumnsMap.mColumnSmsType);
            // Note that messages from the SIM card all have a boxId of zero.
            return (boxId == TextBasedSmsColumns.MESSAGE_TYPE_INBOX ||
                    boxId == TextBasedSmsColumns.MESSAGE_TYPE_ALL) ?
                    INCOMING_ITEM_TYPE_SMS : OUTGOING_ITEM_TYPE_SMS;
        } else {
            boxId = cursor.getInt(mColumnsMap.mColumnMmsMessageBox);
            // Note that messages from the SIM card all have a boxId of zero: Mms.MESSAGE_BOX_ALL
            return (boxId == Mms.MESSAGE_BOX_INBOX || boxId == Mms.MESSAGE_BOX_ALL) ?
                    INCOMING_ITEM_TYPE_MMS : OUTGOING_ITEM_TYPE_MMS;
        }
    }

    public boolean hasSmsInConversation(Cursor cursor) {
        boolean hasSms = false;
        if (isCursorValid(cursor)) {
            if (cursor.moveToFirst()) {
                do {
                    String type = cursor.getString(mColumnsMap.mColumnMsgType);
                    if ("sms".equals(type)) {
                        hasSms = true;
                        break;
                    }
                } while (cursor.moveToNext());
                // Reset the position to 0
                cursor.moveToFirst();
            }
        }
        return hasSms;
    }

    public Cursor getCursorForItem(MessageItem item) {
        Cursor cursor = getCursor();
        if (isCursorValid(cursor)) {
            if (cursor.moveToFirst()) {
                do {
                    long id = cursor.getLong(mRowIDColumn);
                    String type = cursor.getString(mColumnsMap.mColumnMsgType);
                    if (id == item.mMsgId && (type != null && type.equals(item.mType))) {
                        return cursor;
                    }
                } while (cursor.moveToNext());
            }
        }
        return null;
    }

    public String getCachedBodyForPosition(int position) {
        return mBodyCache.get(position);
    }

    public static class ColumnsMap {
        public int mColumnMsgType;
        public int mColumnMsgId;
        public int mColumnSmsAddress;
        public int mColumnSmsBody;
        public int mColumnPhoneId;
        public int mColumnSmsDate;
        public int mColumnSmsDateSent;
        public int mColumnSmsRead;
        public int mColumnSmsType;
        public int mColumnSmsStatus;
        public int mColumnSmsLocked;
        public int mColumnSmsErrorCode;
        public int mColumnMmsSubject;
        public int mColumnMmsSubjectCharset;
        public int mColumnMmsDate;
        public int mColumnMmsDateSent;
        public int mColumnMmsRead;
        public int mColumnMmsMessageType;
        public int mColumnMmsMessageBox;
        public int mColumnMmsDeliveryReport;
        public int mColumnMmsReadReport;
        public int mColumnMmsErrorType;
        public int mColumnMmsLocked;
        public int mColumnMmsStatus;
        public int mColumnMmsTextOnly;
        public int mColumnMmsSubId;
        public int mColumnRecipientIds;

        public ColumnsMap() {
            mColumnMsgType            = COLUMN_MSG_TYPE;
            mColumnMsgId              = COLUMN_ID;
            mColumnSmsAddress         = COLUMN_SMS_ADDRESS;
            mColumnSmsBody            = COLUMN_SMS_BODY;
            mColumnPhoneId            = COLUMN_PHONE_ID;
            mColumnSmsDate            = COLUMN_SMS_DATE;
            mColumnSmsDateSent        = COLUMN_SMS_DATE_SENT;
            mColumnSmsType            = COLUMN_SMS_TYPE;
            mColumnSmsStatus          = COLUMN_SMS_STATUS;
            mColumnSmsLocked          = COLUMN_SMS_LOCKED;
            mColumnSmsErrorCode       = COLUMN_SMS_ERROR_CODE;
            mColumnMmsSubject         = COLUMN_MMS_SUBJECT;
            mColumnMmsSubjectCharset  = COLUMN_MMS_SUBJECT_CHARSET;
            mColumnMmsMessageType     = COLUMN_MMS_MESSAGE_TYPE;
            mColumnMmsMessageBox      = COLUMN_MMS_MESSAGE_BOX;
            mColumnMmsDeliveryReport  = COLUMN_MMS_DELIVERY_REPORT;
            mColumnMmsReadReport      = COLUMN_MMS_READ_REPORT;
            mColumnMmsErrorType       = COLUMN_MMS_ERROR_TYPE;
            mColumnMmsLocked          = COLUMN_MMS_LOCKED;
            mColumnMmsStatus          = COLUMN_MMS_STATUS;
            mColumnMmsTextOnly        = COLUMN_MMS_TEXT_ONLY;
            mColumnMmsSubId           = COLUMN_MMS_SUB_ID;
            mColumnRecipientIds       = COLUMN_RECIPIENT_IDS;
        }

        public ColumnsMap(Cursor cursor) {
            // Ignore all 'not found' exceptions since the custom columns
            // may be just a subset of the default columns.
            try {
                mColumnMsgType = cursor.getColumnIndexOrThrow(
                        MmsSms.TYPE_DISCRIMINATOR_COLUMN);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMsgId = cursor.getColumnIndexOrThrow(BaseColumns._ID);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnSmsAddress = cursor.getColumnIndexOrThrow(Sms.ADDRESS);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnSmsBody = cursor.getColumnIndexOrThrow(Sms.BODY);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnPhoneId = cursor.getColumnIndexOrThrow(Sms.PHONE_ID);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnSmsDate = cursor.getColumnIndexOrThrow(Sms.DATE);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnSmsDateSent = cursor.getColumnIndexOrThrow(Sms.DATE_SENT);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnSmsType = cursor.getColumnIndexOrThrow(Sms.TYPE);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnSmsStatus = cursor.getColumnIndexOrThrow(Sms.STATUS);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnSmsLocked = cursor.getColumnIndexOrThrow(Sms.LOCKED);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnSmsErrorCode = cursor.getColumnIndexOrThrow(Sms.ERROR_CODE);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsSubject = cursor.getColumnIndexOrThrow(Mms.SUBJECT);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsSubjectCharset = cursor.getColumnIndexOrThrow(Mms.SUBJECT_CHARSET);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsMessageType = cursor.getColumnIndexOrThrow(Mms.MESSAGE_TYPE);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsMessageBox = cursor.getColumnIndexOrThrow(Mms.MESSAGE_BOX);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsDeliveryReport = cursor.getColumnIndexOrThrow(Mms.DELIVERY_REPORT);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsReadReport = cursor.getColumnIndexOrThrow(Mms.READ_REPORT);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsErrorType = cursor.getColumnIndexOrThrow(PendingMessages.ERROR_TYPE);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsLocked = cursor.getColumnIndexOrThrow(Mms.LOCKED);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsStatus = cursor.getColumnIndexOrThrow(Mms.STATUS);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsTextOnly = cursor.getColumnIndexOrThrow(Mms.TEXT_ONLY);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnMmsSubId = cursor.getColumnIndexOrThrow(Mms.PHONE_ID);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }

            try {
                mColumnRecipientIds = cursor.getColumnIndexOrThrow(Threads.RECIPIENT_IDS);
            } catch (IllegalArgumentException e) {
                Log.w("colsMap", e.getMessage());
            }
        }
    }

    private static class MessageItemCache extends LruCache<Long, MessageItem> {
        public MessageItemCache(int maxSize) {
            super(maxSize);
        }

        @Override
        protected void entryRemoved(boolean evicted, Long key,
                MessageItem oldValue, MessageItem newValue) {
            oldValue.cancelPduLoading();
        }
    }
}
