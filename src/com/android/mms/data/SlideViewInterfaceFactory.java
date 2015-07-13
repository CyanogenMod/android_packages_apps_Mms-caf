package com.android.mms.data;

import com.android.mms.model.SlideModel;
import com.android.mms.ui.AttachmentView;
import com.android.mms.ui.AudioAttachmentView;
import com.android.mms.ui.ImageAttachmentView;
import com.android.mms.ui.VCalAttachmentView;
import com.android.mms.ui.VcardAttachmentView;
import com.android.mms.ui.VideoAttachmentView;

import android.content.Context;

public class SlideViewInterfaceFactory {
    public static AttachmentView getViewForSlide(SlideModel slide, Context context) {
        if (slide.hasImage()) {
            return new ImageAttachmentView(slide, context);
        } else if (slide.hasVideo()) {
            return new VideoAttachmentView(slide, context);
        } else if (slide.hasAudio()) {
            return new AudioAttachmentView(slide, context);
        } else if (slide.hasVcard()) {
            return new VcardAttachmentView(slide, context);
        } else if (slide.hasVCal()) {
            return new VCalAttachmentView(slide, context);
        } else {
            throw new IllegalArgumentException();
        }
    }
}
