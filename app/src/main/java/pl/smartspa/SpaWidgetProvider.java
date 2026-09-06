package pl.smartspa;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class SpaWidgetProvider extends AppWidgetProvider {
    public static void updateAll(Context context){AppWidgetManager m=AppWidgetManager.getInstance(context);int[] ids=m.getAppWidgetIds(new android.content.ComponentName(context,SpaWidgetProvider.class));for(int id:ids)update(context,m,id);}
    private static void update(Context c,AppWidgetManager m,int id){RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_spa);SharedPreferences p=c.getSharedPreferences("widget_state",Context.MODE_PRIVATE);int temp=p.getInt("temp",0),target=p.getInt("target",30);boolean connected=p.getBoolean("connected",false);v.setTextViewText(R.id.widgetTemp,temp>0?temp+"°":"--°");v.setTextViewText(R.id.widgetTarget,"TARGET  "+target+"°C");v.setTextViewText(R.id.widgetStatus,connected?"● CONNECTED":"● OFFLINE");Intent i=new Intent(c,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);v.setOnClickPendingIntent(R.id.widgetRoot,pi);m.updateAppWidget(id,v);}
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){for(int id:ids)update(c,m,id);}
    @Override public void onEnabled(Context c){super.onEnabled(c);updateAll(c);}
}
