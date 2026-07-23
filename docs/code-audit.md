# InteliPath Backend — Full Source Code Audit

**Date:** 2026-07-02
**Scope:** `intelipath-backend` — all 264 files in `src/main/java/com/inteliroadmap/backend/`, 100% read.
**Criteria:** Dependency Injection, business logic correctness, logging convention, SOLID, logic clarity, folder placement, imports, Swagger.

> **Status update (2026-07-02):** Section 3 (Logging convention) below describes the *pre-fix* state. **This has since been fixed** — all ~285 active log statements across `controllers/`, `security/`, `config/`, `services/`, `scheduler/`, `tools/`, `clients/`, `components/` were standardized to the format `"<SimpleClassName>: message"` (e.g. `log.info("AdminServiceImpl: ...")`). Bracket-style and free-text "Module:" prefixes were removed. Log calls inside commented-out dead code (`DatabaseSeeder.importMockUsersData`, `EmailServiceImpl.sendOtpEmail`, `jobs/`, `parsers/`) were intentionally left untouched since they don't compile/run. **Logging *coverage* (methods/classes with zero logging) was explicitly NOT addressed** — only the format of existing log calls was fixed. All other findings below are still open.

> Note: `HttpCookieOAuth2AuthorizationRequestRepository.java` is referenced below as fine/complete — an earlier review pass flagged it as "truncated/won't compile" but that was a sandbox shell-mount sync artifact, not a real file issue; confirmed correct via direct file read.

---

## Executive Summary

Overall architecture follows the layered model (Controller → Service → Repository → Entity) reasonably well. Recurring issues across the codebase:

1. ~~Logging did not follow any single convention~~ — **fixed**, see status note above.
2. Several classes bypass the service layer and call Repositories directly (DIP violation) — `UniversityController`, `OAuth2AuthenticationSuccessHandler`, and 4/9 mappers (`PortfolioMapper`, `SkillMapper`, `StudentDashboardMapper`, `StudentMapper`).
3. Heavy use of wildcard imports (`import x.y.*`) and dozens of places using fully-qualified class names inline in method bodies instead of importing at the top of the file.
4. Swagger: 5 controllers / 24 endpoints completely missing `@ApiResponses`.
5. Real logic bugs (not just style issues) — see section 2.

---

## 1. Dependency Injection

**Good baseline:** constructor injection (`@RequiredArgsConstructor` + `final`) is used almost everywhere. No field injection (`@Autowired` on a field) found anywhere in the 264 files.

**Violations — bypasses Service layer, calls Repository directly:**
- `controllers/UniversityController.java` — injects `UniversityRepository`, calls `findAll()`, returns the `University` entity directly (no DTO).
- `security/OAuth2AuthenticationSuccessHandler.java` — injects `RefreshTokenRepository` and `UserRepository` directly, bypassing the service layer in the auth flow.

**Violations — manual `new` instead of injected bean:**
- `services/impl/SkillExtractionServiceImpl.java`, `services/impl/SupabaseStorageServiceImpl.java` (x2), `clients/GithubApiClient.java`, `tools/MarkItDownTool.java`, `scheduler/JobScrapingScheduler.java` — 5+ places manually construct `new RestTemplate()` instead of using a shared injected bean.

**Violations — mappers doing repository lookups (DIP):**
- `mappers/PortfolioMapper.java`, `mappers/SkillMapper.java`, `mappers/StudentDashboardMapper.java`, `mappers/StudentMapper.java` — inject `Repository` beans and query DB inside "mapper" methods. The other 5 mappers (`AdminMapper`, `CounselorMapper`, `MarketTrendMapper`, `ScraperMapper`, `UserMapper`) are clean pure mappers.

**Field-naming bug (confusing, not a compile error):**
- `services/impl/PortfolioServiceImpl.java`, `RoadmapServiceImpl.java`, `SkillServiceImpl.java`, `StudentServiceImpl.java` all declare:
  ```java
  private final AuthenticatedStudentService AuthenticatedStudentService;
  ```
  Field name identical to the type name except capitalization — compiles fine but is a readability/convention violation. Should be renamed to `authenticatedStudentService`.

---

## 2. Business Logic Correctness (real bugs)

- **`config/DatabaseSeeder.java`** — wrong CSV column read: `line[11] == null || line[8].isEmpty() ? 0 : Integer.parseInt(line[8])` — null-check uses `line[11]` but the value parsed comes from `line[8]` (a string column, not the numeric node-level column). Will throw `NumberFormatException` or import wrong data.

- **`services/impl/CounselorServiceImpl.java`** — `int progress = (totalRoadmapNode == 0) ? 0 : nodesCompleted / totalRoadmapNode;` — integer division of `int/int` only ever yields `0` or `1`, never a real percentage. Should be `(nodesCompleted * 100) / totalRoadmapNode`, as done correctly elsewhere in `RoadmapServiceImpl.java` and `StudentServiceImpl.java`.

- **`services/impl/VirtualMentorServiceImpl.java`** — `buildSystemPrompt()` uses `student.getUniversity()` (the whole entity) instead of `student.getUniversity().getName()` — dumps the entity's `toString()` into the AI system prompt.

- **`services/impl/MentorServiceImpl.getProgressReports()`** — declared to return real progress-report data but returns entirely hardcoded/mocked data (fake student names, fixed numbers) regardless of the authenticated mentor or DB state.

- **`domain/enums/RecommendationAction.java`, `RecommendationStatus.java`, `RecommendationType.java`** — `fromString()` is a non-static instance method annotated `@JsonValue`. `@JsonValue` is for serialization (no-arg methods), not deserialization with a String parameter as written — this doesn't function as intended. Correct pattern (`public static X fromString(...)`) is used consistently elsewhere (`EvidenceStatus`, `EvidenceType`, `FeedbackStatus`, `ReviewStatus`, `StageType`, `UserRole`, `UserStatus`).

- **`mappers/StudentDashboardMapper.java`** — `importanceRank()`/`skillGapType()` compare against the string `"CRITICAL"`, but `ImportanceLevel` enum only has `LOW, AVG, HIGH` — that branch is dead/unreachable.

- **`repositories/UserSkillRepository.java`** — empty interface, does **not** `extend JpaRepository` — non-functional as a Spring Data repository.

- **`exceptions/enums/ErrorCode.java`** — `INVALID_OTP_CODE` reuses the exact same message as `UNAUTHORIZED` ("Unauthorized") — copy-paste bug.

- **Entity leakage in response DTOs**: `UpdateProfileResponse`, `CareerResponse`, `MentorResponse` (including a raw Spring Data `Page<PortfolioReviewRequest>`!), `RoadmapResponse` — all return JPA entities directly instead of DTOs.

**Dead code to clean up:** `parsers/LinkedInParser.java` and `parsers/TopCvParser.java` (entirely commented out), `jobs/DailyScrapeJob.java` (entirely commented out, duplicate of live `scheduler/JobScrapingScheduler.java`), `exceptions/InvalidRefreshTokenException.java` (3 lines, all comments, no package decl), `exceptions/AppException.java` + `exceptions/enums/ErrorCode.java` (zero usages anywhere, never wired into `GlobalExceptionHandler`), ~170 commented-out lines in `DatabaseSeeder.java` (`importMockUsersData`), unused `BCryptPasswordEncoder` bean in `SecurityConfig.java`.

---

## 3. Logging Convention

**Status: FIXED** — see the status note at the top of this document. Historical detail (pre-fix state) preserved for context: prior to the fix, 0% of log calls used a consistent module-prefix convention; three different styles coexisted (`[Bracket]`, `"Module: "` colon-prefix, no prefix at all). All active log calls now use `"<SimpleClassName>: message"`.

**Still open — logging coverage gaps** (methods/classes with meaningful logic and zero logging, NOT addressed by the format fix):
- `controllers/MarketTrendController.java`, `controllers/RoadmapController.java`, `controllers/UniversityController.java` — no logging at all.
- `services/impl/RagDocumentServiceImpl.java`, `RagVectorStoreServiceImpl.java` — no `@Slf4j`, no logging, including in catch blocks.
- `services/impl/VirtualMentorServiceImpl.java` — has `@Slf4j` but zero log calls despite 4 `orElseThrow` sites and an async DB write.
- `services/impl/MentorServiceImpl.java` — only 1/9 methods log anything.
- `clients/GithubApiClient.java` `fetchFileContent` — catches `Exception`, returns `""`, no logging — silent failure swallowing.
- `tools/StudentProgressTool.java` — no logger at all; catch block discards the exception without logging it.

**Non-English content found (comments/response payloads, not logs):** Vietnamese comments in `JwtAuthenticationFilter.java`, `SecurityConfig.java`, `DocumentIngestionServiceImpl.java`, `PdfToMarkdownServiceImpl.java`; Vietnamese javadoc throughout `GlobalExceptionHandler.java`; hardcoded Vietnamese response content in `MentorController.getWelcomeAlert()`/`getInsight()`, `MarkItDownTool.java`, `PortfolioAiAnalyzer.java`.

---

## 4. SOLID Principles

**SRP violations:**
- `config/DatabaseSeeder.java` — handles 4 unrelated domains (university, career, skill, roadmap) plus 170 lines of dead code.
- `controllers/MentorController.java` — hardcoded fake response content built directly in the controller.
- `controllers/VirtualMentorController.java` — contains entity→DTO mapping methods that belong in `mappers/`.
- `services/impl/RoadmapServiceImpl.java` (659 lines) — mixes CRUD, frontend status-mapping, manual JWT/header parsing, and business rules.
- `services/impl/StudentServiceImpl.java` — injects 5 repositories/mappers it never uses (leftover from extracting `StudentDashboardServiceImpl`).
- `scheduler/JobScrapingScheduler.java` — combines HTTP fetch, 3-entity mapping/persistence, and scheduling in one class.
- `utils/EmailUtil.java` — mixes OTP generation with ~280 lines of embedded HTML/CSS email templates.

**OCP:**
- `parsers/TopCvParser.java` (dead) — giant if/else chain for label translation, should be a `Map` lookup.
- `mappers/StudentDashboardMapper.java` — string-based enum comparison via `toString()`, fragile (already broken — see section 2).

**LSP:**
- `MentorServiceImpl.getProgressReports()` — returns mock data, violating its implicit contract (section 2).
- `RoadmapServiceImpl.getStudentFromAuthHeader()` throws raw `RuntimeException` while sibling methods throw `ResourceNotFoundException`.
- `PortfolioServiceImpl.requestReview()` throws `IllegalStateException` while sibling methods throw `ResourceNotFoundException`.

**ISP:** no significant violations found — interfaces are reasonably scoped to their domain.

**DIP:**
- `UniversityController`, `OAuth2AuthenticationSuccessHandler` — depend on Repository directly instead of Service abstraction.
- `services/impl/RagVectorStoreServiceImpl.java` — depends directly on `JdbcTemplate`, embeds raw SQL in the service layer instead of behind a repository.
- 4 mappers depend directly on Repository (section 1).
- `jobs/DailyScrapeJob.java` (dead) / `parsers/` — no common `JobSiteParser` interface for parsers.

---

## 5. Logic Clarity

Controllers are generally short and readable (1-3 lines delegating to a service) — good practice. Issues concentrated in the service layer:

- Deep nesting in `SkillExtractionServiceImpl.extractAndRebuildSkillTrends()` (4 levels of if/for/instanceof).
- Duplicated "get authenticated user from SecurityContext/header" logic hand-rolled independently in at least 6 places (`CounselorServiceImpl`, `FeedbackServiceImpl`, `UserServiceImpl` ×3 within one file, `MentorServiceImpl`, `VirtualMentorServiceImpl`, `RoadmapServiceImpl`) instead of reusing `AuthenticatedStudentService`.
- `resolveUniversityDisplayName()` duplicated verbatim in `PortfolioMapper.java` and `StudentMapper.java`.
- `SupabaseStorageServiceImpl.uploadAvatar()` duplicates logic instead of reusing the `uploadFile()` helper.
- `ScraperServiceImpl` hand-builds nested DTOs with unchecked casts inline instead of using a dedicated mapper.

---

## 6. Folder / Package Placement

Mostly correct (DTOs in feature-matched subfolders, entities in `domain/entity`, security helpers in `security/`). Misplaced code:

- `controllers/VirtualMentorController.java` — mapping logic that belongs in `mappers/`.
- `controllers/MentorController.java` — content-generation logic that belongs in a service.
- `controllers/UniversityController.java` — query logic that should go through a service.
- 4 mappers (section 1) — repository-fetch logic that belongs in a service.
- `services/impl/RagVectorStoreServiceImpl.java` — raw SQL that belongs in a repository.
- `services/impl/SupabaseStorageServiceImpl.java` — manual HTTP client code that could move to `clients/` (mirroring the existing `clients/GithubApiClient` pattern).
- `components/PortfolioAiAnalyzer.java` — only used by `GithubPortfolioService`; behaves like a service helper, not a generic "component". Consider moving to `services/` or `services/ai/`.
- `jobs/` — entirely dead, duplicates `scheduler/JobScrapingScheduler.java` — recommend deleting.
- `parsers/` — entirely dead (scraping moved to the external Python service) — recommend deleting or archiving.
- 5 response DTOs leaking entities directly (section 2).

---

## 7. Imports

**Wildcard imports** found in dozens of files: all ~32 entity files (`import jakarta.persistence.*;`), `security/CustomOAuth2User.java` (`lombok.*`), `security/JwtService.java` (`io.jsonwebtoken.*`), `config/DatabaseSeeder.java` (4 wildcards), ~13 files in `services/`, roughly half of all controllers (`import org.springframework.web.bind.annotation.*;`) while the other half import individually — inconsistent within the same package.

**Fully-qualified class names used inline instead of importing at the top of the file** — found repeatedly:
- Controllers: `PortfolioController`/`PublicPortfolioController` (`java.util.UUID`), `StudentController`/`UserController` (`MediaType`, `RequestParam`, `MultipartFile`), `MentorController`, `VirtualMentorController` (FQN used even for a field type).
- Services: `RoadmapServiceImpl` (FQN field type, plus `Student.builder()`/`LocalDateTime.now()` despite both already imported), `AuthServiceImpl`, `FeedbackServiceImpl`, `OAuth2UserServiceImpl` (`User.builder()` despite `User` imported), `SupabaseStorageServiceImpl` (`UUID.randomUUID()`, `HttpStatusCodeException`), `VirtualMentorServiceImpl` (`@org.springframework.transaction.annotation.Transactional` used as inline FQN annotation).
- Domain: `FeedbackResponse`, `MentorProfileResponse`, `UpdateProfileRequest`, `SkillNode`/`PortfolioProject`/`NodeType`/`RoadmapRecommendationItem` (Hibernate `SqlTypes`/`JsonNode`).
- Repositories: `RefreshTokenRepository` (`@Transactional` x2), `CompanyRepository` (`Pageable`), `StudentProgressRepository` (`Optional`), `FeedbackRepository` (`LocalDateTime`).
- Mappers: `PortfolioMapper` (`User.builder()`/`Student.builder()` despite a wildcard entity import already present — redundant AND inconsistent), `ScraperMapper` (`List` cast, `List` not imported at all).
- `scheduler/JobScrapingScheduler.java` — `HashMap`/`Map` via FQN repeatedly, plus `Company.builder()`/`Recruitment.builder()` via FQN despite being imported.

**Unused imports:** `security/JwtService.java` (`ResourceNotFoundException`), `domain/entity/AcademicCounselor.java`/`Student.java` (`LocalDate`), `domain/entity/Company.java`/`Recruitment.java` (`ArrayList`, only referenced in dead code), 3 request DTOs importing unused Lombok annotations, and — systemically — nearly every top-level interface file in `services/` imports the entire impl class's dependency list (repositories, mappers, `@Service`, `@Transactional`, `Slf4j`, and even a self-import of the interface itself) despite none of it being used in an interface declaration. Only 6 of ~23 service interfaces are clean.

---

## 8. Swagger / OpenAPI Documentation

Checked all 17 controllers (~90 endpoints):

| Controller | Endpoints | Status |
|---|---|---|
| CounselorController | 11 | ✅ Best-documented reference example |
| StudentController, StudentDashboardController, RoadmapController, AdminController, PortfolioController, PublicPortfolioController, CareerController, RoadmapSkillController | — | ✅ Fully documented |
| RecruitmentPostsController | 3 | ⚠️ Missing `summary` on all 3 endpoints |
| AuthController | 2 | ⚠️ `/logout` missing `@ApiResponses` |
| AdminController | 8 | ⚠️ 2/8 endpoints missing `@ApiResponses` |
| UserController | 4 | ⚠️ `updateAvatar` missing `@ApiResponses` |
| GithubPortfolioController | 1 | ❌ No `@ApiResponses` at all |
| MarketTrendController | 3 | ❌ No `@ApiResponses` on any endpoint, no `@SecurityRequirement` |
| MentorController | 11 | ❌ No `@ApiResponses` on any of 11 endpoints |
| VirtualMentorController | 8 | ❌ No `@ApiResponses` on any of 8 endpoints, no `@Schema` anywhere |
| UniversityController | 1 | ❌ No Swagger annotations at all |

**Total: 5 controllers / 24 endpoints missing `@ApiResponses`.**

---

## Priority Fix List (Remaining)

1. `DatabaseSeeder.java` wrong CSV column bug.
2. `CounselorServiceImpl.java` integer-division percentage bug.
3. `VirtualMentorServiceImpl.java` wrong entity leaking into AI prompt.
4. `MentorServiceImpl.getProgressReports()` returning mock data in production.
5. Broken `fromString()` on 3 enums (`RecommendationAction/Status/Type`).
6. `UserSkillRepository` not extending `JpaRepository`.
7. ~~Logging convention~~ — **done**.
8. Clean up wildcard imports + inline FQN references (section 7).
9. Add missing `@ApiResponses` to 5 controllers / 24 endpoints (section 8).
10. Delete dead code: `parsers/`, `jobs/`, `InvalidRefreshTokenException`, unused `AppException`/`ErrorCode` (or wire into `GlobalExceptionHandler`), ~170 dead lines in `DatabaseSeeder`.
11. Architecture: move `UniversityController`/`OAuth2AuthenticationSuccessHandler` to go through Service; refactor the 4 repository-calling mappers into pure mappers; stop leaking entities through the 5 affected response DTOs.
