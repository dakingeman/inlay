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
from videosound import BEAT, SR, db
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
    fxg = db(meter.integrated_loudness(mix.T) - V.MUSIC_OVER_FX - meter.integrated_loudness(fx.T))
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
