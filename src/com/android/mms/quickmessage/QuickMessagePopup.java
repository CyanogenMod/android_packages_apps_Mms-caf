/*
 * Copyright (C) 2012 The CyanogenMod Project (DvTonder)
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

package com.android.mms.quickmessage;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.Profile;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.telephony.SubscriptionManager;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.telephony.util.BlacklistUtils;
import com.android.mms.MmsConfig;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.ContactList;
import com.android.mms.data.Conversation;
import com.android.mms.transaction.MessagingNotification;
import com.android.mms.transaction.MessagingNotification.NotificationInfo;
import com.android.mms.transaction.SmsMessageSender;
import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.MessagingPreferenceActivity;
import com.android.mms.util.SmileyParser;
import com.android.mms.util.UnicodeFilter;
import com.google.android.mms.MmsException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class QuickMessagePopup extends Activity {
    private static final String LOG_TAG = "QuickMessagePopup";

    private static final boolean DEBUG = false;

    // Intent bungle fields
    public static final String SMS_FROM_NAME_EXTRA =
            "com.android.mms.SMS_FROM_NAME";
    public static final String SMS_FROM_NUMBER_EXTRA =
            "com.android.mms.SMS_FROM_NUMBER";
    public static final String SMS_NOTIFICATION_OBJECT_EXTRA =
            "com.android.mms.NOTIFICATION_OBJECT";
    public static final String QR_SHOW_KEYBOARD_EXTRA =
            "com.android.mms.QR_SHOW_KEYBOARD";

    // Message removal
    public static final String QR_REMOVE_MESSAGES_EXTRA =
            "com.android.mms.QR_REMOVE_MESSAGES";
    public static final String QR_THREAD_ID_EXTRA =
            "com.android.mms.QR_THREAD_ID";

    // View items
    private ImageView mQmPagerArrow;
    private TextView mQmMessageCounter;
    private Button mCloseButton;
    private Button mViewButton;

    // General items
    private Drawable mDefaultContactImage;
    private boolean mScreenUnlocked = false;
    private KeyguardManager mKeyguardManager = null;
    private PowerManager mPowerManager;

    // Message list items
    private ArrayList<QuickMessage> mMessageList;
    private QuickMessage mCurrentQm = null;
    private int mCurrentPage = -1; // Set to an invalid index

    // Configuration
    private boolean mCloseClosesAll = false;
    private boolean mWakeAndUnlock = false;
    private boolean mDarkTheme = false;
    private String mUnicodeStripping = MessagingPreferenceActivity.UNICODE_STRIPPING_LEAVE_INTACT;
    private UnicodeFilter mUnicodeFilter = null;

    // Message pager
    private ViewPager mMessagePager;
    private MessagePagerAdapter mPagerAdapter;

    // Options menu items
    private static final int MENU_ADD_TO_BLACKLIST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialise the message list and other variables
        mMessageList = new ArrayList<QuickMessage>();
        mDefaultContactImage = getResources().getDrawable(R.drawable.ic_contact_picture);
        mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mKeyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);

        // Get the preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mCloseClosesAll = prefs.getBoolean(MessagingPreferenceActivity.QM_CLOSE_ALL_ENABLED, false);
        mWakeAndUnlock = prefs.getBoolean(MessagingPreferenceActivity.QM_LOCKSCREEN_ENABLED, false);
        mDarkTheme = prefs.getBoolean(MessagingPreferenceActivity.QM_DARK_THEME_ENABLED, false);
        mUnicodeStripping = prefs.getString(MessagingPreferenceActivity.UNICODE_STRIPPING,
                MessagingPreferenceActivity.UNICODE_STRIPPING_LEAVE_INTACT);

        // Set the window features and layout
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_quickmessage);

        // Turn on the Options Menu
        invalidateOptionsMenu();

        // Load the views and Parse the intent to show the QuickMessage
        setupViews();
        parseIntent(getIntent().getExtras(), false);

        setFinishOnTouchOutside(false);
    }

    private void setupViews() {
        // Load the main views
        mQmPagerArrow = (ImageView) findViewById(R.id.pager_arrow);
        mQmMessageCounter = (TextView) findViewById(R.id.message_counter);
        mCloseButton = (Button) findViewById(R.id.button_close);
        mViewButton = (Button) findViewById(R.id.button_view);

        // Set the theme color on the pager arrow
        mQmPagerArrow.setBackgroundColor(getResources().getColor(mDarkTheme
                ? R.color.quickmessage_body_dark_bg : R.color.quickmessage_body_light_bg));

        // ViewPager Support
        mPagerAdapter = new MessagePagerAdapter();
        mMessagePager = (ViewPager) findViewById(R.id.message_pager);
        mMessagePager.setAdapter(mPagerAdapter);
        mMessagePager.setOnPageChangeListener(mPagerAdapter);

        // Close button
        mCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // If not closing all, close the current QM and move on
                int numMessages = mMessageList.size();
                if (mCloseClosesAll || numMessages == 1) {
                    clearNotification(true);
                    finish();
                } else {
                    // Dismiss the keyboard if it is shown
                    QuickMessage qm = mMessageList.get(mCurrentPage);
                    if (qm != null) {
                        dismissKeyboard(qm);

                        if (mCurrentPage < numMessages-1) {
                            showNextMessageWithRemove(qm);
                        } else {
                            showPreviousMessageWithRemove(qm);
                        }
                    }
                }
            }
        });

        // View button
        mViewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Override the re-lock if the screen was unlocked
                if (mScreenUnlocked) {
                    // Cancel the receiver that will clear the wake locks
                    ClearAllReceiver.removeCancel(getApplicationContext());
                    ClearAllReceiver.clearAll(false);
                    mScreenUnlocked = false;
                }

                // Trigger the view intent
                mCurrentQm = mMessageList.get(mCurrentPage);
                Intent vi = mCurrentQm.getViewIntent();
                if (vi != null) {
                    mCurrentQm.saveReplyText();
                    vi.putExtra("sms_body", mCurrentQm.getReplyText());

                    startActivity(vi);
                }
                clearNotification(false);
                finish();
            }
        });
    }

    private void parseIntent(Bundle extras, boolean newMessage) {
        if (extras == null) {
            return;
        }

        // Check if we are being called to remove messages already showing
        if (extras.getBoolean(QR_REMOVE_MESSAGES_EXTRA, false)) {
            // Get the ID
            long threadId = extras.getLong(QR_THREAD_ID_EXTRA, -1);
            if (threadId != -1) {
                removeMatchingMessages(threadId);
            }
        } else {
            // Parse the intent and ensure we have a notification object to work with
            NotificationInfo nm =
                    (NotificationInfo) extras.getParcelable(SMS_NOTIFICATION_OBJECT_EXTRA);
            if (nm != null) {
                QuickMessage qm = new QuickMessage(extras.getString(SMS_FROM_NAME_EXTRA),
                        extras.getString(SMS_FROM_NUMBER_EXTRA), nm);
                mMessageList.add(qm);
                mPagerAdapter.notifyDataSetChanged();
                // If triggered from Quick Reply the keyboard should be visible immediately
                if (extras.getBoolean(QR_SHOW_KEYBOARD_EXTRA, false)) {
                    getWindow().setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }

                if (newMessage && mCurrentPage != -1) {
                    // There is already a message showing, stay on the current one
                } else {
                    // Set the current message to the last message received
                    mCurrentPage = mMessageList.size()-1;
                }
                mMessagePager.setCurrentItem(mCurrentPage);

                if (DEBUG)
                    Log.d(LOG_TAG, "parseIntent(): New message from " + qm.getFromName().toString()
                            + " added. Number of messages = " + mMessageList.size()
                            + ". Displaying page #" + (mCurrentPage+1));

                // Make sure the counter is accurate
                updateMessageCounter();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (DEBUG)
            Log.d(LOG_TAG, "onNewIntent() called");

        // Set new intent
        setIntent(intent);

        // Load and display the new message
        parseIntent(intent.getExtras(), true);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Unlock the screen if needed
        unlockScreen();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mScreenUnlocked) {
            // Cancel the receiver that will clear the wake locks
            ClearAllReceiver.removeCancel(getApplicationContext());
            ClearAllReceiver.clearAll(true);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);

        menu.clear();

        // Add to Blacklist item (if enabled)
        if (BlacklistUtils.isBlacklistEnabled(this)) {
            menu.add(0, MENU_ADD_TO_BLACKLIST, 0, R.string.add_to_blacklist)
                    .setIcon(R.drawable.ic_block_message_holo_dark)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ADD_TO_BLACKLIST:
                confirmAddBlacklist();
                return true;

            default:
                return super.onContextItemSelected(item);
        }
    }

    //==========================================================
    // Utility methods
    //==========================================================

    /**
     * Copied from ComposeMessageActivity.java, this method displays a pop-up a dialog confirming
     * adding the current senders number to the blacklist
     */
    private void confirmAddBlacklist() {
        // Get the sender number
        final String number = mCurrentQm.getFromNumber()[0];
        if (number == null) {
            return;
        }

        // Show dialog
        final String message = getString(R.string.add_to_blacklist_message, number);
        new AlertDialog.Builder(this)
                .setTitle(R.string.add_to_blacklist)
                .setMessage(message)
                .setPositiveButton(R.string.alert_dialog_yes,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        BlacklistUtils.addOrUpdate(getApplicationContext(), number,
                                BlacklistUtils.BLOCK_MESSAGES, BlacklistUtils.BLOCK_MESSAGES);
                    }
                })
                .setNegativeButton(R.string.alert_dialog_no, null)
                .show();
    }

    /**
     * This method dismisses the on screen keyboard if it is visible for the supplied qm
     *
     * @param qm - qm to check against
     */
    private void dismissKeyboard(QuickMessage qm) {
        if (qm != null) {
            EditText editView = qm.getEditText();
            if (editView != null) {
                InputMethodManager imm =
                        (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(editView.getApplicationWindowToken(), 0);
            }
        }
    }

    /**
     * If 'Wake and unlock' is enabled, this method will unlock the screen
     */
    private void unlockScreen() {
        // See if the lock screen should be disabled
        if (!mWakeAndUnlock) {
            return;
        }

        ManageWakeLock.acquireFull(this);
        mScreenUnlocked = true;
    }

    /**
     * Update the page indicator counter to show the currently selected visible page number
     */
    public void updateMessageCounter() {
        int current = mCurrentPage + 1;
        int total = mMessageList.size();
        mQmMessageCounter.setText(getString(R.string.message_counter, current, total));

        if (DEBUG) {
            Log.d(LOG_TAG, "updateMessageCounter() called, counter text set to "
                    + current + " of " + total);
        }
    }

    /**
     * Remove the supplied qm from the ViewPager and show the previous/older message
     *
     * @param qm
     */
    public void showPreviousMessageWithRemove(QuickMessage qm) {
        if (qm != null) {
            if (DEBUG)
                Log.d(LOG_TAG, "showPreviousMessageWithRemove()");

            markMessageRead(qm);
            if (mCurrentPage > 0) {
                updatePages(mCurrentPage - 1, qm);
            }
        }
    }

    /**
     * Remove the supplied qm from the ViewPager and show the next/newer message
     *
     * @param qm
     */
    public void showNextMessageWithRemove(QuickMessage qm) {
        if (qm != null) {
            if (DEBUG)
                Log.d(LOG_TAG, "showNextMessageWithRemove()");

            markMessageRead(qm);
            if (mCurrentPage < (mMessageList.size() - 1)) {
                updatePages(mCurrentPage, qm);
            }
        }
    }

    /**
     * Handle qm removal and the move to and display of the appropriate page
     *
     * @param gotoPage - page number to display after the removal
     * @param removeMsg - qm to remove from ViewPager
     */
    private void updatePages(int gotoPage, QuickMessage removeMsg) {
        mMessageList.remove(removeMsg);
        mPagerAdapter.notifyDataSetChanged();
        mMessagePager.setCurrentItem(gotoPage);
        updateMessageCounter();

        if (DEBUG)
            Log.d(LOG_TAG, "updatePages(): Removed message " + removeMsg.getThreadId()
                    + " and changed to page #" + (gotoPage+1) + ". Remaining messages = "
                    + mMessageList.size());
    }

    /**
     * Remove all matching quickmessages for the supplied thread id
     *
     * @param threadId
     */
    public void removeMatchingMessages(long threadId) {
        if (DEBUG)
            Log.d(LOG_TAG, "removeMatchingMessages() looking for match with threadID = "
                    + threadId);

        Iterator<QuickMessage> itr = mMessageList.iterator();

        // Iterate through the list and remove the messages that match
        while (itr.hasNext()) {
            if (itr.next().getThreadId() == threadId) {
                itr.remove();
            }
        }

        // See if there are any remaining messages and update the pager
        if (mMessageList.size() > 0) {
            mPagerAdapter.notifyDataSetChanged();
            mMessagePager.setCurrentItem(1); // First message
            updateMessageCounter();
        } else {
            // we are done
            finish();
        }
    }

    /**
     * Marks the supplied qm as read
     *
     * @param qm
     */
    private void markMessageRead(QuickMessage qm) {
        if (qm == null) {
            return;
        }
        Conversation con = Conversation.get(this, qm.getThreadId(), true);
        if (con != null) {
            con.markAsRead(false);
            if (DEBUG)
                Log.d(LOG_TAG, "markMessageRead(): Marked message "
                        + qm.getThreadId() + " as read");
        }
    }

    /**
     * Marks all qm's in the message list as read
     */
    private void markAllMessagesRead() {
        // This iterates through our MessageList and marks the contained threads as read
        for (QuickMessage qm : mMessageList) {
            markMessageRead(qm);
        }
    }

    /**
     * Show the appropriate image for the QuickContact badge
     *
     * @param badge
     * @param addr
     * @param isSelf
     */
    private void updateContactBadge(QuickContactBadge badge, String addr, boolean isSelf) {
        if (isSelf || !TextUtils.isEmpty(addr)) {
            Contact contact = isSelf ? Contact.getMe(false) : Contact.get(addr, false);
            contact.bindAvatar(badge);

            if (isSelf) {
                badge.assignContactUri(Profile.CONTENT_URI);
            } else {
                if (contact.existsInDatabase()) {
                    badge.assignContactUri(contact.getUri());
                } else {
                    badge.assignContactFromPhone(contact.getNumber(), true);
                }
            }
        } else {
            badge.setImageDrawable(mDefaultContactImage);
        }
    }

    /**
     * Sends a quick message immediately, with the only UI being a Toast indicating
     * success or failure.
     * @param subId The subscription ID of the SIM slot to send the message with.
     * @param threadId The thread ID of the QuickMessage.
     * @param message The actual message.
     * @param qm The QuickMessage object to send.
     */
    private void sendQuickMessageBackground(int subId, long threadId,
            String message, QuickMessage qm) {
        SmsMessageSender sender = new SmsMessageSender(this,
                qm.getFromNumber(), message, threadId, subId);

        try {
            if (DEBUG) {
                Log.d(LOG_TAG, "sendQuickMessage(): Sending message to " + qm.getFromName()
                        + ", with threadID = " + threadId
                        + ". Current page is #" + (mCurrentPage + 1));
            }

            sender.sendMessage(threadId);
            Toast.makeText(this, R.string.toast_sending_message, Toast.LENGTH_SHORT).show();
        } catch (MmsException e) {
            if (DEBUG) {
                Log.e(LOG_TAG, "Error sending message to " + qm.getFromName());
            }
        }
    }

    /**
     * Use standard api to send the supplied message
     *
     * @param message - message to send
     * @param qm - qm to reply to (for sender details)
     */
    private boolean sendQuickMessage(final String message, final QuickMessage qm) {
        if (message == null || qm == null) {
            return true;
        }

        final long threadId = qm.getThreadId();

        // If SMS prompt is enabled, prompt the user.
        // Use the default SMS phone ID to reply in the multi-sim environment.
        if (SubscriptionManager.isSMSPromptEnabled()) {
            MessageUtils.showSimSelector(this, new MessageUtils.OnSimSelectedCallback() {
                @Override
                public void onSimSelected(int subId) {
                    sendQuickMessageBackground(subId, threadId, message, qm);
                    mPagerAdapter.moveOn(qm);
                }
            });
            return false;
        } else {
            int subId = SubscriptionManager.getDefaultSmsSubId();
            sendQuickMessageBackground(subId, threadId, message, qm);
            return true;
        }
    }

    /**
     * Clears the status bar notification and, optionally, mark all messages as read
     * This is used to clean up when we are done with all qm's
     *
     * @param markAsRead - should remaining qm's be maked as read?
     */
    private void clearNotification(boolean markAsRead) {
        // Dismiss the notification that brought us here.
        NotificationManager notificationManager =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.cancel(MessagingNotification.NOTIFICATION_ID);

        // Mark all contained conversations as seen
        if (markAsRead) {
            markAllMessagesRead();
        }

        // Clear the messages list
        mMessageList.clear();
        mPagerAdapter.notifyDataSetChanged();
        if (DEBUG)
            Log.d(LOG_TAG, "clearNotification(): Message list cleared. Size = "
                    + mMessageList.size());
    }

    //==========================================================
    // Inner classes
    //==========================================================

    /**
     * Message Pager class, used to display and navigate through the ViewPager pages
     */
    private class MessagePagerAdapter extends PagerAdapter
                    implements ViewPager.OnPageChangeListener {
        @Override
        public int getCount() {
            return mMessageList.size();
        }

        @Override
        public Object instantiateItem(View collection, int position) {
            QuickMessage qm = mMessageList.get(position);
            if (qm == null) {
                return null;
            }

            if (mCurrentQm == null) {
                mCurrentQm = qm;
            }

            // Load the layout to be used
            int layoutResId = mDarkTheme
                    ? R.layout.quickmessage_content_dark : R.layout.quickmessage_content_light;
            View layout = LayoutInflater.from(QuickMessagePopup.this).inflate(layoutResId, null);

            // Load the main views
            EditText qmReplyText = (EditText) layout.findViewById(R.id.embedded_text_editor);
            TextView qmTextCounter = (TextView) layout.findViewById(R.id.text_counter);
            ImageButton qmSendButton = (ImageButton) layout.findViewById(R.id.send_button_sms);
            TextView qmMessageText = (TextView) layout.findViewById(R.id.messageTextView);
            TextView qmFromName = (TextView) layout.findViewById(R.id.fromTextView);
            TextView qmTimestamp = (TextView) layout.findViewById(R.id.timestampTextView);
            QuickContactBadge qmContactBadge =
                    (QuickContactBadge) layout.findViewById(R.id.contactBadge);

            if (DEBUG)
                Log.d(LOG_TAG, "instantiateItem(): Creating page #" + (position + 1)
                        + " for message from " + qm.getFromName()
                        + ". Number of pages to create = " + getCount());

            // Set the general fields
            qmFromName.setText(qm.getFromName());
            qmTimestamp.setText(MessageUtils.formatTimeStampString(QuickMessagePopup.this,
                    qm.getTimestamp(), false));
            qmContactBadge.setOverlay(null);
            updateContactBadge(qmContactBadge, qm.getFromNumber()[0], false);
            SmileyParser parser = SmileyParser.getInstance();
            qmMessageText.setText(parser.addSmileySpans(qm.getMessageBody()));

            if (!mDarkTheme) {
                // We are using a holo.light background with a holo.dark activity theme
                // Override the EditText background to use the holo.light theme
                qmReplyText.setBackgroundResource(R.drawable.edit_text_holo_light);
            }

            if (!TextUtils.equals(mUnicodeStripping,
                    MessagingPreferenceActivity.UNICODE_STRIPPING_LEAVE_INTACT)) {
                boolean stripNonDecodableOnly = TextUtils.equals(mUnicodeStripping,
                        MessagingPreferenceActivity.UNICODE_STRIPPING_NON_DECODABLE);
                mUnicodeFilter = new UnicodeFilter(stripNonDecodableOnly);
            }

            // Set the remaining values
            qmReplyText.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE);
            qmReplyText.setText(qm.getReplyText());
            qmReplyText.setSelection(qm.getReplyText().length());
            qmReplyText.addTextChangedListener(new QmTextWatcher(QuickMessagePopup.this,
                    qmTextCounter, qmSendButton, mUnicodeFilter));
            qmReplyText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (event != null) {
                        // event != null means enter key pressed
                        if (!event.isShiftPressed()) {
                            // if shift is not pressed then move focus to send button
                            if (v != null) {
                                View focusableView = v.focusSearch(View.FOCUS_RIGHT);
                                if (focusableView != null) {
                                    focusableView.requestFocus();
                                    return true;
                                }
                            }
                        }
                        return false;
                    }
                    if (actionId == EditorInfo.IME_ACTION_SEND && v != null) {
                        QuickMessage current = mMessageList.get(mCurrentPage);
                        if (current != null) {
                            sendMessageAndMoveOn(v.getText().toString(), current);
                        }
                    }
                    return true;
                }
            });

            qmReplyText.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(MmsConfig.getMaxTextLimit())
            });

            QmTextWatcher.getQuickReplyCounterText(qmReplyText.getText().toString(),
                    qmTextCounter, qmSendButton);

            // Add the context menu
            registerForContextMenu(qmReplyText);

            // Store the EditText object for future use
            qm.setEditText(qmReplyText);

            // Send button
            qmSendButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    QuickMessage current = mMessageList.get(mCurrentPage);
                    if (current != null) {
                        EditText editView = current.getEditText();
                        if (editView != null) {
                            sendMessageAndMoveOn(editView.getText().toString(), current);
                        }
                    }
                }
            });

            // Add the layout to the viewpager
            ((ViewPager) collection).addView(layout);

            return layout;
        }

        /**
         * This method sends the supplied message in reply to the supplied qm and then
         * moves to the next or previous message as appropriate.
         *
         * @param message - message to send
         * @param qm - qm we are replying to (for sender details)
         */
        private void sendMessageAndMoveOn(String message, QuickMessage qm) {
            if (mUnicodeFilter != null) {
                message = mUnicodeFilter.filter(message).toString();
            }
            if (sendQuickMessage(message, qm)) {
                moveOn(qm);
            }
        }

        /**
         * Sends the supplied message in reply to the supplied qm and then
         * moves to the next or previous message as appropriate. If this is the last qm
         * in the MessageList, we end by clearing the notification and calling finish()
         * @param qm - qm we are replying to (for sender details)
         */
        public void moveOn(QuickMessage qm) {
            // Close the current QM and move on
            int numMessages = mMessageList.size();
            if (numMessages == 1) {
                // No more messages
                clearNotification(true);
                finish();
            } else {
                // Dismiss the keyboard if it is shown
                dismissKeyboard(qm);

                if (mCurrentPage < numMessages-1) {
                    showNextMessageWithRemove(qm);
                } else {
                    showPreviousMessageWithRemove(qm);
                }
            }
        }

        @Override
        public void onPageSelected(int position) {
            // The user had scrolled to a new message
            if (mCurrentQm != null) {
                mCurrentQm.saveReplyText();
            }

            // Set the new 'active' QuickMessage
            mCurrentPage = position;
            mCurrentQm = mMessageList.get(position);

            if (DEBUG)
                Log.d(LOG_TAG, "onPageSelected(): Current page is #" + (position + 1)
                        + " of " + getCount() + " pages. Currenty visible message is from "
                        + mCurrentQm.getFromName());

            updateMessageCounter();
        }

        @Override
        public int getItemPosition(Object object) {
            // This is needed to force notifyDatasetChanged() to rebuild the pages
            return PagerAdapter.POSITION_NONE;
        }

        @Override
        public void destroyItem(View collection, int position, Object view) {
            ((ViewPager) collection).removeView((View) view);
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
                return view == ((View) object);
        }

        @Override
        public void finishUpdate(View container) {
            if (mCurrentQm != null && mCurrentQm.getEditText() != null) {
                // After a page switch, re-focus on the reply editor
                mCurrentQm.getEditText().requestFocus();
            }
        }

        @Override
        public void restoreState(Parcelable state, ClassLoader loader) {}

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void startUpdate(View container) {
            if (mCurrentQm != null) {
                // When the view is refreshed, preserve the current reply
                mCurrentQm.saveReplyText();
            }
        }

        @Override
        public void onPageScrollStateChanged(int state) {}

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}
   }
}
