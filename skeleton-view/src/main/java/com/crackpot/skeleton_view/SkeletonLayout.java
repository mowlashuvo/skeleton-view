package com.crackpot.skeleton_view;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

public class SkeletonLayout extends FrameLayout {

    private static final int DEFAULT_ANIMATION_DURATION = 1500;

    private static final byte DEFAULT_ANGLE = 0;

    private static final byte MIN_ANGLE_VALUE = -90;
    private static final byte MAX_ANGLE_VALUE = 90;
    private static final byte MIN_MASK_WIDTH_VALUE = 0;
    private static final byte MAX_MASK_WIDTH_VALUE = 1;

    private static final byte MIN_GRADIENT_CENTER_COLOR_WIDTH_VALUE = 0;
    private static final byte MAX_GRADIENT_CENTER_COLOR_WIDTH_VALUE = 1;

    private int maskOffsetX;
    private Rect maskRect;
    private Paint gradientTexturePaint;
    private ValueAnimator maskAnimator;

    private Bitmap localMaskBitmap;
    private Bitmap maskBitmap;
    private Canvas canvasForSkeletonMask;

    private boolean isAnimationReversed;
    private boolean isAnimationStarted;
    private boolean autoStart;
    private int skeletonAnimationDuration;
    private int skeletonColor;
    private int skeletonAngle;
    private float maskWidth;
    private float gradientCenterColorWidth;

    private ViewTreeObserver.OnPreDrawListener startAnimationPreDrawListener;

    public SkeletonLayout(Context context) {
        this(context, null);
    }

    public SkeletonLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SkeletonLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setWillNotDraw(false);

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.SkeletonLayout,
                0, 0);

        try {
            skeletonAngle = a.getInteger(R.styleable.SkeletonLayout_skeleton_angle, DEFAULT_ANGLE);
            skeletonAnimationDuration = a.getInteger(R.styleable.SkeletonLayout_skeleton_animation_duration, DEFAULT_ANIMATION_DURATION);
            skeletonColor = a.getColor(R.styleable.SkeletonLayout_skeleton_color, getColor(R.color.grey_20));
            autoStart = a.getBoolean(R.styleable.SkeletonLayout_skeleton_auto_start, true);
            maskWidth = a.getFloat(R.styleable.SkeletonLayout_skeleton_mask_width, 0.5F);
            gradientCenterColorWidth = a.getFloat(R.styleable.SkeletonLayout_skeleton_gradient_center_color_width, 0.1F);
            isAnimationReversed = a.getBoolean(R.styleable.SkeletonLayout_skeleton_reverse_animation, false);

        } finally {
            a.recycle();
        }

        setMaskWidth(maskWidth);
        setGradientCenterColorWidth(gradientCenterColorWidth);
        setSkeletonAngle(skeletonAngle);

        enableForcedSoftwareLayerIfNeeded();

        if (autoStart && getVisibility() == VISIBLE) {
            startSkeletonAnimation();
        }


//        getSkeletonRowCount(context);

    }

    @Override
    protected void onDetachedFromWindow() {
        resetSkeleton();
        super.onDetachedFromWindow();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!isAnimationStarted || getWidth() <= 0 || getHeight() <= 0) {
            super.dispatchDraw(canvas);
        } else {
            dispatchDrawSkeleton(canvas);
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == VISIBLE) {
            if (autoStart) {
                startSkeletonAnimation();
            }
        } else {
            stopSkeletonAnimation();
        }
    }

    public void startSkeletonAnimation() {
        if (isAnimationStarted) {
            return;
        }

        if (getWidth() == 0) {
            startAnimationPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    getViewTreeObserver().removeOnPreDrawListener(this);
                    startSkeletonAnimation();

                    return true;
                }
            };

            getViewTreeObserver().addOnPreDrawListener(startAnimationPreDrawListener);

            return;
        }

        Animator animator = getSkeletonAnimation();
        animator.start();
        isAnimationStarted = true;
    }

    public void stopSkeletonAnimation() {
        if (startAnimationPreDrawListener != null) {
            getViewTreeObserver().removeOnPreDrawListener(startAnimationPreDrawListener);
        }

        resetSkeleton();
    }

    public boolean isAnimationStarted() {
        return isAnimationStarted;
    }

    public void setSkeletonColor(int skeletonColor) {
        this.skeletonColor = skeletonColor;
        resetIfStarted();
    }

    public void setSkeletonAnimationDuration(int durationMillis) {
        this.skeletonAnimationDuration = durationMillis;
        resetIfStarted();
    }

    public void setAnimationReversed(boolean animationReversed) {
        this.isAnimationReversed = animationReversed;
        resetIfStarted();
    }


    public void setSkeletonAngle(int angle) {
        if (angle < MIN_ANGLE_VALUE || MAX_ANGLE_VALUE < angle) {
            throw new IllegalArgumentException(String.format("Angle value must be between %d and %d",
                    MIN_ANGLE_VALUE,
                    MAX_ANGLE_VALUE));
        }
        this.skeletonAngle = angle;
        resetIfStarted();
    }


    public void setMaskWidth(float maskWidth) {
        if (maskWidth <= MIN_MASK_WIDTH_VALUE || MAX_MASK_WIDTH_VALUE < maskWidth) {
            throw new IllegalArgumentException(String.format("maskWidth value must be higher than %d and less or equal to %d",
                    MIN_MASK_WIDTH_VALUE, MAX_MASK_WIDTH_VALUE));
        }

        this.maskWidth = maskWidth;
        resetIfStarted();
    }

    public void setGradientCenterColorWidth(float gradientCenterColorWidth) {
        if (gradientCenterColorWidth <= MIN_GRADIENT_CENTER_COLOR_WIDTH_VALUE
                || MAX_GRADIENT_CENTER_COLOR_WIDTH_VALUE <= gradientCenterColorWidth) {
            throw new IllegalArgumentException(String.format("gradientCenterColorWidth value must be higher than %d and less than %d",
                    MIN_GRADIENT_CENTER_COLOR_WIDTH_VALUE, MAX_GRADIENT_CENTER_COLOR_WIDTH_VALUE));
        }

        this.gradientCenterColorWidth = gradientCenterColorWidth;
        resetIfStarted();
    }

    private void resetIfStarted() {
        if (isAnimationStarted) {
            resetSkeleton();
            startSkeletonAnimation();
        }
    }

    private void dispatchDrawSkeleton(Canvas canvas) {
        super.dispatchDraw(canvas);

        localMaskBitmap = getMaskBitmap();
        if (localMaskBitmap == null) {
            return;
        }

        if (canvasForSkeletonMask == null) {
            canvasForSkeletonMask = new Canvas(localMaskBitmap);
        }

        canvasForSkeletonMask.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        canvasForSkeletonMask.save();
        canvasForSkeletonMask.translate(-maskOffsetX, 0);

        super.dispatchDraw(canvasForSkeletonMask);

        canvasForSkeletonMask.restore();

        drawSkeleton(canvas);

        localMaskBitmap = null;
    }

    private void drawSkeleton(Canvas destinationCanvas) {
        createSkeletonPaint();

        destinationCanvas.save();

        destinationCanvas.translate(maskOffsetX, 0);
        destinationCanvas.drawRect(maskRect.left, 0, maskRect.width(), maskRect.height(), gradientTexturePaint);

        destinationCanvas.restore();
    }

    private void resetSkeleton() {
        if (maskAnimator != null) {
            maskAnimator.end();
            maskAnimator.removeAllUpdateListeners();
        }

        maskAnimator = null;
        gradientTexturePaint = null;
        isAnimationStarted = false;

        releaseBitMaps();
    }

    private void releaseBitMaps() {
        canvasForSkeletonMask = null;

        if (maskBitmap != null) {
            maskBitmap.recycle();
            maskBitmap = null;
        }
    }

    private Bitmap getMaskBitmap() {
        if (maskBitmap == null) {
            maskBitmap = createBitmap(maskRect.width(), getHeight());
        }

        return maskBitmap;
    }

    private void createSkeletonPaint() {
        if (gradientTexturePaint != null) {
            return;
        }

        final int edgeColor = reduceColorAlphaValueToZero(skeletonColor);
        final float skeletonLineWidth = getWidth() / 2 * maskWidth;
        final float yPosition = (0 <= skeletonAngle) ? getHeight() : 0;

        LinearGradient gradient = new LinearGradient(
                0, yPosition,
                (float) Math.cos(Math.toRadians(skeletonAngle)) * skeletonLineWidth,
                yPosition + (float) Math.sin(Math.toRadians(skeletonAngle)) * skeletonLineWidth,
                new int[]{edgeColor, skeletonColor, skeletonColor, edgeColor},
                getGradientColorDistribution(),
                Shader.TileMode.CLAMP);

        BitmapShader maskBitmapShader = new BitmapShader(localMaskBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

        ComposeShader composeShader = new ComposeShader(gradient, maskBitmapShader, PorterDuff.Mode.DST_IN);

        gradientTexturePaint = new Paint();
        gradientTexturePaint.setAntiAlias(true);
        gradientTexturePaint.setDither(true);
        gradientTexturePaint.setFilterBitmap(true);
        gradientTexturePaint.setShader(composeShader);
    }

    private Animator getSkeletonAnimation() {
        if (maskAnimator != null) {
            return maskAnimator;
        }

        if (maskRect == null) {
            maskRect = calculateBitmapMaskRect();
        }

        final int animationToX = getWidth();
        final int animationFromX;

        if (getWidth() > maskRect.width()) {
            animationFromX = -animationToX;
        } else {
            animationFromX = -maskRect.width();
        }

        final int skeletonBitmapWidth = maskRect.width();
        final int skeletonAnimationFullLength = animationToX - animationFromX;

        maskAnimator = isAnimationReversed ? ValueAnimator.ofInt(skeletonAnimationFullLength, 0)
                : ValueAnimator.ofInt(0, skeletonAnimationFullLength);
        maskAnimator.setDuration(skeletonAnimationDuration);
        maskAnimator.setRepeatCount(ObjectAnimator.INFINITE);

        maskAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                maskOffsetX = animationFromX + (int) animation.getAnimatedValue();

                if (maskOffsetX + skeletonBitmapWidth >= 0) {
                    invalidate();
                }
            }
        });

        return maskAnimator;
    }

    private Bitmap createBitmap(int width, int height) {
        try {
            return Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
        } catch (OutOfMemoryError e) {
            System.gc();

            return null;
        }
    }

    private int getColor(int id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getContext().getColor(id);
        } else {
            //noinspection deprecation
            return getResources().getColor(id);
        }
    }

    private int reduceColorAlphaValueToZero(int actualColor) {
        return Color.argb(0, Color.red(actualColor), Color.green(actualColor), Color.blue(actualColor));
    }

    private Rect calculateBitmapMaskRect() {
        return new Rect(0, 0, calculateMaskWidth(), getHeight());
    }

    private int calculateMaskWidth() {
        final double skeletonLineBottomWidth = (getWidth() / 2 * maskWidth) / Math.cos(Math.toRadians(Math.abs(skeletonAngle)));
        final double skeletonLineRemainingTopWidth = getHeight() * Math.tan(Math.toRadians(Math.abs(skeletonAngle)));

        return (int) (skeletonLineBottomWidth + skeletonLineRemainingTopWidth);
    }

    private float[] getGradientColorDistribution() {
        final float[] colorDistribution = new float[4];

        colorDistribution[0] = 0;
        colorDistribution[3] = 1;

        colorDistribution[1] = 0.5F - gradientCenterColorWidth / 2F;
        colorDistribution[2] = 0.5F + gradientCenterColorWidth / 2F;

        return colorDistribution;
    }

    private void enableForcedSoftwareLayerIfNeeded() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
    }

    public int getSkeletonRowCount(Context context) {
        int pxHeight = getDeviceHeight(context);
//        int skeletonRowHeight = (int) getResources()
//                .getDimension(R.dimen.row_layout_height); //converts to pixel

        int skeletonRowHeight = dpToPx(context,100);

        return (int) Math.ceil(pxHeight / skeletonRowHeight);
    }
    public int getDeviceHeight(Context context){
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        return metrics.heightPixels;
    }
    public int dpToPx(Context c, int dp) {
        Resources r = c.getResources();
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics()));
    }

}