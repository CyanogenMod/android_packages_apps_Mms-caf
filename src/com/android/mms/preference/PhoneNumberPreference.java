/*
 * Copyright (C) 2015 The CyanogenMod Project
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

package com.android.mms.preference;

import android.content.Context;
import android.preference.EditTextPreference;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.telephony.PhoneNumberUtils;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import com.android.mms.MmsApp;
import com.android.mms.util.AddressUtils;

/**
 * EditTextPreference customized to display and work with phone numbers
 */
public class PhoneNumberPreference extends EditTextPreference {

    public PhoneNumberPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public PhoneNumberPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public PhoneNumberPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PhoneNumberPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        EditText editText = getEditText();
        editText.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
        editText.setInputType(InputType.TYPE_CLASS_PHONE);
    }

    @Override
    public void setText(String text) {
        // normalize phone number before storing it
        String newText = PhoneNumberUtils.
                formatNumberToE164(text, MmsApp.getApplication().getCurrentCountryIso());
        super.setText(newText);
    }

    @Override
    public String getText() {
        String normalizedNumber = super.getText();
        return AddressUtils.getPhoneNumberFromNormalizedNumber(normalizedNumber);
    }

    @Override
    protected void onAddEditTextToDialogView(View dialogView, EditText editText) {
        super.onAddEditTextToDialogView(dialogView, editText);
        editText.setSelection(editText.getText().length());
    }

    @Override
    protected void onBindDialogView(View view) {
        // reset the edit text before setting the new preference value
        // otherwise, the PhoneNumberFormattingTextWatcher doesn't do the right thing
        getEditText().setText("");
        super.onBindDialogView(view);
    }
}