package id.fyland;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.*;
import java.util.*;

/** Reads active sessions under the user's notification-listener grant. No polling or microphone. */
final class MediaBridge {
 final IslandListener owner;
 final MediaSessionManager manager;
 final LinkedHashMap<MediaController,MediaController.Callback> watched=new LinkedHashMap<>();
 boolean registered;
 final MediaSessionManager.OnActiveSessionsChangedListener changed=new MediaSessionManager.OnActiveSessionsChangedListener(){public void onActiveSessionsChanged(List<MediaController> l){bind(l);}};
 MediaBridge(IslandListener owner){this.owner=owner;manager=(MediaSessionManager)owner.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE);}
 void start(){
  if(manager==null)return;
  try{
   ComponentName component=new ComponentName(owner,IslandListener.class);
   if(!registered){manager.addOnActiveSessionsChangedListener(changed,component,owner.main);registered=true;}
   bind(manager.getActiveSessions(component));
  }catch(SecurityException ignored){ErrorLog.w(ignored);}
 }
 void bind(List<MediaController> sessions){
  for(Map.Entry<MediaController,MediaController.Callback> e:watched.entrySet())e.getKey().unregisterCallback(e.getValue());
  watched.clear();
  if(sessions!=null)for(MediaController controller:sessions){
   MediaController.Callback callback=new MediaController.Callback(){
    @Override public void onMetadataChanged(MediaMetadata metadata){owner.mediaChanged();}
    @Override public void onPlaybackStateChanged(PlaybackState state){owner.mediaChanged();}
    @Override public void onSessionDestroyed(){owner.main.post(new Runnable(){public void run(){start();}});}
   };
   watched.put(controller,callback);controller.registerCallback(callback,owner.main);
  }
  owner.mediaChanged();
 }
 Notice best(){
  if(!Prefs.flag(owner,"music",true))return null;
  MediaController selected=null;int best=0;
  for(MediaController controller:watched.keySet()){
   if(Prefs.get(owner).getStringSet("excluded",Collections.emptySet()).contains(controller.getPackageName()))continue;
   PlaybackState state=controller.getPlaybackState();if(state==null)continue;
   int s=state.getState();int score=(s==PlaybackState.STATE_PLAYING||s==PlaybackState.STATE_BUFFERING||s==PlaybackState.STATE_CONNECTING)?3:s==PlaybackState.STATE_PAUSED?2:0;
   if(score>best){selected=controller;best=score;}
  }
  return selected==null?null:Notice.fromMedia(selected,owner);
 }
 void stop(){
  if(registered){manager.removeOnActiveSessionsChangedListener(changed);registered=false;}
  for(Map.Entry<MediaController,MediaController.Callback> e:watched.entrySet())e.getKey().unregisterCallback(e.getValue());watched.clear();
 }
}
