#!/usr/bin/env python3
"""
Merge the per-roadmap imports into one candidate CSV and grade every node.

Grading exists because importing all 91 roadmaps produced far more nodes than
have been curated. FR2.3 ("at least two curated learning resource links for
every technical node") applies to what students actually see, so the route
builder must read only nodes that pass. Everything else stays in the pool and
is promoted later, driven by which nodes `career_targets` actually asks for.

Status values:

    READY        >=2 links and a summary          -> publishable
    CHECKPOINT   is_checkpoint = true             -> publishable, exempt
    GROUP        spine heading (MAIN, no links)   -> publishable, exempt
    NEEDS_LINK   has a summary, exactly 1 link    -> add one link
    NEEDS_BOTH   has a summary, no links          -> add two links
    FRAGMENT     no summary, no links, name is    -> merge into the parent
                 repeated inside its roadmap         or drop
    MISSING      no summary, no links, unique     -> write content

Two exemptions are deliberate and should be defensible at review:

  * CHECKPOINT nodes are deliverables ("Checkpoint - Simple CRUD Apps"), not
    reading material. Demanding two article links of them is a category error.
  * GROUP nodes are the headings roadmap.sh draws as rectangles rather than
    boxes ("Basics of OOP", "Collections"). They organise the tree; the leaves
    beneath them carry the resources. Reading FR2.3's "technical node" to
    exclude a heading is a judgement call, so state it in the report.

A node is only publishable when its parent is too, otherwise routes would
contain nodes with no path leading to them. That cascade runs to a fixed point.

Usage:
    python merge_incoming.py --dir ../data/v2/incoming \
        --out ../data/v2/roadmap_nodes_v3.csv \
        --compare ../data/v2/roadmap_nodes.csv
"""

import argparse
import collections
import csv
import glob
import io
import os
import re
import unicodedata

COLUMNS = [
    "node_id", "career_id", "skill_group", "name", "node_level", "stage",
    "axis", "node_kind", "is_optional", "is_checkpoint", "selection",
    "choose_count", "weight", "completion_policy", "required_proficiency",
    "evidence_keywords", "parent_id", "previous_id", "description",
    "link1", "link2", "link3", "status",
]

PUBLISHABLE = {"READY", "CHECKPOINT", "GROUP"}


def norm(s):
    s = unicodedata.normalize("NFD", s or "")
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]", "", s.lower())


def link_count(row):
    return sum(1 for k in ("link1", "link2", "link3") if (row.get(k) or "").strip())


def load(directory):
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


def load_flagged(directory):
    flagged = set()
    for path in glob.glob(os.path.join(directory, "*.review.csv")):
        for r in csv.DictReader(io.open(path, encoding="utf-8")):
            flagged.add(r["node_id"])
    return flagged


def classify(rows):
    # A name repeated inside one roadmap with no content of its own is a label
    # fragment, not a topic: redis carries twelve nodes called "Usecases", each
    # meaningful only under its own command.
    repeated = collections.Counter()
    for r in rows:
        if not r["description"].strip() and link_count(r) == 0:
            repeated[(r["_slug"], norm(r["name"]))] += 1

    for r in rows:
        has_desc = bool(r["description"].strip())
        n = link_count(r)

        if r["is_checkpoint"] == "true":
            r["status"] = "CHECKPOINT"
        elif r["axis"] == "MAIN":
            # A spine heading is exempt because of WHAT IT IS, not because of how
            # many links it happens to carry. Keying the exemption on n == 0 meant
            # a heading with one stray link lost it and was demoted to NEEDS_LINK,
            # which the cascade then charged to its entire subtree: 258 headings
            # across the pool, and it is why "Pick a Language" — one link — took
            # Java and everything under it down with it.
            r["status"] = "READY" if (n >= 2 and has_desc) else "GROUP"
        elif n >= 2 and has_desc:
            r["status"] = "READY"
        elif n >= 2:
            r["status"] = "NEEDS_DESC"
        elif has_desc:
            r["status"] = "NEEDS_LINK" if n == 1 else "NEEDS_BOTH"
        elif repeated[(r["_slug"], norm(r["name"]))] > 1:
            r["status"] = "FRAGMENT"
        else:
            r["status"] = "MISSING"
    return rows


# Slugs whose title case is wrong, and which a student would notice.
SLUG_TITLES = {
    "aspnet": "ASP.NET", "aspnet-core": "ASP.NET Core", "cpp": "C++",
    "csharp": "C#", "nodejs": "Node.js", "javascript": "JavaScript",
    "typescript": "TypeScript", "graphql": "GraphQL", "php": "PHP",
    "sql": "SQL", "aws": "AWS", "api-design": "API Design", "ai": "AI",
    "ux-design": "UX Design", "qa": "QA", "devops": "DevOps",
    "ios": "iOS", "mongodb": "MongoDB", "postgresql-dba": "PostgreSQL DBA",
    "mlops": "MLOps", "cyber-security": "Cyber Security", "c": "C",
}


def slug_title(slug):
    if slug in SLUG_TITLES:
        return SLUG_TITLES[slug]
    return " ".join(w.capitalize() for w in slug.split("-"))


# Where a roadmap belongs when its own name is not a node in the career.
#
# A framework roadmap belongs under the language it is written in — Laravel is a
# thing you learn *because* you chose PHP, not a peer of "Internet". Without this
# the Backend roadmap opened with Laravel, Golang and Kotlin as top-level topics
# sitting above the actual first step.
ROADMAP_ANCHOR_ALIASES = {
    "laravel": ["PHP"],
    "django": ["Python"],
    "flask": ["Python"],
    "fastapi": ["Python"],
    "spring-boot": ["Java"],
    "nodejs": ["JavaScript (Node.js)", "JavaScript", "Node.js"],
    "aspnet-core": ["C#"],
    "aspnet": ["C#"],
    "golang": ["Go"],
    "rails": ["Ruby"],
    "react": ["React", "JavaScript"],
    "vue": ["Vue.js", "JavaScript"],
    "angular": ["Angular", "TypeScript"],
}

# The group a language roadmap falls back to when neither its own name nor an
# alias names an existing node: it is still a language choice, so it belongs with
# the other choices rather than at the top of the roadmap.
LANGUAGE_ROADMAPS = {
    "java", "kotlin", "scala", "golang", "rust", "python", "ruby", "php",
    "csharp", "cpp", "c", "typescript", "javascript", "elixir", "dart", "swift",
}


def find_anchor(by_key, career_id, slug, title):
    """The node a source roadmap should hang under, or None to leave it at the top."""
    anchor = by_key.get((career_id, norm(title)))
    if anchor is not None:
        return anchor
    for alias in ROADMAP_ANCHOR_ALIASES.get(slug, []):
        anchor = by_key.get((career_id, norm(alias)))
        if anchor is not None:
            return anchor
    return None


def nest_by_source(rows):
    """
    Give every imported roadmap its own root, so it becomes a sub-tree instead
    of emptying its spine into the career's top level.

    Each file under incoming/ is one roadmap.sh roadmap with its own top-level
    chain. Merged flat, `backend` ended up with 623 roots drawn from 23 source
    roadmaps — laravel 48, kotlin 46, ruby 46, golang 45 — which is not a
    roadmap, it is 23 roadmaps stacked in one column. Nesting turns that into 23
    roots a student can descend into, which is also what makes "go deeper on
    Java" a place that exists rather than a wish.

    Where the career already has a node named after the roadmap, the new root is
    hung underneath it: java.csv lands under the existing `Java` node, so the
    depth appears exactly where a student would look for it. Ids never collide
    because the two sides carry different slug prefixes.
    """
    by_key = {}
    for r in rows:
        if r["status"] in PUBLISHABLE:
            by_key.setdefault((r["career_id"], norm(r["name"])), r)

    groups = collections.OrderedDict()
    for r in rows:
        if r.get("_slug") in (None, "curated"):
            continue
        if r["parent_id"].strip():
            continue
        groups.setdefault((r["career_id"], r["_slug"]), []).append(r)

    # One CHOOSE_ONE group per career ("Pick a Language"), used as the home for a
    # language roadmap the curated set never named.
    choose_groups = {}
    for r in rows:
        if r["status"] in PUBLISHABLE and (r.get("selection") or "").upper() == "CHOOSE_ONE":
            choose_groups.setdefault(r["career_id"], r)

    taken = {r["node_id"] for r in rows}
    synthetic = []
    attached = 0
    for (career_id, slug), roots in groups.items():
        title = slug_title(slug)
        anchor = find_anchor(by_key, career_id, slug, title)
        if anchor is None and slug in LANGUAGE_ROADMAPS:
            anchor = choose_groups.get(career_id)
        # A roadmap whose only root is the anchor itself has nothing to nest.
        if anchor is not None and all(r["node_id"] == anchor["node_id"] for r in roots):
            continue

        if anchor is not None:
            # The anchor IS the heading for this roadmap, so adding one of our own
            # would read "Java > Java > Basics of OOP". Hang the roadmap's roots
            # straight off it instead.
            for r in roots:
                if r["node_id"] != anchor["node_id"]:
                    r["parent_id"] = anchor["node_id"]
            attached += 1
            continue

        root_id = "%s-roadmap" % slug
        suffix = 2
        while root_id in taken:
            root_id = "%s-roadmap-%d" % (slug, suffix)
            suffix += 1
        taken.add(root_id)

        row = {c: "" for c in COLUMNS}
        row.update({
            "node_id": root_id,
            "career_id": career_id,
            "name": title,
            "node_level": "0",
            "axis": "MAIN",
            "node_kind": "CORE",
            "is_optional": "false",
            "is_checkpoint": "false",
            "selection": "ALL",
            "weight": "0",
            "completion_policy": "NEVER_COMPLETE",
            "description": "Deep dive into %s." % title,
            # A heading, so the same exemption the other spine headings get.
            "status": "GROUP",
            "_slug": slug,
        })
        if anchor is not None:
            row["parent_id"] = anchor["node_id"]
            attached += 1
        synthetic.append(row)

        for r in roots:
            if anchor is not None and r["node_id"] == anchor["node_id"]:
                continue
            r["parent_id"] = root_id

    print("Nesting: %d source roadmaps given a root (%d hung under an existing "
          "node of the same name)" % (len(synthetic), attached))
    return rows + synthetic


def apply_cascade(rows, flagged):
    """Demote nodes whose parent is unresolved or itself unpublishable."""
    for r in rows:
        if r["status"] in PUBLISHABLE and r["node_id"] in flagged:
            r["status"] = "NEEDS_PARENT"

    by_id = {r["node_id"]: r for r in rows}
    passes = 0
    changed = True
    while changed:
        changed = False
        passes += 1
        for r in rows:
            parent = r["parent_id"]
            if not parent or r["status"] not in PUBLISHABLE:
                continue
            p = by_id.get(parent)
            if p is None or p["status"] not in PUBLISHABLE:
                r["status"] = "BLOCKED_PARENT"
                changed = True
    return passes


def union_with_existing(imported, existing_path):
    """
    Merge the import on top of the curated CSV, existing rows winning.

    Matching is by (career_id, normalised name) rather than node_id: the two
    sets were generated at different times and their ids do not line up, but a
    skill named the same thing within the same career is the same skill.

    node_id collisions are a separate hazard — both sides prefix ids with the
    roadmap slug, so an imported node can claim an id an existing row already
    holds while describing something else. Those imports are re-suffixed rather
    than dropped.
    """
    existing = list(csv.DictReader(io.open(existing_path, encoding="utf-8")))
    for r in existing:
        r["_slug"] = "curated"
    existing = classify(existing)

    by_key = {(r["career_id"], norm(r["name"])): r for r in existing}
    taken_ids = {r["node_id"] for r in existing}

    kept = []
    # Dropping a superseded import orphans its children, which still name it as
    # their parent. Record where each dropped id went so those references can be
    # repointed at the curated row that replaced it.
    redirect = {}
    for r in imported:
        key = (r["career_id"], norm(r["name"]))
        winner = by_key.get(key)
        if winner is not None:
            redirect[r["node_id"]] = winner["node_id"]
            continue
        if r["node_id"] in taken_ids:
            suffix = 2
            while "%s-i%d" % (r["node_id"], suffix) in taken_ids:
                suffix += 1
            redirect[r["node_id"]] = "%s-i%d" % (r["node_id"], suffix)
            r["node_id"] = redirect[r["node_id"]]
        taken_ids.add(r["node_id"])
        kept.append(r)

    dangling = 0
    for r in kept:
        for col in ("parent_id", "previous_id"):
            ref = r[col]
            if ref and ref in redirect:
                r[col] = redirect[ref]
                dangling += 1

    print("Union: %d curated + %d imported (%d superseded, %d references "
          "repointed)" % (len(existing), len(kept),
                          len(imported) - len(kept), dangling))
    return existing + kept


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--compare", default=None,
                    help="existing roadmap_nodes.csv, to report what it holds "
                         "that the import does not")
    ap.add_argument("--keep-existing", action="store_true",
                    help="union with --compare instead of replacing it: the "
                         "existing rows win on conflict")
    args = ap.parse_args()

    rows = classify(load(args.dir))

    # The existing CSV is hand-curated and measurably better than the import
    # (58 of its 62 unique nodes carry >=2 links, all 62 carry a summary), so a
    # straight replacement would throw away real work. Union instead, letting
    # the existing row win wherever both describe the same skill.
    if args.keep_existing and args.compare:
        rows = union_with_existing(rows, args.compare)

    # Before the cascade: the synthetic roots must already be in the tree, or
    # the cascade would judge parentage against a shape that is about to change.
    rows = nest_by_source(rows)

    flagged = load_flagged(args.dir)
    passes = apply_cascade(rows, flagged)

    with io.open(args.out, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=COLUMNS, quoting=csv.QUOTE_ALL,
                           extrasaction="ignore")
        w.writeheader()
        w.writerows(rows)

    counts = collections.Counter(r["status"] for r in rows)
    pub = sum(v for k, v in counts.items() if k in PUBLISHABLE)

    print("Wrote %s" % args.out)
    print("  total nodes : %d" % len(rows))
    print("  publishable : %d  (%.0f%%)   cascade settled in %d passes"
          % (pub, 100.0 * pub / len(rows), passes))
    print()
    for k, v in counts.most_common():
        mark = "publish" if k in PUBLISHABLE else ""
        print("   %-15s %6d  %s" % (k, v, mark))

    if args.compare and os.path.exists(args.compare):
        old = list(csv.DictReader(io.open(args.compare, encoding="utf-8")))
        new_names = {(r["career_id"], norm(r["name"])) for r in rows}
        lost = [r for r in old
                if (r["career_id"], norm(r["name"])) not in new_names]
        print()
        print("Against %s" % os.path.basename(args.compare))
        print("  existing nodes            : %d" % len(old))
        print("  absent from the import    : %d" % len(lost))
        if lost:
            print("  (these would be lost by replacing outright)")
            for r in lost[:15]:
                print("     %-18s %s" % (r["career_id"], r["name"][:50]))


if __name__ == "__main__":
    main()
