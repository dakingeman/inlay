# Video sound

Inlay's video music and the app sounds on top, as code. Read [docs/brand/MUSIC.md](../../../docs/brand/MUSIC.md) first:
the song, its shape, the rules, and how to fit it to a new video in four commands.

- `arrange.py` + `profiles/*.json`: the adaptive engine; a profile says how much energy each scene has
- `stems6.py`, `motifdeep.py`: render the song's parts for a video (`TUNING=equal END_SHIFT=1`)
- `videosound.py`: the grid, the instruments' building blocks, the app's key sounds, every check
- `bloom.py`, `layers.py`, `groove2.py`, `energy.py`, `depth.py`, `stems.py`, `stems5.py`: the instruments
- `mux.py`: into the video, with proof the sound survived; `audit.py`, `rubscan.py`: measuring
- `master.py`, `master6.py`: the round 40-42 mixes, kept so every approved version can be rebuilt
- `cues.md`: the cue sheets of the remade 1.2.0 videos (from `docs/visuals-src/finish-video.py`)

Needs Python with numpy, scipy, pyloudnorm, mosqito, soundfile, and ffmpeg.
