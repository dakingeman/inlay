"""Layers on top of the locked 1B base (round 27). Music only.

    LAYERS=ripple,glow python layers.py <cues.md> <video> <out.wav>

Base: bloom.py STYLE=soft (1B exactly, colour notes 7 dB softer). Every layer plays only notes that
are already in the chord (same octave) or a safe distance above it, so nothing rubs:
  ripple  soft glass notes high above, one a beat (Emre liked it)
  glow    a second synth: a hollow, slightly breathy tone doubling the chords' top line an octave up,
          blooming with each chord, so the hidden melody gets a new colour without new notes
  pluck   a soft plucked synth on beats 2 and 4, two chord notes a bar
  chime   deep, soft house-style chimes on the off-beats (the 'and' of each beat), very quiet
  motif   the same chimes in a 3-3-2 house rhythm (beats 1, 2.5, 4) with a two-bar motif
"""
import os, sys
import numpy as np
import videosound as V
import bloom as B
from videosound import T, n, lp, hp, ph0, BEAT, BAR, SR

LAYERS = [x for x in os.environ.get("LAYERS", "").split(",") if x]
B.STYLE = "soft"

TOP = {"Am": "G4", "Em": "A4", "G": "A4", "C": "E4"}            # the chords' top line (the melody you hear)
up = lambda nt, k=1: nt[:-1] + str(int(nt[-1]) + k)

def glow(f, dur, vel):
    """Hollow and soft: odd overtones only (like a gentle square wave, rounded off), a slow bloom
    like the chords but a touch quicker, so it speaks just before them."""
    t = T(dur + 3.0)
    s = (np.sin(2 * np.pi * f * t + ph0(f, 1)) + 0.14 * np.sin(2 * np.pi * 3 * f * t + ph0(f, 3)) + 0.04 * np.sin(2 * np.pi * 5 * f * t + ph0(f, 5)))
    a = np.clip(t / 0.9, 0, 1); s *= a * a * (3 - 2 * a) * np.where(t < dur, 1.0, np.exp(-(t - dur) / 0.8))
    out = np.vstack([s, np.roll(s, int(SR * 0.012))])           # 12 ms between ears: width without detune
    return vel * lp(hp(out, 200), 2600)

def pluck(f, vel):
    """A soft plucked synth: overtones 1-6 falling away, the higher ones fading first (like a string)."""
    t = T(1.2); s = np.zeros(len(t))
    for k in range(1, 5): s += (1 / k ** 1.8) * np.sin(2 * np.pi * k * f * t + ph0(f, k)) * np.exp(-t * (2.5 + 2.2 * k))
    a = np.clip(t / 0.005, 0, 1)
    return vel * lp(hp(s * a * a * (3 - 2 * a), 180), 2400, 4)

def chime(f, vel):
    """A deep, soft house chime: a warm FM bell (1:1, whole-number overtones only) whose brightness
    drops away in 0.12 s, leaving a round tone ringing about half a second."""
    t = T(1.0); idx = 0.9 * np.exp(-t / 0.10) + 0.15        # a gentle strike: deep and round
    s = np.sin(2 * np.pi * f * t + idx * np.sin(2 * np.pi * f * t)) * np.exp(-t / 0.28)
    a = np.clip(t / 0.006, 0, 1)                                 # a 6 ms start: soft, not clicky
    return vel * lp(hp(s * a * a * (3 - 2 * a), 200), 2200, 4)

def layers(cues, video):
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L)
    m = np.zeros((2, int(SR * (L + 6))))
    def put(sig, t, pan=0.0):
        i = int(round(t * SR))
        if i >= m.shape[1]: return
        if sig.ndim == 1:
            l, r = np.cos((pan + 1) * np.pi / 4) * 1.414, np.sin((pan + 1) * np.pi / 4) * 1.414; sig = np.vstack([sig * l, sig * r])
        k = min(sig.shape[1], m.shape[1] - i); m[:, i:i + k] += sig[:, :k]; V.MUSIC_ONSETS.append(t)   # a note's start is not a click
    pr = [(a, b) for nm, a, b in V.parts(cues, L) if nm == "packrow"]
    for bar in bars:
        t0, ch, i = bar["t"], bar["chord"], bar["i"]
        if bar["end"] or t0 < 0: continue
        last = i == -1
        vo = B.VOICING[ch]
        if "glow" in LAYERS:
            put(glow(n(up(TOP[ch])), (L - t0 + 1.0) if last else BAR * BEAT, 0.045), t0)
        if last: continue                                        # the home chord rings alone into the end card
        if "ripple" in LAYERS:
            for j, nt in enumerate(B.RIPPLE[ch]): put(B.glass(n(nt), 0.05 if j else 0.06), t0 + j * BEAT)
        if "pluck" in LAYERS:
            for bt, nt in ((1, up(vo[2])), (3, up(vo[1]))): put(pluck(n(nt), 0.06), t0 + bt * BEAT, (-0.35, 0.35)[bt == 3])
        if "chime" in LAYERS:                                    # the off-beats, alternating two chord notes an octave up
            for j in range(4): put(chime(n(up(vo[3 - (j % 2)])), 0.04 if j % 2 else 0.05), t0 + (j + 0.5) * BEAT, (0.3, -0.3)[j % 2])
        if "motif" in LAYERS:                                    # 3-3-2: beats 1, 2.5 and 4, a two-bar motif
            a, b2 = (vo[3], vo[2]) if (i % 2 == 0) else (vo[2], vo[3])
            dn = (lambda x: x) if os.environ.get("CHIMES_DEEP") else up        # round 42: CHIMES_DEEP plays the motif an octave lower
            for bt, nt, v in ((0, dn(a), 0.055), (1.5, dn(b2), 0.045), (3, dn(vo[1]), 0.04)): put(chime(n(nt), v * (1.25 if os.environ.get("CHIMES_DEEP") else 1)), t0 + bt * BEAT, 0.25 if bt == 1.5 else -0.15)
    m = m[:, :int(SR * L)]; tt = np.arange(m.shape[1]) / SR
    if pr:                                                       # the layers bow out further under the sound packs
        s0, e1 = pr[0]; g = np.interp(tt, [s0 - 0.6, s0 + 0.2, e1 - 0.4, e1 + 0.4], [0, 1, 1, 0]); m *= V.db(-6.0 * g)
    return m

def check_layers():
    """The rub rule for the layers: every layer note against every chord note sounding with it."""
    problems = []
    for ch, vo in B.VOICING.items():
        notes = set()
        if "glow" in LAYERS: notes.add(up(TOP[ch]))
        if "ripple" in LAYERS: notes |= set(B.RIPPLE[ch])
        if "pluck" in LAYERS: notes |= {up(vo[2]), up(vo[1])}
        if "chime" in LAYERS or "motif" in LAYERS: notes |= {up(vo[3]), up(vo[2]), up(vo[1])}
        for a in notes:
            for b in vo:
                fa, fb = n(a), n(b); s = abs(12 * np.log2(fa / fb))
                if 0.3 < s < 2.6 and 15 < abs(fa - fb) < 120: problems.append(f"{a} rubs with the chord's {b} in {ch}")
    if problems: raise SystemExit("Layer check failed:\n  " + "\n  ".join(problems))

if __name__ == "__main__":
    import pyloudnorm as pyln
    from scipy.signal import resample_poly
    check_layers(); V.MUSIC_ONSETS.clear(); V.ONSET_WINDOW = 0.15
    cues = V.read_cues(sys.argv[1])[sys.argv[2]]; L = V.LENGTH[sys.argv[2]]
    m = V.clean_low_end(B.render(cues, sys.argv[2]) + layers(cues, sys.argv[2]))
    meter = pyln.Meter(SR); g = V.db(-16 - meter.integrated_loudness(m.T))
    tp = 20 * np.log10(np.abs(resample_poly(m * g, 4, 1, axis=1)).max())
    if tp > -1.1: g *= V.db(-1.1 - tp)
    m = V.fade_to_silence(m * g, L)
    V.check_audio(m, m, [], "+".join(LAYERS), parts=(m, np.zeros_like(m)))
    V.save(sys.argv[3], m)
