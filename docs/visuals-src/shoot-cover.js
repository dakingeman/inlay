// node shoot-cover.js <id> <out.png> [w h scale]
const {chromium}=require('playwright'),path=require('path');
const [id,out,w=1280,h=640,sc=1]=process.argv.slice(2);
(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:+w,height:+h},deviceScaleFactor:+sc});
await p.goto('file://'+path.resolve('cover.html')+`?id=${id}&w=${w}&h=${h}${process.env.KEYS?"&keys="+process.env.KEYS:""}${process.env.SUB?"&sub="+encodeURIComponent(process.env.SUB):""}`);await p.waitForFunction(()=>document.title==='done');await p.waitForTimeout(300);
await p.screenshot({path:out,clip:{x:0,y:0,width:+w,height:+h}});await b.close();})();
