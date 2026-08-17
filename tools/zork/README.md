# Zork I for Light Phone III

A port of the classic interactive fiction game **Zork I** to the Light Phone III SDK.
Runs entirely offline — the Zork story file is embedded in the APK, so no internet
is needed.

## Architecture

```
zork/
├── build.gradle.kts            # Android app module — depends on :tools:zork:engine
├── engine/                     # Java library module — the Z-machine interpreter
│   └── src/main/java/de/onyxbits/textfiction/zengine/
│           ├── ZMachine*.java  # Z-machine v3/v5/v8 opcodes
│           ├── ZWindow.java    # Frame buffer + input handling
│           └── ...             # Dictionary, object tree, IFF parsing
└── src/main/kotlin/com/mortimort/zork/
    ├── ZorkScreen.kt           # Main game UI + keyboard editor + About page
    ├── ZorkViewModel.kt        # State: transcript, status line, engine wrapper
    ├── ZorkEngine.kt           # Kotlin adapter over the Java Z-machine
    ├── ZorkEntryPoint.kt       # SDK lifecycle hook (no-op — offline tool)
    ├── StoryData.kt            # Zork I story file as base64 chunks
    └── AboutText.kt            # About page content (copyright, MIT, SDK credit)
```

- **Engine (Java):** Vendored [Zplet](http://zplet.sourceforge.net/) interpreter
  (`de.onyxbits.textfiction.zengine`, Apache-2.0). Compiled as a `:tools:zork:engine`
  Java library module — the Light SDK's Kotlin-only source check only applies to a
  tool module's own `src/`, so Java dependencies are allowed.
- **UI (Kotlin):** Three screens — main game, keyboard editor, About page.

## Build

```bash
# Debug (LP3 emulator)
JAVA_HOME=/usr/local/opt/openjdk@17 ANDROID_SDK_ROOT=~/Library/Android/sdk \
  ./gradlew :tools:zork:assembleDebug

# Release (minified, arm64 only)
JAVA_HOME=/usr/local/opt/openjdk@17 ANDROID_SDK_ROOT=~/Library/Android/sdk \
  ./gradlew :tools:zork:assembleRelease
```

## Install & run

```bash
# Emulator
adb -s emulator-5554 install -r tools/zork/build/outputs/apk/debug/zork-debug.apk
adb -s emulator-5554 shell am start -n com.mortimort.zork/com.thelightphone.sdk.LightActivity

# Real LP3
adb install -r tools/zork/build/outputs/apk/release/zork-release.apk
```

## Project status

- [x] Z-machine v3 engine (Zplet) compiled as Java lib
- [x] Zork I story file embedded as base64
- [x] Main screen with scrollable transcript, command input via LP3 keyboard editor
- [x] About page (title card, copyright, Microsoft MIT release note, SDK credit)
- [x] Restart button on About page — resets Z-machine, returns to main screen
- [x] Back button on About page — returns to main screen
- [x] Build passes `:tools:zork:assembleRelease` (minified, 16MB arm64 APK)
- [ ] Additional story files / save-game support

## Credits & licensing

- **Zork I story & source:** Originally by Infocom (1980). In 2025, Microsoft released
  the Zork I source code and Z-Machine specification under the MIT License.
- **Interpreter:** Zplet by Matthew T. Russotto, released under the Artistic License.
- **Light Phone III SDK:** com.thelightphone.sdk (thelightphone.com).

This is an independent, non-commercial fan project. Zork and Infocom are trademarks
of their respective owners.
