package pl.smartspa;

import android.appwidget.AppWidgetManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Build;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import android.text.format.Formatter;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

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

import pl.smartspa.ai.SpaAiEngine;

public class MainActivity extends AppCompatActivity {
    private static final String DEFAULT_HOST="192.168.1.40"; private static final int DEFAULT_PORT=81;
    private static final String[] LANGS={"pl","en","de","fr","es"};
    private static final String[] FLAGS={"🇵🇱  PL","🇬🇧  EN","🇩🇪  DE","🇫🇷  FR","🇪🇸  ES"};
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final OkHttpClient client=new OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build();
    private WebSocket ws; private boolean manualClose;
    private String host; private int port,target=30,currentTemp;
    private int heatState,pumpState,airState,jetsState,powerState; private String otherModel="--",otherFw="--",otherIp="--"; private int otherRssi=0; private final ExecutorService discoveryExecutor=Executors.newSingleThreadExecutor(); private volatile boolean discoveryRunning=false; private int heatCommandState=-1; private String language="pl";
    private String pumpProfile="default",hydroMode="standard";
    private TextView networkStateView,networkDetailsView; private TextView status,tempCaption,targetValue,settingsTitle,connectionTitle,connectionSummary,infoView,rssiView;
    private TextView heatLabel,pumpLabel,airLabel,jetsLabel,powerLabel,settingsNote,hostTitle,portTitle;
    private TextView sessionTimerView; private MaterialButton sessionButton;
    private TextView weatherTitle,weatherDetails,weatherRecommendation,smartAiSummary,weatherInlineResult; private MaterialButton weatherRefresh,weatherSetButton,weatherLocationButton,smartAiBar,weatherInlineSearch; private EditText weatherInlineQuery; private LinearLayout smartAiPanel,weatherInlineResultsContainer;
    private double weatherLat=0,weatherLon=0; private String weatherCity=""; private int outsideTemp=0,recommendedTemp=37; private String weatherText=""; private int sessionDurationMinutes=30; private long sessionEndAt=0L;
    private final Runnable sessionTick=new Runnable(){@Override public void run(){updateSessionTimer();}};
    private TextInputEditText hostEdit,portEdit; private MaterialButton connectButton,backButton;
    private Spinner languageSpinner,pumpSpinner,hydroSpinner; private TemperatureDial dial; private View mainScreen,settingsScreen,connectionScreen; private FrameLayout rootFrame; private View drawer; private MaterialButton quickHeat,quickAir,quickPump; private MaterialButton[] sessionQuickButtons;
    private int BG,CARD,CARD2,TEXT,MUTED,BLUE,GREEN,RED,BORDER,DIAL_TRACK;
    private static final String PREF_THEME="theme_mode";
    private static final int THEME_SYSTEM=0,THEME_LIGHT=1,THEME_DARK=2;

    @Override protected void onCreate(Bundle b){
        int savedTheme=getSharedPreferences("app_settings",MODE_PRIVATE).getInt(PREF_THEME,THEME_SYSTEM);
        getDelegate().setLocalNightMode(savedTheme==THEME_DARK?AppCompatDelegate.MODE_NIGHT_YES:savedTheme==THEME_LIGHT?AppCompatDelegate.MODE_NIGHT_NO:AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        super.onCreate(b);
        initThemeColors();
        applyWindowTheme();
        SharedPreferences p=getPreferences(MODE_PRIVATE);host=p.getString("host",DEFAULT_HOST);port=p.getInt("port",DEFAULT_PORT);getSharedPreferences("spa",MODE_PRIVATE).edit().putString("host",host).putInt("port",port).apply();language=p.getString("language","pl");pumpProfile=p.getString("pump_profile","default");hydroMode=p.getString("hydro_mode","standard");sessionDurationMinutes=Math.max(1,Math.min(30,p.getInt("session_duration",30)));sessionEndAt=p.getLong("session_end",0L);buildUi();applyLanguage();updateSessionTimer();loadWeatherLocation();createNotificationChannel();scheduleWeatherNotifications();scheduleSpaMonitor(this);connect();fetchWeather();if(Build.VERSION.SDK_INT>=33)ActivityCompat.requestPermissions(this,new String[]{"android.permission.POST_NOTIFICATIONS"},700);}
    private void initThemeColors(){
        boolean dark=(getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if(dark){
            BG=Color.rgb(11,16,22); CARD=Color.rgb(18,26,34); CARD2=Color.rgb(24,35,45); TEXT=Color.rgb(242,247,250); MUTED=Color.rgb(155,175,189); BLUE=Color.rgb(69,196,255); GREEN=Color.rgb(99,230,190); RED=Color.rgb(255,107,107); BORDER=Color.rgb(42,58,70); DIAL_TRACK=Color.rgb(42,57,69);
        }else{
            BG=Color.rgb(242,247,250); CARD=Color.WHITE; CARD2=Color.rgb(247,250,252); TEXT=Color.rgb(25,42,55); MUTED=Color.rgb(91,111,124); BLUE=Color.rgb(28,151,221); GREEN=Color.rgb(43,190,136); RED=Color.rgb(222,76,76); BORDER=Color.rgb(213,225,233); DIAL_TRACK=Color.rgb(224,234,241);
        }
    }
    private void applyWindowTheme(){
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        int flags=0; if(Build.VERSION.SDK_INT>=23 && isLightTheme()) flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; if(Build.VERSION.SDK_INT>=26 && isLightTheme()) flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    private boolean isLightTheme(){return (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)!=android.content.res.Configuration.UI_MODE_NIGHT_YES;}
    private TextView tv(String s,float size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setIncludeFontPadding(true);return v;}
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private void gap(LinearLayout r,int h){r.addView(new Space(this),new LinearLayout.LayoutParams(1,h));}
    private MaterialCardView card(){MaterialCardView c=new MaterialCardView(this);c.setCardBackgroundColor(CARD);c.setRadius(28);c.setStrokeColor(BORDER);c.setStrokeWidth(1);return c;}
    private LinearLayout inside(MaterialCardView c,int p){LinearLayout b=vertical();b.setPadding(p,p,p,p);c.addView(b,new ViewGroup.LayoutParams(-1,-2));return b;}
    private TextView cap(String s){TextView t=tv(s.toUpperCase(Locale.ROOT),12,MUTED);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setLetterSpacing(.10f);return t;}
    private MaterialButton bigButton(String text){MaterialButton b=new MaterialButton(this);b.setText(text);b.setAllCaps(false);b.setTextSize(16);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(TEXT);b.setCornerRadius(20);b.setStrokeWidth(1);b.setStrokeColor(android.content.res.ColorStateList.valueOf(BORDER));b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(CARD2));b.setInsetTop(0);b.setInsetBottom(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(18,0,18,0);return b;}
    private Spinner spinner(String[] items,int selected){Spinner s=new Spinner(this);s.setBackgroundResource(android.R.drawable.editbox_background);ArrayAdapter<String>a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,items){public View getView(int p,View c,ViewGroup parent){TextView v=(TextView)super.getView(p,c,parent);v.setTextColor(TEXT);v.setTextSize(15);v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(16,0,12,0);return v;}public View getDropDownView(int p,View c,ViewGroup parent){TextView v=(TextView)super.getDropDownView(p,c,parent);v.setTextColor(TEXT);v.setTextSize(16);v.setPadding(18,18,12,18);v.setBackgroundColor(CARD);return v;}};s.setAdapter(a);s.setSelection(selected);return s;}

    private void buildUi(){
        rootFrame=new FrameLayout(this);rootFrame.setBackgroundColor(BG);
        mainScreen=buildMain();settingsScreen=buildSettings();connectionScreen=buildConnection();
        rootFrame.addView(mainScreen,new FrameLayout.LayoutParams(-1,-1));settingsScreen.setVisibility(View.GONE);connectionScreen.setVisibility(View.GONE);
        rootFrame.addView(settingsScreen,new FrameLayout.LayoutParams(-1,-1));rootFrame.addView(connectionScreen,new FrameLayout.LayoutParams(-1,-1));
        drawer=buildDrawer();drawer.setVisibility(View.GONE);rootFrame.addView(drawer,new FrameLayout.LayoutParams(-1,-1));setContentView(rootFrame);
    }

    private View buildMain(){
        ScrollView sc=new ScrollView(this);
        sc.setFillViewport(true);
        LinearLayout root=vertical();
        root.setPadding(dp(6),dp(6),dp(6),dp(18));
        sc.addView(root);

        // HEADER — kompakt, jak na ustalonym projekcie referencyjnym.
        MaterialCardView head=card();
        LinearLayout hb=inside(head,dp(7));
        LinearLayout top=row();
        MaterialButton menu=bigButton("☰");
        menu.setTextSize(21);
        menu.setPadding(0,0,0,0);
        menu.setOnClickListener(v->toggleDrawer());
        top.addView(menu,new LinearLayout.LayoutParams(dp(48),dp(46)));
        TextView title=tv("Smart Spa",24,TEXT);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        status=tv("●  "+tr("disconnected"),12,RED);
        status.setGravity(Gravity.CENTER);
        status.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        top.addView(status,new LinearLayout.LayoutParams(0,dp(46),1));
        languageSpinner=spinner(FLAGS,langIndex());
        LinearLayout.LayoutParams lsp=new LinearLayout.LayoutParams(dp(94),dp(46));
        lsp.setMargins(dp(4),0,0,0);
        top.addView(languageSpinner,lsp);
        hb.addView(top);
        root.addView(head,new LinearLayout.LayoutParams(-1,-2));
        gap(root,dp(6));

        MaterialCardView tempCard=card();
        LinearLayout tb=inside(tempCard,dp(9));
        tempCaption=cap(tr("currentTemp"));
        tempCaption.setGravity(Gravity.CENTER);
        tb.addView(tempCaption,new LinearLayout.LayoutParams(-1,dp(24)));

        dial=new TemperatureDial();
        tb.addView(dial,new LinearLayout.LayoutParams(-1,dp(285)));

        targetValue=tv(tr("target")+" 30°C",27,TEXT);
        targetValue.setGravity(Gravity.CENTER);
        targetValue.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams targetLp=new LinearLayout.LayoutParams(-1,dp(46));
        targetLp.setMargins(0,0,0,dp(4));
        tb.addView(targetValue,targetLp);

        // SESJA — zwarta, ale nadal czytelna. Wszystkie szybkie czasy w jednym rzędzie.
        MaterialCardView sessionCard=new MaterialCardView(this);
        sessionCard.setCardBackgroundColor(CARD2);
        sessionCard.setRadius(dp(16));
        sessionCard.setStrokeColor(BORDER);
        sessionCard.setStrokeWidth(1);
        LinearLayout sb=inside(sessionCard,dp(8));

        LinearLayout sessionHead=row();
        TextView sessionTitle=cap("SESJA SPA");
        sessionHead.addView(sessionTitle,new LinearLayout.LayoutParams(0,dp(22),1));
        TextView lengthLabel=cap("DŁUGOŚĆ");
        lengthLabel.setGravity(Gravity.CENTER);
        sessionHead.addView(lengthLabel,new LinearLayout.LayoutParams(dp(92),dp(22)));
        sb.addView(sessionHead);

        LinearLayout session=row();
        session.setGravity(Gravity.CENTER_VERTICAL);
        sessionTimerView=tv("",23,TEXT);
        sessionTimerView.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        sessionTimerView.setGravity(Gravity.CENTER_VERTICAL);
        session.addView(sessionTimerView,new LinearLayout.LayoutParams(0,dp(44),1));
        sessionButton=bigButton("");
        sessionButton.setTextSize(12);
        sessionButton.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        sessionButton.setPadding(dp(7),0,dp(7),0);
        sessionButton.setCornerRadius(dp(14));
        sessionButton.setOnClickListener(v->showSessionDurationDialog());
        LinearLayout.LayoutParams sessionBtnLp=new LinearLayout.LayoutParams(dp(92),dp(42));
        sessionBtnLp.setMargins(dp(5),0,0,0);
        session.addView(sessionButton,sessionBtnLp);
        sb.addView(session,new LinearLayout.LayoutParams(-1,dp(44)));

        gap(sb,dp(4));
        LinearLayout durationRow=row();
        durationRow.setPadding(0,0,0,0);
        int[] durations={1,5,10,15,20,25,30};
        sessionQuickButtons=new MaterialButton[durations.length];
        for(int i=0;i<durations.length;i++){
            final int minutes=durations[i];
            MaterialButton db=bigButton(minutes+" "+tr("minutes"));
            db.setTextSize(9.5f);
            db.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            db.setPadding(0,0,0,0);
            db.setMinWidth(0);
            db.setMinimumWidth(0);
            db.setCornerRadius(dp(11));
            db.setOnClickListener(v->setSessionDuration(minutes));
            sessionQuickButtons[i]=db;
            LinearLayout.LayoutParams dpLp=new LinearLayout.LayoutParams(0,dp(36),1);
            dpLp.setMargins(dp(1),0,dp(1),0);
            durationRow.addView(db,dpLp);
        }
        sb.addView(durationRow,new LinearLayout.LayoutParams(-1,dp(38)));
        updateSessionQuickButtons();
        tb.addView(sessionCard,new LinearLayout.LayoutParams(-1,-2));
        gap(tb,dp(7));

        // Główne sterowanie — trzy przyciski zawsze w jednym rzędzie.
        LinearLayout quick=row();
        quickHeat=bigButton("♨  "+label("heat"));
        quickAir=bigButton("≈  "+label("bubbles"));
        quickPump=bigButton("◉  "+label("filtering"));
        quickHeat.setTextSize(13);
        quickAir.setTextSize(13);
        quickPump.setTextSize(13);
        LinearLayout.LayoutParams q1=new LinearLayout.LayoutParams(0,dp(50),1);q1.setMargins(dp(1),0,dp(1),0);
        LinearLayout.LayoutParams q2=new LinearLayout.LayoutParams(0,dp(50),1);q2.setMargins(dp(1),0,dp(1),0);
        LinearLayout.LayoutParams q3=new LinearLayout.LayoutParams(0,dp(50),1);q3.setMargins(dp(1),0,dp(1),0);
        quick.addView(quickHeat,q1);quick.addView(quickAir,q2);quick.addView(quickPump,q3);
        tb.addView(quick,new LinearLayout.LayoutParams(-1,dp(52)));
        quickHeat.setOnClickListener(v->toggleHeating());
        quickAir.setOnClickListener(v->toggleBubbles());
        quickPump.setOnClickListener(v->toggle(4,pumpState));

        root.addView(tempCard,new LinearLayout.LayoutParams(-1,-2));
        gap(root,dp(7));

        // SMART AI — kompaktna belka na froncie. Kliknięcie rozwija panel inline, bez osobnego okna.
        MaterialCardView aiCard=card();
        aiCard.setRadius(dp(16));
        LinearLayout aiBox=inside(aiCard,dp(7));
        LinearLayout aiRow=row();
        smartAiBar=bigButton("✦  SMART AI  ·  POGODA");
        smartAiBar.setTextSize(12);
        smartAiBar.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        smartAiBar.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        smartAiBar.setPadding(dp(12),0,dp(8),0);
        smartAiBar.setCornerRadius(dp(13));
        smartAiSummary=tv("Sprawdzanie warunków…",11,MUTED);
        smartAiSummary.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams aiSummaryLp=new LinearLayout.LayoutParams(0,dp(38),1);
        aiSummaryLp.setMargins(dp(7),0,dp(2),0);
        aiRow.addView(smartAiBar,new LinearLayout.LayoutParams(dp(154),dp(38)));
        aiRow.addView(smartAiSummary,aiSummaryLp);
        aiBox.addView(aiRow);

        smartAiPanel=vertical();
        smartAiPanel.setVisibility(View.GONE);
        gap(smartAiPanel,dp(7));
        TextView aiLocationTitle=tv("LOKALIZACJA JACUZZI",12,MUTED);
        aiLocationTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        aiLocationTitle.setLetterSpacing(.08f);
        smartAiPanel.addView(aiLocationTitle);
        gap(smartAiPanel,dp(5));
        LinearLayout searchRow=row();
        weatherInlineQuery=new EditText(this);
        weatherInlineQuery.setSingleLine(true);
        weatherInlineQuery.setText(weatherCity);
        weatherInlineQuery.setHint("Wpisz miejscowość, np. Gościęcin");
        weatherInlineQuery.setTextSize(17);
        weatherInlineQuery.setTextColor(TEXT);
        weatherInlineQuery.setHintTextColor(MUTED);
        weatherInlineQuery.setBackgroundResource(android.R.drawable.editbox_background);
        weatherInlineQuery.setPadding(dp(14),0,dp(12),0);
        weatherInlineQuery.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        searchRow.addView(weatherInlineQuery,new LinearLayout.LayoutParams(0,dp(54),1));
        weatherInlineSearch=bigButton("SZUKAJ");
        weatherInlineSearch.setTextSize(11);
        weatherInlineSearch.setPadding(dp(8),0,dp(8),0);
        LinearLayout.LayoutParams searchLp=new LinearLayout.LayoutParams(dp(76),dp(54));
        searchLp.setMargins(dp(6),0,0,0);
        searchRow.addView(weatherInlineSearch,searchLp);
        smartAiPanel.addView(searchRow);
        gap(smartAiPanel,dp(5));
        weatherInlineResult=tv("",15,TEXT);
        weatherInlineResult.setLineSpacing(0,1.12f);
        smartAiPanel.addView(weatherInlineResult);
        weatherInlineResultsContainer=vertical();
        smartAiPanel.addView(weatherInlineResultsContainer);
        gap(smartAiPanel,dp(7));
        LinearLayout aiActions=row();
        weatherSetButton=bigButton("USTAW ZALECANĄ");
        weatherRefresh=bigButton("↻  ODŚWIEŻ");
        weatherSetButton.setTextSize(11); weatherRefresh.setTextSize(11);
        aiActions.addView(weatherSetButton,new LinearLayout.LayoutParams(0,dp(46),1));
        LinearLayout.LayoutParams refreshLp=new LinearLayout.LayoutParams(0,dp(46),1); refreshLp.setMargins(dp(6),0,0,0);
        aiActions.addView(weatherRefresh,refreshLp);
        smartAiPanel.addView(aiActions);
        aiBox.addView(smartAiPanel);
        smartAiBar.setOnClickListener(v->toggleSmartAiPanel());
        smartAiSummary.setOnClickListener(v->toggleSmartAiPanel());
        weatherInlineSearch.setOnClickListener(v->searchWeatherInline());
        weatherSetButton.setOnClickListener(v->setTarget(recommendedTemp));
        weatherRefresh.setOnClickListener(v->fetchWeather());
        root.addView(aiCard,new LinearLayout.LayoutParams(-1,-2));
        return sc;
    }

    private View buildSettings(){ScrollView sc=new ScrollView(this);sc.setFillViewport(true);LinearLayout root=vertical();root.setPadding(dp(8),dp(8),dp(8),dp(30));sc.addView(root);
        MaterialCardView head=card();LinearLayout hb=inside(head,dp(12));LinearLayout h=row();backButton=bigButton("‹   "+tr("back"));backButton.setTextSize(15);backButton.setOnClickListener(v->showMain());h.addView(backButton,new LinearLayout.LayoutParams(0,dp(60),1));settingsTitle=tv(tr("spaSettings"),26,TEXT);settingsTitle.setGravity(Gravity.CENTER);settingsTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);settingsTitle.setSingleLine(true);h.addView(settingsTitle,new LinearLayout.LayoutParams(0,dp(60),2));hb.addView(h);root.addView(head,new LinearLayout.LayoutParams(-1,-2));gap(root,dp(10));
        MaterialCardView controls=card();LinearLayout c=inside(controls,dp(14));TextView ct=cap(tr("spaControl"));ct.setTextSize(13);c.addView(ct);gap(c,dp(8));heatLabel=tv("",18,TEXT);pumpLabel=tv("",18,TEXT);airLabel=tv("",18,TEXT);jetsLabel=tv("",18,TEXT);powerLabel=tv("",18,TEXT);addSwitch(c,heatLabel,3);addSwitch(c,pumpLabel,4);addSwitch(c,airLabel,2);addSwitch(c,jetsLabel,11);addSwitch(c,powerLabel,25);root.addView(controls);gap(root,dp(10));
        MaterialCardView spa=card();LinearLayout s=inside(spa,dp(16));TextView st=cap(tr("spaSettings"));st.setTextSize(13);s.addView(st);gap(s,dp(10));TextView pl=tv(tr("pumpProfile"),17,TEXT);pl.setTypeface(Typeface.DEFAULT,Typeface.BOLD);s.addView(pl);gap(s,dp(6));pumpSpinner=spinner(new String[]{tr("default"),tr("pump1"),tr("pump2"),tr("external")},pumpIndex());s.addView(pumpSpinner,new LinearLayout.LayoutParams(-1,dp(68)));gap(s,dp(14));TextView hl=tv(tr("hydroMode"),17,TEXT);hl.setTypeface(Typeface.DEFAULT,Typeface.BOLD);s.addView(hl);gap(s,dp(6));hydroSpinner=spinner(new String[]{tr("standard"),tr("power"),tr("quiet")},hydroIndex());s.addView(hydroSpinner,new LinearLayout.LayoutParams(-1,dp(68)));gap(s,dp(12));settingsNote=tv(tr("note"),14,MUTED);settingsNote.setLineSpacing(0,1.2f);s.addView(settingsNote);pumpSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){pumpProfile=new String[]{"default","pump1","pump2","external"}[pos];saveSettings();}});hydroSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){hydroMode=new String[]{"standard","power","quiet"}[pos];saveSettings();}});root.addView(spa);gap(root,dp(10));
        root.addView(buildThemeSettings());gap(root,dp(10));root.addView(buildNotificationSettings());gap(root,dp(10));root.addView(buildWeatherSettings());return sc;}

    private View buildThemeSettings(){
        MaterialCardView c=card(); LinearLayout b=inside(c,dp(16));
        TextView t=cap("WYGLĄD APLIKACJI"); t.setTextSize(13); b.addView(t); gap(b,dp(8));
        TextView label=tv("Motyw",17,TEXT); label.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.addView(label); gap(b,dp(6));
        Spinner themeSpinner=spinner(new String[]{"Systemowy","Jasny","Ciemny"},getThemeMode());
        b.addView(themeSpinner,new LinearLayout.LayoutParams(-1,dp(68))); gap(b,dp(8));
        TextView note=tv("Systemowy automatycznie dopasuje aplikację do trybu jasnego lub ciemnego telefonu.",14,MUTED); note.setLineSpacing(0,1.15f); b.addView(note);
        themeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(AdapterView<?> p){}
            public void onItemSelected(AdapterView<?> p,View v,int pos,long id){
                int current=getThemeMode(); if(pos==current)return;
                getSharedPreferences("app_settings",MODE_PRIVATE).edit().putInt(PREF_THEME,pos).apply();
                getDelegate().setLocalNightMode(pos==THEME_DARK?AppCompatDelegate.MODE_NIGHT_YES:pos==THEME_LIGHT?AppCompatDelegate.MODE_NIGHT_NO:AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                recreate();
            }
        });
        return c;
    }
    private int getThemeMode(){return getSharedPreferences("app_settings",MODE_PRIVATE).getInt(PREF_THEME,THEME_SYSTEM);}


    private View buildNotificationSettings(){
        MaterialCardView c=card(); LinearLayout b=inside(c,dp(16));
        TextView t=cap("POWIADOMIENIA"); t.setTextSize(13); b.addView(t); gap(b,dp(8));
        TextView n=tv("Smart Spa pamięta te ustawienia i może sprawdzać jacuzzi w tle, nawet gdy ekran aplikacji jest zamknięty.",14,MUTED); n.setLineSpacing(0,1.15f); b.addView(n); gap(b,dp(10));
        addNotificationSwitch(b,"Woda osiągnęła temperaturę docelową","target",true);
        addNotificationSwitch(b,"Zbyt długie grzanie","long_heat",true);
        addNotificationSwitch(b,"Utrata / powrót połączenia","connection",true);
        addNotificationSwitch(b,"Błędy jacuzzi","errors",true);
        gap(b,dp(6));
        TextView h=tv("Ostrzegaj o długim grzaniu po",16,TEXT); h.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.addView(h); gap(b,dp(5));
        Spinner hours=spinner(new String[]{"1 godzina","2 godziny","3 godziny","4 godziny","5 godzin","6 godzin","8 godzin","10 godzin","12 godzin"},Math.max(0,Math.min(8,getSharedPreferences("notifications",MODE_PRIVATE).getInt("heat_hours",3)-1)));
        b.addView(hours,new LinearLayout.LayoutParams(-1,dp(62)));
        hours.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int pos,long id){getSharedPreferences("notifications",MODE_PRIVATE).edit().putInt("heat_hours",pos+1).apply();}});
        return c;
    }
    private void addNotificationSwitch(LinearLayout parent,String title,String key,boolean def){
        LinearLayout r=row(); TextView l=tv(title,17,TEXT); l.setTypeface(Typeface.DEFAULT,Typeface.BOLD); r.addView(l,new LinearLayout.LayoutParams(0,dp(58),1)); Switch sw=new Switch(this); sw.setChecked(getSharedPreferences("notifications",MODE_PRIVATE).getBoolean(key,def)); sw.setOnCheckedChangeListener((buttonView,isChecked)->getSharedPreferences("notifications",MODE_PRIVATE).edit().putBoolean(key,isChecked).apply()); r.addView(sw,new LinearLayout.LayoutParams(dp(62),dp(58))); parent.addView(r); gap(parent,dp(3));
    }

    private View buildWeatherCard(){
        MaterialCardView c=card(); LinearLayout b=inside(c,18);
        weatherTitle=tv("INTELIGENTNA TEMPERATURA",15,TEXT); weatherTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.addView(weatherTitle);
        gap(b,6);
        weatherDetails=tv("Pogoda: --\nNa zewnątrz: --",15,MUTED); weatherDetails.setLineSpacing(0,1.15f); b.addView(weatherDetails);
        gap(b,8);
        weatherRecommendation=tv("Zalecana temperatura SPA: --",20,TEXT); weatherRecommendation.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.addView(weatherRecommendation);
        gap(b,10);
        LinearLayout r=row(); weatherSetButton=bigButton("USTAW ZALECANĄ"); weatherRefresh=bigButton("↻  ODŚWIEŻ"); r.addView(weatherSetButton,new LinearLayout.LayoutParams(0,64,1)); r.addView(weatherRefresh,new LinearLayout.LayoutParams(0,64,1)); b.addView(r);
        weatherSetButton.setOnClickListener(v->setTarget(recommendedTemp)); weatherRefresh.setOnClickListener(v->fetchWeather());
        return c;
    }
    private View buildWeatherSettings(){
        MaterialCardView c=card(); LinearLayout b=inside(c,dp(16)); TextView t=cap("Lokalizacja SPA"); t.setTextSize(13); b.addView(t); gap(b,dp(7));
        weatherLocationButton=bigButton("📍  "+(weatherCity.isEmpty()?"Ustaw lokalizację":weatherCity)); weatherLocationButton.setTextSize(15); b.addView(weatherLocationButton,new LinearLayout.LayoutParams(-1,dp(62)));
        weatherLocationButton.setOnClickListener(v->showWeatherLocationDialog()); gap(b,dp(8));
        TextView n=tv("Pogoda jest pobierana dla miejsca, w którym znajduje się jacuzzi. Aplikacja nie musi korzystać z GPS telefonu.",14,MUTED); n.setLineSpacing(0,1.15f); b.addView(n);
        return c;
    }
    private void loadWeatherLocation(){SharedPreferences p=getSharedPreferences("weather",0);weatherLat=p.getFloat("lat",0);weatherLon=p.getFloat("lon",0);weatherCity=p.getString("city","");}
    private void saveWeatherLocation(String city,double lat,double lon){weatherCity=city;weatherLat=lat;weatherLon=lon;getSharedPreferences("weather",0).edit().putString("city",city).putFloat("lat",(float)lat).putFloat("lon",(float)lon).apply();if(weatherLocationButton!=null)weatherLocationButton.setText("📍  "+city);if(weatherInlineQuery!=null)weatherInlineQuery.setText(city);if(weatherInlineResult!=null)weatherInlineResult.setText("✓ Wybrano: "+city);if(weatherInlineResultsContainer!=null)weatherInlineResultsContainer.removeAllViews();fetchWeather();}
    private void showWeatherLocationDialog(){
        LinearLayout box=vertical(); box.setPadding(22,8,22,8); TextView hint=tv("Wpisz miejscowość, np. Gościęcin",14,MUTED); box.addView(hint); EditText q=new EditText(this); q.setSingleLine(true); q.setText(weatherCity); q.setTextSize(18); q.setTextColor(TEXT); q.setHintTextColor(MUTED); q.setPadding(dp(14),0,dp(12),0); q.setBackgroundResource(android.R.drawable.editbox_background); box.addView(q,new LinearLayout.LayoutParams(-1,dp(64))); TextView result=tv("",15,TEXT); result.setLineSpacing(0,1.15f); box.addView(result); MaterialButton search=bigButton("SZUKAJ");box.addView(search,new LinearLayout.LayoutParams(-1,64));
        android.app.AlertDialog d=new android.app.AlertDialog.Builder(this).setTitle("Lokalizacja jacuzzi").setView(box).setNegativeButton("ANULUJ",null).create();
        search.setOnClickListener(v->{String name=q.getText().toString().trim();if(name.isEmpty())return;search.setEnabled(false);result.setText("Szukam…");new Thread(()->{try{Request req=new Request.Builder().url("https://geocoding-api.open-meteo.com/v1/search?name="+java.net.URLEncoder.encode(name,"UTF-8")+"&count=5&language=pl&format=json&countryCode=PL").build();Response resp=client.newCall(req).execute();String body=resp.body()!=null?resp.body().string():"";JSONObject o=new JSONObject(body);org.json.JSONArray a=o.optJSONArray("results");runOnUiThread(()->{search.setEnabled(true);if(a==null||a.length()==0){result.setText("Nie znaleziono miejscowości.");return;}result.setText("Wybierz wynik:");for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;String label=x.optString("name","")+" · "+x.optString("admin1","");final String selectedLabel=label;final double lat=x.optDouble("latitude"),lon=x.optDouble("longitude");MaterialButton item=bigButton(selectedLabel);box.addView(item,new LinearLayout.LayoutParams(-1,58));item.setOnClickListener(z->{saveWeatherLocation(selectedLabel,lat,lon);d.dismiss();});}});}catch(Exception e){runOnUiThread(()->{search.setEnabled(true);result.setText("Błąd połączenia z serwisem pogody.");});}}).start();});
        d.show();
    }
    private void fetchWeather(){if(weatherLat==0&&weatherLon==0){updateWeatherViews();return;}new Thread(()->{try{String u="https://api.open-meteo.com/v1/forecast?latitude="+weatherLat+"&longitude="+weatherLon+"&current=temperature_2m,weather_code,cloud_cover,wind_speed_10m&hourly=temperature_2m,weather_code&timezone=auto&forecast_days=2";Response resp=client.newCall(new Request.Builder().url(u).build()).execute();String body=resp.body()!=null?resp.body().string():"";JSONObject o=new JSONObject(body);JSONObject cur=o.optJSONObject("current");outsideTemp=cur!=null?(int)Math.round(cur.optDouble("temperature_2m",0)):0;int code=cur!=null?cur.optInt("weather_code",0):0;weatherText=SpaAiEngine.weatherDescription(code);recommendedTemp=SpaAiEngine.recommendedTemperature(outsideTemp,code);getSharedPreferences("weather",0).edit().putLong("updated",System.currentTimeMillis()).putInt("outside",outsideTemp).putInt("recommended",recommendedTemp).putString("description",weatherText).apply();runOnUiThread(()->{updateWeatherViews();});}catch(Exception e){runOnUiThread(()->{if(weatherDetails!=null)weatherDetails.setText("Pogoda: niedostępna\nSprawdź połączenie z internetem.");});}}).start();}
    private void updateWeatherViews(){
        if(smartAiSummary!=null){
            if(weatherLat==0&&weatherLon==0) smartAiSummary.setText("Ustaw lokalizację");
            else if(weatherText==null||weatherText.isEmpty()) smartAiSummary.setText("Pobieranie pogody…");
            else smartAiSummary.setText(weatherCity+"  ·  "+outsideTemp+"°C  ·  zalecane "+recommendedTemp+"°C");
        }
        if(weatherDetails!=null){
            if(weatherLat==0&&weatherLon==0){weatherDetails.setText("Nie ustawiono lokalizacji jacuzzi.\nWejdź w Ustawienia → Lokalizacja SPA.");weatherRecommendation.setText("Zalecana temperatura SPA: --");}
            else {weatherDetails.setText("📍 "+weatherCity+"\nPogoda: "+weatherText+"\nNa zewnątrz: "+outsideTemp+"°C");weatherRecommendation.setText("Zalecana temperatura SPA: "+recommendedTemp+"°C");}
        }
        if(weatherLocationButton!=null)weatherLocationButton.setText("📍  "+(weatherCity.isEmpty()?"Ustaw lokalizację":weatherCity));
        if(weatherInlineResult!=null && !weatherCity.isEmpty() && (weatherInlineResultsContainer==null || weatherInlineResultsContainer.getChildCount()==0)) weatherInlineResult.setText(weatherText==null||weatherText.isEmpty()?"Ustawiona lokalizacja: "+weatherCity:"📍  "+weatherCity+"  ·  "+outsideTemp+"°C  ·  zalecane "+recommendedTemp+"°C");
    }

    private void toggleSmartAiPanel(){
        if(smartAiPanel==null)return;
        boolean open=smartAiPanel.getVisibility()==View.VISIBLE;
        smartAiPanel.setVisibility(open?View.GONE:View.VISIBLE);
        if(!open&&weatherInlineQuery!=null){weatherInlineQuery.setText(weatherCity);weatherInlineQuery.setSelection(weatherInlineQuery.length());}
    }
    private void searchWeatherInline(){
        if(weatherInlineQuery==null||weatherInlineResult==null)return;
        String name=weatherInlineQuery.getText().toString().trim();
        if(name.isEmpty()){weatherInlineResult.setText("Wpisz nazwę miejscowości.");return;}
        weatherInlineSearch.setEnabled(false);
        weatherInlineResult.setText("Szukam miejscowości…");
        new Thread(()->{try{
            Request req=new Request.Builder().url("https://geocoding-api.open-meteo.com/v1/search?name="+java.net.URLEncoder.encode(name,"UTF-8")+"&count=5&language=pl&format=json&countryCode=PL").build();
            Response resp=client.newCall(req).execute(); String body=resp.body()!=null?resp.body().string():"";
            JSONObject o=new JSONObject(body); final org.json.JSONArray a=o.optJSONArray("results");
            runOnUiThread(()->{
                weatherInlineSearch.setEnabled(true);
                if(weatherInlineResultsContainer!=null)weatherInlineResultsContainer.removeAllViews();
                if(a==null||a.length()==0){weatherInlineResult.setText("Nie znaleziono miejscowości.");return;}
                weatherInlineResult.setText("Wybierz miejscowość:");
                for(int i=0;i<a.length();i++){JSONObject x=a.optJSONObject(i);if(x==null)continue;String label=x.optString("name","");String admin=x.optString("admin1","");if(!admin.isEmpty())label+=" · "+admin;final String selectedLabel=label;final double lat=x.optDouble("latitude"),lon=x.optDouble("longitude");MaterialButton item=bigButton(selectedLabel);item.setTextSize(14);item.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);item.setPadding(dp(14),0,dp(10),0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.setMargins(0,dp(4),0,0);weatherInlineResultsContainer.addView(item,lp);item.setOnClickListener(v->{saveWeatherLocation(selectedLabel,lat,lon);weatherInlineQuery.setText(selectedLabel);weatherInlineQuery.setSelection(weatherInlineQuery.length());weatherInlineResult.setText("✓ Wybrano: "+selectedLabel);weatherInlineResultsContainer.removeAllViews();});}
            });
        }catch(Exception e){runOnUiThread(()->{weatherInlineSearch.setEnabled(true);weatherInlineResult.setText("Błąd połączenia z serwisem pogody.");});}}).start();
    }

    private void showSmartAiDialog(){
        LinearLayout box=vertical();
        box.setPadding(dp(8),dp(4),dp(8),dp(2));
        TextView place=tv(weatherCity.isEmpty()?"Lokalizacja nieustawiona":"📍  "+weatherCity,16,TEXT);
        place.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(place);
        gap(box,dp(8));
        TextView weather=tv((weatherText==null||weatherText.isEmpty()?"Pogoda niedostępna":weatherText)+"  ·  "+(weatherLat==0&&weatherLon==0?"--":outsideTemp+"°C"),15,MUTED);
        box.addView(weather);
        gap(box,dp(6));
        TextView rec=tv("Zalecana temperatura SPA: "+(weatherLat==0&&weatherLon==0?"--":recommendedTemp+"°C"),19,TEXT);
        rec.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(rec);
        gap(box,dp(10));
        LinearLayout actions=row();
        MaterialButton set=bigButton("USTAW ZALECANĄ");
        MaterialButton refresh=bigButton("↻  ODŚWIEŻ");
        actions.addView(set,new LinearLayout.LayoutParams(0,dp(48),1));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(48),1);rp.setMargins(dp(6),0,0,0);actions.addView(refresh,rp);
        box.addView(actions);
        gap(box,dp(6));
        MaterialButton location=bigButton("📍  ZMIEŃ LOKALIZACJĘ");
        box.addView(location,new LinearLayout.LayoutParams(-1,dp(44)));
        android.app.AlertDialog d=new android.app.AlertDialog.Builder(this).setTitle("SMART AI  ·  POGODA").setView(box).setNegativeButton("ZAMKNIJ",null).create();
        set.setOnClickListener(v->{setTarget(recommendedTemp);d.dismiss();});
        refresh.setOnClickListener(v->{fetchWeather();d.dismiss();});
        location.setOnClickListener(v->{d.dismiss();showWeatherLocationDialog();});
        d.show();
    }
    public static void scheduleSpaMonitor(Context context){
        SpaNotificationManager.createChannel(context.getApplicationContext());
        android.app.AlarmManager am=(android.app.AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        Intent i=new Intent(context,SpaMonitorReceiver.class);
        PendingIntent pi=PendingIntent.getBroadcast(context,SpaMonitorReceiver.REQUEST_CODE,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        long first=System.currentTimeMillis()+2*60*1000L;
        am.setInexactRepeating(android.app.AlarmManager.RTC_WAKEUP,first,5*60*1000L,pi);
    }

    private void createNotificationChannel(){if(Build.VERSION.SDK_INT>=26){NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("smartspa_weather","Smart Spa – inteligentne powiadomienia",NotificationManager.IMPORTANCE_DEFAULT));}}
    private void scheduleWeatherNotifications(){android.app.AlarmManager am=(android.app.AlarmManager)getSystemService(ALARM_SERVICE);Intent i=new Intent(this,WeatherNotificationReceiver.class);PendingIntent pi=PendingIntent.getBroadcast(this,701,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);long first=System.currentTimeMillis()+60*60*1000L;am.setInexactRepeating(android.app.AlarmManager.RTC_WAKEUP,first,6*60*60*1000L,pi);}
    private void notifyRecommendation(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;Intent i=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,702,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);NotificationCompat.Builder b=new NotificationCompat.Builder(this,"smartspa_weather").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Smart Spa").setContentText("Zalecana temperatura SPA: "+recommendedTemp+"°C · na zewnątrz "+outsideTemp+"°C").setStyle(new NotificationCompat.BigTextStyle().bigText("Na zewnątrz: "+outsideTemp+"°C, "+weatherText+". Smart Spa zaleca "+recommendedTemp+"°C.")).setContentIntent(pi).setAutoCancel(true);getSystemService(NotificationManager.class).notify(703,b.build());}

    private View buildConnection(){ScrollView sc=new ScrollView(this);sc.setFillViewport(true);LinearLayout root=vertical();root.setPadding(14,14,14,30);sc.addView(root);LinearLayout h=row();MaterialButton back=bigButton("‹   "+tr("back"));back.setOnClickListener(v->showMain());h.addView(back,new LinearLayout.LayoutParams(0,82,1));connectionTitle=tv(tr("connection"),24,TEXT);connectionTitle.setGravity(Gravity.CENTER);connectionTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);h.addView(connectionTitle,new LinearLayout.LayoutParams(0,82,2));root.addView(h);gap(root,12);
        MaterialCardView conn=card();LinearLayout c=inside(conn,18);TextView ip=tv(tr("host"),15,TEXT);c.addView(ip);hostEdit=new TextInputEditText(this);hostEdit.setText(host);hostEdit.setTextSize(17);hostEdit.setTextColor(TEXT);hostEdit.setSingleLine(true);hostEdit.setInputType(InputType.TYPE_CLASS_TEXT);TextInputLayout hi=new TextInputLayout(this);hi.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);hi.addView(hostEdit);c.addView(hi,new LinearLayout.LayoutParams(-1,64));gap(c,12);TextView po=tv(tr("port"),15,TEXT);c.addView(po);portEdit=new TextInputEditText(this);portEdit.setText(String.valueOf(port));portEdit.setTextSize(17);portEdit.setTextColor(TEXT);portEdit.setSingleLine(true);portEdit.setInputType(InputType.TYPE_CLASS_NUMBER);TextInputLayout pi=new TextInputLayout(this);pi.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);pi.addView(portEdit);c.addView(pi,new LinearLayout.LayoutParams(-1,64));gap(c,14);connectButton=bigButton(tr("connect"));connectButton.setOnClickListener(v->readConnectionAndConnect());c.addView(connectButton,new LinearLayout.LayoutParams(-1,72));root.addView(conn);gap(root,12);
        MaterialCardView details=card();LinearLayout d=inside(details,18);TextView dt=tv(tr("details"),20,TEXT);dt.setTypeface(Typeface.DEFAULT,Typeface.BOLD);d.addView(dt);infoView=tv("Model: --\nFirmware: --\nIP: --",15,TEXT);infoView.setLineSpacing(0,1.15f);d.addView(infoView,new LinearLayout.LayoutParams(-1,-2));gap(d,8);rssiView=tv("RSSI: -- dBm",15,MUTED);d.addView(rssiView);root.addView(details);return sc;}

    private void addSwitch(LinearLayout parent,TextView label,int cmd){
        LinearLayout r=row();r.setPadding(dp(12),dp(5),dp(8),dp(5));r.setGravity(Gravity.CENTER_VERTICAL);r.setBackgroundResource(android.R.drawable.list_selector_background);r.setClickable(true);
        String icon=cmd==3?"♨":cmd==4?"◉":cmd==2?"≈":cmd==11?"✦":"⏻";
        TextView ico=tv(icon,25,TEXT);ico.setGravity(Gravity.CENTER);r.addView(ico,new LinearLayout.LayoutParams(dp(46),dp(70)));
        label.setTextSize(16);label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);label.setGravity(Gravity.CENTER_VERTICAL);label.setSingleLine(true);r.addView(label,new LinearLayout.LayoutParams(0,dp(70),1));
        TextView state=tv(tr("off"),13,MUTED);state.setGravity(Gravity.CENTER);state.setTypeface(Typeface.DEFAULT,Typeface.BOLD);state.setSingleLine(true);state.setPadding(10,0,10,0);
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(dp(88),dp(50));slp.setMargins(6,0,0,0);r.addView(state,slp);
        r.setOnClickListener(v->{if(cmd==3){toggleHeating();return;}int st=cmd==4?pumpState:cmd==2?airState:cmd==11?jetsState:powerState;toggle(cmd,st);});
        label.setTag(state);parent.addView(r,new LinearLayout.LayoutParams(-1,dp(76)));gap(parent,dp(7));
    }
    private void showMain(){settingsScreen.setVisibility(View.GONE);connectionScreen.setVisibility(View.GONE);mainScreen.setVisibility(View.VISIBLE);}
    private void showSettings(){mainScreen.setVisibility(View.GONE);connectionScreen.setVisibility(View.GONE);settingsScreen.setVisibility(View.VISIBLE);}
    private void showConnection(){mainScreen.setVisibility(View.GONE);settingsScreen.setVisibility(View.GONE);connectionScreen.setVisibility(View.VISIBLE);}
    private int langIndex(){for(int i=0;i<LANGS.length;i++)if(LANGS[i].equals(language))return i;return 0;}private int pumpIndex(){if("pump1".equals(pumpProfile))return 1;if("pump2".equals(pumpProfile))return 2;if("external".equals(pumpProfile))return 3;return 0;}private int hydroIndex(){if("power".equals(hydroMode))return 1;if("quiet".equals(hydroMode))return 2;return 0;}
    private void saveSettings(){getPreferences(MODE_PRIVATE).edit().putString("language",language).putString("pump_profile",pumpProfile).putString("hydro_mode",hydroMode).apply();}

    private void applyLanguage(){if(languageSpinner!=null&&languageSpinner.getSelectedItemPosition()!=langIndex())languageSpinner.setSelection(langIndex());if(tempCaption!=null)tempCaption.setText(tr("currentTemp"));if(settingsTitle!=null)settingsTitle.setText(tr("spaSettings"));if(connectionTitle!=null)connectionTitle.setText(tr("connection"));if(targetValue!=null)targetValue.setText(tr("target")+" "+target+"°C");updateSessionTimer();if(backButton!=null)backButton.setText("‹   "+tr("back"));if(connectButton!=null)connectButton.setText(tr("connect"));if(settingsNote!=null)settingsNote.setText(tr("note"));updateControlLabels();if(quickHeat!=null){quickHeat.setText("♨  "+label("heat"));quickAir.setText("≈  "+label("bubbles"));quickPump.setText("◉  "+label("filtering"));}updateConnectionSummary();if(drawer!=null){int vis=drawer.getVisibility();rootFrame.removeView(drawer);drawer=buildDrawer();drawer.setVisibility(vis);rootFrame.addView(drawer,new FrameLayout.LayoutParams(-1,-1));}
        if(pumpSpinner!=null){ArrayAdapter<String>a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{tr("default"),tr("pump1"),tr("pump2"),tr("external")});pumpSpinner.setAdapter(a);pumpSpinner.setSelection(pumpIndex());}
        if(hydroSpinner!=null){ArrayAdapter<String>a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{tr("standard"),tr("power"),tr("quiet")});hydroSpinner.setAdapter(a);hydroSpinner.setSelection(hydroIndex());}
        if(languageSpinner!=null)languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int pos,long id){String l=LANGS[pos];if(!l.equals(language)){language=l;saveSettings();applyLanguage();}}});}

    private String tr(String k){if("scan".equals(k)){if("en".equals(language))return"SCAN FOR SPA";if("de".equals(language))return"SPA SUCHEN";if("fr".equals(language))return"RECHERCHER LE SPA";if("es".equals(language))return"BUSCAR SPA";return"SZUKAJ JACUZZI";}if("scanning".equals(k)){if("en".equals(language))return"Scanning the network…";if("de".equals(language))return"Netzwerk wird durchsucht…";if("fr".equals(language))return"Recherche sur le réseau…";if("es".equals(language))return"Buscando en la red…";return"Szukam jacuzzi w sieci…";}if("found_spa".equals(k)){if("en".equals(language))return"SPA DETECTED:";if("de".equals(language))return"WHIRLPOOL ERKANNT:";if("fr".equals(language))return"SPA DÉTECTÉ :";if("es".equals(language))return"SPA DETECTADO:";return"WYKRYTO JACUZZI:";}if("not_found".equals(k)){if("en".equals(language))return"Spa not found. Enter IP manually.";if("de".equals(language))return"Spa nicht gefunden. IP manuell eingeben.";if("fr".equals(language))return"Spa introuvable. Saisissez l’IP manuellement.";if("es".equals(language))return"No se encontró el spa. Introduce la IP manualmente.";return"Nie wykryto jacuzzi. Wpisz IP ręcznie.";}if("not_connected".equals(k)){if("en".equals(language))return"NOT CONNECTED";if("de".equals(language))return"NICHT VERBUNDEN";if("fr".equals(language))return"NON CONNECTÉ";if("es".equals(language))return"NO CONECTADO";return"NIE POŁĄCZONO";}if("connecting_to".equals(k)){if("en".equals(language))return"Connecting to";if("de".equals(language))return"Verbinde mit";if("fr".equals(language))return"Connexion à";if("es".equals(language))return"Conectando a";return"Łączenie z";}if("pl".equals(language)){switch(k){case"menu":return"KONFIGURACJA";case"hardwareConfig":return"KONFIGURACJA SPRZĘTU";case"spaConfig":return"USTAWIENIA SPA";case"networkConfig":return"KONFIGURACJA SIECI";case"close":return"ZAMKNIJ";case"currentTemp":return"AKTUALNA TEMPERATURA";case"target":return"CEL";case"remaining":return"POZOSTAŁO";case"sessionReady":return"SESJA GOTOWA";case"sessionLength":return"DŁUGOŚĆ SESJI";case"minutes":return"MIN";case"cancel":return"ANULUJ";case"control":return"STEROWANIE";case"spaSettings":return"USTAWIENIA SPA";case"connection":return"POŁĄCZENIE";case"back":return"WRÓĆ";case"spaControl":return"STEROWANIE SPA";case"pumpProfile":return"PROFIL POMPY";case"hydroMode":return"TRYB HYDROJET";case"default":return"Domyślna";case"pump1":return"Pompa 1";case"pump2":return"Pompa 2";case"external":return"Pompa zewnętrzna";case"standard":return"Standardowy";case"power":return"Mocny";case"quiet":return"Cichy";case"host":return"Adres ESP8266";case"port":return"Port WebSocket";case"connect":return"POŁĄCZ";case"details":return"SZCZEGÓŁY POŁĄCZENIA";case"note":return"Ustawienia są zapisywane lokalnie. Komendy ESP8266 pozostają zgodne z oryginalnym protokołem.";case"connected":return"POŁĄCZONO";case"connecting":return"ŁĄCZENIE";case"on":return"WŁ.";case"off":return"WYŁ.";case"heating":return"GRZEJE";case"ready":return"NAGRZANE";case"standby":return"CZUWANIE";default:return"ROZŁĄCZONO";}}if("de".equals(language)){switch(k){case"menu":return"KONFIGURATION";case"hardwareConfig":return"HARDWARE-KONFIGURATION";case"spaConfig":return"SPA-EINSTELLUNGEN";case"networkConfig":return"NETZWERK-KONFIGURATION";case"close":return"SCHLIESSEN";case"currentTemp":return"AKTUELLE TEMPERATUR";case"target":return"ZIEL";case"remaining":return"VERBLEIBEND";case"sessionReady":return"BEREIT";case"sessionLength":return"SITZUNGSDAUER";case"minutes":return"MIN";case"cancel":return"ABBRECHEN";case"control":return"STEUERUNG";case"spaSettings":return"SPA-EINSTELLUNGEN";case"connection":return"VERBINDUNG";case"back":return"ZURÜCK";case"spaControl":return"SPA-STEUERUNG";case"pumpProfile":return"PUMPENPROFIL";case"hydroMode":return"HYDROJET-MODUS";case"default":return"Standard";case"pump1":return"Pumpe 1";case"pump2":return"Pumpe 2";case"external":return"Externe Pumpe";case"standard":return"Standard";case"power":return"Stark";case"quiet":return"Leise";case"host":return"ESP8266-Adresse";case"port":return"WebSocket-Port";case"connect":return"VERBINDEN";case"details":return"VERBINDUNGSDETAILS";case"note":return"Die Einstellungen werden lokal gespeichert. ESP8266-Befehle bleiben mit dem ursprünglichen Protokoll kompatibel.";case"connected":return"VERBUNDEN";case"connecting":return"VERBINDUNG";case"on":return"EIN";case"off":return"AUS";case"heating":return"HEIZT";case"ready":return"BEREIT";case"standby":return"BEREITSCHAFT";default:return"GETRENNT";}}if("fr".equals(language)){switch(k){case"menu":return"CONFIGURATION";case"hardwareConfig":return"CONFIGURATION MATÉRIEL";case"spaConfig":return"RÉGLAGES DU SPA";case"networkConfig":return"CONFIGURATION RÉSEAU";case"close":return"FERMER";case"currentTemp":return"TEMPÉRATURE ACTUELLE";case"target":return"CIBLE";case"remaining":return"RESTANT";case"sessionReady":return"SESSION PRÊTE";case"sessionLength":return"DURÉE DE SESSION";case"minutes":return"MIN";case"cancel":return"ANNULER";case"control":return"COMMANDE";case"spaSettings":return"RÉGLAGES DU SPA";case"connection":return"CONNEXION";case"back":return"RETOUR";case"spaControl":return"COMMANDE DU SPA";case"pumpProfile":return"PROFIL DE POMPE";case"hydroMode":return"MODE HYDROJET";case"default":return"Par défaut";case"pump1":return"Pompe 1";case"pump2":return"Pompe 2";case"external":return"Pompe externe";case"standard":return"Standard";case"power":return"Puissant";case"quiet":return"Silencieux";case"host":return"Adresse ESP8266";case"port":return"Port WebSocket";case"connect":return"CONNECTER";case"details":return"DÉTAILS DE CONNEXION";case"note":return"Les réglages sont enregistrés localement. Les commandes ESP8266 restent compatibles avec le protocole d'origine.";case"connected":return"CONNECTÉ";case"connecting":return"CONNEXION";case"on":return"MARCHE";case"off":return"ARRÊT";case"heating":return"CHAUFFE";case"ready":return"CHAUFFÉ";case"standby":return"VEILLE";default:return"DÉCONNECTÉ";}}if("es".equals(language)){switch(k){case"menu":return"CONFIGURACIÓN";case"hardwareConfig":return"CONFIGURACIÓN DE HARDWARE";case"spaConfig":return"AJUSTES DEL SPA";case"networkConfig":return"CONFIGURACIÓN DE RED";case"close":return"CERRAR";case"currentTemp":return"TEMPERATURA ACTUAL";case"target":return"OBJETIVO";case"remaining":return"RESTANTE";case"sessionReady":return"SESIÓN LISTA";case"sessionLength":return"DURACIÓN DE SESIÓN";case"minutes":return"MIN";case"cancel":return"CANCELAR";case"control":return"CONTROL";case"spaSettings":return"AJUSTES DEL SPA";case"connection":return"CONEXIÓN";case"back":return"VOLVER";case"spaControl":return"CONTROL DEL SPA";case"pumpProfile":return"PERFIL DE BOMBA";case"hydroMode":return"MODO HYDROJET";case"default":return"Predeterminada";case"pump1":return"Bomba 1";case"pump2":return"Bomba 2";case"external":return"Bomba externa";case"standard":return"Estándar";case"power":return"Potente";case"quiet":return"Silencioso";case"host":return"Dirección ESP8266";case"port":return"Puerto WebSocket";case"connect":return"CONECTAR";case"details":return"DETALLES DE CONEXIÓN";case"note":return"Los ajustes se guardan localmente. Los comandos ESP8266 siguen siendo compatibles con el protocolo original.";case"connected":return"CONECTADO";case"connecting":return"CONECTANDO";case"on":return"ENC.";case"off":return"APAG.";case"heating":return"CALENTANDO";case"ready":return"LISTO";case"standby":return"ESPERA";default:return"DESCONECTADO";}}switch(k){case"menu":return"CONFIGURATION";case"hardwareConfig":return"HARDWARE CONFIG";case"spaConfig":return"SPA CONFIG";case"networkConfig":return"NETWORK CONFIG";case"close":return"CLOSE";case"currentTemp":return"CURRENT TEMPERATURE";case"target":return"TARGET";case"remaining":return"REMAINING";case"sessionReady":return"SESSION READY";case"sessionLength":return"SESSION LENGTH";case"minutes":return"MIN";case"cancel":return"CANCEL";case"control":return"CONTROL";case"spaSettings":return"SPA SETTINGS";case"connection":return"CONNECTION";case"back":return"BACK";case"spaControl":return"SPA CONTROL";case"pumpProfile":return"PUMP PROFILE";case"hydroMode":return"HYDROJET MODE";case"default":return"Default";case"pump1":return"Pump 1";case"pump2":return"Pump 2";case"external":return"External pump";case"standard":return"Standard";case"power":return"Power";case"quiet":return"Quiet";case"host":return"ESP8266 address";case"port":return"WebSocket port";case"connect":return"CONNECT";case"details":return"CONNECTION DETAILS";case"note":return"Settings are stored locally. ESP8266 commands remain compatible with the original protocol.";case"connected":return"CONNECTED";case"connecting":return"CONNECTING";case"on":return"ON";case"off":return"OFF";case"heating":return"HEATING";case"ready":return"HEATED";case"standby":return"STANDBY";default:return"DISCONNECTED";}}
    private String label(String k){if("pl".equals(language)){if(k.equals("heat"))return"GRZANIE";if(k.equals("filtering"))return"FILTROWANIE";if(k.equals("pump"))return"POMPA";if(k.equals("bubbles"))return"BĄBELKI";if(k.equals("jets"))return"HYDROJET";return"ZASILANIE";}if("de".equals(language)){if(k.equals("heat"))return"HEIZUNG";if(k.equals("filtering"))return"FILTERUNG";if(k.equals("pump"))return"PUMPE";if(k.equals("bubbles"))return"BLASEN";if(k.equals("jets"))return"HYDROJET";return"STROM";}if("fr".equals(language)){if(k.equals("heat"))return"CHAUFFAGE";if(k.equals("filtering"))return"FILTRATION";if(k.equals("pump"))return"POMPE";if(k.equals("bubbles"))return"BULLES";if(k.equals("jets"))return"HYDROJET";return"ALIMENTATION";}if("es".equals(language)){if(k.equals("heat"))return"CALEFACCIÓN";if(k.equals("filtering"))return"FILTRADO";if(k.equals("pump"))return"BOMBA";if(k.equals("bubbles"))return"BURBUJAS";if(k.equals("jets"))return"HYDROJET";return"ALIMENTACIÓN";}if(k.equals("heat"))return"HEATING";if(k.equals("filtering"))return"FILTERING";if(k.equals("pump"))return"PUMP";if(k.equals("bubbles"))return"BUBBLES";if(k.equals("jets"))return"HYDROJET";return"POWER";}
    private void setSessionDuration(int minutes){
        sessionDurationMinutes=Math.max(1,Math.min(30,minutes));
        getPreferences(MODE_PRIVATE).edit().putInt("session_duration",sessionDurationMinutes).apply();
        if(sessionEndAt>System.currentTimeMillis()){
            sessionEndAt=System.currentTimeMillis()+sessionDurationMinutes*60000L;
            saveSession();
        }
        updateSessionTimer();
        updateSessionQuickButtons();
    }
    private void updateSessionQuickButtons(){
        if(sessionQuickButtons==null)return;
        for(MaterialButton b:sessionQuickButtons){
            String text=b.getText().toString();
            boolean selected=text.startsWith(String.valueOf(sessionDurationMinutes)+" ");
            b.setTextColor(selected?Color.WHITE:TEXT);
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selected?BLUE:CARD2));
            b.setStrokeColor(android.content.res.ColorStateList.valueOf(selected?BLUE:BORDER));
        }
    }
    private void showSessionDurationDialog(){
        final NumberPicker picker=new NumberPicker(this);
        picker.setMinValue(1);
        picker.setMaxValue(30);
        picker.setValue(Math.max(1,Math.min(30,sessionDurationMinutes)));
        picker.setWrapSelectorWheel(false);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(24),dp(8),dp(24),dp(8));
        TextView hint=tv("1–30 "+tr("minutes"),14,MUTED);
        hint.setGravity(Gravity.CENTER);
        box.addView(hint,new LinearLayout.LayoutParams(-1,44));
        box.addView(picker,new LinearLayout.LayoutParams(-2,dp(170)));
        new android.app.AlertDialog.Builder(this)
            .setTitle(tr("sessionLength"))
            .setView(box)
            .setPositiveButton("OK",(d,which)->{
                setSessionDuration(picker.getValue());
            })
            .setNegativeButton(tr("cancel"),null)
            .show();
    }
    private void startSession(){sessionEndAt=System.currentTimeMillis()+sessionDurationMinutes*60000L;saveSession();updateSessionTimer();}
    private void stopSession(boolean sendOff){sessionEndAt=0L;saveSession();handler.removeCallbacks(sessionTick);if(sendOff){send(3,0);send(2,0);send(4,0);send(11,0);}}
    private void saveSession(){
        getPreferences(MODE_PRIVATE).edit().putLong("session_end",sessionEndAt).putInt("session_duration",sessionDurationMinutes).apply();
        getSharedPreferences("widget_state",MODE_PRIVATE).edit().putLong("session_end",sessionEndAt).putInt("session_duration",sessionDurationMinutes).apply();
        SpaWidgetProvider.updateAll(this);
    }
    private String formatSession(long ms){long total=Math.max(0,(ms+999)/1000);long min=total/60;long sec=total%60;return String.format(Locale.ROOT,"%02d:%02d",min,sec);}
    private void updateSessionTimer(){if(sessionTimerView==null||sessionButton==null)return;long now=System.currentTimeMillis();if(sessionEndAt>now){sessionTimerView.setText(tr("remaining")+"  "+formatSession(sessionEndAt-now));sessionTimerView.setTextColor(RED);sessionButton.setText(sessionDurationMinutes+" "+tr("minutes"));handler.removeCallbacks(sessionTick);handler.postDelayed(sessionTick,1000);}else if(sessionEndAt!=0L){sessionEndAt=0L;saveSession();sessionTimerView.setText(tr("sessionReady"));sessionTimerView.setTextColor(MUTED);sessionButton.setText(sessionDurationMinutes+" "+tr("minutes"));if(heatState==1||pumpState==1||airState==1||jetsState==1){send(3,0);send(2,0);send(4,0);send(11,0);heatCommandState=0;heatState=0;airState=0;updateControlLabels();}}else{sessionTimerView.setText(tr("sessionReady"));sessionTimerView.setTextColor(MUTED);sessionButton.setText(sessionDurationMinutes+" "+tr("minutes"));}updateSessionQuickButtons();}

    private void toggleHeating(){int st=heatCommandState>=0?heatCommandState:heatState;int next=st==1?0:1;heatCommandState=next;heatState=next;updateState(heatLabel,heatState);send(3,next);}
    private void updateQuickButtonColors(){if(quickHeat!=null){quickHeat.setBackgroundTintList(android.content.res.ColorStateList.valueOf(heatState==1?RED:CARD2));quickHeat.setTextColor(heatState==1?Color.WHITE:TEXT);}if(quickAir!=null){quickAir.setBackgroundTintList(android.content.res.ColorStateList.valueOf(airState==1?BLUE:CARD2));quickAir.setTextColor(airState==1?Color.WHITE:TEXT);}if(quickPump!=null){quickPump.setBackgroundTintList(android.content.res.ColorStateList.valueOf(pumpState==1?BLUE:CARD2));quickPump.setTextColor(pumpState==1?Color.WHITE:TEXT);}}
    private void updateControlLabels(){if(heatLabel==null)return;heatLabel.setText(label("heat"));pumpLabel.setText(label("pump"));airLabel.setText(label("bubbles"));jetsLabel.setText(label("jets"));powerLabel.setText(label("power"));updateState(heatLabel,heatState);updateState(pumpLabel,pumpState);updateState(airLabel,airState);updateState(jetsLabel,jetsState);updateState(powerLabel,powerState);}
    private void updateState(TextView l,int state){Object o=l.getTag();if(!(o instanceof TextView))return;TextView st=(TextView)o;boolean heating=l==heatLabel;int bg;int fg;if(heating){boolean commandedOn=heatCommandState==1;boolean actuallyAtTarget=currentTemp>0&&target>0&&currentTemp>=target;if(commandedOn&&!actuallyAtTarget){st.setText(tr("heating"));fg=Color.WHITE;bg=RED;}else if(commandedOn&&actuallyAtTarget){st.setText(tr("ready"));fg=Color.WHITE;bg=GREEN;}else if(state==1){st.setText(tr("heating"));fg=Color.WHITE;bg=RED;}else{st.setText(tr("off"));fg=MUTED;bg=CARD2;}}else{st.setText(state==1?tr("on"):tr("off"));fg=state==1?Color.WHITE:MUTED;bg=state==1?BLUE:CARD2;}st.setTextColor(fg);android.graphics.drawable.GradientDrawable pill=new android.graphics.drawable.GradientDrawable();pill.setColor(bg);pill.setCornerRadius(dp(24));st.setBackground(pill);updateQuickButtonColors();}
    private void toggleDrawer(){if(drawer==null)return;drawer.setVisibility(drawer.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);}
    private View buildDrawer(){FrameLayout overlay=new FrameLayout(this);overlay.setBackgroundColor(Color.argb(90,0,0,0));LinearLayout panel=vertical();panel.setBackgroundColor(Color.rgb(235,241,245));panel.setPadding(22,30,18,24);FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(dp(330),-1);pp.gravity=Gravity.LEFT;overlay.addView(panel,pp);TextView h=tv(tr("menu"),25,TEXT);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);panel.addView(h,new LinearLayout.LayoutParams(-1,60));TextView hint=tv("ESP8266 / SPA",13,MUTED);panel.addView(hint,new LinearLayout.LayoutParams(-1,32));gap(panel,12);
        MaterialButton spa=bigButton("♨   "+tr("spaConfig"));spa.setOnClickListener(v->{drawer.setVisibility(View.GONE);showSettings();});panel.addView(spa,new LinearLayout.LayoutParams(-1,76));gap(panel,10);
        MaterialButton net=bigButton("⌁   "+tr("networkConfig"));net.setOnClickListener(v->showNetworkConfig());panel.addView(net,new LinearLayout.LayoutParams(-1,76));gap(panel,18);
        MaterialButton close=bigButton("×   "+tr("close"));close.setOnClickListener(v->drawer.setVisibility(View.GONE));panel.addView(close,new LinearLayout.LayoutParams(-1,70));overlay.setOnClickListener(v->{if(v==overlay)drawer.setVisibility(View.GONE);});panel.setOnClickListener(v->{});return overlay;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private void showNetworkConfig(){
        drawer.setVisibility(View.GONE);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(24,8,24,8);
        TextView state=tv(tr("not_connected"),18,MUTED); networkStateView=state; state.setGravity(Gravity.CENTER); box.addView(state,new LinearLayout.LayoutParams(-1,52));
        TextView details=tv("",15,MUTED); networkDetailsView=details; details.setGravity(Gravity.CENTER); box.addView(details,new LinearLayout.LayoutParams(-1,-2));
        MaterialButton scan=bigButton("⌕   "+tr("scan")); box.addView(scan,new LinearLayout.LayoutParams(-1,64));
        EditText ip=new EditText(this); ip.setHint("192.168.1.40"); ip.setSingleLine(true); ip.setInputType(InputType.TYPE_CLASS_PHONE); ip.setText(host); box.addView(ip,new LinearLayout.LayoutParams(-1,58));
        MaterialButton connect=bigButton(tr("connect")); box.addView(connect,new LinearLayout.LayoutParams(-1,64));
        android.app.AlertDialog d=new android.app.AlertDialog.Builder(this).setTitle(tr("networkConfig")).setView(box).setNegativeButton(tr("close"),null).create();
        Runnable manual=()->{String h=ip.getText().toString().trim();if(h.isEmpty())h="192.168.1.40";host=h;getSharedPreferences("spa",0).edit().putString("host",host).apply();state.setText(tr("connecting_to")+" "+host);state.setTextColor(BLUE);connect();};
        connect.setOnClickListener(v->manual.run());
        scan.setOnClickListener(v->{if(discoveryRunning)return;discoveryRunning=true;scan.setEnabled(false);state.setText(tr("scanning"));state.setTextColor(BLUE);discoveryExecutor.execute(()->{String found=discoverSpaIp();runOnUiThread(()->{discoveryRunning=false;scan.setEnabled(true);if(found!=null){ip.setText(found);host=found;getSharedPreferences("spa",0).edit().putString("host",host).putInt("port",port).apply();state.setText(tr("found_spa")+" "+found);state.setTextColor(GREEN);manual.run();}else{state.setText(tr("not_found"));state.setTextColor(MUTED);}});});});
        d.show();
    }
    private void updateNetworkDialog(){
        if(networkStateView==null||networkDetailsView==null)return;
        if(ws!=null){
            networkStateView.setText(tr("connected")); networkStateView.setTextColor(GREEN);
            networkDetailsView.setText(tr("device")+": "+otherModel+"\n"+tr("ip_address")+": "+otherIp+"\nWebSocket: :"+port+"\n"+tr("firmware")+": "+otherFw+"\n"+tr("wifi_signal")+": "+otherRssi+" dBm\n"+tr("status")+": ONLINE");
        }
    }
    private String discoverSpaIp(){
        try{WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);if(wm==null)return null;String local=Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());String[] p=local.split("\\.");if(p.length!=4)return null;String prefix=p[0]+"."+p[1]+"."+p[2]+".";int own=Integer.parseInt(p[3]);for(int n=1;n<=254;n++){if(n==own)continue;if(probeSpa(prefix+n))return prefix+n;}}catch(Exception ignored){}return null;
    }
    private boolean probeSpa(String ip){try{java.net.Socket socket=new java.net.Socket();socket.connect(new java.net.InetSocketAddress(ip,81),150);socket.close();return true;}catch(Exception ignored){return false;}}

    private void updateConnectionSummary(){if(connectionSummary!=null)connectionSummary.setText("●  "+tr("connection")+"  "+host+":"+port);}
    private void readConnectionAndConnect(){String h=hostEdit.getText()==null?"":hostEdit.getText().toString().trim();String ps=portEdit.getText()==null?"":portEdit.getText().toString().trim();if(h.isEmpty()){toast("Host required");return;}int p;try{p=Integer.parseInt(ps);}catch(Exception e){p=DEFAULT_PORT;}if(p<1||p>65535){toast("Invalid port");return;}host=h;port=p;getPreferences(MODE_PRIVATE).edit().putString("host",host).putInt("port",port).apply();getSharedPreferences("spa",MODE_PRIVATE).edit().putString("host",host).putInt("port",port).apply();updateConnectionSummary();manualClose=false;connect();}
    private void setTarget(int t){target=Math.max(20,Math.min(40,t));if(dial!=null)dial.setTarget(target);if(targetValue!=null)targetValue.setText(tr("target")+" "+target+"°C");updateSessionTimer();send(0,target);}
    private void toggleBubbles(){int next=airState==1?0:1;airState=next;updateState(airLabel,airState);send(2,next);if(next==1){startSession();}else{stopSession(false);}}private void toggle(int cmd,int state){send(cmd,state==1?0:1);}private void send(int cmd,int value){if(ws==null){toast("No connection to ESP8266");return;}try{JSONObject o=new JSONObject();o.put("CMD",cmd);o.put("VALUE",value);o.put("XTIME",0);o.put("INTERVAL",0);o.put("TXT","");ws.send(o.toString());}catch(Exception e){toast("Command error");}}
    private void connect(){if(host==null||host.isEmpty())return;manualClose=false;if(ws!=null)ws.close(1000,"reconnect");setStatus(tr("connecting"));try{ws=client.newWebSocket(new Request.Builder().url("ws://"+host+":"+port+"/").build(),new WebSocketListener(){@Override public void onOpen(WebSocket w,Response r){SpaNotificationManager.processConnection(MainActivity.this,true);runOnUiThread(()->{setStatus(tr("connected"));if(connectButton!=null)connectButton.setText(tr("connected"));updateNetworkDialog();});}@Override public void onMessage(WebSocket w,String text){SpaNotificationManager.processMessage(MainActivity.this,text);parse(text);}@Override public void onFailure(WebSocket w,Throwable t,Response r){SpaNotificationManager.processConnection(MainActivity.this,false);runOnUiThread(()->{setStatus(tr("disconnected"));if(connectButton!=null)connectButton.setText(tr("connect"));});if(!manualClose)reconnectLater();}@Override public void onClosed(WebSocket w,int c,String reason){if(!manualClose)reconnectLater();}});}catch(Exception e){setStatus(tr("disconnected"));}}
    private void reconnectLater(){handler.removeCallbacksAndMessages(null);handler.postDelayed(this::connect,3000);}
    private void parse(String text){try{JSONObject o=new JSONObject(text);String c=o.optString("CONTENT");if("STATES".equals(c)){target=o.optInt("TGT",target);currentTemp=o.optInt("TMP",0);int grn=o.optInt("GRN",0);if(heatCommandState<0)heatCommandState=grn;heatState=(heatCommandState==1)?1:grn;pumpState=o.optInt("FLT",0);int previousAirState=airState;airState=o.optInt("AIR",0);jetsState=o.optInt("HJT",0);powerState=o.optInt("PWR",0);if(previousAirState!=airState){final boolean bubblesOn=airState==1;runOnUiThread(()->{if(bubblesOn)startSession();else stopSession(false);});}saveWidgetState();runOnUiThread(()->{if(dial!=null){dial.setCurrent(currentTemp);dial.setTarget(target);}if(targetValue!=null)targetValue.setText(tr("target")+" "+target+"°C");updateSessionTimer();updateControlLabels();});}else if("OTHER".equals(c)){String model=o.optString("MODEL","--"),fw=o.optString("FW","--"),ip=o.optString("IP",host);int r=o.optInt("RSSI",0);otherModel=model;otherFw=fw;otherIp=ip;otherRssi=r;runOnUiThread(()->{if(infoView!=null)infoView.setText("Model: "+model+"\nFirmware: "+fw+"\nIP: "+ip);if(rssiView!=null)rssiView.setText("RSSI: "+r+" dBm");updateNetworkDialog();});}}catch(Exception ignored){}}
    private void saveWidgetState(){getSharedPreferences("widget_state",MODE_PRIVATE).edit().putInt("temp",currentTemp).putInt("target",target).putInt("power",powerState).putLong("session_end",sessionEndAt).putInt("session_duration",sessionDurationMinutes).putBoolean("connected",true).apply();SpaWidgetProvider.updateAll(this);}
    private void setStatus(String s){if(status==null)return;status.setText("●  "+s);boolean ok=s.equals(tr("connected"));status.setTextColor(ok?GREEN:(s.equals(tr("connecting"))?BLUE:RED));}
    private void toast(String s){runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());}

    private class TemperatureDial extends View{private final Paint track=new Paint(3),arc=new Paint(3),text=new Paint(3),small=new Paint(3);private int cur,tgt=30;private final RectF oval=new RectF();TemperatureDial(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);setFocusable(true);}void setCurrent(int v){cur=v;invalidate();}void setTarget(int v){tgt=Math.max(20,Math.min(40,v));invalidate();}@Override protected void onDraw(Canvas c){float cx=getWidth()/2f,cy=getHeight()/2f;float radius=Math.min(getWidth()*.37f,getHeight()*.40f);float stroke=Math.max(22,Math.min(30,getWidth()*.065f));oval.set(cx-radius,cy-radius,cx+radius,cy+radius);track.setStyle(Paint.Style.STROKE);track.setStrokeWidth(stroke);track.setStrokeCap(Paint.Cap.ROUND);track.setColor(DIAL_TRACK);c.drawArc(oval,-140,280,false,track);arc.setStyle(Paint.Style.STROKE);arc.setStrokeWidth(stroke);arc.setStrokeCap(Paint.Cap.ROUND);arc.setColor(BLUE);float sweep=(tgt-20)/20f*280f;c.drawArc(oval,-140,sweep,false,arc);text.setTypeface(Typeface.DEFAULT);text.setFakeBoldText(true);text.setTextAlign(Paint.Align.CENTER);text.setColor(TEXT);text.setTextSize(Math.min(92,getWidth()*.21f));String s=cur>0?cur+"°":"--°";Paint.FontMetrics fm=text.getFontMetrics();float baseline=cy-(fm.ascent+fm.descent)/2f;c.drawText(s,cx,baseline,text);small.setTypeface(Typeface.DEFAULT);small.setFakeBoldText(true);small.setTextAlign(Paint.Align.CENTER);small.setColor(MUTED);small.setTextSize(Math.max(14,getWidth()*.045f));c.drawText("20°C",cx-radius,cy+radius+dp(28),small);c.drawText("40°C",cx+radius,cy+radius+dp(28),small);}
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_DOWN&&e.getAction()!=MotionEvent.ACTION_MOVE&&e.getAction()!=MotionEvent.ACTION_UP)return true;float cx=getWidth()/2f,cy=getHeight()/2f;double angle=Math.atan2(e.getY()-cy,e.getX()-cx);double start=Math.toRadians(-140);double a=angle-start;while(a<0)a+=Math.PI*2;while(a>Math.PI*2)a-=Math.PI*2;double sweepDeg=Math.toDegrees(a);if(sweepDeg>280)sweepDeg=sweepDeg<320?280:0;int v=20+(int)Math.round(sweepDeg/280d*20);tgt=Math.max(20,Math.min(40,v));target=tgt;if(targetValue!=null)targetValue.setText(tr("target")+" "+tgt+"°C");invalidate();if(e.getAction()==MotionEvent.ACTION_UP)send(0,tgt);return true;}}
    @Override protected void onDestroy(){manualClose=true;handler.removeCallbacksAndMessages(null);handler.removeCallbacks(sessionTick);if(ws!=null)ws.close(1000,"app close");client.dispatcher().executorService().shutdown();super.onDestroy();}
}
