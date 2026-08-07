-- Roadmap/catalog boundary. Idempotent statements are intentional: this migration
-- must work against both the long-lived local database and a database created by
-- infrastructure/docker/postgres/init/01_init_intelipath.sql.

ALTER TABLE skills ADD COLUMN IF NOT EXISTS catalog_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE skills DROP CONSTRAINT IF EXISTS ck_skills_catalog_status;
ALTER TABLE skills ADD CONSTRAINT ck_skills_catalog_status
    CHECK (catalog_status IN ('ACTIVE', 'QUARANTINED'));

CREATE TABLE IF NOT EXISTS skill_quarantine (
    skill_id UUID PRIMARY KEY REFERENCES skills(skill_id),
    reason VARCHAR(100) NOT NULL,
    snapshot_name TEXT NOT NULL,
    snapshot_category VARCHAR(100),
    quarantined_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- An unknown category is represented honestly, not guessed from spelling.
UPDATE skills SET category = 'UNCATEGORIZED'
WHERE category IS NULL OR btrim(category) = '';

-- Preserve orphan rows and their audit trail. Nothing is deleted.
INSERT INTO skill_quarantine(skill_id, reason, snapshot_name, snapshot_category)
SELECT s.skill_id, 'NO_LIVE_REFERENCE', s.skill_name, s.category
FROM skills s
WHERE NOT EXISTS (SELECT 1 FROM career_required_skills x WHERE x.skill_id = s.skill_id)
  AND NOT EXISTS (SELECT 1 FROM skill_nodes x WHERE x.skill_id = s.skill_id)
  AND NOT EXISTS (SELECT 1 FROM student_skills x WHERE x.skill_id = s.skill_id)
  AND NOT EXISTS (SELECT 1 FROM recruitment_skills x WHERE x.skill_id = s.skill_id)
  AND NOT EXISTS (SELECT 1 FROM skill_trends x WHERE x.skill_id = s.skill_id)
  AND NOT EXISTS (SELECT 1 FROM fpt_subject_skills x WHERE x.skill_id = s.skill_id)
ON CONFLICT (skill_id) DO NOTHING;

UPDATE skills s SET catalog_status = 'QUARANTINED'
WHERE EXISTS (SELECT 1 FROM skill_quarantine q WHERE q.skill_id = s.skill_id);

ALTER TABLE skill_nodes ADD COLUMN IF NOT EXISTS semantic_type VARCHAR(20);
ALTER TABLE skill_nodes DROP CONSTRAINT IF EXISTS ck_skill_nodes_semantic_type;
ALTER TABLE skill_nodes ADD CONSTRAINT ck_skill_nodes_semantic_type
    CHECK (semantic_type IN ('TOPIC', 'SKILL', 'CAPABILITY', 'CHECKPOINT'));

-- Structure decides TOPIC; explicit checkpoint metadata decides CHECKPOINT.
-- A leaf is a career SKILL only when career_required_skills says so. Other leaves
-- remain teachable CAPABILITY nodes but cannot silently define career requirements.
UPDATE skill_nodes n
SET semantic_type = CASE
    WHEN coalesce(n.is_checkpoint, false) THEN 'CHECKPOINT'
    WHEN EXISTS (SELECT 1 FROM skill_nodes c WHERE c.parent_node = n.node_id) THEN 'TOPIC'
    WHEN n.skill_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM career_required_skills crs
        WHERE crs.career_id = n.career_id AND crs.skill_id = n.skill_id
    ) THEN 'SKILL'
    ELSE 'CAPABILITY'
END;

-- Topics, checkpoints and non-catalog capabilities are not catalog skill claims.
UPDATE skill_nodes SET skill_id = NULL
WHERE semantic_type <> 'SKILL';

-- Zero meant "not assessed" and must stay distinguishable from a real threshold.
UPDATE skill_nodes SET required_proficiency = NULL
WHERE required_proficiency = 0 OR semantic_type <> 'SKILL';

-- Required proficiency comes from the sole career-skill source. Prerequisites only
-- raise an otherwise ungraded measurable skill to APPLIED; tree depth is never used.
UPDATE skill_nodes n
SET required_proficiency = CASE
    WHEN upper(crs.importance_level) = 'HIGH' THEN 85
    WHEN upper(crs.importance_level) = 'AVG' THEN 70
    WHEN upper(crs.importance_level) = 'LOW' THEN 55
    WHEN n.prerequisite IS NOT NULL AND jsonb_typeof(n.prerequisite) = 'array'
         AND jsonb_array_length(n.prerequisite) > 0 THEN 70
    ELSE NULL
END
FROM career_required_skills crs
WHERE n.semantic_type = 'SKILL'
  AND crs.career_id = n.career_id
  AND crs.skill_id = n.skill_id;

-- Tier describes dependency/capability readiness, not position in the imported tree.
-- First establish the requirement band, then raise nodes whose prerequisites are
-- themselves dependent. Topic tier is the earliest tier among its direct children.
UPDATE skill_nodes
SET tier = CASE
    WHEN semantic_type = 'SKILL' AND required_proficiency >= 85 THEN 3
    WHEN semantic_type = 'SKILL' AND required_proficiency >= 70 THEN 2
    WHEN semantic_type = 'SKILL' THEN 1
    WHEN semantic_type = 'CAPABILITY' AND prerequisite IS NOT NULL
         AND jsonb_typeof(prerequisite) = 'array' AND jsonb_array_length(prerequisite) > 0 THEN 2
    WHEN semantic_type = 'CAPABILITY' THEN 1
    ELSE NULL
END;

UPDATE skill_nodes n SET tier = 3
WHERE n.semantic_type IN ('SKILL', 'CAPABILITY')
  AND EXISTS (
      SELECT 1
      FROM jsonb_array_elements(coalesce(n.prerequisite, '[]'::jsonb)) p
      JOIN skill_nodes prerequisite_node
        ON prerequisite_node.node_id::text = coalesce(p->>'nodeId', p->>'node_id')
      WHERE prerequisite_node.prerequisite IS NOT NULL
        AND jsonb_typeof(prerequisite_node.prerequisite) = 'array'
        AND jsonb_array_length(prerequisite_node.prerequisite) > 0
  );

UPDATE skill_nodes topic
SET tier = children.first_tier
FROM (
    SELECT parent_node, min(tier) first_tier
    FROM skill_nodes WHERE parent_node IS NOT NULL AND tier IS NOT NULL
    GROUP BY parent_node
) children
WHERE topic.node_id = children.parent_node
  AND topic.semantic_type IN ('TOPIC', 'CHECKPOINT');

UPDATE skill_nodes SET tier = 1 WHERE tier IS NULL;
ALTER TABLE skill_nodes ALTER COLUMN semantic_type SET NOT NULL;

-- Enforce the direction at the database boundary: a roadmap SKILL may reference
-- only a career skill already declared by career_required_skills.
CREATE OR REPLACE FUNCTION enforce_roadmap_skill_catalog_source() RETURNS trigger AS $$
BEGIN
    IF NEW.semantic_type = 'SKILL' THEN
        IF NEW.skill_id IS NULL OR NOT EXISTS (
            SELECT 1 FROM career_required_skills crs
            WHERE crs.career_id = NEW.career_id AND crs.skill_id = NEW.skill_id
        ) THEN
            RAISE EXCEPTION 'Roadmap SKILL must reference career_required_skills for its career';
        END IF;
    ELSIF NEW.skill_id IS NOT NULL THEN
        RAISE EXCEPTION 'Only semantic_type SKILL may carry skill_id';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_roadmap_skill_catalog_source ON skill_nodes;
CREATE TRIGGER trg_roadmap_skill_catalog_source
BEFORE INSERT OR UPDATE OF semantic_type, skill_id, career_id ON skill_nodes
FOR EACH ROW EXECUTE FUNCTION enforce_roadmap_skill_catalog_source();
