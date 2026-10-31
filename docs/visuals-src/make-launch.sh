#!/usr/bin/env bash
# The launch set (1.2.0, 30 Oct 2026): every README image, the top image, the link preview and the Reddit set, from
# launch.html (layout L3-B: words on top, the moment centred under them; button names drawn as the app's badges; bumpers and
# triggers only where used). Run from docs/visuals-src after VisualsExport (stills, badges, screens) and trim.py.
# Every image prints its measured alignment checks and runs check.js (overlap, margins, contrast); any FAIL stops the script.
set -euo pipefail
cd "$(dirname "$0")"
OUT=../images; mkdir -p "$OUT/pack" out/launch
fail=0
shot(){ local out=$1 w=$2 h=$3 q=$4
  local r; r=$(SCALE=${SCALE:-1.2} node launch-shoot.js "$out" "$w" "$h" "$q"); echo "$r"; echo "$r" | grep -q FAIL && fail=1
  local k; k=$(node check.js launch.html "$w" "$h" "$q" 2>&1 | tail -1); echo "   $k"; echo "$k" | grep -qiE "close \(|overlap|outside" && fail=1 || true; }
for id in typing words accents devices hold editing tidy looks icons tv-mode languages settings sound-packs tour controls; do
  shot "$OUT/pack/$id.png" 1600 900 "id=$id&v=b"; done
shot "$OUT/pack/hero.png" 1600 900 "id=hero&v=b"
# Link preview: GitHub shows 1280 × 640 (drawn at 1600 × 800); the PlayStation and Nintendo versions for posts
for pl in jsrf ps switch; do SCALE=1 shot "out/launch/social-$pl.png" 1600 800 "id=social&v=p1&w=1600&h=800&plat=$pl"
  python3 -c "from PIL import Image; Image.open('out/launch/social-$pl.png').convert('RGB').resize((1280,640),Image.LANCZOS).save('out/launch/social-$pl-1280.png')"; done
cp out/launch/social-jsrf-1280.png "$OUT/social-preview.png"
# Reddit set: the README images in gallery order
SOC=out/launch/reddit; rm -rf "$SOC"; mkdir -p "$SOC"; cp "$OUT/pack/hero.png" "$SOC/01-hero.png"; n=2
for id in typing words accents hold editing tidy devices tv-mode looks icons languages settings sound-packs tour controls; do
  cp "$OUT/pack/$id.png" "$SOC/$(printf %02d $n)-$id.png"; n=$((n+1)); done
cp out/launch/social-ps-1280.png "$SOC/link-preview-playstation.png"; cp out/launch/social-switch-1280.png "$SOC/link-preview-nintendo.png"
python3 -c "
import glob, oxipng
for f in glob.glob('$OUT/pack/*.png') + ['$OUT/social-preview.png'] + glob.glob('$SOC/*.png'): oxipng.optimize(f, level=3, strip=oxipng.StripChunks.safe())"
[ $fail = 0 ] && echo "ALL CHECKS PASS" || { echo "SOMETHING FAILED"; exit 1; }
