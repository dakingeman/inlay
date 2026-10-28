// "Premium pass": one visual language for every device picture.
// Same geometry as before; only the finish changes:
//  - hairline strokes (1.5 px on screen, whatever the drawing's size)
//  - soft top-lit body with a rim highlight, dark recessed wells for the parts
//  - lit parts: solid accent + a focus ring (the same ring the keyboard uses), no blurry glow
//  - one label font (the app's Roboto), quiet when idle
//  - a soft floor shadow so each device sits on the page
const RING={gap:3.5,w:2.5};
// Style B (27 Oct 2026): flat dark body, one bright line for every outline, no gradient
const P = { acc:'#72D6E8', rimBody:'#A7AEBC', rimPart:'#A7AEBC', well:'#0F1115', ink:'#F2F3F5', inkLit:'#06121A' };
const PREMIUM_DEFS = `<svg width="0" height="0" style="position:absolute"><defs>
 <linearGradient id="pb" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#1C1F26"/><stop offset="1" stop-color="#1C1F26"/></linearGradient>
 <linearGradient id="rim" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#fff" stop-opacity=".16"/><stop offset=".35" stop-color="#fff" stop-opacity="0"/></linearGradient>
 <radialGradient id="floor"><stop offset="0" stop-color="#000" stop-opacity=".55"/><stop offset="1" stop-color="#000" stop-opacity="0"/></radialGradient>
 <filter id="ring" x="-60%" y="-60%" width="220%" height="220%">
  <feMorphology in="SourceAlpha" operator="dilate" radius="6" result="o"/><feMorphology in="SourceAlpha" operator="dilate" radius="3.5" result="i"/>
  <feComposite in="o" in2="i" operator="out" result="r"/><feFlood flood-color="${P.acc}" flood-opacity=".45"/><feComposite in2="r" operator="in" result="rc"/>
  <feMerge><feMergeNode in="rc"/><feMergeNode in="SourceGraphic"/></feMerge></filter>
</defs></svg>`;
let _ring=0, _m=0;
function ringFilter(id,f){ // f = drawing units per screen pixel; 2 px gap, 1.5 px ring
  const i=3*f, o=5.5*f;
  return `<defs><filter id="${id}" x="-60%" y="-60%" width="220%" height="220%"><feMorphology in="SourceAlpha" operator="dilate" radius="${o}" result="o"/><feMorphology in="SourceAlpha" operator="dilate" radius="${i}" result="i"/><feComposite in="o" in2="i" operator="out" result="r"/><feFlood flood-color="${P.acc}" flood-opacity=".55"/><feComposite in2="r" operator="in" result="rc"/><feMerge><feMergeNode in="rc"/><feMergeNode in="SourceGraphic"/></feMerge></filter></defs>`;}
function polish(svg,f){ const rid='ring'+(_ring++); f=f||1;
  // remove the old glow filters' definitions (harmless) and repaint every shape
  const out0= svg.replace(/<(path|rect|circle)\b([^>]*?)(\/?)>/g,(m,tag,a,close)=>{
    const has=(k,v)=>new RegExp(`${k}="${v}"`).test(a);
    let isBody=has('fill','#101116'), isLit=/filter="url\(#(g|hg2|grm)\)"/.test(a);
    const outline=/stroke="(#5a5e6a|#33363f|#2c2e36|#72D6E8)"/.test(a);
    if(isBody){ a=a.replace(/fill="#101116"/,'fill="url(#pb)"').replace(/stroke="[^"]*"/,`stroke="${P.rimBody}"`); }
    else if(isLit){ a=a.replace(/fill-opacity="[^"]*"/,'fill-opacity="1"').replace(/stroke-opacity="[^"]*"/,'').replace(/filter="url\(#\w+\)"/,'').replace(/stroke="[^"]*"/,`stroke="${P.acc}"`); }
    else { a=a.replace(/fill="#0f1014"/,`fill="${P.well}"`).replace(/fill="#0c0d11"/,`fill="#101218"`).replace(/fill="#0d0e12"/,`fill="${P.well}"`).replace(/stroke="(#5a5e6a|#33363f|#2c2e36)"/,`stroke="${P.rimPart}"`); }
    if(outline) a=a.replace(/stroke-width="[^"]*"/,'stroke-width="2.5" vector-effect="non-scaling-stroke"');
    else if(/stroke-width=/.test(a) && !/stroke="none"/.test(a)) a=a.replace(/stroke-width="[^"]*"/,'stroke-width="2.5" vector-effect="non-scaling-stroke"');
    let out=`<${tag}${a}${close}>`;
    if(isLit){ const bare=a.replace(/\s(fill|stroke|stroke-width|stroke-opacity|fill-opacity|filter|vector-effect|stroke-linejoin)="[^"]*"/g,''), mid=rid+'m'+(_m++);
      out=`<mask id="${mid}" maskUnits="userSpaceOnUse" x="-5000" y="-5000" width="10000" height="10000"><rect x="-5000" y="-5000" width="10000" height="10000" fill="#fff"/><${tag}${bare} fill="#000" stroke="#000" stroke-width="${2*RING.gap*f}" stroke-linejoin="round"/></mask>`+
          `<${tag}${bare} fill="none" stroke="${P.acc}" stroke-width="${2*(RING.gap+RING.w)*f}" stroke-linejoin="round" mask="url(#${mid})"/>`+out; }
    return out;
  })
  // labels: the app's own font, a little tracking; idle labels quieter
  .replace(/font-family="I" font-weight="700"/g,'font-family="R" font-weight="700" letter-spacing=".02em"')
  .replace(/fill="#C9CCD3"/g,`fill="${P.ink}"`).replace(/fill="#8d919c"/g,`fill="${P.ink}"`).replace(/stroke="#3a3c45"/g,`stroke="${P.ink}"`).replace(/stroke="#8d919c"/g,`stroke="${P.ink}"`).replace(/fill="#0B0F17"/g,`fill="${P.inkLit}"`);
  const out1=out0.replace(/font-size="([\d.]+)"/g,(m,v)=>`font-size="${Math.max(+v,14*f)}"`);
  return ringFilter(rid,f)+out1;
}
// soft shadow on the floor under a device: cx, bottom y, width (in the drawing's units)
const floorShadow=(cx,by,w)=>`<ellipse cx="${cx}" cy="${by+6}" rx="${w*0.46}" ry="${w*0.035}" fill="url(#floor)"/>`;

// D-pad: one cross with rounded ends and no seams; a pressed arm fills inside the same outline
let _dp=0;
function dpadCross(dx,dy,L,t,on){
  const r=Math.min(6,t*0.5), v=k=>(on&&on[k])||0, id='dp'+(_dp++);
  const d=`M${dx-t},${dy-L+r} A${r},${r} 0 0 1 ${dx-t+r},${dy-L} H${dx+t-r} A${r},${r} 0 0 1 ${dx+t},${dy-L+r} V${dy-t} H${dx+L-r} A${r},${r} 0 0 1 ${dx+L},${dy-t+r} V${dy+t-r} A${r},${r} 0 0 1 ${dx+L-r},${dy+t} H${dx+t} V${dy+L-r} A${r},${r} 0 0 1 ${dx+t-r},${dy+L} H${dx-t+r} A${r},${r} 0 0 1 ${dx-t},${dy+L-r} V${dy+t} H${dx-L+r} A${r},${r} 0 0 1 ${dx-L},${dy+t-r} V${dy-t+r} A${r},${r} 0 0 1 ${dx-L+r},${dy-t} H${dx-t} Z`;
  const arm={Dup:[dx-t,dy-L,2*t,L-t],Ddown:[dx-t,dy+t,2*t,L-t],Dleft:[dx-L,dy-t,L-t,2*t],Dright:[dx+t,dy-t,L-t,2*t]};
  let s=`<clipPath id="${id}"><path d="${d}"/></clipPath><path d="${d}" fill="#0f1014" stroke="#5a5e6a" stroke-width="2" stroke-linejoin="round"/>`;
  for(const k in arm) if(v(k)>0){const [x,y,w,h]=arm[k]; s+=`<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="#72D6E8" clip-path="url(#${id})"/><path d="${d}" fill="none" stroke="#5a5e6a" stroke-width="2" stroke-linejoin="round"/>`;}
  return s;
}
