package id.fyland;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import java.util.Calendar;
import java.util.Locale;

/** Pages and tools drawn inside the expanded island: apps, weather, clock (alarm / stopwatch / timer) and a notepad. */
final class Panels {
 static final int ACCENT=0xffa4c397,MUTED=0xff93ab94,ORANGE=0xffff9f0a,TILE=0xff0e2616;

 static void build(IslandOverlay o){
  String p=o.panel;
  if("apps".equals(p))apps(o);
  else if("weather".equals(p))weather(o);
  else if("clock".equals(p))clock(o);
  else if("timer".equals(p))timer(o);
  else if("alarm".equals(p))alarm(o);
  else if("notepad".equals(p))notepad(o);
  else if("calendar".equals(p))calendar(o);
  else o.closePanel();
 }

 // ---------- shared pieces ----------
 static GradientDrawable circle(int color){GradientDrawable d=new GradientDrawable();d.setShape(GradientDrawable.OVAL);d.setColor(color);return d;}

 static void header(final IslandOverlay o,String title,final String back){
  LinearLayout row=new LinearLayout(o.c);row.setGravity(Gravity.CENTER_VERTICAL);
  if(back!=null){
   TextView b=o.text("‹",22,Color.WHITE);b.setGravity(Gravity.CENTER);b.setPadding(o.dp(8),0,o.dp(12),o.dp(2));
   b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){o.setPanel(back);}});
   row.addView(b);
  }
  TextView t=o.text(title,12,ACCENT);t.setTypeface(null,Typeface.BOLD);t.setPadding(back==null?o.dp(8):0,0,0,0);
  row.addView(t,new LinearLayout.LayoutParams(0,-2,1));
  TextView x=o.text("✕",15,MUTED);x.setGravity(Gravity.CENTER);x.setPadding(o.dp(14),o.dp(8),o.dp(8),o.dp(8));
  x.setOnClickListener(new View.OnClickListener(){public void onClick(View v){o.closePanel();}});
  row.addView(x);
  o.view.addView(row,new LinearLayout.LayoutParams(-1,-2));
 }

 static TextView pill(IslandOverlay o,String label,int fg,int bg){
  TextView t=o.text(label,14,fg);t.setTypeface(null,Typeface.BOLD);t.setGravity(Gravity.CENTER);
  t.setPadding(o.dp(18),o.dp(12),o.dp(18),o.dp(12));
  GradientDrawable d=new GradientDrawable();d.setColor(bg);d.setCornerRadius(o.dp(20));t.setBackground(d);
  return t;
 }

 static void dots(IslandOverlay o,int page){
  LinearLayout row=new LinearLayout(o.c);row.setGravity(Gravity.CENTER);row.setPadding(0,o.dp(8),0,o.dp(2));
  for(int i=0;i<2;i++){
   View d=new View(o.c);d.setBackground(circle(i==page?0xffffffff:0xff55585c));
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(o.dp(6),o.dp(6));lp.leftMargin=lp.rightMargin=o.dp(3);row.addView(d,lp);
  }
  o.view.addView(row,new LinearLayout.LayoutParams(-1,-2));
 }

 static void tile(final IslandOverlay o,LinearLayout row,String emoji,String label,int color,final Runnable action){
  LinearLayout t=new LinearLayout(o.c);t.setOrientation(1);t.setGravity(Gravity.CENTER_HORIZONTAL);t.setPadding(0,o.dp(6),0,o.dp(6));
  TextView icon=o.text(emoji,22,Color.WHITE);icon.setGravity(Gravity.CENTER);icon.setBackground(circle(color));
  t.addView(icon,new LinearLayout.LayoutParams(o.dp(48),o.dp(48)));
  TextView l=o.text(label,10,0xffcfd0d3);l.setGravity(Gravity.CENTER);l.setPadding(0,o.dp(5),0,0);
  t.addView(l,new LinearLayout.LayoutParams(-1,-2));
  row.addView(t,new LinearLayout.LayoutParams(0,-2,1));
  t.setOnClickListener(new View.OnClickListener(){public void onClick(View v){action.run();}});
 }

 /** Like tile(), but with the supplied artwork instead of an emoji. Falls back to the emoji if the image is missing. */
 static void tileImg(final IslandOverlay o,LinearLayout row,String res,String emoji,String label,int color,final Runnable action){
  int id=o.c.getResources().getIdentifier(res,"drawable",o.c.getPackageName());
  if(id==0){tile(o,row,emoji,label,color,action);return;}
  LinearLayout t=new LinearLayout(o.c);t.setOrientation(1);t.setGravity(Gravity.CENTER_HORIZONTAL);t.setPadding(0,o.dp(6),0,o.dp(6));
  ImageView icon=new ImageView(o.c);icon.setImageResource(id);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
  t.addView(icon,new LinearLayout.LayoutParams(o.dp(48),o.dp(48)));
  TextView l=o.text(label,10,0xffcfd0d3);l.setGravity(Gravity.CENTER);l.setPadding(0,o.dp(5),0,0);
  t.addView(l,new LinearLayout.LayoutParams(-1,-2));
  row.addView(t,new LinearLayout.LayoutParams(0,-2,1));
  t.setOnClickListener(new View.OnClickListener(){public void onClick(View v){action.run();}});
 }

 // ---------- page 1: apps ----------
 static void apps(final IslandOverlay o){
  TextView t=o.text(L.s(R.string.s423),10,MUTED);t.setPadding(o.dp(8),o.dp(2),0,o.dp(4));o.view.addView(t);
  LinearLayout row=new LinearLayout(o.c);row.setPadding(0,o.dp(2),0,o.dp(2));
  tileImg(o,row,"ic_tile_youtube","YT","YouTube",0xffff0000,new Runnable(){public void run(){o.closePanel();Popup.openYouTube(o);}});
  tileImg(o,row,"ic_tile_tiktok","TT","TikTok",0xff111111,new Runnable(){public void run(){o.closePanel();Popup.openTikTok(o);}});
  tileImg(o,row,"ic_tile_clock","⏰",L.s(R.string.s427),0xffff9f0a,new Runnable(){public void run(){o.setPanel("clock");}});
  tileImg(o,row,"ic_tile_calendar","📅",L.s(R.string.s429),0xffe5484d,new Runnable(){public void run(){o.setPanel("calendar");}});
  tile(o,row,"🎙",L.s(R.string.s430),0xffe5484d,new Runnable(){public void run(){Popup.show(o,"rec");o.closePanel();}});
  tileImg(o,row,"ic_tile_notepad","📝",L.s(R.string.s433),0xffe0a800,new Runnable(){public void run(){o.setPanel("notepad");}});
  o.view.addView(row,new LinearLayout.LayoutParams(-1,-2));
  dots(o,1);
 }

 // ---------- page 1: weather ----------
 static void weather(final IslandOverlay o){
  if(!Weather.fresh()&&!Weather.loading&&!Weather.failed)Weather.load(o.c,false,new Weather.Done(){public void done(){o.refreshPanel();}});
  if(Weather.fresh()){
   // Full-bleed card: the sky fills the whole island, only the status-bar inset (and the camera side in landscape) stays as padding.
   int top=o.view.getPaddingTop(),side=o.dp(16);
   o.view.setPadding(Math.max(0,o.view.getPaddingLeft()-side),0,Math.max(0,o.view.getPaddingRight()-side),0);
   WeatherCard row=new WeatherCard(o.c,top,true);
   o.view.addView(row,new LinearLayout.LayoutParams(-1,-2));
   // Tap: the phone's own weather app (when it has one, else refresh as before). Long press: refresh.
   row.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
    if(Weather.openBuiltIn(o.c)){o.closePanel();return;}
    Weather.load(o.c,true,new Weather.Done(){public void done(){o.refreshPanel();}});o.refreshPanel();
   }});
   row.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){Weather.load(o.c,true,new Weather.Done(){public void done(){o.refreshPanel();}});o.refreshPanel();return true;}});
   return;
  }
  TextView t=o.text(L.s(R.string.s434),10,MUTED);t.setPadding(o.dp(8),o.dp(2),0,o.dp(2));o.view.addView(t);
  if(Weather.failed){
   TextView msg=o.text(Weather.error,12,0xffe5a0a0);msg.setSingleLine(false);msg.setMaxLines(3);msg.setPadding(o.dp(8),o.dp(10),o.dp(8),o.dp(10));o.view.addView(msg);
   o.addChip(chipRow(o),L.s(R.string.s435),new Runnable(){public void run(){Weather.failed=false;Weather.load(o.c,true,new Weather.Done(){public void done(){o.refreshPanel();}});o.refreshPanel();}});
  }else{
   TextView msg=o.text(L.s(R.string.s436),13,MUTED);msg.setPadding(o.dp(8),o.dp(18),o.dp(8),o.dp(18));o.view.addView(msg);
  }
  dots(o,0);
 }
 static LinearLayout chipRow(IslandOverlay o){LinearLayout r=new LinearLayout(o.c);o.view.addView(r,new LinearLayout.LayoutParams(-1,-2));return r;}

 // ---------- clock hub ----------
 static void clock(final IslandOverlay o){
  header(o,L.s(R.string.s427),"apps");
  LinearLayout row=new LinearLayout(o.c);row.setPadding(0,o.dp(2),0,o.dp(4));
  tileImg(o,row,"ic_tile_clock","⏰","Alarm",ORANGE,new Runnable(){public void run(){o.setPanel("alarm");}});
  tile(o,row,"⏱","Stopwatch",ORANGE,new Runnable(){public void run(){
   if(!ClockKit.swOn)ClockKit.startSw();
   o.showRestExpanded();
  }});
  tile(o,row,"⏲","Timer",ORANGE,new Runnable(){public void run(){o.setPanel("timer");}});
  o.view.addView(row,new LinearLayout.LayoutParams(-1,-2));
 }

 // ---------- timer: ruler, like the iPhone's ----------
 static String fmtMin(int m){return String.format(Locale.US,"%d:00",m);}
 static void timer(final IslandOverlay o){
  header(o,"Timer","clock");
  final int[] minutes={Math.max(1,Prefs.value(o.c,"timer_min",5))};
  final TextView big=o.text(fmtMin(minutes[0]),40,ORANGE);big.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
  Ruler ruler=new Ruler(o.c,minutes[0],new Ruler.Change(){public void on(int m){minutes[0]=m;big.setText(fmtMin(m));}});
  o.view.addView(ruler,new LinearLayout.LayoutParams(-1,o.dp(76)));
  LinearLayout row=new LinearLayout(o.c);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(o.dp(6),o.dp(4),o.dp(10),o.dp(6));
  TextView start=pill(o,"Start Timer",ORANGE,0xff3d2606);
  start.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
   if(minutes[0]<=0){Toast.makeText(o.c,L.s(R.string.s440),Toast.LENGTH_SHORT).show();return;}
   Prefs.get(o.c).edit().putInt("timer_min",minutes[0]).apply();
   ClockKit.startTimer(o.c,minutes[0]*60000L);
   o.closePanel();
  }});
  row.addView(start,new LinearLayout.LayoutParams(-2,-2));
  row.addView(new View(o.c),new LinearLayout.LayoutParams(0,1,1));
  row.addView(big,new LinearLayout.LayoutParams(-2,-2));
  o.view.addView(row,new LinearLayout.LayoutParams(-1,-2));
 }

 /** A horizontal ruler of minutes that slides under a fixed marker. */
 static final class Ruler extends View {
  interface Change{void on(int minutes);}
  static final int MAX=180;
  final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final Change cb;final float d,spacing;float value,lastX;int reported;
  Ruler(Context c,int start,Change cb){super(c);this.cb=cb;d=c.getResources().getDisplayMetrics().density;spacing=11*d;value=start;reported=start;}
  @Override protected void onDraw(Canvas canvas){
   super.onDraw(canvas);
   float cx=getWidth()/2f;
   int lo=(int)Math.floor(value-cx/spacing)-1,hi=(int)Math.ceil(value+cx/spacing)+1;
   p.setStrokeCap(Paint.Cap.ROUND);
   for(int m=Math.max(0,lo);m<=Math.min(MAX,hi);m++){
    float x=cx+(m-value)*spacing;float dist=Math.abs(x-cx)/cx;
    int alpha=Math.max(30,(int)(255*(1f-dist*dist*.85f)));
    boolean five=m%5==0;
    p.setColor((ORANGE&0x00ffffff)|(alpha<<24));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.2f*d);
    float top=26*d,h=five?30*d:22*d;
    canvas.drawLine(x,top,x,top+h,p);
    if(five){p.setStyle(Paint.Style.FILL);p.setTextSize(13*d);p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextAlign(Paint.Align.CENTER);canvas.drawText(String.valueOf(m),x,18*d,p);}
   }
   p.setColor(ORANGE);p.setStyle(Paint.Style.FILL);
   Path t=new Path();float ty=getHeight()-12*d;t.moveTo(cx,ty);t.lineTo(cx-6*d,ty+10*d);t.lineTo(cx+6*d,ty+10*d);t.close();canvas.drawPath(t,p);
  }
  @Override public boolean onTouchEvent(MotionEvent e){
   switch(e.getActionMasked()){
    case MotionEvent.ACTION_DOWN:lastX=e.getX();if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);return true;
    case MotionEvent.ACTION_MOVE:
     value=Math.max(0,Math.min(MAX,value-(e.getX()-lastX)/spacing));lastX=e.getX();
     report();invalidate();return true;
    case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:
     value=Math.round(value);report();invalidate();return true;
   }
   return super.onTouchEvent(e);
  }
  void report(){int m=Math.round(value);if(m!=reported){reported=m;cb.on(m);}}
 }

 // ---------- alarm ----------
 static String fmtAlarm(int h,int m){return String.format(Locale.US,"%02d:%02d",h,m);}
 static void alarm(final IslandOverlay o){
  header(o,"Alarm","clock");
  final int[] at={Prefs.value(o.c,"alarm_h",7),Prefs.value(o.c,"alarm_m",0)};
  final TextView time=o.text(fmtAlarm(at[0],at[1]),44,ORANGE);time.setTypeface(null,Typeface.BOLD);time.setGravity(Gravity.CENTER);time.setPadding(0,o.dp(4),0,o.dp(8));
  o.view.addView(time,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout hours=new LinearLayout(o.c);
  step(o,hours,L.s(R.string.s443),-60,at,time);step(o,hours,L.s(R.string.s444),60,at,time);
  o.view.addView(hours,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout mins=new LinearLayout(o.c);
  step(o,mins,L.s(R.string.s445),-5,at,time);step(o,mins,"−1",-1,at,time);step(o,mins,"+1",1,at,time);step(o,mins,L.s(R.string.s446),5,at,time);
  o.view.addView(mins,new LinearLayout.LayoutParams(-1,-2));
  TextView make=pill(o,L.s(R.string.s447),ORANGE,0xff3d2606);
  LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.topMargin=o.dp(8);mp.bottomMargin=o.dp(4);
  make.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
   Prefs.get(o.c).edit().putInt("alarm_h",at[0]).putInt("alarm_m",at[1]).apply();
   try{
    Intent i=new Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR,at[0]).putExtra(AlarmClock.EXTRA_MINUTES,at[1]).putExtra(AlarmClock.EXTRA_MESSAGE,"Fyland").putExtra(AlarmClock.EXTRA_SKIP_UI,true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    o.c.startActivity(i);
    Toast.makeText(o.c,L.s(R.string.s448)+fmtAlarm(at[0],at[1])+L.s(R.string.s449),Toast.LENGTH_SHORT).show();
    o.closePanel();
   }catch(Exception e){ErrorLog.w(e);Toast.makeText(o.c,L.s(R.string.s450),Toast.LENGTH_LONG).show();}
  }});
  o.view.addView(make,mp);
 }
 /** delta is in minutes; ±60 changes the hour. */
 static void step(IslandOverlay o,LinearLayout row,String label,final int delta,final int[] at,final TextView time){
  TextView b=o.text(label,12,ACCENT);b.setGravity(Gravity.CENTER);b.setPadding(o.dp(4),o.dp(11),o.dp(4),o.dp(11));
  GradientDrawable d=new GradientDrawable();d.setColor(TILE);d.setCornerRadius(o.dp(12));b.setBackground(d);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(o.dp(2),o.dp(3),o.dp(2),o.dp(3));row.addView(b,lp);
  b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
   int total=((at[0]*60+at[1]+delta)%1440+1440)%1440;
   at[0]=total/60;at[1]=total%60;time.setText(fmtAlarm(at[0],at[1]));
  }});
 }

// ---------- calendar: see and add events right in the island ----------
 static boolean calendarAllowed(Context c){
  return c.checkSelfPermission(android.Manifest.permission.READ_CALENDAR)==android.content.pm.PackageManager.PERMISSION_GRANTED
    &&c.checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR)==android.content.pm.PackageManager.PERMISSION_GRANTED;
 }
 static long writableCalendar(Context c){
  android.database.Cursor cur=null;
  try{
   cur=c.getContentResolver().query(CalendarContract.Calendars.CONTENT_URI,new String[]{CalendarContract.Calendars._ID},
     CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL+">=? AND "+CalendarContract.Calendars.VISIBLE+"=1",new String[]{"500"},
     CalendarContract.Calendars.IS_PRIMARY+" DESC");
   if(cur!=null&&cur.moveToFirst())return cur.getLong(0);
  }catch(Exception ignored){ErrorLog.w(ignored);}finally{if(cur!=null)cur.close();}
  return -1;
 }
 static boolean saveEvent(Context c,String title,long begin){
  try{
   long cal=writableCalendar(c);if(cal<0)return false;
   android.content.ContentValues v=new android.content.ContentValues();
   v.put(CalendarContract.Events.CALENDAR_ID,cal);v.put(CalendarContract.Events.TITLE,title);
   v.put(CalendarContract.Events.DTSTART,begin);v.put(CalendarContract.Events.DTEND,begin+3600000L);
   v.put(CalendarContract.Events.EVENT_TIMEZONE,java.util.TimeZone.getDefault().getID());v.put(CalendarContract.Events.HAS_ALARM,1);
   android.net.Uri u=c.getContentResolver().insert(CalendarContract.Events.CONTENT_URI,v);
   if(u==null)return false;
   android.content.ContentValues r=new android.content.ContentValues();
   r.put(CalendarContract.Reminders.EVENT_ID,android.content.ContentUris.parseId(u));r.put(CalendarContract.Reminders.MINUTES,10);r.put(CalendarContract.Reminders.METHOD,CalendarContract.Reminders.METHOD_ALERT);
   c.getContentResolver().insert(CalendarContract.Reminders.CONTENT_URI,r);
   return true;
  }catch(Exception e){ErrorLog.w(e);return false;}
 }
 /** Up to three events in the next week, as one line each. */
 static java.util.ArrayList<String> upcoming(Context c){
  java.util.ArrayList<String> out=new java.util.ArrayList<>();
  android.database.Cursor cur=null;
  try{
   long now=System.currentTimeMillis();
   android.net.Uri.Builder b=CalendarContract.Instances.CONTENT_URI.buildUpon();
   android.content.ContentUris.appendId(b,now);android.content.ContentUris.appendId(b,now+7L*86400000L);
   cur=c.getContentResolver().query(b.build(),new String[]{CalendarContract.Instances.TITLE,CalendarContract.Instances.BEGIN,CalendarContract.Instances.ALL_DAY},null,null,CalendarContract.Instances.BEGIN+" ASC");
   java.text.SimpleDateFormat day=new java.text.SimpleDateFormat("EEE d MMM  HH:mm",Locale.getDefault()),allDay=new java.text.SimpleDateFormat("EEE d MMM",Locale.getDefault());
   while(cur!=null&&cur.moveToNext()&&out.size()<3){
    String t=cur.getString(0);if(t==null||t.isEmpty())t=L.s(R.string.s456);
    boolean all=cur.getInt(2)==1;
    out.add((all?allDay:day).format(new java.util.Date(cur.getLong(1)))+"  ·  "+t);
   }
  }catch(Exception ignored){ErrorLog.w(ignored);}finally{if(cur!=null)cur.close();}
  return out;
 }
 static void calendar(final IslandOverlay o){
  header(o,L.s(R.string.s429),"apps");
  if(!calendarAllowed(o.c)){
   TextView msg=o.text(L.s(R.string.s457),12,MUTED);msg.setSingleLine(false);msg.setMaxLines(3);msg.setPadding(o.dp(8),o.dp(8),o.dp(8),o.dp(10));
   o.view.addView(msg);
   TextView ask=pill(o,L.s(R.string.s458),Color.WHITE,0xffe5484d);
   ask.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
    o.closePanel();
    o.c.startActivity(new Intent(o.c,MainActivity.class).putExtra("ask_cal",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   }});
   o.view.addView(ask,new LinearLayout.LayoutParams(-1,-2));
   return;
  }
  TextView soon=o.text(L.s(R.string.s459),10,MUTED);soon.setPadding(o.dp(8),o.dp(2),0,o.dp(2));o.view.addView(soon);
  java.util.ArrayList<String> events=upcoming(o.c);
  if(events.isEmpty()){TextView none=o.text(L.s(R.string.s460),12,0xffcfd0d3);none.setPadding(o.dp(8),o.dp(2),0,o.dp(6));o.view.addView(none);}
  for(String line:events){TextView t=o.text(line,12,0xffe4e5e7);t.setPadding(o.dp(8),o.dp(2),o.dp(8),o.dp(2));o.view.addView(t);}
  final EditText title=new EditText(o.c);
  title.setTextColor(Color.WHITE);title.setHintTextColor(0xff8e9094);title.setTextSize(14);title.setHint(L.s(R.string.s461));title.setSingleLine(true);
  title.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
  GradientDrawable bg=new GradientDrawable();bg.setColor(TILE);bg.setCornerRadius(o.dp(14));title.setBackground(bg);title.setPadding(o.dp(12),o.dp(10),o.dp(12),o.dp(10));
  title.setOnKeyListener(new View.OnKeyListener(){public boolean onKey(View v,int key,KeyEvent event){
   if(key==KeyEvent.KEYCODE_BACK&&event.getAction()==KeyEvent.ACTION_UP){o.closePanel();return true;}
   return false;
  }});
  LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.topMargin=o.dp(6);o.view.addView(title,tp);
  Calendar now=Calendar.getInstance();
  final int[] at={0,((now.get(Calendar.HOUR_OF_DAY)+1)%24)*60};   // day offset, minute of day
  final TextView when=o.text("",18,ACCENT);when.setTypeface(null,Typeface.BOLD);when.setGravity(Gravity.CENTER);when.setPadding(0,o.dp(8),0,o.dp(4));
  final Runnable refresh=new Runnable(){public void run(){when.setText(describe(at));}};
  refresh.run();
  o.view.addView(when,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout days=new LinearLayout(o.c);
  calStep(o,days,L.s(R.string.s462),-1440,at,refresh);calStep(o,days,L.s(R.string.s463),1440,at,refresh);calStep(o,days,L.s(R.string.s464),10080,at,refresh);
  o.view.addView(days,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout hours=new LinearLayout(o.c);
  calStep(o,hours,L.s(R.string.s465),-60,at,refresh);calStep(o,hours,L.s(R.string.s466),60,at,refresh);calStep(o,hours,L.s(R.string.s467),-15,at,refresh);calStep(o,hours,L.s(R.string.s468),15,at,refresh);
  o.view.addView(hours,new LinearLayout.LayoutParams(-1,-2));
  TextView make=pill(o,L.s(R.string.s469),Color.WHITE,0xffe5484d);
  LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.topMargin=o.dp(8);mp.bottomMargin=o.dp(4);
  make.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
   String name=title.getText().toString().trim();
   if(name.isEmpty()){title.setError(L.s(R.string.s470));return;}
   if(saveEvent(o.c,name,toMillis(at))){
    Toast.makeText(o.c,L.s(R.string.s471)+describe(at),Toast.LENGTH_SHORT).show();
    o.refreshPanel();
   }else Toast.makeText(o.c,L.s(R.string.s472),Toast.LENGTH_LONG).show();
  }});
  o.view.addView(make,mp);
 }
 /** at[0] = days from today, at[1] = minute of the day. */
 static long toMillis(int[] at){
  Calendar c=Calendar.getInstance();c.add(Calendar.DAY_OF_YEAR,at[0]);
  c.set(Calendar.HOUR_OF_DAY,at[1]/60);c.set(Calendar.MINUTE,at[1]%60);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
  return c.getTimeInMillis();
 }
 static String describe(int[] at){
  return new java.text.SimpleDateFormat("EEE, d MMM  ·  HH:mm",Locale.getDefault()).format(new java.util.Date(toMillis(at)));
 }
 /** delta is in minutes: it moves the whole date-time, so ±1440 changes the day and ±60 the hour. */
 static void calStep(IslandOverlay o,LinearLayout row,String label,final int delta,final int[] at,final Runnable refresh){
  TextView b=o.text(label,12,ACCENT);b.setGravity(Gravity.CENTER);b.setPadding(o.dp(4),o.dp(11),o.dp(4),o.dp(11));
  GradientDrawable d=new GradientDrawable();d.setColor(TILE);d.setCornerRadius(o.dp(12));b.setBackground(d);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(o.dp(2),o.dp(3),o.dp(2),o.dp(3));row.addView(b,lp);
  b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){
   int total=at[0]*1440+at[1]+delta;
   int day=Math.floorDiv(total,1440);at[0]=Math.max(0,day);at[1]=day<0?at[1]:Math.floorMod(total,1440);
   refresh.run();
  }});
 }

 // ---------- notepad ----------
 static void notepad(final IslandOverlay o){
  header(o,L.s(R.string.s433),null);
  final EditText note=new EditText(o.c);
  note.setTextColor(Color.WHITE);note.setHintTextColor(0xff8e9094);note.setTextSize(14);note.setHint(L.s(R.string.s474));
  note.setGravity(Gravity.TOP|Gravity.START);note.setMinLines(5);note.setMaxLines(8);
  note.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
  GradientDrawable bg=new GradientDrawable();bg.setColor(TILE);bg.setCornerRadius(o.dp(14));note.setBackground(bg);note.setPadding(o.dp(12),o.dp(10),o.dp(12),o.dp(10));
  note.setText(Prefs.get(o.c).getString("note",""));note.setSelection(note.length());
  note.addTextChangedListener(new android.text.TextWatcher(){
   public void beforeTextChanged(CharSequence s,int a,int b,int c){}
   public void onTextChanged(CharSequence s,int a,int b,int c){Prefs.get(o.c).edit().putString("note",s.toString()).apply();}
   public void afterTextChanged(android.text.Editable e){}
  });
  note.setOnKeyListener(new View.OnKeyListener(){public boolean onKey(View v,int key,KeyEvent event){
   if(key==KeyEvent.KEYCODE_BACK&&event.getAction()==KeyEvent.ACTION_UP){o.closePanel();return true;}
   return false;
  }});
  LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=o.dp(4);
  o.view.addView(note,np);
  LinearLayout buttons=new LinearLayout(o.c);o.view.addView(buttons,new LinearLayout.LayoutParams(-1,-2));
  o.addChip(buttons,L.s(R.string.s221),new Runnable(){public void run(){
   String text=note.getText().toString();
   if(text.isEmpty()){Toast.makeText(o.c,L.s(R.string.s476),Toast.LENGTH_SHORT).show();return;}
   ((ClipboardManager)o.c.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(L.s(R.string.s433),text));
   Toast.makeText(o.c,L.s(R.string.s477),Toast.LENGTH_SHORT).show();
  }});
  o.addChip(buttons,L.s(R.string.s478),new Runnable(){public void run(){
   try{
    ClipData clip=((ClipboardManager)o.c.getSystemService(Context.CLIPBOARD_SERVICE)).getPrimaryClip();
    if(clip==null||clip.getItemCount()==0){Toast.makeText(o.c,L.s(R.string.s479),Toast.LENGTH_SHORT).show();return;}
    CharSequence text=clip.getItemAt(0).coerceToText(o.c);
    int a=Math.max(0,Math.min(note.getSelectionStart(),note.getSelectionEnd())),b=Math.max(0,Math.max(note.getSelectionStart(),note.getSelectionEnd()));
    note.getText().replace(a,b,text);
   }catch(Exception e){ErrorLog.w(e);Toast.makeText(o.c,L.s(R.string.s480),Toast.LENGTH_SHORT).show();}
  }});
  o.addChip(buttons,L.s(R.string.s224),new Runnable(){public void run(){note.setText("");}});
  o.view.setOnClickListener(null);
  o.handler.postDelayed(new Runnable(){public void run(){
   if(!"notepad".equals(o.panel))return;
   note.requestFocus();
   try{((android.view.inputmethod.InputMethodManager)o.c.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(note,0);}catch(Exception ignored){ErrorLog.w(ignored);}
  }},350);
 }
}
