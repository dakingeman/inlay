"""Round 31: the 'submerged' instrument and the rhythm versions, on the locked 1B-soft base (music only).

    SUB=clean|drift|bubble|tape|pulse   python energy.py sound <cues.md> <video> <out.wav> [seconds]
    GROOVE=heartbeat|softhouse|pulse    python energy.py groove <cues.md> <video> <out.wav>

Submerged: an analog-style pulse wave (whole-number overtones, 35 % pulse width) heavily muffled,
as if heard underwater (cutoff ~650 Hz, a gentle resonance), blooming in like the chords.
  clean   steady pitch, steady filter
  drift   + tiny, slow random pitch drift (a few cents, under one wobble a second), different in each ear
  bubble  + drift, and the muffling 'breathes' and bubbles (the cutoff moves randomly, 2-4 times a second)
  tape    + bubble, and a faint tape hiss (steady hiss only: no pops, no crackle)
  pulse   the same muffled pulse as a moving line: eighth notes, the chord's low note and its octave
The filter is built into the sound note by note (each overtone gets its own level over time), so a
moving filter can never click or ring. Every overtone is measured and checked against the chords.

Groove (the chords, notes and timing untouched, only channels added):
  heartbeat  a soft round kick on beats 1 and 3, a dark shaker on the off-beats
  softhouse  a soft kick on every beat, the shaker, a soft snap on 2 and 4, and round 27's motif chimes
  pulse      heartbeat + the submerged pulse line, and the chords breathe gently with the kick (a fixed,
             smooth curve written in, not a compressor: nothing reacts, so nothing can step or crackle)
Everything added steps back under the sound packs and stops at the home chord (one last soft kick on it).
"""
import os, sys
import numpy as np
import videosound as V
import bloom as B
from videosound import T, n, lp, hp, ph0, BEAT, BAR, SR

SUB = os.environ.get("SUB", "clean"); GROOVE = os.environ.get("GROOVE", "")
HELD = {"Am": ("E3", "G4"), "Em": ("E2", "A4"), "G": ("G2", "A4"), "C": ("C3", "G3")}    # passed every check in round 30
LINE = {"Am": ("E3", "E4"), "Em": ("E2", "E3"), "G": ("G2", "G3"), "C": ("C3", "C4")}    # pulse line: low note and its octave

def smooth_random(n_, rate, seed):
    """A slow random wander between -1 and 1 (random points 'rate' times a second, joined smoothly)."""
    rng = np.random.default_rng(seed); k = int(len(n_) / SR * rate) + 3
    pts = rng.uniform(-1, 1, k); x = np.linspace(0, k - 3, len(n_))
    i = np.floor(x).astype(int); fr = x - i; fr = fr * fr * (3 - 2 * fr)
    return pts[i] * (1 - fr) + pts[i + 1] * fr

def submerged(f, dur, mode, seed, pulse=False):
    t = T(dur + (0.5 if not pulse else 0.4)); out = np.zeros((2, len(t)))
    drift = mode in ("drift", "bubble", "tape", "pulse"); bubble = mode in ("bubble", "tape", "pulse")
    for ch in (0, 1):
        cents = 4.0 * smooth_random(t, 0.7, seed * 2 + ch) if drift else np.zeros(len(t))
        phase = 2 * np.pi * np.cumsum(f * 2 ** (cents / 1200)) / SR + ph0(f, ch)
        fc = 650 * (1 + (0.35 * smooth_random(t, 3.0, seed * 7 + ch) if bubble else 0))
        s = np.zeros(len(t))
        for k in range(1, 16):
            if k * f > 5000: break
            a = np.sin(np.pi * k * 0.35) / k                                       # a 35 % pulse wave's overtones
            x = k * f / fc; g = 1 / np.sqrt(1 + x ** 4) * (1 + 0.5 * np.exp(-((x - 1) / 0.35) ** 2))   # muffled, slight resonance
            s += a * g * np.sin(k * phase)
        out[ch] = s
    if pulse:
        a = np.clip(t / 0.008, 0, 1); env = a * a * (3 - 2 * a) * np.exp(-t / 0.16)
        r = np.clip((t - dur) / 0.08, 0, 1); env *= np.cos(r * np.pi / 2) ** 2
    else:
        a = np.clip(t / 0.8, 0, 1); body = 0.8 + 0.2 * np.exp(-np.maximum(t - 0.8, 0) / 1.2)
        r = np.clip((t - dur) / 0.3, 0, 1); env = a * a * (3 - 2 * a) * body * np.cos(r * np.pi / 2) ** 2
    out *= env
    return hp(out, 70, 2)

def overtone_levels(nt, pulse=False):
    y = submerged(n(nt), 2.0, "clean", 1, pulse)[0]; seg = y[int(SR * 0.9):int(SR * 1.9)] if not pulse else y[:int(SR * 0.3)]
    N = 4 * SR; spec = np.abs(np.fft.rfft(seg * np.hanning(len(seg)), N)); fr = np.fft.rfftfreq(N, 1 / SR); f = n(nt)   # fine grid
    lvl = lambda g: spec[(fr > g * 0.985) & (fr < g * 1.015)].max(); base = lvl(f)
    return [(k, lvl(f * k) / base) for k in range(1, 12) if f * k < 5000]

def check(extra=()):
    """The rub rule: every overtone of the submerged notes against the chord (and chimes when used)."""
    probs = []
    up = lambda x: x[:-1] + str(int(x[-1]) + 1)
    use_line = SUB == "pulse" or GROOVE == "pulse"
    for ch in B.VOICING:
        vo = B.VOICING[ch]; others = [(b, "chord") for b in vo] + [(up(x), "chime") for x in vo[1:] if "motif" in extra]
        for nt in (LINE[ch] if use_line else HELD[ch]):
            for k, rel in overtone_levels(nt, use_line):
                f = n(nt) * k
                if any(abs(12 * np.log2(f / n(b))) < 0.1 for b in vo): continue
                for b, who in others:
                    g = n(b); s = abs(12 * np.log2(f / g))
                    if 0.3 < s < 2.6 and 15 < abs(f - g) < 120 and rel * 0.35 > 10 ** (-15 / 20):
                        probs.append(f"{nt} overtone {k} ({20 * np.log10(rel):.0f} dB) rubs with the {who}'s {b} in {ch}")
    if probs: raise SystemExit("Rub check failed:\n  " + "\n  ".join(sorted(set(probs))))

# ---------------------------------------------------------------- percussion (no pitch, soft, dark)
def kick(vel):
    t = T(0.9); f = 44 + 56 * np.exp(-t / 0.035)                     # long enough to die away fully
    a = np.clip(t / 0.003, 0, 1)
    return vel * np.sin(2 * np.pi * np.cumsum(f) / SR) * a * a * (3 - 2 * a) * np.exp(-t / 0.15)

def shaker(vel, seed):
    t = T(0.08); nz = np.random.default_rng(seed).standard_normal(len(t))
    a = np.clip(t / 0.002, 0, 1)
    return vel * lp(hp(nz, 2000, 4), 5000, 4) * a * a * (3 - 2 * a) * np.exp(-t / 0.022)

def snap(vel, seed):
    t = T(0.2); nz = np.random.default_rng(seed).standard_normal(len(t))
    a = np.clip(t / 0.002, 0, 1)
    return vel * lp(hp(nz, 900, 2), 2800, 4) * a * a * (3 - 2 * a) * np.exp(-t / 0.05)

# ---------------------------------------------------------------- building
def put(m, sig, t, pan=0.0):
    """Mix a sound in. Like videosound.add: the last 40 ms always fade to true silence, so a sound
    whose tail is still ringing when its buffer ends can never stop with a step (a click)."""
    sig = np.array(sig, dtype=float, copy=True); fd = min(int(SR * 0.04), sig.shape[-1] // 4)
    if fd > 0: sig[..., -fd:] *= np.cos(np.linspace(0, np.pi / 2, fd)) ** 2
    i = int(round(t * SR))
    if i >= m.shape[1]: return
    if sig.ndim == 1:
        l, r = np.cos((pan + 1) * np.pi / 4) * 1.414, np.sin((pan + 1) * np.pi / 4) * 1.414; sig = np.vstack([sig * l, sig * r])
    k = min(sig.shape[1], m.shape[1] - i); m[:, i:i + k] += sig[:, :k]

def sub_layer(cues, video, mode, line=False):
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L); m = np.zeros((2, int(SR * (L + 6))))
    for bar in bars:
        t0, ch, i = bar["t"], bar["chord"], bar["i"]
        if bar["end"] or t0 < 0: continue
        if line:
            if i == -1: continue                                       # the home chord rings alone
            for j in range(8):
                nt = LINE[ch][j % 2]; put(m, submerged(n(nt), 0.26, mode, 100 + j, pulse=True) * (1.0 if j % 2 == 0 else 0.8), t0 + j * BEAT / 2)
                V.MUSIC_ONSETS.append(t0 + j * BEAT / 2)
        else:
            d = (L - t0 + 1.0) if i == -1 else BAR * BEAT * 0.94
            for q, nt in enumerate(HELD[ch]): put(m, submerged(n(nt), d, mode, 10 * abs(i) + q), t0)
    if mode == "tape" and not line:                                   # steady tape hiss, no pops: shaped noise, very quiet
        nz = np.random.default_rng(31).standard_normal((2, m.shape[1]))
        hiss = lp(hp(nz, 1500, 2), 6000, 2); m += hiss / np.sqrt((hiss ** 2).mean()) * np.sqrt((m ** 2).mean()) * 0.06
    return m[:, :int(SR * L)]

def drums(cues, video, style):
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L); m = np.zeros((2, int(SR * (L + 6))))
    for bar in bars:
        t0, i = bar["t"], bar["i"]
        if bar["end"] or t0 < 0: continue
        if i == -1: put(m, kick(0.55), t0); V.MUSIC_ONSETS.append(t0); continue    # one last soft kick on the home chord
        kicks = (0, 1, 2, 3) if style == "softhouse" else (0, 2)
        for b in kicks: put(m, kick(0.6 if b in (0, 2) else 0.48), t0 + b * BEAT); V.MUSIC_ONSETS.append(t0 + b * BEAT)
        for b in range(4): put(m, shaker(0.07 if b % 2 else 0.055, 7 * abs(i) + b), t0 + (b + 0.5) * BEAT, 0.3); V.MUSIC_ONSETS.append(t0 + (b + 0.5) * BEAT)
        if style == "softhouse":
            for b in (1, 3): put(m, snap(0.05, 3 * abs(i) + b), t0 + b * BEAT, -0.15); V.MUSIC_ONSETS.append(t0 + b * BEAT)
    return m[:, :int(SR * L)]

def pump(cues, video, depth_db=2.0):
    """The chords breathe with the kick: a fixed, smooth dip after each beat 1 and 3, written in."""
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L); tt = np.arange(int(SR * L)) / SR; g = np.zeros_like(tt)
    for bar in bars:
        if bar["end"] or bar["t"] < 0 or bar["i"] == -1: continue
        for b in (0, 2):
            k = bar["t"] + b * BEAT; x = (tt - k) / (BEAT * 1.6)
            g += np.where((x >= 0) & (x < 1), np.sin(np.pi * np.clip(x, 0, 1) ** 0.6) * (1 - x), 0)
    return V.db(-depth_db * np.clip(g, 0, 1))

def dip(m, cues, video, db_=-6.0):
    L = V.LENGTH[video]; tt = np.arange(m.shape[1]) / SR
    pr = [(a, b) for nm, a, b in V.parts(cues, L) if nm == "packrow"]
    if pr:
        s0, e1 = pr[0]; g = np.interp(tt, [s0 - 0.6, s0 + 0.2, e1 - 0.4, e1 + 0.4], [0, 1, 1, 0]); m = m * V.db(db_ * g)
    return m

def finish(m, out, L, seconds=None):
    import pyloudnorm as pyln
    from scipy.signal import resample_poly
    meter = pyln.Meter(SR); m = V.clean_low_end(m)
    g = V.db(-16 - meter.integrated_loudness(m.T))
    tp = 20 * np.log10(np.abs(resample_poly(m * g, 4, 1, axis=1)).max())
    if tp > -1.1: g *= V.db(-1.1 - tp)
    m = V.fade_to_silence(m * g, L)
    V.check_audio(m, m, [], os.path.basename(out), parts=(m, np.zeros_like(m)))
    if seconds:
        s = int(SR * seconds); m = m[:, :s].copy(); f = int(SR * 1.5); m[:, -f:] *= np.cos(np.linspace(0, np.pi / 2, f)) ** 2
    V.save(out, m)

if __name__ == "__main__":
    import pyloudnorm as pyln
    what, cues_p, video, out = sys.argv[1:5]; seconds = float(sys.argv[5]) if len(sys.argv) > 5 else None
    cues = V.read_cues(cues_p)[video]; L = V.LENGTH[video]; meter = pyln.Meter(SR)
    V.MUSIC_ONSETS.clear(); V.ONSET_WINDOW = 0.15
    base = B.render(cues, video); lb = meter.integrated_loudness(base.T)
    at = lambda x, lu: x * V.db(lb + lu - meter.integrated_loudness(x.T))
    if what == "sound":
        check(); m = base + dip(at(sub_layer(cues, video, SUB, line=SUB == "pulse"), -7.0), cues, video)
    else:
        extra = ("motif",) if GROOVE == "softhouse" else ()
        if GROOVE == "pulse": SUB = "pulse"
        check(extra)
        m = base * (pump(cues, video)[None, :base.shape[1]] if GROOVE == "pulse" else 1.0)
        m = m + dip(at(drums(cues, video, GROOVE), -5.0), cues, video, -8.0)
        if GROOVE == "softhouse":
            import layers as LY; LY.LAYERS = ["motif"]; m = m + LY.layers(cues, video)
        if GROOVE == "pulse": m = m + dip(at(sub_layer(cues, video, "pulse", line=True), -8.0), cues, video)
    finish(m, out, L, seconds)
