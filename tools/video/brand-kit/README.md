# Inlay device kit (final, agreed 26 Oct 2026)

This folder is the **only** source for Inlay's device pictures: the Xbox, PlayStation and Switch controllers, the wide handheld, the vertical handheld and the TV remote.

Anything older is superseded and must not be used:
- `tools/video/controller.js`
- `tools/video/handhelds.js`
- the old pack cards
- the files in `_superseded/`

## What's here

| File | What it is |
|---|---|
| `device-kit.png` | **The approved look.** Match this exactly. |
| `in-use/in-use-1…6.png` | Approved mock-ups of the pictures in real screens: Button guide (wide handheld + PS icons; Xbox controller with a combo), tour (vertical handheld + Switch icons), What's new (PlayStation controller), remote guide, and one row ("Shift") on every device. |
| `device-kit.html` | Renders `device-kit.png`. |
| `in-use.html` | Renders the six in-use screens. |
| `kit.js` | One function per device: `kitController(style, on, width)`, `kitHandheld('wide' or 'vertical', style, on, width)`, `kitRemote(on, width)`. `style` is `xbox`, `ps` or `switch`. `on` lists the lit buttons, e.g. `{RT:1, A:1, Dright:1}`. |
| `controller.prem.js` | The three controller drawings. Xbox is the traced original and is the reference; never change its shape. (28 Oct 2026: its grip ends had been clipped flat by the frame; `merge-outlines.py` completes them as round ends, fitted to the grip's own sides.) |
| `merge-outlines.py` → `controller.merged.js` | Each controller's body and bumper humps as one smooth outline, checked against the trace (max 0.9 units off). |
| `build.sh` | Rebuilds the app's pictures, every step in order. Always use it; `DeviceAssetsTest` fails if a step was skipped. |
| `handhelds2.js` | Wide handheld (traced from an AYN Odin 3 front photo) and vertical handheld (after the Retroid Pocket Classic). Start and Select sit at the top of the wide handheld. `HH_VERTICAL_STICKS` is the vertical handheld with two sticks. Also holds the floating shoulder keys (`shoulderKeysA`) and the spacing check (`spacingCheck`). |
| `devices2.js` | TV remote. |
| `premium.js` | The finish applied to every device (`polish`): one line thickness, recessed parts, a pressed ring that follows each button's shape, and the plain D-pad (`dpadCross`). |
| `odin3-outline.json` | The traced Odin 3 outline. |
| `fonts/` | Inter and Atkinson Hyperlegible Next. Both are under the SIL Open Font License, so they can be shipped. |

The pages also load `../kb2.js`, `../spotlight4.js` and the Roboto fonts from `tools/video`.

## The rules (they apply everywhere the pictures appear)

1. **One line thickness on every outline.** No thin decoration lines, and no glow.
2. **Shoulder buttons are floating keys** above the device, over the real shoulder spots, with the trigger on top and the bumper below. They are the only place bumpers and triggers are shown and lit. Controllers keep their bumper curves on the outline, unlit and without labels. One spec on every device, in dp on the Device tab (`SHOULDER` in `kit.js`): bumper 36 × 11 (pill), trigger 28 × 14 (5 dp corners), 8 dp between them, 10 dp above the body, 6 dp of ring room round the picture.
3. **A pressed button** is solid accent (#72D6E8) with a ring that follows its exact shape. Unpressed buttons sit darker than the body.
4. **The D-pad** is one plain cross with no arrows. Only the pressed arm lights up, with the same ring as a button round its three outer sides. It's the same size on every device.
9. **Sticks:** a recessed well 8 units wider than the cap, nothing else. Clicked in, the cap sinks to 86 % and gets the standard press ring.
10. **No PS button** on the PlayStation controller: Inlay doesn't use it.
5. **Labels** use Roboto Bold, never below the minimum size.
6. **The spacing check** (`spacingCheck('wide')` and `spacingCheck('vertical')`) must return an empty list.
7. **Minimum size:** never show a handheld picture smaller than about 400 px wide.
8. **The Switch Pro** has plain pills for − and +. Capture and home are left out.

## In the app (Kotlin)

The app draws on a Canvas, so port these shapes and numbers into the Android views rather than loading the HTML. Keep the same proportions, line thickness, colours and states as `device-kit.png`. Check each screen against the `in-use` mock-ups.
