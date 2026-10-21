"""Проверяет, что каждый класс события действительно кем-то вызывается.

Событие, объявленное в коде, но ни разу не отправленное через EventManager,
молча не работает: модули на него подписываются, но хук никогда не срабатывает.
На порте это главный источник «функция вроде есть, но ничего не делает».
"""
import re
import sys
from pathlib import Path

ROOT = Path("src/main/java")
EVENT_DIR_NAMES = {"event", "events", "impl", "input", "player", "render"}

event_files = []
for p in ROOT.rglob("*.java"):
    if "event" in str(p.parent).replace("\\", "/").lower() and p.name.startswith(("Event", "GlassHands", "Hand", "After", "Before", "Attack", "BlockBreaking", "ClickSlot", "CursorLock", "DiscordEventHandlers")):
        event_files.append(p)

# Все .java файлы, чтобы искать вызовы
all_files = list(ROOT.rglob("*.java"))

# Исключаем сами определения событий и инфраструктуру
INFRA = {"EventManager", "Event", "EventCancellable", "EventStoppable", "EventType", "EventParser"}

declared = []
for p in sorted(event_files):
    name = p.stem
    if name in INFRA:
        continue
    declared.append((name, p))

unused = []
for name, path in declared:
    pattern = re.compile(r"new\s+" + re.escape(name) + r"\s*\(")
    found = False
    for f in all_files:
        if f == path:
            continue
        try:
            text = f.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        if pattern.search(text):
            found = True
            break
    if not found:
        unused.append(name)

print(f"event classes: {len(declared)}")
if unused:
    print(f"NEVER FIRED ({len(unused)}):")
    for u in unused:
        print(f"  {u}")
else:
    print("ALL EVENTS ARE FIRED SOMEWHERE")
sys.exit(0)
