// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

import 'dart:async';

import 'package:flutter/services.dart';
import 'package:meta/meta.dart';
import 'package:platform/platform.dart';

import 'auth_strings.dart';
import 'error_codes.dart';

enum BiometricType { face, fingerprint, iris }

const MethodChannel _channel = MethodChannel('plugins.flutter.io/local_auth_invisible');

Platform _platform = const LocalPlatform();

@visibleForTesting
void setMockPathProviderPlatform(Platform platform) {
  _platform = platform;
}

/// A Flutter plugin for authenticating the user identity locally.
class LocalAuthentication {
  /// Authenticates the user with biometrics available on the device.
  ///
  /// Returns a [Future] holding true, if the user successfully authenticated,
  /// false otherwise.
  ///
  /// [localizedReason] is the message to show to user while prompting them
  /// for authentication. This is typically along the lines of: 'Please scan
  /// your finger to access MyApp.'
  ///
  /// [useErrorDialogs] = true means the system will attempt to handle user
  /// fixable issues encountered while authenticating. For instance, if
  /// fingerprint reader exists on the phone but there's no fingerprint
  /// registered, the plugin will attempt to take the user to settings to add
  /// one. Anything that is not user fixable, such as no biometric sensor on
  /// device, will be returned as a [PlatformException].
  ///
  /// [stickyAuth] is used when the application goes into background for any
  /// reason while the authentication is in progress. Due to security reasons,
  /// the authentication has to be stopped at that time. If stickyAuth is set
  /// to true, authentication resumes when the app is resumed. If it is set to
  /// false (default), then as soon as app is paused a failure message is sent
  /// back to Dart and it is up to the client app to restart authentication or
  /// do something else.
  ///
  /// Construct [AndroidAuthStrings] and [IOSAuthStrings] if you want to
  /// customize messages in the dialogs.
  ///
  /// Setting [sensitiveTransaction] to true enables platform specific
  /// precautions. For instance, on face unlock, Android opens a confirmation
  /// dialog after the face is recognized to make sure the user meant to unlock
  /// their phone.
  ///
  /// Throws an [PlatformException] if there were technical problems with local
  /// authentication (e.g. lack of relevant hardware). This might throw
  /// [PlatformException] with error code [otherOperatingSystem] on the iOS
  /// simulator.
  /// [maxTimeoutMillis] max milliseconds the authenticator will wait
  /// is the timeout is elapsed without any user actions, the authenticator
  /// will be canceled with failure
  /// minimum value is 3000 milliseconds, maximum is 30000
  /// the default value is 7000 which means local auth will be canceled 
  /// after 7 seconds of inactivity
  Future<bool> authenticateWithBiometrics({
    required String localizedReason,
    bool useErrorDialogs = true,
    bool stickyAuth = false,
    AndroidAuthMessages androidAuthStrings = const AndroidAuthMessages(),
    IOSAuthMessages iOSAuthStrings = const IOSAuthMessages(),
    bool sensitiveTransaction = true,
    int maxTimeoutMillis = 7000,
  }) async {
    final Map<String, Object> args = <String, Object>{
      'localizedReason': localizedReason,
      'stickyAuth': stickyAuth,
      'sensitiveTransaction': sensitiveTransaction,
      "useErrorDialogs":useErrorDialogs
    };
    if (_platform.isIOS) {
      args.addAll(iOSAuthStrings.args);
    } else if (_platform.isAndroid) {
      args.addAll(androidAuthStrings.args);
    } else {
      throw PlatformException(
          code: otherOperatingSystem,
          message: 'Local authentication does not support non-Android/iOS '
              'operating systems.',
          details: 'Your operating system is ${_platform.operatingSystem}');
    }
    maxTimeoutMillis = maxTimeoutMillis.clamp(3000, 30000);
    args.addAll({
      'maxTimeoutMillis': maxTimeoutMillis,
    });
    return await _channel.invokeMethod<bool?>('authenticateWithBiometrics', args) ?? false;
  }

  /// Returns true if device is capable of checking biometrics
  ///
  /// Returns a [Future] bool true or false:
  Future<bool> get canCheckBiometrics async =>
      (await _channel.invokeListMethod<String?>('getAvailableBiometrics'))?.isNotEmpty == true;

  /// Returns true if auth was cancelled successfully.
  /// This api only works for Android.
  /// Returns false if there was some error or no auth in progress.
  ///
  /// Returns [Future] bool true or false:
  Future<bool> stopAuthentication() async {
    if (_platform.isAndroid) {
      return await _channel.invokeMethod<bool>('stopAuthentication') ?? false;
    }
    return Future<bool>.sync(() => true);
  }

  /// Returns a list of enrolled biometrics
  ///
  /// Returns a [Future] List<BiometricType> with the following possibilities:
  /// - BiometricType.face
  /// - BiometricType.fingerprint
  /// - BiometricType.iris (not yet implemented)
  Future<List<BiometricType>> getAvailableBiometrics() async {
    final List<String>? result = (await _channel.invokeListMethod<String>(
      'getAvailableBiometrics',
    ));
    final List<BiometricType> biometrics = <BiometricType>[];
    result?.forEach((String value) {
      switch (value) {
        case 'face':
          biometrics.add(BiometricType.face);
          break;
        case 'fingerprint':
          biometrics.add(BiometricType.fingerprint);
          break;
        case 'iris':
          biometrics.add(BiometricType.iris);
          break;
        case 'undefined':
          break;
      }
    });
    return biometrics;
  }
}
