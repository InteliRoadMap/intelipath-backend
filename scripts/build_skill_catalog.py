#!/usr/bin/env python3
"""
Build a classified skill catalog from every source available, ranked by authority.

No single public source is both complete and correctly classified for modern
software skills (see docs/skill-catalog-sources.md for the comparison). This
script merges them, each doing the job it is actually good at:

  1. Scraper market data  (skills table)      - ground truth: what ITviec
     postings actually say. Every name here MUST end up in the catalog,
     because it is what career_targets and skill_trends already reference.
  2. Curated roadmap_nodes.csv                 - hand-picked, 100% classified
     already via its own category where present. Always wins on conflict.
  3. Imported roadmap.sh nodes (v3, PUBLISHABLE) - 4,400+ technology names,
     already scoped to this system's 8 careers.
  4. O*NET Technology Skills (US Dept. of Labor, public domain) - 8,768 named
     technologies with UNSPSC commodity categories, mapped onto our 9 categories.
  5. github-linguist/linguist (MIT)            - definitive for what counts as
     a programming language.
  6. devicons/devicon (MIT)                    - tagged framework/library/
     database/testing/tool/platform for ~580 well-known technologies.
  7. Wikidata (CC0, via SPARQL "instance of")  - supplementary language/
     framework/library/database labels for anything the above missed.
  8. StackOverflow tag synonyms (public API)    - alias table (js -> javascript,
     nodejs -> node.js), not names or categories.

Classification authority order (first match wins):
    PROTOCOL_NAMES (hardcoded, unambiguous)  ->  curated category (if set)
    -> linguist -> devicon -> O*NET -> Wikidata -> group-hint -> CONCEPT

CONCEPT is not a failure state. "Encapsulation" and "CAP Theorem" are skills
worth tracking that are not technologies, and forcing them into a technology
category would be the wrong kind of correctness. The script reports the CONCEPT
share so a reviewer can audit it, not so it can be driven to zero.

Usage:
    python build_skill_catalog.py \
        --nodes ../data/v2/roadmap_nodes_v3.csv \
        --existing ../data/v2/roadmap_nodes.csv \
        --market .catalog-cache/market_skills.txt \
        --out ../data/v2/skills_v4.csv \
        --aliases-out ../data/v2/skill_aliases_v4.csv
"""

import argparse
import collections
import csv
import io
import json
import os
import re
import unicodedata

CACHE = os.path.join(os.path.dirname(__file__), ".catalog-cache")

CATEGORIES = ["LANGUAGE", "FRAMEWORK", "LIBRARY", "DATABASE", "PLATFORM",
              "TOOL", "TESTING", "PROTOCOL", "CONCEPT"]

PROTOCOL_NAMES = {
    "http", "https", "http2", "http3", "tcp", "udp", "ip", "dns", "ftp", "sftp",
    "ssh", "smtp", "imap", "pop3", "websocket", "websockets", "grpc", "soap",
    "rest", "restapi", "graphql", "mqtt", "amqp", "oauth", "oauth2", "openid",
    "saml", "jwt", "ssl", "tls", "ssltls", "cors", "csp", "webrtc", "sse",
}

# Hand-picked corrections for well-known technologies that no upstream source
# classified correctly. Each was checked individually: devicon tags "Apache"
# only as "php" (a devicon data quality issue, not ours), Appium/Caddy/bcrypt
# are absent from every source tried (linguist, devicon, O*NET, Wikidata).
# This list is deliberately short — it exists to fix specific, verified misses,
# not to substitute for the authority chain.
MANUAL_OVERRIDES = {
    "apache": "PLATFORM", "nginx": "PLATFORM", "caddy": "PLATFORM",
    "iis": "PLATFORM", "msiis": "PLATFORM", "tomcat": "PLATFORM",
    "appium": "TESTING", "cypress": "TESTING", "playwright": "TESTING",
    "postman": "TOOL", "insomnia": "TOOL", "webpack": "TOOL", "vite": "TOOL",
    "bcrypt": "LIBRARY", "scrypt": "LIBRARY", "argon2": "LIBRARY",
    "md5": "PROTOCOL", "sha": "PROTOCOL", "sha256": "PROTOCOL",
    "elasticsearch": "DATABASE", "cassandra": "DATABASE", "dynamodb": "DATABASE",
    "firebase": "PLATFORM", "supabase": "PLATFORM", "vercel": "PLATFORM",
    "netlify": "PLATFORM", "heroku": "PLATFORM", "cloudflare": "PLATFORM",
    "kafka": "PLATFORM", "rabbitmq": "PLATFORM", "nats": "PLATFORM",
    "jenkins": "TOOL", "githubactions": "TOOL", "gitlabci": "TOOL",
    "prometheus": "TOOL", "grafana": "TOOL", "datadog": "TOOL",
}

DEVICON_TAG_CATEGORY = [
    ("language", "LANGUAGE"), ("framework", "FRAMEWORK"), ("library", "LIBRARY"),
    ("database", "DATABASE"), ("testing", "TESTING"), ("cloud", "PLATFORM"),
    ("platform", "PLATFORM"), ("hosting", "PLATFORM"), ("os", "PLATFORM"),
    ("server", "PLATFORM"), ("editor", "TOOL"), ("ide", "TOOL"), ("tool", "TOOL"),
    ("package", "TOOL"), ("manager", "TOOL"),
]

# O*NET's 135 UNSPSC commodity titles are procurement categories, not a dev
# taxonomy ("Medical software", "Word processing software" have no place here).
# Map only the titles that name a real category; everything else is skipped,
# which is why O*NET contributes far fewer than its raw 8,768 rows.
ONET_CATEGORY = [
    (r"operating system", "PLATFORM"),
    (r"web platform development", "FRAMEWORK"),
    (r"object or component oriented development", "LANGUAGE"),
    (r"program testing", "TESTING"),
    (r"development environment", "TOOL"),
    (r"compiler and decompiler", "TOOL"),
    (r"configuration management", "TOOL"),
    (r"transaction (server|security)", "PLATFORM"),
    (r"data base (management|user interface)", "DATABASE"),
    (r"data base reporting", "TOOL"),
    (r"enterprise application integration", "PLATFORM"),
    (r"enterprise resource planning", "PLATFORM"),
    (r"network (monitoring|operating system|security or firewall)", "PLATFORM"),
    (r"web page creation and editing", "TOOL"),
    (r"content workflow", "PLATFORM"),
    (r"industrial control software", "PLATFORM"),
    (r"backup or archival", "TOOL"),
    (r"metadata management", "TOOL"),
]

GROUP_HINTS = [
    (r"test|testing|qa\b", "TESTING"),
    (r"database|databases|nosql|rdbms|sql\b", "DATABASE"),
    (r"framework|frameworks", "FRAMEWORK"),
    (r"librar|packages", "LIBRARY"),
    (r"language|languages", "LANGUAGE"),
    (r"build tools|package manager|cli|editor|ide|version control", "TOOL"),
    (r"cloud|hosting|deployment|containeriz|orchestrat|registr", "PLATFORM"),
    (r"protocol|api styles|web security|authentication", "PROTOCOL"),
]


def norm(s):
    s = unicodedata.normalize("NFD", s or "")
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9+#]", "", s.lower())


# --------------------------------------------------------------------------- #
# Source loaders — every one reads from CACHE and never touches the network,
# so the build is reproducible from what /build_skill_catalog fetched once.
# --------------------------------------------------------------------------- #

def load_linguist():
    path = os.path.join(CACHE, "languages.yml")
    if not os.path.exists(path):
        return {}
    text = io.open(path, encoding="utf-8").read()
    out, current = {}, None
    for line in text.splitlines():
        m = re.match(r"^([A-Za-z0-9][^:]*):\s*$", line)
        if m:
            current = m.group(1).strip()
            continue
        m = re.match(r"^  type:\s*(\w+)", line)
        if m and current and m.group(1) in ("programming", "markup"):
            out[norm(current)] = current
    return out


def load_devicon():
    path = os.path.join(CACHE, "devicon.json")
    if not os.path.exists(path):
        return {}
    entries = json.load(io.open(path, encoding="utf-8"))
    out = {}
    for e in entries:
        tags = {t.lower() for t in e.get("tags", [])}
        category = next((c for tag, c in DEVICON_TAG_CATEGORY if tag in tags), None)
        if not category:
            continue
        labels = [e.get("name", "")] + list(e.get("altnames") or [])
        for a in (e.get("aliases") or []):
            if isinstance(a, dict):
                labels += [v for v in a.values() if isinstance(v, str)]
        for label in labels:
            if label:
                out.setdefault(norm(label), category)
    return out


def load_onet():
    path = os.path.join(CACHE, "onet_tech.txt")
    if not os.path.exists(path):
        return {}
    lines = io.open(path, encoding="utf-8", errors="replace").read().splitlines()
    if len(lines) < 2:
        return {}
    hdr = lines[0].split("\t")
    i_name, i_cat = hdr.index("Example"), hdr.index("Commodity Title")
    out = {}
    for line in lines[1:]:
        cols = line.split("\t")
        if len(cols) <= max(i_name, i_cat):
            continue
        title = cols[i_cat].lower()
        category = next((c for pat, c in ONET_CATEGORY if re.search(pat, title)), None)
        if category:
            out.setdefault(norm(cols[i_name]), (cols[i_name], category))
    return out


def load_wikidata():
    out = {}
    for fname, cat in (("LANGUAGE.json", "LANGUAGE"), ("FRAMEWORK.json", "FRAMEWORK"),
                       ("LIBRARY.json", "LIBRARY"), ("DATABASE.json", "DATABASE")):
        path = os.path.join(CACHE, "wd", fname)
        if not os.path.exists(path):
            continue
        try:
            d = json.load(io.open(path, encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        for b in d.get("results", {}).get("bindings", []):
            v = b["itemLabel"]["value"]
            if re.match(r"^Q\d+$", v):
                continue
            out.setdefault(norm(v), (v, cat))
    return out


def load_so_synonyms():
    path = os.path.join(CACHE, "so_synonyms.json")
    if not os.path.exists(path):
        return []
    return json.load(io.open(path, encoding="utf-8"))


def load_market(path):
    if not path or not os.path.exists(path):
        return []
    return [l.strip() for l in io.open(path, encoding="utf-8") if l.strip()]


# --------------------------------------------------------------------------- #
# Classification
# --------------------------------------------------------------------------- #

def classify(name, group, curated_category, linguist, devicon, onet, wikidata):
    key = norm(name)
    if key in PROTOCOL_NAMES:
        return "PROTOCOL", "protocol-name"
    if curated_category:
        return curated_category, "curated"
    if key in MANUAL_OVERRIDES:
        return MANUAL_OVERRIDES[key], "manual"
    if key in linguist:
        return "LANGUAGE", "linguist"
    if key in devicon:
        return devicon[key], "devicon"
    if key in onet:
        return onet[key][1], "onet"
    if key in wikidata:
        return wikidata[key][1], "wikidata"
    g = (group or "").lower()
    for pattern, category in GROUP_HINTS:
        if re.search(pattern, g):
            return category, "group-hint"
    return "CONCEPT", "default"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--nodes", required=True, help="roadmap_nodes_v3.csv")
    ap.add_argument("--existing", required=True, help="curated roadmap_nodes.csv "
                     "(read for its own skill_group values, not the v3 pool)")
    ap.add_argument("--market", default=None, help="one skill name per line, "
                     "exported from the live skills table")
    ap.add_argument("--market-verified", default=None, help="subset of --market "
                     "backed by an actual skill_trends row, i.e. seen in a real "
                     "job posting rather than only ever hand-seeded")
    ap.add_argument("--out", required=True)
    ap.add_argument("--aliases-out", required=True)
    args = ap.parse_args()

    linguist = load_linguist()
    devicon = load_devicon()
    onet = load_onet()
    wikidata = load_wikidata()
    synonyms = load_so_synonyms()
    market = load_market(args.market)
    # Roughly half of the live `skills` table has never been seen in a real
    # posting — it is the original hand-seeded skills.csv, sitting in the same
    # table as names SkillExtractionServiceImpl actually pulled from ITviec.
    # Only the verified subset is trustworthy as "the market said this".
    verified = set(norm(n) for n in load_market(args.market_verified))

    print("sources loaded:")
    print("  linguist  %5d languages" % len(linguist))
    print("  devicon   %5d classified" % len(devicon))
    print("  onet      %5d classified" % len(onet))
    print("  wikidata  %5d classified" % len(wikidata))
    print("  so-synonyms %3d pairs" % len(synonyms))
    print("  market    %5d live skill names" % len(market))
    print()

    publishable = {"READY", "GROUP", "CHECKPOINT"}
    careers = ["frontend", "backend", "full-stack", "data-science", "devops",
               "game-developer", "qa", "software-architect"]

    # Curated rows carry their own category where the old skills.csv had one;
    # they are the only source allowed to override the authority chain.
    curated_category = {}
    for r in csv.DictReader(io.open(args.existing, encoding="utf-8")):
        if r.get("skill_group"):
            curated_category.setdefault(norm(r["skill_group"]), None)

    # candidates: normalized_name -> {"name": display, "careers": set, "sources": set}
    candidates = collections.defaultdict(
        lambda: {"name": None, "careers": set(), "sources": set()})

    for r in csv.DictReader(io.open(args.nodes, encoding="utf-8")):
        if r["status"] not in publishable or r["career_id"] not in careers:
            continue
        if r["is_checkpoint"] == "true":
            continue
        key = norm(r["name"])
        if not key:
            continue
        c = candidates[key]
        c["name"] = c["name"] or r["name"]
        c["careers"].add(r["career_id"])
        c["sources"].add("roadmap")
        c["group"] = r.get("skill_group", "")

    # Market names with no career come from postings scraped nationwide, not
    # from a roadmap node, so nothing here can tell which of the 8 careers they
    # belong to. Forcing a guess (the earlier version defaulted to 'backend')
    # would silently corrupt career_targets matching later. They are written to
    # a side file for a human to route instead.
    unscoped = []
    for name in market:
        key = norm(name)
        if not key:
            continue
        c = candidates[key]
        c["name"] = name if not c["name"] else c["name"]
        c["sources"].add("market-verified" if key in verified else "market-legacy")
        if not c["careers"]:
            unscoped.append(name)

    rows, stats, class_source = [], collections.Counter(), collections.Counter()
    for key, c in sorted(candidates.items()):
        if not c["careers"]:
            continue   # unscoped market names are reported, not force-assigned
        category, source = classify(c["name"], c.get("group", ""),
                                    None, linguist, devicon, onet, wikidata)
        stats[category] += 1
        class_source[source] += 1
        # A verified market sighting is the strongest signal available; a
        # legacy-seed name with no trend evidence is worth keeping (it may
        # still be correct) but should not outrank something the scraper
        # actually confirmed. Multi-career presence is the next best signal
        # until career_targets (Phase 3) computes a real frequency.
        if "market-verified" in c["sources"]:
            importance = "HIGH"
        elif len(c["careers"]) > 1 or "market-legacy" in c["sources"]:
            importance = "AVG"
        else:
            importance = "LOW"
        for career in sorted(c["careers"]):
            rows.append({"name": c["name"], "career_id": career,
                         "importance": importance, "category": category,
                         "sources": "|".join(sorted(c["sources"]))})

    rows.sort(key=lambda r: (r["career_id"], r["name"].lower()))
    with io.open(args.out, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["name", "career_id", "importance",
                                          "category", "sources"],
                           quoting=csv.QUOTE_ALL)
        w.writeheader()
        w.writerows(rows)

    # Alias table: StackOverflow synonyms, restricted to tags that actually
    # matched a skill in this catalog — a synonym for an unrelated SO tag
    # ("java" the Indonesian coffee-culture tag community, hypothetically)
    # would be noise here.
    catalog_keys = {norm(r["name"]) for r in rows}
    alias_rows = []
    for s in synonyms:
        to_key, from_key = norm(s["to_tag"]), norm(s["from_tag"])
        if to_key in catalog_keys and from_key != to_key:
            alias_rows.append({"skill_key": to_key, "alias": s["from_tag"],
                               "source": "stackoverflow"})
    with io.open(args.aliases_out, "w", encoding="utf-8-sig", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["skill_key", "alias", "source"],
                           quoting=csv.QUOTE_ALL)
        w.writeheader()
        w.writerows(alias_rows)

    if unscoped:
        unscoped_path = os.path.splitext(args.out)[0] + "_unscoped.csv"
        with io.open(unscoped_path, "w", encoding="utf-8-sig", newline="") as f:
            w = csv.writer(f, quoting=csv.QUOTE_ALL)
            w.writerow(["name", "verified"])
            for name in sorted(set(unscoped)):
                w.writerow([name, "yes" if norm(name) in verified else "no"])

    distinct = len({k for k, c in candidates.items() if c["careers"]})
    market_hit = sum(1 for k in {norm(m) for m in market} if k in catalog_keys)
    print("Wrote %s" % args.out)
    print("  rows (skill x career)     : %d" % len(rows))
    print("  distinct skills           : %d" % distinct)
    print("  market skills represented : %d / %d  (%.0f%%)"
          % (market_hit, len(market), 100.0 * market_hit / len(market) if market else 0))
    if unscoped:
        print("  market names w/o a career : %d  -> %s  (route manually, not seeded)"
              % (len(set(unscoped)), unscoped_path))
    print()
    print("  by category:")
    for c in CATEGORIES:
        if stats[c]:
            print("     %-10s %5d  %3.0f%%" % (c, stats[c], 100.0 * stats[c] / distinct))
    print()
    print("  by classifier:")
    for s, n in class_source.most_common():
        print("     %-14s %5d" % (s, n))
    print()
    print("Wrote %s (%d aliases)" % (args.aliases_out, len(alias_rows)))


if __name__ == "__main__":
    main()
