# Video tools

The spotlight video is drawn in code, frame by frame, so it can be re-cut when the app changes.

- `kb2.js` draws the keyboard exactly as the app does (sizes, colours, badges, themes, TV mode).
- `spotlight4.js` is the storyboard: a list of button presses drives the highlight, the text, the lit controller and the sounds. Everything sits on a 100 bpm grid.
- `music4.py` writes the music to that grid (same scale as the app's sounds).
- `controller.js` draws the controller from `ctrl_paths.json`, an outline traced from a reference drawing (logo and share button left out).
- `frames.js` renders frames with Playwright; `qa.js` measures every frame for overlaps and off-screen elements.
- `whats-new.html` is the What's new screen as a clip, the same pages as `WhatsNew` in the app; `RC(t)` draws the frame at time t. It uses Roboto (Android's own font, Apache 2.0, the `roboto-*.woff2` files here) so it looks like the real screen.
- Fonts for the cards: copy `inter.ttf` and `atkinson_next.ttf` from `app/src/main/res/font` next to these files before rendering.
- The logo is embedded from `docs/brand/inlay-lockup-dark.svg`, never redrawn.

Render: `node frames.js s3-wide.html 60 fw`, then ffmpeg the frames with the mixed audio (see the chat log in the Brain project note).
