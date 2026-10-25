// Measured check: no caption, screen, controller, prompt or end-card box may overlap or leave the frame, in any frame.
const { chromium } = require('playwright');
(async()=>{const b=await chromium.launch(); let bad=0;
for (const page of ['s4-wide.html','s4-tall.html']){ const [w,h]=page.includes('tall')?[1080,1920]:[1920,1080];
  const p=await b.newPage({viewport:{width:w,height:h}}); await p.goto('file://'+process.cwd()+'/'+page); await p.evaluate(()=>document.fonts.ready);
  const dur=await p.evaluate(()=>DUR); let minGap=1e9;
  for (let t=0;t<dur;t+=0.5){ const r=await p.evaluate(({t,w,h})=>{R(t); const st=document.getElementById('stage'); const kids=[...st.children];
      const boxes=kids.map(k=>{const r=k.getBoundingClientRect(); const op=parseFloat(getComputedStyle(k).opacity); return {x:r.left,y:r.top,r:r.right,b:r.bottom,op,w:r.width};}).filter(b=>b.op>0.05&&b.w>0);
      const out=[]; let mg=1e9;
      for (const bx of boxes){ if (bx.x<0||bx.y<0||bx.r>w||bx.b>h) out.push('offscreen '+JSON.stringify(bx)); }
      for (let i=0;i<boxes.length;i++) for (let j=i+1;j<boxes.length;j++){ const a=boxes[i],c=boxes[j]; const gx=Math.max(a.x-c.r,c.x-a.r), gy=Math.max(a.y-c.b,c.y-a.b); const g=Math.max(gx,gy); if (g<4) out.push(`overlap/gap ${g.toFixed(1)}px between ${i} and ${j}`); mg=Math.min(mg,g); }
      // text clipping: any element whose scrollWidth exceeds its clientWidth
      const clipped=[...st.querySelectorAll('div')].filter(d=>d.scrollWidth>d.clientWidth+1&&getComputedStyle(d).overflow!=='hidden'&&getComputedStyle(d).whiteSpace!=='pre').length;
      return {out,mg,clipped,n:boxes.length};}, {t,w,h});
    minGap=Math.min(minGap,r.mg); if (r.out.length){ bad++; console.log(page,'t='+t.toFixed(1),r.out.join('; ')); } }
  console.log(page,'checked',Math.ceil(dur/0.5),'frames; smallest gap between elements',minGap===1e9?'n/a':minGap.toFixed(1)+'px');
  await p.close(); }
await b.close(); console.log(bad?'PROBLEMS: '+bad:'all clear');})();
