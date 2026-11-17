package id.fyland;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.os.SystemClock;
import android.view.View;

/** The Face ID unlock motion (rings spinning into a circle, then the check) played from res/raw/face_id.json.
 *  It draws only the foreground layers: the island window itself is the black rounded square that grows and shrinks. */
final class FaceIdView extends View {
 /** Composition size of the square the Lottie background settles at; the view scales this to its own size. */
 static final float SQUARE=289f,CX=219.5f,CY=184.5f;
 /** Frame where the check is complete; the last frame is held until the island collapses. */
 static final float END=112f;
 static LottieLite lottie;static boolean failed;
 long start;
 final Paint fallback=new Paint(Paint.ANTI_ALIAS_FLAG);

 FaceIdView(Context c){
  super(c);
  if(lottie==null&&!failed){try{lottie=LottieLite.load(c.getApplicationContext(),"face_id");}catch(Throwable t){ErrorLog.w(t);failed=true;}}
  fallback.setStyle(Paint.Style.STROKE);fallback.setStrokeCap(Paint.Cap.ROUND);fallback.setStrokeJoin(Paint.Join.ROUND);fallback.setColor(0xffa6f897);
 }

 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);
  int w=getWidth(),h=getHeight();if(w<=0||h<=0)return;
  long now=SystemClock.uptimeMillis();if(start==0)start=now;
  float s=Math.min(w,h)/SQUARE;
  if(lottie==null){drawFallback(canvas,w,h);return;}
  float frame=Math.min(END,(now-start)/1000f*lottie.fps);
  canvas.save();
  canvas.translate(w/2f,h/2f);canvas.scale(s,s);canvas.translate(-CX,-CY);
  lottie.draw(canvas,frame,true);
  canvas.restore();
  if(frame<END&&isShown())postInvalidateOnAnimation();
 }

 /** Only used if the animation file cannot be read: a plain ring and check. */
 void drawFallback(Canvas c,int w,int h){
  float u=Math.min(w,h)/100f;
  c.save();c.translate((w-100*u)/2f,(h-100*u)/2f);c.scale(u,u);
  fallback.setStrokeWidth(4f);c.drawCircle(50f,50f,22f,fallback);
  Path chk=new Path();chk.moveTo(40f,51f);chk.lineTo(47f,58f);chk.lineTo(60f,43f);
  float k=Math.min(1f,(SystemClock.uptimeMillis()-(start==0?SystemClock.uptimeMillis():start))/450f);
  PathMeasure pm=new PathMeasure(chk,false);Path part=new Path();pm.getSegment(0,pm.getLength()*k,part,true);
  c.drawPath(part,fallback);
  c.restore();
  if(k<1f&&isShown())postInvalidateOnAnimation();
 }
}
