# Folio

A home-screen launcher for GrapheneOS, built from GrapheneOS's Launcher3 (Android 17). It has no Google services and no search bar or feed.

What Folio adds:
- **Drawer tabs**: create tabs for the app drawer and choose their apps, from the drawer or from Folio's settings. Swipe left or right to move between tabs. Apps in a tab are shown only there, not under the main **Apps** tab.
- **Double-tap to lock**: double-tap an empty part of the home screen to turn the screen off.
- **Swipe down for notifications**: swipe down anywhere on the home screen to open the notification shade.
- **Cover folders**: the folder shows its first app's icon; a tap opens that app, and a swipe up opens the folder. Turn it on from the folder's ⋮ menu, which also has **Rename folder**.
- **Icon packs**: works with packs made for Nova and ADW, including daily calendar icons, a per-app icon picker (**Edit icon** on any app), and styling for apps the pack doesn't cover.
- **Grid sizes**: in **Home settings › Grid**, choose the home screen's columns (3–8) and rows (3–10), how many icons the dock holds, and the app drawer's columns, with a live preview. Changing the home screen or dock size asks first, then clears the home screen and dock (shortcuts, folders and widgets) so you start fresh; apps stay in the drawer. Changing only the drawer columns keeps everything. Icons shrink when there are more columns than the phone's default.
- **Lock home screen**: in **Home settings**, stops shortcuts, folders and widgets on the home screen and dock from being moved, removed, resized or added (including new apps and "add to home screen" requests). Long-pressing an icon still shows its menu.
- **Feed panel** (off by default): swipe right from the first home screen page to read RSS, Atom and JSON feeds in a Material 3 panel. Opening an article shows just the article (text, images, headings, quotes and links), pulled from its page the way Firefox's Reader View does, instead of loading the whole website. Add a feed or just a site's address, import or export OPML, sort feeds into folders, star articles, and pick the text size. New articles are checked in the background every few hours (or only when you refresh), optionally only on Wi-Fi. Turn it on in **Home settings › Feed panel**; Folio only uses the network for this, and only while it's on.
- **Easy to switch to**: Folio appears in other launchers' app drawers. Opening it asks to make Folio your default home app.

## Download

Open **Releases** and download the APK from the newest release.

Releases are only published when you ask for one: go to **Actions › Build release › Run workflow**, pick the branch (normally `main`), optionally type a version such as `1.0`, and press **Run workflow**. Leave the version blank to get `0.2.N`. Pushing a `v*` tag (for example `v1.0`) also publishes a release.

Ordinary pushes still build the APK on **Actions › Build Folio**, to check the code compiles. That APK is kept as the run's **Folio** artifact but isn't published.

Folio is built for GrapheneOS's Android 17 and is also tested on Android 16.

### Installing on GrapheneOS
1. Install the APK and open Folio from your current launcher, which asks to make it your home app. You can also pick it in **Settings › Apps › Default apps › Home app**. The GrapheneOS launcher stays installed, so you can switch back.
2. Double-tap to lock (and the most reliable swipe-down for notifications) uses an accessibility service. If **Folio screen lock** is greyed out in Accessibility settings, first go to **Settings › Apps › Folio › ⋮ › Allow restricted settings**.

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

Folio's release key has the certificate SHA-256 `5A:CB:BD:65:B5:6A:3D:A0:13:47:43:44:A7:28:F1:A6:CD:CF:EE:FC:E2:2F:45:CA:91:6F:5F:81:41:12:40:18`. Each build logs the certificate it was signed with (step **Show signing certificate**).

## Building

CI (`.github/workflows/build.yml`) runs on a standard GitHub runner in about 5 minutes. It does the following:
- Installs JDK 21 and the Android 17 SDK (platform `android-37.2`).
- Replaces the SDK's `android.jar` with one that also contains Android 17's hidden and system APIs. Some vendored libraries use those, as they do in GrapheneOS's own build. The jar is only used for compiling; nothing from it goes into the APK.
- Runs `gradle :app:assembleRelease` with the versions pinned in `gradle.properties` and the `gradle.lockfile`s.

To build locally (Linux or macOS, JDK 21, Gradle 9.8+):
1. Do the same `android.jar` replacement in your SDK.
2. Run `gradle :app:assembleRelease`.

The APK is written to `gradle/modules/app/build/outputs/apk/release/`.

The CI also pushes its logs to the `ci-logs` branch, which is handy when GitHub's own log viewer isn't available. That branch also has `hidden-api.txt`, which lists any calls to Android APIs that are private to the OS. Folio is a regular app, so Android blocks those calls, and each entry is a likely crash.

The **Device test** workflow (run it by hand from Actions) installs the latest build of `main` on an emulator, makes it the home app, and either adds widgets through the picker goes through the feed panel against a small local test site (`build-support/devtest-feed`), or checks the home screen lock and the grid change (warning, cleared home screen). Screenshots and logs go to the `ci-devtest` branch. Google's Android 17 emulator image is currently unstable on CI's software graphics, so the **api** box can choose `36` to test on Android 16. Each build also lists any framework resources newer than Android 16 (`sdk-compat.txt` on `ci-logs`), which would crash there.

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
