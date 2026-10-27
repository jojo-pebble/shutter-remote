# Shutter Remote

Press the shutter of your phone's built-in camera from a Pebble watch.

## Download

- Phone: download `app-debug.apk` from the latest [Release](../../releases/latest) and install it (Android 8 or newer).
- Watch: install **Shutter Remote** from the Pebble Appstore, or use `watch/shutter-remote.pbw` from this repository.

- Phone: the Android app in this folder (an accessibility service that presses the camera's shutter button).
- Watch: the Pebble app in `watch/` (ready file: `watch/shutter-remote.pbw`).

## How to use

1. On the phone open Shutter Remote and follow the three steps: turn on the service, press your camera's shutter once so the app remembers it, and take a test photo.
2. On the watch open Shutter Remote. Up: switch between Instant and 3 s timer. Middle: take a photo (in video mode: start/stop).
3. The watch vibrates briefly when the shutter was pressed; a double vibration means it did not work (camera not on screen, service off, or button not set up).

The phone screen must be on and unlocked. The app only presses the button in the camera app it has remembered.

## Install the watch app (Mac, Terminal)

```
pebble install --cloudpebble ~/Documents/Pebble/pebble-shutter/watch/shutter-remote.pbw
```

To rebuild the watch app after changes, run `pebble build` inside `watch/`.

## Build the phone app (Android Studio)

1. File > Open > the folder `~/Documents/Pebble/pebble-shutter` (the one with `settings.gradle.kts`).
2. Wait for "Gradle sync" to finish.
3. To install on your phone over Wi-Fi: pick the phone at the top and press the green Run button.
4. To make a shareable file: Build > Build APK(s); the file appears in `app/build/outputs/apk/debug/`.

Versions: Android Gradle Plugin 9.4.1, Gradle 9.6.0, Kotlin built into the plugin. The only third-party library is PebbleKit 2 (io.rebble.pebblekit2:client).
