"""
Inlay logo animation: the name typed out on the logo's own keys (chosen 29 Oct 2026).
Built from the same shapes and spacing as wordmark.py, so a still from the loop at rest is the logo.

What happens, and why
  - The cursor blinks once in an empty field, then "inlay" is typed and the name rests as inlay|.
  - Each letter has its own key, picked by keyboard_route(): the logo's 3 x 2 keys laid over a real QWERTY keyboard,
    each letter on the free key nearest its real spot; the y is fixed on the middle key, so the loop ends where it began.
  - The one teal key hops to each key as it is pressed. Key, teal, letter and cursor change on the same frame.
  - Press: down to 80 % at once, springs back over 200 ms, 1 % past full size, settles (the app's press, a touch more).
  - Letters: pushed down 3 % and back up over MOTION_PRESS (110 ms), with a pale-teal glint gone in 160 ms.
  - Rhythm (ms between letters 307 451 235 235): same-hand gaps are longer than hand changes (Ostry 1983: 192 vs
    147 ms), with a small beat at the syllable break in|lay (Pinet et al. 2016), played 1.6 x slower than real.
  - Cursor: Android's blink (530 ms on / off), lit for 400 ms after every change. Cleared right to left, then loops.
  - Nothing ever touches: checked on every frame.

Run:  python3 logo_motion.py OUTDIR      writes dark and light frame lists (JSON of SVG strings) for render.js
"""
import sys, os, json, random
from itertools import permutations
import wordmark as W

OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/logo_motion"; os.makedirs(OUT, exist_ok=True)
FPS = 50

# ---- easing (Design.kt) ----
def bez(x1, y1, x2, y2):
    def f(t):
        if t <= 0: return 0.0
        if t >= 1: return 1.0
        lo, hi = 0.0, 1.0
        for _ in range(40):
            u = (lo + hi) / 2; x = 3 * (1 - u) ** 2 * u * x1 + 3 * (1 - u) * u * u * x2 + u ** 3
            lo, hi = (u, hi) if x < t else (lo, u)
        u = (lo + hi) / 2; return 3 * (1 - u) ** 2 * u * y1 + 3 * (1 - u) * u * u * y2 + u ** 3
    return f
EASE_OUT = bez(.05, .7, .1, 1)
def back_out(p, c1=1.2): c3 = c1 + 1; return 1 + c3 * (p - 1) ** 3 + c1 * (p - 1) ** 2
def mix(a, b, t):
    a = [int(a[i:i + 2], 16) for i in (1, 3, 5)]; b = [int(b[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join(f"{round(x + (y - x) * t):02X}" for x, y in zip(a, b))

# ---- motion tokens ----
PRESS = .110                       # Design.MOTION_PRESS: the letter's travel
KEY_DOWN, KEY_BACK = .80, .200     # the key: down to 80 % at once, back over 200 ms (Design.MOTION_CONTROL)
GLINT = .160                       # the letter's pale-teal glint
BLINK_ON, BLINK_HOLD, BLINK_FADE = .530, .400, .080   # Design.MOTION_BLINK, MOTION_BLINK_HOLD

# ---- themes: dark is the brand's home; light for light pages (GitHub light, print) ----
THEMES = {"dark": dict(fg=W.PAPER, bg=W.INK, acc=W.TEAL, glint=.6),
          "light": dict(fg=W.INK, bg="#FFFFFF", acc=W.TEAL_DEEP, glint=.85)}   # dark letters need more teal to show the glint
# the README header sits on GitHub's own page colours, so the loop is drawn on those
THEMES_GITHUB = {"github-dark": dict(THEMES["dark"], bg="#0D1117"), "github-light": dict(THEMES["light"])}

# ---- the word ----
def run(text):
    out = []; x = 0.0
    for n, adv in W.shape(text):
        d, b = W.glyph(n, x); out.append((d, b[0] + x, b[2] + x)); x += adv + W.TRACK
    return out
WORD = run("inlay")
def after(i): return WORD[i][2] + W.CLEAR if i >= 0 else WORD[0][1]   # cursor x after letter i (-1: empty field)
RIGHT = after(4) + W.CURSOR_W

# ---- the symbol: 3 x 2 keys and a bar on the 108 grid (logo.py full()) ----
K, G, R, BAR = 13.0, 4.25, 3.8, 9.5
X0, Y0 = (108 - (3 * K + 2 * G)) / 2, 53.5 - (2 * K + 2 * G + BAR) / 2
KEYS = [(X0 + c * (K + G), Y0 + r * (K + G)) for r in range(2) for c in range(3)]
HOME = 4
SYM_H = W.ASC * 1.25; SC = SYM_H / 44.0; SYM_W = 47.5 * SC; GAP = (13 + 4.25) * SC; MARGIN = 13 * SC
SYM_X = WORD[0][1] - SYM_W - GAP; SYM_Y = -W.ASC / 2 - SYM_H / 2

def keyboard_route(word="inlay", home=HOME):
    """Which logo key types each letter: the 3 x 2 keys laid over QWERTY (top row = its top row, middle row = its
    home and bottom rows, with the rows' real stagger); each letter takes the free key nearest its real spot. The
    last letter is fixed on the home key. Result for inlay: top middle, middle right, top right, middle left, home."""
    rows, off = ["qwertyuiop", "asdfghjkl", "zxcvbnm"], [0, .25, .75]
    def real(ch):
        r = next(i for i, keys in enumerate(rows) if ch in keys); return ((rows[r].index(ch) + off[r] + .5) / 10, r / 2)
    def logo(k): return ((k % 3 + .5) / 3, [0, .75][k // 3])
    d = lambda a, b: (a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2
    free = [k for k in range(6) if k != home]
    return list(min(permutations(free, len(word) - 1), key=lambda p: sum(d(real(c), logo(k)) for c, k in zip(word, p)))) + [home]
ROUTE = keyboard_route()

# ---- rhythm ----
def rhythm(word="inlay", hands="RRRLR", syllable_at=1, syllable=.09, relax=1.6):
    """Gaps between letters: same hand 192 ms, hand change 147 ms (Ostry 1983), + a beat at the syllable break."""
    return [((.192 if hands[j] == hands[j + 1] else .147) + (syllable if j == syllable_at else 0)) * relax
            for j in range(len(word) - 1)]
T0 = .85                                   # one blink in the empty field first
TYPE = [T0]
for g in rhythm(): TYPE.append(TYPE[-1] + g)
REST = T0 + 1.6 + 2.2                      # inlay| resting, cursor blinking
DELS = [REST + i * .05 for i in range(5)]  # cleared right to left
TOTAL = DELS[-1] + .30

def caret(rest_for):
    if rest_for < BLINK_HOLD: return 1.0
    p = (rest_for - BLINK_HOLD) % (2 * BLINK_ON)
    if p < BLINK_ON - BLINK_FADE: return 1.0
    if p < BLINK_ON: return (BLINK_ON - p) / BLINK_FADE
    if p < 2 * BLINK_ON - BLINK_FADE: return 0.0
    return (p - 2 * BLINK_ON + BLINK_FADE) / BLINK_FADE

def key_scale(age):
    if age < 0 or age >= KEY_BACK: return 1.0
    return KEY_DOWN + (1 - KEY_DOWN) * back_out(age / KEY_BACK)

def frame(t, th, check):
    fg, bg, acc = th["fg"], th["bg"], th["acc"]; tint = mix(fg, acc, th["glint"])
    typed = sum(t >= x for x in TYPE); n = typed - sum(t >= x for x in DELS)
    # letters
    body = []
    for i in range(n):
        age = t - TYPE[i] if t < DELS[0] else 9
        dy = -W.ASC * .03 * (1 - EASE_OUT(age / PRESS)); col = mix(tint, fg, EASE_OUT(age / GLINT))
        body.append(f'<g fill="{col}" transform="translate(0 {dy:.1f})">{WORD[i][0]}</g>')
    # cursor
    changes = [x for x in TYPE + DELS if x <= t]
    cur_x = after(n - 1); cur_op = caret(t - changes[-1]) if changes else caret(t + BLINK_HOLD)
    if cur_op > .004: body.append(W.cursor(cur_x, acc, cur_op))
    if cur_op > .05:
        for i in range(n): check.append(max(WORD[i][1] - (cur_x + W.CURSOR_W), cur_x - WORD[i][2]))
    # keys: the teal key is the last one pressed (home before the first press and after the y)
    owner = ROUTE[typed - 1] if 0 < typed <= len(ROUTE) else HOME
    if typed >= len(ROUTE): owner = HOME
    parts = []
    for k, (x, y) in enumerate(KEYS):
        s = 1.0
        for j, tx in enumerate(TYPE):
            if ROUTE[j] == k and 0 <= t - tx < KEY_BACK: s = key_scale(t - tx)
        cx, cy = x + K / 2, y + K / 2
        parts.append(f'<path fill="{acc if k == owner else fg}" transform="translate({cx} {cy}) scale({s:.4f}) '
                     f'translate({-cx} {-cy})" d="{W.logo.rr(x, y, K, K, R)}"/>')
    parts.append(f'<path fill="{fg}" d="{W.logo.rr(X0, Y0 + 2 * (K + G), 3 * K + 2 * G, BAR, R)}"/>')
    sym = f'<g transform="translate({SYM_X:.1f},{SYM_Y:.1f}) scale({SC:.4f}) translate(-30.25,-31.5)">{"".join(parts)}</g>'
    left = SYM_X - MARGIN; top = SYM_Y - MARGIN; bottom = -W.DESC + MARGIN; w = RIGHT + MARGIN - left
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{left:.0f} {top:.0f} {w:.0f} {bottom - top:.0f}">'
            f'<rect x="{left:.0f}" y="{top:.0f}" width="{w:.0f}" height="{bottom - top:.0f}" fill="{bg}"/>'
            f'{sym}<g transform="scale(1,-1)">{"".join(body)}</g></svg>')

if __name__ == "__main__":
    for name, th in {**THEMES, **THEMES_GITHUB}.items():
        check = []
        frames = [frame(f / FPS, th, check) for f in range(round(TOTAL * FPS))]
        json.dump(frames, open(os.path.join(OUT, f"logo-{name}.json"), "w"))
        assert min(check) >= W.CLEAR - 1, "the cursor came closer to a letter than the rule"
        print(name, len(frames), "frames", f"{len(frames) / FPS:.2f}s", "route", ROUTE,
              "gaps ms", [round((b - a) * 1000) for a, b in zip(TYPE, TYPE[1:])], "closest", round(min(check)))
