// Inlay keyboard renderer for images and the highlight reel.
// Mirrors KeyGridView: sizes in dp (tool 26, key 31, gap 4, edge 6), 10-unit rows,
// Glow highlight, controller badges in the key corners, RT combo chips.
const RECTS = {};
const LANGS = {
  English: ["qwertyuiop","asdfghjkl'","zxcvbnm"],
  Deutsch: ["qwertzuiopü","asdfghjklöä","yxcvbnmß"],
  Français: ["azertyuiop","qsdfghjklm","wxcvbn'"],
  Español: ["qwertyuiop","asdfghjklñ","zxcvbnm"],
  Norsk: ["qwertyuiopå","asdfghjkløæ","zxcvbnm"],
  Türkçe: ["qwertyuıopğü","asdfghjklşi'","zxcvbnmöç"],
  Русский: ["йцукенгшщзхъ","фывапролджэё","ячсмитьбю"],
};
const THEMES = {
  OLED: {bg:"#000000", key:"#1B1B1F", special:"#111114"},
  Light: {bg:"#D7DAE0", key:"#FFFFFF", special:"#BFC4CC"},
  Outline: {bg:"#000000", key:"#000000", special:"#0B0B0D", edge:"#62656D"},
  Clear: {bg:"rgba(0,0,0,0.24)", key:"rgba(0,0,0,0.65)", special:"rgba(0,0,0,0.73)", edge:"rgba(255,255,255,0.15)", clear:true},
  Wallpaper: {bg:"#1A1C22", key:"#3F4759", special:"#2C313E"},
  Midnight: {bg:"#03060C", key:"#172030", special:"#0F1622"},
  Graphite: {bg:"#0E0E10", key:"#26262B", special:"#1A1A1E"},
};
const TEAL = "#72D6E8";
function lum(hex){ if(!hex.startsWith('#')) return 0; const c=[1,3,5].map(i=>parseInt(hex.substr(i,2),16)/255).map(v=>v<=0.03928?v/12.92:Math.pow((v+0.055)/1.055,2.4)); return 0.2126*c[0]+0.7152*c[1]+0.0722*c[2]; }
function inkFor(bg){ const L=lum(bg); return (1.05/(L+0.05)) >= ((L+0.05)/(lum("#0B0F17")+0.05)) ? "#FFFFFF" : "#0B0F17"; }
function rowsFor(o){
  const L = LANGS[o.lang||"English"]; const cols = Math.max(...L.map(r=>[...r].length)); const w = 10/cols;
  const ch = (s,wt=1)=>[...s].map(c=>({t:"c",l:c,w:wt}));
  const spare = cols-[...L[2]].length; const sh=Math.floor(spare/2), de=spare-sh;
  const side=2;
  let bottom;
  if (o.field==="url") bottom=[{t:"layer",l:"?123",w:2,h:"LT"},{t:"c",l:"/",w:1},{t:"space",w:2,h:"Y"},{t:"c",l:".com",w:2},{t:"c",l:".",w:1},{t:"enter",w:2,h:"START"}];
  else if (o.field==="email") bottom=[{t:"layer",l:"?123",w:2,h:"LT"},{t:"c",l:"@",w:1},{t:"space",w:2,h:"Y"},{t:"c",l:".com",w:2},{t:"c",l:".",w:1},{t:"enter",w:2,h:"START"}];
  else bottom=[{t:"layer",l:o.layer==="symbols"?"ABC":"?123",w:2,h:"LT"},{t:"c",l:",",w:1},{t:"space",w:4,h:"Y"},{t:"c",l:".",w:1},{t:"enter",w:2,h:"START"}];
  if (o.layer==="numpad") {
    const n=s=>({t:"c",l:s,w:2});
    return [[n("1"),n("2"),n("3"),{t:"del",w:2,h:"X"}],[n("4"),n("5"),n("6"),n("-")],[n("7"),n("8"),n("9"),n(",")],[{t:"layer",l:"ABC",w:2,h:"LT"},n("0"),n("."),{t:"enter",w:2,h:"START"}]];
  }
  if (o.layout==='classic') return [ch(L[0]), ch("asdfghjkl"), [{t:"shift",w:1.5,h:"RT"}].concat(ch("zxcvbnm")).concat([{t:"del",w:1.5,h:"X"}]), [{t:"layer",l:"?123",w:1.5,h:"LT"},{t:"c",l:",",w:1},{t:"space",w:5,h:"Y"},{t:"c",l:".",w:1},{t:"enter",w:1.5,h:"START"}]];
  if (o.layer==="symbols") return [ch("1234567890"), ch("@#$%&-+()/"), ch("*\"':;!?=€").concat([{t:"del",w:1,h:"X"}]), bottom];
  return [ch(L[0],w), ch(L[1],w), [{t:"shift",w:sh*w,h:"RT"}].concat(ch(L[2],w)).concat([{t:"del",w:de*w,h:"X"}]), bottom];
}
const STYLE={v:'xbox'};
function faceGlyph(b,cx,cy,sz,style){ // b = physical A/B/X/Y (Xbox positions)
  const g=sz*0.26, sw=sz*0.085;
  if(style==='ps'){ const col={A:'#8FB4FF',B:'#FF6B6B',X:'#F59BD5',Y:'#4DD6B5'}[b];
    if(b==='A') return `<path d="M${cx-g},${cy-g}L${cx+g},${cy+g}M${cx+g},${cy-g}L${cx-g},${cy+g}" stroke="${col}" stroke-width="${sw}" stroke-linecap="round"/>`;
    if(b==='B') return `<circle cx="${cx}" cy="${cy}" r="${g}" fill="none" stroke="${col}" stroke-width="${sw}"/>`;
    if(b==='X') return `<rect x="${cx-g*.85}" y="${cy-g*.85}" width="${g*1.7}" height="${g*1.7}" fill="none" stroke="${col}" stroke-width="${sw}" stroke-linejoin="round"/>`;
    return `<path d="M${cx},${cy-g}L${cx+g},${cy+g*.75}L${cx-g},${cy+g*.75}Z" fill="none" stroke="${col}" stroke-width="${sw}" stroke-linejoin="round"/>`; }
  const lab = style==='switch' ? {A:'B',B:'A',X:'Y',Y:'X'}[b] : b;
  const col = style==='switch' ? '#F2F2F2' : {A:'#6CC24A',B:'#F0503C',X:'#3E9BFF',Y:'#F5C518'}[b];
  return `<text x="${cx}" y="${cy}" fill="${col}" font-size="${sz*0.62}" font-weight="700" font-family="I" text-anchor="middle" dominant-baseline="central">${lab}</text>`;
}
function shoulderLabel(b,style){ const m={xbox:{LB:'LB',RB:'RB',LT:'LT',RT:'RT',LS:'LS'},ps:{LB:'L1',RB:'R1',LT:'L2',RT:'R2',LS:'L3'},switch:{LB:'L',RB:'R',LT:'ZL',RT:'ZR',LS:'LS'}}; return m[style][b]; }
function badge(x,y,S,b,style){ // a controller badge on a dark disc, top-right anchored at (x,y)
  style=style||STYLE.v; const shoulder = ["RT","LT","LB","RB","LS"].includes(b);
  const h=13*S, w=shoulder?20*S:13*S, cx=x-w/2, cy=y+h/2;
  let s=`<rect x="${x-w}" y="${y}" width="${w}" height="${h}" rx="${h/2}" fill="#0A0A0D" fill-opacity="0.95" stroke="#ffffff" stroke-opacity="0.3" stroke-width="${0.8*S}"/>`;
  if (b==="SELECT") { const g=2.6*S, q=g*1.2; s+=`<rect x="${cx-g}" y="${cy-g}" width="${q}" height="${q}" fill="none" stroke="#fff" stroke-width="${0.9*S}"/><rect x="${cx+g-q}" y="${cy+g-q}" width="${q}" height="${q}" fill="none" stroke="#fff" stroke-width="${0.9*S}"/>`; }
  else if (b==="START") s+=[-2.2,0,2.2].map(d=>`<line x1="${cx-2.6*S}" x2="${cx+2.6*S}" y1="${cy+d*S}" y2="${cy+d*S}" stroke="#fff" stroke-width="${0.9*S}" stroke-linecap="round"/>`).join("");
  else if (shoulder) s+=`<text x="${cx}" y="${cy}" fill="#fff" font-size="${6.6*S}" font-weight="700" font-family="I" text-anchor="middle" dominant-baseline="central">${shoulderLabel(b,style)}</text>`;
  else s+=faceGlyph(b,cx,cy,h,style);
  return s;
}
function chip(x,cy,S,parts,ink){ // returns [svg,width]
  let w=8*S, items=[];
  for (const p of parts){ if(p.b){items.push({b:p.b,x:w}); w+= (["RT","LT","LB","RB"].includes(p.b)?20:13)*S+4*S;} else {items.push({t:p.t,x:w}); w+=p.t.length*5.6*S+8*S;} }
  const h=18*S; let s=`<rect x="${x}" y="${cy-h/2}" width="${w}" height="${h}" rx="${h/2}" fill="${ink}" fill-opacity="0.07" stroke="${ink}" stroke-opacity="0.14"/>`;
  for (const it of items){ if(it.b){const bw=(["RT","LT","LB","RB"].includes(it.b)?20:13)*S; s+=badge(x+it.x+bw,cy-6.5*S,S,it.b);} else s+=`<text x="${x+it.x}" y="${cy}" fill="${ink}" fill-opacity="0.82" font-size="${10.5*S}" font-weight="500" font-family="I" dominant-baseline="central">${it.t}</text>`; }
  return [s,w];
}
function kb(o){
  STYLE.v=o.style||'xbox';
  const glide=o.glide; // {from:[r,c], to:[r,c], p:0..1}
  const S=o.S||2, W=o.W||1280, th=THEMES[o.theme||"OLED"], acc=o.accent||TEAL;
  const edge=6*S, tool=26*S, key=31*S, gap=4*S, corner=(o.corner??9)*S;
  const tv=o.tv, pv=34*S, top0=edge+(tv?pv+gap:0), bottomM=tv?(o.tvBottom??14*S):0;
  const H=top0+tool+gap+4*key+3*gap+edge+bottomM; const cw=tv?Math.min(W*0.9,600*S):Math.min(W-2*edge,1000*S), left=(W-cw)/2, unit=cw/10;
  const backdrop = th.clear ? "#000000" : th.bg;
  let s=`<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">`;
  if (o.behind) s+=o.behind(W,H);
  if (tv){ const pad=10*S, px0=left-pad, py0=edge-pad*0.6, pw=cw+2*pad, ph=top0+tool+gap+4*key+3*gap+pad-py0;
    s+=`<rect x="${px0}" y="${py0}" width="${pw}" height="${ph}" rx="${20*S}" fill="${th.bg}" fill-opacity="${th.clear?1:0.95}" stroke="${inkFor(backdrop)}" stroke-opacity="0.12" stroke-width="${S}"/>`;
    const ink=inkFor(backdrop); s+=`<rect x="${left}" y="${edge}" width="${cw}" height="${pv}" rx="${10*S}" fill="${ink}" fill-opacity="0.08"/>`;
    const fs=18*S, ty=edge+pv/2; const b=o.preview?.before||'', sel=o.preview?.sel||'', a=o.preview?.after||'';
    const meas=(t)=>{ try{ const c=(globalThis.__mc ||= document.createElement('canvas').getContext('2d')); c.font=`500 ${fs}px I`; return c.measureText(t).width; }catch(e){ return t.length*fs*0.52; } }; let x=left+12*S;
    if(!b&&!sel&&!a) s+=`<text x="${x}" y="${ty}" fill="${ink}" fill-opacity="0.35" font-size="${fs}" font-family="I" font-weight="500" dominant-baseline="central">Type with your controller</text>`;
    s+=`<text x="${x}" y="${ty}" fill="${ink}" font-size="${fs}" font-family="I" font-weight="500" dominant-baseline="central" xml:space="preserve">${b}</text>`;
    x+=meas(b);
    if(sel){ s+=`<rect x="${x-S}" y="${edge+7*S}" width="${meas(sel)+2*S}" height="${pv-14*S}" rx="${4*S}" fill="${acc}" fill-opacity="0.35"/><text x="${x}" y="${ty}" fill="${ink}" font-size="${fs}" font-family="I" font-weight="500" dominant-baseline="central" xml:space="preserve">${sel}</text>`; x+=meas(sel); }
    else s+=`<rect x="${x+S}" y="${edge+8*S}" width="${2*S}" height="${pv-16*S}" fill="${acc}"/>`;
  } else s+=`<rect width="${W}" height="${H}" fill="${th.bg}"/>`;
  const rows=rowsFor(o);
  const tools=[Object.assign({l:"Select",w:1.9},o.selectBadge?{h:"SELECT"}:{}),{l:"Cut",w:1.2},{l:"Copy",w:1.3},{l:"Paste",w:3.8},{l:"clock",w:0.9},{l:"gear",w:0.9}];
  const all=[tools].concat(rows);
  const f=o.focus||[2,4]; const LABELS=[];
  const caps = o.shift && o.shift!=="off";
  all.forEach((row,r)=>{
    const tot=row.reduce((a,k)=>a+k.w,0); let x=left+(10-tot)/2*unit;
    const top = r===0?top0:top0+tool+gap+(r-1)*(key+gap), h=r===0?tool:key;
    row.forEach((k,c)=>{
      const x0=x+gap/2, x1=x+k.w*unit-gap/2; const isF = !glide && r===f[0] && c===f[1] && !o.noFocus;
      RECTS[r+','+c]=[x0,top,x1-x0,h];
      const isDest = glide && r===glide.to[0] && c===glide.to[1] && glide.p>0.5;
      let fill = r===0 ? null : (k.t==="c"||k.t==="space") ? th.key : th.special;
      if (k.t==="shift" && o.shift==="lock") fill=acc; else if (k.t==="shift" && o.shift==="once") fill=acc+"40";
      if (r===0 && o.selecting && k.l==="Select") fill=acc+"40";
      if (r===0 && o.held) { x+=k.w*unit; return; }
      if (isF){ s+=`<rect x="${x0-2.5*S}" y="${top-2.5*S}" width="${x1-x0+5*S}" height="${h+5*S}" rx="${corner+2.5*S}" fill="none" stroke="${acc}" stroke-opacity="0.35" stroke-width="${2*S}"/>`; fill=acc; }
      if (fill) s+=`<rect x="${x0}" y="${top}" width="${x1-x0}" height="${h}" rx="${corner}" fill="${fill}"/>`;
      if (th.edge && r>0 && !isF) s+=`<rect x="${x0+0.5*S}" y="${top+0.5*S}" width="${x1-x0-S}" height="${h-S}" rx="${corner-0.5*S}" fill="none" stroke="${th.edge}" stroke-width="${S}"/>`;
      const _mark=s.length;
      const vis = (isF||isDest) ? acc : (fill && fill.startsWith('#') && fill.length===7 ? fill : backdrop);
      const ink = inkFor(vis), cx=(x0+x1)/2, cy=top+h/2;
      const sh = th.clear && !isF && !isDest ? ` style="filter:drop-shadow(0 ${0.5*S}px ${1.2*S}px rgba(0,0,0,.6))"` : "";
      if (k.t==="c"){ const l = caps && [...k.l].length===1 ? k.l.toLocaleUpperCase(o.lang==="Türkçe"?"tr":undefined) : k.l;
        const fs = [...k.l].length>1 ? 14*S : 19*S;
        s+=`<text x="${cx}" y="${cy}" fill="${ink}" font-size="${fs}" font-family="A, I" text-anchor="middle" dominant-baseline="central"${sh}>${l}</text>`; }
      else if (k.t==="layer") s+=`<text x="${cx}" y="${cy}" fill="${ink}" font-size="${13*S}" font-weight="600" font-family="I" text-anchor="middle" dominant-baseline="central"${sh}>${k.l}</text>`;
      else if (k.t==="space"){
        if (o.selecting){ let [c1,w1]=chip(0,0,S,[{b:"LB"},{b:"RB"},{t:"Extend"}],ink); let [c2,w2]=chip(0,0,S,[{b:"B"},{t:"Done"}],ink);
          const tw=w1+w2+6*S; let cx0=cx-tw/2; s+=chip(cx0,cy,S,[{b:"LB"},{b:"RB"},{t:"Extend"}],ink)[0]+chip(cx0+w1+6*S,cy,S,[{b:"B"},{t:"Done"}],ink)[0]; }
        else if (o.spaceLabel!==false) s+=`<text x="${cx}" y="${cy}" fill="${ink}" fill-opacity="0.47" font-size="${13*S}" font-weight="500" font-family="I" text-anchor="middle" dominant-baseline="central"${sh}>${o.spaceText||o.lang||"English"}</text>`;
        else s+=`<line x1="${cx-(x1-x0)*0.12}" x2="${cx+(x1-x0)*0.12}" y1="${cy+3*S}" y2="${cy+3*S}" stroke="${ink}" stroke-opacity="0.68" stroke-width="${1.8*S}" stroke-linecap="round"/>`; }
      else if (k.t==="shift"){ const a=6*S; s+=`<path d="M${cx},${cy-a} L${cx+a},${cy} L${cx+a*0.45},${cy} L${cx+a*0.45},${cy+a*0.8} L${cx-a*0.45},${cy+a*0.8} L${cx-a*0.45},${cy} L${cx-a},${cy} Z" fill="${o.shift==="lock"?ink:"none"}" stroke="${ink}" stroke-opacity="0.8" stroke-width="${1.8*S}" stroke-linejoin="round"/>`; }
      else if (k.t==="del"){ const a=6*S; s+=`<path d="M${cx-a*1.3},${cy} L${cx-a*0.5},${cy-a*0.8} L${cx+a*1.2},${cy-a*0.8} L${cx+a*1.2},${cy+a*0.8} L${cx-a*0.5},${cy+a*0.8} Z M${cx-a*0.05},${cy-a*0.35} L${cx+a*0.65},${cy+a*0.35} M${cx+a*0.65},${cy-a*0.35} L${cx-a*0.05},${cy+a*0.35}" fill="none" stroke="${ink}" stroke-opacity="0.8" stroke-width="${1.8*S}" stroke-linejoin="round" stroke-linecap="round"/>`; }
      else if (k.t==="enter"){ const a=6*S; s+=`<path d="M${cx+a},${cy-a*0.9} L${cx+a},${cy+a*0.3} L${cx-a},${cy+a*0.3} M${cx-a*0.45},${cy-a*0.25} L${cx-a},${cy+a*0.3} L${cx-a*0.45},${cy+a*0.85}" fill="none" stroke="${ink}" stroke-opacity="0.8" stroke-width="${1.8*S}" stroke-linejoin="round" stroke-linecap="round"/>`; }
      else if (r===0){ const tink=inkFor(backdrop);
        if (k.l==="clock") s+=`<circle cx="${cx}" cy="${cy}" r="${6*S}" fill="none" stroke="${tink}" stroke-opacity="0.7" stroke-width="${1.6*S}"/><path d="M${cx},${cy-3.5*S} L${cx},${cy} L${cx+2.5*S},${cy+1.5*S}" fill="none" stroke="${tink}" stroke-opacity="0.7" stroke-width="${1.6*S}" stroke-linecap="round"/>`;
        else if (k.l==="gear") s+=`<circle cx="${cx}" cy="${cy}" r="${4.5*S}" fill="none" stroke="${tink}" stroke-opacity="0.7" stroke-width="${2.6*S}" stroke-dasharray="${2*S} ${1.3*S}"/><circle cx="${cx}" cy="${cy}" r="${2*S}" fill="none" stroke="${tink}" stroke-opacity="0.7" stroke-width="${1.4*S}"/>`;
        else s+=`<text x="${cx}" y="${cy}" fill="${tink}" fill-opacity="0.75" font-size="${12.5*S}" font-weight="500" font-family="I" text-anchor="middle" dominant-baseline="central">${k.l}</text>`; }
      if (k.h && o.hints!==false && !(k.t==="space" && o.selecting)) s+=badge(x1-3*S,top+3*S,S,k.h);
      LABELS.push(s.slice(_mark)); s=s.slice(0,_mark);
      x+=k.w*unit;
    });
  });
  s+='%%GLIDE%%'+LABELS.join('');
  if (o.held){ const chips=o.chips||[[{b:"LB"},{b:"RB"},{t:"Jump word"}],[{b:"A"},{t:"Capital"}],[{b:"Y"},{t:"Accent"}],[{b:"LT"},{t:"Language"}],[{b:"X"},{t:"Undo"}]];
    const ink=inkFor(backdrop); const ws=chips.map(c=>chip(0,0,S,c,ink)[1]); const tw=ws.reduce((a,b)=>a+b,0)+6*S*(chips.length-1);
    let x=W/2-tw/2; chips.forEach((c,i)=>{ s+=chip(x,top0+tool/2,S,c,ink)[0]; x+=ws[i]+6*S; }); }
  if (glide){ const a=RECTS[glide.from.join(',')], b=RECTS[glide.to.join(',')];
    if (a && b){ const e=1-Math.pow(1-glide.p,3); const L=(i)=>a[i]+(b[i]-a[i])*e; const [gx,gy,gw,gh]=[L(0),L(1),L(2),L(3)];
      const sc=o.press?1-0.06*Math.sin(Math.PI*o.press):1; const cx=gx+gw/2, cy=gy+gh/2, w2=gw*sc, h2=gh*sc;
      const pill=`<rect x="${cx-w2/2-2.5*S}" y="${cy-h2/2-2.5*S}" width="${w2+5*S}" height="${h2+5*S}" rx="${corner+2.5*S}" fill="none" stroke="${acc}" stroke-opacity="${tv?0.55:0.35}" stroke-width="${(tv?3:2)*S}"/><rect x="${cx-w2/2}" y="${cy-h2/2}" width="${w2}" height="${h2}" rx="${corner}" fill="${acc}"/>`;
      // labels were drawn after backgrounds; put the pill under the labels by splitting at the marker
      const i=s.indexOf('%%GLIDE%%'); if(i>=0) s=s.slice(0,i)+pill+s.slice(i+9); } }
  s=s.replace('%%GLIDE%%','');
  return s+"</svg>";
}
if (typeof module!=="undefined") module.exports={kb,LANGS,THEMES,TEAL};
