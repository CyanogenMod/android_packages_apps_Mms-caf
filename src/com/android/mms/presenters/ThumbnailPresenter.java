package com.android.mms.presenters;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import com.android.mms.R;
import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.RoundedBitmapStateWrapper;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ThumbnailManager;
import com.android.mms.views.ThumbnailMessageView;

@SuppressWarnings("ConstantConditions")
public abstract class ThumbnailPresenter<V extends ThumbnailMessageView, M extends ThumbnailPresenterModel> extends
        RecyclePresenter<V, M> implements OnClickListener {

    private final int mCornerRadius;
    private V mView;

    public ThumbnailPresenter(Context context, M modelInterface) {
        super(context, modelInterface);
        mCornerRadius = context.getResources()
                .getDimensionPixelOffset(R.dimen.thumbnail_attachment_corner_radius);
    }

    public abstract V getView(PresenterOptions presenterOptions);
    public abstract V getAttachmentView(AttachmentPresenterOptions presenterOptions);

    @Override
    protected final V inflateView(ViewGroup parent, final PresenterOptions presenterOptions) {
        V view = getView(presenterOptions);
        view.setElevation(5);
        if (!presenterOptions.isInActionMode()) {
            view.setOnClickListener(this);
            view.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    presenterOptions.onItemLongClick();
                    return true;
                }
            });
        }
        mView = view;
        return view;
    }

    @Override
    protected final void bindView(final V viewInterface, final PresenterOptions presenterOptions) {
        if (presenterOptions != null) {
            int color = MessageUtils.getDarkerColor(presenterOptions.getAccentColor());
            color = MessageUtils.getColorAtAlpha(color, 0.29f);
            viewInterface.setSelectColor(color);
        }
        getModel().loadThumbnailBitmap(new ItemLoadedCallback<ThumbnailManager.ImageLoaded>() {
            @Override
            public void onItemLoaded(ThumbnailManager.ImageLoaded result, Throwable exception) {
                Bitmap bitmap = result.mBitmap;
                if (bitmap != null) {
                    RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory
                            .create(getContext().getResources(), bitmap);
                    drawable.setCornerRadius(mCornerRadius);
                    RoundedBitmapStateWrapper roundedBitmapStateWrapper =
                            new RoundedBitmapStateWrapper(drawable);
                    viewInterface.setImage(roundedBitmapStateWrapper);
                    presenterOptions.donePresenting(bitmap.getHeight());
                }
            }
        }, presenterOptions.getMaxWidth());
    }

    @Override
    protected final V inflateAttachmentView(AttachmentPresenterOptions presenterOptions) {
        V view = getAttachmentView(presenterOptions);
        ViewGroup.MarginLayoutParams layoutParams = new ViewGroup.MarginLayoutParams(
                presenterOptions.getAttachmentWidth(), presenterOptions.getAttachmentHeight());
        view.setLayoutParams(layoutParams);
        mView = view;
        return view;
    }

    @Override
    protected final void bindAttachmentView(final V view, AttachmentPresenterOptions presenterOptions) {
        getModel().loadThumbnailBitmap(new ItemLoadedCallback<ThumbnailManager.ImageLoaded>() {
            @Override
            public void onItemLoaded(ThumbnailManager.ImageLoaded result, Throwable exception) {
                view.setImage(new BitmapDrawable(result.mBitmap));
            }
        }, ThumbnailManager.THUMBNAIL_SIZE);
    }

    @Override
    public boolean hideArrowHead() {
        return true;
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // So we don't see "surrounding" items in Gallery
        intent.putExtra("SingleItemOnly", true);
        String contentType;
        contentType = getModel().getContentType();
        intent.setDataAndType(getModel().getUri(), contentType);
        getContext().startActivity(intent);
    }

    @Override
    public void unbind() {
        getModel().cancelThumbnailLoading();
        if (mView != null) {
            mView.setOnClickListener(null);
            mView.setOnLongClickListener(null);
            mView.setImage(null);
        }
    }
}
