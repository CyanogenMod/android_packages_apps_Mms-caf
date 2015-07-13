package com.android.mms.presenters;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.android.mms.model.MediaModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.model.TextModel;
import com.android.mms.ui.Presenter;

import java.util.concurrent.atomic.AtomicInteger;

public class SlideShowPresenter extends Presenter<SlideshowModel> {

    private PresenterOptions.Callback mOriginalCallback;
    private AtomicInteger mPresenterDoneCount, mTotalHeight;

    public SlideShowPresenter(Context context, SlideshowModel slideshowModel) {
        super(context, slideshowModel);
    }

    @Override
    public boolean showsArrowHead() {
        if (!getModel().isEmpty() && getModel().get(0).getPresenter() != null) {
            return getModel().get(0).getPresenter().showsArrowHead();
        } else {
            return super.showsArrowHead();
        }
    }
    @Override
    public void cancelBackgroundLoading() {
        for (MediaModel i : getModel()) {
            if (i.getPresenter() != null) {
                i.getPresenter().cancelBackgroundLoading();
            }
        }
    }

    @Override
    public void present(ViewGroup v, PresenterOptions presenterOptions) {
        mOriginalCallback = presenterOptions.getCallback();
        presenterOptions.setCallback(mDelegateCallback);
        mTotalHeight = new AtomicInteger(0);
        mPresenterDoneCount = new AtomicInteger(getModel().size());
        System.out.println("Model size " + getModel().size());
        if (getModel().isSimple()) {
            // Ensure recycling occurs for simple models
            MediaModel firstModel = getModel().get(0);
            firstModel.getPresenter().present(v, presenterOptions);
        } else {
            // Remove any existing children
            v.removeAllViews();
            v.removeAllViewsInLayout();
            for (MediaModel i : getModel()) {
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams
                        (ViewGroup.LayoutParams.MATCH_PARENT, 0);
                LinearLayout container = new LinearLayout(getContext());
                container.setClipToPadding(false);
                if (i instanceof TextModel) {
                    layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    layoutParams.weight = 0;
                } else {
                    layoutParams.weight = 1;
                    layoutParams.height = 0;
                }
                //container.setPadding(20, 20, 20, 20);
                container.setGravity(presenterOptions.isIncomingMessage() ? Gravity.START : Gravity.END);
                v.addView(container, layoutParams);
                if (i.getPresenter() != null) {
                    i.getPresenter().present(container, presenterOptions);
                }
            }
        }
    }

    private PresenterOptions.Callback mDelegateCallback = new PresenterOptions.Callback() {
        @Override
        public void onItemLongClick() {
            if (mOriginalCallback != null) {
                mOriginalCallback.onItemLongClick();
            }
        }

        @Override
        public long getMessageId() {
            if (mOriginalCallback != null) {
                return mOriginalCallback.getMessageId();
            }
            return 0;
        }

        @Override
        public void donePresenting(int height) {
            int current = mPresenterDoneCount.decrementAndGet();
            mTotalHeight.addAndGet(height);
            System.out.println("donePresenting " + height + " " + mTotalHeight.get() + " " + current);
            if (current == 0 && mOriginalCallback != null) {
                mOriginalCallback.donePresenting(mTotalHeight.get());
            }
        }
    };

    @Override
    public void presentThumbnail(ViewGroup v, PresenterOptions presenterOptions) {
        throw new UnsupportedOperationException("Attachments should never have slideshows");
    }
}
