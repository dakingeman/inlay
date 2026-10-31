const {chromium}=require('playwright'),path=require('path');
(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1600,height:900}});
for(const [n,q] of [['none','g=0.0001'],['fine','']]){await p.goto('file://'+path.resolve('hero-final.html')+'?'+q);await p.waitForFunction(()=>document.title==='done');await p.waitForTimeout(200);await p.screenshot({path:`out/gtest-${n}.png`});
 if(n==='fine') console.log(await p.evaluate(()=>[...document.getElementById('c').children].map(e=>e.tagName+' z='+getComputedStyle(e).zIndex+' pos='+getComputedStyle(e).position+' '+(e.className&&e.className.baseVal!==undefined?e.className.baseVal:e.className)).join('\n')));}
await b.close();})();
