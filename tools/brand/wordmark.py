"""
Inlay wordmark (chosen 29 Oct 2026): lowercase "inlay" in Atkinson Hyperlegible Next Bold, drawn as shapes, with a
text cursor resting after it: inlay|  Every number is derived from the font here, so all the files agree.
Imported by kit.py (the brand files) and logo_motion.py (the animation); this file writes nothing itself.

The rules it follows (measured on the shapes, not on the letters' boxes):
  - cursor width = half the n's stem (70 of 140 units): clearly thinner than the letters, still 1.5 px at 24 px tall
  - cursor gap = 0.8 x the ink gap between n and l (77 units): a touch closer than the letters, so it belongs
  - cursor height = baseline to the l's top, plus the font's own 12-unit overshoot at both ends (rounded ends look
    shorter than flat ones), ends fully rounded
  - files are cropped to the ink: the i's side bearing is not part of the mark
  - lockup: the symbol is 1.25 x the l's height, centred on it; the gap is one key plus one key gap (the name sits like
    the next key in the row); clear space is one key all round
"""
import os, uharfbuzz as hb
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.boundsPen import BoundsPen
import logo

FONT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../app/src/main/res/font/atkinson_next.ttf")
WEIGHT = 700                     # BRAND.md: the name is set in Atkinson Hyperlegible Next Bold
TRACK = -12                      # units per letter: big bold letters sit a touch tighter (-1.2 %)
INK, PAPER, TEAL, TEAL_DEEP = "#0B0B0E", "#F2F3F5", "#72D6E8", "#1E8597"

font = instantiateVariableFont(TTFont(FONT), {"wght": WEIGHT})
_tmp = "/tmp/inlay-atk700.ttf"; font.save(_tmp)
gs = font.getGlyphSet()
hbfont = hb.Font(hb.Face(hb.Blob.from_file_path(_tmp)))

def shape(text):
    buf = hb.Buffer(); buf.add_str(text); buf.guess_segment_properties(); hb.shape(hbfont, buf, {"kern": True, "liga": False})
    return [(font.getGlyphName(i.codepoint), p.x_advance) for i, p in zip(buf.glyph_infos, buf.glyph_positions)]

def outline(name):
    pen = SVGPathPen(gs); gs[name].draw(pen); return pen.getCommands()

def bounds(name):
    b = BoundsPen(gs); gs[name].draw(b); return b.bounds

def glyph(name, x):
    return f'<path transform="translate({x},0)" d="{outline(name)}"/>', bounds(name)

ASC = bounds("l")[3]                                   # 708: baseline to the l's top
DESC = bounds("y")[1]                                  # -162: the y's tail
OVERSHOOT = -bounds("o")[1]                            # 12: how far round letters dip past the line
STEM = 140                                             # the n's stem at mid height, measured on the 700 instance
_nl = dict(shape("nl"))
NL_GAP = (_nl["n"] + TRACK - bounds("n")[2]) + bounds("l")[0]   # ink gap between n and l (95.8)
CURSOR_W = round(STEM * 0.5)                           # 70
CLEAR = round(NL_GAP * 0.8)                            # 77

def letters():
    """The five letters: (glyph name, x position, ink left, ink right)."""
    out = []; x = 0
    for n, adv in shape("inlay"):
        b = bounds(n); out.append((n, x, b[0] + x, b[2] + x)); x += adv + TRACK
    return out
LETTERS = letters()
INK_LEFT = LETTERS[0][2]
CURSOR_X = LETTERS[-1][3] + CLEAR                      # the cursor's left edge, after the y's ink
RIGHT = CURSOR_X + CURSOR_W

def cursor(x, colour, opacity=1):
    op = "" if opacity >= 1 else f' opacity="{opacity:.3f}"'
    return (f'<rect x="{x:.1f}" y="{-OVERSHOOT:.1f}" width="{CURSOR_W}" height="{ASC + 2 * OVERSHOOT:.1f}" '
            f'rx="{CURSOR_W / 2:.1f}" fill="{colour}"{op}/>')

def word_group(ink, cur):
    """The wordmark in font units, flipped to SVG's y-down: baseline at y = 0."""
    body = "".join(f'<g fill="{ink}">{glyph(n, x)[0]}</g>' for n, x, _, _ in LETTERS) + cursor(CURSOR_X, cur)
    return f'<g transform="scale(1,-1)">{body}</g>'

def wordmark_svg(ink, cur):
    top = -ASC - OVERSHOOT; h = ASC + OVERSHOOT - DESC
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{INK_LEFT:.0f} {top:.0f} {RIGHT - INK_LEFT:.0f} {h:.0f}">'
            f'{word_group(ink, cur)}</svg>\n')

# lockup geometry, shared with the TV banner and the animation
KEYS_PATH, LIT_PATH = logo.full()
SYM_H = ASC * 1.25; SYM_SCALE = SYM_H / 44.0           # the symbol's content is 47.5 x 44 on the 108 grid
SYM_W = 47.5 * SYM_SCALE; KEY = 13 * SYM_SCALE; SYM_GAP = (13 + 4.25) * SYM_SCALE
SYM_X = INK_LEFT - SYM_W - SYM_GAP; SYM_Y = -ASC / 2 - SYM_H / 2
BOX = (SYM_X, min(SYM_Y, -ASC - OVERSHOOT), RIGHT, -DESC)      # the lockup's ink: left, top, right, bottom

def symbol_group(keys, lit):
    return (f'<g transform="translate({SYM_X:.1f},{SYM_Y:.1f}) scale({SYM_SCALE:.4f}) translate(-30.25,-31.5)">'
            f'<path fill="{keys}" d="{KEYS_PATH}"/><path fill="{lit}" d="{LIT_PATH}"/></g>')

def lockup_svg(dark=True, ground=None):
    """Symbol + name with one key of clear space all round; ground None = transparent."""
    ink = PAPER if dark else INK; cur = TEAL if dark else TEAL_DEEP
    l, t, r, b = BOX; l -= KEY; t -= KEY; r += KEY; b += KEY
    bg = f'<rect x="{l:.0f}" y="{t:.0f}" width="{r - l:.0f}" height="{b - t:.0f}" fill="{ground}"/>' if ground else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{l:.0f} {t:.0f} {r - l:.0f} {b - t:.0f}">'
            f'{bg}{symbol_group(ink, cur)}{word_group(ink, cur)}</svg>\n')
