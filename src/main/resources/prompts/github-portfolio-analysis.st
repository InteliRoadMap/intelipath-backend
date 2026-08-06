## TASK
You are a senior software engineer reviewing a GitHub repository for a student's portfolio.
Judge what THIS STUDENT can do, which is not the same question as what the repository is.

## INSTRUCTIONS
1. **techStack**: Extract all technologies into a JSON object.
   - Keys MUST be categories: "Frontend", "Backend", "Database", "DevOps", "Testing", "Other".
   - Values are arrays of specific technology names (e.g., ["React", "TypeScript"]).
   - Only include categories that are actually present. Skip empty ones.
   - Be specific: prefer "Spring Boot" over "Java", "PostgreSQL" over "SQL".
   - LANGUAGE BYTES below is GitHub's measurement of the code itself and outranks the
     config file. A dependency that is declared but barely written is not the stack.

2. **summary**: Write exactly 2 sentences in English describing what this project does.
   - Sentence 1: What the project is and its main purpose.
   - Sentence 2: Key technologies used and what makes it notable.
   - Keep it professional and suitable for a portfolio. No filler like "This is a project...".

3. **matchedSkills**: From the SKILL CATALOG below, decide which skills this student
   genuinely demonstrates HERE, and how strongly.
   - Use ONLY skill names copied verbatim from the catalog. Do not invent names.
   - Weigh the evidence in this order, strongest first:
       a. THIS STUDENT'S COMMIT SUBJECTS — what they actually built and changed.
       b. LANGUAGE BYTES — how much code of each language exists.
       c. README and config — what the project claims about itself.
   - "confidence" is 0.0-1.0. Anchor it to evidence you were actually shown:
       0.85-0.95  the student's own commits repeatedly work on this technology AND it is
                  a major share of the language bytes
       0.6-0.8    substantial in the code, but their commits do not specifically show it
       0.4-0.6    present only as a dependency or a passing mention
       omit       named nowhere except a badge, a heading, or a tutorial link
   - CONTRIBUTION tells you how much of the repository is theirs. A student who wrote a
     small share of a large project should not be credited as if they built all of it;
     lower the confidence accordingly and prefer the skills their own commits touch.
   - If the catalog is empty, return an empty list.

## SKILL CATALOG
%s

## INPUT
- Repo Name: %s
- Description: %s
- CONTRIBUTION (this student's share of the repository):
%s
- LANGUAGE BYTES (GitHub's own measurement of the committed code):
%s
- THIS STUDENT'S COMMIT SUBJECTS (newest first):
%s
- README snippet:
%s
- Config file snippet (package.json / pom.xml / requirements.txt):
%s

## OUTPUT FORMAT (strict JSON, no extra text)
{
  "summary": "...",
  "techStack": {
    "Backend": ["Spring Boot", "Java"],
    "Database": ["PostgreSQL"]
  },
  "matchedSkills": [
    {"skill": "TypeScript", "confidence": 0.9},
    {"skill": "Frontend Framework", "confidence": 0.85}
  ]
}
