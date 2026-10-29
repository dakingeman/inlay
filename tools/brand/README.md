# Brand tools

Everything in `docs/brand/` (and the app's TV banner) is generated here. The finished files are already in the repo, so you only need these to change the logo. The rules they follow are in [docs/brand/BRAND.md](../../docs/brand/BRAND.md).

| File | What it does |
|---|---|
| `logo.py` | The symbol's geometry on Android's 108-unit icon grid |
| `wordmark.py` | The name, inlay\|, drawn from Atkinson Hyperlegible Next Bold (the app's own font file) with fontTools and HarfBuzz; all spacing is measured from the letters |
| `kit.py` | Writes the brand SVGs into `docs/brand/`, the app's `tv_banner.xml` and `docs/visuals-src/hero-parts/lockup-clear.svg` |
| `png.js` | Renders the brand PNGs from those SVGs |
| `logo_motion.py` | The logo animation, as a list of SVG frames per theme |
| `render_motion.js` | Renders those frames to PNGs (then ffmpeg makes the GIF and MP4) |
| `readme_header.py` | The README header: the animation and the tagline on GitHub's page colours |

To rebuild everything (needs Python with fontTools, uharfbuzz and Pillow; Node with Playwright; ffmpeg):

```
python3 kit.py && node png.js
python3 logo_motion.py OUT && node render_motion.js OUT 1440 logo-dark logo-light && node render_motion.js OUT 600 logo-github-dark logo-github-light
python3 readme_header.py OUT
```

`archive/` keeps earlier explorations and why they lost.
