package com.android.mms.presenters;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.android.mms.R;
import com.android.mms.model.MediaModel;
import com.android.mms.model.SlideshowModel;
import com.android.mms.model.TextModel;
import com.android.mms.ui.MessageListItem;
import com.android.mms.ui.Presenter;

import java.util.concurrent.atomic.AtomicInteger;

public class SlideShowPresenter extends Presenter<SlideshowModel> {

    private AtomicInteger mPresenterDoneCount, mTotalHeight;
    private MessageListItem mMessageListItem;

    public SlideShowPresenter(Context context, SlideshowModel slideshowModel) {
        super(context, slideshowModel);
        mTotalHeight = new AtomicInteger(0);
        mPresenterDoneCount = new AtomicInteger(0);
    }

    @Override
    public boolean hideArrowHead() {
        if (!getModel().isEmpty() && getModel().get(0).getPresenter() != null) {
            return getModel().get(0).getPresenter().hideArrowHead();
        } else {
            return super.hideArrowHead();
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
    public View present(ViewGroup v, PresenterOptions presenterOptions) {
        throw new UnsupportedOperationException("Call present slides !");
    }

    private PresenterOptions mPresenterOptions = new PresenterOptions() {
        @Override
        public void onItemLongClick() {
            mMessageListItem.onItemLongClick();
        }

        @Override
        public long getMessageId() {
            return mMessageListItem.getMessageItem().getMessageId();
        }

        @Override
        public void donePresenting(int height) {
            int current = mPresenterDoneCount.decrementAndGet();
            mTotalHeight.addAndGet(height);
            if (current == 0) {
                mMessageListItem.getMessageInfoCache()
                        .addMessageItemInfo(getMessageId(), mTotalHeight.get());
            }
        }

        @Override
        public int getMaxWidth() {
            return mMessageListItem.getMessageInfoCache().getMaxItemWidth();
        }

        @Override
        public boolean isIncomingMessage() {
            return mMessageListItem.isIncoming();
        }

        @Override
        public int getAccentColor() {
            return mMessageListItem.getAccentColor();
        }
    };

    @Override
    public void presentThumbnail(ViewGroup v, AttachmentPresenterOptions presenterOptions) {
        throw new UnsupportedOperationException("Attachments should never have slideshows");
    }

    public void setMessageListItem(MessageListItem messageListItem) {
        mMessageListItem = messageListItem;
    }

    public void presentSlides(ViewGroup parent) {
        mTotalHeight.set(0);
        mPresenterDoneCount.set(getModel().size());
        if (getModel().isSimple()) {
            // Ensure recycling occurs for simple models
            MediaModel firstModel = getModel().get(0);
            firstModel.getPresenter().present(parent, mPresenterOptions);
        } else {
            // Remove any existing children
            parent.removeAllViews();
            parent.removeAllViewsInLayout();

            for (int c = 0; c < getModel().size(); c++) {
                MediaModel i = getModel().get(c);
                Presenter presenter = i.getPresenter();

                if (c == 0 && presenter.hideArrowHead()
                        && mMessageListItem.isBubbleArrowheadVisibile()) {
                    mMessageListItem.setBubbleArrowheadVisibility(View.INVISIBLE);
                }

                View view = presenter.present(parent, mPresenterOptions);
                LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) view.getLayoutParams();
                if (i instanceof TextModel || presenter instanceof SimpleAttachmentPresenter) {
                    layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    layoutParams.weight = 0;
                } else {
                    layoutParams.weight = 1;
                    layoutParams.height = 0;
                }
                layoutParams.gravity = mPresenterOptions.isIncomingMessage() ? Gravity.START : Gravity.END;
                view.setLayoutParams(layoutParams);
            }
        }
    }
}
