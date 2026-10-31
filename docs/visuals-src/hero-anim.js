// node hero-anim.js <videoDir> <outDir> [scale]  — the animated hero: every recorded keyboard frame from VisualsVideo.sHeroTag,
// set into launch.html (v=g, the tagline faint ahead of what's typed). Writes f0000.png … at 30 fps.
const {chromium}=require('playwright'),path=require('path'),fs=require('fs');
(async()=>{const [vdir,out,scale,mode]=process.argv.slice(2); fs.mkdirSync(out,{recursive:true});
const frames=JSON.parse(fs.readFileSync(path.join(vdir,'frames.json'),'utf8'));
const b=await chromium.launch(); const p=await b.newPage({viewport:{width:1600,height:900},deviceScaleFactor:+(scale||1)});
await p.goto('file://'+path.resolve('launch.html')+'?id=hero&v=g&ghost=line&st=early&grain=0'); await p.waitForFunction(()=>document.title==='done');
let changed=0; const full=frames.findIndex(f=>f.before==='Type with your controller ');
for(let i=0;i<frames.length;i++){ if(i>0&&frames[i].before!==frames[i-1].before) changed=i;
  // the logo's cursor: solid 400 ms after a change, then 530 ms on / 530 ms off (Android's blink)
  const since=(i-changed)*1000/30, curOn=since<400||Math.floor((since-400)/530)%2===1; const kb='file://'+path.resolve(vdir,`kb-${String(i).padStart(4,'0')}.png`);
  await p.evaluate(([t,k,m,c])=>window.setFrame(t,k,m,c),[frames[i].before,kb,(full>=0&&i>full)?'none':(mode||'line'),curOn]);
  await p.screenshot({path:path.join(out,`f${String(i).padStart(4,'0')}.png`),clip:{x:0,y:0,width:1600,height:900}}); }
await b.close(); console.log(frames.length+' frames');})();
