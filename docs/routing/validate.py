"""Read-only routing checks. Run with Python 3.9+ from any working directory."""

from pathlib import Path
import re
import sys
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parents[2]
ROUTING = ROOT / "docs" / "routing"
DOCUMENTS = [ROOT / "AGENTS.md", ROOT / "README.md", *sorted(ROUTING.rglob("*.md"))]
JAVA = {p.stem: p for base in ("src/main/java", "src/test/java")
        for p in (ROOT / base).rglob("*.java")}
IDENTIFIERS = set()
for source_path in JAVA.values():
    IDENTIFIERS.update(re.findall(r"\b[A-Za-z_]\w*\b", source_path.read_text(encoding="utf-8-sig")))
# Current routing names resolve to production classes; no historical absence exceptions.
ABSENT = set()
errors = []
links = symbols = selectors = 0


def fail(document, message):
    errors.append(f"{document.relative_to(ROOT).as_posix()}: {message}")


for document in DOCUMENTS:
    content = document.read_text(encoding="utf-8-sig")
    for label, destination in re.findall(r"!?\[([^\]]+)\]\(([^)\n]+)\)", content):
        destination = destination.strip().strip("<>")
        parsed = urlsplit(destination)
        if parsed.scheme or parsed.netloc:
            continue  # Existing external citations are not fetched by this local check.
        target = (document.parent / unquote(parsed.path)).resolve() if parsed.path else document
        if not target.is_relative_to(ROOT):
            fail(document, f"link leaves repository: {destination}")
            continue
        links += 1
        if not target.exists():
            fail(document, f"missing link: {destination}")
            continue
        if parsed.fragment:
            # Routing links intentionally use files/symbols rather than line fragments.
            if document.is_relative_to(ROUTING):
                fail(document, f"use a file/symbol link without fragile fragment: {destination}")
        if target.suffix == ".java":
            source = target.read_text(encoding="utf-8-sig")
            if re.fullmatch(r"[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)?", label):
                parts = label.split(".")
                if parts[0] in JAVA:  # Descriptive labels such as 'credit' are not symbols.
                    for part in parts:
                        symbols += 1
                        if not re.search(rf"\b{re.escape(part)}\b", source):
                            fail(document, f"symbol {label} absent from {target.relative_to(ROOT)}")
    if document.is_relative_to(ROUTING) or document.name == "AGENTS.md":
        for name in set(re.findall(r"\bKOME[A-Z][A-Za-z0-9_]+\b", content)):
            symbols += 1
            if name in ABSENT:
                if name in JAVA:
                    fail(document, f"absence note is stale: {name} now exists")
            elif name not in JAVA:
                fail(document, f"unknown source/test class: {name}")
        for name in set(re.findall(r"`([A-Za-z_]\w*)`", content)):
            symbols += 1
            if name not in IDENTIFIERS and name not in ABSENT:
                fail(document, f"unknown code identifier: {name}")
        for selector in re.findall(r"--tests\s+['\"]([^'\"]+)['\"]", content):
            selectors += 1
            target = ROOT / "src/test/java" / (selector.replace(".", "/") + ".java")
            if not target.is_file():
                fail(document, f"test selector does not resolve: {selector}")
            else:
                source = target.read_text(encoding="utf-8-sig")
                package = selector.rsplit(".", 1)[0]
                if not re.search(rf"\bpackage\s+{re.escape(package)}\s*;", source):
                    fail(document, f"test package mismatch: {selector}")
                if not re.search(r"@(?:org\.junit\.)?Test\b", source):
                    fail(document, f"selector names a fixture without JUnit tests: {selector}")

index = (ROUTING / "INDEX.md").read_text(encoding="utf-8")
for guide in sorted((ROUTING / "systems").glob("*/README.md")):
    if guide.relative_to(ROUTING).as_posix() not in index:
        fail(guide, "system guide is not discoverable from INDEX.md")
for flow in sorted((ROUTING / "flows").glob("*.md")):
    if flow.relative_to(ROUTING).as_posix() not in index:
        fail(flow, "flow is not discoverable from INDEX.md")
if "docs/routing/INDEX.md" not in (ROOT / "AGENTS.md").read_text(encoding="utf-8"):
    errors.append("AGENTS.md: missing routing entry point")
if "docs/routing/INDEX.md" not in (ROOT / "README.md").read_text(encoding="utf-8"):
    errors.append("README.md: missing routing entry point")

print(f"Checked {len(DOCUMENTS)} documents, {links} local links, "
      f"{symbols} symbol references, {selectors} exact JUnit selectors.")
if errors:
    print("\n".join(errors))
    sys.exit(1)
print("PASS: files, symbol presence, test selectors, and route discoverability.")
print("Limits: no Java execution, gameplay proof, external link checks, or method semantics.")
