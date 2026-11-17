package id.fyland;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** A small Lottie player for shape layers only: bezier path morphs, fills, strokes, layer transforms and cubic easing.
 *  Enough for res/raw/face_id.json (the Face ID animation) without pulling in a library. */
final class LottieLite {
 /** One animated (or constant) value. Scalars, vectors and whole bezier shapes are all float arrays, so one interpolator covers them. */
 static final class Prop {
  float[] t;float[][] v;float[][] ease;boolean[] hold;float[] out;
  float[] at(float f){
   int n=t.length;
   if(n==1||f<=t[0])return v[0];
   if(f>=t[n-1])return v[n-1];
   int k=0;while(k<n-2&&f>=t[k+1])k++;
   float[] a=v[k],b=v[k+1];
   if(hold[k])return a;
   float x=(f-t[k])/(t[k+1]-t[k]);
   float[] e=ease[k];
   float m=e==null?x:bezier(e[0],e[1],e[2],e[3],x);
   for(int j=0;j<out.length;j++)out[j]=a[j]+(b[j]-a[j])*m;
   return out;
  }
 }
 static final class Item {int kind;Prop shape,color,opacity,width;int cap,join;float miter=4f;} // kind: 0 path, 1 fill, 2 stroke
 static final class Layer {boolean background;float in,out;Prop a,p,s,r,o;final ArrayList<Item> items=new ArrayList<Item>();}

 final ArrayList<Layer> layers=new ArrayList<Layer>();
 float width,height,fps,lastFrame;
 final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 final Path path=new Path();

 /** Solves a CSS-style cubic-bezier easing curve for progress x (0..1). */
 static float bezier(float x1,float y1,float x2,float y2,float x){
  if(x<=0f)return 0f;if(x>=1f)return 1f;
  float lo=0f,hi=1f,m=.5f;
  for(int i=0;i<24;i++){
   m=(lo+hi)/2f;float u=1f-m;
   float bx=3f*u*u*m*x1+3f*u*m*m*x2+m*m*m;
   if(bx<x)lo=m;else hi=m;
  }
  float u=1f-m;
  return 3f*u*u*m*y1+3f*u*m*m*y2+m*m*m;
 }

 static LottieLite load(Context c,String rawName)throws Exception{
  int id=c.getResources().getIdentifier(rawName,"raw",c.getPackageName());
  if(id==0)throw new IllegalStateException("raw/"+rawName+" missing");
  InputStream in=c.getResources().openRawResource(id);
  ByteArrayOutputStream bo=new ByteArrayOutputStream();
  byte[] buf=new byte[8192];int n;
  try{while((n=in.read(buf))>0)bo.write(buf,0,n);}finally{in.close();}
  return parse(new JSONObject(new String(bo.toByteArray(),"UTF-8")));
 }

 static LottieLite parse(JSONObject root)throws Exception{
  LottieLite l=new LottieLite();
  l.width=(float)root.optDouble("w",100);l.height=(float)root.optDouble("h",100);l.fps=(float)root.optDouble("fr",60);l.lastFrame=(float)root.optDouble("op",1);
  JSONArray ls=root.getJSONArray("layers");
  for(int i=0;i<ls.length();i++){
   JSONObject lo=ls.getJSONObject(i);
   if(lo.optInt("ty",-1)!=4)continue;
   Layer L=new Layer();
   L.background="Face BG".equals(lo.optString("nm"));
   L.in=(float)lo.optDouble("ip",0);L.out=(float)lo.optDouble("op",1e9);
   JSONObject ks=lo.getJSONObject("ks");
   L.a=prop(ks.getJSONObject("a"));L.p=prop(ks.getJSONObject("p"));L.s=prop(ks.getJSONObject("s"));L.r=prop(ks.getJSONObject("r"));L.o=prop(ks.getJSONObject("o"));
   JSONArray shapes=lo.getJSONArray("shapes");
   for(int j=0;j<shapes.length();j++){
    JSONObject so=shapes.getJSONObject(j);String ty=so.optString("ty");
    Item it=new Item();
    if("sh".equals(ty)){it.kind=0;it.shape=prop(so.getJSONObject("ks"));}
    else if("fl".equals(ty)){it.kind=1;it.color=prop(so.getJSONObject("c"));it.opacity=prop(so.getJSONObject("o"));}
    else if("st".equals(ty)){
     it.kind=2;it.color=prop(so.getJSONObject("c"));it.opacity=prop(so.getJSONObject("o"));it.width=prop(so.getJSONObject("w"));
     it.cap=so.optInt("lc",1);it.join=so.optInt("lj",1);it.miter=(float)so.optDouble("ml",4);
    }else continue;
    L.items.add(it);
   }
   l.layers.add(L);
  }
  return l;
 }

 static Prop prop(JSONObject o)throws Exception{
  Prop p=new Prop();Object k=o.get("k");
  if(o.optInt("a",0)==1&&k instanceof JSONArray){
   JSONArray ks=(JSONArray)k;int n=ks.length();
   p.t=new float[n];p.v=new float[n][];p.ease=new float[n][];p.hold=new boolean[n];
   for(int i=0;i<n;i++){
    JSONObject kf=ks.getJSONObject(i);
    p.t[i]=(float)kf.getDouble("t");p.v[i]=vec(kf.get("s"));
    JSONObject eo=kf.optJSONObject("o"),ei=kf.optJSONObject("i");
    if(eo!=null&&ei!=null)p.ease[i]=new float[]{(float)eo.optDouble("x",0),(float)eo.optDouble("y",0),(float)ei.optDouble("x",1),(float)ei.optDouble("y",1)};
    p.hold[i]=kf.optInt("h",0)==1;
   }
  }else{
   p.t=new float[]{0};p.v=new float[][]{vec(k)};p.ease=new float[1][];p.hold=new boolean[1];
  }
  p.out=new float[p.v[0].length];
  return p;
 }

 static float[] vec(Object x)throws Exception{
  if(x instanceof Number)return new float[]{((Number)x).floatValue()};
  if(x instanceof JSONObject)return shape((JSONObject)x);
  JSONArray a=(JSONArray)x;
  if(a.length()>0&&a.get(0) instanceof JSONObject)return shape(a.getJSONObject(0));
  float[] r=new float[a.length()];
  for(int i=0;i<r.length;i++)r[i]=(float)a.getDouble(i);
  return r;
 }

 /** [closed, n, v(2n), in(2n), out(2n)] */
 static float[] shape(JSONObject o)throws Exception{
  JSONArray v=o.getJSONArray("v"),i=o.getJSONArray("i"),out=o.getJSONArray("o");
  int n=v.length();float[] r=new float[2+6*n];
  r[0]=o.optBoolean("c",false)?1f:0f;r[1]=n;
  for(int k=0;k<n;k++){
   JSONArray pv=v.getJSONArray(k),pi=i.getJSONArray(k),po=out.getJSONArray(k);
   r[2+2*k]=(float)pv.getDouble(0);r[3+2*k]=(float)pv.getDouble(1);
   r[2+2*n+2*k]=(float)pi.getDouble(0);r[3+2*n+2*k]=(float)pi.getDouble(1);
   r[2+4*n+2*k]=(float)po.getDouble(0);r[3+4*n+2*k]=(float)po.getDouble(1);
  }
  return r;
 }

 static void addShape(Path path,float[] s){
  int n=(int)s[1];if(n<1)return;
  int vo=2,io=2+2*n,oo=2+4*n;
  path.moveTo(s[vo],s[vo+1]);
  for(int k=1;k<n;k++){
   path.cubicTo(s[vo+2*k-2]+s[oo+2*k-2],s[vo+2*k-1]+s[oo+2*k-1],s[vo+2*k]+s[io+2*k],s[vo+2*k+1]+s[io+2*k+1],s[vo+2*k],s[vo+2*k+1]);
  }
  if(s[0]>0f&&n>1){
   int e=n-1;
   path.cubicTo(s[vo+2*e]+s[oo+2*e],s[vo+2*e+1]+s[oo+2*e+1],s[vo]+s[io],s[vo+1]+s[io+1],s[vo],s[vo+1]);
   path.close();
  }
 }

 /** Draws one frame in composition pixels (width x height). The caller sets the canvas transform. */
 void draw(Canvas c,float frame,boolean skipBackground){
  for(int li=layers.size()-1;li>=0;li--){
   Layer L=layers.get(li);
   if(skipBackground&&L.background)continue;
   if(frame<L.in||frame>L.out)continue;
   float layerAlpha=L.o.at(frame)[0]/100f;
   if(layerAlpha<=.001f)continue;
   float[] a=L.a.at(frame),p=L.p.at(frame),s=L.s.at(frame),r=L.r.at(frame);
   c.save();
   c.translate(p[0],p[1]);c.rotate(r[0]);c.scale(s[0]/100f,s[1]/100f);c.translate(-a[0],-a[1]);
   path.reset();
   for(int ii=0;ii<L.items.size();ii++){
    Item it=L.items.get(ii);
    if(it.kind==0){addShape(path,it.shape.at(frame));continue;}
    float[] col=it.color.at(frame);
    float alpha=Math.max(0f,Math.min(1f,it.opacity.at(frame)[0]/100f*layerAlpha));
    paint.reset();paint.setAntiAlias(true);
    paint.setColor(Color.rgb(Math.round(col[0]*255f),Math.round(col[1]*255f),Math.round(col[2]*255f)));
    paint.setAlpha(Math.round(alpha*255f));
    if(it.kind==1){paint.setStyle(Paint.Style.FILL);path.setFillType(Path.FillType.WINDING);}
    else{
     paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(it.width.at(frame)[0]);paint.setStrokeMiter(it.miter);
     paint.setStrokeCap(it.cap==2?Paint.Cap.ROUND:it.cap==3?Paint.Cap.SQUARE:Paint.Cap.BUTT);
     paint.setStrokeJoin(it.join==2?Paint.Join.ROUND:it.join==3?Paint.Join.BEVEL:Paint.Join.MITER);
    }
    c.drawPath(path,paint);path.reset();
   }
   c.restore();
  }
 }
}
