package id.fyland;

import android.content.Context;

/** Looks up UI text in the string resources so the app follows the system language. */
final class L {
 static Context ctx;
 private L(){}
 static String s(int id){
  try{return ctx.getString(id);}catch(Exception e){return "";}
 }
}
