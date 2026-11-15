#!/usr/bin/env python3
"""Offline regression checks for educational assets (no Android SDK needed)."""
import json
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
# Arapça okunuşlar ve sure adları her dilde bilinçli olarak aynı kalır.
SHARED_RECITATIONS = {
    "Bismillâhirrahmânirrahîm", "Allâhu Ekber", "Sübhâneke", "Fâtiha", "Elhamdülillah",
    "Ettehiyyâtü", "Kevser", "İhlâs", "Nâs", "Rabbenâ âtinâ", "Âmin", "Esselâmü aleyküm",
    "Semi'allâhü limen hamideh", "Sübhâne rabbiye'l-a'lâ", "Sübhâne rabbiye'l-azîm",
}


class ContentLocalizationTest(unittest.TestCase):
    def test_question_banks(self):
        original = json.loads((ASSETS / "sorular.json").read_text())["questions"]
        self.assertEqual(120, len(original))
        for lang in ("fr", "en", "de"):
            bank = json.loads((ASSETS / f"sorular_{lang}.json").read_text())["questions"]
            self.assertEqual([q["id"] for q in original], [q["id"] for q in bank])
            for source, translated in zip(original, bank):
                with self.subTest(lang=lang, question=source["id"]):
                    self.assertEqual(source["level"], translated["level"])
                    self.assertEqual(source.get("source"), translated.get("source"))
                    self.assertEqual(4, len(set(translated["options"])))
                    for field in ("q", "info"):
                        self.assertTrue(translated[field].strip())
                        self.assertNotEqual(source[field], translated[field])
                    self.assertTrue(all(o.strip() for o in translated["options"]))
                    for index, option in enumerate(source["options"]):
                        if re.fullmatch(r"[0-9]+", option):
                            self.assertEqual(option, translated["options"][index], "Sayısal şıkların sırası değişmemeli")

    def test_learning_resources_cover_every_step_and_recitation(self):
        data = json.loads((ROOT / "tools/diller.json").read_text())
        entries = {key: value for section in data for key, value in section["strings"].items() if key.startswith("learn_")}
        game = (ROOT / "app/src/main/java/com/aydinmtl/namazvakitleri/PrayerGame.kt").read_text()
        steps = len(re.findall(r"^        PrayerStep\(", game, re.M))
        recitations = len(re.findall(r"^    val \w+ = Recitation\(", game, re.M))
        self.assertEqual(36, steps)
        self.assertEqual(17, recitations)
        for key, value in entries.items():
            with self.subTest(resource=key):
                if isinstance(value, dict):
                    arrays = value["array"]
                    expected = steps if key.startswith("learn_step_") else recitations if key.startswith("learn_recitation_") else len(arrays["tr"])
                    self.assertEqual({"tr", "fr", "en", "de"}, set(arrays))
                    for language, texts in arrays.items():
                        self.assertEqual(expected, len(texts), f"{key}: {language}")
                        self.assertTrue(all(text.strip() for text in texts))
                        if language != "tr":
                            for base, translated in zip(arrays["tr"], texts):
                                if key in {"learn_step_correct", "learn_step_wrong_one", "learn_step_wrong_two"} and base in SHARED_RECITATIONS:
                                    self.assertEqual(base, translated, f"Arapça okunuş korunmalı: {key}: {language}")
                                else:
                                    self.assertNotEqual(base, translated, f"{key}: {language}")
                else:
                    self.assertEqual(4, len(value))
                    placeholders = sorted(re.findall(r"%\d+\$[sdf]", value[0]))
                    for translated in value:
                        self.assertTrue(translated.strip())
                        self.assertEqual(placeholders, sorted(re.findall(r"%\d+\$[sdf]", translated)))

    def test_generated_learning_resources_match_source(self):
        entries = {key: value for section in json.loads((ROOT / "tools/diller.json").read_text()) for key, value in section["strings"].items() if key.startswith("learn_")}
        for index, language in enumerate(("tr", "fr", "en", "de")):
            folder = "values" if language == "tr" else f"values-{language}"
            root = ET.parse(ROOT / "app/src/main/res" / folder / "strings.xml").getroot()
            generated = {node.attrib["name"]: node for node in root}
            for key, value in entries.items():
                with self.subTest(language=language, resource=key):
                    self.assertIn(key, generated)
                    expected = value["array"][language] if isinstance(value, dict) else [value[index]]
                    node = generated[key]
                    actual = [item.text or "" for item in node] if node.tag == "string-array" else [node.text or ""]
                    # Android XML ayrıca ters eğik çizgiyle tırnakları ve satır sonlarını kaçırır.
                    escape = lambda text: text.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
                    self.assertEqual([escape(text) for text in expected], actual)

    def test_educational_layouts_do_not_embed_turkish_ui(self):
        namespace = "{http://schemas.android.com/apk/res/android}"
        for name in ("activity_quiz.xml", "activity_prayer_game.xml"):
            layout = ET.parse(ROOT / "app/src/main/res/layout" / name).getroot()
            for node in layout.iter():
                for attribute in ("text", "hint", "contentDescription"):
                    value = node.get(namespace + attribute, "")
                    if value and not value.startswith("@"):
                        self.assertIn(value, {"Ahmet", "Zeynep", "🏆", "🕌", "‹", "›"}, f"{name}: {attribute}={value}")


if __name__ == "__main__":
    unittest.main()
