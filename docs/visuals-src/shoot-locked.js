// node shoot-locked.js — the locked README hero at double resolution: out/hero-locked@2x.png and docs/images/hero-locked.png
const {chromium}=require('playwright'),path=require('path'),fs=require('fs');
(async()=>{const b=await chromium.launch();const p=await b.newPage({viewport:{width:1600,height:900},deviceScaleFactor:2});
await p.goto('file://'+path.resolve('hero-final.html'));await p.waitForFunction(()=>document.title==='done');await p.waitForTimeout(300);
await p.screenshot({path:'out/hero-locked@2x.png'});fs.copyFileSync('out/hero-locked@2x.png','../images/hero-locked.png');await b.close();})();
