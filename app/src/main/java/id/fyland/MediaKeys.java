package id.fyland;

import android.media.session.PlaybackState;
import java.util.List;
import java.util.Locale;

/** Finds and classifies the extra music buttons (like, shuffle, repeat, add) that a player offers through its custom actions. */
final class MediaKeys {
 static final int HEART=0,SHUFFLE=1,REPEAT=2,ADD=3;
 /** Same bit values as PlaybackStateCompat; some players advertise these instead of custom actions. */
 static final long CAN_REPEAT=1L<<18,CAN_SHUFFLE=1L<<21;
 /** Command names MediaControllerCompat sends for shuffle / repeat; any compat-based player (and Media3) understands them. */
 static final String COMPAT_REPEAT="android.support.v4.media.session.action.SET_REPEAT_MODE",COMPAT_REPEAT_ARG="android.support.v4.media.session.action.ARGUMENT_REPEAT_MODE";
 static final String COMPAT_SHUFFLE="android.support.v4.media.session.action.SET_SHUFFLE_MODE",COMPAT_SHUFFLE_ARG="android.support.v4.media.session.action.ARGUMENT_SHUFFLE_MODE";
 /** Repeat modes use the compat numbering: 0 off, 1 one, 2 all. Tapping cycles off -> all -> one -> off. */
 static int nextRepeat(int mode){return mode==0?2:mode==2?1:0;}
 /** The island's repeat button repeats the current song: off <-> repeat one. */
 static int toggleRepeatOne(int mode){return mode==1?0:1;}

 private static boolean has(String text,String... words){for(String w:words)if(text.contains(w))return true;return false;}
 /** Which button this custom action belongs to, or -1 when it is none of the four. */
 static int kindOf(String id,String title){
  String t=((id==null?"":id)+" "+(title==null?"":title)).toLowerCase(Locale.ROOT);
  if(has(t,"shuffle","acak","random"))return SHUFFLE;
  if(has(t,"repeat","ulang","loop"))return REPEAT;
  if(has(t,"like","love","favorit","favourite","heart","suka","thumb"))return HEART;
  if(has(t,"add","tambah","save","simpan","playlist","library","koleksi"))return ADD;
  return -1;
 }
 /** True when the action's own label says the thing is already on ("Remove from Liked Songs", "Turn shuffle off"). */
 static boolean activeTitle(String title){
  if(title==null)return false;
  String t=title.toLowerCase(Locale.ROOT);
  return has(t,"remove","unlike","un-like","hapus","delete","turn off","disable","matikan","nonaktif","saved","added","tersimpan")||t.endsWith(" off")||t.contains(" off ");
 }
 static PlaybackState.CustomAction find(PlaybackState state,int kind){
  if(state==null)return null;
  List<PlaybackState.CustomAction> list=state.getCustomActions();
  if(list==null)return null;
  for(PlaybackState.CustomAction a:list)if(kindOf(a.getAction(),a.getName()==null?null:a.getName().toString())==kind)return a;
  return null;
 }
 /** Changes whenever the set of offered buttons changes, so the panel redraws them. */
 static String signature(PlaybackState state){
  if(state==null)return "-";
  StringBuilder b=new StringBuilder();b.append(state.getActions()&(CAN_REPEAT|CAN_SHUFFLE));
  List<PlaybackState.CustomAction> list=state.getCustomActions();
  if(list!=null)for(PlaybackState.CustomAction a:list)b.append('|').append(a.getAction()).append(':').append(a.getName());
  return b.toString();
 }
}
