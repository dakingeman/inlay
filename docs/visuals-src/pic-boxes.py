"""python3 pic-boxes.py <scene> ...  — for the videos: where each device drawing sits in a keyboard scene's pic-NNNN.png frames,
without the floating shoulder keys (bumpers and triggers float above the body with a gap; the videos never press them, so
they're left out for a cleaner, bigger picture, as in the hero). Writes app/build/visuals/video/<scene>/picbox.json (and kbbox.json, the keyboard's drawn box):
{device: [left, top, right, bottom]} in frame pixels, the same box for every frame of that device so nothing jumps."""
import json, os, sys
import numpy as np
from PIL import Image

REC = os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../app/build/visuals/video')


def body_box(a):
    """The drawing's box below its floating keys: rows with ink form runs; the biggest run is the body,
    and every run above it (the floating keys) is dropped."""
    rows = (a > 160).any(axis=1)                  # solid lines only: the soft floor shadow stays out of the box
    runs, y = [], 0
    while y < len(rows):
        if rows[y]:
            s = y
            while y < len(rows) and rows[y]: y += 1
            runs.append((s, y))
        y += 1
    if not runs: return None
    body = max(runs, key=lambda r: r[1] - r[0])
    t, b = body                                    # the floor shadow below may spill out of the box; it isn't clipped
    cols = (a[t:b] > 160).any(axis=0); xs = np.where(cols)[0]
    return [int(xs[0]), int(t), int(xs[-1]) + 1, int(b)]


for scene in sys.argv[1:]:
    d = os.path.join(REC, scene)
    frames = json.load(open(os.path.join(d, 'frames.json')))
    boxes, full = {}, {}
    for i in range(0, len(frames), 3):
        a = np.array(Image.open(os.path.join(d, f'pic-{i:04d}.png')).getchannel('A'))
        bx = body_box(a)
        if not bx: continue
        dev = frames[i]['dev']; o = boxes.get(dev)
        boxes[dev] = bx if not o else [min(o[0], bx[0]), min(o[1], bx[1]), max(o[2], bx[2]), max(o[3], bx[3])]
        ys, xs = np.where(a > 160); fb = [int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1]; o = full.get(dev)
        full[dev] = fb if not o else [min(o[0], fb[0]), min(o[1], fb[1]), max(o[2], fb[2]), max(o[3], fb[3])]
    json.dump(boxes, open(os.path.join(d, 'picbox.json'), 'w'))
    # the shoulder keys the scene uses (they show only when used): the top of everything drawn, for the slot to make room
    json.dump(full, open(os.path.join(d, 'picfull.json'), 'w'))
    # the keyboard's drawn box (TV mode draws its own panel, shown as it is): kbbox.json, from the first frame
    a = np.array(Image.open(os.path.join(d, 'kb-0000.png')).getchannel('A')); ys, xs = np.where(a > 8)
    json.dump([int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1], open(os.path.join(d, 'kbbox.json'), 'w'))
    print(scene, boxes)
