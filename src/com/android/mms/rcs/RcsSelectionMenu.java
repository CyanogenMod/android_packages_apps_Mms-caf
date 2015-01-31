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

import com.android.mms.ui.PopupList;
import com.android.mms.ui.PopupList.Item;
import com.android.mms.ui.PopupList.OnPopupItemClickListener;
import com.android.mms.R;

import android.content.Context;
import android.view.View;
import android.widget.Button;

public class RcsSelectionMenu implements View.OnClickListener {
    private final Context mContext;
    private final Button mButton;
    private final PopupList mPopupList;
    public static final int SELECT_OR_DESELECT = 1;

    public RcsSelectionMenu(Context context, Button button,
            PopupList.OnPopupItemClickListener listener) {
        mContext = context;
        mButton = button;
        mPopupList = new PopupList(context, mButton);
        mPopupList.addItem(SELECT_OR_DESELECT, context.getString(R.string.selected_all));
        mPopupList.setOnPopupItemClickListener(listener);
        mButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        mPopupList.show();
    }

    public void dismiss() {
        mPopupList.dismiss();
    }

    public void updateSelectAllMode(boolean inSelectAllMode) {
        PopupList.Item item = mPopupList.findItem(SELECT_OR_DESELECT);
        if (item != null) {
            item.setTitle(mContext.getString(
                    inSelectAllMode ? R.string.deselected_all : R.string.selected_all));
        }
    }

    public void setTitle(CharSequence title) {
        mButton.setText(title);
    }
}
