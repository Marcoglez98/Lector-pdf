package com.lecturas.hojas;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Locale;

/** Pantalla Neón Literario: portadas reales de PDF, progreso y búsqueda local. */
public class NeonLibraryView extends ScrollView {
    public interface Actions {
        void open(Uri uri);
        void addPdf();
        void changeTheme();
    }
    private final Activity activity;
    private final SharedPreferences prefs;
    private final Actions actions;
    private final LinearLayout grid;
    private final TextView[] filters=new TextView[3];
    private final EditText search;
    private String selected="Todos";
    private static final int BACK=0xFF080F1A,WHITE=0xFFF2ECFF,
        MUTED=0xFFB1ACCF,CYAN=0xFF00D9FF,PINK=0xFFFF2D9B,
        PURPLE=0xFF9D4CFF,CARD=0xFF171A36;

    public NeonLibraryView(Activity activity,SharedPreferences prefs,Actions actions){
        super(activity);
        this.activity=activity;this.prefs=prefs;this.actions=actions;
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setBackground(gradient(0xFF0A0F21,0xFF130E29,0));
        LinearLayout main=column();
        main.setPadding(dp(20),dp(18),dp(20),dp(28));
        addView(main,new LayoutParams(-1,-1));
        LinearLayout header=row();
        TextView logo=text("✦  HOJAS  /  LECTOR PDF",12,CYAN,true);
        logo.setLetterSpacing(.13f);
        header.addView(logo,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView themes=button("◈  Temas",13,CYAN,0xFF19233E);
        themes.setOnClickListener(v->actions.changeTheme());
        header.addView(themes);
        add(main,header,0,0,0,20);
        TextView neon=text("NEÓN",38,CYAN,true);
        neon.setTypeface(Typeface.create("serif",Typeface.BOLD));
        neon.setShadowLayer(dp(12),0,0,0x9900AFFF);
        add(main,neon,0,0,0,0);
        TextView title=text("LITERARIO",38,PINK,true);
        title.setTypeface(Typeface.create("serif",Typeface.BOLD));
        title.setShadowLayer(dp(12),0,0,0x99DA169C);
        add(main,title,0,-8,0,13);
        TextView desc=text("Más que PDFs, mundos por vivir.\nTus historias siempre contigo.",
            15,MUTED,false);
        desc.setLineSpacing(dp(3),1.1f);
        add(main,desc,0,0,0,24);
        TextView open=button("＋   ABRIR UN PDF",17,Color.WHITE,PURPLE);
        open.setBackground(gradient(0xFF5D3BD1,0xFFC32A9E,15));
        open.setElevation(dp(6));
        open.setOnClickListener(v->actions.addPdf());
        add(main,open,0,0,0,26);
        TextView library=text("Mi biblioteca",26,WHITE,true);
        library.setTypeface(Typeface.create("serif",Typeface.BOLD));
        add(main,library,0,0,0,12);
        search=new EditText(activity);
        search.setSingleLine(true);
        search.setTextSize(14);
        search.setHint("⌕  Buscar libros y títulos...");
        search.setHintTextColor(MUTED);
        search.setTextColor(WHITE);
        search.setPadding(dp(14),0,dp(14),0);
        search.setBackground(gradient(0xFF1E2644,0xFF13172D,13));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(47));
        sp.bottomMargin=dp(15);
        main.addView(search,sp);
        LinearLayout tabs=row();
        String[] names={"Todos","Leyendo","Favoritos"};
        for(int i=0;i<names.length;i++){
            final String item=names[i];
            TextView tab=button(item,12,WHITE,CARD);
            filters[i]=tab;
            tab.setOnClickListener(v->{selected=item;render();});
            LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(41),1);
            if(i<2)tp.rightMargin=dp(8);
            tabs.addView(tab,tp);
        }
        add(main,tabs,0,0,0,17);
        grid=column();
        main.addView(grid);
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){render();}
            public void afterTextChanged(Editable e){}
        });
        render();
        TextView footer=text("✧  LEER  ·  EXPLORAR  ·  SENTIR  ·  CONTINUAR",
            11,CYAN,true);
        footer.setLetterSpacing(.08f);footer.setGravity(Gravity.CENTER);
        add(main,footer,0,20,0,20);
        LinearLayout actionsRow=row();
        TextView addBook=button("＋ PDF",13,WHITE,CARD);
        addBook.setOnClickListener(v->actions.addPdf());
        TextView switcher=button("✦ Estilos",13,CYAN,CARD);
        switcher.setOnClickListener(v->actions.changeTheme());
        actionsRow.addView(addBook,new LinearLayout.LayoutParams(0,dp(46),1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1);
        p.leftMargin=dp(10);actionsRow.addView(switcher,p);
        main.addView(actionsRow);
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout column(){LinearLayout l=new LinearLayout(activity);l.setOrientation(1);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(activity);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private GradientDrawable gradient(int a,int b,int rad){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});
        g.setCornerRadius(dp(rad));
        if(rad>0)g.setStroke(dp(1),0xFF453665);
        return g;
    }
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(activity);
        t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private TextView button(String s,int size,int color,int fill){
        TextView t=text(s,size,color,true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(gradient(fill,fill,13));
        t.setPadding(dp(10),dp(8),dp(10),dp(8));
        return t;
    }
    private void add(LinearLayout parent,View v,int l,int top,int r,int bottom){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(dp(l),dp(top),dp(r),dp(bottom));
        parent.addView(v,lp);
    }
    private JSONArray history(){
        try{return new JSONArray(prefs.getString("library_history","[]"));}
        catch(Exception ex){return new JSONArray();}
    }
    private String key(String v){
        try{
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8));
            StringBuilder s=new StringBuilder();
            for(int i=0;i<12;i++)s.append(String.format(Locale.US,"%02x",digest[i]));
            return s.toString();
        }catch(Exception e){return Integer.toHexString(v.hashCode());}
    }
    private void render(){
        grid.removeAllViews();
        for(TextView filter:filters){
            boolean on=selected.contentEquals(filter.getText());
            filter.setBackground(gradient(on?0xFF6742CF:CARD,on?0xFFC62BA4:CARD,12));
            filter.setTextColor(on?Color.WHITE:MUTED);
        }
        JSONArray entries=history();
        ArrayList<JSONObject> books=new ArrayList<>();
        String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        for(int i=0;i<entries.length();i++){
            JSONObject obj=entries.optJSONObject(i);
            if(obj==null)continue;
            String uri=obj.optString("uri","");
            String title=obj.optString("title","Mi libro");
            if(!title.toLowerCase(Locale.ROOT).contains(q))continue;
            String id=key(uri);
            if(selected.equals("Leyendo") && prefs.getInt("position_"+id,0)<=0)continue;
            if(selected.equals("Favoritos")){
                try{
                    if(new JSONArray(prefs.getString("marks_"+id,"[]")).length()==0)continue;
                }catch(Exception e){continue;}
            }
            books.add(obj);
        }
        if(books.isEmpty()){
            TextView empty=text(entries.length()==0?
                "Tu próxima gran historia empieza aquí.\nAbre un PDF y llena tu biblioteca.":
                "Todavía no hay libros en esta sección.",16,MUTED,false);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(dp(5),1f);
            empty.setPadding(dp(18),dp(38),dp(18),dp(38));
            empty.setBackground(gradient(0xFF172445,0xFF10152B,17));
            add(grid,empty,0,5,0,12);
            return;
        }
        for(int i=0;i<books.size();i+=2){
            LinearLayout group=row();
            group.setGravity(Gravity.TOP);
            card(group,books.get(i),i);
            if(i+1<books.size())card(group,books.get(i+1),i+1);
            else group.addView(new View(activity),new LinearLayout.LayoutParams(0,1,1));
            add(grid,group,0,0,0,17);
        }
    }
    private void card(LinearLayout row,JSONObject data,int index){
        String uri=data.optString("uri","");
        String name=data.optString("title","Mi libro");
        String id=key(uri);
        LinearLayout item=column();
        item.setPadding(dp(8),dp(8),dp(8),dp(12));
        item.setBackground(gradient(0xFF192544,0xFF11162F,15));
        item.setElevation(dp(4));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        lp.setMargins(dp(index%2==0?0:6),0,dp(index%2==0?6:0),0);
        row.addView(item,lp);
        FrameLayout cover=new FrameLayout(activity);
        cover.setBackground(gradient(index%3==0?0xFF284A74:index%3==1?0xFF62235B:0xFF145E68,
            0xFF0C1536,9));
        item.addView(cover,new LinearLayout.LayoutParams(-1,dp(175)));
        File file=new File(new File(activity.getFilesDir(),"cover_thumbnails"),id+".jpg");
        if(file.exists()&&file.length()>1024){
            Bitmap picture=BitmapFactory.decodeFile(file.getAbsolutePath());
            if(picture!=null){
                ImageView image=new ImageView(activity);
                image.setImageBitmap(picture);
                image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cover.addView(image,new FrameLayout.LayoutParams(-1,-1));
            }
        }else{
            TextView art=text(index%2==0?"✧\n◈\n✦":"☾\n✦\n✧",34,
                index%2==0?CYAN:PINK,false);
            art.setShadowLayer(dp(12),0,0,index%2==0?CYAN:PINK);
            art.setGravity(Gravity.CENTER);
            cover.addView(art,new FrameLayout.LayoutParams(-1,-1));
        }
        TextView title=text(name,15,WHITE,true);
        title.setTypeface(Typeface.create("serif",Typeface.BOLD));
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(-1,dp(44));
        titleParams.topMargin=dp(8);
        item.addView(title,titleParams);
        int page=prefs.getInt("position_"+id,0);
        int total=prefs.getInt("pages_"+id,0);
        int percent=total>0?(int)Math.round(Math.max(0,Math.min(100,
            (page+1)*100.0/total))):0;
        TextView progress=text(total>0?"Pág. "+(page+1)+" de "+total:"Libro PDF",
            11,MUTED,false);
        item.addView(progress);
        ProgressBar bar=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);bar.setProgress(percent);
        bar.setProgressTintList(ColorStateList.valueOf(index%2==0?CYAN:PINK));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF393650));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(5));
        bp.topMargin=dp(10);item.addView(bar,bp);
        item.setContentDescription("Abrir "+name);
        item.setOnClickListener(v->actions.open(Uri.parse(uri)));
    }
}
