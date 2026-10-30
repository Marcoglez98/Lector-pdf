package com.lecturas.hojas;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RadialGradient;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.List;
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
        void onMarkup(float x0,float y0,float x1,float y1,boolean erase);
        void onReaderTap(boolean doubleTap);
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
    private boolean bookmarkOpening = false;
    private int bookmarkSheets = 8;
    private float bookmarkProgress = 0f;
    private final Paint fanPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path fanPath = new Path();
    private float turnProgress = 0;
    private float opening = 1;
    private float zoom = 1f;
    private float panX = 0, panY = 0;
    private boolean fullPage = false;
    private float fullscreenProgress = 0f;
    private ValueAnimator layoutAnimator;
    private boolean magnifierVisible=false;
    private float magnifierAlpha=0f, magnifierX, magnifierY;
    private ValueAnimator magnifierAnimator;
    private final Paint magnifierPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint bookPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bookStroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path magnifierCircle=new Path();
    private boolean longHeld=false;
    private float downX, downY, lastX, lastY;
    private boolean moved;
    private int canvasColor = Color.rgb(234, 225, 211);
    private int paperColor = Color.WHITE;
    private ValueAnimator currentAnimator;
    public static final class Ink {
        public final List<RectF> rects;
        public final int color;
        public Ink(List<RectF> rects,int color){this.rects=rects;this.color=color;}
    }
    private final List<Ink> marks=new ArrayList<>();
    private final Paint inkPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gesturePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<RectF> animationRects;
    private float inkProgress=1f, markEndX,markEndY;
    private boolean inkEraser, strokeStarted;
    private int inkColor=0xFFFFE69A, annotationTool=0;
    private ValueAnimator inkAnimator;

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
                if(page==null || turning) return;
                RectF rect=getPageRect();
                if(!rect.contains(e.getX(),e.getY()))return;
                longHeld=true;
                showMagnifier(e.getX(),e.getY(),true);
            }
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                if(listener!=null && annotationTool==0)listener.onReaderTap(false);
                return true;
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                if(listener!=null && annotationTool==0)listener.onReaderTap(true);
                return true;
            }
        });
        setBackgroundColor(canvasColor);
    }

    public void setListener(Listener listener) { this.listener = listener; }
    public void setAnnotationTool(int tool) {
        annotationTool=Math.max(0,Math.min(2,tool));
        strokeStarted=false;invalidate();
    }
    public int getAnnotationTool(){return annotationTool;}
    public void setMarks(List<Ink> values) {
        marks.clear();
        if(values!=null)marks.addAll(values);
        invalidate();
    }
    public void animateInk(List<RectF> rects,int color,boolean eraser,Runnable done) {
        if(inkAnimator!=null)inkAnimator.cancel();
        animationRects=new ArrayList<>(rects);inkColor=color;inkEraser=eraser;inkProgress=0;
        ValueAnimator a=ValueAnimator.ofFloat(0f,1f);
        inkAnimator=a;a.setDuration(480);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(v->{inkProgress=(float)v.getAnimatedValue();invalidate();});
        a.addListener(new android.animation.AnimatorListenerAdapter(){
            boolean cancelled=false;
            @Override public void onAnimationCancel(android.animation.Animator animator){cancelled=true;}
            @Override public void onAnimationEnd(android.animation.Animator animator){
                animationRects=null;invalidate();
                if(!cancelled&&done!=null)done.run();
            }
        });
        a.start();
    }
    public void setPaperColor(int canvasBackground, int pageBackground) {
        canvasColor = canvasBackground;
        paperColor = pageBackground;
        invalidate();
    }
    public void setFullPage(boolean value){
        if(fullPage==value && layoutAnimator==null)return;
        fullPage=value;
        if(layoutAnimator!=null)layoutAnimator.cancel();
        ValueAnimator animator=ValueAnimator.ofFloat(fullscreenProgress,value?1f:0f);
        layoutAnimator=animator;
        animator.setDuration(390);
        animator.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        animator.addUpdateListener(a->{
            fullscreenProgress=(float)a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(android.animation.Animator animation){
                if(layoutAnimator==animation)layoutAnimator=null;
            }
        });
        animator.start();
    }
    private float blend(float a,float b,float p){return a+(b-a)*p;}
    private int fadedColor(int color,int alpha){
        return (color & 0x00FFFFFF)|((Math.max(0,Math.min(255,alpha)))<<24);
    }
    private void showMagnifier(float x,float y,boolean visible){
        magnifierX=x;magnifierY=y;
        magnifierVisible=visible;
        if(magnifierAnimator!=null)magnifierAnimator.cancel();
        ValueAnimator animation=ValueAnimator.ofFloat(magnifierAlpha,visible?1f:0f);
        magnifierAnimator=animation;animation.setDuration(visible?210:160);
        animation.setInterpolator(new DecelerateInterpolator(1.6f));
        animation.addUpdateListener(a->{magnifierAlpha=(float)a.getAnimatedValue();invalidate();});
        animation.start();
    }
    public RectF getVisiblePageBounds(){return new RectF(getPageRect());}
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
        bookmarkOpening = false;
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

    /**
     * Abre de forma visual un bloque de hojas hacia el separador.
     * Para mantener fluidez en móviles, dibuja un abanico de hojas simulado,
     * no rasteriza todas las páginas intermedias. La última es el PDF real.
     */
    public void openAtRibbon(Bitmap destination, boolean toNext, int crossedPages, Runnable finished){
        if(destination==null)return;
        if(page==null || crossedPages<=0){
            setPage(destination);
            if(finished!=null)finished.run();
            return;
        }
        if(currentAnimator!=null)currentAnimator.cancel();
        arriving=destination;
        forward=toNext;
        bookmarkSheets=Math.max(5,Math.min(15,5+(int)Math.sqrt(Math.max(1,crossedPages))));
        turning=true;
        bookmarkOpening=true;
        opening=1f;
        bookmarkProgress=0f;
        ValueAnimator a=ValueAnimator.ofFloat(0f,1f);
        currentAnimator=a;
        a.setDuration(Math.min(1850,1050+Math.min(crossedPages,25)*21L));
        a.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        a.addUpdateListener(v->{bookmarkProgress=(float)v.getAnimatedValue();invalidate();});
        a.addListener(new android.animation.AnimatorListenerAdapter(){
            boolean cancelled=false;
            @Override public void onAnimationCancel(android.animation.Animator animation){cancelled=true;}
            @Override public void onAnimationEnd(android.animation.Animator animation){
                if(cancelled)return;
                Bitmap old=page;
                page=arriving;
                arriving=null;
                turning=false;
                bookmarkOpening=false;
                bookmarkProgress=1;
                panX=panY=0;
                if(old!=null && old!=page && !old.isRecycled())old.recycle();
                invalidate();
                if(finished!=null)finished.run();
            }
        });
        a.start();
    }
    private static float clamp01(float v){return Math.max(0f,Math.min(1f,v));}
    private float smooth(float t){float p=clamp01(t);return p*p*(3f-2f*p);}
    private void drawBookmarkFan(Canvas canvas,RectF rect){
        float p=bookmarkProgress;
        float w=rect.width(),h=rect.height();
        // Página de destino: ya está preparada antes de iniciar la animación.
        drawSheet(canvas,arriving,rect);
        float oldVisible=clamp01(1f-p*1.7f);
        if(oldVisible>.001f && page!=null){
            canvas.save();
            if(forward)canvas.clipRect(rect.left,rect.top,rect.left+w*oldVisible,rect.bottom);
            else canvas.clipRect(rect.right-w*oldVisible,rect.top,rect.right,rect.bottom);
            drawSheet(canvas,page,rect);
            canvas.restore();
        }
        float fadeOut=1f-smooth((p-.78f)/.22f);
        float spine=forward?rect.left+w*.075f:rect.right-w*.075f;
        // Sombras, grosor y hojas en abanico: pasan de derecha a izquierda.
        for(int i=bookmarkSheets-1;i>=0;i--){
            float stagger=(float)i/bookmarkSheets;
            float progress=smooth((p-stagger*.30f)/.72f);
            if(progress<=.001f)continue;
            float edge=forward
                ? rect.right-(rect.right-spine)*progress
                : rect.left+(spine-rect.left)*progress;
            float bend=(float)Math.sin(progress*Math.PI)*dp(15);
            float yTop=rect.top+dp(1)+bend*.22f;
            float yBottom=rect.bottom-dp(1)-bend*.15f;
            fanPath.reset();
            fanPath.moveTo(spine,rect.top);
            fanPath.quadTo((spine+edge)*.5f,yTop-bend,edge,yTop);
            fanPath.quadTo(edge+(forward?-bend:bend)*.55f,rect.centerY(),edge,yBottom);
            fanPath.quadTo((spine+edge)*.5f,rect.bottom+bend,spine,rect.bottom);
            fanPath.close();
            float leafAlpha=fadeOut*(.36f+.53f*(1f-stagger));
            fanPaint.setStyle(Paint.Style.FILL);
            fanPaint.setColor((Math.min(240,(int)(leafAlpha*230))<<24)|0x00F9F0E4);
            canvas.drawPath(fanPath,fanPaint);
            fanPaint.setStyle(Paint.Style.STROKE);
            fanPaint.setStrokeWidth(dp(.7f));
            fanPaint.setColor((Math.min(155,(int)(fadeOut*130))<<24)|0x0089775F);
            canvas.drawPath(fanPath,fanPaint);
            fanPaint.setStyle(Paint.Style.FILL);
            float shadow=dp(18);
            fanPaint.setShader(new LinearGradient(
                edge-(forward?shadow:-shadow),0,
                edge+(forward?shadow:-shadow),0,
                new int[]{0x00000000,((int)(72*fadeOut)<<24)|0x002B1A0F,0x00000000},
                null,Shader.TileMode.CLAMP));
            canvas.drawRect(Math.min(edge-shadow,edge+shadow),rect.top,
                Math.max(edge-shadow,edge+shadow),rect.bottom,fanPaint);
            fanPaint.setShader(null);
        }
        // El conjunto de páginas ya pasadas se acomoda a la izquierda.
        float settled=smooth((p-.20f)/.75f)*fadeOut;
        if(settled>.001f){
            float layerSpan=dp(5+bookmarkSheets*.65f)*settled;
            fanPaint.setStyle(Paint.Style.STROKE);
            for(int i=0;i<bookmarkSheets;i++){
                float shift=(float)i/bookmarkSheets*layerSpan;
                float x=forward?rect.left-shift:rect.right+shift;
                fanPaint.setStrokeWidth(dp(.65f));
                fanPaint.setColor(((int)(125*settled)<<24)|0x00A88D70);
                canvas.drawLine(x,rect.top+dp(3),x,rect.bottom-dp(4),fanPaint);
            }
            fanPaint.setStyle(Paint.Style.FILL);
        }
    }

    private RectF getPageRect() {
        if(page==null)return new RectF(0,0,getWidth(),getHeight());
        float maxBookW=Math.max(1f,getWidth()-dp(48));
        float maxBookH=Math.max(1f,getHeight()-dp(38));
        float maxCleanW=Math.max(1f,getWidth()-dp(2));
        float maxCleanH=Math.max(1f,getHeight()-dp(4));
        float bookScale=Math.min(maxBookW/page.getWidth(),maxBookH/page.getHeight());
        float cleanScale=Math.min(maxCleanW/page.getWidth(),maxCleanH/page.getHeight());
        float scale=blend(bookScale,cleanScale,fullscreenProgress)*zoom;
        float w=page.getWidth()*scale,h=page.getHeight()*scale;
        float left=(getWidth()-w)/2f+panX,top=(getHeight()-h)/2f+panY;
        return new RectF(left,top,left+w,top+h);
    }
    private void clampPan(){
        if(page==null)return;
        RectF r=getPageRect();
        float x=Math.max(0,(r.width()-getWidth())/2f);
        float y=Math.max(0,(r.height()-getHeight())/2f);
        panX=Math.max(-x,Math.min(x,panX));
        panY=Math.max(-y,Math.min(y,panY));
    }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int background=fullscreenProgress>0.98f?paperColor:canvasColor;
        canvas.drawColor(background);
        if (page == null) return;
        RectF rect = getPageRect();
        canvas.save();
        canvas.clipRect(0,0,getWidth(),getHeight());
        if(fullscreenProgress<0.99f) drawBookBackdrop(canvas,rect,1f-fullscreenProgress);
        if (bookmarkOpening && arriving!=null) {
            drawBookmarkFan(canvas,rect);
        } else if (opening < 1 && !turning) {
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
        if(!turning && opening>=0.99f) drawAnnotations(canvas,rect);
        if(fullscreenProgress<0.99f)drawBookPageEdges(canvas,rect,1f-fullscreenProgress);
        if(magnifierAlpha>0.008f)drawMagnifier(canvas,rect);
        canvas.restore();
    }

    private void drawAnnotations(Canvas canvas, RectF pageRect) {
        canvas.save();
        canvas.clipRect(pageRect);
        for(Ink mark:marks) drawInkRects(canvas,pageRect,mark.rects,mark.color,1f,false);
        if(animationRects!=null)drawInkRects(canvas,pageRect,animationRects,inkColor,inkProgress,inkEraser);
        if(strokeStarted && annotationTool!=0) {
            gesturePaint.setColor(annotationTool==2?0xFFBDB5AA:0xCCB99334);
            gesturePaint.setStyle(Paint.Style.STROKE);
            gesturePaint.setStrokeWidth(dp(7));
            gesturePaint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(downX,downY,markEndX,markEndY,gesturePaint);
            gesturePaint.setStyle(Paint.Style.FILL);
        }
        canvas.restore();
    }
    private void drawInkRects(Canvas canvas,RectF pageRect,List<RectF> rects,int color,float progress,boolean erase) {
        inkPaint.setColor(erase ? paperColor : ((color & 0x00FFFFFF) | 0x92000000));
        for(RectF r:rects) {
            float left=pageRect.left+r.left*pageRect.width();
            float right=pageRect.left+r.right*pageRect.width();
            float top=pageRect.top+r.top*pageRect.height();
            float bottom=pageRect.top+r.bottom*pageRect.height();
            float lineHeight=Math.max(dp(5),bottom-top);
            // Franja irregular como la punta de un subrayador real.
            top+=lineHeight*0.17f;
            bottom+=lineHeight*0.09f;
            right=left+(right-left)*Math.min(1f,Math.max(0f,progress));
            canvas.drawRoundRect(left-dp(1),top,right+dp(2),bottom,dp(2),dp(2),inkPaint);
        }
    }

    // Capas de papel y lomo a ambos lados, sin alterar el contenido del PDF.
    private void drawBookBackdrop(Canvas canvas,RectF rect,float alpha) {
        int a=(int)(255*alpha);
        if(a<2)return;
        bookPaint.setStyle(Paint.Style.FILL);
        bookPaint.setColor(fadedColor(0xFFB89A79,(int)(185*alpha)));
        bookPaint.setShadowLayer(dp(10),0,dp(5),fadedColor(0xFF392617,(int)(90*alpha)));
        canvas.drawRoundRect(rect.left-dp(10),rect.top+dp(5),rect.right+dp(10),rect.bottom+dp(10),dp(5),dp(5),bookPaint);
        bookPaint.clearShadowLayer();
        for(int i=6;i>=1;i--){
            float offset=i*dp(1.3f);
            bookPaint.setColor(fadedColor(i%2==0?0xFFF5EBDB:0xFFE6D5BD,(int)(250*alpha)));
            canvas.drawRoundRect(rect.left-dp(7)+offset*0.3f,rect.top+offset*.50f,
                rect.right+dp(6)+offset*.55f,rect.bottom+offset,
                dp(2),dp(2),bookPaint);
            bookStroke.setColor(fadedColor(0xFFAC9276,(int)(125*alpha)));
            bookStroke.setStrokeWidth(dp(.7f));
            canvas.drawLine(rect.right+dp(5)+offset*.55f,rect.top+offset*.6f,
                rect.right+dp(5)+offset*.55f,rect.bottom+offset,bookStroke);
        }
        bookPaint.setShader(new LinearGradient(rect.left-dp(11),0,rect.left+dp(27),0,
            new int[]{fadedColor(0xFF72523B,(int)(155*alpha)),fadedColor(0xFFD5B998,(int)(115*alpha)),
                fadedColor(0xFFFFF7E8,(int)(90*alpha)),0x00FFFFFF},
            null,Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect.left-dp(10),rect.top+dp(3),rect.left+dp(27),rect.bottom+dp(4),
            dp(4),dp(4),bookPaint);
        bookPaint.setShader(null);
    }
    private void drawBookPageEdges(Canvas canvas,RectF rect,float alpha){
        if(alpha<=0)return;
        bookPaint.setColor(fadedColor(0xFFB9A48A,(int)(130*alpha)));
        bookPaint.setStrokeWidth(dp(.65f));
        for(int i=1;i<=6;i++){
            float y=rect.bottom+dp(i);
            canvas.drawLine(rect.left+dp(5),y,rect.right+dp(7),y,bookPaint);
        }
        bookPaint.setShader(new LinearGradient(rect.left,0,rect.left+dp(35),0,
            new int[]{fadedColor(0xFF67462C,(int)(105*alpha)),
                fadedColor(0xFF9A7C62,(int)(40*alpha)),0x00FFFFFF},
            null,Shader.TileMode.CLAMP));
        canvas.drawRect(rect.left,rect.top,rect.left+dp(35),rect.bottom,bookPaint);
        bookPaint.setShader(null);
        bookPaint.setShader(new LinearGradient(rect.right-dp(16),0,rect.right,0,
            new int[]{0x00FFFFFF,fadedColor(0xFF867159,(int)(60*alpha))},
            null,Shader.TileMode.CLAMP));
        canvas.drawRect(rect.right-dp(16),rect.top,rect.right,rect.bottom,bookPaint);
        bookPaint.setShader(null);
    }
    private void drawMagnifier(Canvas canvas,RectF rect) {
        if(page==null||page.isRecycled()||turning||magnifierAlpha<=.01f)return;
        float radius=dp(65)*(.86f+.14f*magnifierAlpha);
        float centerX=Math.max(radius+dp(5),Math.min(getWidth()-radius-dp(5),magnifierX));
        float centerY=magnifierY-dp(112);
        if(centerY<radius+dp(8))centerY=magnifierY+dp(108);
        centerY=Math.max(radius+dp(6),Math.min(getHeight()-radius-dp(6),centerY));
        float zoomFactor=2.35f;
        float sourceX=(magnifierX-rect.left)*page.getWidth()/Math.max(1,rect.width());
        float sourceY=(magnifierY-rect.top)*page.getHeight()/Math.max(1,rect.height());
        float srcWidth=radius*2f*page.getWidth()/Math.max(1,rect.width())/zoomFactor;
        float srcHeight=radius*2f*page.getHeight()/Math.max(1,rect.height())/zoomFactor;
        Rect src=new Rect(
            (int)Math.max(0,Math.min(page.getWidth()-1,sourceX-srcWidth/2f)),
            (int)Math.max(0,Math.min(page.getHeight()-1,sourceY-srcHeight/2f)),
            (int)Math.min(page.getWidth(),sourceX+srcWidth/2f),
            (int)Math.min(page.getHeight(),sourceY+srcHeight/2f));
        if(src.width()<2||src.height()<2)return;
        magnifierCircle.reset();
        magnifierCircle.addCircle(centerX,centerY,radius,Path.Direction.CW);
        magnifierPaint.setShader(null);
        magnifierPaint.setColor(fadedColor(0xFF4F3023,(int)(210*magnifierAlpha)));
        magnifierPaint.setShadowLayer(dp(12),dp(1),dp(6),fadedColor(0xFF000000,(int)(115*magnifierAlpha)));
        canvas.drawCircle(centerX,centerY,radius+dp(4),magnifierPaint);
        magnifierPaint.clearShadowLayer();
        canvas.save();
        canvas.clipPath(magnifierCircle);
        magnifierPaint.setColor(Color.WHITE);
        canvas.drawCircle(centerX,centerY,radius,magnifierPaint);
        magnifierPaint.setAlpha((int)(255*magnifierAlpha));
        canvas.drawBitmap(page,src,
            new RectF(centerX-radius,centerY-radius,centerX+radius,centerY+radius),
            magnifierPaint);
        canvas.restore();
        magnifierPaint.setStyle(Paint.Style.STROKE);
        magnifierPaint.setColor(fadedColor(0xFFE9CDA7,(int)(245*magnifierAlpha)));
        magnifierPaint.setStrokeWidth(dp(3));
        canvas.drawCircle(centerX,centerY,radius,magnifierPaint);
        magnifierPaint.setStrokeWidth(dp(1));
        magnifierPaint.setColor(fadedColor(0xFF7A523E,(int)(190*magnifierAlpha)));
        canvas.drawCircle(centerX,centerY,radius+dp(3),magnifierPaint);
        magnifierPaint.setStyle(Paint.Style.FILL);
        magnifierPaint.setAlpha(255);
    }

    private void drawSheet(Canvas canvas, Bitmap bitmap, RectF rect) {
        backgroundPaint.setColor(paperColor);
        backgroundPaint.setShadowLayer(dp(7),dp(1),dp(2),0x33000000);
        canvas.drawRoundRect(rect,dp(2),dp(2),backgroundPaint);
        backgroundPaint.clearShadowLayer();
        canvas.drawBitmap(bitmap,null,rect,bitmapPaint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (turning || page == null) return true;
        if(annotationTool!=0){
            gestureDetector.onTouchEvent(event);
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE && longHeld){
                magnifierX=event.getX();magnifierY=event.getY();invalidate();
            }else if(event.getActionMasked()==MotionEvent.ACTION_UP ||
                    event.getActionMasked()==MotionEvent.ACTION_CANCEL){
                if(longHeld){showMagnifier(magnifierX,magnifierY,false);longHeld=false;}
            }
            switch(event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX=markEndX=event.getX();downY=markEndY=event.getY();
                    strokeStarted=true;invalidate();return true;
                case MotionEvent.ACTION_MOVE:
                    markEndX=event.getX();markEndY=event.getY();invalidate();return true;
                case MotionEvent.ACTION_UP:
                    markEndX=event.getX();markEndY=event.getY();strokeStarted=false;
                    RectF pageRect=getPageRect();
                    float x0=(downX-pageRect.left)/pageRect.width();
                    float y0=(downY-pageRect.top)/pageRect.height();
                    float x1=(markEndX-pageRect.left)/pageRect.width();
                    float y1=(markEndY-pageRect.top)/pageRect.height();
                    if(listener!=null && x0>=0 && x0<=1 && y0>=0 && y0<=1)
                        listener.onMarkup(x0,y0,Math.max(0,Math.min(1,x1)),
                            Math.max(0,Math.min(1,y1)),annotationTool==2);
                    invalidate();return true;
                case MotionEvent.ACTION_CANCEL: strokeStarted=false;invalidate();return true;
                default:return true;
            }
        }
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                longHeld=false;
                downX = lastX = event.getX();
                downY = lastY = event.getY();
                moved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if(longHeld){
                    magnifierX=event.getX();magnifierY=event.getY();
                    invalidate();return true;
                }
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
                if(longHeld){
                    showMagnifier(event.getX(),event.getY(),false);
                    longHeld=false;return true;
                }
                float swipeX = event.getX() - downX;
                float swipeY = event.getY() - downY;
                if (!scaleDetector.isInProgress() && zoom < 1.05f &&
                    Math.abs(swipeX)>dp(62) && Math.abs(swipeX)>Math.abs(swipeY)*1.3f) {
                    if (listener != null) listener.onPageRequested(swipeX<0);
                } else if (!moved) performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if(longHeld)showMagnifier(event.getX(),event.getY(),false);
                longHeld=false;return true;
            default: return true;
        }
    }
    @Override public boolean performClick() { super.performClick(); return true; }
}
