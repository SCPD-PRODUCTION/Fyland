package id.fyland;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.*;
import android.service.notification.*;
import java.util.*;

public class IslandListener extends NotificationListenerService {
 public static IslandListener live;
 static boolean uiSuspended;
 static long suspendedAt;
 /** The settings screen hides the island only briefly; if the app is never reopened the island must not stay gone. */
 static boolean suspended(){
  if(!uiSuspended)return false;
  if(android.os.SystemClock.elapsedRealtime()-suspendedAt>12000){uiSuspended=false;return false;}
  return true;
 }
 IslandOverlay overlay;
 MediaBridge media;
 Notice music,mic,demoRest;
 SystemEvents events;
 String dismissedMedia="";
 final ArrayList<Notice> items=new ArrayList<>();
 /** Keys we removed from the native shade ourselves. Touched from the binder thread and the main thread. */
 final Set<String> detached=Collections.synchronizedSet(new HashSet<String>());
 final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
 final HashSet<String> quieted=new HashSet<>(); // worker thread only
 final LinkedHashMap<String,String> seen=new LinkedHashMap<>();
 final Handler main=new Handler(Looper.getMainLooper());
 int index,lastPlug=-1;
 boolean registered,connected;
 final Runnable mediaUpdate=new Runnable(){public void run(){updateMusic();}};

 final BroadcastReceiver screen=new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
  String action=intent.getAction();
  if(Intent.ACTION_SCREEN_OFF.equals(action)){if(overlay!=null)overlay.hide();}
  else if(Intent.ACTION_USER_PRESENT.equals(action))returnToRest();
  else if(Intent.ACTION_BATTERY_CHANGED.equals(action)){if(events!=null&&Prefs.enabled(c))events.battery(intent);}
 }};
 @Override public void onListenerConnected(){
  super.onListenerConnected();connected=true;live=this;KeepAliveService.sync(this);
  IslandAccess.dropStandalone();
  if(overlay==null)overlay=new IslandOverlay(IslandAccess.live==null?this:IslandAccess.live);
  items.clear();detached.clear();lastPlug=-1;
  if(!registered){
   IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);filter.addAction(Intent.ACTION_BATTERY_CHANGED);
   if(Build.VERSION.SDK_INT>=33)registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screen,filter);
   registered=true;
  }
  try{StatusBarNotification[] active=getActiveNotifications();if(active!=null)for(StatusBarNotification status:active){
   if(!eligible(status,null))continue;
   Notice item=new Notice(status,this);items.add(item);seen.put(item.pkg,item.app);if(items.size()>=30)break;
  }}catch(SecurityException ignored){ErrorLog.w(ignored);}
  if(media==null)media=new MediaBridge(this);media.start();
  if(events==null)events=new SystemEvents(this);events.start();
  updateMusic();
 }
 boolean eligible(StatusBarNotification status,RankingMap map){
  Notification n=status.getNotification();
  if(NotificationPolicy.isOwnOverlayNotice(status.getPackageName(),status.getTag(),String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE)),String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TEXT)),getPackageName()))return false;
  if((n.flags&Notification.FLAG_GROUP_SUMMARY)!=0)return false;
  if(Prefs.get(this).getStringSet("excluded",Collections.emptySet()).contains(status.getPackageName()))return false;
  boolean isMedia=Notification.CATEGORY_TRANSPORT.equals(n.category)||n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION);
  if(isMedia)return Prefs.flag(this,"music",true);
  Ranking rank=new Ranking();return Prefs.flag(this,"silent",true)||map==null||!map.getRanking(status.getKey(),rank)||rank.getImportance()>=NotificationManager.IMPORTANCE_DEFAULT;
 }
 @Override public void onNotificationPosted(StatusBarNotification status,RankingMap map){
  main.removeCallbacks(Popup.refreshRun);main.postDelayed(Popup.refreshRun,400);
  suppressNow(status,map); // before the hop to the main thread, so the native copy lives as briefly as possible
  main.post(new Runnable(){public void run(){accept(status,map);}});
 }
 /** Whether Block All should remove the app's own copy of this notification (the island shows it instead). */
 boolean shouldBlock(StatusBarNotification status,RankingMap map){
  IslandOverlay o=overlay;
  if(!connected||o==null||suspended()||!Prefs.enabled(this)||!Prefs.flag(this,"block",false))return false;
  if(!eligible(status,map)||!o.canShow())return false;
  Notification n=status.getNotification();
  if(!status.isClearable()||n.fullScreenIntent!=null||n.extras.containsKey(Notification.EXTRA_MEDIA_SESSION))return false;
  if((n.flags&(Notification.FLAG_ONGOING_EVENT|Notification.FLAG_NO_CLEAR|Notification.FLAG_FOREGROUND_SERVICE|Notification.FLAG_GROUP_SUMMARY))!=0)return false;
  String category=n.category;
  if(Notification.CATEGORY_CALL.equals(category)||Notification.CATEGORY_ALARM.equals(category)||Notification.CATEGORY_TRANSPORT.equals(category)||Notification.CATEGORY_NAVIGATION.equals(category)||Notification.CATEGORY_PROGRESS.equals(category))return false;
  // Reply-able chats are removed too: the island replies through the PendingIntent it already holds. This switch keeps them instead.
  if(Prefs.flag(this,"keep_reply",false)&&Notice.replyIndex(n)>=0)return false;
  return true;
 }
 /** Binder thread. Removes the native copy at once and lowers its channel so later ones do not pop up at all. */
 void suppressNow(StatusBarNotification status,RankingMap map){
  try{
   if(!shouldBlock(status,map))return;
   quietChannel(status);
   String key=status.getKey();detached.add(key);
   try{cancelNotification(key);}catch(SecurityException e){ErrorLog.w(e);detached.remove(key);}
  }catch(RuntimeException ignored){ErrorLog.w(ignored);}
 }
 /** Heads-up needs importance HIGH. Dropping the channel to DEFAULT keeps sound but ends the pop-up; undone by restoreChannels(). */
 void quietChannel(StatusBarNotification status){
  if(!Prefs.flag(this,"quiet",true)||Build.VERSION.SDK_INT<26)return;
  final String pkg=status.getPackageName(),channelId=status.getNotification().getChannelId();
  if(channelId==null||!status.getUser().equals(android.os.Process.myUserHandle()))return;
  final UserHandle user=status.getUser();
  worker.execute(new Runnable(){public void run(){
   if(!quieted.add(pkg+"\t"+channelId))return;
   try{
    List<NotificationChannel> channels=getNotificationChannels(pkg,user);if(channels==null)return;
    for(NotificationChannel channel:channels){
     if(!channelId.equals(channel.getId()))continue;
     int original=channel.getImportance();if(original<NotificationManager.IMPORTANCE_HIGH)return;
     HashSet<String> saved=new HashSet<>(Prefs.get(IslandListener.this).getStringSet("quiet_orig",Collections.<String>emptySet()));
     saved.add(pkg+"\t"+channelId+"\t"+original);Prefs.get(IslandListener.this).edit().putStringSet("quiet_orig",saved).apply();
     channel.setImportance(NotificationManager.IMPORTANCE_DEFAULT);updateNotificationChannel(pkg,user,channel);
     return;
    }
   }catch(Exception ignored){ErrorLog.w(ignored);}
  }});
 }
 /** Puts every channel we lowered back to its original importance (only if the user has not changed it since). */
 void restoreChannels(){
  if(Build.VERSION.SDK_INT<26)return;
  worker.execute(new Runnable(){public void run(){
   android.content.SharedPreferences prefs=Prefs.get(IslandListener.this);
   Set<String> saved=new HashSet<>(prefs.getStringSet("quiet_orig",Collections.<String>emptySet()));
   if(saved.isEmpty())return;
   HashSet<String> left=new HashSet<>(saved);UserHandle me=android.os.Process.myUserHandle();
   for(String entry:saved){
    String[] f=entry.split("\t");if(f.length!=3){left.remove(entry);continue;}
    try{
     List<NotificationChannel> channels=getNotificationChannels(f[0],me);
     if(channels!=null)for(NotificationChannel channel:channels)if(f[1].equals(channel.getId())){
      if(channel.getImportance()==NotificationManager.IMPORTANCE_DEFAULT){channel.setImportance(Integer.parseInt(f[2]));updateNotificationChannel(f[0],me,channel);}
      break;
     }
     left.remove(entry);
    }catch(Exception e){ErrorLog.w(e);if(e instanceof NumberFormatException)left.remove(entry);}
   }
   prefs.edit().putStringSet("quiet_orig",left).apply();quieted.clear();
  }});
 }
 void accept(StatusBarNotification status,RankingMap map){
  if(!connected||overlay==null||!Prefs.enabled(this)||!eligible(status,map))return;
  Notice item=new Notice(status,this);seen.put(item.pkg,item.app);if(seen.size()>150)seen.remove(seen.keySet().iterator().next());
  for(Iterator<Notice> it=items.iterator();it.hasNext();){Notice old=it.next();if(old.key.equals(item.key)){
   boolean same=old.title.equals(item.title)&&old.text.equals(item.text)&&String.valueOf(old.clockText).equals(String.valueOf(item.clockText))&&old.chrono==item.chrono&&old.kind==item.kind&&!detached.contains(item.key);
   it.remove();items.add(0,item);
   if(same){if(item.isMedia())mediaChanged();else if(overlay.current!=null&&overlay.current.key.equals(item.key)){if(!overlay.replyMode){overlay.current=item;overlay.render();}}return;}
   break;
  }}
  // The existing key may have been replaced in place above.
  items.removeIf(new java.util.function.Predicate<Notice>(){public boolean test(Notice n){return n.key.equals(item.key);}});items.add(0,item);
  while(items.size()>30){Notice removed=items.remove(items.size()-1);detached.remove(removed.key);}index=0;
  if(item.kind==Notice.K_TIMER||item.kind==Notice.K_CALL_ON){
   if(overlay.current!=null&&overlay.current.kind==Notice.K_CALL_IN&&overlay.current.key.equals(item.key))overlay.current=null;
   mediaChanged();return;
  }
  if(item.kind==Notice.K_CALL_IN){overlay.showExpanded(item);return;}
  if(item.isMedia()){mediaChanged();return;}
  boolean locked=((KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked();
  boolean shown=overlay.show(item,items.size());
  if(shown&&!locked&&!detached.contains(item.key)&&shouldBlock(status,map)){detached.add(item.key);try{cancelNotification(status.getKey());}catch(SecurityException e){ErrorLog.w(e);detached.remove(item.key);}}
 }
 @Override public void onNotificationRemoved(StatusBarNotification status){main.removeCallbacks(Popup.refreshRun);main.postDelayed(Popup.refreshRun,400);main.post(new Runnable(){public void run(){
  if(detached.contains(status.getKey()))return;
  items.removeIf(new java.util.function.Predicate<Notice>(){public boolean test(Notice n){return n.key.equals(status.getKey());}});
  if(overlay!=null&&overlay.current!=null&&overlay.current.key.equals(status.getKey())&&!overlay.replyMode)returnToRest();
  mediaChanged();
 }});}
 void mediaChanged(){main.removeCallbacks(mediaUpdate);main.postDelayed(mediaUpdate,100);}
 void updateMusic(){
  if(!connected)return;
  music=media==null?null:media.best();
  if(music==null&&Prefs.flag(this,"music",true))for(Notice item:items){
   if(item.isMedia()&&(item.media==null||item.media.getPlaybackState()==null||item.playing()||(item.media.getPlaybackState()!=null&&item.media.getPlaybackState().getState()==android.media.session.PlaybackState.STATE_PAUSED))){music=item;break;}
  }
  if(overlay==null)return;
  Notice rest=restNotice(),cur=overlay.current;
  if(cur==null||cur.rests()){
   if(cur!=null&&overlay.expanded&&cur.isMedia()&&rest!=null&&!rest.isMedia())return; // do not pull an open music panel away
   boolean keep=cur!=null&&overlay.expanded&&rest!=null&&cur.isMedia()==rest.isMedia()&&cur.kind==rest.kind;
   if(rest!=null)overlay.showMusic(rest,keep);else overlay.idle();
  }
 }
 /** What the island shows when nothing else is happening: call, timer, mic, demo, then music. */
 Notice restNotice(){
  if(demoRest!=null)return demoRest;
  Notice call=null,timer=null;
  for(Notice item:items){if(item.kind==Notice.K_CALL_ON&&call==null)call=item;else if(item.kind==Notice.K_TIMER&&timer==null)timer=item;}
  if(call!=null)return call;
  if(timer!=null)return timer;
  Notice own=ClockKit.rest();
  if(own!=null)return own;
  if(mic!=null)return mic;
  if(music!=null&&Prefs.flag(this,"music",true)&&!Prefs.get(this).getStringSet("excluded",Collections.<String>emptySet()).contains(music.pkg)&&!dismissedMedia.equals(mediaIdentity(music)))return music;
  return null;
 }
 /** A phone-state event from SystemEvents. Does not interrupt an incoming call or a message being read. */
 void postEvent(final Notice n){main.post(new Runnable(){public void run(){
  if(!connected||overlay==null||!Prefs.enabled(IslandListener.this)||overlay.replyMode)return;
  Notice cur=overlay.current;
  if(cur!=null&&cur.kind==Notice.K_CALL_IN)return;
  if(cur!=null&&overlay.expanded&&cur.kind==0&&!cur.isMedia())return;
  overlay.show(n,items.size());
 }});}
 void micActive(boolean on){
  if(!Prefs.flag(this,"ev_mic",true))on=false;
  if(on==(mic!=null))return;
  mic=on?Notice.event(Notice.K_MIC,"Mic","",SystemEvents.ORANGE,104,0):null;
  mediaChanged();
 }
 /** Settings button: plays every pill once so each animation can be checked. */
 void demoAll(){
  final Notice[] seq=new Notice[]{
   ev(Notice.K_RINGER,"Silent","Silent",SystemEvents.RED,200,2300,true,0),
   ev(Notice.K_RINGER,"Ring","Ring",SystemEvents.WHITE,200,2300,false,0),
   ev(Notice.K_CHARGING,"Charging","72%",SystemEvents.GREEN,220,3000,true,72),
   ev(Notice.K_LOWBATT,"Low Battery","20%",SystemEvents.RED,220,3000,false,20),
   ev(Notice.K_DND,"Focus","On",SystemEvents.PURPLE,170,2300,true,0),
   ev(Notice.K_TORCH,"Flashlight","On",SystemEvents.YELLOW,170,2300,true,0),
   ev(Notice.K_BT,"Bluetooth",L.s(R.string.s59),SystemEvents.BLUE,230,2800,true,0),
   ev(Notice.K_FACE,"Face Unlock","Unlocked",SystemEvents.GREEN,200,2400,true,0)
  };
  long at=0;
  for(final Notice n:seq){main.postDelayed(new Runnable(){public void run(){postEvent(n);}},at);at+=3000;}
  main.postDelayed(new Runnable(){public void run(){
   Notice t=Notice.event(Notice.K_TIMER,"Timer","05:00",SystemEvents.ORANGE,150,0);t.chrono=true;t.countDown=true;t.chronoBase=android.os.SystemClock.elapsedRealtime()+300000;
   demoRest=t;mediaChanged();
  }},at);at+=4500;
  main.postDelayed(new Runnable(){public void run(){
   Notice call=Notice.event(Notice.K_CALL_IN,"Ketum",L.s(R.string.s63),SystemEvents.GREEN,170,0);
   demoRest=null;if(overlay!=null)overlay.showExpanded(call);
  }},at);at+=4500;
  main.postDelayed(new Runnable(){public void run(){demoRest=null;returnToRest();}},at);
 }
 static Notice ev(int kind,String title,String text,int accent,int w,long hold,boolean on,int level){Notice n=Notice.event(kind,title,text,accent,w,hold);n.on=on;n.level=level;return n;}
 static String mediaIdentity(Notice n){return n.pkg+"|"+n.title;}
 void returnToRest(){
  if(overlay==null)return;
  Notice r=restNotice();
  if(r!=null)overlay.showMusic(r,false);else overlay.idle();
 }
 public void rebindOverlay(){main.post(new Runnable(){public void run(){
  Notice prior=overlay==null?null:overlay.current;boolean expanded=overlay!=null&&overlay.expanded;
  if(overlay!=null)overlay.destroy();
  overlay=new IslandOverlay(IslandAccess.live==null?IslandListener.this:IslandAccess.live);
  if(prior!=null){overlay.show(prior,items.size());overlay.expanded=expanded;overlay.render();}else returnToRest();
 }});}
 public void refresh(){main.post(new Runnable(){public void run(){
  if(!Prefs.flag(IslandListener.this,"block",false)||!Prefs.flag(IslandListener.this,"quiet",true))restoreChannels();
  if(overlay==null)return;
  items.removeIf(new java.util.function.Predicate<Notice>(){public boolean test(Notice n){return Prefs.get(IslandListener.this).getStringSet("excluded",Collections.emptySet()).contains(n.pkg);}});
  if(!Prefs.enabled(IslandListener.this)||suspended()){overlay.hide();return;}
  if(overlay.current!=null&&!overlay.current.isMedia()&&!Prefs.get(IslandListener.this).getStringSet("excluded",Collections.emptySet()).contains(overlay.current.pkg))overlay.render();
  else{overlay.current=null;updateMusic();}
 }});}
 public void preview(Notice n){main.post(new Runnable(){public void run(){if(overlay!=null)overlay.show(n,1);}});}
 void latest(){if(!items.isEmpty()){index=0;overlay.show(items.get(0),items.size());}}
 void navigate(int direction){if(items.isEmpty()){returnToRest();return;}index=(index+direction+items.size())%items.size();overlay.show(items.get(index),items.size());overlay.expanded=true;overlay.render();}
 void dismissCurrent(){
  if(overlay.current!=null){Notice current=overlay.current;
   if(current.isMedia()){dismissedMedia=mediaIdentity(current);overlay.current=null;overlay.idle();return;}
   items.removeIf(new java.util.function.Predicate<Notice>(){public boolean test(Notice n){return n.key.equals(current.key);}});detached.remove(current.key);
   try{if(current.notification!=null)cancelNotification(current.key);}catch(Exception ignored){ErrorLog.w(ignored);}
  }
  returnToRest();
 }
 @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);refresh();}
 void release(){
  connected=false;main.removeCallbacksAndMessages(null);
  if(registered){unregisterReceiver(screen);registered=false;}
  if(events!=null){events.stop();events=null;}
  if(media!=null)media.stop();if(overlay!=null)overlay.destroy();overlay=null;music=null;mic=null;demoRest=null;
  if(live==this)live=null;
  if(IslandAccess.live!=null)IslandAccess.live.startStandalone();
 }
 @Override public void onListenerDisconnected(){
  release();super.onListenerDisconnected();
  // Some phones drop the connection in the background and never reconnect on their own; ask Android to bind us again.
  try{requestRebind(new ComponentName(this,IslandListener.class));}catch(Exception ignored){ErrorLog.w(ignored);}
 }
 @Override public void onDestroy(){release();items.clear();detached.clear();seen.clear();worker.shutdown();super.onDestroy();}
}
