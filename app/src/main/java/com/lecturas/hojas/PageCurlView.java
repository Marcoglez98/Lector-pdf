package com.lecturas.hojas;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * Lienzo del modo libro: sombra de lomo, hoja parcialmente doblada y transición
 * reversible. Los PDFs se rasterizan fuera del hilo de la interfaz.
 */
public class PageCurlView extends View {
    public interface Listener {
        void onPageRequested(boolean next);
        void onSelectionHint();
    }

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint effectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;
    private Listener listener;
    private Bitmap page;
    private Bitmap arriving;
    private boolean forward = true;
    private boolean turning = false;
    private float turnProgress = 0;
    private float opening = 1;
    private float zoom = 1f;
    private float panX = 0, panY = 0;
    private float downX, downY, lastX, lastY;
    private boolean moved;
    private int canvasColor = Color.rgb(234, 225, 211);
    private int paperColor = Color.WHITE;
    private ValueAnimator currentAnimator;

    public PageCurlView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                setZoom(zoom * d.getScaleFactor());
                return true;
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public void onLongPress(MotionEvent e) {
                if (listener != null) listener.onSelectionHint();
            }
            @Override public boolean onDown(MotionEvent e) { return true; }
        });
        setBackgroundColor(canvasColor);
    }

    public void setListener(Listener listener) { this.listener = listener; }
    public void setPaperColor(int canvasBackground, int pageBackground) {
        canvasColor = canvasBackground;
        paperColor = pageBackground;
        invalidate();
    }
    public float getZoom() { return zoom; }
    public void setZoom(float newZoom) {
        zoom = Math.max(1f, Math.min(3f, newZoom));
        clampPan();
        invalidate();
    }
    public void setPage(Bitmap value) {
        if (currentAnimator != null) currentAnimator.cancel();
        if (page != null && page != value && !page.isRecycled()) page.recycle();
        if (arriving != null && arriving != value && arriving != page && !arriving.isRecycled()) arriving.recycle();
        turning = false;
        page = value;
        arriving = null;
        panX = 0;
        panY = 0;
        invalidate();
    }
    public void openBook() {
        if (page == null) return;
        opening = 0;
        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(850);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> { opening = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }
    public void turnTo(Bitmap newPage, boolean toNext, boolean animate, Runnable onFinished) {
        if (newPage == null) return;
        if (!animate || page == null) {
            setPage(newPage);
            if (onFinished != null) onFinished.run();
            return;
        }
        if (currentAnimator != null) currentAnimator.cancel();
        arriving = newPage;
        forward = toNext;
        turning = true;
        turnProgress = 0f;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        currentAnimator = animator;
        animator.setDuration(675);
        animator.setInterpolator(new DecelerateInterpolator(1.25f));
        animator.addUpdateListener(a -> {
            turnProgress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator a) {
                if (cancelled) return;
                Bitmap departing = page;
                page = arriving;
                arriving = null;
                turning = false;
                if (departing != null && departing != page && !departing.isRecycled()) departing.recycle();
                panX = 0;
                panY = 0;
                invalidate();
                if (onFinished != null) onFinished.run();
            }
        });
        animator.start();
    }

    private RectF getPageRect() {
        if (page == null) return new RectF(0,0,getWidth(),getHeight());
        float availableWidth = Math.max(1, getWidth() - dp(28));
        float scale = availableWidth / page.getWidth() * zoom;
        float w = page.getWidth() * scale;
        float h = page.getHeight() * scale;
        return new RectF((getWidth()-w)/2 + panX, dp(14) + panY,
                (getWidth()+w)/2 + panX, dp(14)+h+panY);
    }
    private void clampPan() {
        if (page == null) return;
        float originalWidth = Math.max(1, getWidth()-dp(28));
        float contentWidth = originalWidth * zoom;
        float maxX = Math.max(0, (contentWidth - getWidth()+dp(28))/2);
        panX = Math.max(-maxX, Math.min(maxX, panX));
        float contentHeight = page.getHeight() * contentWidth / page.getWidth();
        float minY = Math.min(0, getHeight() - dp(28) - contentHeight);
        panY = Math.max(minY, Math.min(0, panY));
    }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(canvasColor);
        if (page == null) return;
        RectF rect = getPageRect();
        canvas.save();
        canvas.clipRect(0,0,getWidth(),getHeight());
        if (opening < 1 && !turning) {
            Camera camera = new Camera();
            Matrix matrix = new Matrix();
            camera.save();
            camera.rotateY(-72f * (1f-opening));
            camera.getMatrix(matrix);
            camera.restore();
            matrix.preTranslate(-rect.left, -getHeight()/2f);
            matrix.postTranslate(rect.left, getHeight()/2f);
            canvas.concat(matrix);
            drawSheet(canvas,page,rect);
        } else if (!turning || arriving == null) {
            drawSheet(canvas, page, rect);
        } else {
            // Primero se ve la página de abajo; la página que gira se recorta.
            drawSheet(canvas, arriving, rect);
            float edge = (forward ? 1f-turnProgress : turnProgress) * getWidth();
            canvas.save();
            if (forward) canvas.clipRect(0,0,edge,getHeight());
            else canvas.clipRect(edge,0,getWidth(),getHeight());
            drawSheet(canvas,page,rect);
            canvas.restore();

            float strip = dp(22) + dp(65) * (float)Math.sin(turnProgress * Math.PI);
            float left = forward ? edge : edge-strip;
            float right = forward ? edge+strip : edge;
            canvas.save();
            canvas.clipRect(Math.max(0,left),0,Math.min(getWidth(),right),getHeight());
            canvas.save();
            canvas.scale(-1f,1f,edge,0);
            drawSheet(canvas,page,rect);
            canvas.restore();
            effectPaint.setShader(new LinearGradient(left,0,right,0,
                forward ? new int[]{0xA020170E,0x50FFFFFF,0xE0C5B9A7} : new int[]{0xE0C5B9A7,0x50FFFFFF,0xA020170E},
                null, Shader.TileMode.CLAMP));
            canvas.drawRect(left,0,right,getHeight(),effectPaint);
            effectPaint.setShader(null);
            canvas.restore();
            float shadowW = dp(18);
            effectPaint.setShader(new LinearGradient(edge-shadowW,0,edge+shadowW,0,
                new int[]{0x00000000,0x55000000,0x00000000}, null,Shader.TileMode.CLAMP));
            canvas.drawRect(edge-shadowW,0,edge+shadowW,getHeight(),effectPaint);
            effectPaint.setShader(null);
        }
        canvas.restore();
    }

    private void drawSheet(Canvas canvas, Bitmap bitmap, RectF rect) {
        backgroundPaint.setColor(paperColor);
        backgroundPaint.setShadowLayer(dp(7),dp(1),dp(2),0x33000000);
        canvas.drawRoundRect(rect,dp(2),dp(2),backgroundPaint);
        backgroundPaint.clearShadowLayer();
        canvas.drawBitmap(bitmap,null,rect,bitmapPaint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        if (turning || page == null) return true;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                moved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (scaleDetector.isInProgress()) return true;
                float dx = event.getX() - lastX;
                float dy = event.getY() - lastY;
                if (Math.abs(event.getY()-downY)>dp(9) || Math.abs(event.getX()-downX)>dp(9)) moved = true;
                if (zoom>1.01f) panX += dx;
                if (Math.abs(event.getY()-downY)>Math.abs(event.getX()-downX)*0.7f || zoom>1.01f) panY += dy;
                clampPan();
                lastX = event.getX();
                lastY = event.getY();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                float swipeX = event.getX() - downX;
                float swipeY = event.getY() - downY;
                if (!scaleDetector.isInProgress() && zoom < 1.05f &&
                    Math.abs(swipeX)>dp(62) && Math.abs(swipeX)>Math.abs(swipeY)*1.3f) {
                    if (listener != null) listener.onPageRequested(swipeX<0);
                } else if (!moved) performClick();
                return true;
            case MotionEvent.ACTION_CANCEL: return true;
            default: return true;
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
