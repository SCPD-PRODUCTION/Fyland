package id.fyland;

import android.app.*;
import android.content.*;
import android.os.*;

/** Foreground service with a silent, minimal notification. It keeps the process alive so Android (and phone brands'
 *  battery managers) do not kill the island overlay in the background. */
public class KeepAliveService extends Service {
 static final String CHANNEL="keepalive";
 static final int ID=7001;
 static volatile boolean running;
 /** Starts or stops the service to match the settings. Safe to call often and from anywhere. */
 static void sync(Context c){
  try{
   Context app=c.getApplicationContext();if(app==null)app=c;
   boolean want=Prefs.enabled(app)&&Prefs.flag(app,"keepalive",true);
   Intent i=new Intent(app,KeepAliveService.class);
   if(want&&!running){if(Build.VERSION.SDK_INT>=26)app.startForegroundService(i);else app.startService(i);}
   else if(!want&&running)app.stopService(i);
  }catch(Exception e){ErrorLog.w(e);}
 }
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  try{
   NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
   if(Build.VERSION.SDK_INT>=26&&nm!=null){
    NotificationChannel ch=new NotificationChannel(CHANNEL,L.s(R.string.s173),NotificationManager.IMPORTANCE_MIN);
    ch.setShowBadge(false);ch.setSound(null,null);ch.enableVibration(false);nm.createNotificationChannel(ch);
   }
   Intent open=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
   PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
   Notification n=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_stat).setContentTitle(L.s(R.string.s173)).setContentText(L.s(R.string.s174)).setContentIntent(pi).setOngoing(true).setShowWhen(false).build();
   if(Build.VERSION.SDK_INT>=34)startForeground(ID,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(ID,n);
   running=true;
  }catch(Exception e){ErrorLog.w(e);running=false;stopSelf();}
  return START_STICKY;
 }
 @Override public void onDestroy(){running=false;super.onDestroy();}
}
