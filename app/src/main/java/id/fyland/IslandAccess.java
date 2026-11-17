package id.fyland;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.view.accessibility.AccessibilityEvent;

/** Visual overlay host. No screen-content retrieval, events, gestures or input control.
 *  Also keeps an idle island on screen on its own, so it is there right after boot and on the lock screen
 *  even before the notification listener connects. */
public class IslandAccess extends AccessibilityService {
 static IslandAccess live;
 IslandOverlay standalone;
 final Handler watchdog=new Handler(Looper.getMainLooper());
 /** If the notification listener is gone, keep the island alive on our own and keep asking Android to reconnect it. */
 final Runnable watch=new Runnable(){public void run(){
  try{
   KeepAliveService.sync(IslandAccess.this);
   if(IslandListener.live==null&&Prefs.enabled(IslandAccess.this)){
    startStandalone();
    try{NotificationListenerService.requestRebind(new ComponentName(IslandAccess.this,IslandListener.class));}catch(Exception ignored){ErrorLog.w(ignored);}
   }else if(IslandListener.live!=null&&IslandListener.live.overlay==null&&IslandListener.live.connected)IslandListener.live.rebindOverlay();
  }catch(Exception ignored){ErrorLog.w(ignored);}
  watchdog.postDelayed(this,10000);
 }};
 @Override protected void onServiceConnected(){
  live=this;KeepAliveService.sync(this);
  watchdog.removeCallbacks(watch);watchdog.postDelayed(watch,5000);
  AccessibilityServiceInfo info=getServiceInfo();
  if(info!=null){info.eventTypes=0;info.flags=0;setServiceInfo(info);}
  if(IslandListener.live!=null)IslandListener.live.rebindOverlay();else startStandalone();
 }
 void startStandalone(){
  if(IslandListener.live!=null)return;
  if(standalone==null)standalone=new IslandOverlay(this);
  standalone.idle();
 }
 static void dropStandalone(){
  IslandAccess a=live;
  if(a!=null&&a.standalone!=null){a.standalone.destroy();a.standalone=null;}
 }
 static void refreshStandalone(){
  IslandAccess a=live;
  if(a==null||a.standalone==null)return;
  if(!Prefs.enabled(a)||IslandListener.suspended())a.standalone.hide();else a.standalone.idle();
 }
 @Override public void onAccessibilityEvent(AccessibilityEvent event){}
 @Override public void onInterrupt(){}
 @Override public boolean onUnbind(Intent intent){release();return super.onUnbind(intent);}
 @Override public void onDestroy(){release();super.onDestroy();}
 private void release(){
  watchdog.removeCallbacks(watch);
  if(standalone!=null){standalone.destroy();standalone=null;}
  if(live==this){live=null;if(IslandListener.live!=null)IslandListener.live.rebindOverlay();}
 }
}
