"""Inlay 1.1 sound packs: Retro, Typewriter, Felt.
Same rules as every pack: ONE action = ONE hit; actions differ by weight, pitch bend and length;
loudness mastered into the files with the same felt-loudness meter and levels as Soft;
pitched sounds use only C major pentatonic (pure ratios) and harmonic overtones."""
import numpy as np, importlib.util, sys
import softlib as soft   # softlib.py = soft-synth.py without its make() calls
SR, press, master, save, LEVEL, lp, hp, bp_res = soft.SR, soft.press, soft.master, soft.save, soft.LEVEL, soft.lp, soft.hp, soft.bp_res
C4 = 261.63
P = {"C": 1, "D": 9/8, "E": 5/4, "G": 3/2, "A": 5/3}         # pentatonic, pure ratios
def note(n, octave): return C4 * P[n] * 2**(octave-4)

# ---- RETRO: soft-edged pulse blips, like a handheld menu ------------------------------
def blip(f, ms, *, glide=0.0, duty_harm=7, decay=None, click=0.15, seed=1):
    """Band-limited square (odd harmonics only, all exact multiples), pitch bend in the
    first 25 ms, quick soft fade. Rounded attack so it never clicks harshly."""
    n = int(SR*ms/1000); t = np.arange(n)/SR
    bend = 2**((glide/12)*(1-np.exp(-t/0.010)))
    ph = 2*np.pi*np.cumsum(f*bend)/SR
    s = sum(np.sin(k*ph)/k for k in range(1, duty_harm+1, 2))
    a = np.clip(t/0.0015, 0, 1); d = decay or ms*0.35
    s *= a*a*(3-2*a) * np.exp(-t/(d/1000))
    rng = np.random.default_rng(seed)
    s += click*hp(rng.standard_normal(n), 3000)*np.exp(-t/0.0006)      # tiny contact
    return hp(lp(s, 7000), 140)
retro = {
    'move':   blip(note("C", 6), 14, duty_harm=3, decay=4, click=0.05),
    'type':   blip(note("E", 5), 26, decay=9),
    'space':  blip(note("G", 4), 34, decay=12),
    'delete': blip(note("A", 4), 30, glide=-5, decay=10),             # a little fall = take away
    'on':     blip(note("C", 5), 46, glide=7, decay=16),              # up a fifth = on
    'off':    blip(note("G", 5), 46, glide=-7, decay=16),             # down a fifth = off
    'lock':   blip(note("C", 4), 70, glide=12, duty_harm=9, decay=26),# up an octave, heaviest
    'select_all': blip(note("E", 5), 56, glide=5, decay=20),
    'confirm':blip(note("G", 4), 70, glide=5, decay=26),              # resolves up to the root
    'back':   blip(note("C", 5), 50, glide=-12, decay=16),
}
takes_retro = [blip(note(n, 5), 26, decay=9, seed=30+i) for i, n in enumerate(["D", "G", "A"])]

# ---- TYPEWRITER: a hard metal strike on a platen; Enter gets the small bell ------------
def strike(seed, weight=1.0, bright=1.0, ms=60):
    return press(seed=seed, thud=0.8*weight, thud_f=(190, 700), thud_decay=2.2,
                 click_f=(2600*bright, 4100*bright), click=1.25, click_decay=(1.6, 1.0),
                 glint_f=6100*bright, glint=0.25, glint_decay=2.5, air=0.14, ms=ms)
def bell(f, ms=260):
    """A small, quick bell: root + octave + double octave only (no inharmonic partials)."""
    n = int(SR*ms/1000); t = np.arange(n)/SR
    s = sum(a*np.sin(2*np.pi*f*k*t)*np.exp(-t/d) for k, a, d in ((1, 1, 0.11), (2, 0.35, 0.06), (4, 0.12, 0.03)))
    a = np.clip(t/0.001, 0, 1); return hp(s*a, 200)
ding = np.zeros(int(SR*0.26)); st = strike(19, weight=1.3, bright=0.9, ms=60); ding[:len(st)] += st; ding += 0.5*bell(note("G", 6))
typewriter = {
    'move':   press(seed=11, thud=0.0, click_f=(3600, 5200), click_decay=(0.8, 0.6), air=0.06, ms=22),
    'type':   strike(12),
    'space':  strike(13, weight=1.5, bright=0.75),
    'delete': strike(14, weight=1.2, bright=0.85),
    'on':     strike(15, weight=1.7, bright=1.05, ms=70),
    'off':    strike(16, weight=1.4, bright=0.7, ms=70),
    'lock':   strike(17, weight=2.0, bright=1.0, ms=90),
    'select_all': strike(18, weight=1.2, bright=1.15, ms=80),
    'confirm': ding,                                   # the carriage-return bell, one event
    'back':   strike(20, weight=1.1, bright=0.6),
}
takes_tw = [strike(40+i, bright=1+0.03*(i-1)) for i in range(3)]

# ---- FELT: only the soft thud, no click: for late at night ------------------------------
def tap(seed, weight=1.0, f=(160, 520), ms=50, decay=2.4):
    return press(seed=seed, thud=weight, thud_f=f, thud_decay=decay, click_f=(900, 1300), click=0.18,
                 click_decay=(1.8, 1.2), air=0.0, ms=ms)
felt = {
    'move':   tap(21, 0.5, f=(240, 700), ms=20, decay=1.2),
    'type':   tap(22), 'space': tap(23, 1.3, f=(140, 460), decay=3.0), 'delete': tap(24, 1.1, f=(150, 500)),
    'on':     tap(25, 1.5, f=(170, 600), ms=60, decay=3.2), 'off': tap(26, 1.3, f=(140, 440), ms=60, decay=2.8),
    'lock':   tap(27, 1.8, f=(130, 480), ms=80, decay=3.8), 'select_all': tap(28, 1.3, f=(180, 620), ms=60),
    'confirm':tap(29, 1.5, f=(150, 540), ms=70, decay=3.4), 'back': tap(30, 1.0, f=(130, 420), ms=50),
}
takes_felt = [tap(50+i) for i in range(3)]

def tame(s, target):
    """Round off the first instant of a sharp hit (a soft tanh limiter) so mastering can
    reach the target loudness without touching the ceiling. Keeps the loudness ORDER."""
    s = np.asarray(s, float); s = s/np.max(np.abs(s))
    for _ in range(12):
        m = master(s, target)
        if softlib_felt(m) >= target - 0.3: return m
        s = np.tanh(s*1.6)/np.tanh(1.6)            # a little more rounding, then try again
    return master(s, target)
import softlib; softlib_felt = softlib.felt

# Felt sits a little quieter on purpose: it's the night pack (still in the same order)
for name, cues, takes, offset in (("retro", retro, takes_retro, -1.0), ("typewriter", typewriter, takes_tw, 0.0), ("felt", felt, takes_felt, -3.0)):
    for cue, s in cues.items():
        save(f"out/{name}_{cue}.wav", tame(s, LEVEL[cue] + offset))
    for i, s in enumerate(takes, 1):
        save(f"out/{name}_type{i}.wav", tame(s, LEVEL['type'] + offset))
print("done")
