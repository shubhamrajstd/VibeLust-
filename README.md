# Vibe Lust Wallpapers

A premium, modern Android application built using **Jetpack Compose**, **Kotlin**, **Kotlin Coroutines / Flow**, and **Room Database**. It functions as a complete live video wallpaper engine, allowing users to configure elegant looping video backgrounds, trim locally uploaded video assets, pre-load high-quality cosmic cloud assets, and enjoy smooth high-performance system rendering via a background Wallpaper Service.

---

## 🚀 Key Features

* **Live Video Wallpaper Renderer**: Custom native `VideoWallpaperService` managing surface state, canvas aspect scaling, and looping media playback.
* **Modern M3 UI Design**: Visually stunning dashboard utilizing deep, ambient color palettes, negative space, and modern Material Design 3 elements.
* **Trimming & Uploads**: Core logic for trimming local video clips to precise ranges and applying them locally.
* **Room Database Integration**: Fast local SQLite storage schema cache listing wallpaper details, author metadata, and storage mapping.
* **Pre-Loaded Premium Streams**: Built-in intelligent internet cloud buffering with fallback pipelines ensuring robust pre-loaded loops out of the box.
* **Google and Custom Email Authentication**: Ready-to-go Google Sign-In with robust local mock shortcuts for developers and direct secure email input sign-in.

---

## 🛠️ Prerequisites

* **Java Development Kit (JDK)**: JDK 17 is required.
* **Android SDK**: Compile SDK level 34.
* **Gradle Build System**: Pre-installed wrapper configuration is optimized.

---

## 📦 How to Build Locally

Use the pre-configured **Gradle Wrapper** files (`gradlew` and `gradlew.bat`) included in this repository to build without needing a manual Gradle installation.

### 1. Build Debug APK
```bash
./gradlew assembleDebug
```
The output debug APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

### 2. Build Release Bundle (AAB) & APK
```bash
./gradlew bundleRelease assembleRelease
```
The outputs will be located at:
* **Release APK**: `app/build/outputs/apk/release/app-release.apk`
* **Release AAB (Play Store Bundle)**: `app/build/outputs/bundle/release/app-release.aab`

---

## 🔐 Code Signing Configuration (APK & AAB)

The project's `/app/build.gradle.kts` file is already pre-configured to detect and apply a secure release signing configuration automatically if the expected keystore file is present:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("vibelust.jks")
        storePassword = "SHUBHAM@2008"
        keyAlias = "vibelust"
        keyPassword = "SHUBHAM@2008"
    }
}
```

### Option A: Standard Direct Signing (Easiest)
Simply place your existing keystore file **`vibelust.jks`** inside the **`app/`** directory.
The Gradle build checks for this file. If it exists (`file("vibelust.jks").exists()`), it automatically applies the credentials during Release compiles so you receive a fully-signed final output immediately!

To generate a new keystore matching the default project configuration, run this terminal command:
```bash
keytool -genkeypair -v -keystore app/vibelust.jks -alias vibelust -keyalg RSA -keysize 2048 -validity 10000 -storepass SHUBHAM@2008 -keypass SHUBHAM@2008
```

### Option B: Securing Passwords with Environment Variables (Encouraged for Public Repos)
To avoid hardcoding secrets in your public git commits:
1. Replace the hardcoded strings in `/app/build.gradle.kts` with `System.getenv("KEYSTORE_PASSWORD")` or similar variables.
2. Provide them at runtime when building locally.

---

## 🤖 Continuous Integration on GitHub (CI)

We have preloaded an automated GitHub Actions workflow in **`.github/workflows/build.yml`**.

As soon as you push this codebase to GitHub:
1. GitHub will automatically checkout the repository.
2. It will boot a virtual Ubuntu compiler with JDK 17 and configure a Gradle cache directory.
3. It will assemble the debug building configurations along with standard production release APKs/AABs.
4. **Downloads/Artifacts**: The final compiled files (`app-debug.apk`, `app-release.apk`, `app-release.aab`) will be packaged and available immediately for download directly from the action runs panel of your GitHub repository!
