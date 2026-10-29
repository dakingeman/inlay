"""
Wordmark animations (proposal). Every frame is built from the same glyph shapes and spacing as wordmark2.py,
so a still from any loop is the real logo. Writes one JSON list of SVG frames per option to OUT, and checks
that the cursor never touches a letter in any frame.
  A typed      the name is typed out at a human rhythm, rests as inlay|, then is deleted and typed again
  B typed+key  as A, and the lit key in the symbol dips with each keystroke
  C suggest    "in" is typed, "lay" is offered in grey (in|lay), taken, and the cursor jumps to the end (inlay|)
  D choices    as C, but the keyboard offers "put" and "k" first, then "lay"
Run:  python3 anim.py OUTDIR
"""
import sys, json, os, math
import wordmark2 as W

OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/anim"
os.makedirs(OUT, exist_ok=True)
FPS = 30
INK, PAPER, TEAL, GREY = W.INK, W.PAPER, W.TEAL, W.GREY_ON_DARK

def run(text, x0=0.0):
    """Glyphs of text from pen position x0: (path d, ink left, ink right) per glyph."""
    out = []; x = x0
    for n, adv in W.shape(text):
        d, b = W.glyph(n, x)
        out.append((d, b[0] + x, b[2] + x)); x += adv + W.TRACK
    return out

word = run("inlay")
INK_L = word[0][1]
SHIFT = W.CURSOR_ROOM                                   # room opened between n and l in the typing form
def after(i): return word[i][2] + W.CLEAR                # cursor x just after glyph i
START = INK_L                                            # cursor x before anything is typed
# suggestions start where the l starts in the typing form (its ink left), so the gap after the cursor is the rule
L_TYPING = word[2][1] + SHIFT
def suggestion(text):
    g = run(text); dx = L_TYPING - g[0][1]
    return [(d.replace('translate(', f'translate({dx:.2f},0) translate(', 1), l + dx, r + dx) for d, l, r in g]

# symbol geometry, as in the lockup
sym_h = W.ASC * 1.25; sc = sym_h / 44.0
sym_w = 47.5 * sc; key = 13 * sc; gap = (13 + 4.25) * sc
SYM_X = INK_L - sym_w - gap; SYM_Y = -W.ASC / 2 - sym_h / 2
KEYS, LIT = W.logo.full()

def ease(t): t = min(max(t, 0), 1); return 4 * t ** 3 if t < .5 else 1 - (-2 * t + 2) ** 3 / 2
def mix(a, b, t):
    a = [int(a[i:i + 2], 16) for i in (1, 3, 5)]; b = [int(b[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join(f"{round(x + (y - x) * t):02X}" for x, y in zip(a, b))
BLINK = 1.06                                             # one on/off cycle, as Android's text cursor
def blink(t):
    """Caret opacity t seconds after it came to rest: solid half the cycle, soft 80 ms fades."""
    if t < 0: return 1
    p = (t % BLINK) / BLINK
    if p < .5: return 1
    if p < .575: return 1 - (p - .5) / .075
    if p < .925: return 0
    return (p - .925) / .075

def frame(glyphs, cur_x, cur_op, press=1.0, right=None):
    """glyphs: list of (d, colour, opacity). cur_x: cursor's left edge. press: lit key scale."""
    body = []
    for d, col, op in glyphs:
        if op <= 0.004: continue
        o = "" if op >= 1 else f' opacity="{op:.3f}"'
        body.append(f'<g fill="{col}"{o}>{d}</g>')
    if cur_op > 0.004: body.append(W.cursor(cur_x, TEAL, cur_op))
    cx, cy = 54, 55.25                                   # the lit key's centre in the symbol's grid
    lit = f'<path fill="{TEAL}" transform="translate({cx} {cy}) scale({press:.4f}) translate({-cx} {-cy})" d="{LIT}"/>'
    sym = (f'<g transform="translate({SYM_X:.1f},{SYM_Y:.1f}) scale({sc:.4f}) translate(-30.25,-31.5)">'
           f'<path fill="{PAPER}" d="{KEYS}"/>{lit}</g>')
    left = SYM_X - key; top = SYM_Y - key; bottom = -W.DESC + key; w = right + key - left
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{left:.0f} {top:.0f} {w:.0f} {bottom - top:.0f}">'
            f'<rect x="{left:.0f}" y="{top:.0f}" width="{w:.0f}" height="{bottom - top:.0f}" fill="{INK}"/>'
            f'{sym}<g transform="scale(1,-1)">{"".join(body)}</g></svg>')

RIGHT_FINAL = after(4) + W.CURSOR_W
worst = [1e9]
def check(vis, cur_x, cur_op):
    """vis: list of (ink left, ink right) of visible glyphs. Records the closest cursor-to-letter gap."""
    if cur_op <= 0.05: return
    for l, r in vis:
        g = max(l - (cur_x + W.CURSOR_W), cur_x - r)
        worst[0] = min(worst[0], g)

# ---------- A / B: typed out ----------
GAPS = [0.17, 0.15, 0.19, 0.14]                          # time between keystrokes: uneven, like a person
def typed(press_key):
    t0 = 1.06                                            # one blink at the empty start
    times = [t0]; [times.append(times[-1] + g) for g in GAPS]
    done = times[-1]; rest_until = done + 0.3 + 2 * BLINK   # settle, then two blinks on inlay|
    dels = [rest_until + i * 0.05 for i in range(5)]     # delete right to left, quick
    total = dels[-1] + 0.25
    frames = []
    for f in range(round(total * FPS)):
        t = f / FPS
        n = sum(1 for x in times if t >= x) - sum(1 for x in dels if t >= x)
        glyphs = []; vis = []
        for i in range(n):
            born = times[i]; op = 1 if t >= dels[0] else min(1, (t - born) / 0.07)
            glyphs.append((word[i][0], PAPER, op)); vis.append(word[i][1:])
        cur_x = after(n - 1) if n else START
        if t < t0: op = blink(t)
        elif t < done + 0.3: op = 1                     # solid while typing
        elif t < dels[0]: op = blink(t - done - 0.3)
        else: op = 1
        press = 1.0
        if press_key:
            last = max([x for x in times + dels if x <= t], default=-9)
            k = (t - last)
            press = 1 - 0.1 * (k / 0.04 if k < 0.04 else max(0, 1 - (k - 0.04) / 0.12))
        check(vis, cur_x, op)
        frames.append(frame(glyphs, cur_x, op, press, RIGHT_FINAL))
    return frames

# ---------- C / D: suggestion taken ----------
def suggest(offers):
    """offers: list of (text, seconds shown); the last one is 'lay' and is taken."""
    t0 = 0.8; ti = [t0, t0 + 0.17]; typed_at = ti[1]
    shows = []; t = typed_at + 0.25
    for text, dur in offers: shows.append((t, t + dur, text)); t += dur
    acc0 = t; acc1 = acc0 + 0.45
    rest = acc1 + 0.1 + BLINK * 1.4
    dels = [rest + i * 0.05 for i in range(5)]
    total = dels[-1] + 0.25
    runs = {text: suggestion(text) for text, _ in offers}
    right = max([RIGHT_FINAL] + [g[-1][2] for g in runs.values()])
    frames = []
    for f in range(round(total * FPS)):
        t = f / FPS; glyphs = []; vis = []
        typed = sum(1 for x in ti if t >= x)
        if t < acc0:
            for i in range(typed):
                glyphs.append((word[i][0], PAPER, min(1, (t - ti[i]) / 0.07))); vis.append(word[i][1:])
            for j, (a, b, text) in enumerate(shows):
                if not (a <= t < b): continue
                op = min(1, (t - a) / 0.15)               # each offer fades in over 150 ms
                if j < len(shows) - 1: op = min(op, (b - t) / 0.1)   # and out over 100 ms before the next
                for d, l, r in runs[text]: glyphs.append((d, GREY, op)); vis.append((l, r))
            cur_x = after(typed - 1) if typed else START
            op = blink(t) if t < t0 else (1 if t < typed_at + 0.25 else blink(t - typed_at - 0.25))
        elif t < acc1 or t < dels[0]:
            k = ease((t - acc0) / 0.45)
            for i in range(5):
                dx = -SHIFT * (1 - k) * -1 if i >= 2 else 0   # l, a, y slide left by SHIFT as k goes 0 -> 1
                d = word[i][0]
                if i >= 2: d = d.replace('translate(', f'translate({SHIFT * (1 - k):.2f},0) translate(', 1)
                glyphs.append((d, PAPER if i < 2 else mix(GREY, PAPER, k), 1))
                vis.append((word[i][1] + (SHIFT * (1 - k) if i >= 2 else 0), word[i][2] + (SHIFT * (1 - k) if i >= 2 else 0)))
            if t < acc1:                                 # the caret fades, jumps, and comes back: it never slides over letters
                cur_x = after(1) if k < .5 else after(4)
                op = 1 - k / .25 if k < .25 else ((k - .75) / .25 if k > .75 else 0)
            else:
                cur_x = after(4); op = blink(t - acc1 - 0.1) if t > acc1 + 0.1 else 1
        else:
            n = 5 - sum(1 for x in dels if t >= x)
            for i in range(n): glyphs.append((word[i][0], PAPER, 1)); vis.append(word[i][1:])
            cur_x = after(n - 1) if n else START; op = 1
        check(vis, cur_x, op)
        frames.append(frame(glyphs, cur_x, op, 1.0, right))
    return frames

opts = {
    "A-typed": typed(False),
    "B-typed-key": typed(True),
    "C-suggest": suggest([("lay", 1.7)]),
    "D-choices": suggest([("put", 0.6), ("k", 0.6), ("lay", 1.2)]),
}
for name, fr in opts.items():
    json.dump(fr, open(os.path.join(OUT, name + ".json"), "w"))
    print(name, len(fr), "frames", f"{len(fr) / FPS:.2f}s")
print("closest the cursor ever gets to a letter:", round(worst[0]), "units (the rule at rest is", W.CLEAR, ")")
