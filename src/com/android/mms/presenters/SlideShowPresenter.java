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

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class SlideShowPresenter extends Presenter<SlideshowModel> {

    private SlideShowPresenterOptions mPresenterOptions;

    public SlideShowPresenter(Context context, SlideshowModel slideshowModel) {
        super(context, slideshowModel);
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
    public void unbind() {
        // Lose the reference
        if (mPresenterOptions != null) {
            mPresenterOptions.unBind();
            mPresenterOptions = null;
        }

        // Tell presenters to unbind
        for (MediaModel i : getModel()) {
            if (i.getPresenter() != null) {
                i.getPresenter().unbind();
            }
        }
    }

    @Override
    public View present(ViewGroup v, PresenterOptions presenterOptions) {
        throw new UnsupportedOperationException("Call present slides !");
    }

    private static class SlideShowPresenterOptions extends PresenterOptions {
        private final boolean mShouldRecycle;
        private ArrayList<View> mViewCache;
        private MessageListItem mMessageListItem;
        private AtomicInteger mPresenterDoneCount, mTotalHeight;

        SlideShowPresenterOptions(MessageListItem messageListItem, int slideCount) {
            mMessageListItem = messageListItem;
            mTotalHeight = new AtomicInteger(0);
            mShouldRecycle = slideCount == 1;
            mPresenterDoneCount = new AtomicInteger(slideCount);
        }

        @Override
        public <T> T getCachedView(Class<T> c) {
            View view = null;
            for (View v : mViewCache) {
                if (v.getClass().equals(c)) {
                    view = v;
                    break;
                }
            }
            if (view != null) {
                mViewCache.remove(view);
            }
            return (T) view;
        }

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
            if (mMessageListItem != null) {
                int current = mPresenterDoneCount.decrementAndGet();
                mTotalHeight.addAndGet(height);
                if (current == 0) {
                    mMessageListItem.getMessageInfoCache()
                            .addMessageItemInfo(getMessageId(), mTotalHeight.get());
                }
            }
        }

        @Override
        public int getMaxWidth() {
            if (mMessageListItem != null) {
                return mMessageListItem.getMessageInfoCache().getMaxItemWidth();
            } else {
                return -1;
            }
        }

        @Override
        public boolean isIncomingMessage() {
            if (mMessageListItem != null) {
                return mMessageListItem.isIncoming();
            } else {
                return false;
            }
        }

        @Override
        public int getAccentColor() {
            if (mMessageListItem != null) {
                return mMessageListItem.getAccentColor();
            } else {
                return -1;
            }
        }

        @Override
        public boolean shouldRecycle() {
            return mShouldRecycle;
        }

        @Override
        public boolean isInActionMode() {
            if (mMessageListItem != null) {
                return mMessageListItem.isInActionMode();
            } else {
                return false;
            }
        }

        private void unBind() {
            mMessageListItem = null;
            if (mViewCache != null) {
                mViewCache.clear();
            }
        }

        public void populateViewCache(ViewGroup parent) {
            mViewCache = new ArrayList<View>(parent.getChildCount());
            for (int i = 0; i < parent.getChildCount(); i++) {
                View v = parent.getChildAt(i);
                mViewCache.add(v);
            }
            parent.removeAllViews();
            parent.removeAllViewsInLayout();
        }

        public void evictViewCache() {
            if (mViewCache != null) {
                mViewCache.clear();
            }
        }
    }

    @Override
    public void presentThumbnail(ViewGroup v, AttachmentPresenterOptions presenterOptions) {
        throw new UnsupportedOperationException("Attachments should never have slideshows");
    }

    public void presentSlides(MessageListItem messageListItem, ViewGroup parent) {
        mPresenterOptions = new SlideShowPresenterOptions(
                messageListItem, getModel().size());
        if (getModel().isSimple()) {
            // Ensure recycling occurs for simple models
            MediaModel firstModel = getModel().get(0);
            firstModel.getPresenter().present(parent, mPresenterOptions);
        } else {
            mPresenterOptions.populateViewCache(parent);
            for (int c = 0; c < getModel().size(); c++) {
                MediaModel i = getModel().get(c);
                Presenter presenter = i.getPresenter();

                if (c == 0 && presenter.hideArrowHead()
                        && messageListItem.isBubbleArrowheadVisibile()) {
                    messageListItem.setBubbleArrowheadVisibility(View.INVISIBLE);
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
                layoutParams.gravity = mPresenterOptions.isIncomingMessage() ?
                        Gravity.START : Gravity.END;
                view.setLayoutParams(layoutParams);
            }
            mPresenterOptions.evictViewCache();
        }
    }
}
