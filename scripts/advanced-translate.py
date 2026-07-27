#!/usr/bin/env python3
import os
import re
import json
import urllib.request
import urllib.parse
import xml.etree.ElementTree as ET

chinese_regex = re.compile(r'[\u4e00-\u9fa5]+')
cache = {}

def get_translation(text):
    if text in cache:
        return cache[text]
    try:
        url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=zh-CN&tl=en&dt=t&q=" + urllib.parse.quote(text)
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req, timeout=5) as r:
            res = json.loads(r.read().decode('utf-8'))
            translated = "".join([part[0] for part in res[0] if part[0]])
            print(f"  Translated: '{text}' -> '{translated}'")
            cache[text] = translated
            return translated
    except Exception as e:
        print(f"  Failed translating '{text}': {e}")
        return text

def translate_xml_string_resource(path):
    try:
        tree = ET.parse(path)
        root = tree.getroot()
        changed = False

        for elem in root.iter():
            if elem.get('name') and chinese_regex.search(elem.get('name')):
                translated = get_translation(elem.get('name'))
                if translated != elem.get('name'):
                    elem.set('name', translated)
                    changed = True

            if elem.tag == 'string' and elem.text and chinese_regex.search(elem.text):
                translated = get_translation(elem.text)
                if translated != elem.text:
                    elem.text = translated
                    changed = True

            if elem.tag == 'item' and elem.text and chinese_regex.search(elem.text):
                translated = get_translation(elem.text)
                if translated != elem.text:
                    elem.text = translated
                    changed = True

        if changed:
            with open(path, 'r', encoding='utf-8') as f:
                original = f.read()
            content = '<?xml version="1.0" encoding="utf-8"?>\n' + re.sub(r'^<\?xml[^?]+\?>\n?', '', original)
            with open(path, 'w', encoding='utf-8') as f:
                f.write(content)
            print(f"  Translated XML: {path}")
        return changed
    except Exception:
        return False

print("🚀 Starting repository auto-translation...")
for root, dirs, files in os.walk("."):
    if any(p in root for p in [".git", ".github", "build", ".gradle"]):
        continue
    for file in files:
        if file.endswith(('.kt', '.json', '.txt', '.java', '.properties')):
            path = os.path.join(root, file)
            try:
                with open(path, 'r', encoding='utf-8') as f:
                    content = f.read()
                matches = sorted(list(set(chinese_regex.findall(content))), key=len, reverse=True)
                if not matches:
                    continue
                print(f"Translating code in: {path}")
                for match in matches:
                    trans = get_translation(match)
                    if trans != match:
                        content = content.replace(match, trans)
                with open(path, 'w', encoding='utf-8') as f:
                    f.write(content)
            except Exception:
                pass
        elif file.endswith('.xml') and 'values' in root and 'strings' in file:
            path = os.path.join(root, file)
            try:
                translate_xml_string_resource(path)
            except Exception:
                pass
print("✅ Auto-translation complete!")
