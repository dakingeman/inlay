"""layers2.py was never committed (round 30s). groove2.py and depth.py import it, but the approved chain (stems6.py,
motifdeep.py, arrange.py) only needs the import to work: chime is the same soft chime as layers.py's, and check is only
used by groove2/depth's own experiment entry points, which stop here instead of running unchecked."""
import layers as _L

chime = _L.chime

def check(*a, **k):
    raise SystemExit("layers2.check isn't in the repo: run the approved chain (stems6.py, motifdeep.py, arrange.py) instead")
