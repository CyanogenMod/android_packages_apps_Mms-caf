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

    @Override
    public void setImage(Bitmap bitmap) {
        ((ImageView)findViewById(R.id.icon)).setImageBitmap(bitmap);
    }

    @Override
    public void setImage(Drawable drawable) {
        ((ImageView)findViewById(R.id.icon)).setImageDrawable(drawable);
    }

    @Override
    public void setLabel1(String label) {
        ((TextView)findViewById(R.id.label1)).setText(label);
    }

    @Override
    public void setLabel2(String label) {
        ((TextView)findViewById(R.id.label2)).setText(label);
    }
}
