# -*- coding: utf-8 -*-
import re
from collections import defaultdict
from pathlib import Path

byfile = defaultdict(list)
for line in Path('build/port_errors.txt').read_text(encoding='utf-8', errors='ignore').splitlines():
    m = re.match(r'(C:[^\s]+\.java):(\d+): error: (.*)', line)
    if m:
        byfile[m.group(1)].append(m.group(3))

bydir = defaultdict(int)
files = defaultdict(set)
for f, errs in byfile.items():
    rel = f.split('vesence')[-1].strip('\\/')
    top = rel.replace('/', '\\').split('\\')[0]
    bydir[top] += len(errs)
    files[top].add(rel)

for d, c in sorted(bydir.items(), key=lambda x: -x[1]):
    print(f'{c:6d} errors in {len(files[d]):4d} files  {d}')
