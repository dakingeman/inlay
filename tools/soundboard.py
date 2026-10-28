"""A listening page for sound packs: every sound on a button, a "type a sentence" button,
a speaker switch (headphones, handheld, phone) and each pack's check result.
One self-contained HTML file, so it opens in any browser, including on the Odin.

    python tools/soundboard.py                                   # every shipping pack, from res/raw
    python tools/soundboard.py build/sound-lab --only mypack,soft    # a try-out pack next to Soft
    -> build/soundboard.html
"""
import argparse, base64, html, json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
import sounds as S, soundcheck as C

KEYS = [("move", "Move"), ("type", "Letter"), ("space", "Space"), ("delete", "Delete"), ("on", "On"),
        ("off", "Off"), ("lock", "Caps lock"), ("select_all", "Select all"), ("confirm", "Enter"), ("back", "Back")]

PAGE = """<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Inlay soundboard</title>
<style>
:root{--bg:#0B0B0E;--card:#141418;--raise:#1B1B1F;--line:rgba(242,243,245,.08);--fg:#F2F3F5;--mute:#9A9CA6;--teal:#72D6E8;--good:#7FD39A;--bad:#F08A7A}
body{margin:0;background:var(--bg);color:var(--fg);font:16px/1.5 system-ui,sans-serif}
.wrap{max-width:960px;margin:0 auto;padding:32px 16px;display:grid;gap:20px}
h1{margin:0;font-size:28px} p{margin:0;color:var(--mute)}
.bar{display:flex;gap:8px;flex-wrap:wrap;align-items:center}
.seg button,.k,.btn{border:0;cursor:pointer;font:inherit;color:inherit}
.seg{display:inline-flex;background:var(--raise);border-radius:999px;padding:3px;gap:2px}
.seg button{background:none;padding:6px 14px;border-radius:999px;color:var(--mute)}
.seg button[aria-pressed=true]{background:var(--teal);color:#0B0B0E}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:12px}
.card{background:var(--card);border:1px solid var(--line);border-radius:20px;padding:16px;display:grid;gap:10px;align-content:start}
.card h2{margin:0;font-size:18px;display:flex;justify-content:space-between;gap:8px}
.pill{font-size:12px;padding:3px 8px;border-radius:999px;border:1px solid currentColor;font-weight:500}
.ok{color:var(--good)}.no{color:var(--bad)}
.keys{display:flex;flex-wrap:wrap;gap:6px}
.k{background:var(--raise);border-radius:10px;padding:7px 10px;font-size:14px}
.k.on{background:var(--teal);color:#0B0B0E}
.btn{justify-self:start;background:var(--fg);color:var(--bg);padding:8px 14px;border-radius:999px;font-weight:700}
.fails{font-size:13px;color:var(--bad)}
button:focus-visible{outline:2px solid var(--fg);outline-offset:2px}
</style></head><body><div class="wrap">
<h1>Inlay soundboard</h1>
<p>Tap any sound. "Type a sentence" plays the four letter sounds in turn, like fast typing. The speaker switch imitates a handheld or phone speaker.</p>
<div class="bar"><div class="seg" id="spk"><button data-s="head" aria-pressed="true">Headphones</button><button data-s="handheld" aria-pressed="false">Handheld</button><button data-s="phone" aria-pressed="false">Phone</button></div></div>
<div class="grid" id="packs"></div></div>
<script>
const AUD=__AUDIO__, PACKS=__PACKS__, KEYS=__KEYS__;
let ctx,bufs={},spk="head",chain=null;
function out(){if(chain&&chain.k===spk)return chain.n;const d=ctx.destination;if(spk==="head"){chain={k:spk,n:d};return d}
 const f=spk==="phone"?750:450,a=ctx.createBiquadFilter(),b=ctx.createBiquadFilter(),l=ctx.createBiquadFilter();
 a.type=b.type="highpass";a.frequency.value=b.frequency.value=f;a.Q.value=.54;b.Q.value=1.31;l.type="lowpass";l.frequency.value=9000;
 a.connect(b);b.connect(l);l.connect(d);chain={k:spk,n:a};return a}
async function buf(k){ctx=ctx||new AudioContext();if(bufs[k])return bufs[k];const b=Uint8Array.from(atob(AUD[k]),c=>c.charCodeAt(0));return bufs[k]=await ctx.decodeAudioData(b.buffer)}
async function play(k,w=0){const b=await buf(k),s=ctx.createBufferSource();s.buffer=b;s.connect(out());s.start(ctx.currentTime+w)}
async function sentence(p){const t=["type","type1","type2","type3"];for(const k of [...t,"space","confirm"])await buf(p+"_"+k);
 let at=.05,last=-1;for(const ch of "inlay sounds good"){let n;if(ch===" ")n="space";else{let i=Math.floor(Math.random()*3);if(last>=0&&i>=last)i++;last=i;n=t[i]}play(p+"_"+n,at);at+=.105}play(p+"_confirm",at+.12)}
const el=document.getElementById("packs");
for(const p of PACKS){const c=document.createElement("article");c.className="card";
 c.innerHTML=`<h2>${p.label}<span class="pill ${p.ok?"ok":"no"}">${p.ok?"All checks pass":"Fails checks"}</span></h2>`+(p.fails.length?`<div class="fails">${p.fails.join("<br>")}</div>`:"")+`<div class="keys"></div>`;
 const ks=c.querySelector(".keys");for(const [k,l] of KEYS){const b=document.createElement("button");b.className="k";b.textContent=l;b.onclick=()=>{play(p.name+"_"+k);b.classList.add("on");setTimeout(()=>b.classList.remove("on"),140)};ks.appendChild(b)}
 const s=document.createElement("button");s.className="btn";s.textContent="Type a sentence";s.onclick=()=>sentence(p.name);c.appendChild(s);el.appendChild(c)}
document.querySelectorAll("#spk button").forEach(b=>b.onclick=()=>{spk=b.dataset.s;document.querySelectorAll("#spk button").forEach(x=>x.setAttribute("aria-pressed",x===b))});
</script></body></html>"""

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("folder", nargs="?", default=C.RAW)
    ap.add_argument("--only", default=""); ap.add_argument("--out", default=os.path.join(os.path.dirname(__file__), "..", "build", "soundboard.html"))
    a = ap.parse_args()
    packs = S.find([n for n in a.only.split(",") if n])
    report, _ = C.check(a.folder, packs)
    audio, meta = {}, []
    for name, _, _ in packs:
        for cue in S.CUES + ["type1", "type2", "type3"]:
            path = os.path.join(a.folder, f"{name}_{cue}.wav")
            if os.path.isfile(path): audio[f"{name}_{cue}"] = base64.b64encode(open(path, "rb").read()).decode()
        meta.append({"name": name, "label": html.escape(name.replace("_", " ").title()),
                     "ok": not report[name]["fails"], "fails": [html.escape(f) for f in report[name]["fails"]]})
    page = PAGE.replace("__AUDIO__", json.dumps(audio)).replace("__PACKS__", json.dumps(meta)).replace("__KEYS__", json.dumps(KEYS))
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    open(a.out, "w", encoding="utf-8").write(page)
    print(f"Soundboard: {os.path.abspath(a.out)}")

if __name__ == "__main__":
    main()
