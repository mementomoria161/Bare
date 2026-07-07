# Bare Browser

**Bare** is a minimalist, privacy-focused web browser for Android. It is designed to be simple, fast, and light on system resources, featuring a unified single-page layout that integrates tab overview and settings directly into the main interface.

---

## Features

- **Material You Dynamic Coloring**: The toolbar and system bars dynamically adapt to match the color palette of the webpage you are viewing.
- **Tab Overview**: A slide-up panel showing open tabs directly in the main screen with thumbnails.
- **Settings Overlay**: Fast access to search engines (Google, DuckDuckGo, Bing, Ecosia, or custom), desktop mode toggle, data clearing, and an auto-close tabs timer.
- **Privacy First**: Easy clearing of history, cache, and cookies. No tracking or unnecessary permissions.
- **Responsive Layout**: Designed for single-hand use with a bottom capsule bar.

---

## Tech Stack & Architecture

- **Language**: Kotlin
- **UI**: Android XML Layouts with Material Design 3 Components
- **Browser Core**: Android System WebView
- **State Persistence**: JSON-serialized tab lists and settings saved in SharedPreferences

---

## Build & Development

### Prerequisites
- Android Studio (Koala or newer recommended)
- Android SDK 26 (Android 8.0) or higher (Min SDK is 26)
- JDK 17 (embedded with Android Studio)

### How to Run
We recommend compiling and running the project directly within **Android Studio**:
1. Open Android Studio and choose **File -> Open**.
2. Select the repository root folder.
3. Once Gradle syncs, select `app` in the run configuration drop-down and click the green **Run** button to launch the app on an emulator or a connected physical device.