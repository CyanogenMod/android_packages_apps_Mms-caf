package com.android.mms.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;
import com.android.mms.R;

/**
 * Makes sure that from view has a max width of remaining space
 */
public class ConversationListItemInfo extends RelativeLayout {
    private View mFromView;
    private View mInfoRow;

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
        mFromView = findViewById(R.id.from);
        mInfoRow = findViewById(R.id.info_row);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mInfoRow.measure(0, 0);
        mFromView.measure(0, 0);
        int spec = MeasureSpec.makeMeasureSpec(Math.min(mFromView.getMeasuredWidth(),
                MeasureSpec.getSize(widthMeasureSpec) - mInfoRow.getMeasuredWidth()),
                MeasureSpec.AT_MOST);
        mFromView.getLayoutParams().width = MeasureSpec.getSize(spec);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
