// Teaser B: a quick hook, then six features that set Inlay apart for controller users, one per bar,
// each with a small visual. Transitions follow one rule: everything enters from below and leaves upward,
// one motion per cut, on the bar line. Reuses the keyboard, controller and typing engine from spotlight4.js (teaser mode).
const T2 = { hook: [0, 2 * BAR], feats: [2 * BAR, 8 * BAR], end: [8 * BAR, 10 * BAR] };
const DUR2 = T2.end[1];
const FEAT = [
  { title: 'Every button has a job', line: 'A types, X deletes, Y is space, Start goes.', vis: 'buttons' },
  { title: 'Edit text, properly', line: 'Select, cut, copy, paste and undo from the controller.', vis: 'edit' },
  { title: 'Hold RT for more', line: 'Capitals, word jumps, accents and undo, with tips on the keys.', vis: 'rt' },
  { title: 'Clipboard history', line: 'Your last six copies, one button away.', vis: 'clips' },
  { title: 'Smart keys', line: '.com and / for web addresses, @ for email, a number pad for numbers.', vis: 'smart' },
  { title: 'Make it yours', line: 'Twelve themes, seven sound packs, Xbox, PlayStation or Switch icons.', vis: 'yours' },
];
const featAt = i => T2.feats[0] + i * BAR;

function visual(kind, tt, t0, w, h) {
  // p: how far into this feature's bar we are; a: staggered pop-in for inner items
  const pop = (i, n = 0.07) => { const a = ease.out(seg(tt, t0 + 0.1 + i * n, t0 + 0.38 + i * n)); return `opacity:${a};transform:translateY(${(1 - a) * 14}px) scale(${0.94 + 0.06 * a})`; };
  const chip = (parts, i) => `<div style="display:inline-flex;align-items:center;gap:10px;padding:10px 18px;border-radius:30px;background:#1C1C21;border:1px solid #ffffff14;font-size:26px;color:#C9CCD3;${pop(i)}">${parts}</div>`;
  if (kind === 'buttons') {
    const rows = [['A', 'Type'], ['X', 'Delete'], ['Y', 'Space'], ['START', 'Go']];
    return `<div style="display:flex;flex-direction:column;gap:18px">${rows.map(([b, l], i) => `<div style="display:flex;align-items:center;gap:22px;${pop(i)}">${bigBadge(b, 72)}<span style="font-size:40px;font-weight:500">${l}</span></div>`).join('')}</div>`;
  }
  if (kind === 'edit') {
    const sel = seg(tt, t0 + 0.5, t0 + 1.1), s = 'metroid prime', n = Math.round(5 * sel);
    const txt = esc(s.slice(0, 13 - n)) + `<span style="background:${ACC}66;border-radius:6px">${esc(s.slice(13 - n))}</span>`;
    return `<div style="${pop(0)}"><div class="field" style="width:${w * .8}px;height:84px;font-size:38px;white-space:pre">${txt}</div></div>
      <div style="display:flex;gap:12px;margin-top:26px;flex-wrap:wrap">${['Select', 'Cut', 'Copy', 'Paste', 'Undo'].map((l, i) => chip(l, i + 2))}</div>`;
  }
  if (kind === 'rt') {
    const tips = [[['LB', 'RB'], 'Jump word'], [['A'], 'Capital'], [['Y'], 'Accent'], [['X'], 'Undo']];
    return `<div style="display:flex;align-items:center;gap:22px;${pop(0)}">${bigBadge('RT', 80)}<span style="font-size:40px;color:#9A9DA5">hold, then…</span></div>
      <div style="display:flex;gap:12px;margin-top:28px;flex-wrap:wrap">${tips.map(([bs, l], i) => chip(bs.map(b => bigBadge(b, 40)).join('') + l, i + 1)).join('')}</div>`;
  }
  if (kind === 'clips') {
    const clips = ['github.com/dakingeman/inlay', 'metroid prime', 'save state slot 3'];
    return `<div style="display:flex;flex-direction:column;gap:14px">${clips.map((c, i) => `<div style="height:70px;border-radius:18px;background:${i === 0 ? ACC : '#1B1B1F'};color:${i === 0 ? '#0B0F17' : '#F2F3F5'};display:flex;align-items:center;padding:0 26px;font-size:30px;width:${w * .78}px;box-sizing:border-box;${pop(i)}">${c}</div>`).join('')}
      <div style="display:flex;gap:18px;margin-top:10px;font-size:24px;color:#9A9DA5;align-items:center;${pop(3)}">${bigBadge('A', 36)} Paste <span style="width:10px"></span>${bigBadge('X', 36)} Remove</div></div>`;
  }
  if (kind === 'smart') {
    const key = (l, i, wd = 180) => `<div style="width:${wd}px;height:90px;border-radius:16px;background:#1B1B1F;display:flex;align-items:center;justify-content:center;font-family:A;font-size:32px;${pop(i)}">${l}</div>`;
    const num = ['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((n, i) => `<div style="width:74px;height:58px;border-radius:12px;background:${n === '5' ? ACC : '#1B1B1F'};color:${n === '5' ? '#0B0F17' : '#F2F3F5'};display:flex;align-items:center;justify-content:center;font-family:A;font-size:24px">${n}</div>`).join('');
    return `<div style="display:flex;gap:14px">${key('.com', 0)}${key('/', 1, 110)}${key('@', 2, 110)}</div>
      <div style="display:grid;grid-template-columns:repeat(3,74px);gap:8px;margin-top:22px;${pop(3)}">${num}</div>`;
  }
  if (kind === 'yours') {
    const dots = ['#72D6E8', '#FF8A80', '#B39DFF', '#7EE6A8', '#FFC857', '#F48FB1'].map((c, i) => `<div style="width:46px;height:46px;border-radius:50%;background:${c};${pop(i, 0.06)}"></div>`).join('');
    const minis = [['Light', { theme: 'Light', accent: '#4FA8FF' }], ['Clear', { theme: 'Clear', accent: ACC, behind: gameBehind }], ['Outline', { theme: 'Outline', accent: '#FFC857' }]]
      .map(([n, o], i) => `<div style="width:255px;${pop(i + 2)}">${kbBox(kb(Object.assign({ W: 1280, S: 2, focus: [2, 4], hints: false }, o)), 255)}</div>`).join('');
    return `<div style="display:flex;gap:12px">${dots}</div><div style="display:flex;gap:14px;margin-top:22px">${minis}</div>
      <div style="display:flex;gap:10px;margin-top:22px;align-items:center;${pop(6)}">${['A', 'B'].map(b => bigBadge(b, 40, 'xbox')).join('')}<span style="width:8px"></span>${['A', 'B'].map(b => bigBadge(b, 40, 'ps')).join('')}<span style="width:8px"></span>${['A', 'B'].map(b => bigBadge(b, 40, 'switch')).join('')}<span style="font-size:24px;color:#9A9DA5;margin-left:10px">7 sound packs</span></div>`;
  }
  return '';
}

function render2(tt, W, H) {
  const tall = H > W, st = document.getElementById('stage');
  // 1. hook: the launcher scene from the main teaser, fading out into the list
  if (tt < T2.hook[1] + 0.05) { render(tt, W, H); st.style.opacity = 1 - seg(tt, T2.hook[1] - 0.35, T2.hook[1]); return; }
  st.style.opacity = 1; const out = [];
  // 2. features, one per bar: the outgoing one lifts and fades, the incoming one rises in
  if (tt < T2.end[0] + 0.3) {
    for (let i = 0; i < FEAT.length; i++) {
      const t0 = featAt(i), t1 = featAt(i + 1);
      const inA = ease.out(seg(tt, t0 - 0.06, t0 + 0.3)), outA = i < FEAT.length - 1 ? seg(tt, t1 - 0.3, t1 - 0.06) : seg(tt, T2.end[0] - 0.3, T2.end[0] - 0.06);
      const o = inA * (1 - outA); if (o <= 0) continue;
      const y = (1 - inA) * 40 - outA * 36;
      const f = FEAT[i];
      const left = tall ? 70 : 110, titleTop = tall ? 230 : 300, visLeft = tall ? 70 : 960, visTop = tall ? 760 : 260, visW = tall ? 940 : 800, visScale = tall ? 1 : 1.15;
      out.push(`<div style="position:absolute;left:0;top:0;width:${W}px;height:${H}px;opacity:${o};transform:translateY(${y}px)">
        <div style="position:absolute;left:${left}px;top:${titleTop}px;width:${tall ? 940 : 820}px">
          <div style="font-size:24px;letter-spacing:3px;color:${ACC};font-weight:600;margin-bottom:18px">${i + 1} / ${FEAT.length}</div>
          <div style="font-family:A;font-weight:700;font-size:${tall ? 84 : 86}px;letter-spacing:-1px;line-height:1.02">${f.title}</div>
          <div style="font-size:${tall ? 36 : 34}px;color:#9A9DA5;margin-top:22px;line-height:1.35">${f.line}</div></div>
        <div style="position:absolute;left:${visLeft}px;top:${visTop}px;width:${visW}px;height:${tall ? 760 : 560}px;display:flex;flex-direction:column;justify-content:center;transform:scale(${visScale});transform-origin:0 50%">${visual(f.vis, tt, t0, visW / visScale, 560)}</div></div>`);
    }
    // progress: six segments, the current one filled
    const cur = cl(Math.floor((tt - T2.feats[0]) / BAR), 0, 5), prog = seg(tt, featAt(cur), featAt(cur + 1));
    out.push(`<div style="position:absolute;left:0;right:0;bottom:${tall ? 110 : 56}px;display:flex;justify-content:center;gap:10px;opacity:${1 - seg(tt, T2.end[0] - 0.3, T2.end[0])}">${FEAT.map((_, k) => `<div style="width:64px;height:6px;border-radius:3px;background:#2E2E35;overflow:hidden"><div style="height:100%;width:${k < cur ? 100 : k === cur ? prog * 100 : 0}%;background:${ACC}"></div></div>`).join('')}</div>`);
  }
  // 3. end card: the logo exactly as designed
  const e0 = T2.end[0], eo = Math.min(seg(tt, e0 + 0.1, e0 + 0.7), 1 - seg(tt, DUR2 - 0.8, DUR2 - 0.05));
  if (eo > 0) { const a = ease.out(seg(tt, e0 + 0.1, e0 + 0.9));
    out.push(`<div style="position:absolute;left:0;right:0;top:${H * 0.5 - (tall ? 250 : 190)}px;text-align:center;opacity:${eo}">
      <div style="display:inline-block;width:${tall ? 820 : 760}px;transform:scale(${0.96 + 0.04 * a})">${LOCKUP}</div>
      <div style="font-size:${tall ? 40 : 36}px;color:#F2F3F5;margin-top:22px;opacity:${seg(tt, e0 + 0.5, e0 + 1.0)}">Free and open source. No ads, no internet permission.</div>
      <div style="font-size:${tall ? 36 : 32}px;color:${ACC};font-weight:600;margin-top:24px;opacity:${seg(tt, e0 + 0.9, e0 + 1.4)}">github.com/dakingeman/inlay</div>
      <div style="font-size:${tall ? 24 : 21}px;color:#7d8089;margin-top:44px;opacity:${seg(tt, e0 + 1.3, e0 + 1.8)}">Code written by Claude</div></div>`); }
  st.innerHTML = out.join('');
}
