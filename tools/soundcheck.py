"""Checks every Inlay sound against the rules in docs/brand/SOUND.md.

    pip install numpy scipy mosqito      # mosqito is only needed for the sharpness check
    python tools/soundcheck.py           # prints one line per pack, exit code 1 if anything fails
    python tools/soundcheck.py --json report.json
    python tools/soundcheck.py build/sound-lab --only mypack       # a pack you're trying out

Checks per file: format, length, no silence or click at the start, peak, felt loudness on target,
crisp start, one clean hit, soft edges (sharpness), clean ending, every note in key (tonal packs).
Per pack: four letter takes matched in loudness but not identical, no smear when typing fast,
loudness order on three simulated speakers.
"""
import glob, json, os, sys, wave, warnings
import numpy as np
from scipy.signal import hilbert
sys.path.insert(0, os.path.dirname(__file__))
import sounds as S

warnings.filterwarnings("ignore")
RAW = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
TONAL = {"chime", "glass", "pop", "retro"}
BIG = {"lock", "confirm", "select_all"}
MAX_MS = {"move": 60, "type": 160, "space": 160, "delete": 160, "back": 160,
          "on": 200, "off": 200, "select_all": 200, "lock": 300, "confirm": 300}
OFFSET = {name: off for name, _, off in S.PACKS}
try:
    from mosqito.sq_metrics import sharpness_din_st
except Exception:
    sharpness_din_st = None

def load(path):
    w = wave.open(path)
    fmt = (w.getframerate(), w.getnchannels(), w.getsampwidth() * 8)
    x = np.frombuffer(w.readframes(w.getnframes()), "<i2").astype(float) / 32768
    return x, fmt

def handheld(x): return S.lp(S.hp(x, 450, 4), 9000)

def measure(x):
    m = {"ms": len(x) / S.SR * 1000, "peak": 20 * np.log10(np.max(np.abs(x)) + 1e-12)}
    a = np.abs(x); m["lead_ms"] = np.argmax(a > a.max() * 0.01) / S.SR * 1000
    m["felt"] = S.felt(x)
    raw = np.abs(hilbert(x))
    e1 = np.convolve(raw, np.ones(48) / 48, "same"); p1 = e1.argmax(); on = np.argmax(e1 > e1[p1] * 0.01)
    m["attack_ms"] = (np.argmax(e1 > e1[p1] * 10 ** (-3 / 20)) - on) / S.SR * 1000
    e8 = np.convolve(raw, np.ones(384) / 384, "same"); pk = e8.argmax()
    e = 20 * np.log10(e8[pk:] / e8[pk] + 1e-12); e = e[:np.argmax(e < -40) or len(e)]
    m["bump_db"] = float(np.max(e - np.minimum.accumulate(e))) if len(e) else 0.0
    n = len(x); fade = int(S.SR * 0.010); seg = x[max(0, n - fade - 384):n - fade]
    m["end_db"] = 20 * np.log10(np.sqrt(np.mean(seg ** 2)) / np.max(np.abs(x)) + 1e-12)
    if sharpness_din_st:
        pa = np.concatenate([x / np.max(np.abs(x)) * 0.2 * np.sqrt(2), np.zeros(int(0.1 * S.SR))])
        m["sharp"] = float(sharpness_din_st(pa, S.SR, weighting="din"))
    for k, f in (("head", lambda v: v), ("handheld", handheld), ("phone", S.phone)):
        m["felt_" + k] = S.felt(f(x))
    return m

def in_key(x):
    """The note the sound settles on (its fundamental, after the strike and any bend), in cents
    from the nearest C, D, E, G or A in pure tuning."""
    seg = x[int(len(x) * 0.4):]; X = np.abs(np.fft.rfft(seg * np.hanning(len(seg)), 1 << 17))
    f = np.fft.rfftfreq(1 << 17, 1 / S.SR); band = (f > 150) & (f < 4000)
    fb, Xb = f[band], X[band]
    peaks = [i for i in range(1, len(Xb) - 1) if Xb[i] >= Xb[i - 1] and Xb[i] >= Xb[i + 1] and Xb[i] > Xb.max() * 0.25]
    f0 = fb[peaks[0]] if peaks else fb[np.argmax(Xb)]
    best = 1e9
    for r in S.RATIO.values():
        for o in range(2, 9):
            best = min(best, abs(1200 * np.log2(f0 / (S.C4 * r * 2 ** (o - 4)))))
    return best

def check(raw=RAW, packs=None):
    report = {}; ok_all = True
    for name, _, off in (packs or S.PACKS):
        files = {os.path.basename(p)[len(name) + 1:-4]: p for p in glob.glob(os.path.join(raw, f"{name}_*.wav"))}
        fails = []; meas = {}
        for cue in S.CUES + ["type1", "type2", "type3"]:
            if cue not in files: fails.append(f"{cue}: missing"); continue
            x, fmt = load(files[cue]); m = measure(x); meas[cue] = m
            base = "type" if cue.startswith("type") else cue
            if fmt != (48000, 1, 16): fails.append(f"{cue}: format {fmt}")
            if m["ms"] > MAX_MS[base]: fails.append(f"{cue}: {m['ms']:.0f} ms long")
            if m["lead_ms"] > 2: fails.append(f"{cue}: {m['lead_ms']:.1f} ms of silence first")
            if m["peak"] > -0.99: fails.append(f"{cue}: peak {m['peak']:.2f} dB")
            if abs(m["felt"] - (S.LEVEL[base] + off)) > 0.5: fails.append(f"{cue}: {m['felt'] - S.LEVEL[base] - off:+.1f} dB off target")
            if m["attack_ms"] > (3.0 if name in TONAL else 1.5): fails.append(f"{cue}: slow start {m['attack_ms']:.1f} ms")
            if m["bump_db"] > 1.5: fails.append(f"{cue}: second bump {m['bump_db']:.1f} dB")
            if m["end_db"] > -30: fails.append(f"{cue}: cut off ({m['end_db']:.0f} dB at the end)")
            if "sharp" in m and m["sharp"] > (2.0 if base in BIG else 1.75): fails.append(f"{cue}: sharp {m['sharp']:.2f} acum")
            if np.abs(x[0]) > 0.05 * np.max(np.abs(x)): fails.append(f"{cue}: starts with a click")
            if name in TONAL and in_key(x) > 15: fails.append(f"{cue}: out of key ({in_key(x):.0f} cents)")
        if len(meas) == 13:
            takes = [meas[k]["felt"] for k in ("type", "type1", "type2", "type3")]
            if max(takes) - min(takes) > 0.5: fails.append(f"letter takes differ by {max(takes) - min(takes):.1f} dB")
            spectra = []
            for k in ("type", "type1", "type2", "type3"):
                x, _ = load(files[k]); X = np.abs(np.fft.rfft(x, 8192)) + 1e-9; spectra.append(20 * np.log10(X / X.max())[50:1700])
            closest = min(np.sqrt(np.mean((spectra[i] - spectra[j]) ** 2)) for i in range(4) for j in range(i + 1, 4))
            if closest < 3: fails.append("two letter takes sound the same (fast typing will repeat)")
            x, _ = load(files["type"]); n = int(S.SR * 0.105)
            if len(x) > n and 20 * np.log10(np.sqrt(np.mean(x[n:n + 480] ** 2)) / np.max(np.abs(x)) + 1e-12) > -30:
                fails.append("a letter still rings when the next one comes (fast typing smears)")
            for spk in ("head", "handheld", "phone"):
                g = lambda c: meas[c]["felt_" + spk]
                if g("lock") - g("type") < 0.5: fails.append(f"{spk}: caps lock not above a letter")
                if g("type") - g("move") < 3: fails.append(f"{spk}: move too close to a letter")
                if g("confirm") - g("back") < 0.5: fails.append(f"{spk}: Enter not above back")
        report[name] = {"fails": fails, "files": {k: {a: round(float(b), 2) for a, b in v.items()} for k, v in meas.items()}}
        ok_all &= not fails
        print(f"{name:11s} {'PASS' if not fails else 'FAIL'}  " + "; ".join(fails))
    print("ALL PASS" if ok_all else "SOME CHECKS FAILED")
    return report, ok_all

if __name__ == "__main__":
    raw = RAW; out = None
    args = sys.argv[1:]
    if "--json" in args: out = args[args.index("--json") + 1]; args = [a for a in args if a not in ("--json", out)]
    only = None
    if "--only" in args: only = args[args.index("--only") + 1]; args = [a for a in args if a not in ("--only", only)]
    if args: raw = args[0]
    rep, ok = check(raw, S.find([n for n in (only or "").split(",") if n]) if only else None)
    if out: json.dump(rep, open(out, "w"), indent=1)
    sys.exit(0 if ok else 1)
