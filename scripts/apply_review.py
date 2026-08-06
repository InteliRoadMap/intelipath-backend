#!/usr/bin/env python3
"""
Apply reviewed parent corrections back onto the imported roadmap CSVs.

The importer derives hierarchy from the geometry of roadmap.sh's canvas, which
is right most of the time and confidently wrong the rest: it files `Maven`
under Concurrency and `JDBC` under File Operations purely because those nodes
sit inside the wrong rectangle. Those need domain knowledge, not arithmetic.

Input is a corrections file with three columns:

    roadmap,node_name,new_group

`node_name` and `new_group` are matched case- and punctuation-insensitively
against the names in that roadmap, so corrections stay readable and survive a
re-import that renumbers ids. A `new_group` of `-` detaches the node from its
parent (used for fragments that should not be children of anything).

The script rewrites `parent_id` and `skill_group` in place, refuses to create
a cycle, and reports anything it could not resolve rather than failing silently.

Usage:
    python apply_review.py --dir ../data/v2/incoming --fixes parent_fixes.csv
    python apply_review.py --dir ../data/v2/incoming --fixes parent_fixes.csv --dry-run
"""

import argparse
import collections
import csv
import io
import os
import re
import unicodedata


def norm(s):
    s = unicodedata.normalize("NFD", s or "")
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]", "", s.lower())


def load_roadmap(path):
    return list(csv.DictReader(io.open(path, encoding="utf-8")))


def creates_cycle(rows_by_id, child_id, new_parent_id):
    """Walk up from the proposed parent; meeting the child means a cycle."""
    seen = set()
    cur = new_parent_id
    while cur and cur not in seen:
        if cur == child_id:
            return True
        seen.add(cur)
        parent = rows_by_id.get(cur, {}).get("parent_id")
        cur = parent or None
    return False


def clear_flags(directory, slug, corrected_ids):
    """
    Drop reviewed nodes from the importer's flag file.

    The flags mean "the heuristic was unsure"; once a human has ruled, keeping
    them would leave merge_incoming.py grading a corrected node as
    NEEDS_PARENT forever.
    """
    path = os.path.join(directory, "%s.review.csv" % slug)
    if not os.path.exists(path) or not corrected_ids:
        return
    rows = list(csv.DictReader(io.open(path, encoding="utf-8")))
    remaining = [r for r in rows if r["node_id"] not in corrected_ids]
    if len(remaining) == len(rows):
        return
    with io.open(path, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["node_id", "name", "parent_guess",
                                          "reason"], quoting=csv.QUOTE_ALL)
        w.writeheader()
        w.writerows(remaining)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", required=True, help="the incoming/ directory")
    ap.add_argument("--fixes", required=True,
                    help="CSV with roadmap,node_name,new_group")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    fixes = collections.defaultdict(list)
    for r in csv.DictReader(io.open(args.fixes, encoding="utf-8-sig")):
        if not (r.get("roadmap") or "").strip():
            continue
        fixes[r["roadmap"].strip()].append((
            r["node_name"].strip(),
            r["new_group"].strip(),
            (r.get("from_group") or "").strip(),
        ))

    applied = skipped = cycles = 0
    unresolved = []

    for slug, items in sorted(fixes.items()):
        path = os.path.join(args.dir, "%s.csv" % slug)
        if not os.path.exists(path):
            unresolved.append("%s: no such roadmap file" % slug)
            continue

        rows = load_roadmap(path)
        by_id = {r["node_id"]: r for r in rows}
        by_name = collections.defaultdict(list)
        for r in rows:
            by_name[norm(r["name"])].append(r)

        touched = False
        corrected = set()
        for node_name, new_group, from_group in items:
            # Only branch nodes are repositioned. A name can belong to both a
            # spine group and a leaf — "Basics of OOP" is a section heading and
            # also a subtopic in the java roadmap — and reparenting the heading
            # would silently reshape the spine rather than fix a leaf.
            targets = [r for r in by_name.get(norm(node_name), [])
                       if r["axis"] == "BRANCH"]

            # A name can also appear under several groups at once, one placement
            # right and another wrong: the frontend roadmap files "Nuxt.js"
            # correctly under Vue.js and again, wrongly, under Accessibility.
            # `from_group` pins the correction to the wrong copy only.
            if from_group:
                targets = [r for r in targets
                           if norm(r["skill_group"]) == norm(from_group)]

            if not targets:
                unresolved.append(
                    "%s: no branch node named '%s'%s"
                    % (slug, node_name,
                       " under '%s'" % from_group if from_group else ""))
                continue

            if new_group == "-":
                for t in targets:
                    t["parent_id"] = ""
                    t["skill_group"] = ""
                    corrected.add(t["node_id"])
                    applied += 1
                    touched = True
                continue

            parents = [r for r in by_name.get(norm(new_group), [])
                       if r["axis"] == "MAIN"]
            if not parents:
                unresolved.append(
                    "%s: group '%s' not found (for '%s')"
                    % (slug, new_group, node_name))
                continue
            parent = parents[0]

            for t in targets:
                if t["node_id"] == parent["node_id"]:
                    skipped += 1
                    continue
                if creates_cycle(by_id, t["node_id"], parent["node_id"]):
                    cycles += 1
                    unresolved.append(
                        "%s: '%s' -> '%s' would create a cycle"
                        % (slug, node_name, new_group))
                    continue
                t["parent_id"] = parent["node_id"]
                t["skill_group"] = parent["name"]
                corrected.add(t["node_id"])
                applied += 1
                touched = True

        if touched and not args.dry_run:
            with io.open(path, "w", encoding="utf-8", newline="") as f:
                w = csv.DictWriter(f, fieldnames=rows[0].keys(),
                                   quoting=csv.QUOTE_ALL)
                w.writeheader()
                w.writerows(rows)
            clear_flags(args.dir, slug, corrected)

    print("%s%d corrections applied" % ("[dry run] " if args.dry_run else "",
                                        applied))
    if skipped:
        print("  %d skipped (node already is the group)" % skipped)
    if cycles:
        print("  %d rejected as cycles" % cycles)
    if unresolved:
        print("  %d unresolved:" % len(unresolved))
        for u in unresolved:
            print("     %s" % u)


if __name__ == "__main__":
    main()
