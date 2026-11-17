package id.fyland;
import java.util.Locale;
final class NotificationPolicy {
 private NotificationPolicy(){}
 static boolean isOwnOverlayNotice(String pkg,String tag,String title,String body,String ownPackage){
  if(ownPackage.equals(pkg))return true;
  if(!"android".equals(pkg)&&!"com.android.systemui".equals(pkg))return false;
  String value=((tag==null?"":tag)+" "+(title==null?"":title)+" "+(body==null?"":body)).toLowerCase(Locale.ROOT);
  return value.contains(ownPackage.toLowerCase(Locale.ROOT))||value.contains("fyland");
 }
}
