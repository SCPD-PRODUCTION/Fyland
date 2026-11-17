package id.fyland;

/** Host checks for the two regressions observed in the user's recording. */
public final class RegressionChecks {
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
 public static void main(String[] args){
  String own="id.fyland";
  check(NotificationPolicy.isOwnOverlayNotice("android",own,"Fyland is displaying over other apps","If you don't want…",own),"Ignore observed overlay warning");
  check(NotificationPolicy.isOwnOverlayNotice("android","AlertWindowNotification - "+own,"System","",own),"Ignore localized warning through package tag");
  check(NotificationPolicy.isOwnOverlayNotice("com.android.systemui",null,"Fyland","Menggambar di atas aplikasi",own),"Ignore SystemUI overlay warning");
  check(NotificationPolicy.isOwnOverlayNotice(own,null,"Preview","",own),"Do not mirror app's own notifications");
  check(!NotificationPolicy.isOwnOverlayNotice("android",null,"Battery low","15% remaining",own),"Retain unrelated system notification");
  check(!NotificationPolicy.isOwnOverlayNotice("com.whatsapp",null,"Fyland","Someone mentioned the app",own),"Do not suppress actual message mentioning app");
  check(!NotificationPolicy.isOwnOverlayNotice("com.spotify.music",null,"Track title","Artist",own),"Accept music source");
  WindowAttachment window=new WindowAttachment();int[] events=new int[3];
  Runnable add=()->events[0]++,update=()->events[1]++,remove=()->events[2]++;
  for(int i=0;i<100;i++)window.show(add,update);
  check(events[0]==1&&events[1]==99&&events[2]==0,"100 updates must not detach or reattach overlay");
  window.hide(remove);window.hide(remove);
  check(!window.attached()&&events[2]==1,"Hide removes one window only");
  window.show(add,update);
  check(window.attached()&&events[0]==2,"Unlock can attach again once");
  WindowAttachment failed=new WindowAttachment();boolean threw=false;
  try{failed.show(()->{throw new IllegalStateException("Permission denied");},update);}catch(IllegalStateException expected){threw=true;}
  check(threw&&!failed.attached(),"Rejected attach must not report mounted");
  System.out.println("11 regression checks passed: overlay-warning feedback and stable window lifecycle.");
 }
}
