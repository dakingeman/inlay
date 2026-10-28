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

- Everything sits on a **4 dp grid**: 4, 8, 12, 16, 24, 32.
- Settings content is centred and never wider than **720 dp**. The device picture sits beside the page only from **600 dp** wide; below that it moves above the page, so rows keep their full width. With the keyboard up, the page glides to the screen's middle.
- Info rows with long values (credits, licences) stack the value under the title.
- Settings rows: 16 dp side padding, 12 dp top and bottom, at least 64 dp tall.
- Related rows sit together in a **card**, with 6 % white hairlines between rows and a small uppercase **section header** (32 dp above, 8 dp below).
- **Nested corners share a centre:** inner radius = outer radius − the gap. Cards are 22 dp with a 4 dp inset, so rows inside are 18 dp. This keeps curves parallel, which reads as "made with care".
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
- Settings: the focused row gets a **2 dp ring in the highlight colour** on a lifted surface, faded in over 140 ms, on the row's own edge.
- Pressing A on a row gives a tiny press-in (98.5 %) and release, so every action feels physical. Rows that only show information don't react.
- Changing section slides the new page in from the side you moved towards.
- Up and Down never leave a page by accident. The first and last rows hold focus.

## 4. Colour and contrast

- Backgrounds are OLED black or near-black.
- The keyboard never covers the app with Android's full-screen typing box: you always type straight into the app's own field. The accent is the only saturated colour.
- Text on any key is picked automatically, white or near-black, by the WCAG contrast formula.
- Small text must reach 4.5:1 contrast, icons and focus rings 3:1.

## 5. Type

- Letters: Atkinson Hyperlegible Next by default (designed for letter clarity), weight adjustable.
- Settings: row titles 16, captions 13, section labels 12 (uppercase, spaced).

## 6. Icons

- One family: **1.8 dp stroke, round caps and joins**, drawn in the same box size.
- Controller badges always sit on a dark disc with a thin light edge, so they read on any key.
- Badges follow the chosen style everywhere (Xbox, PlayStation, Switch), in Settings too.
- **Each platform's own system buttons:** Xbox View (a window in front of another) and Menu (three lines); PlayStation Create (three short rays) and Options (three lines); Switch − and +. Traced from KDE's gamepad icon set, never drawn from memory.
- **Badge icons share one stroke (`BadgePainter.ICON_STROKE`, 1.3 dp) and one keyline:** every icon fits a circle with at least 12 % of the badge's height to spare. Shapes are balanced by eye: PlayStation's square a little smaller, its triangle a little bigger and sitting on its visual centre.
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

- **Emphasized decelerate** (Material 3, `0.05, 0.7, 0.1, 1`): a quick start that settles softly. **Standard** (`0.2, 0, 0, 1`) for panels that move and stay.
- **The highlight never lags.** When a direction repeats, each glide shortens to fit the gap since the last move.
- **What's new uses the tour's screen.** Same layout, badges, rows and acted-out demos, so a new feature is introduced in the app's own voice. Pages must fit without scrolling.
- **Nothing waits.** A press is drawn on the next frame. Sounds and vibration run on their own helper thread, fonts are built once, and the keyboard only asks the app about its text once typing pauses.
- **Highlight style setting** (after Android TV's focus system: fill, glow, outline, tonal lift):
  - **Glow** (default): accent fill with a soft halo.
  - **Solid:** accent fill only, the simplest.
  - **Outline:** the key lifts one tone, with a 2 dp accent ring 2.5 dp outside it.
  - **Lift:** an accent-tinted surface. The key and its label grow 7 %, with a short accent bar under the label.

  Text on the highlight always picks white or near-black by contrast.
- **Highlight motion setting:** Instant (no animation), Subtle (default), Fluid. In Fluid, the leading edge runs about 30 % ahead in time, so the highlight leans very slightly into the move and then settles.
- **Wrapping** from one edge to the other snaps, with a small pulse, instead of sweeping across the whole keyboard.
- **Panels push, never cover.** When the preview keyboard appears, the page shrinks with it and scrolls in step, so the focused row stays in view.

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
