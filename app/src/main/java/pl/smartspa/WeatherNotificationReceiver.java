package pl.smartspa;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import pl.smartspa.ai.SpaAiEngine;

public class WeatherNotificationReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent){
        new Thread(()->run(context.getApplicationContext())).start();
    }
    private void run(Context c){
        try{
            if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
            android.content.SharedPreferences p=c.getSharedPreferences("weather",0); double lat=p.getFloat("lat",0),lon=p.getFloat("lon",0); String city=p.getString("city",""); if(lat==0&&lon==0)return;
            String u="https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+"&current=temperature_2m,weather_code&timezone=auto";
            Response resp=new OkHttpClient().newCall(new Request.Builder().url(u).build()).execute(); String body=resp.body()!=null?resp.body().string():""; JSONObject o=new JSONObject(body),cur=o.getJSONObject("current"); int out=(int)Math.round(cur.optDouble("temperature_2m",0)); int code=cur.optInt("weather_code",0); int rec=SpaAiEngine.recommendedTemperature(out,code); String desc=SpaAiEngine.shortWeatherDescription(code);
            p.edit().putLong("updated",System.currentTimeMillis()).putInt("outside",out).putInt("recommended",rec).putString("description",desc).apply();
            Intent i=new Intent(c,MainActivity.class); PendingIntent pi=PendingIntent.getActivity(c,702,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder b=new NotificationCompat.Builder(c,"smartspa_weather").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Smart Spa · "+city).setContentText("Zalecana temperatura: "+rec+"°C").setStyle(new NotificationCompat.BigTextStyle().bigText("Na zewnątrz: "+out+"°C, "+desc+". Smart Spa zaleca ustawienie "+rec+"°C." )).setContentIntent(pi).setAutoCancel(true);
            c.getSystemService(NotificationManager.class).notify(704,b.build());
        }catch(Exception ignored){}
    }

}
