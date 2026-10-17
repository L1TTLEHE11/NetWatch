#!/usr/bin/env python3
"""
Publish a new NetWatch version for in-app updates.

    python3 tools/release.py --en "What's new 1" "What's new 2" --ru "Что нового 1" "Что нового 2"

1. reads versionCode / versionName from app/build.gradle.kts;
2. copies app-release.apk to apk/NetWatch-<version>-universal.apk
   (keeps the 3 latest: while mirror caches refresh, installed apps may still
   download the previous file, so it must not disappear);
3. writes update.json (version, path, SHA-256, size, date, release notes per language;
   "notes" stays Russian for app versions before 2.5, which were Russian-only);
4. updates APK links, version and SHA-256 in README.md, README.ru.md and the site;
5. writes changelogs for app catalogs (fastlane/metadata).
Then: git add -A && git commit && git push, and purge the jsDelivr cache (printed below).
"""
import datetime, hashlib, json, pathlib, re, shutil, sys

root = pathlib.Path(__file__).resolve().parent.parent
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
code = int(re.search(r"versionCode\s*=\s*(\d+)", gradle).group(1))
name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)

# --- release notes: --en ... --ru ...
notes = {"en": [], "ru": []}; cur = "en"
for a in sys.argv[1:]:
    if a in ("--en", "--ru"): cur = a[2:]; continue
    notes[cur].append(a)
notes["en"] = notes["en"] or ["Fixes and improvements"]
notes["ru"] = notes["ru"] or ["Улучшения и исправления"]

src = root / "app/build/outputs/apk/full/release/app-full-release.apk"
if not src.exists():
    sys.exit("Build first: ./gradlew assembleFullRelease")

apk_dir = root / "apk"; apk_dir.mkdir(exist_ok=True)
dst = apk_dir / f"NetWatch-{name}-universal.apk"
def ver_key(p): return [int(x) for x in re.findall(r"\d+", p.name)]
others = sorted((p for p in apk_dir.glob("NetWatch-*-universal.apk") if p.name != dst.name), key=ver_key)
for old in others[: max(0, len(others) - 2)]:
    old.unlink()
shutil.copyfile(src, dst)
data = dst.read_bytes()
sha = hashlib.sha256(data).hexdigest()

info = {
    "versionCode": code, "versionName": name,
    "apk": f"apk/{dst.name}", "sha256": sha, "size": len(data),
    "date": datetime.date.today().strftime("%d.%m.%Y"),
    "notes": notes["ru"], "notes_en": notes["en"], "notes_ru": notes["ru"],
}
(root / "update.json").write_text(json.dumps(info, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

def patch(path, extra=()):
    if not path.exists(): return
    t = path.read_text(encoding="utf-8")
    t = re.sub(r"NetWatch-[\d.]+-universal\.apk", dst.name, t)
    t = re.sub(r"(SHA-256: `)[0-9a-f]{64}(`)", rf"\g<1>{sha}\g<2>", t)
    t = re.sub(r'("softwareVersion": ")[^"]+(")', rf"\g<1>{name}\g<2>", t)
    for pat, rep in extra: t = re.sub(pat, rep, t)
    path.write_text(t, encoding="utf-8")

patch(root / "README.md"); patch(root / "README.ru.md")

# website in every language + sitemap, from update.json
import subprocess
subprocess.run([sys.executable, str(root / "tools/site.py")], check=True)

fl = root / "fastlane/metadata/android"
if fl.exists():
    for loc, lang in (("en-US", "en"), ("ru-RU", "ru")):
        d = fl / loc / "changelogs"; d.mkdir(parents=True, exist_ok=True)
        (d / f"{code}.txt").write_text(("\n".join("• " + n for n in notes[lang]))[:500] + "\n", encoding="utf-8")

print(f"✓ {dst.name}  ({len(data) / 1048576:.2f} MB)\n  versionCode {code}, SHA-256 {sha}\n  update.json, READMEs, site and changelogs updated")
print("After git push, purge the jsDelivr cache:\n  curl -s https://purge.jsdelivr.net/gh/L1TTLEHE11/NetWatch@main/update.json")
