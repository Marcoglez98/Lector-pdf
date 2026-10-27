package com.lecturas.hojas;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Narrador Android: voces instaladas (locales o en línea), fragmentación y lectura continua. */
public final class NarrationHelper {
    public interface Host {
        void requestText(int page, java.util.function.Consumer<String> reply);
        void showReadingPage(int page);
        void stateChanged();
        void notice(String message);
    }

    private final Activity activity;
    private final Host host;
    private final SharedPreferences prefs;
    private TextToSpeech tts;
    private boolean initialized, playing, paused;
    private int page, lastPage, serial;
    private String text = "";
    private int offset;
    private int spokenOffset;
    private int chunkOffset;
    private String currentChunk = "";
    private final List<Voice> voices = new ArrayList<>();

    public NarrationHelper(Activity activity, SharedPreferences prefs, Host host) {
        this.activity=activity;this.prefs=prefs;this.host=host;
        tts = new TextToSpeech(activity, status -> {
            initialized = status == TextToSpeech.SUCCESS;
            if (initialized) {
                String tag=prefs.getString("tts_voice","");
                if (tag.isEmpty()) tts.setLanguage(new Locale("es","MX"));
                refreshVoices();
                selectVoice(tag);
                tts.setSpeechRate(prefs.getFloat("tts_rate",0.92f));
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) {
                        activity.runOnUiThread(() -> {
                            if (!playing || paused || !id.equals("hojas-"+serial)) return;
                            offset=chunkOffset+currentChunk.length();
                            speakNext();
                        });
                    }
                    @Override public void onRangeStart(String id,int start,int end,int frame) {
                        activity.runOnUiThread(()->{
                            if(playing && !paused && id.equals("hojas-"+serial))
                                spokenOffset=Math.max(spokenOffset,chunkOffset+start);
                        });
                    }
                    @Override public void onError(String id) {
                        activity.runOnUiThread(() -> {
                            if (playing && !paused && id.equals("hojas-"+serial)) {
                                stop();
                                host.notice("No se pudo reproducir esta voz. Elige otra en Voces.");
                            }
                        });
                    }
                });
            } else host.notice("Activa un motor de texto a voz en los ajustes de Android.");
            host.stateChanged();
        });
    }
    public boolean isReady(){return initialized;}
    public boolean isPlaying(){return playing && !paused;}
    public boolean isPaused(){return playing && paused;}
    public float getRate(){return prefs.getFloat("tts_rate",0.92f);}
    public void setRate(float rate){
        rate=Math.max(0.65f,Math.min(1.5f,rate));
        prefs.edit().putFloat("tts_rate",rate).apply();
        if(initialized) tts.setSpeechRate(rate);
    }
    public List<Voice> getVoices(){
        refreshVoices();
        return new ArrayList<>(voices);
    }
    private void refreshVoices() {
        voices.clear();
        if(!initialized)return;
        Set<Voice> list=tts.getVoices();
        if(list==null)return;
        for(Voice v:list) if("es".equals(v.getLocale().getLanguage())) voices.add(v);
        Collections.sort(voices,(a,b)->{
            int quality=Integer.compare(b.getQuality(),a.getQuality());
            if(quality!=0)return quality;
            int offline=Boolean.compare(a.isNetworkConnectionRequired(),b.isNetworkConnectionRequired());
            if(offline!=0)return offline;
            return a.getLocale().toLanguageTag().compareToIgnoreCase(b.getLocale().toLanguageTag());
        });
    }
    public String selectedVoice(){
        return prefs.getString("tts_voice","");
    }
    public boolean selectVoice(String id) {
        if(!initialized)return false;
        for(Voice v:getVoices()) if(v.getName().equals(id)) {
            if(tts.setVoice(v)==TextToSpeech.SUCCESS){
                prefs.edit().putString("tts_voice",id).apply();return true;
            }
        }
        return false;
    }
    public String voiceLabel(Voice v) {
        String info=v.isNetworkConnectionRequired()?"Necesita Internet":"Sin conexión";
        if(v.getFeatures()!=null &&
           v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))
            info="Requiere descargar datos";
        return v.getLocale().toLanguageTag()+"  ·  "+v.getName()+"\n"+info;
    }
    public void preview(){
        if(!initialized){host.notice("Todavía no está listo el motor de voz");return;}
        if(playing)stop();
        tts.speak("Cada libro es una puerta a nuevas historias. Así escucharás la narración en Hojas.",
            TextToSpeech.QUEUE_FLUSH,new Bundle(),"muestra-voz");
    }
    public void installVoiceData(){
        Intent intent=new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
        if(tts!=null && tts.getDefaultEngine()!=null) intent.setPackage(tts.getDefaultEngine());
        try { activity.startActivity(intent); }
        catch(Exception ex) {
            try { activity.startActivity(new Intent("com.android.settings.TTS_SETTINGS")); }
            catch(Exception ignored) {
                activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                host.notice("Busca 'Salida de texto a voz' y descarga voces.");
            }
        }
    }
    public void start(int fromPage, int count) {
        if(!initialized){host.notice("No hay motor de voz preparado. Revisa Voces.");return;}
        if(count<=0)return;
        stop();
        playing=true;paused=false;lastPage=count-1;page=fromPage;offset=0;spokenOffset=0;text="";
        loadCurrentPage();
        host.stateChanged();
    }
    private void loadCurrentPage() {
        if(!playing || paused)return;
        if(page>lastPage){stop();host.notice("Terminaste de escuchar el libro.");return;}
        int token=++serial;
        host.showReadingPage(page);
        host.requestText(page, body -> {
            if(!playing || paused || token!=serial)return;
            text=body==null?"":body;
            if(text.startsWith("Esta página no contiene texto seleccionable."))text="";
            offset=0;spokenOffset=0;speakNext();
        });
    }
    private void speakNext() {
        if(!playing || paused)return;
        while(offset<text.length() && Character.isWhitespace(text.charAt(offset)))offset++;
        if(offset>=text.length()){
            page++;
            loadCurrentPage();
            return;
        }
        int end=Math.min(text.length(),offset+2450);
        if(end<text.length()) {
            int lastPeriod=-1;
            for(int k=end-1;k>offset+900;k--) {
                char c=text.charAt(k);
                if(c=='.'||c=='!'||c=='?'||c=='\n'||c==';'){lastPeriod=k+1;break;}
            }
            if(lastPeriod>0)end=lastPeriod;
            else {
                int space=text.lastIndexOf(' ',end);
                if(space>offset+300)end=space+1;
            }
        }
        chunkOffset=offset;
        spokenOffset=offset;
        currentChunk=text.substring(offset,end);
        ++serial;
        int result=tts.speak(currentChunk,TextToSpeech.QUEUE_FLUSH,new Bundle(),"hojas-"+serial);
        if(result!=TextToSpeech.SUCCESS){
            stop();host.notice("La voz no pudo empezar. Comprueba los datos descargados.");
        }
    }
    public void pause() {
        if(!playing || paused)return;
        paused=true;serial++;
        offset=Math.max(offset,spokenOffset);
        tts.stop();
        host.stateChanged();
    }
    public void resume() {
        if(!playing || !paused)return;
        paused=false;
        speakNext();host.stateChanged();
    }
    public void stop() {
        playing=false;paused=false;serial++;
        if(tts!=null)tts.stop();
        host.stateChanged();
    }
    public void onManualPageChange(){if(playing)stop();}
    public void close(){stop();if(tts!=null)tts.shutdown();}
}
