package id.fyland;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.*;
import java.util.ArrayList;
import java.util.Locale;

/** The island's own timer and stopwatch. They keep running in the background and show as a pill, like the iPhone's. */
final class ClockKit {
 static final Handler main=new Handler(Looper.getMainLooper());
 static Context app;
 static boolean timerOn,timerPaused,timerDone;
 static long timerEnd,timerLeft,timerTotal;
 static boolean swOn,swPaused;
 static long swBase,swFrozen;
 static final ArrayList<Long> laps=new ArrayList<>();
 static Ringtone ringtone;

 static final Runnable fire=new Runnable(){public void run(){timerFired(app);}};
 static final Runnable autoStop=new Runnable(){public void run(){stopRing();}};
 static final Runnable pauseTimerRun=new Runnable(){public void run(){pauseTimer();}};
 static final Runnable resumeTimerRun=new Runnable(){public void run(){resumeTimer();}};
 static final Runnable cancelTimerRun=new Runnable(){public void run(){cancelTimer();}};
 static final Runnable doneRun=new Runnable(){public void run(){dismissDone();}};
 static final Runnable pauseSwRun=new Runnable(){public void run(){pauseSw();}};
 static final Runnable resumeSwRun=new Runnable(){public void run(){resumeSw();}};
 static final Runnable lapRun=new Runnable(){public void run(){lap();}};
 static final Runnable stopSwRun=new Runnable(){public void run(){stopSw();}};

 static long now(){return SystemClock.elapsedRealtime();}

 // ---------- timer ----------
 static void startTimer(Context c,long ms){
  app=c.getApplicationContext();
  cancelAlarm();stopRing();
  timerOn=true;timerPaused=false;timerDone=false;timerTotal=ms;timerEnd=now()+ms;
  schedule();changed();
 }
 static void schedule(){
  main.removeCallbacks(fire);
  long left=Math.max(0,timerEnd-now());
  main.postDelayed(fire,left);
  try{
   AlarmManager am=(AlarmManager)app.getSystemService(Context.ALARM_SERVICE);
   PendingIntent show=PendingIntent.getActivity(app,72,new Intent(app,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
   am.setAlarmClock(new AlarmManager.AlarmClockInfo(System.currentTimeMillis()+left,show),alarmIntent());
  }catch(Exception ignored){ErrorLog.w(ignored);}
 }
 static PendingIntent alarmIntent(){
  Intent i=new Intent(app,ClockReceiver.class).setAction("id.fyland.TIMER_FIRED");
  return PendingIntent.getBroadcast(app,71,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
 }
 static void cancelAlarm(){
  main.removeCallbacks(fire);
  try{if(app!=null)((AlarmManager)app.getSystemService(Context.ALARM_SERVICE)).cancel(alarmIntent());}catch(Exception ignored){ErrorLog.w(ignored);}
 }
 static void pauseTimer(){
  if(!timerOn||timerPaused||timerDone)return;
  timerLeft=Math.max(0,timerEnd-now());timerPaused=true;cancelAlarm();changed();
 }
 static void resumeTimer(){
  if(!timerOn||!timerPaused)return;
  timerPaused=false;timerEnd=now()+timerLeft;schedule();changed();
 }
 static void cancelTimer(){timerOn=false;timerPaused=false;timerDone=false;cancelAlarm();stopRing();changed();}
 static void dismissDone(){timerOn=false;timerDone=false;timerPaused=false;stopRing();changed();}
 static void timerFired(Context c){
  if(!timerOn||timerDone||timerPaused)return;
  if(c!=null)app=c.getApplicationContext();
  timerDone=true;cancelAlarm();ring(app);changed();
  final IslandListener l=IslandListener.live;
  if(l!=null)l.main.post(new Runnable(){public void run(){
   if(l.overlay!=null&&l.overlay.panel==null&&!l.overlay.replyMode)l.overlay.showMusic(l.restNotice(),true);
  }});
 }

 // ---------- stopwatch ----------
 static void startSw(){swOn=true;swPaused=false;swBase=now();swFrozen=0;laps.clear();changed();}
 static void pauseSw(){if(!swOn||swPaused)return;swFrozen=now()-swBase;swPaused=true;changed();}
 static void resumeSw(){if(!swOn||!swPaused)return;swBase=now()-swFrozen;swPaused=false;changed();}
 static void lap(){if(swOn)laps.add(swElapsed());changed();}
 static void stopSw(){swOn=false;swPaused=false;laps.clear();changed();}
 static long swElapsed(){return swPaused?swFrozen:now()-swBase;}

 // ---------- island notices ----------
 static Notice rest(){
  if(timerOn)return timerNotice();
  if(swOn)return swNotice();
  return null;
 }
 static Notice timerNotice(){
  Notice n=Notice.event(Notice.K_TIMER,timerDone?L.s(R.string.s30):"Timer","",timerDone?SystemEvents.RED:SystemEvents.ORANGE,150,0);
  n.key="own:timer";n.own=true;
  if(timerDone){n.clockText="0:00";n.ownStop=doneRun;}
  else if(timerPaused){n.clockText=fmt(timerLeft);n.ownPaused=true;n.ownToggle=resumeTimerRun;n.ownStop=cancelTimerRun;}
  else{n.chrono=true;n.countDown=true;n.chronoBase=timerEnd;n.ownToggle=pauseTimerRun;n.ownStop=cancelTimerRun;}
  return n;
 }
 static Notice swNotice(){
  String title="Stopwatch";
  if(!laps.isEmpty())title="Lap "+laps.size()+"  ·  "+fmt(laps.get(laps.size()-1));
  Notice n=Notice.event(Notice.K_TIMER,title,"",SystemEvents.ORANGE,150,0);
  n.key="own:stopwatch";n.own=true;
  if(swPaused){n.clockText=fmt(swFrozen);n.ownPaused=true;n.ownToggle=resumeSwRun;}
  else{n.chrono=true;n.countDown=false;n.chronoBase=swBase;n.ownToggle=pauseSwRun;}
  n.ownLap=lapRun;n.ownStop=stopSwRun;
  return n;
 }
 static void changed(){
  final IslandListener l=IslandListener.live;
  if(l!=null)l.main.post(new Runnable(){public void run(){l.mediaChanged();}});
 }
 static String fmt(long ms){
  long s=Math.max(0,ms/1000);long h=s/3600,m=(s%3600)/60,sec=s%60;
  return h>0?String.format(Locale.US,"%d:%02d:%02d",h,m,sec):String.format(Locale.US,"%d:%02d",m,sec);
 }

 // ---------- ringing ----------
 static void ring(Context c){
  stopRing();
  try{
   Uri u=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
   if(u==null)u=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
   ringtone=RingtoneManager.getRingtone(c,u);
   if(ringtone!=null){
    ringtone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
    if(Build.VERSION.SDK_INT>=28)ringtone.setLooping(true);
    ringtone.play();
   }
  }catch(Exception ignored){ErrorLog.w(ignored);}
  try{((Vibrator)c.getSystemService(Context.VIBRATOR_SERVICE)).vibrate(VibrationEffect.createWaveform(new long[]{0,600,400},0));}catch(Exception ignored){ErrorLog.w(ignored);}
  main.removeCallbacks(autoStop);main.postDelayed(autoStop,30000);
 }
 static void stopRing(){
  main.removeCallbacks(autoStop);
  try{if(ringtone!=null)ringtone.stop();}catch(Exception ignored){ErrorLog.w(ignored);}
  ringtone=null;
  try{if(app!=null)((Vibrator)app.getSystemService(Context.VIBRATOR_SERVICE)).cancel();}catch(Exception ignored){ErrorLog.w(ignored);}
 }
}
