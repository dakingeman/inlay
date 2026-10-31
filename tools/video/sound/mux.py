"""Put a finished WAV into a video without touching the picture, then prove the sound survived.

    python tools/video/sound/mux.py <video.mp4> <sound.wav> <out.mp4> [effects-stem.wav] [web width]

The picture is copied as is (no re-encode). The sound is AAC (plays everywhere: browsers, GitHub,
phones) with ffmpeg's 'fast' coder at 320 kbit/s: on this kind of clean, tonal music its default
coder leaves errors only ~35 dB under the music, which is audible as fuzz on the chords; 'fast'
leaves them ~60 dB under. Its tricks are switched off too: noise substitution (PNS) replaces
'noise-like' bands with generated noise, which on key clicks over soft music is heard as fuzz;
TNS and intensity stereo go with it. Errors then stay ~50 dB under the music even during clicks. After writing, the file is decoded again and compared with the WAV,
sample for sample. In the moments with music only, the error must be at least 45 dB under the
music; while a key click plays (found from the effects stem, if given) at least 40 dB (35 dB from
2.5-6 kHz: a click is noise, and the encoder spends its bits on the click, which hides it).
Otherwise the file is deleted and the script fails. An error quieter than -75 dBFS passes regardless: that is
below hearing on any speaker (1B has no music above 2.5 kHz at all, so there nothing is "under" it)."""
import os, subprocess, sys, wave
import numpy as np
from scipy.signal import stft

def mux(video, snd, out, fxstem=None, scale=None):
    vf = ["-vf", f"scale={scale}:-2", "-c:v", "libx264", "-preset", "veryfast", "-crf", "28"] if scale else ["-c:v", "copy"]
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", video, "-i", snd, "-map", "0:v", "-map", "1:a"] + vf +
                   ["-c:a", "aac", "-b:a", "320k", "-aac_coder", "fast", "-aac_pns", "0", "-aac_tns", "0", "-aac_is", "0", "-movflags", "+faststart", out], check=True)
    import videosound as _V; x = _V.read_wav(snd)
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", out, "-f", "s16le", "-ac", "2", "-ar", "48000", "-"], capture_output=True).stdout
    y = np.frombuffer(raw, "<i2").reshape(-1, 2).T / 32768
    d = int(np.correlate(y[0, :48000 * 3], x[0, :48000 * 3 - 4096], "valid").argmax())
    n = min(x.shape[1], y.shape[1] - d); yy, xx = y[:, d:d + n], x[:, :n]
    if fxstem:
        fx = _V.read_wav(fxstem)
    report, bad = [], []
    from scipy.signal import butter, sosfilt
    H = 2048; nblk = n // H
    env = np.abs(fx).max(axis=0)[:nblk * H].reshape(nblk, H).max(1) if fxstem else np.zeros(nblk)
    click = np.convolve(env > 1e-3, np.ones(3), "same") > 0                     # a block with a click, or next to one
    for lo, hi, lim_m, lim_c in ((20, 2500, -45, -40), (2500, 6000, -45, -35)):
        sos = butter(4, [lo, hi], "bandpass", fs=48000, output="sos")
        for ch in (0, 1):
            e = sosfilt(sos, (yy - xx)[ch])[:nblk * H].reshape(nblk, H); x2 = sosfilt(sos, xx[ch])[:nblk * H].reshape(nblk, H)
            pe, px = (e ** 2).mean(1), (x2 ** 2).mean(1)
            for nm, msk, lim in (("music only", ~click, lim_m), ("during clicks", click, lim_c)):
                if not msk.any(): continue
                rel = 10 * np.log10(pe[msk].sum() / (px[msk].sum() + 1e-20)); absl = 10 * np.log10(pe[msk].mean() + 1e-20)
                report.append(f"{lo}-{hi} Hz {nm}: {rel:.0f} dB under ({absl:.0f} dBFS)")
                if rel > lim and absl > -75: bad.append(f"{lo}-{hi} Hz {nm}: error {rel:.1f} dB under the music (limit {lim}), {absl:.0f} dBFS")
    if bad: os.remove(out); raise SystemExit(f"{out}: the sound did not survive encoding: " + "; ".join(dict.fromkeys(bad)))
    print(f"{os.path.basename(out)}: sound survived encoding (" + ", ".join(dict.fromkeys(report)) + ")")

if __name__ == "__main__":
    mux(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else None, int(sys.argv[5]) if len(sys.argv) > 5 else None)
