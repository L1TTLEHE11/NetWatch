<div align="center">

<img src="docs/icon.png" width="96" alt="Иконка NetWatch">

# NetWatch: Network Scanner

**Бесплатный сетевой сканер и Wi-Fi анализатор для Android**<br>
Сканер сети · Wi-Fi эфир · пинг · скорость интернета · мониторинг обрывов · traceroute · порты · MikroTik

[![Версия](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fraw.githubusercontent.com%2FL1TTLEHE11%2FNetWatch%2Fmain%2Fupdate.json&query=%24.versionName&label=%D0%B2%D0%B5%D1%80%D1%81%D0%B8%D1%8F&color=ff003c)](https://github.com/L1TTLEHE11/NetWatch/raw/main/apk/NetWatch-2.4.2-universal.apk)
[![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-00f0ff)](#установка)
[![Лицензия GPL-3.0](https://img.shields.io/badge/%D0%BB%D0%B8%D1%86%D0%B5%D0%BD%D0%B7%D0%B8%D1%8F-GPL--3.0-fcee0a)](LICENSE)
[![Без рекламы](https://img.shields.io/badge/%D1%80%D0%B5%D0%BA%D0%BB%D0%B0%D0%BC%D0%B0-%D0%BD%D0%B5%D1%82-00ff66)](#приватность)

### [📥 Скачать APK](https://github.com/L1TTLEHE11/NetWatch/raw/main/apk/NetWatch-2.4.2-universal.apk) · [Сайт](https://l1ttlehe11.github.io/NetWatch/) · [English](#english)

</div>

<table>
<tr>
<td><img src="docs/screenshots/1_home.png" alt="Главная: сигнал, пинги, последний замер"></td>
<td><img src="docs/screenshots/3_scan.png" alt="Сканер сети: устройства с типами"></td>
<td><img src="docs/screenshots/4_wifi.png" alt="Wi-Fi анализатор: каналы и рекомендация"></td>
<td><img src="docs/screenshots/2_speed.png" alt="Интернетометр"></td>
</tr>
<tr>
<td><img src="docs/screenshots/5_monitor.png" alt="Мониторинг обрывов"></td>
<td><img src="docs/screenshots/6_mikrotik.png" alt="MikroTik: интерфейсы, CAPsMAN, клиенты"></td>
<td><img src="docs/screenshots/7_history.png" alt="История замеров"></td>
<td><img src="docs/screenshots/8_more.png" alt="Раздел «Ещё»"></td>
</tr>
</table>

NetWatch — набор инструментов для тех, кто настраивает и чинит сети: от домашнего роутера до отеля с десятком точек доступа. Всё в одном приложении, бесплатно, без рекламы и без сбора данных. Изначально — Android-версия десктопной утилиты NetWatch (Python / Tkinter).

## Возможности

| Раздел | Что умеет |
|---|---|
| **01 Главная** | Сигнал Wi-Fi в dBm с графиком, пинг до роутера и интернета, быстрые действия, последний замер скорости и итог за неделю, устройства в сети, статус мониторинга |
| **02 Скорость** | Многопоточный замер (8 потоков ↓, 6 ↑), цифры и шкалы обновляются 10 раз в секунду, пинг и джиттер, график и история |
| **03 Сеть → Сканер** | Все устройства подсети за 10–30 с, тип устройства (роутер, MikroTik, камера, принтер, Android TV, Smart TV, ПК, iPhone, NAS), открытые порты, имена через NetBIOS, mDNS и UPnP, метка «НОВОЕ» для новых устройств |
| **03 Сеть → Пинг · TCP** | Live-пинг любого узла со статистикой потерь, TCP-тест скорости до устройства в локальной сети, Wake-on-LAN |
| **03 Сеть → Диагностика** | Traceroute, сравнение DNS-серверов (провайдер, Google, Cloudflare, Яндекс, Quad9, AdGuard), сканер портов с расшифровкой служб, интерфейсы и маршруты |
| **04 Wi-Fi → Эфир** | Все сети вокруг, график загрузки каналов 2.4 / 5 / 6 ГГц, рекомендация лучшего канала |
| **04 Wi-Fi → Обход** | Замеры по точкам объекта («номер 204», «ресепшн»): сигнал, точка доступа, пинг, потери, скорость; проблемные точки подсвечиваются; выгрузка в Excel и PDF |
| **05 Ещё → Мониторинг** | Фоновая проверка роутера и интернета каждые 2 с, уведомления об обрывах, потери, время без связи, журнал событий |
| **05 Ещё → MikroTik** | RouterOS API (v6 и v7): CPU, память, температура, трафик по интерфейсам, точки CAPsMAN, Wi-Fi клиенты с сигналом, DHCP-аренды |
| **05 Ещё → История** | Все замеры, сканы и сессии сохраняются автоматически; фильтры по типу, сети и периоду; сводка скорости за период с выгрузкой в Excel |

Также: PDF-отчёт одной кнопкой, виджет на рабочий стол, крупный шрифт, обновление прямо из приложения с проверкой SHA-256.

## Установка

1. Скачайте [**NetWatch-2.4.2-universal.apk**](https://github.com/L1TTLEHE11/NetWatch/raw/main/apk/NetWatch-2.4.2-universal.apk). Один файл для всех устройств на Android 8.0+ (arm64-v8a, armeabi-v7a, x86, x86_64).
2. Откройте его на телефоне и разрешите установку из этого источника.
3. Разрешите доступ к геолокации, когда приложение попросит: без него Android скрывает имя Wi-Fi сети.

Дальше приложение обновляется само: «Ещё → Настройки → Обновления».

SHA-256: `5d242123f6df6489a7579eeacd444875bd24103ba388686792ac2a15dbe18ec8`

## Приватность

NetWatch **ничего не собирает и никуда не отправляет**: нет аналитики, рекламы, учётных записей и серверов. История замеров хранится только на телефоне. Подробно — в [политике конфиденциальности](https://l1ttlehe11.github.io/NetWatch/privacy.html).

| Разрешение | Зачем |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | пинги, замеры, параметры сети |
| `ACCESS_FINE/COARSE_LOCATION` | без него Android скрывает SSID и результаты сканирования Wi-Fi; координаты не используются |
| `CHANGE_WIFI_STATE` | запуск сканирования Wi-Fi эфира |
| `FOREGROUND_SERVICE`, `WAKE_LOCK`, `POST_NOTIFICATIONS` | фоновый мониторинг и уведомления об обрывах |
| `REQUEST_INSTALL_PACKAGES` | установка обновлений, скачанных из приложения |

## Ошибки и предложения

Нашли ошибку или есть идея — [создайте issue](https://github.com/L1TTLEHE11/NetWatch/issues/new/choose). Pull request'ы приветствуются.

## Для разработчиков

**Kotlin + Jetpack Compose**, minSdk 26, targetSdk 35. Без сторонних сетевых библиотек: RouterOS API, mDNS, SSDP, NetBIOS, DNS, Wake-on-LAN реализованы в коде проекта.

```bash
# нужен JDK 17+ и Android SDK (platform 35, build-tools 35)
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleRelease          # APK: app/build/outputs/apk/release/
./gradlew testDebugUnitTest        # тесты протоколов + скриншоты экранов (Robolectric + Roborazzi)
```

<details>
<summary>Подпись, выпуск версий, структура проекта</summary>

**Подпись.** Release подписывается ключом из `keystore.properties` → `keys/netwatch-release.jks` (оба в `.gitignore`). Обновление ставится поверх только при подписи тем же ключом. Без `keystore.properties` сборка подпишется debug-ключом.

**Выпуск версии.**
1. Поднять `versionCode` и `versionName` в `app/build.gradle.kts`.
2. `./gradlew assembleRelease`
3. `python3 tools/release.py "что нового 1" "что нового 2"` — APK в `apk/` (хранятся 3 последние), `update.json`, ссылки в README и на сайте, список изменений для каталогов.
4. `git push`, затем `curl -s https://purge.jsdelivr.net/gh/L1TTLEHE11/NetWatch@main/update.json`.

**Скриншоты для README и сайта:** `./gradlew testDebugUnitTest --tests '*DemoShots*'` (экраны с примерными данными → `docs/screenshots/`).

```
update.json                — актуальная версия для обновления из приложения
tools/release.py           — выпуск новой версии
docs/                      — сайт (GitHub Pages), политика конфиденциальности, скриншоты
fastlane/metadata/android/ — описания и картинки для каталогов приложений
app/src/main/java/com/pshenov/netwatch/
├── NetVm.kt, Features.kt  — состояние и логика экранов
├── Net.kt                 — пинг, Wi-Fi / сотовая сеть, шлюз, замеры скорости
├── Tools.kt               — сканер сети и портов, traceroute, DNS, NetBIOS, Wake-on-LAN
├── Discovery.kt           — обнаружение устройств по mDNS и SSDP/UPnP
├── WifiScan.kt            — сканирование эфира и выбор канала
├── Mikrotik.kt            — клиент RouterOS API
├── Monitor.kt             — фоновый мониторинг
├── History.kt             — хранилище истории и известных устройств
├── Updater.kt             — обновление из приложения
├── Report.kt, Widget.kt   — отчёты PDF/TXT/CSV, виджет
└── Ui*.kt                 — интерфейс
```
</details>

---

## English

**NetWatch** is a free, open-source network scanner and Wi-Fi analyzer for Android with a cyberpunk HUD interface. No ads, no analytics, no data collection. *Interface language: Russian.*

- **LAN scanner** — finds all devices on the subnet, detects routers, MikroTik, IP cameras, printers, Android TV / Smart TV, PCs, iPhones and NAS; discovers silent devices via mDNS and SSDP/UPnP with their names; highlights new devices.
- **Wi-Fi analyzer** — nearby networks, 2.4 / 5 / 6 GHz channel graph, best channel recommendation.
- **Site survey** — signal, ping and loss per location, export to CSV and PDF.
- **Speed test** — multi-threaded download / upload with live gauges, ping, jitter, history.
- **Diagnostics** — traceroute, DNS benchmark, port scanner, interfaces and routes, live ping, LAN TCP throughput test, Wake-on-LAN.
- **24/7 monitor** — background router and internet checks with outage notifications and downtime statistics.
- **MikroTik** — RouterOS API client: resources, live interface traffic, CAPsMAN access points, Wi-Fi clients, DHCP leases.
- **History, PDF reports, home-screen widget, in-app updates** with SHA-256 verification.

[**Download APK**](https://github.com/L1TTLEHE11/NetWatch/raw/main/apk/NetWatch-2.4.2-universal.apk) — universal, Android 8.0+.

<sub>Keywords: network scanner, IP scanner, LAN scanner, Wi-Fi analyzer, WiFi channel analyzer, ping, traceroute, port scanner, speed test, network monitor, MikroTik, RouterOS, CAPsMAN, Wake-on-LAN, mDNS, UPnP · сетевой сканер, сканер IP, анализатор Wi-Fi, пинг, трассировка, сканер портов, скорость интернета, мониторинг сети, микротик</sub>

## Лицензия

[GNU GPL v3](LICENSE) — свободное ПО: его можно использовать, изучать, менять и распространять; изменённые версии тоже должны оставаться открытыми под GPL-3.0.

**NetWatch** · Created by **L1TTLEHE11**
