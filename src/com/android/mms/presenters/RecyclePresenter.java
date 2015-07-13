package com.android.mms.presenters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import com.android.mms.ui.Presenter;

import java.lang.reflect.ParameterizedType;

@SuppressWarnings({"unchecked", "ConstantConditions"})
public abstract class RecyclePresenter<V extends View, M> extends Presenter<M> {

    public RecyclePresenter(Context context, M modelInterface) {
        super(context, modelInterface);
    }

    @Override
    public final View present(ViewGroup parent, PresenterOptions presenterOptions) {
        V view = null;
        Class<V> recycleClass = getViewClass();
        if (presenterOptions.shouldRecycle()) {
            // Only try to recycle if parent has one child
            if (parent.getChildCount() == 1) {
                Class viewClass = parent.getChildAt(0).getClass();
                if (viewClass.equals(recycleClass)) {
                    // View was recycled, lets re-use it
                    view = (V) parent.getChildAt(0);
                }
            }
        } else {
            view = presenterOptions.getCachedView(recycleClass);
            if (view != null) {
                parent.addView(view);
            }
        }

        if (view == null) {
            // Add view
            view = inflateView(parent, presenterOptions);
            parent.addView(view);
        }

        bindView(view, presenterOptions);
        return view;
    }

    @Override
    public final void presentThumbnail(ViewGroup parent, AttachmentPresenterOptions presenterOptions) {
        super.presentThumbnail(parent, presenterOptions);
        V view = inflateAttachmentView(presenterOptions);
        parent.addView(view);
        bindAttachmentView(view, presenterOptions);
    }

    protected abstract Class<V> getViewClass();
    protected abstract V inflateView(ViewGroup parent, PresenterOptions presenterOptions);
    protected abstract void bindView(V view, PresenterOptions presenterOptions);
    protected abstract V inflateAttachmentView(AttachmentPresenterOptions presenterOptions);
    protected abstract void bindAttachmentView(V view, AttachmentPresenterOptions presenterOptions);
}
