"""
Builds Inlay's brand files from logo.py (the symbol) and wordmark.py (the name), straight into docs/brand/,
plus the app's Android TV banner and the hero lockup used by the visuals. Run:  python3 kit.py
The PNGs are rendered from these by png.js.
"""
import os, logo, wordmark as WM
from wordmark import INK, PAPER, TEAL, TEAL_DEEP
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.join(HERE, "../..")
BRAND = os.path.join(ROOT, "docs/brand")
W, A = logo.full(); SW, SA = logo.small()

def svg(vb, body):
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}">{body}</svg>\n'
def symbol(keys=PAPER, lit=TEAL, small=False):
    w, a = (SW, SA) if small else (W, A)
    return f'<path fill="{keys}" d="{w}"/><path fill="{lit}" d="{a}"/>'
def mono(colour, small=False):
    w, a = (SW, SA) if small else (W, A)     # the lit key becomes an empty slot
    return f'<path fill="{colour}" d="{w}"/>'
TILE_R = 24
def tile(bg=INK): return f'<rect width="108" height="108" rx="{TILE_R}" fill="{bg}"/>'

files = {}
# the symbol and icon (unchanged since the 1.2 refresh)
files["inlay-icon.svg"] = svg("0 0 108 108", tile() + symbol())
files["inlay-icon-small.svg"] = svg("0 0 108 108", tile() + symbol(small=True))
files["inlay-symbol.svg"] = svg("18 18 72 72", symbol())
files["inlay-symbol-on-light.svg"] = svg("18 18 72 72", symbol(INK, TEAL_DEEP))
files["inlay-symbol-black.svg"] = svg("18 18 72 72", mono("#000000"))
files["inlay-symbol-white.svg"] = svg("18 18 72 72", mono("#FFFFFF"))
# the name: inlay|
files["inlay-wordmark.svg"] = WM.wordmark_svg(PAPER, TEAL)              # on dark
files["inlay-wordmark-light.svg"] = WM.wordmark_svg(INK, TEAL_DEEP)     # on light
files["inlay-wordmark-black.svg"] = WM.wordmark_svg("#000000", "#000000")
files["inlay-wordmark-white.svg"] = WM.wordmark_svg("#FFFFFF", "#FFFFFF")
# symbol + name: on their ground, and clear (for the README and for laying over pictures)
files["inlay-lockup-dark.svg"] = WM.lockup_svg(True, INK)
files["inlay-lockup-light.svg"] = WM.lockup_svg(False, "#FFFFFF")
files["inlay-lockup-clear-dark.svg"] = WM.lockup_svg(True)
files["inlay-lockup-clear-light.svg"] = WM.lockup_svg(False)

# ---- Android TV banner: 320 x 180 dp, the lockup on ink ----
BW, BH = 320, 180
l, t, r, b = WM.BOX
S = 208 / (r - l)                          # the lockup is 65 % of the banner's width
TX = BW / 2 - S * (l + r) / 2
TY = BH / 2 - S * (WM.SYM_Y + WM.SYM_H / 2) # centred on the symbol (and the l's height), which is what the eye reads;
                                           # the y's tail hangs below, as it does in the lockup
def banner_svg():
    return svg(f"0 0 {BW} {BH}", f'<rect width="{BW}" height="{BH}" fill="{INK}"/>'
               f'<g transform="translate({TX:.2f},{TY:.2f}) scale({S:.5f})">'
               f'{WM.symbol_group(PAPER, TEAL)}{WM.word_group(PAPER, TEAL)}</g>')
files["inlay-tv-banner.svg"] = banner_svg()

def banner_xml():
    def grp(attrs, inner, ind):
        a = " ".join(f'android:{k}="{v}"' for k, v in attrs.items())
        return f'{ind}<group {a}>\n{inner}{ind}</group>\n'
    def path(fill, d, ind): return f'{ind}<path android:fillColor="{fill}" android:pathData="{d}"/>\n'
    i4, i8, i12, i16 = " " * 8, " " * 12, " " * 16, " " * 20
    sym = grp({"translateX": f"{WM.SYM_X:.2f}", "translateY": f"{WM.SYM_Y:.2f}"},
              grp({"scaleX": f"{WM.SYM_SCALE:.5f}", "scaleY": f"{WM.SYM_SCALE:.5f}"},
                  grp({"translateX": "-30.25", "translateY": "-31.5"}, path(PAPER, W, i16) + path(TEAL, A, i16), i12), i8), i4)
    letters = "".join(grp({"translateX": f"{x}"}, path(PAPER, WM.outline(n), i16), i12) for n, x, _, _ in WM.LETTERS)
    cur = path(TEAL, logo.rr(WM.CURSOR_X, -WM.OVERSHOOT, WM.CURSOR_W, WM.ASC + 2 * WM.OVERSHOOT, WM.CURSOR_W / 2), i12)
    word = grp({"scaleY": "-1"}, letters + cur, i8)
    body = grp({"translateX": f"{TX:.3f}", "translateY": f"{TY:.3f}"},
               grp({"scaleX": f"{S:.6f}", "scaleY": f"{S:.6f}"}, sym + word, " " * 8), " " * 4)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Android TV home-screen banner (320 x 180 dp): the Inlay lockup on ink. Built by tools/brand/kit.py -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{BW}dp"\n    android:height="{BH}dp"\n'
            f'    android:viewportWidth="{BW}"\n    android:viewportHeight="{BH}">\n'
            f'    <path android:fillColor="{INK}" android:pathData="M0,0H{BW}V{BH}H0Z"/>\n' + body + '</vector>\n')

if __name__ == "__main__":
    for name, body in files.items(): open(os.path.join(BRAND, name), "w").write(body)
    open(os.path.join(ROOT, "app/src/main/res/drawable/tv_banner.xml"), "w").write(banner_xml())
    open(os.path.join(ROOT, "docs/visuals-src/hero-parts/lockup-clear.svg"), "w").write(WM.lockup_svg(True))
    ink_top, ink_bottom = TY + S * t, TY + S * b
    print("wrote", len(files), "brand files, tv_banner.xml and hero-parts/lockup-clear.svg")
    print(f"banner: lockup {S * (r - l):.0f} x {ink_bottom - ink_top:.0f} dp, left/right margin {TX + S * l:.0f} dp, "
          f"top {ink_top:.0f} dp, bottom {BH - ink_bottom:.0f} dp")
