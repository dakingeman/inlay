#!/bin/sh
# Rebuilds the 1.3.0 release covers (Emre's pick, 8 Nov): R4 layout, styled line with the app's spark, top row with icons and words.
cd "$(dirname "$0")"
B="$(cat cover-line.html)"
SUB="$B" KEYS=split-keys-both node shoot-cover.js R4 ../images/release-1.3.0.png
SUB="$B" KEYS=split-keys-both node shoot-cover.js R4 ../images/release-1.3.0-square.png 1080 1080
