package com.android.mms.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
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
    public void setIconTint(int color) {
        ImageView icon = getIcon();
        if (icon != null) {
            icon.setColorFilter(color);
        }
    }

    @Override
    public void setTitle(String label) {
        TextView title = getTitle();
        if (title != null) {
            title.setText(label);
        }
    }

    @Override
    public void setTitleColor(int color) {
        TextView title = getTitle();
        if (title != null) {
            title.setTextColor(color);
        }
    }

    @Override
    public void setSubTitle(String label) {
        TextView subTitle = getSubTitle();
        if (subTitle != null) {
            subTitle.setText(label);
        }
    }

    @Override
    public void setSubTitleColor(int color) {
        TextView subTitle = getSubTitle();
        if (subTitle != null) {
            subTitle.setTextColor(color);
        }
    }

    @Override
    public View getView() {
        return this;
    }
}
