#!/usr/bin/env python3
"""
Generates the website (GitHub Pages, /docs) in every language from one template:

    docs/index.html, docs/privacy.html        — English (default, x-default)
    docs/ru/index.html, docs/ru/privacy.html  — Russian

    python3 tools/site.py          (release.py runs it automatically)

Styles: docs/site.css. Version and APK name come from update.json. On the first visit to the English page a browser
set to Russian is redirected to /ru/; a manual choice in the EN | RU switcher is remembered.
To add a language: add a dict to T and P below, and the language to LANGS.
"""
import json, pathlib, html

root = pathlib.Path(__file__).resolve().parent.parent
docs = root / "docs"
info = json.loads((root / "update.json").read_text(encoding="utf-8"))
VER = info["versionName"]
APK = "https://github.com/L1TTLEHE11/NetWatch/raw/main/" + info["apk"]
SITE = "https://l1ttlehe11.github.io/NetWatch/"
REPO = "https://github.com/L1TTLEHE11/NetWatch"
LANGS = [("en", "", "EN", "en_US"), ("ru", "ru/", "RU", "ru_RU")]   # code, folder, switcher label, og:locale

SHOTS = ["1_home", "3_scan", "4_wifi", "2_speed", "5_monitor", "6_mikrotik", "7_history", "8_more"]

T = {
"en": dict(
    title="NetWatch — free network scanner and Wi-Fi analyzer for Android",
    desc="NetWatch is a free network diagnostics app for Android: LAN device scanner, Wi-Fi channel analyzer, ping, internet speed test, outage monitor, traceroute, port scanner and MikroTik. No ads, no data collection, open source.",
    keywords="network scanner, network scanner android, lan scanner, ip scanner, wifi analyzer, wi-fi channel analyzer, ping, traceroute, port scanner, speed test, network monitor, mikrotik, routeros, capsman, wake on lan, mdns, upnp",
    og_title="NetWatch — network scanner and Wi-Fi analyzer for Android",
    og_desc="LAN scanner, Wi-Fi channels, ping, speed test, outage monitor and MikroTik in one free app. No ads, open source.",
    og_img="og.png",
    ld_desc="Free network scanner and Wi-Fi analyzer for Android: LAN devices, Wi-Fi channels, ping, internet speed, outage monitor, traceroute, ports, MikroTik.",
    currency="USD",
    h1="Free network scanner and&nbsp;Wi-Fi analyzer for&nbsp;Android",
    lead="Finds every device on your network, picks the best Wi-Fi channel, tests your speed, catches ISP outages and shows what is going on inside your MikroTik. No ads, no data collection, open source.",
    download="⬇ DOWNLOAD APK", source="SOURCE CODE",
    meta=f"Version {VER} · Android 8.0+ · one file for every device · updates from inside the app",
    tags=["GPL-3.0", "no ads", "no analytics", "works offline on your LAN", "English · Русский"],
    h_features="FEATURES",
    features=[
        ("LAN scanner", "Every device on the subnet in 10–30 seconds: routers, MikroTik, IP cameras, printers, Android TV and Smart TV, PCs, iPhones, NAS. Open ports, device names, a “new” tag."),
        ("Wi-Fi analyzer", "All nearby networks on a 2.4 / 5 / 6 GHz channel graph and the least crowded channel for your access point."),
        ("Speed test", "Multi-threaded download and upload test with live bars, ping and jitter, history and a weekly summary: when your internet is slowest."),
        ("Outage monitor", "24/7 background checks of the router and the internet, outage notifications, loss statistics and downtime — evidence for your ISP."),
        ("Site survey", "Signal and ping measurements per location (“room 204”, “reception”), weak spots highlighted, Excel and PDF report."),
        ("Diagnostics", "Traceroute, DNS benchmark, port scanner with service names, live ping of any host, LAN TCP throughput test, Wake-on-LAN."),
        ("MikroTik", "RouterOS API client: load, real-time traffic per port, CAPsMAN access points, Wi-Fi clients with signal, DHCP leases."),
        ("History and reports", "Every test is saved on the phone, filters by network and period, one-tap PDF report, home-screen widget."),
    ],
    h_shots="SCREENSHOTS",
    shots=["Home", "LAN scanner", "Wi-Fi air", "Speed", "Monitor", "MikroTik", "History", "More"],
    shots_alt=["Home: Wi-Fi signal, pings, last speed test", "LAN scanner: devices with types and ports", "Wi-Fi analyzer: channel graph and recommended channel",
               "Internet speed test", "Outage monitor", "MikroTik: interfaces, CAPsMAN, Wi-Fi clients", "Measurement history with speed summary", "More section"],
    h_install="HOW TO INSTALL",
    steps=[f'Download <a href="{APK}">{info["apk"].split("/")[-1]}</a> to your phone.', "Open the file and allow installs from this source.",
           "Allow location access — without it Android hides the Wi-Fi network name."],
    install_note="New versions arrive right in the app: “More → Settings → Updates”. Every file is verified by SHA-256.",
    h_privacy="PRIVACY",
    privacy1="<b>NetWatch collects nothing and sends nothing anywhere.</b> No analytics, no ads, no accounts, no servers of its own. All measurements stay on your phone.",
    privacy2='Location is needed only because Android won’t reveal the network name and Wi-Fi scan results without it. <a href="privacy.html">Privacy policy</a>.',
    h_lang="LANGUAGES",
    lang_note=f'The app speaks English and Russian and follows your system language; switch it in “More → Settings → Language”. Want to add yours? <a href="{REPO}/blob/main/CONTRIBUTING.md#translating">Help translate NetWatch</a>.',
    f_license=f'Free software under the <a href="{REPO}/blob/main/LICENSE">GNU GPL v3</a>',
    f_bug="Report a bug", f_privacy="Privacy",
),
"ru": dict(
    title="NetWatch — бесплатный сетевой сканер и Wi-Fi анализатор для Android",
    desc="NetWatch — бесплатное приложение для диагностики сети на Android: сканер устройств в сети, анализатор Wi-Fi каналов, пинг, замер скорости интернета, мониторинг обрывов, traceroute, сканер портов и MikroTik. Без рекламы и сбора данных, открытый код.",
    keywords="сетевой сканер, сканер сети android, анализатор wi-fi, wifi analyzer, сканер ip, пинг, traceroute, трассировка, сканер портов, скорость интернета, мониторинг сети, mikrotik, микротик, routeros, capsman, wake on lan",
    og_title="NetWatch — сетевой сканер и Wi-Fi анализатор для Android",
    og_desc="Сканер сети, Wi-Fi эфир, пинг, скорость, мониторинг обрывов и MikroTik в одном бесплатном приложении. Без рекламы, открытый код.",
    og_img="og-ru.png",
    ld_desc="Бесплатный сетевой сканер и Wi-Fi анализатор для Android: устройства в сети, каналы Wi-Fi, пинг, скорость интернета, мониторинг обрывов, traceroute, порты, MikroTik.",
    currency="RUB",
    h1="Бесплатный сетевой сканер и&nbsp;Wi-Fi анализатор для&nbsp;Android",
    lead="Найдёт все устройства в сети, подберёт канал Wi-Fi, замерит скорость, поймает обрывы провайдера и покажет, что творится на MikroTik. Без рекламы, без сбора данных, с открытым кодом.",
    download="⬇ СКАЧАТЬ APK", source="ИСХОДНЫЙ КОД",
    meta=f"Версия {VER} · Android 8.0+ · один файл для всех устройств · обновляется из приложения",
    tags=["GPL-3.0", "без рекламы", "без аналитики", "работает офлайн в локальной сети", "Русский · English"],
    h_features="ЧТО УМЕЕТ",
    features=[
        ("Сканер сети", "Все устройства в подсети за 10–30 секунд: роутеры, MikroTik, IP-камеры, принтеры, Android TV и Smart TV, компьютеры, iPhone, NAS. Открытые порты, имена устройств, метка «новое»."),
        ("Wi-Fi анализатор", "Все сети вокруг на графике каналов 2.4 / 5 / 6 ГГц и рекомендация самого свободного канала для вашей точки доступа."),
        ("Скорость интернета", "Многопоточный замер скачивания и отдачи с живыми шкалами, пинг и джиттер, история и итог за неделю: когда интернет медленнее всего."),
        ("Мониторинг обрывов", "Круглосуточная проверка роутера и интернета в фоне, уведомления об обрывах, статистика потерь и время без связи — для разговора с провайдером."),
        ("Обход объекта", "Замеры сигнала и пинга по точкам здания («номер 204», «ресепшн»), проблемные места подсвечиваются, отчёт в Excel и PDF."),
        ("Диагностика", "Traceroute, сравнение DNS-серверов, сканер портов с расшифровкой служб, live-пинг любого узла, TCP-тест скорости в локальной сети, Wake-on-LAN."),
        ("MikroTik", "Подключение по RouterOS API: нагрузка, трафик по портам в реальном времени, точки CAPsMAN, Wi-Fi клиенты с уровнем сигнала, DHCP-аренды."),
        ("История и отчёты", "Все замеры сохраняются на телефоне, фильтры по сети и периоду, PDF-отчёт одной кнопкой, виджет на рабочий стол."),
    ],
    h_shots="СКРИНШОТЫ",
    shots=["Главная", "Сканер сети", "Wi-Fi эфир", "Скорость", "Мониторинг", "MikroTik", "История", "Ещё"],
    shots_alt=["Главная: сигнал Wi-Fi, пинги, последний замер скорости", "Сканер сети: список устройств с типами и портами", "Wi-Fi анализатор: график каналов и рекомендованный канал",
               "Замер скорости интернета", "Мониторинг обрывов связи", "MikroTik: интерфейсы, CAPsMAN, клиенты Wi-Fi", "История замеров со сводкой скорости", "Раздел «Ещё»"],
    h_install="КАК УСТАНОВИТЬ",
    steps=[f'Скачайте <a href="{APK}">{info["apk"].split("/")[-1]}</a> на телефон.', "Откройте файл и разрешите установку из этого источника.",
           "Разрешите доступ к геолокации — без него Android скрывает имя Wi-Fi сети."],
    install_note="Новые версии приходят прямо в приложение: «Ещё → Настройки → Обновления». Каждый файл проверяется по SHA-256.",
    h_privacy="ПРИВАТНОСТЬ",
    privacy1="<b>NetWatch ничего не собирает и никуда не отправляет.</b> Нет аналитики, рекламы, учётных записей и собственных серверов. Все замеры хранятся только на вашем телефоне.",
    privacy2='Геолокация нужна лишь потому, что Android без неё не показывает имя сети и результаты сканирования Wi-Fi. <a href="privacy.html">Политика конфиденциальности</a>.',
    h_lang="ЯЗЫКИ",
    lang_note=f'Приложение на русском и английском и подстраивается под язык системы; переключить — «Ещё → Настройки → Язык». Хотите добавить свой язык? <a href="{REPO}/blob/main/CONTRIBUTING.md#translating">Помогите с переводом</a>.',
    f_license=f'Свободное ПО под лицензией <a href="{REPO}/blob/main/LICENSE">GNU GPL v3</a>',
    f_bug="Сообщить об ошибке", f_privacy="Конфиденциальность",
),
}

P = {
"en": dict(
    title="Privacy policy — NetWatch",
    desc="NetWatch privacy policy: the app does not collect, store on servers or share personal data.",
    h1="NetWatch privacy policy",
    since="Effective from 17 October 2026 · developer: L1TTLEHE11",
    body=f"""<h2>In short</h2>
<p>NetWatch does not collect, sell or share your data. The app has no ads, no analytics, no accounts and no developer servers.</p>

<h2>Data processed on the phone</h2>
<ul>
<li>Network parameters: Wi-Fi name and signal, IP addresses, gateway, DNS — for display and diagnostics.</li>
<li>Measurement results: pings, speed, devices found on the local network, survey points, monitor sessions.</li>
<li>Addresses and settings you enter, including the MikroTik address, login and (if you choose “remember”) password.</li>
</ul>
<p>All of this is stored only in the app’s private folder on your device and is deleted together with the app or with the “Clear” button in “History”.</p>

<h2>Location</h2>
<p>Location permission is requested only because Android does not give apps the Wi-Fi network name (SSID) and scan results without it. Coordinates are never requested, used or stored.</p>

<h2>Network requests</h2>
<p>To work, the app connects to:</p>
<ul>
<li>devices on your local network and hosts you choose — ping, scanning, traceroute, ports, MikroTik;</li>
<li>public servers for the speed test (mirror.yandex.ru, mirror.selectel.ru, speed.cloudflare.com, speed.hetzner.de) and DNS servers for the DNS test;</li>
<li>GitHub and jsDelivr — to check for and download updates (automatic checks can be turned off in settings).</li>
</ul>
<p>These servers see your IP address, as with any website. The app sends them no device or user identifiers.</p>

<h2>Sharing</h2>
<p>Data leaves the phone only when you act — when you share a PDF, TXT or CSV report yourself via the “Share” menu.</p>

<h2>Children</h2>
<p>The app is not intended for children and collects no data about them.</p>

<h2>Changes and contact</h2>
<p>Changes to this policy are published on this page. Questions: <a href="{REPO}/issues">GitHub issues</a>. The app’s source code is open: <a href="{REPO}">github.com/L1TTLEHE11/NetWatch</a>.</p>""",
),
"ru": dict(
    title="Политика конфиденциальности — NetWatch",
    desc="Политика конфиденциальности приложения NetWatch: приложение не собирает, не хранит на серверах и не передаёт персональные данные.",
    h1="Политика конфиденциальности приложения NetWatch",
    since="Действует с 17 октября 2026 года · разработчик: L1TTLEHE11",
    body=f"""<h2>Коротко</h2>
<p>NetWatch не собирает, не продаёт и не передаёт ваши данные. В приложении нет рекламы, аналитики, учётных записей и собственных серверов разработчика.</p>

<h2>Какие данные обрабатываются на телефоне</h2>
<ul>
<li>Параметры сети: имя и сигнал Wi-Fi, IP-адреса, шлюз, DNS — для отображения и диагностики.</li>
<li>Результаты замеров: пинги, скорость, найденные устройства в локальной сети, точки обхода, сессии мониторинга.</li>
<li>Введённые вами адреса и настройки, включая адрес, логин и (если вы выбрали «запомнить») пароль MikroTik.</li>
</ul>
<p>Всё это хранится только в закрытой папке приложения на вашем устройстве и удаляется вместе с приложением или кнопкой «Очистить» в разделе «История».</p>

<h2>Геолокация</h2>
<p>Разрешение на геолокацию запрашивается только потому, что Android без него не отдаёт приложениям имя Wi-Fi сети (SSID) и результаты сканирования эфира. Координаты не запрашиваются, не используются и не сохраняются.</p>

<h2>Сетевые запросы</h2>
<p>Для работы функций приложение обращается к:</p>
<ul>
<li>устройствам вашей локальной сети и выбранным вами узлам — пинг, сканирование, traceroute, порты, MikroTik;</li>
<li>публичным серверам для замера скорости (mirror.yandex.ru, mirror.selectel.ru, speed.cloudflare.com, speed.hetzner.de) и DNS-серверам для теста DNS;</li>
<li>GitHub и jsDelivr — для проверки и загрузки обновлений (можно отключить автопроверку в настройках).</li>
</ul>
<p>Эти серверы видят ваш IP-адрес, как при открытии любого сайта. Никаких идентификаторов устройства или пользователя приложение им не передаёт.</p>

<h2>Передача данных</h2>
<p>Данные покидают телефон только по вашему действию — когда вы сами отправляете отчёт PDF, TXT или CSV через меню «Поделиться».</p>

<h2>Дети</h2>
<p>Приложение не предназначено для детей и не собирает данные о них.</p>

<h2>Изменения и контакты</h2>
<p>Изменения этой политики публикуются на этой странице. Вопросы — через <a href="{REPO}/issues">issues на GitHub</a>. Исходный код приложения открыт: <a href="{REPO}">github.com/L1TTLEHE11/NetWatch</a>.</p>""",
),
}

def alternates(page):
    out = [f'<link rel="alternate" hreflang="{c}" href="{SITE}{d}{page}">' for c, d, _, _ in LANGS]
    out.append(f'<link rel="alternate" hreflang="x-default" href="{SITE}{page}">')
    return "\n".join(out)


def switcher(lang, page):
    """EN | RU links; the click remembers the choice so the auto-redirect never overrides it."""
    up = "../" if dict((c, d) for c, d, _, _ in LANGS)[lang] else ""
    items = []
    for c, d, label, _ in LANGS:
        if c == lang: items.append(f'<b aria-current="true">{label}</b>')
        else: items.append(f'<a href="{up}{d}{page}" hreflang="{c}" lang="{c}" data-lang="{c}">{label}</a>')
    return '<nav class="lang" aria-label="Language">' + '<span>|</span>'.join(items) + "</nav>"


SCRIPT = """<script>
document.addEventListener('click',function(e){var a=e.target.closest('a[data-lang]');if(a){try{localStorage.setItem('nw_lang',a.dataset.lang)}catch(_){}}});
</script>"""

# Only the English (default) pages redirect: a Russian browser on the first visit goes to /ru/.
REDIRECT = """<script>
(function(){try{var s=localStorage.getItem('nw_lang');var l=(navigator.languages&&navigator.languages[0])||navigator.language||'';
if(s==='ru'||(!s&&/^ru\\b/i.test(l))){location.replace('ru/'+location.pathname.split('/').pop()+location.hash)}}catch(e){}})();
</script>"""


def head(lang, page, title, desc, extra=""):
    d = dict((c, f) for c, f, _, _ in LANGS)[lang]
    up = "../" if d else ""
    return f"""<!doctype html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title>
{REDIRECT if not d else ""}<meta name="description" content="{html.escape(desc)}">
<link rel="canonical" href="{SITE}{d}{page}">
{alternates(page)}
<link rel="icon" type="image/png" href="{up}favicon.png">
<link rel="apple-touch-icon" href="{up}icon.png">
<meta name="theme-color" content="#060204">
{extra}<link rel="stylesheet" href="{up}site.css">
</head>"""


def index(lang):
    t = T[lang]; d = dict((c, f) for c, f, _, _ in LANGS)[lang]; up = "../" if d else ""
    og_loc = dict((c, o) for c, _, _, o in LANGS)
    shots = f"{up}screenshots/{lang}/"
    ld = {
        "@context": "https://schema.org", "@type": "SoftwareApplication",
        "name": "NetWatch: Network Scanner", "alternateName": "NetWatch",
        "operatingSystem": "Android 8.0+", "applicationCategory": "UtilitiesApplication",
        "softwareVersion": VER, "inLanguage": [c for c, _, _, _ in LANGS],
        "license": "https://www.gnu.org/licenses/gpl-3.0.html", "isAccessibleForFree": True,
        "offers": {"@type": "Offer", "price": "0", "priceCurrency": t["currency"]},
        "downloadUrl": APK, "codeRepository": REPO,
        "image": SITE + "icon.png", "screenshot": f"{SITE}screenshots/{lang}/3_scan.png",
        "author": {"@type": "Person", "name": "L1TTLEHE11", "url": "https://github.com/L1TTLEHE11"},
        "description": t["ld_desc"],
    }
    extra = f"""<meta name="keywords" content="{t['keywords']}">
<meta property="og:type" content="website">
<meta property="og:title" content="{html.escape(t['og_title'])}">
<meta property="og:description" content="{html.escape(t['og_desc'])}">
<meta property="og:image" content="{SITE}{t['og_img']}">
<meta property="og:url" content="{SITE}{d}">
<meta property="og:locale" content="{og_loc[lang]}">
""" + "".join(f'<meta property="og:locale:alternate" content="{o}">\n' for c, o in og_loc.items() if c != lang) + f"""<meta name="twitter:card" content="summary_large_image">
<script type="application/ld+json">
{json.dumps(ld, ensure_ascii=False, indent=2)}
</script>
"""
    cards = "\n".join(f'    <div class="card"><span class="n">{i:02d}</span><b>{n}</b><p>{p}</p></div>' for i, (n, p) in enumerate(t["features"], 1))
    gallery = "\n".join(f'    <figure><img src="{shots}{s}.png" alt="{html.escape(a)}" loading="lazy"><figcaption>{c}</figcaption></figure>'
                        for s, c, a in zip(SHOTS, t["shots"], t["shots_alt"]))
    tags = "".join(f'<span class="tag">{x}</span>' for x in t["tags"])
    steps = "\n".join(f"    <li>{s}</li>" for s in t["steps"])
    return head(lang, "", t["title"], t["desc"], extra) + f"""
<body>
<div class="top"></div>
<div class="wrap">
{switcher(lang, "")}

<header class="hero">
  <div>
    <span class="logo">NETWATCH // TOOL</span>
    <h1>{t['h1']}</h1>
    <p class="lead">{t['lead']}</p>
    <div class="cta">
      <a class="btn main" href="{APK}">{t['download']}</a>
      <a class="btn alt" href="{REPO}">{t['source']}</a>
    </div>
    <div class="meta">{t['meta']}</div>
    <div class="tags">{tags}</div>
  </div>
  <div class="phones" aria-hidden="true">
    <img src="{shots}1_home.png" alt="">
    <img src="{shots}3_scan.png" alt="">
  </div>
</header>

<section>
  <h2>{t['h_features']}</h2>
  <div class="grid">
{cards}
  </div>
</section>

<section>
  <h2>{t['h_shots']}</h2>
  <div class="gallery">
{gallery}
  </div>
</section>

<section>
  <h2>{t['h_install']}</h2>
  <ol class="steps">
{steps}
  </ol>
  <p class="meta">{t['install_note']}</p>
</section>

<section>
  <h2>{t['h_privacy']}</h2>
  <div class="privacy">
    <p>{t['privacy1']}</p>
    <p style="margin-bottom:0">{t['privacy2']}</p>
  </div>
</section>

<section>
  <h2>{t['h_lang']}</h2>
  <p>{t['lang_note']}</p>
</section>

{footer(lang)}

</div>
{SCRIPT}
</body>
</html>
"""


def footer(lang):
    t = T[lang]
    return f"""<footer>
  <span>NetWatch · Created by <a href="https://github.com/L1TTLEHE11">L1TTLEHE11</a></span>
  <span>{t['f_license']}</span>
  <span><a href="{REPO}/issues">{t['f_bug']}</a></span>
  <span><a href="privacy.html">{t['f_privacy']}</a></span>
</footer>"""


def privacy(lang):
    p = P[lang]
    return head(lang, "privacy.html", p["title"], p["desc"]) + f"""
<body class="doc">
<div class="top"></div>
<main>
{switcher(lang, "privacy.html")}
<p><a href="./">← NetWatch</a></p>
<h1>{p['h1']}</h1>
<p class="mut">{p['since']}</p>

{p['body']}
</main>
{SCRIPT}
</body>
</html>
"""


for c, d, _, _ in LANGS:
    out = docs / d; out.mkdir(parents=True, exist_ok=True)
    (out / "index.html").write_text(index(c), encoding="utf-8")
    (out / "privacy.html").write_text(privacy(c), encoding="utf-8")

# sitemap with language alternates
import datetime
today = datetime.date.today().isoformat()
urls = []
for page, prio in (("", "1.0"), ("privacy.html", "0.3")):
    links = "".join(f'<xhtml:link rel="alternate" hreflang="{c}" href="{SITE}{d}{page}"/>' for c, d, _, _ in LANGS) + \
            f'<xhtml:link rel="alternate" hreflang="x-default" href="{SITE}{page}"/>'
    for c, d, _, _ in LANGS:
        urls.append(f"  <url><loc>{SITE}{d}{page}</loc><lastmod>{today}</lastmod><priority>{prio}</priority>{links}</url>")
(docs / "sitemap.xml").write_text('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">\n'
                                  + "\n".join(urls) + "\n</urlset>\n", encoding="utf-8")
print(f"✓ site {VER}: " + ", ".join(f"/{d}" for _, d, _, _ in LANGS) + " + sitemap.xml")
