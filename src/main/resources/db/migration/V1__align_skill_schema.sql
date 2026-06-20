ALTER TABLE skills
    DROP COLUMN IF EXISTS name;

ALTER TABLE career_roles
    DROP COLUMN IF EXISTS role_name;

WITH ranked_student_skills AS (
    SELECT student_skill_id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id, skill_id
               ORDER BY student_skill_id
           ) AS duplicate_rank
    FROM student_skills
)
DELETE FROM student_skills
WHERE student_skill_id IN (
    SELECT student_skill_id
    FROM ranked_student_skills
    WHERE duplicate_rank > 1
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_student_skills_student_skill'
    ) THEN
        ALTER TABLE student_skills
            ADD CONSTRAINT uk_student_skills_student_skill
            UNIQUE (user_id, skill_id);
    END IF;
END
$$;
