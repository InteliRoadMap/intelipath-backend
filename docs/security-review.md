# InteliPath — Source Code Review

**Date:** 2026-07-02
**Scope:** `intelipath-backend` (Java/Spring Boot, ~264 files / ~16,700 LOC) and `intelipath-service` (Python/FastAPI, ~22 files / ~806 LOC)
**Focus:** Code quality and security

---

## Summary

The backend has solid architectural bones — layered controller/service/repository design, parameterized queries throughout, a well-built refresh-token rotation scheme, and correct `FetchType.LAZY` discipline — but ships two serious security defects: an OAuth2 cookie is deserialized with raw Java `ObjectInputStream` (a hardened AES-GCM replacement already exists in the codebase but was never wired in — **this has since been fixed, see "Fix Applied" note below**), and a real-looking `.env` file with live-looking production secrets sits unencrypted in the project folder.

The Python service is small and has no persistence layer, but its one REST endpoint that fetches arbitrary URLs (`POST /api/extract`) has no SSRF protection, no auth, and no timeout — even though a correct SSRF guard already exists in the same codebase's MCP server and simply isn't applied to the REST path. There is no test coverage in the service at all.

Both projects show a recurring pattern: **a correct security fix exists somewhere in the codebase but wasn't applied everywhere it's needed.**

> **Fix Applied (2026-07-02):** Critical Security Issue #1 below (insecure OAuth2 cookie deserialization) has been fixed — `SecureOAuth2CookieCodec` is now wired into `HttpCookieOAuth2AuthorizationRequestRepository`, and the unsafe `CookieUtils.serialize`/`deserialize` methods were removed. See `security/HttpCookieOAuth2AuthorizationRequestRepository.java` and `security/SecureOAuth2CookieCodec.java`. All other findings below are still open.

---

## Priority Fixes

1. ~~**Backend — insecure deserialization in OAuth2 cookie handling** (Critical)~~ — **FIXED**
2. **Backend — live secrets in `.env` on disk** (Critical, treat as compromised) — still open
3. **Service — unauthenticated SSRF on `POST /api/extract`** (Critical) — still open
4. **Service — unauthenticated, unbounded scrape endpoint (DoS)** (Critical) — still open

---

## Part 1 — intelipath-backend (Java / Spring Boot)

### Critical Security Issues

**1. ~~Insecure deserialization of an attacker-controlled cookie~~ — FIXED**
`security/HttpCookieOAuth2AuthorizationRequestRepository.java` previously deserialized the `oauth2_auth_request` cookie via raw `SerializationUtils.deserialize()` (Java `ObjectInputStream`) on attacker-suppliable bytes with no integrity check. This has been fixed: the repository now depends on `SecureOAuth2CookieCodec` (AES-GCM authenticated encryption, deserialization only happens after authentication succeeds) for both `loadAuthorizationRequest` (via `codec.decode(...)`) and `saveAuthorizationRequest` (via `codec.encode(...)`). The now-dead unsafe `CookieUtils.serialize`/`deserialize` methods were deleted. The corresponding unit test was updated to inject `SecureOAuth2CookieCodec` via constructor.

**2. Live-looking secrets in plaintext `.env`**
`intelipath-backend/.env` contains what appear to be real credentials: a Supabase Postgres password, a Supabase **service-role** JWT (full DB bypass), a Gmail app password, Google/GitHub OAuth client secrets, and an OpenAI project API key. `.gitignore` correctly excludes `.env` and it was never committed to git history — but the file exists unencrypted on disk. Recommend rotating all of these credentials regardless, and confirming no copy of this file was ever shared or zipped elsewhere.

**3. Public portfolio endpoint doesn't match the permit-list (fails closed, but broken)**
`SecurityConfig.java` permits `/api/v1/public/**` without auth, but `PublicPortfolioController` is mapped to `/api/v1/public-portfolio` — a different path that does not match that Ant pattern. Its own Swagger docs say it's meant to serve e-portfolios "without authentication," but every request currently 401s. Not exploitable, but it's a real config bug: either add `/api/v1/public-portfolio/**` to the permit-list or rename the path.

### Other Security Concerns

**4. Access token cookie has no `Secure`/`SameSite` attributes**
`OAuth2AuthenticationSuccessHandler.java` sets the JWT access token via `CookieUtils.addNonHttpOnlyCookie()`, which uses the raw `jakarta.servlet.http.Cookie` API — no `Secure` or `SameSite` setter exists on that API, so those attributes are simply absent. The refresh token, by contrast, correctly uses `ResponseCookie` with `httpOnly`, `secure`, and `sameSite` set (`AuthenticationCookieService.java`). If the SPA needs JS access to the token, at minimum set `Secure` and `SameSite=Lax`, ideally via the same `ResponseCookie`-based helper.

**5. Email addresses logged at INFO/WARN across the codebase (PII in logs)**
Recurring pattern across `JwtAuthenticationFilter`, `OAuth2AuthenticationSuccessHandler`, `AuthServiceImpl`, `AuthenticatedStudentServiceImpl`. `application-prod.yaml` sets `com.inteliroadmap: INFO`, so this lands in production logs. Recommend downgrading to DEBUG or masking.

**6. Duplicated, inconsistent authorization logic**
`AdminServiceImpl.validateAdmin()` manually re-extracts and re-validates the JWT from the raw `Authorization` header, duplicating what `JwtAuthenticationFilter` and the class-level `@PreAuthorize("hasRole('ADMIN')")` on `AdminController` already enforce — two independent sources of truth that can drift. It also throws `ResourceNotFoundException` (→ HTTP 404) for what's semantically a 401/403. The same manual-header pattern appears in `RoadmapController.java` and `VirtualMentorController.java`, while `AuthenticatedStudentServiceImpl.java` correctly uses `SecurityContextHolder`. Standardize on `SecurityContextHolder` everywhere.

**7. Generic exception handler returns raw exception messages to clients**
`GlobalExceptionHandler.java` — the catch-all `Exception` handler returns `.details(exception.getMessage())` directly in the HTTP 500 response body. No stack trace, but DB/driver exception messages can reveal table/column names or internal details. Log server-side instead; don't return `.details()` for the generic handler in production.

**8. CORS origins hardcoded despite an env var existing for it**
`.env` defines `CORS_ALLOWED_ORIGINS`, but `CorsConfig.java` hardcodes the same two dev origins in Java — the env var is dead. Not exploitable today, but a deployment footgun.

**9. Undefined placeholder will fail startup**
`application.yaml` references `${SCRAPER_LIMIT}`, which isn't defined in `.env` or `.env.example`. Confirm it's supplied out-of-band in deployment, or add a default (`${SCRAPER_LIMIT:10}`).

**10. Docker image skips tests and runs as root**
`Dockerfile` builds with `-Dmaven.test.skip=true`; the final stage has no `USER` directive, so the container runs as root.

### Code Quality Issues

**11. Controller bypasses the service layer** — `UniversityController.java` injects `UniversityRepository` directly and returns the JPA `@Entity` `University` straight to the client.

**12. Hardcoded fake metrics in the admin dashboard** — `AdminServiceImpl.java` returns hardcoded "12% growth", hardcoded 78% active, and `getSystemHealth` always returns 99.9/"ONLINE" regardless of actual state.

**13. Dead code and leftover debug output** — commented-out `@OneToMany` block in `User.java`; commented import in `SecurityConfig.java`; leftover `System.out.println` in `LinkedInParser.java`/`TopCvParser.java`; unused `BCryptPasswordEncoder` bean; commented-out `importMockUsersData()` call in `DatabaseSeeder.java`.

**14. Thin test coverage** — only 12 test files against ~264 main source files; zero tests for `AdminController`/`AdminServiceImpl`, `RoadmapController`, `MentorController`, `CounselorController`, `PortfolioController`, `MarketTrendController`, `UniversityController`.

**15. One missing `FetchType.LAZY`** — `OauthAccount.java` has a `@ManyToOne` with no explicit fetch type.

**16. Mixed Vietnamese/English comments** throughout.

### Architecture Observations

- Layering is consistent (Controller → Service → Repository) with `UniversityController` the sole exception.
- Auth is OAuth2-only (Google, GitHub); no local password flow.
- Refresh-token design is genuinely good: DB-backed tokens with pessimistic row locking, rotation on refresh, explicit ownership verification.
- Two overlapping cookie-handling paths exist (`AuthenticationCookieService` vs. `CookieUtils`) — consolidating onto one would eliminate the root cause of Concern #4.

### Positive Notes

- No string-concatenated SQL/JPQL anywhere.
- Refresh-token rotation with row locking and ownership checks is well-engineered.
- CORS uses an explicit allowlist, not a wildcard.
- 26/27 `@ManyToOne` associations explicitly declare `LAZY` fetch.
- `@Valid` is applied broadly and consistently on `@RequestBody` parameters.
- `.env` is correctly git-ignored and confirmed never committed.
- Correct Spring Security fundamentals: stateless JWT sessions, proper filter ordering, method-level `@PreAuthorize`.
- Reasonably current dependency baseline: Spring Boot 3.5.15, Java 21, jjwt 0.12.5, springdoc 2.8.5.

---

## Part 2 — intelipath-service (Python / FastAPI)

### Critical Security Issues

**1. Unauthenticated SSRF on `POST /api/extract`**
`app/schemas/extraction.py` types the input as plain `url: str` (not a validated `HttpUrl`). `app/api/endpoints/extraction.py` passes it directly to `extract_markdown_from_url()`, which does `requests.get(url, headers=headers)` with no validation and no timeout. A correct SSRF guard (`validate_public_document_url()` — resolves DNS, rejects private/loopback/link-local IPs) already exists in `app/mcp_server.py` but is **not applied to this endpoint**. Move the existing guard into `markitdown_service.extract_markdown_from_url()` so both the REST and MCP paths inherit it.

**2. No authentication on any endpoint**
All three REST endpoints and the mounted MCP server at `/mcp` are open to anyone who can reach the host.

**3. Unauthenticated, unbounded synchronous scrape (DoS)**
`app/api/recruitment_routes.py` runs `parse_topcv_jobs()` synchronously inside the request handler. A `limiter <= 0` makes the scrape loop run until no more jobs are found. No background task/queue, no timeout, no concurrency guard.

### Other Security Concerns

**4. Second-order SSRF via scraped content** — `topcv_parser.py` extracts `href` values from scraped pages with no validation and passes them to `curl`.

**5. No CORS configuration** — likely means a frontend needing cross-origin access is currently blocked.

**6. `requests.get` has no timeout** — compounds SSRF risk.

**7. Raw exception messages returned to clients** in `extraction.py`.

**8. `limit` parameter hand-parsed as a string** instead of using FastAPI's native `int` typing.

**9. Duplicate, conflicting `Settings` classes** — `app/config/config.py` (used) vs `app/core/config.py` (dead code).

**10. Selenium driver has no timeouts or container-friendly options.**

**11. `markitdown[all]` pulls a large, mostly unpinned dependency tree.**

**12. Nearly nothing in `requirements.txt` is version-pinned** — only `fastmcp==3.4.2`.

### Code Quality Issues

Duplicate `logging.basicConfig()` calls; duplicate `import re`; broad `except Exception` swallowing in several places; dead code (`parse_linkedin_jobs()` never called); config/documentation drift for `LINKEDIN_TARGET`; least defensively typed schema guards the most sensitive endpoint.

### Positive Notes

`validate_public_document_url()` in `mcp_server.py` is a well-built SSRF defense. `CurlEngine.get_document()` avoids shell injection. Selenium/temp-file cleanup use `try/finally` correctly. No secrets found anywhere in the service. Skill-extraction regex correctly uses word-boundary lookarounds.

### Test Coverage

No tests exist anywhere in the service.

---

## Recommended Next Steps (Remaining)

1. ~~Wire `SecureOAuth2CookieCodec` into `HttpCookieOAuth2AuthorizationRequestRepository`~~ — **done**.
2. Rotate every credential in `intelipath-backend/.env`.
3. Move `validate_public_document_url()` from `mcp_server.py` into `markitdown_service.extract_markdown_from_url()`; add a `requests` timeout.
4. Add authentication to the Python service's REST endpoints; move the TopCV scrape to a background task with a bounded `limit`.
5. Pin dependency versions in `requirements.txt`.
6. Fix the `/api/v1/public-portfolio` vs `/api/v1/public/**` path mismatch in `SecurityConfig`.
