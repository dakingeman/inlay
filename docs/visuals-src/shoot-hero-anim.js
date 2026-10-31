// node shoot-hero-anim.js <videoDir> <outDir> [scale] — the README hero animation: every frame VisualsVideo.sHeroAnim
// recorded, set into the locked hero (hero-final.html). Cursor: Android's blink (solid 400 ms after a change, then 530 ms
// on and off). Writes f0000.png … at 30 fps.
const {chromium}=require('playwright'),path=require('path'),fs=require('fs');
(async()=>{const [vdir,out,scale]=process.argv.slice(2); fs.mkdirSync(out,{recursive:true});
const frames=JSON.parse(fs.readFileSync(path.join(vdir,'frames.json'),'utf8'));
const b=await chromium.launch(); const p=await b.newPage({viewport:{width:1600,height:900},deviceScaleFactor:+(scale||1)});
await p.goto('file://'+path.resolve('hero-final.html')); await p.waitForFunction(()=>document.title==='done');
let changed=0; const held=new Set(); let flashKey=null, flashLeft=0; const FLASH=6; const taken=frames.findIndex(f=>/controller/.test(f.before));
for(let i=0;i<frames.length;i++){ if(i>0&&frames[i].before!==frames[i-1].before) changed=i;
  const since=(i-changed)*1000/30, curOn=since<400||Math.floor((since-400)/530)%2===1;
  // only the logged buttons (in:/down:/up:) and the R3 take; frame 0 is where the loop also ends, so it starts quiet
  for(const e of (i===0?'':(frames[i].ev||'')).split('+').filter(e=>e.includes(':')||e==='RS')){ const [k,v]=e.includes(':')?e.split(':'):['in',e];
    if(k==='in'){ flashKey=v; flashLeft=FLASH; } else if(k==='down') held.add(v); else if(k==='up') held.delete(v); }
  const lit=held.size?[...held][0]:(flashLeft>0?flashKey:'none'); if(flashLeft>0) flashLeft--;
  const litName=['A','X','RS','Dup','Ddown','Dleft','Dright'].includes(lit)?lit:'none';
  const kb='file://'+path.resolve(vdir,`kb-${String(i).padStart(4,'0')}.png`);
  await p.evaluate(([t,k,c,g,l])=>window.setFrame(t,k,c,g,l),[frames[i].before,kb,curOn,i<taken,process.env.LIT==='1'?litName:null]);
  await p.screenshot({path:path.join(out,`f${String(i).padStart(4,'0')}.png`)}); }
await b.close(); console.log(frames.length+' frames');})();
