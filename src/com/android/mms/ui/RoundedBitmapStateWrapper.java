package com.android.mms.ui;

import android.graphics.drawable.Drawable;

public class RoundedBitmapStateWrapper extends DrawableWrapper implements Drawable.Callback {

    public RoundedBitmapStateWrapper(Drawable drawable) {
        super(drawable);
    }

    @Override
    public ConstantState getConstantState() {
        return new ConstantState() {
            @Override
            public Drawable newDrawable() {
                return RoundedBitmapStateWrapper.this;
            }

            @Override
            public int getChangingConfigurations() {
                return 0;
            }
        };
    }
}
