# Updating Inlay: the checklist

Every change to Inlay goes through this list before it's released, so a new feature feels like it was always part of the app and nothing else breaks. It's written for me, for Claude, and for anyone who forks the app.

## 1. The code

- [ ] It compiles cleanly.
- [ ] Look, motion, sound and vibration values come from `Design.kt` and match `DESIGN_GUIDE.md`.
- [ ] One action = one sound and one vibration pulse. No double hits.
- [ ] Sounds changed? Run `python tools/sounds.py`, then `python tools/soundcheck.py` (must say ALL PASS), listen with `python tools/soundboard.py`, and keep `docs/brand/SOUND.md` in step. New packs start in the lab (SOUND.md, "Adding a pack").
- [ ] Nothing slow runs on a button press. Questions to the app (its text, its cursor) wait until typing pauses, and fonts and sounds are prepared once and reused.
- [ ] A new setting has a sensible default for new users, and people updating keep all their old settings.

## 2. Safe on every device

- [ ] Anything that needs particular hardware (a right stick, analog triggers, a vibration motor) has an off switch and does nothing on devices without it.
- [ ] Controllers differ. Check which axis or button really belongs to what before using it, and never act on an input that hasn't been seen resting first.
- [ ] Works on Android 10 to the newest version. Check anything that changed between versions.
- [ ] Still no internet permission. Passwords (copies marked as sensitive) are never stored or passed on.
- [ ] Portrait, 4:3, wide screens and TV mode still look right, and the keyboard never covers more than 60 % of the screen.

## 3. Inside the app: everyone can find it

- [ ] **Settings:** a row on the right page, with a plain one-line description that shows the buttons as badges, like `{R3}`.
- [ ] **Button guide** (Settings): every new button or combo is listed, and hidden when its setting is off.
- [ ] **Keyboard hints:** if it's an RT combo or a select-mode action, check the tips on the keyboard (RT tips, space-bar chips).
- [ ] **Tour:** if it's a core control, add it to the right page. Check the page on a 1080p landscape screen: every row must show without scrolling (the words shrink a step if needed, but keep pages short).
- [ ] **What's new** (`WhatsNew.kt` + `TutorialActivity`): for an update with something new, bump `WhatsNew.VERSION` and write 1 to 3 highlight pages, each acted out by the keyboard. Fix-only updates leave it alone, so nobody is shown a screen for nothing. Every demo needs a still moment for when Android's "Remove animations" is on. Every demo that plays in a loop shows the **loop bar** under the device picture: give `every(…)` the length of its runs (worked out before they play, so the bar fills at one even pace), or use `runSteps`, which adds up its steps for you. Only the welcome page's endless device showcase goes without. `ConsistencyTest.everyLoopingDemoShowsTheLoopBar` and `loopBarRunsAreExact` check it, and `StageFitsTest` checks that the picture, the loop bar and the keyboard show in full on every screen size.
- [ ] Words match the controller: badges follow the chosen Xbox, PlayStation or Switch icons.

## 4. The repo: everyone can see it

- [ ] `CHANGELOG.md`: what's new, in plain words, newest first.
- [ ] `README.md`: the Features table, the Controls image and its text table, and the "See all N screens" count.
- [ ] **Images and clips:** remake anything the update changes in `docs/visuals-src` (see its README): the README images, the teaser and a What's new clip for the release, drawn by the app's own code (the clip has a template: "The next What's new clip" in that README). It goes at the top of the GitHub release, and into `docs/images/`.
- [ ] Visuals: new or changed cards come from `tools/video/pack.html` (1600 × 900, the app's real layouts). Check every image at full size: 24 px between badges and words, nothing touching or overlapping, and the cards that didn't change still come out identical.
- [ ] `DESIGN_GUIDE.md` if any look, motion or sound value changed.

## 5. Release

- [ ] `versionCode` +1 and `versionName` set in `app/build.gradle.kts`.
- [ ] Signed release APK, built with the **same Inlay key as every earlier release**. Never a new key, never the debug build. The app ID `io.github.dakingeman.inlay` never changes.
- [ ] Tested on the device, installed over the previous version, against a written list of what to test and what "good" looks like.
- [ ] Commit and push, then a GitHub release named `vX.Y.Z` with `Inlay-X.Y.Z.apk` and release notes in Markdown. Obtainium picks it up from there.
