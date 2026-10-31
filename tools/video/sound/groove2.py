"""Round 32: a bigger bloom with a touch of warm distortion, and grooves that breathe (music only).

    BLOOM=clean|drift|drift-warm         python groove2.py bloom  <cues.md> <video> <out.wav> [seconds]
    GROOVE=pulse|softhouse|deep|deep-sub python groove2.py groove <cues.md> <video> <out.wav>

The bloom: the submerged pulse now swells from nothing over 1.4 s AND opens up as it swells (its
muffling lifts from ~300 Hz to ~1 kHz, then settles back to ~600 Hz) - the timbre blooms, not just the
level. Warmth: a tiny valve-style curve on the steady tone (whole-number overtones only), then a gentle
roll-off so no buzz gets through; the swell is applied after, so the colour never switches on mid-note.

The grooves follow how producers loosen simple patterns (Attack Magazine, Soundbridge):
  - every hit a slightly different level; main beats accented, in-between notes soft
  - ghost notes: very quiet hits on in-between 16ths, mostly just before beats 2 and 4
  - swing (58 %) on the shaker and hats only; the kick stays dead on the beat; claps a few ms early
  - a four-bar phrase: bars 2 and 4 vary, bar 4 ends with a small fill into the next phrase
Deep house adds: off-beat open hats, a short chord stab on the first off-beat (varying across the
phrase), and a deep, syncopated bass that only plays the chord's own root notes.
Tempo, chords and their timing never change. Every pitched note is checked by the rub rule.
"""
import os, sys
import numpy as np
import videosound as V
import bloom as B
import energy as E
import layers2 as L2
from videosound import T, n, lp, hp, ph0, BEAT, BAR, SR

BLOOM = os.environ.get("BLOOM", "drift"); GROOVE = os.environ.get("GROOVE", "")
rng = np.random.default_rng(32)
up = lambda nt, k=1: nt[:-1] + str(int(nt[-1]) + k)

# ---------------------------------------------------------------- the bigger bloom
def bloom_note(f, dur, mode, seed):
    t = T(dur + 0.6); out = np.zeros((2, len(t)))
    drift = mode.startswith("drift"); drive = 2.2 if mode == "drift-warm" else 1.5
    a = np.clip(t / 1.4, 0, 1); sw = a * a * (3 - 2 * a)
    fc = 300 + 700 * sw * np.exp(-np.maximum(t - 1.4, 0) / 1.5) + 300 * np.clip(t / 1.4, 0, 1) * (1 - np.exp(-np.maximum(t - 1.4, 0) / 1.5))
    for ch in (0, 1):
        cents = 4.0 * E.smooth_random(t, 0.7, seed * 2 + ch) if drift else np.zeros(len(t))
        phase = 2 * np.pi * np.cumsum(f * 2 ** (cents / 1200)) / SR + ph0(f, ch)
        s = np.zeros(len(t))
        for k in range(1, 16):
            if k * f > 5000: break
            x = k * f / fc; g = 1 / np.sqrt(1 + x ** 4) * (1 + 0.6 * np.exp(-((x - 1) / 0.35) ** 2))
            s += np.sin(np.pi * k * 0.35) / k * g * np.sin(k * phase)
        s = s / (np.abs(s).max() + 1e-9) * 0.7
        bias = 0.12; s = (np.tanh(drive * (s + bias)) - np.tanh(drive * bias)) / (drive * (1 - np.tanh(drive * bias) ** 2))   # the tiny warm distortion
        out[ch] = s
    body = 0.75 + 0.25 * np.exp(-np.maximum(t - 1.4, 0) / 1.0); r = np.clip((t - dur) / 0.4, 0, 1)
    out *= sw * body * np.cos(r * np.pi / 2) ** 2                    # the swell, applied after the colour
    return lp(hp(out, 70, 2), 2200, 4)                               # nothing buzzy gets through

def bloom_layer(cues, video, mode):
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L); m = np.zeros((2, int(SR * (L + 6))))
    for bar in bars:
        t0, ch, i = bar["t"], bar["chord"], bar["i"]
        if bar["end"] or t0 < 0: continue
        d = (L - t0 + 1.0) if i == -1 else BAR * BEAT * 0.92
        for q, nt in enumerate(E.HELD[ch]): E.put(m, bloom_note(n(nt), d, mode, 10 * abs(i) + q), t0)
    return m[:, :int(SR * L)]

# ---------------------------------------------------------------- percussion
def open_hat(vel, seed):
    t = T(0.25); nz = np.random.default_rng(seed).standard_normal(len(t)); a = np.clip(t / 0.003, 0, 1)
    return vel * lp(hp(nz, 3000, 4), 7000, 4) * a * a * (3 - 2 * a) * np.exp(-t / 0.07)        # dark and short

def clap(vel, seed):
    t = T(0.25); nz = np.random.default_rng(seed).standard_normal(len(t)); env = np.zeros(len(t))
    for d0 in (0.0, 0.009, 0.018):                                    # a clap is three quick slaps
        tt = np.maximum(t - d0, 0); a = np.clip(tt / 0.0015, 0, 1); env += (t >= d0) * a * np.exp(-tt / (0.006 if d0 < 0.018 else 0.06))
    return vel * lp(hp(nz, 900, 2), 2600, 4) * env / 2

def stab(f_pair, vel, seed):
    """A short deep house chord stab: the two safe notes of the chord in the bloom's muffled pulse."""
    # (round 42 tried the stabs an octave down too: they rubbed with the chords nearly twice as often, so they stay)
    out = sum(bloom_note_short(n(nt), seed + j) for j, nt in enumerate(f_pair)); return vel * out

def bloom_note_short(f, seed):
    t = T(0.5); s = np.zeros(len(t))
    for k in range(1, 12):
        if k * f > 4000: break
        x = k * f / 900; s += np.sin(np.pi * k * 0.35) / k / np.sqrt(1 + x ** 4) * np.sin(2 * np.pi * k * f * t + ph0(f, k))
    a = np.clip(t / 0.006, 0, 1); s *= a * a * (3 - 2 * a) * np.exp(-t / 0.14)
    return lp(s, 2200, 4)

def deep_bass(f, vel):
    t = T(0.6); s = np.sin(2 * np.pi * f * t) + 0.2 * np.sin(4 * np.pi * f * t)
    a = np.clip(t / 0.01, 0, 1); return vel * s * a * a * (3 - 2 * a) * np.exp(-t / 0.22)

BASS_ROOT = {"Am": "A1", "Em": "E2", "G": "G1", "C": "C2"}
STAB_POS = {0: [0.5], 1: [0.5, 2.75], 2: [0.5], 3: [0.5, 1.75, 3.5]}            # per bar of the 4-bar phrase (beats)
BASS_POS = {0: [0, 0.75, 2.5], 1: [0, 1.5, 2.75], 2: [0, 0.75, 2.5], 3: [0, 1.5, 3.0, 3.5]}   # (beat, 1) = an octave up           # the bed's own roots, an octave down

def groove(cues, video, style):
    """Returns (drums, pitched) layers and the pitched events for the rub check."""
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L)
    dr = np.zeros((2, int(SR * (L + 6)))); pt = np.zeros_like(dr); ev = []
    def hit(sig, t, pan=0.0, layer=None):
        E.put(dr if layer is None else layer, sig, t, pan); V.MUSIC_ONSETS.append(t)
    jit = lambda ms: rng.uniform(-ms, ms) / 1000
    sw = lambda k: 0.04 * BEAT if k % 2 else 0.0                     # 58 % swing on the in-between 16ths
    for bar in bars:
        t0, ch, i = bar["t"], bar["chord"], bar["i"]
        if bar["end"] or t0 < 0: continue
        if i == -1: hit(E.kick(0.55), t0); continue                  # one last soft kick on the home chord
        pos = i % 4                                                  # 0..3 in the four-bar phrase (3 = the C bar, phrase end)
        four = style in ("softhouse", "deep", "deep-sub")
        # kick: straight; ghost kicks vary by bar
        for b in ((0, 1, 2, 3) if four else (0, 2)): hit(E.kick(0.62 if b in (0, 2) else 0.5), t0 + b * BEAT)
        ghosts = {0: [], 1: [2.75], 2: [], 3: [3.5, 3.75]}[pos] if style != "pulse" else {0: [], 1: [1.75], 2: [], 3: [3.25, 3.5]}[pos]
        for b in ghosts: hit(E.kick(0.22), t0 + b * BEAT)
        # shaker: 16ths, swung, accented on the off-beat, ghosts in between; bar 4 rolls into the next phrase
        for k in range(16):
            if style == "pulse" and k % 2 and not (pos == 3 and k >= 12): continue        # pulse: 8ths, a 16th roll at the end
            acc = 1.0 if k % 4 == 2 else 0.55 if k % 2 == 0 else 0.32
            if pos == 3 and k >= 12: acc = 0.45 + 0.12 * (k - 12)                         # the fill: a small crescendo
            hit(E.shaker(0.075 * acc * rng.uniform(0.9, 1.1), 1000 + 16 * abs(i) + k), t0 + k * BEAT / 4 + sw(k) + jit(4), 0.3)
        # claps or snaps on 2 and 4, a few ms early; ghost snaps just before them on some bars
        for b in (1, 3):
            if style == "pulse" and b == 1 and pos % 2 == 0: continue                     # pulse: the back-beat comes in gradually
            hit((clap if style.startswith("deep") else E.snap)(0.06 if style.startswith("deep") else 0.05, 50 * abs(i) + b), t0 + b * BEAT - 0.008, -0.15)
        if pos in (1, 3):
            hit(E.snap(0.018, 900 + abs(i)), t0 + 0.75 * BEAT + sw(3), -0.2)              # a ghost just before beat 2
        # deep house: off-beat open hats (soft, varying), a chord stab, a syncopated deep bass
        if style.startswith("deep"):
            for b in range(4): hit(open_hat(0.05 * (1.0 if b % 2 else 0.8) * rng.uniform(0.9, 1.1), 2000 + 4 * abs(i) + b), t0 + (b + 0.5) * BEAT + jit(3), 0.25)
            st_pos = STAB_POS[pos]                                    # the stab on the first off-beat, more on bars 2 and 4
            for b in st_pos:
                t = t0 + b * BEAT; E.put(pt, stab(E.HELD[ch], 0.05 if b == 0.5 else 0.035, 7 * abs(i)), t, 0.1); V.MUSIC_ONSETS.append(t)
                for nt in E.HELD[ch]: ev.append(dict(t=t, end=t + 0.5, note=nt, inst="harm", vel=0.05, pan=0, amp=0.03, dur=0.3))
            for b in BASS_POS[pos]:
                b, octv = (b, 0) if not isinstance(b, tuple) else b
                t = t0 + b * BEAT; E.put(pt, deep_bass(n(up(BASS_ROOT[ch], octv)), 0.16 if not octv else 0.12), t); V.MUSIC_ONSETS.append(t)
        # pulse: the submerged pulse line, with accents and a rest on bar 2 (breathing room)
        if style == "pulse":
            for j in range(8):
                if pos == 1 and j in (5, 6): continue
                nt = E.LINE[ch][j % 2]; v = (1.0 if j in (0, 3, 6) else 0.65) * rng.uniform(0.92, 1.06)
                t = t0 + j * BEAT / 2 + (0.03 * BEAT if j % 2 else 0); E.put(pt, E.submerged(n(nt), 0.26, "drift", 100 + j, pulse=True) * v, t); V.MUSIC_ONSETS.append(t)
                ev.append(dict(t=t, end=t + 0.4, note=nt, inst="harm", vel=0.05, pan=0, amp=0.03, dur=0.26))
        # softhouse: the motif chimes, loosened (accents, a ghost note, a pickup at the phrase end)
        if style == "softhouse":
            vo = B.VOICING[ch]; a_, b_ = (vo[3], vo[2]) if i % 2 == 0 else (vo[2], vo[3])
            notes = [(0, up(a_), 0.055), (1.5 + 0.04, up(b_), 0.045), (3, up(vo[1]), 0.04)]
            if pos == 1: notes.append((2.0, up(a_), 0.022))                                # a ghost echo (done ringing before the next chime)
            if pos == 3: notes.append((3.5 + 0.04, up(vo[3]), 0.035))                     # a pickup into the next phrase
            for b, nt, v in notes:
                t = t0 + b * BEAT; E.put(pt, L2.chime(n(nt), v), t, 0.2); V.MUSIC_ONSETS.append(t)
                ev.append(dict(t=t, end=t + 0.62, note=nt, inst="chime", vel=v, pan=0, amp=v))
    return dr[:, :int(SR * L)], pt[:, :int(SR * L)], ev, bars

if __name__ == "__main__":
    import pyloudnorm as pyln
    what, cues_p, video, out = sys.argv[1:5]; seconds = float(sys.argv[5]) if len(sys.argv) > 5 else None
    cues = V.read_cues(cues_p)[video]; L = V.LENGTH[video]; meter = pyln.Meter(SR)
    V.MUSIC_ONSETS.clear(); V.ONSET_WINDOW = 0.15
    base = B.render(cues, video); lb = meter.integrated_loudness(base.T)
    at = lambda x, lu: x * V.db(lb + lu - meter.integrated_loudness(x.T))
    if what == "bloom":
        E.SUB = "clean"; E.check()                                   # same notes as round 31 (overtones checked there)
        m = base + E.dip(at(bloom_layer(cues, video, BLOOM), -6.0), cues, video)
    else:
        dr, pt, ev, bars = groove(cues, video, GROOVE)
        L2.check(ev, bars, L)                                        # every pitched note against the chords and each other
        m = base * (E.pump(cues, video)[None, :base.shape[1]] if GROOVE == "pulse" else 1.0)
        m = m + E.dip(at(dr, -4.5), cues, video, -8.0) + E.dip(at(pt, -6.5), cues, video, -8.0)
        if GROOVE == "deep-sub": m = m + E.dip(at(bloom_layer(cues, video, "drift"), -8.0), cues, video)
    E.finish(m, out, L, seconds)
