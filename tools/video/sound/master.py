"""Round 39: the professional mix of Emre's saved arrangement (mixer page, saved 28 Oct 15:54).

    python master.py <tracks-folder> <cues.md> <out-folder> [analyse]

1. his tracks and choices, his volumes, his bar grid (20 ms fades at the bar lines, as on the page)
2. low end: the bass and the sub pulse dip 3 dB for a moment after each kick (a fixed, smooth curve)
3. tonal balance: small, broad corrections from the measured spectrum (at most 2 dB)
4. master: -15 LUFS, true peak at most -1 dBTP, fade to silence, all checks; 24-bit WAV
"""
import os, sys, json
import numpy as np
import videosound as V
from videosound import BEAT, BAR, SR, lp, hp, db

SEG = [0, 1.17, 3.77, 6.37, 8.97, 11.57, 14.17, 16.77, 19.37, 21.97, 24.57, 27.17, 29.77, 32.37, 34.97]
SEGNAME = ["intro"] + [f"bar {i}" for i in range(1, 14)] + ["end card"]
MIX = {  # part: (track, volume dB, bars off)  - from the saved mix; muted parts left out
    "chords":    ("chords", 0, []),
    "chordbass": ("chord-bass", 0, ["intro", "bar 1", "bar 2", "bar 3", "bar 4"]),
    "bloom":     ("bloom", 0, ["intro", "bar 1", "bar 2", "bar 3", "bar 4"]),
    "harmony":   ("harmony-full", 0, ["intro", "bar 1", "bar 2", "bar 3", "bar 4"]),
    "space":     ("space", 0, ["intro", "bar 1", "bar 2", "bar 3", "bar 4"]),
    "repeat":    ("bloom-repeat", 0, ["intro", "bar 1", "bar 2", "bar 3", "bar 4"]),
    "chordrep":  ("chord-repeat-every", 0, ["intro", "bar 1", "bar 2", "bar 3", "bar 4", "bar 5"]),
    "kick":      ("kick", 0, ["intro", "bar 8"]),
    "stabs":     ("stabs-one", 0, []),
    "stabecho":  ("stab-echo", 0, ["intro", "bar 1", "bar 2", "bar 3"]),
    "motif":     ("motif", 0, ["intro", "bar 1", "bar 2", "bar 3"]),
    "bass":      ("bass-deep-bounce", 0, []),
    "bounce":    ("bounce-tone", 0, []),
    "pulse":     ("pulse-eighths", 0, []),
}

def bar_gate(n, off):
    g = np.ones(n); t = np.arange(n) / SR
    on = [nm not in off for nm in SEGNAME]
    for k in range(len(SEG)):
        a = SEG[k]; b = SEG[k + 1] if k + 1 < len(SEG) else n / SR
        if not on[k]: g[(t >= a - 0.005) & (t < b - 0.005)] = 0
    # soften every on/off edge into a 20 ms raised cosine ending at the bar line (as the page does)
    edges = np.nonzero(np.diff(g))[0]; r = int(SR * 0.02)
    for e in edges:
        a0 = max(0, e - r + 1); v0, v1 = g[a0 - 1] if a0 > 0 else g[0], g[e + 1]
        g[a0:e + 1] = v0 + (v1 - v0) * (0.5 - 0.5 * np.cos(np.linspace(0, np.pi, e + 1 - a0)))
    return g

def kick_dip(n, kick_off, depth_db=3.0):
    """After each main kick (every beat, where the kick plays): down 3 dB at once-ish (8 ms), back over 0.3 beat."""
    t = np.arange(n) / SR; g = np.zeros(n)
    for k in range(1, 14):
        if SEGNAME[k] in kick_off: continue
        for b in range(4):
            x = (t - (SEG[k] + b * BEAT)) / (0.3 * BEAT)
            a = np.clip((t - (SEG[k] + b * BEAT)) / 0.008, 0, 1)
            g = np.maximum(g, np.where((x >= 0) & (x < 1), a * 0.5 * (1 + np.cos(np.pi * np.clip(x, 0, 1))), 0))
    return db(-depth_db * g)

def third_octaves(x):
    f = 1000 * 2 ** (np.arange(-15, 13) / 3); X = np.abs(np.fft.rfft(x.mean(0))) ** 2; fr = np.fft.rfftfreq(x.shape[1], 1 / SR)
    return f, np.array([10 * np.log10(X[(fr >= c / 2 ** (1 / 6)) & (fr < c * 2 ** (1 / 6))].sum() + 1e-20) for c in f])

def peq(x, f0, gain_db, q=0.8):
    """A broad, gentle bell (RBJ peaking EQ)."""
    from scipy.signal import sosfiltfilt
    A = 10 ** (gain_db / 40); w = 2 * np.pi * f0 / SR; al = np.sin(w) / (2 * q)
    b = [1 + al * A, -2 * np.cos(w), 1 - al * A]; a = [1 + al / A, -2 * np.cos(w), 1 - al / A]
    sos = np.array([[b[0] / a[0], b[1] / a[0], b[2] / a[0], 1, a[1] / a[0], a[2] / a[0]]])
    return sosfiltfilt(sos, x, axis=-1)                                  # zero-phase: no smearing of the timing

EQ = [(300, -2.0), (2000, 1.5)]   # measured: 200-400 Hz piled up 11-14 dB over the mids (mud); little presence for phone speakers

if __name__ == "__main__":
    import pyloudnorm as pyln
    from scipy.signal import resample_poly
    folder, cues_p, out = sys.argv[1:4]; analyse = len(sys.argv) > 4; os.makedirs(out, exist_ok=True)
    L = V.LENGTH["teaser"]; cues = V.read_cues(cues_p)["teaser"]
    parts = {}
    for k, (trk, vol, off) in MIX.items():
        x = V.read_wav(os.path.join(folder, trk + ".wav")); parts[k] = x * db(vol) * bar_gate(x.shape[1], off)
    n = parts["chords"].shape[1]; dip = kick_dip(n, MIX["kick"][2])
    for k in ("bass", "pulse"): parts[k] = parts[k] * dip
    mix = sum(parts.values())
    low = sum(parts[k] for k in ("kick", "bass", "pulse", "chordbass"))
    f, p = third_octaves(mix)
    if analyse:
        ref = p[(f > 400) & (f < 1200)].mean()
        print("third-octave balance (dB, relative to 400-1200 Hz):")
        for c, v in zip(f, p): print(f"  {c:7.0f} Hz  {v - ref:6.1f}  {'#' * max(0, int((v - ref + 40) / 2))}")
        corr = np.corrcoef(mix[0], mix[1])[0, 1]; print(f"left/right correlation {corr:.2f} (1 = mono, below 0 = phase trouble)")
        print(f"low end (kick + bass + pulse + chord bass) share of the mix: {10*np.log10((low**2).sum()/(mix**2).sum()):.1f} dB")
        sys.exit()
    for f0, gdb in EQ: mix = peq(mix, f0, gdb)
    mid, side = (mix[0] + mix[1]) / 2, (mix[0] - mix[1]) / 2          # everything under ~180 Hz in the centre (mono low end)
    from scipy.signal import butter, sosfiltfilt
    side = sosfiltfilt(butter(2, 180, "highpass", fs=SR, output="sos"), side); mix = np.vstack([mid + side, mid - side])
    meter = pyln.Meter(SR); mix = V.clean_low_end(mix)
    st, mo = meter.integrated_loudness(mix.T), meter.integrated_loudness(np.vstack([mix.mean(0)] * 2).T)
    print(f"mono check: a mono speaker plays it {st - mo:.1f} dB quieter (under 1.5 dB is safe)")
    # the video version: the app's own sound effects on top, at the balance Emre approved (music 7 LU over the clicks)
    fx = V.soften_fx(V.effects("teaser", cues, L)); fx = fx[:, :mix.shape[1]]
    fxg = db(meter.integrated_loudness(mix.T) - V.MUSIC_OVER_FX - meter.integrated_loudness(fx.T))
    video = mix + fx * fxg
    g = db(-15 - meter.integrated_loudness(mix.T)); tp = 20 * np.log10(np.abs(resample_poly(mix * g, 4, 1, axis=1)).max())
    if tp > -1.1: g *= db(-1.1 - tp)
    mix = V.fade_to_silence(mix * g, L)
    V.MUSIC_ONSETS.clear(); V.ONSET_WINDOW = 0.15
    for s in SEG: V.MUSIC_ONSETS.extend([s + b * BEAT / 4 for b in range(16)])   # every 16th: the parts' own note starts
    V.check_audio(mix, mix, [], "music master", parts=(mix, np.zeros_like(mix)))
    V.save24(os.path.join(out, "teaser-music-master.wav"), mix)
    gv = db(-15 - meter.integrated_loudness(video.T)); tpv = 20 * np.log10(np.abs(resample_poly(video * gv, 4, 1, axis=1)).max())
    if tpv > -1.1: gv *= db(-1.1 - tpv)
    m_part, f_part = V.fade_to_silence(mix / g * gv, L), V.fade_to_silence(fx * fxg * gv, L); vid = m_part + f_part
    V.check_audio(m_part, vid, V.FX_ONSETS, "video mix (music + effects)", parts=(m_part, f_part))
    V.save24(os.path.join(out, "teaser-video-mix.wav"), vid); V.save24(os.path.join(out, "teaser-video-effects.wav"), f_part)
    print(f"video mix: {meter.integrated_loudness(vid.T):.1f} LUFS, true peak {20*np.log10(np.abs(resample_poly(vid, 4, 1, axis=1)).max()):.1f} dBTP")
    print(f"master: {meter.integrated_loudness(mix.T):.1f} LUFS, true peak {20*np.log10(np.abs(resample_poly(mix, 4, 1, axis=1)).max()):.1f} dBTP")
