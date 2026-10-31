// node launch-shoot.js out.png W H query  — renders launch.html and prints its measured alignment checks
const {chromium}=require('playwright'),path=require('path');
(async()=>{const [out,W,H,q]=process.argv.slice(2);const b=await chromium.launch();
const p=await b.newPage({viewport:{width:+W,height:+H},deviceScaleFactor:+(process.env.SCALE||1)});
await p.goto('file://'+path.resolve('launch.html')+'?'+q);await p.waitForFunction(()=>document.title==='done');await p.waitForTimeout(250);
await p.screenshot({path:out,clip:{x:0,y:0,width:+W,height:+H}});
const r=await p.evaluate(()=>window.REPORT.checks.map(k=>({what:k.what,a:+k.a.toFixed(1),b:k.b!==undefined?+k.b.toFixed(1):undefined,min:k.min,max:k.max,ok:k.ok})));
const bad=r.filter(k=>!k.ok); console.log(`${q}: ${r.length-bad.length}/${r.length} alignment checks pass`); bad.forEach(k=>console.log('   FAIL',JSON.stringify(k)));
await b.close();})();
