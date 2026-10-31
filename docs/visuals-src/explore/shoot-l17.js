const {chromium}=require('playwright'),path=require('path');
(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1600,height:900}});
for(let c=1;c<=5;c++){await p.goto('file://'+path.resolve('hero-3b.html')+'?c='+c);await p.waitForFunction(()=>document.title==='done',{timeout:15000});await p.waitForTimeout(200);
await p.screenshot({path:`out/l17-${c}.png`});}
await b.close();})();
