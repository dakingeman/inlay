#!/bin/sh
# Rebuilds the app's device pictures from this kit, every step in order:
#   merge-outlines.py  controller outlines (body + bumper humps, rounded grip ends)
#   kitgen.js          draws every device, idle and with each button lit
#   kit2android.py     one SVG + lighting CSS per device
#   geo.js             measures every button (press rings, sticks)
#   room.py            how much free space each button has round it (sizes the rings)
# Run from anywhere: sh tools/video/brand-kit/build.sh   (DeviceAssetsTest checks the result)
set -e
cd "$(dirname "$0")"
OUT=../../../app/src/main/assets/devices
TMP=$(mktemp -d)
python3 merge-outlines.py
node kitgen.js . "$TMP/kit.json" > /dev/null
python3 kit2android.py "$TMP/kit.json" "$OUT" > /dev/null
node geo.js "$OUT" > /dev/null
python3 room.py "$OUT" > /dev/null
rm -rf "$TMP"
echo "device pictures rebuilt in $OUT"
