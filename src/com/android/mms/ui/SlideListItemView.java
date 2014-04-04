/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Map;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.mms.R;
import com.google.android.mms.ContentType;
import com.android.mms.model.LayoutModel;

/**
 * A simplified view of slide in the slides list.
 */
public class SlideListItemView extends LinearLayout implements SlideViewInterface {
    private static final String TAG = "SlideListItemView";
    private static final int VIEW_OPTION = 0;
    private static final int SAVE_OPTION = 1;
    private static final String CONTACTS = "contacts";

    private TextView mTextPreview;
    private ImageView mImagePreview;
    private TextView mAttachmentName;
    private ImageView mAttachmentIcon;
    private Uri mImageUri;

    public SlideListItemView(Context context) {
        super(context);
    }

    public SlideListItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        mTextPreview = (TextView) findViewById(R.id.text_preview_bottom);
        mTextPreview.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
        mImagePreview = (ImageView) findViewById(R.id.image_preview);
        mAttachmentName = (TextView) findViewById(R.id.attachment_name);
        mAttachmentIcon = (ImageView) findViewById(R.id.attachment_icon);
    }

    public void startAudio() {
        // Playing audio is not needed in this view.
    }

    public void startVideo() {
        // Playing audio is not needed in this view.
    }

    public void setAudio(Uri audio, String name, Map<String, ?> extras) {
        if (name != null) {
            mAttachmentName.setText(name);
            if (mContext instanceof MobilePaperShowActivity) {
                mAttachmentIcon.setImageResource(R.drawable.ic_attach_capture_audio_holo_light);
                ViewAttachmentListener l = new ViewAttachmentListener(audio, name, false);
                setOnClickListener(l);
            } else {
                mAttachmentIcon.setImageResource(R.drawable.ic_mms_music);
            }
        } else {
            mAttachmentName.setText("");
            mAttachmentIcon.setImageDrawable(null);
        }
    }

    public void setImage(String name, Bitmap bitmap) {
        try {
            if (null == bitmap) {
                bitmap = BitmapFactory.decodeResource(getResources(),
                        R.drawable.ic_missing_thumbnail_picture);
            }
            mImagePreview.setImageBitmap(bitmap);
            if (mContext instanceof MobilePaperShowActivity && mImageUri != null) {
                mImagePreview.setVisibility(View.VISIBLE);
                ViewAttachmentListener l = new ViewAttachmentListener(mImageUri, name, false);
                mImagePreview.setOnClickListener(l);
            }
        } catch (java.lang.OutOfMemoryError e) {
            Log.e(TAG, "setImage: out of memory: ", e);
        }
    }

    private class ViewAttachmentListener implements OnClickListener {
        private final Uri attachmentUri;
        private final String attachmentName;
        private final boolean importVcard;

        public ViewAttachmentListener(Uri uri, String name, boolean vcard) {
            attachmentUri = uri;
            attachmentName = name;
            importVcard = vcard;
        }

        @Override
        public void onClick(View v) {
            if (attachmentUri != null && !TextUtils.isEmpty(attachmentName)) {
                String[] options = new String[] {
                        mContext.getString(importVcard ? R.string.import_vcard : R.string.view),
                        mContext.getString(R.string.save)
                };
                AlertDialog.Builder b = new AlertDialog.Builder(mContext);
                DialogInterface.OnClickListener click = new DialogInterface.OnClickListener() {
                    @Override
                    public final void onClick(DialogInterface dialog, int which) {
                        if (which == VIEW_OPTION) {
                            try {
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                if (importVcard) {
                                    intent.setDataAndType(attachmentUri,
                                            ContentType.TEXT_VCARD.toLowerCase());
                                    intent.putExtra(MessageUtils.VIEW_VCARD, true);
                                } else {
                                    intent.setData(attachmentUri);
                                }
                                mContext.startActivity(intent);
                            } catch (Exception e) {
                                Log.e(TAG, "Can't open " + attachmentUri);
                            }
                        } else if (which == SAVE_OPTION) {
                            int resId = saveAttachment(attachmentUri, attachmentName) ?
                                    R.string.copy_to_sdcard_success : R.string.copy_to_sdcard_fail;
                            Toast.makeText(mContext, resId, Toast.LENGTH_SHORT).show();
                        }
                        dialog.dismiss();
                    }
                };
                b.setTitle(R.string.select_link_title);
                b.setCancelable(true);
                b.setItems(options, click);
                b.show();
            }
        }
    }

    private boolean saveAttachment(Uri attachmentUri, String attachmentName) {
        InputStream input = null;
        FileOutputStream fout = null;
        try {
            input = mContext.getContentResolver().openInputStream(attachmentUri);
            if (input instanceof FileInputStream) {
                FileInputStream fin = (FileInputStream) input;
                File originalFile = new File(attachmentName);
                String fileName = originalFile.getName();
                String dir = Environment.getExternalStorageDirectory() + "/"
                        + Environment.DIRECTORY_DOWNLOADS + "/";
                String extension;
                int index;
                if ((index = fileName.lastIndexOf('.')) == -1) {
                    extension = "";
                } else {
                    extension = fileName.substring(index + 1, fileName.length());
                    fileName = fileName.substring(0, index);
                }
                fileName = fileName.replaceAll("^\\.", "");
                File file = getUniqueDestination(dir + fileName, extension);
                File parentFile = file.getParentFile();
                if (!parentFile.exists() && !parentFile.mkdirs()) {
                    Log.e(TAG, "[MMS] copyPart: mkdirs for " + parentFile.getPath() + " failed!");
                    return false;
                }

                fout = new FileOutputStream(file);
                byte[] buffer = new byte[8000];
                int size = 0;
                while ((size = fin.read(buffer)) != -1) {
                    fout.write(buffer, 0, size);
                }
                mContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri
                        .fromFile(file)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception caught while save attachment: ", e);
            return false;
        } finally {
            if (null != input) {
                try {
                    input.close();
                } catch (Exception e) {
                    Log.e(TAG, "Exception caught while closing input: ", e);
                    return false;
                }
            }
            if (null != fout) {
                try {
                    fout.close();
                } catch (Exception e) {
                    Log.e(TAG, "Exception caught while closing output: ", e);
                    return false;
                }
            }
        }
        return true;
    }

    private File getUniqueDestination(String base, String extension) {
        File file = new File(base + "." + extension);
        for (int i = 2; file.exists(); i++) {
            file = new File(base + "_" + i + "." + extension);
        }
        return file;
    }

    public void setUri(Uri uri) {
        mImageUri= uri;
    }

    public void setImageRegionFit(String fit) {
        // TODO Auto-generated method stub
    }

    public void setImageVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    public void setText(String name, String text) {
        mTextPreview.setText(text);
        mTextPreview.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
    }

    public void setTextVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    public void setVideo(String name, Uri video) {
        if (name != null) {
            mAttachmentName.setText(name);
            if (mContext instanceof MobilePaperShowActivity) {
                mAttachmentIcon.setImageResource(R.drawable.ic_menu_movie);
                ViewAttachmentListener l = new ViewAttachmentListener(video, name, false);
                setOnClickListener(l);
            } else {
                mAttachmentIcon.setImageResource(R.drawable.movie);
            }
        } else {
            mAttachmentName.setText("");
            mAttachmentIcon.setImageDrawable(null);
        }

        // TODO: get a thumbnail from the video
        mImagePreview.setImageBitmap(null);
    }

    public void setVideoThumbnail(String name, Bitmap thumbnail) {
    }

    public void setVideoVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    public void stopAudio() {
        // Stopping audio is not needed in this view.
    }

    public void stopVideo() {
        // Stopping video is not needed in this view.
    }

    public void reset() {
        // TODO Auto-generated method stub
    }

    public void setVisibility(boolean visible) {
        // TODO Auto-generated method stub
    }

    public void pauseAudio() {
        // TODO Auto-generated method stub

    }

    public void pauseVideo() {
        // TODO Auto-generated method stub

    }

    public void seekAudio(int seekTo) {
        // TODO Auto-generated method stub

    }

    public void seekVideo(int seekTo) {
        // TODO Auto-generated method stub

    }

    @Override
    public void setVcard(Uri lookupUri, String name) {
    }

    public void setVcard(Uri uri, String lookupUri, String name) {
        if (name != null) {
            mAttachmentName.setText(name);
            if (mContext instanceof MobilePaperShowActivity) {
                mAttachmentIcon.setImageResource(R.drawable.ic_attach_vcard);
                Uri attUri = uri;
                // If vCard uri is not from contacts, we need improt this vCard
                boolean needImport = !(lookupUri != null && lookupUri.contains(CONTACTS));
                if (!needImport) {
                    attUri = Uri.parse(lookupUri);
                }
                ViewAttachmentListener l = new ViewAttachmentListener(attUri, name, needImport);
                setOnClickListener(l);
            }
        }
    }

    public void setLayoutModel(int model) {
        if (model == LayoutModel.LAYOUT_TOP_TEXT) {
            mTextPreview = (TextView) findViewById(R.id.text_preview_top);
        } else {
            mTextPreview = (TextView) findViewById(R.id.text_preview_bottom);
        }
        mTextPreview.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
    }

    public TextView getContentText() {
        return mTextPreview;
    }
}
