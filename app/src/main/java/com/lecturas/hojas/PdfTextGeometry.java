package com.lecturas.hojas;

import android.graphics.RectF;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.text.TextPosition;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Asocia caracteres de PDFBox con las coordenadas reales de cada página.
 * Rectángulos 0..1 permiten guardar marcas independientes del zoom/pantalla.
 * En PDF rotados o con fuentes raras la alineación puede ser aproximada.
 */
public final class PdfTextGeometry {
    static final class Glyph {
        String text;
        RectF rect;
        Glyph(String text,RectF rect){this.text=text;this.rect=rect;}
    }
    public static final class Selection {
        public final String quote;
        public final JSONArray rects;
        public Selection(String quote,JSONArray rects){this.quote=quote;this.rects=rects;}
    }
    public static final class Page {
        final ArrayList<Glyph> glyphs = new ArrayList<>();
        public boolean isEmpty(){return glyphs.isEmpty();}
        private int nearest(float x,float y) {
            int best=-1;float distance=Float.MAX_VALUE;
            for(int i=0;i<glyphs.size();i++) {
                RectF r=glyphs.get(i).rect;
                float dx=Math.max(r.left-x,Math.max(0,x-r.right));
                float dy=Math.max(r.top-y,Math.max(0,y-r.bottom));
                float score=dy*dy*6f+dx*dx;
                if(score<distance){distance=score;best=i;}
            }
            // No seleccionar una línea totalmente lejana.
            return distance>0.028f ? -1 : best;
        }
        public Selection select(float x0,float y0,float x1,float y1){
            int first=nearest(x0,y0),last=nearest(x1,y1);
            if(first<0||last<0)return null;
            if(last<first){int n=first;first=last;last=n;}
            if(first==last) {
                while(first>0 && !isBreak(first-1,first) &&
                        !Character.isWhitespace(glyphs.get(first-1).text.charAt(0)))first--;
                while(last<glyphs.size()-1 && !isBreak(last,last+1) &&
                        !Character.isWhitespace(glyphs.get(last+1).text.charAt(0)))last++;
            }
            return build(first,last);
        }
        private boolean isBreak(int a,int b){
            RectF x=glyphs.get(a).rect,y=glyphs.get(b).rect;
            return Math.abs(x.centerY()-y.centerY())>0.019f || y.left<x.left-0.02f;
        }
        private Selection build(int start,int end) {
            if(start<0||end<start||end>=glyphs.size())return null;
            StringBuilder quote=new StringBuilder();
            JSONArray coords=new JSONArray();
            RectF line=null;
            Glyph prev=null;
            for(int i=start;i<=end;i++){
                Glyph g=glyphs.get(i);
                if(prev!=null) {
                    if(isBreak(i-1,i)) {quote.append(' ');if(line!=null){coords.put(toJson(line));line=null;}}
                    else if(g.rect.left-prev.rect.right>0.013f && !quote.toString().endsWith(" "))quote.append(' ');
                }
                quote.append(g.text);
                if(line==null)line=new RectF(g.rect);
                else line.union(g.rect);
                prev=g;
            }
            if(line!=null)coords.put(toJson(line));
            String phrase=quote.toString().replaceAll("\\s+"," ").trim();
            return phrase.isEmpty()?null:new Selection(phrase,coords);
        }
        public Selection matchQuote(String target) {
            if(target==null||target.trim().isEmpty())return null;
            String needle=target.replaceAll("\\s+"," ").trim().toLowerCase();
            StringBuilder str=new StringBuilder();
            ArrayList<Integer> positions=new ArrayList<>();
            for(int i=0;i<glyphs.size();i++) {
                Glyph g=glyphs.get(i);
                if(i>0 && (isBreak(i-1,i) ||
                        g.rect.left-glyphs.get(i-1).rect.right>0.013f)){
                    str.append(' ');positions.add(Math.max(0,i-1));
                }
                for(char c:g.text.toLowerCase().toCharArray()){str.append(c);positions.add(i);}
            }
            String full=str.toString();
            int at=full.indexOf(needle);
            if(at<0)return null;
            return build(positions.get(at),positions.get(at+needle.length()-1));
        }
    }
    public static Page extract(PDDocument pdf,int index) throws IOException {
        if(pdf==null)return new Page();
        PDPage pdPage=pdf.getPage(index);
        PDRectangle box=pdPage.getCropBox();
        int rotation=((pdPage.getRotation()%360)+360)%360;
        final float width=Math.max(1,(rotation==90||rotation==270)?box.getHeight():box.getWidth());
        final float height=Math.max(1,(rotation==90||rotation==270)?box.getWidth():box.getHeight());
        Page result=new Page();
        PDFTextStripper stripper=new PDFTextStripper(){
            @Override protected void writeString(String s,List<TextPosition> positions) throws IOException {
                for(TextPosition p:positions){
                    String value=p.getUnicode();
                    if(value==null || value.isEmpty() || value.trim().isEmpty())continue;
                    float x0=p.getXDirAdj()/width,y0=p.getYDirAdj()/height;
                    float x1=(p.getXDirAdj()+p.getWidthDirAdj())/width;
                    float y1=(p.getYDirAdj()+p.getHeightDir())/height;
                    RectF rect=new RectF(
                        Math.max(0,Math.min(1,x0)), Math.max(0,Math.min(1,y0)),
                        Math.max(0,Math.min(1,x1)), Math.max(0,Math.min(1,y1)));
                    if(rect.width()>0.0001f && rect.height()>0.0001f)
                        result.glyphs.add(new Glyph(value,rect));
                }
            }
        };
        stripper.setStartPage(index+1);
        stripper.setEndPage(index+1);
        stripper.setSortByPosition(true);
        stripper.getText(pdf);
        return result;
    }
    public static JSONObject toJson(RectF r){
        JSONObject o=new JSONObject();
        try {o.put("l",(double)r.left);o.put("t",(double)r.top);
             o.put("r",(double)r.right);o.put("b",(double)r.bottom);}
        catch(Exception ignored){}
        return o;
    }
    public static RectF fromJson(JSONObject o){
        if(o==null)return null;
        return new RectF((float)o.optDouble("l"),(float)o.optDouble("t"),
             (float)o.optDouble("r"),(float)o.optDouble("b"));
    }
    public static List<RectF> rectangles(JSONArray arr) {
        ArrayList<RectF> rectangles=new ArrayList<>();
        if(arr==null)return rectangles;
        for(int i=0;i<arr.length();i++){
            RectF r=fromJson(arr.optJSONObject(i));
            if(r!=null&&r.width()>0&&r.height()>0)rectangles.add(r);
        }
        return rectangles;
    }
    private PdfTextGeometry(){}
}
