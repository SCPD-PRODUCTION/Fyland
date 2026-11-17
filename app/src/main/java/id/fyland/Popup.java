package id.fyland;

import android.Manifest;
import android.app.ActivityOptions;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.app.Notification;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/** Floating mini windows over any app: WhatsApp replies, a music player and a voice recorder.
 *  They can be dragged by the title bar, resized from the corner, maximised, shrunk to a bubble and closed. */
final class Popup {
 static final HashMap<String,Popup> open=new HashMap<>();
 static final Runnable refreshRun=new Runnable(){public void run(){refreshAll();}};
 static void refreshAll(){for(Popup p:new ArrayList<>(open.values()))p.refresh();}
 /** On phones that support floating windows, open the real WhatsApp app in one. Returns false when the phone cannot. */
 static boolean hintShown;
 /** True when this phone can show a real app in a floating (freeform) window. */
 static boolean floatingSupported(Context c){
  try{if(c.getPackageManager().hasSystemFeature("android.software.freeform_window_management"))return true;}catch(Throwable ignored){ErrorLog.w(ignored);}
  try{if(android.provider.Settings.Global.getInt(c.getContentResolver(),"enable_freeform_support",0)==1)return true;}catch(Throwable ignored){ErrorLog.w(ignored);}
  return false;
 }
 /** ActivityOptions bundle key that carries the launch windowing mode (same string the framework reads in ActivityOptions(Bundle)). */
 static final String KEY_WINDOWING_MODE="android.activity.windowingMode";
 static final int WINDOWING_MODE_FREEFORM=5;
 /** Builds the launch bundle with the floating (freeform) windowing mode forced.
  *  ActivityOptions.setLaunchWindowingMode() is a hidden API that the system blocks for normal apps, so the old reflection call silently failed
  *  and the launch bounds alone were ignored on a fullscreen phone display (WhatsApp opened full screen). Writing the mode straight into the
  *  options Bundle needs no hidden API and is honoured by the system when "Enable freeform windows" is on. */
 static Bundle freeformBundle(ActivityOptions ao){
  try{ao.getClass().getMethod("setLaunchWindowingMode",int.class).invoke(ao,WINDOWING_MODE_FREEFORM);}catch(Throwable ignored){}
  Bundle b=ao.toBundle();
  if(b==null)b=new Bundle();
  b.putInt(KEY_WINDOWING_MODE,WINDOWING_MODE_FREEFORM);
  return b;
 }
 /** Opens a real app (first installed package of the list) in its own floating (freeform) window. Always asks for the floating mode; whether it is granted is up to the phone. */
 static void launchFloatingApp(IslandOverlay o,String... pkgs){
  Context c=o.c;
  try{
   PackageManager pm=c.getPackageManager();
   Intent i=null;
   for(String p:pkgs){i=pm.getLaunchIntentForPackage(p);if(i!=null)break;}
   if(i==null){Toast.makeText(c,L.s(R.string.u_app_missing),Toast.LENGTH_SHORT).show();return;}
   DisplayMetrics dm=o.real();
   int w=Math.min(o.dp(340),dm.widthPixels-o.dp(16)),h=Math.min(o.dp(540),dm.heightPixels-o.dp(120));
   int x=(dm.widthPixels-w)/2,y=o.dp(90);
   ActivityOptions ao=ActivityOptions.makeBasic().setLaunchBounds(new Rect(x,y,x+w,y+h));
   // A new task, so an app already open full screen is not just brought to the front (that would ignore the floating bounds).
   i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
   c.startActivity(i,freeformBundle(ao));
  }catch(Exception e){ErrorLog.w(e);Toast.makeText(c,L.s(R.string.s486),Toast.LENGTH_SHORT).show();}
 }
 static void openYouTube(IslandOverlay o){launchFloatingApp(o,"com.google.android.youtube");}
 static void openTikTok(IslandOverlay o){launchFloatingApp(o,"com.zhiliaoapp.musically","com.ss.android.ugc.trill");}
 /** WhatsApp floating (kept for the chat popup). */
 static boolean launchFloating(IslandOverlay o){
  if(!floatingSupported(o.c))return false;
  launchFloatingApp(o,"com.whatsapp","com.whatsapp.w4b");
  return true;
 }
 /** Island tile: WhatsApp floating when the phone allows it, otherwise the quick-reply window (no WhatsApp Web). */
 static void openWhatsApp(IslandOverlay o){
  show(o,"chat");
 }
 static boolean tryFreeform(IslandOverlay o){return false;}
 static void show(IslandOverlay o,String kind){
  if(kind.equals("chat")&&!open.containsKey(kind)&&tryFreeform(o))return;
  Popup p=open.get(kind);
  if(p!=null){p.restore();return;}
  p=new Popup(o,kind);open.put(kind,p);
  if(!p.create())open.remove(kind);
 }

 static final int ACCENT=0xffa4c397,MUTED=0xff93ab94,TILE=0xff0e2616,RED=0xffff453a;

 final IslandOverlay o;final Context c;final WindowManager wm;final String kind;
 final Handler h=new Handler(Looper.getMainLooper());
 FrameLayout root,content;LinearLayout card,titleBar;TextView bubble;View handle;
 WindowManager.LayoutParams lp;
 boolean minimized,maxed,inputOn,attached;
 int sw,sh,nx,ny,nw,nh,savedY,mx,my,mw,mh;
 // chat
 String selKey;TextView msgView;EditText input;boolean gotFocus;
 // music
 String musicSig="";TextView timeNow,timeTotal;Bar bar;
 // recorder
 AmpWave wave;TextView recTime,recStatus;boolean recShown;

 Popup(IslandOverlay o,String kind){this.o=o;this.c=o.c;this.wm=o.wm;this.kind=kind;}
 int dp(float x){return o.dp(x);}
 TextView tv(String s,int sp,int color){TextView t=new TextView(c);t.setText(s);t.setTextSize(sp);t.setTextColor(color);return t;}
 LinearLayout col(){LinearLayout l=new LinearLayout(c);l.setOrientation(1);return l;}
 GradientDrawable rounded(int color,int r){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));return d;}
 String title(){return kind.equals("chat")?"💬  WhatsApp":kind.equals("music")?L.s(R.string.s489):L.s(R.string.s490);}

 boolean create(){
  DisplayMetrics dm=o.real();sw=dm.widthPixels;sh=dm.heightPixels;
  int w=kind.equals("chat")?dp(350):kind.equals("music")?dp(300):dp(280),hh=kind.equals("chat")?dp(520):kind.equals("music")?dp(250):dp(260);
  w=Math.min(w,sw-dp(16));hh=Math.min(hh,sh-dp(120));
  nw=w;nh=hh;nx=(sw-w)/2;ny=dp(96);
  root=new FrameLayout(c);
  card=col();card.setBackground(o.shape(0xf2101113,22));
  root.addView(card,new FrameLayout.LayoutParams(-1,-1));
  titleBar=new LinearLayout(c);titleBar.setGravity(Gravity.CENTER_VERTICAL);titleBar.setPadding(dp(14),0,dp(4),0);
  TextView t=tv(title(),13,ACCENT);t.setTypeface(null,Typeface.BOLD);t.setSingleLine(true);
  titleBar.addView(t,new LinearLayout.LayoutParams(0,-2,1));
  if(kind.equals("chat"))titleBar.addView(barButton("↗",new Runnable(){public void run(){launchWhatsApp();}}));
  titleBar.addView(barButton("–",new Runnable(){public void run(){minimize();}}));
  titleBar.addView(barButton("▢",new Runnable(){public void run(){toggleMax();}}));
  titleBar.addView(barButton("✕",new Runnable(){public void run(){close();}}));
  card.addView(titleBar,new LinearLayout.LayoutParams(-1,dp(42)));
  content=new FrameLayout(c);card.addView(content,new LinearLayout.LayoutParams(-1,0,1));
  titleBar.setOnTouchListener(new DragListener(false));
  handle=new View(c){@Override protected void onDraw(Canvas cv){
   Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(0xff6b6e73);p.setStrokeWidth(dp(1.6f));p.setStrokeCap(Paint.Cap.ROUND);
   float s=getWidth();cv.drawLine(s-dp(5),s-dp(14),s-dp(14),s-dp(5),p);cv.drawLine(s-dp(5),s-dp(8),s-dp(8),s-dp(5),p);
  }};
  handle.setOnTouchListener(new View.OnTouchListener(){float rx,ry;int w0,h0;public boolean onTouch(View v,MotionEvent e){
   switch(e.getActionMasked()){
    case MotionEvent.ACTION_DOWN:rx=e.getRawX();ry=e.getRawY();w0=lp.width;h0=lp.height;return true;
    case MotionEvent.ACTION_MOVE:
     lp.width=Math.max(dp(240),Math.min(sw-lp.x,w0+(int)(e.getRawX()-rx)));
     lp.height=Math.max(dp(190),Math.min(sh-lp.y,h0+(int)(e.getRawY()-ry)));
     maxed=false;update();return true;
   }
   return true;
  }});
  root.addView(handle,new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.BOTTOM|Gravity.RIGHT));
  bubble=tv(kind.equals("chat")?"💬":kind.equals("music")?"🎵":"🎙",22,Color.WHITE);bubble.setGravity(Gravity.CENTER);bubble.setBackground(rounded(0xf2101113,26));bubble.setVisibility(View.GONE);
  bubble.setOnTouchListener(new DragListener(true));
  root.addView(bubble,new FrameLayout.LayoutParams(-1,-1));
  int type=o.aboveStatus?WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY:WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
  int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
  lp=new WindowManager.LayoutParams(w,hh,type,flags,PixelFormat.TRANSLUCENT);
  lp.gravity=Gravity.TOP|Gravity.LEFT;lp.x=nx;lp.y=ny;
  lp.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN|WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;
  lp.setTitle("Fyland popup");
  try{wm.addView(root,lp);attached=true;}catch(Exception e){ErrorLog.w(e);return false;}
  root.getViewTreeObserver().addOnWindowFocusChangeListener(new ViewTreeObserver.OnWindowFocusChangeListener(){public void onWindowFocusChanged(boolean hasFocus){
   if(hasFocus)gotFocus=true;
   else if(inputOn&&gotFocus){gotFocus=false;enableInput(false);}
  }});
  buildBody();
  if(!kind.equals("chat"))h.postDelayed(tick,kind.equals("rec")?80:500);
  return true;
 }
 TextView barButton(String s,final Runnable action){
  TextView b=tv(s,16,Color.WHITE);b.setGravity(Gravity.CENTER);
  b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){action.run();}});
  b.setLayoutParams(new LinearLayout.LayoutParams(dp(38),dp(42)));
  return b;
 }
 void update(){if(!attached)return;try{wm.updateViewLayout(root,lp);}catch(Exception ignored){ErrorLog.w(ignored);}}

 /** Drags the window by the title bar, or by the bubble (a short tap on the bubble opens the window again). */
 final class DragListener implements View.OnTouchListener{
  final boolean isBubble;float rx,ry;int x0,y0;boolean moved;
  DragListener(boolean isBubble){this.isBubble=isBubble;}
  public boolean onTouch(View v,MotionEvent e){
   switch(e.getActionMasked()){
    case MotionEvent.ACTION_DOWN:if(!isBubble)enableInput(false);rx=e.getRawX();ry=e.getRawY();x0=lp.x;y0=lp.y;moved=false;return true;
    case MotionEvent.ACTION_MOVE:{
     float dx=e.getRawX()-rx,dy=e.getRawY()-ry;
     if(Math.abs(dx)>dp(6)||Math.abs(dy)>dp(6))moved=true;
     lp.x=Math.max(0,Math.min(sw-lp.width,x0+(int)dx));lp.y=Math.max(0,Math.min(sh-dp(46),y0+(int)dy));
     update();return true;
    }
    case MotionEvent.ACTION_UP:if(isBubble&&!moved)restore();return true;
   }
   return true;
  }
 }
 void minimize(){
  if(minimized)return;
  enableInput(false);
  mx=lp.x;my=lp.y;mw=lp.width;mh=lp.height;minimized=true;
  card.setVisibility(View.GONE);handle.setVisibility(View.GONE);bubble.setVisibility(View.VISIBLE);
  lp.width=dp(52);lp.height=dp(52);update();
 }
 void restore(){
  if(!minimized)return;
  minimized=false;
  bubble.setVisibility(View.GONE);card.setVisibility(View.VISIBLE);handle.setVisibility(View.VISIBLE);
  lp.width=mw;lp.height=mh;lp.x=Math.max(0,Math.min(sw-mw,lp.x));lp.y=Math.max(0,Math.min(sh-mh,lp.y));update();
 }
 void toggleMax(){
  if(minimized)return;
  if(!maxed){nx=lp.x;ny=lp.y;nw=lp.width;nh=lp.height;lp.x=dp(8);lp.y=dp(40);lp.width=sw-dp(16);lp.height=(int)(sh*.8f);maxed=true;}
  else{lp.x=nx;lp.y=ny;lp.width=nw;lp.height=nh;maxed=false;}
  update();
 }
 void close(){
  enableInput(false);
  h.removeCallbacksAndMessages(null);
  try{wm.removeViewImmediate(root);}catch(Exception ignored){ErrorLog.w(ignored);}
  attached=false;open.remove(kind);
 }

 /** The window cannot take the keyboard while it is not focusable; turn that on only while typing, and lift the window clear of the keyboard. */
 void enableInput(boolean on){
  if(inputOn==on||!attached)return;
  inputOn=on;
  if(on){
   lp.flags&=~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
   savedY=lp.y;if(lp.y+lp.height>sh/2)lp.y=Math.max(dp(24),dp(60));
  }else{
   lp.flags|=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;lp.y=savedY;
   try{((android.view.inputmethod.InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(),0);}catch(Exception ignored){ErrorLog.w(ignored);}
  }
  update();
 }
 void focusInput(final EditText e){
  enableInput(true);
  h.postDelayed(new Runnable(){public void run(){
   e.requestFocus();
   try{((android.view.inputmethod.InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(e,0);}catch(Exception ignored){ErrorLog.w(ignored);}
  }},200);
 }

 void buildBody(){
  if(kind.equals("chat"))buildChat();else if(kind.equals("music"))buildMusic();else buildRec();
 }
 void refresh(){
  if(!attached||!kind.equals("chat"))return;
  Notice sel=selected();
  if(selKey!=null&&sel==null){selKey=null;buildChatLite();return;}
  if(selKey==null){if(!inputOn)buildChatLite();return;}
  if(msgView!=null){String now=body(sel);if(!now.equals(msgView.getText().toString()))msgView.setText(now);}
 }
 final Runnable tick=new Runnable(){public void run(){
  if(!attached)return;
  try{if(kind.equals("music"))tickMusic();else if(kind.equals("rec"))tickRec();}catch(Exception ignored){ErrorLog.w(ignored);}
  h.postDelayed(this,kind.equals("rec")?80:500);
 }};

 // ---------- WhatsApp ----------
 ArrayList<Notice> conversations(){
  ArrayList<Notice> out=new ArrayList<>();
  IslandListener l=IslandListener.live;if(l==null)return out;
  for(Notice n:l.items)if(n.pkg!=null&&n.pkg.startsWith("com.whatsapp")&&n.kind==0&&!n.isMedia())out.add(n);
  return out;
 }
 Notice selected(){if(selKey==null)return null;for(Notice n:conversations())if(n.key.equals(selKey))return n;return null;}
 String body(Notice n){return n.fullText!=null&&!n.fullText.isEmpty()?n.fullText:n.text==null?"":n.text;}
 void launchWhatsApp(){
  try{
   Intent i=c.getPackageManager().getLaunchIntentForPackage("com.whatsapp");
   if(i==null)i=c.getPackageManager().getLaunchIntentForPackage("com.whatsapp.w4b");
   if(i==null){Toast.makeText(c,L.s(R.string.s492),Toast.LENGTH_SHORT).show();return;}
   i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);
  }catch(Exception e){ErrorLog.w(e);Toast.makeText(c,L.s(R.string.s486),Toast.LENGTH_SHORT).show();}
 }
 TextView button(String label,int fg,int bg,final Runnable action){
  TextView b=tv(label,13,fg);b.setTypeface(null,Typeface.BOLD);b.setGravity(Gravity.CENTER);b.setPadding(dp(16),dp(10),dp(16),dp(10));b.setBackground(rounded(bg,16));
  b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){action.run();}});
  return b;
 }
  void buildChat(){buildChatLite();}
 void buildChatLite(){
  content.removeAllViews();msgView=null;input=null;enableInput(false);
  LinearLayout box=col();box.setPadding(dp(12),dp(2),dp(12),dp(14));
  content.addView(box,new FrameLayout.LayoutParams(-1,-1));
  Notice sel=selected();
  if(sel==null){
   selKey=null;
   ArrayList<Notice> list=conversations();
   if(list.isEmpty()){
    TextView t=tv(L.s(R.string.s498),13,MUTED);t.setPadding(0,dp(10),0,dp(14));box.addView(t);
    box.addView(button(L.s(R.string.s499),Color.WHITE,0xff25a244,new Runnable(){public void run(){launchWhatsApp();}}));
   }else{
    ScrollView sv=new ScrollView(c);LinearLayout lst=col();sv.addView(lst);
    for(final Notice n:list){
     LinearLayout row=col();row.setPadding(dp(12),dp(9),dp(12),dp(9));row.setBackground(rounded(TILE,14));
     TextView name=tv(n.title==null?"":n.title,14,Color.WHITE);name.setTypeface(null,Typeface.BOLD);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(name);
     String b=body(n);int cut=b.lastIndexOf('\n');if(cut>=0)b=b.substring(cut+1);
     TextView last=tv(b,12,MUTED);last.setSingleLine(true);last.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(last);
     LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=dp(6);lst.addView(row,rp);
     row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){selKey=n.key;buildChatLite();}});
    }
    box.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
   }
   return;
  }
  LinearLayout head=new LinearLayout(c);head.setGravity(Gravity.CENTER_VERTICAL);
  TextView back=tv("‹",22,Color.WHITE);back.setPadding(0,0,dp(12),dp(2));
  back.setOnClickListener(new View.OnClickListener(){public void onClick(View v){selKey=null;buildChatLite();}});
  head.addView(back);
  TextView name=tv(sel.title==null?"":sel.title,14,Color.WHITE);name.setTypeface(null,Typeface.BOLD);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
  head.addView(name,new LinearLayout.LayoutParams(0,-2,1));
  TextView openApp=tv(L.s(R.string.s99),12,ACCENT);openApp.setPadding(dp(10),dp(6),0,dp(6));
  openApp.setOnClickListener(new View.OnClickListener(){public void onClick(View v){launchWhatsApp();}});
  head.addView(openApp);
  box.addView(head,new LinearLayout.LayoutParams(-1,-2));
  final ScrollView scroll=new ScrollView(c);
  msgView=tv(body(sel),13,0xffe4e5e7);msgView.setLineSpacing(dp(2),1f);msgView.setPadding(dp(4),dp(8),dp(4),dp(8));
  scroll.addView(msgView);
  box.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  scroll.post(new Runnable(){public void run(){scroll.fullScroll(View.FOCUS_DOWN);}});
  LinearLayout reply=new LinearLayout(c);reply.setGravity(Gravity.CENTER_VERTICAL);
  input=new EditText(c);input.setTextColor(Color.WHITE);input.setHintTextColor(0xff8e9094);input.setTextSize(14);input.setHint(L.s(R.string.s111));input.setMaxLines(3);
  input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
  input.setBackground(rounded(TILE,16));input.setPadding(dp(12),dp(8),dp(12),dp(8));
  input.setOnTouchListener(new View.OnTouchListener(){public boolean onTouch(View v,MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN)focusInput(input);return false;}});
  input.setOnKeyListener(new View.OnKeyListener(){public boolean onKey(View v,int key,KeyEvent e){
   if(key==KeyEvent.KEYCODE_BACK&&e.getAction()==KeyEvent.ACTION_UP){input.clearFocus();enableInput(false);return true;}
   return false;
  }});
  reply.addView(input,new LinearLayout.LayoutParams(0,-2,1));
  TextView send=button(L.s(R.string.s113),Color.WHITE,0xff25a244,new Runnable(){public void run(){sendReply();}});
  LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-2,-2);sp.leftMargin=dp(8);reply.addView(send,sp);
  box.addView(reply,new LinearLayout.LayoutParams(-1,-2));
 }
 void sendReply(){
  Notice sel=selected();if(sel==null||input==null)return;
  String value=input.getText().toString().trim();
  if(value.isEmpty()){input.setError(L.s(R.string.s115));return;}
  int index=sel.replyIndex();
  if(index<0||sel.notification==null){Toast.makeText(c,L.s(R.string.s500),Toast.LENGTH_SHORT).show();return;}
  String error=sel.sendReply(c,value);
  if(error==null){
   if(msgView!=null)msgView.setText(msgView.getText()+L.s(R.string.s501)+value);
   input.setText("");input.clearFocus();enableInput(false);
   Toast.makeText(c,L.s(R.string.s502),Toast.LENGTH_SHORT).show();
  }else Toast.makeText(c,L.s(R.string.s503)+" ("+error+")",Toast.LENGTH_LONG).show();
 }

 // ---------- music ----------
 MediaController controller(){
  IslandListener l=IslandListener.live;if(l==null)return null;
  if(l.music!=null&&l.music.media!=null)return l.music.media;
  for(Notice n:l.items)if(n.isMedia()&&n.media!=null)return n.media;
  return null;
 }
 static String clock(long ms){long s=Math.max(0,ms/1000);return String.format(Locale.US,"%d:%02d",s/60,s%60);}
 void buildMusic(){
  content.removeAllViews();timeNow=null;timeTotal=null;bar=null;
  LinearLayout box=col();box.setPadding(dp(14),dp(2),dp(14),dp(14));
  content.addView(box,new FrameLayout.LayoutParams(-1,-1));
  final MediaController mc=controller();
  if(mc==null){
   musicSig="none";
   TextView t=tv(L.s(R.string.s504),13,MUTED);t.setPadding(0,dp(10),0,dp(14));box.addView(t);
   box.addView(button(L.s(R.string.s505),Color.WHITE,0xff7c5cd6,new Runnable(){public void run(){
    try{Intent i=Intent.makeMainSelectorActivity(Intent.ACTION_MAIN,Intent.CATEGORY_APP_MUSIC);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);}
    catch(Exception e){ErrorLog.w(e);Toast.makeText(c,L.s(R.string.s506),Toast.LENGTH_SHORT).show();}
   }}));
   return;
  }
  MediaMetadata md=mc.getMetadata();PlaybackState st=mc.getPlaybackState();
  String title=md==null?null:md.getString(MediaMetadata.METADATA_KEY_TITLE);if(title==null&&md!=null)title=md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
  String artist=md==null?null:md.getString(MediaMetadata.METADATA_KEY_ARTIST);if(artist==null&&md!=null)artist=md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
  boolean playing=st!=null&&st.getState()==PlaybackState.STATE_PLAYING;
  musicSig=title+"|"+artist+"|"+playing;
  Bitmap art=null;
  if(md!=null){art=md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);if(art==null)art=md.getBitmap(MediaMetadata.METADATA_KEY_ART);if(art==null)art=md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);}
  LinearLayout top=new LinearLayout(c);top.setGravity(Gravity.CENTER_VERTICAL);
  ImageView img=new ImageView(c);img.setScaleType(ImageView.ScaleType.CENTER_CROP);
  if(art!=null)img.setImageBitmap(art);else img.setBackground(rounded(TILE,14));
  img.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,Outline out){out.setRoundRect(0,0,v.getWidth(),v.getHeight(),dp(14));}});img.setClipToOutline(true);
  top.addView(img,new LinearLayout.LayoutParams(dp(70),dp(70)));
  LinearLayout words=col();words.setPadding(dp(12),0,0,0);
  TextView tt=tv(title==null?L.s(R.string.s507):title,15,Color.WHITE);tt.setTypeface(null,Typeface.BOLD);tt.setMaxLines(2);tt.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(tt);
  TextView aa=tv(artist==null?"":artist,12,MUTED);aa.setSingleLine(true);aa.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(aa);
  top.addView(words,new LinearLayout.LayoutParams(0,-2,1));
  box.addView(top,new LinearLayout.LayoutParams(-1,-2));
  bar=new Bar(c,new Bar.Seek(){public void to(float frac){
   MediaMetadata m=mc.getMetadata();long dur=m==null?0:m.getLong(MediaMetadata.METADATA_KEY_DURATION);
   if(dur>0)mc.getTransportControls().seekTo((long)(dur*frac));
  }});
  LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(22));bp.topMargin=dp(12);box.addView(bar,bp);
  LinearLayout times=new LinearLayout(c);
  timeNow=tv("0:00",10,MUTED);timeTotal=tv("0:00",10,MUTED);timeTotal.setGravity(Gravity.RIGHT);
  times.addView(timeNow,new LinearLayout.LayoutParams(0,-2,1));times.addView(timeTotal,new LinearLayout.LayoutParams(0,-2,1));
  box.addView(times,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout ctl=new LinearLayout(c);ctl.setGravity(Gravity.CENTER);
  addCtl(ctl,IslandOverlay.RoundButton.PREV,dp(44),new Runnable(){public void run(){mc.getTransportControls().skipToPrevious();}});
  final boolean isPlaying=playing;
  addCtl(ctl,isPlaying?IslandOverlay.RoundButton.PAUSE:IslandOverlay.RoundButton.PLAY,dp(52),new Runnable(){public void run(){
   if(isPlaying)mc.getTransportControls().pause();else mc.getTransportControls().play();
  }});
  addCtl(ctl,IslandOverlay.RoundButton.NEXT,dp(44),new Runnable(){public void run(){mc.getTransportControls().skipToNext();}});
  LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(6);box.addView(ctl,cp);
  tickMusic();
 }
 void addCtl(LinearLayout row,int type,int size,final Runnable action){
  IslandOverlay.RoundButton b=new IslandOverlay.RoundButton(c,type,Color.WHITE,type==IslandOverlay.RoundButton.PREV||type==IslandOverlay.RoundButton.NEXT?0x00000000:0xff17301d,false,"kontrol");
  LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(size,size);lp2.leftMargin=lp2.rightMargin=dp(10);row.addView(b,lp2);
  b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){action.run();h.postDelayed(new Runnable(){public void run(){tickMusic();}},250);}});
 }
 void tickMusic(){
  MediaController mc=controller();
  String sig;
  if(mc==null)sig="none";
  else{
   MediaMetadata md=mc.getMetadata();PlaybackState st=mc.getPlaybackState();
   String title=md==null?null:md.getString(MediaMetadata.METADATA_KEY_TITLE);if(title==null&&md!=null)title=md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
   String artist=md==null?null:md.getString(MediaMetadata.METADATA_KEY_ARTIST);if(artist==null&&md!=null)artist=md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
   sig=title+"|"+artist+"|"+(st!=null&&st.getState()==PlaybackState.STATE_PLAYING);
  }
  if(!sig.equals(musicSig)){buildMusic();return;}
  if(mc==null||bar==null)return;
  MediaMetadata md=mc.getMetadata();PlaybackState st=mc.getPlaybackState();
  long dur=md==null?0:md.getLong(MediaMetadata.METADATA_KEY_DURATION);
  long pos=0;
  if(st!=null){pos=st.getPosition();if(st.getState()==PlaybackState.STATE_PLAYING)pos+=(long)((SystemClock.elapsedRealtime()-st.getLastPositionUpdateTime())*st.getPlaybackSpeed());}
  if(dur>0)pos=Math.min(pos,dur);
  bar.set(dur>0?(float)pos/dur:0f);
  if(timeNow!=null)timeNow.setText(clock(pos));if(timeTotal!=null)timeTotal.setText(dur>0?clock(dur):"");
 }
 static final class Bar extends View{
  interface Seek{void to(float frac);}
  final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final Seek seek;float frac;boolean dragging;
  Bar(Context c,Seek s){super(c);seek=s;}
  void set(float f){if(!dragging){frac=f;invalidate();}}
  @Override protected void onDraw(Canvas cv){
   float d=getResources().getDisplayMetrics().density,y=getHeight()/2f,w=getWidth();
   p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(4*d);p.setColor(0xff3a3d41);cv.drawLine(3*d,y,w-3*d,y,p);
   p.setColor(0xffa4c397);cv.drawLine(3*d,y,3*d+(w-6*d)*frac,y,p);
   cv.drawCircle(3*d+(w-6*d)*frac,y,5*d,p);
  }
  @Override public boolean onTouchEvent(MotionEvent e){
   float d=getResources().getDisplayMetrics().density;
   float f=Math.max(0f,Math.min(1f,(e.getX()-3*d)/Math.max(1f,getWidth()-6*d)));
   switch(e.getActionMasked()){
    case MotionEvent.ACTION_DOWN:dragging=true;frac=f;invalidate();return true;
    case MotionEvent.ACTION_MOVE:frac=f;invalidate();return true;
    case MotionEvent.ACTION_UP:dragging=false;frac=f;invalidate();seek.to(f);return true;
    case MotionEvent.ACTION_CANCEL:dragging=false;return true;
   }
   return true;
  }
 }

 // ---------- voice recorder ----------
 void buildRec(){
  content.removeAllViews();
  LinearLayout box=col();box.setGravity(Gravity.CENTER_HORIZONTAL);box.setPadding(dp(14),dp(2),dp(14),dp(14));
  content.addView(box,new FrameLayout.LayoutParams(-1,-1));
  wave=new AmpWave(c);box.addView(wave,new LinearLayout.LayoutParams(-1,dp(62)));
  recTime=tv("00:00",30,Color.WHITE);recTime.setTypeface(null,Typeface.BOLD);recTime.setGravity(Gravity.CENTER);recTime.setPadding(0,dp(6),0,0);
  box.addView(recTime,new LinearLayout.LayoutParams(-1,-2));
  recStatus=tv(RecorderService.status.isEmpty()?L.s(R.string.s509):RecorderService.status,11,MUTED);recStatus.setGravity(Gravity.CENTER);recStatus.setPadding(0,dp(2),0,dp(8));recStatus.setSingleLine(false);
  box.addView(recStatus,new LinearLayout.LayoutParams(-1,-2));
  recShown=RecorderService.recording;
  IslandOverlay.RoundButton b=new IslandOverlay.RoundButton(c,recShown?IslandOverlay.RoundButton.STOP:IslandOverlay.RoundButton.REC,RED,0x00000000,true,recShown?L.s(R.string.s510):L.s(R.string.s511));
  b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){toggleRecord();}});
  box.addView(b,new LinearLayout.LayoutParams(dp(62),dp(62)));
 }
 void toggleRecord(){
  try{
   if(RecorderService.recording){
    c.startService(new Intent(c,RecorderService.class).setAction("stop"));
   }else{
    if(c.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
     Toast.makeText(c,L.s(R.string.s512),Toast.LENGTH_LONG).show();
     c.startActivity(new Intent(c,MainActivity.class).putExtra("ask_mic",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
     return;
    }
    c.startForegroundService(new Intent(c,RecorderService.class).setAction("start"));
   }
  }catch(Exception e){ErrorLog.w(e);Toast.makeText(c,L.s(R.string.s513)+e.getMessage(),Toast.LENGTH_LONG).show();}
 }
 void tickRec(){
  if(recShown!=RecorderService.recording){buildRec();return;}
  if(recTime==null)return;
  if(RecorderService.recording){
   long ms=SystemClock.elapsedRealtime()-RecorderService.startAt;
   recTime.setText(String.format(Locale.US,"%02d:%02d",ms/60000,(ms/1000)%60));recTime.setTextColor(RED);
   wave.push(RecorderService.amp/20000f);
  }else{recTime.setTextColor(Color.WHITE);wave.push(0f);}
  if(recStatus!=null&&!RecorderService.status.isEmpty())recStatus.setText(RecorderService.status);
 }
 static final class AmpWave extends View{
  final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final float[] bars=new float[44];int pos;
  AmpWave(Context c){super(c);}
  void push(float v){bars[pos%bars.length]=Math.max(0f,Math.min(1f,v));pos++;invalidate();}
  @Override protected void onDraw(Canvas cv){
   float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight(),step=w/bars.length,bw=Math.max(2*d,step*.55f);
   p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(bw);
   for(int i=0;i<bars.length;i++){
    float v=bars[(pos+i)%bars.length];float len=Math.max(3*d,v*h);
    p.setColor(v>0.02f?RED:0xff55585c);
    float x=step*(i+.5f);cv.drawLine(x,(h-len)/2,x,(h+len)/2,p);
   }
  }
 }
}
