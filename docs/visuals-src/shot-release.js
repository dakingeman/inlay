const {chromium}=require('playwright'),path=require('path');
(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1600,height:1100}});
await p.goto('file://'+path.resolve('release.html')+'?id=swap-buttons');await p.waitForFunction(()=>document.title==='done');await p.waitForTimeout(300);
const c=await p.evaluate(()=>JSON.parse(JSON.stringify(window.CHECK)));console.log(JSON.stringify(c));
await p.screenshot({path:'release/inlay-1.2.1-swap-buttons.png',clip:{x:0,y:0,width:c.W,height:c.H}});await b.close();})();
