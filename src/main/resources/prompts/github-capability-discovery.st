## TASK
Inspect this repository evidence and identify concrete technologies and engineering
capabilities demonstrated by implementation. This is retrieval context for a later,
catalog-constrained verifier; do not grade the student and do not assume a fixed language.

## RULES
- Work from imports, annotations, base classes, function calls, schemas, routes, tests,
  runtime configuration and the student's commit subjects.
- Name the most conventional industry capability or technology represented by each
  construct (for example a framework, persistence approach, API style, test approach,
  security mechanism, build/deployment capability or database actually wired at runtime).
- Be ecosystem-neutral. Apply the same standard to JVM, .NET, Python, JavaScript,
  TypeScript, Go, Rust, PHP, Ruby and any other ecosystem present.
- A dependency alone may identify a technology but not an advanced capability.
- Ignore badges, tutorial links and generic README claims unsupported by supplied files.
- Keep at most 30 distinct, concrete capabilities. Avoid generic labels such as
  "programming", "backend", "API" or "database" when a specific capability is visible.
- Evidence must cite the supplied file path, construct, config key or commit subject.

## INPUT
- Repo: %s
- Description: %s
- Contribution: %s
- Language bytes: %s
- Student commit subjects: %s
- README: %s
- Bounded source/config evidence: %s

## OUTPUT (strict JSON)
{
  "capabilities": [
    {"name": "conventional technology or capability name", "evidence": "specific supplied construct or path"}
  ]
}
