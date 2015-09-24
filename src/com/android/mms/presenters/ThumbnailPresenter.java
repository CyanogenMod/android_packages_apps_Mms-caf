package com.android.mms.presenters;

import com.android.mms.R;
import com.android.mms.ui.MessageUtils;
import com.android.mms.ui.RoundedBitmapStateWrapper;
import com.android.mms.util.ItemLoadedCallback;
import com.android.mms.util.ThumbnailManager;
import com.android.mms.views.ThumbnailMessageView;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v4.graphics.drawable.RoundedBitmapDrawableFactory;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import java.lang.ref.WeakReference;

@SuppressWarnings("ConstantConditions")
public abstract class ThumbnailPresenter<V extends ThumbnailMessageView, M extends ThumbnailPresenterModel> extends
        RecyclePresenter<V, M> implements OnClickListener {

    private final int mCornerRadius;

    public ThumbnailPresenter(Context context, M modelInterface) {
        super(context, modelInterface);
        mCornerRadius = context.getResources()
                .getDimensionPixelOffset(R.dimen.thumbnail_attachment_corner_radius);
    }

    @Override
    protected final void bindMessageAttachmentView(final V viewInterface, final PresenterOptions presenterOptions) {
        viewInterface.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        viewInterface.setLayoutParams(layoutParams);
        viewInterface.setElevation(5);
        if (!presenterOptions.isInActionMode()) {
            viewInterface.setOnClickListener(this);
            viewInterface.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    presenterOptions.onItemLongClick();
                    return true;
                }
            });
        }
        int color = MessageUtils.getDarkerColor(presenterOptions.getAccentColor());
        color = MessageUtils.getColorAtAlpha(color, 0.29f);
        viewInterface.setSelectColor(color);
        getModel().loadThumbnailBitmap(new ItemLoadedCallback<ThumbnailManager.ImageLoaded>() {
            @Override
            public void onItemLoaded(ThumbnailManager.ImageLoaded result, Throwable exception) {
                Bitmap bitmap = result.mBitmap;
                if (bitmap != null) {
                    RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory
                            .create(getContext().getResources(), bitmap);
                    drawable.setAntiAlias(true);
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
    protected final void bindPreviewAttachmentView(final V view, AttachmentPresenterOptions presenterOptions) {
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                presenterOptions.getAttachmentWidth(), presenterOptions.getAttachmentHeight());
        view.setLayoutParams(layoutParams);
        view.setOnClickListener(this);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
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
    public void unbindView(V view) {
        getModel().cancelThumbnailLoading();
        if (view != null) {
            view.setOnClickListener(null);
            view.setClickable(false);
            view.setOnLongClickListener(null);
            view.setLongClickable(false);
            // TODO This is done so in action mode when the view is
            // unbound we don't see a flicker due to image -> null -> image
//            view.setImage(null);
        }
    }
}
