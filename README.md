# Moments for Android

Android client for Moments, built independently from the iOS app with Kotlin and Jetpack Compose.

## Current foundation

- Compose UI shell: home feed, search, create, activity and profile
- Material 3 theme using the Moments visual language
- Single source of truth for the bottom navigation destination
- Architecture packages ready for Firebase-backed data, messaging and feature modules

## Open in Android Studio

1. Open this `MomentsAndroid` folder.
2. Use JDK 17 or newer for Gradle.
3. Sync the project.
4. Add `app/google-services.json` only when Firebase is wired. It is deliberately gitignored.

The Firebase SDK is intentionally not initialized in this first scaffold: the Android app needs its own Android Firebase registration and configuration file before doing so.

