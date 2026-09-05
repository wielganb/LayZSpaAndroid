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

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS).build();

    private WebSocket ws;
    private boolean manualClose;
    private String host;
    private int port;
    private int target;
    private int heatState, pumpState, airState, jetsState, powerState;
    private String language = "en";

    private TextView status, tempView, targetView, infoView, rssiView;
    private TextView appTitle, appSubtitle, tempLabel, targetLabel, controlsTitle, systemTitle;
    private TextView hostLabel, portLabel;
    private TextInputEditText hostEdit, portEdit;
    private MaterialButton connectButton, languageButton;
    private TextView liveBadge;
    private MaterialButton heatButton, pumpButton, airButton, jetsButton, powerButton;

    private final int BG = Color.rgb(7, 12, 18);
    private final int CARD = Color.rgb(15, 23, 32);
    private final int CARD2 = Color.rgb(20, 30, 41);
    private final int CARD3 = Color.rgb(25, 37, 49);
    private final int TEXT = Color.rgb(242, 247, 251);
    private final int MUTED = Color.rgb(137, 157, 173);
    private final int BLUE = Color.rgb(66, 190, 245);
    private final int BLUE2 = Color.rgb(29, 105, 146);
    private final int GREEN = Color.rgb(86, 220, 178);
    private final int RED = Color.rgb(255, 103, 103);
    private final int BORDER = Color.rgb(45, 65, 82);

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.rgb(5, 9, 14));
        SharedPreferences p = getPreferences(MODE_PRIVATE);
        host = p.getString("host", DEFAULT_HOST);
        port = p.getInt("port", DEFAULT_PORT);
        language = p.getString("language", "en");
        buildUi();
        applyLanguage();
        connect();
    }

    private TextView tv(String s, float size, int color) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(size); v.setTextColor(color);
        v.setIncludeFontPadding(true);
        return v;
    }

    private GradientDrawable rounded(int color, float radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(radius);
        if (stroke > 0) d.setStroke(1, stroke);
        return d;
    }

    private LinearLayout vertical() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private MaterialButton smallButton(String label) {
        MaterialButton b = new MaterialButton(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(TEXT); b.setCornerRadius(16);
        b.setStrokeWidth(1);
        b.setStrokeColor(ColorStateList.valueOf(BORDER));
        b.setBackgroundTintList(ColorStateList.valueOf(CARD2));
        b.setMinHeight(0); b.setMinimumHeight(0);
        return b;
    }

    private MaterialButton controlButton(String label) {
        MaterialButton b = new MaterialButton(this);
        b.setText(label); b.setAllCaps(false); b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(TEXT); b.setCornerRadius(20);
        b.setStrokeWidth(1);
        b.setStrokeColor(ColorStateList.valueOf(BORDER));
        b.setBackgroundTintList(ColorStateList.valueOf(CARD2));
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(8, 8, 8, 8);
        return b;
    }

    private MaterialCardView island() {
        MaterialCardView c = new MaterialCardView(this);
        c.setCardBackgroundColor(CARD);
        c.setRadius(24);
        c.setStrokeColor(BORDER);
        c.setStrokeWidth(1);
        c.setUseCompatPadding(false);
        return c;
    }

    private TextView sectionTitle(String s) {
        TextView t = tv(s.toUpperCase(Locale.ROOT), 12, MUTED);
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLetterSpacing(0.14f);
        return t;
    }

    private void addGap(LinearLayout root, int h) {
        Space s = new Space(this);
        root.addView(s, new LinearLayout.LayoutParams(1, h));
    }

    private LinearLayout islandContent(MaterialCardView card, int pad) {
        LinearLayout box = vertical();
        box.setPadding(pad, pad, pad, pad);
        card.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return box;
    }

    private void addControlRow(LinearLayout parent, MaterialButton left, MaterialButton right) {
        LinearLayout r = row();
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, 94, 1f);
        a.setMargins(0, 0, 7, 0);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, 94, 1f);
        b.setMargins(7, 0, 0, 0);
        r.addView(left, a); r.addView(right, b);
        parent.addView(r);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = vertical();
        root.setPadding(18, 16, 18, 30);
        scroll.addView(root);

        // Header island
        MaterialCardView headerCard = island();
        LinearLayout header = islandContent(headerCard, 18);
        LinearLayout top = row();
        LinearLayout titles = vertical();
        appTitle = tv("LAY-Z-SPA", 28, TEXT);
        appTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        appTitle.setIncludeFontPadding(false);
        titles.addView(appTitle, new LinearLayout.LayoutParams(0, 40, 1));
        appSubtitle = tv("SMART SPA CONTROL", 10, MUTED);
        appSubtitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        appSubtitle.setLetterSpacing(0.16f);
        titles.addView(appSubtitle, new LinearLayout.LayoutParams(0, 24, 1));
        top.addView(titles, new LinearLayout.LayoutParams(0, 64, 1));
        liveBadge = tv("LIVE", 10, GREEN);
        liveBadge.setGravity(Gravity.CENTER);
        liveBadge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        liveBadge.setBackground(rounded(Color.rgb(16, 42, 39), 14, Color.rgb(39, 91, 79)));
        LinearLayout.LayoutParams liveLp = new LinearLayout.LayoutParams(54, 34);
        liveLp.setMargins(6, 0, 6, 0);
        top.addView(liveBadge, liveLp);
        languageButton = smallButton("EN");
        top.addView(languageButton, new LinearLayout.LayoutParams(64, 54));
        header.addView(top);
        status = tv("●  DISCONNECTED", 12, RED);
        status.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        status.setPadding(0, 10, 0, 0);
        header.addView(status);
        root.addView(headerCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 118));
        addGap(root, 12);

        // Connection island
        MaterialCardView connectionCard = island();
        LinearLayout connection = islandContent(connectionCard, 16);
        connection.addView(sectionTitle("Connection"));
        addGap(connection, 8);
        LinearLayout inputs = row();
        TextInputLayout hostTil = new TextInputLayout(this);
        hostTil.setHint("IP / hostname");
        hostTil.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        hostTil.setBoxCornerRadii(16,16,16,16);
        hostTil.setBoxStrokeColor(BORDER);
        hostEdit = new TextInputEditText(this);
        hostEdit.setText(host); hostEdit.setTextColor(TEXT); hostEdit.setTextSize(15);
        hostEdit.setSingleLine(true);
        hostEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        hostTil.addView(hostEdit);
        inputs.addView(hostTil, new LinearLayout.LayoutParams(0, 62, 1));

        TextInputLayout portTil = new TextInputLayout(this);
        portTil.setHint("Port");
        portTil.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        portTil.setBoxCornerRadii(16,16,16,16);
        portTil.setBoxStrokeColor(BORDER);
        portEdit = new TextInputEditText(this);
        portEdit.setText(String.valueOf(port)); portEdit.setTextColor(TEXT); portEdit.setTextSize(15);
        portEdit.setSingleLine(true); portEdit.setInputType(InputType.TYPE_CLASS_NUMBER);
        portTil.addView(portEdit);
        LinearLayout.LayoutParams portLp = new LinearLayout.LayoutParams(92, 62);
        portLp.setMargins(8, 0, 0, 0);
        inputs.addView(portTil, portLp);
        connection.addView(inputs);
        connectButton = smallButton("CONNECT");
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 52);
        cLp.setMargins(0, 8, 0, 0);
        connection.addView(connectButton, cLp);
        root.addView(connectionCard);
        addGap(root, 12);

        // Temperature island
        MaterialCardView tempCard = island();
        LinearLayout temp = islandContent(tempCard, 20);
        temp.setGravity(Gravity.CENTER_HORIZONTAL);
        tempLabel = sectionTitle("Current temperature");
        tempLabel.setGravity(Gravity.CENTER);
        temp.addView(tempLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 28));
        tempView = tv("--.-°", 58, TEXT);
        tempView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tempView.setGravity(Gravity.CENTER);
        tempView.setIncludeFontPadding(false);
        temp.addView(tempView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 88));
        targetView = tv("TARGET  --°", 15, BLUE);
        targetView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        targetView.setGravity(Gravity.CENTER);
        temp.addView(targetView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 32));
        LinearLayout targetRow = row();
        MaterialButton minus = controlButton("−");
        MaterialButton plus = controlButton("+");
        LinearLayout.LayoutParams m = new LinearLayout.LayoutParams(0, 64, 1f);
        m.setMargins(0, 4, 6, 0);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 64, 1f);
        p.setMargins(6, 4, 0, 0);
        targetRow.addView(minus, m); targetRow.addView(plus, p);
        temp.addView(targetRow);
        root.addView(tempCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addGap(root, 12);

        // Controls island
        MaterialCardView controlsCard = island();
        LinearLayout controls = islandContent(controlsCard, 16);
        controlsTitle = sectionTitle("Spa controls");
        controls.addView(controlsTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 28));
        addGap(controls, 8);
        heatButton = controlButton("HEATING\nOFF");
        pumpButton = controlButton("PUMP / FILTER\nOFF");
        jetsButton = controlButton("HYDROJET\nOFF");
        airButton = controlButton("BUBBLES\nOFF");
        addControlRow(controls, heatButton, pumpButton);
        addGap(controls, 8);
        addControlRow(controls, jetsButton, airButton);
        addGap(controls, 8);
        powerButton = controlButton("POWER  •  OFF");
        controls.addView(powerButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 82));
        root.addView(controlsCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addGap(root, 12);

        // System island
        MaterialCardView systemCard = island();
        LinearLayout system = islandContent(systemCard, 16);
        systemTitle = sectionTitle("System");
        system.addView(systemTitle);
        addGap(system, 8);
        infoView = tv("Model: --\nFirmware: --\nIP: --", 14, TEXT);
        infoView.setLineSpacing(4, 1.0f);
        system.addView(infoView);
        rssiView = tv("RSSI: -- dBm", 13, MUTED);
        rssiView.setPadding(0, 10, 0, 0);
        system.addView(rssiView);
        root.addView(systemCard);

        languageButton.setOnClickListener(v -> {
            language = language.equals("en") ? "de" : "en";
            getPreferences(MODE_PRIVATE).edit().putString("language", language).apply();
            applyLanguage();
        });
        connectButton.setOnClickListener(v -> readConnectionAndConnect());
        minus.setOnClickListener(v -> setTarget(target - 1));
        plus.setOnClickListener(v -> setTarget(target + 1));
        heatButton.setOnClickListener(v -> toggle(3, heatState));
        pumpButton.setOnClickListener(v -> toggle(4, pumpState));
        airButton.setOnClickListener(v -> toggle(2, airState));
        jetsButton.setOnClickListener(v -> toggle(11, jetsState));
        powerButton.setOnClickListener(v -> toggle(25, powerState));

        setContentView(scroll);
    }

    private void applyLanguage() {
        boolean de = language.equals("de");
        languageButton.setText(de ? "DE" : "EN");
        appSubtitle.setText(de ? "SMART-SPA-STEUERUNG" : "SMART SPA CONTROL");
        tempLabel.setText(de ? "AKTUELLE TEMPERATUR" : "CURRENT TEMPERATURE");
        controlsTitle.setText(de ? "SPA-STEUERUNG" : "SPA CONTROLS");
        systemTitle.setText(de ? "SYSTEM" : "SYSTEM");
        hostLabel = hostLabel; portLabel = portLabel;
        if (connectButton != null) connectButton.setText(de ? "VERBINDEN" : "CONNECT");
        if (liveBadge != null) liveBadge.setText(de ? "LIVE" : "LIVE");
        updateAllButtons();
    }

    private void updateAllButtons() {
        updateButton(heatButton, language.equals("de") ? "HEIZUNG" : "HEATING", heatState);
        updateButton(pumpButton, language.equals("de") ? "PUMPE / FILTER" : "PUMP / FILTER", pumpState);
        updateButton(jetsButton, "HYDROJET", jetsState);
        updateButton(airButton, language.equals("de") ? "BLASEN" : "BUBBLES", airState);
        updatePowerButton();
        if (target > 0) targetView.setText((language.equals("de") ? "ZIEL  " : "TARGET  ") + target + "°");
    }

    private void updateButton(MaterialButton b, String label, int state) {
        if (b == null) return;
        boolean on = state == 1;
        b.setText(label + "\n" + (on ? "ON" : "OFF"));
        if (on) {
            b.setTextColor(Color.rgb(7, 20, 24));
            b.setBackgroundTintList(ColorStateList.valueOf(GREEN));
            b.setStrokeColor(ColorStateList.valueOf(GREEN));
        } else {
            b.setTextColor(TEXT);
            b.setBackgroundTintList(ColorStateList.valueOf(CARD2));
            b.setStrokeColor(ColorStateList.valueOf(BORDER));
        }
    }

    private void updatePowerButton() {
        if (powerButton == null) return;
        boolean on = powerState == 1;
        String power = language.equals("de") ? "STROM" : "POWER";
        powerButton.setText(power + "  •  " + (on ? "ON" : "OFF"));
        if (on) {
            powerButton.setTextColor(Color.rgb(7, 18, 24));
            powerButton.setBackgroundTintList(ColorStateList.valueOf(BLUE));
            powerButton.setStrokeColor(ColorStateList.valueOf(BLUE));
        } else {
            powerButton.setTextColor(TEXT);
            powerButton.setBackgroundTintList(ColorStateList.valueOf(CARD2));
            powerButton.setStrokeColor(ColorStateList.valueOf(BORDER));
        }
    }

    private void readConnectionAndConnect() {
        String newHost = hostEdit.getText() == null ? "" : hostEdit.getText().toString().trim();
        String portText = portEdit.getText() == null ? "" : portEdit.getText().toString().trim();
        if (newHost.isEmpty()) { toast(language.equals("de") ? "Host eingeben" : "Enter a host"); return; }
        int newPort;
        try { newPort = Integer.parseInt(portText); } catch (Exception e) { newPort = DEFAULT_PORT; }
        if (newPort < 1 || newPort > 65535) { toast(language.equals("de") ? "Ungültiger Port" : "Invalid port"); return; }
        host = newHost; port = newPort;
        getPreferences(MODE_PRIVATE).edit().putString("host", host).putInt("port", port).apply();
        manualClose = false;
        connect();
    }

    private void setTarget(int t) {
        if (target == 0) return;
        t = Math.max(20, Math.min(40, t));
        send(0, t);
    }

    private void toggle(int cmd, int state) {
        send(cmd, state == 1 ? 0 : 1);
    }

    private void send(int cmd, int value) {
        if (ws == null) { toast(language.equals("de") ? "Keine Verbindung zum ESP8266" : "No connection to ESP8266"); return; }
        try {
            JSONObject o = new JSONObject();
            o.put("CMD", cmd); o.put("VALUE", value);
            o.put("XTIME", 0); o.put("INTERVAL", 0); o.put("TXT", "");
            if (!ws.send(o.toString())) toast(language.equals("de") ? "Befehl nicht gesendet" : "Command not sent");
        } catch (Exception e) { toast(language.equals("de") ? "Befehlsfehler" : "Command error"); }
    }

    private void connect() {
        if (host == null || host.isEmpty()) return;
        manualClose = false;
        if (ws != null) ws.close(1000, "reconnect");
        setStatus(language.equals("de") ? "●  VERBINDUNG WIRD HERGESTELLT" : "●  CONNECTING");
        String url = "ws://" + host + ":" + port + "/";
        try {
            Request request = new Request.Builder().url(url).build();
            ws = client.newWebSocket(request, new WebSocketListener() {
                @Override public void onOpen(WebSocket w, Response r) {
                    runOnUiThread(() -> {
                        setStatus(language.equals("de") ? "●  VERBUNDEN" : "●  CONNECTED");
                        connectButton.setText(language.equals("de") ? "VERBUNDEN" : "CONNECTED");
                    });
                }
                @Override public void onMessage(WebSocket w, String text) { parse(text); }
                @Override public void onFailure(WebSocket w, Throwable t, Response r) {
                    runOnUiThread(() -> {
                        setStatus(language.equals("de") ? "●  NICHT VERBUNDEN" : "●  DISCONNECTED");
                        connectButton.setText(language.equals("de") ? "VERBINDEN" : "CONNECT");
                    });
                    if (!manualClose) reconnectLater();
                }
                @Override public void onClosed(WebSocket w, int code, String reason) {
                    if (!manualClose) reconnectLater();
                }
            });
        } catch (Exception e) {
            setStatus(language.equals("de") ? "●  UNGÜLTIGE ADRESSE" : "●  INVALID ADDRESS");
        }
    }

    private void reconnectLater() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::connect, 3000);
    }

    private void parse(String text) {
        try {
            JSONObject o = new JSONObject(text);
            String c = o.optString("CONTENT");
            if ("STATES".equals(c)) {
                target = o.optInt("TGT", 0);
                int temp = o.optInt("TMP", 0);
                heatState = o.optInt("GRN", 0);
                pumpState = o.optInt("FLT", 0);
                airState = o.optInt("AIR", 0);
                jetsState = o.optInt("HJT", 0);
                powerState = o.optInt("PWR", 0);
                runOnUiThread(() -> {
                    tempView.setText(temp + "°");
                    targetView.setText((language.equals("de") ? "ZIEL  " : "TARGET  ") + target + "°");
                    updateAllButtons();
                });
            } else if ("OTHER".equals(c)) {
                String model = o.optString("MODEL", "--");
                String fw = o.optString("FW", "--");
                String espIp = o.optString("IP", host);
                int rssi = o.optInt("RSSI", 0);
                runOnUiThread(() -> {
                    if (language.equals("de")) {
                        infoView.setText("Modell: " + model + "\nFirmware: " + fw + "\nIP: " + espIp);
                        rssiView.setText("RSSI: " + rssi + " dBm");
                    } else {
                        infoView.setText("Model: " + model + "\nFirmware: " + fw + "\nIP: " + espIp);
                        rssiView.setText("RSSI: " + rssi + " dBm");
                    }
                });
            }
        } catch (Exception ignored) {}
    }

    private void setStatus(String s) {
        if (status != null) {
            status.setText(s);
            if (s.contains("CONNECTED") || s.contains("VERBUNDEN")) status.setTextColor(GREEN);
            else if (s.contains("CONNECTING") || s.contains("VERBINDUNG")) status.setTextColor(BLUE);
            else status.setTextColor(RED);
        }
    }

    private void toast(String s) {
        runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }

    @Override protected void onDestroy() {
        manualClose = true;
        handler.removeCallbacksAndMessages(null);
        if (ws != null) ws.close(1000, "app close");
        client.dispatcher().executorService().shutdown();
        super.onDestroy();
    }
}
