package com.android.mms.ui;

import java.util.Map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.mms.R;
import com.android.mms.model.SlideModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ThumbnailManager.ImageLoaded;

public class SlideShowPreview extends LinearLayout implements SlideViewInterface {

    public SlideShowPreview(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        // TODO Auto-generated constructor stub
    }

    public SlideShowPreview(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        // TODO Auto-generated constructor stub
    }

    public SlideShowPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        // TODO Auto-generated constructor stub
    }

    public SlideShowPreview(Context context) {
        super(context);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void reset() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setVisibility(boolean visible) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setImage(String name, Bitmap bitmap) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setImageRegionFit(String fit) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setImageVisibility(boolean visible) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setVideo(String name, Uri video) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setVideoThumbnail(String name, Bitmap bitmap) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setVideoVisibility(boolean visible) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void startVideo() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void stopVideo() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void pauseVideo() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void seekVideo(int seekTo) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setAudio(Uri audio, String name, Map<String, ?> extras) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void startAudio() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void stopAudio() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void pauseAudio() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void seekAudio(int seekTo) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setText(String name, String text) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setTextVisibility(boolean visible) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setVcard(Uri lookupUri, String name) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void setVCal(Uri vcalUri, String name) {
        // TODO Auto-generated method stub
        
    }

    public void setPresenter(Presenter mPresenter, SlideshowModel slideshow) {
        removeAllViews();
        for (final SlideModel slide : slideshow) {
            final ImageView img = new ImageView(getContext());
            addView(img, new LinearLayout.LayoutParams(400, 400));
            mPresenter.present(this, slide, new ItemLoadedCallback<ImageLoaded>() {
                @Override
                public void onItemLoaded(ImageLoaded imageLoaded, Throwable exception) {
                    if (exception == null) {
                        img.setImageBitmap(imageLoaded.mBitmap);
                    } else {
                        exception.printStackTrace();
                    }
                }
            });
        }
    }

}
