"""python3 finish-video.py <name> [--gif]
Crossfades the parts of out/<name>/ (8 frames, about a quarter second) and writes out/<name>.mp4 (H.264, 30 fps,
no sound), the cue sheet out/<name>-cues.md / .json (every cut, key press, highlight move and name, in seconds) and a
pacing check (anything faster than a viewer can follow: see PACE). With --gif it also makes out/<name>.gif from the
grainless render out/<name>-nograin/ (render it with GRAIN=0), using the hero GIF's palette method: one shared palette,
ordered dithering, 960 px wide."""
import json, os, sys, subprocess, shutil
from PIL import Image

X = 8          # crossfade length in frames
FPS = 30
# Pacing rule (VISUAL_SYSTEM.md §10): what a viewer can follow in a video, even when the app itself goes faster
PACE = {'press': 0.25,      # key presses at least this far apart (s)
        'name': 0.8,        # a new name or label stays at least this long before the next one
        'move': 0.09}       # highlight steps (a held direction repeats at about 0.1 s, which reads fine)


def part_frames(src, parts):
    return [[os.path.join(src, f'p{k:02d}', f) for f in sorted(os.listdir(os.path.join(src, f'p{k:02d}')))] for k in range(len(parts))]


def crossfade(src, fin, parts):
    seq = part_frames(src, parts)
    shutil.rmtree(fin, ignore_errors=True); os.makedirs(fin)
    out = []
    for k, part in enumerate(seq):
        body = list(part)
        if k > 0:                                    # the start of this part fades over the end of the last
            prev = out[-X:]; del out[-X:]
            for j in range(X):
                a = Image.open(prev[j]).convert('RGB'); b = Image.open(body[j]).convert('RGB')
                t = (j + 1) / (X + 1); t = t * t * (3 - 2 * t)
                nj = os.path.join(fin, f'blend-{k}-{j}.png'); Image.blend(a, b, t).save(nj); out.append(nj)
            body = body[X:]
        out += body
    for n, s in enumerate(out):
        dst = os.path.join(fin, f'{n:05d}.png')
        os.replace(s, dst) if s.startswith(fin) else os.link(s, dst)
    return len(out)


NAMES = {'A': 'press A', 'OK': 'press OK', 'holdA': 'hold A (accents rise)', 'releaseA': 'let go of A (accent typed)', 'RS': 'right stick in (word taken)',
         'RB': 'press RB', 'Y': 'press Y (keyboard slides up)', 'move': 'highlight moves', 'right': 'slider step right', 'left': 'slider step left',
         'down': 'highlight moves down', 'words': 'suggestions change', 'device': 'device picture changes', 'key': 'key typed (in the pack’s sound)'}


def cues(name, parts, events, total):
    starts = [sum(n for _, n in parts[:k]) - X * k for k in range(len(parts))]
    rows = [(starts[k] / FPS, 'cut' if k else 'start', f'{pid} begins' + (f' (crossfade {X / FPS:.2f} s)' if k else '')) for k, (pid, _) in enumerate(parts)]
    for part, f, what in events:
        for w in what.split('+'):
            if w.startswith('name: '): kind, label = 'name', 'name pops up: ' + w[6:]
            elif w.startswith('pack: '): kind, label = 'name', 'highlight lands on ' + w[6:] + ' (play its sample)'
            elif w == 'right' and parts[part][0] == 'packrow': kind, label = 'name', 'D-pad right: the next sound pack shows (play its sample)'
            else: kind, label = ('move' if w in ('move', 'down', 'right', 'left', 'device', 'words') else 'press'), NAMES.get(w, w)
            rows.append(((starts[part] + f) / FPS, kind, label))
    rows.append((total / FPS, 'end', 'video ends'))
    rows.sort(key=lambda r: (r[0], r[1] != 'cut'))
    json.dump([{'t': round(t, 3), 'kind': k, 'what': w} for t, k, w in rows], open(os.path.join('out', name + '-cues.json'), 'w'), indent=1)
    with open(os.path.join('out', name + '-cues.md'), 'w') as f:
        f.write('| Time (s) | Kind | What happens |\n|---|---|---|\n')
        for t, k, w in rows: f.write(f'| {t:6.2f} | {k} | {w} |\n')
    return rows


def pace(rows):
    """Everything closer together than PACE allows. Moves only count within one part (a cut resets them)."""
    bad, last = [], {}
    for t, k, w in rows:
        if k in ('cut', 'start'): last = {}; continue
        if k not in PACE: continue
        if k == 'move' and w not in ('highlight moves', 'highlight moves down'): continue
        if k in last and t - last[k] < PACE[k] - 1e-6: bad.append(f'{t:6.2f} s  {w}: {t - last[k]:.2f} s after the last (needs {PACE[k]} s)')
        last[k] = t
    return bad


if __name__ == '__main__':
    name = sys.argv[1]
    src = os.path.join('out', name)
    parts = json.load(open(os.path.join(src, 'parts.json')))
    events = json.load(open(os.path.join(src, 'events.json')))
    fin = os.path.join('out', name + '-final')
    total = crossfade(src, fin, parts)
    mp4 = os.path.join('out', name + '.mp4')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(FPS), '-i', os.path.join(fin, '%05d.png'), '-c:v', 'libx264', '-preset', 'veryfast',
                    '-crf', '18', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', mp4], check=True)
    rows = cues(name, parts, events, total)
    msg = f'{name}: {total} frames, {total / FPS:.1f} s, mp4 {os.path.getsize(mp4) // 1024} KB, {len(rows)} cues'
    if '--gif' in sys.argv:
        ng = os.path.join('out', name + '-nograin'); fin2 = ng + '-final'
        crossfade(ng, fin2, json.load(open(os.path.join(ng, 'parts.json'))))
        gif = os.path.join('out', name + '.gif')
        fps = os.environ.get('GIF_FPS', '15'); width = os.environ.get('GIF_W', '960')
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', str(FPS), '-i', os.path.join(fin2, '%05d.png'), '-vf',
                        f'fps={fps},scale={width}:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=256:stats_mode=full[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle',
                        gif], check=True)
        msg += f', gif {os.path.getsize(gif) // 1024} KB'
    print(msg)
    bad = pace(rows)
    print('pacing: OK' if not bad else f'pacing: {len(bad)} too fast\n  ' + '\n  '.join(bad[:12]))
