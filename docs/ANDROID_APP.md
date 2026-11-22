# Android app

The native Android application is built with Kotlin and Jetpack Compose.

## Current scope

- Home dashboard and backend connection status
- Match scanner, intelligence, accumulator, performance and settings screens
- Secure HTTPS health-check configuration
- No fabricated fixtures, probabilities or selections
- No provider API keys embedded in the APK

## Build

GitHub Actions workflow: `.github/workflows/android-apk.yml`

A successful run uploads `goal-scanner-2.0-debug-apk` as a downloadable workflow artifact. This is a debug APK for installation/testing, not yet a signed public-release APK.

## Device compatibility

Minimum Android version: Android 7.0 (API 24). Target SDK: 35. The app is suitable for modern Android devices, including Android 16.

## Next implementation stages

1. Verify the APK build and install it on a physical device.
2. Deploy a secure backend and connect the app.
3. Add real fixture and odds feeds with source timestamps.
4. Integrate validated prediction models and historical performance.
5. Create a signed release build after testing.
