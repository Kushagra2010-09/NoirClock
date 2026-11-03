# Noir Clock

Premium dark AMOLED clock widget for Android. Kotlin, Jetpack Compose app + native AppWidget.
Digital and Analog styles, live preview, no internet permission.




## How the widget stays live and good
- Digital: TextClock views, ticked by the launcher. No alarms, no services.
- Analog: bitmap redrawn once a minute by a single non-waking alarm, only while an analog widget exists.
- Android cannot redraw a widget every second, so the analog home-screen dial has no sweeping second hand (the in-app preview does).
