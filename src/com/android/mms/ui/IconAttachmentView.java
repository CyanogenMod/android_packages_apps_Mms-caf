package com.android.mms.ui;

import com.android.mms.R;
import com.android.mms.model.SlideModel;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

public abstract class IconAttachmentView extends AttachmentView {

    private ImageView mImageView;

    public IconAttachmentView(SlideModel slide, Context context) {
        super(slide, context);
    }

    @Override
    public final void setupView(ViewGroup root) {
        View.inflate(root.getContext(), R.layout.icon_attachment_view, root);
        mImageView = (ImageView) root.findViewById(R.id.icon_attachment);
        root.setBackgroundResource(R.drawable.attachment_blank);
        setIcon();
    }

    public ImageView getIcon() {
        return mImageView;
    }

    public abstract void setIcon();
}
