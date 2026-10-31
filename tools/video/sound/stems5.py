"""Round 38 (stems5): ripple, motif chimes, stab echo, half-time kick, snaps.
Round 37 (stems4): off-beat hat sounds and the lift.
Round 36 (stems3): round 35 plus Emre's 12:04-12:10 notes and the sub pulse.
Round 35: the mixer tracks plus Emre's notes from the mixer page (28 Oct, 11:45-12:02).

New tracks (each a choice; the approved versions stay):
  chord-repeat-here / -every   the chord blooms once more on beat 3 (bar 4 only, as a test / every bar)
  claps-only                   the claps without the ghost snaps
  stabs-more                   extra stabs: the 'and' of 3 in the phrase's 1st bar, the last 16th of the 2nd
  hats-soft / -shaker / -dusty the Techno+ pattern, unchanged, in three gentler sounds
  bounce-rim / bounce-drum     a soft swung bounce that changes every bar of the 4-bar phrase
  bass-deep-bounce             the deep bass with small octave hops that differ bar to bar
"""
import os, sys, json
import numpy as np
import videosound as V
import bloom as B
import energy as E
import groove2 as G
import depth as D
import stems as S
import rubscan
from videosound import T, n, lp, hp, ph0, BEAT, BAR, SR
from scipy.signal import iirpeak, tf2sos, sosfilt
up = lambda nt, k=1: nt[:-1] + str(int(nt[-1]) + k)

def chord_bloom(notes, dur, vel, levels, pans, swell):
    """bloom.bloom (1B's chord sound) with a choice of swell time."""
    t = T(dur + 5.0); out = np.zeros((2, len(t)))
    a = np.clip(t / swell, 0, 1); sw = a * a * (3 - 2 * a); shape = sw * np.where(t < dur, 1.0, np.exp(-(t - dur) / 0.6))
    for nt, lv, pan in zip(notes, levels, pans):
        f = n(nt); l, r = np.cos((pan + 1) * np.pi / 4) * 1.414, np.sin((pan + 1) * np.pi / 4) * 1.414
        for ch, c, g in ((0, -2, l), (1, 2, r)):
            ff = f * 2 ** (c / 1200)
            out[ch] += g * lv * (np.sin(2 * np.pi * ff * t + ph0(ff, 1)) + 0.12 * np.sin(2 * np.pi * 2 * ff * t + ph0(ff, 2))) * shape
    return vel * lp(hp(out, 140), 2000) / len(notes)

def chord_repeat(cues, video, every):
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L); m = np.zeros((2, int(SR * (L + 6))))
    for bar in bars:
        t0, ch, i = bar["t"], bar["chord"], bar["i"]
        if bar["end"] or t0 < 0 or i == -1: continue
        if not every and abs(t0 - 8.97) > 0.05: continue             # the test: bar 4 (G) only
        nts, lv, pn = B.chord_parts(ch)
        E.put(m, chord_bloom(nts, 2 * BEAT - 0.15, 0.42 * 0.75, lv, pn, 0.45), t0 + 2 * BEAT)
    return E.dip(m[:, :int(SR * L)], cues, video, -4.0)

def hat_sound(kind):
    if kind == "soft":   return lambda v, s: D.closed_hat(v * 1.1, s, 0.012, 2500, 5500)          # darker, rounder
    if kind == "shaker": return lambda v, s: E.shaker(v * 1.2, s)                                 # the soft shaker
    if kind == "dusty":  return lambda v, s: D.closed_hat(v * 1.25, s, 0.022, 1800, 3800)          # muffled, lo-fi 'tsh'
    return lambda v, s: D.closed_hat(v, s, 0.014)

def techno_plus(cues, video, kind):
    """stems.techno_plus, the same pattern and timing, with a choice of hat sound."""
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L); m = np.zeros((2, int(SR * (L + 6))))
    rng = np.random.default_rng(34); acc = [0.55, 0.32, 1.0, 0.42]; hat = hat_sound(kind)
    for bar in bars:
        t0, i = bar["t"], bar["i"]
        if bar["end"] or t0 < 0 or i == -1: continue
        pos = i % 4
        for k in range(16):
            if pos == 0 and k < 8: continue
            v = acc[k % 4] * rng.uniform(0.9, 1.08) * (1.1 if pos == 3 and k >= 12 else 1.0)
            t = t0 + k * BEAT / 4 + (0.016 * BEAT if k % 2 else 0)
            E.put(m, hat(0.06 * v, 6000 + 16 * abs(i) + k), t, 0.25); V.MUSIC_ONSETS.append(t)
        for b in (1, 3):
            t = t0 + (b + 0.5) * BEAT
            sig = D.closed_hat(0.05, 6500 + 4 * abs(i) + b, 0.06, *( (2200, 5000) if kind != "techno" else (4000, 8000)))
            E.put(m, sig, t, -0.2); V.MUSIC_ONSETS.append(t)
    return m[:, :int(SR * L)]

BOUNCE = {0: [(0.75, 0, 0.8), (2.25, 1, 0.6)],
          1: [(0.75, 0, 0.8), (1.5, 1, 0.6), (3.25, 0, 0.7)],
          2: [(0.75, 0, 0.8), (2.25, 1, 0.6), (2.75, 0, 0.5)],
          3: [(0.75, 0, 0.8), (1.5, 1, 0.6), (2.25, 0, 0.6), (3.5, 1, 0.7), (3.75, 0, 0.55)]}
DRUM = {"Am": ("A3", "E4"), "Em": ("G3", "E4"), "G": ("G3", "D4"), "C": ("G3", "C4")}   # chord notes the chords already play

def rim(vel, seed):
    t = T(0.12); nz = np.random.default_rng(seed).standard_normal(len(t)); a = np.clip(t / 0.001, 0, 1)
    b, a2 = iirpeak(1900, 4.0, fs=SR)
    return vel * sosfilt(tf2sos(b, a2), lp(hp(nz, 900, 2), 4000, 2)) * a * np.exp(-t / 0.012) * 2.2

def hand_drum(f, vel, seed):
    t = T(0.5); a = np.clip(t / 0.002, 0, 1); a = a * a * (3 - 2 * a)
    body = (np.sin(2 * np.pi * f * t) + 0.25 * np.sin(4 * np.pi * f * t)) * np.exp(-t / 0.11)
    slap = lp(hp(np.random.default_rng(seed).standard_normal(len(t)), 1200, 2), 3500, 2) * np.exp(-t / 0.006) * 0.25
    return vel * (body + slap) * a

def bounce(cues, video, kind):
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L); m = np.zeros((2, int(SR * (L + 6))))
    for bar in bars:
        t0, ch, i = bar["t"], bar["chord"], bar["i"]
        if bar["end"] or t0 < 0 or i == -1: continue
        for b, which, v in BOUNCE[i % 4]:
            t = t0 + (b + (0.04 if abs(b % 0.5 - 0.25) < 1e-6 else 0)) * BEAT        # the in-between 16ths swing a little
            sig = rim(0.07 * v, 70 * abs(i) + int(b * 4)) if kind == "rim" else hand_drum(n(DRUM[ch][which]), 0.09 * v, 80 * abs(i) + int(b * 4))
            E.put(m, sig, t, 0.35 if which else -0.35); V.MUSIC_ONSETS.append(t)
    return m[:, :int(SR * L)]


# ---------------------------------------------------------------- round 36
def sub_pulse(f, vel, decay):
    """The sub pulse from Emre's spec: a sine (starting exactly at zero, so its instant attack can't
    click), cut off steeply at 120 Hz, a short pluck: instant attack, decay, no sustain, quick release.
    Every pulse at the same level: the steady, 'compressed' feel without a compressor."""
    t = T(decay * 4 + 0.05); s = np.sin(2 * np.pi * f * t) * np.exp(-t / decay)
    r = np.clip((t - decay * 3.5) / 0.03, 0, 1); s *= np.cos(r * np.pi / 2) ** 2
    return vel * lp(s, 120, 4)

PULSE_NOTES = {"Am": ("A1", "E2"), "Em": ("E2", "A1"), "G": ("G1", "D2"), "C": ("C2", "G1")}   # root, fifth (Em: A, its fifth B isn't in the scale)
PULSES = {"eighths":  [(k * 0.5, 0 if k % 4 != 3 else 1, 1.0 if k % 2 == 0 else 0.8, 0.12) for k in range(8)],
          "heartbeat": [(0, 0, 1.0, 0.2), (0.25, 0, 0.7, 0.15), (2, 1, 1.0, 0.2), (2.25, 1, 0.7, 0.15)],
          "sonar":    [(0, 0, 1.0, 0.35), (1.5, 1, 0.85, 0.3), (2.75, 0, 0.9, 0.3)]}

def pulse_layer(cues, video, kind):
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L); m = np.zeros((2, int(SR * (L + 6))))
    for bar in bars:
        t0, ch, i = bar["t"], bar["chord"], bar["i"]
        if bar["end"] or t0 < 0: continue
        hits = PULSES[kind] if i != -1 else [(0, 0, 1.0, 0.35)]                          # one last soft pulse on the home chord
        for b, which, v, dec in hits:
            t = t0 + b * BEAT; E.put(m, sub_pulse(n(PULSE_NOTES[ch][which]), 0.3 * v, dec), t); V.MUSIC_ONSETS.append(t)
    return m[:, :int(SR * L)]

def tone_bounce(cues, video):
    """The hand drum bounce with only its tuned notes: the slap (the drum/hi-hat-like noise) removed."""
    keep = np.random.default_rng
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L); m = np.zeros((2, int(SR * (L + 6))))
    for bar in bars:
        t0, ch, i = bar["t"], bar["chord"], bar["i"]
        if bar["end"] or t0 < 0 or i == -1: continue
        for b, which, v in BOUNCE[i % 4]:
            t = t0 + (b + (0.04 if abs(b % 0.5 - 0.25) < 1e-6 else 0)) * BEAT
            f = n(DRUM[ch][which]); tt = T(0.5); a = np.clip(tt / 0.004, 0, 1); a = a * a * (3 - 2 * a)
            sig = 0.1 * v * np.sin(2 * np.pi * f * tt) * np.exp(-tt / 0.11) * a          # pure tone: its overtone met the bloom's A
            E.put(m, sig, t, 0.35 if which else -0.35); V.MUSIC_ONSETS.append(t)
    return m[:, :int(SR * L)]

HAT_PATTERNS = {   # (beat, level, open?) per bar; plain and on the grid, no swing
    "offbeat": [(b + 0.5, 1.0, True) for b in range(4)],                                   # the 'and' of every beat
    "eighths": [(k * 0.5, 1.0 if k % 2 else 0.45, False) for k in range(8)],              # straight 8ths, accented off-beats
    "sparse":  [(1.5, 1.0, True), (3.5, 1.0, True), (3.75, 0.5, False)],                  # 'and' of 2 and 4, a little pickup
    "shuffle": [(b + x, v, False) for b in range(4) for x, v in ((0, 0.5), (2 / 3, 1.0))]}  # a lazy triplet shuffle

def hat_pattern(cues, video, kind):
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L); m = np.zeros((2, int(SR * (L + 6))))
    rng = np.random.default_rng(36)
    for bar in bars:
        t0, i = bar["t"], bar["i"]
        if bar["end"] or t0 < 0 or i == -1: continue
        for b, v, op in HAT_PATTERNS[kind]:
            t = t0 + b * BEAT; v *= rng.uniform(0.92, 1.06)
            sig = D.closed_hat(0.06 * v, 9000 + 37 * abs(i) + int(b * 12), 0.05 if op else 0.012, 2500, 5500)
            E.put(m, sig, t, 0.25 if op else -0.15); V.MUSIC_ONSETS.append(t)
    return m[:, :int(SR * L)]

# ---------------------------------------------------------------- round 37
def soft_hat(kind, vel, seed):
    """Off-beat hat sounds with gentle starts, so none of them pokes out."""
    t = T(0.35); nz = np.random.default_rng(seed).standard_normal(len(t))
    def env(att, dec): a = np.clip(t / att, 0, 1); return a * a * (3 - 2 * a) * np.exp(-np.maximum(t - att, 0) / dec)
    if kind == "breath": return vel * 1.3 * lp(hp(nz, 2500, 2), 6000, 2) * env(0.012, 0.06)        # airy, slow start
    if kind == "tick":   return vel * 1.6 * lp(hp(nz, 1800, 4), 3500, 4) * env(0.003, 0.010)       # tiny and muted
    if kind == "brush":  return vel * 1.1 * lp(hp(nz, 900, 2), 4500, 2) * env(0.008, 0.08)         # soft and wide
    if kind == "shaker": return E.shaker(vel * 1.2, seed)
    if kind == "felt":   return vel * 1.4 * lp(hp(nz, 2200, 4), 4200, 4) * env(0.005, 0.035)       # a rounded, dull hat
    raise ValueError(kind)

def offbeat_hats(cues, video, kind):
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L); m = np.zeros((2, int(SR * (L + 6))))
    rng = np.random.default_rng(36)                                   # same timing and level moves as round 36's off-beats
    for bar in bars:
        t0, i = bar["t"], bar["i"]
        if bar["end"] or t0 < 0 or i == -1: continue
        for b in range(4):
            t = t0 + (b + 0.5) * BEAT; v = rng.uniform(0.92, 1.06)
            E.put(m, soft_hat(kind, 0.06 * v, 9000 + 37 * abs(i) + int((b + 0.5) * 12)), t, 0.25); V.MUSIC_ONSETS.append(t)
    return m[:, :int(SR * L)]

def lift(cues, video, at=21.97):
    """A slow, rising swell of the bloom across the bar before the claps come in, breaking off
    exactly on the downbeat (a 25 ms fade, so it ends clean): the 'swell into the drop'."""
    L = V.LENGTH[video]; m = np.zeros((2, int(SR * (L + 6)))); d = 4 * BEAT
    for q, nt in enumerate(("G2", "D3", "G3")):                       # an open fifth that belongs to both the G bar and the C it leads to
        f = n(nt); t = T(d); x = np.clip(t / d, 0, 1)
        fc = 200 + 700 * x ** 2; s = np.zeros(len(t))
        for k in (1, 2, 3, 4, 6, 8):                                  # in-tune overtones only (a pulse's 5th and 7th fall outside the scale)
            if k * f > 5000: break
            g = 1 / np.sqrt(1 + (k * f / fc) ** 4); s += np.sin(np.pi * k * 0.35) / k * g * np.sin(2 * np.pi * k * f * t + ph0(f, k))
        s *= x ** 2.2; fd = int(SR * 0.025); s[-fd:] *= np.cos(np.linspace(0, np.pi / 2, fd)) ** 2
        E.put(m, np.vstack([s, np.roll(s, int(SR * 0.008 * (q + 1)))]) * 0.25, at - d)
    return m[:, :int(SR * L)]

# ---------------------------------------------------------------- round 38
def stab_echo(stabs, cues=None, video=None, decay=0.42, n_rep=3):
    """A dub echo of the chord stab: repeats every dotted eighth (0.75 beat), each quieter and darker.
    Each bar's echoes stop at that bar's end (25 ms fade), so they never ring into the next chord."""
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L); out = np.zeros_like(stabs); d = int(round(0.75 * BEAT * SR)); fd = int(SR * 0.025)
    for bar in bars:
        if bar["end"] or bar["t"] < 0: continue
        a, z = int(SR * bar["t"]), min(int(SR * (bar["t"] + BAR * BEAT)), stabs.shape[1])
        seg = np.zeros_like(stabs); seg[:, a:z] = stabs[:, a:z]; x = seg; e = np.zeros_like(stabs)
        for k in range(1, n_rep + 1):
            x = lp(x, 2200 - 450 * k, 2) * decay; e[:, d * k:] += x[:, :e.shape[1] - d * k]
        w = np.zeros(stabs.shape[1]); w[a:z] = 1; w[z - fd:z] = np.cos(np.linspace(0, np.pi / 2, fd)) ** 2
        out += e * w
    return out

def half_time(kick, cues, video):
    """The kick on beats 1 and 3 only (and the ghosts in those beats): beats 2 and 4 are faded out over 5 ms."""
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L); g = np.ones(kick.shape[1]); f = int(SR * 0.005)
    for bar in bars:
        if bar["end"] or bar["t"] < 0 or bar["i"] == -1: continue
        for b in (1, 3):
            a = int(SR * (bar["t"] + b * BEAT - 0.006)); z = int(SR * (bar["t"] + (b + 1) * BEAT - 0.006))
            g[a:z] = 0; g[a - f:a] = np.minimum(g[a - f:a], np.linspace(1, 0, f)); g[z:z + f] = np.minimum(g[z:z + f] + np.linspace(0, 1, f), 1)
    return kick * g

def snaps(cues, video):
    L = V.LENGTH[video]; bars, e0 = V.timeline(cues, L); m = np.zeros((2, int(SR * (L + 6))))
    for bar in bars:
        t0, i = bar["t"], bar["i"]
        if bar["end"] or t0 < 0 or i == -1: continue
        for b in (1, 3):
            t = t0 + b * BEAT - 0.008; tt = T(0.15); nz = np.random.default_rng(300 + 4 * abs(i) + b).standard_normal(len(tt))
            a = np.clip(tt / 0.0012, 0, 1); b2, a2 = iirpeak(2300, 2.5, fs=SR)
            sig = 0.07 * sosfilt(tf2sos(b2, a2), hp(nz, 1200, 2)) * a * np.exp(-tt / 0.028) * 2
            E.put(m, sig, t, 0.15); V.MUSIC_ONSETS.append(t)
    return m[:, :int(SR * L)]

if __name__ == "__main__":
    import pyloudnorm as pyln
    from scipy.signal import resample_poly
    cues_p, video, out = sys.argv[1:4]; os.makedirs(out, exist_ok=True)
    cues = V.read_cues(cues_p)[video]; L = V.LENGTH[video]; meter = pyln.Meter(SR)
    V.MUSIC_ONSETS.clear(); V.ONSET_WINDOW = 0.15
    lu = lambda x: meter.integrated_loudness(x.T)
    zero = S.zero
    keep_sb, keep_sub = V.softbass, V.sub
    V.softbass = zero; V.sub = zero; chords = B.render(cues, video); V.softbass, V.sub = keep_sb, keep_sub
    keep_bl = B.bloom; B.bloom = lambda *a, **k: np.zeros((2, 10)); cbass = B.render(cues, video); B.bloom = keep_bl
    lb = lu(chords + cbass); gain_to = lambda x, rel: V.db(lb + rel - lu(x))
    tr = {"chords": chords, "chord-bass": cbass}
    tr["chord-repeat-here"] = chord_repeat(cues, video, False); tr["chord-repeat-every"] = chord_repeat(cues, video, True)
    bp, ev_rep, bars = S.bloom_parts(cues, video)
    full = bp["main"] + bp["trio"] + bp["octave_low"]; space = D.room(full) - full; gb = gain_to(full + space, -6.0)
    for k, x in (("bloom", bp["main"]), ("harmony-trio", bp["trio"]), ("harmony-octave", bp["octave"]),
                 ("harmony-full", bp["trio"] + bp["octave_low"]), ("space", space), ("bloom-repeat", bp["repeat"])):
        tr[k] = E.dip(x * gb, cues, video)
    kick, _, _ = S.only(cues, video, ("kick",)); claps, _, _ = S.only(cues, video, ("clap", "snap")); clapsonly, _, _ = S.only(cues, video, ("clap",))
    gd = gain_to(kick + claps, -4.5)
    tr["kick"] = E.dip(kick * gd, cues, video, -8.0); tr["claps"] = E.dip(claps * gd, cues, video, -8.0); tr["claps-only"] = E.dip(clapsonly * gd, cues, video, -8.0)
    stabs, _, _ = S.only(cues, video, ("stab",)); gs = gain_to(stabs, -6.5); tr["stabs"] = E.dip(stabs * gs, cues, video, -8.0)
    keep_sp = dict(G.STAB_POS); G.STAB_POS.update({0: [0.5, 2.5], 1: [0.5, 2.75, 3.75]})
    stabs2, _, _ = S.only(cues, video, ("stab",)); G.STAB_POS.clear(); G.STAB_POS.update(keep_sp)
    tr["stabs-more"] = E.dip(stabs2 * gs, cues, video, -8.0)                       # same level as the stabs
    deephats, _, _ = S.only(cues, video, ("shaker", "open_hat")); th, tb, _, _ = D.flavour(cues, video, "techno")
    hats = {"hats-techno": th, "hats-deep": deephats, "hats-techno-plus": techno_plus(cues, video, "techno"),
            "hats-soft": techno_plus(cues, video, "soft"), "hats-shaker": techno_plus(cues, video, "shaker"), "hats-dusty": techno_plus(cues, video, "dusty")}
    gh = gain_to(hats["hats-techno-plus"], -9.0)
    for k, x in hats.items(): tr[k] = E.dip(x * (gh if k.startswith("hats-techno-plus") or k in ("hats-soft", "hats-shaker", "hats-dusty") else gain_to(x, -9.0)), cues, video, -8.0)
    deepbass, _, _ = S.only(cues, video, ("deep_bass",)); keep_bp = dict(G.BASS_POS)
    G.BASS_POS.update({1: [0, 1.5, 2.75, (3.5, 1)], 2: [0, 0.75, 2.5, (3.25, 1)], 3: [0, 1.5, (2.25, 1), 3.0, (3.5, 1), 3.75]})
    deepbounce, _, _ = S.only(cues, video, ("deep_bass",)); G.BASS_POS.clear(); G.BASS_POS.update(keep_bp)
    gbass = gain_to(deepbass, -6.0)
    tr["bass-deep"] = E.dip(deepbass * gbass, cues, video, -8.0); tr["bass-deep-bounce"] = E.dip(deepbounce * gbass, cues, video, -8.0)
    tr["bass-techno"] = E.dip(tb * gain_to(tb, -6.0), cues, video, -8.0)
    br, bd = bounce(cues, video, "rim"), bounce(cues, video, "drum")
    tr["bounce-rim"] = E.dip(br * gain_to(br, -11.0), cues, video, -8.0); tr["bounce-drum"] = E.dip(bd * gain_to(bd, -10.0), cues, video, -8.0)
    tb2 = tone_bounce(cues, video); tr["bounce-tone"] = E.dip(tb2 * gain_to(bd, -10.0), cues, video, -8.0)
    keep_sp = dict(G.STAB_POS); G.STAB_POS.update({1: [0.5, 2.75, 3.75]})
    stabs1, _, _ = S.only(cues, video, ("stab",)); G.STAB_POS.clear(); G.STAB_POS.update(keep_sp)
    tr["stabs-one"] = E.dip(stabs1 * gs, cues, video, -8.0)
    hp_ = {k: hat_pattern(cues, video, k) for k in HAT_PATTERNS}; gh2 = gain_to(hp_["offbeat"], -9.0)
    for k, x in hp_.items(): tr["hats-" + k] = E.dip(x * gh2, cues, video, -8.0)
    pl = {k: pulse_layer(cues, video, k) for k in PULSES}; gp = gain_to(pl["heartbeat"], -10.0)
    for k, x in pl.items(): tr["pulse-" + k] = E.dip(x * gp, cues, video, -6.0)
    for k in ("breath", "tick", "brush", "shaker", "felt"):
        x = offbeat_hats(cues, video, k); tr["hats-off-" + k] = E.dip(x * gain_to(x, -13.0), cues, video, -8.0)   # quieter: you had them at -6.5
    lf = lift(cues, video); tr["lift"] = lf * gain_to(lf, -9.0)
    import layers as LY
    V_on = list(V.MUSIC_ONSETS)
    LY.LAYERS = ["ripple"]; rp = LY.layers(cues, video); tr["ripple"] = rp * gain_to(rp, -15.0)
    LY.LAYERS = ["motif"]; mo = LY.layers(cues, video); tr["motif"] = mo * gain_to(mo, -14.0)
    tr["stab-echo"] = stab_echo(tr["stabs-one"], cues, video); tr["stab-echo-more"] = stab_echo(tr["stabs-more"], cues, video)
    tr["kick-half"] = half_time(tr["kick"], cues, video)
    sn = snaps(cues, video); tr["snaps"] = E.dip(sn * gain_to(sn, -9.0), cues, video, -8.0)
    # rub scan on real audio: each new pitched choice against Emre's last mix
    last = ["chords", "chord-bass", "chord-repeat-every", "bloom", "harmony-full", "space", "bloom-repeat", "stabs-one", "bass-deep-bounce", "bounce-tone", "pulse-eighths"]
    base = sum(tr[k] for k in last)
    for nm, swap in (("ripple", {"+": ["ripple"]}), ("motif chimes", {"+": ["motif"]}), ("stab echo", {"+": ["stab-echo"]})):
        test = base + sum(tr[k] for k in swap.get("+", [])) - sum(tr[k] for k in swap.get("-", []))
        found = rubscan.new_rubs(test, base)
        print(f"  rub scan, {nm}: {len(found)} new" + "".join(f"\n    {a:.2f}-{b:.2f}s {f1:.0f}/{f2:.0f} Hz" for a, b, f1, f2 in found[:6]))
    # one master gain: the 'try new ideas' mix at -16 LUFS, and even everything at once under -1 dBTP
    idea = ["chords", "chord-bass", "chord-repeat-every", "bloom", "harmony-full", "space", "bloom-repeat", "stabs-one", "kick", "claps-only", "hats-off-breath", "bass-deep-bounce", "bounce-tone", "pulse-eighths", "lift"]
    allin = ["chords", "chord-bass", "chord-repeat-every", "bloom", "harmony-octave", "harmony-trio", "space", "bloom-repeat", "stabs-more", "kick", "claps", "hats-techno-plus", "bass-deep-bounce", "bass-techno", "bounce-rim", "bounce-drum", "pulse-eighths", "lift", "hats-off-brush", "ripple", "motif", "stab-echo-more", "snaps"]
    g = V.db(-16 - lu(V.clean_low_end(sum(tr[k] for k in idea))))
    worst = 20 * np.log10(np.abs(resample_poly(V.clean_low_end(sum(tr[k] for k in allin)) * g, 4, 1, axis=1)).max())
    if worst > -1.1: g *= V.db(-1.1 - worst)
    for k, x in tr.items():
        y = V.fade_to_silence(V.clean_low_end(x) * g, L); tr[k] = y
        V.check_audio(y, y, [], f"track {k}", parts=(y, np.zeros_like(y))); V.save24(os.path.join(out, k + ".wav"), y)
    mix = sum(tr[k] for k in idea); V.check_audio(mix, mix, [], "new-ideas mix", parts=(mix, np.zeros_like(mix)))
    print(f"new-ideas mix {lu(mix):.1f} LUFS; {len(tr)} tracks")
