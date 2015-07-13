/*
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

import android.content.Context;
import android.view.ViewGroup;

import com.android.mms.model.IModelChangedObserver;
import com.android.mms.model.Model;

/**
 * An abstract message presenter.
 */
public abstract class Presenter<M> implements IModelChangedObserver {

    private final Context mContext;
    private final M mModel;

    public Presenter(Context context, M modelInterface) {
        mContext = context;
        mModel = modelInterface;
    }

    public Context getContext() {
        return mContext;
    }

    public M getModel() {
        return mModel;
    }

    public void onModelChanged(Model model, boolean dataChanged){}

    public void cancelBackgroundLoading(){}

    public void present(ViewGroup v, PresenterOptions presenterOptions) {}

    public void presentThumbnail(ViewGroup v, PresenterOptions presenterOptions) {}

    public boolean showsArrowHead() { return true; }

    public static final class PresenterOptions {

        public interface Callback {
            void onItemLongClick();
            long getMessageId();
            void donePresenting(int height);
        }

        private boolean isIncomingMessage;
        private int accentColor;
        private boolean isThumbnail;
        private Callback mCallback;

        public boolean isIncomingMessage() {
            return isIncomingMessage;
        }

        public void setIsIncomingMessage(boolean isIncomingMessage) {
            this.isIncomingMessage = isIncomingMessage;
        }

        public int getAccentColor() {
            return accentColor;
        }

        public void setAccentColor(int accentColor) {
            this.accentColor = accentColor;
        }

        public boolean isThumbnail() {
            return isThumbnail;
        }

        public void setIsThumbnail(boolean isThumbnail) {
            this.isThumbnail = isThumbnail;
        }

        public Callback getCallback() {
            return mCallback;
        }

        public void setCallback(Callback callback) {
            mCallback = callback;
        }
    }
}
