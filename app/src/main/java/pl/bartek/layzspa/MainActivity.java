package pl.bartek.layzspa;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

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
    private int port, target;
    private int heatState, pumpState, airState, jetsState, powerState;
    private String language = "pl";
    private String pumpProfile = "default";
    private String hydroMode = "standard";

    private TextView status, tempView, targetView, infoView, rssiView, appTitle, appSubtitle;
    private TextView tempLabel, controlsTitle, systemTitle, connectionTitle, settingsTitle;
    private TextView pumpProfileLabel, hydroModeLabel, settingsNote;
    private TextInputEditText hostEdit, portEdit;
    private MaterialButton connectButton, heatButton, pumpButton, airButton, jetsButton, powerButton;
    private Spinner languageSpinner, pumpSpinner, hydroSpinner;
    private TextView liveBadge;

    private final int BG = Color.rgb(6, 11, 17);
    private final int CARD = Color.rgb(14, 22, 31);
    private final int CARD2 = Color.rgb(19, 29, 40);
    private final int TEXT = Color.rgb(242, 247, 251);
    private final int MUTED = Color.rgb(137, 157, 173);
    private final int BLUE = Color.rgb(66, 190, 245);
    private final int GREEN = Color.rgb(86, 220, 178);
    private final int RED = Color.rgb(255, 103, 103);
    private final int BORDER = Color.rgb(44, 64, 81);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.rgb(4, 8, 13));
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

    private MaterialCardView island() {
        MaterialCardView c = new MaterialCardView(this);
        c.setCardBackgroundColor(CARD); c.setRadius(24); c.setStrokeColor(BORDER); c.setStrokeWidth(1); c.setUseCompatPadding(false);
        return c;
    }

    private LinearLayout content(MaterialCardView card, int pad) {
        LinearLayout box = vertical(); box.setPadding(pad, pad, pad, pad);
        card.addView(box, new ViewGroup.LayoutParams(-1, -2)); return box;
    }

    private TextView sectionTitle(String s) {
        TextView t = tv(s.toUpperCase(Locale.ROOT), 12, MUTED); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); t.setLetterSpacing(.13f); return t;
    }

    private MaterialButton button(String label, int height) {
        MaterialButton b = new MaterialButton(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER); b.setTextColor(TEXT); b.setCornerRadius(20); b.setStrokeWidth(1);
        b.setStrokeColor(ColorStateList.valueOf(BORDER)); b.setBackgroundTintList(ColorStateList.valueOf(CARD2));
        b.setMinHeight(0); b.setMinimumHeight(0); b.setPadding(10, 8, 10, 8); b.setMaxLines(2);
        b.setLayoutParams(new LinearLayout.LayoutParams(-1, height)); return b;
    }

    private TextInputLayout input(String hint, String value, boolean number) {
        TextInputLayout til = new TextInputLayout(this);
        til.setHint(hint); til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE); til.setBoxCornerRadii(16,16,16,16); til.setBoxStrokeColor(BORDER);
        TextInputEditText e = new TextInputEditText(this); e.setText(value); e.setTextColor(TEXT); e.setTextSize(15); e.setSingleLine(true);
        e.setInputType(number ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT); til.addView(e); return til;
    }

    private Spinner spinner(String[] items, int selected) {
        Spinner s = new Spinner(this); s.setBackground(rounded(CARD2, 16, BORDER));
        ArrayAdapter<String> a = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items) {
            @Override public View getView(int pos, View convert, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getView(pos, convert, parent); v.setTextColor(TEXT); v.setTextSize(14); v.setPadding(14,0,10,0); return v;
            }
        };
        s.setAdapter(a); if (selected >= 0 && selected < items.length) s.setSelection(selected); return s;
    }

    private void addControlRow(LinearLayout parent, MaterialButton left, MaterialButton right) {
        LinearLayout r = row();
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, 106, 1f); a.setMargins(0,0,6,0);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, 106, 1f); b.setMargins(6,0,0,0);
        r.addView(left,a); r.addView(right,b); parent.addView(r);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root = vertical(); root.setPadding(16,14,16,32); scroll.addView(root);

        MaterialCardView headerCard = island(); LinearLayout header = content(headerCard,16);
        LinearLayout top = row();
        LinearLayout titles = vertical();
        appTitle = tv("LAY-Z-SPA", 27, TEXT); appTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD); appTitle.setIncludeFontPadding(false);
        appSubtitle = tv("SMART SPA CONTROL", 10, MUTED); appSubtitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD); appSubtitle.setLetterSpacing(.15f);
        titles.addView(appTitle,new LinearLayout.LayoutParams(-1,40)); titles.addView(appSubtitle,new LinearLayout.LayoutParams(-1,24));
        top.addView(titles,new LinearLayout.LayoutParams(0,66,1));
        liveBadge = tv("● LIVE",10,GREEN); liveBadge.setGravity(Gravity.CENTER); liveBadge.setTypeface(Typeface.DEFAULT,Typeface.BOLD); liveBadge.setBackground(rounded(Color.rgb(12,39,36),16,Color.rgb(36,91,78)));
        top.addView(liveBadge,new LinearLayout.LayoutParams(68,40));
        languageSpinner = spinner(FLAGS, langIndex()); LinearLayout.LayoutParams langLp=new LinearLayout.LayoutParams(142,50); langLp.setMargins(8,0,0,0); top.addView(languageSpinner,langLp);
        header.addView(top); status=tv("●  DISCONNECTED",12,RED); status.setTypeface(Typeface.DEFAULT,Typeface.BOLD); status.setPadding(0,10,0,0); header.addView(status);
        root.addView(headerCard,new LinearLayout.LayoutParams(-1,128)); gap(root,12);

        MaterialCardView connCard=island(); LinearLayout conn=content(connCard,16); connectionTitle=sectionTitle("Connection"); conn.addView(connectionTitle); gap(conn,8);
        LinearLayout inputs=row();
        TextInputLayout hostTil=input("IP / hostname",host,false); hostEdit=(TextInputEditText)hostTil.getEditText(); inputs.addView(hostTil,new LinearLayout.LayoutParams(0,62,1));
        TextInputLayout portTil=input("Port",String.valueOf(port),true); portEdit=(TextInputEditText)portTil.getEditText(); LinearLayout.LayoutParams portLp=new LinearLayout.LayoutParams(96,62);portLp.setMargins(8,0,0,0);inputs.addView(portTil,portLp);
        conn.addView(inputs); connectButton=button("CONNECT",54); connectButton.setLayoutParams(new LinearLayout.LayoutParams(-1,54)); LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-1,54);clp.setMargins(0,8,0,0);conn.addView(connectButton,clp);
        root.addView(connCard); gap(root,12);

        MaterialCardView tempCard=island(); LinearLayout temp=content(tempCard,20); temp.setGravity(Gravity.CENTER_HORIZONTAL);
        tempLabel=sectionTitle("Current temperature"); tempLabel.setGravity(Gravity.CENTER); temp.addView(tempLabel,new LinearLayout.LayoutParams(-1,30));
        tempView=tv("--.-°C",66,TEXT); tempView.setTypeface(Typeface.DEFAULT,Typeface.BOLD); tempView.setGravity(Gravity.CENTER); tempView.setIncludeFontPadding(true); temp.addView(tempView,new LinearLayout.LayoutParams(-1,118));
        targetView=tv("TARGET  --°C",16,BLUE); targetView.setTypeface(Typeface.DEFAULT,Typeface.BOLD); targetView.setGravity(Gravity.CENTER); temp.addView(targetView,new LinearLayout.LayoutParams(-1,34));
        LinearLayout targetRow=row(); MaterialButton minus=button("−",68), plus=button("+",68); LinearLayout.LayoutParams tm=new LinearLayout.LayoutParams(0,68,1);tm.setMargins(0,6,6,0);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,68,1);tp.setMargins(6,6,0,0);targetRow.addView(minus,tm);targetRow.addView(plus,tp);temp.addView(targetRow);
        root.addView(tempCard); gap(root,12);

        MaterialCardView controlsCard=island(); LinearLayout controls=content(controlsCard,16); controlsTitle=sectionTitle("Spa controls");controls.addView(controlsTitle);gap(controls,8);
        heatButton=button("HEATING\nOFF",106);pumpButton=button("PUMP / FILTER\nOFF",106);jetsButton=button("HYDROJET\nOFF",106);airButton=button("BUBBLES\nOFF",106);
        addControlRow(controls,heatButton,pumpButton);gap(controls,8);addControlRow(controls,jetsButton,airButton);gap(controls,8);powerButton=button("POWER  •  OFF",90);controls.addView(powerButton);
        root.addView(controlsCard);gap(root,12);

        MaterialCardView settingsCard=island(); LinearLayout settings=content(settingsCard,16); settingsTitle=sectionTitle("Spa settings");settings.addView(settingsTitle);gap(settings,6);
        TextView sub=tv("HydroJet / pump configuration",13,MUTED);settings.addView(sub);gap(settings,8);
        pumpProfileLabel=tv("Pump profile",13,TEXT);settings.addView(pumpProfileLabel);gap(settings,4);
        String[] pumps={"Default / original","Pump 1","Pump 2","External pump"}; pumpSpinner=spinner(pumps,pumpIndex());settings.addView(pumpSpinner,new LinearLayout.LayoutParams(-1,54));gap(settings,10);
        hydroModeLabel=tv("HydroJet mode",13,TEXT);settings.addView(hydroModeLabel);gap(settings,4);
        String[] modes={"Standard","Power","Quiet"};hydroSpinner=spinner(modes,modeIndex());settings.addView(hydroSpinner,new LinearLayout.LayoutParams(-1,54));gap(settings,8);
        settingsNote=tv("Settings are saved on this device. Hardware-specific pump commands remain unchanged until the ESP8266 protocol defines them.",12,MUTED);settingsNote.setLineSpacing(2,1);settings.addView(settingsNote);
        root.addView(settingsCard);gap(root,12);

        MaterialCardView systemCard=island();LinearLayout system=content(systemCard,16);systemTitle=sectionTitle("System");system.addView(systemTitle);gap(system,8);
        infoView=tv("Model: --\nFirmware: --\nIP: --",14,TEXT);infoView.setLineSpacing(4,1);system.addView(infoView);rssiView=tv("RSSI: -- dBm",13,MUTED);rssiView.setPadding(0,10,0,0);system.addView(rssiView);root.addView(systemCard);

        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){boolean first=true;public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){String l=LANGS[pos];if(!l.equals(language)){language=l;getPreferences(MODE_PRIVATE).edit().putString("language",language).apply();applyLanguage();}else if(first) first=false;}});
        connectButton.setOnClickListener(v->readConnectionAndConnect());minus.setOnClickListener(v->setTarget(target-1));plus.setOnClickListener(v->setTarget(target+1));
        heatButton.setOnClickListener(v->toggle(3,heatState));pumpButton.setOnClickListener(v->toggle(4,pumpState));airButton.setOnClickListener(v->toggle(2,airState));jetsButton.setOnClickListener(v->toggle(11,jetsState));powerButton.setOnClickListener(v->toggle(25,powerState));
        pumpSpinner.setOnItemSelectedListener(savePumpListener());hydroSpinner.setOnItemSelectedListener(saveModeListener());
        setContentView(scroll);
    }

    private AdapterView.OnItemSelectedListener savePumpListener(){return new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){pumpProfile=new String[]{"default","pump1","pump2","external"}[pos];getPreferences(MODE_PRIVATE).edit().putString("pump_profile",pumpProfile).apply();}};}
    private AdapterView.OnItemSelectedListener saveModeListener(){return new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){hydroMode=new String[]{"standard","power","quiet"}[pos];getPreferences(MODE_PRIVATE).edit().putString("hydro_mode",hydroMode).apply();}};}
    private int langIndex(){for(int i=0;i<LANGS.length;i++)if(LANGS[i].equals(language))return i;return 0;}
    private int pumpIndex(){return new String[]{"default","pump1","pump2","external"}[0].equals(pumpProfile)?0:pumpProfile.equals("pump1")?1:pumpProfile.equals("pump2")?2:3;}
    private int modeIndex(){return hydroMode.equals("power")?1:hydroMode.equals("quiet")?2:0;}

    private void applyLanguage(){
        String l=language; languageSpinner.setSelection(langIndex());
        String title=l.equals("pl")?"STEROWANIE SMART SPA":l.equals("de")?"SMART-SPA-STEUERUNG":l.equals("fr")?"CONTRÔLE SMART SPA":l.equals("es")?"CONTROL SMART SPA":"SMART SPA CONTROL";
        appSubtitle.setText(title); connectionTitle.setText(tr("connection")); tempLabel.setText(tr("current")); controlsTitle.setText(tr("controls")); settingsTitle.setText(tr("settings")); systemTitle.setText("SYSTEM");
        connectButton.setText(tr("connect")); pumpProfileLabel.setText(tr("pumpProfile")); hydroModeLabel.setText(tr("hydroMode")); settingsNote.setText(tr("note"));
        updateAllButtons();
        if(ws==null)setStatus("●  "+tr("disconnected"));
    }

    private String tr(String k){
        if(language.equals("pl")){if(k.equals("connection"))return"POŁĄCZENIE";if(k.equals("current"))return"AKTUALNA TEMPERATURA";if(k.equals("controls"))return"STEROWANIE SPA";if(k.equals("settings"))return"USTAWIENIA SPA";if(k.equals("connect"))return"POŁĄCZ";if(k.equals("disconnected"))return"ROZŁĄCZONO";if(k.equals("pumpProfile"))return"Profil pompy";if(k.equals("hydroMode"))return"Tryb HydroJet";return"Ustawienia są zapisywane na tym urządzeniu. Komendy sprzętowe pompy pozostają bez zmian, dopóki protokół ESP8266 nie definiuje ich osobno.";}
        if(language.equals("de")){if(k.equals("connection"))return"VERBINDUNG";if(k.equals("current"))return"AKTUELLE TEMPERATUR";if(k.equals("controls"))return"SPA-STEUERUNG";if(k.equals("settings"))return"SPA-EINSTELLUNGEN";if(k.equals("connect"))return"VERBINDEN";if(k.equals("disconnected"))return"NICHT VERBUNDEN";if(k.equals("pumpProfile"))return"Pumpenprofil";if(k.equals("hydroMode"))return"HydroJet-Modus";return"Die Einstellungen werden auf diesem Gerät gespeichert. Hardware-Befehle bleiben unverändert, bis das ESP8266-Protokoll eigene Befehle definiert.";}
        if(language.equals("fr")){if(k.equals("connection"))return"CONNEXION";if(k.equals("current"))return"TEMPÉRATURE ACTUELLE";if(k.equals("controls"))return"COMMANDES DU SPA";if(k.equals("settings"))return"RÉGLAGES DU SPA";if(k.equals("connect"))return"CONNECTER";if(k.equals("disconnected"))return"DÉCONNECTÉ";if(k.equals("pumpProfile"))return"Profil de pompe";if(k.equals("hydroMode"))return"Mode HydroJet";return"Les réglages sont enregistrés sur cet appareil. Les commandes matérielles restent inchangées tant que le protocole ESP8266 ne définit pas de commandes dédiées.";}
        if(language.equals("es")){if(k.equals("connection"))return"CONEXIÓN";if(k.equals("current"))return"TEMPERATURA ACTUAL";if(k.equals("controls"))return"CONTROL DEL SPA";if(k.equals("settings"))return"AJUSTES DEL SPA";if(k.equals("connect"))return"CONECTAR";if(k.equals("disconnected"))return"DESCONECTADO";if(k.equals("pumpProfile"))return"Perfil de bomba";if(k.equals("hydroMode"))return"Modo HydroJet";return"Los ajustes se guardan en este dispositivo. Los comandos de hardware no cambian hasta que el protocolo ESP8266 los defina.";}
        if(k.equals("connection"))return"CONNECTION";if(k.equals("current"))return"CURRENT TEMPERATURE";if(k.equals("controls"))return"SPA CONTROLS";if(k.equals("settings"))return"SPA SETTINGS";if(k.equals("connect"))return"CONNECT";if(k.equals("disconnected"))return"DISCONNECTED";if(k.equals("pumpProfile"))return"Pump profile";if(k.equals("hydroMode"))return"HydroJet mode";return"Settings are saved on this device. Hardware pump commands remain unchanged until the ESP8266 protocol defines them.";
    }

    private String onOff(int state){return state==1?"ON":"OFF";}
    private String label(String k){
        if(language.equals("pl")){if(k.equals("heat"))return"GRZANIE";if(k.equals("pump"))return"POMPA / FILTR";if(k.equals("bubbles"))return"BĄBELKI";if(k.equals("target"))return"CEL";}
        if(language.equals("de")){if(k.equals("heat"))return"HEIZUNG";if(k.equals("pump"))return"PUMPE / FILTER";if(k.equals("bubbles"))return"BLASEN";if(k.equals("target"))return"ZIEL";}
        if(language.equals("fr")){if(k.equals("heat"))return"CHAUFFAGE";if(k.equals("pump"))return"POMPE / FILTRE";if(k.equals("bubbles"))return"BULLES";if(k.equals("target"))return"CIBLE";}
        if(language.equals("es")){if(k.equals("heat"))return"CALEFACCIÓN";if(k.equals("pump"))return"BOMBA / FILTRO";if(k.equals("bubbles"))return"BURBUJAS";if(k.equals("target"))return"OBJETIVO";}
        if(k.equals("heat"))return"HEATING";if(k.equals("pump"))return"PUMP / FILTER";if(k.equals("bubbles"))return"BUBBLES";return"TARGET";
    }
    private void updateAllButtons(){
        updateButton(heatButton,label("heat"),heatState);updateButton(pumpButton,label("pump"),pumpState);updateButton(jetsButton,"HYDROJET",jetsState);updateButton(airButton,label("bubbles"),airState);updatePowerButton();
        if(target>0)targetView.setText(label("target")+"  "+target+"°C");
    }
    private void updateButton(MaterialButton b,String label,int state){if(b==null)return;boolean on=state==1;b.setText(label+"\n"+onOff(state));b.setTextColor(on?Color.rgb(7,20,24):TEXT);b.setBackgroundTintList(ColorStateList.valueOf(on?GREEN:CARD2));b.setStrokeColor(ColorStateList.valueOf(on?GREEN:BORDER));}
    private void updatePowerButton(){boolean on=powerState==1;String p=language.equals("pl")?"ZASILANIE":language.equals("de")?"STROM":language.equals("fr")?"ALIMENTATION":language.equals("es")?"ALIMENTACIÓN":"POWER";powerButton.setText(p+"  •  "+onOff(powerState));powerButton.setTextColor(on?Color.rgb(7,18,24):TEXT);powerButton.setBackgroundTintList(ColorStateList.valueOf(on?BLUE:CARD2));powerButton.setStrokeColor(ColorStateList.valueOf(on?BLUE:BORDER));}

    private void readConnectionAndConnect(){String h=hostEdit.getText()==null?"":hostEdit.getText().toString().trim();String ps=portEdit.getText()==null?"":portEdit.getText().toString().trim();if(h.isEmpty()){toast("Host required");return;}int p;try{p=Integer.parseInt(ps);}catch(Exception e){p=DEFAULT_PORT;}if(p<1||p>65535){toast("Invalid port");return;}host=h;port=p;getPreferences(MODE_PRIVATE).edit().putString("host",host).putInt("port",port).apply();manualClose=false;connect();}
    private void setTarget(int t){if(target==0)return;t=Math.max(20,Math.min(40,t));send(0,t);}
    private void toggle(int cmd,int state){send(cmd,state==1?0:1);}
    private void send(int cmd,int value){if(ws==null){toast("No connection to ESP8266");return;}try{JSONObject o=new JSONObject();o.put("CMD",cmd);o.put("VALUE",value);o.put("XTIME",0);o.put("INTERVAL",0);o.put("TXT","");ws.send(o.toString());}catch(Exception e){toast("Command error");}}
    private void connect(){if(host==null||host.isEmpty())return;manualClose=false;if(ws!=null)ws.close(1000,"reconnect");setStatus("●  "+(language.equals("pl")?"ŁĄCZENIE":language.equals("de")?"VERBINDUNG":language.equals("fr")?"CONNEXION":language.equals("es")?"CONECTANDO":"CONNECTING"));try{ws=client.newWebSocket(new Request.Builder().url("ws://"+host+":"+port+"/").build(),new WebSocketListener(){@Override public void onOpen(WebSocket w,Response r){runOnUiThread(()->{setStatus("●  "+(language.equals("pl")?"POŁĄCZONO":language.equals("de")?"VERBUNDEN":language.equals("fr")?"CONNECTÉ":language.equals("es")?"CONECTADO":"CONNECTED"));connectButton.setText(language.equals("pl")?"POŁĄCZONO":language.equals("de")?"VERBUNDEN":language.equals("fr")?"CONNECTÉ":language.equals("es")?"CONECTADO":"CONNECTED");});}@Override public void onMessage(WebSocket w,String text){parse(text);}@Override public void onFailure(WebSocket w,Throwable t,Response r){runOnUiThread(()->{setStatus("●  "+tr("disconnected"));connectButton.setText(tr("connect"));});if(!manualClose)reconnectLater();}@Override public void onClosed(WebSocket w,int c,String reason){if(!manualClose)reconnectLater();}});}catch(Exception e){setStatus("●  INVALID ADDRESS");}}
    private void reconnectLater(){handler.removeCallbacksAndMessages(null);handler.postDelayed(this::connect,3000);}
    private void parse(String text){try{JSONObject o=new JSONObject(text);String c=o.optString("CONTENT");if("STATES".equals(c)){target=o.optInt("TGT",0);int temp=o.optInt("TMP",0);heatState=o.optInt("GRN",0);pumpState=o.optInt("FLT",0);airState=o.optInt("AIR",0);jetsState=o.optInt("HJT",0);powerState=o.optInt("PWR",0);runOnUiThread(()->{tempView.setText(temp+"°C");targetView.setText(label("target")+"  "+target+"°C");updateAllButtons();});}else if("OTHER".equals(c)){String model=o.optString("MODEL","--"),fw=o.optString("FW","--"),espIp=o.optString("IP",host);int rssi=o.optInt("RSSI",0);runOnUiThread(()->{String modelKey=language.equals("pl")?"Model":language.equals("de")?"Modell":language.equals("fr")?"Modèle":language.equals("es")?"Modelo":"Model";infoView.setText(modelKey+": "+model+"\nFirmware: "+fw+"\nIP: "+espIp);rssiView.setText("RSSI: "+rssi+" dBm");});}}catch(Exception ignored){}}
    private void setStatus(String s){if(status==null)return;status.setText(s);status.setTextColor((s.contains("POŁĄCZ")||s.contains("VERBUNDEN")||s.contains("CONNECTÉ")||s.contains("CONECTADO")||s.contains("CONNECTED"))?GREEN:(s.contains("ŁĄC")||s.contains("VERBIND")||s.contains("CONNEX")||s.contains("CONECT")||s.contains("CONNECT"))?BLUE:RED);}
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());}
    @Override protected void onDestroy(){manualClose=true;handler.removeCallbacksAndMessages(null);if(ws!=null)ws.close(1000,"app close");client.dispatcher().executorService().shutdown();super.onDestroy();}
}
