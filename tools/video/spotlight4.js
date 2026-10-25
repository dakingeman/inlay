// Inlay spotlight, take 4. Every frame is a pure function of time t.
// The app's screen plus a traced Xbox-style controller outline (controller.js) whose parts light up.
// Scenes change on bar lines of the music (100 bpm, bar = 2.4 s) so its rises and falls match the picture;
// the presses themselves run at a natural pace and are not snapped to beats.
const BPM = 100, BEAT = 60 / BPM, BAR = 4 * BEAT;
const ease = { out: x => 1 - Math.pow(1 - x, 3), io: x => x < .5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2 };
const cl = (x, a = 0, b = 1) => Math.max(a, Math.min(b, x)), seg = (t, a, b) => cl((t - a) / (b - a));
const ACC = '#72D6E8';
const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;');

const bars = (a, b) => [a * BAR, b * BAR];
// Teaser: the launcher scene, a quick run through the looks, then the logo. Scenes it leaves out are parked beyond the end.
const TEASER = typeof window !== 'undefined' ? !!window.TEASER : (typeof globalThis.TEASER !== 'undefined' && globalThis.TEASER);
const SC = TEASER ? { hook: bars(0, 4), looks: bars(4, 8), end: bars(8, 10), type: bars(100, 101), edit: bars(101, 102), icons: bars(102, 103), sound: bars(103, 104), lang: bars(104, 105), tv: bars(105, 106) } : { hook: bars(0, 4), type: bars(4, 11), edit: bars(11, 16), looks: bars(16, 22), icons: bars(22, 24), sound: bars(24, 27), lang: bars(27, 29), tv: bars(29, 31), end: bars(31, 34) };
const DUR = SC.end[1];
const CAP = {
  hook: TEASER ? ['A keyboard you drive with the controller', 'Free and open source for Android handhelds.'] : ['Type without touching the screen', ''],
  type: ['The D-pad moves. A types.', 'Y is space, X deletes, Start goes.'],
  edit: ['Edit text properly', 'Select, delete, undo and copy, all from the controller.'],
  looks: ['Make it look how you want', 'Themes, highlight colours, key shapes and layouts.'],
  icons: ['Icons for your controller', 'Xbox, PlayStation or Switch.'],
  sound: ['Seven sound packs', 'Soft clicks, 8-bit blips, a typewriter.'],
  lang: ['Eleven languages', 'Switch with RT + LT while you type.'],
  tv: ['Docked to a TV?', 'The keyboard floats and shows what you type, big.'],
};

// ---------- the English grid, as the app lays it out ----------
const ROWS = [[1.9, 1.2, 1.3, 3.8, .9, .9], [1,1,1,1,1,1,1,1,1,1], [1,1,1,1,1,1,1,1,1,1], [1,1,1,1,1,1,1,1,2], [2,1,4,1,2]];
const span = (r, i) => { let x = (10 - ROWS[r].reduce((a, b) => a + b, 0)) / 2; for (let k = 0; k < i; k++) x += ROWS[r][k]; return [x, x + ROWS[r][i]]; };
const centre = (r, i) => { const [a, b] = span(r, i); return (a + b) / 2; };
const keyAt = (r, c) => { for (let i = 0; i < ROWS[r].length; i++) { const [, b] = span(r, i); if (c < b - 0.001) return i; } return ROWS[r].length - 1; };
const LET = ["qwertyuiop", "asdfghjkl'", "zxcvbnm"];
const keyOf = ch => { for (let r = 0; r < 3; r++) { const i = LET[r].indexOf(ch); if (i >= 0) return [r + 1, r === 2 ? i + 1 : i]; } return null; };

// ---------- the press script ----------
const EV = [];
let cur = [2, 4], pref = null;
const STEP = 0.22, HOLD_FIRST = 0.26, HOLD_STEP = 0.12, AFTER_PRESS = 0.28;
function route(target) {
  const go = vertFirst => { let [r, i] = cur, p = pref ?? centre(r, i), out = [];
    const vert = () => { while (r !== target[0]) { const d = target[0] > r ? 1 : -1; r += d; i = keyAt(r, p); out.push({ to: [r, i], dir: d > 0 ? 'down' : 'up', p }); } };
    const hor = () => { while (i !== target[1]) { const d = target[1] > i ? 1 : -1; i += d; p = null; out.push({ to: [r, i], dir: d > 0 ? 'right' : 'left', p: null }); } };
    if (vertFirst) { vert(); hor(); } else { hor(); vert(); } return out; };
  const a = go(true), b = go(false); return b.length < a.length ? b : a;
}
/** Moves the highlight to a key. Three or more steps in one direction are a held D-pad (the app repeats). */
function moveTo(t, target) {
  const path = route(target); if (!path.length) return t;
  let i = 0;
  while (i < path.length) {
    let j = i; while (j + 1 < path.length && path[j + 1].dir === path[i].dir) j++;
    const run = path.slice(i, j + 1), hold = run.length >= 3;
    const holdEnd = t + HOLD_FIRST + (run.length - 1) * HOLD_STEP;
    run.forEach((st, k) => {
      const at = t + (hold ? (k === 0 ? 0 : HOLD_FIRST + (k - 1) * HOLD_STEP) : k * STEP);
      EV.push({ t: at, kind: 'move', to: st.to, dir: st.dir, hold: hold ? [t, holdEnd] : null, sound: 'move' });
      cur = st.to; pref = st.p ?? centre(st.to[0], st.to[1]); if (st.dir === 'left' || st.dir === 'right') pref = null;
    });
    t = hold ? holdEnd + STEP : t + run.length * STEP;
    i = j + 1;
  }
  return t;
}
const press = (t, btn, extra = {}) => { EV.push(Object.assign({ t, kind: 'press', btn }, extra)); return t; };
function typeText(t, str, field) {
  for (const ch of str) {
    if (ch === ' ') { press(t, ['Y'], { sound: 'space', edit: { field, add: ' ' } }); t += AFTER_PRESS + 0.1; continue; }
    t = moveTo(t, keyOf(ch)) + 0.06;
    press(t, ['A'], { sound: 'type', edit: { field, add: ch } });
    t += AFTER_PRESS;
  }
  return t;
}
// Scene 1: find a game in a launcher
let t = typeText(0.7, 'zelda', 'games');
press(t + 0.2, ['START'], { sound: 'confirm', label: 'Search', send: 'games' });
// Scene 2: search the web
t = typeText(SC.type[0] + 0.6, 'metroid prime', 'web');
const TYPE_END = t;
// Scene 3: edit it
let e = SC.edit[0] + 0.5;
const act = (btn, extra, gap = 1.25) => { press(e, btn, extra); e += gap; };
act(['SELECT'], { sound: 'on', label: 'Select', sel: { field: 'web', mode: 'start' } });
act(['RT', 'LB'], { sound: 'move', label: 'Select the word', sel: { field: 'web', word: 1 } });
act(['X'], { sound: 'delete', label: 'Delete', edit: { field: 'web', delSel: true } });
act(['RT', 'X'], { sound: 'off', label: 'Undo', edit: { field: 'web', undo: true } });
act(['RT', 'LB'], { sound: 'move', label: 'Jump a word back', sel: { field: 'web', jump: 1 } });
act(['SELECT'], { sound: 'on', label: 'Select', sel: { field: 'web', mode: 'start' } });
act(['RT', 'LB'], { sound: 'move', label: 'Select the word', sel: { field: 'web', word: 1 } });
e = moveTo(e, [0, 2]) + 0.1;
press(e, ['A'], { sound: 'confirm', label: 'Copy', copied: true, sel: { field: 'web', clear: true } });
const EDIT_END = e;
// Scene 7: languages
['Norsk', 'Deutsch', 'Русский'].forEach((l, i) => press(SC.lang[0] + 0.5 + i * 1.3, ['RT', 'LT'], { sound: 'on', label: 'Next language', lang: l }));
EV.sort((a, b) => a.t - b.t);

// ---------- state derived from the script ----------
function textState(field, tt) {
  let s = '', sel = null, cursor = null, saved = null, sent = null;
  for (const ev of EV) { if (ev.t > tt) break;
    if (ev.edit && ev.edit.field === field) {
      if (ev.edit.add !== undefined) { s += ev.edit.add; cursor = null; }
      if (ev.edit.delSel && sel) { saved = { s, sel }; s = s.slice(0, sel[0]) + s.slice(sel[1]); cursor = sel[0]; sel = null; }
      if (ev.edit.undo && saved) { s = saved.s; sel = null; cursor = saved.sel[1]; saved = null; }
    }
    if (ev.send === field) sent = s;
    if (ev.sel && ev.sel.field === field) {
      const c = cursor ?? s.length;
      if (ev.sel.clear) { if (sel) cursor = sel[1]; sel = null; }
      if (ev.sel.jump) { let a = c; while (a > 0 && s[a - 1] === ' ') a--; while (a > 0 && s[a - 1] !== ' ') a--; cursor = a; sel = null; }
      if (ev.sel.mode === 'start') sel = [c, c];
      if (ev.sel.word && sel) { let a = sel[0]; while (a > 0 && s[a - 1] === ' ') a--; while (a > 0 && s[a - 1] !== ' ') a--; sel = [a, sel[1]]; }
    }
  }
  return { s, sel, cursor: cursor ?? s.length, sent };
}
function focusAt(tt) { let from = [2, 4], to = [2, 4], t0 = -9; for (const ev of EV) { if (ev.t > tt) break; if (ev.kind === 'move') { from = to; to = ev.to; t0 = ev.t; } } return { from, to, p: seg(tt, t0, t0 + 0.11) }; }
function lit(tt) {
  const on = {};
  for (const ev of EV) { if (ev.t > tt + 0.01) break;
    if (ev.kind === 'move') { const [a, b] = ev.hold || [ev.t, ev.t + STEP * 0.7]; const v = tt < b ? 1 : 1 - seg(tt, b, b + 0.16); if (v > 0) on['D' + ev.dir] = Math.max(on['D' + ev.dir] || 0, v); }
    else { const holdFor = ev.btn.length > 1 ? 0.55 : 0.28; const v = tt < ev.t + holdFor ? 1 : 1 - seg(tt, ev.t + holdFor, ev.t + holdFor + 0.2); if (v > 0) ev.btn.forEach(b => on[b] = Math.max(on[b] || 0, v)); }
  }
  return on;
}
function pressAmt(tt) { for (const ev of EV) if (ev.kind === 'press' && ev.btn.includes('A') && tt >= ev.t && tt < ev.t + 0.18) return seg(tt, ev.t, ev.t + 0.18); return 0; }
function langAt(tt) { let l = 'English'; for (const ev of EV) if (ev.lang && ev.t <= tt) l = ev.lang; return l; }
function labelAt(tt) { return [...EV].reverse().find(ev => ev.label && tt >= ev.t && tt < ev.t + 1.1); }

// ---------- prompts and badges ----------
function promptHtml(tt, style, size) {
  const ev = labelAt(tt); if (!ev) return '';
  const o = Math.min(seg(tt, ev.t, ev.t + 0.12), 1 - seg(tt, ev.t + 0.85, ev.t + 1.1));
  const rise = (1 - ease.out(seg(tt, ev.t, ev.t + 0.3))) * 10;
  const bs = ev.btn.map(b => bigBadge(b, size, style)).join(`<span style="color:#9A9DA5;font-size:${size * .5}px;margin:0 8px">+</span>`);
  return `<div style="display:flex;align-items:center;justify-content:center;gap:14px;opacity:${o};transform:translateY(${rise}px)">${bs}<span style="font-size:${size * .5}px;color:#C9CCD3;margin-left:6px">${ev.label}</span></div>`;
}
function bigBadge(b, size, style) {
  if (b === 'DPAD') return `<svg width="${size}" height="${size}" viewBox="0 0 34 34"><circle cx="17" cy="17" r="16" fill="#0B0B0E" stroke="#ffffff55" stroke-width="1.5"/><path d="M14,7h6v7h7v6h-7v7h-6v-7h-7v-6h7z" fill="#F2F3F5"/></svg>`;
  const shoulder = ['RT', 'LT', 'LB', 'RB', 'LS', 'RS'].includes(b), wide = shoulder || b === 'SELECT' || b === 'START', w = wide ? size * 1.7 : size;
  let inner;
  if (shoulder) inner = `<text x="${w / 2}" y="${size / 2 + 1}" fill="#F2F3F5" font-family="I" font-weight="700" font-size="${size * .4}" text-anchor="middle" dominant-baseline="central">${shoulderLabel(b, style || 'xbox')}</text>`;
  else if (b === 'START') inner = `<path d="M${w / 2 - size * .2},${size * .36}h${size * .4}M${w / 2 - size * .2},${size * .5}h${size * .4}M${w / 2 - size * .2},${size * .64}h${size * .4}" stroke="#F2F3F5" stroke-width="${size * .07}" stroke-linecap="round"/>`;
  else if (b === 'SELECT') inner = `<rect x="${w / 2 - size * .22}" y="${size * .3}" width="${size * .28}" height="${size * .24}" rx="${size * .04}" fill="none" stroke="#F2F3F5" stroke-width="${size * .06}"/><rect x="${w / 2 - size * .04}" y="${size * .46}" width="${size * .28}" height="${size * .24}" rx="${size * .04}" fill="#0B0B0E" stroke="#F2F3F5" stroke-width="${size * .06}"/>`;
  else inner = faceGlyph(b, w / 2, size / 2 + 1, size, style || 'xbox');
  return `<svg width="${w}" height="${size}"><rect x="1" y="1" width="${w - 2}" height="${size - 2}" rx="${size / 2 - 1}" fill="#0B0B0E" stroke="#ffffff55" stroke-width="1.5"/>${inner}</svg>`;
}

// ---------- the apps on screen ----------
const COVERS = ['#1d3557', '#e63946', '#2a9d8f', '#e9c46a', '#6d597a', '#f4a261', '#264653', '#8ab17d'];
function textHtml(st, tt) {
  const caret = Math.floor(tt * 2) % 2 === 0;
  const caretHtml = `<span style="display:inline-block;width:2px;height:1.1em;vertical-align:-0.18em;background:${ACC};opacity:${caret ? 1 : 0}"></span>`;
  if (st.sel && st.sel[1] > st.sel[0]) return esc(st.s.slice(0, st.sel[0])) + `<span style="background:${ACC}66;border-radius:4px">${esc(st.s.slice(st.sel[0], st.sel[1]))}</span>` + esc(st.s.slice(st.sel[1]));
  return esc(st.s.slice(0, st.cursor)) + caretHtml + esc(st.s.slice(st.cursor));
}
/** A game launcher: search box, a shelf of covers that filters once you search. */
function launcher(tt, w, h, field) {
  const st = textState(field, tt); const found = st.sent !== null;
  const covers = found ? COVERS.slice(0, 2) : COVERS;
  return `<div style="position:absolute;inset:0;background:linear-gradient(160deg,#151a24,#0b0d12)">
    <div style="position:absolute;left:5%;top:6%;font-family:A;font-weight:700;font-size:${h * .075}px">Games</div>
    <div style="position:absolute;left:5%;top:18%;width:62%;height:11%;border-radius:${h * .03}px;background:#1b2130;border:1px solid #ffffff1a;display:flex;align-items:center;padding:0 2%;box-sizing:border-box;font-size:${h * .05}px;white-space:pre">${st.s ? textHtml(st, tt) : textHtml(st, tt) + `<span style="color:#ffffff55">Search games</span>`}</div>
    ${found ? `<div style="position:absolute;left:69%;top:20.5%;font-size:${h * .042}px;color:#9A9DA5">2 games</div>` : ''}
    <div style="position:absolute;left:5%;top:36%;display:flex;gap:1.8%;width:100%">${covers.map(c => `<div style="flex:none;width:10.5%;height:${h * .3}px;border-radius:${h * .02}px;background:${c};opacity:.9"></div>`).join('')}</div>
  </div>`;
}
/** A browser: address bar with the search text, a page of placeholder blocks behind. */
function browser(tt, w, h, field, copied) {
  const st = textState(field, tt);
  return `<div style="position:absolute;inset:0;background:#0d0f14">
    <div style="position:absolute;left:0;right:0;top:0;height:${h * .18}px;background:#171a21;border-bottom:1px solid #ffffff12"></div>
    <div style="position:absolute;left:3%;top:${h * .045}px;display:flex;gap:${w * .012}px">${['‹', '›', '↻'].map(c => `<div style="width:${h * .09}px;height:${h * .09}px;border-radius:50%;display:flex;align-items:center;justify-content:center;color:#8d919c;font-size:${h * .045}px">${c}</div>`).join('')}</div>
    <div style="position:absolute;left:17%;top:${h * .045}px;width:66%;height:${h * .09}px;border-radius:${h * .045}px;background:#0d0f14;border:1px solid #ffffff1f;display:flex;align-items:center;padding:0 2.5%;box-sizing:border-box;font-size:${h * .046}px;white-space:pre;color:#F2F3F5">${st.s ? textHtml(st, tt) : textHtml(st, tt) + `<span style="color:#ffffff55">Search or type a web address</span>`}</div>
    ${copied ? `<div style="position:absolute;left:84.5%;top:${h * .062}px;font-size:${h * .04}px;color:${ACC};opacity:${copied}">Copied</div>` : ''}
    <div style="position:absolute;left:8%;top:28%;width:52%;height:${h * .06}px;border-radius:6px;background:#1a1d25"></div>
    <div style="position:absolute;left:8%;top:40%;width:84%;height:${h * .04}px;border-radius:6px;background:#15181f"></div>
    <div style="position:absolute;left:8%;top:48%;width:70%;height:${h * .04}px;border-radius:6px;background:#15181f"></div>
    <div style="position:absolute;left:8%;top:58%;width:84%;height:${h * .3}px;border-radius:10px;background:#12151b"></div>
  </div>`;
}
const kbBox = (svg, w) => svg.replace('<svg ', `<svg style="width:${w}px;height:auto;display:block" `);
const gameBehind = (W, H) => `<defs><linearGradient id="gg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1f3f6e"/><stop offset=".55" stop-color="#a65a3b"/><stop offset="1" stop-color="#d9b25c"/></linearGradient></defs><rect width="${W}" height="${H}" fill="url(#gg)"/>` + COVERS.map((c, i) => `<rect x="${30 + i * 160}" y="30" width="135" height="420" rx="16" fill="${c}"/>`).join('');

// ---------- the looks scene: themes, then colour, shape, layout ----------
const LOOKS = [
  { name: 'Light', theme: 'Light', accent: '#4FA8FF' }, { name: 'Outline', theme: 'Outline', accent: '#FFC857' }, { name: 'Clear', theme: 'Clear', accent: ACC }, { name: 'Wallpaper', theme: 'Wallpaper', accent: '#B39DFF' },
  { name: 'Coral highlight', theme: 'OLED', accent: '#FF8A80' }, { name: 'Lavender highlight', theme: 'OLED', accent: '#B39DFF' },
  { name: 'Sharp keys', theme: 'OLED', accent: ACC, corner: 5 }, { name: 'Round keys', theme: 'OLED', accent: ACC, corner: 14 },
  { name: 'Classic layout', theme: 'OLED', accent: ACC, layout: 'classic' },
];
const LOOK_AT = (i) => SC.looks[0] + 0.6 + i * (TEASER ? 1.4 : 1.45);
const lookIdx = tt => { let i = -1; LOOKS.forEach((l, k) => { if (tt >= LOOK_AT(k)) i = k; }); return i; };
const PACKS = [['Soft', 'A gentle thud with a crisp click'], ['Tactile', 'Dry mechanical clicks'], ['Retro', 'Short 8-bit blips'], ['Typewriter', 'A metal strike, a bell on Enter'], ['Felt', 'Very soft taps for night']];
const STYLES = [['xbox', 'Xbox'], ['ps', 'PlayStation'], ['switch', 'Switch']];
const styleIdx = tt => cl(Math.floor((tt - SC.icons[0] - 0.4) / 1.45), 0, 2);
const packIdx = tt => cl(Math.floor((tt - SC.sound[0] - 0.6) / 1.3), 0, 4);

function render(tt, W, H) {
  const tall = H > W, out = [];
  const L = tall
    ? { cap: [60, 150, W - 120, 220], scr: [40, 420, 1000, 562], ctl: [170, 1060, 740, 476], prm: [40, 1620, 1000, 90] }
    : { cap: [60, 56, W - 120, 170], scr: [80, 262, 1150, 647], ctl: [1255, 300, 640, 412], prm: [1250, 760, 650, 90] };
  const box = (b, extra, inner) => `<div style="position:absolute;left:${b[0]}px;top:${b[1]}px;width:${b[2]}px;height:${b[3]}px;${extra}">${inner}</div>`;

  for (const k in CAP) { const [a, b] = SC[k]; const o = Math.min(seg(tt, a + 0.3, a + 0.8), 1 - seg(tt, b - 0.4, b - 0.1)); if (o <= 0) continue;
    const r = (1 - ease.out(seg(tt, a + 0.3, a + 0.95))) * 12;
    out.push(box(L.cap, `display:flex;flex-direction:column;justify-content:center;align-items:center;text-align:center;opacity:${o};transform:translateY(${r}px)`,
      `<div style="font-family:A;font-weight:700;font-size:${tall ? 70 : 66}px;letter-spacing:-0.5px;line-height:1.08">${CAP[k][0]}</div>` +
      (CAP[k][1] ? `<div style="font-size:${tall ? 33 : 29}px;color:#9A9DA5;margin-top:14px">${CAP[k][1]}</div>` : ''))); }

  const style = tt >= SC.icons[0] && tt < SC.icons[1] + 0.3 ? STYLES[styleIdx(tt)][0] : 'xbox';
  const mainOut = Math.min(SC.tv[0], SC.end[0]);
  const mainVis = Math.min(ease.out(seg(tt, 0.1, 0.9)), 1 - seg(tt, mainOut - 0.35, mainOut));
  if (mainVis > 0) {
    const li = tt >= SC.looks[0] && tt < SC.looks[1] + 0.3 ? lookIdx(tt) : -1;
    const look = li >= 0 ? LOOKS[li] : { theme: 'OLED', accent: ACC };
    const field = tt < SC.type[0] - 0.15 ? 'games' : 'web';
    const st = textState('web', tt);
    const copyEv = EV.find(ev => ev.copied);
    const copied = tt >= copyEv.t && tt < SC.edit[1] ? seg(tt, copyEv.t, copyEv.t + 0.2) : 0;
    const on = lit(tt), held = (on.RT || 0) > 0.5;
    const kbFor = (lk) => ({ W: 1280, S: 2, theme: lk.theme, accent: lk.accent, corner: lk.corner, layout: lk.layout, style, lang: langAt(tt), glide: focusAt(tt), press: pressAmt(tt), selecting: field === 'web' && st.sel !== null, held, shift: held ? 'once' : 'off',
      chips: [[{ b: "LB" }, { b: "RB" }, { t: "Jump word" }], [{ b: "A" }, { t: "Capital" }], [{ b: "Y" }, { t: "Accent" }], [{ b: "X" }, { t: "Undo" }]], behind: lk.theme === 'Clear' ? gameBehind : null });
    const kbIn = ease.out(seg(tt, 0.5, 1.1));
    let kbHtml = kbBox(kb(kbFor(look)), L.scr[2]);
    if (li >= 0) { const f = seg(tt, LOOK_AT(li), LOOK_AT(li) + 0.3);
      if (f < 1) { const prev = li > 0 ? LOOKS[li - 1] : { theme: 'OLED', accent: ACC }; kbHtml = `<div style="position:absolute;inset:0">${kbBox(kb(kbFor(prev)), L.scr[2])}</div><div style="position:relative;opacity:${f}">${kbHtml}</div>`; } }
    // screens: launcher, then (crossfading at the cut) the browser
    const cut = SC.type[0], fo = 1 - seg(tt, cut - 0.15, cut + 0.08), fi = seg(tt, cut + 0.12, cut + 0.35);
    const screen = tt < cut - 0.15 ? launcher(tt, L.scr[2], L.scr[3], 'games') : tt > cut + 0.35 ? browser(tt, L.scr[2], L.scr[3], 'web', copied)
      : `<div style="position:absolute;inset:0;background:#0d0f14"></div><div style="position:absolute;inset:0;opacity:${fo}">${launcher(tt, L.scr[2], L.scr[3], 'games')}</div><div style="position:absolute;inset:0;opacity:${fi}">${browser(tt, L.scr[2], L.scr[3], 'web', 0)}</div>`;
    const sh = L.scr[3];
    let overlay = '';
    if (tt >= SC.sound[0] && tt < SC.sound[1]) { const i = packIdx(tt), o = Math.min(seg(tt, SC.sound[0] + 0.2, SC.sound[0] + 0.6), 1 - seg(tt, SC.sound[1] - 0.4, SC.sound[1] - 0.1));
      overlay = card(o, sh, `<div><div style="font-size:${sh * .046}px;font-weight:500">Sound pack</div><div style="font-size:${sh * .034}px;color:#9A9DA5;margin-top:4px">${PACKS[i][1]}</div></div>
        <div style="background:#2E2E35;border-radius:30px;height:${sh * .085}px;width:${sh * .5}px;display:flex;align-items:center;justify-content:space-between;padding:0 ${sh * .035}px;box-sizing:border-box;font-size:${sh * .04}px;font-weight:500"><span style="color:${ACC}">‹</span>${PACKS[i][0]}<span style="color:${ACC}">›</span></div>`,
        PACKS.map((p, k) => k === i).map(a => `<div style="width:${a ? 24 : 8}px;height:8px;border-radius:4px;background:${a ? ACC : '#2E2E35'}"></div>`).join('')); }
    if (tt >= SC.icons[0] && tt < SC.icons[1]) { const i = styleIdx(tt), o = Math.min(seg(tt, SC.icons[0] + 0.2, SC.icons[0] + 0.6), 1 - seg(tt, SC.icons[1] - 0.4, SC.icons[1] - 0.1)), s = STYLES[i][0];
      const bsz = sh * .085;
      overlay = card(o, sh, `<div><div style="font-size:${sh * .046}px;font-weight:500">Button icons</div><div style="font-size:${sh * .034}px;color:#9A9DA5;margin-top:4px">${STYLES[i][1]}</div></div>
        <div style="display:flex;align-items:center;gap:${sh * .02}px">${['A', 'B', 'X', 'Y', 'LB', 'RT'].map(b => bigBadge(b, bsz, s)).join('')}</div>`,
        STYLES.map((p, k) => k === i).map(a => `<div style="width:${a ? 24 : 8}px;height:8px;border-radius:4px;background:${a ? ACC : '#2E2E35'}"></div>`).join('')); }
    const pillTxt = li >= 0 && tt < SC.looks[1] ? LOOKS[li].name : '';
    const pill = pillTxt ? `<div style="position:absolute;right:4%;top:6%;font-size:${sh * .04}px;font-weight:600;padding:8px 20px;border-radius:30px;background:#1C1C21;color:${ACC};border:1px solid #ffffff14">${pillTxt}</div>` : '';
    const rise = (1 - ease.out(seg(tt, 0.1, 0.9))) * 40;
    out.push(box(L.scr, `opacity:${mainVis};transform:translateY(${rise}px);border-radius:22px;overflow:hidden;background:#000;box-shadow:0 0 0 2px #23252c,0 40px 90px rgba(0,0,0,.55)`,
      screen + `<div style="position:absolute;left:0;right:0;bottom:0;transform:translateY(${(1 - kbIn) * 100}%)">${kbHtml}</div>` + overlay + pill));
    const slide = (1 - ease.out(seg(tt, 0.35, 1.2))) * (tall ? 60 : 24);
    out.push(box(L.ctl, `opacity:${mainVis * ease.out(seg(tt, 0.35, 1.0))};transform:translate(${tall ? 0 : slide}px,${tall ? slide : 0}px)`, controller(L.ctl[2], on, style)));
    out.push(box(L.prm, `display:flex;align-items:center;justify-content:center;opacity:${mainVis}`, promptHtml(tt, style, tall ? 64 : 56)));
  }
  // ---- TV mode ----
  const tvO = Math.min(seg(tt, SC.tv[0] + 0.1, SC.tv[0] + 0.7), 1 - seg(tt, SC.tv[1] - 0.4, SC.tv[1] - 0.1));
  if (tvO > 0) {
    const tw = tall ? 980 : 1000, th = tw * 9 / 16, left = tall ? (W - tw) / 2 : 120, top = tall ? H * 0.3 : 300;
    const t0 = SC.tv[0] + 0.7, word = 'mario', n = cl(Math.floor((tt - t0) / 0.6) + 1, 0, 5), typed = word.slice(0, n);
    const keys = { m: [3, 7], a: [2, 0], r: [1, 3], i: [1, 7], o: [1, 8] }; const k = keys[word[Math.min(n, 4)]];
    const inner = `<div style="position:absolute;inset:0;background:linear-gradient(160deg,#1a2233,#0b0d12)">
       <div style="position:absolute;left:5%;top:7%;font-family:A;font-weight:700;font-size:${th * .07}px">Games</div>
       <div style="position:absolute;left:5%;top:20%;display:flex;gap:2%;width:100%">${COVERS.map(c => `<div style="flex:none;width:10%;height:${th * .3}px;border-radius:12px;background:${c};opacity:.9"></div>`).join('')}</div></div>
       <div style="position:absolute;left:0;right:0;bottom:0">${kbBox(kb({ W: 1920, S: 2, tv: true, preview: { before: typed }, focus: k, tvBottom: 28 }), tw)}</div>`;
    const z = 0.96 + 0.04 * ease.out(seg(tt, SC.tv[0] + 0.1, SC.tv[0] + 1));
    out.push(`<div style="position:absolute;left:${left}px;top:${top}px;width:${tw}px;opacity:${tvO};transform:scale(${z})">
      <div style="position:relative;width:${tw}px;height:${th}px;border:12px solid #1a1a1f;border-radius:16px;overflow:hidden;background:#000;box-shadow:0 40px 90px rgba(0,0,0,.55)">${inner}</div>
      <div style="width:${tw * .16}px;height:10px;margin:14px auto 0;background:#1a1a1f;border-radius:5px"></div></div>`);
    const on = {}; for (let i = 0; i < 5; i++) { const at = t0 + i * 0.6; if (tt >= at && tt < at + 0.3) on.A = 1 - seg(tt, at + 0.2, at + 0.3); }
    const cw = tall ? 560 : 500, cx = tall ? (W - cw) / 2 : 1320, cy = tall ? top + th + 110 : top + th / 2 - cw * 0.32;
    out.push(`<div style="position:absolute;left:${cx}px;top:${cy}px;width:${cw}px;opacity:${tvO * 0.9}">${controller(cw, on, 'xbox')}</div>`);
  }
  // ---- end card: the logo exactly as designed (the kit file) ----
  const eo = Math.min(seg(tt, SC.end[0] + 0.15, SC.end[0] + 0.8), 1 - seg(tt, DUR - 0.8, DUR - 0.05));
  if (eo > 0) { const a = ease.out(seg(tt, SC.end[0] + 0.15, SC.end[0] + 1.0));
    out.push(`<div style="position:absolute;left:0;right:0;top:${H * 0.5 - (tall ? 250 : 190)}px;text-align:center;opacity:${eo}">
      <div style="display:inline-block;width:${tall ? 820 : 760}px;transform:scale(${0.96 + 0.04 * a})">${LOCKUP}</div>
      <div style="font-size:${tall ? 40 : 36}px;color:#F2F3F5;margin-top:22px;opacity:${seg(tt, SC.end[0] + 0.6, SC.end[0] + 1.1)}">Free and open source. No ads, no internet permission.</div>
      <div style="font-size:${tall ? 36 : 32}px;color:${ACC};font-weight:600;margin-top:24px;opacity:${seg(tt, SC.end[0] + 1.0, SC.end[0] + 1.5)}">github.com/dakingeman/inlay</div>
      <div style="font-size:${tall ? 24 : 21}px;color:#7d8089;margin-top:44px;opacity:${seg(tt, SC.end[0] + 1.4, SC.end[0] + 1.9)}">Code written by Claude</div></div>`); }
  document.getElementById('stage').innerHTML = out.join('');
}
/** A Settings-style card floated over the screen (sound packs, button icons). */
function card(o, sh, rowInner, dots) {
  return `<div style="position:absolute;left:6%;right:6%;top:${sh * 0.3}px;opacity:${o}"><div style="background:#141418;border-radius:26px;border:1px solid #ffffff14;padding:5px;box-shadow:0 30px 60px rgba(0,0,0,.6)">
    <div style="display:flex;justify-content:space-between;align-items:center;padding:${sh * .035}px ${sh * .045}px;border-radius:22px;background:#1C1C21;outline:2px solid ${ACC};outline-offset:-2px">${rowInner}</div></div>
    <div style="display:flex;justify-content:center;gap:9px;margin-top:${sh * .03}px">${dots}</div></div>`;
}
if (typeof module !== 'undefined') module.exports = { EV, DUR, SC, BEAT, BAR, TYPE_END, EDIT_END, LOOKS, LOOK_AT: LOOKS.map((_, i) => LOOK_AT(i)) };
