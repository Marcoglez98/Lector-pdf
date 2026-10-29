package com.lecturas.hojas;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.View;

/** Cinta de tela roja con punta en V y sombra, para las hojas y la estantería inferior. */
public final class RibbonTabView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thread = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ribbon = new Path();
    private String caption;
    private boolean miniature;
    private boolean selected;

    public RibbonTabView(Context context,String caption,boolean miniature,boolean selected) {
        super(context);
        this.caption=caption==null?"":caption;
        this.miniature=miniature;
        this.selected=selected;
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        setContentDescription("Cinta roja: "+this.caption);
        setClickable(true);
    }
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
    public void setCaption(String caption) {
        this.caption=caption==null?"":caption;
        setContentDescription("Cinta roja: "+this.caption);
        invalidate();
    }
    public void setSelectedRibbon(boolean selected){this.selected=selected;invalidate();}
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w=getWidth(),h=getHeight();
        float left=miniature?dp(5):dp(6),right=w-(miniature?dp(5):dp(6));
        float top=miniature?dp(1):dp(3),bottom=h-dp(3);
        float notch=miniature?dp(12):dp(13);
        ribbon.reset();
        ribbon.moveTo(left,top);ribbon.lineTo(right,top);
        ribbon.lineTo(right,bottom);
        ribbon.lineTo((left+right)/2,bottom-notch);
        ribbon.lineTo(left,bottom);ribbon.close();
        fill.setShadowLayer(dp(4),dp(1),dp(2),0x60000000);
        fill.setShader(new LinearGradient(left,top,right,bottom,
            selected?new int[]{0xFFB82339,0xFF8D1429,0xFF5B0D21}:
                     new int[]{0xFFAC233A,0xFF811528,0xFF661022},
            null,Shader.TileMode.CLAMP));
        canvas.drawPath(ribbon,fill);
        fill.clearShadowLayer();fill.setShader(null);
        // Luz reflejada y costuras finas como cinta de tela.
        thread.setStyle(Paint.Style.STROKE);
        thread.setStrokeWidth(dp(0.9f));
        thread.setColor(0x669F7880);
        canvas.drawLine(left+dp(5),top+dp(4),left+dp(5),bottom-dp(6),thread);
        canvas.drawLine(right-dp(5),top+dp(4),right-dp(5),bottom-dp(6),thread);
        thread.setStyle(Paint.Style.FILL);
        thread.setTypeface(android.graphics.Typeface.create("serif",android.graphics.Typeface.BOLD));
        thread.setColor(Color.WHITE);
        thread.setTextSize(miniature?dp(12):dp(12.3f));
        thread.setTextAlign(Paint.Align.CENTER);
        String label=caption;
        float max=w-(miniature?dp(8):dp(18));
        while(label.length()>2 && thread.measureText(label)>max)label=label.substring(0,label.length()-2)+"…";
        float baseline=miniature?top+dp(24):top+dp(23);
        canvas.drawText(label,w/2,baseline,thread);
        if(!miniature) {
            thread.setTypeface(android.graphics.Typeface.create("serif",android.graphics.Typeface.NORMAL));
            thread.setTextSize(dp(10));
            thread.setColor(0xFFD8A7AE);
            canvas.drawText("♥",w/2,top+dp(38),thread);
        }
    }
}
