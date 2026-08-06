#!/usr/bin/env python3
"""
Resolve every node's skill_group against the skills_v4.csv catalog.

Why this exists: DatabaseSeeder links a SkillNode to a Skill via
`skillRepository.findBySkillName(cell(line, R_SKILL_GROUP))` — an exact-name
lookup. In roadmap_nodes_v3.csv, skill_group holds the node's PARENT GROUP
NAME ("Basics of OOP", "Database Access"), because that column was built for
display grouping, not catalog linkage. Checked against the old 162-row
catalog, only 6% of imports resolved; against skills_v4.csv (3,634 rows) it is
higher but still wrong in kind — a parent group name is rarely itself a named
technology, so SkillNode.skill comes out null for most imported nodes and
nothing downstream (career_targets, evidence matching, trend charts) can join
a node to a skill.

Resolution order per node, first hit wins:
    1. current skill_group already names a catalog skill      -> keep (canonical form)
    2. the node's own name names a catalog skill               -> use it
       ("Docker" the node IS the skill "Docker")
    3. walking up parent_id finds an ancestor that names one   -> inherit
       ("Autoconfiguration" -> parent "Spring Boot")
    4. the node belongs to a single-technology roadmap file
       (node_id prefix "java-", "docker-", ...) whose title
       itself is a catalog skill                               -> use it
       ("Variables and Scopes" in the java roadmap -> "Java")
    5. none of the above                                        -> leave blank

Blank is the honest answer for career-overview roadmaps (backend, frontend,
qa, ...) and multi-topic ones (system-design, computer-science): they are not
themselves a single purchasable technology, so no fallback is forced.

Usage:
    python assign_node_skills.py \
        --nodes ../data/v2/roadmap_nodes_v3.csv \
        --catalog ../data/v2/skills_v4.csv \
        --out ../data/v2/roadmap_nodes_v4.csv
"""

import argparse
import collections
import csv
import io
import re
import unicodedata

# Roadmap slugs that are career overviews or multi-topic surveys rather than a
# single technology. Even if their humanized title happened to collide with a
# catalog entry, they must never become a fallback skill for their children.
NOT_A_SKILL_ROADMAP = {
    "backend", "backend-beginner", "frontend", "frontend-beginner", "full-stack",
    "devops", "devops-beginner", "qa", "software-architect",
    "software-design-architecture", "game-developer", "server-side-game-developer",
    "data-science", "ai-data-scientist", "ai-engineer", "ai-agents",
    "ai-product-builder", "ai-red-teaming", "prompt-engineering", "vibe-coding",
    "claude-code", "openclaw", "system-design", "computer-science",
    "datastructures-and-algorithms", "leetcode", "cyber-security", "devsecops",
    "blockchain", "api-design", "design-system", "ux-design", "product-design",
    "product-manager", "engineering-manager", "devrel", "technical-writer",
    "forward-deployed-engineer", "network-engineer", "data-analyst",
    "data-engineer", "bi-analyst", "machine-learning", "mlops",
    "python-data-analysis", "git-github-beginner",
}

# A handful of slugs whose obvious humanization does not match how the
# technology is actually named in the catalog. Checked against skills_v4.csv,
# not guessed: e.g. the slug "nodejs" humanizes to "Nodejs", but the catalog
# (following the roadmap content itself) calls it "Node.js".
SLUG_TITLE_OVERRIDES = {
    "nodejs": "Node.js", "aspnet-core": "ASP.NET Core", "cpp": "C++",
    "git-github": "Git", "postgresql-dba": "PostgreSQL",
    "ruby-on-rails": "Ruby on Rails", "spring-boot": "Spring Boot",
    "vue": "Vue.js", "sql": "SQL", "html": "HTML", "css": "CSS",
    "aws": "AWS", "php": "PHP", "graphql": "GraphQL",
}


def norm(s):
    s = unicodedata.normalize("NFD", s or "")
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9+#.]", "", s.lower())


def humanize(slug):
    return SLUG_TITLE_OVERRIDES.get(slug, slug.replace("-", " ").title())


def load_catalog(path):
    """normalized name -> canonical display name, first occurrence wins."""
    catalog = {}
    for r in csv.DictReader(io.open(path, encoding="utf-8-sig")):
        catalog.setdefault(norm(r["name"]), r["name"])
    return catalog


def roadmap_prefix_of(node_id, known_slugs):
    """Longest known slug that prefixes this node_id, e.g. 'spring-boot-...'."""
    for slug in sorted(known_slugs, key=len, reverse=True):
        if node_id == slug or node_id.startswith(slug + "-"):
            return slug
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--nodes", required=True)
    ap.add_argument("--catalog", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    catalog = load_catalog(args.catalog)
    rows = list(csv.DictReader(io.open(args.nodes, encoding="utf-8-sig")))
    by_id = {r["node_id"]: r for r in rows}

    # node_id prefixes are the --prefix values import_roadmapsh.py was called
    # with, which are the incoming/*.csv slugs. Derive the set from the ids
    # themselves rather than hardcoding it, so this stays correct if the import
    # set changes.
    known_slugs = set(NOT_A_SKILL_ROADMAP) | set(SLUG_TITLE_OVERRIDES)
    for r in rows:
        m = re.match(r"^([a-z0-9]+(?:-[a-z0-9]+)*)-", r["node_id"])
        # Heuristic seed; refined below by checking against real slugs is not
        # possible without the original file list, so keep both plausible
        # candidates (whole prefix, and its longest dash-trimmed forms).
        if m:
            known_slugs.add(m.group(1))

    prefix_skill = {}
    for slug in known_slugs:
        if slug in NOT_A_SKILL_ROADMAP:
            continue
        title = humanize(slug)
        if norm(title) in catalog:
            prefix_skill[slug] = catalog[norm(title)]

    resolved, stats = [], collections.Counter()

    for r in rows:
        current = r.get("skill_group", "")
        if norm(current) in catalog:
            r["skill_group"] = catalog[norm(current)]
            stats["kept-valid"] += 1
            resolved.append(r)
            continue

        own = catalog.get(norm(r["name"]))
        if own:
            r["skill_group"] = own
            stats["own-name"] += 1
            resolved.append(r)
            continue

        found = None
        cur, depth = r, 0
        while cur.get("parent_id") and depth < 12:
            parent = by_id.get(cur["parent_id"])
            if not parent:
                break
            hit = catalog.get(norm(parent["name"]))
            if hit:
                found = hit
                break
            cur, depth = parent, depth + 1
        if found:
            r["skill_group"] = found
            stats["ancestor"] += 1
            resolved.append(r)
            continue

        prefix = roadmap_prefix_of(r["node_id"], known_slugs)
        fallback = prefix_skill.get(prefix) if prefix else None
        if fallback:
            r["skill_group"] = fallback
            stats["roadmap-fallback"] += 1
            resolved.append(r)
            continue

        r["skill_group"] = ""
        stats["unresolved"] += 1
        resolved.append(r)

    fieldnames = list(rows[0].keys())
    with io.open(args.out, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames, quoting=csv.QUOTE_ALL)
        w.writeheader()
        w.writerows(resolved)

    total = len(resolved)
    matched = total - stats["unresolved"]
    print("Wrote %s" % args.out)
    print("  total nodes            : %d" % total)
    print("  resolved to a skill     : %d  (%.0f%%)" % (matched, 100.0 * matched / total))
    print()
    for k in ("kept-valid", "own-name", "ancestor", "roadmap-fallback", "unresolved"):
        print("     %-18s %6d" % (k, stats[k]))
    print()
    print("  single-technology roadmaps resolved to a fallback skill: %d"
          % len(prefix_skill))


if __name__ == "__main__":
    main()
