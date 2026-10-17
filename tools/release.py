#!/usr/bin/env python3
"""
Выпуск новой версии NetWatch для обновления из приложения.

    python3 tools/release.py "Что нового 1" "Что нового 2" ...

1. берёт versionCode / versionName из app/build.gradle.kts;
2. копирует собранный app-release.apk в apk/NetWatch-<версия>-universal.apk
   (оставляет 3 последние версии: пока кэши зеркал обновляются, приложения
   могут ещё скачивать предыдущую — её файл не должен пропасть);
3. пишет update.json (версия, путь, SHA-256, размер, дата, список изменений);
4. обновляет ссылку и SHA-256 в README.md.
Дальше: git add -A && git commit && git push — приложения увидят обновление.
"""
import datetime, hashlib, json, pathlib, re, shutil, sys

root = pathlib.Path(__file__).resolve().parent.parent
gradle = (root / "app/build.gradle.kts").read_text(encoding="utf-8")
code = int(re.search(r"versionCode\s*=\s*(\d+)", gradle).group(1))
name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)

src = root / "app/build/outputs/apk/release/app-release.apk"
if not src.exists():
    sys.exit("Сначала соберите: ./gradlew assembleRelease")

apk_dir = root / "apk"; apk_dir.mkdir(exist_ok=True)
dst = apk_dir / f"NetWatch-{name}-universal.apk"
def ver_key(p):
    return [int(x) for x in re.findall(r"\d+", p.name)]
keep = 3
others = sorted((p for p in apk_dir.glob("NetWatch-*-universal.apk") if p.name != dst.name), key=ver_key)
for old in others[: max(0, len(others) - (keep - 1))]:
    old.unlink()
shutil.copyfile(src, dst)
data = dst.read_bytes()
sha = hashlib.sha256(data).hexdigest()

notes = sys.argv[1:] or ["Улучшения и исправления"]
info = {
    "versionCode": code, "versionName": name,
    "apk": f"apk/{dst.name}", "sha256": sha, "size": len(data),
    "date": datetime.date.today().strftime("%d.%m.%Y"), "notes": notes,
}
(root / "update.json").write_text(json.dumps(info, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

readme = root / "README.md"
txt = readme.read_text(encoding="utf-8")
txt = re.sub(r"NetWatch-[\d.]+-universal\.apk", dst.name, txt)
txt = re.sub(r"(SHA-256: `)[0-9a-f]{64}(`)", rf"\g<1>{sha}\g<2>", txt)
readme.write_text(txt, encoding="utf-8")

# сайт (GitHub Pages): ссылки на APK, номер версии, дата в sitemap
site = root / "docs/index.html"
if site.exists():
    t = site.read_text(encoding="utf-8")
    t = re.sub(r"NetWatch-[\d.]+-universal\.apk", dst.name, t)
    t = re.sub(r'("softwareVersion": ")[^"]+(")', rf"\g<1>{name}\g<2>", t)
    t = re.sub(r"Версия [\d.]+ ·", f"Версия {name} ·", t)
    site.write_text(t, encoding="utf-8")
smap = root / "docs/sitemap.xml"
if smap.exists():
    today = datetime.date.today().isoformat()
    smap.write_text(re.sub(r"(<loc>[^<]*/NetWatch/</loc><lastmod>)[^<]+", rf"\g<1>{today}", smap.read_text(encoding="utf-8")), encoding="utf-8")

# списки изменений для каталогов приложений (F-Droid / IzzyOnDroid читают fastlane/metadata)
fl = root / "fastlane/metadata/android"
if fl.exists():
    for loc, body in (("ru-RU", "\n".join("• " + n for n in notes)), ("en-US", "Fixes and improvements. Full list (in Russian): CHANGELOG.md")):
        d = fl / loc / "changelogs"; d.mkdir(parents=True, exist_ok=True)
        (d / f"{code}.txt").write_text(body[:500] + "\n", encoding="utf-8")

print(f"✓ {dst.name}  ({len(data) / 1048576:.2f} МБ)\n  versionCode {code}, SHA-256 {sha}\n  update.json, README, сайт и списки изменений обновлены")

print("После git push сбросьте кэш зеркала jsDelivr (иначе оно отдаёт старый update.json до 12 часов):")
print("  curl -s https://purge.jsdelivr.net/gh/L1TTLEHE11/NetWatch@main/update.json")
