# Inlay music

Inlay's song for its videos, and how it fits any video. Settled with Emre on 31 Oct 2026 (round 43, mix "A6", Felt key sounds), after 40 rounds on 27–28 Oct. The tools live in `tools/video/sound/`; the app's own sounds are in [SOUND.md](SOUND.md).

## The song

| | |
|---|---|
| Key | C major pentatonic (C D E G A), **equal tuning** (pure ratios made the D–A fifth 22 cents flat; Em and G hold both, so they sounded slightly sour) |
| Chords | Am → Em → G → C, one per bar, the melody in the chords' top line |
| Tempo | 92.3 bpm (a beat = 0.65 s, a bar = 2.6 s), fixed for the whole video |
| Grid | Bars are counted back from the end card, so the end card is a downbeat. The groove runs right up to the end card; the home C chord starts on it and rings out (no sound on the logo) |
| Feel | Laid-back deep house under blooming, slightly underwater chords: warm, deep, a little textured, never bright or harsh |

The parts (all rendered by `stems6.py`, the deep chimes by `motifdeep.py`):

| Part | What it is | Plays from energy |
|---|---|---|
| chords | The 1B bloom chords: soft synth chords that swell in over 1.3 s each bar | 0 |
| stabs | Muffled deep-house chord stabs | 0 |
| bass | The deep bass with small octave hops, different in each bar | 0 |
| bounce, pulse | Tuned bounce notes; a sub sine pulse on 8ths | 0 |
| kick | Soft four-on-the-floor kick (its volume follows the energy curve) | 1 |
| stab echo | A dub echo of the stabs | 1 |
| motif | The melody chimes, high (Emre's original pitch) | 1 |
| motif deep | The same chimes an octave lower, 6 dB under: depth without changing the tune | 1 |
| chord repeat | The chord blooms again on beat 3 | 1 |
| chord bass, bloom, harmony (+2 dB), space (+3 dB), bloom repeat (+2 dB) | The blooming atmosphere: the underwater pulse swelling each bar, its harmony, a small dark room, one more quick rise | 2 |

## The shape (what Emre liked)

- **Rise, peak, momentum, descent.** Sparse at the start, the beat "manifests" scene by scene, the peak about **two thirds** of the way through, then it keeps moving while gently coming down, and settles on the end card.
- The **kick fades in and out** with the energy rather than switching on and off; no hard breakdowns mid-video.
- A **breath** where the app's own sounds need room (the sound-pack scene): low energy, kick off.
- The opening (the hero animation) is calm: chords, stabs and bass only.

## Fitting a new video (`arrange.py`)

1. Make the video and its cue sheet (`finish-video.py`), then put the video's length in `videosound.LENGTH` and its cue table in a cue file (`## Teaser` / `## What's new` style headings).
2. Render the parts: `TUNING=equal END_SHIFT=1 python stems6.py cues.md <video> <tracks>` and `CHIMES_DEEP=1 … motifdeep.py`.
3. Write a profile (`profiles/<video>.json`). The simplest is one line:
   ```json
   {"video": "whats-new", "fx_pack": "felt", "arc": {"start": 0, "peak_at": 0.66, "top": 4, "end": 2, "breaths": {"packrow": 1}}}
   ```
   or exact energy per scene (`"levels": {"hero": 0, "buttons": 1, …}`), or pinned curves (`"curves"`, as the approved teaser does).
   - **Energy 0–4** picks the parts (table above) and writes two smooth curves: the whole bed (−5 dB at 0 → 0 dB at 4) and the kick (−11 dB at 1 → 0 dB at 4).
   - Shorter video: same profile, the arc scales to it. Calmer or busier: lower or raise `top`. A different peak: move `peak_at`.
4. `FX_PACK=felt TUNING=equal END_SHIFT=1 python arrange.py profiles/<video>.json cues.md <tracks> <out>` writes the music, the key-sound stem and the video mix, and fails if any check fails.
5. `python mux.py <video.mp4> <out>/<video>-mix.wav <final.mp4> <out>/<video>-effects.wav` puts it in the video and proves the sound survived the encode.

## Rules (all learned the hard way)

- No limiter, compressor or ducking. Fixed volumes and written-in curves only; the bass and pulse dip 3 dB after each kick (a fixed curve).
- No two sustained notes a tone apart (they beat and sound fuzzy); every new part is checked with `rubscan.py` against the approved mix. Deeper stabs were tried and rubbed twice as often, so they stay where they are.
- No valve warmth or distortion on tones with odd overtones (it adds out-of-scale notes: the organ's B).
- EQ is causal (minimum phase): a zero-phase EQ puts a faint pre-echo before every note.
- Music −15 LUFS, true peak ≤ −1 dBTP, lows under 180 Hz in mono; key sounds 7 LU under the music; 24-bit WAVs; AAC 320k with the "fast" coder, decoded and compared after export.
- Small rooms only, steady pitch and tempo, no composed toplines, no sound on logos, nothing cut off at a scene change.
- `audit.py` reports loudness, balance per octave and how much it loses on a phone speaker.

## Key sounds in the videos

Felt (31 Oct 2026, Emre): softer and darker than Soft, it sits inside the music. Every sound you hear is the app's own.
