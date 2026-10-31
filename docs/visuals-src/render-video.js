// node render-video.js <name> <plan.json> [--only=3,8]  — renders frames into out/<name>/p<NN>/ (then finish-video.py)
// A plan is a list of parts, each with its own backdrop variation:
//   {card:'intro'|'new'|'outro', ms}           a title or end card ('new' takes version and title: the update's name)
//   any part may carry title and sub: its on-screen words (otherwise the scene's defaults in video.html CAPS)
//   {packs:'list'|'carousel', step, ms}          the nine sound packs, one highlight landing every `step` seconds
//   {scene, from, to}                            a keyboard recording (VisualsVideo: kb-NNNN.png + pic-NNNN.png)
//   {screen, from, to}                           a whole-screen recording (VisualsVideo: scr-NNNN.png)
//   {hero, from, to}                             the README hero animation (VisualsVideo.sHeroAnim) set in hero-final.html, at 1920 x 1080
// from / to are frame numbers of the recording (30 fps). Recordings always play at the app's real speed.
// Faster than before: frames are drawn by several browser pages at once (PAGES, default 6), and each part keeps its own
// folder, so --only=3,8 redraws just those parts and keeps the rest from the last render.
// Also writes parts.json (frames per part) and events.json (what happens on which frame), which finish-video.py turns
// into the cue sheet and checks for pacing.
const {chromium}=require('playwright'),path=require('path'),fs=require('fs');
const REC=path.resolve(__dirname,'../../app/build/visuals/video');
const PACK_LEAD=.5;
// A pressed shortcut, spelled out on screen (round V6): the buttons and what they did, from the recording's event log
const COMBO={Yspace:['{Y}','Space'],Xdel:['{X}','Delete'],Menu:['{START}','Send'],RS:['{R3}','Take the word'],holdA:['Hold {A}','Accents'],
  releaseA:['Let go of {A}','Typed'],RB:['{R2} + {R1}','Next word'],View:['{SELECT}','Select'],RBgrow:['{R1}','One more word'],copy:['{A}','Copy'],
  clips:['{A}','Your last copies'],Xhold:['Hold {X}','Delete'],undo:['{R2} + {X}','Undo']};
const C_MIN=24, C_MAX=75;   // frames a shortcut stays spelled out: at least 0.8 s, at most 2.5 s (or until the next one)
(async()=>{
  const args=process.argv.slice(2), [name,planFile]=args.filter(a=>!a.startsWith('--'));
  const only=(args.find(a=>a.startsWith('--only='))||'').slice(7).split(',').filter(Boolean).map(Number);
  const plan=JSON.parse(fs.readFileSync(planFile)), grain=process.env.GRAIN!=='0', PAGES=+(process.env.PAGES||6);
  const out=path.resolve(__dirname,'out',name);
  if(!only.length) fs.rmSync(out,{recursive:true,force:true});
  fs.mkdirSync(out,{recursive:true});
  // every badge file must have clear room on all four sides (a badge touching its file's edge has been cut off)
  const chk=require('child_process').spawnSync('python3',['-c',`import glob,sys
from PIL import Image
import numpy as np
bad=[f for f in glob.glob('renders/btn-*.png')+glob.glob('renders/badge-*.png') if (lambda a:a[0].any() or a[-1].any() or a[:,0].any() or a[:,-1].any())(np.asarray(Image.open(f).convert('RGBA'))[:,:,3])]
print(' '.join(bad)); sys.exit(1 if bad else 0)`],{cwd:__dirname,encoding:'utf8'});
  if(chk.status!==0) throw new Error('badge files touching their edge (cut off): '+chk.stdout);
  const b=await chromium.launch();
  const pages=await Promise.all([...Array(PAGES)].map(async()=>{ const p=await b.newPage({viewport:{width:1920,height:1080}});
    await p.goto('file://'+path.resolve(__dirname,'video.html')+'?opt='+encodeURIComponent(process.env.OPT||'{}')+(grain?'':'&grain=0')); await p.waitForFunction(()=>document.title==='done'); return p; }));
  const heroPage=plan.some(p=>p.hero)?await (async()=>{ const p=await b.newPage({viewport:{width:1600,height:900},deviceScaleFactor:1.2});
    await p.goto('file://'+path.resolve(__dirname,'hero-final.html')); await p.waitForFunction(()=>document.title==='done'); return p; })():null;
  const old=fs.existsSync(path.join(out,'parts.json'))?JSON.parse(fs.readFileSync(path.join(out,'parts.json'))):[];
  const oldEv=fs.existsSync(path.join(out,'events.json'))?JSON.parse(fs.readFileSync(path.join(out,'events.json'))):[];
  const log=[], events=[];
  let gstart=0;
  for(const [index,part] of plan.entries()){
    const dir=path.join(out,'p'+String(index).padStart(2,'0'));
    // jobs: one per frame, each a function call on a page; evs: events on frame k of this part
    const jobs=[], evs=[];
    if(part.hero){ const rd=path.join(REC,part.hero), fr=JSON.parse(fs.readFileSync(path.join(rd,'frames.json'))), from=part.from||0, to=Math.min(part.to||fr.length,fr.length);
      // the hero's cursor (Android's: solid 400 ms after a change, then 530 ms on and off) and its faint "oller" until it's taken
      const taken=fr.findIndex(f=>/controller/.test(f.before)); let changed=from;
      for(let i=from;i<to;i++){ if(i>from&&fr[i].before!==fr[i-1].before) changed=i; const since=(i-changed)*1000/30;
        jobs.push(['HERO',{t:fr[i].before,kb:'file://'+path.join(rd,'kb-'+String(i).padStart(4,'0')+'.png'),cur:since<400||Math.floor((since-400)/530)%2===1,ghost:i<taken}]);
        for(const e of (fr[i].ev||'').split('+')) if(e==='RS') evs.push([i-from,'RS']); else if(e.startsWith('in:A')) evs.push([i-from,'A']); } }
    else if(part.card){ const k=Math.round(part.ms*30/1000); for(let i=0;i<k;i++) jobs.push(['Card',[part.card,i/30,index,{version:part.version,title:part.title,gsec:(gstart+i)/30}]]); }
    else if(part.packs){ const k=Math.round(part.ms*30/1000), step=part.step||.9;
      for(let i=0;i<k;i++) jobs.push(['Packs',{part:'packs',index,style:part.packs,sec:i/30,step}]);
      for(let j=0;j<9;j++){ const f=Math.round((PACK_LEAD+j*step)*30); if(f<k) evs.push([f,'pack: '+['Soft','Thock','Tactile','Typewriter','Felt','Chime','Glass','Pop','Retro'][j]]); } }
    else {
      const id=part.scene||part.screen, rd=path.join(REC,id);
      const fr=JSON.parse(fs.readFileSync(path.join(rd,'frames.json')));
      const from=part.from||0, to=Math.min(part.to||fr.length,fr.length);
      const changes=[]; for(let i=from+1;i<to;i++) if(fr[i].dev!==fr[i-1].dev) changes.push(i);
      const boxes=part.scene?JSON.parse(fs.readFileSync(path.join(rd,'picbox.json'))):{};   // from pic-boxes.py
      const full=part.scene&&fs.existsSync(path.join(rd,'picfull.json'))?JSON.parse(fs.readFileSync(path.join(rd,'picfull.json'))):{};
      const kbBox=part.scene&&fs.existsSync(path.join(rd,'kbbox.json'))?JSON.parse(fs.readFileSync(path.join(rd,'kbbox.json'))):null;
      let changed=from;
      const combos=[]; for(let i=from;i<to;i++) for(const e of (fr[i].ev||'').split('+')) if(COMBO[e]&&!(e==='RB'&&id!=='holdrt')) combos.push([i-from,COMBO[e]]);
      const comboAt=k=>{ let c=null; for(let j=0;j<combos.length;j++) if(combos[j][0]<=k){ const end=Math.min(combos[j][0]+C_MAX,j+1<combos.length?Math.max(combos[j+1][0],combos[j][0]+C_MIN):1e9); if(k<end) c={combo:combos[j][1],comboAge:(k-combos[j][0])/30,comboLeft:j+1<combos.length&&combos[j+1][0]<=end?undefined:(end-k)/30}; } return c||{}; };
      const dur=(to-from)/30;
      for(let i=from;i<to;i++){ const f=fr[i], k=i-from;
        if(i>from&&(f.before!==fr[i-1].before||f.sel!==fr[i-1].sel)) changed=i; const since=(i-changed)*1000/30, curOn=since<400||Math.floor((since-400)/530)%2===1;
        if(f.ev) evs.push([k,f.ev]);
        if(part.screen&&i>from&&f.tag!==undefined){ const h=fr[i-1]; if(f.tag!==h.tag&&f.tag) evs.push([k,'name: '+f.tag]); if(f.typed.length>h.typed.length) evs.push([k,'key']); }
        const png=s=>'file://'+path.join(rd,s+'-'+String(i).padStart(4,'0')+'.png');
        if(part.screen){ let a=0; if(f.tag) while(i-a-1>=0&&fr[i-a-1].tag===f.tag) a++;
          jobs.push(['Screen',{part:id,index,cap:part.cap||f.cap,title:part.title,sub:part.sub,tag:f.tag,tagAge:a,scr:png('scr'),sec:k/30,dur,left:dur-k/30,gsec:(gstart+k)/30}]); continue; }
        // fade-through when the device changes: 5 frames out, 10 in (the app's own showcase timing)
        let picAlpha=1,picScale=1;
        for(const c of changes){ if(i>=c-5&&i<c){ const t=(c-i)/5; picAlpha=t; picScale=.94+.06*t; } else if(i>=c&&i<c+10){ const t=(i-c)/10; picAlpha=t; picScale=.94+.06*t; } }
        jobs.push(['Stage',{part:id,index,scene:id,frame:f,title:part.title,sub:part.sub,kb:png('kb'),pic:png('pic'),picAlpha,picScale,box:boxes[f.dev],full:full[f.dev],kbBox,sec:k/30,curOn,dur,left:dur-k/30,gsec:(gstart+k)/30,...comboAt(k)}]);
      }
    }
    const pid=part.hero&&'hero'||part.card||part.packs&&('packs-'+part.packs)||part.scene||part.screen;
    if(only.length&&!only.includes(index)&&fs.existsSync(dir)&&old[index]&&old[index][1]===jobs.length){
      gstart+=jobs.length-8; log.push([pid,jobs.length]); evs.forEach(e=>events.push([index,...e])); continue; }   // kept from the last render
    fs.rmSync(dir,{recursive:true,force:true}); fs.mkdirSync(dir);
    let next=0;
    if(part.hero){ for(let j=0;j<jobs.length;j++){ const a=jobs[j][1];
        await heroPage.evaluate(a=>window.setFrame(a.t,a.kb,a.cur,a.ghost,null),a); await heroPage.screenshot({path:path.join(dir,String(j).padStart(5,'0')+'.png')}); } }
    else await Promise.all(pages.map(async p=>{ for(let j;(j=next++)<jobs.length;){ const [fn,arg]=jobs[j];
      await p.evaluate(([fn,arg])=>Array.isArray(arg)?window[fn](...arg):window[fn](arg),[fn,arg]);
      await p.screenshot({path:path.join(dir,String(j).padStart(5,'0')+'.png')}); } }));
    gstart+=jobs.length-8;
    // the no-crop guard: any button badge or logo cut off in any frame of this part stops the render
    for(const p of pages){ const c=await p.evaluate(()=>{ const x=[...new Set(window.CROPS||[])]; window.CROPS=[]; return x; }); if(c.length) throw new Error(`part ${index} ${pid}: cut off: ${c.join('; ')}`); }
    log.push([pid,jobs.length]); evs.forEach(e=>events.push([index,...e]));
    console.log('part',index,pid,jobs.length,'frames');
  }
  fs.writeFileSync(path.join(out,'parts.json'),JSON.stringify(log));
  fs.writeFileSync(path.join(out,'events.json'),JSON.stringify(events));
  const n=log.reduce((a,x)=>a+x[1],0);
  console.log(name,n,'frames',(n/30).toFixed(1)+'s',JSON.stringify(log));
  await b.close();
})();
