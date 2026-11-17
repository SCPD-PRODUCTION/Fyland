package id.fyland;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Look and feel of the Fyland app screens (dark green glass cards, mint icons). */
final class Ui {
 static final int INK=0xffeaf4e6, MUTED=0xff8fae93, MINT=0xffb9e3a9, GREEN=0xff3fd16b, DOT=0xff4ade80, OFF=0xff22392a, LINE=0xff1f4a31;
 private Ui(){}
 static int dp(Context c,float v){return (int)(v*c.getResources().getDisplayMetrics().density+.5f);}
 static void click(View v,final Runnable r){v.setClickable(true);v.setOnClickListener(new View.OnClickListener(){public void onClick(View x){r.run();}});}
 static Drawable icon(Context c,int res,int tint){Drawable d=c.getDrawable(res).mutate();d.setTint(tint);return d;}
 static ImageView iconView(Context c,int res,int tint){ImageView v=new ImageView(c);v.setImageDrawable(icon(c,res,tint));return v;}
 static GradientDrawable card(Context c,float radius){
  GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff134028,0xff0a2217});
  g.setCornerRadius(dp(c,radius));g.setStroke(dp(c,1),0xff25583a);return g;
 }
 static GradientDrawable fill(Context c,int color,float radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(c,radius));return g;}
 /** Rounded square with a mint icon, like the tiles in the design. */
 static View tile(Context c,int res,float size){
  FrameLayout f=new FrameLayout(c);
  GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff266040,0xff123826});g.setCornerRadius(dp(c,size*.3f));f.setBackground(g);
  ImageView i=iconView(c,res,MINT);int s=dp(c,size*.52f);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(s,s);lp.gravity=android.view.Gravity.CENTER;f.addView(i,lp);
  return f;
 }

 /** Green on/off switch. */
 static class Toggle extends View {
  interface OnChange{void on(boolean value);}
  boolean on;float pos;OnChange cb;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);ValueAnimator anim;
  Toggle(Context c,boolean initial,OnChange cb){
   super(c);on=initial;pos=initial?1f:0f;this.cb=cb;setClickable(true);
   setOnClickListener(new View.OnClickListener(){public void onClick(View v){set(!on,true);if(Toggle.this.cb!=null)Toggle.this.cb.on(on);}});
  }
  void set(boolean value,boolean animate){
   on=value;if(anim!=null)anim.cancel();
   if(!animate){pos=on?1f:0f;invalidate();return;}
   anim=ValueAnimator.ofFloat(pos,on?1f:0f);anim.setDuration(160);
   anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener(){public void onAnimationUpdate(ValueAnimator a){pos=(Float)a.getAnimatedValue();invalidate();}});anim.start();
  }
  @Override protected void onMeasure(int w,int h){setMeasuredDimension(dp(getContext(),52),dp(getContext(),30));}
  @Override protected void onDraw(Canvas c){
   float w=getWidth(),h=getHeight();
   p.setColor(blend(OFF,GREEN,pos));c.drawRoundRect(0,0,w,h,h/2,h/2,p);
   float r=h/2-dp(getContext(),3),cx=h/2+(w-h)*pos;
   p.setColor(0xffffffff);c.drawCircle(cx,h/2,r,p);
  }
  static int blend(int a,int b,float t){
   int r=(int)(Color.red(a)+(Color.red(b)-Color.red(a))*t),g=(int)(Color.green(a)+(Color.green(b)-Color.green(a))*t),bl=(int)(Color.blue(a)+(Color.blue(b)-Color.blue(a))*t);
   return Color.rgb(r,g,bl);
  }
 }

 /** Dark green gradient with the soft round shapes and sparkle from the design. */
 static class Backdrop extends View {
  final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);final Path star=new Path();
  Backdrop(Context c){super(c);}
  @Override protected void onDraw(Canvas c){
   float w=getWidth(),h=getHeight();
   p.setShader(new LinearGradient(0,0,0,h,new int[]{0xff05200f,0xff031209,0xff020c06},new float[]{0f,.45f,1f},Shader.TileMode.CLAMP));
   c.drawRect(0,0,w,h,p);p.setShader(null);
   p.setStyle(Paint.Style.FILL);p.setColor(0x1243a05f);c.drawCircle(w*1.0f,h*.07f,w*.62f,p);
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(getContext(),1.2f));p.setColor(0x3a4aa86b);c.drawCircle(w*1.0f,h*.07f,w*.62f,p);
   p.setStyle(Paint.Style.FILL);p.setColor(0x0e43a05f);c.drawCircle(w*.05f,h*.62f,w*.7f,p);
   sparkle(c,w*.88f,h*.075f,dp(getContext(),9),0xaa9fd6a0);
  }
  void sparkle(Canvas c,float x,float y,float r,int color){
   star.reset();star.moveTo(x,y-r);star.quadTo(x,y,x+r,y);star.quadTo(x,y,x,y+r);star.quadTo(x,y,x-r,y);star.quadTo(x,y,x,y-r);star.close();
   p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawPath(star,p);
  }
 }
}
