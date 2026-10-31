// node logo-hero.js <outDir> <lh>  — hero round L8: the field and keyboard still (the "contr" moment), the logo typing
// itself above. Writes bg-on.png / bg-off.png (field cursor on/off, no logo) and logo.json (where the still lockup sits).
const {chromium}=require('playwright'),path=require('path'),fs=require('fs');
(async()=>{const [out,lh]=process.argv.slice(2); fs.mkdirSync(out,{recursive:true});
const b=await chromium.launch(); const p=await b.newPage({viewport:{width:1600,height:900}});
await p.goto('file://'+path.resolve('launch.html')+`?id=hero&v=g&ghost=word&st=offer&grain=0&lh=${lh}`); await p.waitForFunction(()=>document.title==='done');
await p.waitForTimeout(250);
await p.screenshot({path:path.join(out,'with-logo.png')});
const r=await p.evaluate(()=>{const l=[...document.querySelectorAll('img')].find(i=>i.src.includes('lockup')); const b=l.getBoundingClientRect(); l.style.visibility='hidden'; return {x:b.x,y:b.y,w:b.width,h:b.height};});
fs.writeFileSync(path.join(out,'logo.json'),JSON.stringify(r));
await p.screenshot({path:path.join(out,'bg-on.png')});
await p.evaluate(()=>document.querySelectorAll('.cur').forEach(c=>c.style.visibility='hidden'));
await p.screenshot({path:path.join(out,'bg-off.png')});
await b.close();})();
