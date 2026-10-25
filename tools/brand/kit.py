import logo, ast
INK, PAPER, TEAL, TEAL_DEEP, GRAPHITE = "#0B0B0E", "#F2F3F5", "#72D6E8", "#1E8597", "#1B1B1F"
parts, adv, cap, desc = ast.literal_eval(open('wordmark_raw.txt').read())
W, A = logo.full(); SW, SA = logo.small()
def svg(vb, body, w=None, h=None):
    size = f' width="{w}" height="{h}"' if w else ""
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{vb}"{size}>{body}</svg>\n'
def symbol(keys=PAPER, lit=TEAL, small=False):
    w, a = (SW, SA) if small else (W, A)
    return f'<path fill="{keys}" d="{w}"/><path fill="{lit}" d="{a}"/>'
def mono(colour, small=False):
    w, a = (SW, SA) if small else (W, A)     # the lit key becomes an empty slot
    return f'<path fill="{colour}" d="{w}"/>'
TILE_R = 24
def tile(bg=INK): return f'<rect width="108" height="108" rx="{TILE_R}" fill="{bg}"/>'
def wordmark(colour, x, y, height):
    """Wordmark sized by cap height, baseline at y."""
    s = height / cap
    return f'<g fill="{colour}" transform="translate({x:.2f},{y:.2f}) scale({s:.5f},{-s:.5f})">' + "".join(parts) + '</g>', adv * s
files = {}
files["inlay-icon.svg"] = svg("0 0 108 108", tile() + symbol())
files["inlay-icon-small.svg"] = svg("0 0 108 108", tile() + symbol(small=True))
files["inlay-symbol.svg"] = svg("18 18 72 72", symbol())
files["inlay-symbol-on-light.svg"] = svg("18 18 72 72", symbol(INK, TEAL_DEEP))
files["inlay-symbol-black.svg"] = svg("18 18 72 72", mono("#000000"))
files["inlay-symbol-white.svg"] = svg("18 18 72 72", mono("#FFFFFF"))
# Lockups: tile height = 2.2 x cap height, gap = 0.5 x tile
def lockup(dark=True):
    """On dark: the bare symbol (a dark tile would vanish into the background).
    On light: the app tile. Symbol height = 1.25 x the wordmark's cap height; the gap is
    one key wide plus a gap, so the name sits like the next key in the row."""
    if dark:
        # symbol content box in the 108 grid: x 30.25..77.75, y 31.5..75.5 (44 tall)
        caph = 40.0; sh = caph * 1.25; sc = sh / 44.0
        icon = f'<g transform="scale({sc:.4f}) translate(-30.25,-31.5)">{symbol()}</g>'
        iw = 47.5 * sc; gap = 17.25 * sc
        wm, wmw = wordmark(PAPER, iw + gap, sh/2 + caph/2, caph)
        total, height = iw + gap + wmw, sh
    else:
        th = 108; caph = th / 2.2; gap = th * 0.36
        icon = tile(INK) + symbol()
        wm, wmw = wordmark(INK, th + gap, th/2 + caph/2, caph)
        total, height = th + gap + wmw, th
    pad = height * 0.45
    bg = f'<rect x="{-pad}" y="{-pad}" width="{total+2*pad}" height="{height+2*pad}" fill="{INK if dark else "#FFFFFF"}"/>'
    return svg(f"{-pad:.1f} {-pad:.1f} {total+2*pad:.1f} {height+2*pad:.1f}", bg + icon + wm)
files["inlay-lockup-dark.svg"] = lockup(True)
files["inlay-lockup-light.svg"] = lockup(False)
wm, wmw = wordmark(PAPER, 0, 668, 668)
files["inlay-wordmark.svg"] = svg(f"0 -40 {adv+20} {668+80}", wm)
for name, body in files.items(): open("kit/" + name, "w").write(body)
print(list(files))
