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
    private int BACK,WHITE,MUTED,CYAN,PINK,PURPLE,CARD;
    private int BACK2,CARD2,EDGE,PROGRESS;
    private String titleFirst,titleSecond,subtitle;
    private String motif="✦";
    private final String theme;
    private void chooseTheme(String key){
        switch(key){
            case "aurora":
                BACK=0xFF061A28;BACK2=0xFF103647;WHITE=0xFFF5F1DA;
                MUTED=0xFFB7C6C3;CYAN=0xFF0DD7D0;PINK=0xFFFFB341;
                PURPLE=0xFF008B91;CARD=0xFF133C50;CARD2=0xFF104559;
                EDGE=0xFF488287;PROGRESS=0xFF0DD7D0;
                titleFirst="AURORA";titleSecond="LITERARIA";
                subtitle="Cada libro es un nuevo amanecer.\\nHistorias que iluminan tu día.";
                motif="☀";
                break;
            case "jardin":
                BACK=0xFFFFF7E9;BACK2=0xFFFFE7CB;WHITE=0xFF244D48;
                MUTED=0xFF72645B;CYAN=0xFF068F91;PINK=0xFFE11569;
                PURPLE=0xFFE11569;CARD=0xFFFFFDFC;CARD2=0xFFFFF1E5;
                EDGE=0xFFEBBDCC;PROGRESS=0xFF0A9CA5;
                titleFirst="JARDÍN";titleSecond="BOHEMIO";
                subtitle="Historias que florecen contigo.\\nUn rincón de flores y libros.";
                motif="✿";
                break;
            case "talavera":
                BACK=0xFFFFF4E4;BACK2=0xFFFFE9D1;WHITE=0xFF12356F;
                MUTED=0xFF6C5775;CYAN=0xFF1254C1;PINK=0xFFFF8300;
                PURPLE=0xFF1254C1;CARD=0xFFFFFFFF;CARD2=0xFFFFF6EF;
                EDGE=0xFF9CB3D3;PROGRESS=0xFFFF8300;
                titleFirst="TALAVERA";titleSecond="POP";
                subtitle="¡Libros que te llevan más lejos!\\nColor, tradición y lectura.";
                motif="❀";
                break;
            default:
                BACK=0xFF080F1A;BACK2=0xFF171130;WHITE=0xFFF2ECFF;
                MUTED=0xFFB1ACCF;CYAN=0xFF00D9FF;PINK=0xFFFF2D9B;
                PURPLE=0xFF9D4CFF;CARD=0xFF171A36;CARD2=0xFF192544;
                EDGE=0xFF453665;PROGRESS=0xFF00D9FF;
                titleFirst="NEÓN";titleSecond="LITERARIO";
                subtitle="Más que PDFs, mundos por vivir.\\nTus historias siempre contigo.";
                motif="✦";
        }
    }

    public NeonLibraryView(Activity activity,SharedPreferences prefs,String theme,Actions actions){
        super(activity);
        this.activity=activity;this.prefs=prefs;this.actions=actions;
        this.theme=theme;
        chooseTheme(theme);
        setFillViewport(true);
        setVerticalScrollBarEnabled(false);
        setBackground(gradient(BACK,BACK2,0));
        LinearLayout main=column();
        main.setPadding(dp(20),dp(18),dp(20),dp(28));
        addView(main,new LayoutParams(-1,-1));
        LinearLayout header=row();
        TextView logo=text(motif+"  HOJAS  /  LECTOR PDF",12,CYAN,true);
        logo.setLetterSpacing(.13f);
        header.addView(logo,new LinearLayout.LayoutParams(0,dp(42),1));
        TextView themes=button("◈  Temas",13,CYAN,CARD);
        themes.setOnClickListener(v->actions.changeTheme());
        header.addView(themes);
        add(main,header,0,0,0,20);
        TextView neon=text(titleFirst,38,CYAN,true);
        neon.setTypeface(Typeface.create("serif",Typeface.BOLD));
        if(theme.equals("neon")||theme.equals("aurora"))
            neon.setShadowLayer(dp(10),0,0,CYAN&0x77FFFFFF);
        add(main,neon,0,0,0,0);
        TextView title=text(titleSecond,38,PINK,true);
        title.setTypeface(Typeface.create("serif",Typeface.BOLD));
        if(theme.equals("neon"))
            title.setShadowLayer(dp(10),0,0,0x99DA169C);
        add(main,title,0,-8,0,13);
        TextView desc=text(subtitle,15,MUTED,false);
        desc.setLineSpacing(dp(3),1.1f);
        add(main,desc,0,0,0,24);
        TextView open=button("＋   ABRIR UN PDF",17,Color.WHITE,PURPLE);
        open.setBackground(gradient(PURPLE,theme.equals("neon")?0xFFC32A9E:PINK,15));
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
        search.setBackground(gradient(CARD,CARD2,13));
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
        TextView footer=text(motif+"  LEER  ·  EXPLORAR  ·  SENTIR  ·  CONTINUAR",
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
        if(rad>0)g.setStroke(dp(1),EDGE);
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
            filter.setBackground(gradient(on?PURPLE:CARD,on?PINK:CARD,12));
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
            empty.setBackground(gradient(CARD,CARD2,17));
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
        item.setBackground(gradient(CARD,CARD2,15));
        item.setElevation(dp(4));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        lp.setMargins(dp(index%2==0?0:6),0,dp(index%2==0?6:0),0);
        row.addView(item,lp);
        FrameLayout cover=new FrameLayout(activity);
        cover.setBackground(gradient(
            index%3==0?PURPLE:index%3==1?CYAN:PINK,CARD2,9));
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
        bar.setProgressTintList(ColorStateList.valueOf(index%2==0?PROGRESS:PINK));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(EDGE));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(5));
        bp.topMargin=dp(10);item.addView(bar,bp);
        item.setContentDescription("Abrir "+name);
        item.setOnClickListener(v->actions.open(Uri.parse(uri)));
    }
}
