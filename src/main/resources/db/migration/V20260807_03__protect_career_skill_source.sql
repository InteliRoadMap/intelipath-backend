-- Keep career_required_skills authoritative after migration as well as during it.
CREATE OR REPLACE FUNCTION protect_roadmap_career_skill_source() RETURNS trigger AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM skill_nodes n
        WHERE n.career_id = OLD.career_id
          AND n.skill_id = OLD.skill_id
          AND n.semantic_type = 'SKILL'
    ) THEN
        RAISE EXCEPTION 'Cannot remove career_required_skills row while roadmap SKILL references it';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_protect_roadmap_career_skill_source ON career_required_skills;
CREATE TRIGGER trg_protect_roadmap_career_skill_source
BEFORE DELETE OR UPDATE OF career_id, skill_id ON career_required_skills
FOR EACH ROW EXECUTE FUNCTION protect_roadmap_career_skill_source();
