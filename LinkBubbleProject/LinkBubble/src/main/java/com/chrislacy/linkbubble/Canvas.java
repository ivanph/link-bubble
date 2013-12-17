package com.chrislacy.linkbubble;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import java.util.Vector;

public class Canvas extends RelativeLayout {

    private Context mContext;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mWindowManagerParams = new WindowManager.LayoutParams();

    private Vector<BubbleTarget> mTargets = new Vector<BubbleTarget>();

    private final float mMaxAlpha = 0.9f;
    private final float mFadeTime = 0.2f;
    private final float mAlphaDelta = mMaxAlpha / mFadeTime;

    private float mCurrentAlpha = 0.0f;
    private float mTargetAlpha = 0.0f;

    private boolean mEnabled;

    private ContentView mContentView;
    ContentView getContentView() { return mContentView; }

    public class TargetInfo {

        public TargetInfo() {
            mAction = Config.BubbleAction.None;
            mTargetX = -1;
            mTargetY = -1;
        }

        Config.BubbleAction mAction;
        int mTargetX;
        int mTargetY;
    }

    public Canvas(Context context) {
        super(context);

        mContext = context;
        mEnabled = true;

        applyAlpha();

        mTargets.add(new BubbleTarget(this, mContext, android.R.drawable.ic_delete, Config.BubbleAction.Destroy, 0.5f, 0.85f, true));
        mTargets.add(new BubbleTarget(this, mContext, Config.BubbleAction.ConsumeLeft, 0.2f, 0.15f, true));
        mTargets.add(new BubbleTarget(this, mContext, Config.BubbleAction.ConsumeRight, 0.8f, 0.15f, true));

        Settings.setConsumeBubblesChangedEventHandler(new Settings.ConsumeBubblesChangedEventHandler() {
            @Override
            public void onConsumeBubblesChanged() {
                for (int i=0 ; i < mTargets.size() ; ++i) {
                    mTargets.get(i).onConsumeBubblesChanged();
                }
            }
        });

        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        mWindowManagerParams.gravity = Gravity.TOP | Gravity.LEFT;
        mWindowManagerParams.x = 0;
        mWindowManagerParams.y = 0;
        mWindowManagerParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        mWindowManagerParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        mWindowManagerParams.type = WindowManager.LayoutParams.TYPE_PHONE;
        mWindowManagerParams.flags = WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        mWindowManagerParams.format = PixelFormat.TRANSPARENT;
        mWindowManagerParams.setTitle("LinkBubble: Canvas");
        mWindowManager.addView(this, mWindowManagerParams);
    }

    public void onOrientationChanged() {
        for (int i=0 ; i < mTargets.size() ; ++i) {
            BubbleTarget bt = mTargets.get(i);
            bt.OnOrientationChanged();
        }
        if (mContentView != null) {
            mContentView.onOrientationChanged();
        }
    }

    public void setContentView(ContentView cv) {
        if (mContentView != null) {
            removeView(mContentView);
            mContentView.onCurrentContentViewChanged(false);
        }
        mContentView = cv;
        if (mContentView != null) {
            RelativeLayout.LayoutParams p = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            p.topMargin = Config.mContentOffset;
            addView(mContentView, p);
            mContentView.onCurrentContentViewChanged(true);
            mContentView.requestFocus();
        }
    }

    private void applyAlpha() {
        Util.Assert(mCurrentAlpha >= 0.0f && mCurrentAlpha <= 1.0f);

        int color = ((int)(180 * mCurrentAlpha) << 24);
        if (Config.USE_CONTENT_ACTIVITY) {
            MainController.updateBackgroundColor(color);
        } else {
            setBackgroundColor(color);
        }

        if (!mEnabled || mCurrentAlpha == 0.0f) {
            setVisibility(GONE);
        } else {
            setVisibility(VISIBLE);
        }
    }

    public void setContentViewTranslation(float ty) {
        if (mContentView != null) {
            mContentView.setTranslationY(ty);
        }
    }

    public void showContentView() {
        Util.Assert(mContentView != null);
        mContentView.setVisibility(VISIBLE);
    }

    public void hideContentView() {
        Util.Assert(mContentView != null);
        mContentView.setVisibility(GONE);
    }

    public void enable(boolean enable) {
        mEnabled = enable;
        applyAlpha();
    }

    public void fadeIn() {
        mTargetAlpha = mMaxAlpha;
        MainController.scheduleUpdate();
    }

    public void fadeOut() {
        mTargetAlpha = 0.0f;
        MainController.scheduleUpdate();
    }

    public void fadeInTargets() {
        for (int i=0 ; i < mTargets.size() ; ++i) {
            mTargets.get(i).fadeIn();
        }
    }

    public void fadeOutTargets() {
        for (int i=0 ; i < mTargets.size() ; ++i) {
            mTargets.get(i).fadeOut();
        }
    }

    public void destroy() {
        mWindowManager.removeView(this);
    }

    public void update(float dt, Bubble frontBubble) {
        if (mCurrentAlpha < mTargetAlpha) {
            mCurrentAlpha = Util.clamp(0.0f, mCurrentAlpha + mAlphaDelta * dt, mMaxAlpha);
            MainController.scheduleUpdate();
        } else if (mCurrentAlpha > mTargetAlpha) {
            mCurrentAlpha = Util.clamp(0.0f, mCurrentAlpha - mAlphaDelta * dt, mMaxAlpha);
            MainController.scheduleUpdate();
        }
        applyAlpha();

        for (int i=0 ; i < mTargets.size() ; ++i) {
            mTargets.get(i).update(dt, frontBubble);
        }
    }

    public TargetInfo getBubbleAction(Circle bubbleCircle) {
        TargetInfo ti = new TargetInfo();

        for (int i=0 ; i < mTargets.size() ; ++i) {
            BubbleTarget bt = mTargets.get(i);

            Circle snapCircle = bt.GetSnapCircle();
            Circle defaultCircle = bt.GetDefaultCircle();

            if (bubbleCircle.Intersects(snapCircle)) {
                ti.mAction = bt.GetAction();
                ti.mTargetX = (int) defaultCircle.mX;
                ti.mTargetY = (int) defaultCircle.mY;
                break;
            }
        }

        return ti;
    }
}