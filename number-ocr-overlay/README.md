# Number OCR Overlay

A native Kotlin Android application that places a movable and resizable selector over a playing video, samples the selected screen region once per second, enhances faint digits, recognizes numeric codes with Google ML Kit, suppresses consecutive duplicates, and exports the chronological history as plain text.

## Architecture

| Component | Responsibility |
|---|---|
| `MainActivity` | Explains permissions, opens overlay settings, requests MediaProjection consent, and launches the service. |
| `ScreenCaptureService` | Owns the foreground MediaProjection session, ImageReader, one-second sampling loop, preprocessing, and OCR. |
| `OverlayController` | Owns the application overlay selector and floating controls. The selector supports rectangle, square, and circle display modes and exposes its bounding rectangle to the capture service. |
| `DetectionRepository` | Keeps the current session history and ignores a code when it is identical to the immediately preceding detected code. |
| `ResultsActivity` | Displays timestamped detections and provides copy, clear, and Storage Access Framework TXT export. |

The project intentionally uses a low-frequency sampling loop instead of an ImageReader listener. This avoids processing every display frame and reduces CPU and battery use. Only the newest available image is acquired, so stale frames do not accumulate.

## Project structure

```text
NumberOcrOverlay/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/numberocr/
│       │   ├── MainActivity.kt
│       │   ├── capture/ScreenCaptureService.kt
│       │   ├── overlay/OverlayController.kt
│       │   └── results/
│       │       ├── DetectionRepository.kt
│       │       └── ResultsActivity.kt
│       └── res/
│           ├── layout/activity_main.xml
│           ├── layout/activity_results.xml
│           └── values/resources.xml
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```

## Setup

Open the folder in Android Studio. Use a JDK 17 installation, let Android Studio sync Gradle, and install Android SDK Platform 35 and Build Tools through the SDK Manager. Connect an Android 8.0/API 26 or newer device or start an emulator, then press **Run**.

The supplied sandbox does not include an Android SDK or Gradle executable, so compilation could not be executed in this environment. The source tree and Gradle configuration are ready to be opened and built in Android Studio.

## Runtime workflow

First tap **Start Floating Mode**. If overlay permission has not been granted, Android opens the system overlay settings. Return to the app and tap the button again. Android then shows the MediaProjection screen-recording consent dialog. Accept it. The app starts a foreground service, displays the selector, and creates a full-display virtual display whose frames are cropped in memory to the selector bounds.

Move the selector by dragging inside it. Resize it from the bottom-right handle. Use the shape button to cycle between **Rectangle**, **Square**, and **Circle**. The circle and square are visual modes; OCR uses the corresponding bounding rectangle, which is the safest behavior for ML Kit because it receives a rectangular bitmap.

Tap **Record / Start OCR** when the video is ready. The service samples once per second. It converts the crop to ARGB, applies grayscale-like contrast enhancement using a color matrix, and sends the processed bitmap to ML Kit Text Recognition. The result is filtered with `\\d{2,}`, so one-digit fragments are ignored by default. Change this regular expression if one-digit values are meaningful in the target video.

Tap **Stop** to terminate the foreground service and remove the overlay. The in-memory history remains available from **View Results** until the app process is killed or the user taps **Clear**. Each entry contains the local device time and the normalized numeric code. **Copy All** places the formatted history on the clipboard. **Export TXT** opens Android's document picker, allowing the user to choose the destination without requiring broad storage permissions.

## Permissions and Android behavior

The manifest declares `SYSTEM_ALERT_WINDOW` for `TYPE_APPLICATION_OVERLAY`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`, and `POST_NOTIFICATIONS`. Overlay permission is granted by the user through system settings; MediaProjection consent is requested at runtime and must be renewed when Android requires it. For apps targeting Android 14 or newer, the foreground service type must be declared and supplied at runtime for media projection.[1]

The foreground notification is intentionally low importance and ongoing. On Android 13 and newer, notification permission may be requested separately if the app is expanded to include an explicit notification-permission flow. The current app still declares the permission and remains usable on devices where the user has allowed notifications through system settings.

## Important implementation notes

Screen coordinates and capture coordinates normally align when the virtual display uses the physical display metrics. On devices with unusual display cutouts, desktop modes, or aggressive manufacturer window scaling, test the crop alignment and adjust for the device's insets if required.

The preprocessing step increases contrast but does not guarantee recognition of every watermark. For difficult sources, add a user-selectable threshold, invert mode, or a small upscale operation before `InputImage.fromBitmap`. ML Kit works best when digits are large, sharp, and tightly cropped.[2]

The default duplicate policy suppresses only consecutive repeats. For example, `874, 874, 912, 874` produces three entries because the final `874` is newly appeared after another code. This matches real-time “newly appeared” behavior while allowing a code to reappear later.

The repository is in memory for simplicity. For durable history across process restarts, replace it with a Room database or a small file-backed store. If the app is intended for Google Play distribution, review foreground-service and screen-capture policy requirements and provide clear disclosure to users.

## Recommended production hardening

Before shipping, add an explicit **permission status** indicator, a **Pause** action, an adjustable sampling interval, a configurable minimum digit length, a user-facing threshold slider, lifecycle handling when MediaProjection is revoked, and a persistent database. Also add instrumentation tests for duplicate suppression and device tests across portrait, landscape, Android 13, Android 14, and Android 15.

## References

[1]: https://developer.android.com/about/versions/14/changes/fgs-types-required "Android Developers: Foreground service types are required"
[2]: https://developers.google.com/ml-kit/vision/text-recognition/v2/android "Google ML Kit: Recognize text in images with ML Kit on Android"
