package com.nemerpus.hondaassistbridge;
import android.app.*;
import android.os.Bundle;

public final class AppVisibilityTracker implements Application.ActivityLifecycleCallbacks {
    private static volatile int started=0;
    static boolean isForeground(){return started>0;}
    @Override public void onActivityStarted(Activity a){started++;}
    @Override public void onActivityStopped(Activity a){started=Math.max(0,started-1);}
    public void onActivityCreated(Activity a,Bundle b){} public void onActivityResumed(Activity a){}
    public void onActivityPaused(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle b){}
    public void onActivityDestroyed(Activity a){}
}
