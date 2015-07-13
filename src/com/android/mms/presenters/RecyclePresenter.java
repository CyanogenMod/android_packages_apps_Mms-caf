package com.android.mms.presenters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import com.android.mms.ui.Presenter;

import java.lang.reflect.ParameterizedType;

@SuppressWarnings({"unchecked", "ConstantConditions"})
public abstract class RecyclePresenter<V, M> extends Presenter<V, M> {

    protected Class<M> mModelClass;
    protected Class<V> mViewClass;

    public RecyclePresenter(Context context, M modelInterface) {
        super(context, modelInterface);
        ParameterizedType parameterizedType = (ParameterizedType) getClass().getGenericSuperclass();
        mModelClass = (Class<M>) parameterizedType.getActualTypeArguments()[0];
        mViewClass = (Class<V>)  parameterizedType.getActualTypeArguments()[1];
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
            parent.addView((View) view);
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
