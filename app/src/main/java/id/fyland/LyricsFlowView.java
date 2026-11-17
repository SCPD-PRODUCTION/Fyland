package id.fyland;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.text.*;
import android.view.*;
import android.widget.ScrollView;
import java.util.*;

/** Whole lyric document, smooth auto-scroll, progressive soft-edged reveal across wrapped lines. */
final class LyricsFlowView extends ScrollView {
 final FlowCanvas content;
 LyricsDocument document;
 long anchorPosition,anchorTime,trackDuration,manualUntil;
 /** The light runs a hair ahead of the reported position so it feels in time with the voice (screen and player lag). */
 static final long LEAD=150;
 float rate=1;
 boolean playing;
 int lastAuto=-2;
 String message=L.s(R.string.s122);

 LyricsFlowView(Context context){
  super(context);setFillViewport(false);setVerticalScrollBarEnabled(false);setOverScrollMode(OVER_SCROLL_NEVER);setSmoothScrollingEnabled(true);
  content=new FlowCanvas(context);addView(content,new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT));
 }
 int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 void setDocument(LyricsDocument doc){
  document=doc;message="";lastAuto=-2;manualUntil=0;content.rebuild();scrollTo(0,0);content.invalidate();
 }
 void setMessage(String value){document=null;message=value==null?"":value;lastAuto=-2;content.rebuild();scrollTo(0,0);content.invalidate();}
 void update(long position,boolean active,float speed,long duration){
  anchorPosition=Math.max(0,position);anchorTime=SystemClock.elapsedRealtime();playing=active;rate=Math.max(0,speed);trackDuration=duration;
  content.invalidate();
 }
 long position(){long value=anchorPosition+(playing?(long)((SystemClock.elapsedRealtime()-anchorTime)*rate):0);return trackDuration>0?Math.min(trackDuration,value):value;}
 @Override public boolean onTouchEvent(MotionEvent event){
  if(event.getActionMasked()==MotionEvent.ACTION_DOWN||event.getActionMasked()==MotionEvent.ACTION_MOVE)manualUntil=SystemClock.uptimeMillis()+6000;
  return super.onTouchEvent(event);
 }
 @Override protected void onDetachedFromWindow(){playing=false;super.onDetachedFromWindow();}
 void follow(int index){
  if(document==null||document.lines.isEmpty()||index<0||index>=content.blocks.size())return;
  if(SystemClock.uptimeMillis()<manualUntil)return;
  if(lastAuto==index)return;lastAuto=index;
  final LyricsDocument snapshot=document;final int target=LyricsTimeline.scrollTarget(content.blocks.get(index).top,getHeight(),content.totalHeight,.38f);
  post(new Runnable(){public void run(){if(document==snapshot&&isShown())smoothScrollTo(0,target);}});
 }
 final class Block {
  final StaticLayout text;final int top;
  Block(StaticLayout text,int top){this.text=text;this.top=top;}
 }
 final class FlowCanvas extends View {
  final TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
  final ArrayList<Block> blocks=new ArrayList<>();
  int widthBuilt=-1,totalHeight;
  FlowCanvas(Context context){super(context);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(16*getResources().getDisplayMetrics().scaledDensity);setContentDescription(L.s(R.string.s205));}
  StaticLayout layout(String text,int width){return StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setIncludePad(false).setAlignment(Layout.Alignment.ALIGN_NORMAL).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR).setLineSpacing(dp(2),1).setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY).build();}
  void rebuild(){widthBuilt=-1;requestLayout();invalidate();}
  void ensure(int width){
   width=Math.max(1,width);if(width==widthBuilt)return;widthBuilt=width;blocks.clear();int y=dp(2);int textWidth=Math.max(1,width-dp(12));
   if(document!=null&&!document.lines.isEmpty()){
    for(LyricsDocument.Line line:document.lines){StaticLayout text=layout(line.text.isEmpty()?"…":line.text,textWidth);blocks.add(new Block(text,y));y+=text.getHeight()+dp(4);}
   }else{
    String value=document==null?message:document.plain;StaticLayout text=layout(value.isEmpty()?L.s(R.string.s206):value,textWidth);blocks.add(new Block(text,y));y+=text.getHeight()+dp(16);
   }
   totalHeight=Math.max(dp(30),y);
  }
  @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec);ensure(width);setMeasuredDimension(width,resolveSize(totalHeight,heightSpec));}
  @Override protected void onDraw(Canvas canvas){
   super.onDraw(canvas);ensure(getWidth());long now=position()+LEAD;int active=document==null?-1:document.indexAt(now);
   follow(active);Rect clip=canvas.getClipBounds();
   for(int i=0;i<blocks.size();i++){
    Block block=blocks.get(i);if(block.top+block.text.getHeight()<clip.top||block.top>clip.bottom)continue;
    canvas.save();canvas.translate(dp(4),block.top);paint.setShader(null);paint.clearShadowLayer();paint.setColor(0xb8ffffff);block.text.draw(canvas);
    if(document!=null&&!document.lines.isEmpty()&&i==active)reveal(canvas,block.text,LyricsTimeline.reveal(document,i,now,trackDuration));
    canvas.restore();
   }
   paint.setShader(null);paint.clearShadowLayer();
   if(playing&&rate>0&&isShown()&&document!=null&&!document.lines.isEmpty())postInvalidateOnAnimation();
  }
  void reveal(Canvas canvas,StaticLayout layout,float fraction){
   float total=0;for(int row=0;row<layout.getLineCount();row++)total+=layout.getLineWidth(row);
   float remaining=total*fraction,edge=dp(10);
   for(int row=0;row<layout.getLineCount()&&remaining>0;row++){
    float rowWidth=layout.getLineWidth(row),amount=Math.min(rowWidth,remaining);remaining-=rowWidth;if(amount<=0)continue;
    float left=layout.getLineLeft(row),right=layout.getLineRight(row);boolean rtl=layout.getParagraphDirection(row)<0;
    canvas.save();canvas.clipRect(rtl?Math.max(left-dp(5),right-amount-edge):left-dp(5),layout.getLineTop(row),rtl?right+dp(5):Math.min(right+dp(5),left+amount+edge),layout.getLineBottom(row));
    paint.setColor(Color.WHITE);paint.setShadowLayer(dp(5),0,0,0x99ffffff);
    if(amount<rowWidth){
     float front=rtl?right-amount:left+amount;
     paint.setShader(new LinearGradient(rtl?front+edge:front-edge,0,rtl?front-edge:front+edge,0,new int[]{Color.WHITE,0x00ffffff},null,Shader.TileMode.CLAMP));
    }else paint.setShader(null);
    layout.draw(canvas);canvas.restore();
   }
   paint.setShader(null);paint.clearShadowLayer();
  }
 }
}
