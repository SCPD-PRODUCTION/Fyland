package id.fyland;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Rings the island timer even if the app process was asleep when it ran out. */
public class ClockReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent intent){ClockKit.timerFired(c);}
}
