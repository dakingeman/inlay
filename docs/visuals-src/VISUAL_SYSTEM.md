# Inlay visual system

The rules every Inlay image follows (GitHub, Reddit, release notes, and later the tour and What's new). They were settled with Emre in October 2026 and sit on top of `docs/brand/BRAND.md` and `DESIGN_GUIDE.md`. When a new image is needed, follow these and run `make-kit.sh`; don't redesign from scratch.

## 1. The idea

Flat surfaces made visible with light, not shadow. Graphite from the brand palette does most of the work. The highlight colour (Inlay teal `#72D6E8` on GitHub) only appears where something is **selected**: the lit key, hint chips, the lit button on a device, the lit tiles, and one word of the headline. No decoration in teal, no coloured halos, no purple-to-blue "AI" gradients.

## 2. Backdrop (`backdrop.js`)

| Layer | Rule |
|---|---|
| Base | Vertical gradient `#16171C → #101115 → #0B0B0E` (Ink at the bottom). Never flat black |
| Light | One soft neutral light from above (white at 4 %) |
| Whisper | One wash of the highlight colour in a top corner: OKLCH lightness 0.30, chroma 0.045, the colour's hue turned 14° towards blue (teal → slightly bluer, so it never reads green). Near yellow (within 60° of 95°) a dark wash turns olive or brown, so it keeps only a warm hint (down to 40 % of the chroma) and turns towards orange, never through green (`AccentLight.washStrength`, 29 Oct 2026) |
| Motif | Only on the hero and the social preview: tiles on a 46 px grid (38 px cell, 8 px gap, 9 px corners), see §3. README images, the tour and What's new have no motif |
| Finish | Vignette (24 % at the corners) and fine grain at 6 %. GIF frames leave the grain out (it makes GIFs huge and speckled) |

### Variation (so the README never looks copy-pasted)
Each image is seeded by its id, so it always comes out the same, and its place in the README decides:
- **Whisper corner:** top right, top left, right of centre, left of centre, in turn, so neighbours never share one. The seed nudges its position (±5 %), strength (62–84 %) and spread.
- **Second wash:** every other image gets a quieter blue wash rising from the bottom corner opposite the whisper, so the light feels like it comes from a different place in each image.
Never varies: base colours, the whisper's colour rule, the accent colour. Decided 27 Oct: no dots anywhere, and no tiles outside the hero and the social preview.

## 3. The inlay motif

- **Tiles** sit in the two side bands beside the main visual, exactly as tall as it, with one empty cell between them and the content. Full strength for the first two cells, then a smooth fade towards the image edge (a mask, never stepped columns). No fade at the top or bottom of the band.
- **Tiles look like Settings cards:** white at 4.5 % fill, 13 % hairline edge.
- **Lit tiles** use the app's Glow highlight at 60 %: teal fill, a 2.5 px ring 4 px out, plus a very soft light on the tiles around them (clipped to the tiles, never in the gaps).
- **Placement rules** (`litPlan` in `hero.html`, the same idea in `backdrop.js`): one per side for balance; one in the upper third, one in the lower third; never the same distance from the content on both sides (no mirror); 1–4 cells from the content, never in the faded edge. In a loop, each moves 2–4 cells per slide (it drifts, it doesn't jump), including from the last slide back to the first. Added 27 Oct: a side never keeps the same distance two slides running, uses at least three different distances over the loop, and sits right next to the content at most once.

## 4. Type and layout

- Atkinson Hyperlegible Next Bold for headlines, Inter for text, Roboto inside the app screens (all from the app).
- Hero headline 66 px, subline 23 px `#C9CCD3`. Feature images: headline 46 px, text 21 px. The Inlay logo is 68 px tall wherever it shows (hero, social preview, stage images).
- Everything on the 46 px grid where it can be. Margins at least 96 px (feature images) / 112 px (hero).
- Text keeps at least 4.5 : 1 contrast; nothing closer than 8 px; `check.js` measures every image.
- Panels (app screens, keyboard, close-up cards) have one solid edge, 2 px `#2E2E35`, the same on every background (a see-through hairline looked thin in some places), plus a soft dark shadow. No coloured glow, no inner highlight.

## 4b. Feature images

- **Imagery always matches the words.** Each image shows exactly the setting or moment its text talks about (the sound-packs image shows the Sound pack row, not vibration). Check this before shipping any image.
- **Stage images** (device on the left, screen on the right): the device stands on the same line as the bottom of the screen next to it, whatever the device (handheld, remote). Device 530 px wide, text from y 140, at least 24 px between the text and the device.
- **Hint chips** (Paste / Remove / Back, "D-pad to choose", "Let go to keep it open") get a clearer fill and a teal edge in every render, so they read at README size.
- **Tidy-ups** are shown in a plain notes app (same window style as the library), with a natural sentence, never in the game library.
- **Icons image:** the three controllers on top, and under each a close-up card of the keys whose hints change (shift and delete, ?123 and enter), instead of three full keyboards.
- **Looks image:** four themes in a 2 × 2 grid, big enough to tell apart from a distance, chosen to look as different as possible: Light with ocean blue; Forest with lime, round keys and solid highlight; Mocha with tangerine, sharp keys and the number row; Outline with orchid and the classic layout.

## 5. Device pictures

- One look everywhere (app, hero, README), decided 27 Oct 2026 as **style B**: flat dark body `#1C1F26` (no gradient), every outline the same bright line `#A7AEBC` at one weight (2.5), sunk buttons `#0F1115`, screen `#0C0D11`, white labels `#F2F3F5`. Pressed = teal fill + a ring that follows the button, unchanged.
- Source of truth: `tools/video/brand-kit/premium.js` (colours) → `kitgen.js` → `kit2android.py` → `geo.js` → `app/src/main/assets/devices`. `hero-devices.js` makes the banner copies in `hero-parts/`.
- The hero drops the floating shoulder keys (nothing is pressed); README images and the app keep them.
- Device row (hero): controller, wide handheld (30 % larger), the split vertical handheld (without sticks on the left, with sticks on the right, a teal seam), TV remote. Bottoms on one line, the handheld lifted 42 % and the controller 22 % of their height difference towards the centre. Spacing weighs each drawing half by itself and half by its name; the row spans the keyboard's width and sits just below the middle of the space under it. Names 19 px, the device in use white and bold, the others dimmed to 42 %.

## 6. Generic art (game covers, TV thumbnails)

Flat, one simple shape each, no gradients, no glow. Seven tones (base / shape): blue `#3F63B8/#5F89DB`, terracotta `#B65C34/#DA8459`, green `#2F8A66/#50B08B`, purple `#7650B8/#9B79DA`, ochre `#B88A22/#DCB049`, teal `#22879B/#44AEC3`, berry `#AE4466/#D36B8B`. Shapes: circle, hill, three bars, tilted square, arch, two dots. Covers get a flat dark strip behind the title. TV thumbnails sit at 86 % behind the keyboard. Game names mix platforms (Xbox, PlayStation, Nintendo); TV uses films and series.

## 6b. The demo apps (library, notes, TV search)

The keyboard is always shown typing into an app, never on its own. These apps are someone else's Android app, so they follow Android, not Inlay's brand:
- **Roboto** for everything inside them (titles, covers, notes). Atkinson and Inter stay Inlay's own voice, outside the screen.
- **Window:** black, 22 px corners, one solid edge 2 px `#2E2E35` (the app's `Design.TRACK`, the same edge as the tour's window), soft dark shadow.
- **Text box:** card `#111114`, 8 % hairline, placeholder in the secondary text colour `#9A9DA5` (6.95 : 1; never the darker grey), cursor in the highlight colour.
- **Covers:** flat art (§6), with a dark strip (55 % black) over the bottom 41 %; platform (Roboto Medium, 80 % white, tracked) above the title (Roboto Bold, 95 % white). Measured at least 5.7 : 1 and 7.3 : 1 on every cover colour, the shapes included.
- The tour uses the same text box look inside its window, without the library around it (the keyboard is the subject there, and small screens need the room).

## 7. The hero

- Headline "Type with your **controller**" (teal on *controller*), subline "A free keyboard for Android handhelds, controllers and TV.", logo 68 px without its black box (the social preview uses 112 px, because it's seen small in feeds and the name has to register at a glance) (`hero-parts/lockup-clear.svg`).
- One keyboard panel (21 × 9 grid cells) showing a real app moment, rendered by `VisualsExport.hero` in Xbox icons. Hint chips get a clearer fill and a teal edge in banner renders only.
- Loop, one moment per device: controller → word suggestions ("metal gear sol"), handheld → hold RT tips ("donkey kong| country returns"), vertical → selecting ("conker live and reloaded"), remote → TV mode on a big screen ("stranger t").
- The panel fades out (180 ms) and in (320 ms) between moments, the rest cross-fades; 2.2 s per moment (about 10.7 s per loop, decided 27 Oct: long enough to read, short enough to feel alive); seamless.
- GIF: 1600 × 900, 20 fps, one shared palette, ordered dithering, no grain; about 6 MB (GitHub's limit is 10 MB). The MP4 keeps the grain.

## 8. Highlight colour in the app (for the tour and What's new, not GitHub)

The same rule works for any colour, presets and the colour slider: the whisper, lit tiles, their light, the key highlight, chip edges and lit device buttons all take the user's colour; the graphite base and the logo never change. `hero.html?acc=RRGGBB` previews it.

## 9. Files

| File | What it does |
|---|---|
| `backdrop.js` | The backdrop system for every feature image |
| `hero.html` | The hero and its loop slides (`?s=s1..s4`), social preview (`&social=1`), colour previews (`&acc=`) |
| `hero-parts/` | Banner device pictures and the logo without its box |
| `hero-devices.js` | Remakes `hero-parts` from the app's device kit |
| `hero-loop.py` | Builds the loop video from the slides |
| `make-kit.sh` | Makes the whole kit again: README images (laid out at 1600 × 900, saved at 1920 × 1080 for sharp screens), hero GIF (1600 × 900, kept smaller for file size), social preview (1280 × 640, GitHub's recommended size) and the social/Reddit set (the same images, numbered in gallery order), then compresses every PNG losslessly |
| `tour-mockups.html` | Experiments for the tour / What's new backdrop |

## 10. The launch videos (teaser and What's new)

Settled with Emre on 27 Oct 2026 for 1.2.0. Made by `video.html`, `render-video.js` and `finish-video.py` from the `VisualsVideo` recordings (plus `pic-boxes.py`); the plans are `teaser.plan.json` and `whats-new.plan.json`.

- **Everything moving is the app.** Keyboard scenes (`words`, `accents`, `devices`, `tv`) record the keyboard and device picture; screen scenes (`auto`, `packrow`, `colour`, `tourlook`, `settings`) record whole Settings, tour and What's new screens at the Odin 2 Portal's size. Word suggestions come from the real word list and Learn my words; Automatic runs as if on an AYN Odin 2 Portal.
- **Two layouts.** Stage (as the README stage images): words top left, the device under them, the game library with the keyboard on the right. Screen: headline 72 px and subline over the recorded screen, 1440 px wide, 2 px `#2E2E35` edge.
- **Device pictures in stage scenes:** without the floating shoulder keys (the videos never press them; `pic-boxes.py` finds the body), as big as the room under the words allows (640 px column at most), standing on the screen's bottom line. The devices scene has no subline so the tall devices get the room, and the device's name sits under it.
- **Sound packs:** the real Settings row, stepped with the D-pad, 0.9 s a pack (Emre chose it over designed lists and cards; the What's new page's own showcase types too fast for video). The teaser shows four packs, What's new all nine.
- **Type.** Headlines 76 px (72 px over screens), sublines 32 px `#C9CCD3`, at most about seven words, one idea each. Cards: logo 160 px (Emre: bigger and more obvious), headline 84 px.
- **End card (side by side):** the logo left, a thin divider, then "Free and **open source**." (teal, the one teal phrase), "No ads.", "No tracking." one beat each (0.65 s apart), the link last. Each fades in while rising 16 px over 0.5 s (ease-out); all in by 3.1 s, then 1.5 s still: 4.6 s. No credit line in the videos (the README says who made it).
- **Backdrop.** `inlayBackground` per part (its place in the plan picks the corner), vignette, grain in the MP4 only. No motif.
- **Pacing.** Recordings play at the app's real speed (never sped up); parts are trimmed with `from`/`to`. 8-frame crossfades between parts; device changes use the app's fade-through (5 frames out, 10 in). `finish-video.py` checks the pace from the event log and must say "pacing: OK": key presses at least 0.25 s apart, a new name or label at least 0.8 s before the next, highlight steps no faster than a held direction (0.09 s). When a real demo is too fast for video, record it at a calmer pace or design a calmer moment; show Emre first.
- **Cue sheet.** `finish-video.py` writes `out/<name>-cues.md`: every cut, key press, highlight move and pack change in seconds, for the music and sound effects.
- **Faster renders.** Several browser pages draw frames at once (`PAGES`, default 6) and each part keeps its own folder, so `render-video.js <name> <plan> --only=3,8` redraws just those parts.
- **GIF:** rendered again without grain (`GRAIN=0 node render-video.js teaser-nograin …`), 960 px, 15 fps, one shared palette, ordered dithering, under 8 MB.
