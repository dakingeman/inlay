const {chromium}=require('playwright'),path=require('path');
(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1600,height:900}});
for(let c=1;c<=8;c++){await p.goto('file://'+path.resolve('hero-ideas3.html')+'?c='+c);await p.waitForFunction(()=>document.title==='done',{timeout:15000});await p.waitForTimeout(200);
await p.screenshot({path:`out/l12-${c}.png`});}
await b.close();})();
