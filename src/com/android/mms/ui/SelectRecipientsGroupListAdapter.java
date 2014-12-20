/*
 * Copyright (C) 2013 The CyanogenMod Project
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

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import com.android.mms.R;
import com.android.mms.data.Group;

import java.util.List;

public class SelectRecipientsGroupListAdapter extends ArrayAdapter<Group> {
    private final LayoutInflater mInflater;

    public SelectRecipientsGroupListAdapter(Context context) {
        super(context, 0);
        mInflater = LayoutInflater.from(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView != null) {
            bindView((ViewHolder) convertView.getTag(), getItem(position));
            return convertView;
        }

        View view = mInflater.inflate(R.layout.select_recipients_list_group_item, parent, false);
        ViewHolder holder = new ViewHolder();

        holder.title = (TextView) view.findViewById(R.id.title);
        holder.account = (TextView) view.findViewById(R.id.account);
        holder.checkBox = (CheckBox) view.findViewById(R.id.checkbox);
        view.setTag(holder);

        bindView(holder, getItem(position));

        return view;
    }

    private void bindView(ViewHolder holder, Group group) {
        SpannableStringBuilder groupTitle = new SpannableStringBuilder(group.getTitle());
        int before = groupTitle.length();

        groupTitle.append(" ");
        groupTitle.append(Integer.toString(group.getSummaryCount()));
        groupTitle.setSpan(new ForegroundColorSpan(
                getContext().getResources().getColor(R.color.message_count_color)),
                before, groupTitle.length(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        holder.title.setText(groupTitle);

        holder.account.setText(group.getAccountName());
        holder.checkBox.setChecked(group.isChecked());
    }

    private static class ViewHolder {
        TextView title;
        TextView account;
        CheckBox checkBox;
    }
}
