# Changelog

The newest version is first. The section of a version is what the release page shows for it (the list of commits that
GitHub generates comes below it).

## 2.4.0 — 2026-11-18

### Smoother
- The APK is now the optimised release build (R8 shrinking and the baseline profiles of Compose). Earlier APKs were debug
  builds, which Android runs noticeably slower, and that was the main reason why scrolling, transitions and opening files
  did not feel smooth. Android finishes optimising an installed app in the background, so the effect is fullest after the
  app has been used for a while.
- A PDF is opened away from the main thread, so a big file no longer freezes the screen while it opens.
- Pinch and double-tap zoom in PDFs and pictures redraw the page inside the graphics layer instead of rebuilding it on every
  movement of a finger.
- The place in a document is written down only when it has changed, so reading does not make the list do work every second
  and a half.
- A file that is opened from the list uses the details that the list already has, instead of asking the system again on
  the main thread.
- The web engine, which the first Word, Excel or PowerPoint document needs, is loaded once shortly after the app starts,
  so that document no longer stalls the screen while it slides in.

### Audio
- Audio files open on a screen of their own instead of a black video surface: the cover and the tags stored in the file
  (title, artist, album), a seek bar with the time, play and pause, jumps of 10 seconds back and forward, and a speed
  button (0.75×, 1×, 1.25×, 1.5×, 2×). A phone held sideways gets a two-column layout. A file without a cover gets the
  app's own tile.
- Playback pauses for a phone call or another player and when the headphones are pulled out, and goes on with the screen off.
- A file that cannot be played says so and offers to open it in another app. A video file that has only sound is shown
  as audio.
- Video keeps the usual player controls. There is still no notification or lock-screen player.

### PDF
- Search now works in PDFs. It lists the pages that have the words, shows the text around the first match with the number
  of matches on the page, and the previous and next buttons go through those pages. The text is read in the background
  while the bar is open, so the first results come at once and a long document keeps filling in.
- Limits: the words are not marked on the page itself, and a PDF made of scanned pictures has no text to search.

### Reliability and small things
- **About** has a *Copy details* button: the version, the kind of build, the Android version and the device go to the
  clipboard, ready for a bug report. A debug build says so in that window.
- Access that Android gave List to a file is given back when the entry is removed for good, when the list is cleared and
  when an entry is pointed at a file again, so the list of permissions does not grow for ever.
- Android Lint runs next to every build and Dependabot proposes updates of the libraries and of the GitHub Actions
  (once a month). Neither can stop a build or a release.
- Release notes are written here, in this file.
- New unit tests cover the search, the audio arithmetic and the text that *Copy details* produces.

### Updating
- The APK is signed with the same key as before, so it installs over the earlier versions and keeps the list, the names
  and the folders.
- If the optimiser ever fails in the build, the build falls back to the plain debug APK and says so on the release page.
