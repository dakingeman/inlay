// Makes the hero device pictures from app/src/main/assets/devices: lighter tones, shoulder keys removed, fitted viewBox, A (or OK) lit. Run: node hero-devices.js
// Makes hero-only device pictures: lighter tones, shoulder keys removed, viewBox fitted to the drawing
const {chromium}=require('playwright'),fs=require('fs');
const A=require('path').join(__dirname,'../../app/src/main/assets/devices')+'/';   // the app's device kit, wherever the project lives
const TONE={'#1B1D23':'#2C303B','#121318':'#1D2028','#4E525C':'#8C93A3','#0A0B0E':'#14161C','#666A75':'#A3AAB8','#D5D8DE':'#F2F3F5','#07080a':'#0C0D11','#0D0E12':'#101218'};
(async()=>{const b=await chromium.launch();const p=await b.newPage();
for(const [n,lit] of [['wide-ps',['A']],['controller-ps',['A']],['vertical-ps',['A']],['vertical-sticks-ps',['A']],['wide-switch',['A']],['controller-switch',['A']],['vertical-switch',['A']],['vertical-sticks-switch',['A']]]){
  let s=fs.readFileSync(A+n+'.svg','utf8');for(const[k,v] of Object.entries(TONE)) s=s.split(k).join(v).split(k.toUpperCase()).join(v);
  s=s.replace('stop-opacity=".16"','stop-opacity=".28"');
  const j=JSON.parse(fs.readFileSync(A+n+'.json','utf8'));
  const css=lit.map(k=>j[k]).join('\n').split('ACC').join('#72D6E8');
  if(css) s=s.replace('<defs>','<style>'+css+'</style><defs>');
  const keys=['LB','RB','LT','RT'].map(k=>j._geo&&j._geo[k]).filter(Boolean);
  await p.setContent('<html><body style="margin:0">'+s.replace(/width="[0-9.]+" height="[0-9.]+"/,'width="1300"')+'</body></html>');
  const out=await p.evaluate(keys=>{const svg=document.querySelector('svg');
    const inKey=(bb)=>{const cx=bb.x+bb.width/2,cy=bb.y+bb.height/2;return keys.some(k=>cx>=k.x-4&&cx<=k.x+k.w+4&&cy>=k.y-4&&cy<=k.y+k.h+4&&bb.width<=k.w+30&&bb.height<=k.h+30);};
    [...svg.querySelectorAll('path,rect,circle,ellipse,text,g>use,line,polygon')].forEach(e=>{if(e.closest('defs,mask,clipPath'))return;let bb;try{bb=e.getBBox()}catch(x){return}
      if(e.tagName==='rect'&&bb.width>=10000)return; if(inKey(bb)) e.remove();});
    // fit the viewBox to what is left (without the floor shadow)
    const fl=svg.querySelector('ellipse[fill="url(#floor)"]');let x0=1e9,y0=1e9,x1=-1e9,y1=-1e9;
    const M=svg.getScreenCTM().inverse();const pt=(x,y)=>{const q=svg.createSVGPoint();q.x=x;q.y=y;return q.matrixTransform(M);};
    [...svg.children].forEach(e=>{if(['defs','style','mask','clipPath'].includes(e.tagName)||e===fl)return;if(getComputedStyle(e).display==='none'||e.getAttribute('display')==='none')return;
      const r=e.getBoundingClientRect();if(!r.width||r.width>3000)return;const a=pt(r.left,r.top),c=pt(r.right,r.bottom);
      x0=Math.min(x0,a.x);y0=Math.min(y0,a.y);x1=Math.max(x1,c.x);y1=Math.max(y1,c.y);});
    const pad=4;svg.setAttribute('viewBox',`${x0-pad} ${y0-pad} ${x1-x0+2*pad} ${y1-y0+2*pad}`);svg.removeAttribute('width');svg.removeAttribute('height');
    if(fl) fl.remove();
    return {svg:svg.outerHTML,box:[x0,y0,x1-x0,y1-y0]};},keys);
  const name='hero-parts/c-'+n+(lit.length?'-lit':'')+'.svg';fs.writeFileSync(require('path').join(__dirname,name),out.svg.replace('<svg','<svg xmlns="http://www.w3.org/2000/svg"'.replace(/^<svg/,'<svg')).replace(/xmlns="http:\/\/www.w3.org\/2000\/svg" xmlns="http:\/\/www.w3.org\/2000\/svg"/,'xmlns="http://www.w3.org/2000/svg"'));
  console.log(name,out.box.map(Math.round));
  // geometry of the screen inside the wide handheld
}
await b.close();})();
