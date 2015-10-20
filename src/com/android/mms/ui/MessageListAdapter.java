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

import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.HashMap;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Looper;
import android.os.Handler;
import android.os.Message;
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
import android.view.ViewStub;
import android.widget.AbsListView;
import android.widget.CursorAdapter;
import android.widget.ListView;

import android.widget.TextView;
import com.android.mms.LogTag;
import com.android.mms.MmsApp;
import com.android.mms.R;
import com.android.mms.data.MessageInfoCache;
import com.android.mms.ui.zoom.ZoomMessageListItem;
import com.android.mms.ui.zoom.ZoomMessageListView;
import com.android.mms.util.CursorUtils;
import com.android.mms.util.PrettyTime;
import com.google.android.mms.MmsException;

/**
 * The back-end data adapter of a message list.
 */
public class MessageListAdapter extends CursorAdapter implements Handler.Callback,
        MmsApp.PhoneNumberLookupListener {
    private static final String TAG = LogTag.TAG;
    private static final boolean LOCAL_LOGV = false;

    private static final int PRIME_CACHE_WINDOW = 4;

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
        Sms.SUBSCRIPTION_ID,
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
        Mms.SUBSCRIPTION_ID
    };

    static final String[] MAILBOX_PROJECTION = new String[] {
        // TODO: should move this symbol into android.provider.Telephony.
        MmsSms.TYPE_DISCRIMINATOR_COLUMN,
        BaseColumns._ID,
        Conversations.THREAD_ID,
        // For SMS
        Sms.ADDRESS,
        Sms.BODY,
        Sms.SUBSCRIPTION_ID,
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
        Mms.SUBSCRIPTION_ID,   // add for DSDS
        Threads.RECIPIENT_IDS  // add for obtaining address of MMS
    };

    static final String[] FORWARD_PROJECTION = new String[] {
        "'sms' AS " + MmsSms.TYPE_DISCRIMINATOR_COLUMN,
        BaseColumns._ID,
        Conversations.THREAD_ID,
        Sms.ADDRESS,
        Sms.BODY,
        Sms.SUBSCRIPTION_ID,
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
    static final int COLUMN_SMS_SUB_ID          = 5;
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

    public static final int MSG_TYPE_INCOMING_SMS = 0;
    public static final int MSG_TYPE_OUTGOING_SMS = 1;
    public static final int MSG_TYPE_INCOMING_IMAGE = 2;
    public static final int MSG_TYPE_OUTGOING_IMAGE = 3;
    public static final int MSG_TYPE_INCOMING_VIDEO = 4;
    public static final int MSG_TYPE_OUTGOING_VIDEO = 5;
    public static final int MSG_TYPE_INCOMING_SIMPLE = 6;
    public static final int MSG_TYPE_OUTGOING_SIMPLE = 7;
    public static final int MSG_TYPE_INCOMING_SLIDESHOW = 8;
    public static final int MSG_TYPE_OUTGOING_SLIDESHOW = 9;

    private static int TOTAL_ITEM_TYPE = 10;
    private final MessageInfoCache mMessageCache;

    private static final int TIMESTAMP_UPDATE_FREQUENCY = 1 * 60 * 1000;  // 1 minute
    private static final int TIMESTAMP_GROUPING_INTERVAL = 5 * 60 * 1000; // 5 minutes
    private static final int HANDLER_MSG_REDRAW = 0;
    private static final int HANDLER_MSG_UPDATE_TIME = 1;

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
    private int mSpaceAboveGroupedMessages;
    private int mSpaceAboveNonGroupedMessages;
    public PrettyTime mPrettyTime;
    public Calendar mCalendar = Calendar.getInstance();
    private int mCachedUnreadCount = -1;
    private long mNewMsgsHeaderMsgId = -1;
    private boolean mSeenRecentOutgoingMsg = false;
    private HashSet<TextView> mTimestampViews = new HashSet<>();
    private HashMap<Integer, String> mBodyCache;
    private Handler mHandler;
    private Handler mTimestampUpdater;
    private MetadataPool mMetadataPool;

    public MessageListAdapter(
            Context context, Cursor c, ListView listView, boolean useDefaultColumnsMap,
            Pattern highlight, MessageInfoCache messageInfoCache, Looper looper) {
        super(context, c, FLAG_REGISTER_CONTENT_OBSERVER);
        mContext = context;
        mHighlight = highlight;
        mMessageCache = messageInfoCache;
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
                    mTimestampViews.remove(mli.getTimestampView());
                    // also remove the timestamp view in New Messages header, if applicable
                    if (mli.isNewMessagesHeaderVisibile()) {
                        mTimestampViews.remove(mli.getNewMessagessHeaderTimestamp());
                    }

                    // recycle metadata
                    mMetadataPool.store(mli.recycleMeatada());

                    // Clear references to resources
                    mli.unbind();
                }
            }
        });
        mBodyCache = new HashMap<Integer, String>();
        mSpaceAboveGroupedMessages = context.getResources()
                .getDimensionPixelSize(R.dimen.message_item_space_above_grouped);
        mSpaceAboveNonGroupedMessages = context.getResources()
                .getDimensionPixelSize(R.dimen.message_item_space_above_non_grouped);

        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case HANDLER_MSG_REDRAW:
                        notifyDataSetChanged();
                        break;
                }
            }
        };

        mPrettyTime = new PrettyTime(mContext);
        mTimestampUpdater = new Handler(looper, this);
        mTimestampUpdater.sendEmptyMessageDelayed(HANDLER_MSG_UPDATE_TIME,
                TIMESTAMP_UPDATE_FREQUENCY);
        mMetadataPool = new MetadataPool();
    }

    private int getBoxId(int position) {
        return getBoxId((Cursor) getItem(position));
    }

    private int getBoxId(Cursor cursor) {
        int boxId;
        String msgType = cursor.getString(mColumnsMap.mColumnMsgType);
        if (isMmsMsgType(msgType)) {
            boxId = cursor.getInt(mColumnsMap.mColumnMmsMessageBox);
        } else {
            boxId = cursor.getInt(mColumnsMap.mColumnSmsType);
        }
        return boxId;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        if (view instanceof MessageListItem) {
            String msgType = cursor.getString(mColumnsMap.mColumnMsgType);
            boolean isMms = isMmsMsgType(msgType);
            int boxId = getBoxId(cursor);
            long msgId = cursor.getLong(mColumnsMap.mColumnMsgId);
            MessageItem msgItem = getCachedMessageItem(msgType, msgId, cursor);
            int position = cursor.getPosition();

            Metadata metadata = getMessageMetadata(position);

            if (msgItem != null) {
                MessageListItem mli = (MessageListItem) view;

                if (mMultiManageMode != MessageUtils.INVALID_MODE) {
                    mli.setManageSelectMode(mMultiManageMode);
                }

                final Resources res = context.getResources();
                final int accentColor;
                if (!metadata.isIncoming) {
                    accentColor = res.getColor(R.color.outgoing_message_bg);
                } else if (mAccentColor != 0) {
                    accentColor = mAccentColor;
                } else {
                    accentColor = res.getColor(R.color.incoming_message_bg_default);
                }

                adjustViewForMessageType(mli, msgItem, position, metadata);

                mli.bind(msgItem, accentColor, mIsGroupConversation, position,
                        mListView, metadata, mPrettyTime);

                mli.setMsgListItemHandler(mMsgListItemHandler);
                mBodyCache.put(position, msgItem.mBody);
                mTimestampViews.add(mli.getTimestampView());
            }

            handleZoomForItem(view);
        }
    }

    // traverse the neighboring elements in the cursor to extract display metadata for
    // the current element
    private Metadata getMessageMetadata(int currentPosition) {
        Metadata metadata = mMetadataPool.acquire();
        Cursor currentItem = (Cursor) getItem(currentPosition);
        String msgType = currentItem.getString(mColumnsMap.mColumnMsgType);
        int currentItemBoxId = getBoxId(currentItem);

        metadata.isMms = isMmsMsgType(msgType);
        metadata.isIncoming = isIncomingMessage(currentItemBoxId, metadata.isMms);
        metadata.isUnread =
                currentItem.getInt(metadata.isMms ? COLUMN_MMS_READ : COLUMN_SMS_READ) == 0;

        long currentItemTimestamp = getTimeStamp(currentItem);

        // move cursor to next position
        Cursor nextItem = null;
        if (currentPosition < getCount() - 1) {
            nextItem = (Cursor) getItem(currentPosition + 1);

            int nextItemBoxId = getBoxId(nextItem);
            boolean isNextItemMms = isMmsMsgType(nextItem);
            long nextItemTimeStamp = getTimeStamp(nextItem);

            metadata.shouldShowTimestamp = determineTimestampVisibility(currentItemBoxId,
                    metadata.isMms, currentItemTimestamp, nextItemBoxId, isNextItemMms,
                    nextItemTimeStamp);
        }

        // move cursor to previous position
        Cursor previousItem = null;
        if (currentPosition > 0) {
            previousItem = (Cursor) getItem(currentPosition - 1);

            int previousItemBoxId = getBoxId(previousItem);
            boolean isPreviousItemMms = isMmsMsgType(previousItem);
            boolean isPreviousUnread =
                    currentItem.getInt(isPreviousItemMms ? COLUMN_MMS_READ : COLUMN_SMS_READ) == 0;

            metadata.shouldShowNewMessagesHeader = metadata.isUnread && !isPreviousUnread &&
                    metadata.isIncoming;
            metadata.isGrouped = isEffectivelySameMsgType(currentItemBoxId, metadata.isMms,
                    previousItemBoxId, isPreviousItemMms);
        }

        // reset cursor position
        CursorUtils.moveToPosition(getCursor(), currentPosition);
        return metadata;
    }

    @Override
    public void onNewInfoAvailable() {
        if (!mHandler.hasMessages(HANDLER_MSG_REDRAW)) {
            mHandler.sendEmptyMessageDelayed(HANDLER_MSG_REDRAW, 500);
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch(msg.what) {
            case HANDLER_MSG_UPDATE_TIME:
                mPrettyTime.updateReferenceTime();
                updateTimestamps();
                mTimestampUpdater.sendEmptyMessageDelayed(HANDLER_MSG_UPDATE_TIME,
                        TIMESTAMP_UPDATE_FREQUENCY);
                return true;
        }
        return false;
    }

    private void updateTimestamps() {
        if (mTimestampViews == null || mTimestampViews.size() <= 0 ) {
            return;
        }

        for (TextView timestampView : mTimestampViews) {
            long timeStamp = timestampView.getTag() != null ? (Long) timestampView.getTag() : 0L;
            if (timeStamp <= 0L ) break;
            timestampView.setText(mPrettyTime.format(timeStamp));
        }
    }

    public void clearCaches() {
        mCachedUnreadCount = -1;
        mNewMsgsHeaderMsgId = -1;
        mSeenRecentOutgoingMsg = false;
    }

    public void notifyOutgoingMessage() {
        // stop keeping new msg count
        mSeenRecentOutgoingMsg = true;
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
        notifyDataSetChanged();
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
        int boxId = getBoxId(cursor);
        boolean isIncoming = isIncomingMessage(boxId, isMmsMsgType(cursor));

        int layoutResourceId = isIncoming
                ? R.layout.message_list_item_recv : R.layout.message_list_item_sent;
        View view = mInflater.inflate(layoutResourceId, parent, false);
        MessageListItem listItem = (MessageListItem) view;
        listItem.setMessageCache(mMessageCache);
        adjustNewViewForMessageType(listItem, cursor);
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
            float zoomScale = ((ZoomMessageListView) mListView).getZoomScale();
            ((ZoomMessageListItem) view).setZoomScale(zoomScale);
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
        return TOTAL_ITEM_TYPE;   // Incoming and outgoing messages, both sms and mms
    }

    private long[] getIdsForCachePrime(Cursor cursor) {
        int startPosition = cursor.getPosition();

        int start = Math.max(0, startPosition - PRIME_CACHE_WINDOW);
        int end = Math.min(cursor.getCount() - 1, startPosition + PRIME_CACHE_WINDOW);
        long[] result = new long[end - start + 1];
        for (int i = start; i <= end; i++) {
            if (!cursor.moveToPosition(i)) {
                break;
            }
            long msgId = cursor.getLong(mColumnsMap.mColumnMsgId);
            result[i - start] = msgId;
        }
        // Restore cursor position
        cursor.moveToPosition(startPosition);
        return result;
    }

    @Override
    public int getItemViewType(int position) {
        Cursor cursor = (Cursor) getItem(position);
        String type = cursor.getString(mColumnsMap.mColumnMsgType);
        long msgId = cursor.getLong(mColumnsMap.mColumnMsgId);
        int boxId = getBoxId(cursor);
        if (isMmsMsgType(type)) {
            boolean incoming = isIncomingMessage(boxId, isMmsMsgType(cursor));
            mMessageCache.primeCache(getIdsForCachePrime(cursor));
            List<String> mimeTypes = mMessageCache.getCachedMimeTypes(msgId);
            if (mimeTypes != null) {
                if (mimeTypes.size() > 1) {
                    return incoming ? MSG_TYPE_INCOMING_SLIDESHOW : MSG_TYPE_OUTGOING_SLIDESHOW;
                } else if (mimeTypes.size() == 1) {
                    String mimeType = mimeTypes.get(0);
                    if (mimeType.startsWith("image")) {
                        return incoming ? MSG_TYPE_INCOMING_IMAGE : MSG_TYPE_OUTGOING_IMAGE;
                    } else if (mimeType.startsWith("video")) {
                        return incoming ? MSG_TYPE_INCOMING_VIDEO : MSG_TYPE_OUTGOING_VIDEO;
                    } else if (mimeType.startsWith("audio") || mimeType.equalsIgnoreCase("text/x-vCard")
                            || mimeType.equalsIgnoreCase("text/x-vCalendar")) {
                        return incoming ? MSG_TYPE_INCOMING_SIMPLE : MSG_TYPE_OUTGOING_SIMPLE;
                    }
                }
            }
        }

        // Note that messages from the SIM card all have a boxId of zero.
        return (boxId == TextBasedSmsColumns.MESSAGE_TYPE_INBOX ||
                boxId == TextBasedSmsColumns.MESSAGE_TYPE_ALL) ?
                MSG_TYPE_INCOMING_SMS : MSG_TYPE_OUTGOING_SMS;
    }

    private boolean isEffectivelySameMsgType(int boxId1, boolean isMms1, int boxId2,
            boolean isMms2) {
        return (isIncomingMessage(boxId1, isMms1) && isIncomingMessage(boxId2, isMms2)) ||
                (isOutgoingMsgType(boxId1, isMms1) && isOutgoingMsgType(boxId2, isMms2));
    }

    private void adjustNewViewForMessageType(MessageListItem mli, Cursor cursor) {
        int boxId = getBoxId(cursor);
        boolean isOutgoing = isOutgoingMsgType(boxId, isMmsMsgType(cursor));
        mli.setAvatarVisbility(isOutgoing ? View.GONE : View.INVISIBLE);
    }

    private void adjustViewForMessageType(MessageListItem mli, MessageItem msgItem, int position,
            Metadata metadata) {
        boolean isGrouped = metadata.isGrouped;
        boolean shouldShowHeader = metadata.shouldShowNewMessagesHeader;

        if (metadata.isIncoming) {
            mli.setAvatarVisbility(isGrouped ? View.INVISIBLE : View.VISIBLE);
        }

        mli.setBubbleArrowheadVisibility(!isGrouped ? View.VISIBLE : View.INVISIBLE);

        if (isGrouped) {
            mli.adjustMessageBlockSpacing(mSpaceAboveGroupedMessages);
        } else {
            mli.adjustMessageBlockSpacing(mSpaceAboveNonGroupedMessages);
        }

        View headerRoot = mli.findViewById(R.id.new_msgs_header_root);

        if (shouldShowHeader && (mNewMsgsHeaderMsgId == -1 || (mNewMsgsHeaderMsgId == msgItem.mMsgId))) {
            mli.setNewMessagesHeaderVisiblity(shouldShowHeader);

            if (mNewMsgsHeaderMsgId == -1) {
                mNewMsgsHeaderMsgId = msgItem.mMsgId;
            }
            if (headerRoot == null) {
                // inflate the view stub
                ViewStub stub = (ViewStub) mli.findViewById(R.id.new_msgs_header_stub);
                if (stub != null) {
                    stub.inflate();
                }

                headerRoot = mli.findViewById(R.id.new_msgs_header_root);
            } else {
                headerRoot.setVisibility(View.VISIBLE);
            }

            TextView msgCount = (TextView) headerRoot.findViewById(R.id.new_msgs_count);
            if (!mSeenRecentOutgoingMsg) {
                mCachedUnreadCount = getCount() - position;
            }
            msgCount.setText(mContext.getResources().getQuantityString(R.plurals.new_messages,
                    mCachedUnreadCount, mCachedUnreadCount));

            // punt off setting the time till after the pdu is loaded
            TextView newMsgDateTime = (TextView) headerRoot.findViewById(R.id.new_msgs_date_time);
            if (!metadata.isMms) {
                newMsgDateTime.setVisibility(View.VISIBLE);
                newMsgDateTime.setText(mPrettyTime.format(msgItem.mDate));
            } else {
                newMsgDateTime.setTag((Long) 0L);
                newMsgDateTime.setVisibility(View.GONE);
            }
            // manage timestamp via the TimestampUpdater
            mTimestampViews.add(newMsgDateTime);

        } else {
            if (headerRoot != null) {
                headerRoot.setVisibility(View.GONE);
            }
        }
    }

    private boolean determineTimestampVisibility(int currentItemBoxId, boolean isCurrentItemMms,
            long currentItemTimeStamp, int nextItemBoxId, boolean isNextItemMms,
            long nextItemTimeStamp) {

        boolean showTimestamp = true;
        if (isCurrentItemMms) {
            showTimestamp = true;
        } else {
            if (isEffectivelySameMsgType(currentItemBoxId, isCurrentItemMms, nextItemBoxId,
                    isNextItemMms)) {

                if(PrettyTime.isWithinSameDay(mCalendar, currentItemTimeStamp, nextItemTimeStamp)) {
                    if (nextItemTimeStamp - currentItemTimeStamp < TIMESTAMP_GROUPING_INTERVAL) {
                        showTimestamp = false;
                    }
                } else {
                    // at a day boundary, therefore, show the timestamp
                    showTimestamp = true;
                }
            }
        }

        return showTimestamp;
    }

    private String getRecipients(int position) {
        return getRecipients((Cursor) getItem(position));
    }

    private String getRecipients(Cursor cursor) {
        return cursor.getString(mColumnsMap.mColumnSmsAddress);
    }

    private long getTimeStamp(Cursor cursor) {
        return cursor.getLong(mColumnsMap.mColumnSmsDate);
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


    public static boolean isIncomingMessage(int boxId, boolean isMms) {
        if (isMms) {
            return boxId == Mms.MESSAGE_BOX_INBOX || boxId == Mms.MESSAGE_BOX_ALL;
        } else {
            return boxId == TextBasedSmsColumns.MESSAGE_TYPE_INBOX ||
                    boxId == TextBasedSmsColumns.MESSAGE_TYPE_ALL;
        }
    }

    public static boolean isOutgoingMsgType(int boxId, boolean isMms) {
        return !isIncomingMessage(boxId, isMms);
    }

    public static boolean isMmsMsgType(String msgType) {
        return msgType.equals(MMS_TYPE);
    }

    public boolean isMmsMsgType(int position) {
        return isMmsMsgType((Cursor) getItem(position));
    }
    public boolean isMmsMsgType(Cursor cursor) {
        String msgType = cursor.getString(mColumnsMap.mColumnMsgType);
        return isMmsMsgType(msgType);
    }

    public static class ColumnsMap {
        public int mColumnMsgType;
        public int mColumnMsgId;
        public int mColumnSmsAddress;
        public int mColumnSmsBody;
        public int mColumnSmsSubId;
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
            mColumnSmsSubId           = COLUMN_SMS_SUB_ID;
            mColumnSmsDate            = COLUMN_SMS_DATE;
            mColumnSmsDateSent        = COLUMN_SMS_DATE_SENT;
            mColumnSmsRead            = COLUMN_SMS_READ;
            mColumnSmsType            = COLUMN_SMS_TYPE;
            mColumnSmsStatus          = COLUMN_SMS_STATUS;
            mColumnSmsLocked          = COLUMN_SMS_LOCKED;
            mColumnSmsErrorCode       = COLUMN_SMS_ERROR_CODE;
            mColumnMmsSubject         = COLUMN_MMS_SUBJECT;
            mColumnMmsSubjectCharset  = COLUMN_MMS_SUBJECT_CHARSET;
            mColumnMmsRead            = COLUMN_MMS_READ;
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
                mColumnSmsSubId = cursor.getColumnIndexOrThrow(Sms.SUBSCRIPTION_ID);
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
                mColumnMmsSubId = cursor.getColumnIndexOrThrow(Mms.SUBSCRIPTION_ID);
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

    public static class Metadata {
        public boolean isIncoming;
        public boolean isGrouped;
        public boolean shouldShowNewMessagesHeader;
        public boolean isMms;
        public boolean boxId;
        public boolean isUnread;
        public boolean shouldShowTimestamp = true;

        public Metadata scrub() {
            isIncoming = false;
            isGrouped = false;
            shouldShowNewMessagesHeader = false;
            isMms = false;
            boxId = false;
            isUnread = false;
            shouldShowTimestamp = true;

            return this;
        }
    }

    private static class MetadataPool {
        private final ArrayList<Metadata> mPool = new ArrayList<>();

        public Metadata acquire() {
            if (Log.isLoggable(LogTag.TAG, Log.VERBOSE)) {
                LogTag.debug("pool size : " + mPool.size());
            }
            if (mPool.size() == 0) {
                return new Metadata();
            } else {
                return mPool.remove(mPool.size() - 1).scrub();
            }
        }

        public void store(Metadata metadata) {
            if (metadata != null) {
                mPool.add(metadata);
            }
            if (Log.isLoggable(LogTag.TAG, Log.VERBOSE)) {
                LogTag.debug("pool size : " + mPool.size());
            }
        }
    }
}
