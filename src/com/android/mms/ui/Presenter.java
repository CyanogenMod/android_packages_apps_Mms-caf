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
import android.view.View;
import android.view.ViewGroup;

import com.android.mms.model.IModelChangedObserver;
import com.android.mms.model.MediaModel;
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

    public void unbind(){}

    public abstract View present(ViewGroup v, PresenterOptions presenterOptions);

    public void presentThumbnail(ViewGroup v, AttachmentPresenterOptions presenterOptions) {}

    public boolean hideArrowHead() { return false; }

    public static abstract class PresenterOptions {
        public abstract void onItemLongClick();
        public abstract long getMessageId();
        public abstract void donePresenting(int height);
        public abstract int getMaxWidth();
        public abstract boolean isIncomingMessage();
        public abstract int getAccentColor();
        public abstract boolean shouldRecycle();
        public abstract boolean isInActionMode();
    }

    public abstract static class AttachmentPresenterOptions {
        public abstract int getAttachmentWidth();
        public abstract int getAttachmentHeight();
    }
}
