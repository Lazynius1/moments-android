#!/usr/bin/env python3
"""Lista los @Composable declarados que no invoca nadie.

El fallo recurrente de este port es escribir la vista y no montarla en ninguna
pantalla, así que conviene pasar este script al cerrar cada carpeta.

Uso:  python3 tools/find_orphans.py [subcarpeta]
      python3 tools/find_orphans.py views/profile
"""
import collections
import os
import re
import sys

SOURCE_ROOT = "app/src/main/java/com/moments/android"


def collect_declarations(root):
    """nombre de composable -> archivo donde se declara."""
    declarations = {}
    for current, _dirs, files in os.walk(root):
        for name in files:
            if not name.endswith(".kt"):
                continue
            path = os.path.join(current, name)
            source = open(path, encoding="utf-8", errors="ignore").read()
            for match in re.finditer(r"@Composable\s*(?:@\w+(?:\([^)]*\))?\s*)*fun\s+(\w+)", source):
                composable = match.group(1)
                if composable[0].isupper() and not composable.startswith("Preview"):
                    declarations.setdefault(composable, path)
    return declarations


def count_usages(root, declarations):
    usages = collections.Counter()
    for current, _dirs, files in os.walk(root):
        for name in files:
            if not name.endswith(".kt"):
                continue
            path = os.path.join(current, name)
            source = open(path, encoding="utf-8", errors="ignore").read()
            for composable, declared_in in declarations.items():
                found = len(re.findall(r"\b" + composable + r"\s*\(", source))
                if path == declared_in:
                    found -= len(re.findall(r"fun\s+" + composable + r"\s*\(", source))
                usages[composable] += max(found, 0)
    return usages


def main():
    scope = sys.argv[1] if len(sys.argv) > 1 else ""
    root = os.path.join(SOURCE_ROOT, scope)
    if not os.path.isdir(root):
        print(f"No existe: {root}")
        return 1

    declarations = collect_declarations(root)
    usages = count_usages(SOURCE_ROOT, declarations)
    orphans = sorted(
        (declarations[name], name) for name, count in usages.items() if count == 0
    )

    print(f"Composables declarados en {scope or 'todo el proyecto'}: {len(declarations)}")
    print(f"Nunca invocados: {len(orphans)}\n")

    by_file = collections.Counter(path for path, _ in orphans)
    for path, count in by_file.most_common():
        names = [name for file_path, name in orphans if file_path == path]
        print(f"{count:3d}  {os.path.relpath(path, SOURCE_ROOT)}")
        print(f"     {', '.join(names)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
