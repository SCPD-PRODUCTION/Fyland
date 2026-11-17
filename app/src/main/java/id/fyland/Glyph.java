package id.fyland;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** Small hand-drawn icons for the island (bell, battery, moon, torch, headphones, mic, timer, phone), with light motion. */
final class Glyph extends View {
 static final int BELL=1,BATTERY=2,MOON=3,TORCH=4,HEADPHONES=5,MIC=6,TIMER=7,PHONE=8,HANGUP=9,FACE=10;
 final int type;int color;
 boolean off,charging,pulse;float level=1f;
 long start;
 final Paint line=new Paint(Paint.ANTI_ALIAS_FLAG),fill=new Paint(Paint.ANTI_ALIAS_FLAG);
 final Path path=new Path();final RectF r=new RectF();

 Glyph(Context c,int type,int color){
  super(c);this.type=type;this.color=color;
  line.setStyle(Paint.Style.STROKE);line.setStrokeCap(Paint.Cap.ROUND);line.setStrokeJoin(Paint.Join.ROUND);line.setStrokeWidth(2f);
  fill.setStyle(Paint.Style.FILL);
 }
 /** Starts the entrance motion (bell shake, battery fill, torch glow). */
 void begin(){start=SystemClock.uptimeMillis();invalidate();}
 float age(){return (SystemClock.uptimeMillis()-start)/1000f;}

 @Override protected void onDraw(Canvas canvas){
  super.onDraw(canvas);
  float u=Math.min(getWidth(),getHeight())/24f;if(u<=0)return;
  canvas.save();canvas.translate((getWidth()-24*u)/2f,(getHeight()-24*u)/2f);canvas.scale(u,u);
  line.setColor(color);fill.setColor(color);
  float t=age();boolean animating=false;
  switch(type){
   case BELL:animating=bell(canvas,t);break;
   case BATTERY:animating=battery(canvas,t);break;
   case MOON:moon(canvas);break;
   case TORCH:animating=torch(canvas,t);break;
   case HEADPHONES:headphones(canvas);break;
   case MIC:animating=mic(canvas,t);break;
   case TIMER:animating=timer(canvas,t);break;
   case PHONE:phone(canvas,false);break;
   case HANGUP:phone(canvas,true);break;
   case FACE:animating=face(canvas,t);break;
  }
  canvas.restore();
  if(animating&&isShown())postInvalidateOnAnimation();
 }
 static int mix(int a,int b,float f){
  int r=(int)(((a>>16)&255)*(1-f)+((b>>16)&255)*f),g=(int)(((a>>8)&255)*(1-f)+((b>>8)&255)*f),bl=(int)((a&255)*(1-f)+(b&255)*f);
  return 0xff000000|(r<<16)|(g<<8)|bl;
 }
 /** Face-unlock motion: corner brackets and a scan line over a simple face, then the face dissolves and a check draws in the accent colour. */
 boolean face(Canvas c,float t){
  float scan=Math.min(1f,t/.85f),done=Math.max(0f,Math.min(1f,(t-.85f)/.45f));
  line.setStrokeWidth(1.7f);
  line.setColor(mix(0xffffffff,color,done));
  float L=3.5f,T=3.5f,R=20.5f,B=20.5f,k=4.4f,grow=1f+.06f*(float)Math.sin(done*Math.PI);
  c.save();c.scale(grow,grow,12f,12f);
  path.reset();
  path.moveTo(L,T+k);path.lineTo(L,T);path.lineTo(L+k,T);
  path.moveTo(R-k,T);path.lineTo(R,T);path.lineTo(R,T+k);
  path.moveTo(R,B-k);path.lineTo(R,B);path.lineTo(R-k,B);
  path.moveTo(L+k,B);path.lineTo(L,B);path.lineTo(L,B-k);
  c.drawPath(path,line);
  c.restore();
  float feat=1f-done;
  if(feat>0f){
   int a=(int)(255*feat*Math.min(1f,t/.25f));
   line.setAlpha(a);fill.setColor(0xffffffff);fill.setAlpha(a);
   c.drawCircle(9f,10.2f,1f,fill);c.drawCircle(15f,10.2f,1f,fill);
   path.reset();path.moveTo(12f,10.6f);path.lineTo(12f,13.6f);path.lineTo(11f,13.6f);c.drawPath(path,line);
   r.set(8.6f,12.2f,15.4f,17.2f);c.drawArc(r,25f,130f,false,line);
   float y=T+2f+(B-T-4f)*(.5f+.5f*(float)Math.sin(scan*Math.PI*3f-Math.PI/2));
   line.setColor(mix(0xffffffff,color,.55f));line.setAlpha((int)(200*feat));line.setStrokeWidth(1.4f);c.drawLine(L+1.5f,y,R-1.5f,y,line);
   fill.setColor(color);
  }
  if(done>0f){
   line.setColor(color);line.setStrokeWidth(2.2f);
   Path chk=new Path();chk.moveTo(7.6f,12.4f);chk.lineTo(10.7f,15.4f);chk.lineTo(16.4f,8.8f);
   PathMeasure pm=new PathMeasure(chk,false);Path part=new Path();pm.getSegment(0,pm.getLength()*done,part,true);
   c.drawPath(part,line);
  }
  return t<1.4f;
 }
 boolean bell(Canvas c,float t){
  float angle=t<1.1f?(float)(20*Math.sin(t*30)*Math.exp(-t*3.6)):0f;
  c.save();c.rotate(off?0:angle,12f,4f);
  path.reset();path.moveTo(5.5f,17.5f);path.cubicTo(7f,16f,7f,14f,7f,11f);path.cubicTo(7f,7.5f,9f,5.5f,12f,5.5f);path.cubicTo(15f,5.5f,17f,7.5f,17f,11f);path.cubicTo(17f,14f,17f,16f,18.5f,17.5f);path.close();
  c.drawPath(path,fill);
  r.set(9.6f,18.6f,14.4f,22.4f);c.drawArc(r,0,180,true,fill);
  c.drawLine(12f,3.2f,12f,5.2f,line);
  c.restore();
  if(off){
   Paint cut=new Paint(Paint.ANTI_ALIAS_FLAG);cut.setStyle(Paint.Style.STROKE);cut.setStrokeCap(Paint.Cap.ROUND);cut.setColor(Color.BLACK);cut.setStrokeWidth(5f);c.drawLine(4.5f,3.5f,19.5f,20.5f,cut);
   c.drawLine(4.5f,3.5f,19.5f,20.5f,line);
  }
  return !off&&t<1.2f;
 }
 boolean battery(Canvas c,float t){
  float shown=Math.min(level,t<.8f?level*(1f-(float)Math.pow(1-Math.min(1f,t/.8f),3)):level);
  line.setStrokeWidth(1.7f);r.set(2.5f,7.5f,19f,16.5f);c.drawRoundRect(r,3.2f,3.2f,line);
  r.set(20f,10.6f,21.8f,13.4f);c.drawRoundRect(r,.9f,.9f,fill);
  float w=Math.max(0f,13.2f*shown);if(w>.6f){r.set(4.3f,9.3f,4.3f+w,14.7f);c.drawRoundRect(r,1.8f,1.8f,fill);}
  line.setStrokeWidth(2f);
  if(charging){
   Paint bolt=new Paint(Paint.ANTI_ALIAS_FLAG);bolt.setColor(0xffffffff);
   float pulseA=.75f+.25f*(float)Math.sin(t*5);bolt.setAlpha((int)(255*pulseA));
   path.reset();path.moveTo(11.8f,5.2f);path.lineTo(8.6f,12.2f);path.lineTo(11.1f,12.2f);path.lineTo(10.2f,18.8f);path.lineTo(14.4f,11.2f);path.lineTo(11.9f,11.2f);path.close();
   c.drawPath(path,bolt);return true;
  }
  return t<.9f;
 }
 void moon(Canvas c){
  Path a=new Path();a.addCircle(11.5f,12.5f,8f,Path.Direction.CW);
  Path b=new Path();b.addCircle(16.2f,9f,7f,Path.Direction.CW);
  a.op(b,Path.Op.DIFFERENCE);c.drawPath(a,fill);
 }
 boolean torch(Canvas c,float t){
  if(!off){
   Paint glow=new Paint(Paint.ANTI_ALIAS_FLAG);glow.setColor(color);
   float k=Math.min(1f,t/.35f);glow.setAlpha((int)(70*k*(.8f+.2f*Math.sin(t*6))));c.drawCircle(12f,7f,9f*k,glow);
  }
  path.reset();path.moveTo(7.8f,2.8f);path.lineTo(16.2f,2.8f);path.lineTo(15f,8.6f);path.lineTo(9f,8.6f);path.close();c.drawPath(path,fill);
  r.set(9f,9.8f,15f,21.6f);c.drawRoundRect(r,2.2f,2.2f,off?line:fill);
  if(!off){Paint dot=new Paint(Paint.ANTI_ALIAS_FLAG);dot.setColor(0xff000000);c.drawCircle(12f,13.6f,1.2f,dot);}
  return !off&&t<3f;
 }
 void headphones(Canvas c){
  line.setStrokeWidth(2.2f);r.set(4.5f,4f,19.5f,19f);c.drawArc(r,180,180,false,line);
  r.set(3.2f,11.6f,7.6f,19.6f);c.drawRoundRect(r,2.2f,2.2f,fill);r.set(16.4f,11.6f,20.8f,19.6f);c.drawRoundRect(r,2.2f,2.2f,fill);
  line.setStrokeWidth(2f);
 }
 boolean mic(Canvas c,float t){
  float a=.7f+.3f*(float)Math.sin(t*4.5);fill.setAlpha((int)(255*a));line.setAlpha((int)(255*a));
  r.set(9f,2.6f,15f,13.6f);c.drawRoundRect(r,3f,3f,fill);
  r.set(5.8f,6.8f,18.2f,17.2f);c.drawArc(r,0,180,false,line);
  c.drawLine(12f,17.2f,12f,21f,line);c.drawLine(8.8f,21f,15.2f,21f,line);
  return pulse;
 }
 boolean timer(Canvas c,float t){
  Paint dim=new Paint(line);dim.setAlpha(70);
  r.set(4.2f,5.8f,19.8f,21.4f);c.drawArc(r,0,360,false,dim);
  float sweep=pulse?(t*60f)%360f:300f;c.drawArc(r,-90,Math.max(8f,sweep),false,line);
  c.drawLine(10.2f,2.8f,13.8f,2.8f,line);
  c.drawLine(12f,13.6f,12f,9.4f,line);
  return pulse;
 }
 void phone(Canvas c,boolean hang){
  c.save();if(hang)c.rotate(135f,12f,12f);
  line.setStrokeWidth(3.4f);r.set(5f,5f,19f,19f);c.drawArc(r,45,180,false,line);
  float cx=12f,cy=12f,rad=7f;
  c.drawCircle(cx+(float)(rad*Math.cos(Math.toRadians(45))),cy+(float)(rad*Math.sin(Math.toRadians(45))),2.2f,fill);
  c.drawCircle(cx+(float)(rad*Math.cos(Math.toRadians(225))),cy+(float)(rad*Math.sin(Math.toRadians(225))),2.2f,fill);
  line.setStrokeWidth(2f);c.restore();
 }
}
