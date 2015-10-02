package com.android.mms.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import com.android.mms.R;

import java.util.ArrayList;

/**
 * Makes sure that from view has a max width of remaining space
 */
public class ConversationListItemInfo extends RelativeLayout {
    private View mFromView;
    private ArrayList<View> mViewsNextToFrom = new ArrayList<>(VIEWS_NEXT_TO_FROM.length);

    private static final int[] VIEWS_NEXT_TO_FROM = {
        R.id.from_count, R.id.unread_count, R.id.icon_row, R.id.date
    };

    public ConversationListItemInfo(Context context) {
        super(context);
    }

    public ConversationListItemInfo(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ConversationListItemInfo(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ConversationListItemInfo(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        for (int i = 0; i < VIEWS_NEXT_TO_FROM.length; i++) {
            mViewsNextToFrom.add(findViewById(VIEWS_NEXT_TO_FROM[i]));
        }
        mFromView = findViewById(R.id.from);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthOfViewsNextToFrom = 0;
        final int length = mViewsNextToFrom.size();
        for (int i = 0; i < length; i++) {
            final View v = mViewsNextToFrom.get(i);
            v.measure(0, 0);
            widthOfViewsNextToFrom += v.getMeasuredWidth();
        }
        mFromView.measure(0, 0);

        int width = Math.min(mFromView.getMeasuredWidth(),
                MeasureSpec.getSize(widthMeasureSpec) - widthOfViewsNextToFrom);
        mFromView.getLayoutParams().width = width;
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
