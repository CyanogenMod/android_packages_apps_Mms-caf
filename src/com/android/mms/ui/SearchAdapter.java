package com.android.mms.ui;

import com.android.mms.R;
import com.android.mms.data.Contact;
import com.android.mms.data.Conversation;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class SearchAdapter extends CursorAdapter {
    private String mQuery;

    public SearchAdapter(Context context) {
        super(context, null, 0);
    }

    private class ViewHolder {
        private TextView name, date;
        private View attachment;
        private SearchActivity.TextViewSnippet body;
        private ImageView avatarView;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        View view = View.inflate(context, R.layout.conversation_list_item, null);
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
        Conversation conversation = Conversation.from(context, cursor);

        holder.name.setText(conversation.getRecipients().formatNames(", "));
        holder.date.setText(MessageUtils.formatTimeStampString(context, conversation.getDate()));
        holder.attachment.setVisibility(conversation.hasAttachment() ? View.VISIBLE : View.GONE);
        holder.body.setText(cursor.getString(cursor.getColumnIndex("body")), mQuery);

        if (conversation.getRecipients().size() == 1) {
            Contact contact = conversation.getRecipients().get(0);
            contact.bindAvatar(holder.avatarView);
        } else {
            // TODO get a multiple recipients asset (or do something else)
            holder.avatarView.setImageDrawable(ConversationListItem.sDefaultContactImage);
        }
    }

    public Conversation getConversation(int pos) {
        Cursor cursor = getCursor();
        if (cursor == null) {
            return null;
        }
        int originalPosition = cursor.getPosition();
        Cursor cPosition = (Cursor) getItem(originalPosition);
        Conversation conversation = Conversation.from(mContext, cPosition);
        cursor.moveToPosition(originalPosition);
        return conversation;
    }

    public void setQuery(String query) {
        mQuery = query;
    }

    public String getQuery() {
        return mQuery;
    }
}
