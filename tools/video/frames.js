const { chromium } = require('playwright'); const fs=require('fs');
const [,, page, fps, dir, only] = process.argv; fs.mkdirSync(dir,{recursive:true});
(async()=>{const b=await chromium.launch();const [w,h]=page.includes('tall')?[1080,1920]:[1920,1080];
const p=await b.newPage({viewport:{width:w,height:h}}); p.on('pageerror',e=>console.log('E',e.message));
await p.goto('file://'+process.cwd()+'/'+page); await p.evaluate(()=>document.fonts.ready); await p.waitForTimeout(400);
const dur=await p.evaluate(()=>DUR);
const ts= only? only.split(',').map(Number) : Array.from({length:Math.round(dur*fps)},(_, i)=>i/fps);
for (let i=0;i<ts.length;i++){ await p.evaluate(t=>R(t),ts[i]); await p.screenshot({path:`${dir}/${only?'t_'+ts[i]:String(i).padStart(5,'0')}.png`}); }
await b.close();})();
