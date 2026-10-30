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
    private boolean neonTheme=false;
    public void setNeonTheme(boolean enabled){neonTheme=enabled;invalidate();}

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
        if(w<1 || h<1)return;
        float edge=miniature?dp(3):dp(6);
        float left=edge,right=w-edge;
        float top=miniature?0:dp(3);
        float bottom=h-dp(3);
        float notch=miniature?dp(14):dp(13);
        ribbon.reset();
        ribbon.moveTo(left,top);
        ribbon.lineTo(right,top);
        ribbon.lineTo(right,bottom);
        ribbon.lineTo((left+right)/2,bottom-notch);
        ribbon.lineTo(left,bottom);
        ribbon.close();

        fill.setShadowLayer(dp(miniature?7:4),dp(2),dp(3),0x65000000);
        fill.setShader(new LinearGradient(left,top,right,bottom,
            neonTheme ? (selected?
                new int[]{0xFFFF78D8,0xFFFF2D9B,0xFFB01389}:
                new int[]{0xFFFC6CCA,0xFFE52CAA,0xFF942273}) :
            selected?new int[]{0xFFB92B43,0xFFA51631,0xFF650C23}:
                new int[]{0xFFAD283F,0xFF8B1630,0xFF650D27},
            null,Shader.TileMode.CLAMP));
        canvas.drawPath(ribbon,fill);
        fill.clearShadowLayer();
        fill.setShader(null);
        if(neonTheme){
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(dp(1.4f));
            fill.setColor(0xFFDDA0F6);
            canvas.drawPath(ribbon,fill);
            fill.setStyle(Paint.Style.FILL);
        }
        // Borde reflectante y dos costuras continuas, como una tira de satén.
        thread.setStyle(Paint.Style.STROKE);
        thread.setStrokeWidth(dp(miniature?1.1f:.9f));
        thread.setColor(neonTheme?0xD9FFE0FA:0x99FFD9D5);
        canvas.drawLine(left+dp(4),top+dp(5),left+dp(4),bottom-dp(9),thread);
        thread.setColor(0x77D16D78);
        canvas.drawLine(right-dp(4),top+dp(5),right-dp(4),bottom-dp(9),thread);
        thread.setStyle(Paint.Style.FILL);
        thread.setTypeface(android.graphics.Typeface.create("serif",android.graphics.Typeface.BOLD));
        thread.setTextAlign(Paint.Align.CENTER);
        thread.setColor(Color.WHITE);
        if(miniature){
            // Un único símbolo bordado arriba, sin cubrir la lectura con números.
            thread.setTextSize(dp(15));
            canvas.drawText("✦",w/2f,top+dp(25),thread);
            thread.setColor(0x88FFF3E7);
            thread.setStrokeWidth(dp(.8f));
            for(int y=(int)dp(42);y<bottom-dp(18);y+=(int)dp(23)){
                canvas.drawCircle(w/2f,y,dp(1.1f),thread);
            }
        }else{
            thread.setTextSize(dp(12.3f));
            String label=caption;
            float max=w-dp(18);
            while(label.length()>2 && thread.measureText(label)>max)
                label=label.substring(0,label.length()-2)+"…";
            canvas.drawText(label,w/2,top+dp(23),thread);
            thread.setTypeface(android.graphics.Typeface.create("serif",android.graphics.Typeface.NORMAL));
            thread.setTextSize(dp(10));
            thread.setColor(0xFFD8A7AE);
            canvas.drawText("♥",w/2,top+dp(38),thread);
        }
    }
}
