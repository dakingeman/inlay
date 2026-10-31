# python3 logo-keys.py <logoFramesDir> <keyFramesDir> <recordingDir> <outDir>
# Hero L9-A: adds the logo animation (docs/brand/motion, its own frames at 50 fps) over the keyboard frames from
# logo-keys.js. The logo's symbol stays still (the real keyboard does the typing); its letters follow the recording:
# typed exactly on the logo's own timing, then deleted one per Delete-key tap.
import sys, os, json
import numpy as np
from PIL import Image
L, K, R, od = sys.argv[1:5]
BG = np.array([11, 10, 13])                     # the logo animation's background
box = json.load(open(f'{K}/logo.json')); k = box['h'] / 150
s = 368 * k / 1259                              # the still lockup's ink width / the animation's ink width at rest
ox, oy = box['x'] + 26 * k - 90 * s, box['y'] + 26 * k - 90 * s
ix, iy = int(np.floor(ox)), int(np.floor(oy)); fx, fy = ox - ix, oy - iy
W, H = int(1440 * s) + 3, int(516 * s) + 3
sym = Image.open(f'{L}/f0001.png').convert('RGB').crop((0, 0, 470, 516))
fr = json.load(open(f'{R}/frames.json'))
chg = [i for i in range(1, len(fr)) if len(fr[i]['before']) < len(fr[i - 1]['before'])]
D = [4.66, 4.70, 4.76, 4.82, 4.86]              # when the animation removes each letter
def animt(i):
    if i < chg[0]: return i / 30 + 0.02
    for j in range(5):
        if j == 4 or i < chg[j + 1]: return D[j] + 0.02 if j < 4 else (4.88 + (i - chg[4]) / 30) % 5.16
os.makedirs(od, exist_ok=True)
for i in range(len(fr)):
    lg = Image.open(f'{L}/f{min(258, round(animt(i) * 50) + 1):04d}.png').convert('RGB'); lg.paste(sym, (0, 0))
    sc = lg.transform((W, H), Image.AFFINE, (1 / s, 0, -fx / s, 0, 1 / s, -fy / s), resample=Image.BICUBIC, fillcolor=(11, 10, 13))
    base = np.asarray(Image.open(f'{K}/k{i:04d}.png').convert('RGB')).astype(np.int16)
    base[iy:iy + H, ix:ix + W] += (np.asarray(sc).astype(np.int16) - BG)
    Image.fromarray(np.clip(base, 0, 255).astype(np.uint8)).save(f'{od}/f{i:04d}.png')
print(len(fr), 'frames')
