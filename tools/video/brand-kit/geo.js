// Measures every button in the device pictures (in picture coordinates) for the held ring and the sticks.
const {chromium}=require('playwright'); const fs=require('fs'); const path=require('path');
(async()=>{const dir=process.argv[2]; const b=await chromium.launch(); const p=await b.newPage();
for(const f of fs.readdirSync(dir).filter(f=>f.endsWith('.svg'))){
  const svg=fs.readFileSync(path.join(dir,f),'utf8'); const jf=path.join(dir,f.replace('.svg','.json')); const css=JSON.parse(fs.readFileSync(jf,'utf8'));
  await p.setContent('<html><body style="margin:0">'+svg+'</body></html>');
  const geo=await p.evaluate((css)=>{const s=document.querySelector('svg'); const out={};
    const toRoot=(el)=>{const bb=el.getBBox(), M=s.getScreenCTM().inverse().multiply(el.getScreenCTM());
      const P=(x,y)=>{const q=s.createSVGPoint();q.x=x;q.y=y;return q.matrixTransform(M);}; const a=P(bb.x,bb.y), c=P(bb.x+bb.width,bb.y+bb.height);
      return {x:a.x,y:a.y,w:c.x-a.x,h:c.y-a.y,k:Math.hypot(M.a,M.b),M};};
    for(const key of Object.keys(css)){ if(key.startsWith('_')) continue;
      const rules=css[key].split('\n'); const m=rules.map(r=>r.match(/^\.(\S+?)\{(.*)\}$/)).filter(Boolean);
      let litCls=(m.find(x=>/^fill:ACC/.test(x[2]))||m.find(x=>/fill:ACC/.test(x[2])))?.[1]; if(!litCls) continue;
      const el=s.querySelector('.'+litCls); if(!el) continue;
      const wasHidden=el.getAttribute('display')==='none'; if(wasHidden) el.setAttribute('display','inline');
      const g=toRoot(el); if(wasHidden) el.setAttribute('display','none');
      const e={x:+g.x.toFixed(2),y:+g.y.toFixed(2),w:+g.w.toFixed(2),h:+g.h.toFixed(2),tag:el.tagName};
      if(el.tagName==='rect') e.rx=+((+el.getAttribute('rx')||0)*g.k).toFixed(2);
      // Any other shape: its outline and where it sits, so the held ring can follow it exactly
      if(el.tagName==='path'){ e.d=el.getAttribute('d'); e.m=[g.M.a,g.M.b,g.M.c,g.M.d,g.M.e,g.M.f].map(v=>+v.toFixed(4)); }
      if(key==='LS'||key==='RS'){ const cx=+el.getAttribute('cx'), cy=+el.getAttribute('cy');
        const same=[...s.querySelectorAll('circle')].filter(c=>Math.abs(+c.getAttribute('cx')-cx)<.5&&Math.abs(+c.getAttribute('cy')-cy)<.5);
        const well=same.filter(c=>!c.getAttribute('class')&&c.getAttribute('fill')!=='#000').sort((a,b)=>b.getAttribute('r')-a.getAttribute('r'))[0];
        const halo=same.find(c=>c.getAttribute('fill')==='#000');
        const inner=same.filter(c=>c.getAttribute('class')&&c!==el&&!c.getAttribute('mask'))[0];
        e.stick={r:+(g.w/2).toFixed(2), inner: inner? +(inner.getAttribute('r')*g.k).toFixed(2):0, well: well? +(well.getAttribute('r')*g.k).toFixed(2):0, wellFill: well? well.getAttribute('fill'):null,
          halo: halo? +((+halo.getAttribute('r') + (+halo.getAttribute('stroke-width'))/2)*g.k).toFixed(2):0, hide:m.map(x=>x[1])};}
      out[key]=e;}
    return out;},css);
  css._geo=geo; fs.writeFileSync(jf,JSON.stringify(css));
  console.log(f, Object.keys(geo).length, JSON.stringify(geo.RS||geo.A||{}).slice(0,300));
}
await b.close();})();
