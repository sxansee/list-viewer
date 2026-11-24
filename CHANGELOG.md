# Changelog

The newest version is first. The section of a version is what the release page shows for it (the list of commits that
GitHub generates comes below it).

## 2.5.1 — 2026-11-24

### Zooming
- **PDF:** a pinch zooms around the point between the fingers, follows them at every moment and no longer makes the page
  jump when the fingers are lifted. The pages are no longer laid out again during a zoom: they are enlarged as they are,
  and drawn again in finer detail once the fingers are lifted. Two fingers also scroll the document, and one finger moves
  a zoomed page sideways and up or down at the same time. The page can be zoomed up to 3 times.
- **PDF:** a double tap zooms in on the place that was tapped (a second one zooms back out) with a short animation instead
  of a jump.
- **Pictures:** the same pinch around the fingers and the same double tap. A zoomed picture stops at its own edges, not at
  the edges of the screen, so there is no empty space to drag it into. Pictures are loaded up to 2560 pixels on the longer
  side (they used to be loaded at the size of the screen), so they stay sharp when enlarged, up to 8 times.

### Updating the project
- `tools/Update-List.ps1` keeps its backup copies in the folder `.backups` inside the project instead of next to it. Git
  ignores that folder, the update leaves it alone, and only the 5 newest copies are kept (`-Keep` changes the number). Without
  other instructions the script looks for the project in `D:\my-projects\list`.

## 2.5.0 — 2026-11-22

### Audio that goes on
- Audio files that are opened from the list play as a queue: when one ends, the next audio file of the list starts. The audio
  screen has *previous* and *next* buttons and shows the place in the queue ("3 / 12"). The queue is the audio files of the
  list the file was opened from, in the order the list shows them (up to 200 around the opened one). The name, the menu
  (share, rename, details…) and the remembered place follow the track that is playing.
- Music goes on with the screen off and in front of other apps, with a notification and lock-screen controls (play and
  pause, previous, next, and a seek bar where the phone has one). It is a foreground service of the *media playback* kind.
  Closing the audio screen with *Back* stops the music, and so does swiping the app away from the recent apps.
- **Play in the background** is in the options of the audio screen (⋮ menu) and is on by default. Off, the music is played
  inside the app and ends when the app is left, as in 2.4.0. The choice applies from the next file that is opened.
- From Android 13 the notification needs the permission for notifications. List asks for it once, the first time audio is
  played; the music does not depend on the answer.
- The title of a track is the name of the file (or the name given to it in List). The artist, the album and the cover still
  come from the tags.

### Moving between files
- A file that was opened from the list has *previous file* and *next file* buttons in the top bar and shows its place in the
  list ("3 / 12") in the subtitle, so a folder of documents or pictures can be read through without going back to the list.
  The order is the one the list showed when the file was opened (with its search, filter and folders). The change of file
  is a quick cross-fade.

### PDF
- The words found by the search are marked on the pages: the match that the search bar is at in orange, the others in
  yellow. A jump to a match scrolls the page so that the match is on the screen, also when it is low on a tall page.
- Limits: the places of the words are read from the file when a page comes on the screen, so the marks can appear a moment
  after the page does; text that is turned or runs down the page is not marked, and a PDF made of scanned pictures still
  has no text to search.

### Reliability and bug reports
- If the app crashes, the next start offers (once) to copy a crash report: the error with its trace, the version and the
  device. File paths and addresses are removed from the report. **About** can copy the last report at any time.
- **About** says which key the installed APK is signed with: the public key of this repository, or another one (then its
  fingerprint is shown in full). The signature is also part of *Copy details*.

### Your own signing key
- The release APK can be signed with your own key instead of the public one in the repository: put the key in the
  repository secrets `KEYSTORE_BASE64` and `KEYSTORE_PASSWORD` (and, if they differ from the defaults, `KEY_ALIAS` and
  `KEY_PASSWORD`). `tools/New-ReleaseKey.ps1` makes the key on Windows, outside the project folder. The workflow checks
  the signature of every APK and stops with a clear message if the key cannot be opened or the APK ended up signed with
  another key. Without the secrets nothing changes: builds are signed with the public key, as before. See *Signing key* in
  the README, including the one-time cost of switching (Android does not install an update that is signed with a different
  key, so the first build with a new key needs a reinstall and a restore from a backup).

### Under the hood
- New libraries: Media3 session and Guava (both Apache-2.0, listed in THIRD_PARTY.txt).
- New permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `POST_NOTIFICATIONS`, only for the playback
  notification.
- New unit tests for the audio queue, the navigation between files, the marks in a PDF, crash reports and signatures
  (130 tests in all).

### Updating
- Unless you set your own key, the APK is signed with the same key as before, so it installs over 2.4.0 and keeps the list,
  the names and the folders.

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
