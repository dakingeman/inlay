# Builds docs/images/whats-new-1.3.0.mp4: the title card, the What's new film (WhatsNewFilmExport.clip) and the end card.
# Music (tools/video/sound, docs/brand/MUSIC.md, profile whats-new-1.3.json and cues-1.3.md) then goes in with mux.py.
# Page to page the app dissolves its close-up (not the words) over 240 ms (WhatsNewStage.CROSS_MS); the checks' clock skips a few
# frames right after a page change, so the film's frames there are redrawn here as that same dissolve.
import json, os, subprocess, sys, shutil
from PIL import Image
here = os.path.dirname(os.path.abspath(__file__)); root = os.path.abspath(os.path.join(here, '../..'))
film = os.path.join(root, 'app/build/wn-clip'); cards = os.path.join(here, 'out/wn13-cards')
out = os.path.join(here, 'out/wn13-film'); shutil.rmtree(out, ignore_errors=True); os.makedirs(out)
meta = json.load(open(os.path.join(film, 'pages.json')))
n = meta['frames']; starts = [p['from'] for p in meta['pages']][1:]
CROSS = 0.240 * 30
# Only the close-up dissolves, as in the app (the words come in on their own): the stage's box on the Odin 2 Portal
# (835 x 469 dp at 368 dpi): everything right of the words column (the film has no button prompts)
STAGE = (820, 0, 1920, 1080)
def frame(i):
    im = Image.open(os.path.join(film, '%04d.png' % i)).convert('RGB')
    if im.size == (1920, 1080): return im
    c = Image.new('RGB', (1920, 1080), (0, 0, 0)); c.paste(im, (0, (1080 - im.height) // 2)); return c   # the Odin's 1078 rows, padded
redo = {}
for s in starts:
    old = frame(s - 1); k = 0
    while k < CROSS:
        t = (k + 1) / CROSS; a = 1 - min(1, t * t * (3 - 2 * t))
        new = frame(s + max(k, 1)); box = STAGE
        new.paste(Image.blend(new.crop(box), old.crop(box), a), box[:2]); redo[s + k] = new; k += 1
for i in range(n):
    (redo.get(i) or frame(i)).save(os.path.join(out, '%04d.png' % i))
dst = os.path.join(root, 'docs/images/whats-new-1.3.0.mp4')
first = 2.5; off2 = round(first - 0.3 + n / 30 - 0.3, 3)
subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-framerate', '30', '-i', os.path.join(cards, 'p00/%05d.png'),
    '-framerate', '30', '-i', os.path.join(out, '%04d.png'), '-framerate', '30', '-i', os.path.join(cards, 'p01/%05d.png'),
    '-filter_complex', f'[0:v]format=yuv420p,setsar=1[a];[1:v]format=yuv420p,setsar=1[b];[2:v]format=yuv420p,setsar=1[c];'
    f'[a][b]xfade=transition=fade:duration=0.3:offset={first - 0.3}[ab];[ab][c]xfade=transition=fade:duration=0.3:offset={off2}[v]',
    '-map', '[v]', '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '18', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', dst], check=True)
print('wrote', dst)
