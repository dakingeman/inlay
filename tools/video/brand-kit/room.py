"""How much free space each button has round it, for the press and hold rings (last step of build.sh).

Renders every device picture (nothing lit) large, then for each button that can light measures the
distance from its edge to the nearest other drawn thing: another button, an outline, a label, the
screen, the picture's edge. Writes it into the picture's _geo as "room" (picture units). The app sizes
its rings so the widest one (the held Timer ring) plus a little air always fits in the tightest room.
Run: python3 room.py ../../../app/src/main/assets/devices
"""
import json, os, re, sys
import numpy as np
from PIL import Image
from io import BytesIO
from scipy import ndimage
from playwright.sync_api import sync_playwright

DIR = sys.argv[1]
H_PX = 1600                                   # render height: about 0.3 picture units per pixel
ARMS = ['Dup', 'Ddown', 'Dleft', 'Dright']

def mask(shape, g, s, grow=0.0):
    yy, xx = np.mgrid[0:shape[0], 0:shape[1]]
    x0, y0, x1, y1 = g['x'] * s - grow, g['y'] * s - grow, (g['x'] + g['w']) * s + grow, (g['y'] + g['h']) * s + grow
    if g.get('tag') == 'circle':
        cx, cy, r = (x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / 2
        return (xx - cx) ** 2 + (yy - cy) ** 2 <= r * r
    return (xx >= x0) & (xx <= x1) & (yy >= y0) & (yy <= y1)

with sync_playwright() as p:
    b = p.chromium.launch(); page = b.new_page()
    for f in sorted(x for x in os.listdir(DIR) if x.endswith('.svg')):
        svg = open(os.path.join(DIR, f)).read(); jf = os.path.join(DIR, f[:-4] + '.json'); css = json.load(open(jf))
        vb = list(map(float, re.search(r'viewBox="([^"]+)"', svg).group(1).split()))
        s = H_PX / vb[3]; W = int(round(vb[2] * s))
        sized = re.sub(r'<svg\b', f'<svg width="{W}" height="{H_PX}"', svg, count=1)
        page.set_viewport_size({'width': W, 'height': H_PX})
        page.set_content(f'<html><body style="margin:0;background:#000">{sized}</body></html>')
        im = np.asarray(Image.open(BytesIO(page.screenshot())).convert('RGB')).astype(int)
        drawn = im.max(axis=2) > 45           # outlines, labels, symbols (body and wells are darker)
        drawn[[0, -1], :] = True; drawn[:, [0, -1]] = True   # the picture's edge counts as a neighbour
        geo = css['_geo']
        # shapes in render pixels, from the picture origin
        sh = lambda g: dict(g, x=g['x'] - vb[0], y=g['y'] - vb[1])
        cross = None
        if all(a in geo for a in ARMS):
            xs = [geo[a]['x'] for a in ARMS]; ys = [geo[a]['y'] for a in ARMS]
            xe = [geo[a]['x'] + geo[a]['w'] for a in ARMS]; ye = [geo[a]['y'] + geo[a]['h'] for a in ARMS]
            cross = {'x': min(xs), 'y': min(ys), 'w': max(xe) - min(xs), 'h': max(ye) - min(ys), 'tag': 'rect'}
        def path_mask(g):
            # a shape that isn't a circle or a box (the remote's arrows): drawn on its own, filled
            mm = ','.join(str(v) for v in g['m'])
            one = f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H_PX}" viewBox="{' '.join(map(str, vb))}"><path d="{g["d"]}" transform="matrix({mm})" fill="#fff" stroke="#fff" stroke-width="3"/></svg>'
            page.set_content(f'<html><body style="margin:0;background:#000">{one}</body></html>')
            return np.asarray(Image.open(BytesIO(page.screenshot())).convert('L')) > 100
        grow = 4                                # px: the outline's outer half and a pixel of smoothing
        for k, g in geo.items():
            if 'stick' in g: continue         # a stick's ring sits inside its own well
            if k in ARMS and cross and g.get('tag') != 'path':
                # the D-pad is one cross: its arms and middle are its own, its empty corners are not
                vx = dict(cross, x=geo['Dup']['x'], w=geo['Dup']['w']); hz = dict(cross, y=geo['Dleft']['y'], h=geo['Dleft']['h'])
                own = mask(im.shape, sh(vx), s, grow=grow) | mask(im.shape, sh(hz), s, grow=grow)
            elif 'd' in g and 'm' in g:
                own = ndimage.binary_dilation(path_mask(g), iterations=grow)
            else:
                own = mask(im.shape, sh(g), s, grow=grow)
            others = drawn & ~own
            dist = ndimage.distance_transform_edt(~others)
            edge = ndimage.binary_dilation(own, iterations=2) & ~own
            g['room'] = round(float(dist[edge].min()) / s, 2)
            if os.environ.get('ROOM_WHY'):        # where the nearest neighbour is (picture units), for layout work
                iy, ix = np.unravel_index(np.where(edge, dist, np.inf).argmin(), dist.shape)
                _, (ny, nx) = ndimage.distance_transform_edt(~others, return_indices=True)[0], ndimage.distance_transform_edt(~others, return_indices=True)[1][:, iy, ix]
                g['_why'] = [round(ix / s + vb[0], 1), round(iy / s + vb[1], 1), round(nx / s + vb[0], 1), round(ny / s + vb[1], 1)]
        json.dump(css, open(jf, 'w'), separators=(',', ':'))
        print(f, ' '.join(f"{k}:{g['room']}" for k, g in geo.items() if 'room' in g))
    b.close()
