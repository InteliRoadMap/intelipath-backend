#!/usr/bin/env python3
"""
Consolidate the roadmap.sh import into a single review sheet.

Why this exists: import_roadmapsh.py writes one file per roadmap, and only
flags the nodes its own heuristic considered ambiguous. But the dangerous
mistakes are the ones the heuristic was confident about and still got wrong —
"Maven" ends up under the "Concurrency" group simply because it falls inside
that rectangle. Those only surface when you look at a whole group at once.

So this sheet lists every branch node grouped by its parent, not just the
flagged ones. A reviewer scans group by group and, when a node is clearly in
the wrong place, writes the correct group name into `parent_fix`. Leaving it
blank keeps the current assignment.

Usage:
    python make_review_sheet.py --dir ../data/v2/incoming
    # edit the parent_fix column in _REVIEW_ALL.csv
    # then run apply_review.py
"""

import argparse
import collections
import csv
import glob
import io
import os

OUT_NAME = "_REVIEW_ALL.csv"
COLUMNS = ["roadmap", "group", "node_id", "name", "flag", "parent_fix"]


def load_nodes(directory):
    rows = []
    for path in sorted(glob.glob(os.path.join(directory, "*.csv"))):
        base = os.path.basename(path)
        if base.startswith("_") or base.endswith(".review.csv"):
            continue
        slug = os.path.splitext(base)[0]
        for r in csv.DictReader(io.open(path, encoding="utf-8")):
            r["_slug"] = slug
            rows.append(r)
    return rows


def load_flags(directory):
    """node_ids the importer already marked as ambiguous."""
    flagged = set()
    for path in glob.glob(os.path.join(directory, "*.review.csv")):
        for r in csv.DictReader(io.open(path, encoding="utf-8")):
            flagged.add(r["node_id"])
    return flagged


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", required=True, help="the incoming/ directory")
    args = ap.parse_args()

    rows = load_nodes(args.dir)
    flagged = load_flags(args.dir)

    branches = [r for r in rows if r["axis"] == "BRANCH"]
    mains = [r for r in rows if r["axis"] == "MAIN"]

    # Keep groups in the same order they appear along the spine, so the sheet
    # reads like the roadmap rather than like an alphabetical dump.
    order = {}
    for i, m in enumerate(mains):
        order[(m["_slug"], m["name"])] = i

    groups = collections.defaultdict(list)
    for b in branches:
        groups[(b["_slug"], b["skill_group"])].append(b)

    out = []
    for key in sorted(groups, key=lambda k: (k[0], order.get(k, 9999))):
        slug, grp = key
        for b in sorted(groups[key], key=lambda r: r["name"]):
            out.append({
                "roadmap": slug,
                "group": grp or "(no group)",
                "node_id": b["node_id"],
                "name": b["name"],
                "flag": "REVIEW" if b["node_id"] in flagged else "",
                "parent_fix": "",
            })

    path = os.path.join(args.dir, OUT_NAME)
    # utf-8-sig so Excel opens it without mangling non-ASCII names.
    with io.open(path, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=COLUMNS, quoting=csv.QUOTE_ALL)
        w.writeheader()
        w.writerows(out)

    n_flag = sum(1 for r in out if r["flag"])
    print("Wrote %s" % path)
    print("  branch nodes : %d" % len(out))
    print("  groups       : %d" % len(groups))
    print("  flagged      : %d" % n_flag)
    print()
    print("Largest groups (review these first):")
    for key, v in sorted(groups.items(), key=lambda x: -len(x[1]))[:12]:
        print("   %-20s %-30s %d nodes" % (key[0], key[1][:30], len(v)))


if __name__ == "__main__":
    main()
