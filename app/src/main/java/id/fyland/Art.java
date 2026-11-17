package id.fyland;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Album-art helpers: centre-cropped circular drawing, and background loading for art that players give as a URI. */
final class Art {
 private Art(){}
 private static final int MAX_SIDE=640,MAX_BYTES=3*1024*1024;
 private static final LinkedHashMap<String,Bitmap> cache=new LinkedHashMap<String,Bitmap>(8,.75f,true){
  @Override protected boolean removeEldestEntry(Map.Entry<String,Bitmap> eldest){return size()>8;}
 };
 private static final HashSet<String> pending=new HashSet<>(),failed=new HashSet<>();
 private static final ExecutorService pool=Executors.newSingleThreadExecutor();

 static Bitmap cached(String uri){synchronized(cache){return cache.get(uri);}}
 static boolean failed(String uri){synchronized(cache){return failed.contains(uri);}}

 /** Starts a background load once per URI. When it finishes, the island rebuilds its music view. */
 static void request(Context context,final String uri){
  final Context app=context.getApplicationContext()==null?context:context.getApplicationContext();
  synchronized(cache){if(cache.containsKey(uri)||pending.contains(uri)||failed.contains(uri))return;pending.add(uri);}
  pool.execute(new Runnable(){public void run(){
   Bitmap bitmap=decode(app,uri);
   synchronized(cache){pending.remove(uri);if(bitmap!=null)cache.put(uri,bitmap);else failed.add(uri);}
   if(bitmap!=null)new Handler(Looper.getMainLooper()).post(new Runnable(){public void run(){
    IslandListener live=IslandListener.live;if(live!=null){live.mediaChanged();
     try{IslandOverlay ov=live.overlay;if(ov!=null&&ov.current!=null&&!ov.current.isMedia()){ov.fingerprint="";ov.render();}}catch(Throwable t){ErrorLog.w(t);}}
   }});
  }});
 }

 static Bitmap decode(Context context,String value){
  try{
   Uri uri=Uri.parse(value);byte[] data=read(context,uri);if(data==null)return null;
   BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);
   if(bounds.outWidth<=0||bounds.outHeight<=0)return null;
   int sample=1;while(bounds.outWidth/(sample*2)>=MAX_SIDE&&bounds.outHeight/(sample*2)>=MAX_SIDE)sample*=2;
   BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=sample;
   return BitmapFactory.decodeByteArray(data,0,data.length,options);
  }catch(Throwable t){ErrorLog.w(t);return null;}
 }

 private static byte[] read(Context context,Uri uri)throws IOException{
  String scheme=uri.getScheme();InputStream in=null;HttpURLConnection connection=null;
  try{
   if("http".equals(scheme)||"https".equals(scheme)){
    connection=(HttpURLConnection)new URL(uri.toString()).openConnection();connection.setConnectTimeout(4000);connection.setReadTimeout(5000);
    in=connection.getInputStream();
   }else in=context.getContentResolver().openInputStream(uri);
   if(in==null)return null;
   ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n,total=0;
   while((n=in.read(buffer))>0){total+=n;if(total>MAX_BYTES)return null;out.write(buffer,0,n);}
   return out.toByteArray();
  }finally{
   try{if(in!=null)in.close();}catch(IOException ignored){ErrorLog.w(ignored);}
   if(connection!=null)connection.disconnect();
  }
 }

 /** Any drawable (an app icon, a notification large icon) as a square bitmap, so it can be cropped like real art. */
 static Bitmap toBitmap(Drawable drawable,int size){
  if(drawable==null)return null;
  try{
   if(drawable instanceof BitmapDrawable&&((BitmapDrawable)drawable).getBitmap()!=null)return ((BitmapDrawable)drawable).getBitmap();
   Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
   Canvas canvas=new Canvas(bitmap);drawable.setBounds(0,0,size,size);drawable.draw(canvas);
   return bitmap;
  }catch(Throwable t){ErrorLog.w(t);return null;}
 }

 /** Fills the view's bounds with a centre-cropped circle, whatever the bitmap's aspect ratio. */
 static final class Circle extends Drawable {
  final Bitmap bitmap;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);final BitmapShader shader;final Matrix matrix=new Matrix();
  Circle(Bitmap bitmap){this.bitmap=bitmap;shader=new BitmapShader(bitmap,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);paint.setShader(shader);}
  @Override protected void onBoundsChange(Rect r){
   float w=Math.max(1,bitmap.getWidth()),h=Math.max(1,bitmap.getHeight());
   float scale=Math.max(r.width()/w,r.height()/h);
   matrix.setScale(scale,scale);matrix.postTranslate(r.left+(r.width()-w*scale)/2f,r.top+(r.height()-h*scale)/2f);
   shader.setLocalMatrix(matrix);
  }
  @Override public void draw(Canvas canvas){Rect r=getBounds();canvas.drawCircle(r.exactCenterX(),r.exactCenterY(),Math.min(r.width(),r.height())/2f,paint);}
  @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
  @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
  @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
 }
}
