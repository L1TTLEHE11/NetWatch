"""Website texts for tools/site.py. One dict per language; keys must match between languages."""

GUIDES = ["who-is-on-my-wifi", "best-wifi-channel", "prove-internet-outages", "mikrotik-monitoring-android"]

T = {
"en": dict(
    title="NetWatch — free network scanner and Wi-Fi analyzer for Android",
    desc="See who is connected to your Wi-Fi, why the internet is slow and when it drops. Free Android network scanner, Wi-Fi channel analyzer, speed test, outage monitor and MikroTik client. No ads, no tracking, open source.",
    keywords="network scanner, who is on my wifi, wifi analyzer, best wifi channel, lan scanner, ip scanner, speed test, internet outage monitor, ping, traceroute, port scanner, mikrotik, routeros, capsman, android",
    og_title="NetWatch — see what’s really going on in your network",
    og_desc="Who is connected, why the internet is slow, when it drops. Free Android app, no ads, open source.",
    og_img="og.png", currency="USD",
    nav=[("#scenarios", "Features"), ("#guides", "Guides"), ("#faq", "FAQ")],
    h1="See what’s really going on in&nbsp;your&nbsp;network",
    lead="Who is connected, why the internet is slow, when it drops — and what to show your ISP. A free network scanner and Wi-Fi analyzer for Android.",
    download="⬇ DOWNLOAD APK", source="SOURCE CODE",
    size_note="Android 8.0+ · {size} · version {ver}",
    qr_title="Scan to download on your phone",
    qr_note="Point the phone camera at the code.",
    trust=["Free forever", "No ads", "No account", "No tracking", "Open source"],
    h_scen="WHAT IT HELPS WITH",
    scen=[
        dict(q="Who is on my Wi-Fi?", shot="3_scan", guide="who-is-on-my-wifi",
             a="Scan the network in 10–30 seconds and see every device: router, phones, PCs, TVs, cameras, printers. NetWatch remembers your devices and marks new ones.",
             pts=["device type, name and open ports", "“NEW” tag for devices you haven’t seen before", "works with any router"]),
        dict(q="Why is the internet slow?", shot="2_speed", guide="best-wifi-channel",
             a="Test the speed, check the Wi-Fi signal and see how crowded your channel is. History shows when it’s slowest — e.g. “in the evening, 28% slower”.",
             pts=["multi-threaded speed test with ping and jitter", "Wi-Fi channel graph and the best channel for your router", "speed history by time of day"]),
        dict(q="Is my ISP dropping the connection?", shot="5_monitor", guide="prove-internet-outages",
             a="Leave the monitor running: it checks the router and the internet every 2 seconds and shows whether the problem is your Wi-Fi or your provider.",
             pts=["notifications when the connection drops and comes back", "loss, number of outages, total downtime", "PDF report to send to your ISP"]),
        dict(q="What is my router doing?", shot="6_mikrotik", guide="mikrotik-monitoring-android",
             a="Connect a MikroTik and see its load, live traffic per port, access points and Wi-Fi clients with their signal — right on your phone.",
             pts=["RouterOS v6 and v7, CAPsMAN", "DHCP leases and MAC addresses", "read-only access is enough"]),
    ],
    guide_link="Step-by-step guide →",
    h_admin="FOR ADMINS AND INSTALLERS",
    admin_lead="The same app scales from a flat to an office or a whole building.",
    admin=[("Site survey", "Signal, access point, ping and loss at every spot (“kitchen”, “2nd floor”), weak spots highlighted, Excel and PDF export."),
           ("Diagnostics", "Traceroute, DNS benchmark, port scanner with service names, interfaces and routes."),
           ("LAN tools", "Live ping of any host, TCP throughput test between devices, Wake-on-LAN."),
           ("Reports", "Full PDF / TXT report in one tap, CSV export, home-screen widget, history of every test.")],
    h_cmp="NETWATCH VS A TYPICAL FREE NETWORK APP",
    cmp_cols=["", "NetWatch", "Typical free app"],
    cmp=[("Ads", "none", "often"), ("Paid subscription for full features", "no", "often"), ("Account required", "no", "sometimes"),
         ("Analytics and ad trackers", "none", "usually"), ("MikroTik built in", "yes", "rarely"), ("Outage monitor with report", "yes", "rarely"),
         ("Open source", "yes", "rarely")],
    h_shots="SCREENSHOTS",
    shots=["Home", "LAN scanner", "Wi-Fi spectrum", "Speed", "Monitor", "MikroTik", "History", "More"],
    shots_alt=["Home: Wi-Fi signal, pings, last speed test", "LAN scanner: devices with types and ports", "Wi-Fi analyzer: channel graph and recommended channel",
               "Internet speed test", "Outage monitor", "MikroTik: interfaces, CAPsMAN, Wi-Fi clients", "Measurement history with speed summary", "More section"],
    h_guides="GUIDES",
    h_faq="QUESTIONS",
    faq=[
        ("Is it safe to install an APK from outside Google Play?",
         "NetWatch is open source: anyone can read the code it is built from. Every version is signed with the same key, and its SHA-256 checksum is published on GitHub. The app has no ads, no trackers and needs no account. An F-Droid version, built by F-Droid from the source code, is on its way."),
        ("Why does it ask for location access?",
         "Android hides the Wi-Fi network name and scan results from apps without it. NetWatch never reads or stores your coordinates."),
        ("Does it work without internet?",
         "Yes: the network scanner, Wi-Fi analyzer, ping, ports, site survey and MikroTik work inside your local network. Only the speed test and internet checks need a connection."),
        ("Is it really free?",
         "Yes. There is no paid version, no subscription and no ads. NetWatch is free software under the GNU GPL v3."),
        ("Can it hack Wi-Fi or other people’s devices?",
         "No. NetWatch is a diagnostic tool: it shows what is on your network and how it performs. Use it only on networks you own or have permission to check."),
        ("Is there an iPhone version?", "No, NetWatch is for Android 8.0 and newer, including tablets and Android TV boxes."),
        ("Which languages are supported?",
         "English and Russian; the app follows your system language. You can <a href=\"https://github.com/L1TTLEHE11/NetWatch/blob/main/CONTRIBUTING.md#translating\">help translate it</a> into yours."),
    ],
    h_install="How to install the APK",
    steps=["Download the APK on your phone (or scan the QR code).", "Open the file and allow installs from this source.",
           "Allow location access when asked — Android needs it to show the Wi-Fi network name."],
    install_note="New versions arrive in the app: More → Settings → Updates.",
    final_h="Free. No ads. No tracking.", final_p="Made by one person who needed a good network tool and decided to share it.",
    f_license="Free software under the <a href=\"{repo}/blob/main/LICENSE\">GNU GPL v3</a>",
    f_bug="Report a bug", f_privacy="Privacy", f_source="Source code", f_guides="Guides",
    g_back="← NetWatch", g_related="More guides", g_cta_h="Try it on your network", g_cta_p="NetWatch is free, has no ads and collects nothing.",
    g_updated="Updated",
),
"ru": dict(
    title="NetWatch — бесплатный сетевой сканер и Wi-Fi анализатор для Android",
    desc="Узнайте, кто подключён к вашему Wi-Fi, почему тормозит интернет и когда он пропадает. Бесплатный сканер сети, анализатор каналов Wi-Fi, замер скорости, мониторинг обрывов и MikroTik для Android. Без рекламы и слежки, открытый код.",
    keywords="сетевой сканер, кто подключен к моему wifi, анализатор wi-fi, выбрать канал wifi, сканер сети android, сканер ip, скорость интернета, пропадает интернет, мониторинг обрывов, пинг, traceroute, сканер портов, mikrotik, микротик, capsman",
    og_title="NetWatch — узнайте, что на самом деле происходит в вашей сети",
    og_desc="Кто подключён, почему тормозит интернет, когда он пропадает. Бесплатное приложение для Android без рекламы, с открытым кодом.",
    og_img="og-ru.png", currency="RUB",
    nav=[("#scenarios", "Возможности"), ("#guides", "Инструкции"), ("#faq", "Вопросы")],
    h1="Узнайте, что на&nbsp;самом деле происходит в&nbsp;вашей&nbsp;сети",
    lead="Кто подключён, почему тормозит интернет, когда он пропадает — и что показать провайдеру. Бесплатный сетевой сканер и Wi-Fi анализатор для Android.",
    download="⬇ СКАЧАТЬ APK", source="ИСХОДНЫЙ КОД",
    size_note="Android 8.0+ · {size} · версия {ver}",
    qr_title="Наведите камеру телефона",
    qr_note="QR-код скачает приложение прямо на телефон.",
    trust=["Бесплатно навсегда", "Без рекламы", "Без регистрации", "Без слежки", "Открытый код"],
    h_scen="С ЧЕМ ПОМОГАЕТ",
    scen=[
        dict(q="Кто сидит на моём Wi-Fi?", shot="3_scan", guide="who-is-on-my-wifi",
             a="Сканер за 10–30 секунд покажет все устройства в сети: роутер, телефоны, компьютеры, телевизоры, камеры, принтеры. NetWatch запоминает ваши устройства и отмечает новые.",
             pts=["тип устройства, имя и открытые порты", "метка «НОВОЕ» для незнакомых устройств", "работает с любым роутером"]),
        dict(q="Почему тормозит интернет?", shot="2_speed", guide="best-wifi-channel",
             a="Замерьте скорость, проверьте сигнал Wi-Fi и загрузку канала. История покажет, когда хуже всего — например, «вечером медленнее на 28%».",
             pts=["многопоточный замер с пингом и джиттером", "график каналов Wi-Fi и лучший канал для роутера", "история скорости по времени суток"]),
        dict(q="Провайдер говорит, что всё работает?", shot="5_monitor", guide="prove-internet-outages",
             a="Включите мониторинг: он проверяет роутер и интернет каждые 2 секунды и показывает, где проблема — в вашем Wi-Fi или у провайдера.",
             pts=["уведомления об обрыве и восстановлении", "потери, число обрывов, время без связи", "PDF-отчёт, который можно отправить провайдеру"]),
        dict(q="Что творится на роутере?", shot="6_mikrotik", guide="mikrotik-monitoring-android",
             a="Подключите MikroTik и смотрите нагрузку, трафик по портам в реальном времени, точки доступа и Wi-Fi клиентов с уровнем сигнала — прямо с телефона.",
             pts=["RouterOS v6 и v7, CAPsMAN", "DHCP-аренды и MAC-адреса", "достаточно доступа только на чтение"]),
    ],
    guide_link="Пошаговая инструкция →",
    h_admin="ДЛЯ АДМИНОВ И МОНТАЖНИКОВ",
    admin_lead="То же приложение подходит и для квартиры, и для офиса, и для целого здания.",
    admin=[("Обход объекта", "Сигнал, точка доступа, пинг и потери в каждой точке («кухня», «2 этаж»), проблемные места подсвечиваются, выгрузка в Excel и PDF."),
           ("Диагностика", "Traceroute, сравнение DNS-серверов, сканер портов с расшифровкой служб, интерфейсы и маршруты."),
           ("Локальная сеть", "Live-пинг любого узла, TCP-тест скорости между устройствами, Wake-on-LAN."),
           ("Отчёты", "Полный отчёт PDF / TXT одной кнопкой, выгрузка CSV, виджет на рабочий стол, история всех замеров.")],
    h_cmp="NETWATCH И ТИПИЧНОЕ БЕСПЛАТНОЕ ПРИЛОЖЕНИЕ",
    cmp_cols=["", "NetWatch", "Типичное бесплатное"],
    cmp=[("Реклама", "нет", "часто"), ("Подписка за полные функции", "нет", "часто"), ("Нужна регистрация", "нет", "бывает"),
         ("Аналитика и рекламные трекеры", "нет", "обычно"), ("Встроенный MikroTik", "есть", "редко"), ("Мониторинг обрывов с отчётом", "есть", "редко"),
         ("Открытый код", "да", "редко")],
    h_shots="СКРИНШОТЫ",
    shots=["Главная", "Сканер сети", "Wi-Fi эфир", "Скорость", "Мониторинг", "MikroTik", "История", "Ещё"],
    shots_alt=["Главная: сигнал Wi-Fi, пинги, последний замер скорости", "Сканер сети: список устройств с типами и портами", "Wi-Fi анализатор: график каналов и рекомендованный канал",
               "Замер скорости интернета", "Мониторинг обрывов связи", "MikroTik: интерфейсы, CAPsMAN, клиенты Wi-Fi", "История замеров со сводкой скорости", "Раздел «Ещё»"],
    h_guides="ИНСТРУКЦИИ",
    h_faq="ВОПРОСЫ",
    faq=[
        ("Безопасно ли ставить APK не из Google Play?",
         "У NetWatch открытый код: любой может посмотреть, из чего собрано приложение. Каждая версия подписана одним и тем же ключом, а её контрольная сумма SHA-256 опубликована на GitHub. В приложении нет рекламы, трекеров и регистрации. Скоро появится версия в F-Droid — её собирает сам F-Droid из исходного кода."),
        ("Зачем приложению доступ к геолокации?",
         "Без него Android не показывает приложениям имя Wi-Fi сети и результаты сканирования эфира. Координаты NetWatch не читает и не сохраняет."),
        ("Работает ли без интернета?",
         "Да: сканер сети, Wi-Fi анализатор, пинг, порты, обход объекта и MikroTik работают внутри локальной сети. Интернет нужен только для замера скорости и проверки связи с внешним миром."),
        ("Это правда бесплатно?",
         "Да. Платной версии, подписки и рекламы нет. NetWatch — свободное ПО под лицензией GNU GPL v3."),
        ("Можно ли им взломать Wi-Fi или чужие устройства?",
         "Нет. NetWatch — инструмент диагностики: он показывает, что есть в вашей сети и как она работает. Используйте его только в своих сетях или с разрешения владельца."),
        ("Есть ли версия для iPhone?", "Нет, NetWatch работает на Android 8.0 и новее, в том числе на планшетах и ТВ-приставках."),
        ("Какие языки поддерживаются?",
         "Русский и английский; приложение подстраивается под язык системы. Можно <a href=\"https://github.com/L1TTLEHE11/NetWatch/blob/main/CONTRIBUTING.md#translating\">помочь с переводом</a> на другие языки."),
    ],
    h_install="Как установить APK",
    steps=["Скачайте APK на телефон (или отсканируйте QR-код).", "Откройте файл и разрешите установку из этого источника.",
           "Разрешите доступ к геолокации, когда приложение попросит, — без него Android скрывает имя Wi-Fi сети."],
    install_note="Новые версии приходят прямо в приложение: «Ещё → Настройки → Обновления».",
    final_h="Бесплатно. Без рекламы. Без слежки.", final_p="Сделано одним человеком, которому был нужен хороший сетевой инструмент, — и он решил им поделиться.",
    f_license="Свободное ПО под лицензией <a href=\"{repo}/blob/main/LICENSE\">GNU GPL v3</a>",
    f_bug="Сообщить об ошибке", f_privacy="Конфиденциальность", f_source="Исходный код", f_guides="Инструкции",
    g_back="← NetWatch", g_related="Другие инструкции", g_cta_h="Проверьте свою сеть", g_cta_p="NetWatch бесплатный, без рекламы и ничего не собирает.",
    g_updated="Обновлено",
),
}

# ---------------------------------------------------------------- guides
G = {
"en": {
"who-is-on-my-wifi": dict(
    title="How to see who is connected to your Wi-Fi (Android)",
    short="Who is connected to my Wi-Fi",
    desc="Find every device on your Wi-Fi network from an Android phone in 30 seconds, spot unknown devices and lock out strangers. Free, no ads.",
    h1="How to see who is connected to your Wi-Fi",
    intro="A neighbour using your Wi-Fi, a forgotten smart plug or an old phone — all of them slow your network down and some are a security risk. Here is how to list every device on your network from an Android phone.",
    shot="3_scan",
    body="""
<h2>1. Connect to the network you want to check</h2>
<p>Your phone must be on the same Wi-Fi as the devices you are looking for. Install <a href="./">NetWatch</a> and allow location access when asked — Android needs it to show the network name.</p>

<h2>2. Run the scanner</h2>
<p>Open <b>Network → Scanner</b> and tap <b>Scan network</b>. NetWatch checks every address of your subnet in 10–30 seconds. It sends several pings and probes common ports, so it also finds devices that ignore a single ping, such as TVs in power-saving mode.</p>

<h2>3. Read the list</h2>
<ul>
<li><b>Type</b> — router, MikroTik, phone, iPhone, Windows PC, printer, IP camera, Android TV, Smart TV, NAS.</li>
<li><b>Name</b> — taken from NetBIOS, mDNS and UPnP, e.g. “Living Room TV” or “OFFICE-PC”.</li>
<li><b>Open ports</b> — what services a device offers (web page, file sharing, remote desktop).</li>
</ul>

<h2>4. Find out what an unknown device is</h2>
<ul>
<li>Compare the list with the devices you own: phones, laptops, TV, consoles, smart speakers, cameras, the printer.</li>
<li>Switch your own devices off one by one and scan again — whatever disappears was yours.</li>
<li>Open the router’s page in a browser (the address is shown on the NetWatch home screen) and look at its list of connected clients or DHCP leases.</li>
<li>Have a MikroTik? Connect it in <b>More → MikroTik</b>: the scanner then shows MAC addresses and names from the router.</li>
</ul>

<h2>5. Let NetWatch watch for new devices</h2>
<p>The scanner remembers devices for each network. Next time, everything that wasn’t there before is marked <b>NEW</b>, so a stranger stands out immediately.</p>

<h2>Found a stranger? Lock them out</h2>
<ul>
<li>Change the Wi-Fi password in the router settings — every device will have to reconnect.</li>
<li>Use WPA2 or WPA3 security; never “open” or WEP (NetWatch shows the security type of every network in <b>Wi-Fi → Spectrum</b>).</li>
<li>Turn off WPS and change the router’s admin password from the default.</li>
</ul>

<h2>Why some devices may be missing</h2>
<p>Phones in deep sleep and devices with a strict firewall may not answer at all — scan again after a minute. Android does not let apps read other devices’ MAC addresses, so NetWatch identifies devices by their behaviour, names and ports instead.</p>
"""),
"best-wifi-channel": dict(
    title="How to choose the best Wi-Fi channel (Android app)",
    short="Best Wi-Fi channel",
    desc="Slow Wi-Fi in an apartment building? See how crowded each Wi-Fi channel is and switch your router to the least busy one. Free Android Wi-Fi analyzer.",
    h1="How to choose the best Wi-Fi channel",
    intro="In apartment buildings dozens of networks share the same few channels, and each one slows the others down. Moving your router to a free channel is often the cheapest speed boost you can get.",
    shot="4_wifi",
    body="""
<h2>1. Look at the air</h2>
<p>Open <b>Wi-Fi → Spectrum</b> in <a href="./">NetWatch</a>. Every nearby network is drawn as a hill over the channels it uses; the higher the hill, the stronger the signal. Where hills overlap, networks interfere with each other.</p>

<h2>2. Check the recommendation</h2>
<ul>
<li><b>2.4 GHz</b>: only channels <b>1, 6 and 11</b> don’t overlap, so NetWatch picks the least loaded of them.</li>
<li><b>5 GHz</b>: there are many more channels and much less congestion; NetWatch recommends one of 36–48 or 149–165, which work with every device and don’t require radar detection (DFS).</li>
<li>If your current channel is already fine, NetWatch says so — no need to change anything.</li>
</ul>

<h2>3. Change the channel on the router</h2>
<p>Open the router’s settings page (its address is on the NetWatch home screen), go to the wireless settings and set the recommended channel instead of “Auto”. In crowded buildings use a <b>20 MHz</b> channel width on 2.4 GHz — wider channels collide with more neighbours.</p>

<h2>4. Check the result</h2>
<p>Run a <b>Speed</b> test before and after the change, and look at the signal graph on the home screen. NetWatch keeps every test in <b>History</b>, so the difference is easy to compare.</p>

<h2>Tips</h2>
<ul>
<li>If your devices support it, prefer 5 GHz: it is faster and less crowded, but reaches less far through walls.</li>
<li>Signal weak in some rooms? Walk around with <b>Wi-Fi → Site survey</b> and measure each spot — it shows where you need a second access point.</li>
<li>Android allows only about 4 Wi-Fi scans every 2 minutes. If the graph doesn’t refresh, wait a little.</li>
</ul>
"""),
"prove-internet-outages": dict(
    title="How to prove internet outages to your ISP",
    short="Prove outages to your ISP",
    desc="Internet keeps dropping but your ISP says everything is fine? Log every outage with an Android phone, see whether it’s your Wi-Fi or the provider, and send a PDF report.",
    h1="How to prove internet outages to your ISP",
    intro="“Everything works on our side” — the usual answer from support. To get the problem fixed, you need times, durations and proof that the problem is not your Wi-Fi. A phone can collect all of it.",
    shot="5_monitor",
    body="""
<h2>1. Start the monitor</h2>
<p>In <a href="./">NetWatch</a> open <b>More → Monitoring</b> and tap <b>Start monitoring</b>. Allow notifications and background work — the monitor keeps running when the app is minimized. Keep the phone on Wi-Fi near the router and preferably on the charger.</p>

<h2>2. Let it see where the problem is</h2>
<p>Every 2 seconds NetWatch pings two things separately: <b>your router</b> and <b>the internet</b>.</p>
<ul>
<li>Router doesn’t answer → the problem is inside your home: Wi-Fi, the router itself or its power.</li>
<li>Router answers, internet doesn’t → the problem is on the provider’s side. This is exactly what support needs to hear.</li>
</ul>

<h2>3. Leave it for a day or more</h2>
<p>Each outage is recorded with its start time and duration, and you get a notification when the connection drops and when it comes back. The screen shows packet loss, the number of outages and total downtime.</p>

<h2>4. Add speed tests</h2>
<p>Outages are not the only problem — the internet can also be slow at certain hours. Run a few <b>Speed</b> tests in the morning and in the evening: <b>History</b> shows the average and how much slower it gets at different times of day.</p>

<h2>5. Send the report</h2>
<p>Tap <b>More → Report → PDF</b>. The report includes the monitor sessions with every outage, loss and downtime, speed tests, the Wi-Fi signal and network details. Attach it to your support ticket or send it in the chat with your provider.</p>

<h2>What to write to support</h2>
<p>Something like: “From 18:00 to 23:00 the internet dropped 7 times, 14 minutes in total. The router was reachable the whole time, so the problem is not my home network. Report attached.” Specific numbers get a much faster reaction than “it’s slow sometimes”.</p>
"""),
"mikrotik-monitoring-android": dict(
    title="Monitor a MikroTik router from your Android phone",
    short="MikroTik monitoring from a phone",
    desc="See MikroTik CPU, memory, temperature, live interface traffic, CAPsMAN access points, Wi-Fi clients and DHCP leases from an Android phone via the RouterOS API. Free app.",
    h1="Monitor a MikroTik router from your phone",
    intro="Winbox is great at a desk. Walking around a building, you want the same answers in your pocket: is the router overloaded, which port is busy, which access point a client is on and how strong its signal is.",
    shot="6_mikrotik",
    body="""
<h2>1. Enable the API on the router</h2>
<p>In Winbox or WebFig open <b>IP → Services</b> and enable <b>api</b> (port 8728) or <b>api-ssl</b> (port 8729). In the terminal:</p>
<pre>/ip service enable api
/ip service set api address=192.168.88.0/24</pre>
<p>The second line allows API access only from your local network — don’t expose the API to the internet.</p>

<h2>2. Create a read-only user</h2>
<p>NetWatch only reads data, so a user from the <b>read</b> group is enough:</p>
<pre>/user add name=netwatch group=read password=YOUR-PASSWORD</pre>

<h2>3. Connect from NetWatch</h2>
<p>Open <b>More → MikroTik</b> in <a href="./">NetWatch</a>, enter the router address (usually the gateway shown on the home screen), the user and the password. Tick “remember password” if you want — the password is stored only on your phone. Works with RouterOS v6 and v7.</p>

<h2>4. What you see</h2>
<ul>
<li><b>System</b>: identity, model, RouterOS version, uptime, temperature, CPU and memory load.</li>
<li><b>Interfaces</b>: live download and upload on every port and bridge.</li>
<li><b>CAPsMAN</b>: managed access points and their state.</li>
<li><b>Wi-Fi clients</b>: which access point and band each client uses, its signal and connection time.</li>
<li><b>DHCP leases</b>: IP, MAC and host name of every device.</li>
</ul>

<h2>5. Use it with the other tools</h2>
<p>Once a MikroTik is connected, the network scanner shows MAC addresses and names from the router. Combine it with <b>Wi-Fi → Site survey</b> to walk a building and map the signal of every access point, and with <b>Monitoring</b> to catch uplink outages.</p>
"""),
},
"ru": {
"who-is-on-my-wifi": dict(
    title="Как узнать, кто подключён к вашему Wi-Fi (Android)",
    short="Кто подключён к моему Wi-Fi",
    desc="Как за 30 секунд найти все устройства в своей Wi-Fi сети с телефона на Android, распознать чужие и отключить их. Бесплатно, без рекламы.",
    h1="Как узнать, кто подключён к вашему Wi-Fi",
    intro="Сосед на вашем Wi-Fi, забытая умная розетка или старый телефон — всё это тормозит сеть, а иногда и небезопасно. Вот как увидеть все устройства в сети с телефона на Android.",
    shot="3_scan",
    body="""
<h2>1. Подключитесь к сети, которую хотите проверить</h2>
<p>Телефон должен быть в том же Wi-Fi, что и искомые устройства. Установите <a href="./">NetWatch</a> и разрешите доступ к геолокации, когда приложение попросит, — без него Android скрывает имя сети.</p>

<h2>2. Запустите сканер</h2>
<p>Откройте <b>Сеть → Сканер</b> и нажмите <b>Сканировать сеть</b>. За 10–30 секунд NetWatch проверит все адреса подсети. Он отправляет несколько пингов и проверяет популярные порты, поэтому находит и устройства, которые не отвечают на одиночный пинг, — например, телевизоры в режиме энергосбережения.</p>

<h2>3. Разберитесь в списке</h2>
<ul>
<li><b>Тип</b> — роутер, MikroTik, телефон, iPhone, компьютер Windows, принтер, IP-камера, Android TV, Smart TV, NAS.</li>
<li><b>Имя</b> — из NetBIOS, mDNS и UPnP, например «Гостиная TV» или «OFFICE-PC».</li>
<li><b>Открытые порты</b> — какие службы есть на устройстве (веб-страница, общие папки, удалённый рабочий стол).</li>
</ul>

<h2>4. Выясните, что за незнакомое устройство</h2>
<ul>
<li>Сверьте список со своими устройствами: телефоны, ноутбуки, телевизор, приставки, умные колонки, камеры, принтер.</li>
<li>Выключайте свои устройства по одному и сканируйте снова — пропавшее было вашим.</li>
<li>Откройте страницу роутера в браузере (адрес есть на главной NetWatch) и посмотрите список клиентов или DHCP-аренд.</li>
<li>Есть MikroTik? Подключите его в <b>Ещё → MikroTik</b>: сканер начнёт показывать MAC-адреса и имена из роутера.</li>
</ul>

<h2>5. Пусть NetWatch следит за новыми устройствами</h2>
<p>Сканер запоминает устройства для каждой сети. В следующий раз всё, чего раньше не было, получит метку <b>НОВОЕ</b> — чужое устройство сразу бросится в глаза.</p>

<h2>Нашли чужого? Отключите его</h2>
<ul>
<li>Смените пароль Wi-Fi в настройках роутера — всем устройствам придётся подключиться заново.</li>
<li>Используйте защиту WPA2 или WPA3, а не «открытую» сеть или WEP (тип защиты всех сетей виден в <b>Wi-Fi → Эфир</b>).</li>
<li>Выключите WPS и смените стандартный пароль администратора роутера.</li>
</ul>

<h2>Почему некоторых устройств может не быть</h2>
<p>Телефоны в глубоком сне и устройства со строгим файрволом могут вообще не отвечать — повторите сканирование через минуту. Android не даёт приложениям читать MAC-адреса других устройств, поэтому NetWatch узнаёт устройства по поведению, именам и портам.</p>
"""),
"best-wifi-channel": dict(
    title="Как выбрать лучший канал Wi-Fi (приложение для Android)",
    short="Лучший канал Wi-Fi",
    desc="Тормозит Wi-Fi в многоквартирном доме? Посмотрите загрузку каналов Wi-Fi и переключите роутер на самый свободный. Бесплатный Wi-Fi анализатор для Android.",
    h1="Как выбрать лучший канал Wi-Fi",
    intro="В многоквартирных домах десятки сетей делят одни и те же несколько каналов и мешают друг другу. Перевести роутер на свободный канал — часто самый дешёвый способ ускорить Wi-Fi.",
    shot="4_wifi",
    body="""
<h2>1. Посмотрите на эфир</h2>
<p>Откройте <b>Wi-Fi → Эфир</b> в <a href="./">NetWatch</a>. Каждая сеть рядом нарисована «горкой» над каналами, которые она занимает; чем выше горка, тем сильнее сигнал. Там, где горки перекрываются, сети мешают друг другу.</p>

<h2>2. Посмотрите рекомендацию</h2>
<ul>
<li><b>2.4 ГГц</b>: не перекрываются только каналы <b>1, 6 и 11</b>, поэтому NetWatch выбирает самый свободный из них.</li>
<li><b>5 ГГц</b>: каналов гораздо больше, а загрузка меньше; NetWatch советует один из 36–48 или 149–165 — они работают со всеми устройствами и не требуют проверки на радары (DFS).</li>
<li>Если ваш текущий канал и так хорош, NetWatch так и скажет — ничего менять не нужно.</li>
</ul>

<h2>3. Смените канал на роутере</h2>
<p>Откройте страницу настроек роутера (адрес есть на главной NetWatch), зайдите в настройки беспроводной сети и вместо «Авто» выберите рекомендованный канал. В плотной застройке ставьте ширину канала <b>20 МГц</b> на 2.4 ГГц — широкие каналы пересекаются с большим числом соседей.</p>

<h2>4. Проверьте результат</h2>
<p>Сделайте замер <b>Скорости</b> до и после и посмотрите график сигнала на главной. NetWatch сохраняет все замеры в <b>Историю</b>, так что разницу легко сравнить.</p>

<h2>Советы</h2>
<ul>
<li>Если устройства поддерживают 5 ГГц — используйте его: быстрее и свободнее, но хуже проходит через стены.</li>
<li>В части комнат слабый сигнал? Пройдитесь с <b>Wi-Fi → Обход объекта</b> и замерьте каждую точку — станет видно, где нужна вторая точка доступа.</li>
<li>Android разрешает примерно 4 сканирования Wi-Fi за 2 минуты. Если график не обновляется, немного подождите.</li>
</ul>
"""),
"prove-internet-outages": dict(
    title="Как доказать провайдеру, что интернет пропадает",
    short="Доказать обрывы провайдеру",
    desc="Интернет пропадает, а провайдер говорит, что всё работает? Запишите каждый обрыв с телефона на Android, поймите, виноват Wi-Fi или провайдер, и отправьте PDF-отчёт.",
    h1="Как доказать провайдеру, что интернет пропадает",
    intro="«С нашей стороны всё работает» — обычный ответ поддержки. Чтобы проблему начали решать, нужны время, длительность и доказательство, что дело не в вашем Wi-Fi. Всё это может собрать телефон.",
    shot="5_monitor",
    body="""
<h2>1. Включите мониторинг</h2>
<p>В <a href="./">NetWatch</a> откройте <b>Ещё → Мониторинг</b> и нажмите <b>Запустить мониторинг</b>. Разрешите уведомления и работу в фоне — мониторинг продолжит работать, когда приложение свёрнуто. Держите телефон в Wi-Fi рядом с роутером, лучше на зарядке.</p>

<h2>2. Он сам покажет, где проблема</h2>
<p>Каждые 2 секунды NetWatch отдельно проверяет <b>ваш роутер</b> и <b>интернет</b>.</p>
<ul>
<li>Роутер не отвечает → проблема дома: Wi-Fi, сам роутер или его питание.</li>
<li>Роутер отвечает, а интернет нет → проблема на стороне провайдера. Именно это и нужно сказать поддержке.</li>
</ul>

<h2>3. Оставьте его на сутки или дольше</h2>
<p>Каждый обрыв записывается со временем начала и длительностью, а при обрыве и восстановлении приходит уведомление. На экране видны потери пакетов, число обрывов и общее время без связи.</p>

<h2>4. Добавьте замеры скорости</h2>
<p>Бывает, что интернет не пропадает, а тормозит в определённые часы. Сделайте несколько замеров <b>Скорости</b> утром и вечером: <b>История</b> покажет среднюю скорость и насколько медленнее бывает в разное время суток.</p>

<h2>5. Отправьте отчёт</h2>
<p>Нажмите <b>Ещё → Отчёт → PDF</b>. В отчёте — сессии мониторинга с каждым обрывом, потери и время без связи, замеры скорости, сигнал Wi-Fi и параметры сети. Приложите его к заявке или отправьте в чат провайдера.</p>

<h2>Что написать в поддержку</h2>
<p>Например: «С 18:00 до 23:00 интернет пропадал 7 раз, всего 14 минут. Роутер всё это время был доступен, значит, проблема не в домашней сети. Отчёт прилагаю». Конкретные цифры работают гораздо быстрее, чем «иногда тормозит».</p>
"""),
"mikrotik-monitoring-android": dict(
    title="Мониторинг MikroTik с телефона на Android",
    short="Мониторинг MikroTik с телефона",
    desc="Нагрузка CPU и памяти, температура, трафик по интерфейсам, точки CAPsMAN, Wi-Fi клиенты и DHCP-аренды MikroTik на телефоне через RouterOS API. Бесплатное приложение.",
    h1="Мониторинг MikroTik с телефона",
    intro="За столом есть Winbox. А когда ходишь по зданию, те же ответы нужны в кармане: не перегружен ли роутер, какой порт занят, к какой точке подключён клиент и какой у него сигнал.",
    shot="6_mikrotik",
    body="""
<h2>1. Включите API на роутере</h2>
<p>В Winbox или WebFig откройте <b>IP → Services</b> и включите <b>api</b> (порт 8728) или <b>api-ssl</b> (порт 8729). В терминале:</p>
<pre>/ip service enable api
/ip service set api address=192.168.88.0/24</pre>
<p>Вторая строка разрешает доступ к API только из локальной сети — не открывайте API в интернет.</p>

<h2>2. Создайте пользователя только для чтения</h2>
<p>NetWatch только читает данные, поэтому хватит пользователя из группы <b>read</b>:</p>
<pre>/user add name=netwatch group=read password=ВАШ-ПАРОЛЬ</pre>

<h2>3. Подключитесь из NetWatch</h2>
<p>Откройте <b>Ещё → MikroTik</b> в <a href="./">NetWatch</a>, введите адрес роутера (обычно это шлюз с главной экрана), пользователя и пароль. Можно отметить «запомнить пароль» — пароль хранится только на телефоне. Работает с RouterOS v6 и v7.</p>

<h2>4. Что вы увидите</h2>
<ul>
<li><b>Система</b>: имя, модель, версия RouterOS, аптайм, температура, загрузка CPU и памяти.</li>
<li><b>Интерфейсы</b>: скачивание и отдача на каждом порту и бридже в реальном времени.</li>
<li><b>CAPsMAN</b>: управляемые точки доступа и их состояние.</li>
<li><b>Wi-Fi клиенты</b>: к какой точке и диапазону подключён каждый клиент, его сигнал и время подключения.</li>
<li><b>DHCP-аренды</b>: IP, MAC и имя каждого устройства.</li>
</ul>

<h2>5. Используйте вместе с другими инструментами</h2>
<p>Когда MikroTik подключён, сканер сети показывает MAC-адреса и имена из роутера. Вместе с <b>Wi-Fi → Обход объекта</b> можно пройти здание и составить карту сигнала каждой точки, а <b>Мониторинг</b> поймает обрывы канала до провайдера.</p>
"""),
},
}

# ---------------------------------------------------------------- privacy
P = {
"en": dict(
    title="Privacy policy — NetWatch",
    desc="NetWatch privacy policy: the app and the website collect no personal data and use no analytics.",
    h1="NetWatch privacy policy",
    since="Effective from 17 October 2026 · developer: L1TTLEHE11",
    body="""<h2>In short</h2>
<p>NetWatch does not collect, sell or share your data. The app has no ads, no analytics, no accounts and no developer servers. This website has no analytics, no cookies and loads nothing from third-party servers.</p>

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
<li>GitHub and jsDelivr — to check for and download updates (automatic checks can be turned off in settings). The F-Droid version has no built-in updater and never contacts them.</li>
</ul>
<p>These servers see your IP address, as with any website. The app sends them no device or user identifiers.</p>

<h2>Sharing</h2>
<p>Data leaves the phone only when you act — when you share a PDF, TXT or CSV report yourself via the “Share” menu.</p>

<h2>Website</h2>
<p>The site is hosted on GitHub Pages, which may keep technical server logs (such as IP addresses) under the <a href="https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement">GitHub privacy statement</a>. The site itself only remembers your language choice in your browser’s local storage.</p>

<h2>Children</h2>
<p>The app is not intended for children and collects no data about them.</p>

<h2>Changes and contact</h2>
<p>Changes to this policy are published on this page. Questions: <a href="{repo}/issues">GitHub issues</a>. The source code is open: <a href="{repo}">github.com/L1TTLEHE11/NetWatch</a>.</p>""",
),
"ru": dict(
    title="Политика конфиденциальности — NetWatch",
    desc="Политика конфиденциальности NetWatch: приложение и сайт не собирают персональные данные и не используют аналитику.",
    h1="Политика конфиденциальности приложения NetWatch",
    since="Действует с 17 октября 2026 года · разработчик: L1TTLEHE11",
    body="""<h2>Коротко</h2>
<p>NetWatch не собирает, не продаёт и не передаёт ваши данные. В приложении нет рекламы, аналитики, учётных записей и собственных серверов разработчика. На этом сайте нет аналитики и cookies, и он ничего не загружает со сторонних серверов.</p>

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
<li>GitHub и jsDelivr — для проверки и загрузки обновлений (автопроверку можно отключить в настройках). В версии из F-Droid встроенного обновления нет, и она к ним не обращается.</li>
</ul>
<p>Эти серверы видят ваш IP-адрес, как при открытии любого сайта. Никаких идентификаторов устройства или пользователя приложение им не передаёт.</p>

<h2>Передача данных</h2>
<p>Данные покидают телефон только по вашему действию — когда вы сами отправляете отчёт PDF, TXT или CSV через меню «Поделиться».</p>

<h2>Сайт</h2>
<p>Сайт размещён на GitHub Pages, который может хранить технические журналы сервера (например, IP-адреса) по <a href="https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement">политике конфиденциальности GitHub</a>. Сам сайт запоминает только выбранный язык — в локальном хранилище вашего браузера.</p>

<h2>Дети</h2>
<p>Приложение не предназначено для детей и не собирает данные о них.</p>

<h2>Изменения и контакты</h2>
<p>Изменения этой политики публикуются на этой странице. Вопросы — через <a href="{repo}/issues">issues на GitHub</a>. Исходный код открыт: <a href="{repo}">github.com/L1TTLEHE11/NetWatch</a>.</p>""",
),
}
