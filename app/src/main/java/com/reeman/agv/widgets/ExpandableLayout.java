package com.reeman.agv.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.reeman.agv.R;

public class ExpandableLayout extends RelativeLayout {
    private Boolean isAnimationRunning = false;
    private Boolean isOpened = false;
    private Integer duration;
    private FrameLayout contentLayout;
    private FrameLayout headerLayout;
    private Animation animation;

    public ExpandableLayout(Context context) {
        super(context);
    }

    public ExpandableLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public ExpandableLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(final Context context, AttributeSet attrs) {
        final View rootView = View.inflate(context, R.layout.layout_expandable_view, this);
        headerLayout = rootView.findViewById(R.id.view_expandable_headerlayout);
        final TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.ExpandableLayout);
        final int headerID = typedArray.getResourceId(R.styleable.ExpandableLayout_el_headerLayout, -1);
        final int contentID = typedArray.getResourceId(R.styleable.ExpandableLayout_el_contentLayout, -1);
        contentLayout = rootView.findViewById(R.id.view_expandable_contentLayout);

        if (headerID == -1 || contentID == -1)
            throw new IllegalArgumentException("HeaderLayout and ContentLayout cannot be null!");

        if (isInEditMode())
            return;

        duration = typedArray.getInt(R.styleable.ExpandableLayout_el_duration, getContext().getResources().getInteger(android.R.integer.config_shortAnimTime));
        final View headerView = View.inflate(context, headerID, null);
        headerView.setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        headerLayout.addView(headerView);
        final View contentView = View.inflate(context, contentID, null);
        contentView.setLayoutParams(new ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        contentLayout.addView(contentView);
        contentLayout.setVisibility(GONE);
        headerLayout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isAnimationRunning) {
                    if (onHeadClickListener != null){
                        if (contentLayout.getVisibility() == VISIBLE)
                            onHeadClickListener.onCollapse(ExpandableLayout.this);
                        else
                            onHeadClickListener.onExpand(ExpandableLayout.this);
                        return;
                    }
                    if (contentLayout.getVisibility() == VISIBLE)
                        collapse(contentLayout);
                    else
                        expand(contentLayout);

                    isAnimationRunning = true;
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            isAnimationRunning = false;
                        }
                    }, duration);
                }
            }
        });

        typedArray.recycle();
    }

    private void expand(final View v) {
        v.measure(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        final int targetHeight = v.getMeasuredHeight();
        v.getLayoutParams().height = 0;
        v.setVisibility(VISIBLE);

        animation = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if (interpolatedTime == 1)
                    isOpened = true;
                v.getLayoutParams().height = (interpolatedTime == 1) ? LayoutParams.WRAP_CONTENT : (int) (targetHeight * interpolatedTime);
                v.requestLayout();
            }


            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                if (onExpandListener != null) {
                    onExpandListener.onExpand(ExpandableLayout.this, true);
                }
            }

            @Override
            public void onAnimationEnd(Animation animation) {

            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        animation.setDuration(duration);
        v.startAnimation(animation);
    }

    private void collapse(final View v) {
        final int initialHeight = v.getMeasuredHeight();
        animation = new Animation() {

            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if (interpolatedTime == 1) {
                    v.setVisibility(View.GONE);
                    isOpened = false;
                } else {
                    v.getLayoutParams().height = initialHeight - (int) (initialHeight * interpolatedTime);
                    v.requestLayout();
                }
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                if (onExpandListener != null) {
                    onExpandListener.onExpand(ExpandableLayout.this, false);
                }
            }

            @Override
            public void onAnimationEnd(Animation animation) {

            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        animation.setDuration(duration);
        v.startAnimation(animation);
    }

    public Boolean isOpened() {
        return isOpened;
    }

    public void show() {
        if (!isAnimationRunning) {
            expand(contentLayout);
            isAnimationRunning = true;
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    isAnimationRunning = false;
                }
            }, duration);
        }
    }

    public FrameLayout getHeaderLayout() {
        return headerLayout;
    }

    public FrameLayout getContentLayout() {
        return contentLayout;
    }

    public void hide() {
        if (!isAnimationRunning) {
            collapse(contentLayout);
            isAnimationRunning = true;
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    isAnimationRunning = false;
                }
            }, duration);
        }
    }

    @Override
    public void setLayoutAnimationListener(Animation.AnimationListener animationListener) {
        animation.setAnimationListener(animationListener);
    }

    private OnExpandListener onExpandListener;

    public void setOnExpandListener(OnExpandListener onExpandListener) {
        this.onExpandListener = onExpandListener;
    }

    public interface OnExpandListener {
        void onExpand(ExpandableLayout expandableLayout, boolean isExpand);
    }

    private OnHeadClickListener onHeadClickListener;

    public void setOnHeadClickListener(OnHeadClickListener onHeadClickListener) {
        this.onHeadClickListener = onHeadClickListener;
    }

    public interface OnHeadClickListener{
        void onExpand(ExpandableLayout expandableLayout);

        void onCollapse(ExpandableLayout expandableLayout);
    }
}