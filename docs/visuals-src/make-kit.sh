#!/usr/bin/env bash
# Makes every GitHub / Reddit still and the hero loop again, from the app's own drawings.
# Run from docs/visuals-src after the renders exist (see README: VisualsExport, then trim.py).
# Writes into ../images: pack/*.png (1920 × 1080), hero.gif (1600 × 900), social-preview.png (1280 × 640)
set -euo pipefail
cd "$(dirname "$0")"
OUT=../images; mkdir -p "$OUT/pack" out/hero
HP="lab=1&al=optical&wb=1.3&bw=mix&dir=g2&fx=tiles,spill&lit=glow&litop=.6&sk=.55&seed=11"

# 1. README feature images (backdrop.js gives each its own variation, seeded by its id)
for id in typing words accents devices hold editing tidy looks icons tv-mode languages settings sound-packs tour controls; do
  SCALE=1.2 node shoot.js feature.html "$OUT/pack/$id.png" 1600 900 "id=$id"   # laid out at 1600 × 900, saved at 1920 × 1080
  node check.js feature.html 1600 900 "id=$id"
done

# 2. Hero: one still and the four loop slides (grainless frames for the GIF, plus the panel-blank frames the fade uses)
SCALE=1.2 node shoot.js hero.html "$OUT/pack/hero.png" 1600 900 "s=s1&$HP"
for s in s1 s2 s3 s4; do
  node shoot.js hero.html "out/hero/$s.png" 1600 900 "s=$s&$HP&nograin=1"
  node shoot.js hero.html "out/hero/$s-blank.png" 1600 900 "s=$s&$HP&nograin=1&hide=1"
done
HOLD=2.2 python3 hero-loop.py out/hero "s1 s2 s3 s4" out/hero/hero.mp4
ffmpeg -y -loglevel error -i out/hero/hero.mp4 \
  -vf "fps=20,split[a][b];[a]palettegen=max_colors=256:stats_mode=full[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle" \
  "$OUT/hero.gif"

# 3. Social preview (GitHub link card): the hero without the device row, 1600 × 800 scaled to 1280 × 640
node shoot.js hero.html out/social-1600.png 1600 800 "s=s1&$HP&social=1"
python3 -c "from PIL import Image; Image.open('out/social-1600.png').resize((1280,640),Image.LANCZOS).save('$OUT/social-preview.png')"

# 4. Social / Reddit set: the README images, numbered in gallery order
SOC=out/social; mkdir -p "$SOC"; cp "$OUT/pack/hero.png" "$SOC/01-hero.png"
n=2; for id in typing words accents hold editing tidy devices tv-mode looks icons languages settings sound-packs tour controls; do
  cp "$OUT/pack/$id.png" "$SOC/$(printf %02d $n)-$id.png"; n=$((n+1)); done

# 5. Shrink every PNG without changing a single pixel
python3 -c "
import glob, oxipng
for f in glob.glob('$OUT/pack/*.png') + ['$OUT/social-preview.png'] + glob.glob('$SOC/*.png'): oxipng.optimize(f, level=4, strip=oxipng.StripChunks.safe())"
ls -la "$OUT" "$OUT/pack"
