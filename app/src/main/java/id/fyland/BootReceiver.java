package id.fyland;

import android.content.*;
import android.service.notification.NotificationListenerService;

/** Nudges the island to start after boot / app update. The accessibility service itself starts the idle island. */
public class BootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent intent){
  KeepAliveService.sync(c);
  try{NotificationListenerService.requestRebind(new ComponentName(c,IslandListener.class));}catch(Exception ignored){ErrorLog.w(ignored);}
  IslandAccess a=IslandAccess.live;
  if(a!=null&&IslandListener.live==null)a.startStandalone();
 }
}
