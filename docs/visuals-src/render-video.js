// node render-video.js <name> <plan.json> [--only=3,8]  — renders frames into out/<name>/p<NN>/ (then finish-video.py)
// A plan is a list of parts, each with its own backdrop variation:
//   {card:'intro'|'new'|'outro', ms}           a title or end card ('new' takes version and title: the update's name)
//   any part may carry title and sub: its on-screen words (otherwise the scene's defaults in video.html CAPS)
//   {packs:'list'|'carousel', step, ms}          the nine sound packs, one highlight landing every `step` seconds
//   {scene, from, to}                            a keyboard recording (VisualsVideo: kb-NNNN.png + pic-NNNN.png)
//   {screen, from, to}                           a whole-screen recording (VisualsVideo: scr-NNNN.png)
// from / to are frame numbers of the recording (30 fps). Recordings always play at the app's real speed.
// Faster than before: frames are drawn by several browser pages at once (PAGES, default 6), and each part keeps its own
// folder, so --only=3,8 redraws just those parts and keeps the rest from the last render.
// Also writes parts.json (frames per part) and events.json (what happens on which frame), which finish-video.py turns
// into the cue sheet and checks for pacing.
const {chromium}=require('playwright'),path=require('path'),fs=require('fs');
const REC=path.resolve(__dirname,'../../app/build/visuals/video');
const PACK_LEAD=.5;
(async()=>{
  const args=process.argv.slice(2), [name,planFile]=args.filter(a=>!a.startsWith('--'));
  const only=(args.find(a=>a.startsWith('--only='))||'').slice(7).split(',').filter(Boolean).map(Number);
  const plan=JSON.parse(fs.readFileSync(planFile)), grain=process.env.GRAIN!=='0', PAGES=+(process.env.PAGES||6);
  const out=path.resolve(__dirname,'out',name);
  if(!only.length) fs.rmSync(out,{recursive:true,force:true});
  fs.mkdirSync(out,{recursive:true});
  const b=await chromium.launch();
  const pages=await Promise.all([...Array(PAGES)].map(async()=>{ const p=await b.newPage({viewport:{width:1920,height:1080}});
    await p.goto('file://'+path.resolve(__dirname,'video.html')+(grain?'':'?grain=0')); await p.waitForFunction(()=>document.title==='done'); return p; }));
  const old=fs.existsSync(path.join(out,'parts.json'))?JSON.parse(fs.readFileSync(path.join(out,'parts.json'))):[];
  const oldEv=fs.existsSync(path.join(out,'events.json'))?JSON.parse(fs.readFileSync(path.join(out,'events.json'))):[];
  const log=[], events=[];
  for(const [index,part] of plan.entries()){
    const dir=path.join(out,'p'+String(index).padStart(2,'0'));
    // jobs: one per frame, each a function call on a page; evs: events on frame k of this part
    const jobs=[], evs=[];
    if(part.card){ const k=Math.round(part.ms*30/1000); for(let i=0;i<k;i++) jobs.push(['Card',[part.card,i/30,index,{version:part.version,title:part.title}]]); }
    else if(part.packs){ const k=Math.round(part.ms*30/1000), step=part.step||.9;
      for(let i=0;i<k;i++) jobs.push(['Packs',{part:'packs',index,style:part.packs,sec:i/30,step}]);
      for(let j=0;j<9;j++){ const f=Math.round((PACK_LEAD+j*step)*30); if(f<k) evs.push([f,'pack: '+['Soft','Thock','Tactile','Typewriter','Felt','Chime','Glass','Pop','Retro'][j]]); } }
    else {
      const id=part.scene||part.screen, rd=path.join(REC,id);
      const fr=JSON.parse(fs.readFileSync(path.join(rd,'frames.json')));
      const from=part.from||0, to=Math.min(part.to||fr.length,fr.length);
      const changes=[]; for(let i=from+1;i<to;i++) if(fr[i].dev!==fr[i-1].dev) changes.push(i);
      const boxes=part.scene?JSON.parse(fs.readFileSync(path.join(rd,'picbox.json'))):{};   // from pic-boxes.py
      for(let i=from;i<to;i++){ const f=fr[i], k=i-from;
        if(f.ev) evs.push([k,f.ev]);
        if(part.screen&&i>from&&f.tag!==undefined){ const h=fr[i-1]; if(f.tag!==h.tag&&f.tag) evs.push([k,'name: '+f.tag]); if(f.typed.length>h.typed.length) evs.push([k,'key']); }
        const png=s=>'file://'+path.join(rd,s+'-'+String(i).padStart(4,'0')+'.png');
        if(part.screen){ let a=0; if(f.tag) while(i-a-1>=0&&fr[i-a-1].tag===f.tag) a++;
          jobs.push(['Screen',{part:id,index,cap:part.cap||f.cap,title:part.title,sub:part.sub,tag:f.tag,tagAge:a,scr:png('scr')}]); continue; }
        // fade-through when the device changes: 5 frames out, 10 in (the app's own showcase timing)
        let picAlpha=1,picScale=1;
        for(const c of changes){ if(i>=c-5&&i<c){ const t=(c-i)/5; picAlpha=t; picScale=.94+.06*t; } else if(i>=c&&i<c+10){ const t=(i-c)/10; picAlpha=t; picScale=.94+.06*t; } }
        jobs.push(['Stage',{part:id,index,scene:id,frame:f,title:part.title,sub:part.sub,kb:png('kb'),pic:png('pic'),picAlpha,picScale,box:boxes[f.dev]}]);
      }
    }
    const pid=part.card||part.packs&&('packs-'+part.packs)||part.scene||part.screen;
    if(only.length&&!only.includes(index)&&fs.existsSync(dir)&&old[index]&&old[index][1]===jobs.length){
      log.push([pid,jobs.length]); evs.forEach(e=>events.push([index,...e])); continue; }   // kept from the last render
    fs.rmSync(dir,{recursive:true,force:true}); fs.mkdirSync(dir);
    let next=0;
    await Promise.all(pages.map(async p=>{ for(let j;(j=next++)<jobs.length;){ const [fn,arg]=jobs[j];
      await p.evaluate(([fn,arg])=>Array.isArray(arg)?window[fn](...arg):window[fn](arg),[fn,arg]);
      await p.screenshot({path:path.join(dir,String(j).padStart(5,'0')+'.png')}); } }));
    log.push([pid,jobs.length]); evs.forEach(e=>events.push([index,...e]));
    console.log('part',index,pid,jobs.length,'frames');
  }
  fs.writeFileSync(path.join(out,'parts.json'),JSON.stringify(log));
  fs.writeFileSync(path.join(out,'events.json'),JSON.stringify(events));
  const n=log.reduce((a,x)=>a+x[1],0);
  console.log(name,n,'frames',(n/30).toFixed(1)+'s',JSON.stringify(log));
  await b.close();
})();
