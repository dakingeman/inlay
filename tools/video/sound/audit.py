"""python audit.py <music.wav> [label] — the studio's measured audit of a music master."""
import sys, numpy as np, soundfile as sf, pyloudnorm as pyln
from scipy.signal import resample_poly
x,sr=sf.read(sys.argv[1]); lab=sys.argv[2] if len(sys.argv)>2 else sys.argv[1]
if x.ndim==1: x=np.stack([x,x],1)
m=pyln.Meter(sr); L=m.integrated_loudness(x)
tp=20*np.log10(np.abs(resample_poly(x,4,1,axis=0)).max()); mono=x.mean(1)
ml=L-m.integrated_loudness(np.stack([mono,mono],1))
st=[m.integrated_loudness(x[i:i+3*sr]) for i in range(0,len(x)-3*sr,int(1.5*sr))]; st=[v for v in st if v>-60]
X=np.abs(np.fft.rfft(mono))**2; f=np.fft.rfftfreq(len(mono),1/sr)
def band(a,b): return 10*np.log10(X[(f>=a)&(f<b)].sum()/np.log2(b/a)+1e-30)   # energy per octave (pink = flat)
ref=band(500,2000)
print(f"{lab}: {len(x)/sr:.1f} s · {L:.1f} LUFS · true peak {tp:.1f} dBTP · mono loss {ml:.1f} dB · loudness moves {max(st)-min(st):.1f} LU across the video")
print("  energy per octave vs 500-2000 Hz (a pink, natural balance reads 0):", "  ".join(f"{a}-{b} Hz {band(a,b)-ref:+.0f}" for a,b in ((40,120),(120,250),(250,500),(2000,5000),(5000,10000))))
# how it holds up on a phone speaker (high-pass 750 Hz): loudness lost
from scipy.signal import butter, sosfilt
ph=sosfilt(butter(4,750,'highpass',fs=sr,output='sos'),x,axis=0)
print(f"  on a phone speaker it plays {L-m.integrated_loudness(ph):.1f} dB quieter (the app's sounds lose about 6-10)")
