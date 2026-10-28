// Generic handhelds and a TV remote in Inlay's line style. Every part that can be pressed lights up on its own.
function handheldSvg(kind, w, opt){
  opt=opt||{}; const d=DEV[kind], [vx,vy,vw,vh]=d.vb, line='#5a5e6a', detail='#33363f', ACC=opt.acc||'#72D6E8', on=opt.on||{}, style=opt.style||'xbox';
  const sw=vw/w, lit=k=>on[k]>0, L=k=>lit(k)?`fill="${ACC}" fill-opacity=".9" stroke="${ACC}" filter="url(#gh${kind})"`:`fill="#0f1014" stroke="${line}"`;
  const ink=k=>lit(k)?'#0B0F17':'#8d919c';
  let s=`<svg width="${w}" height="${w*vh/vw}" viewBox="${vx} ${vy} ${vw} ${vh}" style="overflow:visible"><defs><filter id="gh${kind}" x="-40%" y="-40%" width="180%" height="180%"><feGaussianBlur stdDeviation="${4*sw}" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>`;
  // shoulders first (behind the body): a trigger and a bumper on each top corner
  const S=d.shoulders, M=x=>2*S.cx-x, names={xbox:['LT','LB','RT','RB'],ps:['L2','L1','R2','R1'],switch:['ZL','L','ZR','R']}[style];
  const tab=([x,y,ww,hh],k,mir,label,ly)=>{const X=mir?M(x+ww):x;return `<rect x="${X}" y="${y}" width="${ww}" height="${hh+18*sw}" rx="${Math.min(hh,ww)/2.2}" ${L(k)} stroke-width="${1.5*sw}"/><text x="${X+ww/2}" y="${y+hh*0.5}" fill="${ink(k)}" font-family="I" font-weight="700" font-size="${hh*0.52}" text-anchor="middle" dominant-baseline="central">${label}</text>`};
  s+=tab(S.LT,'LT',0,names[0])+tab(S.LT,'RT',1,names[2])+tab(S.LB,'LB',0,names[1])+tab(S.LB,'RB',1,names[3]);
  s+=`<path d="${d.body}" fill="#101116" stroke="${line}" stroke-width="${1.7*sw}" stroke-linejoin="round"/>`;
  const r=d.screen; s+=`<rect x="${r[0]}" y="${r[1]}" width="${r[2]}" height="${r[3]}" rx="${7*sw}" fill="#08090c" stroke="${detail}" stroke-width="${1.2*sw}"/>`+(opt.screen||'');
  const stick=([x,y],rr,k)=>`<circle cx="${x}" cy="${y}" r="${rr*1.3}" fill="#0c0d11" stroke="${detail}" stroke-width="${sw}"/><circle cx="${x}" cy="${y}" r="${rr}" ${L(k)} stroke-width="${1.3*sw}"/><circle cx="${x}" cy="${y}" r="${rr*0.62}" fill="none" stroke="${lit(k)?'#0B0F17':detail}" stroke-opacity="${lit(k)?.4:1}" stroke-width="${sw}"/>`;
  if(kind==='horizontal') s+=stick(d.sticks[0],d.stickR,'LS')+stick(d.sticks[1],d.stickR,'RS');
  // D-pad: a cross whose arms light separately
  const [x,y,rr]=d.dpad, t=rr*0.34, arm=(dx,dy,k)=>{const a=dx?[x+dx*t,y-t,x+dx*rr,y+t]:[x-t,y+dy*t,x+t,y+dy*rr];return `<rect x="${Math.min(a[0],a[2])}" y="${Math.min(a[1],a[3])}" width="${Math.abs(a[2]-a[0])}" height="${Math.abs(a[3]-a[1])}" rx="${t*0.35}" ${L(k)} stroke-width="${1.2*sw}"/>`};
  s+=arm(0,-1,'Dup')+arm(0,1,'Ddown')+arm(-1,0,'Dleft')+arm(1,0,'Dright')+`<rect x="${x-t}" y="${y-t}" width="${2*t}" height="${2*t}" fill="#0f1014"/>`;
  // Face buttons in the chosen style
  const [fx,fy,g,fr]=d.face;
  s+=[['Y',0,-g],['X',-g,0],['B',g,0],['A',0,g]].map(([k,dx,dy])=>{const cx=fx+dx,cy=fy+dy;
    let glyph; if(style==='ps'){const col=lit(k)?'#0B0F17':{A:'#8FB4FF',B:'#FF6B6B',X:'#F59BD5',Y:'#4DD6B5'}[k];glyph=psGlyph(k,cx,cy,fr*0.48,col).replace(/stroke-width="2.2"/g,`stroke-width="${1.5*sw}"`);}
    else {const label=style==='switch'?{A:'B',B:'A',X:'Y',Y:'X'}[k]:k;const col=lit(k)?'#0B0F17':style==='switch'?'#E6E8EC':{A:'#6CC24A',B:'#F0503C',X:'#3E9BFF',Y:'#F5C518'}[k];glyph=`<text x="${cx}" y="${cy+fr*0.05}" fill="${col}" font-family="I" font-weight="700" font-size="${fr*1.05}" text-anchor="middle" dominant-baseline="central">${label}</text>`;}
    return `<circle cx="${cx}" cy="${cy}" r="${fr}" ${L(k)} stroke-width="${1.2*sw}"/>${glyph}`}).join('');
  const pill=([px,py],k)=>`<rect x="${px-11*sw*1.2}" y="${py-5*sw*1.2}" width="${22*sw*1.2}" height="${10*sw*1.2}" rx="${5*sw*1.2}" ${L(k)} stroke-width="${1.1*sw}"/>`;
  s+=pill(d.select,'SELECT')+pill(d.start,'START');
  return s+'</svg>';
}

// A TV remote with only the buttons every remote has doing anything: the D-pad ring, OK, Back (and Home, which is Android's).
// Proportions from the Google TV remote; the other buttons are drawn quieter, because Inlay never uses them.
function remote2Svg(w, opt){
  opt=opt||{}; const line='#5a5e6a', detail='#2c2e36', ACC=opt.acc||'#72D6E8', on=opt.on||{};
  const W=150, H=560, sw=W/w, lit=k=>on[k]>0, L=k=>lit(k)?`fill="${ACC}" fill-opacity=".92" stroke="${ACC}" filter="url(#grm)"`:`fill="#0f1014" stroke="${line}"`, ink=k=>lit(k)?'#0B0F17':'#E6E8EC';
  let s=`<svg width="${w}" height="${w*H/W}" viewBox="0 0 ${W} ${H}" style="overflow:visible"><defs><filter id="grm" x="-40%" y="-40%" width="180%" height="180%"><feGaussianBlur stdDeviation="${4*sw}" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>`;
  s+=`<rect x="5" y="5" width="${W-10}" height="${H-10}" rx="${(W-10)/2}" fill="#101116" stroke="${line}" stroke-width="${1.8*sw}"/>`;
  // the ring: four arcs, each lights on its own; OK in the middle
  const cx=W/2, cy=82, R1=56, R0=27, gap=0.07;
  const arc=(a0,a1,k)=>{const p=(r,a)=>[cx+r*Math.cos(a),cy+r*Math.sin(a)];const A=p(R1,a0+gap),B=p(R1,a1-gap),C=p(R0+5,a1-gap),Dd=p(R0+5,a0+gap);
    return `<path d="M${A} A${R1},${R1} 0 0 1 ${B} L${C} A${R0+5},${R0+5} 0 0 0 ${Dd} Z" ${L(k)} stroke-width="${1.3*sw}" stroke-linejoin="round"/>`};
  const q=Math.PI/4;
  s+=arc(-3*q,-q,'Dup')+arc(-q,q,'Dright')+arc(q,3*q,'Ddown')+arc(3*q,5*q,'Dleft');
  // small arrow marks on the ring so it reads as a D-pad
  s+=[[0,-1],[1,0],[0,1],[-1,0]].map(([dx,dy])=>{const mx=cx+dx*(R1+R0+5)/2, my=cy+dy*(R1+R0+5)/2, k={'0,-1':'Dup','1,0':'Dright','0,1':'Ddown','-1,0':'Dleft'}[dx+','+dy];
    const a=7; return `<path d="M${mx+dx*a},${my+dy*a} L${mx-dx*a+dy*a},${my-dy*a+dx*a} L${mx-dx*a-dy*a},${my-dy*a-dx*a} Z" fill="${lit(k)?'#0B0F17':'#8d919c'}"/>`}).join('');
  s+=`<circle cx="${cx}" cy="${cy}" r="${R0}" ${L('OK')} stroke-width="${1.4*sw}"/><text x="${cx}" y="${cy+1}" fill="${ink('OK')}" font-family="I" font-weight="700" font-size="15" text-anchor="middle" dominant-baseline="central">OK</text>`;
  // Back and Home
  const bx=46, hx=104, by=176, br=19;
  s+=`<circle cx="${bx}" cy="${by}" r="${br}" ${L('BACK')} stroke-width="${1.4*sw}"/>`+backIcon(bx,by,9,ink('BACK'));
  s+=`<circle cx="${hx}" cy="${by}" r="${br}" fill="#0f1014" stroke="${line}" stroke-width="${1.4*sw}"/>`+homeIcon(hx,by,8,'#8d919c');
  // the rest, quieter: mic, volume, two app buttons, power
  s+=`<circle cx="${cx}" cy="236" r="16" fill="#0d0e12" stroke="${detail}" stroke-width="${1.2*sw}"/><rect x="${cx-3.5}" y="228" width="7" height="11" rx="3.5" fill="none" stroke="#3a3c45" stroke-width="1.6"/><path d="M${cx-7},237 a7,7 0 0 0 14,0" fill="none" stroke="#3a3c45" stroke-width="1.6"/>`;
  s+=`<rect x="${cx-17}" y="276" width="34" height="88" rx="17" fill="#0d0e12" stroke="${detail}" stroke-width="${1.2*sw}"/><path d="M${cx-6},298 h12 M${cx},292 v12 M${cx-6},342 h12" stroke="#3a3c45" stroke-width="2" stroke-linecap="round"/>`;
  s+=`<circle cx="46" cy="404" r="15" fill="#0d0e12" stroke="${detail}" stroke-width="${1.2*sw}"/><circle cx="104" cy="404" r="15" fill="#0d0e12" stroke="${detail}" stroke-width="${1.2*sw}"/>`;
  s+=`<circle cx="${cx}" cy="470" r="12" fill="#0d0e12" stroke="${detail}" stroke-width="${1.2*sw}"/><path d="M${cx},464 v6 M${cx-5},467 a6.5,6.5 0 1 0 10,0" fill="none" stroke="#3a3c45" stroke-width="1.6" stroke-linecap="round"/>`;
  return s+'</svg>';
}
// Android's own symbols: back is the arrow pointing left, home the house
function backIcon(x,y,r,c){return `<path d="M${x+r*0.9},${y} H${x-r*0.8} M${x-r*0.15},${y-r*0.7} L${x-r*0.85},${y} L${x-r*0.15},${y+r*0.7}" fill="none" stroke="${c}" stroke-width="${r*0.26}" stroke-linecap="round" stroke-linejoin="round"/>`}
function homeIcon(x,y,r,c){return `<path d="M${x-r},${y-r*0.05} L${x},${y-r*0.95} L${x+r},${y-r*0.05} M${x-r*0.72},${y-r*0.3} V${y+r*0.85} H${x+r*0.72} V${y-r*0.3}" fill="none" stroke="${c}" stroke-width="${r*0.24}" stroke-linecap="round" stroke-linejoin="round"/>`}
function dpadIcon(x,y,r,c){return `<circle cx="${x}" cy="${y}" r="${r}" fill="none" stroke="${c}" stroke-width="${r*0.16}"/><circle cx="${x}" cy="${y}" r="${r*0.36}" fill="none" stroke="${c}" stroke-width="${r*0.14}"/>`+[[0,-1],[1,0],[0,1],[-1,0]].map(([dx,dy])=>{const mx=x+dx*r*0.68,my=y+dy*r*0.68,a=r*0.16;return `<path d="M${mx+dx*a},${my+dy*a} L${mx-dx*a+dy*a},${my-dy*a+dx*a} L${mx-dx*a-dy*a},${my-dy*a-dx*a} Z" fill="${c}"/>`}).join('')}
// Remote badges, same round dark badge as the controller ones
function remoteBadge(k,s){s=s||44;const x=s/2,y=s/2,c='#F2F3F5';const g={OK:`<text x="${x}" y="${y+1}" fill="${c}" font-family="I" font-weight="700" font-size="${s*.32}" text-anchor="middle" dominant-baseline="central">OK</text>`,
  BACK:backIcon(x,y,s*0.24,c), DPAD:dpadIcon(x,y,s*0.3,c)}[k];
  return `<svg width="${s}" height="${s}" style="flex:none"><circle cx="${x}" cy="${y}" r="${s/2-1}" fill="#0B0B0E" stroke="#ffffff55" stroke-width="1.5"/>${g}</svg>`;}
