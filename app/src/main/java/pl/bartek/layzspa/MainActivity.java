package pl.bartek.layzspa;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSeekBar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class MainActivity extends AppCompatActivity {
    private static final String DEFAULT_HOST = "192.168.1.40";
    private static final int DEFAULT_PORT = 81;
    private static final String[] LANGS = {"pl", "en", "de", "fr", "es"};
    private static final String[] FLAGS = {"🇵🇱 Polski", "🇬🇧 English", "🇩🇪 Deutsch", "🇫🇷 Français", "🇪🇸 Español"};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build();
    private WebSocket ws;
    private boolean manualClose;
    private String host;
    private int port, target, currentTemp;
    private int heatState, pumpState, airState, jetsState, powerState;
    private String language = "pl";
    private String pumpProfile = "default";
    private String hydroMode = "standard";

    private TextView status, tempValue, targetValue, connectionSummary, appTitle, appSubtitle;
    private TextView settingsTitle, settingsConnectionTitle, pumpProfileLabel, hydroModeLabel, settingsNote;
    private TextView heatLabel, pumpLabel, airLabel, jetsLabel, powerLabel;
    private TextView infoView, rssiView;
    private TextInputEditText hostEdit, portEdit;
    private MaterialButton connectButton, backButton;
    private Spinner languageSpinner, pumpSpinner, hydroSpinner;
    private TemperatureDial dial;
    private View mainScreen, settingsScreen;

    private final int BG = Color.rgb(235, 242, 247);
    private final int CARD = Color.rgb(255, 255, 255);
    private final int CARD2 = Color.rgb(243, 248, 252);
    private final int TEXT = Color.rgb(28, 42, 53);
    private final int MUTED = Color.rgb(92, 111, 124);
    private final int BLUE = Color.rgb(35, 153, 219);
    private final int GREEN = Color.rgb(54, 190, 139);
    private final int RED = Color.rgb(224, 77, 77);
    private final int BORDER = Color.rgb(208, 220, 229);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(221, 231, 238));
        getWindow().setNavigationBarColor(Color.rgb(221, 231, 238));
        SharedPreferences p = getPreferences(MODE_PRIVATE);
        host = p.getString("host", DEFAULT_HOST);
        port = p.getInt("port", DEFAULT_PORT);
        language = p.getString("language", "pl");
        pumpProfile = p.getString("pump_profile", "default");
        hydroMode = p.getString("hydro_mode", "standard");
        buildUi();
        applyLanguage();
        connect();
    }

    private TextView tv(String s, float size, int color) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(size); v.setTextColor(color); v.setIncludeFontPadding(true);
        return v;
    }
    private GradientDrawable rounded(int color, float radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius);
        if (stroke > 0) d.setStroke(1, stroke); return d;
    }
    private LinearLayout vertical() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout r = new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); return r; }
    private void gap(LinearLayout root, int h) { root.addView(new Space(this), new LinearLayout.LayoutParams(1, h)); }
    private MaterialCardView island() { MaterialCardView c = new MaterialCardView(this); c.setCardBackgroundColor(CARD); c.setRadius(24); c.setStrokeColor(BORDER); c.setStrokeWidth(1); return c; }
    private LinearLayout content(MaterialCardView card, int pad) { LinearLayout box = vertical(); box.setPadding(pad,pad,pad,pad); card.addView(box,new ViewGroup.LayoutParams(-1,-2)); return box; }
    private TextView sectionTitle(String s) { TextView t=tv(s.toUpperCase(Locale.ROOT),12,MUTED); t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setLetterSpacing(.12f); return t; }

    private MaterialButton actionButton(String label) {
        MaterialButton b = new MaterialButton(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(TEXT); b.setCornerRadius(18); b.setStrokeWidth(1); b.setStrokeColor(android.content.res.ColorStateList.valueOf(BORDER));
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(CARD2)); b.setInsetTop(0); b.setInsetBottom(0);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(14,0,14,0); return b;
    }
    private TextInputLayout input(String hint,String value,boolean number){
        TextInputLayout til=new TextInputLayout(this); til.setHint(hint); til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE); til.setBoxCornerRadii(16,16,16,16); til.setBoxStrokeColor(BORDER);
        TextInputEditText e=new TextInputEditText(this); e.setText(value); e.setTextColor(TEXT); e.setTextSize(15); e.setSingleLine(true); e.setInputType(number?InputType.TYPE_CLASS_NUMBER:InputType.TYPE_CLASS_TEXT); til.addView(e); return til;
    }
    private Spinner spinner(String[] items,int selected){
        Spinner s=new Spinner(this); s.setBackground(rounded(CARD2,16,BORDER));
        ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,items){
            @Override public View getView(int pos,View convert,android.view.ViewGroup parent){ TextView v=(TextView)super.getView(pos,convert,parent); v.setTextColor(TEXT); v.setTextSize(14); v.setPadding(14,0,10,0); return v; }
            @Override public View getDropDownView(int pos,View convert,android.view.ViewGroup parent){ TextView v=(TextView)super.getDropDownView(pos,convert,parent); v.setTextColor(TEXT); v.setTextSize(15); v.setPadding(18,16,12,16); v.setBackgroundColor(CARD); return v; }
        }; s.setAdapter(a); s.setSelection(selected); return s;
    }

    private void buildUi(){
        FrameLayout container=new FrameLayout(this); container.setBackgroundColor(BG);
        mainScreen=buildMainScreen(); settingsScreen=buildSettingsScreen();
        container.addView(mainScreen,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-1,-1); settingsScreen.setVisibility(View.GONE); container.addView(settingsScreen,sp);
        setContentView(container);
    }

    private View buildMainScreen(){
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root=vertical(); root.setPadding(16,14,16,28); scroll.addView(root);

        MaterialCardView headerCard=island(); LinearLayout header=content(headerCard,16);
        LinearLayout top=row(); LinearLayout titles=vertical();
        appTitle=tv("LAY-Z-SPA",27,TEXT); appTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD); appTitle.setIncludeFontPadding(false);
        appSubtitle=tv("SMART SPA CONTROL",10,MUTED); appSubtitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD); appSubtitle.setLetterSpacing(.12f);
        titles.addView(appTitle,new LinearLayout.LayoutParams(0,42,1)); titles.addView(appSubtitle,new LinearLayout.LayoutParams(0,24,1));
        top.addView(titles,new LinearLayout.LayoutParams(0,66,1));
        status=tv("●  DISCONNECTED",11,RED); status.setGravity(Gravity.CENTER); status.setTypeface(Typeface.DEFAULT,Typeface.BOLD); status.setPadding(8,0,8,0);
        top.addView(status,new LinearLayout.LayoutParams(-2,42));
        languageSpinner=spinner(FLAGS,langIndex()); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(138,50); lp.setMargins(8,0,0,0); top.addView(languageSpinner,lp);
        header.addView(top);
        root.addView(headerCard,new LinearLayout.LayoutParams(-1,-2)); gap(root,12);

        MaterialCardView tempCard=island(); LinearLayout temp=content(tempCard,18); temp.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView currentLabel=sectionTitle("Current temperature"); temp.addView(currentLabel,new LinearLayout.LayoutParams(-1,30));
        dial=new TemperatureDial(); LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-1,330); dp.gravity=Gravity.CENTER_HORIZONTAL; temp.addView(dial,dp);
        targetValue=tv("TARGET 30°C",16,MUTED); targetValue.setGravity(Gravity.CENTER); targetValue.setTypeface(Typeface.DEFAULT,Typeface.BOLD); temp.addView(targetValue,new LinearLayout.LayoutParams(-1,34));
        temp.addView(tv("20°C",12,MUTED),new LinearLayout.LayoutParams(-1,24));
        AppCompatSeekBar seek=new AppCompatSeekBar(this); seek.setMax(20); seek.setProgress(10); seek.setPadding(12,0,12,0); seek.setContentDescription("Target temperature");
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(-1,42); temp.addView(seek,slp);
        TextView max=tv("40°C",12,MUTED); max.setGravity(Gravity.RIGHT); temp.addView(max,new LinearLayout.LayoutParams(-1,24));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar b,int p,boolean from){int t=20+p; target=t; targetValue.setText(tr("target")+"  "+t+"°C"); dial.setTarget(t); } public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){send(0,target);} });
        root.addView(tempCard,new LinearLayout.LayoutParams(-1,-2)); gap(root,12);

        MaterialCardView quick=island(); LinearLayout q=content(quick,16); TextView quickTitle=sectionTitle("Quick control"); q.addView(quickTitle); gap(q,8);
        LinearLayout rr=row();
        MaterialButton settings=actionButton("⚙  "+tr("settings")); settings.setOnClickListener(v->showSettings()); rr.addView(settings,new LinearLayout.LayoutParams(0,58,1));
        root.addView(quick); 

        MaterialCardView conn=island(); LinearLayout cb=content(conn,14); connectionSummary=tv("●  "+tr("connectionDetails"),14,TEXT); connectionSummary.setTypeface(Typeface.DEFAULT,Typeface.BOLD); cb.addView(connectionSummary); cb.setOnClickListener(v->showSettings()); cb.setBackground(rounded(CARD,18,0));
        TextView hint=tv(tr("tapConnection"),12,MUTED); cb.addView(hint); root.addView(conn); gap(root,8);
        return scroll;
    }

    private View buildSettingsScreen(){
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root=vertical(); root.setPadding(16,14,16,30); scroll.addView(root);
        LinearLayout head=row(); backButton=actionButton("‹  "+tr("back")); backButton.setOnClickListener(v->showMain()); head.addView(backButton,new LinearLayout.LayoutParams(74,52));
        settingsTitle=tv("SPA SETTINGS",23,TEXT); settingsTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD); settingsTitle.setGravity(Gravity.CENTER_VERTICAL); LinearLayout.LayoutParams stp=new LinearLayout.LayoutParams(0,52,1); stp.setMargins(10,0,0,0); head.addView(settingsTitle,stp); root.addView(head); gap(root,12);

        MaterialCardView controls=island(); LinearLayout c=content(controls,16); TextView ct=sectionTitle("Spa control"); c.addView(ct); gap(c,8);
        heatLabel=tv("GRZANIE",15,TEXT); pumpLabel=tv("POMPA / FILTR",15,TEXT); airLabel=tv("BĄBELKI",15,TEXT); jetsLabel=tv("HYDROJET",15,TEXT); powerLabel=tv("ZASILANIE",15,TEXT);
        addSwitch(c,heatLabel,0); addSwitch(c,pumpLabel,4); addSwitch(c,airLabel,2); addSwitch(c,jetsLabel,11); addSwitch(c,powerLabel,25);
        root.addView(controls); gap(root,12);

        MaterialCardView spa=island(); LinearLayout s=content(spa,16); TextView spaTitle=sectionTitle("Spa settings"); s.addView(spaTitle); gap(s,10);
        pumpProfileLabel=tv("Pump profile",14,TEXT); s.addView(pumpProfileLabel); gap(s,5);
        pumpSpinner=spinner(new String[]{"Default","Pump 1","Pump 2","External pump"},pumpIndex()); s.addView(pumpSpinner,new LinearLayout.LayoutParams(-1,52)); gap(s,12);
        hydroModeLabel=tv("HydroJet mode",14,TEXT); s.addView(hydroModeLabel); gap(s,5);
        hydroSpinner=spinner(new String[]{"Standard","Power","Quiet"},hydroIndex()); s.addView(hydroSpinner,new LinearLayout.LayoutParams(-1,52)); gap(s,10);
        settingsNote=tv("",12,MUTED); settingsNote.setLineSpacing(0,1.15f); s.addView(settingsNote);
        pumpSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int pos,long id){pumpProfile=new String[]{"default","pump1","pump2","external"}[pos];saveSettings();}});
        hydroSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int pos,long id){hydroMode=new String[]{"standard","power","quiet"}[pos];saveSettings();}});
        root.addView(spa); gap(root,12);

        MaterialCardView connection=island(); LinearLayout cc=content(connection,16); settingsConnectionTitle=sectionTitle("Connection"); cc.addView(settingsConnectionTitle); gap(cc,10);
        LinearLayout inputs=row(); TextInputLayout ht=input("IP / hostname",host,false); hostEdit=(TextInputEditText)ht.getEditText(); inputs.addView(ht,new LinearLayout.LayoutParams(0,62,1)); TextInputLayout pt=input("Port",String.valueOf(port),true); portEdit=(TextInputEditText)pt.getEditText(); LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(96,62);pp.setMargins(8,0,0,0);inputs.addView(pt,pp); cc.addView(inputs);
        connectButton=actionButton(tr("connect")); LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,54);cp.setMargins(0,10,0,0);cc.addView(connectButton,cp); connectButton.setOnClickListener(v->readConnectionAndConnect());
        infoView=tv("Model: --\nFirmware: --\nIP: --",13,TEXT); infoView.setLineSpacing(0,1.15f); infoView.setPadding(0,14,0,0); cc.addView(infoView);
        rssiView=tv("RSSI: -- dBm",13,MUTED); cc.addView(rssiView);
        root.addView(connection);
        return scroll;
    }

    private void addSwitch(LinearLayout parent,TextView label,int cmd){
        LinearLayout r=row(); r.setPadding(4,3,4,3); r.setBackground(rounded(CARD2,16,0));
        r.addView(label,new LinearLayout.LayoutParams(0,58,1)); TextView state=tv("OFF",13,MUTED); state.setGravity(Gravity.CENTER); state.setTypeface(Typeface.DEFAULT,Typeface.BOLD); r.addView(state,new LinearLayout.LayoutParams(62,58));
        r.setOnClickListener(v->{int st=cmd==0?heatState:cmd==4?pumpState:cmd==2?airState:cmd==11?jetsState:powerState; toggle(cmd,st);});
        label.setTag(state); parent.addView(r,new LinearLayout.LayoutParams(-1,64)); gap(parent,6);
    }

    private void showSettings(){ mainScreen.setVisibility(View.GONE); settingsScreen.setVisibility(View.VISIBLE); }
    private void showMain(){ settingsScreen.setVisibility(View.GONE); mainScreen.setVisibility(View.VISIBLE); }

    private int langIndex(){for(int i=0;i<LANGS.length;i++)if(LANGS[i].equals(language))return i;return 0;}
    private int pumpIndex(){if("pump1".equals(pumpProfile))return 1;if("pump2".equals(pumpProfile))return 2;if("external".equals(pumpProfile))return 3;return 0;}
    private int hydroIndex(){if("power".equals(hydroMode))return 1;if("quiet".equals(hydroMode))return 2;return 0;}
    private void saveSettings(){getPreferences(MODE_PRIVATE).edit().putString("language",language).putString("pump_profile",pumpProfile).putString("hydro_mode",hydroMode).apply();}

    private void applyLanguage(){
        if(languageSpinner!=null && languageSpinner.getSelectedItemPosition()!=langIndex()) languageSpinner.setSelection(langIndex());
        appSubtitle.setText(tr("subtitle")); settingsTitle.setText(tr("settings")); settingsConnectionTitle.setText(tr("connection"));
        targetValue.setText(tr("target")+"  "+target+"°C"); backButton.setText("‹  "+tr("back")); connectButton.setText(tr("connect"));
        settingsNote.setText(tr("note")); pumpProfileLabel.setText(tr("pumpProfile")); hydroModeLabel.setText(tr("hydroMode"));
        updateControlLabels(); updateConnectionSummary();
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int pos,long id){String l=LANGS[pos]; if(!l.equals(language)){language=l;saveSettings();applyLanguage();}}});
    }

    private String tr(String k){
        if("pl".equals(language)){
            if(k.equals("subtitle"))return"INTELIGENTNE STEROWANIE SPA"; if(k.equals("settings"))return"USTAWIENIA SPA"; if(k.equals("connection"))return"POŁĄCZENIE"; if(k.equals("connect"))return"POŁĄCZ"; if(k.equals("back"))return"WRÓĆ"; if(k.equals("target"))return"CEL"; if(k.equals("connectionDetails"))return"POŁĄCZENIE Z ESP8266"; if(k.equals("tapConnection"))return"Dotknij, aby zobaczyć szczegóły Wi‑Fi, RSSI, IP i firmware"; if(k.equals("pumpProfile"))return"Profil pompy"; if(k.equals("hydroMode"))return"Tryb HydroJet"; return"Ustawienia są zapisywane na tym urządzeniu. Nie dodaję nowych komend ESP8266 bez potwierdzenia protokołu.";
        }
        if("de".equals(language)){
            if(k.equals("subtitle"))return"INTELLIGENTE SPA-STEUERUNG"; if(k.equals("settings"))return"SPA-EINSTELLUNGEN"; if(k.equals("connection"))return"VERBINDUNG"; if(k.equals("connect"))return"VERBINDEN"; if(k.equals("back"))return"ZURÜCK"; if(k.equals("target"))return"ZIEL"; if(k.equals("connectionDetails"))return"VERBINDUNG ZU ESP8266"; if(k.equals("tapConnection"))return"Tippen für WLAN, RSSI, IP und Firmware"; if(k.equals("pumpProfile"))return"Pumpenprofil"; if(k.equals("hydroMode"))return"HydroJet-Modus"; return"Die Einstellungen werden auf diesem Gerät gespeichert. Keine neuen ESP8266-Befehle ohne bestätigtes Protokoll.";
        }
        if("fr".equals(language)){
            if(k.equals("subtitle"))return"CONTRÔLE INTELLIGENT DU SPA"; if(k.equals("settings"))return"RÉGLAGES DU SPA"; if(k.equals("connection"))return"CONNEXION"; if(k.equals("connect"))return"CONNECTER"; if(k.equals("back"))return"RETOUR"; if(k.equals("target"))return"CIBLE"; if(k.equals("connectionDetails"))return"CONNEXION À L’ESP8266"; if(k.equals("tapConnection"))return"Touchez pour voir le Wi‑Fi, RSSI, IP et firmware"; if(k.equals("pumpProfile"))return"Profil de pompe"; if(k.equals("hydroMode"))return"Mode HydroJet"; return"Les réglages sont enregistrés sur cet appareil. Aucun nouveau commande ESP8266 sans protocole confirmé.";
        }
        if("es".equals(language)){
            if(k.equals("subtitle"))return"CONTROL INTELIGENTE DEL SPA"; if(k.equals("settings"))return"AJUSTES DEL SPA"; if(k.equals("connection"))return"CONEXIÓN"; if(k.equals("connect"))return"CONECTAR"; if(k.equals("back"))return"VOLVER"; if(k.equals("target"))return"OBJETIVO"; if(k.equals("connectionDetails"))return"CONEXIÓN AL ESP8266"; if(k.equals("tapConnection"))return"Toca para ver Wi‑Fi, RSSI, IP y firmware"; if(k.equals("pumpProfile"))return"Perfil de bomba"; if(k.equals("hydroMode"))return"Modo HydroJet"; return"Los ajustes se guardan en este dispositivo. No se añaden comandos ESP8266 sin protocolo confirmado.";
        }
        if(k.equals("subtitle"))return"SMART SPA CONTROL"; if(k.equals("settings"))return"SPA SETTINGS"; if(k.equals("connection"))return"CONNECTION"; if(k.equals("connect"))return"CONNECT"; if(k.equals("back"))return"BACK"; if(k.equals("target"))return"TARGET"; if(k.equals("connectionDetails"))return"ESP8266 CONNECTION"; if(k.equals("tapConnection"))return"Tap to view Wi‑Fi, RSSI, IP and firmware"; if(k.equals("pumpProfile"))return"Pump profile"; if(k.equals("hydroMode"))return"HydroJet mode"; return"Settings are stored on this device. No new ESP8266 commands are added without a confirmed protocol.";
    }

    private String label(String k){
        if("pl".equals(language)){if(k.equals("heat"))return"GRZANIE";if(k.equals("pump"))return"POMPA / FILTR";if(k.equals("bubbles"))return"BĄBELKI";if(k.equals("jets"))return"HYDROJET";return"ZASILANIE";}
        if("de".equals(language)){if(k.equals("heat"))return"HEIZUNG";if(k.equals("pump"))return"PUMPE / FILTER";if(k.equals("bubbles"))return"BLASEN";if(k.equals("jets"))return"HYDROJET";return"STROM";}
        if("fr".equals(language)){if(k.equals("heat"))return"CHAUFFAGE";if(k.equals("pump"))return"POMPE / FILTRE";if(k.equals("bubbles"))return"BULLES";if(k.equals("jets"))return"HYDROJET";return"ALIMENTATION";}
        if("es".equals(language)){if(k.equals("heat"))return"CALEFACCIÓN";if(k.equals("pump"))return"BOMBA / FILTRO";if(k.equals("bubbles"))return"BURBUJAS";if(k.equals("jets"))return"HYDROJET";return"ALIMENTACIÓN";}
        if(k.equals("heat"))return"HEATING";if(k.equals("pump"))return"PUMP / FILTER";if(k.equals("bubbles"))return"BUBBLES";if(k.equals("jets"))return"HYDROJET";return"POWER";
    }

    private void updateControlLabels(){
        heatLabel.setText(label("heat")); pumpLabel.setText(label("pump")); airLabel.setText(label("bubbles")); jetsLabel.setText(label("jets")); powerLabel.setText(label("power"));
        updateControlState(heatLabel,heatState); updateControlState(pumpLabel,pumpState); updateControlState(airLabel,airState); updateControlState(jetsLabel,jetsState); updateControlState(powerLabel,powerState);
    }
    private void updateControlState(TextView label,int state){Object tag=label.getTag(); if(tag instanceof TextView){TextView t=(TextView)tag; t.setText(state==1?"ON":"OFF");t.setTextColor(state==1?GREEN:MUTED);}}
    private void updateConnectionSummary(){if(connectionSummary!=null)connectionSummary.setText("●  "+tr("connectionDetails")+"   "+host+":"+port);}

    private void readConnectionAndConnect(){String h=hostEdit.getText()==null?"":hostEdit.getText().toString().trim();String ps=portEdit.getText()==null?"":portEdit.getText().toString().trim();if(h.isEmpty()){toast("Host required");return;}int p;try{p=Integer.parseInt(ps);}catch(Exception e){p=DEFAULT_PORT;}if(p<1||p>65535){toast("Invalid port");return;}host=h;port=p;getPreferences(MODE_PRIVATE).edit().putString("host",host).putInt("port",port).apply();updateConnectionSummary();manualClose=false;connect();}
    private void setTarget(int t){target=Math.max(20,Math.min(40,t));if(dial!=null)dial.setTarget(target);if(targetValue!=null)targetValue.setText(tr("target")+"  "+target+"°C");send(0,target);}
    private void toggle(int cmd,int state){send(cmd,state==1?0:1);}
    private void send(int cmd,int value){if(ws==null){toast("No connection to ESP8266");return;}try{JSONObject o=new JSONObject();o.put("CMD",cmd);o.put("VALUE",value);o.put("XTIME",0);o.put("INTERVAL",0);o.put("TXT","");ws.send(o.toString());}catch(Exception e){toast("Command error");}}

    private void connect(){if(host==null||host.isEmpty())return;manualClose=false;if(ws!=null)ws.close(1000,"reconnect");setStatus("●  "+trConnecting());try{ws=client.newWebSocket(new Request.Builder().url("ws://"+host+":"+port+"/").build(),new WebSocketListener(){@Override public void onOpen(WebSocket w,Response r){runOnUiThread(()->{setStatus("●  "+trConnected());connectButton.setText(trConnected());});}@Override public void onMessage(WebSocket w,String text){parse(text);}@Override public void onFailure(WebSocket w,Throwable t,Response r){runOnUiThread(()->{setStatus("●  "+tr("disconnected"));connectButton.setText(tr("connect"));});if(!manualClose)reconnectLater();}@Override public void onClosed(WebSocket w,int c,String reason){if(!manualClose)reconnectLater();}});}catch(Exception e){setStatus("●  INVALID ADDRESS");}}
    private String trConnecting(){if("pl".equals(language))return"ŁĄCZENIE";if("de".equals(language))return"VERBINDUNG";if("fr".equals(language))return"CONNEXION";if("es".equals(language))return"CONECTANDO";return"CONNECTING";}
    private String trConnected(){if("pl".equals(language))return"POŁĄCZONO";if("de".equals(language))return"VERBUNDEN";if("fr".equals(language))return"CONNECTÉ";if("es".equals(language))return"CONECTADO";return"CONNECTED";}
    private void reconnectLater(){handler.removeCallbacksAndMessages(null);handler.postDelayed(this::connect,3000);}
    private void parse(String text){try{JSONObject o=new JSONObject(text);String c=o.optString("CONTENT");if("STATES".equals(c)){target=o.optInt("TGT",target);currentTemp=o.optInt("TMP",0);heatState=o.optInt("GRN",0);pumpState=o.optInt("FLT",0);airState=o.optInt("AIR",0);jetsState=o.optInt("HJT",0);powerState=o.optInt("PWR",0);runOnUiThread(()->{if(dial!=null){dial.setCurrent(currentTemp);dial.setTarget(target);}if(targetValue!=null)targetValue.setText(tr("target")+"  "+target+"°C");updateControlLabels();});}else if("OTHER".equals(c)){String model=o.optString("MODEL","--"),fw=o.optString("FW","--"),espIp=o.optString("IP",host);int rssi=o.optInt("RSSI",0);runOnUiThread(()->{infoView.setText("Model: "+model+"\nFirmware: "+fw+"\nIP: "+espIp);rssiView.setText("RSSI: "+rssi+" dBm");});}}catch(Exception ignored){}}
    private void setStatus(String s){if(status==null)return;status.setText(s);status.setTextColor((s.contains("POŁĄCZ")||s.contains("VERBUNDEN")||s.contains("CONNECTÉ")||s.contains("CONECTADO")||s.contains("CONNECTED"))?GREEN:(s.contains("ŁĄC")||s.contains("VERBIND")||s.contains("CONNEX")||s.contains("CONECT")||s.contains("CONNECT"))?BLUE:RED);}
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());}

    private class TemperatureDial extends View {
        private final Paint track=new Paint(Paint.ANTI_ALIAS_FLAG), arc=new Paint(Paint.ANTI_ALIAS_FLAG), text=new Paint(Paint.ANTI_ALIAS_FLAG), small=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int cur=0,tgt=30;
        private RectF oval=new RectF();
        TemperatureDial(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);setFocusable(true);}
        void setCurrent(int v){cur=v;invalidate();}
        void setTarget(int v){tgt=Math.max(20,Math.min(40,v));invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f;float radius=Math.min(getWidth()*0.34f,getHeight()*0.38f);float stroke=Math.max(18f,Math.min(26f,getWidth()*0.055f));oval.set(cx-radius,cy-radius,cx+radius,cy+radius);
            track.setStyle(Paint.Style.STROKE);track.setStrokeWidth(stroke);track.setStrokeCap(Paint.Cap.ROUND);track.setColor(Color.rgb(224,233,239));c.drawArc(oval,-140,280,false,track);
            arc.setStyle(Paint.Style.STROKE);arc.setStrokeWidth(stroke);arc.setStrokeCap(Paint.Cap.ROUND);arc.setColor(BLUE);float sweep=(tgt-20)/20f*280f;c.drawArc(oval,-140,sweep,false,arc);
            text.setTypeface(Typeface.DEFAULT);text.setFakeBoldText(true);text.setTextAlign(Paint.Align.CENTER);text.setColor(TEXT);text.setTextSize(Math.min(68,getWidth()*0.16f));String curText=cur>0?cur+"°":"--°";c.drawText(curText,cx,cy+10,text);
            small.setTypeface(Typeface.DEFAULT);small.setFakeBoldText(true);small.setTextAlign(Paint.Align.CENTER);small.setColor(MUTED);small.setTextSize(14);c.drawText(tr("target")+"  "+tgt+"°C",cx,cy+42,small);
            small.setTextSize(11);small.setFakeBoldText(false);c.drawText("20",cx-radius-18,cy+radius+5,small);c.drawText("40",cx+radius+18,cy+radius+5,small);
        }
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_DOWN&&e.getAction()!=MotionEvent.ACTION_MOVE&&e.getAction()!=MotionEvent.ACTION_UP)return true;float cx=getWidth()/2f,cy=getHeight()/2f;double angle=Math.toDegrees(Math.atan2(e.getY()-cy,e.getX()-cx));if(angle<0)angle+=360;double a=angle-220;if(a<0)a+=360;if(a>280)a=280;int v=20+(int)Math.round(a/280.0*20);tgt=Math.max(20,Math.min(40,v));target=tgt;targetValue.setText(tr("target")+"  "+tgt+"°C");invalidate();if(e.getAction()==MotionEvent.ACTION_UP)send(0,tgt);return true;}
    }
    @Override protected void onDestroy(){manualClose=true;handler.removeCallbacksAndMessages(null);if(ws!=null)ws.close(1000,"app close");client.dispatcher().executorService().shutdown();super.onDestroy();}
}
