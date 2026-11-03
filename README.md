# Noir Clock

Premium dark AMOLED clock widget for Android. Kotlin, Jetpack Compose app + native AppWidget.
Digital and Analog styles, live preview, no internet permission.

## Build the APK

**GitHub Actions (no Android Studio needed)**
1. Create a repo, upload `NoirClock.zip` (or all these files) to `main`.
2. Make sure `.github/workflows/build.yml` exists.
3. Actions tab -> Build APK -> wait ~5 min -> download `app-debug.apk` from the repo's Releases page.

**Android Studio**
1. File -> Open -> this folder, let Gradle sync.
2. Build -> Build Bundle(s)/APK(s) -> Build APK(s).
   File: `app/build/outputs/apk/debug/app-debug.apk`

## Install and add the widget
1. Copy the APK to the phone, tap it, allow "install unknown apps" if asked.
2. Open **Noir Clock**, pick a style, press **Add widget to home screen**.
   (Or long-press home screen -> Widgets -> Noir Clock.)
3. Resize by long-pressing the widget. Tap it any time to reopen settings.

## How the widget stays live and cheap
- Digital: TextClock views, ticked by the launcher. No alarms, no services.
- Analog: bitmap redrawn once a minute by a single non-waking alarm, only while an analog widget exists.
- Android cannot redraw a widget every second, so the analog home-screen dial has no sweeping second hand (the in-app preview does).
