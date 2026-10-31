// Shared pieces for every Inlay image and video: the stylised game library the keyboard types into.
// Colours, corners and lines come from the app (inlay.css); covers use the app's own theme tones, lifted.
const COVER_TONES=['#22324A','#1F3A2B','#3A2C24','#2B3442','#382A44','#1C3A40','#3A3A22','#2A2F3A','#33283A'];
// Xbox classics, because the pictures show Xbox buttons (the app's own demos do the same)
const LIBRARY=[
  {name:'Ninja Gaiden',plat:'XBOX'},{name:'Ninja Gaiden Black',plat:'XBOX'},
  {name:'Star Wars: Knights of the Old Republic',short:'Knights of the Old Republic',plat:'XBOX'},
  {name:'Star Wars: Knights of the Old Republic II',short:'Knights of the Old Republic II',plat:'XBOX'},
  {name:'Viva Piñata',plat:'XBOX 360'},{name:'Fable: The Lost Chapters',plat:'XBOX'},
  {name:'Conker: Live & Reloaded',plat:'XBOX'},{name:'Jet Set Radio Future',plat:'XBOX'},{name:'MechAssault 2',plat:'XBOX'},
  {name:'Halo: Combat Evolved',plat:'XBOX'},{name:'Halo 2',plat:'XBOX'},{name:'Crimson Skies: High Road to Revenge',short:'Crimson Skies',plat:'XBOX'},{name:'Jet Set Radio',plat:'DREAMCAST'}
].map((g,i)=>Object.assign(g,{tone:COVER_TONES[i%COVER_TONES.length],i}));
const norm=s=>s.normalize('NFD').replace(/[\u0300-\u036f]/g,'').toLowerCase().replace(/&/g,'and').replace(/:/g,'').replace(/\s+/g,' ').trim();
function search(q){ q=norm(q); if(!q) return LIBRARY; return LIBRARY.filter(g=>norm(g.name).includes(q)); }

// Generic art (game covers, TV thumbnails): flat colour, one simple shape, no gradients. Muted tones with a little
// character, so they look designed but never pull the eye away from the keyboard.
const GENERIC=[['#3F63B8','#5F89DB'],['#B65C34','#DA8459'],['#2F8A66','#50B08B'],['#7650B8','#9B79DA'],['#B88A22','#DCB049'],['#22879B','#44AEC3'],['#AE4466','#D36B8B']];
function genericShape(i,w,h,tint){const k=i%6,m=Math.min(w,h);
  if(k===0) return `<circle cx="${w*.68}" cy="${h*.34}" r="${m*.30}" fill="${tint}"/>`;
  if(k===1) return `<path d="M0 ${h*.62} L${w*.55} ${h*.18} L${w} ${h*.52} L${w} ${h} L0 ${h}Z" fill="${tint}"/>`;
  if(k===2) return [0,1,2].map(j=>`<rect x="${w*(.14+j*.26)}" y="${h*.16}" width="${w*.12}" height="${h*.46}" rx="${w*.06}" fill="${tint}"/>`).join('');
  if(k===3) return `<rect x="${w*.22}" y="${h*.14}" width="${w*.56}" height="${w*.56}" rx="${w*.14}" fill="${tint}" transform="rotate(12 ${w*.5} ${h*.14+w*.28})"/>`;
  if(k===4) return `<path d="M${w*.15} ${h*.62} A${w*.35} ${w*.35} 0 0 1 ${w*.85} ${h*.62} Z" fill="${tint}"/>`;
  return `<circle cx="${w*.3}" cy="${h*.3}" r="${m*.16}" fill="${tint}"/><circle cx="${w*.66}" cy="${h*.42}" r="${m*.24}" fill="${tint}"/>`;}
function coverArt(tone,i){const [base,tint]=GENERIC[i%GENERIC.length];
  return `<svg class="art" viewBox="0 0 100 140" preserveAspectRatio="none"><rect width="100" height="140" fill="${base}"/>${genericShape(i,100,140,tint)}<rect y="82" width="100" height="58" fill="#000" opacity=".55"/></svg>`;}
function thumbArt(i){const [base,tint]=GENERIC[i%GENERIC.length];
  return `<svg viewBox="0 0 160 90" width="100%" height="100%" preserveAspectRatio="none" style="display:block"><rect width="160" height="90" fill="${base}"/>${genericShape(i+2,160,90,tint)}</svg>`;}
const COVER_DEFS=`<svg width="0" height="0" style="position:absolute"><defs><linearGradient id="coverfade" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#000" stop-opacity="0"/><stop offset="1" stop-color="#000" stop-opacity=".6"/></linearGradient></defs></svg>`;
function covers(list,{w,gap,title,plat,max}){
  const shown=list.slice(0,max||5);
  return `<div class="covers" style="gap:${gap}px;grid-auto-columns:${w}px">${shown.map(c=>`<div class="cover m" style="width:${w}px;height:${Math.round(w*1.4)}px">${coverArt(c.tone,c.i)}
  <div class="cap"><div class="p" style="font-size:${plat}px">${c.plat}</div><div class="t" style="font-size:${title}px">${c.short||c.name}</div></div></div>`).join('')}</div>`;
}
// Search results as a slim list row (launch set, 30 Oct 2026): a small square cover with the platform and title beside it,
// so the library screen stays close to the device's height and every title stays readable
function resultRow(list,{s,max}){
  const shown=list.slice(0,max||2), th=Math.round(64*s), gap=Math.round(14*s);
  return `<div style="display:flex;gap:${Math.round(24*s)}px">${shown.map(c=>`<div class="m" style="display:flex;align-items:center;gap:${gap}px;flex:none;max-width:${Math.round(330*s)}px">
    <div style="width:${th}px;height:${th}px;border-radius:${Math.round(10*s)}px;overflow:hidden;flex:none;position:relative">${coverArt(c.tone,c.i)}</div>
    <div style="min-width:0"><div style="font-family:R;font-weight:500;font-size:${Math.round(12*s)}px;letter-spacing:.06em;color:rgba(255,255,255,.7)">${c.plat}</div>
    <div style="font-family:R;font-weight:700;font-size:${Math.round(17*s)}px;color:var(--paper);white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${c.short||c.name}</div></div></div>`).join('')}</div>`;
}
// The text box: text before the cursor, the selection, text after
function field({before='',sel='',after='',placeholder='Search your games',count=''},{h,fs,pad}){
  const cur=`<span class="cur" style="height:${Math.round(fs*1.2)}px"></span>`;
  const esc=s=>s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/ /g,'&nbsp;');
  let inner;
  if(!before&&!sel&&!after) inner=cur+`<span class="ph">${placeholder}</span>`;
  else if(sel) inner=`<span>${esc(before)}</span><span class="sel">${esc(sel)}</span><span>${esc(after)}</span>`;
  else inner=`<span>${esc(before)}</span>${cur}<span>${esc(after)}</span>`;
  return `<div class="field m" style="height:${h}px;font-size:${fs}px;padding:0 ${pad}px"><div class="ftext">${inner}</div>${count?`<span class="count" style="font-size:${Math.round(fs*.74)}px">${count}</span>`:''}</div>`;
}
/**
 * The library screen with the keyboard docked at its foot. w = width; everything scales from an
 * 800-wide design. Returns {html,h}. o.kb = keyboard image, o.kbAspect = its width / height.
 */
function libScreen(o){
  // o.coverScale < 1: results as a slim list row (see resultRow), so the screen is closer to the device's height
  const s=o.w/800, pad=Math.round(28*s), fh=Math.round(46*s), cw=Math.round(132*s), cg=Math.round(16*s);
  const results=o.results||search((o.field.before||'')+(o.field.sel||'')+(o.field.after||''));
  const count=o.count!==undefined?o.count:(o.field.before||o.field.sel)?`${results.length} game${results.length===1?'':'s'}`:'';
  const kbH=Math.round(o.w/o.kbAspect);
  const slim=(o.coverScale||1)<1, coversH=slim?Math.round(64*s):Math.round(cw*1.4);
  const top=Math.round(24*s), gapBar=Math.round(20*s), gapKb=Math.round(24*s);
  const h=top+fh+gapBar+coversH+gapKb+kbH;
  const html=`<div class="screen m" style="left:${o.x}px;top:${o.y}px;width:${o.w}px;height:${h}px">
   <div class="lib" style="padding:${top}px ${pad}px 0">
    <div class="bar" style="margin-bottom:${gapBar}px;gap:${Math.round(16*s)}px"><div class="title m" style="font-size:${Math.round(25*s)}px">${o.title||'Library'}</div>
      ${o.note?`<div class="body m" style="margin-left:auto;font-size:${Math.round(15*s)}px">${o.note.meta||''}</div>`:''}
      ${o.noField||o.note?'':`<div style="flex:1;min-width:0">${field(Object.assign({count},o.field),{h:fh,fs:Math.round(19*s),pad:Math.round(18*s)})}</div>`}</div>
    <div style="height:${coversH}px">${o.note?noteBody(o.note,s):results.length?(slim?resultRow(results,{s,max:2}):covers(results,{w:cw,gap:cg,title:Math.round(15*s),plat:Math.round(11*s),max:5})):`<div class="body" style="font-size:${Math.round(16*s)}px;padding-top:${Math.round(60*s)}px">No games match</div>`}</div>
   </div><img class="kbd" src="${o.kb}" style="width:${o.w}px;height:${kbH}px"></div>`;
  return {html,h};
}

// A note being written (used where the typing happens in a notes app, not a game library)
function noteBody(n,s){const cur=`<span style="display:inline-block;width:${Math.max(2,Math.round(2.5*s))}px;margin-left:2px;background:var(--teal);border-radius:1px;height:${Math.round(22*s)}px;vertical-align:-${Math.round(4*s)}px"></span>`;
  return `<div class="m" style="height:100%;box-sizing:border-box;background:var(--card);border-radius:${Math.round(16*s)}px;box-shadow:inset 0 0 0 1px var(--edge);padding:${Math.round(20*s)}px ${Math.round(24*s)}px">
   <div style="font-family:R;font-weight:700;color:var(--paper);font-size:${Math.round(20*s)}px;margin-bottom:${Math.round(12*s)}px">${n.heading}</div>
   ${(n.lines||[]).map(l=>`<div style="font-family:R;font-weight:500;color:var(--text2);font-size:${Math.round(18*s)}px;line-height:1.5">${l}</div>`).join('')}
   <div style="font-family:R;font-weight:500;color:var(--paper);font-size:${Math.round(18*s)}px;line-height:1.5">${n.typing}${cur}</div></div>`;}
