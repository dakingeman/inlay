"""Soft pack: built from the reference button sounds' recipe.
Every sound = a short low THUD (weight, no pitch) + a crisp CLICK (texture), layered
at the same instant, then silence. Nothing rings longer than a few cycles, so it can
never sound like a pipe or a note. Material 'glints' (glass / metal) are tiny extras."""
import numpy as np, wave
SR = 44100

def bp_res(x, f, decay_ms):
    """Two-pole resonator: rings at f and dies away in decay_ms (bandwidth from decay)."""
    r = np.exp(-1/(SR*decay_ms/1000)); w = 2*np.pi*f/SR
    a1, a2 = -2*r*np.cos(w), r*r; g = (1-r*r)/2
    y = np.zeros_like(x); y1=y2=0.0; x2=0.0; x1=0.0
    for i, xi in enumerate(x):
        yi = g*(xi - x2) - a1*y1 - a2*y2
        x2, x1, y2, y1 = x1, xi, y1, yi; y[i] = yi
    return y

def lp(x, f, q=0.7):
    w0 = 2*np.pi*min(f, SR*0.45)/SR; al = np.sin(w0)/(2*q); c = np.cos(w0)
    b0,b1,b2 = (1-c)/2/(1+al), (1-c)/(1+al), (1-c)/2/(1+al); a1, a2 = -2*c/(1+al), (1-al)/(1+al)
    y = np.zeros_like(x); x1=x2=y1=y2=0.0
    for i, xi in enumerate(x):
        yi = b0*xi+b1*x1+b2*x2-a1*y1-a2*y2; x2,x1,y2,y1 = x1,xi,y1,yi; y[i] = yi
    return y
def hp(x, f): return x - lp(x, f)

def excite(n, rng, ms):
    """The strike: a noise burst shaped like a fingertip: very fast rise, fast fall."""
    t = np.arange(n)/SR
    return rng.standard_normal(n) * np.exp(-t/(ms/1000)) * np.clip(t/0.0003, 0, 1)

def press(*, seed, thud_f=(170, 640), thud=1.0, thud_decay=2.6,
          click_f=(1900, 2850), click=1.0, click_decay=(2.2, 1.4),
          glint_f=None, glint=0.0, glint_decay=5.0, air=0.08, ms=60):
    rng = np.random.default_rng(seed)
    n = int(SR*ms/1000)
    s = np.zeros(n)
    # THUD: two very damped low resonances + a soft body; 1-4 cycles long, so no pitch
    e = excite(n, rng, 1.2)
    for f, amp in zip(thud_f, (1.0, 0.55)):
        s += thud*amp*bp_res(e, f*rng.uniform(0.97, 1.03), thud_decay)
    # CLICK: the crisp contact, the texture you actually notice
    e = excite(n, rng, 0.35)
    for f, d, amp in zip(click_f, click_decay, (1.0, 0.6)):
        s += click*amp*bp_res(e, f*rng.uniform(0.97, 1.03), d)*0.9
    # GLINT: an optional tiny material highlight (glass / metal), still short
    if glint_f:
        s += glint*bp_res(excite(n, rng, 0.2), glint_f, glint_decay)*0.5
    # AIR: a whisper of high detail, first millisecond only
    s += air*hp(rng.standard_normal(n), 5000)*np.exp(-np.arange(n)/SR/0.0005)*0.3
    s = lp(lp(s, 6500), 6500)                      # nothing harsh
    return hp(s, 110)                    # nothing the Portal speaker can't play cleanly

def felt(s):
    x = hp(s, 250); x = x + 0.6*hp(x, 2500)
    return 20*np.log10(np.sqrt(np.mean(x[:int(SR*0.05)]**2)) + 1e-9)
def master(s, target):
    s = s.copy(); fade = int(SR*0.006); s[-fade:] *= np.linspace(1, 0, fade)**2
    s -= s.mean(); s *= 10**((target - felt(s))/20)
    pk = np.max(np.abs(s)); lim = 10**(-1/20)
    return s*lim/pk if pk > lim else s
def save(name, s):
    with wave.open(name, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(s, -1, 1)*32767).astype('<i2').tobytes())

LEVEL = {'move': -24, 'type': -16, 'space': -16, 'delete': -17, 'on': -14.5, 'off': -15.5,
         'lock': -13.5, 'select_all': -15, 'confirm': -15.5, 'back': -18}

def make(name, tone=1.0, weight=1.0):
    """tone scales the click pitch (brighter/darker), weight scales the thud (heft)."""
    T = lambda *f: tuple(x*tone for x in f)
    cues = {
        # light touch: only the click, very small
        'move':   press(seed=1, thud=0.0, click_f=T(2600, 3900), click_decay=(1.1, 0.8), air=0.04, ms=25),
        # keys: the reference recipe. Bigger keys = more thud, slightly darker click
        'type':   press(seed=2, thud=0.9*weight, click_f=T(1900, 2850)),
        'space':  press(seed=3, thud=1.3*weight, thud_f=(150, 520), thud_decay=3.2, click_f=T(1500, 2400), click=0.8),
        'delete': press(seed=4, thud=1.1*weight, thud_f=(160, 560), click_f=T(1350, 2150), click=0.85),
        # switches / triggers: the heft is in the thud; on = crisp, off = muted
        'on':     press(seed=5, thud=1.6*weight, thud_f=(150, 560), thud_decay=3.4, click_f=T(2100, 3150), click=1.1, ms=70),
        'off':    press(seed=6, thud=1.4*weight, thud_f=(150, 520), thud_decay=3.0, click_f=T(1400, 2100), click=0.7, air=0.05, ms=70),
        # caps lock: heaviest, with a small brushed-metal glint
        'lock':   press(seed=7, thud=1.9*weight, thud_f=(140, 520), thud_decay=4.0, click_f=T(2000, 3000), click=1.1,
                        glint_f=4200*tone, glint=0.35, glint_decay=6, ms=90),
        # select all: a glassy highlight on a normal press
        'select_all': press(seed=8, thud=1.2*weight, click_f=T(2300, 3450), glint_f=5200*tone, glint=0.3, glint_decay=4.5, ms=80),
        # enter: thick wood, a rounder mid click and a full thud
        'confirm': press(seed=9, thud=1.5*weight, thud_f=(160, 600), thud_decay=3.4, click_f=T(1100, 1800), click=0.9, click_decay=(2.8, 1.8), ms=80),
        # back: soft and muted, mostly thud
        'back':   press(seed=10, thud=1.1*weight, thud_f=(150, 500), click_f=T(1200, 1900), click=0.45, air=0.03, ms=60),
    }
    for cue, s in cues.items():
        save(f'{name}_{cue}.wav', master(s, LEVEL[cue]))
    for i in range(1, 4):   # four takes for letters, like real keys
        save(f'{name}_type{i}.wav', master(press(seed=30+i, thud=0.9*weight,
             click_f=T(1900*(1+0.025*(i-2)), 2850*(1+0.025*(i-2)))), LEVEL['type']))

