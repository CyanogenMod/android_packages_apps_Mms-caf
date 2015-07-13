package com.android.mms.presenters;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.android.mms.R;
import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.Presenter;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ThumbnailManager;
import com.android.mms.views.ImageMessageView;

@SuppressWarnings("ConstantConditions")
public abstract class ThumbnailPresenter<V extends ThumbnailViewInterface, M extends ThumbnailPresenterModel> extends
        RecyclePresenter<V, M> implements OnClickListener {

    private final int mCornerRadius;

    public ThumbnailPresenter(Context context, M modelInterface) {
        super(context, modelInterface);
        mCornerRadius = context.getResources()
                .getDimensionPixelOffset(R.dimen.thumbnail_attachment_corner_radius);
    }

    public abstract V getView(PresenterOptions presenterOptions);
    public abstract V getAttachmentView(AttachmentPresenterOptions presenterOptions);

    @Override
    protected final V inflateView(final PresenterOptions presenterOptions) {
        V viewInterface = getView(presenterOptions);
        View view = viewInterface.getView();
        view.setElevation(5);
        view.setOnClickListener(this);
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // TODO Leaked reference ?
                presenterOptions.onItemLongClick();
                return true;
            }
        });
        return viewInterface;
    }

    @Override
    protected final void bindView(final V viewInterface, final PresenterOptions presenterOptions) {
        if (presenterOptions != null) {
            int color = MessageUtils.getDarkerColor(presenterOptions.getAccentColor());
            color = MessageUtils.getColorAtAlpha(color, 36);
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
                    viewInterface.setImage(drawable);
                    presenterOptions.donePresenting(bitmap.getHeight());
                }
            }
        }, presenterOptions.getMaxWidth());
    }

    @Override
    protected final V inflateAttachmentView(AttachmentPresenterOptions presenterOptions) {
        V viewInterface = getAttachmentView(presenterOptions);
        View view = viewInterface.getView();
        ViewGroup.MarginLayoutParams layoutParams = new ViewGroup.MarginLayoutParams(
                presenterOptions.getAttachmentWidth(), presenterOptions.getAttachmentHeight());
        view.setLayoutParams(layoutParams);
        //view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return viewInterface;
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
    public void cancelBackgroundLoading() {
        getModel().cancelThumbnailLoading();
    }
}
