// node logo-keys.js <videoDir> <outDir> — hero round L9: the keyboard alone (no text box), every recorded frame, no logo
// (the logo animation is added afterwards). Writes k0000.png… at 30 fps and logo.json (where the still lockup sits).
const {chromium}=require('playwright'),path=require('path'),fs=require('fs');
(async()=>{const [vdir,out,lh]=process.argv.slice(2); fs.mkdirSync(out,{recursive:true});
const frames=JSON.parse(fs.readFileSync(path.join(vdir,'frames.json'),'utf8'));
const b=await chromium.launch(); const p=await b.newPage({viewport:{width:1600,height:900}});
await p.goto('file://'+path.resolve('launch.html')+'?id=hero&v=g&nofield=1&st=offer&grain=0&lh='+(lh||150)); await p.waitForFunction(()=>document.title==='done');
await p.waitForTimeout(250);
await p.screenshot({path:path.join(out,'with-logo.png')});
const r=await p.evaluate(()=>{const l=[...document.querySelectorAll('img')].find(i=>i.src.includes('lockup')); const b=l.getBoundingClientRect(); l.style.visibility='hidden'; return {x:b.x,y:b.y,w:b.width,h:b.height};});
fs.writeFileSync(path.join(out,'logo.json'),JSON.stringify(r));
for(let i=0;i<frames.length;i++){ const kb='file://'+path.resolve(vdir,`kb-${String(i).padStart(4,'0')}.png`);
  await p.evaluate(k=>new Promise(res=>{const im=document.querySelector('.kbimg'); im.onload=()=>res(); im.src=k;}),kb);
  await p.screenshot({path:path.join(out,`k${String(i).padStart(4,'0')}.png`),clip:{x:0,y:0,width:1600,height:900}}); }
await b.close(); console.log(frames.length);})();
