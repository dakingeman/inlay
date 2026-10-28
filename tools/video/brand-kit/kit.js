// Inlay device kit: one call per device, all following the same rules (see brand-kit notes)
window.KIT_FLOATING=true;
const KIT_ANCH={xbox:{left:236,right:478,top:84,lift:24},ps:{left:171,right:486,top:52,lift:20},switch:{left:180,right:477,top:50,lift:20}};
const _strip=s=>s.replace(/^<svg[^>]*>/,'').replace(/<\/svg>\s*$/,'');
function kitController(style,on,W){
  on=on||{}; const pad=130, k=W/657, a=Object.assign({kind:'ctrl'},KIT_ANCH[style]);
  const on2=Object.assign({},on,{LB:0,RB:0,LT:0,RT:0}); let svg;
  if(style==='xbox'){ const keep={lt:CTRL.lt,rt:CTRL.rt}; CTRL.lt=CTRL.rt='M0,0'; svg=controller(W,on2,style); Object.assign(CTRL,keep);
    svg=svg.replace(/<text[^>]*>(LT|RT|LB|RB)<\/text>/g,''); [CTRL.lb,CTRL.rb].forEach(d=>{svg=svg.replace(`d="${d}" fill="#0f1014"`,`d="${d}" fill="#101116"`);}); }
  else svg=controller(W,on2,style);
  const inner=floorShadow(328,423,520)+polish(_strip(svg)+shoulderKeysA(a,style,on,1.25),657/W);
  return `<svg width="${W}" height="${(423+pad+20)*k}" viewBox="0 ${-pad} 657 ${423+pad+20}" style="overflow:visible;display:block">${inner}</svg>`;
}
function kitHandheld(kind,style,on,W){
  on=on||{}; const H=handheld2(kind,W,{on,style}); const pad=kind==='vertical'?160:120, k=W/H.VW, sc=kind==='vertical'?1.2:1;
  const bb=kind==='vertical'?[H.VW/2,H.VH-(H.VH-547*0.85)/2,313]:[H.VW/2,H.VH-(H.VH-237)/2,582];
  const inner=floorShadow(...bb)+polish(H.svg+shoulderKeysA(H.anchors,style,on,sc),H.VW/W);
  return `<svg width="${W}" height="${(H.VH+pad+20)*k}" viewBox="0 ${-pad} ${H.VW} ${H.VH+pad+20}" style="overflow:visible;display:block">${inner}</svg>`;
}
function kitRemote(on,w){ let s=remote2Svg(w,{on:on||{}}); return s.replace(/(<svg[^>]*>)([\s\S]*)<\/svg>/,(m,a,b)=>a+floorShadow(75,555,150)+polish(b,150/w)+'</svg>'); }
