package id.fyland;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

/** The music island's background: the song cover under a gradient built from its dominant colour (see draw). */
final class CoverBg extends Drawable {
 final Bitmap bmp;final GradientDrawable shape;final int accent,deep;
 final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),shade=new Paint();
 final Path clip=new Path();final RectF rect=new RectF(),dst=new RectF();
 Shader cached;int cw,ch;

 CoverBg(Bitmap bmp,GradientDrawable shape){this.bmp=bmp;this.shape=shape;this.accent=vivid(bmp);this.deep=darken(accent,.38f);}

 static int darken(int c,float v){float[] h=new float[3];Color.colorToHSV(c,h);h[2]=Math.min(h[2],v);return Color.HSVToColor(h);}

 /** Accent = the cover's DOMINANT hue by area (not its single most saturated pixel), kept as vivid and bright as before so the gradient stays bold. A blue cover with a little orange stays blue, even when the blue is pale. Truly pale or colourless covers get a pale/neutral accent. Each song gets its own. */
 static int vivid(Bitmap b){
  try{
   Bitmap s=Bitmap.createScaledBitmap(b,48,48,true);
   float[] h=new float[3];
   float[] cnt=new float[36],hx=new float[36],hy=new float[36],sw=new float[36];
   int total=0,light=0;long lr=0,lg=0,lb=0;
   for(int y=0;y<48;y++)for(int x=0;x<48;x++){
    int px=s.getPixel(x,y);total++;
    Color.colorToHSV(px,h);
    if(h[2]<.16f)continue;
    // Only truly grey pixels are ignored. A pale-but-tinted area (a soft blue sky, a cream wall) still counts for its hue, just a little less than a vivid area, so a pale blue cover is not outvoted by a few small saturated spots such as skin or lips.
    if(h[1]<.10f){if(h[2]>=.5f){light++;lr+=Color.red(px);lg+=Color.green(px);lb+=Color.blue(px);}continue;}
    float w=.5f+.5f*Math.min(1f,h[1]/.5f);
    int k=((int)(h[0]/10f))%36;double a=Math.toRadians(h[0]);
    cnt[k]+=w;hx[k]+=w*(float)Math.cos(a);hy[k]+=w*(float)Math.sin(a);sw[k]+=w*h[1];
   }
   float best=-1;int bi=0;
   for(int k=0;k<36;k++){float sc=cnt[k]+.5f*(cnt[(k+35)%36]+cnt[(k+1)%36]);if(sc>best){best=sc;bi=k;}}
   if(light>best&&light>.35f*total&&best<.06f*total){ // only truly colourless covers get a pale accent; a pale cover with real colour in it keeps that colour
    float[] c=new float[3];Color.RGBToHSV((int)(lr/light),(int)(lg/light),(int)(lb/light),c);
    return Color.HSVToColor(new float[]{c[0],Math.min(.12f,c[1]),.86f});
   }
   if(best>=.03f*total){
    int a=(bi+35)%36,z=(bi+1)%36;
    float sx=hx[a]+hx[bi]+hx[z],sy=hy[a]+hy[bi]+hy[z],n=Math.max(1f,cnt[a]+cnt[bi]+cnt[z]);
    float hue=(float)Math.toDegrees(Math.atan2(sy,sx));if(hue<0)hue+=360f;
    float sat=(sw[a]+sw[bi]+sw[z])/n;
    return Color.HSVToColor(new float[]{hue,Math.max(.62f,Math.min(1f,sat)),.96f});
   }
   Color.colorToHSV(s.getPixel(16,16),h);return Color.HSVToColor(new float[]{h[0],Math.min(.35f,h[1]),.85f});
  }catch(Exception e){ErrorLog.w(e);return 0xffff7a2e;}
 }
 /** True for pale accents, so marks drawn on top of them switch to a dark ink. */
 static boolean pale(int c){return Color.red(c)>150&&Color.green(c)>150&&Color.blue(c)>150&&(Color.red(c)*.299f+Color.green(c)*.587f+Color.blue(c)*.114f)/255f>.62f;}

 /** Gradient taken from the reference design: near-black at the top, the cover colour rising from the bottom edge (brightest at the bottom centre), darker at the left and right sides. */
 final Paint base=new Paint(),glow=new Paint(),vig=new Paint();
 @Override public void draw(Canvas cv){
  Rect r=getBounds();
  if(r.isEmpty())return;
  float rad=shape.getCornerRadius();
  rect.set(r);clip.reset();clip.addRoundRect(rect,rad,rad,Path.Direction.CW);
  int save=cv.save();
  cv.clipPath(clip);
  if(cached==null||cw!=r.width()||ch!=r.height()){
   cw=r.width();ch=r.height();
   float[] h=new float[3];Color.colorToHSV(accent,h);
   boolean neutral=h[1]<.2f;
   base.setColor(0xbe000000); // the cover shows through at about 25%
   int g=Color.HSVToColor(new float[]{h[0],h[1],Math.min(h[2],neutral?.60f:.67f)});
   int rgb=g&0x00ffffff;
   int[] a={0,0,46,79,115,143,191,255};
   int[] cols=new int[a.length];for(int i=0;i<a.length;i++)cols[i]=((int)(a[i]*.9f)<<24)|rgb;
   cached=new LinearGradient(0,r.top,0,r.bottom,cols,new float[]{0f,.28f,.4f,.5f,.6f,.7f,.85f,1f},Shader.TileMode.CLAMP);
   glow.setShader(cached);
   vig.setShader(new LinearGradient(r.left,0,r.right,0,new int[]{0x85000000,0x38000000,0x00000000,0x1a000000,0x69000000},new float[]{0f,.25f,.52f,.75f,1f},Shader.TileMode.CLAMP));
  }
  if(bmp!=null&&!bmp.isRecycled()){
   float sc=Math.max(r.width()/(float)bmp.getWidth(),r.height()/(float)bmp.getHeight());
   float dw=bmp.getWidth()*sc,dh=bmp.getHeight()*sc,l=r.left+(r.width()-dw)/2f,t=r.top+(r.height()-dh)/2f;
   dst.set(l,t,l+dw,t+dh);cv.drawBitmap(bmp,null,dst,p);
  }
  cv.drawRect(rect,base);
  cv.drawRect(rect,glow);
  cv.drawRect(rect,vig);
  cv.restoreToCount(save);
 }
 @Override public void setAlpha(int alpha){}
 @Override public void setColorFilter(ColorFilter cf){}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
