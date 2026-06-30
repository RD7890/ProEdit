<div align="center">

<img src="https://raw.githubusercontent.com/RD7890/ProEdit/main/assets/logo.png" width="120" height="120" alt="ProEdit Logo" />

# ProEdit

**Professional Photo Editor for Android**

[![Build Status](https://github.com/RD7890/ProEdit/actions/workflows/build-release.yml/badge.svg)](https://github.com/RD7890/ProEdit/actions/workflows/build-release.yml)
[![Release](https://img.shields.io/github/v/release/RD7890/ProEdit?label=Latest%20APK&color=1473E6)](https://github.com/RD7890/ProEdit/releases/latest)
[![Android](https://img.shields.io/badge/Android-8.0%2B-1473E6)](https://developer.android.com)
[![NDK](https://img.shields.io/badge/NDK-C%2B%2B17-31A8FF)](https://developer.android.com/ndk)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF)](https://kotlinlang.org)

*EDIT. ENHANCE. INSPIRE.*

</div>

---

## Download

<div align="center">

**[Latest APK Release](https://github.com/RD7890/ProEdit/releases/latest)**

</div>

---

## Features

### Masking System
| Tool | Description |
|------|-------------|
| **Manual Brush** | Soft-edged paint brush — add or erase selection |
| **Smart Brush** | Color-similarity BFS flood fill — edge-aware auto selection |
| **Radial Mask** | Circular gradient — center selected, edge fades |
| **Linear Mask** | Directional gradient from point A to B |
| **Color Range** | Select pixels by color similarity across the entire image |
| **Invert** | Flip selected/unselected regions |

### Tune Image (Adjustments)
All adjustments apply **only to the masked area** when a mask is active.

| Adjustment | Range | Effect |
|-----------|-------|--------|
| Brightness | -100 → +100 | Lift or drop exposure |
| Contrast | -100 → +100 | Expand or compress tonal range |
| Saturation | -100 → +100 | Push or pull color intensity |
| Highlights | -100 → +100 | Target bright regions |
| Shadows | -100 → +100 | Target dark regions |
| Temperature | -100 → +100 | Warm (orange) or cool (blue) shift |
| Sharpness | -100 → +100 | Unsharp mask enhancement |
| Ambiance | -100 → +100 | Local contrast microboost |

### Filters
Original · Vivid · Dramatic · B&W · Cinematic · Aqua · Matte · Golden

---

## Tech Stack

```
UI          — Kotlin + Jetpack Compose (Material 3)
Processing  — Android NDK C++17 (OpenGL-free, pure CPU pipeline)
Algorithms  — HSL color space, BFS flood fill, unsharp mask
Navigation  — Jetpack Navigation Compose
Build       — Gradle 8.9 + AGP 8.5 + CMake 3.22
CI/CD       — GitHub Actions (auto APK + Release on every push to main)
Min SDK     — Android 8.0 (API 26)
```

---

## Architecture

```
app/
├── cpp/
│   ├── image_processor.cpp   # JNI bridge — adjustments & filters
│   └── masking.cpp           # JNI bridge — all mask operations
└── kotlin/com/rohan/proedit/
    ├── processing/
    │   └── NativeProcessor.kt    # JNI declarations
    ├── viewmodel/
    │   └── EditorViewModel.kt    # State management + coroutines
    └── ui/
        ├── theme/                # Photoshop-style dark palette
        ├── screens/
        │   ├── HomeScreen.kt     # Gallery import
        │   ├── EditorScreen.kt   # Main editor (Tune + Filters)
        │   └── MaskScreen.kt     # Full masking interface
        └── components/
            ├── AdjustmentPanel.kt
            └── ImageCanvas.kt
```

---

## CI/CD Pipeline

Every push to `main` triggers an automated build:

```
Push to main
    ↓ GitHub Actions
    ↓ JDK 17 + NDK 26 + CMake 3.22
    ↓ gradle :app:assembleRelease
    ↓ Auto-sign APK
    ↓ GitHub Release created
    ↓ APK downloadable at /releases/latest
```

---

## Build Locally

```bash
git clone https://github.com/RD7890/ProEdit.git
cd ProEdit
./gradlew :app:assembleDebug
# APK at app/build/outputs/apk/debug/
```

Requires Android Studio with NDK 26 and CMake 3.22.

---

<div align="center">

Built with NDK C++ · Jetpack Compose · No external image libraries

</div>
