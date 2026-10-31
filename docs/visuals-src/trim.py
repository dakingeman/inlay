"""python3 trim.py : copies the app's renders into renders/ and crops device pictures and badges to their drawing."""
import glob, os, shutil
from PIL import Image
src = os.path.join(os.path.dirname(__file__), '../../app/build/visuals')
for f in glob.glob(os.path.join(src, '*.png')):
    name = os.path.basename(f); dst = os.path.join('renders', name)
    im = Image.open(f)
    if name.startswith(('pic-', 'dev-', 'icons-pic-', 'badge-')):
        bb = im.getchannel('A').getbbox()
        pad = 2 if name.startswith('badge-') else 12      # badges keep 2 px of clear room all round (31 Oct: nothing may touch an edge)
        bb = (max(0, bb[0] - pad), max(0, bb[1] - pad), min(im.size[0], bb[2] + pad), min(im.size[1], bb[3] + pad))
        im = im.crop(bb)
    im.save(dst)
