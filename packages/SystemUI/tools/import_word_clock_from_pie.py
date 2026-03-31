#!/usr/bin/env python3
# SPDX-FileCopyrightText: DerpFest AOSP
# SPDX-License-Identifier: Apache-2.0
"""
Import type_clock_* from a Pie-era res-keyguard tree into word_clock_* resources.

Reads values/aosip_arrays.xml (defaults) and each values-*/aosip_arrays.xml.
If values-*/aosip_strings.xml defines type_clock_header plurals (e.g. vi), emits
word_clock_header from the <annotation name="color"> text; other locales rely on
values/derp_strings.xml for the header.

Usage:
  ./import_word_clock_from_pie.py \\
    --pie-root /path/to/res-keyguard \\
    --out-root /path/to/packages/SystemUI/res \\
    --skip de,ru,zh-rTW
"""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def clean_item_text(item: ET.Element) -> str:
    t = "".join(item.itertext()).strip()
    if len(t) >= 2 and t[0] == '"' and t[-1] == '"':
        t = t[1:-1]
    return t.replace("\r\n", "\n").replace("\r", "\n")


def parse_string_arrays(path: Path) -> tuple[list[str], list[str]]:
    root = ET.parse(path).getroot()
    hours: list[str] = []
    minutes: list[str] = []
    for arr in root.findall("string-array"):
        name = arr.get("name")
        items = [clean_item_text(x) for x in arr.findall("item")]
        if name == "type_clock_hours":
            hours = items
        elif name == "type_clock_minutes":
            minutes = items
    return hours, minutes


def extract_header_from_strings(path: Path) -> str | None:
    if not path.exists():
        return None
    root = ET.parse(path).getroot()
    plurals = root.find("plurals[@name='type_clock_header']")
    if plurals is None:
        return None
    for qty in ("one", "other", "few", "many", "two"):
        for item in plurals.findall("item"):
            if item.get("quantity") != qty:
                continue
            for child in item.iter():
                if child.tag == "annotation" and child.get("name") == "color":
                    text = (child.text or "").strip()
                    if text:
                        return text
    return None


def escape_item_for_xml(s: str) -> str:
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return s.replace("\n", "\\n")


def fix_known_bad_hours(locale: str, hours: list[str]) -> list[str]:
    out = list(hours)
    if locale == "pt-rPT" and len(out) >= 2 and out[1] == "Google One":
        out[1] = "Uma"
    return out


def minute_fallback(
    pie_root: Path,
    locale: str,
    minutes: list[str],
    default_minutes: list[str],
) -> list[str]:
    if len(minutes) == 60:
        return minutes
    if locale == "pt-rPT":
        pt_path = pie_root / "values-pt" / "aosip_arrays.xml"
        if pt_path.exists():
            _, m = parse_string_arrays(pt_path)
            if len(m) == 60:
                return m
    if locale.startswith("es-") and locale != "es":
        es_path = pie_root / "values-es" / "aosip_arrays.xml"
        if es_path.exists():
            _, m = parse_string_arrays(es_path)
            if len(m) == 60:
                return m
    if len(minutes) != 60:
        return default_minutes
    return minutes


def word_clock_body_lines(header: str | None, hours: list[str], minutes: list[str]) -> list[str]:
    lines = [
        "",
        "    <!-- Word clock (ClockStyle keyguard_clock_word): typographic strings.",
        "         Imported from Pie res-keyguard type_clock_*; spot-check known issues. -->",
    ]
    if header is not None:
        lines.append(
            '    <string name="word_clock_header">' + escape_item_for_xml(header) + "</string>"
        )
    lines.append('    <string-array name="word_clock_hours_12">')
    for h in hours:
        lines.append("        <item>" + escape_item_for_xml(h) + "</item>")
    lines.append("    </string-array>")
    lines.append('    <string-array name="word_clock_minutes">')
    for m in minutes:
        lines.append("        <item>" + escape_item_for_xml(m) + "</item>")
    lines.append("    </string-array>")
    return lines


def write_new_derp_strings(out_path: Path, header: str | None, hours: list[str], minutes: list[str]) -> None:
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!--",
        "     SPDX-FileCopyrightText: DerpFest AOSP",
        "     SPDX-License-Identifier: Apache-2.0",
        "-->",
        '<resources xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">',
    ]
    lines.extend(word_clock_body_lines(header, hours, minutes))
    lines.append("</resources>")
    lines.append("")
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text("\n".join(lines), encoding="utf-8")


def merge_into_existing(out_path: Path, header: str | None, hours: list[str], minutes: list[str]) -> None:
    text = out_path.read_text(encoding="utf-8")
    if "</resources>" not in text:
        raise ValueError("No </resources> in " + str(out_path))
    frag = "\n".join(word_clock_body_lines(header, hours, minutes)) + "\n"
    merged = text.replace("</resources>", frag + "</resources>", 1)
    out_path.write_text(merged, encoding="utf-8")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pie-root", type=Path, required=True)
    ap.add_argument("--out-root", type=Path, required=True)
    ap.add_argument(
        "--skip",
        default="de,ru,zh-rTW",
        help="Comma-separated values-* suffixes to skip (existing refined locales).",
    )
    args = ap.parse_args()
    pie: Path = args.pie_root.resolve()
    out_root: Path = args.out_root.resolve()
    skip = {x.strip() for x in args.skip.split(",") if x.strip()}

    default_path = pie / "values" / "aosip_arrays.xml"
    if not default_path.is_file():
        print("Missing", default_path, file=sys.stderr)
        return 1
    default_hours, default_minutes = parse_string_arrays(default_path)
    if len(default_hours) != 12 or len(default_minutes) != 60:
        print("Bad default arrays", file=sys.stderr)
        return 1

    count = 0
    for d in sorted(pie.glob("values-*")):
        if not d.is_dir():
            continue
        suffix = d.name[len("values-") :]
        if suffix in skip:
            print("skip", d.name)
            continue
        arrays_path = d / "aosip_arrays.xml"
        if not arrays_path.is_file():
            continue
        hours, minutes = parse_string_arrays(arrays_path)
        if len(hours) != 12:
            print("WARN", d.name, "hours len", len(hours), "→ default")
            hours = default_hours
        hours = fix_known_bad_hours(suffix, hours)
        minutes = minute_fallback(pie, suffix, minutes, default_minutes)
        if len(minutes) != 60:
            print("WARN", d.name, "minutes len", len(minutes), "→ default")
            minutes = default_minutes

        strings_path = d / "aosip_strings.xml"
        header = extract_header_from_strings(strings_path)

        out_path = out_root / d.name / "derp_strings.xml"
        if out_path.exists():
            txt = out_path.read_text(encoding="utf-8")
            if "word_clock_hours_12" in txt:
                print("skip existing word_clock", d.name)
                continue
            merge_into_existing(out_path, header, hours, minutes)
        else:
            write_new_derp_strings(out_path, header, hours, minutes)
        count += 1
        print("wrote", out_path.relative_to(out_root))

    print("done,", count, "files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
