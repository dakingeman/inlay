const {chromium}=require('playwright'),path=require('path');
(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1600,height:900}});
for(const s of ['clear','kb','body','tint','deep']){await p.goto('file://'+path.resolve('hero-3b8.html')+'?c=1&s='+s);await p.waitForFunction(()=>document.title==='done');await p.waitForTimeout(150);
await p.screenshot({path:`out/l25-${s}.png`});}
await b.close();})();
