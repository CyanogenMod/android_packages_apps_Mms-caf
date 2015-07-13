package com.android.mms.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.mms.R;
import com.android.mms.presenters.SimpleViewInterface;

public class SimpleAttachmentView extends LinearLayout implements SimpleViewInterface {

    public SimpleAttachmentView(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public SimpleAttachmentView(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SimpleAttachmentView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SimpleAttachmentView(Context context) {
        super(context);
    }

    private ImageView getIcon() {
        return (ImageView) findViewById(R.id.icon);
    }

    private TextView getTitle() {
        return (TextView) findViewById(R.id.label1);
    }

    private TextView getSubTitle() {
        return (TextView) findViewById(R.id.label2);
    }

    @Override
    public void setIconBitmap(Bitmap bitmap) {
        getIcon().setImageBitmap(bitmap);
    }

    @Override
    public void setIconDrawable(Drawable drawable) {
        getIcon().setImageDrawable(drawable);
    }

    @Override
    public void setTitle(String label) {
        getTitle().setText(label);
    }

    @Override
    public void setTitleColor(int color) {
        getTitle().setTextColor(color);
    }

    @Override
    public void setSubTitle(String label) {
        getSubTitle().setText(label);
    }

    @Override
    public void setSubTitleColor
            (int color) {
        getSubTitle().setTextColor(color);
    }
}
