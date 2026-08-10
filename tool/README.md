# Wikipedia for Light Phone III

A calm, text-first Wikipedia reader for the Light Phone III, built with the
[Light SDK](https://github.com/lightphone/light-sdk). Search articles, open
random ones, browse what happened *on this day*, and jump straight to the next
section or related articles — all in LightOS's monochrome, distraction-free
style.

This is a fork of the Light SDK repo; all tool code lives in the `tool/`
module, which is what Light's build pipeline compiles
(`./gradlew :tool:assembleRelease`).

## Status

**Early community tool — not yet Light-vetted.** As of the Light SDK's July 2026
update, there's no "easy" share path yet; Light is building the pipeline to
sign and distribute community tools. Until then, you can **sideload the debug
APK** onto a Light Phone III via ADB (Light's own docs say that's fine for the
adventurous). LightOS will warn that the tool isn't vetted — that's expected.

## What this tool does

- **Search** — look up any Wikipedia article.
- **Random Article** — open a random article (also available from inside any article).
- **On This Day** — what actually happened on today's date, with the linked
  Wikipedia pages shown as tappable articles beneath each event.
- **Recent** — your last few opened articles, one tap to reopen.
- **Article view** — readable plain-text extract with section navigation
  ("skip to next section") and the full list of related articles, each tappable.
- **Haptics** — taps give a small vibration, matching the rest of LightOS.

## What this tool deliberately does not do

- No accounts, no tracking, no ads.
- No infinite feeds, recommendations, or "discover" surfaces — just the
  encyclopedia, the way LightOS intends tools to behave.

## Screenshot

![Wikipedia home screen](docs/screenshots/home.png)

*Home: Search, Random Article, On This Day, and your Recent articles.*

## Building & sideloading

You'll need Android Studio / the Android SDK and a GitHub token with package
read access (the SDK pulls a dependency from GitHub Packages — see the Light
SDK scaffold README's "Grabbing a token" step). Then, from the repo root:

```bash
./gradlew :tool:assembleDebug
```

The APK lands at `tool/build/outputs/apk/debug/tool-debug.apk`. With Developer
Options + USB debugging enabled on your Light Phone III:

```bash
adb install -r tool/build/outputs/apk/debug/tool-debug.apk
```

In LightOS, allow the tool under tool settings ("Any tools" / sideload) — it'll
warn that this one isn't Light-vetted yet, which is expected for a community
build.

`tool/lighttool.toml` targets real LightOS (`serverPackage = "com.lightos"`).
To run against the LightOS emulator instead, switch that line to
`com.thelightphone.sdk.emulator`.

## Notes

- Requires `android.permission.INTERNET` (Wikipedia REST + Action APIs).
- Article text and metadata come from the Wikimedia APIs; content is licensed
  under [CC BY-SA](https://creativecommons.org/licenses/by-sa/4.0/). The app's
  About screen credits this.

## License

MIT — see the repository [LICENSE](../../LICENSE).
