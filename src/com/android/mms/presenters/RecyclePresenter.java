package com.android.mms.presenters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import com.android.mms.ui.Presenter;

import java.lang.reflect.ParameterizedType;

@SuppressWarnings({"unchecked", "ConstantConditions"})
public abstract class RecyclePresenter<V extends RecyclePresenter.RecyclePresenterInterface, M> extends Presenter<M> {

    protected Class<V> mViewClass;

    public interface RecyclePresenterInterface {
        View getView();
    }
    public RecyclePresenter(Context context, M modelInterface) {
        super(context, modelInterface);
        ParameterizedType parameterizedType = (ParameterizedType) getClass().getGenericSuperclass();
        mViewClass = (Class<V>)  parameterizedType.getActualTypeArguments()[0];
    }

    @Override
    public final View present(ViewGroup parent, PresenterOptions presenterOptions) {
        V view = getRecycledView(parent);
        if (view == null) {
            // Add view
            view = inflateView(presenterOptions);
            parent.addView(view.getView());
        }
        bindView(view, presenterOptions);
        return view.getView();
    }

    @Override
    public final void presentThumbnail(ViewGroup parent, AttachmentPresenterOptions presenterOptions) {
        super.presentThumbnail(parent, presenterOptions);
        V view = getRecycledView(parent);
        if (view == null) {
            // Add view
            view = inflateAttachmentView(presenterOptions);
            parent.addView(view.getView());
        }
        bindAttachmentView(view, presenterOptions);
    }

    private V getRecycledView(ViewGroup parent) {
        V view = null;
        if (parent.getChildCount() == 1 && parent.getChildAt(0)
                .getClass().isAssignableFrom(mViewClass)) {
            // View was recycled, lets re-use it
            view = (V) parent.getChildAt(0);
        }
        return view;
    }

    protected abstract V inflateView(PresenterOptions presenterOptions);
    protected abstract void bindView(V view, PresenterOptions presenterOptions);
    protected abstract V inflateAttachmentView(AttachmentPresenterOptions presenterOptions);
    protected abstract void bindAttachmentView(V view, AttachmentPresenterOptions presenterOptions);
}
