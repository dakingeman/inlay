# python3 logo-anim.py — the logo animation (tools/brand/logo_motion.py: "inlay" typed on the logo's own keys) as frames
# for the video end card: 30 fps from the empty field to "inlay|" typed, on a clear ground (the video's backdrop shows),
# plus REST, the typed logo with its cursor lit (the logo itself). Writes renders/logo-anim.js.
import sys, os, json, re
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../tools/brand'))
sys.argv = [sys.argv[0], os.path.join(os.path.dirname(os.path.abspath(__file__)), 'out', 'logo-motion-tmp')]
import logo_motion as L
th = L.THEMES['dark']; check = []
clear = lambda s: re.sub(r'<rect [^>]*/>', '', s, count=1)
end = L.TYPE[-1] + .5                                       # the last letter, then a moment
frames = [clear(L.frame(f / 30, th, check)) for f in range(round(end * 30))]
rest = clear(L.frame(L.TYPE[-1] + .2, th, check))          # inside the 400 ms hold: the cursor lit, as in the logo
os.makedirs('renders', exist_ok=True)
open('renders/logo-anim.js', 'w').write('const LOGO_ANIM=' + json.dumps({'fps': 30, 'frames': frames, 'rest': rest,
    'keys': [round(t, 3) for t in L.TYPE]}) + ';\n')
print('wrote renders/logo-anim.js:', len(frames), 'frames,', f'{end:.2f} s; letters at', [round(t, 2) for t in L.TYPE])
