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
import android.util.Log;
import com.android.mms.LogTag;
import com.android.mms.MmsConfig;
import com.android.mms.model.*;
import com.google.android.mms.MmsException;

/**
 * An utility to edit contents of a slide.
 */
public class SlideshowEditor {
    private static final String TAG = LogTag.TAG;

    private final Context mContext;
    private SlideshowModel mModel;

    public SlideshowEditor(Context context, SlideshowModel model) {
        mContext = context;
        mModel = model;
    }

    public void setSlideshow(SlideshowModel model) {
        mModel = model;
    }

    /**
     * Add a new slide to the end of message.
     *
     * @return true if success, false if reach the max slide number.
     */
    public boolean addNewSlide() {
        int position = mModel.size();
        return addNewSlide(position);
    }

    /**
     * Add a new slide at the specified position in the message.
     *
     * @return true if success, false if reach the max slide number.
     * @throws IndexOutOfBoundsException - if position is out of range
     *         (position < 0 || position > size()).
     */
    public boolean addNewSlide(int position) {
        int size = mModel.size();
        if (size < MmsConfig.getMaxSlideNumber()) {
            SlideModel slide = new SlideModel(mModel);

//            TextModel text = new TextModel(
//                    mContext, ContentType.TEXT_PLAIN, generateTextSrc(mModel, size),
//                    mModel.getLayout().getTextRegion());
//            slide.add(text);

            mModel.add(position, slide);
            return true;
        } else {
            Log.w(TAG, "The limitation of the number of slides is reached.");
            return false;
        }
    }

    /**
     * Add an existing slide at the specified position in the message.
     *
     * @return true if success, false if reach the max slide number.
     * @throws IndexOutOfBoundsException - if position is out of range
     *         (position < 0 || position > size()).
     */
    public boolean addSlide(int position, SlideModel slide) {
        int size = mModel.size();
        if (size < MmsConfig.getMaxSlideNumber()) {
            mModel.add(position, slide);
            return true;
        } else {
            Log.w(TAG, "The limitation of the number of slides is reached.");
            return false;
        }
    }

    /**
     * Remove one slide.
     *
     * @param position
     */
    public void removeSlide(int position) {
        mModel.remove(position);
    }

    /**
     * Remove all slides.
     */
    public void removeAllSlides() {
        while (mModel.size() > 0) {
            removeSlide(0);
        }
    }

    public void changeImage(int position, Uri newImage) throws MmsException {
        mModel.get(position).add(new ImageModel(
                mContext, newImage, mModel.getLayout().getImageRegion()));
    }

    public void changeAudio(int position, Uri newAudio) throws MmsException {
        AudioModel audio = new AudioModel(mContext, newAudio);
        SlideModel slide = mModel.get(position);
        slide.add(audio);
        slide.updateDuration(audio.getDuration());
    }

    public void changeVideo(int position, Uri newVideo) throws MmsException {
        VideoModel video = new VideoModel(mContext, newVideo,
                mModel.getLayout().getImageRegion());
        SlideModel slide = mModel.get(position);
        slide.add(video);
        slide.updateDuration(video.getDuration());
    }

    public void changeVcard(int position, Uri newVcard) throws MmsException {
        VcardModel vCard = new VcardModel(mContext, newVcard);
        SlideModel slide = mModel.get(position);
        slide.add(vCard);
        slide.updateDuration(vCard.getDuration());
    }

    public void changeVCal(int position, Uri newIcal) throws MmsException {
        VCalModel vcal = new VCalModel(mContext, newIcal);
        SlideModel slide = mModel.get(position);
        slide.add(vcal);
        slide.updateDuration(vcal.getDuration());
    }

    public int getDuration(int position) {
        return mModel.get(position).getDuration();
    }
}
