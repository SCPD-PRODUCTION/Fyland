package id.fyland;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

/** Flowing wave up to the playhead that flattens into a bar thumb, then a flat track. The wave keeps moving every frame
 *  while playing, the thumb glides between position updates, and it levels out when paused. Drag to seek. */
final class WavyProgress extends View {
 interface Seek{void seek(float fraction);}
 static final float STROKE=3.6f,AMPLITUDE=3.1f,WAVELENGTH=30f,PERIOD=1.25f,THUMB_W=3.6f,THUMB_H=20f,GAP=3.2f;
 final Paint wave=new Paint(Paint.ANTI_ALIAS_FLAG),track=new Paint(Paint.ANTI_ALIAS_FLAG),thumb=new Paint(Paint.ANTI_ALIAS_FLAG);
 final Path path=new Path();final RectF rect=new RectF();final float d;
 long baseMs,durationMs,baseElapsed;float speed=1f;boolean playing;
 float phase,amp,thumbScale=1f;long lastFrame;
 boolean dragging;float dragFraction;Seek listener;

 WavyProgress(Context c){
  super(c);d=c.getResources().getDisplayMetrics().density;
  wave.setStyle(Paint.Style.STROKE);wave.setStrokeCap(Paint.Cap.ROUND);wave.setStrokeJoin(Paint.Join.ROUND);wave.setStrokeWidth(STROKE*d);wave.setColor(Color.WHITE);
  track.setStyle(Paint.Style.STROKE);track.setStrokeCap(Paint.Cap.ROUND);track.setStrokeWidth(STROKE*d);track.setColor(0x4dffffff);
  thumb.setColor(Color.WHITE);setClickable(true);
 }
 void setAccent(int color){wave.setColor(color);thumb.setColor(color);track.setColor((color&0x00ffffff)|0x4d000000);invalidate();}
 /** position is the playback position right now; the view extrapolates from here every frame. */
 void setTimeline(long positionMs,long duration,boolean isPlaying,float rate){
  baseMs=Math.max(0,positionMs);durationMs=duration;baseElapsed=SystemClock.elapsedRealtime();playing=isPlaying;speed=rate<=0?1f:rate;
  invalidate();
 }
 float fractionNow(){
  if(dragging)return dragFraction;
  long p=baseMs+(playing?(long)((SystemClock.elapsedRealtime()-baseElapsed)*speed):0);
  return WaveMath.fraction(p,durationMs);
 }
 float left(){return 5*d;}
 float right(){return getWidth()-5*d;}
 float fractionAt(float x){float l=left(),r=right();return r<=l?0f:Math.max(0f,Math.min(1f,(x-l)/(r-l)));}

 @Override public boolean onTouchEvent(MotionEvent e){
  switch(e.getActionMasked()){
   case MotionEvent.ACTION_DOWN:
    if(durationMs<=0)return false;
    if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
    dragging=true;dragFraction=fractionAt(e.getX());invalidate();return true;
   case MotionEvent.ACTION_MOVE:if(dragging){dragFraction=fractionAt(e.getX());invalidate();}return true;
   case MotionEvent.ACTION_UP:
    if(dragging){dragging=false;float f=dragFraction;baseMs=(long)(f*durationMs);baseElapsed=SystemClock.elapsedRealtime();if(listener!=null)listener.seek(f);}
    invalidate();return true;
   case MotionEvent.ACTION_CANCEL:dragging=false;invalidate();return true;
  }
  return super.onTouchEvent(e);
 }

 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);
  long now=SystemClock.uptimeMillis();float dt=lastFrame==0?1f/60f:Math.min(.05f,(now-lastFrame)/1000f);lastFrame=now;
  boolean flowing=playing&&!dragging;
  amp=WaveMath.approach(amp,flowing?1f:0f,dt,7f);
  thumbScale=WaveMath.approach(thumbScale,dragging?.55f:1f,dt,16f);
  if(flowing)phase=WaveMath.advance(phase,dt,PERIOD);
  float w=getWidth(),cy=getHeight()/2f,l=left(),r=right();if(w<=0||r<=l)return;
  float x=l+(r-l)*fractionNow();
  float thumbHalf=THUMB_W*d*thumbScale/2f,gap=GAP*d;
  // flat track after the thumb
  float trackStart=x+thumbHalf+gap;
  if(trackStart<r)canvas.drawLine(trackStart,cy,r,cy,track);
  // wave before the thumb
  float waveEnd=x-thumbHalf-gap;
  if(waveEnd>l){
   float wl=WAVELENGTH*d,a=AMPLITUDE*d*amp,taperLen=wl*1.15f;
   path.reset();boolean first=true;float step=1.6f*d;
   for(float px=l;;px+=step){
    if(px>waveEnd)px=waveEnd;
    float y=cy+WaveMath.y(px,l,wl,phase,a*WaveMath.taper(waveEnd-px,taperLen));
    if(first){path.moveTo(px,y);first=false;}else path.lineTo(px,y);
    if(px>=waveEnd)break;
   }
   canvas.drawPath(path,wave);
  }else canvas.drawLine(l,cy,Math.max(l,waveEnd),cy,wave);
  // thumb bar
  rect.set(x-thumbHalf,cy-THUMB_H*d/2f,x+thumbHalf,cy+THUMB_H*d/2f);canvas.drawRoundRect(rect,thumbHalf,thumbHalf,thumb);
  if(flowing||amp>.01f||dragging||Math.abs(thumbScale-(dragging?.55f:1f))>.01f)postInvalidateOnAnimation();
 }
}
