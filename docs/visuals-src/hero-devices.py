# python3 hero-devices.py — the four device drawings for the README hero (hero-final.html), made from the app's own
# drawings in app/src/main/assets/devices: the confirm button lit in the brand teal (the app's own lit style, from each
# drawing's .json), and the shoulder buttons hidden (they only show when used). Writes renders/dev3-*.svg.
import json, os
SRC = '../../app/src/main/assets/devices/'; OUT = 'renders/'
TEAL = json.load(open('../../design/tokens.json'))['brandAccent']
os.makedirs(OUT, exist_ok=True)
for f, btn in [('controller-xbox', 'A'), ('wide-ps', 'A'), ('vertical-switch', 'A'), ('remote', 'OK')]:
    s = open(SRC + f + '.svg').read()
    lit = json.load(open(SRC + f + '.json'))[btn].replace('ACC', TEAL)
    hide = '.p4x13,.p4x14,.p5x16,.p5x17,.p6x19,.p6x20,.p7x22,.p7x23{display:none}' if f != 'remote' else ''
    open(OUT + f'dev3-{f}.svg', 'w').write(s.replace('</svg>', f'<style>{lit}</style><style>{hide}</style></svg>'))
    print('wrote', OUT + f'dev3-{f}.svg')
