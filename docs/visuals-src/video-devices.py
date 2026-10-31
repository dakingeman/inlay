# python3 video-devices.py — the device drawings the launch videos need to place each device (video.html, the slot rule):
# the app's own drawing for every device a scene can show (shoulder keys hidden, as in the videos) and its button layout
# (_geo, from the app's .json). Writes renders/video-devices.js. The pictures themselves come from the recording.
import json
SRC = '../../app/src/main/assets/devices/'
FILES = {'controller': 'controller-xbox', 'wide': 'wide-xbox', 'vertical': 'vertical-xbox', 'vertical-sticks': 'vertical-sticks-xbox', 'remote': 'remote'}
HIDE = '.p4x13,.p4x14,.p5x16,.p5x17,.p6x19,.p6x20,.p7x22,.p7x23{display:none}'
out = {}
for dev, f in FILES.items():
    s = open(SRC + f + '.svg').read()
    out[dev] = {'svg': s.replace('</svg>', f'<style>{HIDE if dev != "remote" else ""}</style></svg>'), 'geo': json.load(open(SRC + f + '.json'))['_geo']}
open('renders/video-devices.js', 'w').write('const VDEV=' + json.dumps(out) + ';\n')
print('wrote renders/video-devices.js:', ', '.join(out))
