package com.nemerpus.hondaassistbridge;
import android.app.Application;
public class HondaAssistApp extends Application {
    @Override public void onCreate(){super.onCreate();registerActivityLifecycleCallbacks(new AppVisibilityTracker());VehicleRepository.load(this);}
}
