package pl.smartspa;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONObject;

/** Periodic background poller. It keeps notification rules working when the UI is closed. */
public class SpaMonitorReceiver extends BroadcastReceiver {
    public static final int REQUEST_CODE = 7901;
    @Override public void onReceive(Context context, Intent intent) {
        final Context c = context.getApplicationContext();
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            MainActivity.scheduleSpaMonitor(c);
            return;
        }
        new Thread(() -> poll(c)).start();
    }
    private void poll(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences("spa", 0);
        String host = p.getString("host", "");
        if (host.isEmpty()) {
            // MainActivity historically stored the connection in Activity preferences too.
            host = c.getSharedPreferences("main", 0).getString("host", "192.168.1.40");
        }
        int port = p.getInt("port", 81);
        if (host.isEmpty()) host = "192.168.1.40";
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS).build();
        final boolean[] gotState = {false};
        final WebSocket[] socket = {null};
        try {
            Request r = new Request.Builder().url("ws://" + host + ":" + port + "/").build();
            socket[0] = client.newWebSocket(r, new WebSocketListener() {
                @Override public void onOpen(WebSocket w, Response response) { SpaNotificationManager.processConnection(c, true); }
                @Override public void onMessage(WebSocket w, String text) {
                    try {
                        JSONObject o = new JSONObject(text);
                        if ("STATES".equals(o.optString("CONTENT"))) { gotState[0] = true; SpaNotificationManager.processState(c, o); }
                        SpaNotificationManager.processMessage(c, text);
                    } catch (Exception ignored) {}
                    w.close(1000, "poll complete");
                }
                @Override public void onFailure(WebSocket w, Throwable t, Response response) { SpaNotificationManager.processConnection(c, false); }
                @Override public void onClosed(WebSocket w, int code, String reason) { if (!gotState[0]) SpaNotificationManager.processConnection(c, false); }
            });
            Thread.sleep(4500);
        } catch (Exception e) {
            SpaNotificationManager.processConnection(c, false);
        } finally {
            if (socket[0] != null) socket[0].cancel();
            client.dispatcher().executorService().shutdown();
        }
    }
}
