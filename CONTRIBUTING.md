# Working on Loom

Loom is built on GrapheneOS's Launcher3 (see [VENDORED.md](VENDORED.md)). These are the rules
every change follows, so that anyone can read the code and see what it does and why.

## Naming

The project used to be called Folio. The app's package (`app.folio.launcher`), the Java/Kotlin
package (`com.android.launcher3.folio`), settings keys (`pref_folio_*`) and resource names
(`folio_*`) keep that name, because changing them would reset people's settings and break
updates. Don't rename them.

Everything written for people says **Loom**: comments, descriptions, the README, the changelog
and strings in the app. Older comments may still say Folio; fix them when you're working in
that file anyway.

## Documentation is part of every change

1. **Every source file Loom adds starts with a `/** … */` description** before its main class
   or object: in plain words, what it is, why it exists, and anything surprising (an Android
   rule it works around, why it runs where it does). When you change a file, re-read its
   description and fix anything the change made wrong.
2. **Comments explain why, not what.** Android behaviours, GrapheneOS specifics, choices
   between alternatives. Don't narrate lines.
3. **Changes to GrapheneOS's code are marked** with a comment starting `Loom:` (older ones say
   `Folio:`) saying what was changed and why. Keep these changes small, so upstream updates
   still merge cleanly.
4. **Device test scripts** (`build-support/devtest_*.py`) start with a description of what they
   check and how.
5. **Only document what's there.** Check each claim against the code before writing it.
6. **README.md** and **[docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md)** are updated in the same
   change when a feature, setting, screen or file is added, removed, renamed or behaves
   differently. HOW-IT-WORKS is the plain-language guide for people who don't read code: keep
   it free of code, and keep its tables (Home settings, and which file does what) accurate.
7. **CHANGELOG.md** gets a line under "Unreleased" for every change people will notice, in
   plain language. Release notes are taken from it.

Before committing, this prints nothing:

```sh
for f in $(find launcher3/src/com/android/launcher3/folio -name '*.kt' -o -name '*.java'); do
  grep -q '/\*\*' "$f" || echo "undocumented: $f"
done
```

## Release builds

Release builds are shrunk and optimized with R8. Code that Android or a library reaches by name
(reflection), and fields that only keep a listener alive, need a rule in
`launcher3/folio/proguard-folio.pro`, with a comment saying why.

## Testing

Every push to `main` builds a signed APK on GitHub Actions. The device tests in
`.github/workflows/device-test.yml` run Loom on an emulator (feed, widgets, grid, layout, apps,
icons, theme, dots, backup, a11y, profiles); add or extend one when you add a feature. Some things can only be checked
on a real phone (GrapheneOS-specific options, icon shapes from Wallpaper & style); say so when
that's the case.
