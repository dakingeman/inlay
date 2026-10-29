// Renders brand PNGs from the SVGs in docs/brand/ with the pre-installed Chromium.  Usage: node png.js
const {chromium} = require('playwright'); const fs = require('fs'); const path = require('path');
const BRAND = path.join(__dirname, '../../docs/brand');
const SIZES = {  // file: width in px
  'inlay-icon': 512, 'inlay-symbol': 512, 'inlay-symbol-black': 512, 'inlay-symbol-white': 512,
  'inlay-lockup-dark': 1600, 'inlay-lockup-light': 1600, 'inlay-lockup-clear-dark': 1600, 'inlay-lockup-clear-light': 1600,
  'inlay-tv-banner': 640,
};
(async () => {
  const b = await chromium.launch(); const p = await b.newPage({viewport: {width: 1700, height: 1200}});
  for (const [name, w] of Object.entries(SIZES)) {
    const svg = fs.readFileSync(path.join(BRAND, name + '.svg'), 'utf8').replace('<svg ', `<svg id=s width="${w}" `);
    await p.setContent(`<body style="margin:0;background:transparent">${svg}</body>`);
    await (await p.$('#s')).screenshot({path: path.join(BRAND, 'png', `${name}-${w}.png`), omitBackground: true});
  }
  await b.close();
})();
