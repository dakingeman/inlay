// Inlay device kit: one call per device, all following the same rules (see brand-kit notes)
window.KIT_FLOATING=true;
// left/right: over the real shoulders. top: the highest point of the controller's outline under the keys (measured)
const KIT_ANCH={xbox:{left:236,right:478,top:77.9},ps:{left:171,right:486,top:68.9},switch:{left:180,right:477,top:56.8}};
// Floating shoulder keys, one spec on every device (28 Oct 2026). Sizes are in dp as seen on the Device tab,
// turned into each picture's units with UPD (picture units per dp there), so every device shows the same keys.
const SHOULDER={bumperW:36, bumperH:11, triggerW:28, triggerH:14, triggerR:5, gap:8, lift:10, label:7};
const PICTURE_DP=165;   // the Device tab shows every picture this tall
// anchors.top is where the body's outline is under each key (checked by measuring: within 0.2 dp on every device)
const BODY_DROP={};
// the top of the trigger keys, in picture units: the picture starts just above, with room for a lit ring
function shoulderTop(anchors, style, u){
  const S=SHOULDER, top=anchors.top+(BODY_DROP[anchors.kind==='ctrl'?style:anchors.kind]||0);
  return top-(S.lift+S.bumperH+S.gap+S.triggerH)*u;
}
const RING_ROOM=6;   // dp round the picture's edge: room for a lit or held ring (up to 4.5 dp) and a little air
// picture units per dp: the picture's full height (body + keys + ring room + bottom air) is PICTURE_DP
function unitsPerDp(anchors, style, bodyBottom){
  let u=3; for(let i=0;i<30;i++){ const top=shoulderTop(anchors,style,u)-RING_ROOM*u; u=(bodyBottom+20-top)/PICTURE_DP; } return u;
}
function shoulderKeysKit(anchors, style, on, u){
  const S=SHOULDER, ACC=TOKENS.brandAccent, line='#5a5e6a', v=k=>on[k]||0;
  const lit=k=>v(k)>0?`fill="${ACC}" fill-opacity=".9" stroke="${ACC}" filter="url(#hg2)"`:`fill="#0f1014" stroke="${line}"`;
  const ink=k=>v(k)>0.5?TOKENS.inkDark:'#C9CCD3';
  const N={xbox:['LT','LB','RT','RB'],ps:['L2','L1','R2','R1'],switch:['ZL','L','ZR','R']}[style];
  const txt=(x,y,t,k)=>`<text x="${x}" y="${y}" fill="${ink(k)}" font-family="I" font-weight="700" font-size="${S.label*u}" text-anchor="middle" dominant-baseline="central">${t}</text>`;
  const bh=S.bumperH*u, th=S.triggerH*u, gap=S.gap*u, tr=S.triggerR*u;
  const top=anchors.top+(BODY_DROP[anchors.kind==='ctrl'?style:anchors.kind]||0);
  const by=top-S.lift*u-bh, ty=by-gap-th;
  let s='';
  if(anchors.kind==='vertical'){
    // both keys hug the top corners, inside the body's width; the outer corner follows the body's corner
    const room=(anchors.R-anchors.L)/2-14, bw=Math.min(S.bumperW*u,room), tw=Math.min(S.triggerW*u,room*0.78);
    [0,1].forEach(i=>{const kB=i?'RB':'LB', kT=i?'RT':'LT', dir=i?-1:1, xo=i?anchors.R:anchors.L, sw=i?0:1;
      const key=(y,h,w,rc)=>{const r=h/2, c=Math.min(rc,h); return `M${xo},${y+h} V${y+c} A${c},${c} 0 0 ${sw} ${xo+dir*c},${y} H${xo+dir*(w-r)} A${r},${r} 0 0 ${sw} ${xo+dir*(w-r)},${y+h} Z`;};
      const rc=Math.max(anchors.rc,tr);
      s+=`<path d="${key(by,bh,bw,rc)}" ${lit(kB)} stroke-width="2.2" stroke-linejoin="round"/>`+txt(xo+dir*bw/2,by+bh/2+1,N[i?3:1],kB);
      s+=`<path d="${key(ty,th,tw,rc)}" ${lit(kT)} stroke-width="2.2" stroke-linejoin="round"/>`+txt(xo+dir*tw/2,ty+th/2+1,N[i?2:0],kT);
    });
    return s;
  }
  const bw=S.bumperW*u, tw=S.triggerW*u;
  [0,1].forEach(i=>{const cx=i?anchors.right:anchors.left, kT=i?'RT':'LT', kB=i?'RB':'LB';
    s+=`<rect x="${cx-bw/2}" y="${by}" width="${bw}" height="${bh}" rx="${bh/2}" ${lit(kB)} stroke-width="2.2"/>`+txt(cx,by+bh/2+1,N[i?3:1],kB);
    s+=`<rect x="${cx-tw/2}" y="${ty}" width="${tw}" height="${th}" rx="${tr}" ${lit(kT)} stroke-width="2.2"/>`+txt(cx,ty+th/2+1,N[i?2:0],kT);
  });
  return s;
}
const _strip=s=>s.replace(/^<svg[^>]*>/,'').replace(/<\/svg>\s*$/,'');
// One outline per controller (body + bumper humps merged and smoothed by merge-outlines.py)
function _withMerged(style,fn){
  const D=style==='xbox'?CTRL:CTRL2[style], ks=style==='ps'?['l1','r1']:['lb','rb'], keep={body:D.body}; ks.forEach(x=>keep[x]=D[x]);
  D.body=MERGED[style]; ks.forEach(x=>D[x]='M0,0');
  try{ return fn().replace(/<path d="M0,0"[^>]*\/>/g,''); } finally{ Object.assign(D,keep); }
}
function kitController(style,on,W){
  on=on||{}; const k=W/657, a=Object.assign({kind:'ctrl'},KIT_ANCH[style]), u=unitsPerDp(a,style,423), pad=-(shoulderTop(a,style,u)-RING_ROOM*u);
  const on2=Object.assign({},on,{LB:0,RB:0,LT:0,RT:0}); let svg;
  if(style==='xbox'){ const keep={lt:CTRL.lt,rt:CTRL.rt}; CTRL.lt=CTRL.rt='M0,0'; svg=_withMerged('xbox',()=>controller(W,on2,style)); Object.assign(CTRL,keep);
    svg=svg.replace(/<text[^>]*>(LT|RT|LB|RB)<\/text>/g,''); [CTRL.lb,CTRL.rb].forEach(d=>{svg=svg.replace(`d="${d}" fill="#0f1014"`,`d="${d}" fill="#101116"`);}); }
  else svg=_withMerged(style,()=>controller(W,on2,style));
  const inner=floorShadow(328,423,520)+polish(_strip(svg)+shoulderKeysKit(a,style,on,u),657/W);
  return `<svg width="${W}" height="${(423+pad+20)*k}" viewBox="0 ${-pad} 657 ${423+pad+20}" style="overflow:visible;display:block">${inner}</svg>`;
}
function kitHandheld(kind,style,on,W){
  on=on||{}; const H=handheld2(kind,W,{on,style}); const u=unitsPerDp(H.anchors,style,H.VH), pad=-(shoulderTop(H.anchors,style,u)-RING_ROOM*u), k=W/H.VW;
  const bb=kind==='vertical'?[H.VW/2,H.VH-(H.VH-547*0.85)/2,313]:[H.VW/2,H.VH-(H.VH-237)/2,582];
  const inner=floorShadow(...bb)+polish(H.svg+shoulderKeysKit(H.anchors,style,on,u),H.VW/W);
  // the vertical handheld's keys sit flush with the body's sides: leave ring room beside them too
  const side=Math.max(0,RING_ROOM*u-H.anchors.L,H.anchors.R+RING_ROOM*u-H.VW), vw=H.VW+2*side;
  return `<svg width="${W*vw/H.VW}" height="${(H.VH+pad+20)*k}" viewBox="${-side} ${-pad} ${vw} ${H.VH+pad+20}" style="overflow:visible;display:block">${inner}</svg>`;
}
function kitRemote(on,w){ let s=remote2Svg(w,{on:on||{}}); return s.replace(/(<svg[^>]*>)([\s\S]*)<\/svg>/,(m,a,b)=>a+floorShadow(75,555,150)+polish(b,150/w)+'</svg>'); }
