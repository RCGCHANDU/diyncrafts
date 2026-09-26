#!/usr/bin/env python3
"""Restore the six attributed demo clips to the local S3 mock.

The matching source, author, license, and adaptation credit are stored in each
video's description. Run from a host with ffmpeg and access to the S3 mock.
"""

import argparse
import json
import shutil
import subprocess
import tempfile
from pathlib import Path
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen

API = "https://commons.wikimedia.org/w/api.php"
USER_AGENT = "DIYCrafts demo recovery (https://github.com/RCGCHANDU/diyncrafts)"
S3 = "http://127.0.0.1:9090/diyncrafts-local/demo-videos"
SOURCES = {
    "origami": "Interchanging Origami.ogv",
    "pottery": "Pottery 01.webm",
    "wood-polishing": "Wood sculpture polishing 1.ogv",
    "basket-weaving": "The Art of Basket Weaving.webm",
    "treadle-sewing": "Sewing with a 1894 Singer sewing machine.webm",
    "knitting-stitches": "Knitting demo of two stitches.webm",
}


def source_info():
    query = urlencode({
        "action": "query", "prop": "imageinfo", "iiprop": "url|size|mime",
        "titles": "|".join("File:" + title for title in SOURCES.values()),
        "format": "json",
    })
    request = Request(API + "?" + query, headers={"User-Agent": USER_AGENT})
    with urlopen(request, timeout=30) as response:
        pages = json.load(response)["query"]["pages"].values()
    return {page["title"][5:]: page["imageinfo"][0] for page in pages}


def run_ffmpeg(*args):
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *args], check=True)


def restore(slug, title, info):
    with tempfile.TemporaryDirectory(prefix="diy-demo-") as temp:
        directory = Path(temp)
        source = directory / ("source" + Path(title).suffix)
        request = Request(info["url"].split("?")[0], headers={"User-Agent": USER_AGENT})
        print(f"{slug}: downloading {title}", flush=True)
        with urlopen(request, timeout=120) as response, source.open("wb") as output:
            shutil.copyfileobj(response, output)
        if source.stat().st_size != info["size"]:
            raise RuntimeError(f"{slug}: download size mismatch")

        output = directory / "dash"
        output.mkdir()
        run_ffmpeg("-i", str(source), "-map", "0:v:0", "-map", "0:a?",
                   "-vf", "scale=-2:540", "-c:v", "libx264", "-pix_fmt", "yuv420p",
                   "-preset", "veryfast", "-crf", "27", "-c:a", "aac", "-b:a", "96k",
                   "-f", "dash", "-seg_duration", "4", "-use_template", "1",
                   "-use_timeline", "1", str(output / "manifest.mpd"))
        run_ffmpeg("-ss", "1", "-i", str(source), "-frames:v", "1",
                   "-vf", "scale=-2:360", "-q:v", "4", str(output / "thumb.jpg"))

        files = sorted(output.iterdir(), key=lambda file: file.name == "manifest.mpd")
        for file in files:
            content_type = {".mpd": "application/dash+xml", ".m4s": "video/iso.segment",
                            ".jpg": "image/jpeg"}.get(file.suffix, "application/octet-stream")
            url = S3 + "/" + quote(slug + "/" + file.name)
            upload = Request(url, data=file.read_bytes(), method="PUT",
                             headers={"Content-Type": content_type})
            with urlopen(upload, timeout=60) as response:
                if response.status != 200:
                    raise RuntimeError(f"{slug}: upload returned {response.status}")
        with urlopen(Request(S3 + "/" + slug + "/manifest.mpd", method="HEAD"), timeout=15) as response:
            if response.status != 200:
                raise RuntimeError(f"{slug}: manifest verification failed")
        print(f"{slug}: restored {len(files)} files", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", choices=sorted(SOURCES))
    args = parser.parse_args()
    info = source_info()
    for slug, title in SOURCES.items():
        if args.only is None or args.only == slug:
            restore(slug, title, info[title])


if __name__ == "__main__":
    main()
