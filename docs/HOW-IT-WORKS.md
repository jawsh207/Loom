# How Loom works

A plain-language guide to what Loom does and where each part lives, for anyone who wants to
understand the app without reading code. For building and installing, see the
[README](../README.md); for the rules every change follows, see
[CONTRIBUTING](../CONTRIBUTING.md).

## What Loom is

Loom is a home screen app ("launcher") for GrapheneOS phones. It isn't written from scratch:
it's GrapheneOS's own launcher, which itself comes from Android's standard launcher
(Launcher3), with Loom's features added on top. So the basics (home screen pages, the dock,
the app drawer, folders, widgets, notification dots, app shortcuts, search) work the way they
do in GrapheneOS, and Loom's own code sits mostly in one folder,
`launcher3/src/com/android/launcher3/folio/`. Where Loom has to change GrapheneOS's code, the
change is small and marked with a comment, so GrapheneOS's updates can still be merged in.
[VENDORED.md](../VENDORED.md) records exactly which GrapheneOS version Loom is based on.

The project used to be called Folio, which is why that name still appears inside the app
(its package name and file names). Changing those would reset everyone's settings.

## What's on the screen

### Home screen and dock
Pages of app icons, folders and widgets, with the dock along the bottom. Everything here is
standard launcher behaviour, plus:

- **Cover folders.** A folder can show its first app's icon instead of a little grid of icons.
  A tap opens that app; a swipe up opens the folder. It's switched on from the ⋮ menu inside
  an open folder, which also renames the folder.
- **Lock home screen.** Stops anything on the home screen and dock from being moved, removed,
  resized or added. Apps still open and their menus still work. Touching and holding an empty
  spot offers to unlock it for 45 seconds.
- **Gestures.** Double-tap an empty spot to turn the screen off, and swipe down anywhere to
  open the notifications. Turning the screen off needs Loom's accessibility service (Android
  only lets accessibility services lock the screen).
- **Rotation.** Off by default: the home screen and Loom's settings stay upright. On, they turn
  with the phone when auto-rotate is on. Tablets always rotate.

### The app drawer
The list of all apps, opened by swiping up. Loom adds:

- **Drawer tabs.** Your own tabs (say, "Bank" or "Games") each holding the apps you choose;
  swipe left and right between them. An app in a tab is shown only there, not under the main
  **Apps** tab. Tabs are remembered by app, so an app stays in its tab after an update.
- **Rename and hide apps.** Long-press an app to give it a name of your own, or to hide it.
  Typing **hidden** in the drawer's search lists hidden apps so you can bring them back.

### Icons
- **Colorful GrapheneOS icons.** GrapheneOS's own apps have a black symbol on white. Loom
  turns them around: the symbol in white on a colour of its own (and Brave's lion in white
  on orange). It uses each app's real icon, so the symbol is always the app's own.
- **Icon shape** follows what you pick in the system's Wallpaper & style, everywhere in Loom,
  and so does its **App names** switch (show or hide the names under home screen icons).
- **Colours** of Loom's screens come from the system too: neutral greys with "Other colors",
  tinted by your wallpaper with "Wallpaper colors", light or dark with the system theme.

### The feed panel
Off by default. When it's on, swiping right from the first home screen page opens a panel
for reading websites' RSS, Atom and JSON feeds:

- You add a feed (or just a website's address, and Loom finds its feed), sort feeds into
  folders, and import or export them as an OPML file.
- Opening a story shows just the article (text, pictures, headings, quotes, links) pulled out
  of the web page, the way Firefox's Reader View does, instead of loading the whole site.
  Its text can be selected and copied: long-press a word, then drag (or drag the handles) to
  select more. A drag that starts with a long-press selects text instead of closing the panel.
- **Share** sends the article's link; pick a browser from the share sheet to open it there.
- **Listen** reads the article out loud with the phone's text-to-speech engine, following
  along on screen; it can be paused and picks up at the same paragraph.
- Stories can be starred, and filtered to unread or starred ones.
- **At the top** (Manage feeds) can put two things above the stories. **Coming up** shows
  today's date, your next alarm and your next 4 calendar events, however far ahead; it asks for
  calendar access when turned on, and shows the alarm alone without it. It updates as soon as
  an event or alarm changes. Events come from the
  phone's calendar storage, which most calendar apps and sync apps fill; if yours keeps its
  events to itself, the card says there are no calendars to show. **Widget** holds one widget
  of your choice, like the home screen's: it starts at the height it would have there, and the
  handle under it drags it taller or shorter (double-tap the handle to go back). The widget sits
  below Coming up unless **Widget first** is on. Both are off by default.
- Loom checks for new stories in the background, from every hour to once a day (or only when
  you refresh), optionally only on Wi-Fi. This is the only part of Loom that uses the internet, and only
  while the panel is turned on.

## Settings

Everything is in **Home settings** (long-press an empty spot › Home settings):

| Setting | What it does |
|---|---|
| Notification dots | Dots on apps with notifications (Android's own setting) |
| Grid | Home screen columns and rows, dock size, app drawer columns |
| Drawer tabs | Create, rename, fill and remove drawer tabs |
| Feeds | Turn the feed panel on, manage feeds, choose the story style and text size, and what sits at the top of the feed |
| Gestures | Double-tap to lock, swipe down for notifications |
| Add app icons to home screen | New apps go on the home screen |
| Backup & restore | Save your setup to a file, or bring one back |
| Allow home screen rotation | Let the home screen and Loom's settings turn with the phone |
| Lock home screen | Stop the home screen and dock from being changed |

### Changing the grid size
A different home screen size means a different arrangement, so Loom moves your layout onto
the new grid: each item stays in its spot if it still fits; if not, it goes to the nearest
free spot on the same page, then any page with room, then a new page at the end. Dock icons
that don't fit a smaller dock move to the home screen, and a widget bigger than the new grid
shrinks to fit. Nothing is removed, and folders keep their contents.

### Backup & restore
A backup is one `.zip` file saved wherever you choose. It holds your home screen and dock
(pages, folders, widgets, shortcuts), Loom's settings, drawer tabs, renamed and hidden apps,
and your feeds with their starred articles. Restoring asks first, puts everything back and
restarts Loom. Apps that aren't installed are left out. On a new phone (or after reinstalling
Loom) widgets come back as placeholders you tap once to set up, because Android ties each
placed widget to that one installation.

## Where things are kept

Everything stays on the phone, in Loom's private storage; nothing is sent anywhere.

- **The layout** (pages, dock, folders, widgets) is a small database, one per grid size.
- **Settings, drawer tabs, renamed and hidden apps** are in Android's standard settings files.
- **Feeds and saved stories** are in their own database.

## Which file does what

All of these are in `launcher3/src/com/android/launcher3/folio/` unless noted.

| Part | File |
|---|---|
| Rename and hide apps (menu actions, dialogs) | `AppActions.kt`, `AppPrefs.kt`, `FolioShortcuts.java` |
| Backup & restore screen | `BackupActivity.kt` |
| Making and reading backup files | `FolioBackup.kt` |
| Colorful GrapheneOS icons | `ColoredIcons.kt` |
| Cover folders | `CoverFolders.java`, `FolderMenu.java` |
| Double-tap to lock | `DoubleTapToLock.java`, `LockScreenService.java` |
| Swipe down for notifications | `NotificationSwipeController.java` |
| Drawer tabs: the tab strip, swiping, dialogs, settings screen, storage | `DrawerTabStrip.java`, `DrawerTabSwipe.java`, `DrawerTabDialogs.java`, `DrawerTabsActivity.java`, `DrawerTabsStore.java` |
| Grid sizes | `FolioGrid.kt`, `GridSettingsActivity.kt` |
| Moving the layout onto a new grid | `GridMigration.kt` |
| Lock home screen | `HomeLock.kt`, `HomeLockPopup.kt` |
| Rotation of Loom's settings screens | `ScreenRotation.kt` (the home screen's: `states/RotationHelper.java`) |
| "Make Loom your home app" prompt | `SetDefaultHomeActivity.java` |
| Home settings' rounded card look | `SettingsCards.kt` |
| Feed panel: the panel and opening it | `feed/FeedPanel.kt`, `feed/FeedPanelView.kt`, `feed/FeedOverlay.kt` |
| Feed panel: articles | `feed/ArticleExtractor.kt` (pulls the article out of the page), `feed/ArticleContent.kt` (shows it) |
| Feed panel: At the top | `feed/FeedTop.kt` (Coming up and the widget card), `feed/FeedWidget.kt` (the feed's own widget host), `feed/FeedWidgetActivity.kt` (choosing the widget) |
| Feed panel: reading aloud | `feed/FeedSpeaker.kt` |
| Feed panel: fetching and reading feeds | `feed/FeedHttp.kt`, `feed/FeedParser.kt`, `feed/FeedRepository.kt`, `feed/FeedImages.kt` |
| Feed panel: storage and background checks | `feed/FeedDatabase.kt`, `feed/FeedModels.kt`, `feed/FeedRefreshJob.kt`, `feed/FeedPrefs.kt` |
| Feed panel: Manage feeds screen, look, OPML | `feed/FeedSettingsActivity.kt`, `feed/FeedTheme.kt`, `feed/Opml.kt` |

The app's icon is drawn from code in `build-support/icon/gen.py`, which writes the icon files
in `launcher3/res/drawable/`.

## How it's tested

Every change is built on GitHub, and the release build is checked for calls Android would
block. A set of automated tests then runs Loom on an Android emulator and goes through it the
way a person would: adding widgets, using the feed panel, locking the home screen, changing
the grid (comparing the whole layout before and after), drawer tabs across an app update,
renaming and hiding, colorful icons, theme colours, notification dots on cover folders,
the top of the feed and selecting article text, backup and restore, rotation, accessibility labels, and work profiles and Private Space. A
few things can only be checked on a real GrapheneOS phone, such as GrapheneOS-only menu
options and the icon shape and app names switch in Wallpaper & style.
