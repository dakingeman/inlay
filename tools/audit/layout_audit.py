"""Layout audit of the device pictures' press, hold and stick looks.

Reads the shots LayoutAuditExport writes (app/build/layout/<size>/<picture>/...), and for every lit key
measures how close its rings (and a stick's arrows) come to everything else in the picture: other
buttons, outlines, labels, the screen, the picture's edge, and, when two keys are held together, each
other's rings. All distances in dp. Prints every case under the limits; exit code 1 if any.

Run: python3 tools/audit/layout_audit.py [app/build/layout]
"""
import json, re, sys, os
import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = sys.argv[1] if len(sys.argv) > 1 else 'app/build/layout'
ASSETS = 'app/src/main/assets/devices'
_t = json.load(open('design/tokens.json'))['brandAccent']
ACCENT = np.array([int(_t[i:i + 2], 16) for i in (1, 3, 5)])   # the app default (Design.BRAND_ACCENT), from design/tokens.json
DENSITY = 3.0                      # the shots are xxhdpi
MIN_AIR = 0.5                      # dp of clear air between a ring and anything else (DevicePicture ROOM_AIR_DP)
MIN_EDGE = 0.5                     # dp from the picture's edge
REMOTE = {'A': 'OK', 'B': 'BACK'}

def load(p): return np.asarray(Image.open(p).convert('RGB')).astype(int)
def accent(im): return np.abs(im - ACCENT).sum(axis=2) < 60
def drawn(im): return im.max(axis=2) > 45          # anything that isn't background

def mapping(asset, im):
    svg = open(os.path.join(ASSETS, asset + '.svg')).read()
    vb = list(map(float, re.search(r'viewBox="([^"]+)"', svg).group(1).split()))
    H, W = im.shape[:2]; s = min(W / vb[2], H / vb[3])
    return s, (W - vb[2] * s) / 2 - vb[0] * s, (H - vb[3] * s) / 2 - vb[1] * s

def key_mask(g, m, shape, grow=0.0):
    s, ox, oy = m
    x0, y0 = ox + g['x'] * s - grow, oy + g['y'] * s - grow
    x1, y1 = ox + (g['x'] + g['w']) * s + grow, oy + (g['y'] + g['h']) * s + grow
    yy, xx = np.mgrid[0:shape[0], 0:shape[1]]
    if g.get('tag') == 'circle':
        cx, cy, r = (x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / 2
        return (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r
    return (xx >= x0) & (xx <= x1) & (yy >= y0) & (yy <= y1)

def own_area(k, g, m, shape):
    """The key itself and what belongs to it (its outline, label; a stick's well)."""
    if 'stick' in g:
        st = g['stick']; s, ox, oy = m
        cx, cy = ox + (g['x'] + g['w'] / 2) * s, oy + (g['y'] + g['h'] / 2) * s
        r = max(st.get('well', 0), st['r']) * s + 2
        yy, xx = np.mgrid[0:shape[0], 0:shape[1]]
        return (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r
    return key_mask(g, m, shape, grow=2.5)   # its outline (drawn half outside its edge) and a pixel of smoothing

problems, checked, worst = [], 0, {}
for size in sorted(os.listdir(ROOT)):
    for pic in sorted(os.listdir(os.path.join(ROOT, size))):
        d = os.path.join(ROOT, size, pic); asset = pic.replace('playstation', 'ps')
        geo = json.load(open(os.path.join(ASSETS, asset + '.json')))['_geo']
        idle = load(os.path.join(d, 'idle.png')); m = mapping(asset, idle)
        base_drawn = drawn(idle)
        for f in sorted(os.listdir(d)):
            if f == 'idle.png': continue
            im = load(os.path.join(d, f))
            kind, rest = f[:-4].split('-', 1)
            # a TV remote's arrows are one rocker: two of them can't be pressed together, so that pair can't happen
            if asset == 'remote' and kind == 'pair' and all(k.startswith('D') for k in rest.split('+')): continue
            keys = rest.split('+') if kind == 'pair' else [rest.split('-')[0] if kind == 'stick' else rest]
            keys = [REMOTE.get(k, k) if asset == 'remote' else k for k in keys]
            own = np.zeros(idle.shape[:2], bool)
            for k in keys: own |= own_area(k, geo[k], m, idle.shape)
            new = accent(im) & ~accent(idle)
            # marks outside the keys themselves: rings, arrows
            inside = np.zeros_like(own)
            for k in keys: inside |= key_mask(geo[k], m, idle.shape, grow=0.5)
            if not any('stick' in geo[k] for k in keys): marks = new & ~inside
            else: marks = new & ~inside
            if not marks.any(): continue
            checked += 1
            others = base_drawn & ~own
            dist = ndimage.distance_transform_edt(~others) / DENSITY
            air = float(dist[marks].min())
            H, W = marks.shape; ys, xs = np.where(marks)
            edge = float(min(xs.min(), ys.min(), W - 1 - xs.max(), H - 1 - ys.max())) / DENSITY
            ring_air = None
            if kind == 'pair':   # the two keys' lit shapes (key + rings) must be separate pieces, with air between
                lit = accent(im) | new
                lab, n = ndimage.label(lit)
                pa = set(np.unique(lab[key_mask(geo[keys[0]], m, idle.shape, 0.5)])) - {0}
                pb = set(np.unique(lab[key_mask(geo[keys[1]], m, idle.shape, 0.5)])) - {0}
                if pa & pb: ring_air = 0.0
                else:
                    A = np.isin(lab, list(pa)); B = np.isin(lab, list(pb))
                    ring_air = float(ndimage.distance_transform_edt(~A)[B].min()) / DENSITY if A.any() and B.any() else None
            tag = f'{size}dp {pic} {f[:-4]}'
            worst[tag] = air
            if air < MIN_AIR: problems.append(f'{tag}: {air:.2f} dp from another part')
            if edge < MIN_EDGE: problems.append(f'{tag}: {edge:.2f} dp from the picture edge')
            if ring_air is not None and ring_air < 0.4: problems.append(f'{tag}: the two rings {ring_air:.2f} dp apart')
print(f'checked {checked} lit states')
for p in problems: print('  ', p)
tight = sorted(worst.items(), key=lambda x: x[1])[:8]
print('tightest:', ', '.join(f'{k} {v:.2f}' for k, v in tight))
sys.exit(1 if problems else 0)
