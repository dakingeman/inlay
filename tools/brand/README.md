# Brand tools

`logo.py` holds the icon geometry (108-unit adaptive-icon grid). `kit.py` builds the SVG kit in `docs/brand/` from it.
`kit.py` also needs `wordmark_raw.txt`: the word "Inlay" in Atkinson Hyperlegible Next Bold, turned into outlines with fontTools
(instantiate the variable font at wght 700, draw each glyph with SVGPathPen). The finished files are already in `docs/brand/`,
so you only need these to change the logo.
