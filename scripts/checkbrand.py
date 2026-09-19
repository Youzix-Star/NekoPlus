#!/usr/bin/env python3
"""Count a string in an APK's resources/manifest/dex in both encodings.

The resource and manifest string pools are UTF-16, so an ASCII-only grep reports a false "0" —
that mistake is why a wrong "no other product's name anywhere" claim got made once.
Usage: checkbrand.py <apk> <string> [<string> ...]
"""
import sys, zipfile

apk = sys.argv[1]
needles = sys.argv[2:]
with zipfile.ZipFile(apk) as z:
    names = [n for n in z.namelist() if n in ("resources.arsc", "AndroidManifest.xml") or n.startswith("classes") and n.endswith(".dex")]
    blobs = {n: z.read(n) for n in names}
for needle in needles:
    print(f"=== {needle!r} ===")
    for enc in ("utf-8", "utf-16-le"):
        data = needle.encode(enc)
        row = "  ".join(f"{n.split('/')[-1]}={blobs[n].count(data)}" for n in names)
        print(f"  {enc:10} {row}")
