# Bare

**Bare** is a premium, lightweight, privacy-focused Android web browser designed with modern aesthetics and fluid micro-animations. It implements a clean, unified single-page layout that seamlessly blends search, settings, and multi-tab management into an edge-to-edge Material Design 3 user experience.

---

## Key Features

### 🌟 Premium Modern Aesthetics (Material Design 3)
- **Dynamic Accent Coloring**: The browser dynamically samples the top-left pixel color of the loaded webpage to tint the status bar, bottom bar layout, progress indicator, and active selectors, adapting automatically to the site's branding.
- **Glassmorphism & Dims**: The background overlays and pop-up stack menus use beautiful glassmorphism-inspired dark dims (`#B3000000`) for a premium visual depth.
- **Unified URL Pill**: Address bar and actions are enclosed inside a single card container with matching heights, unified paddings, and zero gaps between icon buttons.

### 🗂️ Staggered Tab Overview Panel
- **Integrated Switcher**: Tapping the Tab Count button displays open tabs directly on the page, avoiding a separate layout screen.
- **Overshoot Stagger Animations**: Tabs pop in sequence from bottom to top using a boundary-constrained springy overshoot physics animation.
- **Bottom Fade-Mask Overlay**: A custom transparent-to-dimmed gradient view masks scrolling tab cards, making them fade and disappear behind the button area continuously without reaching screen edges.
- **Website Preview Persistence**: Webpage snapshot thumbnails are captured in low-resolution (20% scale) and cached directly to internal disk storage (`cacheDir`) to show tab previews instantly even after restarting the app.
- **Smart Empty States**: If the last tab is closed, a blank tab is automatically opened so the browser is always ready to use.

### ⚙️ Minimalist Settings Menu
- **Dynamic Search Engine Cycling**: Cycle between Google, DuckDuckGo, Bing, Ecosia, or custom search engine endpoints with dynamically loaded brand logos.
- **Desktop Mode Switcher**: Easily request desktop site layouts, dynamically highlighted via dynamic primary container states.
- **Clear Private Data**: Clear web cache, history, and cookies instantly.
- **Auto-Close Tabs Timer**: Prevent tab clutter by scheduling inactive background tabs to auto-close after **1 Day**, **1 Week**, or **1 Month** (or **Never**).

### 🚀 Gesture & Interaction Polish
- **Prioritized Swipe-to-Show**: Swiping down on a scrollable or non-scrollable page prioritizes sliding in the bottom URL bar first. Pull-to-refresh eligibility is evaluated only after the bar is fully shown.
- **Edge-to-Edge Navigation**: Tab list cards stretch edge-to-edge behind status and navigation bars.
- **Synchronized Transitions**: Direct panel switches between settings and tabs queue closing animations first before launching opening animations. The dim overlay opacity remains fully active during the transition.
- **Keyboard Autofocus**: Opening a new blank tab automatically clears the address bar, focuses the input field, and shows the keyboard immediately.
- **System Back Gestures**: System back presses or swipe gestures automatically close active panel menus.

---

## Tech Stack
- **Language**: Kotlin (`100%`)
- **UI Architecture**: XML Layouts + Material Design 3 Components
- **Core Engine**: Android `WebView` with custom clients
- **Data Serialization**: JSON-based persistent storage (`SharedPreferences`)
- **Compilation Tooling**: Gradle Kotlin DSL

---

## Build and Run

To compile and verify the codebase without packaging a full APK, run:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew compileDebugKotlin
```

To build the debug APK package:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleDebug
```