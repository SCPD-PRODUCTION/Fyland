package id.fyland;

import android.os.*;
import org.json.JSONObject;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** One HTTPS lookup on expanding a track, bounded responses, RAM cache, no message data. */
final class LyricsClient {
 static final class Result {
  final LyricsDocument document;final String message;final long expires;
  Result(LyricsDocument document,String message,long ttl){this.document=document;this.message=message;expires=System.currentTimeMillis()+ttl;}
 }
 interface Callback{void complete(Result result);}
 static final Map<String,Result> CACHE=Collections.synchronizedMap(new LinkedHashMap<String,Result>(24,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,Result> e){return size()>24;}});
 static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
 static volatile long cooldownUntil,lastRequest;
 final Handler main=new Handler(Looper.getMainLooper());
 int generation;
 boolean closed;
 void close(){closed=true;generation++;main.removeCallbacksAndMessages(null);}
 void load(String title,String artist,String album,long duration,Callback callback){
  final int version=++generation;String key=title+"|"+artist+"|"+album+"|"+duration;
  Result cached=CACHE.get(key);if(cached!=null&&cached.expires>System.currentTimeMillis()){callback.complete(cached);return;}
  WORKER.execute(new Runnable(){public void run(){
   Result result=CACHE.get(key);
   if(result==null||result.expires<System.currentTimeMillis()){
    try{
     if(closed||Thread.currentThread().isInterrupted())return;
     if(title==null||title.trim().isEmpty()||artist==null||artist.trim().isEmpty())result=new Result(null,L.s(R.string.s181),60000);
     else if(System.currentTimeMillis()<cooldownUntil)result=new Result(null,L.s(R.string.s182),30000);
     else{
      long wait=500-(System.currentTimeMillis()-lastRequest);if(wait>0)Thread.sleep(wait);
      String query="https://lrclib.net/api/get?track_name="+URLEncoder.encode(title,"UTF-8")+"&artist_name="+URLEncoder.encode(artist,"UTF-8");
      if(album!=null&&!album.isEmpty())query+="&album_name="+URLEncoder.encode(album,"UTF-8");
      if(duration>=1000&&duration<=3600000)query+="&duration="+duration/1000;
      lastRequest=System.currentTimeMillis();result=request(query);
     }
    }catch(InterruptedException e){ErrorLog.w(e);Thread.currentThread().interrupt();return;}
    catch(Exception e){ErrorLog.w(e);result=new Result(null,L.s(R.string.s187),30000);}
    CACHE.put(key,result);
   }
   final Result finalResult=result;main.post(new Runnable(){public void run(){if(!closed&&generation==version)callback.complete(finalResult);}});
  }});
 }
 Result request(String url)throws Exception{
  HttpURLConnection connection=(HttpURLConnection)new URL(url).openConnection();
  try{
   connection.setConnectTimeout(7000);connection.setReadTimeout(7000);connection.setInstanceFollowRedirects(false);
   connection.setRequestProperty("User-Agent","Fyland/1.0 (Android)");
   connection.setRequestProperty("Accept","application/json");
   int code=connection.getResponseCode();
   if(code==429){long delay=60;try{delay=Math.max(1,Long.parseLong(connection.getHeaderField("Retry-After")));}catch(Exception ignored){ErrorLog.w(ignored);long date=connection.getHeaderFieldDate("Retry-After",0);if(date>System.currentTimeMillis())delay=(date-System.currentTimeMillis())/1000+1;}cooldownUntil=System.currentTimeMillis()+delay*1000+1000;return new Result(null,L.s(R.string.s193),Math.max(30000,delay*1000));}
   if(code==404)return new Result(null,L.s(R.string.s194),600000);
   if(code!=200)return new Result(null,L.s(R.string.s195),60000);
   ByteArrayOutputStream data=new ByteArrayOutputStream();
   try(InputStream in=connection.getInputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(data.size()+n>768*1024)throw new IOException("Response too large");data.write(buffer,0,n);}}
   JSONObject json=new JSONObject(data.toString("UTF-8"));
   if(json.optBoolean("instrumental",false))return new Result(null,L.s(R.string.s198),3600000);
   String synced=json.isNull("syncedLyrics")?"":json.optString("syncedLyrics",""),plain=json.isNull("plainLyrics")?"":json.optString("plainLyrics","");
   LyricsDocument doc=LyricsDocument.parse(synced,plain);
   if(doc.lines.isEmpty()&&doc.plain.trim().isEmpty())return new Result(null,L.s(R.string.s194),600000);
   return new Result(doc,doc.lines.isEmpty()?L.s(R.string.s201):L.s(R.string.s202),3600000);
  }finally{connection.disconnect();}
 }
}
