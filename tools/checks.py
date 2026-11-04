"""Inlay's checks, in two sizes, so a small change doesn't wait for the whole suite.

    python tools/checks.py quick     the checks for what changed since the last full run, plus a short smoke set
    python tools/checks.py full      every check, in batches (each batch stays well under the cloud's time limits)
    python tools/checks.py times     how long each check took last time

"What changed" is worked out from the files' fingerprints against the last full run (build/checks-base.json),
so it works with or without git. Picture and video exporters (mock-up material) never run here.
Results: build/checks-last.txt (pass/fail per check class, with times).
"""
import glob, hashlib, json, os, re, subprocess, sys, time

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
TESTS = os.path.join(ROOT, "app/src/test/java/com/example/gridime")
SRC = os.path.join(ROOT, "app/src/main")
RESULTS = os.path.join(ROOT, "app/build/test-results/testDebugUnitTest")
BASE = os.path.join(ROOT, "build/checks-base.json")
PKG = "com.example.gridime."
# Mock-up and export material: not checks
SKIP = re.compile(r"(Export|Shots|Video|Probe|MockExport|Audit)$|^TestTones$")
# Always run in "quick": fast checks that catch most breakage across the app
SMOKE = ["ConsistencyTest", "DesignTokensTest", "SettingsTextFitTest", "SettingsContrastTest", "DeviceDetectionTest", "FaceButtonsTest"]
# Which checks go with which source files (name fragments). Anything not listed sends "quick" to everything it can find by name.
AREAS = [
    (r"Tutorial|TourScale|WhatsNew", ["Tour", "StageFits", "DemoTruth", "SmallScreens"]),
    (r"SettingsActivity|SettingsViews|SettingsChrome|KonamiCode", ["Settings", "FullTestAndKonami", "TvPanel", "SmallScreens", "PreviewFeatures"]),
    (r"DevicePicture|FaceButtons", ["Device", "DpadArms", "FaceButtons", "HighlightGeometry", "FullTestAndKonami"]),
    (r"KeyGridView|Layouts|Languages|RowLight|BadgePainter|Icons", ["Keyboard", "Badge", "Legibility", "Cyrillic", "Highlight", "Combinations", "Clear", "IconSheet", "TrayAndNumberRow", "Screenshot"]),
    (r"KeyboardSettings|Design|Backdrop", ["Accent", "Theme", "CustomColour", "DesignTokens", "Token", "SilentMode"]),
    (r"Feedback|res/raw|SoundPack", ["SoundPacks", "SilentMode"]),
    (r"GridKeyboardService|TextOps|InputHelpers|WordSuggester|UserWords|Clip", ["SmartTyping", "ClipRemoval", "RemovedClip"]),
]

def all_checks():
    return sorted(n[:-3] for n in os.listdir(TESTS) if n.endswith(".kt") and not SKIP.search(n[:-3]))

def fingerprints():
    out = {}
    for path in glob.glob(SRC + "/**/*", recursive=True):
        if os.path.isfile(path): out[os.path.relpath(path, ROOT)] = hashlib.sha1(open(path, "rb").read()).hexdigest()
    for path in glob.glob(TESTS + "/*.kt"): out[os.path.relpath(path, ROOT)] = hashlib.sha1(open(path, "rb").read()).hexdigest()
    return out

def changed():
    if not os.path.exists(BASE): return None
    old = json.load(open(BASE)); new = fingerprints()
    return sorted(p for p in set(old) | set(new) if old.get(p) != new.get(p))

def pick(files):
    names = all_checks(); chosen = set(SMOKE)
    for f in files:
        base = os.path.basename(f)[:-3] if f.endswith(".kt") else f
        if f.startswith("app/src/test/"): chosen.add(base); continue
        hit = False
        for pattern, frags in AREAS:
            if re.search(pattern, f): chosen |= {n for n in names if any(fr in n for fr in frags)}; hit = True
        if not hit: return names                         # something unmapped changed: be safe, run everything
    return sorted(n for n in chosen if n in names)

def run(names, batch_seconds=1500):
    """Run in batches of a few classes, so no single command runs past the time limits."""
    report = []; failed = []
    batches = [names[i:i + 8] for i in range(0, len(names), 8)]
    for b in batches:
        args = ["./gradlew", "-q", ":app:testDebugUnitTest", "--offline"] + sum([["--tests", PKG + n] for n in b], [])
        t = time.time()
        r = subprocess.run(args, cwd=ROOT, capture_output=True, text=True, timeout=batch_seconds)
        for n in b:
            f = os.path.join(RESULTS, f"TEST-{PKG}{n}.xml")
            if not os.path.exists(f): report.append(f"{n}: no result"); failed.append(n); continue
            h = open(f).read()
            m = re.search(r'tests="(\d+)" skipped="\d+" failures="(\d+)" errors="(\d+)"[^>]*time="([\d.]+)"', h)
            ok = m and m.group(2) == "0" and m.group(3) == "0"
            report.append(f"{n}: {'ok' if ok else 'FAILED'} ({m.group(1) if m else '?'} checks, {float(m.group(4)) if m else 0:.0f} s)")
            if not ok: failed.append(n)
        print(f"batch of {len(b)} done in {time.time() - t:.0f} s", flush=True)
    os.makedirs(os.path.join(ROOT, "build"), exist_ok=True)
    open(os.path.join(ROOT, "build/checks-last.txt"), "w").write("\n".join(report) + "\n")
    print("\n".join(report)); print("ALL PASS" if not failed else f"FAILED: {', '.join(failed)}")
    return not failed

if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "quick"
    if mode == "times":
        print(open(os.path.join(ROOT, "build/checks-last.txt")).read()); sys.exit()
    if mode == "full":
        ok = run(all_checks())
        if ok: json.dump(fingerprints(), open(BASE, "w"))       # the next "quick" compares against this
        sys.exit(0 if ok else 1)
    files = changed()
    names = all_checks() if files is None else pick(files)
    print(f"quick: {len(names)} of {len(all_checks())} check classes" + ("" if files is None else f" for {len(files)} changed files"))
    sys.exit(0 if run(names) else 1)
