// Loads the device kit (plain string-building JS) and draws every device, base and with each button lit.
const fs=require('fs'), vm=require('vm'), path=require('path');
const dir=process.argv[2];
const ctx={console, Math, JSON, Object, Array, String, Number, parseFloat, parseInt, isNaN, RegExp};
ctx.window=ctx; ctx.document={fonts:{load:()=>Promise.resolve()}, getElementById:()=>({})};
vm.createContext(ctx);
for(const f of ['controller.prem.js','controller.merged.js','handhelds2.js','devices2.js','premium.js','kit.js'])
  vm.runInContext(fs.readFileSync(path.join(dir,f),'utf8'),ctx,{filename:f});
const keys=['OK','BACK','HOME','A','B','X','Y','LB','RB','LT','RT','LS','RS','Dup','Ddown','Dleft','Dright','START','SELECT','MENU','VIEW','HOME','L3','R3','OPTIONS','SHARE','PLUS','MINUS'];
const devices={};
for(const style of ['xbox','ps','switch']){
  devices['controller-'+style]=on=>ctx.kitController(style,on,657);
  devices['wide-'+style]=on=>ctx.kitHandheld('wide',style,on,700);
  devices['vertical-'+style]=on=>ctx.kitHandheld('vertical',style,on,330);
}
devices['remote']=on=>ctx.kitRemote(on,300);
// The vertical handheld with sticks (approved 26 Oct): same height, sticks below the D-pad and face buttons
const baseV=vm.runInContext('JSON.stringify(HH.vertical)',ctx);
const sticksV=vm.runInContext('JSON.stringify(HH_VERTICAL_STICKS)',ctx);
const withSticks=f=>on=>{vm.runInContext(`HH.vertical=Object.assign(${baseV},${sticksV})`,ctx);try{return f(on);}finally{vm.runInContext(`HH.vertical=${baseV}`,ctx);}};
for(const style of ['xbox','ps','switch']) devices['vertical-sticks-'+style]=withSticks(on=>ctx.kitHandheld('vertical',style,on,330));
const out={defs:vm.runInContext("PREMIUM_DEFS",ctx), devices:{}};
for(const [name,fn] of Object.entries(devices)){
  const reset=()=>{ /* the kit numbers its ids with counters; keep them stable per draw */ };
  const base=fn({}); const lit={};
  for(const k of keys){ const s=fn({[k]:1}); if(s.replace(/(ring|dp|m)\d+/g,'')!==base.replace(/(ring|dp|m)\d+/g,'')) lit[k]=s; }
  out.devices[name]={base,lit};
  console.error(name, Object.keys(lit).join(' '));
}
fs.writeFileSync(process.argv[3],JSON.stringify(out));
