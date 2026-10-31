const {chromium}=require('playwright'),path=require('path');
(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1600,height:900}});
const V=[['0-current','g=.06'],['1-fine','g=.07&gf=.95&gm=1&gb=screen'],['2-film','g=.11&gf=.7&gm=1&gb=screen'],['3-heavy','g=.17&gf=.55&gm=1&gb=screen']];
for(const [n,q] of V){await p.goto('file://'+path.resolve('hero-final.html')+'?'+q);await p.waitForFunction(()=>document.title==='done');await p.waitForTimeout(200);await p.screenshot({path:`out/grain-${n}.png`});}
await b.close();})();
