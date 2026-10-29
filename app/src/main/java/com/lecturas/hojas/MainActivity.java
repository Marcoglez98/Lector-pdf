package com.lecturas.hojas;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.speech.tts.Voice;
import java.util.List;
import java.util.ArrayList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hojas — lector offline de archivos PDF con modo libro, texto ajustable y
 * resaltados persistentes por documento. No modifica el archivo PDF original.
 */
public class MainActivity extends Activity {
    private static final int PICK_PDF = 1701;
    private static final int MENU_HIGHLIGHT = 707;
    private static final int INK = Color.rgb(57, 43, 34);
    private static final int MUTED = Color.rgb(126, 108, 91);
    private static final int ACCENT = Color.rgb(143, 94, 58);
    private static final int PAPER = Color.rgb(247, 241, 231);
    private static final int WHITE = Color.rgb(255, 252, 247);
    private static final int LINE = Color.rgb(225, 212, 197);
    private static final int DARK = Color.rgb(27, 31, 39);

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final HashMap<Integer, String> extractedTextCache = new HashMap<>();
    private SharedPreferences prefs;
    private FrameLayout screen;
    private PdfRenderer renderer;
    private ParcelFileDescriptor pdfDescriptor;
    private PDDocument pdfTextDocument;
    private File currentCachedFile;
    private Uri currentUri;
    private String bookKey = "";
    private String bookName = "";
    private int totalPages = 0;
    private int currentPage = 0;
    private boolean textMode = false;
    private boolean busy = false;
    private int requestVersion = 0;
    private int openVersion = 0;
    private int pendingScrollOffset = -1;
    private boolean pendingOpenAnimation = false;
    private float fontSizeSp = 21f;
    private boolean nightMode = false;
    private boolean animatedPages = true;
    private NarrationHelper narrator;
    private TextView narrationButton;
    private LinearLayout bookTools;
    private TextView markerButton, eraserButton;
    private int annotationMode=0;
    private PdfTextGeometry.Page currentGeometry;
    private int geometryPage=-1;

    private LinearLayout readerLayout;
    private FrameLayout readingSurface;
    private PageCurlView curlView;
    private ScrollView textScroll;
    private TextView textView;
    private TextView pageCounter;
    private TextView modeBookButton;
    private TextView modeTextButton;
    private TextView readingTitle;
    private SeekBar pageSeek;
    private ProgressBar pageLoading;
    private boolean readingFullscreen=false;
    private LinearLayout readerToolbar, readerModes, readerControls, ribbonShelf, ribbonSlots;
    private View readerBorder;
    private RibbonTabView pageRibbon;
    private GestureDetector textTapDetector;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        prefs = getSharedPreferences("hojas_reader_v1", MODE_PRIVATE);
        fontSizeSp = prefs.getFloat("fontsize",21f);
        nightMode = prefs.getBoolean("night",false);
        animatedPages = prefs.getBoolean("animate",true);
        PDFBoxResourceLoader.init(getApplicationContext());
        screen = new FrameLayout(this);
        setContentView(screen);
        narrator=new NarrationHelper(this,prefs,new NarrationHelper.Host(){
            @Override public void requestText(int page,java.util.function.Consumer<String> callback){
                final int opening=openVersion;
                io.execute(()->{
                    String body="";
                    try{body=extractText(page);}catch(Exception ignored){}
                    final String ready=body;
                    runOnUiThread(()->{
                        if(opening==openVersion && !isFinishing())callback.accept(ready);
                    });
                });
            }
            @Override public void showReadingPage(int page){
                if(page!=currentPage && !busy && renderer!=null)loadPageAt(page,animatedPages,page>currentPage);
            }
            @Override public void stateChanged(){updateNarrationButton();}
            @Override public void notice(String message){toast(message);}
        });
        showLibrary();
        if (state!=null && state.getString("openedUri")!=null) {
            openPdf(Uri.parse(state.getString("openedUri")));
        } else if (getIntent()!=null && getIntent().getData()!=null &&
                Intent.ACTION_VIEW.equals(getIntent().getAction())) {
            openPdf(getIntent().getData());
        }
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (readerLayout!=null && readerLayout.getParent()!=null && currentUri!=null)
            outState.putString("openedUri",currentUri.toString());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent!=null && intent.getData()!=null) openPdf(intent.getData());
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable bg(int fill, int corner, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(corner));
        if (stroke!=Color.TRANSPARENT) drawable.setStroke(dp(1),stroke);
        return drawable;
    }
    private TextView label(String content, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(content);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private TextView clickable(String content, int size, int fg, int fill, Runnable handler) {
        TextView t = label(content,size,fg,true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(fill,13,Color.TRANSPARENT));
        t.setPadding(dp(13),dp(10),dp(13),dp(10));
        t.setOnClickListener(v -> handler.run());
        return t;
    }
    private LinearLayout vertical() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private void margin(View v,LinearLayout parent,int l,int t,int r,int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l),dp(t),dp(r),dp(b)); parent.addView(v,p);
    }
    private View line() {
        View v=new View(this); v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(1)));
        return v;
    }
    private void setSystemBars(boolean dark) {
        getWindow().setStatusBarColor(dark ? DARK : PAPER);
        getWindow().setNavigationBarColor(dark ? DARK : PAPER);
        int f = dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(f);
    }

    private void showLibrary() {
        if(narrator!=null)narrator.stop();
        requestVersion++;
        readingFullscreen=false;
        setSystemBars(false);
        screen.removeAllViews();
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setBackgroundColor(PAPER);
        LinearLayout root=vertical(); root.setPadding(dp(24),dp(28),dp(24),dp(26));
        sc.addView(root);
        TextView brand=label("HOJAS  /  LECTOR PDF",12,ACCENT,true);
        brand.setLetterSpacing(0.17f); margin(brand,root,0,0,0,48);
        TextView logo=label("◕",62,ACCENT,false); logo.setTypeface(Typeface.SERIF);
        margin(logo,root,0,0,0,8);
        TextView header=label("Tu biblioteca,\na tu manera.",38,INK,true);
        header.setTypeface(Typeface.create("serif",Typeface.BOLD));
        margin(header,root,0,0,0,12);
        TextView sub=label("Abre tus libros en PDF, pasa sus hojas con suavidad y guarda las frases que quieras recordar.",16,MUTED,false);
        sub.setLineSpacing(dp(4),1.1f); margin(sub,root,0,0,0,29);
        TextView open=clickable("＋   Abrir un PDF",18,Color.WHITE,ACCENT,this::pickPdf);
        margin(open,root,0,0,0,28);

        String recent = prefs.getString("lastUri",null);
        if (recent!=null) {
            TextView recentTitle=label("CONTINUAR LEYENDO",12,ACCENT,true);
            recentTitle.setLetterSpacing(0.12f); margin(recentTitle,root,0,5,0,12);
            LinearLayout recentCard=vertical();
            recentCard.setPadding(dp(18),dp(17),dp(18),dp(17));
            recentCard.setBackground(bg(WHITE,17,LINE));
            TextView name=label("▤   "+prefs.getString("lastName","Mi libro"),17,INK,true);
            name.setMaxLines(2); name.setEllipsize(TextUtils.TruncateAt.END);
            recentCard.addView(name);
            TextView hint=label("Toca para continuar donde te quedaste",13,MUTED,false);
            LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(8);recentCard.addView(hint,hp);
            recentCard.setOnClickListener(v -> openPdf(Uri.parse(recent)));
            margin(recentCard,root,0,0,0,25);
        }
        LinearLayout tip=vertical();tip.setPadding(dp(17),dp(16),dp(17),dp(17));
        tip.setBackground(bg(0xFFEDE3D4,18,Color.TRANSPARENT));
        TextView tip1=label("✦  Un rincón para tus frases",16,INK,true);
        TextView tip2=label("En el modo Texto puedes mantener pulsada una palabra, ajustar la selección y elegir Resaltar. Tus frases se guardan en Favoritos.",14,MUTED,false);
        tip2.setLineSpacing(dp(3),1.1f);
        tip.addView(tip1);
        LinearLayout.LayoutParams x=new LinearLayout.LayoutParams(-1,-2);x.topMargin=dp(10);tip.addView(tip2,x);
        margin(tip,root,0,0,0,0);
        screen.addView(sc);
    }

    private void pickPdf() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("application/pdf");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try { startActivityForResult(i,PICK_PDF); }
        catch (ActivityNotFoundException ex) { toast("No se encontró una aplicación para elegir archivos."); }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if (request==PICK_PDF && result==RESULT_OK && data!=null && data.getData()!=null) {
            Uri u=data.getData();
            try { getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION); }
            catch (Exception ignored) { }
            openPdf(u);
        }
    }
    private String getFileName(Uri uri) {
        String name=null;
        try (Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if (c!=null && c.moveToFirst()) name=c.getString(0);
        } catch (Exception ignored) { }
        if (name==null || name.trim().isEmpty()) name=uri.getLastPathSegment();
        return name==null ? "Mi libro.pdf" : name;
    }
    private String hash(String str) {
        try {
            byte[] d=MessageDigest.getInstance("SHA-256").digest(str.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder s=new StringBuilder();
            for (int i=0;i<12;i++) s.append(String.format(Locale.US,"%02x",d[i]));
            return s.toString();
        } catch (Exception e) { return Integer.toHexString(str.hashCode()); }
    }
    private void openPdf(Uri uri) {
        if(narrator!=null)narrator.stop();
        annotationMode=0;currentGeometry=null;geometryPage=-1;
        final int version=++openVersion;
        requestVersion++;
        busy=true;
        currentUri=uri;
        bookKey=hash(uri.toString());
        bookName=getFileName(uri);
        if (bookName.toLowerCase(Locale.ROOT).endsWith(".pdf")) bookName=bookName.substring(0,bookName.length()-4);
        final String storedBookName=bookName;
        final String storedBookKey=bookKey;
        screen.removeAllViews();
        setSystemBars(false);
        LinearLayout loading=vertical(); loading.setGravity(Gravity.CENTER); loading.setPadding(dp(24),dp(24),dp(24),dp(24)); loading.setBackgroundColor(PAPER);
        TextView cover=label("◕",62,ACCENT,false);cover.setGravity(Gravity.CENTER);loading.addView(cover);
        ProgressBar progress=new ProgressBar(this); progress.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(40),dp(40));pp.gravity=Gravity.CENTER;pp.topMargin=dp(22);loading.addView(progress,pp);
        TextView wait=label("Preparando tus páginas…",17,INK,true);wait.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);wp.topMargin=dp(24);loading.addView(wait,wp);
        screen.addView(loading);
        io.execute(() -> {
            try {
                closeOpenedDocuments();
                File cached=new File(getCacheDir(),"hojas-"+storedBookKey+".pdf");
                try (InputStream in=getContentResolver().openInputStream(uri);
                     FileOutputStream out=new FileOutputStream(cached,false)) {
                    if (in==null) throw new IllegalStateException("No se puede abrir este archivo");
                    byte[] buf=new byte[65536]; int n;
                    while ((n=in.read(buf))!=-1) out.write(buf,0,n);
                }
                pdfDescriptor=ParcelFileDescriptor.open(cached,ParcelFileDescriptor.MODE_READ_ONLY);
                renderer=new PdfRenderer(pdfDescriptor);
                int pages=renderer.getPageCount();
                currentCachedFile=cached;
                // La extracción de texto se carga solo cuando el usuario la necesita.
                extractedTextCache.clear();
                runOnUiThread(() -> {
                    if (version!=openVersion || isFinishing()) return;
                    totalPages=pages;
                    currentPage=Math.min(pages-1,Math.max(0,prefs.getInt("position_"+storedBookKey,0)));
                    textMode=false;
                    prefs.edit().putString("lastUri",uri.toString()).putString("lastName",storedBookName).apply();
                    busy=false;
                    showReader();
                    loadPageAt(currentPage,false,true);
                });
            } catch (Exception e) {
                String message = e.getMessage()==null ? "No se pudo leer el PDF" : e.getMessage();
                closeOpenedDocuments();
                runOnUiThread(() -> {
                    if (version!=openVersion || isFinishing()) return;
                    busy=false; showLibrary();
                    new AlertDialog.Builder(this).setTitle("No se pudo abrir el PDF")
                        .setMessage(message+"\n\nComprueba que no esté protegido por contraseña.")
                        .setPositiveButton("Aceptar",null).show();
                });
            }
        });
    }
    private void closeOpenedDocuments() {
        try { if (pdfTextDocument!=null) pdfTextDocument.close(); } catch (Exception ignored) { }
        try { if (renderer!=null) renderer.close(); } catch (Exception ignored) { }
        try { if (pdfDescriptor!=null) pdfDescriptor.close(); } catch (Exception ignored) { }
        pdfTextDocument=null; renderer=null;pdfDescriptor=null;currentCachedFile=null;
    }

    private void showReader() {
        readingFullscreen=false;
        pendingOpenAnimation=true;
        setSystemBars(nightMode);
        screen.removeAllViews();
        readerLayout=vertical();readerLayout.setBackgroundColor(nightMode ? DARK : PAPER);
        screen.addView(readerLayout,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout toolbar=horizontal();
        readerToolbar=toolbar;
        toolbar.setPadding(dp(12),dp(9),dp(12),dp(9));
        TextView back=clickable("‹",29,nightMode?WHITE:INK,Color.TRANSPARENT,this::showLibrary);
        toolbar.addView(back,new LinearLayout.LayoutParams(dp(46),dp(48)));
        readingTitle=label(bookName,16,nightMode?WHITE:INK,true);
        readingTitle.setSingleLine(true);readingTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams=new LinearLayout.LayoutParams(0,-2,1); nameParams.leftMargin=dp(7);toolbar.addView(readingTitle,nameParams);
        TextView fav=clickable("☆",25,ACCENT,Color.TRANSPARENT,this::showFavorites);
        fav.setContentDescription("Abrir frases favoritas");toolbar.addView(fav);
        TextView immersion=clickable("⛶",22,nightMode?WHITE:INK,Color.TRANSPARENT,()->setReadingFullscreen(true));
        immersion.setContentDescription("Leer a pantalla completa; doble toque para mostrar las herramientas");
        toolbar.addView(immersion);
        TextView voice=clickable("🎙",22,nightMode?WHITE:INK,Color.TRANSPARENT,this::showVoiceDialog);
        voice.setContentDescription("Voces y descarga para narración");
        toolbar.addView(voice);
        TextView settings=clickable("⚙",22,nightMode?WHITE:INK,Color.TRANSPARENT,this::showSettings);
        settings.setContentDescription("Preferencias de lectura");toolbar.addView(settings);
        readerLayout.addView(toolbar);
        View border=line();readerBorder=border;if(nightMode) border.setBackgroundColor(0xFF41434B);readerLayout.addView(border);

        LinearLayout modes=horizontal();readerModes=modes;modes.setPadding(dp(18),dp(11),dp(18),dp(10));
        modeBookButton=clickable("▣  Libro",14,INK,WHITE,()->switchMode(false));
        modeTextButton=clickable("☷  Texto ajustable",14,INK,WHITE,()->switchMode(true));
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(44),1);a.rightMargin=dp(10);modes.addView(modeBookButton,a);
        modes.addView(modeTextButton,new LinearLayout.LayoutParams(0,dp(44),1.4f));
        readerLayout.addView(modes);

        bookTools=horizontal();
        bookTools.setPadding(dp(19),0,dp(19),dp(8));
        markerButton=clickable("✎  Marcador",14,ACCENT,WHITE,()->setAnnotationMode(annotationMode==1?0:1));
        eraserButton=clickable("⌫  Borrador",14,ACCENT,WHITE,()->setAnnotationMode(annotationMode==2?0:2));
        LinearLayout.LayoutParams markerParams=new LinearLayout.LayoutParams(0,dp(41),1);
        markerParams.rightMargin=dp(10);bookTools.addView(markerButton,markerParams);
        bookTools.addView(eraserButton,new LinearLayout.LayoutParams(0,dp(41),1));
        readerLayout.addView(bookTools);
        readingSurface=new FrameLayout(this);
        LinearLayout.LayoutParams center=new LinearLayout.LayoutParams(-1,0,1);
        readerLayout.addView(readingSurface,center);
        curlView=new PageCurlView(this);
        curlView.setListener(new PageCurlView.Listener() {
            @Override public void onPageRequested(boolean next) { navigate(next); }
            @Override public void onSelectionHint() {
                toast("Toca el Marcador para subrayar directamente en el libro.");
            }
            @Override public void onMarkup(float x0,float y0,float x1,float y1,boolean eraser){
                if(eraser)eraseBookMark(x1,y1);
                else highlightBook(x0,y0,x1,y1);
            }
            @Override public void onReaderTap(boolean doubleTap) {
                handleReaderTap(doubleTap);
            }
        });
        curlView.setPaperColor(nightMode?0xFF20232B:0xFFE9DECC,Color.WHITE);
        readingSurface.addView(curlView,new FrameLayout.LayoutParams(-1,-1));
        textScroll=new ScrollView(this);
        textScroll.setFillViewport(true);
        textScroll.setBackgroundColor(nightMode?DARK:WHITE);
        textView=label("",(int)fontSizeSp,nightMode?0xFFF3F0EC:INK,false);
        textView.setTypeface(Typeface.SERIF);
        textView.setTextSize(fontSizeSp);
        textView.setGravity(Gravity.TOP | Gravity.START);
        textView.setTextIsSelectable(true);
        textView.setLineSpacing(dp(7),1.16f);
        textView.setPadding(dp(23),dp(27),dp(23),dp(36));
        textView.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(0,MENU_HIGHLIGHT,3,"✦ Resaltar y guardar").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                return true;
            }
            @Override public boolean onPrepareActionMode(ActionMode mode,Menu menu) { return false; }
            @Override public boolean onActionItemClicked(ActionMode mode,MenuItem item) {
                if (item.getItemId()!=MENU_HIGHLIGHT) return false;
                int start=textView.getSelectionStart(),end=textView.getSelectionEnd();
                CharSequence t=textView.getText();
                if (start>=0 && end>start && end<=t.length()) {
                    String quote=t.subSequence(start,end).toString();
                    int capturedPage=currentPage;
                    showHighlightPicker(capturedPage,start,end,quote);
                } else toast("Selecciona una frase primero.");
                mode.finish();
                return true;
            }
            @Override public void onDestroyActionMode(ActionMode mode) { }
        });
        textTapDetector=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onSingleTapConfirmed(MotionEvent e){
                handleReaderTap(false);return false;
            }
            @Override public boolean onDoubleTap(MotionEvent e){
                handleReaderTap(true);return true;
            }
        });
        // No consumir los eventos: mantener selección de texto y desplazamiento nativos.
        textView.setOnTouchListener((v,e)->{textTapDetector.onTouchEvent(e);return false;});
        textScroll.setOnTouchListener((v,e)->{textTapDetector.onTouchEvent(e);return false;});
        textScroll.addView(textView,new ScrollView.LayoutParams(-1,-2));
        readingSurface.addView(textScroll,new FrameLayout.LayoutParams(-1,-1));
        pageLoading=new ProgressBar(this);
        pageLoading.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(42),dp(42),Gravity.CENTER);
        readingSurface.addView(pageLoading,lp);
        pageRibbon=new RibbonTabView(this,"",true,true);
        FrameLayout.LayoutParams ribbonPosition=new FrameLayout.LayoutParams(dp(56),dp(87),
            Gravity.TOP|Gravity.END);
        ribbonPosition.setMargins(0,dp(2),dp(18),0);
        readingSurface.addView(pageRibbon,ribbonPosition);
        pageRibbon.setOnClickListener(v->showCurrentRibbon());
        pageRibbon.setVisibility(View.GONE);

        LinearLayout controls=horizontal();readerControls=controls;controls.setPadding(dp(12),dp(7),dp(12),dp(7));
        TextView smaller=clickable("A−",16,nightMode?WHITE:INK,Color.TRANSPARENT,()->changeSize(-1));
        TextView larger=clickable("A+",16,nightMode?WHITE:INK,Color.TRANSPARENT,()->changeSize(+1));
        TextView previous=clickable("‹",29,nightMode?WHITE:INK,Color.TRANSPARENT,()->navigate(false));
        TextView following=clickable("›",29,nightMode?WHITE:INK,Color.TRANSPARENT,()->navigate(true));
        narrationButton=clickable("▶ Leer",15,Color.WHITE,ACCENT,this::toggleNarration);
        controls.addView(narrationButton);
        controls.addView(smaller);controls.addView(larger);
        pageSeek=new SeekBar(this);pageSeek.setMax(Math.max(0,totalPages-1));
        pageSeek.setProgressTintList(ColorStateList.valueOf(ACCENT));
        pageSeek.setThumbTintList(ColorStateList.valueOf(ACCENT));
        pageSeek.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        LinearLayout.LayoutParams seekParams=new LinearLayout.LayoutParams(0,dp(38),1);
        seekParams.leftMargin=dp(4);seekParams.rightMargin=dp(4);controls.addView(pageSeek,seekParams);
        pageSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private boolean touched=false;
            @Override public void onProgressChanged(SeekBar bar,int value,boolean user) { }
            @Override public void onStartTrackingTouch(SeekBar bar) { touched=true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                if (touched && !busy) loadPageAt(bar.getProgress(),animatedPages,bar.getProgress()>currentPage);
                touched=false;
            }
        });
        controls.addView(previous);controls.addView(following);
        readerLayout.addView(controls);
        createRibbonShelf();
        pageCounter=label("",12,nightMode?0xFFBABAC0:MUTED,false);
        pageCounter.setGravity(Gravity.CENTER);
        pageCounter.setPadding(0,0,0,dp(12));
        readerLayout.addView(pageCounter);
        updateModeUI();updatePageUI();setLoading(false);updateNarrationButton();
        refreshRibbonViews();
    }

    private void updateModeUI() {
        if (curlView==null) return;
        if(bookTools!=null)bookTools.setVisibility(textMode||readingFullscreen?View.GONE:View.VISIBLE);
        curlView.setAnnotationTool(textMode?0:annotationMode);
        updateAnnotationButtons();
        curlView.setVisibility(textMode?View.GONE:View.VISIBLE);
        textScroll.setVisibility(textMode?View.VISIBLE:View.GONE);
        modeBookButton.setBackground(bg(textMode?(nightMode?0xFF353943:WHITE):ACCENT,13,Color.TRANSPARENT));
        modeTextButton.setBackground(bg(textMode?ACCENT:(nightMode?0xFF353943:WHITE),13,Color.TRANSPARENT));
        modeBookButton.setTextColor(textMode?(nightMode?WHITE:INK):Color.WHITE);
        modeTextButton.setTextColor(textMode?Color.WHITE:(nightMode?WHITE:INK));
        if (!textMode) curlView.setPaperColor(nightMode?0xFF20232B:0xFFE9DECC,Color.WHITE);
        refreshRibbonViews();
    }
    private void updatePageUI() {
        if (pageCounter!=null) pageCounter.setText("PÁGINA "+(currentPage+1)+" DE "+totalPages+"     ·     " +
                (textMode?"MANTÉN PULSADO PARA RESALTAR":annotationMode==1?"ARRASTRA PARA SUBRAYAR":annotationMode==2?"TOCA LA MARCA PARA BORRAR":"DESLIZA PARA PASAR HOJA"));
        if (pageSeek!=null) pageSeek.setProgress(currentPage);
    }
    private void setLoading(boolean isLoading) {
        if (pageLoading!=null) pageLoading.setVisibility(isLoading?View.VISIBLE:View.GONE);
    }
    private void switchMode(boolean useText) {
        if (busy || totalPages==0 || useText==textMode) return;
        textMode=useText;
        updateModeUI();updatePageUI();
        loadPageAt(currentPage,false,true);
    }
    private void changeSize(int direction) {
        if (textMode) {
            fontSizeSp=Math.max(15f,Math.min(34f,fontSizeSp+direction*2));
            textView.setTextSize(fontSizeSp);
            prefs.edit().putFloat("fontsize",fontSizeSp).apply();
            toast("Tamaño de letra: "+(int)fontSizeSp);
        } else {
            curlView.setZoom(curlView.getZoom() + direction*0.2f);
            toast("Zoom de página: "+Math.round(curlView.getZoom()*100)+"%");
        }
    }
    private void navigate(boolean next) {
        if (busy) return;
        if(narrator!=null)narrator.onManualPageChange();
        int target=currentPage+(next?1:-1);
        if (target<0 || target>=totalPages) {
            toast(next?"Llegaste al final del libro":"Estás en la primera página");
            return;
        }
        loadPageAt(target,animatedPages,next);
    }
    private void loadPageAt(int index, boolean withAnimation,boolean forward) {
        if (busy || index<0 || index>=totalPages) return;
        busy=true;setLoading(true);
        final int loadRequest=++requestVersion;
        final boolean loadText=textMode;
        io.execute(() -> {
            try {
                if (loadText) {
                    String text=extractText(index);
                    runOnUiThread(() -> {
                        if (loadRequest!=requestVersion || isFinishing()) return;
                        currentPage=index;
                        showPageText(text);
                        updatePageUI();rememberPosition();
                        refreshRibbonViews();
                        busy=false;setLoading(false);
                    });
                } else {
                    Bitmap bitmap=renderBitmap(index);
                    PdfTextGeometry.Page geometry=new PdfTextGeometry.Page();
                    try{
                        if(pdfTextDocument==null && currentCachedFile!=null)
                            pdfTextDocument=PDDocument.load(currentCachedFile);
                        if(pdfTextDocument!=null)geometry=PdfTextGeometry.extract(pdfTextDocument,index);
                    }catch(Exception ignored){}
                    final PdfTextGeometry.Page readyGeometry=geometry;
                    runOnUiThread(() -> {
                        if (loadRequest!=requestVersion || isFinishing()) { bitmap.recycle(); return; }
                        setLoading(false);
                        curlView.turnTo(bitmap,forward,withAnimation && animatedPages,() -> {
                            currentPage=index;currentGeometry=readyGeometry;geometryPage=index;
                            refreshBookMarks();updatePageUI();rememberPosition();busy=false;
                            refreshRibbonViews();
                            if (pendingOpenAnimation) {
                                pendingOpenAnimation=false;
                                curlView.openBook();
                            }
                        });
                    });
                }
            } catch (Exception ex) {
                String msg=ex.getMessage();
                runOnUiThread(() -> {
                    if (loadRequest!=requestVersion || isFinishing()) return;
                    busy=false;setLoading(false);
                    toast("Error en página "+(index+1)+": "+(msg==null?"PDF no compatible":msg));
                });
            }
        });
    }
    private Bitmap renderBitmap(int pageNumber) throws Exception {
        if (renderer==null) throw new IllegalStateException("El libro ya no está abierto");
        try (PdfRenderer.Page pg=renderer.openPage(pageNumber)) {
            float w=pg.getWidth(),h=pg.getHeight();
                float minTarget=Math.min(2100f,Math.max(1150f,getResources().getDisplayMetrics().widthPixels * 1.6f));
            float scale=Math.min(2500f / Math.max(w,h),minTarget/w);
            int outW=Math.max(1,(int)(w*scale));
            int outH=Math.max(1,(int)(h*scale));
            Bitmap bmp=Bitmap.createBitmap(outW,outH,Bitmap.Config.ARGB_8888);
            bmp.eraseColor(Color.WHITE);
            pg.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bmp;
        }
    }
    private String extractText(int index) throws Exception {
        String old=extractedTextCache.get(index);if(old!=null) return old;
        if (pdfTextDocument==null) {
            if (currentCachedFile==null) throw new IllegalStateException("No se pudo extraer el texto");
            pdfTextDocument=PDDocument.load(currentCachedFile);
        }
        PDFTextStripper stripper=new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(index+1);stripper.setEndPage(index+1);
        String raw=stripper.getText(pdfTextDocument);
        if (raw==null || raw.trim().isEmpty()) raw="Esta página no contiene texto seleccionable. Es posible que el PDF sea una imagen escaneada; puedes verla en modo Libro, pero para seleccionar frases necesitaría reconocimiento de texto (OCR).";
        if (extractedTextCache.size()>12) extractedTextCache.clear();
        extractedTextCache.put(index,raw);
        return raw;
    }
    private void showPageText(String body) {
        if (textView==null) return;
        JSONArray highlights=readMarks();
        SpannableString styled=new SpannableString(body);
        for (int i=0;i<highlights.length();i++) {
            JSONObject mark=highlights.optJSONObject(i);
            if (mark==null || mark.optInt("page",-1)!=currentPage) continue;
            int start=mark.optInt("start",-1),end=mark.optInt("end",-1);
            String quote=mark.optString("quote","");
            if (start<0 || end<=start || end>body.length() || !body.substring(start,end).equals(quote)) {
                start=body.indexOf(quote);
                end=start+quote.length();
            }
            if (start>=0 && end<=body.length() && start<end) {
                styled.setSpan(new BackgroundColorSpan(mark.optInt("color",0xFFFFE69A)),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        textView.setText(styled,TextView.BufferType.SPANNABLE);
        textView.setTextIsSelectable(!body.startsWith("Esta página no contiene texto seleccionable."));
        textView.setTextSize(fontSizeSp);
        textView.setTextColor(nightMode?0xFFF3F0EC:INK);
        textScroll.scrollTo(0,0);
        int scrollOffset=pendingScrollOffset;
        pendingScrollOffset=-1;
        if (scrollOffset>=0) textView.post(() -> {
            Layout l=textView.getLayout();
            if (l!=null && l.getLineCount()>0) {
                int offset=Math.min(scrollOffset,textView.length());
                textScroll.smoothScrollTo(0,Math.max(0,l.getLineTop(l.getLineForOffset(offset))-dp(50)));
            }
        });
    }
    private void rememberPosition() {
        prefs.edit().putInt("position_"+bookKey,currentPage).apply();
    }
    private JSONArray readMarks() {
        try { return new JSONArray(prefs.getString("marks_"+bookKey,"[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }
    private void showHighlightPicker(int page,int start,int end,String quote) {
        new AlertDialog.Builder(this).setTitle("Color del resaltado")
            .setItems(new String[]{"Amarillo suave","Verde salvia","Rosa claro"},(d,which) -> {
                int[] colors={0xFFFFE69A,0xFFBEDFC1,0xFFF6C4CE};
                saveHighlight(page,start,end,quote,colors[which]);
            }).show();
    }
    private void saveHighlight(int page,int start,int end,String quote,int color) {
        JSONArray data=readMarks();
        for (int i=0;i<data.length();i++) {
            JSONObject old=data.optJSONObject(i);
            if (old!=null && old.optInt("page",-1)==page && old.optInt("start",-1)==start && old.optString("quote").equals(quote)) {
                toast("Esta frase ya está en favoritos");return;
            }
        }
        try {
            JSONObject item=new JSONObject();
            item.put("page",page);item.put("start",start);item.put("end",end);
            item.put("quote",quote);item.put("color",color);
            item.put("time",System.currentTimeMillis());
            data.put(item);
            prefs.edit().putString("marks_"+bookKey,data.toString()).apply();
            String currentBody=textView.getText().toString();
            if (currentPage==page && textMode) showPageText(currentBody);
            toast("✦ Frase resaltada y guardada en Favoritos");
        } catch (Exception e) { toast("No se pudo guardar la frase"); }
    }

    private void showFavorites() {
        JSONArray marks=readMarks();
        if (marks.length()==0) {
            new AlertDialog.Builder(this).setTitle("Tus frases favoritas")
                .setMessage("Todavía no tienes frases guardadas en este libro. Entra al modo Texto, mantén pulsada una palabra y elige Resaltar y guardar.")
                .setPositiveButton("Entendido",null).show();
            return;
        }
        ScrollView sc=new ScrollView(this);
        LinearLayout list=vertical();list.setPadding(dp(16),dp(12),dp(16),dp(8));
        sc.addView(list);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("✦ Mis frases favoritas")
            .setView(sc).setNegativeButton("Cerrar",null).create();
        for (int i=0;i<marks.length();i++) {
            JSONObject item=marks.optJSONObject(i);if(item==null) continue;
            final int position=i;
            int page=item.optInt("page",0);
            LinearLayout card=vertical();card.setPadding(dp(14),dp(14),dp(14),dp(12));
            card.setBackground(bg(nightMode?0xFF333B42:WHITE,12,LINE));
            TextView quote=label("“"+item.optString("quote","").trim()+"”",16,nightMode?WHITE:INK,false);
            quote.setTypeface(Typeface.SERIF);quote.setMaxLines(5);quote.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(quote);
            LinearLayout lower=horizontal();
            TextView foot=label("Página "+(page+1)+"  ·  Abrir",12,ACCENT,true);
            lower.addView(foot,new LinearLayout.LayoutParams(0,-2,1));
            TextView share=clickable("↗",18,ACCENT,Color.TRANSPARENT,()->shareQuote(item.optString("quote"),page));
            share.setContentDescription("Compartir frase");lower.addView(share);
            TextView delete=clickable("×",22,MUTED,Color.TRANSPARENT,()->{
                removeMark(position);dialog.dismiss();showFavorites();
            });
            delete.setContentDescription("Eliminar frase guardada");lower.addView(delete);
            LinearLayout.LayoutParams row=new LinearLayout.LayoutParams(-1,-2);row.topMargin=dp(9);card.addView(lower,row);
            card.setOnClickListener(v->{
                dialog.dismiss();pendingScrollOffset=item.optInt("start",0);
                if(!textMode && item.optJSONArray("rects")!=null)loadPageAt(page,false,true);
                else {
                    if(!textMode){textMode=true;updateModeUI();updatePageUI();}
                    loadPageAt(page,false,true);
                }
            });
            LinearLayout.LayoutParams margins=new LinearLayout.LayoutParams(-1,-2);
            margins.bottomMargin=dp(10);list.addView(card,margins);
        }
        dialog.show();
    }
    private void removeMark(int index) {
        JSONArray old=readMarks(), updated=new JSONArray();
        for (int i=0;i<old.length();i++) if (i!=index) updated.put(old.opt(i));
        prefs.edit().putString("marks_"+bookKey,updated.toString()).apply();
        if (textMode) showPageText(textView.getText().toString());
        else refreshBookMarks();
    }
    private void shareQuote(String quote,int page) {
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT,"“"+quote+"”\n\n— "+bookName+", página "+(page+1));
        startActivity(Intent.createChooser(send,"Compartir frase"));
    }
    // MARCADOR SOBRE EL PDF: conservar coordenadas normalizadas por documento y página.
    private void setAnnotationMode(int mode) {
        if(textMode)return;
        annotationMode=mode;
        if(curlView!=null)curlView.setAnnotationTool(annotationMode);
        updateAnnotationButtons();
        updatePageUI();
        if(mode==1)toast("Arrastra el dedo sobre una frase para subrayarla.");
        if(mode==2)toast("Toca la frase coloreada para borrarla.");
    }
    private void updateAnnotationButtons(){
        if(markerButton==null||eraserButton==null)return;
        markerButton.setBackground(bg(annotationMode==1?ACCENT:WHITE,13,Color.TRANSPARENT));
        eraserButton.setBackground(bg(annotationMode==2?ACCENT:WHITE,13,Color.TRANSPARENT));
        markerButton.setTextColor(annotationMode==1?Color.WHITE:ACCENT);
        eraserButton.setTextColor(annotationMode==2?Color.WHITE:ACCENT);
    }
    private List<RectF> markRects(JSONObject mark) {
        JSONArray explicit=mark.optJSONArray("rects");
        if(explicit!=null)return PdfTextGeometry.rectangles(explicit);
        if(currentGeometry!=null){
            PdfTextGeometry.Selection match=currentGeometry.matchQuote(mark.optString("quote",""));
            if(match!=null)return PdfTextGeometry.rectangles(match.rects);
        }
        return new ArrayList<>();
    }
    private void refreshBookMarks(){
        if(curlView==null)return;
        List<PageCurlView.Ink> visible=new ArrayList<>();
        JSONArray data=readMarks();
        for(int i=0;i<data.length();i++){
            JSONObject mark=data.optJSONObject(i);
            if(mark!=null && mark.optInt("page",-1)==currentPage){
                List<RectF> rects=markRects(mark);
                if(!rects.isEmpty())visible.add(new PageCurlView.Ink(rects,
                    mark.optInt("color",0xFFFFE69A)));
            }
        }
        curlView.setMarks(visible);
    }
    private void highlightBook(float x0,float y0,float x1,float y1){
        if(busy || geometryPage!=currentPage || currentGeometry==null){
            toast("Preparando selección de texto en esta hoja.");return;
        }
        PdfTextGeometry.Selection selected=currentGeometry.select(x0,y0,x1,y1);
        if(selected==null){
            toast("No encontré texto en esa parte del PDF. Prueba otra frase.");return;
        }
        int page=currentPage,color=0xFFFFD96B;
        for(int i=0;i<readMarks().length();i++){
            JSONObject existing=readMarks().optJSONObject(i);
            if(existing!=null && existing.optInt("page",-1)==page &&
                existing.optString("quote","").equals(selected.quote)){
                toast("Esa frase ya está subrayada.");return;
            }
        }
        curlView.animateInk(PdfTextGeometry.rectangles(selected.rects),color,false,()->{
            try {
                JSONObject item=new JSONObject();
                item.put("page",page);item.put("start",-1);item.put("end",-1);
                item.put("quote",selected.quote);item.put("rects",selected.rects);
                item.put("color",color);item.put("time",System.currentTimeMillis());
                JSONArray all=readMarks();all.put(item);
                prefs.edit().putString("marks_"+bookKey,all.toString()).apply();
                refreshBookMarks();
                toast("✦ Frase subrayada y guardada en Favoritos.");
            }catch(Exception ex){toast("No se pudo guardar la marca.");}
        });
    }
    private void eraseBookMark(float x,float y){
        if(busy)return;
        JSONArray data=readMarks();
        for(int i=data.length()-1;i>=0;i--){
            JSONObject item=data.optJSONObject(i);
            if(item==null||item.optInt("page",-1)!=currentPage)continue;
            List<RectF> rects=markRects(item);
            for(RectF rect:rects){
                if(new RectF(rect.left-0.016f,rect.top-0.016f,
                    rect.right+0.016f,rect.bottom+0.016f).contains(x,y)){
                    final int index=i;
                    curlView.animateInk(rects,item.optInt("color",0xFFFFD96B),true,()->{
                        removeMark(index);toast("Subrayado borrado.");
                    });
                    return;
                }
            }
        }
        toast("Toca una frase resaltada para usar el borrador.");
    }

    // NARRADOR: voces provistas por el motor de síntesis instalado en Android.
    private void updateNarrationButton(){
        if(narrationButton==null||narrator==null)return;
        narrationButton.setText(narrator.isPaused()?"▶ Continuar":
            narrator.isPlaying()?"Ⅱ Pausar":"▶ Leer");
    }
    private void toggleNarration(){
        if(narrator==null)return;
        if(!narrator.isReady()){showVoiceDialog();return;}
        if(narrator.isPaused()){narrator.resume();return;}
        if(narrator.isPlaying()){narrator.pause();return;}
        narrator.start(currentPage,totalPages);
    }
    private void showVoiceDialog(){
        if(narrator==null)return;
        ScrollView rootScroll=new ScrollView(this);
        LinearLayout panel=vertical();
        panel.setPadding(dp(18),dp(4),dp(18),dp(6));
        rootScroll.addView(panel);
        TextView intro=label("Elige una voz en español. La calidad y las voces sin conexión dependen del motor de Android.",14,MUTED,false);
        intro.setLineSpacing(dp(2),1.1f);
        margin(intro,panel,0,4,0,14);
        List<Voice> voices=narrator.getVoices();
        if(voices.isEmpty()){
            margin(label("Aún no hay voces en español disponibles. Descarga datos del motor de voz.",15,INK,false),
                panel,0,0,0,12);
        } else {
            TextView heading=label("VOCES DISPONIBLES ("+voices.size()+")",12,ACCENT,true);
            margin(heading,panel,0,0,0,8);
            ScrollView voiceScroll=new ScrollView(this);
            LinearLayout voiceRows=vertical();
            voiceScroll.addView(voiceRows);
            for(Voice voice:voices){
                String prefix=voice.getName().equals(narrator.selectedVoice())?"●  ":"○  ";
                TextView option=clickable(prefix+narrator.voiceLabel(voice),13,INK,WHITE,()->{
                    if(narrator.selectVoice(voice.getName())){
                        toast("Voz seleccionada: "+voice.getLocale().toLanguageTag());
                        for(int j=0;j<voiceRows.getChildCount();j++){
                            TextView row=(TextView)voiceRows.getChildAt(j);
                            Voice v=voices.get(j);
                            row.setText((v.getName().equals(narrator.selectedVoice())?"●  ":"○  ")+narrator.voiceLabel(v));
                        }
                    }else toast("La voz requiere descargar sus datos.");
                });
                option.setGravity(Gravity.CENTER_VERTICAL);
                option.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
                option.setPadding(dp(12),dp(8),dp(10),dp(8));
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
                p.bottomMargin=dp(6);voiceRows.addView(option,p);
            }
            panel.addView(voiceScroll,new LinearLayout.LayoutParams(-1,dp(244)));
        }
        LinearLayout actions=horizontal();
        TextView sample=clickable("▶ Muestra",14,Color.WHITE,ACCENT,()->narrator.preview());
        TextView download=clickable("↓ Descargar voces",14,ACCENT,0xFFECE0CF,()->narrator.installVoiceData());
        LinearLayout.LayoutParams c=new LinearLayout.LayoutParams(0,dp(47),1);
        c.rightMargin=dp(8);actions.addView(sample,c);
        actions.addView(download,new LinearLayout.LayoutParams(0,dp(47),1.2f));
        margin(actions,panel,0,12,0,14);
        TextView title=label("VELOCIDAD DE NARRACIÓN",12,ACCENT,true);
        margin(title,panel,0,0,0,3);
        TextView speed=label(String.format(Locale.US,"%.2f×",narrator.getRate()),15,INK,true);
        margin(speed,panel,0,0,0,0);
        SeekBar control=new SeekBar(this);
        control.setMax(85);control.setProgress(Math.round(narrator.getRate()*100)-65);
        control.setProgressTintList(ColorStateList.valueOf(ACCENT));
        control.setThumbTintList(ColorStateList.valueOf(ACCENT));
        control.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar s,int p,boolean fromUser){
                if(fromUser){float rate=(65+p)/100f;narrator.setRate(rate);
                    speed.setText(String.format(Locale.US,"%.2f×",rate));}
            }
            @Override public void onStartTrackingTouch(SeekBar s){}
            @Override public void onStopTrackingTouch(SeekBar s){}
        });
        panel.addView(control);
        new AlertDialog.Builder(this).setTitle("🎙 Narrador · Voces").setView(rootScroll)
            .setPositiveButton("Listo",null).show();
    }


    // MODO LECTURA: un toque oculta los controles, dos toques los restauran.
    private void handleReaderTap(boolean doubleTap){
        if(doubleTap){
            if(readingFullscreen)setReadingFullscreen(false);
        } else if(!readingFullscreen && annotationMode==0) {
            setReadingFullscreen(true);
        }
    }
    private void setReadingFullscreen(boolean enabled){
        if(readerLayout==null||readerLayout.getParent()==null)return;
        if(readingFullscreen==enabled)return;
        readingFullscreen=enabled;
        if(enabled && annotationMode!=0){
            annotationMode=0;
            if(curlView!=null)curlView.setAnnotationTool(0);
            updateAnnotationButtons();
        }
        int visibility=enabled?View.GONE:View.VISIBLE;
        if(readerToolbar!=null)readerToolbar.setVisibility(visibility);
        if(readerBorder!=null)readerBorder.setVisibility(visibility);
        if(readerModes!=null)readerModes.setVisibility(visibility);
        if(readerControls!=null)readerControls.setVisibility(visibility);
        if(pageCounter!=null)pageCounter.setVisibility(visibility);
        if(ribbonShelf!=null)ribbonShelf.setVisibility(visibility);
        if(bookTools!=null)bookTools.setVisibility(enabled||textMode?View.GONE:View.VISIBLE);
        if(textView!=null) {
            textView.setPadding(dp(enabled?15:23),dp(enabled?12:27),
                dp(enabled?15:23),dp(enabled?18:36));
        }
        if(curlView!=null)curlView.setFullPage(enabled);
        refreshRibbonViews();
        if(enabled){
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }else setSystemBars(nightMode);
    }

    private JSONArray readRibbons() {
        try{return new JSONArray(prefs.getString("ribbons_"+bookKey,"[]"));}
        catch(Exception e){return new JSONArray();}
    }
    private void writeRibbons(JSONArray items) {
        prefs.edit().putString("ribbons_"+bookKey,items.toString()).apply();
        refreshRibbonViews();
    }
    private void createRibbonShelf() {
        ribbonShelf=horizontal();
        ribbonShelf.setPadding(dp(12),dp(2),dp(8),dp(3));
        ribbonShelf.setBackgroundColor(nightMode?0xFF303138:0xFFF1E5D6);
        TextView add=clickable("＋ Cinta",13,Color.WHITE,0xFF8F1C32,this::offerRibbon);
        add.setContentDescription("Colocar una cinta roja en esta página");
        ribbonShelf.addView(add,new LinearLayout.LayoutParams(dp(91),dp(45)));
        HorizontalScrollView shelfScroll=new HorizontalScrollView(this);
        shelfScroll.setHorizontalScrollBarEnabled(false);
        ribbonSlots=horizontal();
        ribbonSlots.setPadding(dp(9),0,dp(5),0);
        shelfScroll.addView(ribbonSlots,new ScrollView.LayoutParams(-2,-2));
        LinearLayout.LayoutParams shelfArea=new LinearLayout.LayoutParams(0,dp(59),1);
        ribbonShelf.addView(shelfScroll,shelfArea);
        readerLayout.addView(ribbonShelf);
    }
    private void refreshRibbonViews() {
        if(ribbonSlots==null||pageRibbon==null||bookKey.isEmpty())return;
        JSONArray saved=readRibbons();
        ribbonSlots.removeAllViews();
        JSONObject top=null;
        for(int i=0;i<saved.length();i++) {
            final JSONObject item=saved.optJSONObject(i);
            if(item==null)continue;
            int page=item.optInt("page",0);
            if(page==currentPage)top=item;
            String note=item.optString("note","");
            RibbonTabView tab=new RibbonTabView(this,"Pág. "+(page+1),false,page==currentPage);
            tab.setContentDescription("Cinta roja. Página "+(page+1)+". "+note+
                ". Toca para ir o mantén pulsado para editar o retirar.");
            tab.setOnClickListener(v->openRibbon(item));
            tab.setOnLongClickListener(v->{showRibbonActions(item);return true;});
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(93),dp(58));
            p.rightMargin=dp(3);
            ribbonSlots.addView(tab,p);
        }
        if(saved.length()==0){
            TextView hint=label("Tus cintas aparecerán aquí",12,
                nightMode?0xFFD2A6A7:MUTED,false);
            ribbonSlots.addView(hint);
        }
        pageRibbon.setVisibility(top!=null&&!readingFullscreen?View.VISIBLE:View.GONE);
        if(top!=null){
            final JSONObject selected=top;
            pageRibbon.setCaption("P. "+(currentPage+1));
            pageRibbon.setOnClickListener(v->showRibbonActions(selected));
        }
    }
    private void offerRibbon() {
        if(busy || totalPages<1)return;
        new AlertDialog.Builder(this)
            .setTitle("Cinta roja · Página "+(currentPage+1))
            .setItems(new String[]{"Colocar cinta aquí",
                "Colocar cinta con una nota",
                "Marcar una frase que ya subrayé"},(dialog,which)->{
                if(which==0)saveRibbon(currentPage,"Retomar lectura aquí","",-1);
                else if(which==1)askRibbonNote(null);
                else chooseHighlightedQuote();
            }).show();
    }
    private void chooseHighlightedQuote() {
        JSONArray marks=readMarks();
        ArrayList<JSONObject> matching=new ArrayList<>();
        ArrayList<String> names=new ArrayList<>();
        for(int i=0;i<marks.length();i++){
            JSONObject item=marks.optJSONObject(i);
            if(item!=null&&item.optInt("page",-1)==currentPage){
                String quote=item.optString("quote","").trim();
                if(!quote.isEmpty()){
                    matching.add(item);
                    names.add(quote.length()>100?quote.substring(0,100)+"…":quote);
                }
            }
        }
        if(matching.isEmpty()){
            toast("Primero subraya una frase en esta página.");return;
        }
        new AlertDialog.Builder(this).setTitle("Vincular cinta a una frase")
            .setItems(names.toArray(new String[0]),(d,index)->{
                JSONObject item=matching.get(index);
                String quote=item.optString("quote","");
                saveRibbon(currentPage,quote.length()>65?quote.substring(0,65)+"…":quote,
                    quote,item.optInt("start",-1));
            }).setNegativeButton("Cancelar",null).show();
    }
    private void askRibbonNote(JSONObject existing) {
        EditText edit=new EditText(this);
        edit.setSingleLine(false);
        edit.setMinLines(2);
        edit.setMaxLines(4);
        edit.setTextSize(16);
        edit.setHint("Ej.: Volver a leer esta explicación…");
        edit.setPadding(dp(17),dp(14),dp(17),dp(14));
        if(existing!=null)edit.setText(existing.optString("note",""));
        new AlertDialog.Builder(this)
            .setTitle(existing==null?"Nueva cinta roja · Página "+(currentPage+1):"Editar nota de la cinta")
            .setView(edit)
            .setNegativeButton("Cancelar",null)
            .setPositiveButton("Guardar cinta",(d,w)->{
                String note=edit.getText().toString().trim();
                if(note.isEmpty())note="Retomar lectura aquí";
                if(existing==null)saveRibbon(currentPage,note,"",-1);
                else changeRibbonNote(existing.optString("id",""),note);
            }).show();
    }
    private void saveRibbon(int page,String note,String quote,int offset) {
        JSONArray all=readRibbons();
        if(all.length()>=100){toast("Puedes guardar hasta 100 cintas por libro.");return;}
        JSONObject item=new JSONObject();
        try{
            item.put("id",String.valueOf(System.nanoTime()));
            item.put("page",page);
            item.put("note",note);
            item.put("quote",quote);
            item.put("offset",offset);
            item.put("createdAt",System.currentTimeMillis());
            all.put(item);
            writeRibbons(all);
            toast("Cinta roja colocada en la página "+(page+1));
        }catch(Exception ex){toast("No se pudo guardar la cinta.");}
    }
    private void changeRibbonNote(String id,String note) {
        JSONArray all=readRibbons();
        for(int i=0;i<all.length();i++){
            JSONObject item=all.optJSONObject(i);
            if(item!=null && id.equals(item.optString("id"))){
                try{item.put("note",note);}catch(Exception ignored){}
                writeRibbons(all);return;
            }
        }
    }
    private void removeRibbon(String id) {
        JSONArray old=readRibbons(),updated=new JSONArray();
        for(int i=0;i<old.length();i++){
            JSONObject item=old.optJSONObject(i);
            if(item!=null&&!id.equals(item.optString("id")))updated.put(item);
        }
        writeRibbons(updated);
        toast("Cinta retirada del libro");
    }
    private void showCurrentRibbon(){
        JSONArray saved=readRibbons();
        for(int i=saved.length()-1;i>=0;i--){
            JSONObject item=saved.optJSONObject(i);
            if(item!=null&&item.optInt("page",-1)==currentPage){
                showRibbonActions(item);return;
            }
        }
    }
    private void showRibbonActions(JSONObject item){
        final int page=item.optInt("page",0);
        final String id=item.optString("id","");
        final String note=item.optString("note","Retomar lectura aquí");
        new AlertDialog.Builder(this).setTitle("🔖 Cinta · Página "+(page+1))
            .setMessage(note)
            .setItems(new String[]{"Ir a esa parte del libro","Editar nota","Retirar esta cinta"},(d,which)->{
                if(which==0)openRibbon(item);
                else if(which==1)askRibbonNote(item);
                else new AlertDialog.Builder(this).setTitle("Retirar cinta roja")
                    .setMessage("¿Quitar esta cinta de la página "+(page+1)+"?")
                    .setPositiveButton("Retirar",(a,b)->removeRibbon(id))
                    .setNegativeButton("Cancelar",null).show();
            }).setNegativeButton("Cerrar",null).show();
    }
    private void openRibbon(JSONObject item) {
        if(busy)return;
        int page=item.optInt("page",0);
        String note=item.optString("note","");
        int offset=item.optInt("offset",-1);
        if(offset<0 && !item.optString("quote","").isEmpty() && textMode
            && page==currentPage)offset=textView.getText().toString().indexOf(item.optString("quote"));
        if(textMode&&offset>=0)pendingScrollOffset=offset;
        if(narrator!=null)narrator.onManualPageChange();
        if(page!=currentPage)loadPageAt(page,animatedPages,page>currentPage);
        else if(textMode && pendingScrollOffset>=0)showPageText(textView.getText().toString());
        toast("Cinta roja · Página "+(page+1)+(note.isEmpty()?"":" · "+note));
    }

    private void showSettings() {
        String anim=animatedPages?"activada":"desactivada";
        new AlertDialog.Builder(this).setTitle("Preferencias de lectura")
            .setItems(new String[]{"☀  Modo papel cálido","☾  Modo noche", "◒  Animación "+anim,
                    "↺  Restablecer tamaño / zoom", "＋  Abrir otro PDF"}, (d,index) -> {
                switch(index) {
                    case 0: nightMode=false;prefs.edit().putBoolean("night",false).apply();rebuildReader();break;
                    case 1: nightMode=true;prefs.edit().putBoolean("night",true).apply();rebuildReader();break;
                    case 2: animatedPages=!animatedPages;prefs.edit().putBoolean("animate",animatedPages).apply();
                        toast("Animación "+(animatedPages?"activada":"desactivada"));break;
                    case 3: fontSizeSp=21;prefs.edit().putFloat("fontsize",fontSizeSp).apply();
                        if (textMode) textView.setTextSize(fontSizeSp);else curlView.setZoom(1f);break;
                    case 4: pickPdf();break;
                }
            }).show();
    }
    private void rebuildReader() {
        if (busy) return;
        showReader();loadPageAt(currentPage,false,true);
    }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
    @Override public void onBackPressed() {
        if (readerLayout!=null && readerLayout.getParent()!=null){
            if(readingFullscreen)setReadingFullscreen(false);
            else showLibrary();
        } else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        if(narrator!=null)narrator.close();
        openVersion++;requestVersion++;
        io.execute(this::closeOpenedDocuments);
        io.shutdown();
        super.onDestroy();
    }
}
