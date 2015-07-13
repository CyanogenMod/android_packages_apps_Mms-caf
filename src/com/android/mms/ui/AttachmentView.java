package com.android.mms.ui;

import com.android.mms.R;
import com.android.mms.model.SlideModel;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import java.util.Map;

public abstract class AttachmentView extends RelativeLayout implements SlideViewInterface {

    public final SlideModel mSlide;
    private View mRoot;
    private Handler mHandler;

    public AttachmentView(SlideModel slide, Context context) {
        super(context);
        mSlide = slide;
        inflateViews();
    }

    private void inflateViews() {
        // Attach base view
        LayoutInflater layoutInflater = LayoutInflater.from(getContext());
        mRoot = layoutInflater.inflate(R.layout.attachment_view, this, true);

        View remove = mRoot.findViewById(R.id.remove_attachment);
        remove.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mHandler != null) {
                    Message msg = mHandler.obtainMessage(AttachmentEditor.MSG_REMOVE_ATTACHMENT);
                    msg.obj = mSlide;
                    msg.sendToTarget();
                }
            }
        });

        ViewGroup thumbnail_root = (ViewGroup) mRoot.findViewById(R.id.thumbnail_root);
        thumbnail_root.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mHandler != null) {
                    Message msg = mHandler.obtainMessage(getViewMessageCode());
                    msg.obj = mSlide;
                    msg.sendToTarget();
                }
            }
        });
        setupView(thumbnail_root);
    }

    @Override
    protected final void onFinishInflate() {
        super.onFinishInflate();
    }

    public abstract void setupView(ViewGroup root);
    public abstract int getViewMessageCode();

    @Override
    public void setImage(String name, Bitmap bitmap) {

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

    public void setHandler(Handler handler) {
        mHandler = handler;
    }
}
