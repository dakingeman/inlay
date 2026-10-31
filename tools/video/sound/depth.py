"""Round 33: deeper blooms, and three bass/hat flavours for the locked deep house groove (music only).

    DEPTH=octave|trio|space|full       python depth.py bloom  <cues.md> <video> <out.wav> [seconds]
    FLAVOUR=techno|retro|smooth         python depth.py groove <cues.md> <video> <out.wav>

Locked (unchanged): the 1B-soft chords; the bloom instrument (round 32, drift); from the deep house
groove the kick and ghost kicks, the claps, the ghost snaps and the chord stabs with their rhythm.
Only the bass and the hats/shaker change per flavour.

Deeper blooms (all notes are chord notes or octaves of the bloom's own notes, so they harmonise):
  octave  each bloom note gets a softer twin an octave up that swells a little later (a second wave)
  trio    a third bloom voice in the middle of the chord (one of the chord's own, non-colour notes)
  space   a small, dark room around the bloom: a few early echoes, no long reverb tail
  full    trio + octave + space
Flavours:
  techno  bass: a tight, rolling root on the in-between 16ths; hats: straight, tight closed hats on
          every 16th with accented off-beats, a short open hat; no swing, no shaker
  retro   bass: a warm square-wave octave bass jumping low-high on the eighths (classic disco/house);
          hats: 909-style closed hats on the eighths, a longer open hat on the off-beats, a quiet
          swung tambourine
  smooth  bass: long, round sub notes on 1 and the 'and' of 2 (a lazy syncopation); hats: a brushy,
          swung shaker on the eighths and a soft open hat only on the 'and' of 2 and 4
"""
import os, sys
import numpy as np
import videosound as V
import bloom as B
import energy as E
import layers2 as L2
import groove2 as G
from videosound import T, n, lp, hp, ph0, BEAT, BAR, SR

DEPTH = os.environ.get("DEPTH", "octave"); FLAVOUR = os.environ.get("FLAVOUR", "smooth")
up = lambda nt, k=1: nt[:-1] + str(int(nt[-1]) + k)
TRIO = {"Am": "C4", "Em": "E4", "G": "D4", "C": "E4"}               # Em: E4 (G3's octave overtone sat by the bloom's A4)               # a chord note that isn't the softened colour note

# ---------------------------------------------------------------- deeper blooms
def room(x):
    """A small dark room: five early echoes (11-47 ms), each quieter and darker, slightly different per ear."""
    out = x.copy()
    for k, (dl, dr, g) in enumerate(((0.011, 0.013, 0.22), (0.019, 0.023, 0.17), (0.029, 0.026, 0.13), (0.037, 0.041, 0.10), (0.047, 0.044, 0.07))):
        for ch, d in ((0, dl), (1, dr)):
            s = int(SR * d); out[ch, s:] += g * lp(x[1 - ch if k % 2 else ch], 1400)[:-s]
    return out

def deep_bloom(cues, video, depth):
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L); m = np.zeros((2, int(SR * (L + 6))))
    for bar in bars:
        t0, ch, i = bar["t"], bar["chord"], bar["i"]
        if bar["end"] or t0 < 0: continue
        d = (L - t0 + 1.0) if i == -1 else BAR * BEAT * 0.92
        for q, nt in enumerate(E.HELD[ch]): E.put(m, G.bloom_note(n(nt), d, "drift", 10 * abs(i) + q), t0)          # the locked bloom
        if depth in ("trio", "full"): E.put(m, 0.7 * G.bloom_note(n(TRIO[ch]), d, "drift", 500 + abs(i)), t0)
        if depth in ("octave", "full"):
            for q, nt in enumerate(E.HELD[ch]):
                if depth == "full" and q == 1: continue                 # full: only the low note's twin (the high twin met the trio's overtone)
                if n(up(nt)) < 1000: E.put(m, 0.42 * G.bloom_note(n(up(nt)), max(d - 0.35, 0.5), "drift", 700 + 10 * abs(i) + q), t0 + 0.35, (-0.4, 0.4)[q])
    m = m[:, :int(SR * L)]
    return room(m) if depth in ("space", "full") else m

def depth_events(cues, video, depth):
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L); ev = []
    for bar in bars:
        t0, ch, i = bar["t"], bar["chord"], bar["i"]
        if bar["end"] or t0 < 0: continue
        d = (L - t0) if i == -1 else BAR * BEAT
        notes = list(E.HELD[ch]) + ([TRIO[ch]] if depth in ("trio", "full") else []) + ([up(x) for q, x in enumerate(E.HELD[ch]) if n(up(x)) < 1000 and not (depth == "full" and q == 1)] if depth in ("octave", "full") else [])
        for nt in notes:
            late = 0.35 if nt not in E.HELD[ch] and nt != TRIO[ch] else 0.0           # the octave twins start 0.35 s later
            # the audible part of a bloom: from ~0.6 s into its 1.4 s swell (about -8 dB) until halfway through its release
            ev.append(dict(t=t0 + late + 0.6, end=t0 + d * 0.92 + 0.2, note=nt, inst="harm", vel=0.05, pan=0, amp=0.03, dur=d))
    return ev, bars

# ---------------------------------------------------------------- flavours: hats and bass
def closed_hat(vel, seed, decay=0.018, lo=4500, hi=9000):
    t = T(0.12); nz = np.random.default_rng(seed).standard_normal(len(t)); a = np.clip(t / 0.0015, 0, 1)
    return vel * lp(hp(nz, lo, 4), hi, 4) * a * a * (3 - 2 * a) * np.exp(-t / decay)

def tambourine(vel, seed):
    t = T(0.12); rng = np.random.default_rng(seed); nz = rng.standard_normal(len(t)); a = np.clip(t / 0.002, 0, 1)
    jingle = lp(hp(nz, 5000, 4), 9500, 4) * (1 + 0.6 * np.sin(2 * np.pi * 38 * t))     # the jingles' quick shimmer
    return vel * jingle * a * a * (3 - 2 * a) * np.exp(-t / 0.03)

def bass_note(f, vel, kind, dur=0.3):
    t = T(dur + 0.5); a = np.clip(t / (0.006 if kind != "smooth" else 0.02), 0, 1); a = a * a * (3 - 2 * a)
    if kind == "techno":   s = np.sin(2 * np.pi * f * t) + 0.15 * np.sin(4 * np.pi * f * t); env = np.exp(-t / 0.07)
    elif kind == "retro":  s = lp(sum(np.sin(2 * np.pi * k * f * t + ph0(f, k)) / k for k in (1, 3, 5, 7)), 900, 2); env = np.exp(-t / 0.12)
    else:                  s = np.sin(2 * np.pi * f * t) + 0.1 * np.sin(4 * np.pi * f * t); env = np.where(t < dur, 1.0, np.exp(-(t - dur) / 0.15)) * (0.75 + 0.25 * np.exp(-t / 0.3))
    return vel * s * a * env

def flavour(cues, video, fl):
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L)
    hats = np.zeros((2, int(SR * (L + 6)))); bass = np.zeros_like(hats); ev = []
    rng = np.random.default_rng(33)
    def hit(layer, sig, t, pan=0.0): E.put(layer, sig, t, pan); V.MUSIC_ONSETS.append(t)
    for bar in bars:
        t0, ch, i = bar["t"], bar["chord"], bar["i"]
        if bar["end"] or t0 < 0 or i == -1: continue
        pos = i % 4; root = G.BASS_ROOT[ch]
        if fl == "techno":
            for k in range(16):
                v = (1.0 if k % 4 == 2 else 0.5 if k % 2 == 0 else 0.35) * rng.uniform(0.92, 1.06)
                if pos == 3 and k >= 12: v = 0.5 + 0.15 * (k - 12)
                hit(hats, closed_hat(0.06 * v, 3000 + 16 * abs(i) + k, 0.014), t0 + k * BEAT / 4, 0.25)
            for b in range(4): hit(hats, closed_hat(0.045, 3500 + 4 * abs(i) + b, 0.045, 4000, 8000), t0 + (b + 0.5) * BEAT, -0.2)
            for b in range(4):
                for s16 in (1, 2, 3):
                    if pos == 3 and b == 3 and s16 == 3: continue
                    t = t0 + (b + s16 / 4) * BEAT; hit(bass, bass_note(n(root), 0.2 * (1.0 if s16 == 2 else 0.75), "techno", 0.1), t)
                    ev.append(dict(t=t, end=t + 0.25, note=root, inst="harm", vel=0.05, pan=0, amp=0.02, dur=0.1))
        elif fl == "retro":
            for b in range(4):
                hit(hats, closed_hat(0.03, 4000 + 8 * abs(i) + 2 * b, 0.02, 4000, 8500), t0 + b * BEAT, 0.2)
                hit(hats, closed_hat(0.055, 4001 + 8 * abs(i) + 2 * b, 0.11, 3500, 8000), t0 + (b + 0.5) * BEAT, 0.25)  # the open hat
            for k in range(16):
                if k % 2: hit(hats, tambourine(0.022 * rng.uniform(0.85, 1.1), 4500 + 16 * abs(i) + k), t0 + k * BEAT / 4 + 0.05 * BEAT, -0.3)
            for j in range(8):
                hi = "A2" if ch == "Em" else up(root)          # Em jumps to A, not the octave (high E's overtone sat by the A4)
                nt = root if j % 2 == 0 else hi; t = t0 + j * BEAT / 2
                hit(bass, bass_note(n(nt), 0.16 * (1.0 if j % 2 == 0 else 0.8), "retro", 0.2), t)
                ev.append(dict(t=t, end=t + 0.4, note=nt, inst="harm", vel=0.05, pan=0, amp=0.02, dur=0.2))
        else:   # smooth
            for k in range(8):
                v = (0.9 if k % 2 else 0.55) * rng.uniform(0.88, 1.08)
                hit(hats, E.shaker(0.06 * v, 5000 + 8 * abs(i) + k), t0 + k * BEAT / 2 + (0.12 * BEAT if k % 2 else 0), 0.3)
            for b in (1.5, 3.5): hit(hats, closed_hat(0.04, 5500 + abs(i) + int(b), 0.16, 3000, 6500), t0 + b * BEAT + 0.12 * BEAT * 0, 0.2)
            for b, dur in ((0, 1.3 * BEAT), (1.5, 1.8 * BEAT)) if pos != 3 else ((0, 1.3 * BEAT), (1.5, 1.0 * BEAT), (2.75, 0.9 * BEAT)):
                t = t0 + b * BEAT; hit(bass, bass_note(n(root), 0.17, "smooth", dur), t)
                ev.append(dict(t=t, end=t + dur + 0.3, note=root, inst="harm", vel=0.05, pan=0, amp=0.02, dur=dur))
    return hats[:, :int(SR * L)], bass[:, :int(SR * L)], ev, bars

def locked_deep(cues, video):
    """Round 32's deep groove with its hats and bass taken out: kick, ghost kicks, claps, ghost snaps, stabs."""
    keep = (G.open_hat, E.shaker, G.deep_bass)
    G.open_hat = lambda *a, **k: np.zeros(10); E.shaker = lambda *a, **k: np.zeros(10); G.deep_bass = lambda *a, **k: np.zeros(10)
    try: return G.groove(cues, video, "deep")
    finally: G.open_hat, E.shaker, G.deep_bass = keep

if __name__ == "__main__":
    import pyloudnorm as pyln
    what, cues_p, video, out = sys.argv[1:5]; seconds = float(sys.argv[5]) if len(sys.argv) > 5 else None
    cues = V.read_cues(cues_p)[video]; L = V.LENGTH[video]; meter = pyln.Meter(SR)
    V.MUSIC_ONSETS.clear(); V.ONSET_WINDOW = 0.15
    base = B.render(cues, video); lb = meter.integrated_loudness(base.T)
    at = lambda x, lu: x * V.db(lb + lu - meter.integrated_loudness(x.T))
    if what == "bloom":
        ev, bars = depth_events(cues, video, DEPTH); L2.check(ev, bars, L)
        m = base + E.dip(at(deep_bloom(cues, video, DEPTH), -6.0), cues, video)
    else:
        dr, pt, ev1, bars = locked_deep(cues, video)
        hats, bass, ev2, _ = flavour(cues, video, FLAVOUR)
        ev3, _ = depth_events(cues, video, "plain")
        L2.check(ev1 + ev2 + ev3, bars, L)
        m = base + E.dip(at(dr, -4.5), cues, video, -8.0) + E.dip(at(pt, -6.5), cues, video, -8.0)
        m = m + E.dip(at(hats, -9.0), cues, video, -8.0) + E.dip(at(bass, -6.0), cues, video, -8.0)
        m = m + E.dip(at(deep_bloom(cues, video, "plain"), -8.0), cues, video)     # the bloom underneath, as in deep-sub
    E.finish(m, out, L, seconds)
