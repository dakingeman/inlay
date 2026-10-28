# Inlay sound kit

How Inlay sounds, and the rules every sound follows. The visual side is in [BRAND.md](BRAND.md); the numbers in code are in `Design.kt` and `Feedback.kt`. Files are made by `tools/sounds.py` and checked by `tools/soundcheck.py`.

## The idea

Inlay is one lit key moving across a calm, dark keyboard. Its sounds work the same way: small, quick and clean, with the weight of a real key, and gone before the next press. Nothing rings on, nothing clashes, and every pack tells you the same things.

## The nine packs

Physical packs sound like real keys and have no notes. Tonal packs play short notes from one scale (C major pentatonic: C D E G A, in pure ratios), so any two sounds together are in harmony.

| Pack | Kind | Character | Made from |
|---|---|---|---|
| **Soft** (default) | Physical | A gentle low thud with a crisp click | A damped thud (200–700 Hz, 1–4 vibrations) plus a 2–5 ms click (1.5–3 kHz) |
| **Thock** | Physical | A deep, rounded mechanical key | A hollow case "tok" tuned to a harmonic of the body, plus a muffled bottom-out |
| **Tactile** | Physical | Dry, crisp switch clicks | A short contact through two small plastic resonances, almost no body |
| **Typewriter** | Physical | A metal strike; Enter is the carriage-return bell | The Soft recipe with a harder strike and a faint metal glint; the bell is root, octave and double octave only |
| **Felt** | Physical | Very soft taps for night, 3 dB quieter | The thud only, with a whisper of click |
| **Chime** | Tonal | Wooden mallet notes, warm and low | A wooden bar: partials at 1×, 4× and 10× (a marimba's tuning), G4–C6 |
| **Glass** | Tonal | Tiny tuned taps on glass | Pure partials 1×, 2×, 4×; switches bend a fifth or an octave; C5–A6 |
| **Pop** | Tonal | Round little drops, each on its own note | A sine that settles onto its note in 6–14 ms, with a soft contact click |
| **Retro** | Tonal | Short 8-bit blips, 1 dB quieter | A band-limited square wave (odd harmonics only), pitch bends for switches |

Soft Deep (1.0–1.1) became Thock in 1.2. A saved Soft Deep choice opens as Thock.

## The ten cues

Every pack has the same ten cues with the same meanings, so switching packs never changes what a sound tells you.

| Cue | Plays when | Weight | Felt loudness | Tonal packs |
|---|---|---|---|---|
| move | The highlight moves (and Settings rows) | Lightest | −24 | A tiny, short note |
| type | A letter, number or symbol; picking an option | Normal | −16 | Rotates C, D, G, A |
| space | Space; switching Settings section | Bigger key | −16 | Lower note |
| delete | Delete (held delete plays it at 60 %) | Bigger key | −17 | A step down, or a small fall |
| on | Shift, symbols, select mode, clipboard, a switch on | Latch | −14.5 | Up a fifth |
| off | The same things switching off | Latch | −15.5 | Down a fifth, or back home |
| lock | Caps lock | Heaviest | −13.5 | Up an octave, longest |
| select_all | Select all | Latch + glint | −15 | A bright note |
| confirm | Enter, paste, picking a clipboard item, opening in Settings | Landing | −15.5 | Home note with its fifth (a soft chord) |
| back | B leaves a mode or closes a screen | Soft | −18 | A small fall |

The loudness order is fixed in every pack: **move < back < delete < letter = space < Enter = off < select all < on < lock**. Felt is mastered 3 dB lower and Retro 1 dB lower, in the same order.

Each pack also has `type1`–`type3`: with `type` that's **four letter sounds**, rotated so the same one never plays twice in a row. Physical packs vary the timbre a little. Tonal packs rotate four notes. Pitch never varies per play; only loudness does (±5 %).

## What makes a sound satisfying (the eight ingredients)

Every file must pass all of them. `soundcheck.py` measures all but the seventh, which is built into every recipe.

| # | Ingredient | The check |
|---|---|---|
| 1 | **Crisp start.** It starts the instant you press | Full strength within 1.5 ms (tonal: 3 ms); no silence before it (≤ 2 ms); no click on the very first sample |
| 2 | **One clean hit.** One press, one sound, then gone | After its peak the sound only gets quieter (no rise over 1.5 dB) |
| 3 | **Soft edges.** Never sharp or piercing | Sharpness ≤ 1.75 acum (Enter, lock, select all ≤ 2.0). Most important for move, the sound you hear most |
| 4 | **Weight that fits the action** | Felt loudness within 0.5 dB of target; peak ≤ −1 dBFS |
| 5 | **The same on every speaker** | The order lock > letter > move and Enter > back holds on headphones, a handheld speaker (high-pass 450 Hz) and a phone speaker (750 Hz). If a sound loses its place, it gets overtones the small speaker can play |
| 6 | **It finishes, never chopped** | Already 30 dB down before the last 10 ms; length limits below |
| 7 | **Touched, not beeped** | Every recipe has a tiny contact click at the start, even the tonal ones |
| 8 | **Alive, in tune, with meaning** | Four letter takes within 0.5 dB of each other but clearly different (≥ 3 dB spectral difference); a letter is 30 dB down before the next one at fast typing (105 ms); every tonal sound settles on a scale note (within 15 cents), bends included; up = on, down = off, Enter lands home |

Length limits: move ≤ 60 ms; letter, space, delete, back ≤ 160 ms; on, off, select all ≤ 200 ms; lock and Enter ≤ 300 ms.

Why these: sound must be obviously caused by the press and agree with the vibration and the picture (Apple, *Designing Audio-Haptic Experiences*, WWDC 2019). Sounds start to annoy as loudness and sharpness rise; sharpness matters above about 1.75 acum (Zwicker and Fastl's annoyance model). Short, damped product sounds read as solid and well made (FORCE Technology; TU Dresden's push-button sound studies).

## Volume

- Loudness is mastered into the files and played 1:1, so Settings sounds exactly like the keyboard.
- The volume slider (1–10) changes **2 dB per step**: 10 plays the files as mastered, the default 7 is 6 dB lower (room to go louder), 1 is 18 dB lower, 0 is off. `Design.SOUND_STEP_DB`, `Feedback.volumeGain`.
- Sounds follow the media volume (`USAGE_GAME`), mute in silent mode unless "Play in silent mode" is on, and only one pack is loaded at a time.

## Files

- `app/src/main/res/raw/<pack>_<cue>.wav` and `<pack>_type1..3.wav`: 13 per pack, 117 in all.
- Mono WAV, 48 kHz (what most Android phones play at, so nothing is converted), 16-bit. Each starts on its first sample, ends with a 6 ms fade into true silence.
- `SoundPacksTest` fails if a pack misses a file, a file belongs to no pack, or the code points at the wrong file.

## Making and checking

1. `pip install numpy scipy mosqito` (mosqito only for the sharpness check).
2. `python tools/sounds.py` writes all 117 files into `res/raw`.
3. `python tools/soundcheck.py` must end with **ALL PASS**. `--json report.json` saves every measurement.
4. `python tools/soundboard.py` makes `build/soundboard.html`: every sound on a button, a fast-typing button and a speaker switch. Open it on the Odin (speaker and headphones) before shipping.

## Adding a pack (the template)

New packs start in the **lab** and only reach the app once they pass everything.

1. In `tools/sounds.py`, copy `TEMPLATE()` (the Soft recipe with every knob named), rename it, and change only its recipe. Keep all ten cues and three letter takes, the weight order (move lightest, lock heaviest), and for tonal packs notes from `note()` in C D E G A only, with letters rotating C, D, G and A. Bends (`glide`) settle on the nearest scale note automatically.
2. Add it to `LAB`, then try it:
   - `python tools/sounds.py --only wood --out build/sound-lab`
   - `python tools/soundcheck.py build/sound-lab --only wood`
   - `python tools/soundboard.py build/sound-lab --only wood,soft` (hear it next to Soft)

   Mastering, loudness targets and the small-speaker fix are applied for you; the template passes as it is, so any fail is caused by the change you made.
3. Listen on the Odin. Fix what the check or your ears say.
4. To ship it: move it from `LAB` to `PACKS`; add it to `SoundPack` (label, one-line description, file prefix) in `KeyboardSettings.kt` and to `resourcesFor` / `takesFor` in `Feedback.kt`; run `sounds.py`, `soundcheck.py` and the unit tests (`SoundPacksTest` checks code and files match). Add a row to the pack table above, and update README, CHANGELOG and the sound-packs picture. The What's new sound showcase (`Demo.SOUNDS` in `TutorialActivity.kt`) plays every pack in `SoundPack` by itself, in the same order, with its name popping up: nothing to add there. If the pack count changes, update the page's title ("Nine sound packs") and the README card's title to match.

A good new pack has **one clear material or idea** (wood, rubber, water, metal), a different place on the map (physical ↔ tonal, soft ↔ bright) from the packs already there, and a one-line description a player understands without hearing it.

## Don't

- Don't randomise pitch, add a second hit, or let anything ring past its length limit.
- Don't use notes outside the pentatonic scale, or partials that aren't whole multiples (they sound sour next to other notes).
- Don't balance by peak level; balance by felt loudness.
- Don't add a delay before a sound to make it "land" with an animation. Sound starts with the press.

## Learnings (from Emre's testing)

- The reference button sounds Emre chose: a short low thud plus a crisp click, landing together. That became Soft.
- A long low ring sounds like tapping a pipe. Everything dies away within its length limit.
- Thirds sound cute; fourths, fifths and octaves sound mature. Tonal letters use C, D, G and A.
- Small speakers can't play low notes, so weight must also live in overtones (1.2).
- The move tick is the sound heard most, so it must be the softest-edged one. In 1.1 it was the sharpest.
- A bend measured in semitones lands slightly out of tune with pure-ratio notes; bends now settle on the exact scale note.
- Mastering must remove the DC offset before the fade, or the file ends on a tiny step instead of silence.
- Emre doesn't need to hear small differences; the checks guarantee the floor, and his ear picks the character.
