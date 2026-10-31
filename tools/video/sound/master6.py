"""Round 41 (31 Oct 2026): Emre's round 40 arrangement, fitted to the remade videos, and the studio audit's polish.

    python master6.py <tracks-folder> <cues.md> <video> <out-folder> <arrangement>

The bar grid comes from the video (videosound.timeline: bars counted back from the end card at 92.3 bpm), and
each part is switched on or off per scene instead of per numbered bar, so the same arrangement fits any cut:
a part plays in a bar when that bar's scene is not in its 'off' list. Switches land on bar lines (20 ms curves).
Polish (round 41, from the audit of round 40): low mids 120-500 Hz were 10-11 dB over the mids (boomy) and almost
nothing above 2 kHz, so on a phone speaker the music lost 13 dB and vanished under the clicks; a broad -3 dB at
250 Hz and a gentle presence lift (+3 dB from 1.6 kHz, fixed) open it up without touching the underwater bloom.
"""
import os, sys
import numpy as np
import videosound as V
from videosound import BEAT, BAR, SR, db
from master import kick_dip as _kd
from scipy.signal import sosfilt

def peq(x, f0, gain_db, q=0.8):
    """Round 41: the same broad bell as round 40, but causal (minimum phase). Round 40's zero-phase version ran
    the filter backwards too, which spreads a faint pre-echo a few ms before every attack."""
    A = 10 ** (gain_db / 40); w = 2 * np.pi * f0 / SR; al = np.sin(w) / (2 * q)
    b = [1 + al * A, -2 * np.cos(w), 1 - al * A]; a = [1 + al / A, -2 * np.cos(w), 1 - al / A]
    return sosfilt(np.array([[b[0] / a[0], b[1] / a[0], b[2] / a[0], 1, a[1] / a[0], a[2] / a[0]]]), x, axis=-1)

ARR = {
  # round 40, mapped onto the remade scenes: the hero opening is the calm intro (no kick), the groove drops on the
  # first feature cut, the bloom and harmony join as the features deepen, the kick rests for one scene as before
  "r40": {
    "teaser": {
      "chords": ("chords", 0, []), "stabs": ("stabs-one", 0, []), "bass": ("bass-deep-bounce", 0, []),
      "bounce": ("bounce-tone", 0, []), "pulse": ("pulse-eighths", 0, []),
      "kick": ("kick", 0, ["hero", "devices"]),
      "stabecho": ("stab-echo", 0, ["hero"]), "motif": ("motif", 0, ["hero"]),
      "chordbass": ("chord-bass", 0, ["hero", "buttons", "words"]), "bloom": ("bloom", 0, ["hero", "buttons", "words"]),
      "harmony": ("harmony-full", 0, ["hero", "buttons", "words"]), "space": ("space", 0, ["hero", "buttons", "words"]),
      "repeat": ("bloom-repeat", 0, ["hero", "buttons", "words"]),
      "chordrep": ("chord-repeat-every", 0, ["hero", "buttons", "words", "holdrt"]),
    },
    "whats-new": {
      "chords": ("chords", 0, []), "stabs": ("stabs-one", 0, []), "bass": ("bass-deep-bounce", 0, []),
      "bounce": ("bounce-tone", 0, []), "pulse": ("pulse-eighths", 0, []),
      "kick": ("kick", 0, ["new", "packrow"]),
      "stabecho": ("stab-echo", 0, ["new"]), "motif": ("motif", 0, ["new", "packrow"]),
      "chordbass": ("chord-bass", 0, ["new", "words", "accents"]), "bloom": ("bloom", 0, ["new", "words", "accents"]),
      "harmony": ("harmony-full", 0, ["new", "words", "accents"]), "space": ("space", 0, ["new", "words", "accents"]),
      "repeat": ("bloom-repeat", 0, ["new", "words", "accents"]),
      "chordrep": ("chord-repeat-every", 0, ["new", "words", "accents", "devices", "packrow"]),
    }},
}
ARR["B"] = {
    "teaser": {"chords": ("chords", 0, []), "organ": ("organ", 0, []), "echo": ("echo", 0, ["tv"]),
               "kick": ("kick", 0, ["hero", "devices"]), "bass": ("bass", 0, ["hero"]),
               "snaps": ("snaps", 0, ["hero", "buttons"]), "shakers": ("shakers", 0, ["hero"])},
    "whats-new": {"chords": ("chords", 0, []), "organ": ("organ", 0, []), "echo": ("echo", 0, ["tv"]),
                  "kick": ("kick", 0, ["new", "packrow"]), "bass": ("bass", 0, ["new"]),
                  "snaps": ("snaps", 0, ["new", "words"]), "shakers": ("shakers", 0, ["new"])}}
import copy
# Round 42, option 3 ("more rising tension"): the same parts, entering sooner and building, with one breath before the
# finish: the bloom and harmony join on the words scene, the chord repeat from the first feature, the low end and kick
# rest together on the devices scene, so the TV scene lands as the big return; and the whole bed swells 4 dB across the video
ARR["a3"] = copy.deepcopy(ARR["r40"])
for k in ("chordbass", "bloom", "harmony", "space", "repeat"): ARR["a3"]["teaser"][k] = (ARR["r40"]["teaser"][k][0], 0, ["hero", "buttons"])
ARR["a3"]["teaser"]["chordrep"] = ("chord-repeat-every", 0, ["hero"])
for k in ("bass", "pulse", "bounce"): ARR["a3"]["teaser"][k] = (ARR["r40"]["teaser"][k][0], 0, ["devices"])
ARR["a4"] = copy.deepcopy(ARR["a3"]); ARR["a4"]["teaser"]["motifdeep"] = ("motif-deep", -6, ["hero"])   # the high chimes + their deep twin
ARR["a5"] = copy.deepcopy(ARR["a4"])
for k, dv in (("space", 3), ("repeat", 2), ("harmony", 2)): t_, v_, off_ = ARR["a5"]["teaser"][k]; ARR["a5"]["teaser"][k] = (t_, v_ + dv, off_)
SWELL = {"a3": (-4.0, "tv"), "a4": (-4.0, "tv"), "a5": (-4.0, "tv")}
# Round 42b (Emre: the peak should come around the middle / last third, then keep the momentum and slowly come down
# towards the end card): A5's parts with no breakdown, and two written-in curves, as (scene, dB) points at scene starts
ARR["a6"] = copy.deepcopy(ARR["a5"])
for k in ("bass", "pulse", "bounce"): t_, v_, off_ = ARR["a6"]["teaser"][k]; ARR["a6"]["teaser"][k] = (t_, v_, [])
ARR["a6"]["teaser"]["kick"] = ("kick", 0, ["hero"])
CURVES = {"a6": {"bed":  [("hero", -5.0), ("copy", -1.5), ("undo", 0.0), ("devices", 0.0), ("tv", -1.5), ("outro", -3.0)],
                 "kick": [("buttons", -10.0), ("copy", -2.0), ("undo", 0.0), ("accents", -1.0), ("devices", -4.0), ("tv", -7.0), ("outro", -11.0)]}}
KICK_RISE = {"a4": (-9.0, "buttons", "tv"), "a5": (-9.0, "buttons", "tv")}   # the beat grows in, scene by scene (dB, from, to)                                        # (start dB, scene where it reaches full)
POLISH = {"r40": [], "r41": [(250, -4.0, 0.7), (120, -2.0, 0.7)], "a1": [(250, -4.0, 0.7), (120, -2.0, 0.7)], "a2": [(250, -4.0, 0.7), (120, -2.0, 0.7)], "a3": [(250, -4.0, 0.7), (120, -2.0, 0.7)], "a4": [(250, -4.0, 0.7), (120, -2.0, 0.7)], "a5": [(250, -4.0, 0.7), (120, -2.0, 0.7)], "a6": [(250, -4.0, 0.7), (120, -2.0, 0.7)], "B": [(250, -3.0, 0.7), (2000, 1.0, 0.8)]}   # (Hz, dB, q) on top of round 40's own EQ
SHELF = {"r40": 0.0, "r41": 0.0, "B": 3.0, "a1": 0.0, "a2": 0.0, "a3": 0.0, "a4": 0.0, "a5": 0.0, "a6": 0.0}                                     # a gentle high shelf from 1.6 kHz (dB)

def scenes(cues, L):
    cuts = sorted((t, x.split(" begins")[0]) for t, k, x in cues if k in ("cut", "start"))
    return [(nm, t, cuts[i + 1][0] if i + 1 < len(cuts) else L) for i, (t, nm) in enumerate(cuts)]

def scene_at(sc, t):
    """The scene a bar belongs to: the one on screen a quarter-beat after the bar line (a cut just after a bar line
    belongs to the new scene once it has faded in)."""
    t = t + 0.6
    for nm, a, b in sc:
        if a <= t < b: return nm
    return sc[-1][0]

def bar_gates(n, bars, sc, off, e0):
    t = np.arange(n) / SR; g = np.ones(n)
    for bar in bars:
        a = max(bar["t"], 0.0); b = bar["t"] + BAR * BEAT
        nm = "end" if bar["end"] else scene_at(sc, max(bar["t"], 0.0))
        if nm in off: g[(t >= a - 0.005) & (t < b - 0.005)] = 0
    edges = np.nonzero(np.diff(g))[0]; r = int(SR * 0.02)
    for e in edges:
        a0 = max(0, e - r + 1); v0, v1 = g[a0 - 1] if a0 > 0 else g[0], g[e + 1]
        g[a0:e + 1] = v0 + (v1 - v0) * (0.5 - 0.5 * np.cos(np.linspace(0, np.pi, e + 1 - a0)))
    return g

def kick_dip(n, bars, sc, off, depth_db=3.0):
    t = np.arange(n) / SR; g = np.zeros(n)
    for bar in bars:
        if bar["end"] or bar["t"] < 0 or scene_at(sc, bar["t"]) in off: continue
        for b in range(4):
            s0 = bar["t"] + b * BEAT; x = (t - s0) / (0.3 * BEAT); a = np.clip((t - s0) / 0.008, 0, 1)
            g = np.maximum(g, np.where((x >= 0) & (x < 1), a * 0.5 * (1 + np.cos(np.pi * np.clip(x, 0, 1))), 0))
    return db(-depth_db * g)

def high_shelf(x, f0, gain_db):
    A = 10 ** (gain_db / 40); w = 2 * np.pi * f0 / SR; al = np.sin(w) / 2 * np.sqrt(2); c = np.cos(w); sA = 2 * np.sqrt(A) * al
    b = [A * ((A + 1) + (A - 1) * c + sA), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - sA)]
    a = [(A + 1) - (A - 1) * c + sA, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - sA]
    return sosfilt(np.array([[b[0] / a[0], b[1] / a[0], b[2] / a[0], 1, a[1] / a[0], a[2] / a[0]]]), x, axis=-1)

if __name__ == "__main__":
    import pyloudnorm as pyln
    from scipy.signal import resample_poly, butter, sosfiltfilt
    folder, cues_p, video, out, arr = sys.argv[1:6]; os.makedirs(out, exist_ok=True)
    mixname = arr; arr_key = "B" if arr == "B" else arr if arr in ("a3", "a4", "a5", "a6") else "r40"                       # r41 = round 40's arrangement with the polish
    L = V.LENGTH[video]; cues = V.read_cues(cues_p)[video]; bars, e0 = V.timeline(cues, L); sc = scenes(cues, L)
    print("bars:", " ".join(f"{max(b['t'],0):.2f}:{'end' if b['end'] else scene_at(sc, max(b['t'],0))}" for b in bars))
    MIX = ARR[arr_key][video]; parts = {}
    for k, (trk, vol, off) in MIX.items():
        x = V.read_wav(os.path.join(folder, trk + ".wav")); parts[k] = x * db(vol) * bar_gates(x.shape[1], bars, sc, off, e0)
    n = parts["chords"].shape[1]; dip = kick_dip(n, bars, sc, MIX["kick"][2])
    if mixname in CURVES:
        start = {nm: a for nm, a, b in sc}; tt = np.arange(n) / SR; cv = CURVES[mixname]
        pts = lambda c: ([start[k] for k, v in c], [v for k, v in c])
        x, y = pts(cv["kick"]); parts["kick"] = parts["kick"] * db(np.interp(tt, x, y))
    if mixname in KICK_RISE:                                          # the kick grows from quiet to full across the video
        d0, s0, s1 = KICK_RISE[mixname]; tt = np.arange(n) / SR; a = next(x for nm, x, y in sc if nm == s0); b = next(x for nm, x, y in sc if nm == s1)
        parts["kick"] = parts["kick"] * db(np.interp(tt, [a, b], [d0, 0.0]))
    for k in ("bass", "pulse"):
        if k in parts: parts[k] = parts[k] * dip
    mix = sum(parts.values())
    if mixname in CURVES:
        start = {nm: a for nm, a, b in sc}; tt = np.arange(mix.shape[1]) / SR; c = CURVES[mixname]["bed"]
        mix = mix * db(np.interp(tt, [start[k] for k, v in c], [v for k, v in c]))
    if mixname in SWELL:                                             # a smooth, fixed volume curve written in (no moving parts)
        d0, full = SWELL[mixname]; tt = np.arange(mix.shape[1]) / SR; tf = next(a for nm, a, b in sc if nm == full)
        mix = mix * db(np.interp(tt, [0, tf], [d0, 0.0]))
    for f0, gdb in [(300, -2.0), (2000, 1.5)]: mix = peq(mix, f0, gdb)          # round 40's measured EQ (now causal)
    for f0, gdb, q in POLISH[mixname]: mix = peq(mix, f0, gdb, q)
    if SHELF[mixname]: mix = high_shelf(mix, 1600, SHELF[mixname])
    mid, side = (mix[0] + mix[1]) / 2, (mix[0] - mix[1]) / 2
    side = sosfilt(butter(2, 180, "highpass", fs=SR, output="sos"), side); mix = np.vstack([mid + side, mid - side])
    meter = pyln.Meter(SR); mix = V.clean_low_end(mix)
    fx = V.soften_fx(V.effects(video, cues, L)); fx = fx[:, :mix.shape[1]]
    fxg = db(meter.integrated_loudness(mix.T) - V.MUSIC_OVER_FX - meter.integrated_loudness(fx.T))
    g = db(-15 - meter.integrated_loudness(mix.T)); tp = 20 * np.log10(np.abs(resample_poly(mix * g, 4, 1, axis=1)).max())
    if tp > -1.1: g *= db(-1.1 - tp)
    music = V.fade_to_silence(mix * g, L)
    V.MUSIC_ONSETS.clear(); V.ONSET_WINDOW = 0.15
    for b in bars: V.MUSIC_ONSETS.extend([b["t"] + k * BEAT / 4 for k in range(16)])
    V.check_audio(music, music, [], f"{video} music ({mixname})", parts=(music, np.zeros_like(music)))
    V.save24(os.path.join(out, f"{video}-music.wav"), music)
    video_mix = mix + fx * fxg
    gv = db(-15 - meter.integrated_loudness(video_mix.T)); tpv = 20 * np.log10(np.abs(resample_poly(video_mix * gv, 4, 1, axis=1)).max())
    if tpv > -1.1: gv *= db(-1.1 - tpv)
    m_part, f_part = V.fade_to_silence(mix * gv, L), V.fade_to_silence(fx * fxg * gv, L); vid = m_part + f_part
    V.check_audio(m_part, vid, V.FX_ONSETS, f"{video} video mix ({mixname})", parts=(m_part, f_part))
    V.save24(os.path.join(out, f"{video}-mix.wav"), vid); V.save24(os.path.join(out, f"{video}-effects.wav"), f_part)
    print(f"{video} {mixname}: video mix {meter.integrated_loudness(vid.T):.1f} LUFS, true peak {20*np.log10(np.abs(resample_poly(vid, 4, 1, axis=1)).max()):.1f} dBTP")
