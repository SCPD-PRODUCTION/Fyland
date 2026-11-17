package id.fyland;

import android.content.Context;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** App-wide error trace shown from the main screen. Caught exceptions and crashes from every feature end up here. Stored in device-protected storage so it also works before the first unlock. */
final class ErrorLog {
 static final int MAX_BYTES=64*1024;
 private static Context ctx;
 private static final StringBuilder pending=new StringBuilder();
 private static String lastKey="";private static long lastAt;

 static void init(Context c){
  Context app=c.getApplicationContext();ctx=app==null?c:app;
  final Thread.UncaughtExceptionHandler old=Thread.getDefaultUncaughtExceptionHandler();
  Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler(){public void uncaughtException(Thread t,Throwable e){
   try{write("CRASH thread="+t.getName()+"\n"+trace(e,14));}catch(Throwable ignored){}
   if(old!=null)old.uncaughtException(t,e);
  }});
 }

 /** A caught exception. Expected flow-control exceptions are not recorded. */
 static void w(Throwable e){
  if(e==null||e instanceof NumberFormatException||e instanceof InterruptedException)return;
  try{
   String where=where(e);
   String key=e.getClass().getName()+"|"+e.getMessage()+"|"+where;
   long now=System.currentTimeMillis();
   synchronized(ErrorLog.class){if(key.equals(lastKey)&&now-lastAt<30000){lastAt=now;return;}lastKey=key;lastAt=now;}
   write("["+where+"] "+e.getClass().getSimpleName()+(e.getMessage()==null?"":": "+e.getMessage()));
  }catch(Throwable ignored){}
 }

 /** A problem the code detected itself (no exception object). */
 static void w(String feature,String message){try{write("["+feature+"] "+message);}catch(Throwable ignored){}}

 private static String where(Throwable e){
  Throwable t=e;while(t.getCause()!=null&&t.getCause()!=t)t=t.getCause();
  StringBuilder b=new StringBuilder();int n=0;
  for(StackTraceElement el:t.getStackTrace()){
   if(el.getClassName().startsWith("id.fyland")&&!el.getClassName().endsWith("ErrorLog")){
    if(b.length()>0)b.append(" < ");
    String cn=el.getClassName();b.append(cn.substring(cn.lastIndexOf('.')+1)).append('.').append(el.getMethodName()).append(':').append(el.getLineNumber());
    if(++n>=2)break;
   }
  }
  if(b.length()==0){StackTraceElement[] st=t.getStackTrace();b.append(st.length>0?st[0].getClassName()+":"+st[0].getLineNumber():"?");}
  return b.toString();
 }

 private static String trace(Throwable e,int frames){
  StringWriter sw=new StringWriter();e.printStackTrace(new PrintWriter(sw));
  String[] lines=sw.toString().split("\n");StringBuilder b=new StringBuilder();
  for(int i=0;i<lines.length&&i<frames;i++){if(i>0)b.append('\n');b.append(lines[i]);}
  return b.toString();
 }

 private static File file(){
  if(ctx==null)return null;
  Context d=ctx.isDeviceProtectedStorage()?ctx:ctx.createDeviceProtectedStorageContext();
  return new File(d.getFilesDir(),"error_log.txt");
 }

 private static synchronized void write(String entry){
  String line=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.ROOT).format(new Date())+"  "+entry+"\n";
  pending.append(line);
  File f=file();if(f==null)return;
  try{
   File dir=f.getParentFile();if(dir!=null&&!dir.exists())dir.mkdirs();
   if(f.length()>MAX_BYTES){String all=readFile(f);f.delete();int cut=all.indexOf('\n',all.length()/2);writeFile(f,cut<0?"":all.substring(cut+1),false);}
   writeFile(f,pending.toString(),true);pending.setLength(0);
  }catch(Throwable ignored){
   if(pending.length()>8192)pending.delete(0,pending.length()-8192);
  }
 }
 private static void writeFile(File f,String text,boolean append)throws IOException{FileOutputStream o=new FileOutputStream(f,append);try{o.write(text.getBytes("UTF-8"));}finally{o.close();}}
 private static String readFile(File f){
  try{FileInputStream i=new FileInputStream(f);try{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[4096];int r;while((r=i.read(buf))>0)b.write(buf,0,r);return b.toString("UTF-8");}finally{i.close();}}catch(Throwable e){return "";}
 }

 static synchronized String read(){
  File f=file();String s=f!=null&&f.exists()?readFile(f):"";
  if(pending.length()>0)s+=pending;
  return s.isEmpty()?L.s(R.string.s41):s.trim();
 }
 static synchronized int count(){
  File f=file();String s=f!=null&&f.exists()?readFile(f):"";
  if(pending.length()>0)s+=pending;
  int n=0;for(int i=0;i<s.length();i++)if(s.charAt(i)=='\n')n++;
  return s.isEmpty()?0:n+(s.endsWith("\n")?0:1);
 }
 static synchronized void clear(){File f=file();if(f!=null)f.delete();pending.setLength(0);lastKey="";}
}
