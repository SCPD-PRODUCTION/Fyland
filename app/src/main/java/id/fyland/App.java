package id.fyland;

import android.app.Application;

/** Installs the app-wide error log before any activity, service or receiver runs. */
public class App extends Application {
 @Override public void onCreate(){super.onCreate();L.ctx=this;ErrorLog.init(this);}
}
