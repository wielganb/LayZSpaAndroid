package pl.smartspa;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class SpaSessionWidgetProvider extends AppWidgetProvider {
    public static void updateAll(Context context){
        AppWidgetManager m=AppWidgetManager.getInstance(context);
        int[] ids=m.getAppWidgetIds(new ComponentName(context,SpaSessionWidgetProvider.class));
        for(int id:ids) update(context,m,id);
    }

    private static void update(Context c, AppWidgetManager m, int id){
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_spa_session);
        SharedPreferences p=c.getSharedPreferences("widget_state",Context.MODE_PRIVATE);
        int temp=p.getInt("temp",0), target=p.getInt("target",30);
        boolean connected=p.getBoolean("connected",false);
        long end=p.getLong("session_end",0L);

        v.setTextViewText(R.id.sessionWidgetTemp,temp>0?temp+"°":"--°");
        v.setTextViewText(R.id.sessionWidgetTarget,"CEL "+target+"°C");
        v.setTextViewText(R.id.sessionWidgetStatus,connected?"● POŁĄCZONO":"● ROZŁĄCZONO");
        if(end>System.currentTimeMillis()){
            long remaining=end-System.currentTimeMillis();
            v.setChronometer(R.id.sessionWidgetTimer,android.os.SystemClock.elapsedRealtime()+remaining,"%s",true);
            v.setChronometerCountDown(R.id.sessionWidgetTimer,true);
            v.setTextViewText(R.id.sessionWidgetLabel,"POZOSTAŁO");
        } else {
            v.setChronometer(R.id.sessionWidgetTimer,android.os.SystemClock.elapsedRealtime(),"%s",false);
            v.setChronometerCountDown(R.id.sessionWidgetTimer,false);
            v.setTextViewText(R.id.sessionWidgetTimer,"00:00");
            v.setTextViewText(R.id.sessionWidgetLabel,"SESJA GOTOWA");
        }

        Intent i=new Intent(c,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.sessionWidgetRoot,pi);
        m.updateAppWidget(id,v);
    }

    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids)update(c,m,id);}
    @Override public void onEnabled(Context c){super.onEnabled(c);updateAll(c);}
}
