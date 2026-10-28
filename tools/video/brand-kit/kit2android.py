"""Turns the device kit's drawings into one SVG per device plus, for each button, the CSS that lights it.
Base drawing = nothing pressed. For every button, the lit drawing is compared element by element:
changed parts get a class and a CSS rule; parts that only exist when lit (the focus ring and its mask,
the D-pad's filled arm) are added hidden and switched on by CSS. The accent colour is a placeholder."""
import json, re, sys, copy, difflib, os
import xml.etree.ElementTree as ET
NS = 'http://www.w3.org/2000/svg'
ET.register_namespace('', NS)
ACC = '#72D6E8'
src, outdir = sys.argv[1], sys.argv[2]
os.makedirs(outdir, exist_ok=True)
data = json.load(open(src))
GEOM = ['d', 'x', 'y', 'width', 'height', 'cx', 'cy', 'r', 'rx', 'ry', 'x1', 'y1', 'x2', 'y2', 'points', 'transform']
STYLE = ['fill', 'stroke', 'fill-opacity', 'stroke-opacity', 'stroke-width', 'opacity']

def parse(s):
    return ET.fromstring(s.replace('<svg ', f'<svg xmlns="{NS}" ', 1))
def tag(e): return e.tag.split('}')[1]
def sig(e):
    if tag(e) == 'mask': return 'mask'
    if e.get('mask'): return 'ring:' + tag(e)
    return tag(e) + '|' + '|'.join(f"{k}={e.get(k)}" for k in GEOM if e.get(k) is not None) + '|' + (e.text or '').strip()

def clean(root):
    """What the app's SVG renderer can't do: filters (unused), dominant-baseline (shift the text instead)."""
    for parent in list(root.iter()):
        for c in list(parent):
            if tag(c) == 'filter': parent.remove(c)
    for e in root.iter():
        e.attrib.pop('filter', None)
        if e.get('dominant-baseline') == 'central':
            del e.attrib['dominant-baseline']
            fs = float(e.get('font-size', '14'))
            e.set('dy', f"{fs * 0.35:.2f}")
        if e.get('font-family') in ('R', 'I'): e.set('font-family', 'sans-serif')
        e.attrib.pop('letter-spacing', None)

defs_root = parse(data['defs'])
shared_defs = [c for c in defs_root.iter() if tag(c) == 'defs'][0]
summary = {}
for name, dev in data['devices'].items():
    base = parse(dev['base'])
    base_kids = list(base)
    css = {}
    inserts = []          # (base index, element)
    n = 0
    for key, lit_s in dev['lit'].items():
        lit = parse(lit_s)
        lit_kids = list(lit)
        sm = difflib.SequenceMatcher(a=[sig(e) for e in base_kids], b=[sig(e) for e in lit_kids], autojunk=False)
        rules = []
        idmap = {}           # lit id -> the same element's id in the base drawing (clip paths are numbered per drawing)
        for op, i1, i2, j1, j2 in sm.get_opcodes():
            if op == 'equal':
                for bi, li in zip(range(i1, i2), range(j1, j2)):
                    for be, le in zip(base_kids[bi].iter(), lit_kids[li].iter()):
                        if be.get('id') and le.get('id'): idmap[le.get('id')] = be.get('id')
        for op, i1, i2, j1, j2 in sm.get_opcodes():
            if op == 'equal':
                for bi, li in zip(range(i1, i2), range(j1, j2)):
                    b, l = base_kids[bi], lit_kids[li]
                    diff = {k: l.get(k) for k in STYLE if b.get(k) != l.get(k)}
                    # an attribute the lit part drops goes back to its default
                    for k in STYLE:
                        if k in diff and diff[k] is None: diff[k] = {'fill-opacity': '1', 'stroke-opacity': '1', 'opacity': '1'}.get(k, 'none')
                    if diff:
                        cls = b.get('class') or f"p{len(css)}x{n}"
                        n += 1
                        b.set('class', cls)
                        rules.append('.' + cls.split()[0] + '{' + ';'.join(f"{k}:{v}" for k, v in diff.items()) + '}')
            elif op == 'insert':
                for li in range(j1, j2):
                    el = copy.deepcopy(lit_kids[li])
                    # unique ids per button, so rings of different buttons never clash
                    for e in el.iter():
                        if e.get('id'): e.set('id', f"{key}_{e.get('id')}")
                    if el.get('mask'): el.set('mask', re.sub(r'url\(#', f'url(#{key}_', el.get('mask')))
                    for e in el.iter():
                        cp = e.get('clip-path')
                        if cp:
                            ref = cp[5:-1]
                            e.set('clip-path', f"url(#{idmap.get(ref, ref)})")
                    if tag(el) != 'mask':
                        cls = f"h{key}{n}"; n += 1
                        el.set('class', cls)
                        el.set('display', 'none')
                        extra = ''.join(f";{k}:ACC" for k in ('fill', 'stroke') if (el.get(k) or '').lower() == ACC.lower())
                        rules.append('.' + cls + '{display:inline' + extra + '}')
                    inserts.append((i1, len(inserts), el))
            else:
                print('  !', name, key, op, i2 - i1, j2 - j1, file=sys.stderr)
        css[key] = '\n'.join(rules).replace(ACC, 'ACC').replace(ACC.lower(), 'ACC')
    # put the hidden lit-only parts in place (later positions first so indexes stay valid)
    # Later positions first so indexes stay valid; at the same position, last one first, so they end up in their original order
    for idx, _, el in sorted(inserts, key=lambda t: (-t[0], -t[1])):
        base.insert(idx, el)
    base.insert(0, copy.deepcopy(shared_defs))
    clean(base)
    svg = ET.tostring(base, encoding='unicode')
    svg = svg.replace(ACC, '#72D6E8')
    open(os.path.join(outdir, f"{name}.svg"), 'w').write(svg)
    json.dump(css, open(os.path.join(outdir, f"{name}.json"), 'w'))
    summary[name] = (len(svg) // 1024, {k: v.count('{') for k, v in css.items()})
for k, v in summary.items(): print(k, v[0], 'KB', v[1])
