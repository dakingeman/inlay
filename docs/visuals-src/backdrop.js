// Inlay backdrop system. One rule set for every image (README, social preview, Reddit), so the set looks like one family
// without any two images being copies of each other. The written rules are in VISUAL_SYSTEM.md.
//
// Layers, bottom to top:
//   1. Graphite base: Ink #0B0B0E at the bottom rising to a near-Graphite top (brand palette), never flat black.
//   2. One soft neutral light from above, and one "whisper" of the highlight colour in a top corner (OKLCH, low chroma).
//   3. The inlay motif: tiles (one step lighter than the base, hairline edge) or dots, on a 46 px grid, kept one cell clear of
//      every piece of content and fading out with distance from it.
//   4. One or two lit tiles in the app's Glow highlight style (quiet), with a soft light on the tiles next to them.
//   5. Vignette and fine grain (grain only for PNG/MP4; GIFs skip it).
// What varies per image (seeded by its id, so the same image always comes out the same):
//   corner of the colour whisper, its strength and spread, tiles or dots, how far the motif reaches, how many lit tiles and where.
// What never varies: the base colours, the grid size, the tile style, the lit-tile style, the accent colour.

// The accent comes from the app (design/tokens.js, written from Design.kt): load tokens.js before this file
const INLAY = (() => { const a = TOKENS.brandAccent, n = parseInt(a.slice(1), 16);
  return { accent: a, rgb: [n >> 16 & 255, n >> 8 & 255, n & 255].join(','), P: 46, G: 8 }; })();

function inlayHash(str) { let h = 2166136261; for (const ch of String(str)) { h ^= ch.charCodeAt(0); h = Math.imul(h, 16777619); } return h >>> 0; }
function inlayRng(seed) { let s = seed || 1; return () => { s ^= s << 13; s ^= s >>> 17; s ^= s << 5; return ((s >>> 0) % 100000) / 100000; }; }

/** The background colour layers for an image. index = its place in the README, so neighbours never share a corner. */
function inlayBackground(id, index, W, H) {
  const r = inlayRng(inlayHash(id) + 7);
  const corners = [[86, -6], [12, -8], [62, -12], [30, -10]];      // top right, top left, top right of centre, top left of centre
  const [cx, cy] = corners[index % 4];
  const jx = cx + (r() - .5) * 10, a = (.62 + r() * .22).toFixed(2), sw = Math.round(W * (.62 + r() * .14)), sh = Math.round(H * (.66 + r() * .14));
  // the whisper: Inlay teal turned 14° towards blue in OKLCH (see the hero rule), low lightness and chroma
  const whisper = `radial-gradient(${sw}px ${sh}px at ${jx.toFixed(1)}% ${cy}%, oklch(0.30 0.045 224.6 / ${a}), oklch(0.30 0.045 224.6 / 0) 70%)`;
  const light = `radial-gradient(${Math.round(W * .9)}px ${Math.round(H * .78)}px at 50% -20%, rgba(255,255,255,.04), rgba(255,255,255,0) 70%)`;
  const base = 'linear-gradient(180deg,#16171C 0%,#101115 50%,#0B0B0E 100%)';
  const ox = cx > 50 ? 8 + r() * 10 : 82 + r() * 10, second = index % 2 === 1
    ? `radial-gradient(${Math.round(W * .55)}px ${Math.round(H * .6)}px at ${ox.toFixed(1)}% 108%, oklch(0.26 0.04 250 / ${(.35 + r() * .2).toFixed(2)}), oklch(0.26 0.04 250 / 0) 70%), ` : '';
  return `${second}${whisper}, ${light}, ${base}`;
}

/**
 * Draws the motif around the content already on the canvas. Call it once the content is laid out and its images have loaded.
 * opts.motif: 'tiles' | 'dots' | 'none' (default: alternates with index); opts.grain: false for GIF frames.
 */
function inlayBackdrop(canvas, id, index, opts = {}) {
  const W = canvas.clientWidth, H = canvas.clientHeight, P = INLAY.P, G = INLAY.G;
  const r = inlayRng(inlayHash(id));
  canvas.style.background = inlayBackground(id, index, W, H);
  const motif = opts.motif || (index % 3 === 2 ? 'dots' : 'tiles'); let motifUsed = motif;
  const reach = 4.5 + r() * 2.5;                                  // how many cells the motif runs before it has faded out
  const cr = canvas.getBoundingClientRect();
  // content = everything measured (.m) plus screens and pictures; one clear cell all round
  const boxes = [...canvas.querySelectorAll('.m, .screen, .pic, .shot, .cardimg, .panel')].map(e => { const b = e.getBoundingClientRect();
    return { l: b.left - cr.left, t: b.top - cr.top, r: b.right - cr.left, b: b.bottom - cr.top }; }).filter(b => b.r - b.l > 4 && b.b - b.t > 4);
  // The motif keeps clear of the content as one block: the union of everything shown, snapped out to the grid plus one cell.
  // Tiles then sit in clean bands around that block (never in the gaps between pieces) and fade with distance from it.
  const ox = Math.round((W % P) / 2), oy = Math.round((H % P) / 2);
  // the main visual: everything below the headline (for images with a heading on top), otherwise all content
  const below = boxes.filter(b => b.t >= 210), main = below.length && below.length < boxes.length ? below : boxes;
  const u = main.length ? main.reduce((a, b) => ({ l: Math.min(a.l, b.l), t: Math.min(a.t, b.t), r: Math.max(a.r, b.r), b: Math.max(a.b, b.b) })) : { l: W / 2, t: H / 2, r: W / 2, b: H / 2 };
  const c0 = Math.floor((u.l - ox) / P) - 1, c1 = Math.ceil((u.r - ox) / P), r0 = Math.floor((u.t - oy) / P) - 1, r1 = Math.ceil((u.b - oy) / P);
  // Tiles live in the two side bands, beside the content block and exactly as tall as it (like the hero), and fade towards the
  // image edges. Where a side band would be thinner than two cells (content runs nearly edge to edge), the image uses dots
  // instead, spread over the whole background and keeping clear of the block.
  const cols = Math.floor((W - ox) / P), leftRoom = c0, rightRoom = cols - (c1 + 1);
  let mode = motif; if (mode === 'tiles' && Math.min(leftRoom, rightRoom) < 3) mode = 'dots';
  const cells = [];
  if (mode !== 'none') for (let gx = ox, c = 0; gx + P - G <= W; gx += P, c++) for (let gy = oy, rr = 0; gy + P - G <= H; gy += P, rr++) {
    const inBlock = c >= c0 && c <= c1 && rr >= r0 && rr <= r1;
    if (inBlock && mode === 'tiles') continue;
    let k;
    if (mode === 'tiles') {
      if (gy < u.t - 1 || gy + P - G > u.b + 1 || (c >= c0 && c <= c1)) continue;   // side bands only, within the block's height
      if (boxes.some(b => gx + P - G > b.l - (P - G) && gx < b.r + (P - G) && gy + P - G > b.t - (P - G) && gy < b.b + (P - G))) continue;
      const d = c < c0 ? c0 - c : c - c1;                                  // cells out from the block
      k = d <= 2 ? 1 : Math.max(0, 1 - (d - 2) / (reach - 1));
    } else {
      // dots: the whole background, half a cell clear of each piece of content, a little stronger towards the edges
      const cx = gx + (P - G) / 2, cy = gy + (P - G) / 2;
      if (boxes.some(b => cx > b.l - P * .6 && cx < b.r + P * .6 && cy > b.t - P * .6 && cy < b.b + P * .6)) continue;
      const e = Math.min(1, Math.hypot((cx - W / 2) / (W / 2), (cy - H / 2) / (H / 2)));
      k = .5 + .5 * e;
    }
    if (k <= .05) continue;
    cells.push({ x: gx, y: gy, k });
  }
  motifUsed = mode;
  // lit tiles: 1 or 2, in the strong part of the motif, one per half, never level with each other, never mirrored
  const strong = cells.filter(c => (motifUsed === 'tiles' ? c.k >= .99 : c.k > .6) && c.x > P && c.x < W - 2 * P && c.y > P && c.y < H - 2 * P);
  const lit = [];
  const want = motifUsed !== 'tiles' ? 0 : (index % 3 === 0 ? 1 : 2);
  const pick = arr => arr.length ? arr[Math.floor(r() * arr.length)] : null;
  const first = pick(strong.filter(c => (c.x < W / 2) === (index % 2 === 0)));
  if (first && want) lit.push(first);
  if (want > 1 && first) {
    const second = pick(strong.filter(c => (c.x < W / 2) !== (first.x < W / 2) && Math.abs(c.y - first.y) >= 3 * P &&
      Math.abs(Math.abs(c.x - W / 2) - Math.abs(first.x - W / 2)) >= P));
    if (second) lit.push(second);
  }
  const isLit = c => lit.includes(c);
  let shapes = '', clip = '', spill = '', glow = '';
  for (const c of cells) {
    if (isLit(c)) continue;
    if (motifUsed === 'tiles') { const R = `x="${c.x + .5}" y="${c.y + .5}" width="${P - G - 1}" height="${P - G - 1}" rx="9"`;
      shapes += `<rect ${R} fill="rgba(255,255,255,${(.045 * c.k).toFixed(3)})" stroke="rgba(255,255,255,${(.13 * c.k).toFixed(3)})" stroke-width="1"/>`; clip += `<rect ${R}/>`; }
    else if (motifUsed === 'dots') shapes += `<circle cx="${c.x + (P - G) / 2}" cy="${c.y + (P - G) / 2}" r="1.9" fill="rgba(180,215,230,${(.2 * c.k).toFixed(3)})"/>`;
  }
  for (const c of lit) {
    const X = c.x + .5, Y = c.y + .5, w = P - G - 1;
    if (motifUsed === 'tiles') spill += `<circle cx="${X + w / 2}" cy="${Y + w / 2}" r="130" fill="url(#ib-sg)"/>`;
    glow += `<rect x="${X - 4}" y="${Y - 4}" width="${w + 8}" height="${w + 8}" rx="13" fill="none" stroke="rgba(${INLAY.rgb},.45)" stroke-width="2.5"/><rect x="${X}" y="${Y}" width="${w}" height="${w}" rx="9" fill="${INLAY.accent}"/>`;
  }
  const svg = `<svg class="ib" width="${W}" height="${H}" style="position:absolute;left:0;top:0;pointer-events:none"><defs>
    <radialGradient id="ib-sg"><stop offset="0" stop-color="${INLAY.accent}" stop-opacity=".11"/><stop offset=".45" stop-color="${INLAY.accent}" stop-opacity=".04"/><stop offset="1" stop-color="${INLAY.accent}" stop-opacity="0"/></radialGradient>
    <clipPath id="ib-tc">${clip}</clipPath></defs>${shapes}<g clip-path="url(#ib-tc)">${spill}</g><g opacity=".6">${glow}</g></svg>`;
  canvas.insertAdjacentHTML('afterbegin', svg);
  // finish: vignette and grain on top of everything (grain left out for GIF frames)
  canvas.insertAdjacentHTML('beforeend', `<div style="position:absolute;inset:0;pointer-events:none;background:radial-gradient(${Math.round(W * .94)}px ${Math.round(H * 1.05)}px at 50% 46%, rgba(0,0,0,0) 58%, rgba(0,0,0,.24) 100%)"></div>` +
    (opts.grain === false ? '' : `<svg width="${W}" height="${H}" style="position:absolute;inset:0;opacity:.06;mix-blend-mode:overlay;pointer-events:none"><filter id="ib-n"><feTurbulence type="fractalNoise" baseFrequency=".9" numOctaves="2" stitchTiles="stitch"/></filter><rect width="${W}" height="${H}" filter="url(#ib-n)"/></svg>`));
  return { motif: motifUsed, lit: lit.length, cells: cells.length };
}
