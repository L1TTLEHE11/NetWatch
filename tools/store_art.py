#!/usr/bin/env python3
# Usage: python3 tools/store_art.py .   (after ./gradlew testFullDebugUnitTest --tests "*DemoShots*")
"""Icon 512, 1024x500 banners and downsized screenshots (docs/screenshots/{en,ru}) for README / site / catalogs."""
import sys, pathlib
from PIL import Image, ImageDraw, ImageFont, ImageFilter

root = pathlib.Path(sys.argv[1])
shots = root / "docs/screenshots"
fl = root / "fastlane/metadata/android"
BG = (6, 2, 4); RED = (255, 0, 60); CYAN = (0, 240, 255); YEL = (252, 238, 10); TXT = (240, 246, 252); MUT = (176, 138, 152)
MONO_B = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf"; MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"

# --- скриншоты: 720 px по ширине, палитра 256 цветов (для тёмного UI без потерь на глаз) ---
for f in sorted(shots.glob("*/*.png")):
    im = Image.open(f).convert("RGB")
    if im.width > 720:
        im = im.resize((720, round(im.height * 720 / im.width)), Image.LANCZOS)
    im.quantize(256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE).save(f, optimize=True)

# --- иконка: тот же рисунок, что у adaptive-icon (viewport 108, безопасная зона 18..90) ---
def icon(size):
    S = 4 * size; k = S / 72.0
    im = Image.new("RGB", (S, S), BG); d = ImageDraw.Draw(im)
    P = lambda x, y: ((x - 18) * k, (y - 18) * k)
    d.line([P(30, 32), P(70, 32), P(78, 40), P(78, 76), P(30, 76), P(30, 32), P(70, 32)], fill=RED, width=int(3 * k), joint="curve")
    d.line([P(34, 62), P(44, 62), P(49, 48), P(56, 70), P(62, 54), P(66, 62), P(74, 62)], fill=CYAN, width=int(3.5 * k), joint="curve")
    d.rectangle([P(71, 59), P(77, 65)], fill=YEL)
    return im.resize((size, size), Image.LANCZOS)

for loc in ("ru-RU", "en-US"):
    (fl / loc / "images/phoneScreenshots").mkdir(parents=True, exist_ok=True)
    icon(512).save(fl / loc / "images/icon.png", optimize=True)
icon(512).save(root / "docs/icon.png", optimize=True)
icon(64).save(root / "docs/favicon.png", optimize=True)

# --- баннер 1024x500 ---
def banner(tagline, sub, lang):
    W, H = 1024, 500
    im = Image.new("RGB", (W, H), BG); d = ImageDraw.Draw(im)
    for x in range(0, W, 32): d.line([(x, 0), (x, H)], fill=(20, 8, 14))
    for y in range(0, H, 32): d.line([(0, y), (W, y)], fill=(20, 8, 14))
    # экраны справа: сначала мягкое неоновое свечение, потом сами картинки
    h = 440; pos = []
    for i, name in enumerate(["1_home.png", "3_scan.png"]):
        s_ = Image.open(shots / lang / name).convert("RGB")
        w = round(s_.width * h / s_.height)
        x = W - 34 - (2 - i) * (w + 22); yy = 30 if i else 44
        pos.append((s_.resize((w, h), Image.LANCZOS), x, yy, w, RED if i == 0 else CYAN))
    glow = Image.new("RGB", (W, H), (0, 0, 0)); gd = ImageDraw.Draw(glow)
    for _, x, yy, w, c in pos: gd.rectangle([x - 4, yy - 4, x + w + 4, yy + h + 4], fill=tuple(v // 2 for v in c))
    glow = glow.filter(ImageFilter.GaussianBlur(14))
    from PIL import ImageChops
    im = ImageChops.add(im, glow); d = ImageDraw.Draw(im)
    for s_, x, yy, w, c in pos:
        im.paste(s_, (x, yy)); d.rectangle([x - 1, yy - 1, x + w, yy + h], outline=c, width=2)
    d.rectangle([0, 0, W, 5], fill=RED)
    f1 = ImageFont.truetype(MONO_B, 40); f2 = ImageFont.truetype(MONO_B, 28); f3 = ImageFont.truetype(MONO, 20)
    tw = d.textlength("NETWATCH // TOOL", font=f1)
    d.rectangle([44, 64, 44 + tw + 26, 64 + 58], fill=RED)
    d.text((57, 70), "NETWATCH // TOOL", font=f1, fill=BG)
    y = 160
    for line in tagline:
        d.text((46, y), line, font=f2, fill=YEL); y += 40
    y += 14
    for line in sub:
        d.text((46, y), line, font=f3, fill=MUT); y += 30
    d.text((46, H - 50), "FREE · OPEN SOURCE · GPL-3.0", font=f3, fill=CYAN)
    return im

banner(["Сетевой сканер", "и Wi-Fi анализатор", "для Android"], ["сканер сети · пинг · скорость", "Wi-Fi эфир · мониторинг · MikroTik"], "ru").save(fl / "ru-RU/images/featureGraphic.png", optimize=True)
banner(["Network scanner", "& Wi-Fi analyzer", "for Android"], ["LAN scan · ping · speed test", "Wi-Fi channels · monitor · MikroTik"], "en").save(fl / "en-US/images/featureGraphic.png", optimize=True)
banner(["Network scanner", "& Wi-Fi analyzer", "for Android"], ["free · no ads", "no data collection"], "en").save(root / "docs/og.png", optimize=True)
banner(["Сетевой сканер", "и Wi-Fi анализатор", "для Android"], ["бесплатно · без рекламы", "без сбора данных"], "ru").save(root / "docs/og-ru.png", optimize=True)

for loc, lang in (("ru-RU", "ru"), ("en-US", "en")):
    for i, f in enumerate(sorted((shots / lang).glob("*.png")), 1):
        Image.open(f).save(fl / loc / f"images/phoneScreenshots/{i}.png", optimize=True)
print("ok")
