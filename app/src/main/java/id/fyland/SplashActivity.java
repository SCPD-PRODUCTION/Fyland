package id.fyland;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.VideoView;

/** Splash screen: memutar res/raw/splash.mp4 tanpa suara, lalu membuka MainActivity. */
public class SplashActivity extends Activity {
 static final int BG = 0xff031209;
 static final long MAX_MS = 9500; // pengaman bila video gagal selesai
 private boolean done;
 private final Handler handler = new Handler(Looper.getMainLooper());
 private VideoView video;

 @Override public void onCreate(Bundle b) {
  super.onCreate(b);
  Window w = getWindow();
  w.setStatusBarColor(BG);
  w.setNavigationBarColor(BG);
  w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  FrameLayout root = new FrameLayout(this);
  root.setBackgroundColor(BG);
  video = new VideoView(this);
  root.addView(video, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
  setContentView(root);
  try {
   video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
    @Override public void onPrepared(MediaPlayer mp) { mp.setVolume(0f, 0f); mp.setLooping(false); }
   });
   video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
    @Override public void onCompletion(MediaPlayer mp) { next(); }
   });
   video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
    @Override public boolean onError(MediaPlayer mp, int what, int extra) { ErrorLog.w(new RuntimeException("splash video error " + what + "/" + extra)); next(); return true; }
   });
   video.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.splash));
   video.start();
  } catch (Throwable t) {
   ErrorLog.w(t);
   next();
   return;
  }
  handler.postDelayed(new Runnable() { @Override public void run() { next(); } }, MAX_MS);
 }

 private void next() {
  if (done) return;
  done = true;
  handler.removeCallbacksAndMessages(null);
  try { video.stopPlayback(); } catch (Throwable t) { ErrorLog.w(t); }
  Intent i = new Intent(this, MainActivity.class);
  i.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
  startActivity(i);
  finish();
  overridePendingTransition(0, 0);
 }

 @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
}
