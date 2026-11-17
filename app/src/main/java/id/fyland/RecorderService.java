package id.fyland;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Records audio for the island's voice recorder popup. It runs as a microphone foreground service so Android lets it keep recording in the background. */
public class RecorderService extends Service {
 static volatile boolean recording;
 static volatile long startAt;
 static volatile int amp;
 static volatile String status="";
 final Handler h=new Handler(Looper.getMainLooper());
 MediaRecorder rec;Uri uri;ParcelFileDescriptor pfd;String name;File file;
 final Runnable ampTick=new Runnable(){public void run(){
  if(recording&&rec!=null){try{amp=rec.getMaxAmplitude();}catch(Exception ignored){ErrorLog.w(ignored);}h.postDelayed(this,80);}
 }};
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  String action=intent==null?null:intent.getAction();
  if("stop".equals(action)){stopRec();stopSelf();return START_NOT_STICKY;}
  startRec();
  return START_NOT_STICKY;
 }
 void startRec(){
  if(recording)return;
  try{
   NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
   nm.createNotificationChannel(new NotificationChannel("rec",L.s(R.string.s515),NotificationManager.IMPORTANCE_LOW));
   Notification n=new Notification.Builder(this,"rec").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle(L.s(R.string.s516)).setContentText("Fyland").setOngoing(true).build();
   if(Build.VERSION.SDK_INT>=29)startForeground(41,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);else startForeground(41,n);
  }catch(Exception e){ErrorLog.w(e);status=L.s(R.string.s517);stopSelf();return;}
  try{
   name="Fyland-"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".m4a";
   pfd=null;file=null;uri=null;
   if(Build.VERSION.SDK_INT>=29){
    ContentValues v=new ContentValues();
    v.put(MediaStore.MediaColumns.DISPLAY_NAME,name);v.put(MediaStore.MediaColumns.MIME_TYPE,"audio/mp4");
    v.put(MediaStore.MediaColumns.RELATIVE_PATH,"Music/Fyland");v.put(MediaStore.MediaColumns.IS_PENDING,1);
    uri=getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,v);
    pfd=getContentResolver().openFileDescriptor(uri,"w");
   }
   rec=Build.VERSION.SDK_INT>=31?new MediaRecorder(this):new MediaRecorder();
   rec.setAudioSource(MediaRecorder.AudioSource.MIC);rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
   rec.setAudioEncodingBitRate(128000);rec.setAudioSamplingRate(44100);
   if(pfd!=null)rec.setOutputFile(pfd.getFileDescriptor());
   else{File dir=new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC),"Fyland");dir.mkdirs();file=new File(dir,name);rec.setOutputFile(file.getAbsolutePath());}
   rec.prepare();rec.start();
   recording=true;startAt=SystemClock.elapsedRealtime();status=L.s(R.string.s522);h.post(ampTick);
  }catch(Exception e){ErrorLog.w(e);
   cleanup(false);recording=false;status=L.s(R.string.s523)+(e.getMessage()==null?L.s(R.string.s524):e.getMessage());
   stopForeground(true);stopSelf();
  }
 }
 void stopRec(){
  if(!recording){stopForeground(true);return;}
  recording=false;
  try{rec.stop();}catch(RuntimeException ignored){ErrorLog.w(ignored);}
  cleanup(true);
  status=L.s(R.string.s525)+name;
  stopForeground(true);
 }
 void cleanup(boolean keep){
  try{if(rec!=null)rec.release();}catch(Exception ignored){ErrorLog.w(ignored);}
  rec=null;
  try{if(pfd!=null)pfd.close();}catch(Exception ignored){ErrorLog.w(ignored);}
  pfd=null;
  if(uri!=null){
   try{
    if(keep){ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.IS_PENDING,0);getContentResolver().update(uri,v,null,null);}
    else getContentResolver().delete(uri,null,null);
   }catch(Exception ignored){ErrorLog.w(ignored);}
   uri=null;
  }
 }
 @Override public void onDestroy(){if(recording)stopRec();super.onDestroy();}
}
