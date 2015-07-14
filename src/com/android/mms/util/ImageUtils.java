package com.android.mms.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.AsyncTask;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Script;
import android.renderscript.ScriptGroup;
import android.renderscript.ScriptIntrinsicBlend;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import com.android.mms.R;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Utility class around Image loading and Bitmap effects
 */
public class ImageUtils {

    private static boolean DEBUG = false;
    private static String TAG = ImageUtils.class.getSimpleName();

    public static interface BitmapLoadRequest extends Target {}

    public static void loadBitampFromUrl(Context ctx, String url, ImageView imageView) {
        Picasso.with(ctx)
                .load(url)
                .noPlaceholder()
                .fit()
                .transform(new RoundedTransformation(ctx))
                .into(imageView);
    }

    public static BitmapLoadRequest getBitmapFromUrl(Context ctx, String url,
            final ImageLoadCallback<Bitmap> cb) {
        BitmapLoadRequest t = new BitmapLoadRequest() {
            @Override
            public void onPrepareLoad(Drawable d){
            }
            @Override
            public void onBitmapLoaded(Bitmap result, Picasso.LoadedFrom from) {
                cb.onCompleted(null, result);
            }
            @Override
            public void onBitmapFailed(Drawable drawable) {
                cb.onCompleted(null, null);
            }
        };
        Picasso.with(ctx).load(url).into(t);
        return t;
    }

    public static void cancelRequest(Context ctx, BitmapLoadRequest request) {
        Picasso.with(ctx).cancelRequest(request);
    }

    public static void cancelRequest(ImageView contactImageSlot) {
        Picasso.with(contactImageSlot.getContext()).cancelRequest(contactImageSlot);
    }

    // doesn't offer much besides encapsulation of img loading library for easy swapping later
    public static abstract class ImageLoadCallback<T> {
        public abstract void onCompleted(Exception e, T result);
    }

    /**
     * Scale bitmap to the defined bounds. The bitmap will be scaled while maintaining the
     * aspect ratio and center-cropped(vertically and horizontally) if it exceeds the
     * defined bounds.
     */
    public static Bitmap scaleAndCropBitmapToTarget(Bitmap bitmap, int targetHeight,
            int targetWidth) {
        if (bitmap == null) {
            return bitmap;
        }
        // get bitmap properties
        int bitmapHeight = bitmap.getHeight();
        int bitmapWidth = bitmap.getWidth();

        int deltaWidth = targetWidth - bitmapWidth;
        int deltaHeight = targetHeight - bitmapHeight;

        // nothing to do if src bitmap is bigger than or equal to the target
        if (deltaWidth <= 0 && deltaHeight <= 0)
            return bitmap;

        // scale bitmap along the dimension that is lacking the greatest
        float scale = Math.max( ((float)targetWidth) / bitmapWidth,
                ((float)targetHeight) / bitmapHeight);

        // calculate the new bitmap dimensions
        int newHeight = (int) Math.ceil(bitmapHeight * scale);
        int newWidth = (int) Math.ceil(bitmapWidth * scale);
        Bitmap scaledBitmap =  Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, false);

        // center the bitmap vertically and horizontally
        int startX = Math.max(0, (newWidth - targetWidth) / 2);
        int startY = Math.max(0, (newHeight - targetHeight) / 2);
        if (DEBUG) {
            Log.i(TAG, "bitmapWidth : " + bitmapWidth);
            Log.i(TAG, "bitmapHeight : " + bitmapHeight);
            Log.i(TAG, "deltaWidth : " + deltaWidth);
            Log.i(TAG, "deltaHeight : " + deltaHeight);
            Log.i(TAG, "newWidth : " + newWidth);
            Log.i(TAG, "newHeight : " + newHeight);
            Log.i(TAG, "startX : " + startX);
            Log.i(TAG, "startY : " + startY);
        }

        return Bitmap.createBitmap(scaledBitmap, startX, startY, targetWidth, targetHeight);
    }

    /**
     * Used to convert a drawable into a bitmap.
     *
     * Drawables that don't have intrinsic dimensions are excluded from this conversion. This
     * includes drawables such as ColorDrawables
     */
    public static Bitmap drawableToBitmap (Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable)drawable).getBitmap();
        }

        if (drawable.getIntrinsicHeight() <= 0 || drawable.getIntrinsicWidth() <= 0) {
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getMinimumHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    public static byte[] bitmapToByteArray(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
        byte[] result = stream.toByteArray();
        try {
            stream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }
}
