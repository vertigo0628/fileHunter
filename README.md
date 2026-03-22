# fileHunter (~By vertiGO!)

**fileHunter** is a powerful, minimalist Android File Manager specifically designed to uncover hidden media vaults and browse restricted application data without requiring root access.

## Key Features,

*   **Shizuku Powered:** Uses the Shizuku API to seamlessly bypass Android's Scoped Storage limitations, allowing you to browse restricted internal app folders like `/Android/data`.
*   **Deep Vault Scanner:** Instantly searches your entire device storage for folders containing `.nomedia` files, immediately aggregating all hidden vaults into a single, easy-to-navigate list.
*   **Media Hunter:** Automatically scans "disguised" cache files (like `.bin` or `.tmp` files) and reads their binary Hex headers. If it detects a hidden JPEG photo (`FF D8 FF`), it tags it with a picture icon!
*   **App Label Translation:** Automatically translates cryptic package names (e.g., `org.telegram.messenger`) into human-readable titles (e.g., **Telegram**) for zero-friction navigation.
*   **Direct File Opening:** Safely copies restricted files to a temporary cache and uses a `FileProvider` to open them instantly in your favorite Gallery or Video Player.
*   **Smart Navigation History:** Remembers your exact place in deep vault scans so you never lose your search results when pressing "Back".

## Under the Hood

Built entirely with **Kotlin** and **Jetpack Compose**. 

Notable technical implementations include:
*   A custom Reflection-based bridge to bypass Kotlin visibility restrictions in recent Shizuku API versions.
*   Coroutine-based asynchronous background scanning for non-blocking UI interactions.
*   A customized `FileProvider` configuration for secure external file sharing from protected directories.

## Prerequisites

1.  **Android 11+ Device** (Compatible down to API 31).
2.  **Shizuku Installed & Running**: The app requires Shizuku to be active in the background to access restricted data paths.

## Building and Running

1. Connect your Android device (ensure Developer Options and USB/Wireless Debugging are enabled).
2. Start the Shizuku service on your device.
3. Build and install via Gradle:
   ```bash
   ./gradlew clean assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. On first launch, tap **"Check Permission"** to grant fileHunter access to your Shizuku instance.
