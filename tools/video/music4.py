"""Spotlight music v4: 100 bpm, C major pentatonic (Inlay's scale), arranged to the scene list so the
rises and falls follow the picture. More drive than v3: 16th hats, clap on 2 and 4, a bouncing bass,
a lead that doubles up in the busiest scenes, a break for the sound packs, a drum stop before the end chord."""
import numpy as np, json, sys
SR=44100; BPM=100; B=60/BPM; BAR=4*B
SC=json.loads(sys.argv[1]); DUR=float(sys.argv[2])+0.6; N=int(SR*DUR)
rng=np.random.default_rng(11)
C4=261.63; R={'C':1,'D':9/8,'E':5/4,'G':3/2,'A':5/3}
f=lambda n,o: C4*R[n]*2**(o-4)
T={k:np.zeros(N) for k in ['kick','clap','hat','bass','pad','lead','arp','fx']}
def add(tr,start,x,g=1.0):
    i=int(start*SR)
    if i>=N or i<0: return
    x=x[:N-i]; T[tr][i:i+len(x)]+=x*g
def kick(): n=int(.4*SR); tt=np.arange(n)/SR; fr=42+90*np.exp(-tt/.04); return (np.sin(2*np.pi*np.cumsum(fr)/SR)*np.exp(-tt/.16)+0.5*rng.standard_normal(n)*np.exp(-tt/.004))*np.clip(tt/.001,0,1)
def clap():
    n=int(.22*SR); tt=np.arange(n)/SR; x=np.zeros(n)
    for d in (0,.011,.022):
        i=int(d*SR); x[i:]+=rng.standard_normal(n-i)*np.exp(-np.arange(n-i)/SR/.012)
    x+=rng.standard_normal(n)*np.exp(-tt/.08)*0.6; return x
def hat(o=False): n=int((.22 if o else .06)*SR); x=np.diff(np.concatenate([[0],rng.standard_normal(n)])); return x*np.exp(-np.arange(n)/SR/(.08 if o else .013))
def bass(fr,dur): n=int(dur*SR); tt=np.arange(n)/SR; x=np.sin(2*np.pi*fr*tt)+0.3*np.sin(4*np.pi*fr*tt)+0.08*np.sign(np.sin(2*np.pi*fr*tt)); return x*np.clip(tt/.008,0,1)*np.clip((dur-tt)/.05,0,1)
def pluck(fr,dur=.6,bright=1.0): n=int(dur*SR); tt=np.arange(n)/SR; x=np.sin(2*np.pi*fr*tt)+.4*bright*np.sin(4*np.pi*fr*tt)*np.exp(-tt/.1)+.14*bright*np.sin(6*np.pi*fr*tt)*np.exp(-tt/.05); return x*np.exp(-tt/.22)*np.clip(tt/.003,0,1)
def pad(notes,dur): n=int(dur*SR); tt=np.arange(n)/SR; x=sum(np.sin(2*np.pi*f(*k)*tt)+.2*np.sin(2*np.pi*2*f(*k)*tt+.4) for k in notes); return x*np.clip(tt/.6,0,1)*np.clip((dur-tt)/.5,0,1)
def riser(dur=1.4): n=int(dur*SR); tt=np.arange(n)/SR; x=np.convolve(rng.standard_normal(n),np.ones(40)/40,'same'); return x*(tt/dur)**2
def crash(): n=int(1.2*SR); tt=np.arange(n)/SR; x=np.diff(np.concatenate([[0],rng.standard_normal(n)])); return x*np.exp(-tt/.35)
prog=[(('C',3),('G',3),('E',4),('A',4),('D',5)), (('A',2),('E',3),('C',4),('G',4),('D',5)), (('G',2),('D',3),('G',3),('E',4),('A',4)), (('E',2),('G',2),('D',3),('E',3),('A',3))]
roots=[f('C',2),f('A',1),f('G',1),f('E',2)/2]
motif=[('E',5),('G',5),('A',5),('G',5),('D',5),('E',5),('C',5),('D',5)]
answer=[('A',4),('C',5),('D',5),('C',5),('G',4),('A',4)]
arpn=[('C',5),('E',5),('G',5),('A',5),('C',6),('A',5),('G',5),('E',5)]
bar_of=lambda k: SC[k][0]/BAR
cuts=sorted({round(bar_of(k),3) for k in SC})
hook_end=bar_of('type'); peak=(bar_of('looks'),bar_of('sound')); brk=(bar_of('sound'),bar_of('lang')); endbar=bar_of('end')
NB=int(np.ceil(DUR/BAR))
for bar in range(NB):
    t0=bar*BAR; ch=prog[bar%4]; root=roots[bar%4]
    intro=bar<hook_end; inbreak=brk[0]<=bar<brk[1]; ending=bar>=endbar; inpeak=peak[0]<=bar<peak[1]
    lastbar = bar==endbar-1                                   # drums stop in the last beat before the end card
    if not ending: add('pad',t0,pad(ch,BAR+.6),.11)
    # bass: octave bounce on eighths once the groove is in
    if not ending:
        if intro or inbreak:
            add('bass',t0,bass(root,B*1.8),.4); add('bass',t0+2*B,bass(root,B*1.8),.4)
        else:
            for k in range(8):
                fr = root*2 if k%2 else root; dur=B/2*0.9
                if k==6: fr=root*1.5
                add('bass',t0+k*B/2,bass(fr,dur),.5 if k%2==0 else .36)
    # drums
    if not intro and not inbreak and not ending:
        for b in range(4):
            if lastbar and b==3: break
            if b in (0,2): add('kick',t0+b*B,kick(),1.0)
            if b in (1,3): add('clap',t0+b*B,clap(),.28)
            if bar%2==1 and b==3: add('kick',t0+b*B+B/2,kick(),.7)    # the kick that pushes into the next bar
            for h in range(4): add('hat',t0+b*B+h*B/4,hat(),[.11,.05,.08,.05][h]*(1.15 if inpeak else 1))
        if not lastbar: add('hat',t0+3.5*B,hat(True),.07)
        if (bar+1) in cuts and not lastbar:
            for k in range(4): add('clap',t0+3*B+k*B/4,clap(),.12+0.05*k)
    if intro:
        for b in range(4):
            add('hat',t0+b*B,hat(),.06); add('hat',t0+b*B+B/2,hat(),.035)
            if b==0: add('kick',t0,kick(),.6)
    if inbreak:
        for b in range(4): add('hat',t0+b*B+B/2,hat(),.04)
    # melody
    if not ending:
        if inbreak:
            for k in range(2): add('lead',t0+k*2*B,pluck(f(*ch[2]),1.0),.07)
        else:
            notes = motif if bar%2==1 else answer; step = B/2 if bar%2==1 else B/2*1.3333
            for k,nn in enumerate(notes):
                add('lead',t0+k*step,pluck(f(*nn),.6,1.2 if inpeak else 1.0),.17 if bar%2==1 else .12)
                if inpeak: add('lead',t0+k*step,pluck(f(*nn)*2,.5,.8),.06)       # doubled an octave up at the peak
    if inpeak or (not intro and not inbreak and not ending and bar%4>=2):
        for k in range(16): add('arp',t0+k*B/4,pluck(f(*arpn[k%8])*2,.35),.03 if inpeak else .018)
for c in cuts:
    if c>0 and c<endbar: add('fx',c*BAR-1.4,riser(1.4),.24); add('hat',c*BAR,crash(),.16)
e0=endbar*BAR
add('kick',e0,kick(),.9); add('hat',e0,crash(),.2)
add('pad',e0,pad((('C',3),('G',3),('E',4),('A',4),('D',5)),DUR-e0),.15)
add('lead',e0,pluck(f('C',5),1.8),.22); add('lead',e0+.02,pluck(f('G',5),1.8),.15); add('lead',e0+B,pluck(f('E',6),2.0),.09)
duck=np.ones(N)
for bar in range(NB):
    for b in (0,2):
        i=int((bar*BAR+b*B)*SR); n=min(int(.3*SR),N-i)
        if n>0: duck[i:i+n]=np.minimum(duck[i:i+n],1-.55*np.exp(-np.arange(n)/SR/.11))
T['pad']*=duck; T['arp']*=duck; T['bass']*=0.6+0.4*duck; T['lead']*=0.8+0.2*duck
m=sum(T.values())
F=np.fft.rfft(m); fr=np.fft.rfftfreq(N,1/SR); F*=1/np.sqrt(1+(fr/8500)**4); m=np.fft.irfft(F,N)
# gentle bus compression so the louder parts don't jump out
env=np.abs(m); k=int(SR*0.02); env=np.convolve(env,np.ones(k)/k,'same'); m=m/(1+2.5*np.maximum(0,env-0.18))
fade=np.ones(N); fade[:int(.5*SR)]=np.linspace(0,1,int(.5*SR)); fade[-int(2.5*SR):]=np.linspace(1,0,int(2.5*SR))**1.5
m*=fade; m/=np.max(np.abs(m)); np.save('music4.npy',m.astype(np.float32)); print('music',round(DUR,1),'s, cuts at bars',cuts)
