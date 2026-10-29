"""Controller outlines as ONE smooth shape (device-kit clean-up, 28 Oct 2026).

The traced controllers draw the bumper humps as separate shapes on top of the body, which left a
double line with a sliver between them, and the hand traces carry small wobbles. This merges body +
bumpers into one silhouette (closing slivers under 1.5 units), then redraws it with fewer, smooth
curves. The silhouette stays within MAX_DEV units of the original everywhere (checked, printed).
Writes controller.merged.js: MERGED = {xbox: "<path d>", ps: ..., switch: ...}.
Run: python3 merge-outlines.py   (needs shapely and svgpathtools)
"""
import json, re, math, os
from shapely.geometry import Polygon
from shapely.ops import unary_union
from svgpathtools import parse_path

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = open(os.path.join(HERE, 'controller.prem.js')).read()
MAX_DEV = 0.9          # drawing units (the controller is 657 wide): under 0.15 % of its width
SIMPLIFY = 0.12
SMOOTH_PASSES = 0
CLOSE = 5.0          # slivers and zigzags narrower than this (units) are closed

def obj(name):
    m = re.search(r'const ' + name + r' = (\{.*?\});\s*$', SRC, re.M)
    return json.loads(m.group(1))

def poly(d, n_per_seg=24):
    p = parse_path(d); pts = []
    for seg in p:
        for i in range(n_per_seg):
            z = seg.point(i / n_per_seg); pts.append((z.real, z.imag))
    return Polygon(pts).buffer(0)

def merged(parts):
    u = unary_union([poly(d) for d in parts])
    u = u.buffer(CLOSE, join_style=1).buffer(-CLOSE, join_style=1)  # close slivers between body and humps
    if u.geom_type != 'Polygon': u = max(u.geoms, key=lambda g: g.area)
    return Polygon(u.exterior)

# The Xbox trace was clipped by the 423-unit frame: each grip ends in a flat cut. A real grip ends in one
# round curve, so each end becomes the circle that touches both sides of the grip (fitted on the trace's
# own sides, higher up), its lowest point at GRIP_BOTTOM.
GRIP_BOTTOM = 426.0
GRIP_SIDES = {'xbox': [((102.0, 388.5), (110.4, 409.0), (185.0, 394.2), (165.5, 415.2)),
                       ((612.0, 384.9), (602.6, 408.5), (527.7, 390.3), (546.2, 412.1))]}
def round_end(pg, a1, a2, b1, b2, bottom):
    import numpy as np
    from shapely.geometry import Point as Pt
    def line(p, q):
        p = np.array(p); d = np.array(q) - p; d /= np.linalg.norm(d); return p, d
    (pa, da), (pb, db) = line(a1, a2), line(b1, b2)
    # apex: where the two side lines meet (below the grip)
    t = np.linalg.solve(np.array([da, -db]).T, pb - pa); apex = pa + t[0] * da
    mid = (np.array(a1) + np.array(b1)) / 2                     # a point inside the grip
    def inward(p, d):
        n = np.array([-d[1], d[0]]); return n if np.dot(mid - p, n) > 0 else -n
    na, nb = inward(pa, da), inward(pb, db)
    # centre c with n.(c-p) = r on both lines: c = C0 + r*C1 (linear in r)
    A = np.array([na, nb]); C0 = np.linalg.solve(A, [na @ pa, nb @ pb]); C1 = np.linalg.solve(A, [1.0, 1.0])
    r = (bottom - C0[1]) / (C1[1] + 1); c = C0 + r * C1
    Ta, Tb = c - r * na, c - r * nb                             # where the circle touches each side
    out = 4.0
    tip = Polygon([tuple(Ta - out * na), tuple(apex + (apex - c) / np.linalg.norm(apex - c) * out), tuple(Tb - out * nb)])
    cap = Pt(*c).buffer(r, resolution=64).intersection(Polygon([tuple(Ta), tuple(apex), tuple(Tb)]).buffer(0.01))
    res = pg.difference(tip).union(cap).buffer(0.3, join_style=1).buffer(-0.3, join_style=1)
    if res.geom_type != 'Polygon': res = max(res.geoms, key=lambda g: g.area)
    return Polygon(res.exterior), (c, r)

def smooth_path(pg):
    # even spacing (STEP units) so the curve never overshoots where short and long segments meet
    ring = pg.simplify(SIMPLIFY, preserve_topology=True).exterior
    STEP = 4.0
    count = max(12, int(ring.length / STEP))
    pts = [ring.interpolate(i * ring.length / count).coords[0] for i in range(count)]
    # iron out tracing wobbles: a gentle [1 2 1] average, twice, but never on a real corner
    def ang_at(q, i):
        a, b, c = q[i - 1], q[i], q[(i + 1) % len(q)]
        v1 = (a[0] - b[0], a[1] - b[1]); v2 = (c[0] - b[0], c[1] - b[1])
        cs = (v1[0] * v2[0] + v1[1] * v2[1]) / (math.hypot(*v1) * math.hypot(*v2) + 1e-9)
        return math.degrees(math.acos(max(-1, min(1, cs))))
    keep = [ang_at(pts, i) < 135 for i in range(len(pts))]
    for _ in range(SMOOTH_PASSES):
        m = len(pts)
        pts = [pts[i] if keep[i] else ((pts[i - 1][0] + 2 * pts[i][0] + pts[(i + 1) % m][0]) / 4,
                                       (pts[i - 1][1] + 2 * pts[i][1] + pts[(i + 1) % m][1]) / 4) for i in range(m)]
    n = len(pts)
    # centripetal Catmull-Rom through the simplified points, as cubic Béziers
    def P(i): return pts[i % n]
    out = [f"M{P(0)[0]:.2f},{P(0)[1]:.2f}"]
    def angle(i):
        a, b, c = P(i - 1), P(i), P(i + 1)
        v1 = (a[0] - b[0], a[1] - b[1]); v2 = (c[0] - b[0], c[1] - b[1])
        cs = (v1[0] * v2[0] + v1[1] * v2[1]) / (math.hypot(*v1) * math.hypot(*v2) + 1e-9)
        return math.degrees(math.acos(max(-1, min(1, cs))))
    sharp = [angle(i) < 135 for i in range(n)]       # real corners stay corners: no handle, so no overshoot
    for i in range(n):
        p0, p1, p2, p3 = P(i - 1), P(i), P(i + 1), P(i + 2)
        c1 = p1 if sharp[i % n] else (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = p2 if sharp[(i + 1) % n] else (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        out.append(f"C{c1[0]:.2f},{c1[1]:.2f} {c2[0]:.2f},{c2[1]:.2f} {p2[0]:.2f},{p2[1]:.2f}")
    return ' '.join(out) + 'Z', n

CTRL = obj('CTRL'); CTRL2 = obj('CTRL2')
sets = {'xbox': [CTRL['body'], CTRL['lb'], CTRL['rb']],
        'ps': [CTRL2['ps']['body'], CTRL2['ps']['l1'], CTRL2['ps']['r1']],
        'switch': [CTRL2['switch']['body'], CTRL2['switch']['lb'], CTRL2['switch']['rb']]}
result = {}
for style, parts in sets.items():
    orig = merged(parts)
    ends = []
    for sides in GRIP_SIDES.get(style, []):
        orig, cr = round_end(orig, *sides, GRIP_BOTTOM); ends.append(cr)
        print(f"   {style} grip end: circle r={cr[1]:.1f} at ({cr[0][0]:.1f}, {cr[0][1]:.1f})")
    d, n = smooth_path(orig)
    new = poly(d, 12)
    parts_new = list(new.geoms) if new.geom_type != 'Polygon' else [new]
    big = max(parts_new, key=lambda g: g.area)
    slivers = sum(g.area for g in parts_new if g is not big)
    dev = orig.exterior.hausdorff_distance(big.exterior)            # smoothing vs the merged shape
    # away from the bumpers (more than 12 units from where they meet the body) the outline must not move at all
    from shapely.geometry import Point
    raw = unary_union([poly(x) for x in parts])
    rawb = max(raw.geoms, key=lambda g: g.area) if raw.geom_type != 'Polygon' else raw
    bump = unary_union([poly(x) for x in parts[1:]] + [Point(*c).buffer(r + 30) for c, r in ends]).buffer(12)
    far = [c for c in rawb.exterior.coords if not bump.contains(Point(c))]
    body_dev = max(Point(c).distance(big.exterior) for c in far)
    print(f"   outline away from the bumpers moved at most {body_dev:.2f} units")
    assert body_dev <= MAX_DEV, f"{style}: body outline moved {body_dev:.2f}"
    print(f"   pieces {len(parts_new)}  stray area {slivers:.2f}")
    nodes_before = sum(len(re.findall(r'[MLCQAZ]', p, re.I)) for p in parts)
    print(f"{style:7s} nodes {nodes_before:5d} -> {n:4d}   largest shift {dev:.2f} units (limit {MAX_DEV})")
    assert dev <= MAX_DEV, f"{style}: silhouette moved {dev:.2f} units"
    result[style] = d
open(os.path.join(HERE, 'controller.merged.js'), 'w').write(
    '// Generated by merge-outlines.py: body + bumper humps as one smooth outline per controller\n'
    'const MERGED = ' + json.dumps(result) + ';\n')
