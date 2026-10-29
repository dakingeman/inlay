"""
Wordmark animation, round 2 (proposal): the name typed out (option A), refined for a tactile feel, plus the
symbol's keys taking part. Built from the same shapes and spacing as wordmark2.py, and timed with the app's own
motion tokens (Design.kt), so the logo moves the way the keyboard does:
  MOTION_PRESS 110 ms   key squeeze: drops to 92 % at once, springs back on EASE_OUT   (KeyGridView.pulse)
  MOTION_FOCUS 70 ms    the highlight gliding between keys, 75 % of the way on the first frame
  MOTION_FLASH 260 ms   something lighting for a moment
  MOTION_BLINK 530 ms   the cursor on / off, lit for MOTION_BLINK_HOLD 400 ms after every change
Every loop starts and ends on the exact logo; the cursor never touches a letter (checked on every frame).
Letter styles:  snap | press | travel | ink        Key options:  keys (each letter its own key) | glide (the highlight moves)
Run:  python3 anim2.py OUTDIR
"""
import sys, json, os
import wordmark2 as W

OUT = sys.argv[1] if len(sys.argv) > 1 else "/tmp/anim2"; os.makedirs(OUT, exist_ok=True)
FPS = 50
INK, PAPER, TEAL = W.INK, W.PAPER, W.TEAL
PRESS, FOCUS, FLASH, BLINK_ON, BLINK_HOLD = .110, .070, .260, .530, .400

def bez(x1, y1, x2, y2):
    """CSS/Android cubic bezier easing."""
    def f(t):
        if t <= 0: return 0.0
        if t >= 1: return 1.0
        lo, hi = 0.0, 1.0
        for _ in range(40):
            u = (lo + hi) / 2; x = 3 * (1 - u) ** 2 * u * x1 + 3 * (1 - u) * u * u * x2 + u ** 3
            lo, hi = (u, hi) if x < t else (lo, u)
        u = (lo + hi) / 2; return 3 * (1 - u) ** 2 * u * y1 + 3 * (1 - u) * u * u * y2 + u ** 3
    return f
EASE_OUT = bez(.05, .7, .1, 1); EASE_STANDARD = bez(.2, 0, 0, 1); EASE_IN = bez(.3, 0, .8, .15)
SHADE = "#6E717A"                              # the brand's grey: a key in shadow while it is down
LOOK_MS = .06 + .26

def mix(a, b, t):
    a = [int(a[i:i + 2], 16) for i in (1, 3, 5)]; b = [int(b[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join(f"{round(x + (y - x) * t):02X}" for x, y in zip(a, b))

TINT = mix(PAPER, TEAL, .6)                     # a lit key that is clearly weaker than the logo's own teal key
SOFT_SHADE = mix(PAPER, SHADE, .55)             # pressed in, without looking like a hole

# ---- the word ----
def run(text):
    out = []; x = 0.0
    for n, adv in W.shape(text):
        d, b = W.glyph(n, x); out.append((d, b[0] + x, b[2] + x)); x += adv + W.TRACK
    return out
word = run("inlay")
def after(i): return word[i][2] + W.CLEAR if i >= 0 else word[0][1]
RIGHT = after(4) + W.CURSOR_W

def letter(i, age, style):
    """SVG for letter i, [age] seconds after its key went down. style = "motion/colour":
    motion  snap (there at once) | press (the key's squeeze, from the baseline) | travel (pushed down 3 %, back up)
    colour  white | ink (lands teal, settles over MOTION_FLASH) | glint (pale teal, gone in 160 ms) | warm (up from grey)"""
    motion, colour = (style.split("/") + ["white"])[:2]
    d, l, r = word[i]; cx = (l + r) / 2; e = EASE_OUT(age / PRESS)
    if motion == "press": tf = f' transform="translate({cx:.1f} 0) scale({1 - 0.08 * (1 - e):.4f}) translate({-cx:.1f} 0)"'
    elif motion == "travel" and PRESS_MODE == "thock":
        down = max(0.0, (1 - key_scale(age + ACT)) / DEPTH)           # 0..1, how far the key is down right now
        tf = f' transform="translate(0 {-W.ASC * 0.035 * down:.1f})"'
    elif motion == "travel": tf = f' transform="translate(0 {-W.ASC * 0.03 * (1 - e):.1f})"'
    else: tf = ""
    col = {"white": PAPER, "ink": mix(TEAL, PAPER, EASE_OUT(age / FLASH)), "glint": mix(TINT, PAPER, EASE_OUT(age / .16)),
           "warm": mix(SHADE, PAPER, EASE_OUT(age / FLASH))}[colour]
    return f'<g fill="{col}"{tf}>{d}</g>'

# ---- the symbol: 3 x 2 keys and a bar on the 108 grid (logo.py full()) ----
K, G, R, BAR = 13.0, 4.25, 3.8, 9.5
X0, Y0 = (108 - (3 * K + 2 * G)) / 2, 53.5 - (2 * K + 2 * G + BAR) / 2
KEY = [(X0 + c * (K + G), Y0 + row * (K + G)) for row in range(2) for c in range(3)]
HOME = 4                                                              # the lit key, middle row centre
sym_h = W.ASC * 1.25; sc = sym_h / 44.0; sym_w = 47.5 * sc; gap = (13 + 4.25) * sc; margin = 13 * sc
SYM_X = word[0][1] - sym_w - gap; SYM_Y = -W.ASC / 2 - sym_h / 2

PRESS_MODE = "app"                              # app | deep | dark | downup | thock  (set per job)
# 'thock': a satisfying physical press, in real time (seconds from the finger going down)
DOWN, BOTTOM, UP = .045, .05, .13               # travel down, rest at the bottom, spring back
DEPTH = .20                                     # the key shrinks to 80 %: it reads as pushed in at logo size
ACT = .038                                      # it registers two thirds of the way down: the letter and cursor change here
LEAD = .12                                      # the highlight arrives on the key this long before the press, as in Inlay
def back_out(p, c1=1.70158):                    # spring back with one small overshoot (about 2 % of the key)
    c3 = c1 + 1; return 1 + c3 * (p - 1) ** 3 + c1 * (p - 1) ** 2
EASE_IN_KEY = None
def key_scale(age):
    if age < 0 or age >= DOWN + BOTTOM + UP: return 1.0
    if age < DOWN: return 1 - DEPTH * EASE_IN(age / DOWN)            # accelerates into the bottom: the 'thock'
    if age < DOWN + BOTTOM: return 1 - DEPTH
    return 1 - DEPTH + DEPTH * back_out((age - DOWN - BOTTOM) / UP)
def squeeze(age):
    """KeyGridView.pulse: 0.92 at once, back to 1 over MOTION_PRESS on EASE_OUT. 'deep' and 'downup' press further
    (0.84) and come back over MOTION_CONTROL 200 ms, so the travel of the key is visible at logo size."""
    if PRESS_MODE == "thock": return 1.0 if age is None else key_scale(age)
    if PRESS_MODE in ("deeper", "spring"):           # the 'before' press, a touch more: down to 80 % at once,
        if age is None or age < 0 or age >= .20: return 1.0     #   back over the same 200 ms
        p = age / .20
        return .80 + .20 * (back_out(p, 1.2) if PRESS_MODE == "spring" else EASE_OUT(p))   # spring: 1 % past, settles
    depth, dur = (.16, .20) if PRESS_MODE in ("deep", "downup") else (.08, PRESS)
    if age is None or age < 0 or age >= dur: return 1.0
    return 1 - depth + depth * EASE_OUT(age / dur)

def press_colour(age):
    """The teal key's colour [age] s after it went down."""
    deep = mix(TEAL, INK, .4); up = mix(TEAL, PAPER, .45)
    if age < 0 or PRESS_MODE in ("app", "deep", "deeper", "spring"): return TEAL
    if PRESS_MODE == "dark": return mix(deep, TEAL, EASE_OUT(min(1, age / FLASH)))        # down in the dark, lifts to teal
    if age < .07: return deep                                                             # downup: down in the dark,
    if age < .15: return mix(deep, up, EASE_OUT((age - .07) / .08))                       #   up into a glow,
    return mix(up, TEAL, EASE_IN(min(1, (age - .15) / .22)))                              #   settles to teal

NAMES = [n for n, _ in W.shape("inlay")]
def legend(i, cx, cy, colour, op):
    """Letter i drawn on a key, like the legend printed on a keycap: 62 % of the key tall (ascender to baseline)."""
    b = W.BoundsPen(W.gs); W.gs[NAMES[i]].draw(b); x0, y0, x1, y1 = b.bounds
    s = K * 0.62 / W.ASC
    pen = W.SVGPathPen(W.gs); W.gs[NAMES[i]].draw(pen)
    ty = cy + s * (y0 + y1) / 2                                # centre the letter's own ink on the key
    return (f'<path fill="{colour}" opacity="{op:.3f}" transform="translate({cx - s * (x0 + x1) / 2:.3f} {ty:.3f}) '
            f'scale({s:.5f} {-s:.5f})" d="{pen.getCommands()}"/>')

def symbol(scales, lit_at, looks=None):
    """scales: per key 0..5 (and 'lit'). lit_at: (x, y) of the teal highlight on the grid.
    looks: per key (fill colour, legend letter index or None, legend opacity)."""
    parts = []; looks = looks or {}
    for i, (x, y) in enumerate(KEY):
        s = scales.get(i, 1.0); cx, cy = x + K / 2, y + K / 2
        fill, leg, lop = looks.get(i, (PAPER, None, 0))
        tf = f'translate({cx} {cy}) scale({s:.4f}) translate({-cx} {-cy})'
        parts.append(f'<g transform="{tf}"><path fill="{fill}" d="{W.logo.rr(x, y, K, K, R)}"/>'
                     + (legend(leg, cx, cy, INK, lop) if leg is not None and lop > .004 and s > 0 else "") + '</g>')
    parts.append(f'<path fill="{PAPER}" d="{W.logo.rr(X0, Y0 + 2 * (K + G), 3 * K + 2 * G, BAR, R)}"/>')
    lx, ly = lit_at; s = scales.get("lit", 1.0); cx, cy = lx + K / 2, ly + K / 2
    parts.append(f'<path fill="{looks.get("lit", (TEAL,))[0]}" transform="translate({cx} {cy}) scale({s:.4f}) translate({-cx} {-cy})" d="{W.logo.rr(lx, ly, K, K, R)}"/>')
    return f'<g transform="translate({SYM_X:.1f},{SYM_Y:.1f}) scale({sc:.4f}) translate(-30.25,-31.5)">{"".join(parts)}</g>'

def svg(letters, cur_x, cur_op, scales, lit_at, wordmark_only=False, looks=None):
    body = "".join(letters) + (W.cursor(cur_x, TEAL, cur_op) if cur_op > .004 else "")
    left = (word[0][1] if wordmark_only else SYM_X) - margin; top = SYM_Y - margin
    bottom = -W.DESC + margin; w = RIGHT + margin - left
    sym = "" if wordmark_only else symbol(scales, lit_at, looks)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{left:.0f} {top:.0f} {w:.0f} {bottom - top:.0f}">'
            f'<rect x="{left:.0f}" y="{top:.0f}" width="{w:.0f}" height="{bottom - top:.0f}" fill="{INK}"/>'
            f'{sym}<g transform="scale(1,-1)">{body}</g></svg>')

def caret(rest_for):
    """Cursor opacity [rest_for] seconds after the last change: lit 400 ms, then 530 on / 530 off, 80 ms fades."""
    if rest_for < BLINK_HOLD: return 1.0
    p = (rest_for - BLINK_HOLD) % (2 * BLINK_ON)
    f = .08
    if p < BLINK_ON - f: return 1.0
    if p < BLINK_ON: return 1 - (p - BLINK_ON + f) / f
    if p < 2 * BLINK_ON - f: return 0.0
    return (p - 2 * BLINK_ON + f) / f

# ---- the script: when each letter's key goes down ----
GAPS = [.32, .27, .34, .26]                    # uneven, like a person typing; slow enough to follow each key
T0 = 0.85                                      # the empty field first: the cursor blinks once
TYPE = [T0]; [TYPE.append(TYPE[-1] + g) for g in GAPS]
ROUTE = [1, 3, 2, 0, 5]                         # which key types i, n, l, a, y: spread out, never the same twice
def keyboard_route(word="inlay", home=4):
    """Which logo key types each letter, from where that letter sits on a real QWERTY keyboard. The 3 x 2 keys are
    laid over the keyboard (top row = its top row, middle row = its home and bottom rows); every letter takes the
    free key closest to its real spot. The last letter is fixed on the home key, so the loop ends where it started."""
    from itertools import permutations
    rows, off = ["qwertyuiop", "asdfghjkl", "zxcvbnm"], [0, .25, .75]     # the rows' real stagger, in keys
    def real(ch):
        r = next(i for i, keys in enumerate(rows) if ch in keys)
        return ((rows[r].index(ch) + off[r] + .5) / 10, r / 2)
    def logo(k): return ((k % 3 + .5) / 3, [0, .75][k // 3])
    d = lambda a, b: (a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2
    free = [k for k in range(6) if k != home]
    best = min(permutations(free, len(word) - 1), key=lambda p: sum(d(real(c), logo(k)) for c, k in zip(word, p)))
    return list(best) + [home]
ROUTE_HOME = keyboard_route()                   # i top middle, n middle right, l top right, a middle left, y home
ENTER = TYPE[-1] + .32                         # 'keys' option: the lit key goes down last, like Enter
REST = TYPE[-1] + 2.4                          # inlay| resting with the cursor blinking
DELS = [REST + i * .05 for i in range(5)]      # then cleared, right to left, quick
TOTAL = DELS[-1] + .30

worst = [1e9]
def build(style, keys, wordmark_only=False, slow=1):
    """slow > 1: a slow-motion study of just the typing, [slow] times slower (for comparing styles)."""
    frames = []
    start, end = (TYPE[0] - .2, ENTER + .55) if slow > 1 else (0, TOTAL)
    for f in range(round((end - start) * slow * FPS)):
        t = start + f / FPS / slow
        act = ACT if PRESS_MODE == "thock" else 0
        n = sum(t >= x + act for x in TYPE) - sum(t >= x for x in DELS)
        letters = [letter(i, t - TYPE[i] - act if t < DELS[0] else 9, style) for i in range(n)]
        changes = [x for x in [x + act for x in TYPE] + DELS if x <= t]
        cur_x = after(n - 1) if n else after(-1)
        cur_op = caret(t - changes[-1]) if changes else caret(t + BLINK_HOLD)
        for i in range(n):                                          # the rule: never touch
            if cur_op > .05: worst[0] = min(worst[0], max(word[i][1] - (cur_x + W.CURSOR_W), cur_x - word[i][2]))
        looks = {}
        scales = {HOME: 0.0}; lit = KEY[HOME]                        # the key under the highlight is never drawn: it would peek out when the highlight squeezes
        if keys == "lit":                                           # option B: the lit key for every letter
            last = max([x for x in TYPE if x <= t], default=None)
            scales["lit"] = squeeze(None if last is None else t - last)
        elif keys and keys.startswith("keys"):                               # each letter its own key, then Enter
            fx = keys.split(":")[1] if ":" in keys else ""
            home_end = fx == "swaphome"; fx = "swap" if home_end else fx
            route = ROUTE_HOME if home_end else ROUTE; last_press = TYPE[-1] if home_end else ENTER
            for j, x in enumerate(TYPE):
                k = route[j]; age = t - x
                v = squeeze(age)
                if v != 1.0: scales[k] = v                               # includes the small spring overshoot
                if 0 <= age < LOOK_MS and fx:
                    # on at once (the key went down), then back over MOTION_FLASH, easing in as it leaves
                    hold = .06; fade = 0 if age < hold else EASE_IN((age - hold) / FLASH)
                    if fx == "flash": looks[k] = (mix(TEAL, PAPER, fade), None, 0)
                    elif fx == "shade": looks[k] = (mix(SHADE, PAPER, fade), None, 0)
                    elif fx == "legend":                            # the letter shows on its key while it is down
                        looks[k] = (PAPER, j, 1 - fade)
                    elif fx == "litlegend": looks[k] = (mix(TEAL, PAPER, fade), j, 1 - fade)
                    elif fx == "tint": looks[k] = (mix(TINT, PAPER, EASE_IN(min(1, age / .22))), None, 0)
                    elif fx == "softshade": looks[k] = (mix(SOFT_SHADE, PAPER, EASE_IN(min(1, max(0, age - .04) / .22))), None, 0)
                    elif fx == "glow":                              # down into shade, comes back up lit, settles white
                        if age < .07: c = SOFT_SHADE
                        elif age < .15: c = mix(SOFT_SHADE, TINT, EASE_OUT((age - .07) / .08))
                        else: c = mix(TINT, PAPER, EASE_IN(min(1, (age - .15) / .22)))
                        looks[k] = (c, None, 0)
            if fx == "swap":                                        # the one teal key hops to each press; Enter brings it home
                lead = LEAD if PRESS_MODE == "thock" else 0
                hops = [(x - lead, route[j]) for j, x in enumerate(TYPE)] + ([] if home_end else [(ENTER, HOME)])
                past = [(x, k) for x, k in hops if t >= x and t < DELS[0] + 9]
                if past and past[-1][1] != HOME or (past and t - past[-1][0] < FOCUS):
                    x, k = past[-1]; prev = past[-2][1] if len(past) > 1 else HOME
                    a = (t - x) / FOCUS; m = 1.0 if a >= 1 else .75 + .25 * EASE_STANDARD(a)   # the app's highlight jump
                    if PRESS_MODE == "spring": m = 1.0                         # lands in full on the frame the key goes down: key, teal and letter as one
                    scales[HOME] = (squeeze(t - last_press) if k == HOME else 1.0); scales["lit"] = 0.0
                    looks[prev] = (mix(TEAL, PAPER, m), None, 0); looks[k] = (mix(PAPER, press_colour(t - x), m), None, 0)
                elif home_end and past: looks["lit"] = (press_colour(t - last_press),)
            if fx in ("glow", "softshade") and 0 <= t - ENTER < .5:  # Enter on the home key, in the same language
                age = t - ENTER; deep = mix(TEAL, INK, .35)
                if fx == "glow":
                    up = mix(TEAL, PAPER, .45)
                    c = deep if age < .07 else (mix(deep, up, EASE_OUT((age - .07) / .08)) if age < .15 else mix(up, TEAL, EASE_IN(min(1, (age - .15) / .22))))
                else: c = mix(deep, TEAL, EASE_IN(min(1, max(0, age - .04) / .22)))
                looks["lit"] = (c,)
            if scales.get("lit", 1) != 0: scales["lit"] = squeeze(t - last_press)
        elif keys == "glide":                                       # the highlight travels to each key, presses it
            stops = [(x - FOCUS, ROUTE[j]) for j, x in enumerate(TYPE)] + [(TYPE[-1] + .25, HOME)]
            prev, here, since = HOME, HOME, -9
            for s_t, k in stops:
                if t >= s_t: prev, here, since = here, k, s_t
            a = since and (t - since) / FOCUS
            k_ = 1.0 if a >= 1 else .75 + .25 * EASE_STANDARD(max(a, 0))  # 75 % on the first frame, as the app
            (px, py), (hx, hy) = KEY[prev], KEY[here]
            lit = (px + (hx - px) * k_, py + (hy - py) * k_)
            last = max([x for x in TYPE if x <= t], default=None)
            scales["lit"] = squeeze(None if last is None else t - last)
            scales[HOME] = 1.0; scales[here] = 0.0                    # the home key is a plain key while the highlight is away
            if prev != here and k_ < 1: scales[prev] = 1.0
        frames.append(svg(letters, cur_x, cur_op, scales, lit, wordmark_only, looks))
    return frames

# ---- typing rhythm, from keystroke research ----
# Ostry (1983): letters typed with alternating hands come about 147 ms apart, with the same hand about 192 ms.
# Pinet et al. (2016): the gap is longer at a syllable boundary.  Within a word, typists slow around the fourth
# letter, then speed up to the end (Ostry).  On a QWERTY keyboard: i n l with the right hand, a with the left, y right.
# Real speed is too quick to follow in a logo, so every rhythm is the same pattern played at a relaxed pace (x RELAX).
import random
RELAX = 1.6
SAME, ALT = .192, .147
HANDS = "RRRLR"                                  # i n l a y
def rhythm(syllable=0.0, fourth=0.0, jitter=0.0, seed=7):
    """Gaps between i-n, n-l, l-a, a-y in seconds. syllable: extra pause at in|lay. fourth: extra before the a.
    jitter: random spread (log-normal, the usual shape of typing gaps), fixed by seed so the loop never changes."""
    rnd = random.Random(seed); gaps = []
    for j in range(4):
        g = SAME if HANDS[j] == HANDS[j + 1] else ALT
        if j == 1: g += syllable                 # n -> l crosses the syllable break: in | lay
        if j == 2: g += fourth                   # l -> a: the slow-down around the fourth letter
        if jitter: g *= rnd.lognormvariate(0, jitter)
        gaps.append(g * RELAX)
    return gaps
def set_rhythm(gaps):
    global GAPS, TYPE, ENTER, REST, DELS, TOTAL
    GAPS = gaps; TYPE = [T0]; [TYPE.append(TYPE[-1] + g) for g in GAPS]
    ENTER = TYPE[-1] + .32
    REST = T0 + 1.6 + 2.2                        # every rhythm loops in the same length, so they can be compared
    DELS = [REST + i * .05 for i in range(5)]; TOTAL = DELS[-1] + .30
RHYTHMS = {
    "even":     [.30] * 4,                                         # reference: a machine
    "measured": rhythm(),                                          # hands only
    "syllable": rhythm(syllable=.09),                              # hands + a small beat at in|lay
    "natural":  rhythm(syllable=.06, fourth=.04, jitter=.12),      # all three, plus the small randomness of a person
}
jobs = {"final": ("travel/glint", "keys:swaphome", False, "spring", RHYTHMS["syllable"]),
        "slow-final": ("travel/glint", "keys:swaphome", False, "spring", RHYTHMS["syllable"])}
for name, (s, k, wm, *pm) in jobs.items():
    PRESS_MODE = pm[0] if pm else "app"
    if len(pm) > 1: set_rhythm(pm[1]); print(name, "gaps ms", [round(x * 1000) for x in pm[1]])
    fr = build(s, k, wm, 4 if name.startswith("style") else 3 if name.startswith("slow") else 1); json.dump(fr, open(os.path.join(OUT, name + ".json"), "w")); print(name, len(fr), f"{len(fr) / FPS:.2f}s")
print("closest the cursor gets to a letter:", round(worst[0]), "units (rule:", W.CLEAR, ")")
