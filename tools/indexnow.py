#!/usr/bin/env python3
"""
Tell search engines (Bing, Yandex, Seznam, Naver… via IndexNow) that the website changed.
Run after git push, once GitHub Pages has published:   python3 tools/indexnow.py
The key is the docs/<32 hex>.txt file; it must stay on the site.
"""
import json, pathlib, re, urllib.request

root = pathlib.Path(__file__).resolve().parent.parent
SITE = "https://l1ttlehe11.github.io/NetWatch/"
key = next(p.stem for p in (root / "docs").glob("*.txt") if re.fullmatch(r"[0-9a-f]{32}", p.stem))
urls = re.findall(r"<loc>([^<]+)</loc>", (root / "docs/sitemap.xml").read_text(encoding="utf-8"))
body = json.dumps({"host": "l1ttlehe11.github.io", "key": key, "keyLocation": f"{SITE}{key}.txt", "urlList": urls}).encode()
for ep in ("https://api.indexnow.org/indexnow", "https://yandex.com/indexnow"):
    req = urllib.request.Request(ep, body, {"Content-Type": "application/json; charset=utf-8"})
    try:
        with urllib.request.urlopen(req, timeout=20) as r: print(ep, r.status)
    except urllib.error.HTTPError as e: print(ep, e.code, e.read()[:200])
print(len(urls), "URLs submitted")
