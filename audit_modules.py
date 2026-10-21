"""Ищет модули, объявленные в коде, но не зарегистрированные в менеджере.

Такой модуль существует как класс, компилируется, но в ClickGui его нет и
хуки его не работают — ровно тот же класс «тихой» поломки, что и миксин,
отсутствующий в vesence.mixins.json.
"""
import re
import sys
from pathlib import Path

ROOT = Path("src/main/java")

# Все классы модулей
module_dir = ROOT / "vesence" / "module" / "impl"
modules = []
for p in module_dir.rglob("*.java"):
    text = p.read_text(encoding="utf-8", errors="ignore")
    # модуль = наследник Module с аннотацией @ModuleAnnotation / registerModule
    if "extends Module" in text or "extends Module{" in text:
        modules.append((p.stem, p))

# Где идёт регистрация
manager_files = []
for p in ROOT.rglob("*.java"):
    t = p.read_text(encoding="utf-8", errors="ignore")
    if "module.add(" in t or "registerModule" in t or "addModule" in t:
        manager_files.append(p)

registered_text = "\n".join(
    p.read_text(encoding="utf-8", errors="ignore") for p in manager_files
)

unregistered = []
for name, path in modules:
    # регистрация выглядит как new ИмяМодуля(...) или ИмяМодуля.class
    if re.search(r"\bnew\s+(?:vesence\.module\.impl\.[\w.]+\.)?" + re.escape(name) + r"\s*\(", registered_text):
        continue
    if re.search(r"\b" + re.escape(name) + r"\.class\b", registered_text):
        continue
    unregistered.append(name)

print(f"module classes: {len(modules)}")
print(f"registration files: {len(manager_files)}")
if unregistered:
    print(f"NOT REGISTERED ({len(unregistered)}):")
    for u in sorted(unregistered):
        print(f"  {u}")
else:
    print("ALL MODULES REGISTERED")
sys.exit(0)
