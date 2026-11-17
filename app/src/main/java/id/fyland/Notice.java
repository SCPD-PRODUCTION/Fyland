package id.fyland;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import java.util.Locale;

final class Notice {
 String key,pkg,app,title,text,fullText,artist="",album="";
 long duration;
 Notification notification;
 Drawable icon;
 /** Real artwork (album art / large icon). Null means only the app icon is known. */
 Bitmap art,iconBitmap;
 String artKey="none";
 MediaController media;
 long posted;
 /** Island event kinds. 0 is a normal notification or music session. */
 static final int K_NONE=0,K_RINGER=1,K_CHARGING=2,K_LOWBATT=3,K_DND=4,K_TORCH=5,K_BT=6,K_MIC=7,K_TIMER=8,K_CALL_IN=9,K_CALL_ON=10,K_FACE=11;
 int kind,level,accent=0xffffffff,wDp;
 boolean on,chrono,countDown;
 long holdMs,chronoBase;
 Notification.Action answer,decline;

 Notice(StatusBarNotification s, Context c) {
  key=s.getKey();pkg=s.getPackageName();notification=s.getNotification();posted=s.getPostTime();loadApp(c);
  CharSequence t=notification.extras.getCharSequence(Notification.EXTRA_TITLE);
  CharSequence b=notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
  if(b==null)b=notification.extras.getCharSequence(Notification.EXTRA_TEXT);
  title=t==null?app:t.toString();text=b==null?L.s(R.string.s407):b.toString();fullText=text;
  android.os.Parcelable[] bundles=notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
  if(bundles!=null){
   StringBuilder messages=new StringBuilder();
   try{java.util.List<Notification.MessagingStyle.Message> list=Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles);
    for(int i=Math.max(0,list.size()-20);i<list.size();i++){Notification.MessagingStyle.Message message=list.get(i);if(messages.length()>0)messages.append("\n\n");CharSequence sender=message.getSender();if(sender!=null&&sender.length()>0)messages.append(sender).append(": ");if(message.getText()!=null)messages.append(message.getText());}
   }catch(Exception ignored){ErrorLog.w(ignored);}
   if(messages.length()>0)fullText=messages.substring(0,Math.min(12000,messages.length()));
  }else if(b==null){CharSequence[] lines=notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);if(lines!=null)fullText=android.text.TextUtils.join("\n",lines);}

  try {
   android.media.session.MediaSession.Token token=notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION);
   if(token!=null)media=new MediaController(c,token);
  }catch(Exception ignored){ErrorLog.w(ignored);}
  // Media notifications carry the cover as their large icon; use it when the session gave no bitmap.
  if(isMedia()){
   try{android.graphics.drawable.Icon large=notification.getLargeIcon();
    if(large!=null){Bitmap cover=Art.toBitmap(large.loadDrawable(c),MAX_ART);if(cover!=null){art=cover;artKey="n:"+cover.getWidth()+"x"+cover.getHeight();}}
   }catch(Exception ignored){ErrorLog.w(ignored);}
  }
  classify(c);
  if(!isMedia()&&kind==0)loadVisuals(c);
 }
 static final int MAX_ART=512;
 /** Contact / group picture (WhatsApp only), and a picture or sticker that came with the message. */
 Bitmap avatar;
 String pictureUri;Bitmap picture;boolean sticker;
 boolean whatsapp(){return pkg!=null&&pkg.startsWith("com.whatsapp");}
 /** Reads the profile picture and any attached picture / sticker out of the notification. Anything that cannot be read is just left out. */
 void loadVisuals(Context c){
  try{
   Notification n=notification;if(n==null)return;
   java.util.List<Notification.MessagingStyle.Message> list=null;
   android.os.Parcelable[] bundles=n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
   if(bundles!=null)try{list=Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles);}catch(Exception e){ErrorLog.w(e);}
   if(whatsapp()){
    Bitmap a=null;
    try{android.graphics.drawable.Icon large=n.getLargeIcon();if(large!=null)a=Art.toBitmap(large.loadDrawable(c),MAX_ART);}catch(Exception e){ErrorLog.w(e);}
    if(a==null&&list!=null)for(int i=list.size()-1;i>=0&&a==null;i--){
     try{android.app.Person p=list.get(i).getSenderPerson();if(p!=null&&p.getIcon()!=null)a=Art.toBitmap(p.getIcon().loadDrawable(c),MAX_ART);}catch(Exception e){ErrorLog.w(e);}
    }
    avatar=a;
   }
   Bitmap pic=null;
   Object big=n.extras.get(Notification.EXTRA_PICTURE);
   if(big instanceof Bitmap)pic=(Bitmap)big;
   if(pic==null){
    Object icon=n.extras.get("android.pictureIcon");
    if(icon instanceof android.graphics.drawable.Icon)try{pic=Art.toBitmap(((android.graphics.drawable.Icon)icon).loadDrawable(c),MAX_ART);}catch(Exception e){ErrorLog.w(e);}
   }
   if(pic==null&&list!=null&&!list.isEmpty()){
    Notification.MessagingStyle.Message last=list.get(list.size()-1);
    android.net.Uri data=last.getDataUri();String mime=last.getDataMimeType();
    if(data!=null&&mime!=null&&mime.startsWith("image/")){pictureUri=data.toString();sticker=mime.contains("webp")||looksSticker(last.getText());}
   }
   if(pic!=null){picture=pic;sticker=looksSticker(text);}
  }catch(Throwable t){ErrorLog.w(t);}
 }
 static boolean looksSticker(CharSequence t){if(t==null)return false;String s=t.toString().toLowerCase(Locale.ROOT);return s.contains("sticker")||s.contains("stiker");}
 /** The attached picture: ready bitmap, or one still loading in the background (the island redraws when it arrives). */
 Bitmap picture(Context c){
  if(picture!=null)return picture;
  if(pictureUri==null)return null;
  Bitmap b=Art.cached(pictureUri);if(b!=null)return b;
  if(!Art.failed(pictureUri))Art.request(c,pictureUri);
  return null;
 }
 /** The picture shown in the island's circle: real art when known, otherwise the app icon. */
 Bitmap face(){
  if(art!=null)return art;
  if(avatar!=null)return avatar;
  if(iconBitmap==null)iconBitmap=Art.toBitmap(icon,96);
  return iconBitmap;
 }
 Notice(String title,String body){key="preview";pkg="id.fyland";app="Fyland";this.title=title;text=body;fullText=body;posted=System.currentTimeMillis();}
 void loadApp(Context c){
  app=pkg;
  try{app=c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)).toString();icon=c.getPackageManager().getApplicationIcon(pkg);}catch(Exception ignored){ErrorLog.w(ignored);}
 }
 static Notice fromMedia(MediaController controller,Context c){
  Notice n=new Notice(L.s(R.string.s409),L.s(R.string.s410));n.media=controller;n.pkg=controller.getPackageName();n.key="media:"+n.pkg+":"+controller.getSessionToken().hashCode();n.loadApp(c);
  MediaMetadata m=controller.getMetadata();
  if(m!=null){
   String title=m.getString(MediaMetadata.METADATA_KEY_TITLE),artist=m.getString(MediaMetadata.METADATA_KEY_ARTIST);
   if(title==null)title=m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
   if(artist==null)artist=m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
   n.title=title==null?n.app:title;n.text=artist==null?n.app:artist;n.artist=artist==null?"":artist;n.album=m.getString(MediaMetadata.METADATA_KEY_ALBUM);n.duration=m.getLong(MediaMetadata.METADATA_KEY_DURATION);
   // Album art first (the real cover), then the display icon; then art given only as a URI (loaded in the background).
   Bitmap art=m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
   if(art==null)art=m.getBitmap(MediaMetadata.METADATA_KEY_ART);
   if(art==null)art=m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
   if(art!=null)n.artKey="b:"+art.getWidth()+"x"+art.getHeight();
   else for(String k:new String[]{MediaMetadata.METADATA_KEY_ALBUM_ART_URI,MediaMetadata.METADATA_KEY_ART_URI,MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI}){
    String uri=m.getString(k);if(uri==null||uri.isEmpty())continue;
    Bitmap got=Art.cached(uri);
    if(got!=null){art=got;n.artKey="u:"+uri;break;}
    if(!Art.failed(uri)){Art.request(c,uri);break;}
   }
   n.art=art;
  }else{n.title=n.app;n.text=L.s(R.string.s412);}
  n.fullText=n.text;return n;
 }

 /** A system event (ringer, charging...) drawn as an island pill. */
 static Notice event(int kind,String title,String text,int accent,int wDp,long holdMs){
  Notice n=new Notice(title,text);n.key="ev:"+kind;n.kind=kind;n.accent=accent;n.wDp=wDp;n.holdMs=holdMs;return n;
 }
 /** Kinds that stay on the island until they end, instead of showing for a few seconds. */
 boolean rests(){return isMedia()||kind==K_MIC||kind==K_TIMER||kind==K_CALL_ON;}
 void classify(Context c){
  if(notification==null||isMedia())return;
  String cat=notification.category;android.os.Bundle x=notification.extras;
  boolean chr=x.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER,false);
  if(Notification.CATEGORY_CALL.equals(cat)&&Prefs.flag(c,"ev_call",true)){
   int type=x.getInt("android.callType",0);
   boolean incoming=type==1||(type==0&&!chr&&notification.fullScreenIntent!=null);
   kind=incoming?K_CALL_IN:K_CALL_ON;accent=0xff30d158;wDp=170;
   findCallActions();if(chr)setChrono(x);
   return;
  }
  if(chr&&Prefs.flag(c,"ev_timer",true)&&looksLikeTimer(cat)){kind=K_TIMER;accent=0xffff9f0a;wDp=150;setChrono(x);}
  if(kind==0)classifyClock(c);
 }
 /** The island's own timer / stopwatch: its buttons run these instead of notification actions. */
 boolean own,ownPaused;
 Runnable ownToggle,ownStop,ownLap;
 /** Shown as text instead of a ticking chronometer (paused stopwatch, or a clock that only posts text). */
 String clockText;
 /** A voice recording in progress: same pill as a timer, but with the mic glyph. */
 boolean rec;
 /** Clock and recorder apps usually draw their time in a custom view and leave the notification text empty, so read it from there. */
 void classifyClock(Context c){
  String p=pkg==null?"":pkg.toLowerCase(Locale.ROOT);
  boolean recorder=p.contains("recorder")||p.contains("voicerecord");
  boolean clock=p.contains("clock")||p.contains("stopwatch")||p.contains("timer");
  if(!recorder&&!clock)return;
  if((notification.flags&(Notification.FLAG_ONGOING_EVENT|Notification.FLAG_FOREGROUND_SERVICE))==0)return;
  if(!Prefs.flag(c,recorder?"ev_mic":"ev_timer",true))return;
  android.widget.Chronometer ch=null;String found=null;
  try{
   java.util.ArrayList<android.widget.RemoteViews> views=new java.util.ArrayList<>();
   if(notification.contentView!=null)views.add(notification.contentView);
   if(notification.bigContentView!=null)views.add(notification.bigContentView);
   if(views.isEmpty()){
    try{
     Context app;try{app=c.createPackageContext(pkg,0);}catch(Exception e){ErrorLog.w(e);app=c;}
     Notification.Builder b=Notification.Builder.recoverBuilder(app,notification);
     android.widget.RemoteViews v=b.createContentView();if(v!=null)views.add(v);
     v=b.createBigContentView();if(v!=null)views.add(v);
    }catch(Exception ignored){ErrorLog.w(ignored);}
   }
   for(android.widget.RemoteViews rv:views){
    if(ch!=null||found!=null)break;
    try{
     android.view.View root=rv.apply(c,new android.widget.FrameLayout(c));
     java.util.ArrayList<android.widget.TextView> texts=new java.util.ArrayList<>();collect(root,texts);
     for(android.widget.TextView t:texts)if(t instanceof android.widget.Chronometer){ch=(android.widget.Chronometer)t;break;}
     if(ch==null)for(android.widget.TextView t:texts){CharSequence s=t.getText();if(s!=null&&TIME.matcher(s).find()){found=s.toString().trim();break;}}
    }catch(Exception ignored){ErrorLog.w(ignored);}
   }
  }catch(Exception ignored){ErrorLog.w(ignored);}
  if(ch==null&&found==null){
   // Last resort: the time may be in the plain notification text.
   android.os.Bundle x=notification.extras;
   for(String k:new String[]{Notification.EXTRA_TITLE,Notification.EXTRA_TEXT,Notification.EXTRA_SUB_TEXT,Notification.EXTRA_BIG_TEXT}){
    CharSequence s=x.getCharSequence(k);if(s!=null&&TIME.matcher(s).find()){found=s.toString().trim();break;}
   }
  }
  if(ch==null&&found==null&&!recorder)return;
  boolean paused=actionSays(PAUSED_WORDS)&&!actionSays(RUNNING_WORDS);
  if(ch!=null){
   if(paused){clockText=String.valueOf(ch.getText()).trim();}
   else{chrono=true;countDown=ch.isCountDown();chronoBase=ch.getBase();}
  }else if(found!=null)clockText=found;
  kind=K_TIMER;rec=recorder;accent=recorder?0xffff453a:0xffff9f0a;wDp=150;
  if(!recorder&&title!=null&&title.equalsIgnoreCase(app)){
   if(actionSays(LAP_WORDS))title="Stopwatch";else if(countDown)title="Timer";
  }
 }
 static final java.util.regex.Pattern TIME=java.util.regex.Pattern.compile("\\d{1,2}:\\d{2}");
 static final String[] RUNNING_WORDS={"pause","jeda"},PAUSED_WORDS={"resume","start","lanjut","mulai","play"},LAP_WORDS={"lap","count","putaran","hitung"};
 boolean actionSays(String[] words){
  if(notification==null||notification.actions==null)return false;
  for(Notification.Action a:notification.actions){
   if(a==null||a.title==null)continue;String t=a.title.toString().toLowerCase(Locale.ROOT);
   for(String w:words)if(t.contains(w))return true;
  }
  return false;
 }
 static void collect(android.view.View v,java.util.ArrayList<android.widget.TextView> out){
  if(v instanceof android.widget.TextView)out.add((android.widget.TextView)v);
  if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collect(g.getChildAt(i),out);}
 }
 boolean looksLikeTimer(String cat){
  if(Notification.CATEGORY_ALARM.equals(cat)||"stopwatch".equals(cat))return true;
  String p=pkg==null?"":pkg.toLowerCase(Locale.ROOT);
  return p.contains("clock")||p.contains("timer")||p.contains("stopwatch");
 }
 void setChrono(android.os.Bundle x){
  chrono=true;countDown=x.getBoolean("android.chronometerCountDown",false);
  long when=notification.when,now=System.currentTimeMillis(),el=SystemClock.elapsedRealtime();
  chronoBase=countDown?el+(when-now):el-(now-when);
 }
 void findCallActions(){
  Notification.Action[] a=notification.actions;if(a==null)return;
  for(Notification.Action act:a){
   if(act==null||act.actionIntent==null)continue;
   int r=CallActions.role(act.title);
   if(r==CallActions.ANSWER&&answer==null)answer=act;else if(r==CallActions.DECLINE&&decline==null)decline=act;
  }
  if(kind==K_CALL_IN&&answer==null&&decline==null&&a.length>=2&&a[0]!=null&&a[1]!=null&&a[0].actionIntent!=null&&a[1].actionIntent!=null){decline=a[0];answer=a[1];}
 }
 /** Sends a quick reply. Tries the newest live notification for this chat first (the stored copy can hold an outdated PendingIntent after the app re-posts it), then the stored one. Returns null on success or a short reason on failure. */
 String sendReply(Context c,String value){
  java.util.ArrayList<Notification> tries=new java.util.ArrayList<Notification>();
  try{
   IslandListener l=IslandListener.live;
   if(l!=null&&key!=null){android.service.notification.StatusBarNotification[] fresh=l.getActiveNotifications(new String[]{key});if(fresh!=null&&fresh.length>0&&fresh[0]!=null&&fresh[0].getNotification()!=null)tries.add(fresh[0].getNotification());}
  }catch(Exception e){ErrorLog.w(e);}
  if(notification!=null)tries.add(notification);
  String last="no reply action";
  for(Notification n:tries){
   int index=replyIndex(n);if(index<0)continue;
   try{
    Notification.Action action=n.actions[index];android.app.RemoteInput[] inputs=action.getRemoteInputs();android.os.Bundle results=new android.os.Bundle();
    for(android.app.RemoteInput in:inputs)results.putCharSequence(in.getResultKey(),value);
    android.content.Intent intent=new android.content.Intent();
    android.app.RemoteInput.addResultsToIntent(inputs,intent,results);
    if(android.os.Build.VERSION.SDK_INT>=28)android.app.RemoteInput.setResultsSource(intent,android.app.RemoteInput.SOURCE_FREE_FORM_INPUT);
    action.actionIntent.send(c,0,intent,null,null);
    return null;
   }catch(Exception e){ErrorLog.w(e);last=e.getClass().getSimpleName()+(e.getMessage()==null?"":": "+e.getMessage());}
  }
  return last;
 }
 int replyIndex(){return replyIndex(notification);}
 static int replyIndex(Notification notification){
  if(notification==null||notification.actions==null)return -1;
  for(int i=0;i<notification.actions.length;i++){Notification.Action action=notification.actions[i];if(action.actionIntent==null||action.getRemoteInputs()==null)continue;for(android.app.RemoteInput input:action.getRemoteInputs())if(input.getAllowFreeFormInput())return i;}
  return -1;
 }
 boolean isMedia(){return media!=null||(notification!=null&&(Notification.CATEGORY_TRANSPORT.equals(notification.category)||notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)));}
 boolean playing(){if(media==null)return false;PlaybackState s=media.getPlaybackState();return s!=null&&(s.getState()==PlaybackState.STATE_PLAYING||s.getState()==PlaybackState.STATE_BUFFERING||s.getState()==PlaybackState.STATE_CONNECTING);}
}
