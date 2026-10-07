# Folio

A home-screen launcher for GrapheneOS, built from GrapheneOS's Launcher3 (Android 17). It has no Google services and no search bar or feed.

What Folio adds:
- **Drawer tabs**: create tabs for the app drawer and choose their apps, from the drawer or from Folio's settings.
- **Double-tap to lock**: double-tap an empty part of the home screen to turn the screen off.
- **Cover folders**: the folder shows its first app's icon; a tap opens that app, and a swipe up opens the folder. Turn it on from the folder's ⋮ menu.
- **Icon packs**: works with packs made for Nova and ADW, including daily calendar icons, a per-app icon picker (**Edit icon** on any app), and styling for apps the pack doesn't cover.

## Download

Every push to `main` builds the APK on GitHub Actions:
1. Open **Actions › Build Folio**.
2. Pick the latest green run.
3. Download the **Folio** artifact; it's a zip containing the APK.

Tagging a commit `v1.0`, `v1.1` and so on also publishes the APK as a GitHub release.

### Installing on GrapheneOS
1. Install the APK, then pick Folio in **Settings › Apps › Default apps › Home app**. The GrapheneOS launcher stays installed, so you can switch back.
2. Double-tap to lock uses an accessibility service. If **Folio screen lock** is greyed out in Accessibility settings, first go to **Settings › Apps › Folio › ⋮ › Allow restricted settings**.

### Signing
Without a key, CI signs the APK with a throwaway debug key that changes every run, so each new build has to be uninstalled and reinstalled. For updates that install over the previous version, add your own key as repository secrets:

```bash
keytool -genkeypair -v -keystore folio.jks -alias folio -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 folio.jks   # paste the output into the FOLIO_KEYSTORE_BASE64 secret
```

Add these under **Settings › Secrets and variables › Actions**:

| Secret | Value |
|---|---|
| `FOLIO_KEYSTORE_BASE64` | the base64 text above |
| `FOLIO_KEYSTORE_PASSWORD` | keystore password |
| `FOLIO_KEY_ALIAS` | `folio` |
| `FOLIO_KEY_PASSWORD` | key password |

Keep `folio.jks` somewhere safe. Android only accepts updates signed with the same key.

## Building

CI (`.github/workflows/build.yml`) runs on a standard GitHub runner in about 5 minutes. It does the following:
- Installs JDK 21 and the Android 17 SDK (platform `android-37.2`).
- Replaces the SDK's `android.jar` with one that also contains Android 17's hidden and system APIs. Some vendored libraries use those, as they do in GrapheneOS's own build. The jar is only used for compiling; nothing from it goes into the APK.
- Runs `gradle :app:assembleRelease` with the versions pinned in `gradle.properties` and the `gradle.lockfile`s.

To build locally (Linux or macOS, JDK 21, Gradle 9.8+):
1. Do the same `android.jar` replacement in your SDK.
2. Run `gradle :app:assembleRelease`.

The APK is written to `gradle/modules/app/build/outputs/apk/release/`.

The CI also pushes its logs to the `ci-logs` branch, which is handy when GitHub's own log viewer isn't available.

### Updating tools and libraries
Set `folio.useLatestTools=true` in `gradle.properties`, or run the workflow by hand with **latest** ticked. CI then builds with the newest Gradle plugin, Kotlin and libraries, and puts fresh `gradle.lockfile`s on the `ci-logs` branch. Copy those into `gradle/modules/*/`, update the versions in `gradle.properties`, and set the flag back to `false`.

## Layout

| Path | What |
|---|---|
| `launcher3/` | GrapheneOS Launcher3 with Folio's changes (Folio code is in `src/com/android/launcher3/folio/`) |
| `systemui/`, `frameworks/` | The GrapheneOS libraries Launcher3 links against |
| `flags/` | Feature-flag classes generated from GrapheneOS's release configuration |
| `gradle/modules/` | One Gradle module per library; sources stay in the directories above |
| `build-support/` | Flag generator, flag values, and the version helper |

`VENDORED.md` lists the upstream commits and the changes made for the Gradle build.

## Differences from GrapheneOS's launcher

- **Normal app:** Folio is installed like any other app, not as part of the OS. Recents and the gesture-navigation animations stay with the GrapheneOS launcher, because Android only lets the system launcher provide those.
- **Feature flags:** they are fixed to GrapheneOS's release values at build time.
- **Storage Scopes and Contact Scopes:** these shortcuts use GrapheneOS system APIs. Folio offers them only if the OS lets a regular app reach those APIs, and otherwise hides them.
- **App Functions:** Launcher3's App Functions service, which exposes the home screen to AI agents, is left out.

## Status

The APK builds, but it hasn't been tested on a device yet. Please report anything that crashes or looks wrong.

## License

Apache License 2.0, like the AOSP and GrapheneOS code it's built from.
