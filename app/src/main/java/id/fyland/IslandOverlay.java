package id.fyland;

import android.app.*;
import android.animation.ValueAnimator;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.util.DisplayMetrics;
import android.media.MediaMetadata;
import android.media.Rating;
import android.media.session.*;
import android.media.session.MediaController;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;

/** One attached window per host. Content and layout update in place, with no repeated fades. */
final class IslandOverlay {
 final Context c;
 final WindowManager wm;
 final Handler handler=new Handler(Looper.getMainLooper());
 final boolean aboveStatus;
 LinearLayout view;
 Notice current;
 boolean expanded;
 final WindowAttachment window=new WindowAttachment();
 int count;
 float touchX;
 String fingerprint="";
 WindowManager.LayoutParams params;
 TextView progressLabel;
 WavyProgress progress;
 final LyricsClient lyricsClient=new LyricsClient();
 LyricsDocument lyricsDocument;
 TextView lyricsSource;
 LyricsFlowView lyricsFlow,retainedLyricsFlow;
 String lyricsKey="",lyricsRequested="",lyricsMessage="";
 int lyricLine=-2;
 boolean replyMode;
 int deadChecks,renderRetries,settleTries;
 ComponentCallbacks callbacks;
 /** Shuffle / repeat as last set from the island, per player package (a player does not tell us its mode). */
 final java.util.HashMap<String,Integer> modes=new java.util.HashMap<>();
 /** Swipe pages and tools drawn inside the island: apps, weather, clock, timer, alarm, notepad. null = normal island. */
 String panel;
 void redraw(){if(panel!=null||current!=null)render();else idle();}
 boolean inputPanel(){return "notepad".equals(panel)||"calendar".equals(panel);}
 void setPanel(String p){hideIme();panel=p;expanded=p!=null;fingerprint="";render();}
 void refreshPanel(){if(panel!=null){fingerprint="";render();}}
 void hideIme(){
  try{if(view!=null)((android.view.inputmethod.InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(),0);}catch(Exception ignored){ErrorLog.w(ignored);}
 }
 void closePanel(){
  hideIme();panel=null;expanded=false;fingerprint="";
  if(IslandListener.live!=null&&IslandListener.live.overlay==this)IslandListener.live.returnToRest();else idle();
 }
 /** Show whatever is resting on the island (running timer, music...) opened up. */
 void showRestExpanded(){
  hideIme();panel=null;
  IslandListener l=IslandListener.live;
  Notice r=l==null?null:l.restNotice();
  if(r!=null){showMusic(r,true);return;}
  expanded=false;idle();
 }
 boolean pagerPage(){return "apps".equals(panel)||"weather".equals(panel);}
 void pagerSwipe(int dir){
  if("weather".equals(panel)&&dir>0)setPanel("apps");
  else if("apps".equals(panel)&&dir<0)setPanel("weather");
 }
 /** Tapping the idle pill opens the weather page (and from there the swipe pages). */
 void idleTap(){
  if(Prefs.flag(c,"pages",true))setPanel("weather");
  else if(IslandListener.live!=null)IslandListener.live.latest();
 }
 /** Left swipe on the resting island opens the pages; right swipe keeps browsing notifications. */
 void swipe(int dir){
  Notice n=current;boolean atRest=n==null||n.rests();
  if(atRest&&panel==null&&Prefs.flag(c,"pages",true)){setPanel("weather");return;}
  if(IslandListener.live!=null)IslandListener.live.navigate(dir);
 }
 float gDownX,gDownY;boolean gFired,gMoved,gBlock;
 /** The progress bar is for seeking: a touch that starts on it (with some slack around the thin bar) is never a swipe. */
 boolean onProgress(float x,float y){
  if(progress==null||!expanded||progress.getWindowToken()==null)return false;
  int[] loc=new int[2];progress.getLocationOnScreen(loc);int pad=dp(16);
  return x>=loc[0]-pad&&x<=loc[0]+progress.getWidth()+pad&&y>=loc[1]-pad&&y<=loc[1]+progress.getHeight()+pad;
 }
 int swipeDir(float dx,float dy){
  if(landscape()&&!expanded)return Math.abs(dy)>=Math.abs(dx)?(dy<0?1:-1):((dx>0)==(rotation()!=3)?1:-1);
  return dx<0?1:-1;
 }
 boolean swipeFar(float dx,float dy,float limit){
  if(landscape()&&!expanded)return Math.max(Math.abs(dx),Math.abs(dy))>dp(limit);
  return Math.abs(dx)>dp(limit)&&Math.abs(dx)>Math.abs(dy)*(expanded?1.2f:.6f);
 }
 View.OnTouchListener swipeListener(){return new View.OnTouchListener(){
  public boolean onTouch(View v,MotionEvent event){
   float dx=event.getRawX()-gDownX,dy=event.getRawY()-gDownY;
   switch(event.getActionMasked()){
    case MotionEvent.ACTION_DOWN:return false;
    case MotionEvent.ACTION_MOVE:
     if(gFired)return true;
     if(!swipeFar(dx,dy,12))return false;
     gFired=true;v.cancelLongPress();v.setPressed(false);swipe(swipeDir(dx,dy));return true;
    case MotionEvent.ACTION_UP:{
     if(!gFired&&swipeFar(dx,dy,10)){gFired=true;swipe(swipeDir(dx,dy));}
     boolean was=gFired;gFired=false;return was;
    }
    case MotionEvent.ACTION_CANCEL:{boolean was=gFired;gFired=false;return was;}
   }
   return false;
  }
 };}
 String replyDraft="";
 EditText replyInput;
 TextView replyStatus;
 View coverView;
 TextView coverBattery;
 boolean coverAttached;
 int lastRotation=-1;
 DisplayManager.DisplayListener displayListener;
 // Spring-driven morph of the window and its rounded background (the iPhone-style stretch).
 final Spring spring=new Spring(380f,.64f);
 int curW,curH,curX,curY,curGravity=-1,fromW,fromH,fromX,fromY,toW,toH,toX,toY;
 float cornerNow,fromR,toR;
 GradientDrawable bgShape;
 /** Per-song accent taken from the cover; drives title, wave and button colours. */
 int accent=0xffff7a2e;
 String revealKey="";
 TextView progressTotal;

 final Runnable collapse=new Runnable(){public void run(){
  if(replyMode||panel!=null)return;
  if(current!=null&&current.rests()){expanded=false;lyricsRequested="";render();}
  else if(IslandListener.live!=null)IslandListener.live.returnToRest();
  else idle();
 }};
 final Runnable progressTick=new Runnable(){public void run(){
  if(!window.attached()||current==null||current.media==null||!expanded)return;
  updateProgress();handler.postDelayed(this,500);
 }};

 IslandOverlay(Context context){
  c=context;aboveStatus=context instanceof IslandAccess;wm=(WindowManager)c.getSystemService(Context.WINDOW_SERVICE);
  lastRotation=rotation();
  handler.postDelayed(watchdog,3000);
  try{callbacks=new ComponentCallbacks(){public void onConfigurationChanged(Configuration n){handler.post(new Runnable(){public void run(){onRotated();}});}public void onLowMemory(){}};c.registerComponentCallbacks(callbacks);}catch(Exception ignored){ErrorLog.w(ignored);callbacks=null;}
  IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_USER_PRESENT);filter.addAction(Intent.ACTION_USER_UNLOCKED);
  try{if(Build.VERSION.SDK_INT>=33)c.registerReceiver(screenEvents,filter,Context.RECEIVER_NOT_EXPORTED);else c.registerReceiver(screenEvents,filter);}catch(Exception ignored){ErrorLog.w(ignored);}
  try{
   displayListener=new DisplayManager.DisplayListener(){
    public void onDisplayAdded(int id){}
    public void onDisplayRemoved(int id){}
    public void onDisplayChanged(int id){if(id!=Display.DEFAULT_DISPLAY)return;int r=rotation();if(r!=lastRotation){lastRotation=r;handler.post(new Runnable(){public void run(){onRotated();}});}}
   };
   ((DisplayManager)c.getSystemService(Context.DISPLAY_SERVICE)).registerDisplayListener(displayListener,handler);
  }catch(Exception ignored){ErrorLog.w(ignored);displayListener=null;}
 }
 final BroadcastReceiver screenEvents=new BroadcastReceiver(){public void onReceive(Context ctx,Intent intent){
  String action=intent.getAction();if(action==null)return;
  if(Intent.ACTION_SCREEN_OFF.equals(action))hide();
  else{redraw();}
 }};
 /** Brings the island back when Android dropped its window or an update threw: checks every few seconds while it should be visible. */
 final Runnable watchdog=new Runnable(){public void run(){
  try{checkAlive();}catch(Exception ignored){ErrorLog.w(ignored);}
  handler.postDelayed(this,3000);
 }};
 final Runnable settleRedraw=new Runnable(){public void run(){if(replyMode)return;redraw();}};
 /** Android removed the window behind our back (not through hide()): rebuild it right away instead of waiting for the next check. */
 final Runnable recover=new Runnable(){public void run(){
  if(replyMode||view==null||!window.attached())return;
  if(!view.isAttachedToWindow()){resetWindow();redraw();}
 }};
 final View.OnAttachStateChangeListener detachWatch=new View.OnAttachStateChangeListener(){
  public void onViewAttachedToWindow(View v){}
  public void onViewDetachedFromWindow(View v){if(v==view&&window.attached()){handler.removeCallbacks(recover);handler.postDelayed(recover,250);}}
 };
 void checkAlive(){
  renderRetries=0;
  if(replyMode)return;
  boolean want=allowed()&&(current!=null||panel!=null||true);
  if(!want){deadChecks=0;return;}
  if(!window.attached()||view==null){deadChecks=0;fingerprint="";redraw();return;}
  if(!view.isAttachedToWindow()){deadChecks=0;resetWindow();redraw();return;}
  deadChecks=0;
  // Side, position or width no longer matches what layout() asks for (typically after a rotation): put it right.
  if(params!=null&&!spring.running()&&orientationSettled()){
   WindowManager.LayoutParams w=layout();
   if(w.gravity!=params.gravity||w.x!=params.x||w.y!=params.y||(w.width>0&&w.width!=params.width))redraw();
  }
 }
 /** Drops every reference to the current window so the next render builds and attaches a fresh one. */
 void resetWindow(){
  spring.cancel();handler.removeCallbacks(recover);
  try{if(view!=null){view.removeOnAttachStateChangeListener(detachWatch);wm.removeViewImmediate(view);}}catch(Exception ignored){ErrorLog.w(ignored);}
  window.reset();removeCover();
  view=null;progress=null;progressLabel=null;lyricsFlow=null;lyricsSource=null;progressTotal=null;
  fingerprint="";curW=0;curH=0;curGravity=-1;bgShape=null;cornerNow=0;revealKey="";
 }
 void retryRender(){
  if(renderRetries>=6)return;
  renderRetries++;
  handler.postDelayed(new Runnable(){public void run(){if(replyMode)return;redraw();}},500L*renderRetries);
 }
 void onRotated(){
  settleTries=0;lastRotation=rotation();fingerprint="";redraw();
  // Android needs a moment to finish the rotation; redraw again once it has, so the pill ends on the right side.
  handler.removeCallbacks(settleRedraw);handler.postDelayed(settleRedraw,220);handler.postDelayed(settleRedraw,650);
 }
 /** True when the screen size and the reported rotation agree. They briefly disagree in the middle of a rotation. */
 boolean orientationSettled(){
  if(c.getResources().getConfiguration().smallestScreenWidthDp>=600)return true;
  DisplayMetrics m=real();boolean land=m.widthPixels>m.heightPixels;int r=rotation();
  return land==(r==Surface.ROTATION_90||r==Surface.ROTATION_270);
 }
 DisplayMetrics real(){
  DisplayMetrics m=new DisplayMetrics();
  try{((DisplayManager)c.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(m);}catch(Exception e){ErrorLog.w(e);m=c.getResources().getDisplayMetrics();}
  return m;
 }
 int rotation(){try{return ((DisplayManager)c.getSystemService(Context.DISPLAY_SERVICE)).getDisplay(Display.DEFAULT_DISPLAY).getRotation();}catch(Exception e){ErrorLog.w(e);return Surface.ROTATION_0;}}
 boolean landscape(){DisplayMetrics m=real();return m.widthPixels>m.heightPixels;}
 boolean locked(){KeyguardManager k=(KeyguardManager)c.getSystemService(Context.KEYGUARD_SERVICE);return k!=null&&k.isKeyguardLocked();}
 boolean showContent(){return Prefs.flag(c,"content",true)&&(!locked()||Prefs.flag(c,"lock_content",false));}
 int dp(float x){return(int)(x*c.getResources().getDisplayMetrics().density+.5f);}
 GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),0xff292b2e);return d;}
 boolean allowed(){return !IslandListener.suspended()&&Prefs.enabled(c)&&(aboveStatus||Settings.canDrawOverlays(c))&&(!Prefs.flag(c,"landscape",false)||!landscape())&&((PowerManager)c.getSystemService(Context.POWER_SERVICE)).isInteractive();}
 /** True when a notice posted now would really be drawn (so removing the native copy loses nothing). */
 boolean canShow(){return !replyMode&&allowed()&&!locked();}
 TextView text(String s,int size,int color){TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setMaxLines(1);t.setEllipsize(android.text.TextUtils.TruncateAt.END);return t;}
 void hide(){
  panel=null;handler.removeCallbacks(collapse);handler.removeCallbacks(progressTick);spring.cancel();curW=0;curH=0;curGravity=-1;bgShape=null;cornerNow=0;revealKey="";
  stopReply(false);removeCover();
  try{window.hide(new Runnable(){public void run(){if(view!=null)wm.removeView(view);}});}catch(Exception ignored){ErrorLog.w(ignored);}
 fingerprint="";view=null;progress=null;progressLabel=null;lyricsFlow=null;retainedLyricsFlow=null;lyricsSource=null;progressTotal=null;
 }
 void destroy(){
  try{c.unregisterReceiver(screenEvents);}catch(Exception ignored){ErrorLog.w(ignored);}
  try{if(callbacks!=null)c.unregisterComponentCallbacks(callbacks);}catch(Exception ignored){ErrorLog.w(ignored);}
  callbacks=null;
  try{if(displayListener!=null)((DisplayManager)c.getSystemService(Context.DISPLAY_SERVICE)).unregisterDisplayListener(displayListener);}catch(Exception ignored){ErrorLog.w(ignored);}
  displayListener=null;hide();lyricsClient.close();current=null;handler.removeCallbacksAndMessages(null);}
 void idle(){if(panel!=null)return;stopReply(false);current=null;expanded=false;handler.removeCallbacks(collapse);if(!allowed()||!true){hide();return;}render();}
 boolean show(Notice n,int total){if(replyMode)return false;if(panel!=null){if(n.kind!=Notice.K_CALL_IN)return false;panel=null;}current=n;count=total;expanded=n.isMedia()||n.kind!=0?false:Prefs.flag(c,"expand",false);return render();}
 void showExpanded(Notice n){if(replyMode)return;panel=null;current=n;count=0;expanded=true;render();}
 void showMusic(Notice n,boolean expand){if(replyMode||panel!=null)return;current=n;count=0;expanded=expand;render();}
 boolean render(){
  try{return renderNow();}
  catch(RuntimeException e){ErrorLog.w(e);try{resetWindow();}catch(RuntimeException ignored){ErrorLog.w(ignored);}retryRender();return false;}
 }
 boolean renderNow(){
  handler.removeCallbacks(collapse);handler.removeCallbacks(progressTick);
  if(!allowed()){hide();return false;}
  if(!orientationSettled()&&settleTries<8){settleTries++;handler.removeCallbacks(settleRedraw);handler.postDelayed(settleRedraw,120);return window.attached();}
  settleTries=0;
  if(current==null&&!true){hide();return false;}
  if(view==null){view=new PanelRoot(c);view.setOrientation(1);view.setGravity(Gravity.TOP);view.setElevation(dp(8));view.addOnAttachStateChangeListener(detachWatch);}
  WindowManager.LayoutParams layout=layout();
  if(layout.width<=0){hide();return false;}
  String key=stateKey();
  boolean rebuilt=false;
  if(!fingerprint.equals(key)){buildContent();fingerprint=key;rebuilt=true;}
  if(expanded&&(current!=null||panel!=null)&&!replyMode&&!(panel==null&&landscape()&&current.isMedia())){view.measure(View.MeasureSpec.makeMeasureSpec(layout.width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(real().heightPixels,View.MeasureSpec.AT_MOST));layout.height=view.getMeasuredHeight();}
  boolean coverAdded=syncCover();
  try{
   if(coverAdded&&window.attached()){spring.cancel();wm.removeViewImmediate(view);wm.addView(view,layout);setCur(layout);toW=layout.width;toH=layout.height;toX=layout.x;toY=layout.y;}
   else window.show(new Runnable(){public void run(){attachAnimated(layout);}},new Runnable(){public void run(){animateTo(layout);}});
   params=layout;
  }catch(Exception e){ErrorLog.w(e);resetWindow();retryRender();return false;}
  renderRetries=0;
  if(rebuilt)revealIfChanged();
  if(current!=null&&!expanded&&!current.rests()&&current.kind!=Notice.K_CALL_IN)handler.postDelayed(collapse,current.holdMs>0?current.holdMs:Prefs.value(c,"duration",8)*1000L);
  if(expanded&&current!=null&&current.media!=null){updateProgress();handler.postDelayed(progressTick,500);}
  return true;
 }
 String stateKey(){
  Notice n=current;
  return (n==null?"idle":n.key+"|"+n.title+"|"+n.text+"|"+n.fullText+"|"+n.duration+"|"+n.artKey+"|"+n.playing()+"|"+n.kind+"|"+n.on+"|"+n.level+"|"+(n.media==null?"none":n.media.getSessionToken().hashCode()))+"|"+(n==null?"":n.chrono+"|"+n.clockText+"|"+n.chronoBase+"|"+n.ownPaused+"|"+n.rec)+"|"+panel+"|"+("weather".equals(panel)?Weather.fetched+"|"+Weather.loading+"|"+Weather.failed:"")+"|"+(n==null||n.media==null?"":MediaKeys.signature(n.media.getPlaybackState())+"|"+modeOf(":s")+"|"+modeOf(":r")+"|"+ratedHeart())+"|"+expanded+"|"+replyMode+"|"+count+"|"+Prefs.flag(c,"content",true)+"|"+Prefs.flag(c,"music",true)+"|"+Prefs.flag(c,"cover_bg",true)+"|"+landscape()+"|"+rotation()+"|"+locked()+"|"+Prefs.flag(c,"lock_content",false);
 }
WindowManager.LayoutParams layout(){
  DisplayMetrics dm=real();int screenWidth=dm.widthPixels,screenHeight=dm.heightPixels;boolean land=screenWidth>screenHeight;
  int type=aboveStatus?WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY:WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
  int flags=WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
  if(land)flags|=WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
  if(expanded&&!inputPanel())flags|=WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
  if(!replyMode&&!inputPanel())flags|=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
  int width,height,gravity,x,y;
  if(land){
   // Landscape: vertical pill on the camera side (left for ROTATION_90, right for ROTATION_270), centred vertically.
   boolean leftSide=rotation()!=Surface.ROTATION_270;
   int edge=dp(Prefs.value(c,"land_edge",6)),voff=dp(Prefs.value(c,"land_offset",0));
   gravity=(leftSide?Gravity.LEFT:Gravity.RIGHT)|Gravity.CENTER_VERTICAL;
   if(expanded&&panel==null&&current!=null&&current.isMedia()&&!replyMode){width=Math.max(0,Math.min(dp(180),screenWidth-dp(16)));height=Math.min(dp(344),screenHeight-dp(16));x=edge;y=voff;}
   else if(expanded){width=Math.max(0,Math.min(dp(380),screenWidth-dp(16)));height=WindowManager.LayoutParams.WRAP_CONTENT;x=dp(4);y=voff;}
   else{width=dp(Prefs.value(c,"land_thickness",24));height=dp(Prefs.value(c,"land_length",92));x=edge;y=voff;}
  }else{
   int compact=current==null?Prefs.value(c,"width",136):current.isMedia()?Prefs.value(c,"music_width",152):Prefs.value(c,"notif_width",220);   // battery, charging, ringer, focus, torch, bluetooth, timer and call pills share the notification width
   int center=screenWidth/2+dp(Prefs.value(c,"offset",0));
   if(expanded){width=Math.max(0,screenWidth-dp(12));center=screenWidth/2;}
   else if(coverWanted()){width=Math.min(dp(compact),screenWidth-dp(20));center=screenWidth/2;}
   else if(Prefs.flag(c,"safe_status",true)){
    LayoutGeometry.Result fit=LayoutGeometry.fit(screenWidth,dp(compact),center,dp(Prefs.value(c,"guard_left",60)),dp(Prefs.value(c,"guard_right",104)));
    width=fit.width;center=fit.center;
    if(width<dp(64)){width=Math.min(dp(compact),screenWidth-dp(20));center=screenWidth/2+dp(Prefs.value(c,"offset",0));center=Math.max(width/2+dp(4),Math.min(screenWidth-width/2-dp(4),center));}
   }else{width=Math.min(dp(compact),screenWidth-dp(20));center=Math.max(width/2+dp(4),Math.min(screenWidth-width/2-dp(4),center));}
   height=expanded?WindowManager.LayoutParams.WRAP_CONTENT:dp(Prefs.value(c,"height",32));
   gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;x=center-screenWidth/2;y=expanded?(aboveStatus?dp(7):0):dp(Prefs.value(c,"top",4));
  }
  if(faceCard()){int side=Math.min(dp(FACE_SIDE),screenWidth-dp(24));width=side;height=side;gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;x=dp(Prefs.value(c,"offset",0));y=dp(Prefs.value(c,"top",4));}
  WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,height,type,flags,PixelFormat.TRANSLUCENT);
  p.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;
  p.gravity=gravity;p.x=x;p.y=y;
  if(Build.VERSION.SDK_INT>=30){p.setFitInsetsTypes(0);p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;}
  else p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
  p.setTitle("Fyland");return p;
 }
 /** Portrait notice mode: a black strip over the system status bar. Real SystemUI icons cannot be moved by an app,
  *  so they are covered, and only a clock (left) and battery (right) are redrawn beside the island. Not touchable. */
 boolean coverWanted(){return aboveStatus&&!expanded&&current!=null&&!current.rests()&&!landscape()&&!faceCard()&&Prefs.flag(c,"cover_status",true);}
 /** Face unlock in portrait: the island grows into a black rounded square that plays the Face ID animation (res/raw/face_id.json). */
 static final int FACE_SIDE=124,FACE_RADIUS=28;
 boolean faceCard(){return !expanded&&panel==null&&current!=null&&current.kind==Notice.K_FACE&&!landscape();}
 String batteryText(){
  try{
   Intent b=c.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
   if(b!=null){int level=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,100),plug=b.getIntExtra(BatteryManager.EXTRA_PLUGGED,0);
    if(level>=0)return (plug!=0?"\u26A1 ":"")+(level*100/Math.max(1,scale))+"%";}
  }catch(Exception ignored){ErrorLog.w(ignored);}
  return "";
 }
 boolean syncCover(){
  if(!coverWanted()){removeCover();return false;}
  DisplayMetrics dm=real();int screenWidth=dm.widthPixels;
  int h=Math.max(statusHeight(),dp(Prefs.value(c,"top",4))+dp(Prefs.value(c,"height",32))+dp(3));
  if(coverView==null){
   LinearLayout strip=new LinearLayout(c);strip.setOrientation(0);strip.setGravity(Gravity.CENTER_VERTICAL);
   GradientDrawable g=new GradientDrawable();g.setColor(Color.BLACK);g.setCornerRadii(new float[]{0,0,0,0,dp(16),dp(16),dp(16),dp(16)});strip.setBackground(g);
   int pad=dp(Prefs.value(c,"cover_pad",18));strip.setPadding(pad,0,pad,0);
   TextClock clock=new TextClock(c);clock.setFormat12Hour("h:mm");clock.setFormat24Hour("HH:mm");clock.setTextColor(Color.WHITE);clock.setTextSize(13);clock.setTypeface(null,Typeface.BOLD);clock.setSingleLine(true);
   strip.addView(clock,new LinearLayout.LayoutParams(0,-2,1));
   strip.addView(new View(c),new LinearLayout.LayoutParams(Math.min(dp(220),screenWidth-dp(20)),1));
   coverBattery=new TextView(c);coverBattery.setTextColor(Color.WHITE);coverBattery.setTextSize(12);coverBattery.setTypeface(null,Typeface.BOLD);coverBattery.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);coverBattery.setSingleLine(true);
   strip.addView(coverBattery,new LinearLayout.LayoutParams(0,-2,1));
   coverView=strip;coverAttached=false;
  }
  coverBattery.setText(batteryText());
  int type=aboveStatus?WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY:WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
  WindowManager.LayoutParams p=new WindowManager.LayoutParams(screenWidth,h,type,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,PixelFormat.TRANSLUCENT);
  p.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;p.x=0;p.y=0;
  if(Build.VERSION.SDK_INT>=30){p.setFitInsetsTypes(0);p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;}
  else p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
  p.setTitle("Fyland status");
  try{
   if(coverAttached){wm.updateViewLayout(coverView,p);return false;}
   wm.addView(coverView,p);coverAttached=true;return true;
  }catch(Exception e){ErrorLog.w(e);removeCover();return false;}
 }
 void removeCover(){
  if(coverView!=null&&coverAttached){try{wm.removeView(coverView);}catch(Exception ignored){ErrorLog.w(ignored);}}
  coverView=null;coverBattery=null;coverAttached=false;
 }
 /** Landscape compact pill: icon on top, wave / count below, on the camera side of the screen. */
 void buildLandscapePill(Notice n){
  view.setGravity(Gravity.CENTER_HORIZONTAL);view.setPadding(dp(3),dp(8),dp(3),dp(8));
  int thick=dp(Prefs.value(c,"land_thickness",24));int s=Math.max(dp(12),Math.min(dp(22),thick-dp(6)));
  if(n!=null&&n.isMedia()){s=Math.max(dp(12),thick-dp(6));view.setPadding(dp(3),dp(8),dp(3),dp(3));}
  if(n==null){
   view.setGravity(Gravity.CENTER);
   TextView dot=text("●",9,0xff45494c);dot.setGravity(Gravity.CENTER);view.addView(dot,new LinearLayout.LayoutParams(-2,-2));
  }else{
   Drawable face=n.kind!=0?null:face(n);
   if(n.isMedia()){
    // Portrait island turned 90 degrees: wave on top, cover art at the bottom, camera gap between them.
    Waveform wave=new Waveform(c,n);wave.vertical=true;
    view.addView(wave,new LinearLayout.LayoutParams(Math.min(dp(18),s),dp(18)));
    view.addView(new View(c),new LinearLayout.LayoutParams(1,0,1f));
    if(n.kind!=0){view.addView(glyphFor(n),new LinearLayout.LayoutParams(s,s));}
    else if(face!=null){ImageView icon=new ImageView(c);icon.setScaleType(ImageView.ScaleType.FIT_XY);icon.setImageDrawable(face);icon.setRotation(-90f);view.addView(icon,new LinearLayout.LayoutParams(s,s));}
    else{TextView dot=text("●",11,0xffa4c397);dot.setGravity(Gravity.CENTER);view.addView(dot,new LinearLayout.LayoutParams(s,s));}
   }else{
   // The portrait pill turned 90 degrees, like the music pill: count on top, profile picture / icon flush with the curved bottom.
   int sm=Math.max(dp(12),thick-dp(6));view.setPadding(dp(3),dp(8),dp(3),dp(3));
   if(count>1&&n.kind==0){TextView total=text(String.valueOf(count),11,0xffa4c397);total.setGravity(Gravity.CENTER);view.addView(total,new LinearLayout.LayoutParams(-2,-2));}
   view.addView(new View(c),new LinearLayout.LayoutParams(1,0,1f));
   if(n.kind!=0){view.addView(glyphFor(n),new LinearLayout.LayoutParams(sm,sm));}
   else if(face!=null){ImageView icon=new ImageView(c);icon.setScaleType(ImageView.ScaleType.FIT_XY);icon.setImageDrawable(face);icon.setRotation(-90f);view.addView(icon,new LinearLayout.LayoutParams(sm,sm));}
   else{TextView dot=text("●",11,0xffa4c397);dot.setGravity(Gravity.CENTER);view.addView(dot,new LinearLayout.LayoutParams(sm,sm));}
   }
  }
  view.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(n==null){idleTap();return;}expanded=true;render();}});
  view.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){if(n==null){if(IslandListener.live!=null)IslandListener.live.latest();return true;}open();return true;}});
 }
 Drawable face(Notice n){Bitmap b=n==null?null:n.face();return b==null?null:new Art.Circle(b);}
 int statusHeight(){int id=c.getResources().getIdentifier("status_bar_height","dimen","android");return id>0?c.getResources().getDimensionPixelSize(id):dp(26);}
 void buildContent(){
  Notice n=current;if(lyricsFlow!=null)retainedLyricsFlow=lyricsFlow;view.removeAllViews();progress=null;progressLabel=null;progressTotal=null;lyricsFlow=null;lyricsSource=null;replyInput=null;replyStatus=null;
  boolean land=landscape();view.setGravity(Gravity.TOP);
  bgShape=shape(Color.BLACK,expanded?30:faceCard()?FACE_RADIUS:60);
  float cornerTarget=dp(expanded?30:faceCard()?FACE_RADIUS:60);
  if(window.attached()&&animOn()&&cornerNow>0)bgShape.setCornerRadius(cornerNow);else cornerNow=cornerTarget;
  Drawable backdrop=bgShape;
  if(n!=null&&n.isMedia()){Bitmap a0=n.face();accent=a0!=null?CoverBg.vivid(a0):0xffff7a2e;}
  if(expanded&&panel==null&&n!=null&&n.isMedia()&&Prefs.flag(c,"cover_bg",true)){Bitmap art=n.face();if(art!=null)backdrop=new android.graphics.drawable.LayerDrawable(new Drawable[]{bgShape,new CoverBg(art,bgShape)});}
  view.setBackground(backdrop);
  int padR=dp(expanded?16:10),padL=padR,padTop=expanded?(land||aboveStatus?dp(14):statusHeight()+dp(4)):dp(3);
  if(land&&expanded){int cam=dp(Prefs.value(c,"land_thickness",24)+Prefs.value(c,"land_edge",6));if(rotation()!=Surface.ROTATION_270)padL+=cam;else padR+=cam;}
  if(!expanded&&!land&&n!=null)padL=dp(3);
  view.setPadding(padL,padTop,padR,dp(expanded?8:3));
  if(faceCard())view.setPadding(0,0,0,0);
  view.setOnClickListener(expanded&&!replyMode?new View.OnClickListener(){public void onClick(View v){collapsePanel();}}:null);view.setOnLongClickListener(null);view.setOnTouchListener(null);
  if(panel!=null){Panels.build(this);return;}
  view.setOnTouchListener(swipeListener());
  if(land&&!expanded){buildLandscapePill(n);return;}
  if(n!=null&&n.kind!=0&&buildEvent(n,land))return;
  if(n!=null&&expanded&&!n.isMedia()&&n.kind==0&&buildMessageCard(n))return;
  LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);if(expanded&&!land)row.setPadding(dp(8),0,dp(12),0);view.addView(row,new LinearLayout.LayoutParams(-1,-2));
  if(n==null){
   TextView dot=text("●",9,0xff45494c);dot.setGravity(Gravity.RIGHT);row.addView(dot,new LinearLayout.LayoutParams(-1,-2));
   view.setOnClickListener(new View.OnClickListener(){public void onClick(View v){idleTap();}});
   view.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){if(IslandListener.live!=null)IslandListener.live.latest();return true;}});return;
  }
  Drawable face=face(n);
  if(face!=null){ImageView icon=new ImageView(c);icon.setScaleType(ImageView.ScaleType.FIT_XY);icon.setImageDrawable(face);int fs=expanded?dp(28):Math.max(dp(18),dp(Prefs.value(c,"height",32))-dp(6));LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(fs,fs);ip.rightMargin=dp(expanded?10:8);row.addView(icon,ip);}
  else {TextView dot=text("●",12,0xffa4c397);row.addView(dot,new LinearLayout.LayoutParams(dp(22),-2));}
  LinearLayout words=new LinearLayout(c);words.setOrientation(1);
  if(!expanded&&!n.isMedia()&&n.kind==0){
   // Collapsed message: sender in bold, then the message text, on one line.
   TextView line=text("",12,Color.WHITE);line.setSingleLine(true);line.setEllipsize(android.text.TextUtils.TruncateAt.END);
   android.text.SpannableStringBuilder sb=new android.text.SpannableStringBuilder();
   if(showContent()){
    String who=n.title==null?"":n.title,msg=n.text==null?"":n.text.replace('\n',' ');
    sb.append(who);sb.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),0,sb.length(),0);
    if(!msg.isEmpty()){int from=sb.length();sb.append("  ").append(msg);sb.setSpan(new android.text.style.ForegroundColorSpan(0xffc9cbce),from,sb.length(),0);}
   }else sb.append(n.app);
   line.setText(sb);words.addView(line);
  }else if(expanded||!n.isMedia()){
   TextView title=text(showContent()?n.title:n.app,expanded?14:12,expanded&&n.isMedia()&&n.media!=null?accent:Color.WHITE);title.setTypeface(null,Typeface.BOLD);words.addView(title);
   if(expanded)words.addView(text(n.isMedia()?n.text:n.app,11,0xffb6b8bb));
  }
  row.addView(words,new LinearLayout.LayoutParams(0,expanded?-2:dp(20),1));
  if(!expanded&&!n.isMedia()&&n.kind==0&&showContent()){
   Bitmap pic=n.picture(c);
   if(pic!=null){int ps=Math.max(dp(18),dp(Prefs.value(c,"height",32))-dp(6));ImageView thumb=pictureView(pic,n.sticker,true);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(ps,ps);tp.leftMargin=dp(8);row.addView(thumb,tp);}
  }
  if(n.isMedia()){Waveform wave=new Waveform(c,n);LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(dp(22),dp(20));wp.leftMargin=dp(8);row.addView(wave,wp);}
  else if(count>1){TextView total=text(String.valueOf(count),11,0xffa4c397);total.setPadding(dp(8),0,0,0);row.addView(total);}
  row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(replyMode){stopReply(true);return;}if(expanded&&n.isMedia()&&n.media!=null){open();return;}expanded=!expanded;if(!expanded)lyricsRequested="";render();}});view.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){open();return true;}});
  if(!expanded)return;
  if(n.isMedia()&&n.media!=null&&Prefs.flag(c,"music",true)){buildMedia();return;}
  if(!n.isMedia()&&n.kind==0&&showContent()){
   Bitmap pic=n.picture(c);
   if(pic!=null){LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(n.sticker?110:150));pp.topMargin=dp(10);view.addView(pictureView(pic,n.sticker,false),pp);}
  }
  TextView body=text(showContent()?(n.isMedia()?n.text:n.fullText):(locked()?L.s(R.string.s97):L.s(R.string.s98)),14,0xffeeeeee);
  body.setSingleLine(false);body.setMaxLines(Integer.MAX_VALUE);body.setEllipsize(null);body.setPadding(0,dp(12),0,dp(12));
  ScrollView message=new ScrollView(c);message.setFillViewport(false);message.addView(body);
  int maxBody=dp(n.isMedia()?48:replyMode?60:140);body.measure(View.MeasureSpec.makeMeasureSpec(Math.min(dp(316),real().widthPixels-dp(56)),View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
  view.addView(message,new LinearLayout.LayoutParams(-1,Math.min(maxBody,body.getMeasuredHeight())));
  buildNotificationActions();
  LinearLayout controls=new LinearLayout(c);view.addView(controls);
  addChip(controls,L.s(R.string.s99),new Runnable(){public void run(){open();}});
  if(!n.isMedia())addChip(controls,L.s(R.string.s100),new Runnable(){public void run(){if(IslandListener.live!=null)IslandListener.live.navigate(1);}});
  addChip(controls,L.s(R.string.s101),new Runnable(){public void run(){stopReply(false);if(IslandListener.live!=null)IslandListener.live.dismissCurrent();else idle();}});
 }
 /** Expanded message card (iOS-style): app header + time, avatar with sender and text, photo/sticker, outlined Reply / Mark as read pills. */
 boolean buildMessageCard(final Notice n){
  final int green=0xff34c759,gray=0xff9a9da1;
  view.setOnClickListener(replyMode?null:new View.OnClickListener(){public void onClick(View v){collapsePanel();}});
  // header: app icon, app name, time, next / close
  LinearLayout head=new LinearLayout(c);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(dp(2),0,dp(2),dp(8));
  if(n.icon!=null){ImageView ai=new ImageView(c);ai.setImageDrawable(n.icon);ai.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(16),dp(16));ap.rightMargin=dp(6);head.addView(ai,ap);}
  TextView app=text(n.app==null?"":n.app,12,gray);head.addView(app,new LinearLayout.LayoutParams(0,-2,1));
  TextView when=text(android.text.format.DateFormat.getTimeFormat(c).format(new java.util.Date(n.posted>0?n.posted:System.currentTimeMillis())),11,gray);head.addView(when);
  if(count>1){TextView next=text("\u203a",20,gray);next.setPadding(dp(12),0,dp(4),0);next.setContentDescription(L.s(R.string.s100));next.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(IslandListener.live!=null)IslandListener.live.navigate(1);}});head.addView(next);}
  TextView close=text("\u2715",13,gray);close.setPadding(dp(12),dp(4),dp(4),dp(4));close.setContentDescription(L.s(R.string.s101));
  close.setOnClickListener(new View.OnClickListener(){public void onClick(View v){stopReply(false);if(IslandListener.live!=null)IslandListener.live.dismissCurrent();else idle();}});head.addView(close);
  view.addView(head,new LinearLayout.LayoutParams(-1,-2));
  // body: avatar + sender + message (tap = open the chat)
  LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
  Drawable face=face(n);
  if(face!=null){ImageView av=new ImageView(c);av.setScaleType(ImageView.ScaleType.FIT_XY);av.setImageDrawable(face);LinearLayout.LayoutParams avp=new LinearLayout.LayoutParams(dp(40),dp(40));avp.rightMargin=dp(10);row.addView(av,avp);}
  LinearLayout words=new LinearLayout(c);words.setOrientation(1);
  TextView who=text(showContent()?n.title:n.app,15,Color.WHITE);who.setTypeface(null,Typeface.BOLD);words.addView(who);
  TextView msg=text(showContent()?n.fullText:(locked()?L.s(R.string.s97):L.s(R.string.s98)),13,0xffc9cbce);
  msg.setSingleLine(false);msg.setMaxLines(replyMode?2:4);msg.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(msg);
  row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
  row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(replyMode){stopReply(true);return;}open();}});
  view.addView(row,new LinearLayout.LayoutParams(-1,-2));
  if(showContent()){
   Bitmap pic=n.picture(c);
   if(pic!=null){LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(n.sticker?110:150));pp.topMargin=dp(10);view.addView(pictureView(pic,n.sticker,false),pp);}
  }
  if(locked())return true; // no reply / quick actions while the phone is locked
  int replyIndex=n.notification==null||n.notification.actions==null?-1:n.replyIndex();
  if(replyMode&&replyIndex>=0){buildReplyEditor();return true;}
  LinearLayout pills=new LinearLayout(c);pills.setPadding(0,dp(12),0,dp(4));
  if(replyIndex>=0)addPill(pills,L.s(R.string.s108),green,new Runnable(){public void run(){replyMode=true;handler.removeCallbacks(collapse);render();handler.postDelayed(new Runnable(){public void run(){if(replyInput!=null&&replyMode){replyInput.requestFocus();((android.view.inputmethod.InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(replyInput,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);}}},180);}});
  if(n.notification!=null&&n.notification.actions!=null){
   int pick=-1;
   for(int i=0;i<n.notification.actions.length;i++){Notification.Action a=n.notification.actions[i];if(a.actionIntent==null||a.getRemoteInputs()!=null)continue;String t=a.title==null?"":a.title.toString().toLowerCase(java.util.Locale.ROOT);if(t.contains("read")||t.contains("baca")){pick=i;break;}}
   if(pick<0)for(int i=0;i<n.notification.actions.length;i++){Notification.Action a=n.notification.actions[i];if(a.actionIntent!=null&&a.getRemoteInputs()==null){pick=i;break;}}
   if(pick>=0){final int index=pick;CharSequence t=n.notification.actions[pick].title;
    addPill(pills,t==null?L.s(R.string.s109):t.toString(),green,new Runnable(){public void run(){
     try{if(current!=null&&current.notification!=null&&current.notification.actions!=null&&index<current.notification.actions.length){send(current.notification.actions[index].actionIntent);if(IslandListener.live!=null)IslandListener.live.dismissCurrent();else idle();}}
     catch(Exception e){ErrorLog.w(e);Toast.makeText(c,L.s(R.string.s110),Toast.LENGTH_SHORT).show();}
    }});}
  }
  if(pills.getChildCount()==0)addPill(pills,L.s(R.string.s99),green,new Runnable(){public void run(){open();}});
  view.addView(pills,new LinearLayout.LayoutParams(-1,-2));
  return true;
 }
 /** Transparent pill with a thin colored outline (iOS notification button). */
 void addPill(LinearLayout row,String title,int color,final Runnable action){
  TextView b=text(title,13,color);b.setGravity(Gravity.CENTER);b.setPadding(dp(16),dp(7),dp(16),dp(7));
  GradientDrawable d=new GradientDrawable();d.setColor(Color.TRANSPARENT);d.setCornerRadius(dp(40));d.setStroke(dp(1),color);b.setBackground(d);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=dp(8);row.addView(b,lp);
  b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){action.run();}});
 }
 /** A picture or sticker from a message: photos are cropped with rounded corners, stickers are shown whole. */
 ImageView pictureView(Bitmap bitmap,boolean sticker,final boolean small){
  ImageView v=new ImageView(c);v.setImageBitmap(bitmap);v.setScaleType(sticker?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER_CROP);
  if(!sticker){final float r=dp(small?6:14);v.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View x,Outline out){out.setRoundRect(0,0,x.getWidth(),x.getHeight(),r);}});v.setClipToOutline(true);}
  return v;
 }
 void buildMedia(){
  buildLyrics();
  progress=new WavyProgress(c);progress.setAccent(accent);view.addView(progress,new LinearLayout.LayoutParams(-1,dp(26)));
  progress.listener=new WavyProgress.Seek(){public void seek(float fraction){
   try{if(current!=null&&current.media!=null&&current.duration>0)current.media.getTransportControls().seekTo((long)(fraction*current.duration));}catch(Exception e){ErrorLog.w(e);Toast.makeText(c,L.s(R.string.s102),Toast.LENGTH_SHORT).show();}
  }};
  LinearLayout times=new LinearLayout(c);times.setPadding(dp(4),0,dp(4),dp(8));
  progressLabel=text("",11,0xccffffff);times.addView(progressLabel,new LinearLayout.LayoutParams(0,-2,1));
  progressTotal=text("",11,0xccffffff);progressTotal.setGravity(Gravity.RIGHT);times.addView(progressTotal,new LinearLayout.LayoutParams(0,-2,1));
  view.addView(times,new LinearLayout.LayoutParams(-1,-2));
  buildControls();
 }
 /** Controls under the progress bar: like, shuffle, previous, play/pause, next, repeat, add. Landscape splits them over two rows. */
 void buildControls(){
  boolean land=landscape();
  if(land){
   LinearLayout main=controlRow();addControl(main,MediaButton.PREV,dp(44));addControl(main,MediaButton.PLAY,dp(44));addControl(main,MediaButton.NEXT,dp(44));
   LinearLayout extra=controlRow();addControl(extra,MediaButton.HEART,dp(34));addControl(extra,MediaButton.REPEAT,dp(34));
  }else{
   LinearLayout row=controlRow();int h=dp(48);
   addControl(row,MediaButton.HEART,h);addControl(row,MediaButton.PREV,h);addControl(row,MediaButton.PLAY,h);addControl(row,MediaButton.NEXT,h);addControl(row,MediaButton.REPEAT,h);
  }
 }
 LinearLayout controlRow(){LinearLayout r=new LinearLayout(c);r.setGravity(Gravity.CENTER_VERTICAL);view.addView(r,new LinearLayout.LayoutParams(-1,-2));return r;}
 int modeOf(String suffix){Integer v=current==null?null:modes.get(current.pkg+suffix);return v==null?0:v;}
 void setMode(String suffix,int value){if(current!=null)modes.put(current.pkg+suffix,value);}
 boolean ratedHeart(){
  try{MediaMetadata m=current.media.getMetadata();Rating r=m==null?null:m.getRating(MediaMetadata.METADATA_KEY_USER_RATING);return r!=null&&r.isRated()&&r.hasHeart();}catch(Exception e){ErrorLog.w(e);return false;}
 }
 void addControl(LinearLayout row,final int kind,int height){
  MediaController mc=current.media;PlaybackState state=mc.getPlaybackState();long caps=state==null?0:state.getActions();
  PlaybackState.CustomAction custom=kind==MediaButton.HEART?MediaKeys.find(state,MediaKeys.HEART):kind==MediaButton.SHUFFLE?MediaKeys.find(state,MediaKeys.SHUFFLE):kind==MediaButton.REPEAT?MediaKeys.find(state,MediaKeys.REPEAT):kind==MediaButton.ADD?MediaKeys.find(state,MediaKeys.ADD):null;
  boolean named=custom!=null&&MediaKeys.activeTitle(custom.getName()==null?null:custom.getName().toString());
  MediaButton button=new MediaButton(c,kind,current.playing());button.accent=accent;
  boolean supported=true,on=false;
  switch(kind){
   case MediaButton.PREV:supported=(caps&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0;break;
   case MediaButton.NEXT:supported=(caps&PlaybackState.ACTION_SKIP_TO_NEXT)!=0;break;
   case MediaButton.PLAY:supported=(caps&(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE))!=0;break;
   case MediaButton.HEART:supported=custom!=null||(caps&PlaybackState.ACTION_SET_RATING)!=0;on=named||ratedHeart();break;
   case MediaButton.ADD:supported=custom!=null;on=named;break;
   case MediaButton.SHUFFLE:on=named||modeOf(":s")!=0;break;
   case MediaButton.REPEAT:on=named||modeOf(":r")!=0;button.repeatOne=modeOf(":r")==1;break;
  }
  button.on=on;
  boolean side=kind==MediaButton.HEART||kind==MediaButton.SHUFFLE||kind==MediaButton.REPEAT||kind==MediaButton.ADD;
  boolean tryAnyway=kind==MediaButton.SHUFFLE||kind==MediaButton.REPEAT;
  if(tryAnyway&&custom==null&&(caps&(kind==MediaButton.SHUFFLE?MediaKeys.CAN_SHUFFLE:MediaKeys.CAN_REPEAT))==0){button.setAlpha(.45f);} // not advertised: still sent, many players accept it
  else{button.setAlpha(supported?1f:.35f);button.setEnabled(supported);}
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,height,1);lp.setMargins(dp(side?0:2),dp(2),dp(side?0:2),dp(2));row.addView(button,lp);
  button.setOnClickListener(new View.OnClickListener(){public void onClick(View v){control(kind);}});
 }
 void control(int kind){
  try{
   if(current==null||current.media==null)return;
   MediaController.TransportControls t=current.media.getTransportControls();PlaybackState state=current.media.getPlaybackState();
   switch(kind){
    case MediaButton.PREV:t.skipToPrevious();break;
    case MediaButton.NEXT:t.skipToNext();break;
    case MediaButton.PLAY:if(current.playing())t.pause();else t.play();break;
    case MediaButton.HEART:{
     PlaybackState.CustomAction a=MediaKeys.find(state,MediaKeys.HEART);
     if(a!=null)t.sendCustomAction(a,a.getExtras());else t.setRating(Rating.newHeartRating(!ratedHeart()));
     break;
    }
    case MediaButton.ADD:{
     PlaybackState.CustomAction a=MediaKeys.find(state,MediaKeys.ADD);
     if(a!=null)t.sendCustomAction(a,a.getExtras());
     break;
    }
    case MediaButton.SHUFFLE:{
     boolean next=modeOf(":s")==0;setMode(":s",next?1:0);
     PlaybackState.CustomAction a=MediaKeys.find(state,MediaKeys.SHUFFLE);
     if(a!=null)t.sendCustomAction(a,a.getExtras());
     else{Bundle b=new Bundle();b.putInt(MediaKeys.COMPAT_SHUFFLE_ARG,next?1:0);t.sendCustomAction(MediaKeys.COMPAT_SHUFFLE,b);}
     break;
    }
    case MediaButton.REPEAT:{
     // Like a normal music player: off -> repeat all (queue starts over) -> repeat one (shows "1") -> off.
     int next=MediaKeys.nextRepeat(modeOf(":r"));setMode(":r",next);
     PlaybackState.CustomAction a=MediaKeys.find(state,MediaKeys.REPEAT);
     if(a!=null)t.sendCustomAction(a,a.getExtras());
     else{Bundle b=new Bundle();b.putInt(MediaKeys.COMPAT_REPEAT_ARG,next);t.sendCustomAction(MediaKeys.COMPAT_REPEAT,b);}
     break;
    }
   }
   handler.removeCallbacks(collapse);
   if(kind==MediaButton.SHUFFLE||kind==MediaButton.REPEAT){fingerprint="";render();}
  }catch(Exception e){ErrorLog.w(e);Toast.makeText(c,L.s(R.string.s103),Toast.LENGTH_SHORT).show();}
 }
 void updateProgress(){
  if(current==null||current.media==null||progress==null||progressLabel==null)return;
  MediaMetadata m=current.media.getMetadata();PlaybackState s=current.media.getPlaybackState();
  long duration=m==null?0:m.getLong(MediaMetadata.METADATA_KEY_DURATION),position=s==null?0:s.getPosition();
  if(s!=null&&current.playing()&&s.getLastPositionUpdateTime()>0)position+=(long)((SystemClock.elapsedRealtime()-s.getLastPositionUpdateTime())*s.getPlaybackSpeed());
  position=Math.max(0,duration>0?Math.min(position,duration):position);
  float rate=s==null?1f:s.getPlaybackSpeed();
  progress.setTimeline(position,duration,current.playing(),rate);
  updateLyrics(position);
  progressLabel.setText(clock(progress.dragging?(long)(progress.dragFraction*duration):position));
  if(progressTotal!=null)progressTotal.setText(duration>0?clock(duration):"LIVE");
 }
 static String clock(long ms){long seconds=ms/1000;return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
 void buildNotificationActions(){
  if(locked())return; // no reply / quick actions while the phone is locked
  if(current.notification==null||current.notification.actions==null){
   if("com.whatsapp".equals(current.pkg)||"com.whatsapp.w4b".equals(current.pkg))view.addView(text(L.s(R.string.s107),11,0xff93ab94));return;
  }
  int replyIndex=current.replyIndex();
  if(replyMode&&replyIndex>=0){buildReplyEditor();return;}
  LinearLayout actions=new LinearLayout(c);view.addView(actions);
  if(replyIndex>=0)addChip(actions,L.s(R.string.s108),new Runnable(){public void run(){replyMode=true;handler.removeCallbacks(collapse);render();handler.postDelayed(new Runnable(){public void run(){if(replyInput!=null&&replyMode){replyInput.requestFocus();((android.view.inputmethod.InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(replyInput,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);}}},180);}});
  for(int i=0;i<current.notification.actions.length&&actions.getChildCount()<3;i++){
   Notification.Action action=current.notification.actions[i];if(action.actionIntent==null||action.getRemoteInputs()!=null)continue;final int index=i;
   addChip(actions,action.title==null?L.s(R.string.s109):action.title.toString(),new Runnable(){public void run(){
    try{if(current!=null&&current.notification!=null&&current.notification.actions!=null&&index<current.notification.actions.length)send(current.notification.actions[index].actionIntent);}
    catch(Exception e){ErrorLog.w(e);Toast.makeText(c,L.s(R.string.s110),Toast.LENGTH_SHORT).show();}
   }});
  }
 }
 void buildReplyEditor(){
  replyInput=new EditText(c);replyInput.setTextColor(Color.WHITE);replyInput.setHintTextColor(0xff8e9094);replyInput.setTextSize(14);replyInput.setHint(L.s(R.string.s111));replyInput.setMinLines(1);replyInput.setMaxLines(3);replyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);replyInput.setText(replyDraft);replyInput.setSelection(replyInput.length());
  replyInput.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){replyDraft=s.toString();}public void afterTextChanged(android.text.Editable e){}});
  view.addView(replyInput,new LinearLayout.LayoutParams(-1,-2));
  replyStatus=text(L.s(R.string.s112),11,0xff93ab94);replyStatus.setSingleLine(false);view.addView(replyStatus);
  LinearLayout buttons=new LinearLayout(c);view.addView(buttons);addChip(buttons,L.s(R.string.s113),new Runnable(){public void run(){sendReply();}});addChip(buttons,L.s(R.string.s114),new Runnable(){public void run(){stopReply(true);}});
  replyInput.setOnKeyListener(new View.OnKeyListener(){public boolean onKey(View v,int key,KeyEvent event){if(key==KeyEvent.KEYCODE_BACK&&event.getAction()==KeyEvent.ACTION_UP){stopReply(true);return true;}return false;}});
 }
 void stopReply(boolean redraw){
  if(replyInput!=null)try{((android.view.inputmethod.InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(replyInput.getWindowToken(),0);}catch(Exception ignored){ErrorLog.w(ignored);}
  boolean was=replyMode;replyMode=false;replyDraft="";if(redraw&&was)render();
 }
 void sendReply(){
  if(current==null||!replyMode)return;String value=replyDraft.trim();
  if(value.isEmpty()){if(replyInput!=null)replyInput.setError(L.s(R.string.s115));return;}
  String error=current.sendReply(c,value);
  if(error==null){stopReply(false);Toast.makeText(c,L.s(R.string.s117),Toast.LENGTH_SHORT).show();render();}
  else if(replyStatus!=null)replyStatus.setText(L.s(R.string.s118)+" ("+error+")");
 }
 void buildLyrics(){
  String key=current.title+"|"+current.artist+"|"+current.album+"|"+current.duration;
  if(!key.equals(lyricsKey)){lyricsKey=key;lyricsDocument=null;lyricsMessage="";lyricsRequested="";lyricLine=-2;}
  lyricsSource=text(L.s(R.string.s119),10,0xccffffff);lyricsSource.setPadding(dp(4),dp(8),0,dp(4));view.addView(lyricsSource);
  lyricsFlow=lyricsDocument!=null&&retainedLyricsFlow!=null&&retainedLyricsFlow.document==lyricsDocument?retainedLyricsFlow:new LyricsFlowView(c);view.addView(lyricsFlow,landscape()?new LinearLayout.LayoutParams(-1,0,1f):new LinearLayout.LayoutParams(-1,dp(64)));
  if(!Prefs.flag(c,"lyrics_online",true)){lyricsFlow.setMessage(L.s(R.string.s121));return;}
  if(lyricsDocument!=null){if(lyricsFlow.document!=lyricsDocument)lyricsFlow.setDocument(lyricsDocument);updateLyrics(position());return;}
  if(lyricsRequested.equals(key)){lyricsFlow.setMessage(lyricsMessage.isEmpty()?L.s(R.string.s122):lyricsMessage);return;}
  lyricsRequested=key;lyricsMessage=L.s(R.string.s122);lyricsFlow.setMessage(lyricsMessage);
  lyricsClient.load(current.title,current.artist,current.album,current.duration,new LyricsClient.Callback(){public void complete(LyricsClient.Result result){
   if(current==null||!expanded||!current.isMedia()||!key.equals(current.title+"|"+current.artist+"|"+current.album+"|"+current.duration)||lyricsFlow==null||!Prefs.flag(c,"lyrics_online",true))return;
   lyricsDocument=result.document;lyricsMessage=result.message;lyricLine=-2;lyricsSource.setText(L.s(R.string.s119));
   if(lyricsDocument==null)lyricsFlow.setMessage(result.message);else{lyricsFlow.setDocument(lyricsDocument);updateLyrics(position());}
  }});
 }
 long position(){
  if(current==null||current.media==null)return 0;PlaybackState s=current.media.getPlaybackState();if(s==null)return 0;long p=s.getPosition();
  if(current.playing()&&s.getLastPositionUpdateTime()>0)p+=(long)((SystemClock.elapsedRealtime()-s.getLastPositionUpdateTime())*s.getPlaybackSpeed());return Math.max(0,p);
 }
 void updateLyrics(long position){
  if(lyricsFlow==null||lyricsDocument==null||!Prefs.flag(c,"lyrics_online",true)||current==null)return;
  PlaybackState state=current.media==null?null:current.media.getPlaybackState();
  lyricsFlow.update(position,current.playing(),state==null?0:state.getPlaybackSpeed(),current.duration);
 }
 void collapsePanel(){if(panel!=null){closePanel();return;}stopReply(false);expanded=false;lyricsRequested="";render();}

 // ---------- iPhone-style motion ----------
 boolean animOn(){return Prefs.flag(c,"anim",true);}
 void setCur(WindowManager.LayoutParams p){curW=p.width;curH=p.height;curX=p.x;curY=p.y;curGravity=p.gravity;}
 /** First appearance: the pill grows out of a smaller blob with a springy overshoot. */
 void attachAnimated(WindowManager.LayoutParams layout){
  if(animOn()&&layout.width>0&&layout.height>0){
   WindowManager.LayoutParams first=new WindowManager.LayoutParams();first.copyFrom(layout);
   first.width=Math.max(dp(30),(int)(layout.width*.5f));first.height=Math.max(dp(14),(int)(layout.height*.55f));
   wm.addView(view,first);setCur(first);
   if(bgShape!=null)cornerNow=bgShape.getCornerRadius();
   beginSpring(layout);
  }else{wm.addView(view,layout);setCur(layout);toW=layout.width;toH=layout.height;toX=layout.x;toY=layout.y;}
 }
 void animateTo(WindowManager.LayoutParams t){
  boolean geometry=t.width!=toW||t.height!=toH||t.x!=toX||t.y!=toY;
  boolean animatable=animOn()&&t.width>0&&t.height>0&&curW>0&&t.gravity==curGravity;
  if(!animatable){spring.cancel();wm.updateViewLayout(view,t);setCur(t);toW=t.width;toH=t.height;toX=t.x;toY=t.y;return;}
  if(!geometry){if(!spring.running()){wm.updateViewLayout(view,t);setCur(t);}return;}
  beginSpring(t);
 }
 void beginSpring(WindowManager.LayoutParams t){
  fromW=curW;fromH=curH;fromX=curX;fromY=curY;toW=t.width;toH=t.height;toX=t.x;toY=t.y;curGravity=t.gravity;
  fromR=cornerNow;toR=dp(expanded?30:faceCard()?FACE_RADIUS:60);params=t;
  spring.start(new Spring.Tick(){public void on(float v,boolean done){springFrame(v,done);}});
 }
 void springFrame(float v,boolean done){
  if(view==null||!window.attached()||params==null)return;
  float t=done?1f:v;
  int maxW=real().widthPixels;
  curW=Math.max(dp(10),Math.min(maxW,Math.round(fromW+(toW-fromW)*t)));
  curH=Math.max(dp(10),Math.round(fromH+(toH-fromH)*t));
  curX=Math.round(fromX+(toX-fromX)*t);curY=Math.round(fromY+(toY-fromY)*t);
  params.width=curW;params.height=curH;params.x=curX;params.y=curY;
  try{wm.updateViewLayout(view,params);}catch(Exception ignored){ErrorLog.w(ignored);}
  if(bgShape!=null){cornerNow=fromR+(toR-fromR)*Math.max(0f,Math.min(1f,t));bgShape.setCornerRadius(cornerNow);}
 }
 /** Content fades, scales and un-blurs in when the island changes shape or subject; not on every play/pause redraw. */
 void revealIfChanged(){
  String k=(expanded?"E":"C")+"|"+panel+"|"+(current==null?"idle":current.key)+"|"+(current==null?0:current.kind)+"|"+landscape()+"|"+replyMode;
  if(k.equals(revealKey))return;revealKey=k;
  if(!animOn()||view==null)return;
  final boolean grow=expanded;
  for(int i=0;i<view.getChildCount();i++){
   View v=view.getChildAt(i);long delay=(grow?130:80)+i*35L,dur=grow?320:230;
   v.animate().cancel();v.setAlpha(0f);v.setScaleX(.9f);v.setScaleY(.9f);
   v.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(delay).setDuration(dur).setInterpolator(new DecelerateInterpolator(1.7f)).start();
   blurIn(v,delay,dur);
  }
 }
 void blurIn(final View v,long delay,long duration){
  if(Build.VERSION.SDK_INT<31||!Prefs.flag(c,"blur",true))return;
  try{
   v.setRenderEffect(RenderEffect.createBlurEffect(14f,14f,Shader.TileMode.CLAMP));
   ValueAnimator a=ValueAnimator.ofFloat(14f,0f);a.setStartDelay(delay);a.setDuration(duration);
   a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){public void onAnimationUpdate(ValueAnimator an){
    float r=(Float)an.getAnimatedValue();
    if(r<=.5f)v.setRenderEffect(null);else v.setRenderEffect(RenderEffect.createBlurEffect(r,r,Shader.TileMode.CLAMP));
   }});
   a.start();
  }catch(Throwable ignored){ErrorLog.w(ignored);try{v.setRenderEffect(null);}catch(Throwable ignored2){ErrorLog.w(ignored2);}}
 }

 // ---------- event pills: ringer, charging, Focus, torch, Bluetooth, mic, timer, calls ----------
 Glyph glyphFor(Notice n){
  Glyph g;
  switch(n.kind){
   case Notice.K_RINGER:g=new Glyph(c,Glyph.BELL,n.accent);g.off=n.on;break;
   case Notice.K_CHARGING:case Notice.K_LOWBATT:g=new Glyph(c,Glyph.BATTERY,n.accent);g.level=n.level/100f;g.charging=n.kind==Notice.K_CHARGING;break;
   case Notice.K_DND:g=new Glyph(c,Glyph.MOON,n.accent);break;
   case Notice.K_TORCH:g=new Glyph(c,Glyph.TORCH,n.accent);g.off=!n.on;break;
   case Notice.K_BT:g=new Glyph(c,Glyph.HEADPHONES,n.accent);break;
   case Notice.K_FACE:g=new Glyph(c,Glyph.FACE,n.accent);break;
   case Notice.K_MIC:g=new Glyph(c,Glyph.MIC,n.accent);g.pulse=true;break;
   case Notice.K_TIMER:g=new Glyph(c,n.rec?Glyph.MIC:Glyph.TIMER,n.accent);g.pulse=true;break;
   default:g=new Glyph(c,Glyph.PHONE,n.accent);break;
  }
  g.begin();return g;
 }
 Chronometer chrono(Notice n,int size,int color){
  Chronometer ch=new Chronometer(c);ch.setBase(n.chronoBase);ch.setCountDown(n.countDown);ch.setTextColor(color);ch.setTextSize(size);
  ch.setTypeface(null,Typeface.BOLD);ch.setSingleLine(true);ch.setFontFeatureSettings("tnum");ch.start();return ch;
 }
 boolean buildEvent(final Notice n,boolean land){
  if(expanded){
   if(n.kind==Notice.K_CALL_IN||n.kind==Notice.K_CALL_ON){buildCall(n);return true;}
   if(n.kind==Notice.K_TIMER){buildTimer(n);return true;}
   return false;
  }
  if(faceCard()){
   view.addView(new FaceIdView(c),new LinearLayout.LayoutParams(-1,-1));
   view.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(IslandListener.live!=null)IslandListener.live.returnToRest();else idle();}});
   return true;
  }
  LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);view.addView(row,new LinearLayout.LayoutParams(-1,-1));
  boolean batteryStyle=n.kind==Notice.K_CHARGING||n.kind==Notice.K_LOWBATT;
  if(batteryStyle){
   TextView label=text(n.title,13,Color.WHITE);label.setTypeface(null,Typeface.BOLD);label.setPadding(dp(4),0,0,0);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
   TextView pct=text(n.text,13,n.accent);pct.setTypeface(null,Typeface.BOLD);pct.setPadding(0,0,dp(5),0);row.addView(pct,new LinearLayout.LayoutParams(-2,-2));
   row.addView(glyphFor(n),new LinearLayout.LayoutParams(dp(22),dp(20)));
  }else{
   if(n.rec){Waveform rw=new Waveform(c,n,true,n.accent);rw.bars=7;LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(30),dp(16));rp.leftMargin=dp(8);row.addView(rw,rp);}
   else{LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(dp(20),dp(20));gp.leftMargin=dp(3);row.addView(glyphFor(n),gp);}
   row.addView(new View(c),new LinearLayout.LayoutParams(0,1,1));
   if(n.kind==Notice.K_TIMER||n.kind==Notice.K_CALL_ON){
    if(n.chrono){Chronometer ch=chrono(n,13,n.accent);ch.setGravity(Gravity.RIGHT);ch.setPadding(0,0,dp(4),0);row.addView(ch,new LinearLayout.LayoutParams(-2,-2));}
    else if(n.clockText!=null){TextView t=text(n.clockText,13,n.accent);t.setTypeface(null,Typeface.BOLD);t.setFontFeatureSettings("tnum");t.setGravity(Gravity.RIGHT);t.setPadding(0,0,dp(4),0);row.addView(t,new LinearLayout.LayoutParams(-2,-2));}
   }else if(n.kind==Notice.K_MIC){
    LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(dp(22),dp(16));wp.rightMargin=dp(4);row.addView(new Waveform(c,n,true,n.accent),wp);
   }else{
    TextView t=text(n.text,13,n.accent);t.setTypeface(null,Typeface.BOLD);t.setGravity(Gravity.RIGHT);t.setPadding(0,0,dp(4),0);row.addView(t,new LinearLayout.LayoutParams(-2,-2));
   }
  }
  final boolean expandable=n.kind==Notice.K_TIMER||n.kind==Notice.K_CALL_ON||n.kind==Notice.K_CALL_IN;
  view.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
   if(expandable){expanded=true;render();}
   else if(n.rests()){}
   else if(IslandListener.live!=null)IslandListener.live.returnToRest();else idle();
  }});
  if(n.notification!=null)view.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){open();return true;}});
  return true;
 }
 void buildTimer(final Notice n){
  Notification.Action toggle=null,stop=null,lap=null;
  if(n.notification!=null&&n.notification.actions!=null)for(Notification.Action a:n.notification.actions){
   if(a==null||a.actionIntent==null||a.getRemoteInputs()!=null||a.title==null)continue;
   int role=actionRole(a.title.toString());
   if(role==3&&lap==null)lap=a;else if(role==2&&stop==null)stop=a;else if(role==1&&toggle==null)toggle=a;
  }
  boolean paused=toggle!=null&&actionPlays(toggle.title.toString());
  LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(4),dp(2),dp(4));
  view.addView(row,new LinearLayout.LayoutParams(-1,-2));
  int big=dp(40),small=dp(36);
  if(!n.rec){
   if(n.own){
    if(n.ownToggle!=null)addRound(row,n.ownPaused?RoundButton.PLAY:RoundButton.PAUSE,n.accent,0xff3d2606,false,n.ownPaused?L.s(R.string.s126):L.s(R.string.s127),n.ownToggle,big,0,dp(8));
    if(n.ownLap!=null)addRound(row,RoundButton.LAP,Color.WHITE,0xff17301d,false,L.s(R.string.s128),n.ownLap,big,0,dp(8));
    addRound(row,RoundButton.CLOSE,Color.WHITE,0xff17301d,false,L.s(R.string.s101),n.ownStop,big,0,dp(8));
   }else{
    if(toggle!=null)addRound(row,paused?RoundButton.PLAY:RoundButton.PAUSE,n.accent,0xff3d2606,false,toggle.title.toString(),run(toggle),big,0,dp(8));
    if(lap!=null)addRound(row,RoundButton.LAP,Color.WHITE,0xff17301d,false,lap.title.toString(),run(lap),big,0,dp(8));
    addRound(row,RoundButton.CLOSE,Color.WHITE,0xff17301d,false,stop!=null?stop.title.toString():L.s(R.string.s101),run(stop),big,0,dp(8));
   }
   row.addView(new View(c),new LinearLayout.LayoutParams(0,1,1));
   LinearLayout words=new LinearLayout(c);words.setGravity(Gravity.CENTER_VERTICAL);
   TextView label=text(n.title==null?"":n.title,12,n.accent);label.setTypeface(null,Typeface.BOLD);label.setPadding(0,dp(4),dp(8),0);words.addView(label);
   View t=timeView(n,26,n.accent);if(t!=null)words.addView(t);
   row.addView(words,new LinearLayout.LayoutParams(-2,-2));
  }else{
   Waveform wave=new Waveform(c,n,true,n.accent);wave.bars=24;
   LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(dp(120),dp(34));wp.leftMargin=dp(6);row.addView(wave,wp);
   row.addView(new View(c),new LinearLayout.LayoutParams(0,1,1));
   View t=timeView(n,20,n.accent);if(t!=null)row.addView(t,new LinearLayout.LayoutParams(-2,-2));
   if(toggle!=null)addRound(row,paused?RoundButton.PLAY:RoundButton.PAUSE,Color.WHITE,0xff17301d,false,toggle.title.toString(),run(toggle),small,dp(10),0);
   addRound(row,RoundButton.STOP,n.accent,0x00000000,true,stop!=null?stop.title.toString():L.s(R.string.s101),run(stop),big,dp(10),0);
  }
  view.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){open();return true;}});
 }
 /** The time as a live chronometer, or as plain text when the app only gave a frozen value. */
 View timeView(Notice n,int size,int color){
  if(n.chrono)return chrono(n,size,color);
  if(n.clockText!=null){TextView t=text(n.clockText,size,color);t.setTypeface(null,Typeface.BOLD);t.setFontFeatureSettings("tnum");t.setSingleLine(true);return t;}
  return null;
 }
 /** 1 = pause / resume, 2 = stop / cancel / save, 3 = lap, 0 = anything else. */
 static int actionRole(String title){
  String t=title.toLowerCase(java.util.Locale.ROOT);
  for(String w:new String[]{"lap","count","putaran","hitung","flag","bookmark","tandai","mark"})if(t.contains(w))return 3;
  for(String w:new String[]{"stop","cancel","reset","batal","berhenti","selesai","save","simpan","finish","dismiss","delete","hapus","tutup"})if(t.contains(w))return 2;
  for(String w:new String[]{"pause","jeda","resume","lanjut","start","mulai","play","putar"})if(t.contains(w))return 1;
  return 0;
 }
 static boolean actionPlays(String title){
  String t=title.toLowerCase(java.util.Locale.ROOT);
  for(String w:new String[]{"resume","lanjut","start","mulai","play","putar"})if(t.contains(w))return true;
  return false;
 }
 Runnable run(final Notification.Action action){
  if(action==null)return null;
  return new Runnable(){public void run(){
   try{send(action.actionIntent);}catch(Exception e){ErrorLog.w(e);Toast.makeText(c,L.s(R.string.s110),Toast.LENGTH_SHORT).show();}
  }};
 }
 void addRound(LinearLayout row,int type,int fg,int bg,boolean ring,String label,final Runnable action,int size,int left,int right){
  RoundButton b=new RoundButton(c,type,fg,bg,ring,label);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(size,size);lp.leftMargin=left;lp.rightMargin=right;row.addView(b,lp);
  b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
   handler.removeCallbacks(collapse);
   if(action==null){collapsePanel();return;}
   action.run();
  }});
 }
 GradientDrawable oval(int color){GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.OVAL);g.setColor(color);return g;}
 View callButton(int glyph,int bg,final Notification.Action action){
  FrameLayout f=new FrameLayout(c);f.setBackground(oval(bg));
  f.addView(new Glyph(c,glyph,Color.WHITE),new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));
  f.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
   try{if(action!=null&&action.actionIntent!=null)send(action.actionIntent);else Toast.makeText(c,L.s(R.string.s158),Toast.LENGTH_SHORT).show();}
   catch(Exception e){ErrorLog.w(e);Toast.makeText(c,L.s(R.string.s159),Toast.LENGTH_SHORT).show();}
   handler.postDelayed(new Runnable(){public void run(){if(current!=null&&current.kind==Notice.K_CALL_IN&&IslandListener.live!=null)IslandListener.live.returnToRest();}},1600);
  }});
  return f;
 }
 void buildCall(final Notice n){
  boolean incoming=n.kind==Notice.K_CALL_IN;
  LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(4),dp(2),dp(2),dp(2));
  Drawable face=face(n);
  View avatar;
  if(face!=null){ImageView iv=new ImageView(c);iv.setScaleType(ImageView.ScaleType.FIT_XY);iv.setImageDrawable(face);avatar=iv;}
  else{FrameLayout f=new FrameLayout(c);f.setBackground(oval(0xff2a2c2f));f.addView(new Glyph(c,Glyph.PHONE,n.accent),new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));avatar=f;}
  LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(48),dp(48));ap.rightMargin=dp(12);row.addView(avatar,ap);
  LinearLayout words=new LinearLayout(c);words.setOrientation(1);
  TextView name=text(n.title,17,Color.WHITE);name.setTypeface(null,Typeface.BOLD);words.addView(name);
  if(incoming||!n.chrono)words.addView(text(incoming?L.s(R.string.s160)+n.app:L.s(R.string.s161)+n.app,12,0xffb6b8bb));
  else{Chronometer ch=chrono(n,13,n.accent);words.addView(ch);}
  row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
  LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(46),dp(46));bp.leftMargin=dp(8);
  if(incoming){row.addView(callButton(Glyph.HANGUP,0xffff3b30,n.decline),bp);row.addView(callButton(Glyph.PHONE,0xff30d158,n.answer),new LinearLayout.LayoutParams(bp));}
  else if(n.decline!=null)row.addView(callButton(Glyph.HANGUP,0xffff3b30,n.decline),bp);
  view.addView(row,new LinearLayout.LayoutParams(-1,-2));
  view.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(!n.rests())open();else collapsePanel();}});
 }
 final class PanelRoot extends LinearLayout {
  PanelRoot(Context context){super(context);}
  float sx,sy;boolean swiping;
  @Override public boolean onInterceptTouchEvent(MotionEvent e){
   int a=e.getActionMasked();
   if(pagerPage()){
    if(a==MotionEvent.ACTION_DOWN){sx=e.getRawX();sy=e.getRawY();swiping=false;}
    else if(a==MotionEvent.ACTION_MOVE){float dx=e.getRawX()-sx,dy=e.getRawY()-sy;if(Math.abs(dx)>dp(14)&&Math.abs(dx)>Math.abs(dy)){swiping=true;return true;}}
   }else if(panel==null&&!expanded&&!replyMode){
    // The pill's row is clickable and takes the touch first, so a swipe never reached the root's touch listener.
    // Watch from here instead: once the finger has clearly moved sideways, take the gesture over from the row.
    if(a==MotionEvent.ACTION_MOVE&&!gFired){
     float dx=e.getRawX()-gDownX,dy=e.getRawY()-gDownY;
     if(swipeFar(dx,dy,12)){
      gFired=true;final int dir=swipeDir(dx,dy);
      handler.post(new Runnable(){public void run(){swipe(dir);}});
      return true;
     }
    }
   }
   return super.onInterceptTouchEvent(e);
  }
  @Override public boolean onTouchEvent(MotionEvent e){
   if(swiping){
    if(e.getActionMasked()==MotionEvent.ACTION_UP){swiping=false;float dx=e.getRawX()-sx;if(Math.abs(dx)>dp(26))pagerSwipe(dx<0?1:-1);}
    else if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)swiping=false;
    return true;
   }
   if(gFired&&panel==null){
    int a=e.getActionMasked();
    if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL)gFired=false;
    return true;
   }
   return super.onTouchEvent(e);
  }
  @Override public boolean dispatchTouchEvent(MotionEvent event){
   int a=event.getActionMasked();
   if(a==MotionEvent.ACTION_DOWN){gDownX=event.getRawX();gDownY=event.getRawY();gFired=false;gMoved=false;gBlock=onProgress(gDownX,gDownY);}
   if(expanded&&a==MotionEvent.ACTION_OUTSIDE){collapsePanel();return true;}
   if(panel==null&&!replyMode&&!gBlock){
    // A swipe is recognised here, ahead of every child (the clickable row, buttons, lyrics), so nothing inside the island can hold on to it.
    float dx=event.getRawX()-gDownX,dy=event.getRawY()-gDownY;
    if(a==MotionEvent.ACTION_MOVE&&!gMoved&&(Math.abs(dx)>dp(8)||Math.abs(dy)>dp(8)))gMoved=true;   // stops a slow swipe turning into a long press
    if(gFired){if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL)gFired=false;return true;}
    boolean up=a==MotionEvent.ACTION_UP;
    if((a==MotionEvent.ACTION_MOVE||up)&&swipeFar(dx,dy,up?8:10)){
     gFired=!up;
     MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);
     super.dispatchTouchEvent(cancel);cancel.recycle();
     final int dir=swipeDir(dx,dy);
     handler.post(new Runnable(){public void run(){swipe(dir);}});
     return true;
    }
   }
   return super.dispatchTouchEvent(event);
  }
 }
 void addChip(LinearLayout row,String title,Runnable action){TextView b=text(title,12,0xffa4c397);b.setGravity(Gravity.CENTER);b.setPadding(dp(5),dp(12),dp(5),dp(12));b.setBackground(shape(0xff0e2616,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(2),dp(3),dp(2),dp(3));row.addView(b,lp);b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){action.run();}});}
 /** From Android 14 a PendingIntent sent by an app that is not in the foreground starts no activity unless the sender opts in. Fyland has the overlay permission and a visible island, so it opts in; without this the tap did nothing. */
 Bundle launchOptions(){
  try{
   ActivityOptions ao=ActivityOptions.makeBasic();
   if(Build.VERSION.SDK_INT>=34){
    ao.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
    ao.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
   }
   return ao.toBundle();
  }catch(Throwable t){ErrorLog.w(t);return null;}
 }
 void send(PendingIntent pending)throws PendingIntent.CanceledException{pending.send(c,0,null,null,null,null,launchOptions());}
 /** Sends the intent; false when it is missing or already cancelled (an expired notification), so the caller can try the next way. */
 boolean fire(PendingIntent pending){
  if(pending==null)return false;
  try{send(pending);return true;}catch(Exception e){ErrorLog.w(e);return false;}
 }
 /** Starts the app of exactly this package: its launcher intent, else any MAIN/LAUNCHER activity it declares, else any music-category activity it declares. */
 boolean launchPackage(String pkg){
  if(pkg==null||pkg.isEmpty())return false;
  android.content.pm.PackageManager pm=c.getPackageManager();
  Intent app=null;
  try{app=pm.getLaunchIntentForPackage(pkg);}catch(Exception e){ErrorLog.w(e);}
  if(app==null){
   String[] cats={Intent.CATEGORY_LAUNCHER,Intent.CATEGORY_APP_MUSIC,Intent.CATEGORY_INFO,Intent.CATEGORY_DEFAULT};
   for(String cat:cats){
    try{
     Intent q=new Intent(Intent.ACTION_MAIN).addCategory(cat).setPackage(pkg);
     java.util.List<android.content.pm.ResolveInfo> l=pm.queryIntentActivities(q,0);
     if(l!=null&&!l.isEmpty()){app=new Intent(Intent.ACTION_MAIN).addCategory(cat).setClassName(pkg,l.get(0).activityInfo.name);break;}
    }catch(Exception e){ErrorLog.w(e);}
   }
  }
  if(app==null){ErrorLog.w("open","no launchable activity for "+pkg);return false;}
  try{
   c.startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));return true;
  }catch(Exception e){ErrorLog.w(e);return false;}
 }
 /** Opens the app behind the current item: the player's own screen for music, else the notification's target, else the app's launcher activity. */
 void open(){
  if(current==null||gMoved)return;stopReply(false);
  Notice n=current;boolean ok=false;
  try{
   if(n.media!=null){try{ok=fire(n.media.getSessionActivity());}catch(Exception e){ErrorLog.w(e);}}
   if(!ok&&n.notification!=null)ok=fire(n.notification.contentIntent);
   if(!ok)ok=launchPackage(n.pkg);
   // Only when the item has no package at all: never jump to some other music app (e.g. the default one) when the real player cannot be opened.
   if(!ok&&n.isMedia()&&(n.pkg==null||n.pkg.isEmpty())){
    try{Intent music=Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,Intent.CATEGORY_APP_MUSIC);music.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(music);ok=true;}catch(Exception e){ErrorLog.w(e);}
   }
  }catch(Exception e){ErrorLog.w(e);}
  if(!ok){ErrorLog.w("open","no way to open "+n.pkg);Toast.makeText(c,L.s(R.string.s162),Toast.LENGTH_LONG).show();return;}
  expanded=false;if(n.isMedia())render();else if(IslandListener.live!=null)IslandListener.live.returnToRest();else idle();
 }
 static final class Waveform extends View {
  final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final Notice item;final boolean always;float energy;long lastDraw;boolean vertical;int bars=5;
  Waveform(Context c,Notice item){this(c,item,false,0xffffffff);}
  Waveform(Context c,Notice item,boolean always,int color){super(c);this.item=item;this.always=always;p.setColor(color);setContentDescription(always||item.playing()?L.s(R.string.s163):L.s(R.string.s164));}
  @Override protected void onDraw(Canvas canvas){
   super.onDraw(canvas);
   long now=SystemClock.uptimeMillis();float dt=lastDraw==0?1f/60f:Math.min(.05f,(now-lastDraw)/1000f);lastDraw=now;
   boolean active=always||item.playing();energy=WaveMath.approach(energy,active?1f:0f,dt,9f);
   int bars=this.bars;float w=getWidth()/(float)(bars*2-1),h=getHeight();double t=now/1000.0;
   if(vertical){
    float th=h/(float)(bars*2-1),full=getWidth();
    for(int i=0;i<bars;i++){
     float wave=(float)(.5+.5*Math.sin(t*(5.2+i*1.7)+i*1.9))*(float)(.65+.35*Math.sin(t*2.3+i));
     float scale=.2f+.8f*energy*wave;float bw=Math.max(th,full*scale);
     float y=h-(i*2+1)*th;canvas.drawRoundRect((full-bw)/2,y,(full+bw)/2,y+th,th/2,th/2,p);
    }
   }else
   for(int i=0;i<bars;i++){
    float wave=(float)(.5+.5*Math.sin(t*(5.2+i*1.7)+i*1.9))*(float)(.65+.35*Math.sin(t*2.3+i));
    float scale=.2f+.8f*energy*wave;float bh=Math.max(w,h*scale);
    float x=i*2*w;canvas.drawRoundRect(x,(h-bh)/2,x+w,(h+bh)/2,w/2,w/2,p);
   }
   if((active||energy>.02f)&&isShown())postInvalidateOnAnimation();
  }
 }
static final class RoundButton extends View {
  static final int PAUSE=0,PLAY=1,CLOSE=2,STOP=3,LAP=4,PREV=5,NEXT=6,REC=7;
  final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final int type,fg,bg;final boolean ring;
  RoundButton(Context c,int type,int fg,int bg,boolean ring,String label){super(c);this.type=type;this.fg=fg;this.bg=bg;this.ring=ring;setContentDescription(label);setClickable(true);}
  @Override protected void onDraw(Canvas canvas){
   super.onDraw(canvas);
   float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())/2f,d=getResources().getDisplayMetrics().density;
   p.setStyle(Paint.Style.FILL);p.setColor(bg);canvas.drawCircle(cx,cy,r,p);
   if(ring){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.2f*d);p.setColor(0xffffffff);canvas.drawCircle(cx,cy,r-1.5f*d,p);}
   p.setColor(fg);p.setStyle(Paint.Style.FILL);p.setStrokeCap(Paint.Cap.ROUND);float s=r*.42f;
   if(type==PAUSE){canvas.drawRoundRect(cx-s*.75f,cy-s,cx-s*.15f,cy+s,2*d,2*d,p);canvas.drawRoundRect(cx+s*.15f,cy-s,cx+s*.75f,cy+s,2*d,2*d,p);}
   else if(type==PLAY){Path t=new Path();t.moveTo(cx-s*.6f,cy-s);t.lineTo(cx+s*.9f,cy);t.lineTo(cx-s*.6f,cy+s);t.close();canvas.drawPath(t,p);}
   else if(type==CLOSE){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.2f*d);canvas.drawLine(cx-s*.8f,cy-s*.8f,cx+s*.8f,cy+s*.8f,p);canvas.drawLine(cx+s*.8f,cy-s*.8f,cx-s*.8f,cy+s*.8f,p);}
   else if(type==STOP){canvas.drawRoundRect(cx-s*.8f,cy-s*.8f,cx+s*.8f,cy+s*.8f,4*d,4*d,p);}
   else if(type==REC){canvas.drawCircle(cx,cy,s*.9f,p);}
   else if(type==PREV||type==NEXT){
    float k=type==PREV?-1f:1f;Path t=new Path();
    t.moveTo(cx+k*s*.7f,cy-s*.9f);t.lineTo(cx-k*s*.5f,cy);t.lineTo(cx+k*s*.7f,cy+s*.9f);t.close();canvas.drawPath(t,p);
    canvas.drawRoundRect(cx-k*s*.9f-s*.12f,cy-s*.9f,cx-k*s*.9f+s*.12f,cy+s*.9f,d,d,p);
   }
   else{p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2f*d);canvas.drawLine(cx-s*.6f,cy-s,cx-s*.6f,cy+s,p);Path f=new Path();f.moveTo(cx-s*.6f,cy-s);f.lineTo(cx+s*.9f,cy-s*.4f);f.lineTo(cx-s*.6f,cy+s*.2f);p.setStyle(Paint.Style.FILL);canvas.drawPath(f,p);}
  }
 }
static final class MediaButton extends View {
  static final int HEART=0,SHUFFLE=1,PREV=2,PLAY=3,NEXT=4,REPEAT=5,ADD=6;
  final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final int kind;final boolean playing;boolean on,repeatOne;int accent=0xffff7a2e;
  MediaButton(Context c,int kind,boolean playing){
   super(c);this.kind=kind;this.playing=playing;setClickable(true);
   String label=kind==HEART?L.s(R.string.s165):kind==SHUFFLE?L.s(R.string.s166):kind==PREV?L.s(R.string.s167):kind==NEXT?L.s(R.string.s168):kind==REPEAT?L.s(R.string.s169):kind==ADD?L.s(R.string.s170):playing?L.s(R.string.s127):L.s(R.string.s171);
   setContentDescription(label);
  }
  @Override protected void onDraw(Canvas canvas){
   super.onDraw(canvas);
   float d=getResources().getDisplayMetrics().density,cx=getWidth()/2f,cy=getHeight()/2f,box=Math.min(getWidth(),getHeight());
   boolean round=kind==PREV||kind==PLAY||kind==NEXT;
   int ink=accent;
   if(round){
    float r=box*(kind==PLAY?.5f:.46f);
    p.setStyle(Paint.Style.FILL);p.setColor(isPressed()?0xccffffff:0xf2ffffff);canvas.drawPath(cookie(cx,cy,r),p);
    float s=r*(kind==PLAY?.40f:.36f);p.setColor(0xff1c1c1e);
    if(kind==PLAY){
     if(playing){canvas.drawRoundRect(cx-s*.8f,cy-s,cx-s*.2f,cy+s,2*d,2*d,p);canvas.drawRoundRect(cx+s*.2f,cy-s,cx+s*.8f,cy+s,2*d,2*d,p);}
     else{Path t=new Path();t.moveTo(cx-s*.6f,cy-s);t.lineTo(cx+s*.95f,cy);t.lineTo(cx-s*.6f,cy+s);t.close();canvas.drawPath(t,p);}
    }else{
     float k=kind==PREV?1f:-1f; // previous = bar on the left, arrow pointing left; next = mirror
     Path t=new Path();
     t.moveTo(cx+k*s*.7f,cy-s);t.lineTo(cx-k*s*.6f,cy);t.lineTo(cx+k*s*.7f,cy+s);t.close();canvas.drawPath(t,p);
     canvas.drawRoundRect(cx-k*s*.95f-s*.14f,cy-s,cx-k*s*.95f+s*.14f,cy+s,d,d,p);
    }
    return;
   }
   float s=box*.28f;
   if(kind==REPEAT&&on){
    p.setStyle(Paint.Style.FILL);p.setColor(accent);canvas.drawCircle(cx,cy,box*.225f,p);
    repeatArrow(canvas,cx,cy,s*.72f,CoverBg.pale(accent)?0xff1c1c1e:0xffffffff,d,repeatOne);
    return;
   }
   p.setColor(ink);p.setStrokeWidth(2f*d);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
   if(kind==HEART){
    float a=box*.135f; // half width; height is about 1.8 x a, so it is no longer stretched sideways
    Path h=new Path();h.moveTo(cx,cy+a*.95f);
    h.cubicTo(cx-a*.45f,cy+a*.62f,cx-a*1.0f,cy+a*.18f,cx-a*1.0f,cy-a*.32f);
    h.cubicTo(cx-a*1.0f,cy-a*.82f,cx-a*.58f,cy-a*1.0f,cx-a*.3f,cy-a*.98f);
    h.cubicTo(cx-a*.12f,cy-a*.96f,cx,cy-a*.78f,cx,cy-a*.55f);
    h.cubicTo(cx,cy-a*.78f,cx+a*.12f,cy-a*.96f,cx+a*.3f,cy-a*.98f);
    h.cubicTo(cx+a*.58f,cy-a*1.0f,cx+a*1.0f,cy-a*.82f,cx+a*1.0f,cy-a*.32f);
    h.cubicTo(cx+a*1.0f,cy+a*.18f,cx+a*.45f,cy+a*.62f,cx,cy+a*.95f);h.close();
    p.setStyle(on?Paint.Style.FILL_AND_STROKE:Paint.Style.STROKE);canvas.drawPath(h,p);
   }else if(kind==SHUFFLE){
    p.setStyle(Paint.Style.STROKE);
    Path a=new Path();a.moveTo(cx-s,cy-s*.6f);a.lineTo(cx-s*.25f,cy-s*.6f);a.lineTo(cx+s*.35f,cy+s*.6f);a.lineTo(cx+s*.7f,cy+s*.6f);canvas.drawPath(a,p);
    Path b=new Path();b.moveTo(cx-s,cy+s*.6f);b.lineTo(cx-s*.25f,cy+s*.6f);b.lineTo(cx+s*.35f,cy-s*.6f);b.lineTo(cx+s*.7f,cy-s*.6f);canvas.drawPath(b,p);
    p.setStyle(Paint.Style.FILL);
    Path ha=new Path();ha.moveTo(cx+s*.65f,cy+s*.15f);ha.lineTo(cx+s*1.1f,cy+s*.6f);ha.lineTo(cx+s*.65f,cy+s*1.05f);ha.close();canvas.drawPath(ha,p);
    Path hb=new Path();hb.moveTo(cx+s*.65f,cy-s*1.05f);hb.lineTo(cx+s*1.1f,cy-s*.6f);hb.lineTo(cx+s*.65f,cy-s*.15f);hb.close();canvas.drawPath(hb,p);
   }else if(kind==REPEAT){
    repeatArrow(canvas,cx,cy,s*.72f,ink,d,false);
   }else{
    p.setStyle(Paint.Style.STROKE);canvas.drawCircle(cx,cy,s*1.05f,p);
    canvas.drawLine(cx-s*.5f,cy,cx+s*.5f,cy,p);canvas.drawLine(cx,cy-s*.5f,cx,cy+s*.5f,p);
   }
  }
  private Path cookiePath;private float cookieR;
  /** Scalloped circle (a round "cookie" with 10 soft bumps) used behind previous / play-pause / next. */
  Path cookie(float cx,float cy,float R){
   if(cookiePath==null||Math.abs(cookieR-R)>.5f){
    Path q=new Path();int steps=240,bumps=10;float amp=.04f;
    for(int i=0;i<=steps;i++){double t=2*Math.PI*i/steps;float r=(float)(R*(1-amp)+R*amp*Math.cos(bumps*t));float x=r*(float)Math.cos(t),y=r*(float)Math.sin(t);if(i==0)q.moveTo(x,y);else q.lineTo(x,y);}
    q.close();cookiePath=q;cookieR=R;
   }
   Path out=new Path();out.addPath(cookiePath,cx,cy);return out;
  }
  /** Repeat glyph (two arrows chasing each other round a rounded rectangle). Draws a small "1" in the middle when it repeats the current song. */
  void repeatArrow(Canvas canvas,float cx,float cy,float r,int color,float d,boolean one){
   float w=r*.72f,h=w*.72f,cr=w*.38f,ah=w*.42f; // half width, half height, corner radius, arrow head size
   p.setColor(color);p.setStrokeWidth(1.9f*d);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
   Path half=new Path();
   half.moveTo(cx-w,cy+h-ah*.9f);half.lineTo(cx-w,cy-h+cr);half.quadTo(cx-w,cy-h,cx-w+cr,cy-h);half.lineTo(cx+w-ah*.5f,cy-h);
   Path head=new Path();
   head.moveTo(cx+w+ah*.35f,cy-h);head.lineTo(cx+w-ah*.6f,cy-h-ah*.8f);head.lineTo(cx+w-ah*.6f,cy-h+ah*.8f);head.close();
   for(int k=0;k<2;k++){
    canvas.save();if(k==1)canvas.rotate(180f,cx,cy);
    p.setStyle(Paint.Style.STROKE);canvas.drawPath(half,p);
    p.setStyle(Paint.Style.FILL);canvas.drawPath(head,p);
    canvas.restore();
   }
   if(one){p.setStyle(Paint.Style.FILL);p.setTextSize(w*.95f);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);canvas.drawText("1",cx,cy+w*.33f,p);}
  }
  @Override public void setPressed(boolean pressed){super.setPressed(pressed);invalidate();}
 }
}
