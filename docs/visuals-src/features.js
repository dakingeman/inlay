// Every README image: one idea each. Words follow the app; the keyboard and devices are drawn by the app.
const TV=[{name:'Stranger Things',plat:'SERIES'},{name:'The Grand Budapest Hotel',plat:'FILM'},{name:'Better Call Saul',plat:'SERIES'},
  {name:'Dune: Part Two',plat:'FILM'},{name:'The Lord of the Rings',plat:'FILM'}].map((g,i)=>Object.assign(g,{tone:COVER_TONES[(i+3)%9],i:i+3}));
function grid(items,{x,y,w,cols,gap,rowGap,label=true}){
  return items.map((it,k)=>{const c=k%cols,r=Math.floor(k/cols); const h=Math.round(w/it.aspect);
    return `<div class="abs" style="left:${x+c*(w+gap)}px;top:${y+r*rowGap}px;width:${w}px"><img class="cardimg m" src="renders/${it.src}.png" style="width:${w}px;height:${h}px">${label?`<div class="lbl m">${it.label}</div>`:''}</div>`;}).join('');
}
// devices side by side on one floor line, each at its own width, labels underneath
function devRow(list,y,h){
  const gap=56, total=list.reduce((a,d)=>a+d[2],0)+gap*(list.length-1); let x=Math.round((1600-total)/2);
  return list.map(([src,label,w])=>{const html=`<div class="abs" style="left:${x}px;top:${y}px;width:${w}px;height:${h+60}px;display:flex;flex-direction:column;justify-content:flex-end;align-items:center">
    <img class="m" src="renders/${src}.png" style="width:${w}px;max-height:${h}px;object-fit:contain"><div class="lbl m" style="white-space:nowrap">${label}</div></div>`; x+=w+gap; return html;}).join('');
}
function badgeRows(list,x,y,secName,bw){
  return `<div class="abs" style="left:${x}px;top:${y}px"><div class="label sec m">${secName}</div>${list.map(([b,w])=>{
    const r=BADGES[b]; return `<div class="row"><div class="b" style="width:${bw}px"><img class="m" src="renders/badge-${String(r.i).padStart(2,'0')}.png" style="height:${r.h}px"></div><div class="w m">${w}</div></div>`;}).join('')}</div>`;
}
const FEATURES={
  // GitHub's link preview: 1280 × 640, drawn at 1600 × 800 and scaled down; key content kept in the middle
  social:{layout:'stage',W:1600,H:800,logo:true,textX:180,textY:230,textW:440,size:44,title:'A keyboard you drive with your controller',body:'Free and open source, for Android handhelds, controllers and TV.',
    pic:'pic-hero',picW:420,picBottom:110,screenX:680,screenW:740,screen:{kb:'renders/kb-suggest.png',kbAspect:1920/534,field:{before:'knights of the old rep'}}},
  hero:{layout:'stage',logo:true,textY:250,size:48,title:'A keyboard you drive with your controller',body:'Free and open source, for Android handhelds, controllers and TV.',
    pic:'pic-hero',picW:500,screen:{kb:'renders/kb-suggest.png',kbAspect:1920/534,field:{before:'knights of the old rep'}}},
  typing:{layout:'stage',textY:140,title:'Move with the D-pad, press A',body:'Y is space, X deletes and Start goes. Moving up and down remembers your column, so the highlight never guesses.',
    pic:'pic-type',screen:{kb:'renders/kb-type.png',kbAspect:1920/534,field:{before:'ninja gai'}}},
  words:{layout:'stage',textY:140,title:'Words show up as you type',body:'Press the right stick in for the first one, or move up and pick another. It learns the names and titles you use.',
    pic:'pic-words',screen:{kb:'renders/kb-suggest.png',kbAspect:1920/534,field:{before:'knights of the old rep'}}},
  accents:{layout:'stage',textY:140,title:'Hold A for accents',body:'Hold A on a letter and its accents rise into the top row. Move to the one you want and let go.',
    pic:'pic-accents',screen:{kb:'renders/kb-accents.png',kbAspect:1920/534,field:{before:'viva piñ'}}},
  hold:{layout:'stage',textY:140,title:'Hold RT for more',body:'Tips appear on the keys while you hold it: a capital, word jumps, an accent and undo. Nothing to memorise.',
    pic:'pic-holdrt',screen:{kb:'renders/kb-holdrt.png',kbAspect:1920/534,field:{before:'fable the',after:' lost chapters'},results:[LIBRARY[5]],count:'1 game'}},
  editing:{layout:'stage',textY:140,title:'Select, copy and paste',body:'View starts selecting and RB grows it. The clock keeps your last six copies, one press away.',
    pic:'pic-select',screen:{kb:'renders/kb-clips.png',kbAspect:1920/534,field:{before:'conker live and reloaded'}}},
  tidy:{layout:'stage',textY:140,title:'Tidies up as you type',body:'A capital starts each sentence, a lone i becomes I, and a space before a full stop moves after it. Each has its own switch.',
    pic:'pic-tidy',screen:{kb:'renders/kb-tidy.png',kbAspect:1920/534,title:'Notes',noField:true,field:{},results:[],count:'',
      note:{meta:'Today, 21:40',heading:'Playthrough log',lines:['Day 3, still on the water temple'],typing:'Finally beat the last boss. I think I\u2019ll replay it on hard'}}},
  'tv-mode':{layout:'stage',textY:140,title:'TV mode',body:'A compact keyboard with a big line showing what you type, easy to read from the sofa. It turns on by itself on a TV.',
    pic:'pic-tv',picX:300,picW:118,screen:{kb:'renders/kb-tv.png',kbAspect:1560/762,title:'Search',noField:true,field:{},results:[TV[0]],count:''}},
  devices:{layout:'top',title:'Made for what you hold',body:'Pick a controller, a handheld, a vertical handheld or a TV remote. The buttons, the tips and the tour follow it.',
    content:()=>devRow([['dev-controller','Controller',300],['dev-wide','Handheld',400],['dev-vertical','Vertical handheld',165],['dev-vertical-sticks','Vertical, with sticks',165],['dev-remote','TV remote',92]],340,300)},
  icons:{layout:'top',title:'Icons for your controller',body:'Xbox, PlayStation or Switch. Every hint follows, on the keys and in Settings.',
    content:()=>['xbox','playstation','switch'].map((k,i)=>{const cx=96+i*(448+24), lab=['Xbox','PlayStation','Switch'][i];
      // close-up: shift and delete on top, ?123 and enter below, at 0.53 of the keyboard render (the keys whose hints change)
      const z=.53, key=(sx,sy,sw,sh)=>`<div style="width:${sw*z}px;height:${sh*z}px;overflow:hidden;border-radius:${14*z}px;flex:none"><img src="renders/icons-${k}.png" style="width:${1920*z}px;margin-left:${-sx*z}px;margin-top:${-sy*z}px;display:block"></div>`;
      const card=`<div class="abs m zoom" style="left:${cx}px;top:596px;width:448px;padding:22px;box-sizing:border-box;display:flex;flex-direction:column;gap:10px;align-items:center">
        <div style="display:flex;gap:10px">${key(24,318,176,92)}${key(1531,318,365,92)}</div><div style="display:flex;gap:10px">${key(24,423,364,93)}${key(1531,423,365,93)}</div></div>`;
      return `<img class="abs m" src="renders/icons-pic-${k}.png" style="left:${cx+224-150}px;top:236px;width:300px">`
        +`<div class="lbl m abs" style="left:${cx}px;width:448px;top:${236+312}px;margin:0">${lab}</div>`+card;}).join('')},
  looks:{layout:'top',title:'Make it look how you want',body:'Twelve themes, sixteen highlight colours, three key shapes, two layouts and a number row.',
    content:()=>grid([{src:'look-light-ocean',label:'Light, ocean blue'},{src:'look-forest-lime',label:'Forest, lime, round keys, solid highlight'},
      {src:'look-mocha-tangerine',label:'Mocha, tangerine, sharp keys, number row'},{src:'look-outline-orchid',label:'Outline, orchid, classic layout'}]
      .map(o=>Object.assign(o,{aspect:o.src==='look-mocha-tangerine'?1920/630:1920/534})),{x:96,y:240,w:690,cols:2,gap:28,rowGap:318})},
  languages:{layout:'top',title:'Eleven languages',body:'Pick up to three and switch with RT + LT. Holding A gives accents, so Italian, Dutch, Polish and more work too.',
    content:()=>grid([{src:'lang-german',label:'Deutsch'},{src:'lang-french',label:'Français (AZERTY)'},{src:'lang-russian',label:'Русский'},{src:'lang-turkish',label:'Türkçe'}]
      .map(o=>Object.assign(o,{aspect:1920/534})),{x:96,y:250,w:690,cols:2,gap:28,rowGap:300})},
  settings:{layout:'top',title:'Settings, made for the controller',body:'Six tabs, each about one thing. LB and RB switch tabs, and Y shows the keyboard right there.',
    content:()=>`<img class="abs shot m" src="renders/screen-look.png" style="left:260px;top:240px;width:1080px">`},
  'sound-packs':{layout:'top',title:'Nine sound packs',body:'Soft, Thock, Tactile, Typewriter, Felt, Chime, Glass, Pop and Retro, with vibration tuned so even small motors are felt.',
    content:()=>`<img class="abs shot m" src="renders/screen-soundpack.png" style="left:260px;top:240px;width:1080px">`},
  tour:{layout:'top',title:'A one-minute tour',body:'It opens the first time, shows every button on your own device, and What\u2019s new follows each update.',
    content:()=>`<img class="abs shot m" src="renders/screen-tour-holdrt.png" style="left:260px;top:240px;width:1080px">`},
  controls:{layout:'top',title:'Every button has a job',body:'Settings \u2192 Button guide shows the same list, in your controller\u2019s icons.',
    content:()=>badgeRows([['{A}','Type the highlighted key'],['{X}','Delete \u00b7 hold to speed up'],['{Y}','Space'],['{R2}','Shift \u00b7 tap twice for caps lock'],['{L2}','Symbols and numbers'],['{START}','Enter \u00b7 Search \u00b7 Go'],['{B}','Close the keyboard'],['hold {A}','Accents \u00b7 hold, then choose'],['{R3}','Take the first suggested word'],['{L3}','Keyboard size']],96,240,'Typing',170)
      +badgeRows([['{L1} {R1}','Move the text cursor'],['{R3} left or right','Move the cursor, faster the further'],['{R2} + {L1} {R1}','Jump a whole word'],['{SELECT}','Select text'],['{SELECTx2}','Select everything'],['{R2} + {A}','One capital letter'],['{R2} + {Y}','Accent the last letter'],['{R2} + {X}','Undo'],['{R2} + {L2}','Switch language']],800,240,'Editing and more',300)},
};
// badge renders (made by VisualsExport.badges): their index and height
const BADGE_LIST=["{A}","{X}","{Y}","{R2}","{L2}","{START}","{B}","hold {A}","{R3}","{L3}","{R2} + {A}","{R2} + {Y}","{R2} + {L2}","{L1} {R1}","{R3} left or right","{R2} + {L1} {R1}","{SELECT}","{SELECTx2}","{R3} up or down","{R2} + {X}"];
const BADGES={}; BADGE_LIST.forEach((b,i)=>BADGES[b]={i,h:Math.round(((b.startsWith('hold')||b.startsWith('{R2} +'))?104:61)*0.46)});
