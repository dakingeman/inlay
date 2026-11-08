"""Inlay's adaptive music (round 43, 31 Oct 2026): one song, fitted to any video by a small profile file.

    python arrange.py <profile.json> <cues.md> <tracks-folder> <out-folder>

The song (docs/brand/MUSIC.md) is a set of parts on one grid: C major pentatonic in equal tuning, Am - Em - G - C at
92.3 bpm, bars counted back from the video's end card so the end card always lands on a downbeat and the home chord
rings out over it. stems6.py renders every part for a video (TUNING=equal END_SHIFT=1), motifdeep.py the deep chimes.

A profile says how much energy each scene has, and the engine does the rest:
  - ENERGY LEVELS 0-4 decide which parts play (each part has the lowest level it plays at, PARTS below)
  - the energy also writes two smooth volume curves: the whole bed, and the kick (the beat 'manifests' as it rises)
  - a 'breath' scene (e.g. the sound packs, where the app's own samples need room) drops to a low level, kick off
Energy can be given per scene ("levels") or as an arc ("arc": start level, the peak's position as a fraction of the
video, the peak level, the end level), so a new video needs only its cue sheet and, if wanted, one line of profile.
Curves can also be given exactly ("curves"), which is how the approved teaser mix (A6) is pinned.
Everything else is the approved, measured chain from master6.py: causal EQ, mono lows, -15 LUFS music, the app's
key sounds 7 LU under the music, no limiter, compressor or ducking, every check, 24-bit files.
"""
import os, sys, json
import numpy as np
import videosound as V
from videosound import BEAT, BAR, SR, db
import master6 as M

# part: (track file, volume dB, lowest energy level it plays at)
PARTS = {"chords": ("chords", 0, 0), "stabs": ("stabs-one", 0, 0), "bass": ("bass-deep-bounce", 0, 0), "bounce": ("bounce-tone", 0, 0),
         "pulse": ("pulse-eighths", 0, 0), "kick": ("kick", 0, 1), "stabecho": ("stab-echo", 0, 1), "motif": ("motif", 0, 1),
         "motifdeep": ("motif-deep", -6, 1), "chordrep": ("chord-repeat-every", 0, 1), "chordbass": ("chord-bass", 0, 2),
         "bloom": ("bloom", 0, 2), "harmony": ("harmony-full", 2, 2), "space": ("space", 3, 2), "repeat": ("bloom-repeat", 2, 2)}
NO_KICK_IN_BREATH = True
BED = (-5.0, 0.0)       # bed volume at energy 0 and at energy 4 (dB)
KICK = (-11.0, 0.0)     # kick volume at energy 1 and at energy 4 (dB)

def energies(profile, sc, L):
    """{scene: energy 0-4} from the profile."""
    names = [nm for nm, a, b in sc]
    if "levels" in profile: return {nm: profile["levels"].get(nm, 0) for nm in names}
    a = profile["arc"]; e0 = max(a for nm, a, b in sc if nm == "outro")
    out = {}
    for nm, s, b in sc:
        if nm == "outro": out[nm] = a.get("end", 1.5); continue
        x = (s + b) / 2 / e0; p = a.get("peak_at", 0.65)
        lv = a["start"] + (a["top"] - a["start"]) * (x / p) if x <= p else a["top"] + (a.get("end", 1.5) - a["top"]) * ((x - p) / (1 - p))
        out[nm] = round(min(4, max(0, lv)), 2)
    out[names[0]] = a["start"]
    for nm, lv in a.get("breaths", {}).items(): out[nm] = lv
    return out

def mix_from(profile, sc, en):
    breaths = set(profile.get("arc", {}).get("breaths", {})) | set(profile.get("breaths", []))
    mix = {}
    for k, (trk, vol, lo) in PARTS.items():
        off = [nm for nm, lv in en.items() if lv < lo] + (list(breaths) if k == "kick" and NO_KICK_IN_BREATH else [])
        mix[k] = (trk, vol, off + profile.get("off", {}).get(k, []))
    return mix

def curves_from(profile, sc, en):
    if "curves" in profile: return profile["curves"]
    st = [nm for nm, a, b in sc]
    bed = [(nm, BED[0] + (BED[1] - BED[0]) * min(en[nm], 4) / 4) for nm in st]
    kick = [(nm, KICK[0] + (KICK[1] - KICK[0]) * max(min(en[nm], 4) - 1, 0) / 3) for nm in st if nm != st[0]]
    return {"bed": bed, "kick": kick}

def arcade(f, dur):
    """The chip voice made arcade-like (Emre, 8 Nov: "same tone and melody, more of an arcade sound"), with in-tune
    overtones only (1, 2, 4, 8x: a square's 3rd and 5th land off the scale on E and A): brighter, a blip an octave up
    for the first frame, the volume stepping down in 1/60 s frames like an old sound chip, and a short echo a
    sixteenth later, as arcade machines did."""
    t = np.arange(int(SR * (dur + BEAT / 4 + 0.2))) / SR
    def voice(t, f, amp):
        w = np.sin(2 * np.pi * f * t) + 0.6 * np.sin(2 * np.pi * 2 * f * t) + 0.3 * np.sin(2 * np.pi * 4 * f * t) + 0.12 * np.sin(2 * np.pi * 8 * f * t)
        blip = t < 1 / 60
        w = np.where(blip, np.sin(2 * np.pi * 2 * f * t) + 0.3 * np.sin(2 * np.pi * 4 * f * t), w)   # the first frame an octave up
        frames = np.floor(t * 60) / 60                                                                   # the volume in 1/60 s steps
        env = np.where(t < dur, np.round(15 * np.exp(-frames / (dur * 0.6))) / 15, 0.0)
        return amp * w * env * np.clip(t / 0.002, 0, 1)
    s = voice(t, f, 1.0)
    d = int(SR * BEAT / 4); echo = np.zeros_like(s); echo[d:] = voice(t[:-d], f, 0.35)                # the echo, a 16th later
    s = V.lp(V.hp(s + echo, 150), 7000)
    return np.vstack([s * 0.95, np.concatenate([np.zeros(int(SR * 0.004)), s[:-int(SR * 0.004)]])])   # a hair of width

def touches(tch, parts, bars, sc, n):
    """A version's own touches on the same song (1.3.0, Emre: "keep the soul of the song ... little iterations for each
    What's new"). Each is tied to a scene of the video:
      pan_phrases {scene: width}: the melody chimes answer themselves left and right, one bar each (1.3's Split page)
      chip_melody {scene: dB}: the melody's own notes on the retro chip voice, an octave down, at the chimes' level + dB
                               (1.3's secret page; turn the chimes off there with "off")"""
    import energy as E
    edges = lambda g: np.convolve(g, np.hanning(int(SR * 0.04)) / np.hanning(int(SR * 0.04)).sum(), "same")
    for nm, width in tch.get("pan_phrases", {}).items():
        p = np.zeros(n)
        for bar in bars:
            if bar["end"] or M.scene_at(sc, max(bar["t"], 0.0)) != nm: continue
            a, b = int(max(bar["t"], 0) * SR), int((bar["t"] + BAR * BEAT) * SR)
            p[a:min(b, n)] = width * (-1 if bar["i"] % 2 else 1)
        ang = (edges(p) + 1) * np.pi / 4; gl, gr = np.cos(ang) * np.sqrt(2), np.sin(ang) * np.sqrt(2)
        for k in ("motif", "motifdeep"):
            if k in parts: parts[k] = parts[k] * np.vstack([gl, gr])[:, :parts[k].shape[1]]
    for nm, vol in tch.get("chip_melody", {}).items():
        x = np.zeros((2, n)); spots = []
        for bar in bars:
            if bar["end"] or not bar.get("phrase") or M.scene_at(sc, max(bar["t"], 0.0)) != nm: continue
            for b, nt, ln in bar["phrase"]:
                t = bar["t"] + b * BEAT
                if 0 <= t < n / SR: E.put(x, arcade(V.n(nt) / 2, min(ln * BEAT * 0.9, 1.2)), t); spots.append(t)
        ref = parts.get("motif")
        if ref is not None and spots:
            peak = max(np.abs(ref).max(), 1e-9); x = x / max(np.abs(x).max(), 1e-9) * peak * db(vol)
        parts["chip"] = x
        print(f"chip melody on {nm}: {len(spots)} notes")

def render(profile, cues_p, folder, out):
    import pyloudnorm as pyln
    from scipy.signal import resample_poly, butter, sosfilt
    video = profile["video"]; os.makedirs(out, exist_ok=True)
    L = V.LENGTH[video]; cues = V.read_cues(cues_p)[video]; bars, e0 = V.timeline(cues, L); sc = M.scenes(cues, L)
    en = energies(profile, sc, L); MIX = mix_from(profile, sc, en); cv = curves_from(profile, sc, en)
    print("energy:", "  ".join(f"{nm} {lv:g}" for nm, lv in en.items()))
    parts = {}
    for k, (trk, vol, off) in MIX.items():
        p = os.path.join(folder, trk + ".wav")
        if not os.path.exists(p): continue
        x = V.read_wav(p); parts[k] = x * db(vol) * M.bar_gates(x.shape[1], bars, sc, off, e0)
    n = parts["chords"].shape[1]; tt = np.arange(n) / SR; start = {nm: a for nm, a, b in sc}
    touches(profile.get("touches", {}), parts, bars, sc, n)
    curve = lambda c: db(np.interp(tt, [start[k] for k, v in c], [v for k, v in c]))
    dip = M.kick_dip(n, bars, sc, MIX["kick"][2])
    parts["kick"] = parts["kick"] * curve(cv["kick"])
    for k in ("bass", "pulse"):
        if k in parts: parts[k] = parts[k] * dip
    mix = sum(parts.values()) * curve(cv["bed"])
    for f0, gdb, q in [(300, -2.0, 0.8), (2000, 1.5, 0.8), (250, -4.0, 0.7), (120, -2.0, 0.7)]: mix = M.peq(mix, f0, gdb, q)
    mid, side = (mix[0] + mix[1]) / 2, (mix[0] - mix[1]) / 2
    side = sosfilt(butter(2, 180, "highpass", fs=SR, output="sos"), side); mix = np.vstack([mid + side, mid - side])
    meter = pyln.Meter(SR); mix = V.clean_low_end(mix)
    fx = V.soften_fx(V.effects(video, cues, L)); fx = fx[:, :mix.shape[1]]
    # "key_sounds": false: music only (1.3.0's clip shows no button presses, only a few page turns, and five lone
    # clicks measured 7 LU under the music come out far too loud)
    if profile.get("key_sounds", True) is False: fx = np.zeros_like(mix)
    fxg = db(meter.integrated_loudness(mix.T) - V.MUSIC_OVER_FX - meter.integrated_loudness(fx.T)) if fx.any() else 0.0
    g = db(-15 - meter.integrated_loudness(mix.T)); tp = 20 * np.log10(np.abs(resample_poly(mix * g, 4, 1, axis=1)).max())
    if tp > -1.1: g *= db(-1.1 - tp)
    music = V.fade_to_silence(mix * g, L)
    V.MUSIC_ONSETS.clear(); V.ONSET_WINDOW = 0.15
    for b in bars: V.MUSIC_ONSETS.extend([b["t"] + k * BEAT / 4 for k in range(16)])
    V.check_audio(music, music, [], f"{video} music", parts=(music, np.zeros_like(music)))
    V.save24(os.path.join(out, f"{video}-music.wav"), music)
    vm = mix + fx * fxg; gv = db(-15 - meter.integrated_loudness(vm.T)); tpv = 20 * np.log10(np.abs(resample_poly(vm * gv, 4, 1, axis=1)).max())
    if tpv > -1.1: gv *= db(-1.1 - tpv)
    m_part, f_part = V.fade_to_silence(mix * gv, L), V.fade_to_silence(fx * fxg * gv, L); vid = m_part + f_part
    V.check_audio(m_part, vid, V.FX_ONSETS, f"{video} video mix", parts=(m_part, f_part))
    V.save24(os.path.join(out, f"{video}-mix.wav"), vid); V.save24(os.path.join(out, f"{video}-effects.wav"), f_part)
    print(f"{video}: video mix {meter.integrated_loudness(vid.T):.1f} LUFS, true peak {20*np.log10(np.abs(resample_poly(vid, 4, 1, axis=1)).max()):.1f} dBTP")

if __name__ == "__main__":
    prof = json.load(open(sys.argv[1]))
    if prof.get("fx_pack") and os.environ.get("FX_PACK", prof["fx_pack"]) != V.FX_PACK:
        raise SystemExit(f"set FX_PACK={prof['fx_pack']} (the key sounds are chosen when videosound loads)")
    render(prof, *sys.argv[2:5])
