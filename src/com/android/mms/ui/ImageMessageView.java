package com.android.mms.ui;

import com.android.mms.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import java.util.Map;

public class ImageMessageView extends RelativeLayout implements SlideViewInterface {
    public ImageMessageView(Context context) {
        super(context);
    }

    public ImageMessageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageMessageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ImageMessageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void setImage(String name, Bitmap bitmap) {
        ((ImageView)findViewById(R.id.image)).setImageBitmap(bitmap);
    }

    @Override
    public void setImageRegionFit(String fit) {

    }

    @Override
    public void setImageVisibility(boolean visible) {

    }

    @Override
    public void setVideo(String name, Uri video) {

    }

    @Override
    public void setVideoThumbnail(String name, Bitmap bitmap) {

    }

    @Override
    public void setVideoVisibility(boolean visible) {

    }

    @Override
    public void startVideo() {

    }

    @Override
    public void stopVideo() {

    }

    @Override
    public void pauseVideo() {

    }

    @Override
    public void seekVideo(int seekTo) {

    }

    @Override
    public void setAudio(Uri audio, String name, Map<String, ?> extras) {

    }

    @Override
    public void startAudio() {

    }

    @Override
    public void stopAudio() {

    }

    @Override
    public void pauseAudio() {

    }

    @Override
    public void seekAudio(int seekTo) {

    }

    @Override
    public void setText(String name, String text) {

    }

    @Override
    public void setTextVisibility(boolean visible) {

    }

    @Override
    public void setVcard(Uri lookupUri, String name) {

    }

    @Override
    public void setVCal(Uri vcalUri, String name) {

    }

    @Override
    public void reset() {

    }

    @Override
    public void setVisibility(boolean visible) {

    }
}
