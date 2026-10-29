# Wordmark and logo animation: what we tried (Oct 2026)

The finished versions are `tools/brand/wordmark.py` and `tools/brand/logo_motion.py`. This folder keeps the exploration so nobody repeats it.
The scripts here import each other and still run from inside this folder, but nothing uses them.

## The name

| Tried | Result | Why |
|---|---|---|
| "Inlay" with a capital I, no cursor (1.0 and 1.1) | Replaced | Read as a generic app name; no link to typing. Its outlines are `wordmark_raw.txt` |
| Underline and highlight ideas (a bar under a letter, a box round the a) | Dropped | One reminded Emre of LinkedIn; the others were decoration rather than meaning |
| **in\|lay**: "in" typed, cursor, "lay" suggested in grey (5B) | Loved, not the logo | In one colour the cursor reads as a second l ("inllay"); the grey fades first at small sizes and on white |
| **inlay\|**: the word typed, cursor resting after it (4) | **Chosen** | Reads as one word, works in one colour, holds up to 24 px. in\|lay lives on as the idea behind the animation |

Fixes found while refining: measure the stem on the n, not on the l or i box; crop to the ink (the i's side bearing left 21 units of empty space on one side); give the cursor the font's 12-unit overshoot so its round ends look as tall as the l; grey on white had to go from 3.2 : 1 to 4.1 : 1.

## The animation

| Round | Tried | Kept |
|---|---|---|
| 1 | Typed out (A), typed with the lit key pressing (B), suggestion accepted (C), several suggestions (D) | A, with the keys taking part |
| 2 | Letter styles: snap, press, travel, ink (teal settling to white). Keys: lit key only, each letter its own key, highlight gliding | Each letter its own key |
| 3 | Making a press readable: flash, shade, the letter printed on the key (legend), lit legend | Flash and shade liked, legend disliked |
| 4 | Soft flash, swap (the one teal key hops), soft shade, down-and-up. Letter styles 5-11 (mixes of travel, press, teal, glint, warm) | Swap; letters 6 and 9 |
| 5 | Swap ending on the middle key with the y; press effects: deeper, deep teal, down-and-up | Deeper press |
| 6 | A physical press model (highlight first, hard bottom-out, hold, spring) | Disliked: the colour and the timing felt off. The simple press, a touch deeper with a 1 % spring, won |
| 7 | Rhythms: even, hands only, hands + syllable beat, natural with randomness | Hands + syllable beat |
| 8 | Key order worked out from QWERTY positions instead of hand-picked | Kept |

Lessons: show slow-motion versions when differences are small (at real speed four letter styles looked the same); check sync on the rendered frames, not in the code (the teal's 75 % first-frame glide left two letters a frame out); paint the page behind a render in the picture's own ground (a fractional height left a white line under the dark loop).
