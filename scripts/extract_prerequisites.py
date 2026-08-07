#!/usr/bin/env python3
"""
Fill the `prerequisite` column from what the roadmap authors already wrote.

`skill_nodes.prerequisite` exists in the schema and is empty for every one of the
4,177 rows. Without it there is nothing for a plan verifier to check against, and
every ordering decision has to be guessed from `node_level`, then `stage`, then
tree depth — each one a proxy for the relation nobody recorded.

Most of that relation is not missing at all, only unread. Each file under
`incoming/` carries `previous_id`, set by whoever authored that roadmap.sh
roadmap: 1,498 of the publishable nodes name an explicit predecessor. That is
evidence, not inference, so it is taken first and marked `SOURCE`.

What this script does NOT produce is the ordering **between** roadmaps — that
"Java comes before Spring Boot", or "Docker before Kubernetes". Those pairs live
between 149 roots across the eight careers, and they are what an LLM pass (and a
mentor review) should supply afterwards. 149 items is small enough to read.

Every entry records where it came from, so a later reader can tell an author's
decision from a model's guess:

    [{"nodeId": "...", "source": "SOURCE|AI|MENTOR", "confidence": 1.0,
      "reason": "..."}]

Usage:
    python extract_prerequisites.py \
        --in ../data/v2/roadmap_nodes_v5.csv \
        --out ../data/v2/roadmap_nodes_v6.csv \
        --roots-report ../data/v2/roadmap_roots.csv
"""

import argparse
import collections
import csv
import io
import json

PUBLISHABLE = {"READY", "CHECKPOINT", "GROUP"}

# Careers the system actually offers. The pool covers more (mobile, ai,
# cyber-security, ...), and those rows are carried through untouched rather than
# dropped — they are simply not part of what needs ordering today.
KNOWN_CAREERS = {
    "backend", "data-science", "devops", "frontend",
    "software-architect", "game-developer", "qa", "full-stack",
}


def load(path):
    return list(csv.DictReader(io.open(path, encoding="utf-8-sig")))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="source", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--roots-report", default=None)
    args = ap.parse_args()

    rows = load(args.source)
    if not rows:
        raise SystemExit("no rows in %s" % args.source)

    columns = list(rows[0].keys())
    if "prerequisite" not in columns:
        columns.append("prerequisite")

    by_id = {r["node_id"]: r for r in rows}
    publishable = {r["node_id"] for r in rows if r["status"] in PUBLISHABLE}

    filled = 0
    dangling = 0
    for row in rows:
        previous = (row.get("previous_id") or "").strip()
        if not previous:
            row["prerequisite"] = ""
            continue
        if previous not in by_id or previous not in publishable:
            # The predecessor was graded out. Recording it would hand the verifier
            # a rule referring to a node no student can ever reach, which would
            # lock the dependant forever.
            row["prerequisite"] = ""
            dangling += 1
            continue

        row["prerequisite"] = json.dumps([{
            "nodeId": previous,
            "source": "SOURCE",
            "confidence": 1.0,
            "reason": "%s is ordered before %s in the source roadmap."
                      % (by_id[previous].get("name"), row.get("name")),
        }], ensure_ascii=False)
        filled += 1

    with io.open(args.out, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=columns, quoting=csv.QUOTE_ALL,
                                extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)

    # The roots: publishable nodes in an offered career with no publishable parent.
    # Nothing in the source says which of these comes first, so this list is
    # precisely the work left for the LLM pass and the mentor review.
    roots = [r for r in rows
             if r["status"] in PUBLISHABLE
             and r["career_id"] in KNOWN_CAREERS
             and ((r.get("parent_id") or "").strip() not in publishable)]

    if args.roots_report:
        with io.open(args.roots_report, "w", encoding="utf-8", newline="") as f:
            writer = csv.DictWriter(
                f, fieldnames=["career_id", "node_id", "name", "stage", "description"],
                quoting=csv.QUOTE_ALL, extrasaction="ignore")
            writer.writeheader()
            writer.writerows(roots)

    print("Wrote %s" % args.out)
    print("  prerequisites from source : %d" % filled)
    print("  predecessors graded out    : %d  (left empty rather than dangling)" % dangling)
    print()
    print("  roots still needing a cross-roadmap order: %d" % len(roots))
    for career, count in sorted(collections.Counter(r["career_id"] for r in roots).items()):
        print("     %-20s %3d" % (career, count))


if __name__ == "__main__":
    main()
