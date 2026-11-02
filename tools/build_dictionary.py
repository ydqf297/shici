#!/usr/bin/env python3
"""Build the offline dictionary database bundled in ShiCi.

The source is ECDICT (MIT License):
https://github.com/skywind3000/ECDICT

Usage:
    python tools/build_dictionary.py
    python tools/build_dictionary.py --csv /path/to/ecdict.csv
    python tools/build_dictionary.py --out app/src/main/assets/dictionary.db

If --csv is omitted and the file does not exist locally, the script downloads
the ECDICT CSV from GitHub. Only single-token Latin headwords are included
because the reader looks up words, not phrases. Text is cleaned up (literal
\\n separators become line breaks, [网络] machine translations are dropped).
"""

from __future__ import annotations

import argparse
import csv
import os
import re
import sqlite3
import sys
import time
import urllib.request
from pathlib import Path

ECDICT_URL = "https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv"
LATIN_WORD = re.compile(
    r"^[A-Za-z\u00C0-\u024F\u1E00-\u1EFF]"
    r"[A-Za-z\u00C0-\u024F\u1E00-\u1EFF'\-]*$"
)

SCHEMA = """
CREATE TABLE entries (
  word TEXT PRIMARY KEY COLLATE NOCASE,
  phonetic TEXT NOT NULL DEFAULT '',
  definition TEXT NOT NULL DEFAULT '',
  translation TEXT NOT NULL DEFAULT '',
  tag TEXT NOT NULL DEFAULT '',
  collins INTEGER NOT NULL DEFAULT 0,
  oxford INTEGER NOT NULL DEFAULT 0,
  bnc INTEGER NOT NULL DEFAULT 0,
  frq INTEGER NOT NULL DEFAULT 0,
  exchange TEXT NOT NULL DEFAULT ''
) WITHOUT ROWID;
"""


def clean_text(value: str | None) -> str:
    if not value:
        return ""
    value = (value.replace("\\r\\n", "\n")
             .replace("\\n", "\n")
             .replace("\r\n", "\n")
             .replace("\r", "\n"))
    had_multiple_lines = len(value.split("\n")) > 1
    lines = []
    for raw_line in value.split("\n"):
        line = re.sub(r"[ \t]+", " ", raw_line).strip()
        if not line:
            continue
        # ECDICT's [网络] block is noisy machine translation. Drop it when
        # there are real dictionary senses available.
        if had_multiple_lines and line.startswith("[网络]"):
            continue
        lines.append(line)
    return "\n".join(lines)


def as_int(value: str | None) -> int:
    try:
        return int(value or 0)
    except (TypeError, ValueError):
        return 0


def download_csv(destination: Path) -> None:
    print(f"Downloading ECDICT CSV to {destination} ...")
    destination.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(ECDICT_URL, headers={"User-Agent": "ShiCi-build"})
    with urllib.request.urlopen(request, timeout=120) as response:
        with destination.open("wb") as output:
            while True:
                chunk = response.read(1024 * 256)
                if not chunk:
                    break
                output.write(chunk)
    print(f"Downloaded {destination.stat().st_size / (1024 * 1024):.1f} MB")


def build(csv_path: Path, output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = output_path.with_suffix(output_path.suffix + ".tmp")
    for path in (temp_path,):
        if path.exists():
            path.unlink()

    connection = sqlite3.connect(temp_path)
    connection.executescript(
        "PRAGMA page_size=4096;"
        "PRAGMA journal_mode=OFF;"
        "PRAGMA synchronous=OFF;"
        "PRAGMA cache_size=-200000;"
        + SCHEMA
    )

    started = time.time()
    source_rows = 0
    kept_rows = 0
    batch: list[tuple] = []
    with csv_path.open("r", encoding="utf-8", errors="replace", newline="") as source:
        reader = csv.DictReader(source)
        for row in reader:
            source_rows += 1
            word = (row.get("word") or "").strip()
            if not word or len(word) > 64 or not LATIN_WORD.match(word):
                continue
            batch.append((
                word,
                clean_text(row.get("phonetic")),
                clean_text(row.get("definition")),
                clean_text(row.get("translation")),
                (row.get("tag") or "").strip(),
                as_int(row.get("collins")),
                as_int(row.get("oxford")),
                as_int(row.get("bnc")),
                as_int(row.get("frq")),
                (row.get("exchange") or "").strip(),
            ))
            kept_rows += 1
            if len(batch) >= 20_000:
                connection.executemany(
                    "INSERT OR IGNORE INTO entries VALUES (?,?,?,?,?,?,?,?,?,?)", batch)
                batch.clear()
    if batch:
        connection.executemany(
            "INSERT OR IGNORE INTO entries VALUES (?,?,?,?,?,?,?,?,?,?)", batch)

    connection.commit()
    rows = connection.execute("SELECT COUNT(*) FROM entries").fetchone()[0]
    integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
    if integrity != "ok":
        raise RuntimeError(f"Dictionary integrity check failed: {integrity}")
    connection.execute("VACUUM")
    connection.close()

    if output_path.exists():
        output_path.unlink()
    temp_path.replace(output_path)
    print(f"Source rows: {source_rows:,}")
    print(f"Single-word entries: {rows:,} (candidates {kept_rows:,})")
    print(f"Database: {output_path} "
          f"({output_path.stat().st_size / (1024 * 1024):.1f} MB)")
    print(f"Built in {time.time() - started:.1f}s")


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    workspace = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--csv",
        type=Path,
        default=workspace / "build" / "ecdict.csv",
        help="Input ECDICT CSV (downloaded when missing).")
    parser.add_argument(
        "--out",
        type=Path,
        default=workspace / "app" / "src" / "main" / "assets" / "dictionary.db",
        help="Output SQLite file.")
    args = parser.parse_args()

    csv_path = args.csv.resolve()
    if not csv_path.exists():
        download_csv(csv_path)
    build(csv_path, args.out.resolve())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
