// Renders the frame lists from logo_motion.py to PNGs with the pre-installed Chromium.
// Usage: node render_motion.js DIR WIDTH name [name ...]   ->  DIR/name/f000.png ...
const {chromium} = require('playwright'); const fs = require('fs'); const path = require('path');
const [dir, width, ...names] = process.argv.slice(2);
(async () => {
  const b = await chromium.launch(); const p = await b.newPage({viewport: {width: +width, height: 800}});
  await p.setContent('<body style="margin:0"><div id=w></div></body>');
  for (const name of names) {
    const frames = JSON.parse(fs.readFileSync(path.join(dir, name + '.json')));
    const out = path.join(dir, name); fs.mkdirSync(out, {recursive: true});
    for (let i = 0; i < frames.length; i++) {
      // the page behind the picture gets the picture's own ground: a frame whose height isn't a whole number of
      // pixels would otherwise blend its last row with the page (found 30 Oct: a white line under the dark loop)
      await p.evaluate(s => { const bg = (s.match(/<rect[^>]*fill="(#[0-9A-Fa-f]{6})"/) || [])[1] || 'transparent';
        document.body.style.background = bg;
        document.getElementById('w').innerHTML = s.replace('<svg ', `<svg id=s width="${innerWidth}" `); }, frames[i]);
      await (await p.$('#s')).screenshot({path: path.join(out, 'f' + String(i).padStart(3, '0') + '.png')});
    }
  }
  await b.close();
})();
