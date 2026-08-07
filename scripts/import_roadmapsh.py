#!/usr/bin/env python3
"""
Import roadmap data from roadmap.sh into InteliPath's CSV format.

The data lives in two places and has to be joined:

  1. Structure : GET https://roadmap.sh/api/v1-official-roadmap/{slug}
                 -> { nodes: [{id, type, position{x,y}, data{label}}], edges: [...] }
                 Only node types 'topic' and 'subtopic' carry knowledge; the
                 rest (section/horizontal/vertical/title/label/button/paragraph)
                 are drawing primitives.
  2. Content   : repo nilbuild/developer-roadmap, directory
                 roadmaps/{slug}/content/{title}@{nodeId}.md
                 Joined to (1) through the nodeId after the '@' in the filename.

LICENSING: roadmap.sh is not open source. Its license permits personal use only
and requires prior written consent from the author (Kamran Ahmed) for anything
else. Run this script only once that consent exists, and keep it on file for the
project report appendix.

Usage:
    # Step 1 - sparse clone for the content (once)
    git clone --filter=blob:none --sparse --depth 1 \
        https://github.com/nilbuild/developer-roadmap.git rmsh
    cd rmsh && git sparse-checkout set roadmaps && cd ..

    # Step 2 - generate the CSV
    python import_roadmapsh.py --slug java --career backend \
        --repo ./rmsh --out java.csv

Outputs:
    <out>.csv         nodes in the 22 columns of data/v2/roadmap_nodes.csv
    <out>.review.csv  nodes whose parent assignment needs a human decision
"""

import argparse
import collections
import csv
import io
import json
import math
import os
import re
import unicodedata
import urllib.request

API = "https://roadmap.sh/api/v1-official-roadmap/{slug}"

# Columns of data/v2/roadmap_nodes.csv. Order is kept for readability; the
# seeder reads by name.
COLUMNS = [
    "node_id", "career_id", "skill_group", "name", "node_level", "stage",
    "axis", "node_kind", "is_optional", "is_checkpoint", "selection",
    "choose_count", "weight", "completion_policy", "required_proficiency",
    "evidence_keywords", "parent_id", "previous_id", "description",
    "link1", "link2", "link3",
]

STAGES = ["FOUNDATION", "CORE", "PRACTICAL", "ADVANCED", "JOB_READY"]

# Only three links fit in the CSV, so prefer primary sources over feeds and ads.
LINK_PRIORITY = {
    "official": 0, "book": 1, "article": 2, "course": 3,
    "video": 4, "opensource": 5, "website": 6, "podcast": 7, "feed": 8,
}

LINK_RE = re.compile(r"^\s*-\s*\[@(\w+)@([^\]]+)\]\(([^)]+)\)", re.M)


# --------------------------------------------------------------------------- #
# Helpers
# --------------------------------------------------------------------------- #

def slugify(text):
    """'Spring Data JPA' -> 'spring-data-jpa'. Keeps node_id human readable."""
    s = unicodedata.normalize("NFD", text or "")
    s = "".join(c for c in s if not unicodedata.combining(c))
    s = re.sub(r"[^a-zA-Z0-9]+", "-", s).strip("-").lower()
    return s or "node"


def fetch_graph(slug, cache_dir):
    """Fetch a roadmap's structure, cached on disk so re-runs stay offline."""
    os.makedirs(cache_dir, exist_ok=True)
    path = os.path.join(cache_dir, "%s.json" % slug)
    if os.path.exists(path):
        return json.load(io.open(path, encoding="utf-8"))

    req = urllib.request.Request(
        API.format(slug=slug),
        headers={"User-Agent": "InteliPath-import/1.0"},
    )
    with urllib.request.urlopen(req, timeout=45) as r:
        data = json.loads(r.read().decode("utf-8"))
    json.dump(data, io.open(path, "w", encoding="utf-8"), ensure_ascii=False)
    return data


def parse_content_dir(repo, slug):
    """Read roadmaps/{slug}/content/*.md -> { nodeId: {title, desc, links[]} }."""
    cdir = os.path.join(repo, "roadmaps", slug, "content")
    if not os.path.isdir(cdir):
        raise SystemExit(
            "Not found: %s\n"
            "Did you run 'git sparse-checkout set roadmaps' in the clone?" % cdir
        )

    out = {}
    for fn in os.listdir(cdir):
        if not fn.endswith(".md"):
            continue
        node_id = fn[:-3].rsplit("@", 1)[-1]
        txt = io.open(os.path.join(cdir, fn), encoding="utf-8").read()

        m = re.match(r"#\s*(.+)", txt)
        title = m.group(1).strip() if m else None
        body = txt[m.end():] if m else txt

        # The description is everything before the resource list heading.
        desc = body.split("Visit the following resources")[0]
        desc = re.sub(r"\s+", " ", desc).strip()

        links = [
            {"type": t, "label": lb.strip(), "url": u.strip()}
            for t, lb, u in LINK_RE.findall(txt)
        ]
        links.sort(key=lambda l: LINK_PRIORITY.get(l["type"], 99))

        out[node_id] = {"title": title, "desc": desc, "links": links}
    return out


# --------------------------------------------------------------------------- #
# Deriving structure from geometry
# --------------------------------------------------------------------------- #

def dims(n):
    """Node size — roadmap.sh puts it either at the top level or under 'style'."""
    st = n.get("style") or {}
    w = n.get("width") or st.get("width") or 0
    h = n.get("height") or st.get("height") or 0
    try:
        return float(w), float(h)
    except (TypeError, ValueError):
        return 0.0, 0.0


def build_sections(nodes):
    """
    Derive knowledge groups from 'section' nodes.

    roadmap.sh groups nodes spatially rather than relationally: a 'section' is a
    rectangle, and the 'label' node sitting inside it names the group. In the
    java roadmap, for example, the rectangle at (702,403,249x440) holds the label
    "Basics of OOP", and every subtopic inside those bounds belongs to it.

    This beats measuring distance to the nearest topic, because the real groups
    (OOP, Collections, Testing) are sections, not topics.
    """
    sections = []
    labels = [n for n in nodes if n.get("type") == "label"]

    for s in nodes:
        if s.get("type") != "section":
            continue
        sx, sy = s["position"]["x"], s["position"]["y"]
        sw, sh = dims(s)
        if sw <= 0 or sh <= 0:
            continue

        name = None
        for lb in labels:
            lx, ly = lb["position"]["x"], lb["position"]["y"]
            if sx <= lx <= sx + sw and sy <= ly <= sy + sh:
                text = ((lb.get("data") or {}).get("label") or "").strip()
                if len(text) > 1:          # skip decorative one-character labels
                    name = text
                    break
        if name:
            sections.append({"id": s["id"], "name": name,
                             "x": sx, "y": sy, "w": sw, "h": sh,
                             "area": sw * sh})

    # Smallest first: with nested rectangles the innermost one is the real group.
    sections.sort(key=lambda s: s["area"])
    return sections


def assign_parents(topics, subtopics, sections):
    """
    Pick a parent for every subtopic, in order of preference:

      1. The section rectangle it falls inside  (reliable)
      2. Otherwise the nearest topic            (heuristic)

    Returns { subtopicId: (parentId, ambiguous) }. 'ambiguous' is set only on
    branch 2, when the second-nearest topic is within 30% of the nearest — those
    need a human to confirm.
    """
    result = {}
    for st in subtopics:
        sx, sy = st["position"]["x"], st["position"]["y"]

        inside = next(
            (s for s in sections
             if s["x"] <= sx <= s["x"] + s["w"]
             and s["y"] <= sy <= s["y"] + s["h"]),
            None,
        )
        if inside:
            result[st["id"]] = (inside["id"], False)
            continue

        dists = sorted(
            (math.hypot(sx - t["position"]["x"], sy - t["position"]["y"]),
             t["id"])
            for t in topics
        )
        if not dists:
            result[st["id"]] = (None, False)
            continue
        best_d, best_id = dists[0]
        ambiguous = len(dists) > 1 and dists[1][0] < best_d * 1.30
        result[st["id"]] = (best_id, ambiguous)
    return result


def stage_of(index, total):
    """Split the vertical spine into five evenly sized stages."""
    if total <= 0:
        return STAGES[0]
    band = min(int(index * len(STAGES) / total), len(STAGES) - 1)
    return STAGES[band]


def evidence_keywords(name):
    """Tokens used to detect this skill in code or a diff. Drops filler words."""
    stop = {"and", "the", "of", "a", "an", "to", "in", "for", "with", "or"}
    toks = [t for t in re.split(r"[^a-zA-Z0-9+#]+", name.lower())
            if t and t not in stop and len(t) > 1]
    return "|".join(dict.fromkeys(toks))


# --------------------------------------------------------------------------- #
# Conversion
# --------------------------------------------------------------------------- #

def build_rows(graph, content, career, prefix):
    nodes = graph.get("nodes") or []
    topics = [n for n in nodes if n.get("type") == "topic"]
    subtopics = [n for n in nodes if n.get("type") == "subtopic"]
    sections = build_sections(nodes)

    # The spine runs top to bottom, left to right within the same row.
    topics.sort(key=lambda n: (n["position"]["y"], n["position"]["x"]))
    subtopics.sort(key=lambda n: (n["position"]["y"], n["position"]["x"]))

    parent_of = assign_parents(topics, subtopics, sections)
    node_id_of = {}
    label_of = {}

    def label(n):
        return ((n.get("data") or {}).get("label")
                or (content.get(n["id"]) or {}).get("title")
                or "").strip()

    # node_id has to be unique: a roadmap can repeat the same label in different
    # sections (e.g. "JavaScript" appears twice in backend), and node_id is the
    # key the seeder uses to wire parent_id/previous_id — a collision breaks the
    # tree. Second and later occurrences get a -2, -3, ... suffix.
    used = collections.Counter()

    def make_id(lb):
        base_id = "%s-%s" % (prefix, slugify(lb))
        used[base_id] += 1
        return base_id if used[base_id] == 1 else "%s-%d" % (base_id, used[base_id])

    for n in topics + subtopics:
        lb = label(n)
        label_of[n["id"]] = lb
        node_id_of[n["id"]] = make_id(lb)

    # Sections are real knowledge nodes too ("Basics of OOP", "Collections");
    # roadmap.sh just draws them as frames instead of boxes. They have no content
    # file, so their description stays empty.
    for s in sections:
        label_of[s["id"]] = s["name"]
        node_id_of[s["id"]] = make_id(s["name"])

    spine = [{"id": t["id"], "y": t["position"]["y"], "x": t["position"]["x"]}
             for t in topics]
    spine += [{"id": s["id"], "y": s["y"], "x": s["x"]} for s in sections]
    spine.sort(key=lambda n: (n["y"], n["x"]))

    rows, review = [], []

    def emit(n, axis, level, stage, parent_rid, previous_rid, group):
        c = content.get(n["id"]) or {}
        links = (c.get("links") or [])[:3]

        # roadmap.sh marks project milestones in the node name itself:
        # "Checkpoint - CLI Apps", "Checkpoint - Complete App". Those are not
        # topics to study but deliverables to build, so they map onto the
        # is_checkpoint column. In practice only the full-stack roadmap uses them.
        #
        # node_kind stays CORE: the database constrains it to
        # CORE | ALTERNATIVE | OPTIONAL, which describes a node's role among its
        # siblings, not what kind of work it is. is_checkpoint already carries
        # that meaning.
        name = label_of[n["id"]]
        is_cp = name.strip().lower().startswith("checkpoint")

        rows.append({
            "node_id": node_id_of[n["id"]],
            "career_id": career,
            "skill_group": group or "",
            "name": name,
            "node_level": level if level is not None else "",
            "stage": stage,
            "axis": axis,
            "node_kind": "CORE",
            "is_optional": "false",
            "is_checkpoint": "true" if is_cp else "false",
            "selection": "ALL" if axis == "MAIN" else "",
            "choose_count": "",
            "weight": "1",
            "completion_policy": "",
            "required_proficiency": "2",
            "evidence_keywords": evidence_keywords(name),
            "parent_id": parent_rid or "",
            "previous_id": previous_rid or "",
            "description": c.get("desc", ""),
            "link1": links[0]["url"] if len(links) > 0 else "",
            "link2": links[1]["url"] if len(links) > 1 else "",
            "link3": links[2]["url"] if len(links) > 2 else "",
        })

    # 1) Spine: topics and sections chained in layout order.
    by_id = {n["id"]: n for n in topics}
    for s in sections:
        by_id[s["id"]] = {"id": s["id"], "position": {"x": s["x"], "y": s["y"]}}

    stage_by_parent = {}
    prev = None
    for i, sp in enumerate(spine):
        st = stage_of(i, len(spine))
        stage_by_parent[sp["id"]] = st
        emit(by_id[sp["id"]], "MAIN", i + 1, st,
             None, prev, label_of[sp["id"]])
        prev = node_id_of[sp["id"]]

    # 2) Branches: each subtopic hangs off its section, or its nearest topic.
    for s in subtopics:
        pid, ambiguous = parent_of.get(s["id"], (None, False))
        emit(
            s, "BRANCH", None,
            stage_by_parent.get(pid, STAGES[0]),
            node_id_of.get(pid), None,
            label_of.get(pid, ""),
        )
        if ambiguous or pid is None:
            review.append({
                "node_id": node_id_of[s["id"]],
                "name": label_of[s["id"]],
                "parent_guess": label_of.get(pid, "(undetermined)"),
                "reason": "no parent could be determined" if pid is None
                          else "another topic is almost as close - please check",
            })

    return rows, review


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--slug", required=True,
                    help="roadmap.sh slug, e.g. java, spring-boot, sql")
    ap.add_argument("--career", required=True,
                    help="career_id in InteliPath, e.g. backend")
    ap.add_argument("--repo", required=True,
                    help="path to the sparse clone of developer-roadmap")
    ap.add_argument("--out", required=True, help="output CSV path")
    ap.add_argument("--prefix", default=None,
                    help="node_id prefix (defaults to the slug)")
    ap.add_argument("--cache", default=".roadmapsh-cache")
    args = ap.parse_args()

    prefix = args.prefix or args.slug

    graph = fetch_graph(args.slug, args.cache)
    content = parse_content_dir(args.repo, args.slug)
    rows, review = build_rows(graph, content, args.career, prefix)

    with io.open(args.out, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=COLUMNS, quoting=csv.QUOTE_ALL)
        w.writeheader()
        w.writerows(rows)

    rpath = os.path.splitext(args.out)[0] + ".review.csv"
    with io.open(rpath, "w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(
            f, fieldnames=["node_id", "name", "parent_guess", "reason"],
            quoting=csv.QUOTE_ALL)
        w.writeheader()
        w.writerows(review)

    n_main = sum(1 for r in rows if r["axis"] == "MAIN")
    no_desc = sum(1 for r in rows if not r["description"])
    lt2 = sum(1 for r in rows if not r["link2"])

    print("Wrote %s" % args.out)
    print("  nodes            : %d  (MAIN %d, BRANCH %d)"
          % (len(rows), n_main, len(rows) - n_main))
    print("  missing summary  : %d" % no_desc)
    print("  fewer than 2 links: %d   <- FR2.3 violation, needs manual curation"
          % lt2)
    print("  needs review     : %d   -> %s" % (len(review), rpath))


if __name__ == "__main__":
    main()
