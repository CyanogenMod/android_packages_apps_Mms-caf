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
    public final void present(ViewGroup parent, PresenterOptions presenterOptions) {
        super.present(parent, presenterOptions);
        V view;
        if (parent.getChildCount() == 1 && parent.getChildAt(0)
                .getClass().isAssignableFrom(mViewClass)) {
            // View was recycled, lets re-use it
            view = (V) parent.getChildAt(0);
        } else {
            // Remove any existing children
            parent.removeAllViews();
            parent.removeAllViewsInLayout();

            // Add view
            view = inflateView(presenterOptions);
            parent.addView(view.getView());
        }
        bindView(view, presenterOptions);
    }

    protected abstract V inflateView(PresenterOptions presenterOptions);
    protected abstract void bindView(V view, PresenterOptions presenterOptions);

    @Override
    public final void presentThumbnail(ViewGroup v, PresenterOptions presenterOptions) {
        super.presentThumbnail(v, presenterOptions);
        present(v, presenterOptions);
    }
}
