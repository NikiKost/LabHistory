package ru.labhistory.app.security;

import android.content.Context;
import android.content.SharedPreferences;

public final class AppLockManager {
    private static final String PREFERENCES = "security_settings";
    private static final String KEY_ENABLED = "app_lock_enabled";

    private final SharedPreferences preferences;
    private volatile boolean locked;

    public AppLockManager(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        locked = isEnabled();
    }

    public boolean isEnabled() {
        return preferences.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply();
        locked = false;
    }

    public void lock() {
        if (isEnabled()) {
            locked = true;
        }
    }

    public void unlock() {
        locked = false;
    }

    public boolean shouldAuthenticate() {
        return isEnabled() && locked;
    }
}
