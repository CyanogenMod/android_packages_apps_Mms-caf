/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.mms.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.Drawable;
import android.provider.Telephony.Threads;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.android.internal.telephony.util.BlacklistUtils;
import com.android.mms.LogTag;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.Conversation;
import com.android.mms.ui.ConversationList;
import com.android.mms.ui.ConversationListItem;
import com.android.mms.ui.MessageUtils;
import com.android.mms.util.SmileyParser;

public class MmsWidgetService extends RemoteViewsService {
    private static final String TAG = LogTag.TAG;

    /**
     * Lock to avoid race condition between widgets.
     */
    private static final Object sWidgetLock = new Object();

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        if (Log.isLoggable(LogTag.WIDGET, Log.VERBOSE)) {
            Log.v(TAG, "onGetViewFactory intent: " + intent);
        }
        return new MmsFactory(getApplicationContext(), intent);
    }

    /**
     * Remote Views Factory for Mms Widget.
     */
    private static class MmsFactory
            implements RemoteViewsService.RemoteViewsFactory, Contact.UpdateListener {
        private static final int MAX_CONVERSATIONS_COUNT = 25;
        private final Context mContext;
        private final int mAppWidgetId;
        private boolean mShouldShowViewMore;
        private Cursor mConversationCursor;
        private int mUnreadConvCount;
        private final AppWidgetManager mAppWidgetManager;

        // Static colors
        private static int SUBJECT_TEXT_COLOR_READ;
        private static int SUBJECT_TEXT_COLOR_UNREAD;
        private static int SENDERS_TEXT_COLOR_READ;
        private static int SENDERS_TEXT_COLOR_UNREAD;

        public MmsFactory(Context context, Intent intent) {
            mContext = context;
            mAppWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            mAppWidgetManager = AppWidgetManager.getInstance(context);
            if (Log.isLoggable(LogTag.WIDGET, Log.VERBOSE)) {
                Log.v(TAG, "MmsFactory intent: " + intent + "widget id: " + mAppWidgetId);
            }
            // Initialize colors
            Resources res = context.getResources();
            SENDERS_TEXT_COLOR_READ = res.getColor(R.color.widget_sender_text_color_read);
            SENDERS_TEXT_COLOR_UNREAD = res.getColor(R.color.widget_sender_text_color_unread);
            SUBJECT_TEXT_COLOR_READ = res.getColor(R.color.widget_subject_text_color_read);
            SUBJECT_TEXT_COLOR_UNREAD = res.getColor(R.color.widget_subject_text_color_unread);
        }

        @Override
        public void onCreate() {
            if (Log.isLoggable(LogTag.WIDGET, Log.VERBOSE)) {
                Log.v(TAG, "onCreate");
            }
            Contact.addListener(this);
        }

        @Override
        public void onDestroy() {
            if (Log.isLoggable(LogTag.WIDGET, Log.VERBOSE)) {
                Log.v(TAG, "onDestroy");
            }
            synchronized (sWidgetLock) {
                if (mConversationCursor != null && !mConversationCursor.isClosed()) {
                    mConversationCursor.close();
                    mConversationCursor = null;
                }
                Contact.removeListener(this);
            }
        }

        @Override
        public void onDataSetChanged() {
            if (Log.isLoggable(LogTag.WIDGET, Log.VERBOSE)) {
                Log.v(TAG, "onDataSetChanged");
            }
            synchronized (sWidgetLock) {
                if (mConversationCursor != null) {
                    mConversationCursor.close();
                    mConversationCursor = null;
                }
                mConversationCursor = queryAllConversations();
                mUnreadConvCount = queryUnreadCount();
                onLoadComplete();
            }
        }

        private Cursor queryAllConversations() {
            return mContext.getContentResolver().query(
                    Conversation.sAllThreadsUri, Conversation.ALL_THREADS_PROJECTION,
                    null, null, null);
        }

        private int queryUnreadCount() {
            Cursor cursor = null;
            int unreadCount = 0;
            try {
                cursor = mContext.getContentResolver().query(
                    Conversation.sAllThreadsUri, Conversation.ALL_THREADS_PROJECTION,
                    Threads.READ + "=0", null, null);
                if (cursor != null) {
                    unreadCount = cursor.getCount();
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return unreadCount;
        }

        /**
         * Returns the number of items should be shown in the widget list.  This method also updates
         * the boolean that indicates whether the "show more" item should be shown.
         * @return the number of items to be displayed in the list.
         */
        @Override
        public int getCount() {
            if (Log.isLoggable(LogTag.WIDGET, Log.VERBOSE)) {
                Log.v(TAG, "getCount");
            }
            synchronized (sWidgetLock) {
                if (mConversationCursor == null) {
                    return 0;
                }
                final int count = getConversationCount();
                mShouldShowViewMore = count < mConversationCursor.getCount();
                return count + (mShouldShowViewMore ? 1 : 0);
            }
        }

        /**
         * Returns the number of conversations that should be shown in the widget.  This method
         * doesn't update the boolean that indicates that the "show more" item should be included
         * in the list.
         * @return
         */
        private int getConversationCount() {
            if (Log.isLoggable(LogTag.WIDGET, Log.VERBOSE)) {
                Log.v(TAG, "getConversationCount");
            }

            return Math.min(mConversationCursor.getCount(), MAX_CONVERSATIONS_COUNT);
        }

        /*
         * Add color to a given text
         */
        private SpannableStringBuilder addColor(CharSequence text, int color) {
            SpannableStringBuilder builder = new SpannableStringBuilder(text);
            if (color != 0) {
                builder.setSpan(new ForegroundColorSpan(color), 0, text.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return builder;
        }

        /**
         * @return the {@link RemoteViews} for a specific position in the list.
         */
        @Override
        public RemoteViews getViewAt(int position) {
            if (Log.isLoggable(LogTag.WIDGET, Log.VERBOSE)) {
                Log.v(TAG, "getViewAt position: " + position);
            }
            synchronized (sWidgetLock) {
                // "View more conversations" view.
                if (mConversationCursor == null
                        || (mShouldShowViewMore && position >= getConversationCount())) {
                    return getViewMoreConversationsView();
                }

                if (!mConversationCursor.moveToPosition(position)) {
                    // If we ever fail to move to a position, return the "View More conversations"
                    // view.
                    Log.w(TAG, "Failed to move to position: " + position);
                    return getViewMoreConversationsView();
                }

                Conversation conv = Conversation.from(mContext, mConversationCursor);

                // Inflate and fill out the remote view
                RemoteViews remoteViews = new RemoteViews(
                        mContext.getPackageName(), R.layout.widget_conversation);

                boolean hasAttachment = conv.hasAttachment();
                remoteViews.setViewVisibility(R.id.attachment, hasAttachment ? View.VISIBLE :
                    View.GONE);

                // Date
                remoteViews.setTextViewText(R.id.date,
                        addColor(MessageUtils.formatTimeStampString(mContext, conv.getDate()),
                                conv.hasUnreadMessages() ? SUBJECT_TEXT_COLOR_UNREAD :
                                    SUBJECT_TEXT_COLOR_READ));

                // Avatar
                if (conv.getRecipients().size() == 1) {
                    Bitmap avatarBmp = conv.getRecipients().get(0).getAvatar(mContext);
                    if (avatarBmp != null) {
                        avatarBmp = drawIcon(avatarBmp);
                    } else {
                        Contact contact = conv.getRecipients().get(0);
                        int accentColor = contact.getAccentColor(mContext, true);
                        float textSize = mContext.getResources().getDimension(R.dimen
                                .contact_letter_text_size);
                        avatarBmp = getDefaultContactPhoto(mContext, accentColor, contact.getName(),
                                contact.getNumber(), textSize);
                    }
                    remoteViews.setImageViewBitmap(R.id.avatar, avatarBmp);

                } else {
                    Bitmap avatarBmp = BitmapFactory.decodeResource(mContext.getResources(), R
                            .drawable.ic_contact_picture);
                    avatarBmp = drawIcon(avatarBmp);
                    remoteViews.setImageViewBitmap(R.id.avatar, avatarBmp);
                }

                // From
                int color = conv.hasUnreadMessages() ? SENDERS_TEXT_COLOR_UNREAD :
                        SENDERS_TEXT_COLOR_READ;
                SpannableStringBuilder from = addColor(conv.getRecipients().formatNames(", "),
                        color);

                // From Count
                int recipientCount = conv.getRecipients().size();
                if (recipientCount > 1) {
                    remoteViews.setTextViewText(R.id.from_count, "" + recipientCount);
                }

                // Blocked
                boolean hasBlocked = false;
                for (Contact contact : conv.getRecipients()) {
                    if (BlacklistUtils.isListed(mContext, contact.getNumber(),
                            BlacklistUtils.BLOCK_MESSAGES) != BlacklistUtils.MATCH_NONE) {
                        hasBlocked = true;
                        break;
                    }
                }
                if (hasBlocked) {
                    remoteViews.setViewVisibility(R.id.block, View.VISIBLE);
                } else {
                    remoteViews.setViewVisibility(R.id.block, View.GONE);
                }

                // Draft
                if (conv.hasDraft()) {
                    from.append(mContext.getResources().getString(R.string.draft_separator));
                    int before = from.length();
                    from.append(mContext.getResources().getString(R.string.has_draft));
                    from.setSpan(new TextAppearanceSpan(mContext,
                            android.R.style.TextAppearance_Small, color), before,
                            from.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                    from.setSpan(new ForegroundColorSpan(
                            mContext.getResources().getColor(R.drawable.text_color_red)),
                            before, from.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                }

                // Unread messages are shown in bold
                if (conv.hasUnreadMessages()) {
                    int highlightColor = mContext.getResources().getColor(
                            R.color.mms_next_theme_unread_from);
                    from.setSpan(new ForegroundColorSpan(highlightColor),
                                    0,
                                    from.length(),
                                    Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                    from.setSpan(ConversationListItem.STYLE_BOLD, 0, from.length(),
                            Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                }
                remoteViews.setTextViewText(R.id.from, from);

                // Unread count
                if (conv.getUnreadMessageCount() > 0) {
                    remoteViews.setTextViewText(R.id.unread_count,
                            "" + conv.getUnreadMessageCount());
                    remoteViews.setViewVisibility(R.id.unread_count, View.VISIBLE);
                } else {
                    remoteViews.setViewVisibility(R.id.unread_count, View.GONE);
                }

                // Subject
                // TODO: the SmileyParser inserts image spans but they don't seem to make it
                // into the remote view.
                SmileyParser parser = SmileyParser.getInstance();
                remoteViews.setTextViewText(R.id.subject,
                        addColor(parser.addSmileySpans(conv.getSnippet()),
                                conv.hasUnreadMessages() ? SUBJECT_TEXT_COLOR_UNREAD :
                                    SUBJECT_TEXT_COLOR_READ));

                // Error
                remoteViews.setViewVisibility(R.id.error, (conv.hasError()) ? View.VISIBLE :
                        View.GONE);

                // On click intent.
                Intent clickIntent = new Intent(Intent.ACTION_VIEW);
                clickIntent.setType("vnd.android-dir/mms-sms");
                clickIntent.putExtra("thread_id", conv.getThreadId());

                remoteViews.setOnClickFillInIntent(R.id.widget_conversation, clickIntent);

                return remoteViews;
            }
        }

        /**
         * @return the "View more conversations" view. When the user taps this item, they're
         * taken to the messaging app's conversation list.
         */
        private RemoteViews getViewMoreConversationsView() {
            if (Log.isLoggable(LogTag.WIDGET, Log.VERBOSE)) {
                Log.v(TAG, "getViewMoreConversationsView");
            }
            RemoteViews view = new RemoteViews(mContext.getPackageName(), R.layout.widget_loading);
            view.setTextViewText(
                    R.id.loading_text, mContext.getText(R.string.view_more_conversations));
            PendingIntent pendingIntent =
                    PendingIntent.getActivity(mContext, 0, new Intent(mContext,
                            ConversationList.class),
                            PendingIntent.FLAG_UPDATE_CURRENT);
            view.setOnClickPendingIntent(R.id.widget_loading, pendingIntent);
            return view;
        }

        @Override
        public RemoteViews getLoadingView() {
            RemoteViews view = new RemoteViews(mContext.getPackageName(), R.layout.widget_loading);
            view.setTextViewText(
                    R.id.loading_text, mContext.getText(R.string.loading_conversations));
            return view;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        private void onLoadComplete() {
            if (Log.isLoggable(LogTag.WIDGET, Log.VERBOSE)) {
                Log.v(TAG, "onLoadComplete");
            }
            RemoteViews remoteViews = new RemoteViews(mContext.getPackageName(), R.layout.widget);

            remoteViews.setViewVisibility(R.id.widget_unread_count, mUnreadConvCount > 0 ?
                    View.VISIBLE : View.GONE);

            if (mUnreadConvCount > 0) {
                remoteViews.setTextViewText(
                        R.id.widget_unread_count, Integer.toString(mUnreadConvCount));
                remoteViews.setTextViewText(R.id.widget_label, "New Messages");
            } else {
                remoteViews.setTextViewText(R.id.widget_label,
                        mContext.getString(R.string.app_label));
            }

            mAppWidgetManager.partiallyUpdateAppWidget(mAppWidgetId, remoteViews);
        }

        public void onUpdate(Contact updated) {
            if (Log.isLoggable(LogTag.WIDGET, Log.VERBOSE)) {
                Log.v(TAG, "onUpdate from Contact: " + updated);
            }
            mAppWidgetManager.notifyAppWidgetViewDataChanged(mAppWidgetId, R.id.conversation_list);
        }

    }

    private static Paint sWorkPaint = new Paint();

    /**
     * Helper function that draws the loaded icon bitmap into the new bitmap
     */
    private static Bitmap drawIcon(Bitmap icon) {
        Bitmap bitmap = Bitmap.createBitmap(icon.getWidth(), icon.getHeight(), Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        final RectF src = new RectF(0, 0, icon.getWidth(), icon.getHeight());
        final RectF dst = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
        drawIconOnCanvas(icon, canvas, src, dst);
        return bitmap;
    }

    /**
     * Draws the icon onto the canvas given the source rectangle of the bitmap and the destination
     * rectangle of the canvas.
     */
    protected static void drawIconOnCanvas(Bitmap icon, Canvas canvas, RectF src, RectF dst) {
        final Matrix matrix = new Matrix();

        // Draw bitmap through shader first.
        final BitmapShader shader = new BitmapShader(icon, TileMode.CLAMP, TileMode.CLAMP);
        matrix.reset();

        // Fit bitmap to bounds.
        matrix.setRectToRect(src, dst, Matrix.ScaleToFit.FILL);

        shader.setLocalMatrix(matrix);
        sWorkPaint.reset();
        sWorkPaint.setShader(shader);
        sWorkPaint.setAntiAlias(true);
        sWorkPaint.setFilterBitmap(true);
        sWorkPaint.setDither(true);
        canvas.drawCircle(dst.centerX(), dst.centerY(), dst.width() / 2f, sWorkPaint);

        // Then draw the border.
        final float borderWidth = 1f;
        sWorkPaint.reset();
        sWorkPaint.setColor(Color.TRANSPARENT);
        sWorkPaint.setStyle(Style.STROKE);
        sWorkPaint.setStrokeWidth(borderWidth);
        sWorkPaint.setAntiAlias(true);
        canvas.drawCircle(
                dst.centerX(),
                dst.centerY(),
                dst.width() / 2f - borderWidth / 2,
                sWorkPaint
        );

        sWorkPaint.reset();
    }

    /**
     * This provides an interface for anything subclassing to provide the avatar image for a
     * contact
     *
     * @param displayName {@link String}
     * @param address {@link String}
     * @param textSizeInDp float this should already be scaled by the caller!
     */
    private static Bitmap getDefaultContactPhoto(Context context, int color, String displayName,
            String address, float textSizeInDp) {

        // vars
        String firstLetter = "?";
        if (!TextUtils.isEmpty(displayName)){
            firstLetter = displayName.substring(0, 1).toUpperCase();
        }

        // hacky way to get the size
        Drawable d = context.getDrawable(com.android.ex.chips.R.drawable.ic_contact_picture);
        int w = d.getIntrinsicWidth();
        int h = d.getIntrinsicHeight();

        // Create a bitmap and canvas for paint
        final Bitmap icon;
        try {
            icon = Bitmap.createBitmap(w, h, Config.ARGB_8888);
        } catch (OutOfMemoryError oome) {
            Log.e(TAG, "Not enough memory to create icon!");
            return null;
        }

        Canvas canvas = new Canvas(icon);

        // Build paint
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setDither(true);
        p.setColor(color);
        p.setStrokeWidth(1);
        p.setTextSize(textSizeInDp);
        p.setTextScaleX(0.95f);
        float letterWidth = p.measureText(firstLetter);

        // Draw background circle
        canvas.drawCircle(w / 2, h / 2, w / 2, p);
        p.setColor(Color.WHITE);
        canvas.drawText(firstLetter, (w / 2) - (letterWidth / 2), (h / 2) + (textSizeInDp / 3), p);

        return icon;

    }

}
