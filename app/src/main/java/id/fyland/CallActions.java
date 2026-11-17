package id.fyland;

import java.util.Locale;

/** Decides whether a call-notification button answers or declines, from its label in common languages. */
final class CallActions {
 private CallActions(){}
 static final int UNKNOWN=0,ANSWER=1,DECLINE=2;
 private static final String[] YES={"answer","accept","pick up","jawab","terima","angkat","atender","aceptar","répondre","repondre","antworten","annehmen","接听","接聽","응답"};
 private static final String[] NO={"decline","reject","hang up","hangup","end call","tolak","akhiri","putus","tutup telepon","rechazar","colgar","refuser","raccrocher","ablehnen","auflegen","挂断","拒绝","거절"};
 static int role(CharSequence title){
  if(title==null)return UNKNOWN;
  String t=title.toString().trim().toLowerCase(Locale.ROOT);
  for(String s:YES)if(t.contains(s))return ANSWER;
  for(String s:NO)if(t.contains(s))return DECLINE;
  return UNKNOWN;
 }
}
