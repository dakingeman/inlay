"""Inlay sound packs (1.2; Soft, Thock, Felt and Retro retuned on 31 Oct 2026). Makes every file in app/src/main/res/raw/<pack>_<cue>.wav.

One script for all nine packs, so they share one recipe, one meter and one set of rules
(see docs/brand/SOUND.md). Run from the project folder:

    pip install numpy scipy mosqito
    python tools/sounds.py                 # writes every shipping pack into res/raw
    python tools/soundcheck.py             # measures them; must say ALL PASS

Trying a new pack without touching the app (see "NEW PACKS" below):
    python tools/sounds.py --only mypack --out build/sound-lab
    python tools/soundcheck.py build/sound-lab --only mypack
    python tools/soundboard.py build/sound-lab --only mypack,soft    # opens nowhere: makes build/soundboard.html

Every pack: the same ten cues + three extra letter takes (four letter sounds in all).
Physical packs vary the letter takes in timbre only. Tonal packs rotate the letter through
four notes of C major pentatonic (C D G A: open, never cute). Pitch never varies per play.
"""
import os, wave
import numpy as np
from scipy.signal import butter, sosfilt

SR = 48000
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")

# Felt loudness targets (dB on the small-speaker meter below). Fixed order for every pack:
# move < back < delete < letter = space < Enter = off < select all < on < lock
LEVEL = {'move': -24, 'type': -16, 'space': -16, 'delete': -17, 'on': -14.5, 'off': -15.5,
         'lock': -13.5, 'select_all': -15, 'confirm': -15.5, 'back': -18}
CUES = list(LEVEL)

# ---------------------------------------------------------------- notes (pure ratios)
C4 = 261.63
RATIO = {"C": 1, "D": 9/8, "E": 5/4, "G": 3/2, "A": 5/3}
def note(n, octave): return C4 * RATIO[n] * 2 ** (octave - 4)
SCALE = sorted(C4 * r * 2 ** (o - 4) for r in RATIO.values() for o in range(1, 10))
def bend_curve(t, f, glide, tau):
    """Pitch multiplier over time for a bend of about `glide` semitones. It settles exactly on the
    nearest note of the scale, so a bent sound ends in tune with every other sound."""
    if not glide: return np.ones_like(t)
    aim = f * 2 ** (glide / 12); target = min(SCALE, key=lambda n: abs(np.log2(n / aim)))
    return (target / f) ** (1 - np.exp(-t / tau))

# ---------------------------------------------------------------- building blocks
def _sos(kind, f, order=2):
    return butter(order, f, kind, fs=SR, output='sos')
def lp(x, f, o=2): return sosfilt(_sos('lowpass', min(f, SR * 0.45), o), x)
def hp(x, f, o=2): return sosfilt(_sos('highpass', f, o), x)
def bp(x, f, q=2):
    return sosfilt(_sos('bandpass', [f / (1 + 1 / (2 * q)), min(f * (1 + 1 / (2 * q)), SR * 0.45)]), x)
def T(ms): return np.arange(int(SR * ms / 1000)) / SR
def rise(t, ms=0.6):
    a = np.clip(t / (ms / 1000), 0, 1); return a * a * (3 - 2 * a)
def noise(seed, n): return np.random.default_rng(seed).standard_normal(n)

def resonator(x, f, decay_ms):
    """Two-pole resonator: rings at f and dies away in decay_ms."""
    r = np.exp(-1 / (SR * decay_ms / 1000)); w = 2 * np.pi * f / SR
    a1, a2, g = -2 * r * np.cos(w), r * r, (1 - r * r) / 2
    y = np.zeros_like(x); y1 = y2 = x1 = x2 = 0.0
    for i, xi in enumerate(x):
        yi = g * (xi - x2) - a1 * y1 - a2 * y2
        x2, x1, y2, y1 = x1, xi, y1, yi; y[i] = yi
    return y

def strike(seed, n, ms):
    """A fingertip: noise with a very fast rise and a fast fall."""
    t = np.arange(n) / SR
    return noise(seed, n) * np.exp(-t / (ms / 1000)) * np.clip(t / 0.0003, 0, 1)

def press(*, seed, thud_f=(170, 640), thud=1.0, thud_decay=2.6, click_f=(1900, 2850), click=1.0,
          click_decay=(2.2, 1.4), glint_f=None, glint=0.0, glint_decay=5.0, air=0.08, ms=60):
    """The Soft recipe: a damped low THUD (weight, no pitch) + a crisp CLICK (texture)."""
    rng = np.random.default_rng(seed); n = int(SR * ms / 1000); s = np.zeros(n)
    e = strike(seed, n, 1.2)
    for f, amp in zip(thud_f, (1.0, 0.55)):
        s += thud * amp * resonator(e, f * rng.uniform(0.97, 1.03), thud_decay)
    e = strike(seed + 1000, n, 0.35)
    for f, d, amp in zip(click_f, click_decay, (1.0, 0.6)):
        s += click * amp * resonator(e, f * rng.uniform(0.97, 1.03), d) * 0.9
    if glint_f:
        s += glint * resonator(strike(seed + 2000, n, 0.2), glint_f, glint_decay) * 0.5
    s += air * hp(noise(seed + 3000, n), 5000) * np.exp(-np.arange(n) / SR / 0.0005) * 0.3
    return hp(lp(lp(s, 6500), 6500), 110)

def contact(seed, n, f=2500, amt=0.12, ms=0.6):
    """The tiny touch at the start of a tonal sound, so it feels struck, not beeped."""
    t = np.arange(n) / SR
    return amt * hp(noise(seed, n), f) * np.exp(-t / (ms / 1000))

# ---------------------------------------------------------------- SOFT (default)
def soft_key(seed, weight=1.0, body=(210, 690), click=(1800, 2700), cl=1.0, ms=55, decay=2.6, glint=None):
    return press(seed=seed, thud=weight, thud_f=body, thud_decay=decay, click_f=click, click=cl,
                 glint_f=glint, glint=0.3 if glint else 0, glint_decay=5, ms=ms)

# ---------------------------------------------------------------- THOCK
def thock_key(seed, f=420, weight=1.0, top=1700, topamt=0.7, ms=60, dec=6, knockf=2600):
    """A lubed mechanical key: a hollow case 'tok' + a muffled bottom-out. The tok is tuned to
    a harmonic of the body, so the two parts never beat against each other."""
    t = T(ms); n = len(t)
    e = noise(seed, n) * np.exp(-t / 0.0010) * rise(t, 0.2)
    env = np.exp(-t / (dec / 1000)) * rise(t, 0.6)
    body = weight * (np.sin(2 * np.pi * f * t) + 0.6 * np.sin(2 * np.pi * 2 * f * t) * np.exp(-t / 0.005)
                     + 0.25 * np.sin(2 * np.pi * 3 * f * t) * np.exp(-t / 0.003)) * env
    top = f * max(2, round(top / f))
    return hp(lp(body + topamt * bp(e, top, 4) * 4 + 0.3 * lp(e, knockf), 7000), 150)

# ---------------------------------------------------------------- TACTILE
def tclick(seed, body=0.35, f=(1250, 2300), dec=(1.4, 0.9), bodyf=700, bdec=2.0, ms=32, amt=1.0):
    """A dry switch click: a short contact through two small plastic resonances, almost no body."""
    t = T(ms); n = len(t); e = strike(seed, n, 0.3)
    s = sum(amt * a * resonator(e, fr, d) for fr, d, a in zip(f, dec, (1.0, 0.55)))
    s += body * resonator(strike(seed + 7, n, 0.8), bodyf, bdec)
    return hp(lp(s, 5200, 4), 160)
def TACTILE():
    cues = {
        'move': tclick(1, body=0.2, f=(1200, 1900), dec=(0.6, 0.4), bodyf=760, bdec=0.8, ms=22, amt=0.8),
        'type': tclick(2),
        'space': tclick(3, body=0.6, f=(1050, 1900), bodyf=560, bdec=2.6),
        'delete': tclick(4, body=0.5, f=(1000, 1800), bodyf=600),
        'on': tclick(5, body=0.7, f=(1400, 2500), dec=(1.6, 1.0), bodyf=720, bdec=2.6, ms=40),
        'off': tclick(6, body=0.6, f=(950, 1700), dec=(1.4, 0.9), bodyf=540, ms=40),
        'lock': tclick(7, body=0.9, f=(1300, 2400), dec=(1.8, 1.2), bodyf=600, bdec=3.2, ms=50),
        'select_all': tclick(8, body=0.55, f=(1550, 2700), dec=(1.5, 1.0), bodyf=760, ms=40),
        'confirm': tclick(9, body=0.8, f=(900, 1600), dec=(1.8, 1.2), bodyf=520, bdec=3.0, ms=45),
        'back': tclick(10, body=0.45, f=(850, 1500), dec=(1.2, 0.8), bodyf=480),
    }
    takes = [tclick(40 + i, f=(1250 * (1 + 0.03 * (i - 1)), 2300 * (1 + 0.02 * (i - 1)))) for i in range(3)]
    return cues, takes

# ---------------------------------------------------------------- TYPEWRITER
def tw_strike(seed, weight=1.0, bright=1.0, ms=60):
    return press(seed=seed, thud=0.8 * weight, thud_f=(190, 700), thud_decay=2.2,
                 click_f=(2050 * bright, 3100 * bright), click=1.2, click_decay=(1.6, 1.0),
                 glint_f=4800 * bright, glint=0.12, glint_decay=2.5, air=0.06, ms=ms)
def bell(f, ms=300):
    """A small bell: root + octave + double octave only (no out-of-tune partials)."""
    t = T(ms)
    s = sum(a * np.sin(2 * np.pi * f * k * t) * np.exp(-t / d) for k, a, d in ((1, 1, 0.058), (2, 0.35, 0.034), (4, 0.12, 0.018)))
    return hp(s * rise(t, 1.0), 200)
def TYPEWRITER():
    ding = np.zeros(int(SR * 0.30)); st = tw_strike(19, weight=1.3, bright=0.9); ding[:len(st)] += st
    ding += 0.5 * bell(note("G", 6))
    cues = {
        'move': press(seed=11, thud=0.15, thud_f=(450, 950), thud_decay=1.0, click_f=(1700, 2500),
                      click_decay=(0.9, 0.6), air=0.03, ms=24),
        'type': tw_strike(23),
        'space': tw_strike(13, weight=1.5, bright=0.75),
        'delete': tw_strike(14, weight=1.2, bright=0.85),
        'on': tw_strike(15, weight=1.7, bright=1.0, ms=70),
        'off': tw_strike(16, weight=1.4, bright=0.7, ms=70),
        'lock': tw_strike(28, weight=1.8, bright=1.3, ms=90),
        'select_all': tw_strike(18, weight=1.2, bright=1.05, ms=80),
        'confirm': ding,                                    # the carriage-return bell
        'back': tw_strike(20, weight=1.1, bright=0.6),
    }
    takes = [tw_strike(40 + i, bright=1 + 0.03 * (i - 1)) for i in range(3)]
    return cues, takes

# ---------------------------------------------------------------- SOFT, THOCK, FELT, RETRO: see the retuned recipes below

# ---------------------------------------------------------------- CHIME (wooden mallet)
def mallet(f, dec=30, ms=None, seed=1, soft=1.0):
    """A wooden bar: partials at 1x, 4x and 10x (a marimba's tuning, all whole multiples)."""
    ms = ms or 5 * dec + 18; t = T(ms)
    s = (np.sin(2 * np.pi * f * t) * np.exp(-t / (dec / 1000))
         + 0.30 * soft * np.sin(2 * np.pi * 4 * f * t) * np.exp(-t / (dec * 0.22 / 1000))
         + 0.06 * soft * np.sin(2 * np.pi * 10 * f * t) * np.exp(-t / (dec * 0.07 / 1000)))
    s = s * rise(t, 0.5) + contact(seed, len(t), 1800, 0.10 * soft, 0.7)
    return hp(lp(s, 8000), 180)
def CHIME():
    L = 190
    cues = {
        'move': mallet(note("C", 5), 6, soft=0.4),
        'type': mallet(note("C", 5), 26),
        'space': mallet(note("G", 4), 28),
        'delete': mallet(note("A", 4), 24, soft=0.8),
        'on': mallet(note("G", 5), 34),                       # up a fifth from the letters' home
        'off': mallet(note("C", 5), 30, soft=0.7),            # back down to home, softer
        'lock': mallet(note("C", 6), 50),                     # up an octave, longest
        'select_all': mallet(note("A", 5), 34),
        'confirm': mallet(note("C", 5), 48, ms=L + 70) + 0.6 * mallet(note("G", 5), 40, ms=L + 70, seed=3),
        'back': mallet(note("G", 4), 22, soft=0.6),
    }
    takes = [mallet(note(n, 5), 26, seed=60 + i) for i, n in enumerate(["D", "G", "A"])]
    return cues, takes

# ---------------------------------------------------------------- GLASS
def glass(f, ms=60, dec=13, glide=0.0, tick=0.22, seed=1):
    """Tiny tuned taps on glass: pure partials 1x, 2x, 4x; switches bend a fifth or an octave."""
    ms = max(ms, 5 * dec + 18); t = T(ms)
    bend = bend_curve(t, f, glide, 0.005); ph = 2 * np.pi * np.cumsum(f * bend) / SR
    s = sum(a * np.sin(k * ph) * np.exp(-t / (dec * d / 1000)) for k, a, d in ((1, 1, 1), (2, .35, .6), (4, .12, .35)))
    s = s * rise(t, 0.5) + contact(seed, len(t), 4000, tick, 0.5)
    return hp(lp(s, 9000), 300)
def GLASS():
    cues = {
        'move': glass(note("G", 6), 18, 5, tick=0.15), 'type': glass(note("C", 6)),
        'space': glass(note("G", 5), 70, 22), 'delete': glass(note("A", 5), 55, 15, glide=-2),
        'on': glass(note("C", 6), 90, 28, glide=7), 'off': glass(note("G", 6), 90, 26, glide=-7),
        'lock': glass(note("C", 5), 180, 55, glide=12), 'select_all': glass(note("A", 6), 110, 30),
        'confirm': glass(note("C", 6), 270, 50) + 0.6 * glass(note("G", 6), 270, 40, seed=3),
        'back': glass(note("D", 6), 70, 18, glide=-5),
    }
    takes = [glass(note(n, o), seed=50 + i) for i, (n, o) in enumerate([("D", 6), ("G", 5), ("A", 5)])]
    return cues, takes

# ---------------------------------------------------------------- POP
def pop(f, ms=45, dec=12, sweep=1.8, sweep_ms=6, seed=1, up=False):
    """A round drop that settles onto its note, with a soft contact so it feels touched."""
    ms = max(ms, 5 * dec + 18); t = T(ms); k = np.exp(-t / (sweep_ms / 1000))
    ratio = (1 / sweep) ** k if up else sweep ** k
    ph = 2 * np.pi * np.cumsum(f * ratio) / SR
    s = (np.sin(ph) + 0.18 * np.sin(2 * ph)) * np.exp(-t / (dec / 1000)) * rise(t, 0.8)
    return hp(lp(s + contact(seed, len(t)), 8000), 250)
def POP():
    cues = {
        'move': pop(note("D", 6), 16, 4, 1.4, 3), 'type': pop(note("G", 5)),
        'space': pop(note("C", 5), 55, 16, 2.0, 8), 'delete': pop(note("D", 5), 45, 12, 2.2, 7),
        'on': pop(note("C", 6), 60, 18, 2.0, 10, up=True), 'off': pop(note("G", 5), 60, 16, 2.0, 10),
        'lock': pop(note("C", 6), 160, 28, 1.5, 14, up=True) + 0.5 * pop(note("G", 6), 160, 20, 1.5, 14, up=True),
        'select_all': pop(note("A", 5), 70, 20, 1.6, 8, up=True),
        'confirm': pop(note("C", 5), 170, 30, 1.6, 8) + 0.55 * pop(note("G", 5), 170, 26, 1.6, 8),
        'back': pop(note("A", 4), 55, 14, 1.8, 9),
    }
    takes = [pop(note(n, 5), 45, 12, 1.8, 6, seed=60 + i) for i, n in enumerate(["A", "D", "C"])]
    takes[2] = pop(note("C", 6), 45, 12, 1.8, 6, seed=62)
    return cues, takes

# ---------------------------------------------------------------- RETRO
def blip(f, ms, *, glide=0.0, harm=7, decay=None, click=0.15, seed=1):
    """A band-limited square (odd harmonics only, all exact multiples) with a natural fade."""
    d = decay or ms * 0.35; ms = max(ms, 5 * d + 18); t = T(ms)
    bend = bend_curve(t, f, glide, 0.010); ph = 2 * np.pi * np.cumsum(f * bend) / SR
    s = sum(np.sin(k * ph) / k for k in range(1, harm + 1, 2)) * rise(t, 1.5) * np.exp(-t / (d / 1000))
    s += click * hp(noise(seed, len(t)), 3000) * np.exp(-t / 0.0006)
    return hp(lp(s, 7000), 140)

# ---------------------------------------------------------------- NEW PACKS
# How to add a pack that's as polished as the rest:
#  1. Copy TEMPLATE below, rename it (e.g. WOOD), and change only the recipe function and its knobs.
#     Keep all ten cues and three letter takes, the weight order (move lightest, lock heaviest)
#     and, for tonal packs, notes from note() in C D E G A only (letters rotate C, D, G, A).
#  2. Add it to LAB (not PACKS) and try it:  --only wood --out build/sound-lab, then soundcheck
#     and soundboard. Mastering, the speaker fix and the loudness targets are applied for you.
#  3. When it passes and sounds right on the device, move it from LAB to PACKS, add it to
#     SoundPack in KeyboardSettings.kt and to resourcesFor/takesFor in Feedback.kt, and update
#     docs/brand/SOUND.md, README and CHANGELOG. SoundPacksTest checks the code matches the files.
def TEMPLATE():
    """A starting point: the Soft recipe with every knob named. Weight = thud, brightness = click."""
    def key(seed, weight=1.0, bright=1.0, ms=55):
        return press(seed=seed, thud=weight, thud_f=(210, 690), click_f=(1800 * bright, 2700 * bright), ms=ms)
    cues = {
        'move': press(seed=1, thud=0.12, thud_f=(420, 900), thud_decay=1.2, click_f=(1500, 2200),
                      click_decay=(1.3, 0.9), air=0.0, ms=24),          # tiny: the sound you hear most
        'type': key(2), 'space': key(3, 1.3, 0.85), 'delete': key(4, 1.1, 0.8),
        'on': key(5, 1.5, 1.15, 70), 'off': key(6, 1.3, 0.75, 70),      # on brighter, off duller
        'lock': key(7, 1.8, 1.1, 90), 'select_all': key(8, 1.2, 1.25, 80),
        'confirm': key(9, 1.5, 0.65, 80), 'back': key(10, 1.1, 0.65),  # Enter rounder, back softer
    }
    takes = [key(30 + i, 0.9, 1 + 0.02 * (i - 1)) for i in range(3)]   # small timbre changes only
    return cues, takes

# ---------------------------------------------------------------- the retune: more room between the packs (31 Oct 2026, Emre: yes)
# Measured on the 1.2 letters: Thock and Felt nearly the same (brightness 1270 vs 1350 Hz, 53 vs 54 % body), Soft the
# brightest pack of all (about 4 kHz) though it is the gentle default, Retro no brighter than the wooden Chime.
def SOFT():
    """Soft, warmer: the same thud, the click a little lower and rounder, almost no air."""
    def key(seed, weight=1.0, body=(210, 690), click=(1550, 2350), cl=0.85, ms=55, decay=2.8, glint=None):
        return press(seed=seed, thud=weight, thud_f=body, thud_decay=decay, click_f=click, click=cl,
                     glint_f=glint, glint=0.25 if glint else 0, glint_decay=5, air=0.03, ms=ms)
    cues = {
        'move': press(seed=1, thud=0.14, thud_f=(400, 860), thud_decay=1.2, click_f=(1350, 2000),
                      click_decay=(1.3, 0.9), air=0.0, ms=24),
        'type': key(2, 0.95),
        'space': key(3, 1.3, (190, 600), (1400, 2150), 0.75, decay=3.1),
        'delete': key(4, 1.1, (200, 620), (1300, 1950), 0.75),
        'on': key(5, 1.5, (200, 640), (1900, 2850), 0.95, 70, 3.3),
        'off': key(6, 1.3, (185, 560), (1300, 1900), 0.6, 70, 3.0),
        'lock': key(7, 1.8, (180, 600), (1850, 2750), 0.95, 90, 3.9, glint=3900),
        'select_all': key(8, 1.2, (210, 690), (2050, 3050), 0.9, 80, glint=4700),
        'confirm': press(seed=9, thud=1.5, thud_f=(200, 640), thud_decay=3.4, click_f=(1050, 1700),
                         click=0.8, click_decay=(2.8, 1.8), air=0.02, ms=80),
        'back': press(seed=10, thud=1.1, thud_f=(190, 560), click_f=(1150, 1800), click=0.4, air=0.0, ms=55),
    }
    takes = [key(30 + i, 0.95, (210 * (1 + 0.012 * (i - 1)), 690),
                 (1550 * (1 + 0.025 * (i - 1)), 2350 * (1 + 0.025 * (i - 1)))) for i in range(3)]
    return cues, takes

def THOCK():
    """Thock, deeper and woodier: the case tok a step lower and a touch longer, so it rings like a hollow wooden key."""
    k = lambda seed, f, w, top, topamt=0.7, ms=64, dec=7.5: thock_key(seed, f=f, weight=w, top=top, topamt=topamt, ms=ms, dec=dec, knockf=2100)
    cues = {
        'move': thock_key(1, f=620, weight=0.2, top=2000, topamt=0.6, ms=32, dec=2, knockf=2100),
        'type': k(2, 370, 1.0, 1500), 'space': k(3, 294, 1.3, 1200, dec=9.5, ms=76), 'delete': k(4, 330, 1.1, 1320),
        'on': k(5, 440, 1.4, 1750, dec=9, ms=80), 'off': k(6, 294, 1.25, 1150, 0.55, dec=8.5, ms=76),
        'lock': k(7, 262, 1.8, 1400, 0.75, dec=12, ms=100), 'select_all': k(8, 495, 1.1, 1900, dec=8, ms=74),
        'confirm': k(9, 294, 1.6, 1100, 0.6, dec=12, ms=96), 'back': k(10, 262, 1.0, 1000, 0.35),
    }
    takes = [k(40 + i, 370 * (1 + 0.015 * (i - 1)), 1.0, 1500 * (1 + 0.03 * (i - 1))) for i in range(3)]
    return cues, takes

def FELT():
    """Felt, quieter in colour too: a padded, muffled press with no click to speak of, like typing through a cloth."""
    def pad(seed, weight=1.0, f=(140, 430), ms=55, decay=2.8):
        x = press(seed=seed, thud=weight, thud_f=f, thud_decay=decay, click_f=(650, 950), click=0.07,
                  click_decay=(1.6, 1.1), air=0.0, ms=ms)
        return lp(x, 1500)
    cues = {
        'move': pad(21, 0.45, f=(230, 620), ms=30, decay=1.2),
        'type': pad(22), 'space': pad(23, 1.3, f=(130, 400), decay=3.3), 'delete': pad(24, 1.1, f=(130, 420)),
        'on': pad(25, 1.5, f=(150, 520), ms=64, decay=3.4), 'off': pad(26, 1.3, f=(120, 380), ms=64, decay=3.0),
        'lock': pad(27, 1.8, f=(115, 410), ms=84, decay=4.0), 'select_all': pad(28, 1.3, f=(160, 540), ms=64),
        'confirm': pad(29, 1.5, f=(130, 460), ms=74, decay=3.6), 'back': pad(30, 1.0, f=(115, 360), ms=55),
    }
    takes = [pad(50 + i, f=(140 * (1 + 0.025 * (i - 1)), 430)) for i in range(3)]
    return cues, takes

def RETRO():
    """Retro, brighter and squarer: the letters an octave up with more harmonics, like a handheld's menu blips."""
    b = lambda f, ms, **kw: blip(f, ms, **kw)
    cues = {
        'move': b(note("C", 7), 12, harm=3, decay=3.5, click=0.04),
        'type': b(note("C", 6), 24, harm=9, decay=8), 'space': b(note("G", 5), 32, harm=9, decay=11),
        'delete': b(note("A", 5), 28, glide=-5, harm=9, decay=9),
        'on': b(note("C", 6), 44, glide=7, harm=9, decay=15), 'off': b(note("G", 6), 44, glide=-7, harm=9, decay=15),
        'lock': b(note("C", 6), 70, glide=12, harm=11, decay=26),   # bends up the octave onto C6
        'select_all': b(note("A", 6), 52, glide=5, harm=9, decay=18),
        'confirm': b(note("G", 5), 66, glide=5, harm=9, decay=24), 'back': b(note("C", 6), 48, glide=-12, harm=9, decay=15),
    }
    takes = [b(note(n, 6), 24, harm=9, decay=8, seed=30 + i) for i, n in enumerate(["D", "G", "A"])]
    return cues, takes

# The packs in Settings order, with their level offset (Felt is the quieter night pack)
PACKS = [("soft", SOFT, 0.0), ("thock", THOCK, 0.0), ("tactile", TACTILE, 0.0),
         ("typewriter", TYPEWRITER, 0.0), ("felt", FELT, -3.0), ("chime", CHIME, 0.0),
         ("glass", GLASS, 0.0), ("pop", POP, 0.0), ("retro", RETRO, -1.0)]
# Experiments: made and checked only with --only, never shipped. Add new packs here first.
LAB = [("template", TEMPLATE, 0.0)]

def find(names):
    """Packs by name from PACKS and LAB; all shipping packs when names is empty."""
    if not names: return PACKS
    every = {n: (n, m, o) for n, m, o in PACKS + LAB}
    missing = [n for n in names if n not in every]
    if missing: raise SystemExit(f"Unknown pack(s): {', '.join(missing)}. Add them to PACKS or LAB in tools/sounds.py.")
    return [every[n] for n in names]

# ---------------------------------------------------------------- meter and mastering
def felt(s):
    """Loudness as heard on a small speaker: cut lows, lift presence, RMS over the first 50 ms."""
    x = hp(s, 250); x = x + 0.6 * hp(x, 2500)
    return 20 * np.log10(np.sqrt(np.mean(x[:int(SR * 0.05)] ** 2)) + 1e-9)
def phone(x): return lp(hp(x, 750, 4), 9000)
def master(s, target):
    s = hp(s, 30); s = s - s.mean()                      # no DC offset, so the fade ends at true silence
    fade = int(SR * 0.006); s[-fade:] *= np.linspace(1, 0, fade) ** 2
    s *= 10 ** ((target - felt(s)) / 20)
    pk = np.max(np.abs(s)); lim = 10 ** (-1 / 20)
    return s * lim / pk if pk > lim else s
def tame(s, target):
    """Round off only the first instant of a sharp hit (soft limiter) until the sound reaches
    its target loudness without touching the -1 dB ceiling. Keeps the loudness order."""
    s = np.asarray(s, float); s = s / np.max(np.abs(s))
    for _ in range(14):
        m = master(s, target)
        if felt(m) >= target - 0.3: return m
        s = np.tanh(s * 1.6) / np.tanh(1.6)
    return master(s, target)
def presence(x):
    """Overtones of the body that a small speaker can play, so its weight survives."""
    g = x / np.max(np.abs(x))
    return sosfilt(_sos('bandpass', [650, 2200]), np.tanh(4 * g)) * np.max(np.abs(x))
def speakerproof(sounds):
    """No sound may lose much more on a phone speaker than the rest of its pack."""
    drops = {k: felt(phone(s)) - felt(s) for k, s in sounds.items()}
    ref = max(np.median(list(drops.values())), -3.0)
    out = {}
    for k, s in sounds.items():
        a, y = 0.0, s
        while felt(phone(y)) - felt(y) < ref - 0.4 and a < 8:
            a += 0.15; y = s + a * presence(s)
        out[k] = y
    return out

def save(path, s):
    with wave.open(path, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(s, -1, 1) * 32767).astype('<i2').tobytes())

def build(out=OUT, packs=None):
    os.makedirs(out, exist_ok=True)
    for name, make, offset in (packs or PACKS):
        cues, takes = make()
        allc = dict(cues); allc.update({f'type{i}': s for i, s in enumerate(takes, 1)})
        allc = speakerproof(allc)
        for k, s in allc.items():
            base = 'type' if k.startswith('type') else k
            save(os.path.join(out, f"{name}_{k}.wav"), tame(s, LEVEL[base] + offset))
        print(f"{name}: {len(allc)} files")

if __name__ == "__main__":
    import argparse
    ap = argparse.ArgumentParser(description="Make Inlay's sound packs")
    ap.add_argument("--only", default="", help="comma-separated pack names (PACKS or LAB)")
    ap.add_argument("--out", default=OUT, help="folder for the files (default: res/raw)")
    a = ap.parse_args()
    names = [n for n in a.only.split(",") if n]
    if a.out == OUT and any(n in dict((p[0], 1) for p in LAB) for n in names):
        raise SystemExit("Lab packs go to a try-out folder, not into the app: add --out build/sound-lab")
    build(a.out, find(names))
