# Changes

What's new in each Loom release (called Folio before 0.3). The **Unreleased** section collects changes as they land;
**Build release** publishes it as the release notes and files it under the new version.

## Unreleased

- Neutral backgrounds like Settings and Contacts: the app drawer, folders, long-press menus and the feed panel use the system's neutral page and card colors instead of a wallpaper-tinted shade; the accent color is kept for highlights. Manage feeds, Style and Grid now group their rows into rounded cards, like Settings.

## 0.3.7 (2026-10-22)

- Colorful GrapheneOS icons: Camera is now light blue (the blue of Open Camera's icon) instead of red.
- Fixed: in dark mode, article text and the feed panel's icons (refresh, ⋮) were black. They're light again.
- Loom's own screens (feed panel, Manage feeds, Style, Grid, the lock popup) use Material 3 Expressive with the system's color roles, so they match Settings and the other system apps. The home screen, app drawer, folders and menus already used the system's colors.

## 0.3.6 (2026-10-21)

- Feed panel: **Large images** card style, with the article's picture across the top and the title, source and time underneath. Choose it in **Manage feeds › Style** (below the Feed panel switch).
- Feed panel: **Unread** and **Starred** moved from the top of the list into the ⋮ menu.
- The feed panel now looks like the app drawer: the home screen fades out behind it as it slides in, its edge has the drawer's rounded corners, and it uses the drawer's colors (following light and dark mode).

## 0.3.5 (2026-10-21)

### Icons
- **Colorful GrapheneOS icons**: GrapheneOS's own apps (Camera, Apps, PDF Viewer, Auditor, Info, Vanadium, and Phone, Messaging, Contacts and the other built-in apps with GrapheneOS's black-on-white icons) now show their symbol in white on a color of their own, and Brave shows its lion in white on orange. Only icons that really are a symbol on a light background change. An icon pack's own icon or one picked with **Edit icon** still comes first, and the switch is in **Home settings › Icons**.

## 0.3.4 (2026-10-19)

### Fixed
- Apps no longer drop out of their drawer tab after an update. Some apps (banking apps especially) rename their launch screen in updates, and tabs used to track apps by that name; they now track the app itself. Apps that already fell out of a tab this way go back into it. A custom icon picked with **Edit icon** survives such updates too.

### New
- **Rename** apps from their long-press menu, in the app drawer or on the home screen. The new name shows everywhere, including search; **Original name** puts it back.
- **Hide** apps from the app drawer from their long-press menu. Type **hidden** in the drawer's search bar to list them; long-press one and pick **Unhide** to bring it back.

### Changed
- On a locked home screen, long-pressing an app shows its usual menu again (shortcuts, App info, Rename...); moving and removing stay blocked. Touch and hold an empty spot to unlock.

## 0.3.3 (2026-10-17)

- Feed panel: going back from an article returns you to the same place in the feed list instead of the top.

## 0.3.2 (2026-10-16)

- Feed panel: a **Back to top** button appears when you start scrolling back up through the feed list or an article; tap it to jump to the top.

## 0.3.1 (2026-10-12)

- The home screen lock popup now always opens in the middle of the screen.
- The app drawer's tabs are centred when they fit; when there are more than fit, they scroll as before.
- The back gesture now works in the feed panel: it closes the article you're reading, and from the list it closes the panel.

## 0.3.0 (2026-10-09)

- **Folio is now Loom.** A loom weaves separate threads into a single fabric, much as Loom weaves folder covers, drawer tabs, icon packs, a feed reader and more into one home screen. New woven icon to match.
- It installs over Folio as an update: your home screen, settings and feeds carry over.

## 0.2.5 (2026-10-09)

- New **Gestures** screen in settings with **Swipe down for notifications** and **Double-tap to lock**.
- **Apps list** is gone from the home screen's long-press menu (swipe up to open the app drawer).
- **Add to home screen** is gone from the app drawer's long-press menu (drag an app to the home screen instead).

## 0.2.4 (2026-10-09)

### Home screen lock
- Long-pressing anything on a locked home screen (an icon, folder, widget or an empty spot) now shows a popup. Touch and hold its **Hold to unlock** button to unlock the home screen for 45 seconds; it then locks again by itself (after any drag in progress ends).
- While locked, long-pressing an app icon shows this popup instead of the app's menu; the menu is back once unlocked.

### Grid
- Changing the home screen or dock size now resets the home screen and dock to the standard layout for the new size (dock apps, plus Email and Gallery on the first page), instead of leaving them empty. The warning now reads **Reset your home screen?**

### Settings
- New **Icons** screen with **Icon pack** and **Style other icons**; its entry shows the current icon pack.
- The **Feed panel** switch now lives only in **Feeds** (no duplicate on the main screen), and Feeds can be opened while the panel is off.
- **Lock home screen** moved to the bottom of the settings screen.

## 0.2.3

- Changing the home screen grid asks first, then clears the home screen and dock for a fresh start; apps stay in the drawer. Changing only the drawer columns keeps everything.
- New **Lock home screen** setting: shortcuts, folders and widgets on the home screen and dock can't be moved, removed, resized or added.

## 0.2.2

- Choose the home screen's columns and rows, the dock size and the app drawer's columns in **Home settings › Grid**, with a live preview.

## 0.2.1

- New feed panel left of the home screen: RSS, Atom and JSON feeds, OPML import and export, and articles shown as clean text and images instead of the whole web page. Off by default; turn it on in Home settings.
- Adding widgets to the home screen works again.
