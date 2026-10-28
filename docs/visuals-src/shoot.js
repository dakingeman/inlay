// node shoot.js page.html out.png W H [query]  — renders one image once fonts are in
const {chromium}=require('playwright'),path=require('path');
(async()=>{const [page,out,W,H,q]=process.argv.slice(2);const b=await chromium.launch();const p=await b.newPage({viewport:{width:+W,height:+H},deviceScaleFactor:+(process.env.SCALE||1)});
await p.goto('file://'+path.resolve(page)+(q?'?'+q:''));await p.waitForFunction(()=>document.title==='done');await p.waitForTimeout(250);
await p.screenshot({path:out,clip:{x:0,y:0,width:+W,height:+H}});await b.close();})();
