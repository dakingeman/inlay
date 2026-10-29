"""
Inlay wordmark, round 2 (proposal, Oct 2026): lowercase "inlay" in Atkinson Hyperlegible Next Bold, drawn as shapes.
Two forms of one idea, typing with a word suggestion:
  typing  in|lay   "in" typed, the cursor, "lay" suggested in grey   (big places: README header, video, social)
  final   inlay|   the word taken, the cursor resting after it        (everyday, the end of the typing loop)
plus one-colour and light versions, and lockups with the symbol. Every number is derived here, so the files
always agree. Run:  python3 wordmark2.py  (writes proposal/)
"""
import os, uharfbuzz as hb
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.boundsPen import BoundsPen
import logo

FONT = os.path.join(os.path.dirname(__file__), "../../app/src/main/res/font/atkinson_next.ttf")
WEIGHT = 700                     # BRAND.md: the name is set in Atkinson Hyperlegible Next Bold
TRACK = -12                      # units per letter: big bold letters sit a touch tighter (-1.2 %)
INK, PAPER, TEAL, TEAL_DEEP = "#0B0B0E", "#F2F3F5", "#72D6E8", "#1E8597"
GREY_ON_DARK, GREY_ON_LIGHT = "#6E717A", "#7A7D86"   # the suggested part; both about 4:1 on their ground, so light and dark match

font = instantiateVariableFont(TTFont(FONT), {"wght": WEIGHT})
font.save("/tmp/atk700.ttf")
gs = font.getGlyphSet()
blob = hb.Blob.from_file_path("/tmp/atk700.ttf"); face = hb.Face(blob); hbfont = hb.Font(face)

def shape(text):
    buf = hb.Buffer(); buf.add_str(text); buf.guess_segment_properties(); hb.shape(hbfont, buf, {"kern": True, "liga": False})
    names = [font.getGlyphName(i.codepoint) for i in buf.glyph_infos]
    return [(n, p.x_advance) for n, p in zip(names, buf.glyph_positions)]

def glyph(name, x):
    pen = SVGPathPen(gs); gs[name].draw(pen)
    b = BoundsPen(gs); gs[name].draw(b)
    return f'<path transform="translate({x},0)" d="{pen.getCommands()}"/>', b.bounds

ASC = None; STEM = None
def layout(text, cursor_at):
    """Glyphs of [text] with a cursor before index [cursor_at] (len = after the last). Returns pieces and metrics."""
    global ASC, STEM
    run = shape(text); x = 0; pieces = []; inks = []
    gap_extra = 0
    for i, (n, adv) in enumerate(run):
        if i == cursor_at: pieces.append(("cursor", x)); x += CURSOR_ROOM
        d, b = glyph(n, x); pieces.append(("glyph", i, d)); inks.append((b[0] + x, b[2] + x, b))
        x += adv + TRACK
    if cursor_at >= len(run): pieces.append(("cursor", x - TRACK + END_ROOM)); x += END_ROOM + CURSOR_W
    return pieces, x, inks

# The cursor: the straight stems of n and l are its neighbours, so its clearance on each side is measured from
# ink, and set equal to the stems' own gap in "nl" (optical spacing: stem to stem looks like stem to cursor)
lb = BoundsPen(gs); gs["l"].draw(lb); ASC = lb.bounds[3]
STEM = 140                                             # the n's stem at mid height, measured on the 700 instance
nb = BoundsPen(gs); gs["n"].draw(nb)
nl = dict(shape("nl"))
NL_GAP = (nl["n"] + TRACK - nb.bounds[2]) + lb.bounds[0]   # ink gap between n and l
CURSOR_W = round(STEM * 0.5)                          # half a stem: clearly thinner than the letters, still 1.5 px at 24 px
CLEAR = round(NL_GAP * 0.8)                           # a touch closer than the letters are to each other: it belongs
CURSOR_ROOM = CLEAR * 2 + CURSOR_W - NL_GAP           # extra room opened between n and l
END_ROOM = CLEAR                                      # after the y's arm (its ink, at x-height)

ob = BoundsPen(gs); gs["o"].draw(ob); OVERSHOOT = -ob.bounds[1]   # round letters dip this far past the line (12)

def cursor(x_left_ink_gap, colour, opacity=1):
    # Rounded ends look shorter than the flat ends of n and l, so the cursor overshoots both ends by the same
    # amount the font's round letters do: it then looks exactly as tall as the l
    r = CURSOR_W / 2
    op = "" if opacity >= 1 else f' opacity="{opacity:.3f}"'
    return f'<rect x="{x_left_ink_gap:.1f}" y="{-OVERSHOOT:.1f}" width="{CURSOR_W}" height="{ASC + 2 * OVERSHOOT:.1f}" rx="{r:.1f}" fill="{colour}"{op}/>'

def build(form, ink, cursor_colour, ghost):
    if form == "typing":
        pieces, width, inks = layout("inlay", 2)
    else:
        pieces, width, inks = layout("inlay", 5)
    body = []
    cx = None
    for p in pieces:
        if p[0] == "cursor":
            if form == "typing":
                n_right = inks[1][1]; cx = n_right + CLEAR
            else:
                cx = inks[4][1] + END_ROOM
            body.append(cursor(cx, cursor_colour))
        else:
            _, i, d = p
            colour = ghost if (form == "typing" and i >= 2 and ghost) else ink
            body.append(f'<g fill="{colour}">{d}</g>')
    right = max(cx + CURSOR_W if cx else 0, inks[-1][1])
    # flip: font units are y-up
    g = f'<g transform="scale(1,-1)">{"".join(body)}</g>'
    return g, right, inks[0][0]                            # the i's ink edge: the side bearing is not part of the mark

DESC = 0
yb = BoundsPen(gs); gs["y"].draw(yb); DESC = yb.bounds[1]     # the y's tail, below the baseline

def wordmark_svg(form, ink, cur, ghost=None, pad=0):
    g, right, ink_l = build(form, ink, cur, ghost)
    top = -ASC - OVERSHOOT; h = ASC + OVERSHOOT - DESC
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{ink_l - pad:.0f} {top - pad:.0f} {right - ink_l + 2 * pad:.0f} {h + 2 * pad:.0f}">{g}</svg>\n', right

def lockup_svg(form, dark=True):
    """Symbol + wordmark on a clear (transparent) ground. The symbol is as tall as the wordmark's l (baseline to
    ascender) x 1.25, centred on that height; the gap is one key plus one key gap, so the name sits like the
    next key in the row. Clear space: one key all round, inside the file."""
    ink = PAPER if dark else INK; cur = TEAL if dark else TEAL_DEEP
    ghost = (GREY_ON_DARK if dark else GREY_ON_LIGHT) if form == "typing" else None
    g, right, ink_l = build(form, ink, cur, ghost)
    sym_h = ASC * 1.25; sc = sym_h / 44.0                 # symbol content: 47.5 x 44 in the 108 grid
    sym_w = 47.5 * sc; key = 13 * sc; gap = (13 + 4.25) * sc
    sym = f'<g transform="translate({ink_l - sym_w - gap:.1f},{-ASC / 2 - sym_h / 2:.1f}) scale({sc:.4f}) translate(-30.25,-31.5)">' + \
          logo_symbol(ink, cur) + '</g>'
    left = ink_l - sym_w - gap - key; top = min(-ASC / 2 - sym_h / 2, -ASC) - key
    bottom = max(-ASC / 2 + sym_h / 2, -DESC) + key; w = right + key - left
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{left:.0f} {top:.0f} {w:.0f} {bottom - top:.0f}">{sym}<g transform="translate(0,0)">{g}</g></svg>\n'

W, A = logo.full()
def logo_symbol(keys, lit): return f'<path fill="{keys}" d="{W}"/><path fill="{lit}" d="{A}"/>'

out = os.path.join(os.path.dirname(__file__), "proposal"); os.makedirs(out, exist_ok=True)
files = {
    "inlay-wordmark-typing-dark.svg": wordmark_svg("typing", PAPER, TEAL, GREY_ON_DARK)[0],
    "inlay-wordmark-typing-light.svg": wordmark_svg("typing", INK, TEAL_DEEP, GREY_ON_LIGHT)[0],
    "inlay-wordmark-dark.svg": wordmark_svg("final", PAPER, TEAL)[0],
    "inlay-wordmark-light.svg": wordmark_svg("final", INK, TEAL_DEEP)[0],
    "inlay-wordmark-black.svg": wordmark_svg("final", "#000000", "#000000")[0],
    "inlay-wordmark-white.svg": wordmark_svg("final", "#FFFFFF", "#FFFFFF")[0],
    "inlay-lockup-typing-dark.svg": lockup_svg("typing", True),
    "inlay-lockup-typing-light.svg": lockup_svg("typing", False),
    "inlay-lockup-dark.svg": lockup_svg("final", True),
    "inlay-lockup-light.svg": lockup_svg("final", False),
}
for n, s in files.items(): open(os.path.join(out, n), "w").write(s)
print("stem", STEM, "nl gap", NL_GAP, "cursor", CURSOR_W, "clear", CLEAR, "asc", ASC, "desc", DESC)
