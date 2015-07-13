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
import android.net.Uri;
import android.text.TextUtils;

import com.android.mms.LogTag;
import com.android.mms.model.AudioModel;
import com.android.mms.model.VCalModel;
import com.android.mms.model.ImageModel;
import com.android.mms.model.Model;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.model.VcardModel;
import com.android.mms.model.VideoModel;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ThumbnailManager.ImageLoaded;

public class MmsThumbnailPresenter extends Presenter {
    private static final String TAG = LogTag.TAG;

    public MmsThumbnailPresenter(Context context) {
        super(context);
    }

    @Override
    public void present(SlideViewInterface view, SlideModel slide) {
        present(view, slide, null);
    }

    public void present(SlideViewInterface view, SlideModel slide, ItemLoadedCallback callback) {
        if (callback == null) {
            callback = new LoadedCallback(view, slide);
        }
        if (slide != null) {
            presentSlide(view, slide, callback);
        }
    }

    private void presentSlide(SlideViewInterface view, SlideModel slide, ItemLoadedCallback callback) {
        view.reset();

        if (slide.hasImage()) {
            System.out.println("Present image");
            presentImageThumbnail(slide.getImage(), callback);
        } else if (slide.hasVideo()) {
            presentVideoThumbnail(slide.getVideo(), callback);
        } else if (slide.hasAudio()) {
            presentAudioThumbnail(view, slide.getAudio(), callback);
        } else if (slide.hasVcard()) {
            presentVcardThumbnail(view, slide.getVcard(), callback);
        } else if (slide.hasVCal()) {
            presentVCalThumbnail(view, slide.getVCal(), callback);
        }
    }

    private static class LoadedCallback implements ItemLoadedCallback<ImageLoaded> {
        SlideModel mSlide;
        SlideViewInterface mView;
        LoadedCallback(SlideViewInterface view, SlideModel slide) {
            mSlide = slide;
            mView = view;
        }
        @Override
        public void onItemLoaded(ImageLoaded imageLoaded, Throwable exception) {
            if (exception == null) {
                if (mSlide != null) {
                    if (mSlide.hasVideo() && imageLoaded.mIsVideo) {
                        mView.setVideoThumbnail(null,
                                imageLoaded.mBitmap);
                    } else if (mSlide.hasImage() && !imageLoaded.mIsVideo) {
                        mView.setImage(null, imageLoaded.mBitmap);
                    }
                }
            }
        }
    }


    private void presentVideoThumbnail(VideoModel video, ItemLoadedCallback callback) {
        video.loadThumbnailBitmap(callback);
    }

    private void presentImageThumbnail(final ImageModel image, ItemLoadedCallback callback) {
        image.loadThumbnailBitmap(callback);
    }

    protected void presentAudioThumbnail(SlideViewInterface view, AudioModel audio, ItemLoadedCallback callback) {
        view.setAudio(audio.getUri(), audio.getSrc(), audio.getExtras());
    }

    protected void presentVcardThumbnail(SlideViewInterface view, VcardModel vcard, ItemLoadedCallback callback) {
        view.setVcard(
                TextUtils.isEmpty(vcard.getLookupUri()) ? null : Uri.parse(vcard.getLookupUri()),
                vcard.getSrc());
    }

    protected void presentVCalThumbnail(SlideViewInterface view, VCalModel vcalModel, ItemLoadedCallback callback) {
        view.setVCal(vcalModel.getUri(), vcalModel.getSrc());
    }

    public void onModelChanged(Model model, boolean dataChanged) {
        // TODO Auto-generated method stub
    }

    public void cancelBackgroundLoading() {
        // Currently we only support background loading of thumbnails. If we extend background
        // loading to other media types, we should add a cancelLoading API to Model.
//        SlideModel slide = ((SlideshowModel) mModel).get(0);
//        if (slide != null && slide.hasImage()) {
//            slide.getImage().cancelThumbnailLoading();
//        }
    }

}
