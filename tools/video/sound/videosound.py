"""Music and sound effects for Inlay's launch videos (teaser and What's new).

Everything is made here from the cue sheet and the app's own sounds, so it can be made again
whenever a video changes:

    pip install numpy scipy pyloudnorm
    python tools/video/sound/videosound.py <cue-sheet.md> <direction> <out-folder>
        -> teaser.wav, whats-new.wav (final mix), plus music and effects stems

Directions: felt, beat, glass, pluck, retro (see DIRECTIONS).

Rules (docs/brand/SOUND.md, app-sound-studio skill, video mode):
- One key: C major pentatonic (C D E G A), the same as the app's note packs.
- No sound on the logos. Effects are the app's own Soft sounds, on the exact frame of each cue; each sound pack shows
  with its own Settings sample. The music follows the cuts: every part gets its own chord and a
  pulse whose tempo is nudged (about 86-94 bpm) so a whole number of beats fits the part.
- The music ducks under the effects. Master: -14 LUFS integrated, peaks at most -1 dBTP.
"""
import os, re, sys, wave, functools
import numpy as np
from scipy.signal import butter, sosfilt, resample_poly

SR = 48000
HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.environ.get("INLAY_RAW") or os.path.join(HERE, "..", "..", "..", "app", "src", "main", "res", "raw")

# ---------------------------------------------------------------- cue sheet
def read_cues(path):
    """{'teaser': [(t, kind, text)], 'whats-new': [...]} from the cue sheet's two tables."""
    out, cur = {}, None
    for line in open(path, encoding="utf-8"):
        if line.startswith("## Teaser"): cur = out.setdefault("teaser", [])
        elif line.startswith("## What"): cur = out.setdefault("whats-new", [])
        m = re.match(r"\|\s*([\d.]+)\s*\|\s*(\w+)\s*\|\s*(.*?)\s*\|", line)
        if cur is not None and m: cur.append((float(m.group(1)), m.group(2), m.group(3)))
    return out

# End card beats after the end card's cut (+0.27 s crossfade)
END_BEATS = {"logo": 0.0, "divider": 0.25, "foss": 0.6, "ads": 1.25, "tracking": 1.9, "link": 2.6}
# Which pack each "name" cue shows (read from the frames). None = the cut hides it, stay silent.
PACK_ORDER = {"teaser": [],
              "whats-new": ["thock", "tactile", "typewriter", "felt", "chime", "glass", "pop", "retro"]}

# ---------------------------------------------------------------- basics
def T(sec): return np.arange(int(SR * sec)) / SR
def sos(kind, f, o=2): return butter(o, f, kind, fs=SR, output="sos")
def lp(x, f, o=2): return sosfilt(sos("lowpass", min(f, SR * 0.45), o), x, axis=-1)
def hp(x, f, o=2): return sosfilt(sos("highpass", f, o), x, axis=-1)
def db(x): return 10 ** (x / 20)
C4 = 261.63
RATIO = {"C": 1, "D": 9/8, "E": 5/4, "G": 3/2, "A": 5/3}
# Round 42 (31 Oct 2026, Emre: "I don't know if I like the tuning of some of the notes"): in pure tuning the fifth from
# D (9/8) up to A (5/3) is 680 cents, 22 cents flat of a true fifth, and both Em and G hold D4 and A4 together.
# TUNING=equal uses the tuning every piano and synth uses: every fifth the same, nothing sour.
if os.environ.get("TUNING") == "equal": RATIO = {"C": 1, "D": 2 ** (2 / 12), "E": 2 ** (4 / 12), "G": 2 ** (7 / 12), "A": 2 ** (9 / 12)}
def n(name):
    """'C4', 'A2', 'G5' -> frequency in pure C-major-pentatonic tuning."""
    return C4 * RATIO[name[0]] * 2 ** (int(name[1:]) - 4)

FX_PACK = os.environ.get("FX_PACK", "soft")   # round 42: which app sound pack plays the on-screen presses (Emre tried Felt)
@functools.lru_cache(maxsize=None)
def load(name):
    if name.startswith("soft_") and FX_PACK != "soft": name = FX_PACK + name[4:]
    w = wave.open(os.path.join(RAW, name + ".wav")); x = np.frombuffer(w.readframes(w.getnframes()), "<i2") / 32768
    return resample_poly(x, SR, w.getframerate()) if w.getframerate() != SR else x

def add(track, sig, t, gain=1.0, pan=0.0):
    """Mix a mono (or stereo) signal into a stereo track at t seconds. Rule: every sound ends at true
    silence. If its tail is still sounding when its buffer ends, the last 40 ms fade it to zero, so
    a note can never stop with a step (a step is a click; many of them sound like crackle)."""
    sig = np.array(sig, dtype=float, copy=True)
    fade = min(int(SR * 0.04), sig.shape[-1] // 4)
    if fade > 0: sig[..., -fade:] *= np.cos(np.linspace(0, np.pi / 2, fade)) ** 2
    i = int(round(t * SR))
    if i >= track.shape[1] or i < 0: return
    if sig.ndim == 1:
        l, r = np.cos((pan + 1) * np.pi / 4), np.sin((pan + 1) * np.pi / 4)
        sig = np.vstack([sig * l * 1.414, sig * r * 1.414])
    m = min(sig.shape[1], track.shape[1] - i)
    track[:, i:i + m] += gain * sig[:, :m]

def ph0(f, k=1):
    """A fixed starting phase for partial k of a note at f Hz (the same every render). When every
    partial of a chord starts at zero they all peak together, which makes spiky waveforms that need
    taming later; spread starting phases keep the chord smooth, with nothing to tame."""
    return 2 * np.pi * np.random.default_rng(int(f * 100) * 7 + k).random()

def env(t, a, d, hold=None):
    e = np.clip(t / max(a, 1e-4), 0, 1)
    if hold is None: return e * np.exp(-np.maximum(t - a, 0) / d)
    return e * np.where(t < hold, 1.0, np.exp(-(t - hold) / d))

# ---------------------------------------------------------------- instruments (all in tune, whole-number overtones)
def epiano(f, dur, vel=1.0, bright=1.0):
    """A soft electric piano: FM with a decaying index, like a Rhodes played gently."""
    t = T(dur + 1.2); idx = bright * 1.3 * np.exp(-t / 0.35) + 0.15
    s = np.sin(2 * np.pi * f * t + idx * np.sin(2 * np.pi * f * t))
    s += 0.08 * np.sin(2 * np.pi * 4 * f * t) * np.exp(-t / 0.05)          # the soft tine
    s *= env(t, 0.004, 0.9) * np.where(t < dur, 1.0, np.exp(-(t - dur) / 0.25))
    return vel * hp(s, 90)

def mallet(f, dur=0.6, vel=1.0):
    """A wooden bar: partials 1x, 4x and 10x (Inlay's Chime pack, longer)."""
    t = T(dur)
    s = (np.sin(2 * np.pi * f * t) * np.exp(-t / (dur * 0.35)) + 0.3 * np.sin(2 * np.pi * 4 * f * t) * np.exp(-t / (dur * 0.08))
         + 0.05 * np.sin(2 * np.pi * 10 * f * t) * np.exp(-t / (dur * 0.03)))
    return vel * hp(s * np.clip(t / 0.002, 0, 1), 150)

def glassy(f, dur=0.9, vel=1.0):
    """Glass pluck: pure partials 1x, 2x, 4x (Inlay's Glass pack, longer)."""
    t = T(dur)
    s = sum(a * np.sin(2 * np.pi * k * f * t) * np.exp(-t / (dur * d)) for k, a, d in ((1, 1, .3), (2, .3, .18), (4, .08, .1)))
    return vel * hp(s * np.clip(t / 0.002, 0, 1), 250)

def pad(freqs, dur, vel=1.0, bright=1.0):
    """A slow, soft chord. Stereo by a few cents of detune, never wide or washy. The top two notes
    are doubled an octave up, quietly, so small speakers still hear the chord."""
    t = T(dur + 3.0); out = np.zeros((2, len(t)))
    freqs = list(freqs) + [freqs[-2] * 2, freqs[-1] * 2]
    shape = np.clip(t / 0.6, 0, 1) ** 2 * np.where(t < dur, 1.0, np.exp(-(t - dur) / 0.5))
    for f in freqs:
        for ch, c in ((0, -4), (1, 4)):
            ff = f * 2 ** (c / 1200)
            out[ch] += (np.sin(2 * np.pi * ff * t + ph0(ff, 1)) + 0.25 * bright * np.sin(2 * np.pi * 2 * ff * t + ph0(ff, 2))
                        + 0.08 * bright * np.sin(2 * np.pi * 3 * ff * t + ph0(ff, 3))) * shape
    return vel * lp(hp(out, 120), 3500 * bright) / len(freqs)

def sub(f, dur, vel=1.0):
    """Bass with overtones (2x, 3x) so phone speakers still hear it."""
    t = T(dur + 0.3)
    s = (np.sin(2 * np.pi * f * t) + 0.6 * np.sin(2 * np.pi * 2 * f * t) + 0.35 * np.sin(2 * np.pi * 3 * f * t)
         + 0.2 * np.sin(2 * np.pi * 4 * f * t) + 0.1 * np.sin(2 * np.pi * 6 * f * t))
    s *= np.clip(t / 0.01, 0, 1) * np.where(t < dur, np.exp(-t / 2.5), np.exp(-dur / 2.5) * np.exp(-(t - dur) / 0.08))
    return vel * s

def softglass(f, dur=0.9, vel=1.0):
    """A rounder glass note for the airy mix: fundamental and octave only, a 4 ms swell, no edge."""
    t = T(dur); s = (np.sin(2 * np.pi * f * t) + 0.15 * np.sin(2 * np.pi * 2 * f * t)) * np.exp(-t / (dur * 0.3))
    return vel * hp(s * np.clip(t / 0.004, 0, 1), 250)

def chipwarm(f, dur=0.4, vel=1.0):
    """The 8-bit voice, warmer: rounded off above 2.5 kHz with a slow tape-like wobble."""
    t = T(dur + 0.25); wob = 1 + 0.0012 * np.sin(2 * np.pi * 0.7 * t)
    ph = 2 * np.pi * np.cumsum(f * wob) / SR
    s = (np.sin(ph) + 0.4 * np.sin(2 * ph) + 0.08 * np.sin(4 * ph)) * np.clip(t / 0.006, 0, 1)
    s *= np.where(t < dur, np.exp(-t / (dur * 0.9)), np.exp(-dur / (dur * 0.9)) * np.exp(-(t - dur) / 0.08))
    return vel * lp(hp(s, 150), 3200)

def reverb(x, mix=0.18, size=1.4):
    """A soft room: decaying, darkened noise as the echo, a little different left and right."""
    from scipy.signal import fftconvolve
    t = T(size); out = np.zeros((2, x.shape[1] + len(t) - 1))
    for ch in (0, 1):
        ir = lp(np.random.default_rng(11 + ch).standard_normal(len(t)), 3500) * np.exp(-t / (size / 5))
        ir /= np.sqrt(np.sum(ir ** 2))
        out[ch] = fftconvolve(x[ch], ir)
    return x * (1 - mix) + mix * out[:, :x.shape[1]]

def atmopad(freqs, dur, vel=1.0, cents=2, rel=0.9):
    """Slow, warm chords that melt into each other: 0.9 s swell in, 1.6 s fade out, a few cents of
    chorus, nothing above about 2.5 kHz."""
    t = T(dur + 5.0); out = np.zeros((2, len(t)))
    # a slow, curved swell in and a long, curved fade out, so one chord melts into the next
    a = np.clip(t / 1.3, 0, 1); sw = a * a * (3 - 2 * a)
    shape = sw * np.where(t < dur, 1.0, np.exp(-(t - dur) / rel))
    for f in freqs:
        for ch, c in ((0, -cents), (1, cents)):                    # a hair of width (0 for the per-note voice)
            ff = f * 2 ** (c / 1200)
            out[ch] += (np.sin(2 * np.pi * ff * t + ph0(ff, 1)) + 0.12 * np.sin(2 * np.pi * 2 * ff * t + ph0(ff, 2))) * shape
    return vel * lp(hp(out, 140), 2000) / len(freqs)

def atmonote(f, dur, vel=1.0):
    """One voice of the atmospheric chord, held for as long as that note stays in the chord (a common
    tone is never restarted: two copies of one note, one fading and one starting, beat against each
    other, which is heard as a fuzzy resonance at every chord change). No left/right detune: the
    stereo comes from placing the four voices, so a mono speaker hears no beating either."""
    return atmopad((f,), dur, vel / 4, cents=0)

def atmonote_q(f, dur, vel=1.0):
    """The same chord voice, but it lets go quickly (40 ms) because the next chord has a neighbouring
    note: a long fade would ring against it and beat."""
    return atmopad((f,), dur, vel / 4, cents=0, rel=0.04)

def airlead(f, dur, vel=1.0, rel=0.4):
    """A soft, round, held voice for a slow sung line: a pure tone with a whisper of 2nd and 3rd
    harmonic, an 80 ms curved swell, easing down to about 70 % while held, a 0.4 s fade. Steady pitch,
    no vibrato, nothing above about 2.5 kHz."""
    t = T(dur + 1.6)
    s = np.sin(2 * np.pi * f * t + ph0(f, 1)) + 0.18 * np.sin(2 * np.pi * 2 * f * t + ph0(f, 2)) + 0.04 * np.sin(2 * np.pi * 3 * f * t + ph0(f, 3))
    a = np.clip(t / 0.08, 0, 1); body = 0.7 + 0.3 * np.exp(-t / 0.9)
    s *= a * a * (3 - 2 * a) * body * np.where(t < dur, 1.0, np.exp(-(t - dur) / rel))
    return vel * lp(hp(s, 150), 2500)

def softbass(f, dur, vel=1.0):
    """A bass that sits behind the music: the triangle's body without its buzzy overtones,
    a soft 25 ms start and a gentle end, so you feel it more than hear it."""
    t = T(dur + 0.4)
    s = np.sin(2 * np.pi * f * t) + 0.25 * np.sin(2 * np.pi * 2 * f * t + ph0(f, 2)) + 0.08 * np.sin(2 * np.pi * 3 * f * t + ph0(f, 3))
    a = np.clip(t / 0.025, 0, 1)
    s *= a * a * (3 - 2 * a) * np.where(t < dur, 1.0, np.exp(-(t - dur) / 0.06))
    return vel * lp(s, 500)

def chipsmooth(f, dur=0.3, vel=1.0):
    """The 8-bit voice with its edges softened: a 10 ms swell and a faint scoop up into the note
    (from 12 cents under, settling in 25 ms), so one note flows into the next."""
    t = T(dur + 0.7)
    ph = 2 * np.pi * f * t                                        # steady pitch: no slides of any kind
    s = np.sin(ph) + 0.40 * np.sin(2 * ph) + 0.05 * np.sin(4 * ph)
    a = np.clip(t / 0.018, 0, 1)                                  # a softer 18 ms start: no sharp peak
    s *= a * a * (3 - 2 * a) * np.where(t < dur, np.exp(-t / (dur * 0.6)), np.exp(-dur / (dur * 0.6)) * np.exp(-(t - dur) / 0.12))
    return vel * lp(hp(s, 150), 3200)

def hat(vel=1.0):
    """A soft, dark shaker instead of a bright hi-hat: noise around 3-6 kHz (not 7 kHz and up),
    a 1.5 ms swell instead of a hard edge, gone in about 40 ms."""
    t = T(0.045); nz = np.random.default_rng(7).standard_normal(len(t))
    s = lp(hp(nz, 2800), 6000) * np.clip(t / 0.0015, 0, 1) * np.exp(-t / 0.011)
    return vel * 1.3 * s

def kick(vel=1.0):
    """A soft, round kick (a falling sine) with Thock's key as its click, so it's still a keyboard."""
    t = T(0.35); f = 48 + 70 * np.exp(-t / 0.03)
    s = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.11) * np.clip(t / 0.002, 0, 1)
    k = load("thock_type"); s[:len(k)] += 0.35 * k
    return vel * s

# ---------------------------------------------------------------- harmony (pentatonic only)
CHORDS = {   # name: (bass, voicing)
    # Open voicings only: no two notes closer than a fourth. Two sustained notes a second apart
    # (say D4 and E4, 33 Hz apart) beat against each other 33 times a second, which the ear hears
    # as a fuzzy resonance; spread out, the same chord is glassy and clean.
    "C":  ("C2", ["C3", "G3", "E4", "D5"]),     # C add9, open
    "Am": ("A1", ["A2", "E3", "C4", "G4"]),     # A minor 7, open
    "G":  ("G1", ["G2", "D3", "A3", "E4"]),     # G 6/9, stacked fifths
    "Em": ("E2", ["E3", "A3", "D4", "G4"]),     # E quartal, stacked fourths
}
# The chord for each part of each video, by its name in the cue sheet
PART_CHORD = {"intro": "C", "new": "C", "words": "Am", "accents": "G", "devices": "C", "auto": "Em",
              "packrow": "C", "colour": "Am", "tourlook": "G", "tv": "Em", "settings": "G", "outro": "C"}
RETRO_TUNE = {"C": ["C4", "G4", "D5", "G4"], "Am": ["A3", "C4", "G4", "C4"], "G": ["G3", "D4", "A4", "D4"], "Em": ["A3", "D4", "G4", "D4"]}
ARP = {"C": ["C4", "G4", "D5", "E5"], "Am": ["A3", "E4", "G4", "C5"], "G": ["G3", "D4", "A4", "E5"], "Em": ["E4", "A4", "D5", "G5"]}

def parts(cues, length):
    """[(name, start, end)] from the cut times."""
    cuts = [(0.0, cues[0][2].split()[0])] + [(t, txt.split()[0]) for t, k, txt in cues if k == "cut"]
    return [(nm, s, cuts[i + 1][0] if i + 1 < len(cuts) else length) for i, (s, nm) in enumerate(cuts)]

def beats(s, e, target=0.667):
    nb = max(1, round((e - s) / target)); b = (e - s) / nb
    return [s + i * b for i in range(nb)], b

# ---------------------------------------------------------------- more instruments for the new directions
def pluck(f, dur=0.5, vel=1.0, seed=0):
    """A soft plucked string (Karplus-Strong), damped so it never rings on."""
    p = int(round(SR / f)); rng = np.random.default_rng(seed)
    buf = lp(rng.standard_normal(p), 3000); out = np.zeros(int(SR * dur)); i = 0
    while i < len(out):
        m = min(p, len(out) - i); out[i:i + m] = buf[:m]
        buf = 0.996 * 0.5 * (buf + np.roll(buf, -1)); i += m
    t = T(dur)
    return vel * hp(out * np.exp(-t / (dur * 0.35)), 150)

def chip(f, dur=0.3, vel=1.0, harm=7):
    """A mellow 8-bit note. A square wave's 3rd overtone sits outside the scale on some notes and
    sounds sour, so this is a soft pulse made of in-tune overtones only (1x, 2x, 4x) plus a little
    triangle body."""
    t = T(dur)
    s = np.sin(2 * np.pi * f * t) + 0.45 * np.sin(2 * np.pi * 2 * f * t) + 0.10 * np.sin(2 * np.pi * 4 * f * t)
    s *= np.clip(t / 0.003, 0, 1) * np.exp(-t / (dur * 0.45))
    return vel * lp(hp(s, 150), 4500)

def tri_bass(f, dur, vel=1.0):
    """A triangle-wave bass with its overtones (odd, falling fast), like a handheld's."""
    t = T(dur + 0.05)
    s = sum(((-1) ** (k // 2)) * np.sin(2 * np.pi * k * f * t) / (k * k) for k in range(1, 12, 2))
    return vel * s * np.clip(t / 0.005, 0, 1) * np.where(t < dur, 1.0, np.exp(-(t - dur) / 0.03))

# ---------------------------------------------------------------- the composition (as notes first, then checked, then played)
BEAT = 0.65                     # one tempo for the whole video (92.3 bpm), lined up so the end card's logo is a downbeat
BAR = 4                         # beats per bar
BARS = ["Am", "Em", "G", "C"]   # one chord per bar; the bar before the end card is C, so the tune lands home
# The tune: an 8-bar phrase and its answer, in beats (start, note, length). Only C D E G A. Bar 8 resolves
# to C and leaves a breath before the end card.
MELODY = [   # built on round 6's calm feel (Emre liked it) with the rules that make a tune satisfying:
    # one rhythm the ear can predict (two short notes, one long: q q h), mostly steps, an arch that rises
    # to a peak and comes back down, bars 2-3 heard again as 6-7, a question at bar 4 (ends on D, open)
    # and the answer at bar 8 (steps down D -> C, home). Strong beats are always notes of their chord.
    [(0, "C5", 1), (1, "D5", 1), (2, "E5", 2)],     # Am  rise
    [(0, "G5", 1), (1, "E5", 1), (2, "D5", 2)],     # Em  touch the top, fall
    [(0, "G4", 1), (1, "A4", 1), (2, "D5", 2)],     # G   climb again
    [(0, "E5", 2), (2, "D5", 2)],                   # C   the question: left open
    [(0, "E5", 1), (1, "G5", 1), (2, "A5", 2)],     # Am  rise higher: the peak of the tune
    [(0, "G5", 1), (1, "E5", 1), (2, "D5", 2)],     # Em  same as bar 2
    [(0, "G4", 1), (1, "A4", 1), (2, "D5", 2)],     # G   same as bar 3
    [(0, "C5", 4)],                                 # C   the answer: a step down to home, held into the end card
]
MSTYLE = os.environ.get("MSTYLE", "tune")     # tune | arp8 | arpquarter | roll
# The rising open arpeggio from rounds 1-2 (the "melody" Emre liked first): root, fifth, ninth, third
ARP_UP = {"C": ["C4", "G4", "D5", "E5"], "Am": ["A3", "E4", "G4", "C5"], "G": ["G3", "D4", "A4", "E5"], "Em": ["E4", "A4", "D5", "G5"]}
TOPLINE = [[(0, "E5", 4)], [(0, "D5", 2), (2, "E5", 2)], [(0, "D5", 4)], [(0, "E5", 4)],
           [(0, "E5", 4)], [(0, "G5", 2), (2, "E5", 2)], [(0, "D5", 4)], [(0, "C5", 4)]]
# Close chords where neighbours share notes, so moving between them sounds like one smooth swell
ATMO_SETS = {
    # Two sustained notes a tone apart (D4 next to E4, say) beat 33 times a second, heard as a fuzzy
    # resonance. The original close voicings had that in three chords (measured 0.32 asper on the
    # chord bed). "warm" moves just that one note up an octave (0.19); "open" spreads every chord so
    # no two notes are closer than a fourth (0.13, the cleanest). Emre chooses by ear.
    "warm": {"Am": ("A3", "C4", "E4", "G4"), "Em": ("G3", "E4", "A4", "D5"), "G": ("G3", "E4", "A4", "D5"), "C": ("C4", "E4", "G4", "D5")},
    "open": {"Am": ("A3", "E4", "G4", "C5"), "Em": ("E3", "A3", "D4", "G4"), "G": ("G3", "D4", "A4", "E5"), "C": ("G3", "C4", "G4", "E5")},
}
ATMO = ATMO_SETS[os.environ.get("ATMO_SET", "warm")]
ATMO_MIX = {ch: ((-0.4, 1.0), (0.4, 1.0), (-0.15, 1.0), (0.15, 0.85)) for ch in ("Am", "Em", "G", "C")}   # (pan, level) per voice
CHORD_NOTES = {"Am": "ACEG", "Em": "EGAD", "G": "GDAE", "C": "CEGD"}   # pentatonic chord tones (with colour notes)
CHORD_TONES = {"C": ["C4", "E4", "G4", "D5"], "Am": ["A3", "C4", "E4", "G4"], "Em": ["E4", "G4", "A4", "D5"], "G": ["G3", "D4", "A4", "E5"]}
DIRECTIONS = ["felt", "warmglass", "retro", "retrowarm", "beat", "glass"]

# ---------------------------------------------------------------- the new directions (round 23)
# All three start from what Emre liked: the rolling arpeggio of rounds 1-2 / 2AS over the calm 1B bed.
# The bed under each bar is chosen so no bed note sits within a tone of any arpeggio note in that bar
# (the beating rule in check_score enforces it).
VARIANT = os.environ.get("VARIANT", "")
BED_SAFE = {"Am": ("A3", "E4", "C5"), "Em": ("E3", "A3", "D4", "G4"), "G": ("G3", "D4", "G4"), "C": ("G3", "C4", "G4", "E5")}
# "hidden": the top note of each bar's run sings a line: rising C D E G, then answering G E D (-> C)
HIDDEN = {   # (chord, half of the 8-bar phrase) -> four notes, low to high; the last one is the line note
    ("Am", 0): ("A3", "E4", "G4", "C5"), ("Em", 0): ("A3", "D4", "G4", "D5"), ("G", 0): ("G3", "D4", "A4", "E5"), ("C", 0): ("C4", "G4", "C5", "G5"),
    ("Am", 1): ("A3", "E4", "C5", "G5"), ("Em", 1): ("A3", "D4", "A4", "E5"), ("G", 1): ("G3", "D4", "A4", "D5"), ("C", 1): ("G3", "C4", "G4", "C5"),
}
BED_HIDDEN = {"Am": ("A3", "E4", "G4"), "Em": ("E3", "A3", "D4"), "G": ("G3", "D4", "A4"), "C": ("G3", "C4", "G4")}
END_CHORD = {"": None, "clean": None, "hidden": ("C4", "G4", "C5", "E5"), "pulse": ("C4", "G4", "C5", "E5")}

LINE = {0: {"Am": "C5", "Em": "D5", "G": "E5", "C": "G5"}, 1: {"Am": "G5", "Em": "E5", "G": "D5", "C": "C5"}}
NEXT_CHORD = {"Am": "Em", "Em": "G", "G": "C", "C": "Am"}

def line_bar(ch, i):
    """The sung line for one bar: its line note held for three beats, then one passing note on
    beat 4 that leads into the next bar's line note (a chord tone of this bar, approached from the
    side it's moving to, never the same note twice in a row)."""
    here = LINE[(i % 8) // 4][ch]; nxt_ch = NEXT_CHORD[ch]; nxt = LINE[((i + 1) % 8) // 4][nxt_ch]
    if i + 1 == -1: nxt = "C5"
    semi = lambda nt: 12 * np.log2(n(nt) / C4)
    cands = [f"{c}{o}" for c in CHORD_NOTES[ch] for o in (4, 5)]
    cands = [c for c in cands if c not in (here, nxt) and 2 <= abs(semi(c) - semi(nxt)) <= 5 and 60 <= semi(c) + 60 <= 79]
    if not cands: return [(0, here, 4)]
    toward = [c for c in cands if (semi(c) - semi(nxt)) * (semi(here) - semi(nxt)) > 0] or cands   # from the side we come from
    pick = min(toward, key=lambda c: abs(semi(c) - semi(nxt)))
    return [(0, here, 3), (3, pick, 1)]

def variant_arp(ch, i):
    """The four arpeggio notes for this bar, already in their final octave."""
    if VARIANT in ("hidden", "pulse"): return list(HIDDEN[(ch, (i % 8) // 4)])
    up = lambda nt: nt[:-1] + str(int(nt[-1]) + 1)
    return [up(x) for x in ARP_UP[ch]]                      # "clean": exactly 2AS's notes, an octave up as before

def variant_bed(ch):
    if VARIANT == "clean": return BED_SAFE[ch]
    if VARIANT in ("hidden", "pulse"): return BED_HIDDEN[ch]
    return ATMO[ch]

def timeline(cues, length):
    """Bar lines for the whole video at one fixed tempo. Bar 0 is the end card (C); bars count back
    from it, so the bar before the end card is G and the phrases end there."""
    e0 = max(t for t, k, x in cues if k == "cut" and x.startswith("outro")) + 0.27
    # Round 42 (Emre: "it sounds like the song is about to end while it's still in the TV section"): the home bar, where
    # the groove stops and the C chord rings out, used to be the bar BEFORE the end card. END_SHIFT=1 moves it onto the
    # end card itself (the grid stays locked to it, one bar later), so the groove carries the last scene to the cut.
    if os.environ.get("END_SHIFT") == "1": e0 += BEAT * BAR
    bar_s = BEAT * BAR; first_bar = -int(np.ceil(e0 / bar_s))
    bars = []
    for i in range(first_bar, int((length - e0) / bar_s) + 1):
        t = e0 + i * bar_s
        bars.append(dict(i=i, t=t, chord="C" if i >= 0 else BARS[i % 4], end=i >= 0,
                         phrase=MELODY[i % 8] if i < 0 else None))
    return bars, e0

def compose(direction, cues, length):
    """The whole score as a list of notes: dict(t, inst, note, dur, vel, pan, voice)."""
    bars, e0 = timeline(cues, length); ev = []
    def note(t, inst, nt, dur, vel, pan=0.0, voice=None):
        if not 0 <= t < length: return
        t = round(t, 6)
        # Never double the tune: an accompaniment note landing on the tune's note moves up an octave
        if voice not in (None, "melody") and isinstance(nt, str) and inst not in ("kick", "rim", "hat"):
            while any(e["note"] == nt and abs(e["t"] - t) < 0.001 for e in ev):    # any pitched note already there
                nt = nt[:-1] + str(int(nt[-1]) + 1)
        ev.append(dict(t=t, inst=inst, note=nt, dur=dur, vel=vel, pan=pan, voice=voice or inst))
    lead = {"felt": "epiano", "warmglass": "epiano", "glass": "glassy", "beat": "epiano", "retro": "chip", "retrowarm": "chipwarm"}[direction]
    for bar in bars:
        t0, ch = bar["t"], bar["chord"]; bass, voicing = CHORDS[ch]; tones = CHORD_TONES[ch]
        if bar["end"]: continue                                   # the end card plays the chord already ringing
        if t0 < 0:                                                # the video starts mid-bar: just the chord, fading in
            atmos = direction in ("retro", "retrowarm") and os.environ.get("DRUMS", "1") == "0"
            if os.environ.get("ARP_ONLY", "0") == "1": continue   # (the reactive mix's calm layer has it already)
            if t0 + BAR * BEAT > 0:
                if atmos: ev.append(dict(t=0.0, inst="atmopad", note=variant_bed(ch), dur=t0 + BAR * BEAT, vel=0.42, pan=0.0, voice="lead-in", free=True, chord=ch))
                else: ev.append(dict(t=0.0, inst="pad", note=tuple(voicing), dur=t0 + BAR * BEAT, vel=0.22, pan=0.0, voice="lead-in", free=True))
            continue
        if MSTYLE != "tune":
            up = 1 if lead in ("chip", "chipwarm", "glassy") else 0           # bright voices an octave up, like round 2
            o = lambda nt: nt[:-1] + str(int(nt[-1]) + up)
            ar = ARP_UP[ch]
            if VARIANT: ar = variant_arp(ch, bar["i"]); o = lambda nt: nt
            smooth = os.environ.get("SMOOTH", "0") == "1"
            if smooth: lead_i, legato = "chipsmooth", 1.25
            else: lead_i, legato = lead, 1.0
            if bar["i"] == -1:                                      # last bar: the pattern rests, the chord carries into the end card
                if MSTYLE in ("line", "bloom"): note(t0, "airlead", "C5", BAR * BEAT + 1.2, 0.13, 0.0, voice="melody")   # the line lands home
            elif MSTYLE in ("line", "drift", "bloom"):
                if MSTYLE in ("line", "bloom"):
                    for b0, nt, ln in line_bar(ch, bar["i"]):
                        note(t0 + b0 * BEAT, "airlead", nt, ln * BEAT * 0.98, 0.13 if b0 == 0 else 0.10, 0.0, voice="melody")
                if MSTYLE in ("drift", "bloom"):                    # a soft glass pulse, one note a beat, inside the chord
                    ar2 = HIDDEN[(ch, (bar["i"] % 8) // 4)]
                    seq = [ar2[0], ar2[1], ar2[2], ar2[1]] if MSTYLE == "bloom" else [ar2[0], ar2[1], ar2[2], ar2[3]]
                    for j, nt in enumerate(seq):
                        note(t0 + j * BEAT, "glasspulse", nt, 0.6, (0.075 if MSTYLE == "drift" else 0.05) * (1.0 if j == 0 else 0.85), (-0.3, 0.3, -0.15, 0.15)[j], voice="arp")
            elif False:
                for j, nt in enumerate(["C4", "E4", "G4", "C5"]): note(t0 + j * BEAT, lead, o(nt), (4 - j) * BEAT if j == 3 else BEAT, 0.11, -0.1, voice="melody")
            elif MSTYLE == "arp8":
                for j in range(8): note(t0 + j * BEAT / 2, lead, o(ar[j % 4]), BEAT / 2, 0.11 if j % 2 == 0 else 0.08, -0.25 if j % 2 else 0.25, voice="melody")
            elif MSTYLE == "pulse":
                for j in range(4): note(t0 + j * BEAT, lead_i, o(ar[j]), BEAT * 1.1, 0.10 if j == 3 else 0.085, (-0.2, 0.2, -0.1, 0.1)[j], voice="melody")
            elif MSTYLE == "roll":
                for j, ix in enumerate([0, 1, 2, 3, 2, 1, 2, 3]):
                    v = (0.10 if j % 2 == 0 else 0.08) if smooth else (0.11 if j % 2 == 0 else 0.08)
                    if VARIANT == "hidden" and ix == 3: v = 0.115          # the line note leans out a little
                    note(t0 + j * BEAT / 2, lead_i, o(ar[ix]), legato * BEAT / 2, v, -0.25 if j % 2 else 0.25, voice="melody")
            elif MSTYLE == "chords":
                pass                                              # chords only: no arpeggio at all
            elif MSTYLE == "arpquarter":
                for j in range(4): note(t0 + j * BEAT, lead, o(ar[j]), BEAT, 0.10, 0.2, voice="melody")
                for b0, nt, ln in TOPLINE[bar["i"] % 8]: note(t0 + b0 * BEAT, "softglass", nt, ln * BEAT, 0.07, -0.2, voice="top")
        for b0, nt, ln in (bar["phrase"] if MSTYLE == "tune" else []):     # the tune
            if b0 in (0, 2) and nt[0] not in CHORD_NOTES[ch]: raise SystemExit(f"tune: {nt} on a strong beat over {ch} is not a chord note")
            accent = 1.0 if b0 == 0 else 0.88 if b0 == 2 else 0.78        # strong beats a little stronger
            note(t0 + b0 * BEAT, lead, nt, ln * BEAT, (0.20 if lead != "glassy" else 0.13) * accent, -0.1, voice="melody")
        if bar["i"] == -1:
            # The last bar: the home chord starts here and simply keeps ringing through the end card.
            # No new attack when the logo appears, so the hand-over can't be heard.
            if os.environ.get("ARP_ONLY", "0") == "1": continue
            if MSTYLE == "chords" or (direction in ("retro", "retrowarm") and os.environ.get("DRUMS", "1") == "0"):
                note(t0, "atmopad", END_CHORD.get(VARIANT) or variant_bed("C"), length - t0 + 1.5, 0.42); ev[-1]["chord"] = "C"
            else: note(t0, "pad", tuple(voicing), length - t0 + 1.5, 0.30 if direction != "glass" else 0.40)
            note(t0, "sub", bass, length - t0, 0.16)
            continue
        if direction in ("felt", "warmglass"):
            note(t0, "pad", tuple(voicing), BAR * BEAT, 0.20 if direction == "felt" else 0.30)
            for bt in (0, 2): note(t0 + bt * BEAT, "sub", bass, 2 * BEAT, 0.18)
            for j in (1, 3): note(t0 + j * BEAT, "mallet", tones[j], 0.7, 0.07, 0.3, voice="arp")   # two calm notes a bar
            if direction == "warmglass": note(t0 + 3 * BEAT, "softglass", tones[3][:-1] + str(int(tones[3][-1]) + 1), 1.2, 0.04, 0.4, voice="spark")
            if direction == "felt": note(t0, "kick", "C2", 0.3, 0.12)
        elif direction == "glass":
            note(t0, "pad", tuple(voicing) + (voicing[-1][:-1] + str(int(voicing[-1][-1]) + 1),), BAR * BEAT, 0.36)
            note(t0, "sub", bass, BAR * BEAT, 0.15)
            for j in range(4): note(t0 + j * BEAT, "softglass", tones[j][:-1] + str(int(tones[j][-1]) + 1), 0.8, 0.04, -0.35 if j % 2 else 0.35, voice="arp")
        elif direction == "beat":
            note(t0, "pad", tuple(voicing), BAR * BEAT, 0.14)
            for bt in (0, 2): note(t0 + bt * BEAT, "sub", bass, 1.8 * BEAT, 0.26)
            for bt in (0, 2.5): note(t0 + bt * BEAT, "kick", "C2", 0.3, 0.30)
            for bt in (1, 3): note(t0 + bt * BEAT, "rim", "C4", 0.1, 0.34, 0.1)
            for j in range(8): note(t0 + j * BEAT / 2, "hat", "C6", 0.03, 0.16 if j % 2 else 0.24, -0.3)
        elif direction in ("retro", "retrowarm") and os.environ.get("ARP_ONLY", "0") == "1":
            pass                                                  # the rolling layer on its own (for the reactive mix)
        elif direction in ("retro", "retrowarm") and os.environ.get("DRUMS", "0" if False else "1") == "0":
            # Atmospheric: no drums, the bass held for half a bar, a fuller chord underneath
            if MSTYLE in ("chords", "line", "drift", "bloom"):    # chords that melt into each other, voice-led (the 1B bed)
                note(t0, "atmopad", variant_bed(ch), BAR * BEAT, 0.42); ev[-1]["chord"] = ch
                note(t0, "softbass", bass[:-1] + str(int(bass[-1]) + 1), BAR * BEAT * 0.97, 0.15)
                continue
            note(t0, "atmopad", variant_bed(ch), BAR * BEAT, 0.30); ev[-1]["chord"] = ch   # the same held voices as 1B, a little quieter under the tune
            for bt in (0, 2): note(t0 + bt * BEAT, "softbass", bass[:-1] + str(int(bass[-1]) + 1), 1.9 * BEAT, 0.15)
        elif direction in ("retro", "retrowarm"):
            warm = direction == "retrowarm"
            note(t0, "pad", tuple(voicing), BAR * BEAT, 0.13 if warm else 0.10)
            for bt in (0, 1, 2, 3): note(t0 + bt * BEAT, "tribass", bass[:-1] + str(int(bass[-1]) + 1) if bt % 2 == 0 else voicing[1], 0.8 * BEAT, 0.22 if warm else 0.26)
            for bt in (0, 2): note(t0 + bt * BEAT, "kick", "C2", 0.3, 0.12 if warm else 0.14)
            for j in range(4): note(t0 + (j + 0.5) * BEAT, "hat", "C6", 0.03, 0.04 if warm else 0.06, -0.2)
    # The atmospheric chords become four held voices: a note that stays in the next chord is simply
    # held on (voice leading), so chord changes are seamless and nothing restarts on top of itself.
    held = []
    for e in sorted([e for e in ev if e["inst"] == "atmopad"], key=lambda e: e["t"]):
        for k, nt in enumerate(e["note"]):
            prev = next((h for h in held if h["note"] == nt and abs(h["t"] + h["dur"] - e["t"]) < 0.02), None)
            if prev: prev["dur"] = e["t"] + e["dur"] - prev["t"]
            else:
                pan, lv = ATMO_MIX[e["chord"]][k]
                held.append(dict(t=e["t"], inst="atmonote", note=nt, dur=e["dur"], vel=e["vel"] * lv, pan=pan, voice=f"chord{k}", free=e.get("free", False)))
    ev = [e for e in ev if e["inst"] != "atmopad"] + held
    # A sung note that moves to a neighbouring note hands over quickly instead of fading under it
    for a in [e for e in ev if e["inst"] == "airlead"]:
        end = a["t"] + a["dur"]
        if any(b is not a and b["inst"] == "airlead" and end - 0.05 <= b["t"] <= end + 0.5
               and 0.3 < abs(12 * np.log2(n(a["note"]) / n(b["note"]))) < 2.6 for b in ev): a["inst"] = "airlead_q"
    # A held voice that ends while a neighbouring note (within a tone) starts lets go quickly instead
    # of fading over two seconds, so the two never ring together (the beating rule in check_score).
    for h in held:
        end = h["t"] + h["dur"]
        for e in ev:
            if e is h or e["inst"] in ("kick", "rim", "hat") or not (end - 0.05 <= e["t"] <= end + 2.0): continue
            for nm in (e["note"] if isinstance(e["note"], tuple) else (e["note"],)):
                f1, f2 = n(h["note"]), n(nm)
                if 0.3 < abs(12 * np.log2(f1 / f2)) < 2.6 and 15 < abs(f1 - f2) < 120: h["inst"] = "atmonote_q"
    return ev, e0

# ---------------------------------------------------------------- the guardrail: the score must pass before it's played
SCALE_NAMES = {"C", "D", "E", "G", "A"}
POLY_UNUSED = {"melody": 1, "arp": 1, "spark": 1, "pad": 1, "sub": 1, "kick": 1, "rim": 1, "hat": 1, "tribass": 1}
def check_score(ev, e0, length, first_beat):
    """Fails loudly if the music would be messy: notes off the beat grid, two notes doubled on the
    same spot, a voice playing over itself, notes outside the key, or anything but the held chord
    on the end card."""
    problems = []
    grid = BEAT / 4
    for e in ev:
        if e.get("free"): continue                                # the opening's fade-in chord
        off = ((e["t"] - e0) / grid) % 1
        if min(off, 1 - off) * grid > 0.001: problems.append(f"{e['inst']} at {e['t']:.3f}s is off the beat grid")
        names = e["note"] if isinstance(e["note"], tuple) else (e["note"],)
        if any(nm[0] not in SCALE_NAMES for nm in names): problems.append(f"{e['inst']} {names} outside C D E G A")
        if e["t"] >= e0 - 0.001 and e["inst"] not in ("pad", "sub", "atmonote", "atmonote_q", "synthnote", "synthnote_q"): problems.append(f"{e['inst']} plays over the end card at {e['t']:.2f}s")
    seen = set()
    for e in ev:
        key = (e["inst"], e["t"], e["note"])
        if key in seen: problems.append(f"doubled {e['inst']} {e['note']} at {e['t']:.3f}s")
        seen.add(key)
    at = {}
    for e in ev:
        if isinstance(e["note"], str) and e["inst"] not in ("kick", "rim", "hat", "atmonote", "atmonote_q", "airlead", "airlead_q", "glasspulse", "synthnote", "synthnote_q"):   # drums have no pitch; held chord voices sit under everything
            at.setdefault((e["t"], e["note"]), []).append(e["inst"])
    for (t, nt), insts in at.items():
        if len(insts) > 1 and not set(insts) <= {"kick", "rim", "hat"}: problems.append(f"{nt} doubled by {insts} at {t:.3f}s")
    # The tune itself: small steps, chord notes on strong beats, home at the end
    def semis(nt): return 12 * np.log2(n(nt) / C4)
    mel = sorted([e for e in ev if e["voice"] == "melody"], key=lambda e: e["t"])
    for a, b in (zip(mel, mel[1:]) if MSTYLE == "tune" else []):   # arpeggios restart by design
        if abs(semis(b["note"]) - semis(a["note"])) > 7.1: problems.append(f"tune leaps {a['note']}->{b['note']} at {b['t']:.2f}s (more than a fifth)")
    if MSTYLE == "tune" and mel and not mel[-1]["note"].startswith("C"): problems.append("the tune doesn't end on the home note C")
    by_voice = {}
    for e in sorted(ev, key=lambda e: e["t"]): by_voice.setdefault(e["voice"], []).append(e)
    for v, es in by_voice.items():
        for a, b in zip(es, es[1:]):
            if v in ("melody", "pad", "sub", "tribass") and a["t"] + a["dur"] > b["t"] + 0.15 * BEAT and a["inst"] == b["inst"]:
                problems.append(f"{v} overlaps itself at {b['t']:.2f}s")
    # Beating: two pitched notes that sound at the same time within a tone of each other, low enough
    # that they beat 15-120 times a second, are heard as a fuzzy resonance. Not allowed anywhere.
    TAIL = {"synthnote": 1.15, "synthnote_q": 0.09, "glassroll": 0.0, "airlead": 0.92, "airlead_q": 0.08, "glasspulse": 0.0, "softglass": 0.6, "chipsmooth": 0.28, "atmonote": 2.1, "atmonote_q": 0.09, "atmopad": 2.1, "softbass": 0.14, "pad": 1.15, "sub": 0.18}   # to -20 dB
    SWELL = {"synthnote": 0.25, "synthnote_q": 0.25, "airlead": 0.03, "airlead_q": 0.03, "atmonote": 0.35, "atmonote_q": 0.35, "atmopad": 0.35}                 # slow start: under -15 dB this long
    span = []
    for e in ev:
        if e["inst"] in ("kick", "rim", "hat"): continue
        for nm in (e["note"] if isinstance(e["note"], tuple) else (e["note"],)):
            span.append((e["t"], e["t"] + e["dur"] + TAIL.get(e["inst"], 0.1), n(nm), nm, e["inst"], e["t"] + SWELL.get(e["inst"], 0.0)))
    span.sort()
    for a in range(len(span)):
        for b in range(a + 1, len(span)):
            if span[b][0] > span[a][1]: break
            ov = min(span[a][1], span[b][1]) - max(span[a][5], span[b][5])
            f1, f2 = span[a][2], span[b][2]; semis = abs(12 * np.log2(f1 / f2)); beat = abs(f1 - f2)
            if ov > 0.1 and 0.3 < semis < 2.6 and 15 < beat < 120:
                problems.append(f"{span[a][3]} ({span[a][4]}) and {span[b][3]} ({span[b][4]}) beat at {beat:.0f} Hz from {span[b][0]:.2f}s")
    return problems

INSTRUMENTS = {
    "epiano": lambda nt, d, v: epiano(n(nt), d, v),
    "mallet": lambda nt, d, v: mallet(n(nt), d, v),
    "glassy": lambda nt, d, v: glassy(n(nt), max(d, 0.6), v),
    "chip":   lambda nt, d, v: chip(n(nt), max(d, 0.25), v),
    "pad":    lambda nt, d, v: pad([n(x) for x in nt], d, v),
    "sub":    lambda nt, d, v: sub(n(nt), d, v),
    "tribass": lambda nt, d, v: tri_bass(n(nt), d, v),
    "kick":   lambda nt, d, v: kick(v),
    "rim":    lambda nt, d, v: v * load("tactile_space"),
    "hat":    lambda nt, d, v: hat(v),
    "softglass": lambda nt, d, v: softglass(n(nt), max(d, 0.6), v),
    "atmopad": lambda nt, d, v: atmopad([n(x) for x in nt], d, v),
    "softbass": lambda nt, d, v: softbass(n(nt), d, v),
    "chipsmooth": lambda nt, d, v: chipsmooth(n(nt), d, v),
    "atmonote": lambda nt, d, v: atmonote(n(nt), d, v),
    "airlead": lambda nt, d, v: airlead(n(nt), d, v),
    "airlead_q": lambda nt, d, v: airlead(n(nt), d, v, rel=0.035),          # hands over to a neighbouring note, like a singer
    "glasspulse": lambda nt, d, v: softglass(n(nt), 0.6, v),                # finished (-29 dB) before the next beat
    "atmonote_q": lambda nt, d, v: atmonote_q(n(nt), d, v),
    "chipwarm": lambda nt, d, v: chipwarm(n(nt), max(d, 0.3), v),
}

def score(direction, video, cues, length):
    ev, e0 = compose(direction, cues, length)
    problems = check_score(ev, e0, length, None)
    if problems: raise SystemExit(f"Score check failed for {direction}/{video}:\n  " + "\n  ".join(problems[:20]))
    music = np.zeros((2, int(SR * (length + 3)))); bed = np.zeros_like(music)
    for e in ev:
        sig = INSTRUMENTS[e["inst"]](e["note"], e["dur"], e["vel"])
        add(music, sig, e["t"], 1.0, e["pan"])
        if e["inst"] in ("atmonote", "atmonote_q"): add(bed, sig, e["t"], 1.0, e["pan"])
    # Roughness of the sustained chord bed (DIN 45631/A1, in asper): notes too close together beat,
    # which is a fuzzy resonance. (Measured on the bed only: the meter also reacts to the attack of
    # every short note, so the whole mix would read rough even when nothing beats.)
    if np.abs(bed).max() > 0:
        from mosqito.sq_metrics import roughness_dw
        R, _, _, tt = roughness_dw(bed.mean(0)[SR:int(SR * (length - 3))], SR, overlap=0.5)
        if R.max() > 0.25: raise SystemExit(f"{direction}/{video}: the chords beat against each other (roughness {R.max():.2f} asper at {tt[R.argmax()] + 1:.1f}s)")
        print(f"  {direction}/{video}: chord bed roughness at worst {R.max():.2f} asper ({os.environ.get('ATMO_SET', 'warm')} voicings)")
    MUSIC_ONSETS.extend(e["t"] for e in ev)
    if direction == "warmglass": music = lp(music, 8000)                          # just the edge off the top
    if direction == "retrowarm": music = lp(music, 6000)                          # E with just a little more warmth
    # The end card: the held chord and last note fade away slowly (about -18 dB by the end), never just sit
    tt = np.arange(music.shape[1]) / SR
    music *= db(np.interp(tt, [e0, length], [0.0, -18.0], left=0.0, right=-18.0))
    # A tasteful dip under the sound packs: down 4 dB over 0.8 s before, back up over 0.8 s after
    pr = [(s0, e1) for nm, s0, e1 in parts(cues, length) if nm == "packrow"]
    if pr:
        s0, e1 = pr[0]; tt = np.arange(music.shape[1]) / SR
        g = np.interp(tt, [s0 - 0.6, s0 + 0.2, e1 - 0.4, e1 + 0.4], [0, 1, 1, 0])
        music *= db(-4.0 * g)
    return music[:, :int(SR * length)]

# ---------------------------------------------------------------- the end card's lines
END_STYLE = os.environ.get("END_STYLE", "glass")
def end_line(style, nt):
    """One soft, short sound per end card line, on a note of the held C chord. Quiet on purpose."""
    f = n(nt)
    if style == "glass":      # a tiny tuned glass tap, like the Glass pack
        t = T(0.35); s = sum(a * np.sin(2 * np.pi * k * f * t) * np.exp(-t / d) for k, a, d in ((1, 1, .07), (2, .3, .04), (4, .08, .02)))
        s += 0.15 * hp(np.random.default_rng(3).standard_normal(len(t)), 4000) * np.exp(-t / 0.0005)
        return 0.045 * hp(s * np.clip(t / 0.002, 0, 1), 300)
    if style == "thock":      # Thock's own key, the deep satisfying one, with the faintest note under it
        k = load("thock_type") * 0.30; t = T(0.3); s = np.zeros(len(t)); s[:len(k)] += k
        return s + 0.012 * np.sin(2 * np.pi * f * t) * np.exp(-t / 0.08) * np.clip(t / 0.003, 0, 1)
    if style == "felt":       # Felt's soft thud: the quietest, most physical
        k = load("felt_space") * 0.40; t = T(0.3); s = np.zeros(len(t)); s[:len(k)] += k
        return s
    if style == "thockglass": # a Thock key with the glass tap on top: weight and a note together
        a, b = end_line("thock", nt), end_line("glass", nt); m = max(len(a), len(b))
        return 0.8 * (np.pad(a, (0, m - len(a))) + np.pad(b, (0, m - len(b))))
    if style == "wood":       # a wooden mallet tap, like the Chime pack
        t = T(0.3); s = (np.sin(2 * np.pi * f * t) * np.exp(-t / 0.08) + 0.25 * np.sin(2 * np.pi * 4 * f * t) * np.exp(-t / 0.015))
        return 0.05 * hp(s * np.clip(t / 0.002, 0, 1), 200)
    if style == "woodglass":  # the wood tap and the glass tap together, a little quieter each
        a, b = end_line("wood", nt), end_line("glass", nt); m = max(len(a), len(b))
        return 0.7 * (np.pad(a, (0, m - len(a))) + np.pad(b, (0, m - len(b))))
    if style == "pop":        # a round drop that settles onto its note, like the Pop pack
        t = T(0.25); ratio = 1.6 ** np.exp(-t / 0.008); ph = 2 * np.pi * np.cumsum(f * ratio) / SR
        return 0.05 * hp(np.sin(ph) * np.exp(-t / 0.05) * np.clip(t / 0.001, 0, 1), 250)
    if style == "key":        # the app's own Soft key press, with a faint note under it
        k = load("soft_type") * 0.32; t = T(0.3); s = np.zeros(len(t)); s[:len(k)] += k
        return s + 0.02 * np.sin(2 * np.pi * f * t) * np.exp(-t / 0.08) * np.clip(t / 0.003, 0, 1)
    if style == "air":        # a soft breath of air with a pure tone inside: the most modern, least "musical"
        t = T(0.4); rng = np.random.default_rng(5); nz = lp(hp(rng.standard_normal(len(t)), 2500), 7000)
        sh = np.clip(t / 0.03, 0, 1) * np.exp(-t / 0.09)
        return 0.035 * sh * nz + 0.03 * np.sin(2 * np.pi * f * 2 * t) * np.clip(t / 0.01, 0, 1) * np.exp(-t / 0.12)
    raise ValueError(style)

# ---------------------------------------------------------------- sound effects (the app's own sounds)
FX_ONSETS = []
MUSIC_ONSETS = []          # when each music note starts: a note's own start is not a click
ONSET_WINDOW = 0.04        # how long after a note's start its own bright strike may last (0.15 for chimes and plucks)
def effects(video, cues, length):
    FX_ONSETS.clear()
    fx = np.zeros((2, int(SR * (length + 2)))); take = [0]
    def letter():                      # the four letter sounds in turn, like the app
        take[0] = (take[0] + 1) % 4
        return load(["soft_type", "soft_type1", "soft_type2", "soft_type3"][take[0]])
    packs = iter(PACK_ORDER[video]); end_cut = max(t for t, k, x in cues if k == "cut" and x.startswith("outro"))
    sample = [("type", 0.0), ("type1", 0.17), ("space", 0.34), ("type2", 0.52), ("confirm", 0.76)]   # Feedback.sample()
    cuts = sorted(t for t, k, x in cues if k == "cut")
    dropped = []
    def place(sig, at, gain, what, scene_t=None):
        """Rule: a sound must be over before the scene it belongs to fades out (the next cut + 0.1 s).
        If it wouldn't be, it isn't played at all, so nothing is ever cut off mid-sound."""
        ref = at if scene_t is None else scene_t                     # the scene is the one the action happened in
        nxt = next((c for c in cuts if c > ref + 0.01), length)
        end = at + len(sig) / SR
        if end > nxt + 0.1 and end - at > 0.0:
            # find where the sound has truly died away (40 dB down); only that part has to fit
            env = np.abs(sig); thr = env.max() * 0.01; live = np.nonzero(env > thr)[0]
            end = at + (live[-1] + 1) / SR if len(live) else at
            if end > nxt + 0.1: dropped.append(f"{what} at {at:.2f}s"); return
        add(fx, sig, at, gain); FX_ONSETS.append((at, at + sig.shape[-1] / SR))
    for t, kind, text in cues:
        if t >= end_cut - 0.05: continue                  # nothing on the logo or the end card but its lines
        if kind == "name":
            p = next(packs)
            if p:
                for cue, dt in sample: place(load(f"{p}_{cue}"), t + dt, 0.85, f"{p} {cue}", scene_t=t)
        elif kind == "press":
            # the remade videos' shortcuts (31 Oct): each gets the app's own sound for what it does
            if text.startswith("press Y (space)"): place(load("soft_space"), t, 0.85, "Y space")
            elif text.startswith("press X (delete)"): place(load("soft_delete"), t, 0.85, "X delete")
            elif text.startswith("press Menu"): place(load("soft_confirm"), t, 0.9, "Menu send")
            elif text in ("View", "clips", "undo"): place(load("soft_on"), t, 0.9, text)
            elif text == "RBgrow": place(letter(), t, 0.8, "grow selection")
            elif text == "copy": place(load("soft_confirm"), t, 0.9, "copy")
            elif text == "Xhold": place(load("soft_delete"), t, 0.85, "hold X")
            elif text.startswith("let go of A"): place(letter(), t, 0.85, "accent typed")
            elif "hold A" in text: place(load("soft_on"), t, 0.9, "hold A")
            elif "right stick" in text: place(load("soft_confirm"), t, 0.9, "word taken")
            elif "press Y" in text: place(load("soft_on"), t, 0.9, "Y")
            elif "RB" in text: place(load("soft_space"), t, 0.8, "RB")
            else: place(letter(), t, 0.85, "key")
        elif kind == "move" and ("highlight" in text or "slider" in text):
            place(load("soft_move"), t, 0.5, "move")
    # No sound on the logo. The end card's lines each get a soft note from the held C chord
    # (C, D, G, then C an octave up for the link), on their exact frame; the music leaves them room.
    e0 = end_cut + 0.27
    # the remade end card (31 Oct): the logo types itself and the line is typed after it; no effects there,
    # the last chord carries it (the logo rule)
    if dropped: print(f"  {video}: left out so nothing is cut off at a scene change: {', '.join(dropped)}")
    return fx[:, :int(SR * length)]

# ---------------------------------------------------------------- mix and master
def duck(music, fx, depth_db=1.5):
    """Lower the music a little under each effect (fast down, gentle back up)."""
    e = np.abs(fx).max(axis=0); a, r = np.exp(-1 / (SR * 0.02)), np.exp(-1 / (SR * 0.5))
    g = np.zeros_like(e); y = 0.0
    for i, v in enumerate(e):
        y = a * y + (1 - a) * v if v > y else r * y + (1 - r) * v; g[i] = y
    g = np.clip(g / (g.max() + 1e-9) * 3, 0, 1)
    return music * db(-depth_db * g)

def limiter(x, ceiling_db, look_ms=5.0, release_ms=250, return_gain=False):
    """A gentle look-ahead peak limiter. It sees a peak coming 5 ms early, eases the level down
    smoothly before it, and lets go slowly (250 ms), so it never wobbles inside a low note's
    waveform (that wobble is what makes sustained chords crackle)."""
    from scipy.ndimage import minimum_filter1d, uniform_filter1d
    lim = db(ceiling_db); peak = np.abs(x).max(axis=0)
    g = np.minimum(1.0, lim / np.maximum(peak, 1e-9))
    w = int(SR * look_ms / 1000)
    g = minimum_filter1d(g, 2 * w + 1)                            # hold the reduction across the peak
    r = np.exp(-1 / (SR * release_ms / 1000)); out = np.empty_like(g); y = 1.0
    for i, v in enumerate(g):                                     # down at once, back up slowly
        y = v if v < y else r * y + (1 - r) * v; out[i] = y
    out = uniform_filter1d(out, w)                                # round the corners of the gain curve
    out = np.minimum(out, minimum_filter1d(g, 2 * w + 1))         # but never let a peak through
    return (x * out, out) if return_gain else x * out

def master(mix, lufs=-14.0, ceiling=-1.0, max_reduction_db=2.0):
    """Loudness for web video with a clean sound: aim for -14 LUFS, but if that would need the
    limiter to squeeze more than 2 dB anywhere, settle a little quieter instead (down to -16).
    Peaks (true peak, 4x oversampled) never above -1 dB."""
    import pyloudnorm as pyln
    meter = pyln.Meter(SR); mix = hp(mix, 30)
    base = mix * db(-meter.integrated_loudness(mix.T))
    for target in np.arange(lufs, -16.01, -0.5):
        y = base * db(target)
        y2, g = limiter(y, ceiling - 0.3, return_gain=True)
        if 20 * np.log10(g.min()) >= -max_reduction_db: break
    y = y2
    tp = 20 * np.log10(np.abs(resample_poly(y, 4, 1, axis=1)).max())
    if tp > ceiling - 0.05: y *= db(ceiling - 0.1 - tp)
    global LAST_GR; LAST_GR = 20 * np.log10(g.min())
    return y, meter.integrated_loudness(y.T), 20 * np.log10(np.abs(resample_poly(y, 4, 1, axis=1)).max())
LAST_GR = 0.0

def clean_low_end(music):
    """Speakers and headphones crackle when there's more deep bass than they can move. Cut everything
    under 70 Hz (inaudible on most of them anyway) and ease 70-150 Hz down by about 3 dB, so the bass
    is still felt but never pushes the speaker too far."""
    m = hp(music, 70, 4)
    return m - 0.3 * lp(m, 150, 2)

def tame_clicks(music, fx, lufs=-14.0, ceiling=-4.0):
    """The key clicks are the only real peaks. Round off just their first milliseconds on the effects
    bus (so the music is never touched), to -4 dB at the final level. A click is over in a few ms, so
    this can't be heard, and the final limiter then has almost nothing left to do."""
    import pyloudnorm as pyln
    g = db(lufs - pyln.Meter(SR).integrated_loudness((music + fx).T))
    return limiter(fx * g, ceiling, look_ms=0.5, release_ms=25) / g

def soften_fx(fx):
    """A fixed tone control on the key clicks, nothing more: the ear is most sensitive around
    2.5-6 kHz, and that is where a click reads as fuzz over soft music, so that range is eased down
    (about -3 dB) and the top end above 9 kHz is rolled off. No saturation, no limiter: a plain
    filter, which cannot add anything that wasn't there."""
    from scipy.signal import iirpeak, sosfiltfilt, tf2sos
    b, a = iirpeak(4000, 1.0, fs=SR)                              # the 2.5-6 kHz band, isolated
    band = sosfilt(tf2sos(b, a), fx, axis=-1)
    out = fx - 0.3 * band                                         # ease that band down ~3 dB
    return lp(out, 9000, 2)

MUSIC_OVER_FX = 7.0      # the music bed is this many LU louder than the key clicks
def finish(m, fx, length, label, ceiling=-1.0, lufs=-14.0, norm_ref=None):
    """The whole master in one place, with no moving parts:
    1. the music bed is cleaned of deep bass and set MUSIC_OVER_FX louder (in felt loudness) than the clicks;
    2. the key clicks get a fixed tone control (their harshest band eased, the top rolled off);
    3. the two are simply added and the sum is turned to a single fixed volume, chosen so the
       true peak is at the ceiling and the loudness is at most the target;
    4. the end fades to complete silence.
    No limiter, no compressor, no ducking: the final mix is exactly gain x (music + effects), so
    the music can't be dented by a key press. check_audio proves that equation on the result.
    norm_ref: a music track to measure the loudness on instead of m (the reactive mix uses its calm layer)."""
    import pyloudnorm as pyln
    meter = pyln.Meter(SR)
    m = clean_low_end(m); fxr = soften_fx(fx)
    ref = m if norm_ref is None else clean_low_end(norm_ref)
    m = m * db(meter.integrated_loudness(fxr.T) + MUSIC_OVER_FX - meter.integrated_loudness(ref.T))
    mix = m + fxr
    tp = 20 * np.log10(np.abs(resample_poly(mix, 4, 1, axis=1)).max() + 1e-12)
    gain = db(ceiling - 0.1 - tp)
    loud = meter.integrated_loudness((mix * gain).T)
    if loud > lufs: gain *= db(lufs - loud)
    m, fxr = fade_to_silence(m * gain, length), fade_to_silence(fxr * gain, length)
    mix = m + fxr
    check_audio(m, mix, FX_ONSETS, label, parts=(m, fxr))
    return mix, m, fxr, meter.integrated_loudness(mix.T), 20 * np.log10(np.abs(resample_poly(mix, 4, 1, axis=1)).max())

def fade_to_silence(mix, length, seconds=1.8):
    """The video's last moments: the sound fades to complete silence, reaching it just before the end."""
    n = mix.shape[1]; end = min(n, int(SR * (length - 0.08))); start = max(0, end - int(SR * seconds))
    curve = np.cos(np.linspace(0, np.pi / 2, end - start)) ** 2
    mix[:, start:end] *= curve; mix[:, end:] = 0
    return mix

def check_audio(music, mix, fx_onsets, label, parts=None):
    """The guarantee: fails if anything would crackle, click, clip or distort.
    - (parts given) the mix is exactly music + effects, sample for sample: nothing time-varying touched it
    - the music on its own has no clicks at all (a sudden burst of high frequencies that isn't a note)
    - in the final mix, every click happens while a key press or pack sound is playing (that's the sound itself)
    - no sample near full scale, true peak at most -1 dB"""
    from scipy.ndimage import median_filter
    def clicks(x):
        h = hp(x, 3000, 4); w = 48
        e = np.sqrt(np.convolve(h ** 2, np.ones(w) / w, "same"))[::w]
        # a click is a sudden rise: louder than its surroundings AND than what was playing just before
        # (so a steady sound that simply stops, with silence after it, isn't mistaken for one)
        from numpy.lib.stride_tricks import sliding_window_view
        before = np.concatenate([np.full(151, e[:151].mean()), np.median(sliding_window_view(e, 150), axis=1)])[:len(e)]
        r = 20 * np.log10(e / (np.maximum(median_filter(e, size=301), before) + 1e-8) + 1e-12)
        loud = 20 * np.log10(e + 1e-12) > -66                       # loud enough to hear (above -66 dBFS)
        return np.nonzero((r > 10) & loud)[0] * w / SR
    problems = []
    if not (np.isfinite(mix).all() and np.isfinite(music).all()):
        raise SystemExit(f"Audio check failed for {label}:\n  the sound contains invalid values (not a number): a bug, nothing was saved")
    if parts is not None:
        resid = mix - (parts[0] + parts[1])
        if np.abs(resid).max() > 1e-9: problems.append("the mix is not exactly music + effects (something processed it)")
    for ch in (0, 1):
        c = [t for t in clicks(music[ch]) if not any(a - 0.005 <= t <= a + ONSET_WINDOW for a in MUSIC_ONSETS)]
        if len(c): problems.append(f"music click(s) at {', '.join(f'{t:.2f}s' for t in c[:6])}")
        inside = lambda t: any(a - 0.01 <= t <= b for a, b in fx_onsets)
        stray = [t for t in clicks(mix[ch]) if not inside(t) and t > 0.05 and not any(a - 0.005 <= t <= a + ONSET_WINDOW for a in MUSIC_ONSETS)]
        if stray: problems.append(f"click(s) in the mix not from an effect at {', '.join(f'{t:.2f}s' for t in stray[:6])}")
    rough = ""
    if np.abs(mix).max() > 0.98: problems.append("samples near full scale (clipping risk)")
    tp = 20 * np.log10(np.abs(resample_poly(mix, 4, 1, axis=1)).max() + 1e-12)
    if tp > -1.0: problems.append(f"true peak {tp:.1f} dB")
    if problems: raise SystemExit(f"Audio check failed for {label}:\n  " + "\n  ".join(dict.fromkeys(problems)))
    print(f"  {label}: audio check passed (mix = music + effects exactly, no clicks, no clipping, true peak {tp:.1f} dB{rough})")

def save(path, x):
    """16-bit WAV with TPDF dither: a whisper of noise (-96 dB) added before rounding, which is how
    every mastering tool writes 16-bit, so quiet fading notes stay smooth instead of turning grainy."""
    x = np.clip(x, -1, 1); rng = np.random.default_rng(1234)
    d = (rng.random(x.shape) + rng.random(x.shape) - 1.0) / 32768 * (x != 0)   # true silence stays true silence
    with wave.open(path, "wb") as w:
        w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(np.clip(np.round((x + d).T * 32767), -32768, 32767).astype("<i2").tobytes())

def save24(path, x):
    """24-bit WAV, no dither needed (the format's own floor is around -144 dB)."""
    x = np.clip(x, -1, 1); q = np.round(x.T * 8388607).astype("<i4")
    b = np.ascontiguousarray(q).view(np.uint8).reshape(-1, 4)[:, :3].reshape(-1)
    with wave.open(path, "wb") as w:
        w.setnchannels(2); w.setsampwidth(3); w.setframerate(SR); w.writeframes(b.tobytes())

def read_wav(path):
    """Read a 16- or 24-bit stereo WAV as floats, shape (2, n)."""
    w = wave.open(path); n = w.getnframes(); sw = w.getsampwidth(); raw = np.frombuffer(w.readframes(n), np.uint8)
    if sw == 2: return raw.view("<i2").reshape(-1, 2).T / 32768
    b = raw.reshape(-1, 3); v = (b[:, 0].astype(np.int32) | (b[:, 1].astype(np.int32) << 8) | (b[:, 2].astype(np.int32) << 16))
    v = np.where(v >= 1 << 23, v - (1 << 24), v); return v.reshape(-1, 2).T / 8388608

LENGTH = {"teaser": 64.27, "whats-new": 54.07}   # the remade videos (31 Oct 2026)
MUSIC_DB = -6.5          # the bed sits under the key presses

def render(cue_path, direction, out):
    os.makedirs(out, exist_ok=True); cues = read_cues(cue_path)
    for video in ("teaser", "whats-new"):
        L = LENGTH[video]; MUSIC_ONSETS.clear()
        m = score(direction, video, cues[video], L); fx = effects(video, cues[video], L)
        mix, ms, fs, lufs, tp = finish(m, fx, L, f"{direction}/{video}")
        save(os.path.join(out, f"{video}.wav"), mix)
        save(os.path.join(out, f"{video}-music.wav"), ms); save(os.path.join(out, f"{video}-effects.wav"), fs)
        print(f"{direction} {video}: {lufs:.1f} LUFS, true peak {tp:.1f} dBTP, no limiter")

if __name__ == "__main__":
    render(sys.argv[1], sys.argv[2], sys.argv[3])
