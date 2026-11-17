package id.fyland;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONArray;
import org.json.JSONObject;

/** Current weather for the island's weather page. Open-Meteo needs no key; the place is the saved city or, failing that, the network's rough location. */
final class Weather {
 interface Done{void done();}
 static volatile String city="",desc="",icon="",error="";
 static volatile int temp,hi,lo,code,wind;
 static volatile double precip;
 /** Hour of day (0-23) for every forecast pill; the label is built when drawing so the 12/24 hour setting applies at once. */
 static volatile int[] hrHour=new int[0];
 static volatile boolean isDay=true;
 /** Next hours for the forecast pills: label ("3PM"), temperature and WMO code. */
 static volatile String[] hrLabel=new String[0];static volatile int[] hrTemp=new int[0],hrCode=new int[0];
 static volatile long fetched;
 static volatile boolean loading,failed;
 static final Handler main=new Handler(Looper.getMainLooper());

 /** The phone's own weather app: a known package first, else a system app whose package or label says weather / cuaca. null when there is none. */
 static Intent builtInApp(Context c){
  PackageManager pm=c.getPackageManager();
  String[] known={"com.transsion.weather","com.transsion.hiweather","com.android.weather","com.google.android.apps.weather","com.sec.android.daemonservice","com.miui.weather2","com.coloros.weather.service","com.oplus.weather"};
  for(String p:known){try{Intent i=pm.getLaunchIntentForPackage(p);if(i!=null&&!p.equals("com.sec.android.daemonservice"))return i;}catch(Exception e){ErrorLog.w(e);}}
  try{
   java.util.List<android.content.pm.ResolveInfo> l=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
   for(android.content.pm.ResolveInfo r:l){
    String pkg=r.activityInfo.packageName;if(pkg.equals(c.getPackageName()))continue;
    boolean system=(r.activityInfo.applicationInfo.flags&(android.content.pm.ApplicationInfo.FLAG_SYSTEM|android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;
    if(!system)continue;
    String label=String.valueOf(r.loadLabel(pm)).toLowerCase(java.util.Locale.ROOT);
    if(pkg.toLowerCase(java.util.Locale.ROOT).contains("weather")||label.contains("weather")||label.contains("cuaca")){
     Intent i=pm.getLaunchIntentForPackage(pkg);if(i!=null)return i;
    }
   }
  }catch(Exception e){ErrorLog.w(e);}
  return null;
 }
 /** Opens the phone's own weather app. False when there is none or it cannot be started. */
 static boolean openBuiltIn(Context c){
  try{Intent i=builtInApp(c);if(i==null)return false;i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);c.startActivity(i);return true;}
  catch(Exception e){ErrorLog.w(e);return false;}
 }
 /** Default unit follows the region: Fahrenheit only where it is the local norm, otherwise Celsius. */
 static boolean fahrenheitDefault(){String r=java.util.Locale.getDefault().getCountry();return r.equals("US")||r.equals("LR")||r.equals("MM")||r.equals("BS")||r.equals("BZ")||r.equals("KY")||r.equals("PW");}
 static boolean fahrenheit(Context c){return Prefs.flag(c,"weather_f",fahrenheitDefault());}
 /** Letter shown after the degree sign, so Fahrenheit is never mistaken for Celsius. */
 static String unitMark(Context c){return fahrenheit(c)?"F":"";}
 static boolean hour24(Context c){return Prefs.flag(c,"weather_24h",false);}
 /** Celsius value as shown to the person (Celsius or Fahrenheit, per the setting). */
 static int shown(Context c,int celsius){return fahrenheit(c)?(int)Math.round(celsius*9/5.0+32):celsius;}
 /** "3PM" or "15:00". */
 static String hourText(Context c,int h){return hour24(c)?String.format(java.util.Locale.US,"%02d:00",h):(h%12==0?12:h%12)+(h<12?"AM":"PM");}
 static boolean fresh(){return fetched>0&&System.currentTimeMillis()-fetched<15*60*1000L;}

 static void load(final Context c,final boolean force,final Done done){
  if(loading)return;
  if(!force&&fresh())return;
  loading=true;failed=false;
  new Thread(new Runnable(){public void run(){
   try{
    String saved=Prefs.get(c).getString("weather_city","").trim();
    double lat,lon;String name;
    String coord=Prefs.get(c).getString("weather_coord","");
    if(!saved.isEmpty()&&coord.contains(",")){
     String[] ll=coord.split(",");lat=Double.parseDouble(ll[0]);lon=Double.parseDouble(ll[1]);name=saved;
    }else if(!saved.isEmpty()){
     JSONObject g=new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&language="+java.util.Locale.getDefault().getLanguage()+"&name="+URLEncoder.encode(saved,"UTF-8")));
     JSONArray r=g.optJSONArray("results");
     if(r==null||r.length()==0)throw new IOException(L.s(R.string.s554)+saved+L.s(R.string.s555));
     JSONObject o=r.getJSONObject(0);
     lat=o.getDouble("latitude");lon=o.getDouble("longitude");name=o.optString("name",saved);
    }else{
     double[] fix=bestFix(c);
     if(fix!=null){lat=fix[0];lon=fix[1];name=placeName(c,lat,lon);}
     else{
      JSONObject ip=new JSONObject(get("https://ipwho.is/"));
      if(!ip.optBoolean("success",true))throw new IOException(L.s(R.string.s561));
      lat=ip.getDouble("latitude");lon=ip.getDouble("longitude");name=ip.optString("city","");
     }
    }
    JSONObject w=new JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon+"&current=temperature_2m,weather_code,is_day,wind_speed_10m,precipitation&hourly=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=3"));
    JSONObject cur=w.getJSONObject("current");
    int t=(int)Math.round(cur.getDouble("temperature_2m"));
    int wc=cur.getInt("weather_code");
    boolean day=cur.optInt("is_day",1)==1;
    wind=(int)Math.round(cur.optDouble("wind_speed_10m",0));precip=cur.optDouble("precipitation",0);
    JSONObject d=w.getJSONObject("daily");
    hi=(int)Math.round(d.getJSONArray("temperature_2m_max").getDouble(0));
    lo=(int)Math.round(d.getJSONArray("temperature_2m_min").getDouble(0));
    String now=cur.optString("time","");
    JSONObject hr=w.optJSONObject("hourly");
    if(hr!=null){
     JSONArray ht=hr.getJSONArray("time"),hv=hr.getJSONArray("temperature_2m"),hc=hr.getJSONArray("weather_code");
     int start=0;
     for(int i=0;i<ht.length();i++){if(ht.getString(i).compareTo(now.length()>=13?now.substring(0,13):now)>=0){start=i;break;}}
     int n=Math.min(24,ht.length()-start);if(n<0)n=0;
     String[] l=new String[n];int[] tv=new int[n],cv=new int[n],hh=new int[n];
     for(int i=0;i<n;i++){
      String ts=ht.getString(start+i);int h=Integer.parseInt(ts.substring(11,13));
      l[i]=(h%12==0?12:h%12)+(h<12?"AM":"PM");hh[i]=h;
      tv[i]=(int)Math.round(hv.getDouble(start+i));cv[i]=hc.getInt(start+i);
     }
     hrLabel=l;hrTemp=tv;hrCode=cv;hrHour=hh;
    }
    isDay=day;
    temp=t;code=wc;city=name;desc=describe(wc);icon=icon(wc,day);
    fetched=System.currentTimeMillis();failed=false;error="";
   }catch(Exception e){ErrorLog.w(e);
    failed=true;error=e.getMessage()==null?L.s(R.string.s575):e.getMessage();
   }
   loading=false;
   if(done!=null)main.post(new Runnable(){public void run(){done.done();}});
  }}).start();
 }

 /** Location for the weather: a recent fix if there is one, otherwise a fresh single update (the last known fix can be hours old or missing), and the stale one only as a last resort. */
 static double[] bestFix(Context c){
  try{
   if(c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return null;
   LocationManager lm=(LocationManager)c.getSystemService(Context.LOCATION_SERVICE);Location best=null;
   for(String p:lm.getProviders(true)){Location l=lm.getLastKnownLocation(p);if(l!=null&&(best==null||l.getTime()>best.getTime()))best=l;}
   if(best!=null&&System.currentTimeMillis()-best.getTime()<10*60*1000L&&best.hasAccuracy()&&best.getAccuracy()<=300)return new double[]{best.getLatitude(),best.getLongitude()};
   Location now=freshFix(lm);
   if(now!=null)return new double[]{now.getLatitude(),now.getLongitude()};
   return best==null?null:new double[]{best.getLatitude(),best.getLongitude()};
  }catch(Exception e){ErrorLog.w(e);return null;}
 }
 /** One new fix from the network, then GPS if the network gave none. Blocks the worker thread for at most about 12 seconds. */
 static Location freshFix(LocationManager lm){
  String[] order={LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER};
  Location top=null;
  for(String prov:order){
   try{
    if(!lm.isProviderEnabled(prov))continue;
    final Location[] out=new Location[1];final java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);
    if(android.os.Build.VERSION.SDK_INT>=30){
     lm.getCurrentLocation(prov,null,new java.util.concurrent.Executor(){public void execute(Runnable r){main.post(r);}},new java.util.function.Consumer<Location>(){public void accept(Location l){out[0]=l;latch.countDown();}});
    }else{
     lm.requestSingleUpdate(prov,new android.location.LocationListener(){
      public void onLocationChanged(Location l){out[0]=l;latch.countDown();}
      public void onStatusChanged(String p,int s,android.os.Bundle b){}
      public void onProviderEnabled(String p){}
      public void onProviderDisabled(String p){latch.countDown();}
     },Looper.getMainLooper());
    }
    latch.await(6,java.util.concurrent.TimeUnit.SECONDS);
    if(out[0]!=null){
     if(top==null||(out[0].hasAccuracy()&&(!top.hasAccuracy()||out[0].getAccuracy()<top.getAccuracy())))top=out[0];
     if(top.hasAccuracy()&&top.getAccuracy()<=150)return top;
    }
   }catch(Exception e){ErrorLog.w(e);}
  }
  return top;
 }

 /** Neighbourhood-level name (kecamatan, kota) from the device geocoder, falling back to OpenStreetMap when the device has none. */
 static String placeName(Context c,double lat,double lon){
  String n="";
  try{
   if(android.location.Geocoder.isPresent()){
    java.util.List<android.location.Address> a=new android.location.Geocoder(c,java.util.Locale.getDefault()).getFromLocation(lat,lon,5);
    if(a!=null&&!a.isEmpty()){android.location.Address x=a.get(0);for(android.location.Address y:a)if(y.getSubLocality()!=null&&!y.getSubLocality().trim().isEmpty()){x=y;break;}n=parts(x.getSubLocality(),x.getLocality(),x.getSubAdminArea());}
   }
  }catch(Exception e){ErrorLog.w(e);}
  if(n.isEmpty()){
   try{
    JSONObject a=new JSONObject(get("https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18&addressdetails=1&accept-language="+java.util.Locale.getDefault().toLanguageTag()+"&lat="+lat+"&lon="+lon)).optJSONObject("address");
    if(a!=null){
     String city=a.optString("city",a.optString("county",a.optString("town","")));
     String kel=a.optString("village",a.optString("suburb",a.optString("neighbourhood","")));
     String kec=a.optString("city_district",a.optString("municipality",""));
     n=parts(kel,kec,city);
    }
   }catch(Exception e){ErrorLog.w(e);}
  }
  return n;
 }
 /** "Beji Timur, Beji, Depok": kelurahan, kecamatan, city (each without its prefix, duplicates and empty parts dropped). */
 static String parts(String... in){
  StringBuilder sb=new StringBuilder();java.util.ArrayList<String> seen=new java.util.ArrayList<String>();
  for(String p:in){String t=clean(p);if(t.isEmpty())continue;boolean dup=false;for(String q:seen)if(q.equalsIgnoreCase(t))dup=true;if(dup)continue;seen.add(t);if(sb.length()>0)sb.append(", ");sb.append(t);}
  return sb.toString();
 }
 /** "Beji, Depok": the small area first, then the city without its "Kota"/"Kabupaten" prefix. Falls back to whichever part exists. */
 static String join(String area,String sub,String city){
  String a=clean(area);if(a.isEmpty())a=clean(sub);
  String t=clean(city);
  if(a.isEmpty())return t;
  if(t.isEmpty()||t.equalsIgnoreCase(a))return a;
  return a+", "+t;
 }
 static String clean(String s){
  if(s==null)return "";
  s=s.trim();
  String[] pre={"Kelurahan ","Kel. ","Desa ","Kecamatan ","Kec. ","Kota Administrasi ","Kota ","Kabupaten ","Kab. "};
  for(String p:pre)if(s.regionMatches(true,0,p,0,p.length())){s=s.substring(p.length()).trim();break;}
  return s;
 }

 static String get(String url)throws IOException{
  HttpURLConnection con=(HttpURLConnection)new URL(url).openConnection();
  con.setConnectTimeout(8000);con.setReadTimeout(8000);con.setRequestProperty("User-Agent","Fyland/1.0 (Android app; open-source; see project repository for contact)");
  try{
   if(con.getResponseCode()!=200)throw new IOException(L.s(R.string.s591)+con.getResponseCode());
   BufferedReader r=new BufferedReader(new InputStreamReader(con.getInputStream(),"UTF-8"));
   StringBuilder sb=new StringBuilder();String line;
   while((line=r.readLine())!=null)sb.append(line);
   return sb.toString();
  }finally{con.disconnect();}
 }

 static String describe(int c){
  if(c==0)return L.s(R.string.s592);
  if(c==1||c==2)return L.s(R.string.s593);
  if(c==3)return L.s(R.string.s594);
  if(c==45||c==48)return L.s(R.string.s595);
  if(c>=51&&c<=57)return L.s(R.string.s596);
  if(c>=61&&c<=67)return L.s(R.string.s597);
  if(c>=71&&c<=77)return L.s(R.string.s598);
  if(c>=80&&c<=82)return L.s(R.string.s599);
  if(c==85||c==86)return L.s(R.string.s600);
  if(c>=95)return L.s(R.string.s601);
  return L.s(R.string.s594);
 }
 static String icon(int c,boolean day){
  if(c==0)return day?"☀️":"🌙";
  if(c==1||c==2)return day?"⛅":"☁️";
  if(c==3)return "☁️";
  if(c==45||c==48)return "🌫️";
  if(c>=51&&c<=57)return "🌦️";
  if(c>=61&&c<=67)return "🌧️";
  if(c>=71&&c<=77)return "❄️";
  if(c>=80&&c<=82)return "🌦️";
  if(c==85||c==86)return "🌨️";
  if(c>=95)return "⛈️";
  return "☁️";
 }
}
