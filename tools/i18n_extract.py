#!/usr/bin/env python3
"""
Вынос русских строк из Kotlin-кода в ресурсы Android.

  python3 tools/i18n_extract.py collect            → tools/i18n/ru.json (уникальные строки в формате Android)
  python3 tools/i18n_extract.py apply              → values/strings.xml (en), values-ru/strings.xml (ru), правка .kt

Строки с шаблонами Kotlin ("Шлюз $host онлайн", "${x.y} мс") превращаются в форматные
("Шлюз %1$s онлайн") и вызов tr(R.string.key, host). Вложенные строки внутри ${…}
обрабатываются рекурсивно. Комментарии не трогаются.
Перевод берётся из tools/i18n/en.json: {"русский текст": "english text"}.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/l1ttlehe11/netwatch"
RES = ROOT / "app/src/main/res"
I18N = ROOT / "tools/i18n"
CYR = re.compile(r"[А-Яа-яЁё]")
JAVA_KEYWORDS = set("abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while true false null".split())
SKIP_FILES = {"Lang.kt"}


class Lit:
    """Строковый литерал: части — ('t', текст) или ('e', выражение)."""
    def __init__(self, start, end, parts):
        self.start, self.end, self.parts = start, end, parts


def parse_string(code, i):
    """code[i] == '"' (не тройная). Возвращает (Lit, индекс после закрывающей кавычки)."""
    assert code[i] == '"'
    j = i + 1; parts = []; buf = []
    while True:
        c = code[j]
        if c == "\\":
            buf.append(code[j:j + 2]); j += 2; continue
        if c == '"':
            if buf: parts.append(("t", "".join(buf)))
            return Lit(i, j + 1, parts), j + 1
        if c == "$" and j + 1 < len(code) and code[j + 1] == "{":
            if buf: parts.append(("t", "".join(buf))); buf = []
            k = j + 2; depth = 1
            while depth:
                ch = code[k]
                if ch == '"' and code[k:k + 3] != '"""':
                    _, k = parse_string(code, k); continue
                if ch == "'":
                    m = re.match(r"'(\\.|[^\\'])'", code[k:])
                    if m: k += m.end(); continue
                if ch == "{": depth += 1
                elif ch == "}": depth -= 1
                k += 1
            parts.append(("e", code[j + 2:k - 1])); j = k; continue
        if c == "$" and j + 1 < len(code) and (code[j + 1].isalpha() or code[j + 1] == "_"):
            m = re.match(r"[A-Za-z_][A-Za-z0-9_]*", code[j + 1:])
            if buf: parts.append(("t", "".join(buf))); buf = []
            parts.append(("e", m.group(0))); j += 1 + m.end(); continue
        if c == "\n":
            raise ValueError("незакрытая строка")
        buf.append(c); j += 1


def scan(code):
    """Все литералы верхнего уровня (вне комментариев), по порядку."""
    out = []; i = 0; n = len(code)
    while i < n:
        if code.startswith("//", i):
            i = code.find("\n", i); i = n if i < 0 else i; continue
        if code.startswith("/*", i):
            i = code.find("*/", i + 2); i = n if i < 0 else i + 2; continue
        if code.startswith('"""', i):
            i = code.find('"""', i + 3) + 3; continue
        if code[i] == "'":
            m = re.match(r"'(\\.|[^\\'])'", code[i:])
            if m: i += m.end(); continue
        if code[i] == '"':
            lit, i = parse_string(code, i)
            ls = code.rfind("\n", 0, lit.start) + 1; le = code.find("\n", lit.end); le = len(code) if le < 0 else le
            if "i18n-ignore" not in code[ls:le]: out.append(lit)   # пометка: строку не переводить
            continue
        i += 1
    return out


def kt_unescape(t):
    return re.sub(r"\\(.)", lambda m: {"n": "\n", "t": "\t", '"': '"', "\\": "\\", "$": "$", "'": "'", "r": "\r"}.get(m.group(1), m.group(0)), t)


def lit_key_text(lit):
    """Текст для ресурса (без экранирования XML) и список выражений-аргументов."""
    args = []; out = []
    for kind, v in lit.parts:
        if kind == "t":
            out.append(kt_unescape(v))
        else:
            args.append(v); out.append(f"\x00{len(args)}\x00")
    text = "".join(out)
    if args:
        text = text.replace("%", "%%")
        text = re.sub(r"\x00(\d+)\x00", lambda m: f"%{m.group(1)}$s", text)
    return text, args


def has_cyr(lit):
    return any(k == "t" and CYR.search(v) for k, v in lit.parts)


def transform(code, table, found):
    """Заменить русские литералы на tr(...). table: текст → ключ (None при сборе)."""
    lits = scan(code)
    res = []; pos = 0
    for lit in lits:
        res.append(code[pos:lit.start]); pos = lit.end
        # сначала — вложенные строки в выражениях
        parts = [(k, transform(v, table, found) if k == "e" else v) for k, v in lit.parts]
        lit2 = Lit(lit.start, lit.end, parts)
        if not has_cyr(lit2):
            res.append('"' + "".join(v if k == "t" else ("${" + v + "}") for k, v in parts) + '"'); continue
        text, args = lit_key_text(lit2)
        found.add(text)
        if table is None:
            res.append(code[lit.start:lit.end]); continue
        key = table[text]
        res.append(f"tr(R.string.{key}" + "".join(f", {a.strip()}" for a in args) + ")")
    res.append(code[pos:])
    return "".join(res)


def xml_value(text, formatted):
    v = text.replace("\\", "\\\\").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    v = v.replace('"', '\\"').replace("'", "\\'").replace("\n", "\\n").replace("\t", "\\t")
    if v.startswith(("@", "?")): v = "\\" + v
    return '"' + v + '"'


def key_for(en, used):
    base = re.sub(r"%\d\$s", " ", en.lower())
    base = re.sub(r"[^a-z0-9]+", "_", base).strip("_")
    base = "_".join(base.split("_")[:6]) or "s"
    if base[0].isdigit(): base = "s_" + base
    if base in JAVA_KEYWORDS: base += "_label"
    k = base; n = 2
    while k in used: k = f"{base}_{n}"; n += 1
    used.add(k); return k


def files():
    return [f for f in sorted(SRC.glob("*.kt")) if f.name not in SKIP_FILES]


def collect():
    found = set()
    for f in files(): transform(f.read_text(encoding="utf-8"), None, found)
    I18N.mkdir(parents=True, exist_ok=True)
    (I18N / "ru.json").write_text(json.dumps(sorted(found), ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{len(found)} уникальных строк → tools/i18n/ru.json")


def write_xml(path, entries, extra):
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for k, v in extra: lines.append(f'    <string name="{k}">{v}</string>')
    for k, text in entries:
        formatted = bool(re.search(r"%\d\$s", text))
        attr = "" if formatted or "%" not in text else ' formatted="false"'
        lines.append(f'    <string name="{k}"{attr}>{xml_value(text, formatted)}</string>')
    lines.append("</resources>"); path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def apply():
    ru = json.loads((I18N / "ru.json").read_text(encoding="utf-8"))
    en = json.loads((I18N / "en.json").read_text(encoding="utf-8"))
    missing = [t for t in ru if t not in en]
    if missing:
        print("Нет перевода для", len(missing), "строк:"); [print("  ", repr(m)) for m in missing[:40]]; sys.exit(1)
    for t in ru:
        a = sorted(re.findall(r"%\d\$s", t)); b = sorted(re.findall(r"%\d\$s", en[t]))
        if a != b: print("Аргументы не совпадают:", repr(t), "→", repr(en[t])); sys.exit(1)
    used = set(); table = {}
    for t in ru: table[t] = key_for(en[t], used)
    for f in files():
        src = f.read_text(encoding="utf-8")
        new = transform(src, table, set())
        if new != src: f.write_text(new, encoding="utf-8")
    extra = json.loads((I18N / "extra.json").read_text(encoding="utf-8")) if (I18N / "extra.json").exists() else {"en": {}, "ru": {}}
    write_xml(RES / "values/strings.xml", [(table[t], en[t]) for t in ru], [(k, xml_value(v, False)) for k, v in extra["en"].items()])
    write_xml(RES / "values-ru/strings.xml", [(table[t], t) for t in ru], [(k, xml_value(v, False)) for k, v in extra["ru"].items()])
    (I18N / "keys.json").write_text(json.dumps(table, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"✓ {len(ru)} строк: values/strings.xml (en), values-ru/strings.xml (ru), код обновлён")


if __name__ == "__main__":
    {"collect": collect, "apply": apply}[sys.argv[1]]()
