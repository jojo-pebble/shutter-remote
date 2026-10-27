<p align="center"><img src="store/icon-large-144.png" width="144" alt="Shutter Remote"></p>

# Shutter Remote

Take photos with your phone's built-in camera by pressing a button on your Pebble Time 2.

## Get it

**1. Phone app (Android 8 or newer)**

[**Download the APK**](https://github.com/jojo-pebble/shutter-remote/releases/download/v1.1/app-debug.apk)

1. Open the downloaded file. If Android asks, allow installing apps from your browser (Settings > Install unknown apps).
2. Open **Shutter Remote** and follow the three short steps on the screen: turn on the service, press your camera's shutter button once so the app remembers it, and take a test photo.
3. Keep the phone screen on and unlocked while you use the watch.

**2. Watch app**

Install **Shutter Remote** from the Pebble Appstore (category Remotes), or open `watch/shutter-remote.pbw` from this repository with the Pebble app.

## How to use

1. On the phone open Shutter Remote and follow the three steps: turn on the service, press your camera's shutter once so the app remembers it, and take a test photo.
2. On the watch open Shutter Remote. Up: switch between Instant and 3 s timer. Middle: take a photo (in video mode: start/stop).
3. The watch vibrates briefly when the shutter was pressed; a double vibration means it did not work (camera not on screen, service off, or button not set up).

The phone screen must be on and unlocked. The app only presses the button in the camera app it has remembered.

## For developers

### Install the watch app from Terminal

```
pebble install --cloudpebble watch/shutter-remote.pbw
```

To rebuild the watch app after changes, run `pebble build` inside `watch/`.

### Build the phone app (Android Studio)

1. File > Open > the project folder (the one with `settings.gradle.kts`).
2. Wait for "Gradle sync" to finish.
3. To install on your phone over Wi-Fi: pick the phone at the top and press the green Run button.
4. To make a shareable file: Build > Build APK(s); the file appears in `app/build/outputs/apk/debug/`.

Versions: Android Gradle Plugin 9.4.1, Gradle 9.6.0, Kotlin built into the plugin. The only third-party library is PebbleKit 2 (io.rebble.pebblekit2:client).
