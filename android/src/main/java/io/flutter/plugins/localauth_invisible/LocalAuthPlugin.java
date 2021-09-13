package io.flutter.plugins.localauth_invisible;// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.


import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.hardware.fingerprint.FingerprintManagerCompat;
import androidx.fragment.app.FragmentActivity;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugins.localauth_invisible.AuthenticationHelper.AuthCompletionHandler;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** LocalAuthPlugin */
public class LocalAuthPlugin implements MethodCallHandler {
  private final Registrar registrar;
  private final AtomicBoolean authInProgress = new AtomicBoolean(false);
  private AuthenticationHelper authenticationHelper;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Runnable delayRunnable;

  /** Plugin registration. */
  public static void registerWith(Registrar registrar) {
    final MethodChannel channel =
        new MethodChannel(registrar.messenger(), "plugins.flutter.io/local_auth_invisible");
    channel.setMethodCallHandler(new LocalAuthPlugin(registrar));
  }

  private LocalAuthPlugin(Registrar registrar) {
    this.registrar = registrar;
  }

  private void logDebug(Object value) {
    Log.d("LocalAuthPlugin", value.toString());
  }

  void stopIfNotStopped(Result result) {
    if (authInProgress.compareAndSet(true, false)) {
      logDebug("LOCAL AUTH STOPPED");
      result.success(false);
    }
  }

  @Override
  public void onMethodCall(MethodCall call, final Result result) {
    Activity activity = registrar.activity();
    if (activity == null || activity.isFinishing()) {
      result.error("no_activity", "local_auth plugin requires a foreground activity", null);
      return;
    }
    if (call.method.equals("authenticateWithBiometrics")) {
      if (!authInProgress.compareAndSet(false, true)) {
        stopCurrentAuthentication();
      }
      int maxTimeoutMillis = call.argument("maxTimeoutMillis");
      
      if (delayRunnable != null) {
        handler.removeCallbacks(delayRunnable);
        delayRunnable = null;
      }

      delayRunnable = new Runnable() {
        @Override
        public void run() {
          stopIfNotStopped(result);
        }
      };

      handler.postDelayed(delayRunnable, maxTimeoutMillis);


      authenticationHelper =
          new AuthenticationHelper(
              activity,
              call,
              new AuthCompletionHandler() {
                @Override
                public void onSuccess() {
                  if (authInProgress.compareAndSet(true, false)) {
                    result.success(true);
                  }
                }

                @Override
                public void onFailure() {
                  if (authInProgress.compareAndSet(true, false)) {
                    result.success(false);
                  }
                }

                @Override
                public void onError(String code, String error) {
                  if (authInProgress.compareAndSet(true, false)) {
                    result.error(code, error, null);
                  }
                }
              });
      authenticationHelper.authenticate();
    } else if (call.method.equals("getAvailableBiometrics")) {
      try {
        getAvailableBiometrics(result, (FragmentActivity)activity);
        result.success(biometrics);
      } catch (Exception e) {
        result.error("no_biometrics_available", e.getMessage(), null);
      }

    } else if (call.method.equals(("stopAuthentication"))) {

      stopAuthentication(result);
    } else {
      result.notImplemented();
    }
  }
  private void getAvailableBiometrics(final Result result,FragmentActivity activity) {
    try {
      if (activity == null || activity.isFinishing()) {
        result.error("no_activity", "local_auth plugin requires a foreground activity", null);
        return;
      }
      ArrayList<String> biometrics = getAvailableBiometrics(activity);
      result.success(biometrics);
    } catch (Exception e) {
      result.error("no_biometrics_available", e.getMessage(), null);
    }
  }

  private ArrayList<String> getAvailableBiometrics(FragmentActivity activity) {
    ArrayList<String> biometrics = new ArrayList<>();
    if (activity == null || activity.isFinishing()) {
      return biometrics;
    }
    PackageManager packageManager = activity.getPackageManager();
    if (Build.VERSION.SDK_INT >= 23) {
      if (packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
        biometrics.add("fingerprint");
      }
    }
    if (Build.VERSION.SDK_INT >= 29) {
      if (packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)) {
        biometrics.add("face");
      }
      if (packageManager.hasSystemFeature(PackageManager.FEATURE_IRIS)) {
        biometrics.add("iris");
      }
    }

    return biometrics;
  }

  private void stopCurrentAuthentication() {
    if (authenticationHelper != null && authInProgress.get()) {
      authenticationHelper.stopAuthentication();
      authenticationHelper = null;
    }
  }

  /*
   Stops the authentication if in progress.
  */
  private void stopAuthentication(Result result) {
    logDebug("Stop authentication called");
    try {
      if (authenticationHelper != null && authInProgress.get()) {
        stopCurrentAuthentication();
        result.success(true);
        return;
      }
      result.success(false);
    } catch (Exception e) {
      result.success(false);
    }
  }
}
