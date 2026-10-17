#!/usr/bin/env python3
"""
Generates the website (GitHub Pages, /docs) in every language:

    docs/index.html, privacy.html, <guide>.html        — English (default, x-default)
    docs/ru/index.html, privacy.html, <guide>.html     — Russian

    python3 tools/site.py          (release.py runs it automatically; needs: pip install qrcode)

Texts: tools/site_text.py. Styles: docs/site.css. Version, APK and size: update.json.
No analytics, no cookies, nothing loaded from third-party servers: the QR code is inline SVG.
A browser set to Russian is sent to /ru/ on the first visit; a choice in the EN | RU switcher is remembered.
Search engine ownership codes: docs/verify.json {"google": "...", "yandex": "...", "bing": "..."}.
To add a language: add it to LANGS and to every dict in site_text.py.
"""
import datetime, html, json, pathlib, re, sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from site_text import T, G, P, GUIDES  # noqa: E402

root = pathlib.Path(__file__).resolve().parent.parent
docs = root / "docs"
info = json.loads((root / "update.json").read_text(encoding="utf-8"))
VER = info["versionName"]
APK = "https://github.com/L1TTLEHE11/NetWatch/raw/main/" + info["apk"]
SIZE = f"{info['size'] / 1048576:.1f}"
SITE = "https://l1ttlehe11.github.io/NetWatch/"
REPO = "https://github.com/L1TTLEHE11/NetWatch"
TODAY = datetime.date.today().isoformat()
LANGS = [("en", "", "EN", "en_US"), ("ru", "ru/", "RU", "ru_RU")]   # code, folder, switcher label, og:locale
DIR = {c: d for c, d, _, _ in LANGS}
SHOTS = ["1_home", "3_scan", "4_wifi", "2_speed", "5_monitor", "6_mikrotik", "7_history", "8_more"]
MB = {"en": "MB", "ru": "МБ"}
# style version: changes whenever site.css changes, so browsers never mix new pages with a cached old stylesheet
import hashlib
CSSV = hashlib.sha256((docs / "site.css").read_bytes()).hexdigest()[:8]

VERIFY = json.loads((docs / "verify.json").read_text(encoding="utf-8")) if (docs / "verify.json").exists() else {}
VMETA = "".join(f'<meta name="{n}" content="{html.escape(VERIFY[k])}">\n' for k, n in
                (("google", "google-site-verification"), ("yandex", "yandex-verification"), ("bing", "msvalidate.01")) if VERIFY.get(k))


def qr_svg(data):
    import qrcode
    q = qrcode.QRCode(border=2, error_correction=qrcode.constants.ERROR_CORRECT_M); q.add_data(data); q.make()
    m = q.get_matrix(); n = len(m)
    path = "".join(f"M{x},{y}h1v1h-1z" for y, row in enumerate(m) for x, v in enumerate(row) if v)
    return (f'<svg viewBox="0 0 {n} {n}" width="168" height="168" shape-rendering="crispEdges" role="img" aria-label="QR">'
            f'<rect width="{n}" height="{n}" fill="#fff"/><path d="{path}" fill="#060204"/></svg>')


QR = qr_svg(APK)


def up(lang): return "../" if DIR[lang] else ""


def alternates(page):
    out = [f'<link rel="alternate" hreflang="{c}" href="{SITE}{d}{page}">' for c, d, _, _ in LANGS]
    out.append(f'<link rel="alternate" hreflang="x-default" href="{SITE}{page}">')
    return "\n".join(out)


def switcher(lang, page):
    items = []
    for c, d, label, _ in LANGS:
        items.append(f'<b aria-current="true">{label}</b>' if c == lang else
                     f'<a href="{up(lang)}{d}{page}" hreflang="{c}" lang="{c}" data-lang="{c}">{label}</a>')
    return '<span class="lang">' + '<span class="sep">|</span>'.join(items) + "</span>"


def topbar(lang, page, nav=True):
    links = "".join(f'<a href="{h}">{n}</a>' for h, n in T[lang]["nav"]) if nav else ""
    return f'<nav class="bar"><a class="mark" href="./">NETWATCH</a><span class="links">{links}</span>{switcher(lang, page)}</nav>'


SCRIPT = """<script>
document.addEventListener('click',function(e){var a=e.target.closest('a[data-lang]');if(a){try{localStorage.setItem('nw_lang',a.dataset.lang)}catch(_){}}});
</script>"""

# Only English (default) pages redirect: a Russian browser on the first visit goes to /ru/ (same page).
REDIRECT = """<script>
(function(){try{var s=localStorage.getItem('nw_lang');var l=(navigator.languages&&navigator.languages[0])||navigator.language||'';
if(s==='ru'||(!s&&/^ru\\b/i.test(l))){location.replace('ru/'+location.pathname.split('/').pop()+location.hash)}}catch(e){}})();
</script>"""


def head(lang, page, title, desc, extra="", og_img="og.png", og_title=None):
    d = DIR[lang]
    og_loc = {c: o for c, _, _, o in LANGS}
    og = f"""<meta property="og:type" content="{'website' if page == '' else 'article'}">
<meta property="og:title" content="{html.escape(og_title or title)}">
<meta property="og:description" content="{html.escape(desc)}">
<meta property="og:image" content="{SITE}{og_img}">
<meta property="og:url" content="{SITE}{d}{page}">
<meta property="og:locale" content="{og_loc[lang]}">
""" + "".join(f'<meta property="og:locale:alternate" content="{o}">\n' for c, o in og_loc.items() if c != lang) + '<meta name="twitter:card" content="summary_large_image">\n'
    return f"""<!doctype html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title>
{REDIRECT if not d else ""}<meta name="description" content="{html.escape(desc)}">
<link rel="canonical" href="{SITE}{d}{page}">
{alternates(page)}
<link rel="icon" type="image/png" href="{up(lang)}favicon.png">
<link rel="apple-touch-icon" href="{up(lang)}icon.png">
<meta name="theme-color" content="#060204">
{VMETA if not d and not page else ""}{og}{extra}<link rel="stylesheet" href="{up(lang)}site.css?v={CSSV}">
</head>"""


def ld(obj): return f'<script type="application/ld+json">\n{json.dumps(obj, ensure_ascii=False, indent=1)}\n</script>\n'


def strip_tags(s): return re.sub(r"<[^>]+>", "", s)


def cta(lang, center=False):
    t = T[lang]
    return f"""<div class="cta{' center' if center else ''}">
      <a class="btn main" href="{APK}">{t['download']}</a>
      <a class="btn alt" href="{REPO}">{t['source']}</a>
    </div>
    <div class="meta{' center' if center else ''}">{t['size_note'].format(size=SIZE + ' ' + MB[lang], ver=VER)}</div>"""


def footer(lang):
    t = T[lang]
    guides = " · ".join(f'<a href="{g}.html">{G[lang][g]["short"]}</a>' for g in GUIDES)
    return f"""<footer>
  <div class="fg"><b>{t['f_guides']}:</b> {guides}</div>
  <div class="fl">
  <span>NetWatch · Created by <a href="https://github.com/L1TTLEHE11">L1TTLEHE11</a></span>
  <span>{t['f_license'].format(repo=REPO)}</span>
  <span><a href="{REPO}">{t['f_source']}</a></span>
  <span><a href="{REPO}/issues">{t['f_bug']}</a></span>
  <span><a href="privacy.html">{t['f_privacy']}</a></span>
  </div>
</footer>"""


def guide_cards(lang, skip=None):
    return "\n".join(f'    <a class="guide" href="{g}.html"><b>{G[lang][g]["h1"]}</b><span>{G[lang][g]["desc"]}</span></a>'
                     for g in GUIDES if g != skip)


def index(lang):
    t = T[lang]; shots = f"{up(lang)}screenshots/{lang}/"
    app = {
        "@context": "https://schema.org", "@type": "SoftwareApplication",
        "name": "NetWatch: Network Scanner", "alternateName": "NetWatch",
        "operatingSystem": "Android 8.0+", "applicationCategory": "UtilitiesApplication",
        "softwareVersion": VER, "fileSize": f"{SIZE} MB", "inLanguage": [c for c, _, _, _ in LANGS],
        "license": "https://www.gnu.org/licenses/gpl-3.0.html", "isAccessibleForFree": True,
        "offers": {"@type": "Offer", "price": "0", "priceCurrency": t["currency"]},
        "downloadUrl": APK, "codeRepository": REPO,
        "image": SITE + "icon.png", "screenshot": [f"{SITE}screenshots/{lang}/{s}.png" for s in SHOTS[:4]],
        "author": {"@type": "Person", "name": "L1TTLEHE11", "url": "https://github.com/L1TTLEHE11"},
        "description": t["desc"],
    }
    faq = {"@context": "https://schema.org", "@type": "FAQPage", "mainEntity": [
        {"@type": "Question", "name": q, "acceptedAnswer": {"@type": "Answer", "text": strip_tags(a)}} for q, a in t["faq"]]}
    extra = f'<meta name="keywords" content="{t["keywords"]}">\n' + ld(app) + ld(faq)
    trust = "".join(f"<li>{x}</li>" for x in t["trust"])
    scen = []
    for i, s in enumerate(t["scen"]):
        pts = "".join(f"<li>{p}</li>" for p in s["pts"])
        scen.append(f"""  <article class="scen{' rev' if i % 2 else ''}">
    <div class="txt">
      <span class="n">0{i + 1}</span>
      <h3>{s['q']}</h3>
      <p>{s['a']}</p>
      <ul class="ticks">{pts}</ul>
      <a class="more" href="{s['guide']}.html">{t['guide_link']}</a>
    </div>
    <div class="crop"><img src="{shots}{s['shot']}.png" alt="{html.escape(s['q'])}" loading="lazy" width="720" height="1558"></div>
  </article>""")
    admin = "\n".join(f'    <div class="card"><b>{n}</b><p>{p}</p></div>' for n, p in t["admin"])
    c0, c1, c2 = t["cmp_cols"]
    rows = "\n".join(f'      <tr><th scope="row">{a}</th><td class="yes">{b}</td><td>{c}</td></tr>' for a, b, c in t["cmp"])
    faqs = "\n".join(f'    <details><summary>{q}</summary><p>{a}</p></details>' for q, a in t["faq"])
    steps = "".join(f"<li>{s}</li>" for s in t["steps"])
    return head(lang, "", t["title"], t["desc"], extra, t["og_img"], t["og_title"]) + f"""
<body>
<div class="top"></div>
<div class="wrap">
{topbar(lang, "")}

<header class="hero">
  <div>
    <span class="logo">NETWATCH // TOOL</span>
    <h1>{t['h1']}</h1>
    <p class="lead">{t['lead']}</p>
    <div class="dl">
      <div>{cta(lang)}</div>
      <div class="qr">{QR}<div><b>{t['qr_title']}</b><span>{t['qr_note']}</span></div></div>
    </div>
    <ul class="trust">{trust}</ul>
  </div>
  <div class="phones" aria-hidden="true">
    <img src="{shots}1_home.png" alt="" width="720" height="1558">
    <img src="{shots}4_wifi.png" alt="" width="720" height="1558">
  </div>
</header>

<section id="scenarios">
  <h2>{t['h_scen']}</h2>
{chr(10).join(scen)}
</section>

<section>
  <h2>{t['h_admin']}</h2>
  <p class="sub">{t['admin_lead']}</p>
  <div class="grid">
{admin}
  </div>
</section>

<section>
  <h2>{t['h_cmp']}</h2>
  <div class="tablewrap"><table class="cmp">
    <thead><tr><th>{c0}</th><th class="yes">{c1}</th><th>{c2}</th></tr></thead>
    <tbody>
{rows}
    </tbody>
  </table></div>
</section>

<section id="guides">
  <h2>{t['h_guides']}</h2>
  <div class="guides">
{guide_cards(lang)}
  </div>
</section>

<section id="faq">
  <h2>{t['h_faq']}</h2>
  <div class="faq">
{faqs}
    <details><summary>{t['h_install']}</summary><ol class="steps">{steps}</ol><p>{t['install_note']}</p></details>
  </div>
</section>

<section class="final">
  <h2>{t['final_h']}</h2>
  <p class="sub">{t['final_p']}</p>
  {cta(lang, center=True)}
</section>

{footer(lang)}
</div>
{SCRIPT}
</body>
</html>
"""


def guide(lang, slug):
    t = T[lang]; g = G[lang][slug]; page = f"{slug}.html"; shots = f"{up(lang)}screenshots/{lang}/"
    art = {"@context": "https://schema.org", "@type": "Article", "headline": g["h1"], "description": g["desc"],
           "inLanguage": lang, "dateModified": TODAY, "image": f"{SITE}screenshots/{lang}/{g['shot']}.png",
           "author": {"@type": "Person", "name": "L1TTLEHE11", "url": "https://github.com/L1TTLEHE11"},
           "mainEntityOfPage": f"{SITE}{DIR[lang]}{page}"}
    crumbs = {"@context": "https://schema.org", "@type": "BreadcrumbList", "itemListElement": [
        {"@type": "ListItem", "position": 1, "name": "NetWatch", "item": f"{SITE}{DIR[lang]}"},
        {"@type": "ListItem", "position": 2, "name": g["short"], "item": f"{SITE}{DIR[lang]}{page}"}]}
    return head(lang, page, g["title"], g["desc"], ld(art) + ld(crumbs), t["og_img"]) + f"""
<body class="doc">
<div class="top"></div>
<div class="wrap">
{topbar(lang, page, nav=False)}
<main class="article">
  <p class="crumb"><a href="./">{t['g_back']}</a></p>
  <h1>{g['h1']}</h1>
  <p class="lead">{g['intro']}</p>
  <figure class="shot"><img src="{shots}{g['shot']}.png" alt="{html.escape(g['h1'])}" width="720" height="1558"></figure>
{g['body']}
  <div class="gcta">
    <h2>{t['g_cta_h']}</h2>
    <p>{t['g_cta_p']}</p>
    {cta(lang)}
  </div>
  <p class="mut">{t['g_updated']}: {TODAY}</p>
</main>
<section>
  <h2>{t['g_related']}</h2>
  <div class="guides">
{guide_cards(lang, slug)}
  </div>
</section>
{footer(lang)}
</div>
{SCRIPT}
</body>
</html>
"""


def privacy(lang):
    p = P[lang]
    return head(lang, "privacy.html", p["title"], p["desc"], og_img=T[lang]["og_img"]) + f"""
<body class="doc">
<div class="top"></div>
<div class="wrap">
{topbar(lang, "privacy.html", nav=False)}
<main class="article">
<p class="crumb"><a href="./">← NetWatch</a></p>
<h1>{p['h1']}</h1>
<p class="mut">{p['since']}</p>

{p['body'].replace('{repo}', REPO)}
</main>
{footer(lang)}
</div>
{SCRIPT}
</body>
</html>
"""


PAGES = [("", "1.0")] + [(f"{g}.html", "0.8") for g in GUIDES] + [("privacy.html", "0.3")]

for c, d, _, _ in LANGS:
    out = docs / d; out.mkdir(parents=True, exist_ok=True)
    (out / "index.html").write_text(index(c), encoding="utf-8")
    (out / "privacy.html").write_text(privacy(c), encoding="utf-8")
    for g in GUIDES:
        (out / f"{g}.html").write_text(guide(c, g), encoding="utf-8")

urls = []
for page, prio in PAGES:
    links = "".join(f'<xhtml:link rel="alternate" hreflang="{c}" href="{SITE}{d}{page}"/>' for c, d, _, _ in LANGS) + \
            f'<xhtml:link rel="alternate" hreflang="x-default" href="{SITE}{page}"/>'
    for c, d, _, _ in LANGS:
        urls.append(f"  <url><loc>{SITE}{d}{page}</loc><lastmod>{TODAY}</lastmod><priority>{prio}</priority>{links}</url>")
(docs / "sitemap.xml").write_text('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">\n'
                                  + "\n".join(urls) + "\n</urlset>\n", encoding="utf-8")
print(f"✓ site {VER}: {len(PAGES)} pages × {len(LANGS)} languages + sitemap.xml")
