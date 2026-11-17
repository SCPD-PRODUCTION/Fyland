package id.fyland;

import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import java.util.HashMap;
import java.util.List;

/** Phone-state events shown on the island like on an iPhone: ringer, Focus, torch, charging, low battery,
 *  Bluetooth, microphone in use. Public Android APIs only; no extra permission except Bluetooth device names. */
final class SystemEvents {
 static final int RED=0xffff453a,GREEN=0xff30d158,YELLOW=0xffffd60a,PURPLE=0xff9b8cff,BLUE=0xff0a84ff,ORANGE=0xffff9f0a,WHITE=0xffffffff;
 final IslandListener owner;final Handler main;
 AudioManager audio;CameraManager camera;
 boolean started;
 int lastRinger=-1,lastFilter=-1,lastPlug=-1,lastLevel=-1;
 long screenOnAt;
 /** Lock-screen watch: a biometric unlock leaves the device unlocked while the lock screen is still showing, until the user swipes. */
 boolean polling,sawLocked,faceFired,frontCameraUsed,frontCameraCycle,frontCameraReleased;int pollTicks;long graceUntil,lastFrontAt;
 final HashMap<String,Boolean> torchState=new HashMap<>();
 AudioManager.AudioRecordingCallback recording;CameraManager.TorchCallback torchCallback;

 SystemEvents(IslandListener owner){this.owner=owner;this.main=owner.main;}

 final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){try{handle(intent);}catch(RuntimeException ignored){ErrorLog.w(ignored);}}};

 void start(){
  if(started)return;started=true;
  audio=(AudioManager)owner.getSystemService(Context.AUDIO_SERVICE);
  camera=(CameraManager)owner.getSystemService(Context.CAMERA_SERVICE);
  if(audio!=null)lastRinger=audio.getRingerMode();
  NotificationManager nm=(NotificationManager)owner.getSystemService(Context.NOTIFICATION_SERVICE);
  if(nm!=null)lastFilter=nm.getCurrentInterruptionFilter();
  IntentFilter f=new IntentFilter();
  f.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
  f.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
  f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
  f.addAction(Intent.ACTION_SCREEN_ON);
  f.addAction(Intent.ACTION_SCREEN_OFF);
  f.addAction(Intent.ACTION_USER_PRESENT);
  try{if(Build.VERSION.SDK_INT>=33)owner.registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else owner.registerReceiver(receiver,f);}catch(RuntimeException ignored){ErrorLog.w(ignored);}
  watchCamera();
  try{
   if(camera!=null){
    torchCallback=new CameraManager.TorchCallback(){
     @Override public void onTorchModeChanged(String id,boolean enabled){torch(id,enabled);}
     @Override public void onTorchModeUnavailable(String id){}
    };
    camera.registerTorchCallback(torchCallback,main);
   }
  }catch(Exception ignored){ErrorLog.w(ignored);torchCallback=null;}
  try{
   if(audio!=null){
    recording=new AudioManager.AudioRecordingCallback(){
     @Override public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs){
      int active=0;
      if(configs!=null)for(AudioRecordingConfiguration cfg:configs){if(Build.VERSION.SDK_INT>=29&&cfg.isClientSilenced())continue;active++;}
      owner.micActive(active>0);
     }
    };
    audio.registerAudioRecordingCallback(recording,main);
   }
  }catch(Exception ignored){ErrorLog.w(ignored);recording=null;}
 }

 void stop(){
  if(!started)return;started=false;
  stopPoll();
  try{if(camera!=null&&camWatch!=null)camera.unregisterAvailabilityCallback(camWatch);}catch(Exception ignored){ErrorLog.w(ignored);}
  camWatch=null;
  try{owner.unregisterReceiver(receiver);}catch(Exception ignored){ErrorLog.w(ignored);}
  try{if(camera!=null&&torchCallback!=null)camera.unregisterTorchCallback(torchCallback);}catch(Exception ignored){ErrorLog.w(ignored);}
  try{if(audio!=null&&recording!=null)audio.unregisterAudioRecordingCallback(recording);}catch(Exception ignored){ErrorLog.w(ignored);}
  torchCallback=null;recording=null;torchState.clear();
 }

 void handle(Intent intent){
  String action=intent.getAction();if(action==null)return;
  if(Intent.ACTION_SCREEN_ON.equals(action)){
   screenOnAt=SystemClock.elapsedRealtime();startPoll();
   
   return;
  }
  if(Intent.ACTION_SCREEN_OFF.equals(action)){stopPoll();return;}
  if(Intent.ACTION_USER_PRESENT.equals(action)){stopPoll();faceUnlocked();return;}
  if(AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action)){
   int mode=intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE,-1);
   if(mode<0||mode==lastRinger)return;lastRinger=mode;
   if(!Prefs.flag(owner,"ev_ringer",true))return;
   boolean silent=mode!=AudioManager.RINGER_MODE_NORMAL;
   Notice n=Notice.event(Notice.K_RINGER,silent?"Silent":"Ring",silent?"Silent":"Ring",silent?RED:WHITE,200,2400);n.on=silent;
   owner.postEvent(n);
  }else if(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED.equals(action)){
   NotificationManager nm=(NotificationManager)owner.getSystemService(Context.NOTIFICATION_SERVICE);if(nm==null)return;
   int filter=nm.getCurrentInterruptionFilter();
   if(filter==lastFilter)return;boolean wasOn=lastFilter>NotificationManager.INTERRUPTION_FILTER_ALL;lastFilter=filter;
   boolean on=filter>NotificationManager.INTERRUPTION_FILTER_ALL;
   if(on==wasOn||!Prefs.flag(owner,"ev_dnd",true))return;
   Notice n=Notice.event(Notice.K_DND,"Focus",on?"On":"Off",on?PURPLE:WHITE,170,2400);n.on=on;
   owner.postEvent(n);
  }else if(BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
   if(!Prefs.flag(owner,"ev_bt",true))return;
   String name="Connected";
   try{
    BluetoothDevice d=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    if(d!=null){String s=d.getName();if(s!=null&&!s.isEmpty())name=s;}
   }catch(RuntimeException ignored){ErrorLog.w(ignored);}
   Notice n=Notice.event(Notice.K_BT,"Bluetooth",name,BLUE,230,3000);
   owner.postEvent(n);
  }
 }

 /** Does this phone have face unlock? The standard feature flag, plus the names some makers use. */
 static boolean faceSupported(Context c){
  try{
   PackageManager pm=c.getPackageManager();
   if(pm.hasSystemFeature("android.hardware.biometrics.face"))return true;
   android.content.pm.FeatureInfo[] all=pm.getSystemAvailableFeatures();
   if(all!=null)for(android.content.pm.FeatureInfo f:all){
    String n=f.name==null?"":f.name.toLowerCase(java.util.Locale.ROOT);
    if(n.contains("biometrics.face")||n.contains("bio.face")||n.contains("faceunlock")||n.contains("face_unlock")||n.contains("facelock")||n.contains("faceid")||n.contains("face.recogn"))return true;
   }
  }catch(Exception ignored){ErrorLog.w(ignored);}
  return false;
 }
 static boolean fingerprintSupported(Context c){try{return c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);}catch(Exception e){ErrorLog.w(e);return false;}}
 /** Android does not say how the screen was unlocked. A secure lock screen that opens within moments of the screen turning on (a PIN takes longer to type) is treated as a face unlock. */
 void faceUnlocked(){
  long since=SystemClock.elapsedRealtime()-screenOnAt;
  String why=null;
  KeyguardManager km=(KeyguardManager)owner.getSystemService(Context.KEYGUARD_SERVICE);
  if(!Prefs.flag(owner,"ev_face",false))why="toggle Face Unlock mati";
  else if(km==null||!km.isDeviceSecure())why="kunci layar tidak aman (PIN/pola/sandi)";
  else if(faceFired)why="kartu sudah muncul di lock screen";
  else if(screenOnAt==0||since<0)why="layar-nyala belum tercatat";
  else if(since>WINDOW_MS)why="terlalu lama setelah layar menyala ("+since+" ms > "+WINDOW_MS+" ms)";
  
  if(why==null)postFace("USER_PRESENT");
 }
 static final long WINDOW_MS=6000L,GRACE_MS=1500L,PRE_MS=1500L;
 void postFace(String how){
  if(faceFired)return;faceFired=true;
  
  owner.postEvent(Notice.event(Notice.K_FACE,"Face Unlock","Unlocked",GREEN,200,2400));
 }
 int lastDl=-1,lastShown=-1;
 final Runnable poll=new Runnable(){public void run(){
  if(!polling)return;
  pollTicks++;
  try{checkLockState();}catch(RuntimeException e){ErrorLog.w(e);polling=false;}
  if(polling&&pollTicks<110)main.postDelayed(this,120);else if(polling){polling=false;}
 }};
 void startPoll(){
  stopPoll();faceFired=false;sawLocked=false;frontCameraUsed=false;frontCameraCycle=false;frontCameraReleased=false;graceUntil=0;pollTicks=0;lastDl=-1;lastShown=-1;
  if(!Prefs.flag(owner,"ev_face",false))return;
  polling=true;main.post(poll);
 }
 void stopPoll(){polling=false;main.removeCallbacks(poll);}
 /** Locked -> unlocked while the lock screen is still on screen means a biometric (face) unlock; fires the card right on the lock screen. */
 void checkLockState(){
  KeyguardManager km=(KeyguardManager)owner.getSystemService(Context.KEYGUARD_SERVICE);
  if(km==null||!km.isDeviceSecure()){polling=false;return;}
  boolean deviceLocked=km.isDeviceLocked(),shown=km.isKeyguardLocked();
  int dl=deviceLocked?1:0,sh=shown?1:0;
  if(dl!=lastDl||sh!=lastShown){lastDl=dl;lastShown=sh;}
  if(deviceLocked){sawLocked=true;return;}
  if(!shown){
   polling=false;
   // Locked -> unlocked and the lock screen closed in the same instant (phones that go
   // straight to the home screen after a face scan). Front camera activity in the window
   // separates a face scan from a PIN or fingerprint unlock.
   long now=SystemClock.elapsedRealtime(),since=now-screenOnAt;
   if(!sawLocked||faceFired||since<0||since>WINDOW_MS)return;
   // Kamera depan bisa dilepas sedikit sebelum layar menyala (dicatat lastFrontAt) atau sedikit
   // sesudah lock screen tertutup (<~0,5 dtk), jadi pantau tetap jalan sebentar (GRACE_MS).
   boolean cam=frontCameraUsed||frontCameraReleased||frontCameraCycle||(lastFrontAt>0&&screenOnAt-lastFrontAt>=0&&screenOnAt-lastFrontAt<=PRE_MS);
   if(cam){polling=false;postFace("terbuka + kamera depan, lock screen langsung tertutup");return;}
   if(graceUntil==0)graceUntil=now+GRACE_MS;
   if(now>=graceUntil)polling=false;
   return;
  }
  // On some Android builds (including some Tecno/XOS versions), face unlock
  // completes before the first SCREEN_ON poll. In that case isDeviceLocked()
  // is already false, so a strict LOCKED -> UNLOCKED transition is missed.
  // Keep watching while the lock screen is still visible and accept a completed
  // front-camera cycle as supporting evidence, but never camera use alone.
  if(sawLocked){
   polling=false;postFace("terbuka saat lock screen masih tampil");return;
  }
  if(frontCameraCycle&&SystemClock.elapsedRealtime()-screenOnAt<=WINDOW_MS){
   polling=false;postFace("kamera depan + lock screen masih tampil");return;
  }
  // Do not stop here: the camera service may report the face scan a little
  // after the first keyguard state sample. Continue until the bounded poll ends.
 }
 /** Front camera taken or released by the system while the screen is waking: the telltale of a face scan. Logged only. */
 CameraManager.AvailabilityCallback camWatch;
 void watchCamera(){
  if(camera==null||camWatch!=null)return;
  camWatch=new CameraManager.AvailabilityCallback(){
   @Override public void onCameraUnavailable(String id){camLog(id,"dipakai");}
   @Override public void onCameraAvailable(String id){camLog(id,"dilepas");}
  };
  try{camera.registerAvailabilityCallback(camWatch,main);}catch(Exception ignored){ErrorLog.w(ignored);camWatch=null;}
 }
 void camLog(String id,String what){
  long since=SystemClock.elapsedRealtime()-screenOnAt;
  String face="?";
  try{Integer f=camera.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);face=f==null?"?":f==android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT?"depan":f==android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK?"belakang":"lain";}catch(Exception ignored){ErrorLog.w(ignored);}
  
  if("depan".equals(face)){
   lastFrontAt=SystemClock.elapsedRealtime();
   if(screenOnAt==0||since<0||since>WINDOW_MS)return; // sebelum layar menyala: hanya dicatat di lastFrontAt
   if("dipakai".equals(what)){
    frontCameraUsed=true;
   }else{
    frontCameraReleased=true;
    if(frontCameraUsed)frontCameraCycle=true;
   }
   if(polling)try{checkLockState();}catch(RuntimeException ignored){ErrorLog.w(ignored);}
  }
 }

 void torch(String id,boolean enabled){
  Boolean prev=torchState.put(id,enabled);
  if(prev==null||prev==enabled)return; // first callback only reports the current state
  if(!Prefs.flag(owner,"ev_torch",true))return;
  Notice n=Notice.event(Notice.K_TORCH,"Flashlight",enabled?"On":"Off",enabled?YELLOW:WHITE,170,2200);n.on=enabled;
  owner.postEvent(n);
 }

 /** Fed from the sticky battery broadcast the listener already holds. */
 void battery(Intent i){
  int plug=i.getIntExtra(BatteryManager.EXTRA_PLUGGED,0),level=i.getIntExtra(BatteryManager.EXTRA_LEVEL,0),scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,100);
  int pct=level*100/Math.max(1,scale);
  if(lastPlug>=0&&plug!=lastPlug&&plug!=0&&Prefs.flag(owner,"charging",true)){
   Notice n=Notice.event(Notice.K_CHARGING,"Charging",pct+"%",GREEN,220,3200);n.level=pct;owner.postEvent(n);
  }else if(lastLevel>=0&&plug==0&&Prefs.flag(owner,"ev_battery",true)&&((pct<=20&&lastLevel>20)||(pct<=10&&lastLevel>10)||(pct<=5&&lastLevel>5))){
   Notice n=Notice.event(Notice.K_LOWBATT,"Low Battery",pct+"%",RED,220,3600);n.level=pct;owner.postEvent(n);
  }
  lastPlug=plug;lastLevel=pct;
 }
}
