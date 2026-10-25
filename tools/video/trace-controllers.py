"""
Rebuilds the PlayStation (DualShock 4) and Switch Pro outlines used by controller.js (CTRL2).

Inputs (next to this script):
- ds4-layout-points.json: points sampled from "Dualshock 4 Layout.svg" by Tokyoship,
  Wikimedia Commons, CC BY 3.0 (https://commons.wikimedia.org/wiki/File:Dualshock_4_Layout.svg).
- switch-pro-clean.png / sw_contour.npy / sw_face.npy: made from Nintendo's Pro Controller
  diagram (support page) with the red callouts painted out. The photo itself is Nintendo's and
  is NOT kept in this repo; it was only used as a tracing reference.

Output: ctl_paths2.json, pasted into controller.js as CTRL2. Everything is fitted to the same
657 x 423 frame and line style as the Xbox outline. L1/R1 and L2/R2 are raised so every shoulder
button shows clearly above the body and is easy to see when it lights up.
"""
import cv2, numpy as np, json
from scipy.ndimage import gaussian_filter1d

def resample(pts, step):
    pts=np.asarray(pts,float); d=np.r_[0,np.cumsum(np.hypot(*np.diff(pts,axis=0).T))]
    t=np.arange(0,d[-1],step); return np.c_[np.interp(t,d,pts[:,0]),np.interp(t,d,pts[:,1])]

def smooth(pts, sigma, closed=True):
    mode='wrap' if closed else 'nearest'
    return np.c_[gaussian_filter1d(pts[:,0],sigma,mode=mode),gaussian_filter1d(pts[:,1],sigma,mode=mode)]

def to_path(pts, closed=True, every=1):
    p=pts[::every]
    if closed: P=np.vstack([p[-1],p,p[0],p[1]])
    else: P=np.vstack([p[0],p,p[-1]])
    d=f"M{P[1][0]:.1f},{P[1][1]:.1f}"
    for i in range(1,len(P)-2):
        p0,p1,p2,p3=P[i-1],P[i],P[i+1],P[i+2]
        c1=p1+(p2-p0)/6; c2=p2-(p3-p1)/6
        d+=f" C{c1[0]:.1f},{c1[1]:.1f} {c2[0]:.1f},{c2[1]:.1f} {p2[0]:.1f},{p2[1]:.1f}"
    return d+('Z' if closed else '')

out={}
# ---------------- DualShock 4 (vector source, Wikimedia "Dualshock 4 Layout.svg", CC BY 3.0, Tokyoship)
ds=json.load(open('ds4-layout-points.json'))
k=1.0; X=lambda x:(x-300)*k+328.5; Y=lambda y:(y-370.1)*k+420
T=lambda pts: np.array([[X(x),Y(y)] for x,y in pts])
P={}
BUMP_UP=11; TRIG_UP=34          # raise L1/R1 and L2/R2 so both show clearly above the body and light up visibly
for name,pts in ds.items():
    a=resample(T(pts),2.0); a=smooth(a,1.0)
    if name in ('l1','r1'): a[:,1]-=BUMP_UP*k
    P[name]=to_path(a,True,3)
# triggers: the bumper shape, raised, peeking out above it
for b,t in [('l1','l2'),('r1','r2')]:
    a=T(ds[b]); top=a[a[:,1]<=np.median(a[:,1])]
    a2=a.copy(); a2[:,1]-=TRIG_UP*k
    P[t]=to_path(smooth(resample(a2,2.0),1.0),True,3)
P['meta']={'k':k,'face':{'Y':[X(472.2),Y(85.1)],'A':[X(472.2),Y(164.9)],'X':[X(432.0),Y(124.8)],'B':[X(513.0),Y(124.8)],'r':17.05*k},
  'sticks':{'LS':[X(213.4),Y(197.9)],'RS':[X(388.3),Y(197.9)],'ring':49.2*k,'cap':31.9*k,'inner':19.5*k},
  'ps':[X(300),Y(203.4),13.8*k],'labels':{'l2':[X(133),Y(6)],'r2':[X(467),Y(6)],'l1':[X(133),Y(27)],'r1':[X(467),Y(27)]}}
out['ps']=P

# ---------------- Switch Pro (traced from Nintendo's support diagram photo)
im=cv2.imread('switch-pro-clean.png'); g=cv2.cvtColor(im,cv2.COLOR_BGR2GRAY)
c=np.load('sw_contour.npy').astype(float)
CX=400.0
# keep the right half (clean edge), mirror it to the left
i0=np.argmin(np.where(c[:,0]>=CX, c[:,1], 1e9))           # top-centre-ish start
# walk the traced outline in its own order: from where it crosses the centre line at the top,
# round the right-hand side, to where it crosses it at the bottom
n=len(c); near=np.where(np.abs(c[:,0]-CX)<1.5)[0]
itop=near[np.argmin(c[near,1])]; ibot=near[np.argmax(c[near,1])]
arc1=np.roll(c,-itop,axis=0)[:((ibot-itop)%n)+1]
arc2=np.roll(c,-ibot,axis=0)[:((itop-ibot)%n)+1][::-1]
r=arc1 if arc1[:,0].mean()>CX else arc2
left=r[::-1].copy(); left[:,0]=2*CX-left[:,0]
full=np.vstack([r,left])
full=smooth(resample(full,2.0),2.5)
s=517/800*1.02; SX=lambda x:(x-CX)*s+328.5; SY=lambda y:(y-591)*s+420
ST=lambda a: np.c_[SX(a[:,0]),SY(a[:,1])]
body=ST(full)
SP={'body':to_path(body,True,3)}
# face line: where the dark top rim/bumpers end and the lighter face starts (right half, mirrored)
sil=np.zeros(g.shape,np.uint8); cv2.fillPoly(sil,[full.astype(np.int32)],255)
xs=[];ys=[]
for x in range(400,780,4):
    col=np.where(sil[:,x]>0)[0]
    if len(col)==0: continue
    top=col[0]; seg=g[top:top+90,x].astype(int)
    darkseen=False; yy=None
    for i,v in enumerate(seg):
        if v<100: darkseen=True
        if darkseen and v>115: yy=top+i; break
    if yy is not None: xs.append(x); ys.append(yy)
xs=np.array(xs,float); ys=gaussian_filter1d(np.array(ys,float),2)
print('face line', list(zip(xs[::8].round(),ys[::8].round())))
np.save('sw_face.npy',np.c_[xs,ys])
SP['meta']={'s':s,'CX':CX}
json.dump({'SX':[CX,s],'face':np.c_[xs,ys].tolist()},open('sw_dbg.json','w'))
out['switch']=SP
json.dump(out,open('ctl_paths2.json','w'))
print({k:len(v) for k,v in out['ps'].items() if k!='meta'})

# ---- Switch: face line across the whole top (mirrored), and the R / ZR shoulder shapes
face=np.load('sw_face.npy')
fr=face[face[:,0]<=672]                       # stop before the scan runs off the shoulder
fl=fr[::-1].copy(); fl[:,0]=2*CX-fl[:,0]
fline=np.vstack([fl,fr[1:]])
SP['faceline']=to_path(ST(smooth(resample(fline,3.0),2,False)),False,2)
# right shoulder: silhouette from the notch (x≈540) round to where it meets the face line's end
fx_end,fy_end=fr[-1]
pts=full
top_idx=[i for i,p in enumerate(pts) if p[1]<fy_end+2 and p[0]>=540]
seg=pts[top_idx]
seg=seg[np.argsort(seg[:,0])]
# include the outer side down to the face line's end height
side=pts[(pts[:,0]>fx_end)&(pts[:,1]>=fy_end-2)&(pts[:,1]<=fy_end+18)]
side=side[np.argsort(side[:,1])]
ftop=fr[fr[:,0]>=540][::-1]
rb=np.vstack([seg,side,ftop])
SP['rb']=to_path(ST(smooth(resample(rb,2.0),1.5)),True,2)
lb=rb.copy(); lb[:,0]=2*CX-lb[:,0]
SP['lb']=to_path(ST(smooth(resample(lb[::-1],2.0),1.5)),True,2)
# triggers: an even band riding on the bumper's top edge
band=resample(seg,2.0); band=smooth(band,2,False)
up=band.copy(); up[:,1]-=24
rt=np.vstack([band,up[::-1]])
SP['rt']=to_path(ST(rt),True,2)
lt=rt.copy(); lt[:,0]=2*CX-lt[:,0]
SP['lt']=to_path(ST(lt[::-1]),True,2)
mid=band[len(band)//2]
SP['meta']={'s':s,
  'sticks':{'LS':[SX(180),SY(206)],'RS':[SX(504),SY(315)],'ring':62*s,'cap':45*s,'inner':31*s},
  'face':{'Y':[SX(609),SY(152)],'X':[SX(546),SY(204)],'B':[SX(672),SY(204)],'A':[SX(609),SY(260)],'r':29*s},
  'dpad':[SX(277),SY(315),61*s,21*s],
  'minus':[SX(302),SY(146),15*s],'plus':[SX(498),SY(146),15*s],'capture':[SX(343),SY(205),14*s],'home':[SX(456),SY(204),15*s],
  'labels':{'lb':[SX(2*CX-mid[0]),SY(mid[1]+14)],'rb':[SX(mid[0]),SY(mid[1]+14)],'lt':[SX(2*CX-mid[0]),SY(mid[1]-12)],'rt':[SX(mid[0]),SY(mid[1]-12)]}}
out['switch']=SP
json.dump(out,open('ctl_paths2.json','w'))
print('switch', {k:len(v) for k,v in SP.items() if k!='meta'}, 'face end', fx_end, fy_end)
