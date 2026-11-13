#!/usr/bin/env python3
"""
Uygulamanın yazılarını (strings.xml) tek tablodan, dört dil için üretir.

Kullanım (depo kökünde):  python3 tools/diller.py

Kaynak: tools/diller.json. Her yazının dört hâli şu sırayla yazılır: [Türkçe, Fransızca, İngilizce, Almanca].
Çıktı: app/src/main/res/values/strings.xml (Türkçe, varsayılan) ve values-fr, values-en, values-de.
strings.xml dosyalarını elle düzenleme; değişikliği diller.json'a yapıp bu betiği çalıştır.
Yalnızca tek bir metin verilmişse (ör. uygulama adı) o yazı her dilde aynıdır.
"""
import sys, os, re, json

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
DATA = json.load(open(os.path.join(ROOT, "tools", "diller.json"), encoding="utf-8"))
LANGS = ["tr", "fr", "en", "de"]


def esc(s):
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    if s[:1] in ("@", "?"):
        s = "\\" + s
    return s


def fmt_attr(s):
    # %1$s gibi biçim yoksa ama % varsa: formatted="false"
    if "%" in s and not re.search(r"%\d+\$", s):
        return ' formatted="false"'
    return ""


for i, lang in enumerate(LANGS):
    folder = "values" if lang == "tr" else "values-" + lang
    os.makedirs(os.path.join(RES, folder), exist_ok=True)
    out = ['<?xml version="1.0" encoding="utf-8"?>']
    if lang == "tr":
        out.append("<!-- Türkçe (varsayılan) yazılar. Çevirileri values-fr, values-en, values-de klasörlerinde. -->")
        out.append("<!-- Bu dosya elle değil tools/diller.json + tools/diller.py ile üretilir. -->")
    else:
        out.append("<!-- " + {"fr": "Fransızca", "en": "İngilizce", "de": "Almanca"}[lang] + " çeviri. tools/diller.py ile üretilir, elle düzenleme. -->")
    out.append("<resources>")
    for section in DATA:
        out.append("")
        out.append("    <!-- " + section["section"] + " -->")
        for key, val in section["strings"].items():
            if isinstance(val, dict) and "plural" in val:
                forms = val["plural"][lang]
                out.append('    <plurals name="%s">' % key)
                for q, text in forms.items():
                    out.append('        <item quantity="%s">%s</item>' % (q, esc(text)))
                out.append("    </plurals>")
            elif isinstance(val, dict) and "array" in val:
                items = val["array"][lang]
                out.append('    <string-array name="%s">' % key)
                for text in items:
                    out.append("        <item>%s</item>" % esc(text))
                out.append("    </string-array>")
            else:
                if isinstance(val, str):  # her dilde aynı (ör. uygulama adı)
                    if lang != "tr":
                        continue
                    text = val
                else:
                    text = val[i]
                out.append('    <string name="%s"%s>%s</string>' % (key, fmt_attr(text), esc(text)))
    out.append("</resources>")
    open(os.path.join(RES, folder, "strings.xml"), "w", encoding="utf-8").write("\n".join(out) + "\n")
print("ok")
