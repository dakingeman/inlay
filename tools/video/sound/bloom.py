"""1B as Emre liked it (round 18, 27 Oct ~23:50), kept exactly, with only the rub softened (round 26).

    STYLE=soft|sing|ripple python bloom.py <cues.md> <video> <out.wav>      (music only)

What 1B is: Am-Em-G-C at 92 bpm; every bar the whole chord blooms in (1.3 s curved swell) while the
last one fades (0.9 s), so the music breathes ~4 dB a bar; close, warm voicings whose inner and top
notes step from chord to chord (top G-A-A-E, inner C-D-D-C), heard as the melody; a near-pure tone
with a faint octave, nothing above 2 kHz, a 2-cent left/right shimmer; a soft bass held a whole bar;
the last chord (C add9) blooms and rings out into the end card.
What changes: in each pair of notes a tone apart (which rub), the colour note plays 7 dB softer and
sits on the other side of the stereo picture. Two tones rub less the more unequal they are, and on
headphones the two never reach the same ear.
"""
import os, sys
import numpy as np
import videosound as V
from videosound import T, n, lp, hp, ph0, BEAT, BAR, SR

STYLE = os.environ.get("STYLE", "soft")
VOICING = {"Am": ("A3", "C4", "E4", "G4"), "Em": ("G3", "D4", "E4", "A4"), "G": ("G3", "D4", "E4", "A4"), "C": ("G3", "C4", "D4", "E4")}
COLOUR = {"Em": "D4", "G": "E4", "C": "D4"}          # the softer note of each rubbing pair (Am has none)
BASS = {"Am": "A2", "Em": "E3", "G": "G2", "C": "C3"}

def bloom(notes, dur, vel, levels, pans):
    """1B's chord exactly (atmopad), but each note with its own level and place."""
    t = T(dur + 5.0); out = np.zeros((2, len(t)))
    a = np.clip(t / 1.3, 0, 1); sw = a * a * (3 - 2 * a)
    shape = sw * np.where(t < dur, 1.0, np.exp(-(t - dur) / 0.9))
    for nt, lv, pan in zip(notes, levels, pans):
        f = n(nt); l, r = np.cos((pan + 1) * np.pi / 4) * 1.414, np.sin((pan + 1) * np.pi / 4) * 1.414
        for ch, c, g in ((0, -2, l), (1, 2, r)):
            ff = f * 2 ** (c / 1200)
            out[ch] += g * lv * (np.sin(2 * np.pi * ff * t + ph0(ff, 1)) + 0.12 * np.sin(2 * np.pi * 2 * ff * t + ph0(ff, 2))) * shape
    return vel * lp(hp(out, 140), 2000) / len(notes)

def chord_parts(ch):
    notes = VOICING[ch]; top = notes[-1]
    levels, pans = [], []
    for k, nt in enumerate(notes):
        lv = 1.0; pan = (-0.25, 0.25, -0.1, 0.1)[k]
        if COLOUR.get(ch) == nt:
            lv = 10 ** (float(os.environ.get("COLOUR_DB", "-7")) / 20)
            nb = notes[k - 1] if k else notes[k + 1]                          # the neighbour it rubs with
            pan = 0.9 if (-0.25, 0.25, -0.1, 0.1)[notes.index(nb)] < 0 else -0.9
            if ch == "C": pan = 0.0                                           # C's D sits between C and E: soft and centred
        if STYLE == "sing" and nt == top: lv *= 1.5                          # the top line steps forward a little
        levels.append(lv); pans.append(pan)
    return notes, levels, pans

def glass(f, vel):
    t = T(0.6); s = (np.sin(2 * np.pi * f * t) + 0.15 * np.sin(2 * np.pi * 2 * f * t)) * np.exp(-t / 0.18)
    return vel * hp(s * np.clip(t / 0.004, 0, 1), 250)

RIPPLE = {"Am": ("C5", "E5", "C6", "E5"), "Em": ("E5", "A5", "D6", "A5"), "G": ("D5", "A5", "D6", "A5"), "C": ("C5", "G5", "E6", "G5")}   # Am: A4 -> C5 and G: G4 -> D5, A5 (they rubbed with the chord); the rest as Emre first heard it

def render(cues, video):
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L)
    m = np.zeros((2, int(SR * (L + 6))))
    def put(sig, t):
        i = int(round(t * SR)); sig = sig if sig.ndim == 2 else np.vstack([sig, sig]) * 0.707
        k = min(sig.shape[1], m.shape[1] - i); m[:, i:i + k] += sig[:, :k]
    for bar in bars:
        t0, ch, i = bar["t"], bar["chord"], bar["i"]
        if bar["end"]: continue
        if t0 < 0:
            if t0 + BAR * BEAT > 0:
                nts, lv, pn = chord_parts(ch); put(bloom(nts, t0 + BAR * BEAT, 0.42, lv, pn), 0.0)
            continue
        if i == -1:
            nts, lv, pn = chord_parts("C"); put(bloom(nts, L - t0 + 1.5, 0.42, lv, pn), t0)
            put(V.sub(n("C2"), L - t0, 0.16), t0)
            continue
        nts, lv, pn = chord_parts(ch); put(bloom(nts, BAR * BEAT, 0.42, lv, pn), t0)
        put(V.softbass(n(BASS[ch]), BAR * BEAT * 0.97, 0.15), t0)
        if STYLE == "ripple" and __name__ == "__main__":       # a soft glass ripple high above, one note a beat
            for j, nt in enumerate(RIPPLE[ch]): put(glass(n(nt), 0.05 if j else 0.06), t0 + j * BEAT)
    m = m[:, :int(SR * L)]
    tt = np.arange(m.shape[1]) / SR
    m *= V.db(np.interp(tt, [e0, L], [0.0, -18.0], left=0.0, right=-18.0))   # the end card fade, as in 1B
    pr = [(a, b) for nm, a, b in V.parts(cues, L) if nm == "packrow"]
    if pr:
        s0, e1 = pr[0]; g = np.interp(tt, [s0 - 0.6, s0 + 0.2, e1 - 0.4, e1 + 0.4], [0, 1, 1, 0]); m *= V.db(-4.0 * g)
    return m

if __name__ == "__main__":
    import pyloudnorm as pyln
    from scipy.signal import resample_poly
    from mosqito.sq_metrics import roughness_dw
    cues = V.read_cues(sys.argv[1])[sys.argv[2]]; L = V.LENGTH[sys.argv[2]]
    m = V.clean_low_end(render(cues, sys.argv[2]))
    meter = pyln.Meter(SR); g = V.db(-16 - meter.integrated_loudness(m.T))
    tp = 20 * np.log10(np.abs(resample_poly(m * g, 4, 1, axis=1)).max())
    if tp > -1.1: g *= V.db(-1.1 - tp)
    m = V.fade_to_silence(m * g, L)
    V.MUSIC_ONSETS.clear(); V.check_audio(m, m, [], f"1B-{STYLE}", parts=(m, np.zeros_like(m)))
    V.save(sys.argv[3], m)
    R, _, _, _ = roughness_dw(m.mean(0)[SR:int(SR * (L - 3))], SR, overlap=0.5)
    Rl, _, _, _ = roughness_dw(m[0][SR:int(SR * (L - 3))], SR, overlap=0.5)
    print(f"1B-{STYLE}: rub (roughness) on a speaker {R.mean():.3f} avg / {R.max():.2f} worst, in one headphone ear {Rl.mean():.3f} / {Rl.max():.2f}")
