package pl.smartspa;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;

/** Persistent Smart Spa notification state and rules. */
public final class SpaNotificationManager {
    public static final String CHANNEL_ID = "smartspa_spa";
    private static final String PREF = "notifications";
    private SpaNotificationManager() {}

    public static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new android.app.NotificationChannel(
                    CHANNEL_ID, "Smart Spa – jacuzzi", NotificationManager.IMPORTANCE_DEFAULT));
        }
    }

    private static boolean allowed(Context c) {
        return Build.VERSION.SDK_INT < 33 || c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
    }
    private static SharedPreferences p(Context c) { return c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }
    public static boolean enabled(Context c, String key, boolean def) { return p(c).getBoolean(key, def); }

    public static void notify(Context c, int id, String title, String text) {
        if (!allowed(c)) return;
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT);
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, b.build());
    }

    public static synchronized void processConnection(Context context, boolean connected) {
        Context c = context.getApplicationContext();
        SharedPreferences p = p(c);
        boolean initialized = p.getBoolean("connection_initialized", false);
        boolean previous = p.getBoolean("connected", false);
        if (!initialized) {
            p.edit().putBoolean("connection_initialized", true).putBoolean("connected", connected).apply();
            return;
        }
        if (connected == previous) return;
        p.edit().putBoolean("connected", connected).apply();
        if (connected && enabled(c, "connection", true)) notify(c, 8101, "Smart Spa", "Połączenie z jacuzzi zostało przywrócone.");
        if (!connected && enabled(c, "connection", true)) notify(c, 8102, "Smart Spa", "Utracono połączenie z jacuzzi.");
    }

    public static synchronized void processState(Context context, JSONObject o) {
        Context c = context.getApplicationContext();
        try {
            int target = o.optInt("TGT", 0);
            int temp = o.optInt("TMP", 0);
            int heating = o.optInt("GRN", 0);
            long now = System.currentTimeMillis();
            SharedPreferences p = p(c);
            boolean initialized = p.getBoolean("state_initialized", false);
            boolean wasReached = p.getBoolean("target_reached", false);
            boolean reached = target > 0 && temp > 0 && temp >= target;
            if (!initialized) {
                p.edit().putBoolean("state_initialized", true).putBoolean("target_reached", reached).apply();
            } else {
                if (reached && !wasReached && enabled(c, "target", true)) {
                    notify(c, 8103, "Smart Spa – woda gotowa", "Woda osiągnęła ustawioną temperaturę " + target + "°C. Jacuzzi jest gotowe.");
                }
                if (!reached && wasReached) p.edit().putBoolean("target_reached", false).apply();
            }

            long heatSince = p.getLong("heat_since", 0L);
            boolean heatAlert = p.getBoolean("heat_alert_sent", false);
            if (heating == 1) {
                if (heatSince == 0L) heatSince = now;
                long limit = Math.max(1, Math.min(12, p.getInt("heat_hours", 3))) * 60L * 60L * 1000L;
                if (!heatAlert && now - heatSince >= limit && enabled(c, "long_heat", true)) {
                    notify(c, 8104, "Smart Spa – długie grzanie", "Grzanie jacuzzi działa już ponad " + Math.max(1, Math.min(12, p.getInt("heat_hours", 3))) + " godz. Czy na pewno chcesz nadal grzać wodę?");
                    heatAlert = true;
                }
            } else {
                heatSince = 0L;
                heatAlert = false;
            }
            p.edit().putLong("heat_since", heatSince).putBoolean("heat_alert_sent", heatAlert).putInt("last_temp", temp).putInt("last_target", target).apply();

            String err = o.optString("ERROR", "");
            if (err.isEmpty()) err = o.optString("ERR", "");
            if (err.isEmpty()) err = o.optString("error", "");
            if (!err.isEmpty() && !"0".equals(err) && enabled(c, "errors", true)) {
                String last = p.getString("last_error", "");
                if (!err.equals(last)) {
                    notify(c, 8105, "Smart Spa – błąd", err);
                    p.edit().putString("last_error", err).apply();
                }
            }
        } catch (Exception ignored) {}
    }

    public static void processMessage(Context c, String text) {
        try {
            JSONObject o = new JSONObject(text);
            String content = o.optString("CONTENT", "");
            if ("STATES".equals(content)) processState(c, o);
            String err = o.optString("ERROR", "");
            if (err.isEmpty()) err = o.optString("ERR", "");
            if ("ERROR".equalsIgnoreCase(content) && err.isEmpty()) err = o.optString("TXT", "Błąd jacuzzi");
            if (!err.isEmpty() && !"0".equals(err) && enabled(c.getApplicationContext(), "errors", true)) notify(c.getApplicationContext(), 8105, "Smart Spa – błąd", err);
        } catch (Exception ignored) {}
    }
}
