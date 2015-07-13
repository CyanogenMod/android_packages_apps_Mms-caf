package com.android.mms.presenters;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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
        throw new UnsupportedOperationException("Call present slides !");
    }

    private PresenterOptions.Callback mDelegateCallback = new PresenterOptions.Callback() {
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
                        .addMessageItemInfo(getMessageId(), height);
            }
        }

        @Override
        public void hideArrowHead(MediaModel model) {
            int indexOfModel = getModel().indexOf(model);
            if (indexOfModel == 0 && mMessageListItem.isBubbleArrowheadVisibile()) {
                mMessageListItem.setBubbleArrowheadVisibility(View.INVISIBLE);
            }
        }
    };

    @Override
    public void presentThumbnail(ViewGroup v, PresenterOptions presenterOptions) {
        throw new UnsupportedOperationException("Attachments should never have slideshows");
    }

    public void setMessageListItem(MessageListItem messageListItem) {
        mMessageListItem = messageListItem;
    }

    private PresenterOptions getPresenterOptions() {
        Presenter.PresenterOptions presenterOptions = new Presenter.PresenterOptions();
        presenterOptions.setAccentColor(mMessageListItem.getAccentColor());
        presenterOptions.setIsIncomingMessage(mMessageListItem.isIncoming());
        presenterOptions.setCallback(mDelegateCallback);
        return presenterOptions;
    }

    public void presentSlides(ViewGroup parent) {
        mTotalHeight = new AtomicInteger(0);
        mPresenterDoneCount = new AtomicInteger(getModel().size());
        PresenterOptions presenterOptions = getPresenterOptions();
        if (getModel().isSimple()) {
            // Ensure recycling occurs for simple models
            MediaModel firstModel = getModel().get(0);
            firstModel.getPresenter().present(parent, presenterOptions);
        } else {
            // Remove any existing children
            parent.removeAllViews();
            parent.removeAllViewsInLayout();
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
                parent.addView(container, layoutParams);
                if (i.getPresenter() != null) {
                    i.getPresenter().present(container, presenterOptions);
                }
            }
        }

    }
}
