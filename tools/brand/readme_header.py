"""
The README header (option B, chosen Oct 2026): the typing logo and the tagline on one line, on GitHub's own page
colours, for its dark and light themes, plus a still of each for people who turn animations off.
Run after logo_motion.py and render_motion.js (600 px wide) have made DIR/logo-github-dark and -light:
    python3 readme_header.py DIR      writes DIR/header-dark/, DIR/header-light/ (frames) and the stills
The tagline is GitHub's own quiet text colour: 6.5 : 1 on dark, 6.1 : 1 on light.
"""
import sys, os, io, contextlib
from PIL import Image, ImageDraw, ImageFont
with contextlib.redirect_stdout(io.StringIO()): import wordmark as W, logo_motion as M

DIR = sys.argv[1]
TAG = "A keyboard for your controller."
THEMES = {"dark": ("#0D1117", "#9198A1"), "light": ("#FFFFFF", "#59636E")}
FONT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../app/src/main/res/font/atkinson_next.ttf")

l, t, r, b = W.BOX; l -= W.KEY; t -= W.KEY; r += W.KEY; b += W.KEY
LW, LH = Image.open(os.path.join(DIR, "logo-github-dark", "f000.png")).size
k = LW / (r - l); key_px = W.KEY * k
mid_y = (-W.ASC / 2 - t) * k                             # the middle of the l's height, in px
font = ImageFont.truetype(FONT, 44); font.set_variation_by_axes([400])
x_top, x_bottom = font.getbbox("x")[1], font.getbbox("x")[3]
width = int(LW + font.getbbox(TAG)[2] + key_px)          # logo (with its clear space) + tagline + one key after it
for name, (bg, fg) in THEMES.items():
    src = os.path.join(DIR, f"logo-github-{name}"); out = os.path.join(DIR, f"header-{name}"); os.makedirs(out, exist_ok=True)
    frames = sorted(os.listdir(src))
    for f in frames:
        im = Image.new("RGB", (width, LH), bg); im.paste(Image.open(os.path.join(src, f)).convert("RGB"), (0, 0))
        ImageDraw.Draw(im).text((LW, mid_y - x_top - (x_bottom - x_top) / 2), TAG, font=font, fill=fg)  # x-height on the l's middle
        im.save(os.path.join(out, f))
    # the still is the resting logo: the name typed, the cursor lit (200 ms after the last letter). Not the loop's
    # first or last frame: those are the empty field, before typing and after clearing
    rest = round((M.TYPE[-1] + .2) * M.FPS)
    Image.open(os.path.join(out, frames[rest])).save(os.path.join(DIR, f"header-{name}-still.png"))
print("header", width, "x", LH, "px (shown at half size)")
