# README images and videos

Every image and video on the GitHub page is made here, so it can be made again when the app changes. The keyboard, the device pictures, the badges and the Settings screens in them are drawn by the app's own code; this folder only adds the layout, the words and the game library around them.

The look of every image (backdrop, tiles, colours, the hero) is set down in `VISUAL_SYSTEM.md`. To make the whole kit again in one go, run `./make-kit.sh` after steps 1 and 2.

## Make them again

1. `touch app/build/visuals-request` (the drawing runs only when asked).
2. Stills: `./gradlew testDebugUnitTest --tests '*VisualsExport*'`, then `python3 trim.py` (copies them into `renders/`, cropped to the drawing).
3. Video scenes: run each `VisualsVideo` test on its own (`--tests '*VisualsVideo.sWords'` and so on). Each records 30 frames a second into `app/build/visuals/video/<scene>`.
4. Images: `node shoot.js feature.html out/img/<id>.png 1600 900 id=<id>` for every id in `features.js` (`social` is 1600 × 800, scaled to 1280 × 640).
5. Videos: record the scenes (`--tests '*VisualsVideo.sWords'`, `sAccents`, `sDevices`, `sTv`, `sAuto`, `sSounds`, `sColour`, `sTourLook`, `sSettings`, one per gradle run), then `node render-video.js teaser teaser.plan.json`, `GRAIN=0 node render-video.js teaser-nograin teaser.plan.json` and `python3 finish-video.py teaser --gif`; for What's new `node render-video.js whats-new whats-new.plan.json` and `python3 finish-video.py whats-new`. Each also writes a cue sheet (`out/<name>-cues.md`) for the music. No sound yet. Rules: `VISUAL_SYSTEM.md` §10.
6. Check: `node check.js feature.html 1600 900 id=<id>` measures every box: nothing may overlap, touch (under 8 px) or leave the 24 px margin, and text keeps at least 4.5 : 1 contrast.

## Files

| File | What it is |
|---|---|
| `inlay.css` | The app's design tokens: colours, corners, spacing, fonts |
| `parts.js` | The game library (covers in the app's theme tones, the search box) |
| `features.js` | Every README image: its words and layout |
| `feature.html` | Draws one image (`?id=`) |
| `video.html`, `render-video.js`, `finish-video.py` | The videos: captions, layout, crossfades, MP4, GIF and the cue sheet |
| `*.plan.json` | Which scenes each video plays, in order |
| `check.js` | The measured check |

The demo text is always Xbox classics, because the pictures show Xbox buttons (the app's own demos do the same).

## The next What's new clip (a template)

`whats-new.plan.json` is the template: one line per moment, in order, and every line carries its own words. For a new update:

1. **Pick the moments.** The few highlights from the new CHANGELOG section, one idea each, in order of importance.
2. **Record each one** as a `VisualsVideo` test (one per gradle run): a keyboard scene with `scene("name") { … }` (keyboard + device picture, shown with the game library) or a whole screen with `screen("name") { … }` (Settings, the tour, What's new, at the Odin 2 Portal's size). Copy the closest existing scene. Mark presses with `mark("A")` / `press(…)` so the cue sheet and the pacing check see them. For keyboard scenes, run `python3 pic-boxes.py <scene>` once.
3. **Copy the plan** to `whats-new.plan.json` and change it: the `new` card's `version` and `title` (the update's name from the CHANGELOG), then one line per moment with `scene` or `screen`, `from` / `to` (the frames to show, 30 a second) and its `title` and `sub` (at most about seven words each). Keep the `outro` line as it is.
4. **Render and check:** `node render-video.js whats-new whats-new.plan.json`, then `python3 finish-video.py whats-new`. It must say `pacing: OK`. Changed one moment? Add `--only=<its line number, from 0>` and only that part is drawn again.
5. **Out:** `out/whats-new.mp4` → `docs/images/whats-new-<version>.mp4`, and `out/whats-new-cues.md` for the music.

Rules for the look and pacing: `VISUAL_SYSTEM.md` §10.
