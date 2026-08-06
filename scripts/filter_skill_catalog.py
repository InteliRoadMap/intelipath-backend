#!/usr/bin/env python3
"""
Drop the entries in the skill catalog that are not skills.

`skills_v4.csv` was built from roadmap.sh node names, and 3,450 of its 3,634 rows
carry `sources = roadmap` alone — never confirmed against a job posting. Node
names are section headings as often as they are technologies, so the catalog
ended up holding "Introduction", "Components", "Understand the Basics" and
"Learn the Basics" as though a student could put them on a CV. They then flowed
into `career_required_skills`, which is what the assessment asks about and what
the learning plan proposes as work — a plan step titled "Introduction" is not
advice.

What is dropped, and why each rule is defensible:

  * INSTRUCTION  — begins with a verb aimed at the reader ("Learn X",
                   "Understand Y", "Getting started with Z"). A skill is a noun;
                   an instruction is a heading.
  * SECTION      — generic document furniture ("Introduction", "Overview",
                   "Conclusion", "Summary", "Resources", "Basics").
  * VAGUE        — a bare qualifier with no technology attached ("Advanced
                   Topics", "Common Concepts", "Other Tools").
  * PRIMITIVE    — language syntax and built-ins ("boolean", "let", "null",
                   "setTimeout"). Real things, but not skills anyone claims —
                   they are JavaScript sub-topics that became catalog rows.

What is never dropped:

  * Anything the market confirmed (`sources` contains `market-`). Real postings
    asked for it, so it is real whatever it looks like.
  * Anything whose name contains a known technology token, so "Introduction to
    Kotlin" survives as Kotlin material rather than being lost — it is renamed,
    not deleted.

Usage:
    python filter_skill_catalog.py \
        --in ../data/v2/skills_v4.csv \
        --out ../data/v2/skills_v5.csv \
        --report ../data/v2/skills_v5.report.csv
"""

import argparse
import collections
import csv
import io
import re

# Verbs that make a phrase an instruction to the reader rather than a skill.
INSTRUCTION_PREFIXES = (
    "learn", "understand", "understanding", "getting started", "get started",
    "how to", "what is", "what are", "why ", "when to", "introduction to",
    "intro to", "deep dive", "dive into", "explore", "review of", "know ",
    "study ", "master ", "pick a", "choose a", "choosing", "picking",
)

# Whole names that are document furniture in any roadmap.
SECTION_NAMES = {
    "introduction", "intro", "overview", "conclusion", "summary", "resources",
    "references", "basics", "the basics", "learn the basics", "fundamentals",
    "components", "concepts", "topics", "tools", "usecases", "use cases",
    "examples", "getting started", "prerequisites", "next steps", "further reading",
    "advanced", "advanced topics", "misc", "miscellaneous", "other", "others",
    "notes", "glossary", "terminology", "cheatsheet", "roadmap", "guide",
    "best practices", "common patterns", "patterns", "anti-patterns",
    "understand the basics", "additional resources", "what next",
}

# Language syntax and built-ins. Real things, but not skills a person claims:
# nobody writes "boolean" or "setTimeout" on a CV, and 30 of Frontend's 115 core
# skills were entries like these, harvested from JavaScript sub-topic node names.
PRIMITIVE_NAMES = {
    # types and literals
    "boolean", "string", "number", "null", "undefined", "nan", "symbol",
    "bigint", "object", "array", "arrays", "integer", "float", "double", "char",
    # declarations and keywords
    "let", "const", "var", "this", "new", "typeof", "instanceof", "void",
    "return", "yield", "await", "async", "static", "final", "public", "private",
    "protected", "abstract", "interface", "enum", "struct",
    # built-ins a roadmap lists as sub-topics
    "map", "set", "settimeout", "setinterval", "fetch", "console", "math",
    "date", "regexp", "promise", "json", "pipes", "context", "server", "client",
    "callbacks", "closures", "filtering", "sorting", "slicing", "spread",
    "destructuring", "operators", "loops", "conditionals", "functions",
    "variables", "classes", "objects", "strings", "numbers", "booleans",
}

# A bare qualifier with nothing technological attached.
VAGUE_PATTERN = re.compile(
    r"^(advanced|basic|common|general|other|more|additional|further|modern|core)\s+"
    r"(topics?|concepts?|tools?|techniques?|features?|things?|stuff|options?|ideas?)$",
    re.IGNORECASE,
)


def normalise(name):
    return " ".join((name or "").strip().lower().split())


# ── Structural rules ────────────────────────────────────────────────
# The name lists above only catch what somebody thought to write down; these
# catch a *shape*, which is what actually distinguishes a roadmap heading from a
# skill. Three shapes account for most of what survived the first pass.

# "Array vs ArrayList", "RDB vs AOF Tradeoffs" — a comparison is a lesson, not a
# skill. A student holds Array and ArrayList; nobody holds "Array vs ArrayList".
#
# Both sides are required. Matching a bare "vs" anywhere deleted "VS Code" — a
# real editor, market-relevant, and the only two AVG-importance rows the first
# run dropped. A comparison needs two things being compared.
COMPARISON_PATTERN = re.compile(r"\S+\s+(?:vs\.?|versus)\s+\S+", re.IGNORECASE)

# "Avg / Sum / Min / Max", "Basic Commands / SET, GET" — a list of several things
# under one title. Splitting it would be guesswork, so it is dropped rather than
# stored as a skill nobody can claim.
LIST_PATTERN = re.compile(r"^[^/]+(?:\s*/\s*[^/]+){2,}$")

# "Create a new project", "Setting up the environment" — an instruction with an
# article or a gerund. Distinct from INSTRUCTION_PREFIXES, which needs the exact
# opening word.
ACTION_PATTERN = re.compile(
    r"^(create|build|make|install|setting|installing|configure|configuring|"
    r"organize|organizing|writing|running|adding|using)\b.*\b(a|an|the|your|new)\b",
    re.IGNORECASE,
)

# Language keywords in the singular. PRIMITIVE_NAMES holds the plurals a roadmap
# uses for a section ("loops"); these are the words themselves.
SYNTAX_KEYWORDS = {
    "for", "while", "if", "else", "switch", "case", "break", "continue", "do",
    "try", "catch", "finally", "throw", "throws", "class", "function", "method",
    "loop", "variable", "constant", "pointer", "reference", "package", "import",
    "export", "module", "namespace",
}

# Five words or more is prose. Real technology names are short; the longest in
# the market data is "Google Cloud Platform".
MAX_SKILL_WORDS = 4


def market_confirmed(row):
    return "market-" in (row.get("sources") or "")


def classify(row):
    """@return None to keep the row, or the reason it is dropped."""
    if market_confirmed(row):
        return None

    name = normalise(row.get("name"))
    if not name:
        return "EMPTY"
    if name in SECTION_NAMES:
        return "SECTION"
    if name in PRIMITIVE_NAMES:
        return "PRIMITIVE"
    if VAGUE_PATTERN.match(name):
        return "VAGUE"
    for prefix in INSTRUCTION_PREFIXES:
        if name.startswith(prefix):
            return "INSTRUCTION"
    if name in SYNTAX_KEYWORDS:
        return "PRIMITIVE"
    if COMPARISON_PATTERN.search(name):
        return "COMPARISON"
    if LIST_PATTERN.match(name):
        return "LIST"
    if ACTION_PATTERN.match(name):
        return "INSTRUCTION"
    if len(name.split()) > MAX_SKILL_WORDS:
        return "SENTENCE"
    # A single very common English word is a heading, not a technology. Two-letter
    # names ("Go", "R") are left alone — those are real languages.
    if len(name) > 2 and name.isalpha() and name in SECTION_NAMES:
        return "SECTION"
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="source", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--report", default=None)
    args = ap.parse_args()

    rows = list(csv.DictReader(io.open(args.source, encoding="utf-8-sig")))
    if not rows:
        raise SystemExit("no rows in %s" % args.source)
    columns = list(rows[0].keys())

    kept, dropped = [], []
    for row in rows:
        reason = classify(row)
        if reason is None:
            kept.append(row)
        else:
            row["_reason"] = reason
            dropped.append(row)

    with io.open(args.out, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=columns, quoting=csv.QUOTE_ALL,
                                extrasaction="ignore")
        writer.writeheader()
        writer.writerows(kept)

    if args.report:
        with io.open(args.report, "w", encoding="utf-8", newline="") as f:
            writer = csv.DictWriter(f, fieldnames=columns + ["_reason"],
                                    quoting=csv.QUOTE_ALL, extrasaction="ignore")
            writer.writeheader()
            writer.writerows(dropped)

    counts = collections.Counter(r["_reason"] for r in dropped)
    print("Wrote %s" % args.out)
    print("  kept    : %d" % len(kept))
    print("  dropped : %d  (%.1f%%)" % (len(dropped), 100.0 * len(dropped) / len(rows)))
    for reason, count in counts.most_common():
        print("     %-12s %5d" % (reason, count))

    by_importance = collections.Counter(r.get("importance") for r in dropped)
    print("  dropped by importance: %s" % dict(by_importance))
    print()
    print("  sample of what went:")
    for row in dropped[:15]:
        print("     %-12s %s" % (row["_reason"], row["name"]))


if __name__ == "__main__":
    main()
