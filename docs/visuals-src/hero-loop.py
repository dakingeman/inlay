# Hero loop: python3 hero-loop.py <folder with s1.png, s1-blank.png, ...> "s1 s2 s3 s4" out.mp4  (HOLD=3.0 sets seconds per slide)
import numpy as np, subprocess, sys
from PIL import Image
D=sys.argv[1]; ORDER=sys.argv[2].split(); out=sys.argv[3]
FPS=30; HOLD=float(__import__("os").environ.get("HOLD","2.6")); OUT=0.18; IN=0.32
L=lambda n: np.asarray(Image.open(f'{D}/{n}.png').convert('RGB')).astype(np.float32)
full={s:L(s) for s in ORDER}; blank={s:L(s+'-blank') for s in ORDER}
m=np.zeros((900,1600,1),np.float32); m[248:670,313:1287]=1   # the keyboard panel: fades through; the rest crossfades
ease_in=lambda t:t*t; ease_out=lambda t:1-(1-t)**2
p=subprocess.Popen(['ffmpeg','-y','-loglevel','error','-f','rawvideo','-pix_fmt','rgb24','-s','1600x900','-r',str(FPS),'-i','-','-c:v','libx264','-preset','veryfast','-crf','18','-pix_fmt','yuv420p','-movflags','+faststart',out],stdin=subprocess.PIPE)
def put(a): p.stdin.write(np.clip(a,0,255).astype(np.uint8).tobytes())
for i,s in enumerate(ORDER):
  nx=ORDER[(i+1)%len(ORDER)]
  for _ in range(int(HOLD*FPS)): put(full[s])
  n1=int(OUT*FPS); n2=int(IN*FPS); N=n1+n2
  for k in range(N):
    g=(k+1)/N  # outside the panel: a straight crossfade over the whole change
    outside=full[s]*(1-g)+full[nx]*g
    if k<n1: t=ease_in((k+1)/n1); inside=full[s]*(1-t)+blank[s]*t
    else: t=ease_out((k-n1+1)/n2); inside=blank[nx]*(1-t)+full[nx]*t
    put(inside*m+outside*(1-m))
p.stdin.close(); p.wait()
