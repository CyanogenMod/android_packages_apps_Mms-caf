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
public abstract class Presenter<I> implements IModelChangedObserver {
    private final Context mContext;
    private final I mModel;



    public Context getContext() {
        return mContext;
    }

    public I getModel() {
        return mModel;
    }

    public Presenter(Context context, I modelInterface) {
        mContext = context;
        mModel = modelInterface;
    }

//        public void present(SlideViewInterface view, SlideModel slide){}
//        public void present(SlideViewInterface view, SlideModel slide,
//                                                                       ItemLoadedCallback callback){}


    public void onModelChanged(Model model, boolean dataChanged){}

    public void cancelBackgroundLoading(){}

    public void present(ViewGroup v, int accentColor) {}

    public void presentThumbnail(ViewGroup v) {}
}
