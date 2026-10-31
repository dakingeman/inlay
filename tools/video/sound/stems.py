"""Round 34: every part of the current song as its own track, for the mixer page.

    python stems.py <cues.md> <video> <out-folder>

Each track is written at exactly the level it has in the full mix (one shared master gain, no
limiter), so any combination the mixer plays adds up to the real song. Tracks with choices get one
file per choice. Every pitched note of every choice goes through the rub check; the default mix
goes through the full audio check.
"""
import os, sys, json
import numpy as np
import videosound as V
import bloom as B
import energy as E
import layers2 as L2
import groove2 as G
import depth as D
from videosound import T, n, lp, hp, ph0, BEAT, BAR, SR
up = lambda nt, k=1: nt[:-1] + str(int(nt[-1]) + k)

def zero(*a, **k): return np.zeros(10)

def only(cues, video, keep):
    """Round 32's deep groove with every part except `keep` silenced (same timing, same randomness)."""
    names = {"kick": (E, "kick"), "clap": (G, "clap"), "snap": (E, "snap"), "shaker": (E, "shaker"),
             "open_hat": (G, "open_hat"), "stab": (G, "stab"), "deep_bass": (G, "deep_bass")}
    saved = {k: getattr(m, a) for k, (m, a) in names.items()}
    for k, (m, a) in names.items():
        if k not in keep: setattr(m, a, zero)
    try: dr, pt, ev, bars = G.groove(cues, video, "deep")
    finally:
        for k, (m, a) in names.items(): setattr(m, a, saved[k])
    return dr + pt, ev, bars

def bloom_note(f, dur, seed, swell=1.4):
    """groove2.bloom_note (drift), with the swell time as a choice (the repeat swells faster)."""
    t = T(dur + 0.6); out = np.zeros((2, len(t))); drive = 1.5
    a = np.clip(t / swell, 0, 1); sw = a * a * (3 - 2 * a)
    fc = 300 + 700 * sw * np.exp(-np.maximum(t - swell, 0) / 1.5) + 300 * np.clip(t / swell, 0, 1) * (1 - np.exp(-np.maximum(t - swell, 0) / 1.5))
    for ch in (0, 1):
        cents = 4.0 * E.smooth_random(t, 0.7, seed * 2 + ch)
        phase = 2 * np.pi * np.cumsum(f * 2 ** (cents / 1200)) / SR + ph0(f, ch); s = np.zeros(len(t))
        for k in range(1, 16):
            if k * f > 5000: break
            x = k * f / fc; g = 1 / np.sqrt(1 + x ** 4) * (1 + 0.6 * np.exp(-((x - 1) / 0.35) ** 2))
            s += np.sin(np.pi * k * 0.35) / k * g * np.sin(k * phase)
        s = s / (np.abs(s).max() + 1e-9) * 0.7; bias = 0.12
        out[ch] = (np.tanh(drive * (s + bias)) - np.tanh(drive * bias)) / (drive * (1 - np.tanh(drive * bias) ** 2))
    body = 0.75 + 0.25 * np.exp(-np.maximum(t - swell, 0) / 1.0); r = np.clip((t - dur) / (0.4 if swell > 1 else 0.25), 0, 1)
    out *= sw * body * np.cos(r * np.pi / 2) ** 2
    return lp(hp(out, 70, 2), 2200, 4)

def bloom_parts(cues, video):
    """main, trio, octave twins (both), octave twin (low only), repeat - each its own track."""
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L)
    tr = {k: np.zeros((2, int(SR * (L + 6)))) for k in ("main", "trio", "octave", "octave_low", "repeat")}; ev = []
    for bar in bars:
        t0, ch, i = bar["t"], bar["chord"], bar["i"]
        if bar["end"] or t0 < 0: continue
        d = (L - t0 + 1.0) if i == -1 else BAR * BEAT * 0.92
        for q, nt in enumerate(E.HELD[ch]): E.put(tr["main"], G.bloom_note(n(nt), d, "drift", 10 * abs(i) + q), t0)
        E.put(tr["trio"], 0.7 * G.bloom_note(n(D.TRIO[ch]), d, "drift", 500 + abs(i)), t0)
        for q, nt in enumerate(E.HELD[ch]):
            if n(up(nt)) < 1000:
                tw = 0.42 * G.bloom_note(n(up(nt)), max(d - 0.35, 0.5), "drift", 700 + 10 * abs(i) + q)
                E.put(tr["octave"], tw, t0 + 0.35, (-0.4, 0.4)[q])
                if q == 0: E.put(tr["octave_low"], tw, t0 + 0.35, -0.4)
        if i != -1:                                                  # NEW: one more rise before the bar ends
            tr_t = t0 + 2.5 * BEAT; rd = BAR * BEAT - 2.5 * BEAT - 0.12
            for q, nt in enumerate(E.HELD[ch]):
                E.put(tr["repeat"], 0.6 * bloom_note(n(nt), rd, 900 + 10 * abs(i) + q, swell=0.45), tr_t)
                ev.append(dict(t=tr_t + 0.2, end=t0 + BAR * BEAT + 0.1, note=nt, inst="harm", vel=0.05, pan=0, amp=0.03 * 0.6, dur=rd))
    return {k: v[:, :int(SR * L)] for k, v in tr.items()}, ev, bars

def techno_plus(cues, video):
    """Techno hats, improved: a groove of accents across each beat, a hint of swing (54 %), the open
    hat only on beats 2 and 4's off-beats, and a short breather (hats out for beats 1-2) every fourth bar."""
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L); m = np.zeros((2, int(SR * (L + 6))))
    rng = np.random.default_rng(34); acc = [0.55, 0.32, 1.0, 0.42]   # per 16th inside a beat: on, e, AND, a
    for bar in bars:
        t0, i = bar["t"], bar["i"]
        if bar["end"] or t0 < 0 or i == -1: continue
        pos = i % 4
        for k in range(16):
            if pos == 0 and k < 8: continue                          # the breather at the top of each phrase
            v = acc[k % 4] * rng.uniform(0.9, 1.08) * (1.1 if pos == 3 and k >= 12 else 1.0)
            t = t0 + k * BEAT / 4 + (0.016 * BEAT if k % 2 else 0)
            E.put(m, D.closed_hat(0.06 * v, 6000 + 16 * abs(i) + k, 0.014), t, 0.25); V.MUSIC_ONSETS.append(t)
        for b in (1, 3):
            t = t0 + (b + 0.5) * BEAT; E.put(m, D.closed_hat(0.05, 6500 + 4 * abs(i) + b, 0.06, 4000, 8000), t, -0.2); V.MUSIC_ONSETS.append(t)
    return m[:, :int(SR * L)]

_OT = {}
def measured(nt, gain=1.0):
    """The bloom note's real overtone levels (relative to its own fundamental), measured once."""
    if nt not in _OT:
        y = G.bloom_note(n(nt), 3.0, "drift", 1)[0][int(SR * 1.6):int(SR * 2.6)]
        N = 4 * SR; sp = np.abs(np.fft.rfft(y * np.hanning(len(y)), N)); fr = np.fft.rfftfreq(N, 1 / SR); f = n(nt)
        lvl = lambda g: sp[(fr > g * 0.985) & (fr < g * 1.015)].max(); b = lvl(f)
        _OT[nt] = {k: lvl(f * k) / b for k in range(1, 7) if f * k < 5000}
    return _OT[nt]

def with_measured(ev):
    for e in ev:
        if e["inst"] == "harm": e["ot"] = measured(e["note"])
    return ev

if __name__ == "__main__":
    import pyloudnorm as pyln
    from scipy.signal import resample_poly
    cues_p, video, out = sys.argv[1:4]; os.makedirs(out, exist_ok=True)
    cues = V.read_cues(cues_p)[video]; L = V.LENGTH[video]; meter = pyln.Meter(SR)
    V.MUSIC_ONSETS.clear(); V.ONSET_WINDOW = 0.15
    lu = lambda x: meter.integrated_loudness(x.T)
    # the base, split: chords / their soft bass
    keep_sb, keep_sub = V.softbass, V.sub
    V.softbass = zero; V.sub = zero; chords = B.render(cues, video); V.softbass, V.sub = keep_sb, keep_sub
    keep_bl = B.bloom; B.bloom = lambda *a, **k: np.zeros((2, 10)); cbass = B.render(cues, video); B.bloom = keep_bl
    lb = lu(chords + cbass)
    gain_to = lambda x, rel: V.db(lb + rel - lu(x))
    tracks = {}; tracks["chords"] = chords; tracks["chord-bass"] = cbass
    # the bloom family: one gain for all of it (main + full harmony + space at -6 LU, as in bloom-full)
    bp, ev_rep, bars = bloom_parts(cues, video)
    full = bp["main"] + bp["trio"] + bp["octave_low"]; space = D.room(full) - full
    gb = gain_to(full + space, -6.0)
    for k, x in (("bloom", bp["main"]), ("harmony-trio", bp["trio"]), ("harmony-octave", bp["octave"]),
                 ("harmony-full", bp["trio"] + bp["octave_low"]), ("space", space), ("bloom-repeat", bp["repeat"])):
        tracks[k] = E.dip(x * gb, cues, video)
    # the locked deep house parts
    kick, ev1, _ = only(cues, video, ("kick",)); claps, _, _ = only(cues, video, ("clap", "snap"))
    stabs, ev_st, _ = only(cues, video, ("stab",)); deepbass, _, _ = only(cues, video, ("deep_bass",))
    deephats, _, _ = only(cues, video, ("shaker", "open_hat"))
    gd = gain_to(kick + claps, -4.5)
    tracks["kick"] = E.dip(kick * gd, cues, video, -8.0); tracks["claps"] = E.dip(claps * gd, cues, video, -8.0)
    tracks["stabs"] = E.dip(stabs * gain_to(stabs, -6.5), cues, video, -8.0)
    # choices: hats and bass
    th, tb, ev_tb, _ = D.flavour(cues, video, "techno"); tp = techno_plus(cues, video)
    for k, x in (("hats-techno", th), ("hats-techno-plus", tp), ("hats-deep", deephats)):
        tracks[k] = E.dip(x * gain_to(x, -9.0), cues, video, -8.0)
    for k, x in (("bass-techno", tb), ("bass-deep", deepbass)):
        tracks[k] = E.dip(x * gain_to(x, -6.0), cues, video, -8.0)
    # rub check: every pitched note of every choice together
    ev_bloom, _ = D.depth_events(cues, video, "full")
    for e in ev_bloom:                                               # the octave twins are 0.42 x as loud
        if e["note"] not in E.HELD.get(next(b["chord"] for b in bars if b["t"] <= e["t"] - 0.5 < b["t"] + BAR * BEAT), ()) and e["note"] not in D.TRIO.values(): e["amp"] = 0.03 * 0.42
    _, _, ev_deep_bass, _ = G.groove(cues, video, "deep")
    # rub check on the real audio: every pitched choice, against the locked chords alone
    import rubscan
    # Baseline = everything Emre has heard and liked (its own texture included). Test only what is new.
    liked = ["chords", "chord-bass", "bloom", "space", "stabs", "bass-techno"]
    combos = {"repeat over full harmony": (["harmony-full"], ["bloom-repeat"]), "repeat over trio": (["harmony-trio"], ["bloom-repeat"]),
              "repeat over octave": (["harmony-octave"], ["bloom-repeat"]), "deep bass instead of techno": (["harmony-full"], ["bass-deep"])}
    report = {}
    for nm, (h, extra) in combos.items():
        ref = sum(tracks[k] for k in liked + h)
        test = sum(tracks[k] for k in liked + h + extra) - (tracks["bass-techno"] if "bass-deep" in extra else 0)
        found = rubscan.new_rubs(test, ref); report[nm] = found
        print(f"  rub scan, {nm}: {len(found)} new rub(s)" + "".join(f"\n    {a:.2f}-{b:.2f}s  {f1:.0f} Hz with {f2:.0f} Hz" for a, b, f1, f2 in found[:8]))
    json.dump({k: [list(map(float, r)) for r in v] for k, v in report.items()}, open(os.path.join(out, "rubs.json"), "w"))
    # one master gain from the default mix
    default = ["chords", "chord-bass", "bloom", "harmony-full", "space", "bloom-repeat", "stabs", "kick", "claps", "hats-techno-plus", "bass-techno"]
    mix = V.clean_low_end(sum(tracks[k] for k in default))
    g = V.db(-16 - lu(mix)); tp_db = 20 * np.log10(np.abs(resample_poly(mix * g, 4, 1, axis=1)).max())
    worst = max(20 * np.log10(np.abs(resample_poly(V.clean_low_end(sum(tracks[k] for k in combo)) * g, 4, 1, axis=1)).max())
                for combo in (default, [k for k in tracks if not k.startswith(("hats-", "bass-", "harmony-"))] + ["harmony-octave", "harmony-trio", "hats-deep", "hats-techno", "bass-deep", "bass-techno"]))
    if worst > -1.1: g *= V.db(-1.1 - worst)                          # even everything at once stays under -1 dBTP
    meta = {}
    for k, x in tracks.items():
        y = V.fade_to_silence(V.clean_low_end(x) * g, L); tracks[k] = y
        V.check_audio(y, y, [], f"track {k}", parts=(y, np.zeros_like(y)))
        V.save(os.path.join(out, k + ".wav"), y)
    mixd = sum(tracks[k] for k in default); V.check_audio(mixd, mixd, [], "default mix", parts=(mixd, np.zeros_like(mixd)))
    print(f"default mix: {lu(mixd):.1f} LUFS")
