"""Round 41: only the tracks of Emre's saved round 40 arrangement (stems5 renders ~60 and runs out of memory on the
64 s teaser), each made exactly as stems5 makes it, at stems5's levels.
    python stems6.py <cues.md> <video> <out-folder>"""
import os, sys
import numpy as np
import videosound as V, bloom as B, energy as E, groove2 as G, depth as D, stems as S, stems5 as S5
from videosound import SR

if __name__ == "__main__":
    import pyloudnorm as pyln
    from scipy.signal import resample_poly
    cues_p, video, out = sys.argv[1:4]; os.makedirs(out, exist_ok=True)
    cues = V.read_cues(cues_p)[video]; L = V.LENGTH[video]; meter = pyln.Meter(SR); lu = lambda x: meter.integrated_loudness(x.T)
    V.MUSIC_ONSETS.clear(); V.ONSET_WINDOW = 0.15
    keep_sb, keep_sub = V.softbass, V.sub
    V.softbass = S.zero; V.sub = S.zero; chords = B.render(cues, video); V.softbass, V.sub = keep_sb, keep_sub
    keep_bl = B.bloom; B.bloom = lambda *a, **k: np.zeros((2, 10)); cbass = B.render(cues, video); B.bloom = keep_bl
    lb = lu(chords + cbass); gain_to = lambda x, rel: V.db(lb + rel - lu(x))
    tr = {"chords": chords, "chord-bass": cbass, "chord-repeat-every": S5.chord_repeat(cues, video, True)}
    bp, ev_rep, bars = S.bloom_parts(cues, video)
    full = bp["main"] + bp["trio"] + bp["octave_low"]; space = D.room(full) - full; gb = gain_to(full + space, -6.0)
    for k, x in (("bloom", bp["main"]), ("harmony-full", bp["trio"] + bp["octave_low"]), ("space", space), ("bloom-repeat", bp["repeat"])):
        tr[k] = E.dip(x * gb, cues, video)
    del bp, full, space
    kick, _, _ = S.only(cues, video, ("kick",)); claps, _, _ = S.only(cues, video, ("clap", "snap"))
    gd = gain_to(kick + claps, -4.5); tr["kick"] = E.dip(kick * gd, cues, video, -8.0); del claps
    stabs, _, _ = S.only(cues, video, ("stab",)); gs = gain_to(stabs, -6.5)
    keep_sp = dict(S5.G.STAB_POS); S5.G.STAB_POS.update({1: [0.5, 2.75, 3.75]})
    stabs1, _, _ = S.only(cues, video, ("stab",)); S5.G.STAB_POS.clear(); S5.G.STAB_POS.update(keep_sp)
    tr["stabs-one"] = E.dip(stabs1 * gs, cues, video, -8.0); del stabs, stabs1
    deepbass, _, _ = S.only(cues, video, ("deep_bass",)); keep_bp = dict(G.BASS_POS)
    G.BASS_POS.update({1: [0, 1.5, 2.75, (3.5, 1)], 2: [0, 0.75, 2.5, (3.25, 1)], 3: [0, 1.5, (2.25, 1), 3.0, (3.5, 1), 3.75]})
    deepbounce, _, _ = S.only(cues, video, ("deep_bass",)); G.BASS_POS.clear(); G.BASS_POS.update(keep_bp)
    tr["bass-deep-bounce"] = E.dip(deepbounce * gain_to(deepbass, -6.0), cues, video, -8.0); del deepbass, deepbounce
    bd = S5.bounce(cues, video, "drum"); tb2 = S5.tone_bounce(cues, video); tr["bounce-tone"] = E.dip(tb2 * gain_to(bd, -10.0), cues, video, -8.0)
    hb = S5.pulse_layer(cues, video, "heartbeat"); pe = S5.pulse_layer(cues, video, "eighths")
    tr["pulse-eighths"] = E.dip(pe * gain_to(hb, -10.0), cues, video, -6.0)
    import layers as LY
    LY.LAYERS = ["motif"]; mo = LY.layers(cues, video); tr["motif"] = mo * gain_to(mo, -14.0)
    tr["stab-echo"] = S5.stab_echo(tr["stabs-one"], cues, video)
    idea = ["chords", "chord-bass", "chord-repeat-every", "bloom", "harmony-full", "space", "bloom-repeat", "stabs-one", "kick", "bass-deep-bounce", "bounce-tone", "pulse-eighths"]
    g = V.db(-16 - lu(V.clean_low_end(sum(tr[k] for k in idea))))
    for k, x in tr.items():
        y = V.fade_to_silence(V.clean_low_end(x) * g, L)
        V.check_audio(y, y, [], f"track {k}", parts=(y, np.zeros_like(y))); V.save24(os.path.join(out, k + ".wav"), y)
    print(f"{len(tr)} tracks")
