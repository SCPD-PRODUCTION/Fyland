package id.fyland;

import android.app.*;import android.os.*;import android.content.*;import android.content.pm.*;import android.graphics.Color;import android.graphics.Typeface;import android.graphics.drawable.*;import android.net.Uri;import android.provider.Settings;import android.service.notification.NotificationListenerService;import android.view.*;import android.widget.*;import java.util.*;

public class MainActivity extends Activity {
 static final int BG=0xff041107, CARD=0xff0c2314, ACCENT=0xffa4c397, WHITE=0xffe3ecdc, MUTED=0xff93ab94;
 IslandOverlay demo; boolean started;
 int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 @Override public void onCreate(Bundle b){super.onCreate(b); getWindow().setStatusBarColor(0xff05200f);getWindow().setNavigationBarColor(0xff020c06);migrate();buildFrame();render();started=true;}
 void migrate(){
  if(Prefs.value(this,"schema",0)<2){android.content.SharedPreferences.Editor e=Prefs.get(this).edit().putInt("schema",2);if(Prefs.value(this,"top",8)==8)e.putInt("top",4);if(Prefs.value(this,"height",36)==36)e.putInt("height",32);e.apply();}
  // 1.5: landscape is now shown by default (vertical pill on the camera side); drop the old "hide in landscape" default.
  if(Prefs.value(this,"schema",0)<3)Prefs.get(this).edit().remove("landscape").putInt("schema",3).apply();
 }
 boolean listenerGranted(){String v=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");ComponentName target=new ComponentName(this,IslandListener.class);if(v==null)return false;for(String n:v.split(":"))if(target.equals(ComponentName.unflattenFromString(n)))return true;return false;}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);}
 /** City picker: type to search (Open-Meteo geocoding), tap a result to pick that exact place; "Automatic location" clears it. */
 void askCity(){
  final android.widget.LinearLayout box=new android.widget.LinearLayout(this);box.setOrientation(1);int pd=(int)(16*getResources().getDisplayMetrics().density);box.setPadding(pd,pd/2,pd,0);
  final EditText e=new EditText(this);e.setSingleLine(true);e.setHint(L.s(R.string.s209));e.setText(Prefs.get(this).getString("weather_city",""));
  box.addView(e,new android.widget.LinearLayout.LayoutParams(-1,-2));
  final android.widget.ListView list=new android.widget.ListView(this);
  final java.util.ArrayList<String> labels=new java.util.ArrayList<String>();final java.util.ArrayList<String[]> picks=new java.util.ArrayList<String[]>();
  final android.widget.ArrayAdapter<String> ad=new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,labels);
  list.setAdapter(ad);box.addView(list,new android.widget.LinearLayout.LayoutParams(-1,(int)(220*getResources().getDisplayMetrics().density)));
  final android.os.Handler hh=new android.os.Handler(android.os.Looper.getMainLooper());
  final int[] ticket=new int[1];
  final AlertDialog dlg=new AlertDialog.Builder(this).setTitle(L.s(R.string.s211)).setView(box)
   .setPositiveButton(L.s(R.string.s212),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
    String typed=e.getText().toString().trim();
    android.content.SharedPreferences.Editor ed=Prefs.get(MainActivity.this).edit().putString("weather_city",typed);
    if(typed.isEmpty())ed.remove("weather_coord");else if(!typed.equals(Prefs.get(MainActivity.this).getString("weather_city","")))ed.remove("weather_coord");
    ed.apply();Weather.fetched=0;Weather.failed=false;
   }}).setNeutralButton(L.s(R.string.u_city_auto),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
    Prefs.get(MainActivity.this).edit().remove("weather_city").remove("weather_coord").apply();Weather.fetched=0;Weather.failed=false;
   }}).setNegativeButton(L.s(R.string.s114),null).create();
  list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener(){public void onItemClick(android.widget.AdapterView<?> p,View v,int i,long id){
   if(i<0||i>=picks.size())return;String[] pk=picks.get(i);
   Prefs.get(MainActivity.this).edit().putString("weather_city",pk[0]).putString("weather_coord",pk[1]+","+pk[2]).apply();
   Weather.fetched=0;Weather.failed=false;dlg.dismiss();
  }});
  e.addTextChangedListener(new android.text.TextWatcher(){
   Runnable pending;
   public void beforeTextChanged(CharSequence s,int a,int b,int c){}
   public void onTextChanged(CharSequence s,int a,int b,int c){}
   public void afterTextChanged(android.text.Editable s){
    if(pending!=null)hh.removeCallbacks(pending);
    final String q=s.toString().trim();final int my=++ticket[0];
    if(q.length()<2){labels.clear();picks.clear();ad.notifyDataSetChanged();return;}
    pending=new Runnable(){public void run(){
     new Thread(new Runnable(){public void run(){
      final java.util.ArrayList<String> l2=new java.util.ArrayList<String>();final java.util.ArrayList<String[]> p2=new java.util.ArrayList<String[]>();
      try{
       org.json.JSONObject g=new org.json.JSONObject(Weather.get("https://geocoding-api.open-meteo.com/v1/search?count=8&language="+java.util.Locale.getDefault().getLanguage()+"&name="+java.net.URLEncoder.encode(q,"UTF-8")));
       org.json.JSONArray r=g.optJSONArray("results");
       if(r!=null)for(int i=0;i<r.length();i++){
        org.json.JSONObject o=r.getJSONObject(i);String n=o.optString("name"),a1=o.optString("admin1"),co=o.optString("country");
        StringBuilder sb=new StringBuilder(n);if(!a1.isEmpty()&&!a1.equals(n))sb.append(", ").append(a1);if(!co.isEmpty())sb.append(", ").append(co);
        l2.add(sb.toString());p2.add(new String[]{n,String.valueOf(o.getDouble("latitude")),String.valueOf(o.getDouble("longitude"))});
       }
      }catch(Exception ex){ErrorLog.w(ex);}
      hh.post(new Runnable(){public void run(){if(my!=ticket[0])return;labels.clear();labels.addAll(l2);picks.clear();picks.addAll(p2);ad.notifyDataSetChanged();}});
     }}).start();
    }};
    hh.postDelayed(pending,350);
   }
  });
  dlg.show();
 }
 void askMic(){requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},7);}
 void askCalendar(){requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR,android.Manifest.permission.WRITE_CALENDAR},8);}
 @Override public void onResume(){super.onResume();if(busy==2)busy=0;if(getIntent()!=null&&getIntent().getBooleanExtra("ask_mic",false)){getIntent().removeExtra("ask_mic");askMic();}
  if(getIntent()!=null&&getIntent().getBooleanExtra("ask_cal",false)){getIntent().removeExtra("ask_cal");askCalendar();}
  IslandListener.uiSuspended=false;Prefs.update(this);if(started){render();if(listenerGranted()&&IslandListener.live==null)NotificationListenerService.requestRebind(new ComponentName(this,IslandListener.class));}
  if(pending!=null){String next=pending;pending=null;if(!restricted()){busy=2;launch(next);}}else nextStep();}
 String pending; // settings page to open right after the user allowed restricted settings
 /** Android 13+ blocks notification access and accessibility for apps installed from an APK until "Allow restricted settings" is chosen in App info. An app cannot tick that for the user. */
 boolean restricted(){
  if(Build.VERSION.SDK_INT<33)return false;
  try{AppOpsManager a=(AppOpsManager)getSystemService(Context.APP_OPS_SERVICE);int m=a.unsafeCheckOpNoThrow("android:access_restricted_settings",android.os.Process.myUid(),getPackageName());return m==AppOpsManager.MODE_ERRORED||m==AppOpsManager.MODE_IGNORED;}catch(Throwable e){ErrorLog.w(e);return false;}
 }
 void gate(String action){if(restricted())askRestricted(action);else launch(action);}
 void askRestricted(final String action){
  new AlertDialog.Builder(this).setTitle(L.s(R.string.s216))
   .setMessage(L.s(R.string.s217))
   .setPositiveButton(L.s(R.string.s99),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){pending=action;launch(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()));}})
   .setNegativeButton(L.s(R.string.s114),null).show();
 }
 void pauseOverlay(){IslandListener.uiSuspended=true;IslandListener.suspendedAt=android.os.SystemClock.elapsedRealtime();IslandAccess.refreshStandalone();if(demo!=null)demo.destroy();if(IslandListener.live!=null&&IslandListener.live.overlay!=null)IslandListener.live.overlay.hide();}
 void launch(String action){pauseOverlay();try{startActivity(new Intent(action));}catch(Exception e){ErrorLog.w(e);Toast.makeText(this,L.s(R.string.s219),Toast.LENGTH_LONG).show();}}
 /** Errors caught anywhere in the app (and crashes), newest last. */
 void showErrorLog(){
  final TextView t=new TextView(this);t.setText(ErrorLog.read());t.setTextSize(11);t.setTypeface(android.graphics.Typeface.MONOSPACE);t.setTextIsSelectable(true);t.setPadding(40,20,40,20);
  android.widget.ScrollView sv=new android.widget.ScrollView(this);sv.addView(t);
  new AlertDialog.Builder(this).setTitle(L.s(R.string.s220)).setView(sv)
   .setPositiveButton(L.s(R.string.s221),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
    android.content.ClipboardManager cm=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
    if(cm!=null){cm.setPrimaryClip(android.content.ClipData.newPlainText("log",ErrorLog.read()));Toast.makeText(MainActivity.this,L.s(R.string.s223),Toast.LENGTH_SHORT).show();}
   }})
   .setNeutralButton(L.s(R.string.s224),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){ErrorLog.clear();render();}})
   .setNegativeButton(L.s(R.string.s101),null).show();
 }
 // ---------- screens ----------
 FrameLayout frame; ScrollView scroll; LinearLayout content, navBox; int tab; final ArrayList<String> stack=new ArrayList<>(); int inL,inT,inR,inB; String lastKey="";
 String s(int id){return L.s(id);}
 TextView tv(String value,float size,int color){TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(size);return t;}
 void buildFrame(){
  frame=new FrameLayout(this);frame.addView(new Ui.Backdrop(this),new FrameLayout.LayoutParams(-1,-1));
  scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.setFillViewport(true);
  content=new LinearLayout(this);content.setOrientation(1);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));frame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
  navBox=new LinearLayout(this);((LinearLayout)navBox).setOrientation(0);navBox.setBackground(Ui.card(this,32));
  FrameLayout.LayoutParams nl=new FrameLayout.LayoutParams(-1,dp(66),Gravity.BOTTOM);nl.leftMargin=dp(16);nl.rightMargin=dp(16);frame.addView(navBox,nl);
  frame.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener(){public WindowInsets onApplyWindowInsets(View v,WindowInsets i){inL=i.getSystemWindowInsetLeft();inT=i.getSystemWindowInsetTop();inR=i.getSystemWindowInsetRight();inB=i.getSystemWindowInsetBottom();applyPad();return i;}});
  setContentView(frame);
 }
 void applyPad(){
  content.setPadding(inL+dp(18),inT+dp(14),inR+dp(18),inB+dp(112));
  FrameLayout.LayoutParams nl=(FrameLayout.LayoutParams)navBox.getLayoutParams();nl.bottomMargin=inB+dp(12);nl.leftMargin=inL+dp(16);nl.rightMargin=inR+dp(16);navBox.setLayoutParams(nl);
 }
 void go(String page){stack.add(page);render();}
 void back(){if(!stack.isEmpty()){stack.remove(stack.size()-1);render();}}
 @Override public void onBackPressed(){if(!stack.isEmpty())back();else if(tab!=0){tab=0;render();}else super.onBackPressed();}
 void buildNav(){
  navBox.removeAllViews();
  int[] icons={R.drawable.u_home,R.drawable.u_grid,R.drawable.u_settings,R.drawable.u_info};
  String[] names={s(R.string.u_nav_home),s(R.string.u_nav_features),s(R.string.u_nav_settings),s(R.string.u_nav_about)};
  for(int k=0;k<4;k++){
   final int idx=k;boolean sel=k==tab;
   LinearLayout it=new LinearLayout(this);it.setOrientation(1);it.setGravity(Gravity.CENTER);
   ImageView ic=Ui.iconView(this,icons[k],sel?Ui.DOT:Ui.MUTED);it.addView(ic,new LinearLayout.LayoutParams(dp(24),dp(24)));
   TextView t=tv(names[k],11.5f,sel?Ui.DOT:Ui.MUTED);t.setGravity(Gravity.CENTER);t.setSingleLine(true);t.setPadding(0,dp(2),0,0);it.addView(t);
   View bar=new View(this);bar.setBackground(Ui.fill(this,sel?Ui.DOT:0,2));LinearLayout.LayoutParams bl=new LinearLayout.LayoutParams(dp(26),dp(3));bl.topMargin=dp(3);it.addView(bar,bl);
   Ui.click(it,new Runnable(){public void run(){tab=idx;stack.clear();render();}});
   navBox.addView(it,new LinearLayout.LayoutParams(0,-1,1));
  }
 }
 void render(){
  if(frame==null)return;
  String page=stack.isEmpty()?null:stack.get(stack.size()-1);String key=page==null?"tab"+tab:page;int sy=key.equals(lastKey)?scroll.getScrollY():0;lastKey=key;
  content.removeAllViews();applyPad();buildNav();
  if(page==null){if(tab==0)home();else if(tab==1)featuresTab();else if(tab==2)settingsTab();else aboutTab();}
  else detail(page);
  final int y=sy;scroll.post(new Runnable(){public void run(){scroll.scrollTo(0,y);}});
 }
 // ----- building blocks -----
 LinearLayout group(){
  LinearLayout g=new LinearLayout(this);g.setOrientation(1);g.setBackground(Ui.card(this,22));g.setClipToOutline(true);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);content.addView(g,lp);return g;
 }
 void applyRipple(View v){v.setForeground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x33b9e3a9),null,new ColorDrawable(0xffffffff)));}
 LinearLayout row(LinearLayout g,int icon,String title,String sub,View trailing,Runnable click){
  if(g.getChildCount()>0){View d=new View(this);d.setBackgroundColor(Ui.LINE);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,1);dl.leftMargin=dp(icon!=0?70:16);dl.rightMargin=dp(14);g.addView(d,dl);}
  LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(dp(14),dp(12),dp(14),dp(12));
  if(icon!=0)r.addView(Ui.tile(this,icon,42),new LinearLayout.LayoutParams(dp(42),dp(42)));
  LinearLayout words=new LinearLayout(this);words.setOrientation(1);
  words.addView(tv(title,16,Ui.INK));
  if(sub!=null){TextView d=tv(sub,12.5f,Ui.MUTED);d.setPadding(0,dp(2),0,0);d.setLineSpacing(dp(2),1);words.addView(d);}
  LinearLayout.LayoutParams wl=new LinearLayout.LayoutParams(0,-2,1);wl.leftMargin=icon!=0?dp(14):0;r.addView(words,wl);
  if(trailing!=null){LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(-2,-2);tl.leftMargin=dp(10);r.addView(trailing,tl);}
  if(click!=null){Ui.click(r,click);applyRipple(r);}
  g.addView(r,new LinearLayout.LayoutParams(-1,-2));return r;
 }
 ImageView chevron(){ImageView v=Ui.iconView(this,R.drawable.u_chevron,Ui.MUTED);v.setLayoutParams(new LinearLayout.LayoutParams(dp(22),dp(22)));return v;}
 LinearLayout link(LinearLayout g,int icon,String title,String sub,Runnable click){return row(g,icon,title,sub,chevron(),click);}
 void setFlag(String key,boolean v){Prefs.get(this).edit().putBoolean(key,v).apply();Prefs.update(this);}
 void toggleX(LinearLayout g,int icon,String t,String d,boolean init,Ui.Toggle.OnChange cb){
  final Ui.Toggle tg=new Ui.Toggle(this,init,cb);LinearLayout r=row(g,icon,t,d,tg,null);
  Ui.click(r,new Runnable(){public void run(){tg.performClick();}});applyRipple(r);
 }
 void toggle(LinearLayout g,int icon,String t,String d,final String key,boolean def){toggleX(g,icon,t,d,Prefs.flag(this,key,def),new Ui.Toggle.OnChange(){public void on(boolean v){setFlag(key,v);}});}
 void toggleInv(LinearLayout g,int icon,String t,String d,final String key,boolean def){toggleX(g,icon,t,d,!Prefs.flag(this,key,def),new Ui.Toggle.OnChange(){public void on(boolean v){setFlag(key,!v);}});}
 void slider(LinearLayout g,final String title,final String key,final int min,int max,int def,final String unit){
  if(g.getChildCount()>0){View d=new View(this);d.setBackgroundColor(Ui.LINE);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,1);dl.leftMargin=dp(16);dl.rightMargin=dp(14);g.addView(d,dl);}
  LinearLayout r=new LinearLayout(this);r.setOrientation(1);r.setPadding(dp(16),dp(12),dp(16),dp(6));
  LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
  top.addView(tv(title,15,Ui.INK),new LinearLayout.LayoutParams(0,-2,1));
  int cur=Prefs.value(this,key,def);final TextView val=tv(cur+unit,14,Ui.MINT);top.addView(val);r.addView(top);
  SeekBar sb=new SeekBar(this);sb.setMax(max-min);sb.setProgress(cur-min);
  sb.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.DOT));sb.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.MINT));sb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff25402f));
  r.addView(sb,new LinearLayout.LayoutParams(-1,dp(40)));
  sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int p,boolean user){if(user){Prefs.get(MainActivity.this).edit().putInt(key,p+min).apply();val.setText((p+min)+unit);Prefs.update(MainActivity.this);}}});
  g.addView(r,new LinearLayout.LayoutParams(-1,-2));
 }
 void header(String title,String sub,boolean showBack){
  LinearLayout h=new LinearLayout(this);h.setOrientation(1);h.setPadding(dp(4),dp(4),0,dp(16));
  if(showBack){
   FrameLayout b=new FrameLayout(this);b.setBackground(Ui.card(this,20));ImageView i=Ui.iconView(this,R.drawable.u_back,Ui.INK);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER);b.addView(i,ip);
   b.setContentDescription(s(R.string.u_back));Ui.click(b,new Runnable(){public void run(){back();}});
   LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(42),dp(42));bp.bottomMargin=dp(14);h.addView(b,bp);
  }
  TextView t=tv(title,30,Ui.INK);t.setTypeface(null,Typeface.BOLD);h.addView(t);
  if(sub!=null){TextView d=tv(sub,14.5f,Ui.MUTED);d.setPadding(0,dp(4),0,0);h.addView(d);}
  content.addView(h,new LinearLayout.LayoutParams(-1,-2));
 }
 void heading(int icon,String text){
  LinearLayout h=new LinearLayout(this);h.setGravity(Gravity.CENTER_VERTICAL);h.setPadding(dp(4),dp(6),0,dp(10));
  if(icon!=0)h.addView(Ui.iconView(this,icon,Ui.MINT),new LinearLayout.LayoutParams(dp(24),dp(24)));
  TextView t=tv(text,17,Ui.INK);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));t.setPadding(icon!=0?dp(12):0,0,0,0);h.addView(t);
  content.addView(h,new LinearLayout.LayoutParams(-1,-2));
 }
 // ----- home -----
 boolean ready(){return listenerGranted()&&(Settings.canDrawOverlays(this)||IslandAccess.live!=null);}
 void home(){
  LinearLayout hero=new LinearLayout(this);hero.setOrientation(1);hero.setPadding(dp(4),dp(10),0,dp(18));
  LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
  LinearLayout name=new LinearLayout(this);name.setGravity(Gravity.CENTER_VERTICAL);
  ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.u_logo);logo.setAdjustViewBounds(true);name.addView(logo,new LinearLayout.LayoutParams(dp(54),dp(51)));
  TextView word=tv("Fyland",40,Ui.INK);word.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));word.setPadding(dp(8),0,0,0);word.setSingleLine(true);name.addView(word);
  top.addView(name,new LinearLayout.LayoutParams(0,-2,1));
  ImageView tile=new ImageView(this);tile.setImageResource(R.drawable.u_icon_tile);top.addView(tile,new LinearLayout.LayoutParams(dp(78),dp(78)));
  hero.addView(top);
  TextView t1=tv(s(R.string.u_hero_tag1),19,Ui.INK);t1.setPadding(0,dp(8),0,0);hero.addView(t1);
  TextView t2=tv(s(R.string.u_hero_tag2),13.5f,Ui.MUTED);t2.setPadding(0,dp(4),dp(60),0);hero.addView(t2);
  content.addView(hero,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout w=group();
  if(ready())link(w,R.drawable.u_sparkle,s(R.string.u_st_ok_t),s(R.string.u_st_ok_d),new Runnable(){public void run(){preview();}});
  else link(w,R.drawable.u_sparkle,s(R.string.u_st_setup_t),s(R.string.u_st_setup_d),new Runnable(){public void run(){go("setup");}});
  LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
  LinearLayout hl=new LinearLayout(this);hl.setGravity(Gravity.CENTER_VERTICAL);hl.setPadding(dp(4),dp(6),0,dp(10));hl.addView(Ui.iconView(this,R.drawable.u_grid,Ui.MINT),new LinearLayout.LayoutParams(dp(24),dp(24)));
  TextView ht=tv(s(R.string.u_main_features),17,Ui.INK);ht.setPadding(dp(12),0,0,0);ht.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));hl.addView(ht);head.addView(hl,new LinearLayout.LayoutParams(0,-2,1));
  TextView all=tv(s(R.string.u_see_all),13.5f,Ui.MUTED);all.setPadding(dp(8),dp(6),dp(2),dp(6));head.addView(all);ImageView ac=Ui.iconView(this,R.drawable.u_chevron,Ui.MUTED);head.addView(ac,new LinearLayout.LayoutParams(dp(20),dp(20)));
  Ui.click(head,new Runnable(){public void run(){tab=1;stack.clear();render();}});content.addView(head,new LinearLayout.LayoutParams(-1,-2));
  boolean notifOn=Prefs.flag(this,"enabled",true)&&listenerGranted();
  cardRow(featureCard(R.drawable.u_island,s(R.string.u_f_island),s(R.string.u_f_island_d),"island",false),featureCard(R.drawable.u_bell,s(R.string.u_f_notif),s(R.string.u_f_notif_d),"notif",notifOn));
  cardRow(featureCard(R.drawable.u_music,s(R.string.u_f_music),s(R.string.u_f_music_d),"music",false),featureCard(R.drawable.u_star,s(R.string.u_f_events),s(R.string.u_f_events_d),"events",false));
  cardRow(featureCard(R.drawable.u_clock,s(R.string.u_f_timer),s(R.string.u_f_timer_d),"timer",false),featureCard(R.drawable.u_grid,s(R.string.u_f_pages),s(R.string.u_f_pages_d),"pages",false));
  heading(R.drawable.u_settings,s(R.string.u_tools));
  LinearLayout tr=new LinearLayout(this);
  tr.addView(toolCard(R.drawable.u_cloud,s(R.string.u_t_weather),s(R.string.u_t_weather_d),new Runnable(){public void run(){askCity();}}),cellLp(true));
  tr.addView(toolCard(R.drawable.u_clock,s(R.string.u_t_clock),s(R.string.u_t_clock_d),new Runnable(){public void run(){go("pages");}}),cellLp(true));
  tr.addView(toolCard(R.drawable.u_calendar,s(R.string.u_t_calendar),s(R.string.u_t_calendar_d),new Runnable(){public void run(){go("pages");}}),cellLp(false));
  LinearLayout.LayoutParams trp=new LinearLayout.LayoutParams(-1,-2);trp.bottomMargin=dp(14);content.addView(tr,trp);
  heading(R.drawable.u_note,s(R.string.u_others));
  cardRow(smallCard(R.drawable.u_note,s(R.string.u_o_notes),s(R.string.u_o_notes_d),new Runnable(){public void run(){go("pages");}}),smallCard(R.drawable.u_note,s(R.string.u_o_log),s(R.string.u_o_log_d),new Runnable(){public void run(){showErrorLog();}}));
 }
 LinearLayout.LayoutParams cellLp(boolean gap){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(gap)p.rightMargin=dp(10);return p;}
 void cardRow(View a,View b){
  LinearLayout r=new LinearLayout(this);r.addView(a,cellLp(true));r.addView(b,cellLp(false));
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(10);content.addView(r,p);
 }
 View featureCard(int icon,String title,String sub,final String page,boolean dot){
  FrameLayout f=new FrameLayout(this);f.setBackground(Ui.card(this,20));f.setClipToOutline(true);
  LinearLayout c=new LinearLayout(this);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(12),dp(12),dp(10),dp(12));c.setMinimumHeight(dp(92));
  c.addView(Ui.tile(this,icon,40),new LinearLayout.LayoutParams(dp(40),dp(40)));
  LinearLayout words=new LinearLayout(this);words.setOrientation(1);
  TextView t=tv(title,15,Ui.INK);t.setTypeface(null,Typeface.BOLD);t.setMaxLines(2);words.addView(t);
  TextView d=tv(sub,11,Ui.MUTED);d.setMaxLines(3);d.setEllipsize(android.text.TextUtils.TruncateAt.END);d.setPadding(0,dp(2),0,0);words.addView(d);
  LinearLayout.LayoutParams wl=new LinearLayout.LayoutParams(0,-2,1);wl.leftMargin=dp(10);c.addView(words,wl);
  f.addView(c,new FrameLayout.LayoutParams(-1,-1));
  if(dot){View v=new View(this);v.setBackground(Ui.fill(this,Ui.DOT,5));FrameLayout.LayoutParams dp5=new FrameLayout.LayoutParams(dp(10),dp(10),Gravity.TOP|Gravity.END);dp5.topMargin=dp(10);dp5.rightMargin=dp(12);f.addView(v,dp5);}
  Ui.click(f,new Runnable(){public void run(){go(page);}});applyRipple(f);return f;
 }
 View toolCard(int icon,String title,String sub,Runnable click){
  LinearLayout c=new LinearLayout(this);c.setOrientation(1);c.setBackground(Ui.card(this,20));c.setClipToOutline(true);c.setPadding(dp(12),dp(12),dp(10),dp(12));
  c.addView(Ui.tile(this,icon,38),new LinearLayout.LayoutParams(dp(38),dp(38)));
  TextView t=tv(title,15,Ui.INK);t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(8),0,0);c.addView(t);
  TextView d=tv(sub,11,Ui.MUTED);d.setMaxLines(2);d.setEllipsize(android.text.TextUtils.TruncateAt.END);d.setPadding(0,dp(2),0,0);c.addView(d);
  Ui.click(c,click);applyRipple(c);return c;
 }
 View smallCard(int icon,String title,String sub,Runnable click){
  LinearLayout c=new LinearLayout(this);c.setGravity(Gravity.CENTER_VERTICAL);c.setBackground(Ui.card(this,20));c.setClipToOutline(true);c.setPadding(dp(12),dp(12),dp(8),dp(12));
  c.addView(Ui.tile(this,icon,38),new LinearLayout.LayoutParams(dp(38),dp(38)));
  LinearLayout words=new LinearLayout(this);words.setOrientation(1);
  TextView t=tv(title,15,Ui.INK);t.setTypeface(null,Typeface.BOLD);t.setSingleLine(true);words.addView(t);
  TextView d=tv(sub,11,Ui.MUTED);d.setMaxLines(2);d.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(d);
  LinearLayout.LayoutParams wl=new LinearLayout.LayoutParams(0,-2,1);wl.leftMargin=dp(10);c.addView(words,wl);
  c.addView(chevron());Ui.click(c,click);applyRipple(c);return c;
 }
 // ----- features tab -----
 void featuresTab(){
  header(s(R.string.u_feat_t),s(R.string.u_feat_d),false);
  LinearLayout g=group();
  link(g,R.drawable.u_island,s(R.string.u_f_island),s(R.string.u_f_island_d),goTo("island"));
  link(g,R.drawable.u_bell,s(R.string.u_f_notif),s(R.string.u_f_notif_d),goTo("notif"));
  link(g,R.drawable.u_music,s(R.string.u_f_music),s(R.string.u_f_music_d),goTo("music"));
  link(g,R.drawable.u_star,s(R.string.u_f_events),s(R.string.u_f_events_d),goTo("events"));
  link(g,R.drawable.u_clock,s(R.string.u_f_timer),s(R.string.u_f_timer_d),goTo("timer"));
  link(g,R.drawable.u_grid,s(R.string.u_f_pages),s(R.string.u_f_pages_d),goTo("pages"));
  heading(R.drawable.u_settings,s(R.string.u_tools));
  LinearLayout t=group();
  link(t,R.drawable.u_cloud,s(R.string.u_t_weather),s(R.string.u_t_weather_d),new Runnable(){public void run(){askCity();}});
  toggle(t,R.drawable.u_cloud,s(R.string.u_w_f),s(R.string.u_w_f_d),"weather_f",Weather.fahrenheitDefault());
  toggle(t,R.drawable.u_clock,s(R.string.u_w_24),s(R.string.u_w_24_d),"weather_24h",false);
  link(t,R.drawable.u_clock,s(R.string.u_t_clock),s(R.string.u_t_clock_d),goTo("pages"));
  link(t,R.drawable.u_calendar,s(R.string.u_t_calendar),s(R.string.u_t_calendar_d),goTo("pages"));
  heading(R.drawable.u_note,s(R.string.u_others));
  LinearLayout o=group();
  link(o,R.drawable.u_note,s(R.string.u_o_notes),s(R.string.u_o_notes_d),goTo("pages"));
  link(o,R.drawable.u_note,s(R.string.u_o_log),s(R.string.u_o_log_d),new Runnable(){public void run(){showErrorLog();}});
 }
 Runnable goTo(final String page){return new Runnable(){public void run(){go(page);}};}
 // ----- settings tab (as in the design) -----
 void settingsTab(){
  header(s(R.string.u_set_t),s(R.string.u_set_d),false);
  LinearLayout app=group();
  ImageView ic=new ImageView(this);ic.setImageResource(R.drawable.u_icon_tile);
  LinearLayout r=row(app,0,"Fyland","v"+versionName()+"\n"+s(R.string.u_hero_tag1),chevron(),new Runnable(){public void run(){tab=3;stack.clear();render();}});
  r.addView(ic,0,new LinearLayout.LayoutParams(dp(64),dp(64)));((LinearLayout.LayoutParams)r.getChildAt(1).getLayoutParams()).leftMargin=dp(14);
  heading(R.drawable.u_palette,s(R.string.u_g_appearance));
  LinearLayout a=group();
  link(a,R.drawable.u_desktop,s(R.string.u_r_size),s(R.string.u_r_size_d),goTo("size"));
  toggleInv(a,R.drawable.u_phone_land,s(R.string.u_r_land),s(R.string.u_r_land_d),"landscape",false);
  link(a,R.drawable.u_sparkle,s(R.string.u_r_anim),s(R.string.u_r_anim_d),goTo("anim"));
  link(a,R.drawable.u_play,s(R.string.u_r_try),s(R.string.u_r_try_d),new Runnable(){public void run(){demoAll();}});
  heading(R.drawable.u_bell,s(R.string.u_g_notif));
  notifRows(group());
  heading(R.drawable.u_music,s(R.string.u_g_music));
  LinearLayout m=group();link(m,R.drawable.u_music,s(R.string.u_r_mctl),s(R.string.u_r_mctl_d),goTo("music"));
  heading(R.drawable.u_settings,s(R.string.u_g_system));
  LinearLayout y=group();
  link(y,R.drawable.u_tune,s(R.string.u_r_conn),s(R.string.u_r_conn_d),goTo("setup"));
  toggle(y,R.drawable.u_island,s(R.string.u_r_enable),s(R.string.u_r_enable_d),"enabled",true);
  toggle(y,R.drawable.u_bolt,s(R.string.u_r_keep),s(R.string.u_r_keep_d),"keepalive",true);
 }
 String versionName(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "1.0";}}
 void notifRows(LinearLayout g){
  toggleX(g,R.drawable.u_bell,s(R.string.u_r_block),s(R.string.u_r_block_d),Prefs.flag(this,"block",false),new Ui.Toggle.OnChange(){public void on(boolean value){
   if(!value){setFlag("block",false);return;}
   new AlertDialog.Builder(MainActivity.this).setTitle(s(R.string.s248)).setMessage(s(R.string.s249))
    .setPositiveButton(s(R.string.s250),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){setFlag("block",true);render();}})
    .setNegativeButton(s(R.string.s114),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){render();}})
    .setOnCancelListener(new DialogInterface.OnCancelListener(){public void onCancel(DialogInterface d){render();}}).show();
  }});
  toggle(g,R.drawable.u_bell_off,s(R.string.u_r_quiet),s(R.string.u_r_quiet_d),"quiet",true);
  toggleInv(g,R.drawable.u_lock,s(R.string.u_r_lock),s(R.string.u_r_lock_d),"lock_content",false);
  link(g,R.drawable.u_grid,s(R.string.u_r_apps),s(R.string.u_r_apps_d),new Runnable(){public void run(){apps();}});
  final int cur=Prefs.value(this,"duration",8);
  LinearLayout dv=new LinearLayout(this);dv.setGravity(Gravity.CENTER_VERTICAL);dv.addView(tv(cur+s(R.string.s268),14.5f,Ui.INK));dv.addView(Ui.iconView(this,R.drawable.u_chevron_down,Ui.MUTED),new LinearLayout.LayoutParams(dp(22),dp(22)));
  row(g,R.drawable.u_clock,s(R.string.u_r_dur),s(R.string.u_r_dur_d),dv,new Runnable(){public void run(){askDuration();}});
  toggle(g,R.drawable.u_bolt,s(R.string.u_r_expand),s(R.string.u_r_expand_d),"expand",false);
  toggle(g,R.drawable.u_note,s(R.string.u_r_content),s(R.string.u_r_content_d),"content",true);
  toggle(g,R.drawable.u_bell_off,s(R.string.u_r_silent),s(R.string.u_r_silent_d),"silent",true);
  toggle(g,R.drawable.u_bell,s(R.string.u_r_keepreply),s(R.string.u_r_keepreply_d),"keep_reply",false);
 }
 void askDuration(){
  final int[] v={3,5,8,10,15,20,30};String[] l=new String[v.length];int cur=Prefs.value(this,"duration",8),sel=-1;
  for(int i=0;i<v.length;i++){l[i]=v[i]+s(R.string.s268);if(v[i]==cur)sel=i;}
  new AlertDialog.Builder(this).setTitle(s(R.string.u_r_dur)).setSingleChoiceItems(l,sel,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
   Prefs.get(MainActivity.this).edit().putInt("duration",v[w]).apply();Prefs.update(MainActivity.this);d.dismiss();render();
  }}).setNegativeButton(s(R.string.s114),null).show();
 }
 // ----- about tab -----
 void aboutTab(){
  LinearLayout top=new LinearLayout(this);top.setOrientation(1);top.setGravity(Gravity.CENTER_HORIZONTAL);top.setPadding(0,dp(20),0,dp(22));
  ImageView tile=new ImageView(this);tile.setImageResource(R.drawable.u_icon_tile);top.addView(tile,new LinearLayout.LayoutParams(dp(96),dp(96)));
  TextView n=tv("Fyland",30,Ui.INK);n.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));n.setPadding(0,dp(12),0,0);top.addView(n);
  TextView v=tv("v"+versionName(),14,Ui.MINT);v.setPadding(0,dp(2),0,dp(6));top.addView(v);
  TextView t=tv(s(R.string.u_hero_tag1),16,Ui.INK);top.addView(t);TextView t2=tv(s(R.string.u_hero_tag2),13,Ui.MUTED);t2.setGravity(Gravity.CENTER);t2.setPadding(dp(30),dp(4),dp(30),0);top.addView(t2);
  content.addView(top,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout g=group();
  row(g,R.drawable.u_info,s(R.string.u_about_ver),versionName(),null,null);
  row(g,R.drawable.u_settings,s(R.string.u_about_pkg),getPackageName(),null,null);
  link(g,R.drawable.u_tune,s(R.string.u_r_conn),s(R.string.u_r_conn_d),goTo("setup"));
  final int errN=ErrorLog.count();
  link(g,R.drawable.u_note,s(R.string.s220),errN==0?s(R.string.s338):s(R.string.s339)+errN+")",new Runnable(){public void run(){showErrorLog();}});
  LinearLayout h=group();h.setPadding(dp(18),dp(16),dp(18),dp(16));
  TextView ht=tv(s(R.string.s340),16,Ui.INK);ht.setTypeface(null,Typeface.BOLD);h.addView(ht);
  TextView hb=tv(s(R.string.s341),13,Ui.MUTED);hb.setLineSpacing(dp(3),1);hb.setPadding(0,dp(8),0,0);h.addView(hb);
  TextView foot=tv(s(R.string.u_about_made),12,Ui.MUTED);foot.setGravity(Gravity.CENTER);foot.setPadding(0,dp(8),0,0);content.addView(foot,new LinearLayout.LayoutParams(-1,-2));
 }
 // ----- detail pages -----
 void detail(String p){
  if(p.equals("island")){
   header(s(R.string.u_f_island),s(R.string.u_f_island_d),true);LinearLayout g=group();
   toggle(g,R.drawable.u_island,s(R.string.u_r_enable),s(R.string.u_r_enable_d),"enabled",true);
   toggle(g,R.drawable.u_bolt,s(R.string.u_r_keep),s(R.string.u_r_keep_d),"keepalive",true);
   link(g,R.drawable.u_desktop,s(R.string.u_r_size),s(R.string.u_r_size_d),goTo("size"));
   toggleInv(g,R.drawable.u_phone_land,s(R.string.u_r_land),s(R.string.s264),"landscape",false);
   link(g,R.drawable.u_play,s(R.string.u_r_try_notif),s(R.string.u_r_try_notif_d),new Runnable(){public void run(){preview();}});
   link(g,R.drawable.u_tune,s(R.string.u_r_conn),s(R.string.u_r_conn_d),goTo("setup"));
  }else if(p.equals("size")){
   header(s(R.string.u_r_size),s(R.string.u_r_size_d),true);
   heading(0,s(R.string.u_g_portrait));LinearLayout g=group();
   slider(g,s(R.string.s270),"width",90,250,136," dp");slider(g,s(R.string.s271),"notif_width",120,320,220," dp");slider(g,s(R.string.s272),"height",24,65,32," dp");slider(g,s(R.string.s273),"top",0,150,4," dp");slider(g,s(R.string.s274),"offset",-130,130,0," dp");
   LinearLayout g2=group();
   toggle(g2,R.drawable.u_desktop,s(R.string.s275),s(R.string.s276),"safe_status",true);
   slider(g2,s(R.string.s277),"guard_left",30,150,60," dp");slider(g2,s(R.string.s278),"guard_right",50,180,104," dp");
   toggle(g2,R.drawable.u_desktop,s(R.string.s279),s(R.string.s280),"cover_status",true);
   slider(g2,s(R.string.s281),"cover_pad",6,48,18," dp");
   heading(0,s(R.string.u_g_landscape));LinearLayout g3=group();
   slider(g3,s(R.string.s283),"land_thickness",16,60,24," dp");slider(g3,s(R.string.s284),"land_length",50,250,92," dp");slider(g3,s(R.string.s285),"land_edge",0,40,6," dp");slider(g3,s(R.string.s286),"land_offset",-200,200,0," dp");
  }else if(p.equals("notif")){
   header(s(R.string.u_f_notif),s(R.string.u_f_notif_d),true);notifRows(group());
  }else if(p.equals("music")){
   header(s(R.string.u_f_music),s(R.string.u_f_music_d),true);LinearLayout g=group();
   toggle(g,R.drawable.u_music,s(R.string.s326),s(R.string.s327),"music",true);
   toggle(g,R.drawable.u_note,s(R.string.s328),s(R.string.s329),"cover_bg",true);
   slider(g,s(R.string.s330),"music_width",90,220,152," dp");
   toggle(g,R.drawable.u_note,s(R.string.s331),s(R.string.s332),"lyrics_online",true);
  }else if(p.equals("events")){
   header(s(R.string.u_f_events),s(R.string.u_f_events_d),true);LinearLayout g=group();
   toggle(g,R.drawable.u_bell_off,s(R.string.s297),s(R.string.s298),"ev_ringer",true);
   toggle(g,R.drawable.u_star,s(R.string.s300),s(R.string.s301),"ev_dnd",true);
   toggle(g,R.drawable.u_bolt,s(R.string.s303),s(R.string.s304),"ev_torch",true);
   toggle(g,R.drawable.u_bolt,s(R.string.s306),s(R.string.s307),"ev_battery",true);
   toggleX(g,R.drawable.u_headphones,s(R.string.s309),s(R.string.s310),Prefs.flag(this,"ev_bt",true),new Ui.Toggle.OnChange(){public void on(boolean value){
    setFlag("ev_bt",value);
    if(value&&Build.VERSION.SDK_INT>=31&&checkSelfPermission("android.permission.BLUETOOTH_CONNECT")!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"},7);
   }});
   toggle(g,R.drawable.u_info,s(R.string.s312),s(R.string.s313),"ev_mic",true);
   toggle(g,R.drawable.u_bolt,s(R.string.s333),s(R.string.s334),"charging",true);
   toggle(g,R.drawable.u_lock,"Face Unlock",(SystemEvents.faceSupported(this)?s(R.string.s317):s(R.string.s318))+s(R.string.s319),"ev_face",false);
   toggle(g,R.drawable.u_bell,s(R.string.s321),s(R.string.s322),"ev_call",true);
   LinearLayout d=group();link(d,R.drawable.u_play,s(R.string.u_r_try),s(R.string.u_r_try_d),new Runnable(){public void run(){demoAll();}});
  }else if(p.equals("timer")){
   header(s(R.string.u_f_timer),s(R.string.u_f_timer_d),true);LinearLayout g=group();
   toggle(g,R.drawable.u_clock,s(R.string.s314),s(R.string.s315),"ev_timer",true);
   link(g,R.drawable.u_grid,s(R.string.u_f_pages),s(R.string.u_t_clock_d),goTo("pages"));
  }else if(p.equals("pages")){
   header(s(R.string.u_f_pages),s(R.string.u_f_pages_d),true);LinearLayout g=group();
   toggle(g,R.drawable.u_grid,s(R.string.s288),s(R.string.s289),"pages",true);
   row(g,R.drawable.u_note,s(R.string.u_r_pages_tools),s(R.string.u_r_pages_tools_d),null,null);
   LinearLayout t=group();
   link(t,R.drawable.u_cloud,s(R.string.u_r_city),s(R.string.u_r_city_d),new Runnable(){public void run(){askCity();}});
   link(t,R.drawable.u_calendar,s(R.string.u_r_cal),s(R.string.u_r_cal_d),new Runnable(){public void run(){askCalendar();}});
  }else if(p.equals("anim")){
   header(s(R.string.u_r_anim),s(R.string.u_r_anim_d),true);LinearLayout g=group();
   toggle(g,R.drawable.u_sparkle,s(R.string.s293),s(R.string.s294),"anim",true);
   toggle(g,R.drawable.u_sparkle,s(R.string.s295),s(R.string.s296),"blur",true);
   link(g,R.drawable.u_play,s(R.string.u_r_try),s(R.string.u_r_try_d),new Runnable(){public void run(){demoAll();}});
  }else if(p.equals("setup")){
   header(s(R.string.u_r_conn),s(R.string.s234),true);LinearLayout g=group();
   boolean ov=Settings.canDrawOverlays(this),ls=listenerGranted(),ac=IslandAccess.live!=null;
   permRow(g,R.drawable.u_desktop,s(R.string.s235),ov,new Runnable(){public void run(){pauseOverlay();try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(Exception e){ErrorLog.w(e);launch(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);}}});
   permRow(g,R.drawable.u_bell,s(R.string.s236),ls,new Runnable(){public void run(){if(demo!=null)demo.destroy();gate(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);}});
   permRow(g,R.drawable.u_island,s(R.string.s237),ac,new Runnable(){public void run(){gate(Settings.ACTION_ACCESSIBILITY_SETTINGS);}});
   if(Build.VERSION.SDK_INT>=33)link(g,R.drawable.u_lock,s(R.string.s216),null,new Runnable(){public void run(){askRestricted(null);}});
   LinearLayout n=group();n.setPadding(dp(16),dp(14),dp(16),dp(14));TextView nt=tv(s(R.string.s239),13,Ui.MUTED);nt.setLineSpacing(dp(3),1);n.addView(nt);
   LinearLayout t=group();
   String st=ls&&(ov||ac)?(IslandListener.live!=null?(ac?s(R.string.s228):s(R.string.s229)):s(R.string.s230)):s(R.string.s231);
   row(t,R.drawable.u_info,st,null,null,null);
   link(t,R.drawable.u_play,s(R.string.u_r_try_notif),s(R.string.u_r_try_notif_d),new Runnable(){public void run(){preview();}});
   link(t,R.drawable.u_grid,s(R.string.u_r_apps),s(R.string.u_r_apps_d),new Runnable(){public void run(){apps();}});
  }else{stack.clear();render();}
 }
 void permRow(LinearLayout g,int icon,String title,boolean ok,Runnable click){
  View trail=ok?Ui.iconView(this,R.drawable.u_check,Ui.DOT):chevron();
  if(ok)trail.setLayoutParams(new LinearLayout.LayoutParams(dp(24),dp(24)));
  row(g,icon,title,ok?s(R.string.u_perm_ok):s(R.string.u_perm_todo),trail,click);
 }
// ---------- permissions: everything is asked automatically at start; the only permission buttons left are in section 01 ----------
 static final String P="android.permission.";
 boolean has(String p){return checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
 String[] missing(List<String> l){List<String> m=new ArrayList<>();for(String p:l)if(!has(p))m.add(p);return m.toArray(new String[0]);}
 void launch(String action,Uri data){pauseOverlay();try{startActivity(new Intent(action,data));}catch(Exception e){ErrorLog.w(e);launch(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);}}
 boolean usageGranted(){try{AppOpsManager a=(AppOpsManager)getSystemService(Context.APP_OPS_SERVICE);return a.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,android.os.Process.myUid(),getPackageName())==AppOpsManager.MODE_ALLOWED;}catch(Exception e){ErrorLog.w(e);return false;}}
 boolean exactAlarmGranted(){if(Build.VERSION.SDK_INT<31)return true;try{return ((AlarmManager)getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();}catch(Exception e){ErrorLog.w(e);return false;}}
 /** Runtime permissions of every feature, in one request. */
 List<String> runtimeWanted(){
  List<String> all=new ArrayList<>();
  String[] base={P+"CAMERA",P+"RECORD_AUDIO",P+"READ_CONTACTS",P+"CALL_PHONE",P+"ANSWER_PHONE_CALLS",P+"READ_PHONE_STATE",P+"READ_PHONE_NUMBERS",P+"ACCESS_FINE_LOCATION",P+"ACCESS_COARSE_LOCATION",P+"READ_CALENDAR",P+"WRITE_CALENDAR"};
  for(String p:base)all.add(p);
  if(Build.VERSION.SDK_INT>=29)all.add(P+"ACTIVITY_RECOGNITION");
  if(Build.VERSION.SDK_INT>=31){all.add(P+"BLUETOOTH_CONNECT");all.add(P+"BLUETOOTH_SCAN");}
  if(Build.VERSION.SDK_INT>=33)all.add(P+"POST_NOTIFICATIONS");
  if(Build.VERSION.SDK_INT<=32){all.add(P+"READ_EXTERNAL_STORAGE");all.add(P+"WRITE_EXTERNAL_STORAGE");}
  return all;
 }
 int busy; // 0 free, 1 permission dialog open, 2 a Settings page is open
 boolean askedRuntime,askedBackground;
 /** Special access pages are opened once per install; the user can still decline each one. */
 boolean once(String key){if(Prefs.flag(this,"asked_"+key,false))return false;Prefs.get(this).edit().putBoolean("asked_"+key,true).apply();return true;}
 void page(String action,boolean withPackage){busy=2;launch(action,withPackage?Uri.parse("package:"+getPackageName()):null);}
 /** Asks for whatever is still missing, one step at a time. Called again each time the user comes back to the app. */
 void nextStep(){
  if(busy!=0||Build.VERSION.SDK_INT<23)return;
  if(!askedRuntime){askedRuntime=true;String[] m=missing(runtimeWanted());if(m.length>0){busy=1;requestPermissions(m,9);return;}}
  if(!askedBackground&&Build.VERSION.SDK_INT>=29&&(has(P+"ACCESS_FINE_LOCATION")||has(P+"ACCESS_COARSE_LOCATION"))&&!has(P+"ACCESS_BACKGROUND_LOCATION")){askedBackground=true;busy=1;requestPermissions(new String[]{P+"ACCESS_BACKGROUND_LOCATION"},10);return;}
  NotificationManager nm=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
  if(nm!=null&&!nm.isNotificationPolicyAccessGranted()&&once("dnd")){page(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,false);return;}
  if(!Settings.System.canWrite(this)&&once("write")){page(Settings.ACTION_MANAGE_WRITE_SETTINGS,true);return;}
  if(!usageGranted()&&once("usage")){page(Settings.ACTION_USAGE_ACCESS_SETTINGS,false);return;}
  PowerManager pm=(PowerManager)getSystemService(Context.POWER_SERVICE);
  if(pm!=null&&!pm.isIgnoringBatteryOptimizations(getPackageName())&&once("battery")){page(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,true);return;}
  if(Build.VERSION.SDK_INT>=31&&!exactAlarmGranted()&&once("alarm")){page(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,true);return;}
 }
 @Override public void onRequestPermissionsResult(int code,String[] perms,int[] results){super.onRequestPermissionsResult(code,perms,results);if(code==9||code==10)busy=0;if(started)render();}
 void demoAll(){
  if(!Settings.canDrawOverlays(this)&&IslandAccess.live==null){Toast.makeText(this,L.s(R.string.s368),Toast.LENGTH_LONG).show();return;}
  if(IslandListener.live==null){Toast.makeText(this,L.s(R.string.s369),Toast.LENGTH_LONG).show();return;}
  IslandListener.live.demoAll();
 }
 void preview(){if(!Settings.canDrawOverlays(this)&&IslandAccess.live==null){Toast.makeText(this,L.s(R.string.s368),Toast.LENGTH_LONG).show();return;}Notice n=new Notice(L.s(R.string.s370),L.s(R.string.s371));if(IslandListener.live!=null)IslandListener.live.preview(n);else if(IslandAccess.live!=null&&IslandAccess.live.standalone!=null)IslandAccess.live.standalone.show(n,1);else {if(demo!=null)demo.destroy();demo=new IslandOverlay(IslandAccess.live==null?this:IslandAccess.live);demo.show(n,1);new Handler(getMainLooper()).postDelayed(new Runnable(){public void run(){if(demo!=null){demo.destroy();demo=null;}}},10000);}}
 void apps(){
 Set<String> selected=new HashSet<>(Prefs.get(this).getStringSet("excluded",Collections.emptySet()));
 Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);List<ResolveInfo> infos=getPackageManager().queryIntentActivities(intent,0);TreeMap<String,String> items=new TreeMap<>();
 for(ResolveInfo i:infos)if(!i.activityInfo.packageName.equals(getPackageName()))items.put(i.loadLabel(getPackageManager()).toString()+"\n"+i.activityInfo.packageName,i.activityInfo.packageName);
 if(IslandListener.live!=null)for(Map.Entry<String,String> i:IslandListener.live.seen.entrySet())items.put(i.getValue()+"\n"+i.getKey(),i.getKey());
 String[] labels=items.keySet().toArray(new String[0]),packages=items.values().toArray(new String[0]);boolean[] checks=new boolean[labels.length];for(int i=0;i<checks.length;i++)checks[i]=!selected.contains(packages[i]);
 new AlertDialog.Builder(this).setTitle(L.s(R.string.s372)).setMultiChoiceItems(labels,checks,new DialogInterface.OnMultiChoiceClickListener(){public void onClick(DialogInterface d,int index,boolean checked){if(checked)selected.remove(packages[index]);else selected.add(packages[index]);}}).setPositiveButton(L.s(R.string.s212),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){Prefs.get(MainActivity.this).edit().putStringSet("excluded",selected).apply();Prefs.update(MainActivity.this);}}).setNegativeButton(L.s(R.string.s114),null).show();
 }
 @Override protected void onDestroy(){if(demo!=null)demo.destroy();super.onDestroy();}
}
