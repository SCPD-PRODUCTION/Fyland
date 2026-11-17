package id.fyland;
import android.content.*;
import android.os.UserManager;
final class Prefs {
 private static boolean moved;
 /** Settings live in device-protected storage so the island can start before the first unlock after boot. */
 static Context store(Context c){
  Context app=c.getApplicationContext();if(app==null)app=c;
  Context device=app.isDeviceProtectedStorage()?app:app.createDeviceProtectedStorageContext();
  if(!moved&&device!=app){
   try{
    UserManager um=(UserManager)app.getSystemService(Context.USER_SERVICE);
    if(um==null||um.isUserUnlocked()){device.moveSharedPreferencesFrom(app,"island");moved=true;}
   }catch(Exception ignored){}
  }
  return device;
 }
 static SharedPreferences get(Context c){return store(c).getSharedPreferences("island",Context.MODE_PRIVATE);}
 static boolean enabled(Context c){return get(c).getBoolean("enabled",true);}
 static int value(Context c,String k,int d){return get(c).getInt(k,d);}
 static boolean flag(Context c,String k,boolean d){return get(c).getBoolean(k,d);}
 static void update(Context c){
  KeepAliveService.sync(c);
  if(IslandListener.live!=null)IslandListener.live.refresh();
  else IslandAccess.refreshStandalone();
 }
}
