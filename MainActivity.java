package pl.bartek.layzspa;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class MainActivity extends AppCompatActivity {
    private static final String DEFAULT_IP = "192.168.1.40";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS).build();
    private WebSocket ws;
    private boolean manualClose;
    private String ip;
    private int target;
    private int heatState, pumpState, airState, jetsState, powerState;

    private TextView status, tempView, targetView, infoView, rssiView;
    private TextInputEditText ipEdit;
    private MaterialButton connectButton, heatButton, pumpButton, airButton, jetsButton, powerButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences p = getPreferences(MODE_PRIVATE);
        ip = p.getString("ip", DEFAULT_IP);
        buildUi();
        connect();
    }

    private TextView text(String s, float size) {
        TextView v = new TextView(this);
        v.setText(s); v.setTextSize(size); v.setPadding(0, 10, 0, 10);
        return v;
    }

    private MaterialButton button(String s) {
        MaterialButton b = new MaterialButton(this);
        b.setText(s); b.setAllCaps(false);
        return b;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        return r;
    }

    private void addHalf(LinearLayout r, MaterialButton b) {
        r.addView(b, new LinearLayout.LayoutParams(0, 70, 1));
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24, 20, 24, 24);
        scroll.addView(root);

        TextView title = text("Lay-Z-Spa Miami", 28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); root.addView(title);
        status = text("● Rozłączono", 15); root.addView(status);

        LinearLayout ipRow = row();
        TextInputLayout til = new TextInputLayout(this); til.setHint("IP ESP8266");
        ipEdit = new TextInputEditText(this); ipEdit.setText(ip); til.addView(ipEdit);
        ipRow.addView(til, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        connectButton = button("Połącz"); ipRow.addView(connectButton);
        root.addView(ipRow);
        connectButton.setOnClickListener(v -> {
            ip = ipEdit.getText() == null ? "" : ipEdit.getText().toString().trim();
            getPreferences(MODE_PRIVATE).edit().putString("ip", ip).apply();
            manualClose = false; connect();
        });

        tempView = text("Temperatura: -- °C", 24); tempView.setGravity(Gravity.CENTER); root.addView(tempView);
        targetView = text("Cel: -- °C", 20); targetView.setGravity(Gravity.CENTER); root.addView(targetView);

        LinearLayout tr = row();
        MaterialButton minus = button("−"), plus = button("+"); addHalf(tr, minus); addHalf(tr, plus); root.addView(tr);
        minus.setOnClickListener(v -> setTarget(target - 1)); plus.setOnClickListener(v -> setTarget(target + 1));

        root.addView(text("Sterowanie", 20));
        LinearLayout r1 = row(); heatButton = button("Grzanie"); pumpButton = button("Pompa / filtr"); addHalf(r1, heatButton); addHalf(r1, pumpButton); root.addView(r1);
        LinearLayout r2 = row(); airButton = button("Bąbelki"); jetsButton = button("HydroJet"); addHalf(r2, airButton); addHalf(r2, jetsButton); root.addView(r2);
        powerButton = button("Zasilanie"); root.addView(powerButton);

        heatButton.setOnClickListener(v -> toggle(3, heatState, "Grzanie"));
        pumpButton.setOnClickListener(v -> toggle(4, pumpState, "Pompa"));
        airButton.setOnClickListener(v -> toggle(2, airState, "Bąbelki"));
        jetsButton.setOnClickListener(v -> toggle(11, jetsState, "HydroJet"));
        // SETPOWER is command 25 in the current upstream enum (0-based).
        powerButton.setOnClickListener(v -> toggle(25, powerState, "Zasilanie"));

        root.addView(text("Informacje", 20));
        infoView = text("Model: --\nFirmware: --\nIP: --", 15); root.addView(infoView);
        rssiView = text("RSSI: -- dBm", 15); root.addView(rssiView);
        setContentView(scroll);
    }

    private void setTarget(int t) {
        if (target == 0) return;
        t = Math.max(20, Math.min(40, t)); send(0, t);
    }

    private void toggle(int cmd, int state, String name) { send(cmd, state == 1 ? 0 : 1); }

    private void send(int cmd, int value) {
        if (ws == null) { toast("Brak połączenia z ESP8266"); return; }
        try {
            JSONObject o = new JSONObject();
            o.put("CMD", cmd); o.put("VALUE", value); o.put("XTIME", 0); o.put("INTERVAL", 0); o.put("TXT", "");
            if (!ws.send(o.toString())) toast("Nie wysłano polecenia");
        } catch (Exception e) { toast("Błąd polecenia"); }
    }

    private void connect() {
        if (ip == null || ip.isEmpty()) return;
        manualClose = false;
        if (ws != null) ws.close(1000, "reconnect");
        setStatus("● Łączenie z ws://" + ip + ":81/");
        Request request = new Request.Builder().url("ws://" + ip + ":81/").build();
        ws = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket w, Response r) {
                runOnUiThread(() -> { setStatus("● Połączono"); connectButton.setText("Połączono"); });
            }
            @Override public void onMessage(WebSocket w, String text) { parse(text); }
            @Override public void onFailure(WebSocket w, Throwable t, Response r) {
                runOnUiThread(() -> { setStatus("● Brak połączenia"); connectButton.setText("Połącz"); });
                if (!manualClose) reconnectLater();
            }
            @Override public void onClosed(WebSocket w, int code, String reason) { if (!manualClose) reconnectLater(); }
        });
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
                    tempView.setText("Temperatura: " + temp + " °C");
                    targetView.setText("Cel: " + target + " °C");
                    heatButton.setText("Grzanie " + (heatState == 1 ? "ON" : "OFF"));
                    pumpButton.setText("Pompa / filtr " + (pumpState == 1 ? "ON" : "OFF"));
                    airButton.setText("Bąbelki " + (airState == 1 ? "ON" : "OFF"));
                    jetsButton.setText("HydroJet " + (jetsState == 1 ? "ON" : "OFF"));
                    powerButton.setText("Zasilanie " + (powerState == 1 ? "ON" : "OFF"));
                });
            } else if ("OTHER".equals(c)) {
                String model = o.optString("MODEL", "--");
                String fw = o.optString("FW", "--");
                String espIp = o.optString("IP", ip);
                int rssi = o.optInt("RSSI", 0);
                runOnUiThread(() -> {
                    infoView.setText("Model: " + model + "\nFirmware: " + fw + "\nIP: " + espIp);
                    rssiView.setText("RSSI: " + rssi + " dBm");
                });
            }
        } catch (Exception ignored) { }
    }

    private void setStatus(String s) { if (status != null) status.setText(s); }
    private void toast(String s) { runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show()); }

    @Override protected void onDestroy() {
        manualClose = true; handler.removeCallbacksAndMessages(null);
        if (ws != null) ws.close(1000, "app close");
        client.dispatcher().executorService().shutdown(); super.onDestroy();
    }
}
