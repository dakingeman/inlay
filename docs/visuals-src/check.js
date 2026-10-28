// node check.js page.html W H query  — measured check: no two separate elements overlap or touch,
// nothing leaves the canvas (24 px margin), and every text has enough contrast. Prints the smallest gap.
const {chromium}=require('playwright'),path=require('path');
(async()=>{const [page,W,H,q]=process.argv.slice(2);const b=await chromium.launch();const p=await b.newPage({viewport:{width:+W,height:+H}});
await p.goto('file://'+path.resolve(page)+(q?'?'+q:''));await p.waitForFunction(()=>document.title==='done');
const r=await p.evaluate(([W,H])=>{
  const els=[...document.querySelectorAll('.m')].filter(e=>{const s=getComputedStyle(e);return s.display!=='none'&&s.visibility!=='hidden'&&+s.opacity>0;});
  // a drawing's box is its picture (transparent margins are trimmed in the files)
  const box=e=>{const r=e.getBoundingClientRect();return {l:r.left,t:r.top,r:r.right,b:r.bottom,e};};
  const B=els.map(box).filter(x=>x.r-x.l>0&&x.b-x.t>0);
  const out=[]; let min=1e9, minPair='';
  for(let i=0;i<B.length;i++){const a=B[i];
    if(a.l<24||a.t<24||a.r>W-24||a.b>H-24) out.push('edge: '+(a.e.className+' '+(a.e.textContent||a.e.src||'').slice(0,40)));
    for(let j=i+1;j<B.length;j++){const c=B[j]; if(a.e.contains(c.e)||c.e.contains(a.e)) continue;
      // things in the same row of a list or grid are measured between their boxes too
      const gap=Math.max(c.l-a.r,a.l-c.r,c.t-a.b,a.t-c.b);
      if(gap<min){min=gap;minPair=(a.e.textContent||a.e.src||a.e.className).slice(0,30)+' / '+(c.e.textContent||c.e.src||c.e.className).slice(0,30);}
      if(gap<8) out.push('close ('+gap.toFixed(1)+'): '+(a.e.textContent||a.e.src||a.e.className).slice(0,40)+' / '+(c.e.textContent||c.e.src||c.e.className).slice(0,40));}}
  // contrast of every text against the page or its card
  const lum=c=>{const m=c.match(/[\d.]+/g).map(Number);const f=v=>{v/=255;return v<=.03928?v/12.92:Math.pow((v+.055)/1.055,2.4)};return .2126*f(m[0])+.7152*f(m[1])+.0722*f(m[2]);};
  const bgOf=e=>{for(let x=e;x;x=x.parentElement){const s=getComputedStyle(x).backgroundColor;if(s&&!/rgba\(0, 0, 0, 0\)|transparent/.test(s))return s;}return 'rgb(11,11,14)';};
  let minC=99, minCt='';
  document.querySelectorAll('.h1,.body,.lbl,.label,.w,.title,.field span,.count').forEach(e=>{if(!e.textContent.trim())return;const s=getComputedStyle(e);const L1=lum(s.color),L2=lum(bgOf(e));const c=(Math.max(L1,L2)+.05)/(Math.min(L1,L2)+.05);if(c<minC){minC=c;minCt=e.textContent.slice(0,30);}});
  return {problems:out,min,minPair,minC,minCt,n:B.length};
},[+W,+H]);
console.log((q||page)+': '+r.n+' boxes, smallest gap '+r.min.toFixed(1)+' px ('+r.minPair+'), lowest contrast '+r.minC.toFixed(1)+':1 ('+r.minCt+')'+(r.problems.length?'\n  '+r.problems.join('\n  '):''));
await b.close();})();
