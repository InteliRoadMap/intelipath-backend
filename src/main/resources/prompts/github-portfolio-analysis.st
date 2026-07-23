## TASK
You are a senior software engineer reviewing a GitHub repository for a student's portfolio.
Analyze the metadata below and return a structured JSON result.

## INSTRUCTIONS
1. **techStack**: Extract all technologies into a JSON object.
   - Keys MUST be categories: "Frontend", "Backend", "Database", "DevOps", "Testing", "Other".
   - Values are arrays of specific technology names (e.g., ["React", "TypeScript"]).
   - Only include categories that are actually present. Skip empty ones.
   - Be specific: prefer "Spring Boot" over "Java", "PostgreSQL" over "SQL".

2. **summary**: Write exactly 2 sentences in Vietnamese describing what this project does.
   - Sentence 1: What the project is and its main purpose.
   - Sentence 2: Key technologies used and what makes it notable.
   - Keep it professional and suitable for a portfolio. No filler like "Đây là một dự án...".

3. **matchedSkills**: From the SKILL CATALOG below, decide which skills this repository
   genuinely demonstrates and how strongly.
   - Use ONLY skill names copied verbatim from the catalog. Do not invent names.
   - "confidence" is 0.0-1.0 = how strongly the CODE (not just a mention) shows the skill.
     A skill that is the project's primary stack -> high (0.85-0.95). A library used in
     one config file or mentioned in passing -> low (0.4-0.6). Omit skills with no real signal.
   - If the catalog is empty, return an empty list.

## SKILL CATALOG
%s

## INPUT
- Repo Name: %s
- Description: %s
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
