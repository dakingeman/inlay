# Inlay — Design Guide

*Version 1.0.0*

The source of truth for how Inlay looks, moves, sounds and feels.
The numbers live in code in `Design.kt`; this page explains the thinking behind them.
When adding anything new, check it against these rules first.

## 1. Principles

1. **Controller first.** Everything works with D-pad, A and B. Touch is a bonus.
2. **One way to show a thing.** One focus style, one hint style, one icon family.
3. **Say it when it's useful.** Hints appear when they apply (holding RT, select mode) and disappear after.
4. **Calm by default.** Pure black, one accent colour, no decoration that doesn't carry meaning.
5. **Senses agree.** Sound, vibration and animation fire together and tell the same story.

## 2. Layout and spacing

- Everything sits on a **4 dp grid**: 4, 8, 12, 16, 24, 32. One exception: the keyboard has its own grid in design units (key 31, tool strip 26, edge 6, gap 4) that scales with the keyboard's size setting, so it stays in proportion on every screen.
- **Design tokens live in `Design.kt`** and nowhere else: sizes, colours, faint steps, strokes, motion. `DesignTokensTest` fails if Settings or the tour hand-types a text size.
- Settings content is centred and never wider than **720 dp**. The device picture sits beside the page only from **600 dp** wide; below that it moves above the page, so rows keep their full width. With the keyboard up, the page glides to the screen's middle.
- Info rows with long values (credits, licences) stack the value under the title.
- Settings rows: 16 dp side padding, 12 dp top and bottom, at least 64 dp tall (hint lists in the Button guide: 56 dp).
- Related rows sit together in a **card**, with 1 dp, 6 % white hairlines between rows and a small uppercase **section header** (32 dp above, 8 dp below), starting exactly where the row titles start (card inset 4 + row padding 16).
- **Nested corners share a centre:** inner radius = outer radius − the gap. Cards are 22 dp with a 4 dp inset, so rows inside are 18 dp. The tour window is 18 dp with 8 dp padding, so its keyboard and text box are 10 dp. This keeps curves parallel, which reads as "made with care".
- **Depth comes from light, not shadow** (shadows vanish on OLED black): cards are one step lighter than the background and carry an 8 % white hairline edge.
- Scrolling content fades softly at the top and bottom edges instead of being cut off.

**Every screen shape**
- The keyboard's width stops growing at 1000 dp and centres itself on wide screens.
- Its height never takes more than **60 % of the screen**. On short screens (small 4:3 handhelds, split screen) the whole keyboard scales down evenly, so proportions stay the same.
- Settings on narrow screens (under 600 dp wide, e.g. portrait): wide controls (sliders, option pickers, the colour wheel) move under their title, option pickers shrink to fit, colour dots wrap into shorter rows, and the bottom hint bar leaves out its least needed hints ("Close" always stays).

## 3. Focus

- Keyboard: the key under focus is filled with the **accent colour** plus a soft glow ring, and it jumps three quarters of the way to the next key on the very first frame, then settles (70 ms).
- **Rings sit on the element's own edge, never outside it** (keys, clipboard cards, accent cells, Settings tabs). An outside ring fills the gap and touches the neighbour. Glow: ring on the edge, a hairline of key, then the fill (nested corners). Lift grows by at most a quarter of the key gap. The accent tray fills exactly the tool strip's slot. Corner badges share the key corner's centre, at least 3 units in. (Design audit, 28 Oct 2026.)
- Quiet labels (the space bar's language, the TV box's placeholder and word ghost) are dimmed only as far as 4.5 : 1, on every theme.
- **Icons step aside for badges:** on keys too narrow for both, the shift, delete and enter icons move left (then down) so the corner badge never covers them; Round keys' badges fall back to the tight inset there. Checked for every key, style and platform at the Odin 2 Portal and Retroid Pocket Classic sizes (`HighlightGeometryTest`).
- **Your highlight colour, two strengths** (decided with Emre, 28 Oct 2026): full colour plus a ring means "selected / you are here" (keys, focused rows, tabs). A soft tint without any ring means "this is yours" (the NEW IN tag, the word HOLD, the tour label). Rings are never used for anything but focus and selection.
- Settings: the focused row and the focused tab get the **one focus ring: 2 dp in the highlight colour** on the element's own edge; rows on a lifted surface, faded in over 140 ms. Colour dots are the one exception: their focus ring is white and 4 dp outside the dot, because an accent ring would vanish round the accent dot.
- Pressing A on a row gives a tiny press-in (98.5 %) and release, so every action feels physical. Rows that only show information don't react.
- Changing section slides the new page in from the side you moved towards.
- Up and Down never leave a page by accident. The first and last rows hold focus.

### Device pictures: press and hold
- **Pressed:** every part (button, pill, D-pad arm, stick) gets the accent fill and a thin ring that follows its outline, a small gap away. All rings in a picture share one size, set by its tightest spot (`room.py` measures the room round every button), so a ring never touches anything.
- **Held:** a dark mark sweeps round **inside** the button (dark ink on the accent, like a lit label), then stays. It never gets thinner than 1.5 dp and keeps a lit rim of at least 0.75 dp; on a small button it grows inward until the button is hollow, so a hold reads from across the room at any picture size. Chosen with Emre, 29 Oct 2026, over an outer second ring (thin from a distance) and a darkening wipe (looked switched off).
- A pushed stick tilts inside its well with an arrow on its cap; clicked in, it sinks (86 %) and gets the press ring.

## 4. Colour and contrast

- Backgrounds are OLED black or near-black.
- The keyboard never covers the app with Android's full-screen typing box: you always type straight into the app's own field. The accent is the only saturated colour.
- Text on any key is picked automatically, white or near-black, by the WCAG contrast formula.
- Small text must reach 4.5:1 contrast, icons and focus rings 3:1.
- **The highlight adapts to the theme** (`Contrast.accentOn`): it must stand out 3:1 from the keys and the background. Dark themes use the colour exactly as chosen; on Light it is deepened (same hue, lower lightness) just as far as needed. `ThemeAccentTest` checks every theme with every colour.
- **One dark ink** (`Design.INK_DARK`, #0B0F17) for words and marks on the highlight and on light surfaces.
- **Three faint steps** for hairlines and quiet fills (6 %, 8 %, 12 %) and **three accent strengths**: tag tint 15 %, soft 25 % (shift once, the active tool), glow 35 % (glow, lift, text selection). Quiet labels use one soft ink (alpha 170), digits and chip text one strong ink (210).

## 5. Type

- Letters: Atkinson Hyperlegible Next by default (designed for letter clarity), weight adjustable.
- **One scale for Settings, the tour and What's new: 12 · 14 · 16 · 20 · 24 · 28 sp.** Section labels and eyebrows 12 (uppercase, spaced), captions, tabs and pickers 14, row titles, values and body 16, short-screen titles 20, wide titles 24, the big tour title 28.
- **Nothing is ever smaller than 12 sp** (Android TV's minimum). Text may shrink to fit, but stops at 12; layouts make room instead.
- Keyboard (design units, scaled with the keyboard): letters 19, number pad 22, accent tray and number row 16, word suggestions 14, labels 13, hint chips 12. Word weights: best 600, others 500.

## 6. Icons

- One family: **1.8 dp stroke, round caps and joins** (`Design.STROKE_ICON`), drawn in the same box size, everywhere: keyboard, Settings, tour, loop bar.
- Controller badges always sit on a dark disc with a thin light edge, so they read on any key.
- Badges follow the chosen style everywhere (Xbox, PlayStation, Switch), in Settings too.
- **Each platform's own system buttons:** Xbox View (a window in front of another) and Menu (three lines); PlayStation Create (three short rays) and Options (three lines); Switch − and +. Traced from KDE's gamepad icon set, never drawn from memory.
- **Badge icons share one stroke (`Design.STROKE_BADGE`, 1.3 dp times the badge's size) and one keyline:** every icon fits a circle with at least 12 % of the badge's height to spare. Shapes are balanced by eye: PlayStation's square a little smaller, its triangle a little bigger and sitting on its visual centre.
- `BadgeDesignTest` checks every badge in every style: distinct, inside its keyline, and never borrowing another platform's icon.

## 7. Hints

- **Double presses** are one badge with a small "×2" tucked inside, never two badges side by side. In text: `{SELECTx2}`.
- The only hint shape is the **chip**: a soft pill holding badges and a short word, e.g. `[LB][RB] Jump word`.
- Chips list the most useful action first. If space runs out, the last ones drop.
- Where chips appear:
  - Hold RT: they fade in over the tool strip.
  - Select mode: they appear on the space bar.
  - Clipboard: they sit under the cards.

## 8. Motion

| What | Duration | Curve |
|---|---|---|
| Highlight glide | 75 % jump on the first frame, then 70 ms, shortening to 35 ms while a direction repeats | emphasized decelerate |
| Key press squeeze | 110 ms | emphasized decelerate |
| Hint chips fade | 140 ms | emphasized decelerate |
| Switches, pickers, tabs | 200 ms | emphasized decelerate |
| Settings page change | 200 ms | emphasized decelerate |
| Keyboard preview panel | 260 ms | standard |
| Accent tray | waits 150 ms (a tap never opens it), then rises in 200 ms | emphasized decelerate |
| Tags popping in | 180 ms | emphasized decelerate |
| Device button flash | 260 ms | — |
| Welcome showcase | 160 ms out, 340 ms in, scale 0.94 | emphasized accelerate, then decelerate |
| Vertical handheld morph | 560 ms | standard |
| Held button (device pictures) | waits 120 ms, then sweeps round in 380 ms | standard |
| Tour loop bar starting over | the full bar fades in 260 ms while the new one fills | linear |
| Demo text box cursor | 530 ms on, 530 ms off; stays lit 400 ms after every change | — |
| Stick showing its direction | one full sway in 1.8 s | sine |

- **Fades out use the same quick curve as fades in.** Material suggests an accelerating curve for things leaving, but on a keyboard that makes a chip linger for a moment after you let go. Here the response starts on the very first frame, both ways.
- **What stays, stays still.** On a tour page change only the words slide in (12 dp, from the side you moved towards); the device and the keyboard window keep their place, so the eye stays on the demo.
- **Every duration is a token** (`MOTION_*` in `Design.kt`). The tour's demo pacing (how long a demo waits between key presses) is content, not motion, and lives with the demos.

- **Emphasized decelerate** (Material 3, `0.05, 0.7, 0.1, 1`): a quick start that settles softly. **Emphasized accelerate** (`0.3, 0, 0.8, 0.15`) for things leaving. **Standard** (`0.2, 0, 0, 1`) for panels that move and stay. Nothing uses Android's default curve.
- **The highlight never lags.** When a direction repeats, each glide shortens to fit the gap since the last move.
- **What's new uses the tour's screen.** Same layout, badges, rows and acted-out demos, so a new feature is introduced in the app's own voice. Pages must fit without scrolling.
- **Nothing waits.** A press is drawn on the next frame. Sounds and vibration run on their own helper thread, fonts are built once, and the keyboard only asks the app about its text once typing pauses.
- **Highlight style setting** (after Android TV's focus system: fill, glow, outline, tonal lift):
  - **Glow** (default): accent fill with a soft halo.
  - **Solid:** accent fill only, the simplest.
  - **Outline:** the key lifts one tone, with a 2 dp accent ring on its own edge.
  - **Lift:** an accent-tinted surface, as soft as it can be while still standing out 3:1 from the keys around it (from 35 %, measured per theme). The key grows by at most a quarter of the key gap, with a short accent bar under the label.

  Text on the highlight always picks white or near-black by contrast.
- **Highlight motion setting:** Instant (no animation), Subtle (default), Fluid. In Fluid, the leading edge runs about 30 % ahead in time, so the highlight leans very slightly into the move and then settles.
- **Wrapping** from one edge to the other snaps, with a small pulse, instead of sweeping across the whole keyboard.
- **Panels push, never cover.** When the preview keyboard appears, the page shrinks with it and scrolls in step, so the focused row stays in view.

### Tour and What's new: one row at a time

Pages with button rows play as beats, one per row, in order. The row's words show first (the caption on small screens, the lit row in the list on wide ones), the device rings its buttons and the keyboard tints its key, then the demo acts out only that row. Typing and lit buttons can be quick; the words never are.

- A beat lasts the lead (0.8–1.4 s, longer rows read longer), the acting, and 1 s to see the result. Never under 3 s, and never under the time to read the row at 15 letters a second plus 1.2 s to look over.
- A page's first row waits 1.2 s more, for the title. Runs stay around 10–22 s.
- D-pad up and down step through the rows and stay on one (the loop line rests).
- TourBeatTest checks every page on every device: one beat per row, 3–8 s each, under 30 s a run, and each beat lights its own row's buttons. A new page with rows needs its beats in `beatsFor`.

### Tour: the current row and small screens

- **One current-row marker** for the list and the caption: a 4 dp bar in your colour (lifted to 3:1 on the backdrop), 8 dp before the row's buttons, as tall as them and centred on them, hanging in the margin so the buttons and words still line up with the title. In the list the current row is bright and the others are the quiet grey. No boxes behind rows: a box reads as a button.
- **Small screens:** the title runs across the top; the device sits beside the sentence and the caption, centred on them, 16 dp from the text and with the same margin on its right as the words on their left. The keyboard gets the full width. The device's column is as wide as its shape wants, narrowed until no page's words outgrow their room (the keyboard keeps at least 0.66, letters about 12 dp). On a screen so short that the keyboard can't use the full width anyway, the device sits beside the keyboard instead. The caption stays at the foot of the words, right above the keyboard.
- TourMarkerTest measures all of this on eight screens and five devices: nothing cut off, even steps, centred, margins matching, the device never pushed out.

### Battery: motion that costs nothing when it isn't seen
Inlay is a keyboard: it should never show up in a battery list. Every change keeps to these rules (checked in 1.2, test 31):
- **Nothing runs while hidden.** When the keyboard or a screen goes away, every repeat, timer, stick reader and demo stops (`onWindowHidden`, `onFinishInputView`, `onStop`). An idle keyboard draws nothing.
- **Animations end.** Every animation has a finish; only the tour's and What's new's looping demos run on, and only while they're on screen.
- **Redraw only as often as the eye needs.** Slow things (the loop bar filling over seconds) redraw about 30 times a second; moving pictures (swaying sticks, filling rings) at most 60, even on a 120 Hz screen. Short moves (the highlight, a page sliding) use the screen's full rate.
- **Paint expensive things once.** Big gradients and drawings are painted into an image once and reused (the tour backdrop, the device pictures), never rebuilt every frame.
- **No offscreen layers every frame.** Round corners clip with the view's outline, not a saved layer; layers only for one-off fades.

## 9. Sound

The full sound kit (every pack, cue, target and check) is in [docs/brand/SOUND.md](docs/brand/SOUND.md). The short version:

Nine **sound packs** share the same ten cues and the same meanings, so switching packs never changes what a sound tells you.

| Pack | Character |
|---|---|
| **Soft** (default) | A short, low thud for weight plus a crisp click for texture, played together |
| **Thock** (1.2) | A deep, rounded mechanical key. Replaces Soft Deep |
| **Tactile** | Dry, crisp switch clicks, no notes |
| **Typewriter** | A metal strike; Enter is the carriage-return bell |
| **Felt** | Thud only, almost no click, 3 dB lower: the night pack |
| **Chime** | Wooden mallet notes |
| **Glass** (1.2) | Tiny tuned taps on glass; switches bend a fifth or an octave |
| **Pop** (1.2) | Round drops that settle onto their note |
| **Retro** | Band-limited 8-bit blips, 1 dB lower |

Tonal packs (Chime, Glass, Pop, Retro) use C major pentatonic in pure ratios: on = up a fifth, off = down, lock = up an octave, Enter lands on the home note.

**Every sound passes the eight ingredients** (SOUND.md): crisp start, one clean hit, soft edges (sharpness ≤ 1.75 acum), weight that fits, the same on every speaker, a natural ending, a contact click, and four matched letter takes. `tools/soundcheck.py` must say ALL PASS.

**The Soft recipe (from the reference button sounds Emre chose)**
- **Thud:** two very damped low resonances (~200–700 Hz). They last only 1–4 vibrations, so you get weight with no pitch.
- **Click:** a crisp contact (~1.5–3 kHz), gone in 2–5 ms. This is the texture.
- **Thud and click land together** the instant you press.
- **Bigger action = more thud.** Moving the highlight is only a tiny, soft click.

**Rules for every pack**
- **One action = one hit** (and one vibration pulse). Actions differ by weight, click brightness and material, never by counting hits.
- **Loudness is mastered into the files**, measured as felt loudness on a small speaker:
  - move −24
  - type and space −16, delete −17
  - on −14.5, off −15.5, lock −13.5
  - select all −15
  - enter −15.5, back −18

  Played 1:1, so Settings sounds exactly like the keyboard.
- **Volume slider:** 2 dB per step. 10 plays the files as mastered, the default 7 is 6 dB lower, 1 is 18 dB lower.
- **Four letter sounds in every pack**, rotated so the same one never plays twice in a row.
- **Pitch never varies** between plays; only loudness does (±5 %).
- Settings' volume buttons control media volume, the same one the sounds use.
- **Settings speaks with the chosen pack**, never Android's own clicks:

| In Settings | Cue |
|---|---|
| Move to another row, step a slider or colour | move |
| Switch section (LB / RB) | space (a bigger step than a row) |
| Pick an option | type |
| Switch on / off | on / off |
| Open or run something | enter |
| Close (B) | back, then the screen closes once it has rung out |
| A on a row that only shows information | nothing (nothing happened) |

- Only one pack is loaded into memory at a time.

## 10. Vibration

- Every pulse starts with an **8 ms full-power kick**. Small motors need it to spin up, or light pulses are never felt.
- **One action = one pulse**, matching the sounds:

| Action | Pulse |
|---|---|
| Move | short light tick |
| Type, space, delete, enter | firmer click |
| Switch on, select all | longer, stronger **latch**, fading out rather than stopping dead |
| Caps lock | the heaviest latch |

- The strength sliders change both **how long and how hard** the motor runs.
- Vibration is tagged as touch feedback, so it keeps working in silent mode.

## 11. Controls reference

| Button | Keyboard | Settings |
|---|---|---|
| D-pad / stick | move highlight | move between rows, change values |
| A | type | select / change |
| B | close (or leave a mode) | close |
| X | delete (hold to speed up) | — |
| Y | space | show or hide the keyboard preview |
| LB / RB | move cursor | switch sections |
| LT | letters and symbols (number boxes: number pad → letters → symbols) | — |
| RT | tap: shift. Hold: combos (A capital, LB/RB word jump, X undo, Y accent, LT language) | — |
| View / Select | select mode · ×2 select all | — |
| Menu / Start | enter | — |
| LS (press) | keyboard size: Normal, Large, Largest | — |

The first-run tour uses A / Right = next, B / Left = back, Start = skip.

## App icon

A miniature of the keyboard itself: two rows of keys and a space bar, one key lit in the accent colour. Refreshed in 1.1; the full kit and rules are in `docs/brand/BRAND.md`.

- **Canvas:** 108 dp adaptive icon. The logo is 47.5 × 44 dp and fits inside a 62 dp circle (Android's safe zone is 66), so no launcher mask can cut it.
- **Background:** solid `#0B0B0E`. **Keys:** `#F2F3F5`. **Lit key:** `#72D6E8` (Inlay teal, also the default highlight colour).
- **Keys:** 13 dp squares with 4.25 dp gaps (opened up for light-on-dark), and a 9.5 dp space bar. Corner radius 3.8 dp (29 % of a key, the same as the app's Soft keys). The group sits 0.5 dp above centre (optical centre).
- **Small cut** for 16–24 px: one row of three keys plus the bar.
- **Themed icon (Android 13+):** the same shape in one colour. The lit key becomes an empty slot, so it still reads as the "selected" key.
- **TV banner:** `res/drawable/tv_banner.xml`, symbol and name on ink.
- **Rules:** flat vector shapes only, no shadows, no gradients, no text in the icon.
- **Files:** `res/drawable/ic_launcher_*.xml`, `res/mipmap-anydpi-v26/ic_launcher*.xml`, `docs/images/icon.png` (512 × 512) and `docs/brand/`.

## TV mode

For a docked handheld or Android TV, viewed from about 3 m (Android TV design guide). A bigger keyboard is not the answer; the problems on a TV are different:

| Problem on a TV | What TV mode does |
|---|---|
| The app's text box is small, far away or hidden behind the keyboard | A preview line on top of the keyboard shows the text around the cursor in big type (18 dp), with the cursor and selection |
| A full-width keyboard means long D-pad trips | The keyboard stays compact: at most 600 dp wide, centred |
| The edge of a TV picture can be cut off, and the keyboard hides the game | A floating panel with 5 % safe margins at the sides and bottom; the game stays visible around it; never more than 55 % of the height |
| A thin highlight is hard to see from the sofa | The halo is always on in TV mode, thicker and brighter |
| A TV is a shared screen | Password boxes show dots in the preview line |

Turns on by itself (Automatic) on Android TV devices and whenever a TV or monitor is connected; On and Off are in Settings → Typing. Docking or undocking while typing switches straight away.

## Changing the design safely

One place, then the checks tell you what else to do.

1. **Change the value in `Design.kt`** (colours, sizes, line weights, motion) or the preset in `Palette` (themes, highlight colours). Drawn icons live in `Icons.kt`.
2. **Run the tests.** They write the new values to `design/tokens.*` for everything outside the app, and fail in plain words if anything no longer holds:
   - `DesignTokensTest`: no colour, see-through amount, line weight, text size or animation length typed anywhere else.
   - `TokenSyncTest`: the device kit, README images and audit scripts don't keep their own copy of an app colour.
   - `TourContrastTest`, `SettingsContrastTest`, `ThemeAccentTest`, `KeyboardInkTest`, `ClearThemeTest`, `CustomColourTest`: every word 4.5:1 and every mark 3:1, with every preset, the colour sliders' extremes, every theme (wallpaper themes stood in for by twelve hues) and the see-through theme over a white, black and yellow app.
   - `LegibilityRenderTest`: the same, measured on the drawn keyboard's pixels.
   - `CombinationsTest`: about 27,000 keyboards (every language, layout, size, number row, TV mode, button style, font, key shape, highlight style, letter weight, sticks, hints; on the Odin, a small vertical handheld, an upright phone and a tablet): every word inside its key, clear of its badge and of other words, no letter cut short.
   - `HighlightGeometryTest`, `TvPanelTest`, `IconSheetTest` (icons in their box; app icon inside every launcher mask, brand colours only), `DeviceAssetsTest`, `StageFitsTest`, `ConsistencyTest`.
3. **Device pictures changed?** Run `sh tools/video/brand-kit/build.sh`, then the layout audit (`touch app/build/visuals-layout`, run `LayoutAuditExport`, then `python3 tools/audit/layout_audit.py`).

Fallbacks the checks allow, on purpose: a word on a key too narrow for it and its button badge steps left, shrinks by up to 15 %, steps below the badge, and only then does that key's badge step aside (phones held upright at the larger sizes). `CombinationsTest` lists where that happens.
