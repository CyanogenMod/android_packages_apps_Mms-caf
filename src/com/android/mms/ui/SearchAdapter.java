package com.android.mms.ui;

import android.content.Context;
import android.database.Cursor;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.Conversation;

import java.lang.ref.WeakReference;

public class SearchAdapter extends CursorAdapter {

    public static final int THREAD_ID_INDEX = 1;
    private static final int BODY_INDEX = 3;
    private static final int DATE_INDEX = 4;
    private final LayoutInflater mLayoutInflater;

    private String mQuery;

    public SearchAdapter(Context context) {
        super(context, null, 0);
        mLayoutInflater = LayoutInflater.from(context);
    }

    private class ViewHolder {
        private TextView name, date;
        private View attachment;
        private SearchActivity.TextViewSnippet body;
        private ImageView avatarView;
        private FetchConversationTask task;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = mLayoutInflater.inflate(R.layout.conversation_list_item, parent, false);
        ViewHolder holder = new ViewHolder();
        holder.name = (TextView) view.findViewById(R.id.from);
        holder.date = (TextView) view.findViewById(R.id.date);
        holder.body = (SearchActivity.TextViewSnippet) view.findViewById(R.id.subject);
        holder.avatarView = (ImageView) view.findViewById(R.id.avatar);
        holder.avatarView.setVisibility(View.VISIBLE);
        holder.attachment = view.findViewById(R.id.attachment);
        view.setTag(holder);
        return view;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ViewHolder holder = (ViewHolder) view.getTag();
        if (holder.task != null) {
            holder.task.cancel(true);
        }
        long threadId = cursor.getInt(THREAD_ID_INDEX);
        holder.task = new FetchConversationTask(view, threadId);
        holder.task.execute();

        long date = cursor.getLong(DATE_INDEX);
        holder.date.setText(MessageUtils.formatTimeStampString(context, date));
        holder.body.setText(cursor.getString(BODY_INDEX), mQuery);
        holder.avatarView.setImageDrawable(ConversationListItem.sDefaultContactImage);
    }

    private static class FetchConversationTask extends AsyncTask<Void, Void, Conversation> {
        private final WeakReference<View> mViewReference;
        private final long mThreadId;

        FetchConversationTask(View view, long threadId) {
            mViewReference = new WeakReference<View>(view);
            mThreadId = threadId;
        }

        @Override
        protected Conversation doInBackground(Void... params) {
            View view = mViewReference.get();
            if (view != null) {
                return Conversation.get(view.getContext(), mThreadId, true);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Conversation conversation) {
            if (isCancelled()) {
                return;
            }
            if (conversation != null) {
                View view = mViewReference.get();
                if (view != null) {
                    ViewHolder holder = (ViewHolder) view.getTag();
                    if (holder != null && holder.task == this) {
                        holder.name.setText(conversation.getRecipients().formatNames(", "));
                        holder.attachment.setVisibility(conversation.hasAttachment() ? View.VISIBLE : View.GONE);
                        if (conversation.getRecipients().size() == 1) {
                            Contact contact = conversation.getRecipients().get(0);
                            contact.bindAvatar(holder.avatarView);
                        }
                    }
                }

            }
        }
    }

    public void setQuery(String query) {
        mQuery = query;
    }

    public String getQuery() {
        return mQuery;
    }
}
