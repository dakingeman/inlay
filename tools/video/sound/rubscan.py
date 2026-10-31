"""Find rubs in real audio: two tones close in pitch (0.3-2.6 semitones, 15-120 Hz apart), within
15 dB of each other, both clearly audible, ringing together for at least 0.15 s.

    rubs(x)                    -> list of (time, f1, f2, level difference)
    new_rubs(mix, base)        -> the rubs in mix that the base (the locked chords) doesn't already have

Spectra of 0.5 s Blackman-Harris windows every 50 ms (1 Hz grid, enough to separate 15 Hz), peaks above
-45 dB of the loudest in 90-1600 Hz."""
import numpy as np
from scipy.signal import stft, find_peaks
SR = 48000

def rubs(x, lo=90, hi=1600):
    mono = x.mean(0) if x.ndim == 2 else x
    f, t, Z = stft(mono, SR, window="blackmanharris", nperseg=int(SR * 0.5), noverlap=int(SR * 0.45), nfft=SR)   # no ghost side peaks
    P = 20 * np.log10(np.abs(Z) + 1e-12); band = (f >= lo) & (f <= hi); fb = f[band]
    top = P[band].max(); hits = []
    for j in range(len(t)):
        col = P[band, j]
        pk, _ = find_peaks(col, height=top - 45, prominence=12, distance=4)
        for a in range(len(pk)):
            for b in range(a + 1, len(pk)):
                f1, f2 = fb[pk[a]], fb[pk[b]]; semi = abs(12 * np.log2(f2 / f1))
                k = f1 / (f2 - f1)                          # neighbouring overtones of ONE note (k x d and (k+1) x d) don't rub
                if abs(k - round(k)) < 0.06 and round(k) >= 1: continue
                if 0.3 < semi < 2.6 and 15 < f2 - f1 < 120 and abs(col[pk[a]] - col[pk[b]]) < 15:
                    hits.append((t[j], f1, f2, abs(col[pk[a]] - col[pk[b]])))
    return hits

def persistent(hits, min_s=0.15):
    """Keep pairs that last: the same pair (within 3 Hz) in consecutive windows for min_s or more."""
    out = []; by = {}
    for t, f1, f2, d in hits: by.setdefault((round(f1 / 3), round(f2 / 3)), []).append(t)
    for (a, b), ts in by.items():
        ts = sorted(ts); run = [ts[0]]
        for t in ts[1:]:
            if t - run[-1] <= 0.06: run.append(t)
            else:
                if run[-1] - run[0] >= min_s: out.append((run[0], run[-1], a * 3, b * 3))
                run = [t]
        if run[-1] - run[0] >= min_s: out.append((run[0], run[-1], a * 3, b * 3))
    return sorted(out)

def new_rubs(mix, base):
    old = persistent(rubs(base))
    def known(r):
        return any(abs(r[2] - o[2]) <= 4 and abs(r[3] - o[3]) <= 4 and r[0] < o[1] + 0.2 and r[1] > o[0] - 0.2 for o in old)
    return [r for r in persistent(rubs(mix)) if not known(r)]
