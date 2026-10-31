// node shoot-anim.js page.html outDir seconds [fps] — screenshots window.at(t) for every frame of a loop
const {chromium}=require('playwright'),path=require('path'),fs=require('fs');
(async()=>{const [pg,out,sec,fps]=process.argv.slice(2); const F=+(fps||30); fs.mkdirSync(out,{recursive:true});
const b=await chromium.launch(); const p=await b.newPage({viewport:{width:1600,height:900}});
await p.goto('file://'+path.resolve(pg)); await p.waitForFunction(()=>document.title==='done');
const n=Math.round(+sec*F); for(let i=0;i<n;i++){ await p.evaluate(t=>window.at(t),i/F); await p.screenshot({path:path.join(out,`f${String(i).padStart(4,'0')}.png`)}); }
await b.close(); console.log(n);})();
