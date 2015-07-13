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
import com.android.mms.*;
import com.android.mms.model.*;

import com.google.android.mms.ContentType;
import com.google.android.mms.MmsException;
import static com.android.mms.data.WorkingMessage.*;
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
    public int addNewSlide(int type, Uri uri) {
        int position = mModel.size();
        return addNewSlide(position, type, uri);
    }

    /**
     * Add a new slide at the specified position in the message.
     *
     * @return true if success, false if reach the max slide number.
     * @throws IndexOutOfBoundsException - if position is out of range
     *         (position < 0 || position > size()).
     */
    private int addNewSlide(int position, int type, Uri uri) {
        int size = mModel.size();
        if (size >= MmsConfig.getMaxSlideNumber()) {
            Log.w(TAG, "The limitation of the number of slides is reached.");
            return MESSAGE_SIZE_EXCEEDED;
        }

        int result = OK;
        try {
            MediaModel model;
            if (type == IMAGE) {
                model = new ImageModel(mContext, uri,
                        mModel.getLayout().getImageRegion());
            } else if (type == VIDEO) {
                model = new VideoModel(mContext, uri,
                        mModel.getLayout().getImageRegion());
            } else if (type == AUDIO) {
                model = new AudioModel(mContext, uri);
            } else if (type == VCARD) {
                model = new VcardModel(mContext, uri);
            } else if (type == VCAL) {
                model = new VCalModel(mContext, uri);
            } else {
                throw new UnsupportContentTypeException();
            }
            mModel.add(position, model);
        } catch (MmsException e) {
            Log.e(TAG, "internalChangeMedia:", e);
            result = UNKNOWN_ERROR;
        } catch (UnsupportContentTypeException e) {
            Log.e(TAG, "internalChangeMedia:", e);
            result = UNSUPPORTED_TYPE;
        } catch (ExceedMessageSizeException e) {
            Log.e(TAG, "internalChangeMedia:", e);
            result = MESSAGE_SIZE_EXCEEDED;
        } catch (ResolutionException e) {
            Log.e(TAG, "internalChangeMedia:", e);
            result = IMAGE_TOO_LARGE;
        } catch (ContentRestrictionException e) {
            Log.e(TAG, "internalChangeMedia:", e);
            result = NEGATIVE_MESSAGE_OR_INCREASE_SIZE;
        }
        return result;
    }

    /**
     * Generate an unique source for TextModel
     *
     * @param slideshow The current slideshow model
     * @param position The expected position for the new model
     * @return An unique source String
     */
    private String generateTextSrc(SlideshowModel slideshow, int position) {
        final String prefix = "text_";
        final String postfix = ".txt";

        StringBuilder src = new StringBuilder(prefix).append(position).append(postfix);
        boolean hasDupSrc = false;

        do {
            for (MediaModel model : slideshow) {
                if (model instanceof TextModel) {
                    String testSrc = model.getSrc();

                    if (testSrc != null && testSrc.equals(src.toString())) {
                        src = new StringBuilder(prefix).append(position + 1).append(postfix);
                        hasDupSrc |= true;
                        break;
                    }
                }
                hasDupSrc = false;
            }
        } while (hasDupSrc);

        return src.toString();
    }

    /**
     * Add an existing slide at the specified position in the message.
     *
     * @return true if success, false if reach the max slide number.
     * @throws IndexOutOfBoundsException - if position is out of range
     *         (position < 0 || position > size()).
     */
    public boolean addSlide(int position, MediaModel slide) {
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

    public int getDuration(int position) {
        return mModel.get(position).getDuration();
    }
}
