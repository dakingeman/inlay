"""Round 42: only the motif chimes an octave lower (CHIMES_DEEP), at stems6's level, as 'motif-deep'.
    CHIMES_DEEP=1 python motifdeep.py <cues.md> <video> <tracks-folder> (the folder's own motif.wav sets the level)"""
import os, sys
import numpy as np, videosound as V, layers as LY
from videosound import SR
cues_p, video, out = sys.argv[1:4]; cues = V.read_cues(cues_p)[video]; L = V.LENGTH[video]
LY.LAYERS = ["motif"]; m = V.clean_low_end(LY.layers(cues, video)); ref = V.read_wav(os.path.join(out, "motif.wav"))
import pyloudnorm as pyln; M = pyln.Meter(SR); g = V.db(M.integrated_loudness(ref.T) - M.integrated_loudness(m[:, :ref.shape[1]].T))
y = V.fade_to_silence(m * g, L); V.save24(os.path.join(out, "motif-deep.wav"), y[:, :ref.shape[1]]); print("motif-deep written")
