package id.fyland;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.util.Random;

/** The island's weather card: a sky that matches the conditions (animated rain, snow, sun, drifting clouds, lightning, stars) with the temperature on the left and the next hours as glass pills on the right. Drawn entirely with Canvas, no image or animation files. */
final class WeatherCard extends View {
 static final int CLEAR=0,PARTLY=1,CLOUDY=2,FOG=3,RAIN=4,SNOW=5,STORM=6;
 final float d;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG),tp=new Paint(Paint.ANTI_ALIAS_FLAG);
 final Path clip=new Path(),tmp=new Path();final RectF r=new RectF();
 final Random rnd=new Random(7);
 final int N=140;final float[] px=new float[N],py=new float[N],ps=new float[N],pl=new float[N];
 final float[] sx=new float[28],sy=new float[28],sp=new float[28];
 long t0=SystemClock.uptimeMillis(),last=t0;float flash,nextFlash=3f;
 final int kind;final boolean day;final String city,desc;final int temp;
 final String[] hl;final int[] ht,hc;final int topInset;final boolean dots;
 final Context ctx;
 /** Horizontal scroll of the hourly pills (pixels), its limit, a fling speed and where the pills sit (for touch handling). */
 float scroll,maxScroll,vel,pillX0,pillTop,pillBottom,downX,downY,lastX;boolean dragging,scrolled;VelocityTrackerLite vt=new VelocityTrackerLite();

 WeatherCard(Context c,int topInset,boolean dots){
  super(c);d=c.getResources().getDisplayMetrics().density;this.topInset=topInset;this.dots=dots;
  ctx=c;
  kind=kindOf(Weather.code);day=Weather.isDay;city=Weather.city.isEmpty()?L.s(R.string.s602):Weather.city;desc=Weather.desc;temp=Weather.shown(c,Weather.temp);
  int n=Math.min(Weather.hrHour.length,Math.min(Weather.hrTemp.length,Weather.hrCode.length));
  hl=new String[n];ht=new int[n];hc=new int[n];
  for(int i=0;i<n;i++){hl[i]=Weather.hourText(c,Weather.hrHour[i]);ht[i]=Weather.shown(c,Weather.hrTemp[i]);hc[i]=Weather.hrCode[i];}
  for(int i=0;i<N;i++){px[i]=rnd.nextFloat();py[i]=rnd.nextFloat();ps[i]=.7f+rnd.nextFloat()*.9f;pl[i]=.5f+rnd.nextFloat()*.7f;}
  for(int i=0;i<sx.length;i++){sx[i]=rnd.nextFloat();sy[i]=rnd.nextFloat()*.6f;sp[i]=rnd.nextFloat()*6f;}
  setContentDescription(city+", "+temp+"°"+Weather.unitMark(c)+", "+desc);
 }

 static int kindOf(int c){
  if(c==0)return CLEAR;if(c==1||c==2)return PARTLY;if(c==3)return CLOUDY;if(c==45||c==48)return FOG;
  if(c>=51&&c<=67||c>=80&&c<=82)return RAIN;if(c>=71&&c<=77||c==85||c==86)return SNOW;if(c>=95)return STORM;return CLOUDY;
 }
 static boolean wet(int k){return k==RAIN||k==STORM;}

 @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),topInset+(int)((150+(dots?16:0))*d));}

 @Override protected void onDraw(Canvas cv){
  long now=SystemClock.uptimeMillis();float t=(now-t0)/1000f,dt=Math.min(.05f,(now-last)/1000f);last=now;
  float w=getWidth(),h=getHeight();
  if(!dragging&&Math.abs(vel)>8*d){scroll=Math.max(0,Math.min(maxScroll,scroll+vel*dt));vel*=(float)Math.pow(.02,dt);if(scroll<=0||scroll>=maxScroll)vel=0;}else if(!dragging)vel=0;
  r.set(0,0,w,h);clip.reset();clip.addRoundRect(r,(dots?30:22)*d,(dots?30:22)*d,Path.Direction.CW);
  int save=cv.save();cv.clipPath(clip);
  sky(cv,w,h);
  if(kind==CLEAR&&!day||kind==PARTLY&&!day)stars(cv,w,h,t);
  if(kind==CLEAR||kind==PARTLY){if(day)sunGlow(cv,w*.2f,h*.3f,t);}
  if(kind!=CLEAR)drift(cv,w,h,t);
  if(kind==FOG)fog(cv,w,h,t);
  if(wet(kind))rain(cv,w,h,dt);
  if(kind==SNOW)snow(cv,w,h,dt,t);
  if(kind==STORM)lightning(cv,w,h,dt);
  int s2=cv.save();cv.translate(0,topInset);content(cv,w,h-topInset-(dots?16*d:0),t);cv.restoreToCount(s2);
  if(dots)pageDots(cv,w,h);
  cv.restoreToCount(save);
  if(isAttachedToWindow())postInvalidateOnAnimation();
 }

 void pageDots(Canvas cv,float w,float h){
  p.setStyle(Paint.Style.FILL);
  for(int i=0;i<2;i++){p.setColor(i==0?0xffffffff:0x88ffffff);cv.drawCircle(w/2f+(i-.5f)*12*d,h-9*d,3*d,p);}
 }

 void sky(Canvas cv,float w,float h){
  int a,b;
  switch(kind){
   case CLEAR:case PARTLY:if(day){a=kind==CLEAR?0xff2a7de1:0xff3f78b5;b=kind==CLEAR?0xff7cc4ff:0xff86aee0;}else{a=0xff0a1733;b=0xff26386c;}break;
   case SNOW:a=0xff6a7f9d;b=0xffb3c2d8;break;
   case STORM:a=0xff232838;b=0xff434a63;break;
   case FOG:a=0xff7a8594;b=0xffb2bac4;break;
   case RAIN:a=0xff56637c;b=0xff37466a;break;
   default:a=0xff4d607c;b=0xff8296b2;
  }
  p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(0,0,0,h,a,b,Shader.TileMode.CLAMP));cv.drawRect(0,0,w,h,p);p.setShader(null);
  if(kind==RAIN||kind==STORM||kind==CLOUDY){
   p.setShader(new LinearGradient(0,0,0,h*.5f,0x66d5dbe6,0x00d5dbe6,Shader.TileMode.CLAMP));cv.drawRect(0,0,w,h*.5f,p);p.setShader(null);
   p.setShader(new RadialGradient(w*.85f,0,w*.55f,0x55eef1f6,0x00eef1f6,Shader.TileMode.CLAMP));cv.drawRect(0,0,w,h*.7f,p);p.setShader(null);
  }
 }

 void stars(Canvas cv,float w,float h,float t){
  p.setStyle(Paint.Style.FILL);
  for(int i=0;i<sx.length;i++){float tw=.5f+.5f*(float)Math.sin(t*1.6f+sp[i]);p.setColor(((int)(70+150*tw)<<24)|0xffffff);cv.drawCircle(sx[i]*w,sy[i]*h,(.7f+tw*.8f)*d,p);}
 }

 void sunGlow(Canvas cv,float cx,float cy,float t){
  p.setStyle(Paint.Style.FILL);
  p.setShader(new RadialGradient(cx,cy,110*d,0x66ffe38a,0x00ffe38a,Shader.TileMode.CLAMP));cv.drawCircle(cx,cy,110*d,p);p.setShader(null);
 }

 /** Soft cloud banks sliding past, thicker the heavier the sky. */
 void drift(Canvas cv,float w,float h,float t){
  int n=kind==SNOW||kind==FOG?2:kind==PARTLY?2:4;int a=kind==STORM?0x38000000:kind==PARTLY?0x30ffffff:0x2effffff;
  p.setStyle(Paint.Style.FILL);p.setColor(a);
  for(int i=0;i<n;i++){
   float sp=(8+i*5)*d,wd=(120+i*40)*d;float x=((t*sp+i*w*.5f)%(w+wd*2))-wd;float y=(10+i*16)*d;
   cloudShape(cv,x,y+30*d,wd,p);
  }
 }
 void cloudShape(Canvas cv,float cx,float cy,float w,Paint pt){
  cv.drawCircle(cx-w*.18f,cy,w*.2f,pt);cv.drawCircle(cx+w*.08f,cy-w*.1f,w*.26f,pt);cv.drawCircle(cx+w*.3f,cy+w*.02f,w*.17f,pt);
  r.set(cx-w*.38f,cy,cx+w*.47f,cy+w*.2f);cv.drawRoundRect(r,w*.1f,w*.1f,pt);
 }

 void fog(Canvas cv,float w,float h,float t){
  p.setStyle(Paint.Style.FILL);
  for(int i=0;i<4;i++){float x=(float)Math.sin(t*.25f+i)*30*d;p.setColor(0x33ffffff);r.set(x-40*d,h*(.2f+i*.2f),w+40*d+x,h*(.2f+i*.2f)+22*d);cv.drawRoundRect(r,11*d,11*d,p);}
 }

 void rain(Canvas cv,float w,float h,float dt){
  int n=Weather.code>=65&&Weather.code<=67||Weather.code==82||kind==STORM?N:Weather.code>=61||Weather.code>=80?100:60;
  p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(1.1f*d);
  for(int i=0;i<n;i++){
   py[i]+=dt*ps[i]*1.35f;px[i]-=dt*ps[i]*.12f;
   if(py[i]>1.1f){py[i]=-.1f;px[i]=rnd.nextFloat()*1.2f;}
   if(px[i]<-.1f)px[i]+=1.2f;
   float x=px[i]*w,y=py[i]*h,len=(10+14*pl[i])*d;
   p.setColor(((int)(60+90*pl[i])<<24)|0xdce8ff);
   cv.drawLine(x,y,x-len*.14f,y+len,p);
  }
 }

 void snow(Canvas cv,float w,float h,float dt,float t){
  p.setStyle(Paint.Style.FILL);
  for(int i=0;i<N;i++){
   py[i]+=dt*ps[i]*.16f;if(py[i]>1.05f){py[i]=-.05f;px[i]=rnd.nextFloat();}
   float x=px[i]*w+(float)Math.sin(t*1.1f+i)*8*d,y=py[i]*h;
   p.setColor(((int)(120+110*pl[i])<<24)|0xffffff);cv.drawCircle(x,y,(1.2f+pl[i]*1.6f)*d,p);
  }
 }

 void lightning(Canvas cv,float w,float h,float dt){
  nextFlash-=dt;
  if(nextFlash<=0){flash=1f;nextFlash=3f+rnd.nextFloat()*5f;}
  if(flash>0){cv.drawColor(((int)(flash*90)<<24)|0xffffff);flash=Math.max(0,flash-dt*3.2f);}
 }

 void content(Canvas cv,float w,float h,float t){
  // Layout follows the reference card: big glyph + temperature + condition on the left, place and glass hourly pills on the right (the last pill bleeds off the edge).
  float lx=w*.15f;
  icon(cv,Weather.code,day,lx,h*.31f,76*d,t,true);
  tp.setColor(Color.WHITE);tp.setTypeface(Typeface.DEFAULT_BOLD);tp.setTextAlign(Paint.Align.CENTER);tp.setShadowLayer(4*d,0,1*d,0x55000000);
  tp.setTextSize(38*d);cv.drawText(temp+"°"+Weather.unitMark(ctx),lx,h*.74f,tp);
  tp.setTypeface(Typeface.DEFAULT);float ds=13*d;tp.setTextSize(ds);while(tp.measureText(desc)>w*.29f&&ds>9*d){ds-=d;tp.setTextSize(ds);}
  cv.drawText(desc,lx,h*.74f+21*d,tp);
  float x0=w*.31f;
  tp.setTextAlign(Paint.Align.LEFT);tp.setTypeface(Typeface.DEFAULT_BOLD);float cs=26*d;tp.setTextSize(cs);while(tp.measureText(city)>w-x0-14*d&&cs>13*d){cs-=d;tp.setTextSize(cs);}
  cv.drawText(city,x0,h*.27f,tp);
  tp.setTypeface(Typeface.DEFAULT);tp.setTextSize(11.5f*d);tp.setColor(0xe6ffffff);
  float cy=h*.27f+19*d;
  p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.2f*d);p.setColor(0xe6ffffff);cv.drawCircle(x0+6*d,cy-4*d,6*d,p);cv.drawLine(x0+6*d,cy-4*d,x0+6*d,cy-7.5f*d,p);cv.drawLine(x0+6*d,cy-4*d,x0+8.5f*d,cy-3*d,p);
  String hourly=L.s(R.string.s603);cv.drawText(hourly,x0+16*d,cy,tp);
  String info=Weather.wind+" km/h   "+(Weather.precip>=10?String.valueOf(Math.round(Weather.precip)):String.format(java.util.Locale.US,"%.1f",Weather.precip))+" mm";
  float room=w-14*d-(x0+16*d+tp.measureText(hourly)+10*d);
  if(tp.measureText(info)<=room){tp.setTextAlign(Paint.Align.RIGHT);tp.setColor(0xccffffff);cv.drawText(info,w-14*d,cy,tp);tp.setTextAlign(Paint.Align.LEFT);tp.setColor(0xe6ffffff);}
  tp.clearShadowLayer();
  float pw=w*.127f,gap=w*.023f,top=h*.41f,ph=h*.455f;
  pillX0=x0;pillTop=top+topInset;pillBottom=top+ph+topInset;
  maxScroll=Math.max(0,hl.length*(pw+gap)-gap-(w-x0)+14*d);scroll=Math.max(0,Math.min(maxScroll,scroll));
  int keep=cv.save();cv.clipRect(x0-3*d,top-3*d,w,top+ph+3*d);
  for(int i=0;i<hl.length;i++){
   float x=x0+i*(pw+gap)-scroll;if(x>w)break;if(x+pw<x0-3*d)continue;
   r.set(x,top,x+pw,top+ph);
   p.setStyle(Paint.Style.FILL);
   p.setShader(new LinearGradient(0,top,0,top+ph,0x40ffffff,0x24ffffff,Shader.TileMode.CLAMP));cv.drawRoundRect(r,pw*.32f,pw*.32f,p);p.setShader(null);
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1f*d);p.setColor(0x59ffffff);cv.drawRoundRect(r,pw*.32f,pw*.32f,p);
   float mx=x+pw/2f;
   String lab=hl[i];int cut=lab.length();while(cut>0&&!Character.isDigit(lab.charAt(cut-1)))cut--;
   String num=lab.substring(0,cut),suf=lab.substring(cut);
   tp.setTypeface(Typeface.DEFAULT_BOLD);tp.setColor(Color.WHITE);tp.setTextAlign(Paint.Align.LEFT);
   tp.setTextSize(15*d);float wn=tp.measureText(num);tp.setTextSize(10*d);float wsf=tp.measureText(suf);
   float lx0=mx-(wn+wsf+1*d)/2f,by=top+ph*.22f;
   tp.setTextSize(15*d);cv.drawText(num,lx0,by,tp);tp.setTextSize(10*d);cv.drawText(suf,lx0+wn+1*d,by,tp);
   icon(cv,hc[i],true,mx,top+ph*.5f,28*d,t,false);
   tp.setTextAlign(Paint.Align.CENTER);tp.setTextSize(14*d);cv.drawText(ht[i]+"°"+Weather.unitMark(ctx),mx,top+ph*.85f,tp);
  }
  cv.restoreToCount(keep);
  tp.setTextAlign(Paint.Align.LEFT);
 }

 /** One weather glyph. big=true animates the sun's rays and the falling drops. */
 void icon(Canvas cv,int code,boolean isDay,float cx,float cy,float s,float t,boolean big){
  int k=kindOf(code);
  p.setStyle(Paint.Style.FILL);
  if(k==CLEAR){if(isDay)sun(cv,cx,cy,s*.55f,t,big);else moon(cv,cx,cy,s*.5f);return;}
  if(k==PARTLY){if(isDay)sun(cv,cx-s*.2f,cy-s*.2f,s*.4f,t,big);else moon(cv,cx-s*.2f,cy-s*.2f,s*.34f);cloud(cv,cx+s*.08f,cy+s*.08f,s*.8f,0xfff6f9ff);return;}
  if(k==FOG){p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(Math.max(2f*d,s*.06f));p.setColor(0xf0ffffff);for(int i=0;i<4;i++){float y=cy-s*.24f+i*s*.17f,o=(i%2==0?-1:1)*s*.06f;cv.drawLine(cx-s*.38f+o,y,cx+s*.38f+o,y,p);}p.setStyle(Paint.Style.FILL);return;}
  cloud(cv,cx,cy-s*.08f,s*.9f,k==STORM?0xffc5cbd9:0xfff6f9ff);
  if(k==RAIN){
   p.setStyle(Paint.Style.FILL);p.setColor(0xff7fc4ff);
   for(int i=0;i<3;i++){float f=big?((t*.9f+i*.33f)%1f):.0f;float x=cx-s*.2f+i*s*.2f,y=cy+s*.3f+(big?f*s*.12f:0);p.setAlpha(big?(int)(255*(1-f*.6f)):255);drop(cv,x,y,s*.07f);}
   p.setAlpha(255);
  }else if(k==SNOW){
   p.setColor(Color.WHITE);for(int i=0;i<3;i++){float f=big?((t*.45f+i*.33f)%1f):0;cv.drawCircle(cx-s*.2f+i*s*.2f+(big?(float)Math.sin(t*2+i)*s*.03f:0),cy+s*.32f+f*s*.1f,s*.045f,p);}
  }else if(k==STORM){
   p.setColor(0xffffcf3a);tmp.reset();tmp.moveTo(cx+s*.04f,cy+s*.12f);tmp.lineTo(cx-s*.12f,cy+s*.38f);tmp.lineTo(cx,cy+s*.38f);tmp.lineTo(cx-s*.06f,cy+s*.6f);tmp.lineTo(cx+s*.15f,cy+s*.32f);tmp.lineTo(cx+s*.03f,cy+s*.32f);tmp.close();cv.drawPath(tmp,p);
  }
 }
 void drop(Canvas cv,float x,float y,float rr){tmp.reset();tmp.moveTo(x,y-rr*1.9f);tmp.cubicTo(x+rr*1.2f,y-rr*.5f,x+rr*1.1f,y+rr,x,y+rr);tmp.cubicTo(x-rr*1.1f,y+rr,x-rr*1.2f,y-rr*.5f,x,y-rr*1.9f);tmp.close();cv.drawPath(tmp,p);}
 void cloud(Canvas cv,float cx,float cy,float w,int color){
  p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(0,cy-w*.35f,0,cy+w*.25f,color,0xffd5deee,Shader.TileMode.CLAMP));
  cloudShape(cv,cx,cy,w*.9f,p);p.setShader(null);
 }
 void sun(Canvas cv,float cx,float cy,float rr,float t,boolean big){
  p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(Math.max(1.6f*d,rr*.12f));p.setColor(0xffffdf6e);
  float rot=big?t*.5f:0;
  for(int i=0;i<10;i++){double a=rot+i*Math.PI/5;float c0=(float)Math.cos(a),s0=(float)Math.sin(a);float pulse=big?1f+.12f*(float)Math.sin(t*2+i):1f;cv.drawLine(cx+c0*rr*1.25f,cy+s0*rr*1.25f,cx+c0*rr*1.25f*pulse+c0*rr*.35f,cy+s0*rr*1.25f*pulse+s0*rr*.35f,p);}
  p.setStyle(Paint.Style.FILL);p.setShader(new RadialGradient(cx-rr*.25f,cy-rr*.25f,rr*1.2f,0xffffec8a,0xffffb52e,Shader.TileMode.CLAMP));cv.drawCircle(cx,cy,rr,p);p.setShader(null);
 }
 void moon(Canvas cv,float cx,float cy,float rr){
  Path a=new Path(),b=new Path();a.addCircle(cx,cy,rr,Path.Direction.CW);b.addCircle(cx+rr*.5f,cy-rr*.35f,rr*.85f,Path.Direction.CW);
  a.op(b,Path.Op.DIFFERENCE);p.setStyle(Paint.Style.FILL);p.setColor(0xfffff0b0);cv.drawPath(a,p);
 }

 /** Swiping across the hourly pills scrolls them; a swipe anywhere else is left to the island's page swipe, and a tap still opens the weather app. */
 @Override public boolean onTouchEvent(android.view.MotionEvent e){
  switch(e.getActionMasked()){
   case android.view.MotionEvent.ACTION_DOWN:
    downX=lastX=e.getX();downY=e.getY();scrolled=false;
    dragging=maxScroll>0&&downX>=pillX0&&downY>=pillTop-6*d&&downY<=pillBottom+6*d;
    if(dragging){vel=0;vt.reset();vt.add(e.getEventTime(),downX);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}
    return super.onTouchEvent(e)||dragging;
   case android.view.MotionEvent.ACTION_MOVE:
    if(!dragging)return super.onTouchEvent(e);
    {float x=e.getX();if(!scrolled&&Math.abs(x-downX)>6*d){scrolled=true;setPressed(false);cancelLongPress();}
     if(scrolled){scroll=Math.max(0,Math.min(maxScroll,scroll-(x-lastX)));vt.add(e.getEventTime(),x);invalidate();}
     lastX=x;}
    return true;
   case android.view.MotionEvent.ACTION_UP:
   case android.view.MotionEvent.ACTION_CANCEL:
    if(dragging){
     boolean was=scrolled;dragging=false;
     if(was){vel=-vt.speed();postInvalidateOnAnimation();return true;}
    }
    return super.onTouchEvent(e);
  }
  return super.onTouchEvent(e);
 }
 /** Average finger speed (pixels per second) over the last few moves. */
 static final class VelocityTrackerLite{
  final long[] t=new long[6];final float[] x=new float[6];int n;
  void reset(){n=0;}
  void add(long time,float px){if(n==t.length){System.arraycopy(t,1,t,0,n-1);System.arraycopy(x,1,x,0,n-1);n--;}t[n]=time;x[n]=px;n++;}
  float speed(){if(n<2)return 0;long dt=t[n-1]-t[0];if(dt<=0||dt>200)return 0;return (x[n-1]-x[0])*1000f/dt;}
 }
}
