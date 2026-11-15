package ru.labhistory.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

import ru.labhistory.app.LabHistoryApplication;
import ru.labhistory.app.R;
import ru.labhistory.app.security.AppLockManager;

public abstract class SecureActivity extends AppCompatActivity {
    protected static final int ALLOWED_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL;

    private View lockOverlay;
    private TextView lockStatus;
    private boolean automaticPromptVisible;

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        ViewGroup content = findViewById(android.R.id.content);
        lockOverlay = LayoutInflater.from(this).inflate(
                R.layout.view_app_lock,
                content,
                false
        );
        content.addView(lockOverlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        lockStatus = lockOverlay.findViewById(R.id.appLockStatus);
        Button unlockButton = lockOverlay.findViewById(R.id.unlockAppButton);
        unlockButton.setOnClickListener(view -> showAutomaticAuthentication());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lockOverlay != null) {
            lockOverlay.post(this::refreshAppLockProtection);
        }
    }

    @Override
    protected void onPause() {
        if (lockOverlay != null && getAppLockManager().isEnabled()) {
            lockOverlay.setVisibility(View.VISIBLE);
        }
        super.onPause();
    }

    protected final AppLockManager getAppLockManager() {
        return ((LabHistoryApplication) getApplication()).getAppLockManager();
    }

    protected final int deviceAuthenticationStatus() {
        return BiometricManager.from(this).canAuthenticate(ALLOWED_AUTHENTICATORS);
    }

    protected final void refreshAppLockProtection() {
        AppLockManager manager = getAppLockManager();
        if (!manager.isEnabled()) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            automaticPromptVisible = false;
            if (lockOverlay != null) {
                lockOverlay.setVisibility(View.GONE);
            }
            return;
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (manager.shouldAuthenticate()) {
            if (lockOverlay != null) {
                lockOverlay.setVisibility(View.VISIBLE);
                lockOverlay.bringToFront();
            }
            showAutomaticAuthentication();
        } else if (lockOverlay != null) {
            lockOverlay.setVisibility(View.GONE);
        }
    }

    protected final void requestDeviceAuthentication(
            String subtitle,
            Runnable onSuccess,
            AuthenticationErrorCallback onError
    ) {
        showAuthentication(subtitle, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(
                    BiometricPrompt.AuthenticationResult result
            ) {
                super.onAuthenticationSucceeded(result);
                onSuccess.run();
            }

            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                onError.onError(errString == null ? "Проверка отменена" : errString.toString());
            }
        });
    }

    private void showAutomaticAuthentication() {
        if (automaticPromptVisible || !getAppLockManager().shouldAuthenticate()) {
            return;
        }
        automaticPromptVisible = true;
        if (lockStatus != null) {
            lockStatus.setText(R.string.app_locked_message);
        }
        showAuthentication(
                getString(R.string.app_unlock_subtitle),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            BiometricPrompt.AuthenticationResult result
                    ) {
                        super.onAuthenticationSucceeded(result);
                        automaticPromptVisible = false;
                        getAppLockManager().unlock();
                        refreshAppLockProtection();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        if (lockStatus != null) {
                            lockStatus.setText(R.string.app_unlock_failed);
                        }
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        automaticPromptVisible = false;
                        if (lockStatus != null && errString != null) {
                            lockStatus.setText(errString);
                        }
                    }
                }
        );
    }

    private void showAuthentication(
            String subtitle,
            BiometricPrompt.AuthenticationCallback callback
    ) {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt prompt = new BiometricPrompt(this, executor, callback);
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.app_authentication_title))
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                .build();
        prompt.authenticate(promptInfo);
    }

    protected interface AuthenticationErrorCallback {
        void onError(String message);
    }
}
