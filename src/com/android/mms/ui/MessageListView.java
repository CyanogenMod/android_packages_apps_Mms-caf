/*
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

import android.content.Context;
import android.text.ClipboardManager;
import android.util.AttributeSet;
import android.view.*;
import android.widget.AbsListView;
import android.widget.ListView;
import com.android.mms.ui.zoom.ZoomMessageListItem;
import com.android.mms.ui.zoom.ZoomMessageListView;

public final class MessageListView extends ZoomMessageListView {
    private GestureDetector mGestureDetector;
    private OnSizeChangedListener mOnSizeChangedListener;
    public boolean mLongPress;
    private MultiChoiceModeListener mListener;
    private int mState;
    private OnScrollListener mScrollListener;

    private boolean mActionMode;

    public MessageListView(Context context) {
        this(context, null);
    }

    @Override
    public void setOnScrollListener(OnScrollListener l) {
        mScrollListener = l;
    }

    public MessageListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {

            @Override
            public void onLongPress(MotionEvent e) {
                mLongPress = true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                mLongPress = false;
                return true;
            }
        });
        super.setOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                mState = scrollState;
                if (mScrollListener != null) {
                    mScrollListener.onScrollStateChanged(view, scrollState);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (mScrollListener != null) {
                    mScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
                }
            }
        });
    }

    @Override
    protected float getTopFadingEdgeStrength() {
        // don't draw a fading edge at the top
        return 0;
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_C:
            MessageListItem view = (MessageListItem)getSelectedView();
            if (view == null) {
                break;
            }
            MessageItem item = view.getMessageItem();
            if (item != null && item.isSms()) {
                ClipboardManager clip =
                    (ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                clip.setText(item.mBody);
                return true;
            }
            break;
        }

        return super.onKeyShortcut(keyCode, event);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if (mOnSizeChangedListener != null) {
            mOnSizeChangedListener.onSizeChanged(w, h, oldw, oldh);
        }
    }

    /**
     * Set the listener which will be triggered when the size of
     * the view is changed.
     */
    void setOnSizeChangedListener(OnSizeChangedListener l) {
        mOnSizeChangedListener = l;
    }

    public interface OnSizeChangedListener {
        void onSizeChanged(int width, int height, int oldWidth, int oldHeight);
    }

    public int getCheckedPosition(){
        if ( getCheckedItemPositions() != null ) {
            return getCheckedItemPositions().indexOfValue(true);
        }
        return INVALID_POSITION;
    }

    @Override
    public void setMultiChoiceModeListener(MultiChoiceModeListener listener) {
        mListener = new ActionModeCallbackWrapper(listener);
        super.setMultiChoiceModeListener(mListener);
    }

    public ActionMode startActionModeForChild(View originalView) {
        return super.startActionModeForChild(originalView, mListener);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mState == OnScrollListener.SCROLL_STATE_IDLE) {
            if (mLongPress && ev.getAction() == MotionEvent.ACTION_UP && mActionMode) {
                mLongPress = false;
                return true;
            }
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!mActionMode) {
            mGestureDetector.onTouchEvent(ev);
        }
        return super.onInterceptTouchEvent(ev);
    }

    private class ActionModeCallbackWrapper implements MultiChoiceModeListener {
        private final MultiChoiceModeListener mCallback;

        ActionModeCallbackWrapper(MultiChoiceModeListener callback) {
            mCallback = callback;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mActionMode = false;
            if (mCallback != null) {
                mActionMode = mCallback.onCreateActionMode(mode, menu);
            }
            return mActionMode;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            if (mCallback != null) {
                return mCallback.onPrepareActionMode(mode, menu);
            } else {
                return false;
            }
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (mCallback != null) {
                return mCallback.onActionItemClicked(mode, item);
            } else {
                return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = false;
            if (mCallback != null) {
                mCallback.onDestroyActionMode(mode);
            }
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position,
                                              long id, boolean checked) {
            if (mCallback != null) {
                mCallback.onItemCheckedStateChanged(mode, position, id, checked);
            }
        }
    }

    public boolean isInActionMode() {
        return mActionMode;
    }
}

