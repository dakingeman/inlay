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
# ---------------------------------------------------------------- 1.3 RETRO THEME PACKS (lab, 5 Nov 2026)
# Each retro theme gets its own pack. Homages by material and era only: nothing copies a console's own sounds.
# Two directions per theme; Emre picks one each on the soundboard.

def both(a, b, k):
    """Two notes together (a chord), the shorter one padded with silence so they line up."""
    n = max(len(a), len(b)); return np.pad(a, (0, n - len(a))) + k * np.pad(b, (0, n - len(b)))

def brick_key(seed, weight=1.0, hollow=820, ms=58, dull=1.0):
    """Grey brick A, Rubber pad: a grey plastic button on a rubber dome. A soft low squash, a hollow plastic
    'tok' in the shell, and only a dull contact: no crisp click, so it sits between Soft and Felt."""
    x = press(seed=seed, thud=weight, thud_f=(150, 330), thud_decay=2.4, click_f=(950 * dull, 1350 * dull),
              click=0.55, click_decay=(2.0, 1.4), air=0.0, ms=ms)
    t = T(ms); n = len(t)
    x += 0.32 * weight * resonator(strike(seed + 50, n, 0.9), hollow, 7)
    return lp(x, 3600)
def BRICK_RUBBER():
    k = brick_key
    cues = {
        'move': press(seed=1, thud=0.16, thud_f=(330, 700), thud_decay=1.1, click_f=(900, 1250), click=0.35,
                      click_decay=(1.1, 0.8), air=0.0, ms=26),
        'type': k(2), 'space': k(3, 1.3, 700, 64), 'delete': k(4, 1.1, 740),
        'on': k(5, 1.5, 980, 70, 1.15), 'off': k(6, 1.3, 660, 70, 0.85),
        'lock': k(7, 1.8, 880, 92, 1.1), 'select_all': k(8, 1.2, 1040, 74, 1.2),
        'confirm': k(9, 1.5, 620, 82, 0.8), 'back': k(10, 1.05, 600, 56, 0.75),
    }
    takes = [k(30 + i, 1.0, 820 * (1 + 0.04 * (i - 1)), 58, 1 + 0.03 * (i - 1)) for i in range(3)]
    return cues, takes

def disc(f, ms=70, dec=22, bright=1.0, glide=0.0, seed=1, att=1.5):
    """Grey brick B, Disc menu: a soft digital pluck from the CD era. A sine with its 2nd and 3rd partials that fade
    first (like a small FM piano), a slow-ish 3 ms start and a soft contact. Warm, a little dreamy, no buzz."""
    ms = max(ms, 5 * dec + 18); t = T(ms)
    bend = bend_curve(t, f, glide, 0.008); ph = 2 * np.pi * np.cumsum(f * bend) / SR
    s = (np.sin(ph) * np.exp(-t / (dec / 1000))
         + 0.42 * bright * np.sin(2 * ph) * np.exp(-t / (dec * 0.35 / 1000))
         + 0.16 * bright * np.sin(3 * ph) * np.exp(-t / (dec * 0.18 / 1000)))
    s = s * rise(t, att) + contact(seed, len(t), 1600, 0.09, 0.8)
    return hp(lp(s, 6000), 160)
def BRICK_DISC():
    cues = {
        'move': disc(note("G", 4), 18, 4, 0.5),
        'type': disc(note("C", 4)), 'space': disc(note("G", 3), 80, 26), 'delete': disc(note("A", 3), 70, 20, glide=-2),
        'on': disc(note("C", 4), 100, 30, 1.2, glide=7), 'off': disc(note("G", 4), 100, 28, 0.8, glide=-7),
        'lock': disc(note("C", 4), 180, 46, 1.7, glide=12, att=0.8), 'select_all': disc(note("A", 4), 110, 30, 1.3),
        'confirm': both(disc(note("C", 4), 260, 50, 0.9, att=0.6), disc(note("G", 4), 260, 30, 0.8, seed=3, att=0.6), 0.4),
        'back': disc(note("D", 4), 80, 20, 0.7, glide=-5),
    }
    takes = [disc(note(n, 4), seed=60 + i) for i, n in enumerate(["D", "G", "A"])]
    return cues, takes

def bwip(f, ms=50, dec=13, start=0.62, settle_ms=7, seed=1, body=0.5):
    """Indigo A, Bounce: a round rubbery 'bwip' that rises onto its note (Pop falls onto its note, from above), an
    octave lower than Pop, with a little woody 2nd partial. Playful and bouncy, never squeaky."""
    ms = max(ms, 5 * dec + 18); t = T(ms); k = np.exp(-t / (settle_ms / 1000))
    ratio = start ** k; ph = 2 * np.pi * np.cumsum(f * ratio) / SR
    s = (np.sin(ph) + body * np.sin(2 * ph) * np.exp(-t / (dec * 0.4 / 1000))) * np.exp(-t / (dec / 1000)) * rise(t, 1.0)
    return hp(lp(s + contact(seed, len(t), 1500, 0.10, 0.7), 6000), 150)
def INDIGO_BOUNCE():
    cues = {
        'move': bwip(note("G", 5), 16, 4, 0.8, 3, body=0.1),
        'type': bwip(note("C", 5)), 'space': bwip(note("G", 4), 60, 17), 'delete': bwip(note("A", 4), 50, 13, 1.25),
        'on': bwip(note("C", 5), 70, 20, 0.5, 11), 'off': bwip(note("G", 5), 70, 18, 1.35, 10),
        'lock': both(bwip(note("C", 5), 170, 30, 0.5, 15), bwip(note("G", 5), 170, 24, 0.5, 15, seed=3), 0.45),
        'select_all': bwip(note("A", 5), 80, 20, 0.6, 9),
        'confirm': both(bwip(note("C", 5), 190, 34, 0.75, 9), bwip(note("G", 5), 190, 28, 0.75, 9, seed=4), 0.55),
        'back': bwip(note("D", 5), 60, 14, 1.3, 9),
    }
    takes = [bwip(note(n, 5), seed=60 + i) for i, n in enumerate(["D", "G", "A"])]
    return cues, takes

def tine(f, ms=80, dec=18, seed=1, glide=0.0, plink=1.0):
    """Indigo B, Tine: a little plucked metal tine (a thumb piano or a music box), partials 1x and 3x only, a bright
    pluck that settles warm. Round and cheerful, between the wooden Chime and the high Glass."""
    ms = max(ms, 5 * dec + 18); t = T(ms)
    bend = bend_curve(t, f, glide, 0.006); ph = 2 * np.pi * np.cumsum(f * bend) / SR
    s = np.sin(ph) * np.exp(-t / (dec / 1000)) + 0.34 * plink * np.sin(3 * ph) * np.exp(-t / (dec * 0.3 / 1000))
    s = s * rise(t, 0.8) + contact(seed, len(t), 2200, 0.12 * plink, 0.5)
    return hp(lp(s, 7500), 200)
def INDIGO_TINE():
    cues = {
        'move': tine(note("C", 6), 18, 4, plink=0.4),
        'type': tine(note("G", 5)), 'space': tine(note("C", 5), 90, 28), 'delete': tine(note("D", 5), 80, 24, glide=-2),
        'on': tine(note("G", 5), 110, 34, glide=7), 'off': tine(note("D", 6), 110, 30, glide=-7, plink=0.7),
        'lock': tine(note("C", 5), 190, 50, glide=12), 'select_all': tine(note("A", 5), 120, 34),
        'confirm': both(tine(note("C", 5), 270, 52), tine(note("G", 5), 270, 44, seed=3), 0.55),
        'back': tine(note("E", 5), 80, 22, glide=-4, plink=0.6),
    }
    takes = [tine(note(n, o), seed=60 + i) for i, (n, o) in enumerate([("A", 5), ("D", 6), ("C", 6)])]
    return cues, takes

def breath(f, ms=70, dec=20, seed=1, glide=0.0, air=1.0, att=1.7):
    """Gloss white A, Breeze: a pure, airy note with a soft breath on top, like a calm glossy menu. A plain sine and a
    whisper of its octave, a gentle 3 ms start, the breath band-passed low enough to stay soft."""
    ms = max(ms, 5 * dec + 18); t = T(ms)
    bend = bend_curve(t, f, glide, 0.008); ph = 2 * np.pi * np.cumsum(f * bend) / SR
    s = (np.sin(ph) + 0.12 * np.sin(2 * ph) * np.exp(-t / (dec * 0.5 / 1000))) * np.exp(-t / (dec / 1000)) * rise(t, att)
    s += 0.05 * air * bp(noise(seed + 9, len(t)), 2600, 1.2) * np.exp(-t / (dec * 0.6 / 1000)) * rise(t, 1.5)
    return hp(lp(s + contact(seed, len(t), 2000, 0.07, 0.6), 7000), 220)
def WHITE_BREEZE():
    cues = {
        'move': breath(note("G", 5), 18, 4, air=0.3),
        'type': breath(note("C", 6)), 'space': breath(note("C", 6), 80, 26), 'delete': breath(note("D", 6), 70, 20, glide=-2),
        'on': breath(note("G", 6), 100, 30, glide=5), 'off': breath(note("C", 7), 100, 28, glide=-5),
        'lock': breath(note("C", 6), 180, 46, glide=12), 'select_all': breath(note("A", 6), 110, 30),
        'confirm': both(breath(note("C", 6), 260, 50, att=0.6), breath(note("G", 6), 260, 30, seed=3, att=0.6), 0.38),
        'back': breath(note("G", 6), 80, 20, glide=-5, air=0.7),
    }
    takes = [breath(note(n, 6), seed=60 + i) for i, n in enumerate(["D", "G", "A"])]
    return cues, takes

def shell(seed, weight=1.0, f=(1700, 2600), ping=None, body=0.4, ms=40):
    """Gloss white B, Gloss: a light tap on a glossy plastic shell. Two small, smooth plastic resonances, a little
    rounded body and, on the special keys, a faint tuned ping. Cleaner and rounder than Tactile's dry switch."""
    t = T(ms); n = len(t); e = strike(seed, n, 0.4)
    x = sum(a * resonator(e, fr, d) for fr, d, a in zip(f, (1.8, 1.1), (1.0, 0.45)))
    x += body * weight * resonator(strike(seed + 7, n, 0.9), 520, 2.4)
    if ping: x += 0.10 * np.sin(2 * np.pi * ping * t) * np.exp(-t / 0.012) * rise(t, 0.6)
    return hp(lp(x, 5000, 4), 180)
def WHITE_GLOSS():
    cues = {
        'move': shell(1, 0.5, (1500, 2200), body=0.2, ms=22),
        'type': shell(2), 'space': shell(3, 1.3, (1450, 2250), body=0.6, ms=48), 'delete': shell(4, 1.1, (1400, 2100), body=0.5),
        'on': shell(5, 1.4, (1900, 2850), note("G", 6), 0.6, 60), 'off': shell(6, 1.2, (1350, 2050), note("C", 6), 0.55, 60),
        'lock': shell(7, 1.8, (1750, 2700), note("C", 7), 0.8, 80), 'select_all': shell(8, 1.2, (2000, 2900), note("A", 6), 0.5, 60),
        'confirm': shell(9, 1.5, (1300, 2000), note("C", 6), 0.75, 70), 'back': shell(10, 1.0, (1250, 1900), body=0.45),
    }
    takes = [shell(40 + i, 1.0, (1700 * (1 + 0.03 * (i - 1)), 2600 * (1 + 0.02 * (i - 1)))) for i in range(3)]
    return cues, takes

# ---------------------------------------------------------------- round 2: closer to each era's sound design (5 Nov 2026)
# Built from the same ingredients those consoles' menus used (no samples, no melodies): PS1 dark rooms and
# crystal shimmer, GameCube round bubbly toys, Wii soft clean piano clicks.

def room(x, ms=70, amt=0.18, seed=5, tone=3000):
    """A small dark room after the hit: dense, smooth and short, so it never sounds like a second hit."""
    n = len(x); t = np.arange(n) / SR
    ir = lp(noise(seed, n), tone) * np.exp(-t / (ms / 1000 / 4.6)) * np.clip(t / 0.004, 0, 1)
    wet = np.convolve(x, ir)[:n]; wet /= (np.max(np.abs(wet)) + 1e-9) / (np.max(np.abs(x)) + 1e-9)
    return x + amt * wet

def psx_dark(f, ms=110, dec=24, seed=1, glide=0.0, air=1.0, att=1.2):
    """Grey brick C, Memory card: a dark, soft synth pluck in a small room, the late-90s menu feel. Low and round,
    the top rolled off at 2.4 kHz, a breath of room behind it."""
    t = T(ms); bend = bend_curve(t, f, glide, 0.008); ph = 2 * np.pi * np.cumsum(f * bend) / SR
    s = (np.sin(ph) + 0.35 * np.sin(2 * ph) * np.exp(-t / (dec * 0.4 / 1000))) * np.exp(-t / (dec / 1000)) * rise(t, att)
    s = s + contact(seed, len(t), 1400, 0.08, 0.8)
    if air > 0: s = room(s, 80, 0.22 * air, seed + 3, 2400)
    return hp(lp(s, 2400), 150) * np.clip((t[-1] - t) / 0.006, 0, 1)
def PSX_MEMORY():
    d = psx_dark
    cues = {
        'move': d(note("G", 5), 46, 5, air=0.0),
        'type': d(note("C", 5)), 'space': d(note("G", 4), 130, 28), 'delete': d(note("A", 4), 120, 22, glide=-2),
        'on': d(note("C", 5), 160, 32, glide=7), 'off': d(note("G", 5), 160, 30, glide=-7),
        'lock': d(note("C", 5), 220, 48, glide=12), 'select_all': d(note("A", 5), 170, 32),
        'confirm': both(d(note("C", 5), 280, 50, att=0.5), d(note("G", 5), 280, 34, seed=3, att=0.5), 0.4),
        'back': d(note("D", 5), 130, 22, glide=-5, air=0.45),
    }
    takes = [d(note(n, 5), seed=60 + i) for i, n in enumerate(["D", "G", "A"])]
    return cues, takes

def crystal(f, ms=110, dec=22, seed=1, glide=0.0, sub=1.0, wet=0.16):
    """Grey brick D, Crystal: a clear glassy bell (partials 1x, 2x, 4x) over a soft low body an octave and a half
    down, with a little room: the shimmer-over-depth of the era's start-up screens, shrunk to a key press."""
    t = T(ms); bend = bend_curve(t, f, glide, 0.006); ph = 2 * np.pi * np.cumsum(f * bend) / SR
    bell = sum(a * np.sin(k * ph) * np.exp(-t / (dec * d_ / 1000)) for k, a, d_ in ((1, 1, 1), (2, .3, .5), (4, .1, .25)))
    low = 0.5 * sub * np.sin(ph / 4) * np.exp(-t / (dec * 0.5 / 1000))
    s = (bell + low) * rise(t, 0.9) + contact(seed, len(t), 2600, 0.10, 0.5)
    if wet > 0: s = room(s, 70, wet, seed + 3, 4000)
    return hp(lp(s, 7000), 140) * np.clip((t[-1] - t) / 0.006, 0, 1)
def PSX_CRYSTAL():
    c = crystal
    cues = {
        'move': c(note("G", 6), 46, 5, sub=0.0, wet=0.0),
        'type': c(note("C", 6)), 'space': c(note("G", 5), 130, 26), 'delete': c(note("A", 5), 120, 20, glide=-2),
        'on': c(note("C", 6), 160, 30, glide=7), 'off': c(note("G", 6), 160, 28, glide=-7),
        'lock': c(note("C", 6), 220, 46, glide=12), 'select_all': c(note("A", 6), 170, 30),
        'confirm': both(c(note("C", 6), 280, 48), c(note("G", 6), 280, 30, seed=3, sub=0.0), 0.4),
        'back': c(note("D", 6), 130, 20, glide=-5),
    }
    takes = [c(note(n, 6), seed=60 + i) for i, n in enumerate(["D", "G", "A"])]
    return cues, takes

def bloop(f, ms=60, dec=14, seed=1, up=0.5, sweep_ms=10, body=0.25):
    """Indigo C, Bubble: a round, bubbly 'bloop' that swoops up an octave onto its note, quick and cheerful,
    like a toy-box menu. A pure sine with a touch of its octave, a soft rubbery contact."""
    t = T(max(ms, 5 * dec + 18)); k = np.exp(-t / (sweep_ms / 1000)); ph = 2 * np.pi * np.cumsum(f * up ** k) / SR
    s = (np.sin(ph) + body * np.sin(2 * ph) * np.exp(-t / (dec * 0.5 / 1000))) * np.exp(-t / (dec / 1000)) * rise(t, 1.0)
    return hp(lp(s + contact(seed, len(t), 1300, 0.09, 0.8), 6500), 160)
def CUBE_BUBBLE():
    b = bloop
    cues = {
        'move': b(note("G", 5), 18, 4, up=0.8, sweep_ms=3, body=0.1),
        'type': b(note("C", 6)), 'space': b(note("G", 5), 70, 18, up=0.7, sweep_ms=3), 'delete': b(note("A", 5), 60, 14, up=1.5),
        'on': b(note("C", 6), 80, 22, sweep_ms=7), 'off': b(note("D", 6), 80, 20, up=1.5, sweep_ms=8),
        'lock': both(b(note("C", 6), 170, 30, sweep_ms=7), b(note("G", 6), 170, 24, sweep_ms=7, seed=3), 0.45),
        'select_all': b(note("A", 6), 90, 22, sweep_ms=12),
        'confirm': both(b(note("C", 6), 190, 34, up=0.75), b(note("G", 6), 190, 28, up=0.75, seed=4), 0.55),
        'back': b(note("D", 6), 70, 16, up=1.5),
    }
    takes = [b(note(n, 6), seed=60 + i) for i, n in enumerate(["D", "G", "A"])]
    return cues, takes

def toybox(f, ms=90, dec=20, seed=1, glide=0.0):
    """Indigo D, Toy box: a bright, hollow plastic-and-wood 'tonk', like a toy xylophone in a cartoon menu.
    Partials 1x, 2x and 3x for the hollow toy tone, a pitch that drops a hair onto its note, a firm tap."""
    t = T(max(ms, 5 * dec + 18)); bend = bend_curve(t, f, glide, 0.006) * (1 + 0.04 * np.exp(-t / 0.004))
    ph = 2 * np.pi * np.cumsum(f * bend) / SR
    s = (np.sin(ph) * np.exp(-t / (dec / 1000)) + 0.5 * np.sin(2 * ph) * np.exp(-t / (dec * 0.35 / 1000))
         + 0.25 * np.sin(3 * ph) * np.exp(-t / (dec * 0.2 / 1000))) * rise(t, 0.6)
    return hp(lp(s + contact(seed, len(t), 2000, 0.13, 0.5), 7000), 200)
def CUBE_TOYBOX():
    y = toybox
    cues = {
        'move': y(note("C", 6), 18, 4),
        'type': y(note("G", 5)), 'space': y(note("C", 5), 90, 26), 'delete': y(note("D", 5), 80, 20, glide=-2),
        'on': y(note("G", 5), 110, 30, glide=7), 'off': y(note("D", 6), 110, 28, glide=-7),
        'lock': y(note("C", 5), 190, 46, glide=12), 'select_all': y(note("A", 5), 120, 30),
        'confirm': both(y(note("C", 5), 260, 48), y(note("G", 5), 260, 36, seed=3), 0.5),
        'back': y(note("E", 5), 80, 20, glide=-4),
    }
    takes = [y(note(n, o), seed=60 + i) for i, (n, o) in enumerate([("A", 5), ("D", 6), ("C", 6)])]
    return cues, takes

def ping(f, ms=110, dec=26, seed=1, glide=0.0, soft=1.0, wet=0.20):
    """Gloss white C, Cursor: a soft, clean, rounded ping with a gentle room, the calm hover tick of a living-room
    menu. A sine and a whisper of its octave, a soft start, almost no contact."""
    t = T(ms); bend = bend_curve(t, f, glide, 0.008); ph = 2 * np.pi * np.cumsum(f * bend) / SR
    s = (np.sin(ph) + 0.15 * np.sin(2 * ph) * np.exp(-t / (dec * 0.4 / 1000))) * np.exp(-t / (dec / 1000)) * rise(t, 1.4)
    s = s + contact(seed, len(t), 2400, 0.05 * soft, 0.5)
    if wet > 0: s = room(s, 90, wet, seed + 3, 5000)
    return hp(lp(s, 7000), 250) * np.clip((t[-1] - t) / 0.006, 0, 1)
def WII_CURSOR():
    q = ping
    cues = {
        'move': q(note("A", 6), 46, 5, soft=0.5, wet=0.0),
        'type': q(note("C", 6)), 'space': q(note("G", 5), 130, 28), 'delete': q(note("A", 5), 120, 22, glide=-2),
        'on': q(note("G", 6), 160, 30, glide=5), 'off': q(note("C", 6), 160, 28, glide=-5),
        'lock': q(note("C", 6), 220, 46, glide=12), 'select_all': q(note("A", 6), 170, 30),
        'confirm': both(q(note("C", 6), 280, 50), q(note("G", 6), 280, 32, seed=3), 0.38),
        'back': q(note("G", 5), 130, 22, glide=-5),
    }
    takes = [q(note(n, 6), seed=60 + i) for i, n in enumerate(["D", "G", "A"])]
    return cues, takes

def epiano(f, ms=150, dec=28, seed=1, glide=0.0, hammer=1.0, wet=0.15, att=1.0):
    """Gloss white D, Soft piano: a soft felt-piano note with a little room, the gentle piano of a calm settings
    menu. Partials 1x to 4x, the higher ones fading first, a soft felt hammer."""
    t = T(ms); bend = bend_curve(t, f, glide, 0.008); ph = 2 * np.pi * np.cumsum(f * bend) / SR
    s = sum(a * np.sin(k * ph) * np.exp(-t / (dec * d_ / 1000)) for k, a, d_ in ((1, 1, 1), (2, .4, .55), (3, .18, .35), (4, .08, .22)))
    s = s * rise(t, att) + 0.6 * hammer * lp(contact(seed, len(t), 600, 0.2, 1.2), 1800)
    if wet > 0: s = room(s, 60, wet, seed + 3, 4500)
    return hp(lp(s, 6500), 160) * np.clip((t[-1] - t) / 0.006, 0, 1)
def WII_PIANO():
    e = epiano
    cues = {
        'move': e(note("G", 5), 46, 5, hammer=0.4, wet=0.0),
        'type': e(note("C", 5)), 'space': e(note("G", 4), 140, 30), 'delete': e(note("A", 4), 120, 24, glide=-2),
        'on': e(note("C", 5), 170, 34, glide=7), 'off': e(note("G", 5), 170, 30, glide=-7),
        'lock': e(note("C", 5), 230, 50, glide=12), 'select_all': e(note("A", 5), 180, 32),
        'confirm': both(e(note("C", 5), 290, 52, att=0.2, hammer=0.5), e(note("G", 5), 290, 36, seed=3, att=0.2, hammer=0.5), 0.45),
        'back': e(note("D", 5), 130, 24, glide=-5),
    }
    takes = [e(note(n, 5), seed=60 + i) for i, n in enumerate(["D", "G", "A"])]
    return cues, takes

# ---------------------------------------------------------------- round 3: homages, as close to each console's sound as we can (5 Nov 2026)
# Emre: "instantly recognisable if blind hearing it". Close in timbre, intervals, room and the shape of each UI sound;
# no start-up tune or menu music copied note for note.

def lofi(x, rate=22050):
    """The PS1's samples: 22 kHz playback, so the top rolls off near 10 kHz, and a faint grain."""
    return lp(lp(x, rate * 0.45, 4), rate * 0.45, 2)
def hall(x, ms=150, amt=0.32, seed=11, tone=5200):
    """The PS1 sound chip's reverb, shrunk to fit a key press: lush and smooth, fading well inside the cue's length.
    It starts with the sound (no build-up), so it can never swell into a second hit."""
    n = len(x); t = np.arange(n) / SR
    ir = lp(noise(seed, n), tone) * np.exp(-t / (ms / 1000 / 4.6)); ir[0] = 0
    wet = np.convolve(x, ir)[:n]; wet *= (np.max(np.abs(x)) + 1e-9) / (np.max(np.abs(wet)) + 1e-9)
    return x + amt * wet
def spu_bell(f, ms=120, dec=26, seed=1, glide=0.0, wet=0.16, detune=0.0):
    """Grey brick E, SPU bells: the short, fast-fading sampled bells of the late-90s grey console, two voices a few
    cents apart (the chip's stereo width, summed), through its lush reverb and 22 kHz top end."""
    t = T(ms); bend = bend_curve(t, f, glide, 0.006)
    def v(c): ph = 2 * np.pi * np.cumsum(f * 2 ** (c / 1200) * bend) / SR; return sum(a * np.sin(k * ph) * np.exp(-t / (dec * d / 1000)) for k, a, d in ((1, 1, 1), (2, .45, .5), (3, .16, .3), (4, .08, .2)))
    s = (0.5 * v(-detune) + 0.5 * v(detune)) * rise(t, 0.7) + contact(seed, len(t), 3000, 0.08, 0.4)
    if wet > 0: s = hall(s, 140, wet, seed + 3)
    return hp(lp(lofi(s), 7000), 150) * np.clip((t[-1] - t) / 0.006, 0, 1)
def PSX_SPU():
    b = spu_bell
    cues = {
        'move': b(note("G", 6), 46, 8, wet=0.0),
        'type': b(note("C", 6)), 'space': b(note("G", 5), 150, 35), 'delete': b(note("D", 6), 130, 25, glide=-5),
        'on': b(note("G", 5), 170, 35, glide=5, wet=0.1), 'off': b(note("D", 6), 170, 35, glide=-5, wet=0.1),          # fourths and fifths
        'lock': b(note("C", 6), 240, 54, glide=12, wet=0.1), 'select_all': b(note("A", 6), 180, 38, wet=0.1),
        'confirm': both(b(note("C", 6), 290, 57), b(note("G", 6), 290, 41, seed=3), 0.45),
        'back': b(note("G", 5), 140, 28, glide=-5, wet=0.22),
    }
    takes = [b(note(n, o), 145, seed=60 + i) for i, (n, o) in enumerate([("D", 6), ("G", 5), ("A", 5)])]
    return cues, takes

def sweep_pad(f, ms=140, dec=34, seed=1, glide=0.0, open_=1.0, wet=0.16, att=2.2):
    """Grey brick F, Spacious: a breathy synth pluck whose filter opens and closes, in the style of the big digital
    pads of the time, through the same lush reverb. Band-limited saw (whole harmonics only), a soft 2.5 ms start."""
    t = T(ms); bend = bend_curve(t, f, glide, 0.008); ph = 2 * np.pi * np.cumsum(f * bend) / SR
    cut = f * (1.5 + 5 * open_ * np.exp(-t / (dec * 0.5 / 1000)))
    s = sum(np.sin(k * ph) / k * (1 / (1 + (k * f / cut) ** 4)) for k in range(1, 9))
    s = s * np.exp(-t / (dec / 1000)) * rise(t, att) + 0.05 * bp(noise(seed + 2, len(t)), 3000, 1.0) * np.exp(-t / 0.01)
    if wet > 0: s = hall(s, 150, wet, seed + 3, 4500)
    return hp(lofi(s), 150) * np.clip((t[-1] - t) / 0.006, 0, 1)
def PSX_SWEEP():
    w = sweep_pad
    cues = {
        'move': w(note("G", 5), 46, 6, open_=0.4, wet=0.0),
        'type': w(note("C", 5)), 'space': w(note("G", 4), 150, 34), 'delete': w(note("A", 4), 140, 28, glide=-2, wet=0.08),
        'on': w(note("C", 5), 180, 36, glide=7), 'off': w(note("G", 5), 180, 34, glide=-7, open_=0.7),
        'lock': w(note("C", 5), 250, 50, glide=12), 'select_all': w(note("A", 5), 190, 36),
        'confirm': both(w(note("C", 5), 290, 52, att=0.6, open_=1.3), w(note("G", 5), 290, 30, seed=3, att=0.6, open_=0.5), 0.28),
        'back': w(note("D", 5), 150, 26, glide=-5, open_=0.4, wet=0.0),
    }
    takes = [w(note(n, 5), seed=60 + i) for i, n in enumerate(["D", "G", "A"])]
    return cues, takes

def cube_plink(f, ms=70, dec=14, flick=0.75, flick_ms=1.2, seed=1, wet=0.08):
    """Indigo E, Cube menu: a tiny, very bright 'plink' that flicks up onto its note in a couple of milliseconds,
    clean and glassy, with a little space around it: the purple cube console's menu cursor."""
    t = T(max(ms, 5 * dec + 18)); k = np.exp(-t / (flick_ms / 1000)); ph = 2 * np.pi * np.cumsum(f * flick ** k) / SR
    s = (np.sin(ph) + 0.25 * np.sin(2 * ph) * np.exp(-t / (dec * 0.4 / 1000))) * np.exp(-t / (dec / 1000)) * rise(t, 0.5)
    s = s + contact(seed, len(t), 3500, 0.07, 0.4)
    if wet > 0: s = room(s, 80, wet, seed + 3, 6000)
    return hp(lp(s, 9000), 300) * np.clip((t[-1] - t) / 0.005, 0, 1)
def CUBE_MENU():
    p_ = cube_plink
    cues = {
        'move': p_(note("A", 6), 30, 4, 0.85, 1.5, wet=0.0),
        'type': p_(note("D", 6)), 'space': p_(note("G", 5), 90, 16), 'delete': p_(note("A", 5), 80, 14, 1.3, wet=0.0),
        'on': p_(note("G", 6), 110, 18, 0.66, 5), 'off': p_(note("C", 6), 110, 16, 1.5, 5),
        'lock': both(p_(note("C", 6), 190, 28, 0.5, 3), p_(note("G", 6), 190, 22, 0.5, 3, seed=3), 0.5),
        'select_all': p_(note("A", 6), 120, 18, 0.66, 5),
        'confirm': both(p_(note("C", 6), 220, 30, 0.75, 3), p_(note("G", 6), 220, 24, 0.75, 3, seed=4), 0.6),
        'back': p_(note("D", 6), 90, 14, 1.4, 3, wet=0.0),
    }
    takes = [p_(note(n, o), seed=60 + i) for i, (n, o) in enumerate([("C", 6), ("G", 6), ("A", 6)])]
    return cues, takes

def fm_bell(f, ms=120, dec=26, seed=1, glide=0.0, index=2.2, wet=0.14):
    """Indigo F, Cube chime: a round, rubbery FM bell (carrier and modulator 1:1, so every overtone is a whole
    multiple) whose brightness falls away as it sounds, with a quick upward bounce: the cube console's select chime."""
    t = T(max(ms, 5 * dec + 18)); bend = bend_curve(t, f, glide, 0.006) * (1 - 0.06 * np.exp(-t / 0.006))
    ph = 2 * np.pi * np.cumsum(f * bend) / SR; idx = index * np.exp(-t / (dec * 0.35 / 1000))
    s = np.sin(ph + idx * np.sin(ph)) * np.exp(-t / (dec / 1000)) * rise(t, 0.8) + contact(seed, len(t), 2200, 0.09, 0.5)
    if wet > 0: s = room(s, 90, wet, seed + 3, 5500)
    return hp(lp(s, 8000), 200) * np.clip((t[-1] - t) / 0.006, 0, 1)
def CUBE_CHIME():
    c = fm_bell
    cues = {
        'move': c(note("G", 6), 40, 4, index=0.8, wet=0.0),
        'type': c(note("C", 6)), 'space': c(note("G", 5), 140, 26), 'delete': c(note("A", 5), 120, 20, glide=-2),
        'on': c(note("C", 6), 170, 30, glide=7, index=2.8), 'off': c(note("G", 6), 170, 28, glide=-7, index=1.6),
        'lock': c(note("C", 6), 240, 44, glide=12, index=3.0), 'select_all': c(note("A", 6), 180, 30, index=1.8),
        'confirm': both(c(note("C", 6), 290, 46), c(note("G", 6), 290, 32, seed=3, index=1.4), 0.45),
        'back': c(note("D", 6), 140, 20, glide=-5, index=1.4),
    }
    takes = [c(note(n, o), seed=60 + i, index=1.4) for i, (n, o) in enumerate([("D", 6), ("G", 5), ("A", 5)])]
    return cues, takes

def wii_tick(seed, f=2000, body=0.5, ms=40, wet=0.0):
    """Gloss white E (the hover): a soft, muted little tick, like a rounded plastic woodblock under a cloth."""
    t = T(ms); n = len(t); e = strike(seed, n, 0.5)
    x = resonator(e, f, 1.6) + 0.28 * resonator(e, f * 1.5, 1.0) + body * resonator(strike(seed + 7, n, 1.0), 620, 2.2)
    if wet > 0: x = room(x, 60, wet, seed + 3, 5000)
    return hp(lp(x, 4800, 4), 220) * np.clip((t[-1] - t) / 0.004, 0, 1)
def wii_blip(f, ms=130, dec=22, seed=1, glide=0.0, wet=0.18):
    """Gloss white E (the presses): a clean, soft bell blip with a gentle room, the white console's 'select'."""
    t = T(ms); bend = bend_curve(t, f, glide, 0.007); ph = 2 * np.pi * np.cumsum(f * bend) / SR
    s = (np.sin(ph) + 0.3 * np.sin(2 * ph) * np.exp(-t / (dec * 0.4 / 1000)) + 0.08 * np.sin(4 * ph) * np.exp(-t / (dec * 0.2 / 1000)))
    s = s * np.exp(-t / (dec / 1000)) * rise(t, 1.0) + contact(seed, len(t), 2600, 0.06, 0.5)
    if wet > 0: s = room(s, 100, wet, seed + 3, 6000)
    return hp(lp(s, 8000), 250) * np.clip((t[-1] - t) / 0.006, 0, 1)
def WII_CHANNEL():
    """Ticks for moving and letters (the cursor gliding over channels), soft bell blips for everything that switches."""
    k, b = wii_tick, wii_blip
    cues = {
        'move': k(1, 2300, 0.25, 26, wet=0.0),
        'type': k(2), 'space': k(3, 1800, 0.8, 40), 'delete': k(4, 1700, 0.7, 36),
        'on': b(note("G", 6), 150, 22, glide=5), 'off': b(note("D", 6), 150, 20, glide=-5, wet=0.08),
        'lock': b(note("C", 6), 230, 36, glide=12), 'select_all': b(note("A", 6), 170, 24),
        'confirm': both(b(note("C", 6), 280, 38), b(note("G", 6), 280, 26, seed=3), 0.4),
        'back': b(note("G", 5), 140, 20, glide=-5, wet=0.12),
    }
    takes = [k(40 + i, 1850 * (1 + 0.04 * (i - 1)), 0.5) for i in range(3)]
    return cues, takes

def whistle(f, ms=120, dec=24, seed=1, glide=0.0, breath=1.0, wet=0.12):
    """Gloss white F, Plaza: a soft, friendly whistle that slides up a hair onto its note, breath and all, like the
    little whistles of the white console's character plaza."""
    t = T(ms); bend = bend_curve(t, f, glide, 0.008) * (1 - 0.05 * np.exp(-t / 0.010)); ph = 2 * np.pi * np.cumsum(f * bend) / SR
    s = (np.sin(ph) + 0.06 * np.sin(2 * ph)) * np.exp(-t / (dec / 1000)) * rise(t, 2.0)
    s += 0.07 * breath * bp(noise(seed + 9, len(t)), f * 2, 1.5) * np.exp(-t / (dec * 0.5 / 1000)) * rise(t, 1.2)
    s += contact(seed, len(t), 2400, 0.05, 0.5)
    if wet > 0: s = room(s, 90, wet, seed + 3, 6000)
    return hp(lp(s, 7500), 300) * np.clip((t[-1] - t) / 0.006, 0, 1)
def WII_PLAZA():
    w = whistle
    cues = {
        'move': w(note("G", 6), 46, 5, breath=0.3, wet=0.0),
        'type': w(note("C", 6)), 'space': w(note("G", 5), 140, 28), 'delete': w(note("A", 5), 120, 22, glide=-2),
        'on': w(note("C", 6), 170, 30, glide=7), 'off': w(note("G", 6), 170, 28, glide=-7),
        'lock': w(note("C", 6), 240, 44, glide=12), 'select_all': w(note("A", 6), 180, 30),
        'confirm': both(w(note("C", 6), 290, 46), w(note("G", 6), 290, 32, seed=3), 0.4),
        'back': w(note("D", 6), 140, 22, glide=-5),
    }
    takes = [w(note(n, 6), seed=60 + i) for i, n in enumerate(["D", "G", "A"])]
    return cues, takes

# ---------------------------------------------------------------- the original black-and-green console, matched to its real menu sounds (5 Nov 2026)
# Measured on the dashboard's own files (reference only, never shipped): the scroll beep is a 28 ms metallic tick near
# 2.6 kHz; select sits on A4, slightly flat (Inlay's pure A is flat by the same amount); back near F4 (not in our scale:
# moved to E4/G4); about half of every sound is metallic hiss. Their select swells in over ~110 ms; here every sound
# starts at once and the swell lives in the hiss's brightness instead, so typing never lags.

def hiss_sweep(seed, n, f0, f1, ms, amt):
    """Metallic hiss whose band sweeps from f0 to f1: the dashboard's 'whoosh', at full strength from the start."""
    t = np.arange(n) / SR; x = noise(seed, n); y = np.zeros(n); step = 240
    lo, hi = sorted((f0, f1)); bank = {f: bp(x, f, 1.1) for f in np.geomspace(lo, hi, 8)}
    for i in range(0, n, step):
        f = f0 * (f1 / f0) ** min(1.0, t[i] / (ms / 1000)); near = min(bank, key=lambda b: abs(np.log(b / f)))
        y[i:i + step] = bank[near][i:i + step]
    return amt * lp(y, 5000) * np.exp(-t / (ms / 1000 / 3.0))
def dash(f, ms=120, dec=30, seed=1, glide=0.0, hiss=1.0, sweep=(1800, 4200), ring=1.0):
    """A green-lit machine tone: the note with whole-multiple partials and a faint metallic ring (an octave and a fifth
    up), plus the sweeping hiss. Starts crisp; the hiss carries the swell."""
    t = T(ms); bend = bend_curve(t, f, glide, 0.010); ph = 2 * np.pi * np.cumsum(f * bend) / SR
    tone = (np.sin(ph) + 0.45 * np.sin(2 * ph) * np.exp(-t / (dec * 0.6 / 1000)) + 0.25 * ring * np.sin(3 * ph) * np.exp(-t / (dec * 0.5 / 1000))
            + 0.12 * ring * np.sin(6 * ph) * np.exp(-t / (dec * 0.3 / 1000))) * np.exp(-t / (dec / 1000)) * rise(t, 0.15)
    s = tone + hiss_sweep(seed, len(t), sweep[0], sweep[1], ms * 0.7, 0.30 * hiss) * rise(t, 0.3) + contact(seed, len(t), 3000, 0.14, 0.4)
    return hp(lp(s, 7500), 140) * np.clip((t[-1] - t) / 0.006, 0, 1)
def scroll_tick(seed, f=note("G", 6), ms=30, amt=1.0):
    """The scroll beep: a tiny metallic tick, a ringing resonance on E7 with a burst of hiss."""
    t = T(ms); n = len(t)
    x = resonator(strike(seed, n, 0.3), f, 2.2) + 0.25 * resonator(strike(seed + 1, n, 0.3), f * 1.5, 1.2)
    x += 0.12 * lp(hp(noise(seed + 2, n), 2500), 4500) * np.exp(-t / 0.0015) * rise(t, 0.3)
    return amt * hp(lp(x, 2900, 4), 500) * rise(t, 0.2) * np.clip((t[-1] - t) / 0.004, 0, 1)
def key_stroke(seed, f=note("G", 6), body=0.6, ms=48):
    """The dashboard keyboard stroke: a bright, hissy plastic click with a touch of metallic ring."""
    t = T(ms); n = len(t); e = strike(seed, n, 0.5)
    x = resonator(e, f, 1.4) + 0.3 * resonator(e, f * 1.5, 0.9) + body * resonator(strike(seed + 7, n, 0.8), f / 2, 2.0)
    x += 0.08 * bp(noise(seed + 3, n), f * 1.5, 4.0) * np.exp(-t / 0.004)
    return hp(lp(x, 3300, 4), 220) * np.clip((t[-1] - t) / 0.004, 0, 1)
def XBOX_DASH():
    d = dash
    cues = {
        'move': scroll_tick(1, note("D", 6)),
        'type': key_stroke(2), 'space': key_stroke(3, note("C", 6), 0.9, 56), 'delete': d(note("D", 5), 140, 24, glide=-5, sweep=(4200, 1400)),
        'on': d(note("A", 4), 180, 34, glide=7, sweep=(1600, 4800)), 'off': d(note("E", 5), 180, 30, glide=-7, sweep=(4200, 1500)),
        'lock': d(note("E", 5), 260, 48, glide=12, sweep=(1400, 4600), hiss=1.4, ring=1.6),
        'select_all': d(note("E", 5), 190, 32, sweep=(2400, 4800), hiss=0.35, ring=1.4),
        'confirm': both(d(note("A", 4), 290, 50, sweep=(1500, 4600)), d(note("E", 5), 290, 36, seed=3, hiss=0.0), 0.4),   # the A select
        'back': d(note("G", 4), 160, 28, glide=-3, sweep=(3800, 1500), hiss=0.45),                                    # their back sits near F4
    }
    takes = [key_stroke(40 + i, note(n, o), 0.6) for i, (n, o) in enumerate([("E", 6), ("D", 6), ("C", 6)])]
    return cues, takes

# ---------------------------------------------------------------- black-and-green, take 2: the real sounds' own gestures, cleaned up (5 Nov 2026)
# From the dashboard's spectrograms: the keyboard stroke is a laser 'zwip' (up to ~8.5 kHz in 30 ms, then down);
# the scroll beep a two-tone buzzy beep (3:1, square-like); select/back low buzzy saw tones; menu forward/back a
# stuttering chatter about every 45 ms; memory select a quick falling staircase. Kept: those gestures. Cleaned up:
# shorter, softer top, every sound starts at once, and the chatter flickers the colour, never the loudness.

def path_phase(points, n):
    """A pitch path through (ms, Hz) points, smooth in log-frequency, as a continuous phase."""
    t = np.arange(n) / SR * 1000; ms, hz = zip(*points)
    f = np.exp(np.interp(t, ms, np.log(hz))); return 2 * np.pi * np.cumsum(f) / SR, f
def saw_on(ph, f, harm=12, top=5000):
    """Band-limited saw on a phase: whole-multiple partials only, rolled off above [top] (no aliasing)."""
    return sum(np.sin(k * ph) / k * np.clip((top - k * f) / (0.3 * top), 0, 1) for k in range(1, harm + 1))
def flicker(x, seed, rate_ms=45, freqs=(1800, 2600, 3600), amt=0.35):
    """The dashboard's chatter as colour: the band boosted jumps every [rate_ms], loudness untouched."""
    n = len(x); step = int(SR * rate_ms / 1000); bands = [bp(x, f, 2.0) for f in freqs]; y = np.zeros(n)
    bands = [b * (np.sqrt(np.mean(bands[0] ** 2)) / (np.sqrt(np.mean(b ** 2)) + 1e-12)) for b in bands]
    order = np.random.default_rng(seed).permutation(len(freqs))
    for j, i in enumerate(range(0, n, step)): y[i:i + step] = bands[order[j % len(freqs)]][i:i + step]
    k = np.convolve(np.ones(96) / 96, y, "same")   # soften each jump so it never clicks
    return x + amt * (y - 0 * k)
def zwip(seed, top=note("E", 7), land=note("G", 5), ms=60, up_ms=7, harm=6):
    """The keyboard stroke: a quick laser 'zwip' up to [top] and down onto [land], one breath, starting at once."""
    n = int(SR * ms / 1000); t = np.arange(n) / SR
    ph, f = path_phase([(0, land * 1.5), (up_ms, top), (ms * 0.36, land), (ms, land)], n)
    s = saw_on(ph, f, harm, 4200) * np.exp(-t / (ms / 1000 / 4.6)) * rise(t, 0.4)
    s += 0.12 * hp(noise(seed, n), 3000) * np.exp(-t / 0.002)
    return hp(lp(s, 4500, 4), 300) * np.clip((t[-1] - t) / 0.005, 0, 1)
def beep2(f=note("E", 6), ms=32, amt=1.0):
    """The scroll beep: two tones 3:1, square-like, tiny."""
    t = T(ms); s = (np.sin(2 * np.pi * f * t) + 0.18 * np.sin(2 * np.pi * 3 * f * t)) * np.exp(-t / 0.0045) * rise(t, 0.4)
    return amt * hp(lp(s, 4500), 600) * np.clip((t[-1] - t) / 0.004, 0, 1)
def buzz(f, ms=160, dec=40, seed=1, glide=0.0, top=3600, chatter=0.0, harm=8):
    """Select and back: a low buzzy saw tone through a soft filter, the dashboard's 'bzzt', optionally chattering."""
    t = T(ms); bend = bend_curve(t, f, glide, 0.012); ph = 2 * np.pi * np.cumsum(f * bend) / SR
    s = saw_on(ph, f * bend, harm, top) * np.exp(-t / (dec / 1000)) * rise(t, 0.3) + contact(seed, len(t), 2600, 0.12, 0.5)
    if chatter: s = flicker(s, seed, 45, (1600, 2400, 3400), chatter)
    return hp(lp(s, 5000, 4), 120) * np.clip((t[-1] - t) / 0.006, 0, 1)
def stairs(notes, ms=150, seed=1, top=4200):
    """Memory select: a quick falling staircase of tones in one breath (the pitch steps, the sound never restarts)."""
    n = int(SR * ms / 1000); t = np.arange(n) / SR; k = len(notes); seg = ms * 0.6 / k
    pts = []; [pts.extend([(i * seg, fr), ((i + 1) * seg - 0.8, fr)]) for i, fr in enumerate(notes)]; pts.append((ms, notes[-1]))
    ph, f = path_phase(pts, n)
    s = saw_on(ph, f, 5, top) * np.exp(-t / (ms / 1000 / 4.4)) * rise(t, 0.4) + contact(seed, n, 3000, 0.1, 0.4)
    return hp(lp(s, 5000, 4), 200) * np.clip((t[-1] - t) / 0.006, 0, 1)
def XBOX_GLOW():
    cues = {
        'move': beep2(),
        'type': zwip(2), 'space': zwip(3, note("C", 7), note("C", 5), 72, 8),
        'delete': zwip(4, note("G", 6), note("D", 5), 70, 5),
        'on': buzz(note("A", 4), 190, 44, glide=12, chatter=0.35, top=4200), 'off': buzz(note("A", 5), 190, 40, glide=-12, chatter=0.3, top=3200),
        'lock': buzz(note("A", 4), 270, 60, glide=12, top=4800),
        'select_all': stairs([note("A", 6), note("E", 6), note("D", 6), note("A", 5)], 170),
        'confirm': buzz(note("A", 4), 290, 46, top=4200, harm=6),                     # the A select: one buzzy tone, like the real one   # the A select
        'back': buzz(note("G", 4), 160, 26, top=2600, harm=5),                                                      # the B back, low
    }
    takes = [zwip(40 + i, note(a, b), note(c, d)) for i, (a, b, c, d) in enumerate([("D", 7), ("G", 6), ("A", 6)] and [("D", 7, "A", 5), ("G", 6, "D", 5), ("A", 6, "E", 5)])]
    return cues, takes

# ---------------------------------------------------------------- round 5: matched to the consoles' own sound files (5 Nov 2026, reference only)

def patina(x, rate=22050, bits=11, floor=0.0, seed=91):
    """A tasteful touch of the console era: its playback bandwidth, a little low-bit grain (dithered, so it's a soft
    grain, never a buzz) and, optionally, a faint noise floor that rides the sound. Premium, not broken."""
    y = lp(lp(x, rate * 0.45, 4), rate * 0.45, 2)
    peak = np.max(np.abs(y)) + 1e-9; q = 2 ** (bits - 1)
    rng = np.random.default_rng(seed)
    d = (rng.uniform(-0.5, 0.5, len(y)) + rng.uniform(-0.5, 0.5, len(y))) / q
    y = np.round((y / peak + d) * q) / q * peak
    if floor > 0:
        env = np.convolve(np.abs(y), np.ones(240) / 240, "same")
        y = y + floor * env * lp(noise(seed + 1, len(y)), 6000)
    return y
def aged(recipe, rate, bits=11, floor=0.0):
    """The same pack with patina on every cue."""
    def make():
        cues, takes = recipe()
        return ({k: patina(v, rate, bits, floor) for k, v in cues.items()}, [patina(v, rate, bits, floor) for v in takes])
    return make

PS1_BELL = ((1.0, 0.0, 1.0), (1.13, -28.0, 0.5), (1.63, -11.7, 0.55), (1.99, -19.6, 0.45), (2.64, -8.1, 0.4),
            (3.63, -11.8, 0.3), (4.27, -11.6, 0.25), (5.26, -17.5, 0.2))   # the system bell's first note: ratio, level, decay share
def bios_bell(f, ms=130, dec=30, seed=1, glide=0.0, wet=0.16, bright=1.0):
    """Grey brick G, BIOS: the grey console's own system bell, partial for partial (measured from its sample), played
    back at its 11 kHz, cut short like its menu sounds, through its reverb. The overtones are the bell's own, not whole
    multiples: a deliberate exception for this homage, kept quiet enough never to sour."""
    t = T(ms); bend = bend_curve(t, f, glide, 0.006); ph = 2 * np.pi * np.cumsum(f * bend) / SR
    s = sum(10 ** (lv / 20) * (bright if r > 1.5 else 1) * np.sin(r * ph) * np.exp(-t / (dec * d / 1000)) for r, lv, d in PS1_BELL)
    s = s * rise(t, 0.5) + contact(seed, len(t), 2500, 0.06, 0.4)
    if wet > 0: s = hall(s, 130, wet, seed + 3, 4000)
    return hp(lp(lp(s, 5000, 4), 5000, 2), 150) * np.clip((t[-1] - t) / 0.006, 0, 1)
def bios_swoosh(seed, up=True, ms=110, lo=900, hi=3200, amt=0.45):
    """The system swoosh, shrunk: a band of noise sliding up (or down), starting at full strength."""
    return hiss_sweep(seed, int(SR * ms / 1000), lo if up else hi, hi if up else lo, ms * 0.8, amt)
def bios_pad(f, ms=250, dec=70, seed=1, close=(4200, 700)):
    """The big start-up pad, as a pluck: a full row of whole-multiple overtones whose filter closes as it sounds."""
    t = T(ms); ph = 2 * np.pi * f * t
    cut = close[0] * (close[1] / close[0]) ** np.clip(t / (ms / 1000), 0, 1)
    s = sum(np.sin(k * ph) / k * 1 / (1 + (k * f / cut) ** 4) for k in range(1, 16)) * np.exp(-t / (dec / 1000)) * rise(t, 0.8)
    s = hall(s + contact(seed, len(t), 2000, 0.06, 0.5), 130, 0.08, seed + 3, 3500)
    return hp(lp(s, 5000, 4), 120) * np.clip((t[-1] - t) / 0.006, 0, 1)
def PSX_BIOS():
    b = bios_bell
    def plus(a, x, k=1.0):
        n = max(len(a), len(x)); return np.pad(a, (0, n - len(a))) + k * np.pad(x, (0, n - len(x)))
    cues = {
        'move': b(note("G", 6), 46, 7, wet=0.0, bright=0.5),
        'type': b(note("C", 6)), 'space': b(note("G", 5), 150, 34), 'delete': b(note("A", 5), 130, 24, wet=0.08),
        'on': plus(b(note("G", 5), 170, 32, glide=5, wet=0.1), bios_swoosh(5, True), 0.35),
        'off': plus(b(note("D", 6), 170, 30, glide=-5, wet=0.1), bios_swoosh(6, False), 0.3),
        'lock': plus(b(note("C", 6), 240, 46, glide=12, wet=0.1), bios_swoosh(7, True, 160, 700, 3800), 0.4),
        'select_all': b(note("A", 6), 180, 34, wet=0.1),
        'confirm': bios_pad(note("C", 5), 290, 70),
        'back': bios_pad(note("G", 4), 160, 34, close=(2600, 600)),
    }
    takes = [b(note(n, o), 145, seed=60 + i, wet=0.1) for i, (n, o) in enumerate([("D", 6), ("G", 5), ("A", 5)])]
    return cues, takes

def ipl_pluck(f, ms=80, dec=16, seed=1, glide=0.0, click=1.0, high=1.0):
    """Indigo G, Start-up: the purple cube console's start-up palette: a short pluck whose sharp click lands with a
    bright overtone eight times up (a toy marimba), and a soft second. Measured from its start-up sound, not its tune."""
    t = T(max(ms, 5 * dec + 18)); bend = bend_curve(t, f, glide, 0.006); ph = 2 * np.pi * np.cumsum(f * bend) / SR
    s = (np.sin(ph) * np.exp(-t / (dec / 1000)) + 0.3 * np.sin(2 * ph) * np.exp(-t / (dec * 0.5 / 1000))
         + 0.32 * high * np.sin(8 * ph) * np.exp(-t / (dec * 0.12 / 1000))) * rise(t, 0.4)
    s += 0.22 * click * hp(noise(seed, len(t)), 2500) * np.exp(-t / 0.0012)
    return hp(lp(s, 7000), 180) * np.clip((t[-1] - t) / 0.005, 0, 1)
def CUBE_IPL():
    q = ipl_pluck
    cues = {
        'move': q(note("G", 5), 30, 5, click=0.4, high=0.4),
        'type': q(note("C", 5)), 'space': q(note("G", 4), 110, 22), 'delete': q(note("A", 4), 90, 16, glide=-2),
        'on': q(note("C", 5), 130, 26, glide=7), 'off': q(note("G", 5), 130, 24, glide=-7, high=0.7),
        'lock': q(note("C", 5), 220, 40, glide=12), 'select_all': q(note("A", 5), 140, 26),
        'confirm': both(q(note("C", 5), 260, 44), q(note("G", 5), 260, 30, seed=3, high=0.5), 0.45),
        'back': q(note("D", 5), 100, 18, glide=-5, high=0.6),
    }
    takes = [q(note(n, 5), seed=60 + i) for i, n in enumerate(["D", "G", "A"])]
    return cues, takes

def zwip3(seed, peak=6200, land=note("D", 4), scale=0.65, harsh=0.0, early=False):
    """Take 3/4 keyboard stroke: the real stroke's pitch path (up to its peak in ~20 ms, then down to ~300 Hz), as a
    clean whistle with a little hiss; [scale] shrinks its length, [harsh] adds back the real one's roughness."""
    path = ([(0, 1500), (12, peak), (20, peak * 0.6), (30, 1400), (38, land), (110, land)] if early else
            [(0, 1800), (16, peak), (30, peak * 0.72), (40, peak * 0.48), (54, 1600), (64, land), (110, land)])
    path = [(a * scale, b) for a, b in path]; ms = path[-1][0]; n = int(SR * ms / 1000); t = np.arange(n) / SR
    ph, f = path_phase(path, n)
    s = (np.sin(ph) + 0.2 * np.sin(2 * ph) * (f < 4000)) * np.exp(-t / (ms / 1000 / 4.8)) * rise(t, 0.4)
    s += (0.06 + 0.29 * harsh) * bp(noise(seed, n), 4000 if harsh else 2600, 0.8) * np.exp(-t / 0.02)
    if not harsh: s += 0.6 * np.sin(ph) * (f < 600) * np.exp(-t / (ms / 1000 / 4.8))      # take 3: more of the landing note
    return hp(lp(s, 9000 if harsh else 7000, 4), 200) * np.clip((t[-1] - t) / 0.004, 0, 1)
def beep3(f=note("E", 7), ms=22, third=0.5):
    """The scroll beep as recorded: E7 with its third partial strong (a square-ish digital beep), very short."""
    t = T(ms); s = (np.sin(2 * np.pi * f * t) + third * np.sin(2 * np.pi * 3 * f * t)) * np.exp(-t / (ms / 1000 / 7)) * rise(t, 0.3)
    return hp(lp(s, 9000), 800) * np.clip((t[-1] - t) / 0.003, 0, 1)
def buzz3(f, ms=220, seed=1, glide=0.0, top=1200, hold=0.55):
    """Select and back as recorded: a low buzzy saw held almost level (the real ones don't fade), then let go."""
    t = T(ms); bend = bend_curve(t, f, glide, 0.012); ph = 2 * np.pi * np.cumsum(f * bend) / SR
    env = np.where(t < hold * ms / 1000, 1 - 0.25 * t / (hold * ms / 1000), 0.75 * np.exp(-(t - hold * ms / 1000) / 0.018)) * rise(t, 0.4)
    s = saw_on(ph, f * bend, 20, top) * env + contact(seed, len(t), 2400, 0.10, 0.5)
    return hp(lp(s, 3000, 4), 100) * np.clip((t[-1] - t) / 0.006, 0, 1)
def chatter(seed, ms=160, every=45, tone=note("A", 7), real=False):
    """Menu forward/back: the dashboard's chatter. Take 3 flickers its colour inside one fading sound; take 4 [real]
    keeps it as true bursts, as the console does (that breaks Inlay's one-press-one-hit rule: for listening only)."""
    n = int(SR * ms / 1000); t = np.arange(n) / SR
    body = (0.3 * np.sin(2 * np.pi * tone * t) + (0.6 if real else 0.3) * bp(noise(seed, n), 6800, 1.2)) * np.exp(-t / (ms / 1000 / 4.6))
    if real:
        gate = (np.mod(t * 1000, every) < every * 0.55).astype(float); gate = np.convolve(gate, np.ones(48) / 48, "same")
        s = body * gate
    else:
        s = flicker(body, seed, every, (5200, 6800, 8200), 0.3)
    lf = note("A", 3 if real else 4); low = 0.5 * saw_on(2 * np.pi * lf * t, lf, 10, 1800) * np.exp(-t / (ms / 1000 / 4.6))
    return hp(lp(s * 0.6 + low, 9000, 4), 120) * rise(t, 0.4) * np.clip((t[-1] - t) / 0.005, 0, 1)
def XBOX_TAKE(real=False):
    def make():
        pk = 8500 if real else 3000
        cues = {
            'move': beep3(note("E", 7), 32, third=0.6) if real else beep3(note("E", 6), 30, third=0.3),   # take 3: an octave down, the softest sound
            'type': zwip3(2, pk, harsh=1.0 if real else 0.0, early=True), 'space': zwip3(3, pk * 0.8, note("C", 4), 0.75, 1.0 if real else 0.0, early=True),
            'delete': zwip3(4, pk, note("G", 3), 0.55, 1.0 if real else 0.0, early=True),
            'on': chatter(5, 170, real=real), 'off': chatter(6, 170, tone=note("E", 7), real=real),
            'lock': both(chatter(7, 240, real=real), buzz3(note("A", 4), 240, 7, top=1600), 0.5),
            'select_all': stairs([note("A", 6), note("E", 6), note("D", 6), note("A", 5)], 170),
            'confirm': buzz3(note("D", 3 if real else 4), 260, 9, top=1200 if real else 800, hold=0.55 if real else 0.3),   # the A select (take 3 an octave up)
            'back': buzz3(note("C", 3 if real else 4), 160, 10, top=1000 if real else 700, hold=0.55 if real else 0.3),     # the B back
        }
        takes = [zwip3(40 + i, pk * r, note(a, 4), 0.65, 1.0 if real else 0.0, early=True) for i, (r, a) in enumerate([(0.92, "E"), (1.06, "G"), (0.98, "A")])]
        return cues, takes
    return make

# Experiments: made and checked only with --only, never shipped. Add new packs here first.
LAB = [("template", TEMPLATE, 0.0),
       ("brick_rubber", BRICK_RUBBER, 0.0), ("brick_disc", BRICK_DISC, 0.0),
       ("indigo_bounce", INDIGO_BOUNCE, 0.0), ("indigo_tine", INDIGO_TINE, 0.0),
       ("white_breeze", WHITE_BREEZE, 0.0), ("white_gloss", WHITE_GLOSS, 0.0),
       ("psx_memory", PSX_MEMORY, 0.0), ("psx_crystal", PSX_CRYSTAL, 0.0), ("cube_bubble", CUBE_BUBBLE, 0.0),
       ("cube_toybox", CUBE_TOYBOX, 0.0), ("wii_cursor", WII_CURSOR, 0.0), ("wii_piano", WII_PIANO, 0.0),
       ("psx_spu", PSX_SPU, 0.0), ("psx_sweep", PSX_SWEEP, 0.0), ("cube_menu", CUBE_MENU, 0.0),
       ("cube_chime", CUBE_CHIME, 0.0), ("wii_channel", WII_CHANNEL, 0.0), ("wii_plaza", WII_PLAZA, 0.0),
       ("xbox_dash", XBOX_DASH, 0.0), ("xbox_glow", XBOX_GLOW, 0.0),
       ("psx_bios", PSX_BIOS, 0.0), ("cube_ipl", CUBE_IPL, 0.0), ("xbox_take3", XBOX_TAKE(False), 0.0), ("xbox_take4", XBOX_TAKE(True), 0.0)]
# The same packs with patina (each console's playback bandwidth and a little grain)
LAB += [(n + "_aged", aged(r, rate, bits, fl), lv) for (n, r, lv), (rate, bits, fl) in [
    (e, cfg) for e in LAB for key, cfg in (("psx", (22050, 10, 0.02)), ("cube", (32000, 11, 0.0)), ("xbox", (32000, 10, 0.008)))
    if e[0].startswith(key)]]

# The same packs through the era's TV, as heard from the sofa (Clean / Patina / TV in the lab)
TV_ERA = {"psx": dict(rate=22050, bits=10, floor=0.02, low=160, top=6500, box=300),     # 1995: a mono CRT's one oval speaker
          "cube": dict(rate=32000, bits=11, floor=0.0, low=130, top=7500, box=340),     # 2001: a stereo CRT, a little fuller
          "xbox": dict(rate=32000, bits=10, floor=0.008, low=120, top=7200, box=300, boxg=0.18, push=0.55)}
def tv(x, rate, bits, floor, low, top, box, seed=23, fuzz=0, boxg=0.28, push=1.0, warm=None):
    """The console's sound as it reached you then: its own playback (patina), then the TV's small paper cone in a
    plastic cabinet (no deep bass, a warm boxy middle, a soft top with a little cone presence, the gentlest warmth
    when it's pushed), then a few metres of living room. Nostalgic, never broken: no hum, no whine, no crackle."""
    # fuzz 0-3: each step a little older (TV 1 to TV 4): a narrower top, a warmer pushed cone with a hint of
    # even harmonics (like a valve), a coarser grain and a soft noise bed that rides the sound, never a hiss on its own
    rate = int(rate * (1 - 0.1 * fuzz)); bits = bits - fuzz; floor = floor + 0.014 * fuzz; top = top * (1 - 0.12 * fuzz)
    y = patina(x, rate, bits, floor)
    if warm: y = warmth(y, warm)
    y = lp(hp(y, low, 2), top, 2); shape = y           # the sound's own rise and fade, before the box colours it
    y = y + boxg * bp(y, box, 0.7) + 0.10 * bp(y, 2600, 1.0)
    g = np.max(np.abs(y)) + 1e-9
    drive = 1.2 + 0.9 * fuzz * push; wet = min(0.6 + 0.1 * fuzz, 0.9)
    u = y / g + 0.06 * fuzz * (y / g) ** 2
    pushed = g * (wet * np.tanh(drive * u) / np.tanh(drive) + (1 - wet) * y / g)
    # Keep the warm overtones, not the squash: give the pushed sound the clean one's own rise and fade back
    def env(z): return np.sqrt(np.convolve(z ** 2, np.ones(96) / 96, "same")) + 1e-6 * g
    y = pushed * np.minimum(env(shape) * (g / (np.max(np.abs(shape)) + 1e-9)) / env(pushed), 4.0) if fuzz else pushed
    if fuzz: y = lp(y, top * 1.1, 2)                         # the cone can't play the new harmonics' very top
    ms = min(100.0, len(y) / SR * 1000 * 0.42)
    y = hall(y, ms, 0.10, seed, 3000)
    # The click still reaches you first (the cone's warm middle builds a moment later), and the room settles in time
    n = len(y); t = np.arange(n) / SR; dry = lp(patina(x, rate, bits, floor), top, 2) * (np.max(np.abs(y)) / (np.max(np.abs(x)) + 1e-9))
    w = np.clip((t - 0.002 - 0.001 * fuzz) / (0.003 + 0.0015 * fuzz), 0, 1)
    if fuzz:                                                 # level-matched, so the hand-over never steps up
        a, b = int(SR * (0.002 + 0.001 * fuzz)), int(SR * (0.005 + 0.0025 * fuzz))
        dry = dry * np.sqrt(np.mean(y[a:b] ** 2) / (np.mean(dry[a:b] ** 2) + 1e-12))
    y = (1 - w) * dry + w * y
    return y * np.clip((t[-1] - t) / ((0.38 if warm else 0.3) * t[-1]), 0, 1) ** (2.0 if warm else 1.5)   # warmth lifts the tail a touch: let it settle sooner
# Warmth (5 Nov): a little distortion that stays clean. Worked out at four times the sample rate (so no harsh
# aliasing), and the sound keeps its own start and fade (no squash), so it reads as warm, not broken.
from scipy.signal import resample_poly
def _keep_shape(clean, pushed):
    def env(z): return np.sqrt(np.convolve(z ** 2, np.ones(96) / 96, "same")) + 1e-6
    g = np.max(np.abs(clean)) + 1e-9
    return pushed * np.minimum(env(clean / g) / env(pushed / g), 4.0)
def warmth(x, kind):
    g = np.max(np.abs(x)) + 1e-9; u = resample_poly(x / g, 4, 1)
    if kind == "valve":      # a valve amp: soft and lopsided, so its overtones are the warm even ones
        k, b = 2.2, 0.22
        v = (np.tanh(k * (u + b)) - np.tanh(k * b)) / np.tanh(k); v = 0.55 * v + 0.45 * u
    elif kind == "tape":     # tape pushed a little hot: rounded peaks, a softer top
        v = np.tanh(1.8 * u) / np.tanh(1.8); v = 0.65 * v + 0.35 * u
    elif kind == "crunch":   # parallel crunch: only the middle is driven hard and tucked under the clean sound
        mid = sosfilt(_sos('bandpass', [500 / 4, 3000 / 4]), u)   # (the filter runs at 4x: same band)
        m = np.max(np.abs(mid)) + 1e-9; d = np.tanh(6 * mid / m) * m
        v = u + 0.55 * d
    elif kind == "hot":      # the TV's own speaker driven past its comfort: the cone's middle breaks up gently
        mid = sosfilt(_sos('bandpass', [250 / 4, 1800 / 4]), u); m = np.max(np.abs(mid)) + 1e-9
        v = u - mid + m * np.tanh(3.2 * mid / m) / np.tanh(3.2) * 1.0
    else: raise ValueError(kind)
    y = resample_poly(v, 1, 4)[:len(x)] * g
    y = lp(y, 7000, 2)
    return _keep_shape(x, y)

def on_tv(recipe, era, fuzz=0, warm=None):
    def make():
        cues, takes = recipe()
        out = {k: tv(v, **era, fuzz=fuzz, warm=None if k == "move" else warm) for k, v in cues.items()}   # Move, the sound you hear most, stays the cleanest
        if fuzz and era.get("boxg") and "lock" in out: out["lock"] = out["lock"] + 0.3 * presence(out["lock"])   # the black console's: keeps caps lock the biggest on a phone
        return out, [tv(v, **era, fuzz=fuzz, warm=warm) for v in takes]
    return make
TV_STEPS = {"_tv": 0, "_tv2": 1, "_tv3": 2, "_tv4": 3}
LAB += [(n + sfx, on_tv(r, TV_ERA[key], fz), lv) for (n, r, lv) in list(LAB) if not n.endswith("_aged")
        for key in TV_ERA if n.startswith(key) for sfx, fz in TV_STEPS.items()]
WARMTHS = ("valve", "tape", "crunch", "hot")
LAB += [(n + "_" + w, on_tv(r, TV_ERA[key], 0, w), lv) for (n, r, lv) in list(LAB) if not n.endswith(("_aged", "_tv", "_tv2", "_tv3", "_tv4"))
        for key in TV_ERA if n.startswith(key) for w in WARMTHS]

# 1.3 shipping: the secret themes' own packs (Emre's picks, 5 Nov 2026). Each is a clean recipe plus an era filter,
# so the clean baseline can always be rebuilt (and is kept in tools/sound-lab/clean/): change only the filter here.
# Emre, 5 Nov 22:18: on the Odin's own speaker the filters didn't come across, so 1.3 tries the clean versions.
# To bring a filter back, swap the line for the one under it (all three passed every check).
PACKS += [("brick", PSX_BIOS, 0.0),                 # Grey brick: BIOS, clean   | on_tv(PSX_BIOS, TV_ERA["psx"], 0, "crunch")
          ("indigo", CUBE_IPL, 0.0),                # Indigo: Start-up, clean   | on_tv(CUBE_IPL, TV_ERA["cube"], 0, "hot")
          ("jewel", XBOX_TAKE(False), 0.0)]         # Black jewel: Take 3, clean | on_tv(XBOX_TAKE(False), TV_ERA["xbox"], 2)
CLEAN = {"brick": "psx_bios", "indigo": "cube_ipl", "jewel": "xbox_take3"}   # each shipping retro pack's clean recipe (LAB)

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
