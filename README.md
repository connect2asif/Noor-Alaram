# TILAWAT ALARM — Android Studio Project

A private, offline Islamic-themed audio scheduler and MP3 playlist alarm for Android 8.0+.

## Features

- A polished navy / emerald / gold interface with an adaptive crescent-and-alarm icon.
- Create as many **daily** or **specific-date** listening schedules as you like.
- Pick **one MP3**, **multiple audio files**, or an **entire folder** for each schedule.
- When using a folder, the app scans its audio files (including subfolders up to five levels) when playback starts. Newly added MP3s are picked up automatically.
- Play files in order or enable **Shuffle playlist**.
- Start automatically at the scheduled time, play one track after the other, and stop when the list ends.
- Preview a saved schedule with **Test play**. Stop / skip audio in the notification or stop using the top-bar Stop button.
- Reschedule daily alarms automatically and restore remaining schedules after reboot, system time change, time-zone change, and app update.
- Uses Android's Storage Access Framework; no broad storage permission or cloud account is needed.

## Build your APK on Windows — step by step

1. Unzip `TilawatAlarm-AndroidStudio.zip` to a normal folder, for example `C:\AndroidProjects\TilawatAlarm`.
2. Install [Android Studio](https://developer.android.com/studio) if needed. The app uses **JDK 17**, **Android SDK Platform 35**, Android Gradle Plugin **8.7.3**, and Gradle **8.9**. Android Studio can install missing Android SDK components.
3. **Double-click `SETUP_FIRST_WINDOWS.bat` once.** This retrieves the **official** Gradle wrapper JAR from the Gradle GitHub repository and verifies its published SHA-256 checksum. An internet connection is needed for this step.
4. Open Android Studio. Choose **Open** and select the `TilawatAlarm` folder (the folder containing `settings.gradle.kts`). Allow **Gradle Sync** to complete. The first sync will download Gradle and Android library dependencies.
5. Choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**. In some Android Studio releases this is **Build > Generate App Bundles or APKs > Build APKs**.
6. Android Studio will place the installable **debug APK** here:
   `TilawatAlarm\app\build\outputs\apk\debug\app-debug.apk`
7. Transfer the APK to your Android phone and install it. You may need to allow **Install unknown apps** for your file manager.
8. Alternatively, connect your phone with a USB cable, enable **USB debugging**, and press **Run** in Android Studio to install the app directly.

You can also run `BUILD_DEBUG_APK_WINDOWS.bat` after the first setup and the Android SDK are installed.

### If setup cannot download Gradle wrapper

Check that your computer can access GitHub, and rerun `SETUP_FIRST_WINDOWS.bat`. You may also download the official wrapper JAR from:

`https://github.com/gradle/gradle/blob/v8.9.0/gradle/wrapper/gradle-wrapper.jar`

Save it as `gradle/wrapper/gradle-wrapper.jar`. Published Gradle 8.9 JAR SHA-256:

`498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17`

The project's Gradle distribution file is separately SHA-256 verified at first download.

## Set up your first daily audio schedule

1. Open **Tilawat Alarm**.
2. Allow **Alarms & reminders** / **precise alarms** when prompted in the yellow permission card.
3. Allow **Notifications** so playback controls appear while audio plays.
4. Tap **Create a listening schedule**.
5. Enter a title, such as `Morning Surah`, and choose `6:30 AM`.
6. Choose **Every day** (or **Specific date** for a one-time play).
7. Tap **Pick MP3s** for one or more tracks, or **Pick folder** for a folder of recordings.
8. Tap **Save schedule**. Repeat to create another schedule such as `Evening Adhkar` at `8:30 PM`.
9. Press **Test play** on a schedule to check audio output. Use the **Stop** button in the top bar or notification to finish a test.

## Important Android behavior

- On **Android 12+**, exact alarm access is important. The app requests `SCHEDULE_EXACT_ALARM`; if it is denied, it schedules a *best-effort inexact alarm* which **can be delayed**.
- Audio is played with Android's **alarm audio usage**, so check the phone's **alarm volume**, Do Not Disturb preferences, and connected Bluetooth devices.
- Some manufacturers (Samsung, Xiaomi, Oppo, Vivo, etc.) aggressively restrict battery usage. If alarms are delayed or blocked, open Android Settings > Apps > Tilawat Alarm > Battery and choose **Unrestricted** where available. OEM settings differ.
- Do **not** move or delete the MP3s after selection. Avoid revoking the app's folder / file permissions.
- Android may impose additional playback limitations, especially after **force stop** or with restrictive device power settings. If the app is force-stopped through Settings, reopen it to re-enable normal scheduling.
- Exact timing and playback cannot be guaranteed for every handset and OS combination. Test a schedule a few minutes ahead on your own device before relying on it.
- Source files are not uploaded. The project does **not** include any commercial music or Qur'an recitation MP3s. Select audio you have the right to use.

## Developer notes

- Package: `com.nooraudio.alarm` (retained so the renamed app can update a previous install without losing scheduled audio)
- UI: **Kotlin + Jetpack Compose + Material 3**.
- Playback: Android `MediaPlayer` in a foreground `mediaPlayback` service, alarm audio focus, partial wake mode.
- Scheduling: `AlarmManager.setExactAndAllowWhileIdle` with inexact fallback.
- Persistence: private `SharedPreferences` storing JSON schedule metadata, SAF content URIs with persisted read grants.
- There is no account system, no advertising, and no internet permission.
- The launcher icon is a **vector adaptive icon** in `app/src/main/res/drawable/`.
- The app is a personal sideloadable project; publication on Google Play may need further Play policy and target-SDK review.

## Code overview

- `MainActivity.kt`: app interface, permission flow, file and folder selection, schedule editor.
- `AlarmStore.kt`: saved schedules.
- `AlarmScheduler.kt`: alarms, one-time or daily recurrence, reboot restoration.
- `AudioAlarmService.kt`: foreground MP3 playback and Stop / Next notification controls.
- `AndroidManifest.xml`: required permissions and component definitions.

**Note:** The project files are supplied as source. A compiled APK is **not** included, and the project has not been built or tested on an actual phone in the creation environment. Build it using the instructions above.
