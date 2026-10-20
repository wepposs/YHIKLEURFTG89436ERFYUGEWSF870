"""Static audit of vesence mixins.

For every injection annotation that anchors on an INVOKE/FIELD target, verify that
the host method of the target class actually contains a reference to that member.
A miss means the injector silently no-ops when `require = 0`.
"""
import os
import re
import subprocess
import sys

SRC = os.path.join("src", "main", "java")
CP = os.path.join("build", "mc26full")

ANN_RE = re.compile(r"@(Inject|WrapOperation|Redirect|ModifyVariable|ModifyConstant|ModifyArg|ModifyArgs|ModifyExpressionValue)\s*\(")


def resolve_type(name, imports):
    """Resolve a source-level type reference (possibly Outer.Inner) to a FQN."""
    if "." in name:
        head, rest = name.split(".", 1)
        base = imports.get(head)
        return (base + "." + rest) if base else name
    return imports.get(name, name)


def strip_comment(line):
    return line


def balanced(text, start):
    """Return text of the parenthesised group starting at text[start] == '('."""
    depth = 0
    i = start
    in_str = False
    in_ch = False
    while i < len(text):
        c = text[i]
        if in_str:
            if c == "\\":
                i += 2
                continue
            if c == '"':
                in_str = False
        elif in_ch:
            if c == "\\":
                i += 2
                continue
            if c == "'":
                in_ch = False
        else:
            if c == '"':
                in_str = True
            elif c == "'":
                in_ch = True
            elif c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
                if depth == 0:
                    return text[start:i + 1]
        i += 1
    return text[start:]


def parse_java_files():
    hooks = []
    for root, _dirs, files in os.walk(SRC):
        if "\\mixin" not in root and "/mixin" not in root:
            continue
        for fn in files:
            if not fn.endswith(".java"):
                continue
            path = os.path.join(root, fn)
            text = open(path, encoding="utf-8", errors="replace").read()
            imports = {}
            for imp in re.findall(r"^import\s+([A-Za-z0-9_.]+);", text, re.M):
                imports[imp.split(".")[-1]] = imp
            mixin_targets = re.findall(r"@Mixin\s*\(\s*(?:value\s*=\s*)?\{?\s*([A-Za-z0-9_.]+)\.class", text)
            if not mixin_targets:
                continue
            mixin_targets = [resolve_type(t, imports) for t in mixin_targets]
            for m in ANN_RE.finditer(text):
                body = balanced(text, m.end() - 1)
                method = re.search(r'method\s*=\s*\{?\s*"([^"(]+)', body)
                if not method:
                    continue
                # collect every @At(...) inside
                for at in re.finditer(r"@At\s*\(", body):
                    atbody = balanced(body, at.end() - 1)
                    tgt = re.search(r'target\s*=\s*"L([A-Za-z0-9_/$]+);([A-Za-z0-9_$<>]+)(\(|[:\"])', atbody)
                    if not tgt:
                        continue
                    hooks.append({
                        "file": fn,
                        "mixin": mixin_targets,
                        "host": method.group(1),
                        "cls": tgt.group(1),
                        "member": tgt.group(2),
                        "kind": "method" if tgt.group(3) == "(" else "field",
                    })
    return hooks


_JAVAP_CACHE = {}


def _inner_variants(cls):
    """`a.b.Outer.Inner` -> also try `a.b.Outer$Inner` (nested classes use $ in bytecode)."""
    yield cls
    parts = cls.split(".")
    for i in range(len(parts) - 1, 1, -1):
        yield ".".join(parts[:i]) + "$" + "$".join(parts[i:])


def javap(cls):
    if cls in _JAVAP_CACHE:
        return _JAVAP_CACHE[cls]
    out = ""
    for cand in _inner_variants(cls):
        try:
            res = subprocess.run(["javap", "-p", "-c", "-cp", CP, cand],
                                 capture_output=True, text=True, errors="replace")
            if res.returncode == 0 and res.stdout.strip():
                out = res.stdout
                break
        except Exception:
            pass
    _JAVAP_CACHE[cls] = out
    return out


def method_bodies(javap_text):
    """Split javap -c output into {methodName: bodyText}."""
    bodies = {}
    cur = None
    buf = []
    for line in javap_text.splitlines():
        mm = re.match(r"^  \S.*?\s([A-Za-z0-9_$<>]+)\(", line)
        if mm and not line.startswith("    "):
            if cur:
                bodies.setdefault(cur, []).append("\n".join(buf))
            cur = mm.group(1)
            buf = []
        elif cur:
            buf.append(line)
    if cur:
        bodies.setdefault(cur, []).append("\n".join(buf))
    return bodies


def check_all_method_targets():
    """Every method= named by any injection/shadow annotation must exist in the @Mixin class."""
    problems = []
    checked = 0
    for root, _dirs, files in os.walk(SRC):
        if "\\mixin" not in root and "/mixin" not in root:
            continue
        for fn in files:
            if not fn.endswith(".java"):
                continue
            path = os.path.join(root, fn)
            text = open(path, encoding="utf-8", errors="replace").read()
            imports = {}
            for imp in re.findall(r"^import\s+([A-Za-z0-9_.]+);", text, re.M):
                imports[imp.split(".")[-1]] = imp
            mixin_targets = re.findall(r"@Mixin\s*\(\s*(?:value\s*=\s*)?\{?\s*([A-Za-z0-9_.]+)\.class", text)
            if not mixin_targets:
                continue
            mixin_targets = [resolve_type(t, imports) for t in mixin_targets]
            names = set()
            for m in re.finditer(r'\bmethod\s*=\s*(\{[^}]*\}|"[^"]*")', text):
                for q in re.findall(r'"([^"]+)"', m.group(1)):
                    nm = q.split("(")[0]
                    if nm and re.fullmatch(r"[A-Za-z0-9_$<>]+", nm):
                        names.add(nm)
            if not names:
                continue
            available = set()
            ok_cls = False
            for mc in mixin_targets:
                txt = javap(mc)
                if not txt.strip():
                    continue
                ok_cls = True
                available |= set(method_bodies(txt).keys())
            if not ok_cls:
                problems.append((fn, "mixin target class not found: " + "|".join(mixin_targets)))
                continue
            for nm in sorted(names):
                checked += 1
                if nm not in available:
                    problems.append((fn, "method target missing: %s in %s" % (nm, "|".join(mixin_targets))))
    print("method= targets checked:", checked)
    return problems


def main():
    hooks = parse_java_files()
    print("invoke/field-anchored hooks:", len(hooks))
    problems = []
    for h in hooks:
        # The host method lives in the @Mixin target class; h["cls"] is only the
        # owner of the member the @At anchors on.
        needle = h["member"]
        found = False
        host_seen = False
        for mixin_cls in h["mixin"]:
            text = javap(mixin_cls)
            if not text.strip():
                continue
            bodies = method_bodies(text)
            host_bodies = []
            for name, blist in bodies.items():
                if name == h["host"]:
                    host_bodies.extend(blist)
            if not host_bodies:
                continue
            host_seen = True
            if any(needle in b for b in host_bodies):
                found = True
                break
        if not host_seen:
            problems.append((h, "host method not found in " + "|".join(h["mixin"])))
            continue
        if not found:
            problems.append((h, "member '%s' not referenced in %s" % (needle, h["host"])))

    problems.extend([(n, w) for n, w in check_all_method_targets()])

    if not problems:
        print("ALL INVOKE/FIELD ANCHORS RESOLVE")
        return 0
    print("\n%d PROBLEM(S):" % len(problems))
    for h, why in problems:
        if isinstance(h, dict):
            print("  %-42s host=%-28s %s.%s :: %s" % (h["file"], h["host"], h["cls"].split("/")[-1], h["member"], why))
        else:
            print("  %-42s %s" % (h, why))
    return 1


if __name__ == "__main__":
    sys.exit(main())
