"""Change sets: keep parallel pieces of work on Inlay from overwriting each other.

Each piece of work (a chat, a mini project) records the files it changes, with a fingerprint
of every file before and after. Before copying anything into the project, check it: you see
at a glance what is new, what is already in, and what someone else changed in the meantime.

    python tools/changeset.py record sound-1.2 --base <git commit> --title "Sound packs 1.2" [--base-dir <copy>]
        (in the working copy where the work was done; writes docs/changesets/sound-1.2.json.
         --base-dir: an untouched copy of the files the work started from, for exact fingerprints)
    python tools/changeset.py check            (in the project folder: every change set)
    python tools/changeset.py check sound-1.2  (just one)

Per file: NEW (not copied in yet, the project still has the file it started from),
IN (already copied in), CONFLICT (the project's file is neither: something else changed it,
so it must be merged by hand, never overwritten). Files touched by more than one change set
are listed too: those are where two pieces of work meet.
Fingerprints are git's own (git hash-object), so they match what GitHub Desktop sees.

    python tools/changeset.py package sound-1.2 --base-dir <copy> --out sound-1.2.zip
        (one zip: the record, every new file, and the starting version of every changed text file)
    python tools/changeset.py apply sound-1.2.zip          (in the project folder; --dry-run to only look)
        NEW files are copied in, IN files are skipped, deleted files are removed only if untouched.
        A CONFLICT in a text file is merged line by line with the starting version (git merge-file);
        if the same lines were changed on both sides it stops and lists them, and changes nothing.
"""
import argparse, hashlib, json, os, shutil, subprocess, sys, tempfile, datetime, zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
SETS = os.path.join(ROOT, "docs", "changesets")
SKIP = ("docs/changesets/",)

def fingerprint(path):
    """git hash-object of a file, or None if it doesn't exist."""
    if not os.path.isfile(path): return None
    data = open(path, "rb").read()
    return hashlib.sha1(b"blob %d\0" % len(data) + data).hexdigest()

def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, check=True).stdout

def record(name, base, title, base_dir=None):
    paths = {line.split("\t", 1)[1] for line in git("diff", "--name-status", "--no-renames", base).splitlines()}
    paths |= set(git("ls-files", "--others", "--exclude-standard").splitlines())
    files = []
    for path in sorted(paths):
        if path.startswith(SKIP) or "__pycache__" in path: continue
        if base_dir: before = fingerprint(os.path.join(base_dir, path))   # exact bytes the work started from
        else:
            r = subprocess.run(["git", "rev-parse", f"{base}:{path}"], cwd=ROOT, capture_output=True, text=True)
            before = r.stdout.strip() if r.returncode == 0 else None
        after = fingerprint(os.path.join(ROOT, path))
        if before != after: files.append({"path": path, "before": before, "after": after})
    files.sort(key=lambda f: f["path"])
    os.makedirs(SETS, exist_ok=True)
    out = {"name": name, "title": title, "recorded": datetime.date.today().isoformat(),
           "base": base, "files": files}
    json.dump(out, open(os.path.join(SETS, name + ".json"), "w"), indent=1)
    print(f"Recorded {name}: {len(files)} files")

def load_sets(only=None):
    if not os.path.isdir(SETS): return []
    names = sorted(f[:-5] for f in os.listdir(SETS) if f.endswith(".json"))
    return [json.load(open(os.path.join(SETS, n + ".json"))) for n in names if only in (None, n)]

def state(f):
    now = fingerprint(os.path.join(ROOT, f["path"]))
    if now == f["after"]: return "IN"
    if now == f["before"]: return "NEW"
    return "CONFLICT"

def check(only=None):
    sets = load_sets(only); bad = False
    if not sets: print("No change sets found."); return True
    owners = {}
    for s in load_sets():
        for f in s["files"]: owners.setdefault(f["path"], []).append(s["name"])
    for s in sets:
        states = [(f, state(f)) for f in s["files"]]
        count = {k: sum(1 for _, st in states if st == k) for k in ("IN", "NEW", "CONFLICT")}
        print(f"\n{s['name']} ({s['title']}, {s['recorded']}): "
              f"{count['IN']} in, {count['NEW']} not copied yet, {count['CONFLICT']} conflicts")
        for f, st in states:
            if st == "CONFLICT":
                bad = True; print(f"  CONFLICT  {f['path']}  (changed by something else since this work started)")
    shared = {p: o for p, o in owners.items() if len(o) > 1}
    if shared:
        print("\nFiles that more than one change set touches (merge these with care):")
        for p, o in sorted(shared.items()): print(f"  {p}: {', '.join(o)}")
    print("\nOK" if not bad else "\nCONFLICTS FOUND: merge them by hand before copying anything over them")
    return not bad

TEXT = (".kt", ".kts", ".md", ".js", ".py", ".json", ".html", ".css", ".xml", ".txt", ".sh", ".toml", ".properties")

def package(name, base_dir, out):
    rec = json.load(open(os.path.join(SETS, name + ".json")))
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(os.path.join(SETS, name + ".json"), "changeset.json")
        for f in rec["files"]:
            if f["after"]: z.write(os.path.join(ROOT, f["path"]), "files/" + f["path"])
            if f["before"] and f["path"].endswith(TEXT) and base_dir:
                z.write(os.path.join(base_dir, f["path"]), "base/" + f["path"])
    print(f"Packaged {name}: {out}")

def apply(zpath, dry):
    tmp = tempfile.mkdtemp(); zipfile.ZipFile(zpath).extractall(tmp)
    rec = json.load(open(os.path.join(tmp, "changeset.json")))
    plan, stuck = [], []
    for f in rec["files"]:
        dst = os.path.join(ROOT, f["path"]); now = fingerprint(dst); src = os.path.join(tmp, "files", f["path"])
        if now == f["after"]: continue
        if now == f["before"]:
            plan.append(("delete" if f["after"] is None else "copy", f, src, dst)); continue
        base = os.path.join(tmp, "base", f["path"])
        if f["after"] and now and os.path.isfile(base):
            merged = os.path.join(tmp, "merged.tmp"); shutil.copy(dst, merged)
            r = subprocess.run(["git", "merge-file", "-L", "project", "-L", "start", "-L", rec["name"], merged, base, src], capture_output=True)
            if r.returncode == 0: plan.append(("merge", f, merged + "." + str(len(plan)), dst)); shutil.copy(merged, plan[-1][2]); continue
        stuck.append(f["path"])
    if stuck:
        print("Stopped, nothing changed. These files were changed on both sides in the same places:")
        for p in stuck: print("  " + p)
        return False
    for act, f, src, dst in plan:
        print(f"  {act:6s} {f['path']}")
        if dry: continue
        if act == "delete":
            if os.path.exists(dst): os.remove(dst)
        else:
            os.makedirs(os.path.dirname(dst), exist_ok=True); shutil.copy(src, dst)
    if not dry:
        os.makedirs(SETS, exist_ok=True); shutil.copy(os.path.join(tmp, "changeset.json"), os.path.join(SETS, rec["name"] + ".json"))
    print(("Would apply " if dry else "Applied ") + f"{rec['name']}: {len(plan)} changes" + ("" if plan else " (already in)"))
    return True

if __name__ == "__main__":
    ap = argparse.ArgumentParser(); sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("record"); r.add_argument("name"); r.add_argument("--base", required=True); r.add_argument("--title", required=True); r.add_argument("--base-dir")
    c = sub.add_parser("check"); c.add_argument("name", nargs="?")
    k = sub.add_parser("package"); k.add_argument("name"); k.add_argument("--base-dir"); k.add_argument("--out", required=True)
    y = sub.add_parser("apply"); y.add_argument("zip"); y.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    if a.cmd == "record": record(a.name, a.base, a.title, a.base_dir)
    elif a.cmd == "package": package(a.name, a.base_dir, a.out)
    elif a.cmd == "apply": sys.exit(0 if apply(a.zip, a.dry_run) else 1)
    else: sys.exit(0 if check(a.name) else 1)
