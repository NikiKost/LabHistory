package ru.labhistory.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import ru.labhistory.app.security.AppLockManager;

public final class LabHistoryApplication extends Application
        implements Application.ActivityLifecycleCallbacks {
    private static final long BACKGROUND_LOCK_DELAY_MS = 500L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AppLockManager appLockManager;
    private final Runnable delayedLock = () -> appLockManager.lock();
    private int startedActivities;

    @Override
    public void onCreate() {
        super.onCreate();
        appLockManager = new AppLockManager(this);
        registerActivityLifecycleCallbacks(this);
    }

    public AppLockManager getAppLockManager() {
        return appLockManager;
    }

    @Override
    public void onActivityStarted(Activity activity) {
        mainHandler.removeCallbacks(delayedLock);
        startedActivities++;
    }

    @Override
    public void onActivityStopped(Activity activity) {
        startedActivities = Math.max(0, startedActivities - 1);
        if (startedActivities == 0 && !activity.isChangingConfigurations()) {
            mainHandler.postDelayed(delayedLock, BACKGROUND_LOCK_DELAY_MS);
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityResumed(Activity activity) {
    }

    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
