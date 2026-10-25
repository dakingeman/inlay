"""Inlay logo, refreshed (direction A). Geometry on Android's 108 adaptive-icon grid.
3 x 2 keys + space bar, one key lit. Rules applied (logo-design-skill testing checklist):
- content inside a 62-unit circle (Android's safe zone is 66) so no launcher mask clips it
- one key size, one gap, one corner ratio (radius = 29 % of a key, the same as the app's Soft keys)
- gaps opened from 4 to 4.25 units: light shapes on dark spread visually (irradiation)
- space bar lower than a key (9.5 vs 13) so it reads as a bar, same radius
- whole group 0.5 unit above centre (optical centre)
- small-size cut for 16-24 px: one row of keys + bar, bigger shapes, bigger gaps."""
INK, PAPER, TEAL = "#0B0B0E", "#F2F3F5", "#72D6E8"
def rr(x, y, w, h, r):
    f = lambda v: f"{v:.2f}".rstrip("0").rstrip(".")
    return (f"M{f(x+r)},{f(y)}H{f(x+w-r)}A{f(r)},{f(r)} 0 0 1 {f(x+w)},{f(y+r)}V{f(y+h-r)}A{f(r)},{f(r)} 0 0 1 {f(x+w-r)},{f(y+h)}"
            f"H{f(x+r)}A{f(r)},{f(r)} 0 0 1 {f(x)},{f(y+h-r)}V{f(y+r)}A{f(r)},{f(r)} 0 0 1 {f(x+r)},{f(y)}Z")
def full():
    k, g, r, bar_h = 13.0, 4.25, 3.8, 9.5
    w = 3*k + 2*g; h = 2*k + 2*g + bar_h
    x0, y0 = (108 - w)/2, 53.5 - h/2
    keys = [(x0 + c*(k+g), y0 + row*(k+g)) for row in range(2) for c in range(3)]
    lit = keys[4]
    others = " ".join(rr(x, y, k, k, r) for x, y in keys if (x, y) != lit)
    bar = rr(x0, y0 + 2*(k+g), w, bar_h, r)
    return others + " " + bar, rr(*lit, k, k, r)
def small():
    k, g, r, bar_h = 16.0, 5.5, 4.6, 11.0
    w = 3*k + 2*g; h = k + g + bar_h
    x0, y0 = (108 - w)/2, 53.5 - h/2
    keys = [(x0 + c*(k+g), y0) for c in range(3)]
    return rr(*keys[0], k, k, r) + " " + rr(*keys[2], k, k, r) + " " + rr(x0, y0 + k + g, w, bar_h, r), rr(*keys[1], k, k, r)
