const {chromium}=require('playwright'),path=require('path');
(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1600,height:900},deviceScaleFactor:+(process.env.SCALE||1)});
for(const [q,o] of [['polish=0','out/final-before.png'],['polish=1','out/final-after.png']]){await p.goto('file://'+path.resolve('hero-final.html')+'?'+q);await p.waitForFunction(()=>document.title==='done');await p.waitForTimeout(150);await p.screenshot({path:o});}
await b.close();})();
