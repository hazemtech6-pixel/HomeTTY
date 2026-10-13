#!/usr/bin/env python3
"""HomeTTY's same-file TOML settings TUI (GPL-3.0-only). Requires Python 3.11+."""
from __future__ import annotations
import argparse
import curses
import json
import math
import os
from pathlib import Path
import re
import shlex
import subprocess
import tempfile
import tomllib

FIELDS = {
    "home.columns": (4, int), "home.clock": (True, bool),
    "home.dock": (True, bool), "home.media": (False, bool),
    "weather.enabled": (False, bool), "weather.latitude": (0.0, float),
    "weather.longitude": (0.0, float), "ai.enabled": (False, bool),
    "security.lock_apps": (False, bool), "security.lock_terminal": (False, bool),
    "security.lock_settings": (False, bool), "security.lock_desktop": (False, bool),
    "security.lock_privileged": (False, bool), "privilege.rish": (False, bool),
    "desktop.environment": ("xfce", str), "desktop.backend": ("x11", str),
}
DESKTOPS = {"xfce", "lxqt", "openbox", "mate", "gnome", "cinnamon", "plasma",
            "i3wm", "dwm", "bspwm", "awesome", "fluxbox", "icewm", "wmaker"}
LIMIT = 262144


def value_at(document, key, fallback=None):
    value = document
    for part in key.split("."):
        if not isinstance(value, dict) or part not in value:
            return fallback
        value = value[part]
    return value


def validate(source):
    if len(source.encode("utf-8")) > LIMIT:
        raise ValueError("Configuration exceeds 256 KiB")
    document = tomllib.loads(source)
    def secrets(node):
        if isinstance(node, dict):
            for key, value in node.items():
                if key.lower().endswith(("api_key", "password", "secret", "token")):
                    raise ValueError("Secrets belong in Android Keystore, not TOML")
                secrets(value)
        elif isinstance(node, list):
            for value in node:
                secrets(value)
    secrets(document)
    for key, (_, kind) in FIELDS.items():
        value = value_at(document, key)
        if value is None:
            continue
        if kind is float:
            if type(value) not in (int, float) or not math.isfinite(value):
                raise ValueError(f"{key} must be a finite number")
        elif type(value) is not kind:
            raise ValueError(f"{key} must be {kind.__name__}")
    columns = value_at(document, "home.columns", 4)
    if not 2 <= columns <= 8:
        raise ValueError("home.columns must be between 2 and 8")
    for key, maximum in (("weather.latitude", 90), ("weather.longitude", 180)):
        if abs(value_at(document, key, 0)) > maximum:
            raise ValueError(f"{key} is outside its valid range")
    if value_at(document, "desktop.environment", "xfce") not in DESKTOPS:
        raise ValueError("Unsupported desktop environment")
    if value_at(document, "desktop.backend", "x11") not in {"x11", "vnc"}:
        raise ValueError("desktop.backend must be x11 or vnc")
    return document


def _single_path(document):
    path = []
    while isinstance(document, dict) and len(document) == 1:
        key, document = next(iter(document.items()))
        path.append(key)
    return path


def replace_value(source, dotted_key, value):
    """Preserve unrelated settings and comments; reject ambiguous inline tables."""
    if dotted_key not in FIELDS:
        raise ValueError("Unknown settings field")
    wanted = dotted_key.split(".")
    literal = str(value).lower() if type(value) is bool else json.dumps(value, ensure_ascii=False, allow_nan=False)
    lines = source.splitlines(keepends=True)
    section = []
    insertion = None
    for index, line in enumerate(lines):
        header = re.match(r"^\s*\[([^\[\]]+)\]\s*(?:#.*)?$", line.rstrip())
        if header:
            try:
                section = _single_path(tomllib.loads(f"[{header.group(1)}]\n_hometty_marker=0"))[:-1]
            except tomllib.TOMLDecodeError:
                section = []
            if section == wanted[:-1]:
                insertion = index + 1
            continue
        assignment = re.match(r"^(\s*[^=]+?\s*=\s*)(.*)$", line.rstrip("\r\n"))
        if not assignment:
            continue
        lhs = assignment.group(1).rsplit("=", 1)[0].strip()
        try:
            key_path = _single_path(tomllib.loads(lhs + "=0"))
        except tomllib.TOMLDecodeError:
            continue
        if section + key_path != wanted:
            continue
        # Preserve a trailing comment on the scalar field. Editable fields have
        # no literal '#' values except strings, whose comment boundary is parsed.
        tail = assignment.group(2)
        comment = ""
        quote = None
        escaped = False
        for offset, character in enumerate(tail):
            if escaped:
                escaped = False
            elif quote == '"' and character == "\\":
                escaped = True
            elif quote and character == quote:
                quote = None
            elif not quote and character in ("'", '"'):
                quote = character
            elif not quote and character == "#":
                comment = " " + tail[offset:]
                break
        lines[index] = assignment.group(1) + literal + comment + "\n"
        result = "".join(lines)
        validate(result)
        return result
    if insertion is not None:
        lines.insert(insertion, wanted[-1] + " = " + literal + "\n")
        result = "".join(lines)
    else:
        result = source.rstrip() + f"\n\n[{'.'.join(wanted[:-1])}]\n{wanted[-1]} = {literal}\n"
    try:
        validate(result)
    except tomllib.TOMLDecodeError as error:
        raise ValueError("This field uses inline or complex table syntax; use the full editor") from error
    return result


def read_config(path):
    with path.open("rb") as stream:
        data = stream.read(LIMIT + 1)
    if len(data) > LIMIT:
        raise ValueError("Configuration exceeds 256 KiB")
    source = data.decode("utf-8")
    validate(source)
    return source


def save_config(path, baseline, source):
    validate(source)
    if read_config(path) != baseline:
        raise ValueError("Configuration changed in another editor; reload before saving")
    descriptor, temporary = tempfile.mkstemp(prefix=".config-", suffix=".toml", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            os.fchmod(stream.fileno(), 0o600)
            stream.write(source)
            stream.flush()
            os.fsync(stream.fileno())
        # Recheck immediately before committing the replacement.
        if read_config(path) != baseline:
            raise ValueError("Configuration changed in another editor; reload before saving")
        os.replace(temporary, path)
        directory = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def tui(screen, path):
    baseline = source = read_config(path)
    fields = list(FIELDS)
    position = 0
    status = "Enter: edit  s: save  r: reload  e: full editor  q: quit"
    screen.keypad(True)
    curses.curs_set(0)
    def write(row, text, selected=False):
        height, width = screen.getmaxyx()
        if row < height - 1:
            screen.addnstr(row, 0, text, max(1, width - 1), curses.A_REVERSE if selected else 0)
    def question(prompt):
        height, width = screen.getmaxyx()
        screen.move(height - 2, 0); screen.clrtoeol()
        screen.addnstr(height - 2, 0, prompt, max(1, width - 1))
        curses.echo(); curses.curs_set(1)
        try:
            return screen.getstr(height - 1, 0, max(1, width - 1)).decode("utf-8")
        finally:
            curses.noecho(); curses.curs_set(0)
    while True:
        document = validate(source)
        screen.erase()
        height, _ = screen.getmaxyx()
        if height < 8:
            write(0, "Enlarge terminal; q quits")
            if screen.getch() in (ord("q"), 27): return
            continue
        write(0, "HomeTTY settings" + (" * unsaved" if source != baseline else ""))
        write(1, str(path))
        visible = height - 5
        first = max(0, min(position - visible + 1, len(fields) - visible))
        for row, key in enumerate(fields[first:first + visible], 3):
            value = value_at(document, key, FIELDS[key][0])
            write(row, f"{key}: {value}", row - 3 + first == position)
        write(height - 2, status)
        screen.refresh()
        key = screen.getch()
        try:
            if key in (curses.KEY_UP, ord("k")):
                position = (position - 1) % len(fields)
            elif key in (curses.KEY_DOWN, ord("j")):
                position = (position + 1) % len(fields)
            elif key in (10, 13, curses.KEY_ENTER):
                field = fields[position]; fallback, kind = FIELDS[field]
                current = value_at(document, field, fallback)
                value = not current if kind is bool else kind(question(field + " = "))
                source = replace_value(source, field, value)
            elif key == ord("s"):
                if question("Save these changes? [y/N] ").lower() == "y":
                    save_config(path, baseline, source); baseline = source; status = "Saved"
            elif key == ord("r"):
                if source == baseline or question("Discard unsaved edits and reload? [y/N] ").lower() == "y":
                    baseline = source = read_config(path); status = "Reloaded"
            elif key == ord("e"):
                if source != baseline and question("Discard unsaved TUI edits? [y/N] ").lower() != "y": continue
                curses.def_prog_mode(); curses.endwin()
                try:
                    subprocess.run(shlex.split(os.environ.get("EDITOR", "vi")) + [str(path)], check=False)
                    baseline = source = read_config(path)
                finally:
                    curses.reset_prog_mode(); screen.refresh()
            elif key in (ord("q"), 27):
                if source == baseline or question("Discard unsaved edits and quit? [y/N] ").lower() == "y": return
        except (ValueError, OSError, tomllib.TOMLDecodeError) as error:
            status = str(error)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--file", type=Path, default=Path.home() / ".config/hometty/config.toml")
    arguments = parser.parse_args()
    try:
        curses.wrapper(tui, arguments.file)
    except (ValueError, OSError, tomllib.TOMLDecodeError) as error:
        parser.exit(2, str(error) + "\n")

if __name__ == "__main__":
    main()
