# FitCheck

AI-powered outfit discovery and virtual try-on app. Master's thesis project — Spring Boot backend, Flutter frontend (built after the backend).

## What it does

Users build a profile (body measurements, style preferences, budget) and upload two reference photos for virtual try-on. From there:

- An infinite-scroll feed recommends outfits matched to their profile.
- A free-text prompt to an AI can generate outfits directly, or refine a single garment within one.
- Any single garment in an outfit can be swapped for alternatives, manually or via prompt.
- Liked outfits can be virtually tried on, generating an image of the user wearing them.
- Outfits can be purchased whole or piece by piece (this feature is deliberately planned last, after the frontend exists).

## Tech stack

- **Backend**: Java 21+, Spring Boot 4.1+, Maven
- **Database**: PostgreSQL 18 with `pgvector` — native installation for local dev, [Aiven](https://aiven.io) (free tier) for hosting; nearest-neighbor candidate retrieval via Spring Data JPA's native vector-search support (`Vector`/`ScoringFunction`/`SearchResults`) as of Chapter 7, backed by an HNSW index on `products.text_embedding`
- **Migrations**: Flyway
- **AI**: Ollama running Qwen3-VL 4B locally (catalog enrichment) and Qwen3-Embedding-4B locally (catalog embeddings, MRL-truncated to 2000 dimensions), [FASHN AI](https://fashn.ai) (virtual try-on). OpenAI's starter is on the classpath for Chapter 10's prompt-driven outfit generation but isn't wired to anything yet — every OpenAI model capability is explicitly disabled until that chapter starts.
- **Storage**: Cloudflare R2 (S3-compatible, 10GB free, zero egress) for user photos and generated try-on images
- **Frontend**: Flutter (not started yet)
- **Testing**: JUnit 5 + Mockito (automated), Postman (manual)
- **Dev environment**: Windows, IntelliJ IDEA

## Status

| # | Chapter | Status |
|---|---|---|
| 1 | Project Foundations & Environment Setup | ✅ Done |
| 2 | Relational Data Layer | ✅ Done |
| 3 | Cloudflare R2 Storage | ✅ Done |
| 4 | Authentication | ✅ Done |
| 5 | Profile & Body Photos | ✅ Done |
| 6.1 | Catalog Data Pipeline | ✅ Done |
| 6.2 | Enrichment & Embeddings | ✅ Done |
| 7 | Compatibility Scoring & Candidate Generation | ✅ Done |
| 8 | Infinite Scroll Feed | ✅ Done |
| 9 | Garment Alternatives | ✅ Done |
| 10 | Prompt-to-Outfit & Prompt-to-Garment-Refinement | ⬜ Not started |
| 11 | FASHN AI Integration & Async Job Pipeline | ⬜ Not started |
| 12 | Likes, Saves & Shares | ⬜ Not started |
| 13 | Logging, Security & API Documentation Audit | ⬜ Not started |
| 14 | Build & Deployment Pipeline | ⬜ Not started |
| 15+ | Frontend (Flutter) | ⬜ Not started |
| 16 | Orders & Checkout (post-frontend) | ⬜ Not started |

### What's actually in place right now

- Feature-based package skeleton (`identity`, `catalog`, `outfit`, `feed`, `tryon`, `commerce`, `common`).
- Shared exception hierarchy (`AppException` and subtypes), a consistent JSON error shape, and a global exception handler.
- Request correlation IDs on every log line via SLF4J's MDC, including on background thread pools via a reusable `MdcTaskDecorator`.
- PostgreSQL running locally with `pgvector` compiled and enabled, and a matching Aiven-hosted instance, switchable via environment variables with no code changes.
- The full schema from `database-schema.md` created via versioned Flyway migrations (currently at V12), applied to both targets.
- Base JPA entity conventions (`BaseEntity` / `AuditableEntity`) covering id generation and audit timestamps.
- Cloudflare R2 integration for media files.
- Authentication & authorization.
- Profile and body photo management, with style-tag preferences.
- Catalog data pipeline: full Kaggle dataset import into `products`, with synthetic variant stock generated at the same time.
- Catalog enrichment pipeline: an admin endpoint for single-item manual testing, and an unattended batch runner for working through the rest — both against a local Qwen3-VL 4B model via Ollama.
- Catalog embeddings pipeline: a separate batch pass generating local embeddings (Qwen3-Embedding-4B via Ollama) for every enriched product, MRL-truncated from its native 2560 dimensions to 2000 and renormalized, with per-item fallback if a batch call fails partway through.
- Deterministic garment-role classification (`GarmentRoleResolver`), keyed on `article_type` alone and verified against all 142 real values in the catalog, applied to existing products via a paginated, idempotent backfill runner.
- `outfit` package entities (`Outfit`, `OutfitItem`) and repositories, plus vector-search repository methods on `ProductRepository`.
- `OutfitCompatibilityScorer`: a weighted, explainable compatibility score combining structured color/layering rules with embedding similarity, returned as a full breakdown (and, as of Chapter 8, persisted in full — every component, not just the blended value).
- `OutfitCandidateGenerator`: beam-search candidate assembly (not plain greedy) with centroid-based nearest-neighbor retrieval and a local-search polish pass, backed by a dedicated, concurrency-safe `OutfitPersistenceService`.
- `GET /api/v1/feed`: cursor-paginated, personalized outfit feed. Ranking is a bounded multiplier on the outfit's own compatibility score (so an unvalidated personalization signal can never outrank the validated one), never-repeat is enforced by a real database constraint, and a background refill keeps the feed topped up without ever blocking the request that triggered it.
- A cross-feature query-facade pattern (`catalog.service.ProductSearchService`, `identity.service.UserProfileQueryService`, and others) so features read each other's data through a narrow, owned contract instead of reaching into each other's repositories directly.
- Automated test coverage for every chapter, pure Mockito unit tests with no Spring context needed — including concurrency-safe tests for the feed's in-memory refill guard (real thread contention via `CountDownLatch`, not just sequential calls).
- `GarmentSwapService`, split across two endpoints rather than one: `GET /api/v1/outfits/{outfitId}/items/{itemId}/alternatives` (a safe, side-effect-free read) lists candidate replacements matched on `article_type`, reusing the same gender-filtered nearest-neighbor retrieval as outfit generation; `POST /api/v1/outfits/{outfitId}/items/{itemId}/swap` (the side-effecting action) applies one, re-checks the whole resulting outfit against the configurable per-outfit budget (a stricter cumulative re-check than generation's own per-item guard, since a swap changes a total the user already saw), and persists the result as a new, immutable outfit tagged `OutfitSource.MANUAL_SWAP` through the same race-safe `OutfitPersistenceService` path (`saveAndFlush` + `item_set_hash` unique constraint) used elsewhere.
- A chapter-wide exception-handling audit: five new `@ExceptionHandler` methods on `GlobalExceptionHandler` covering Spring/DB exception types that were previously falling through to a generic 500 (malformed path variables, malformed JSON bodies, missing request parameters, unsupported HTTP methods, raw database constraint violations), plus two real TOCTOU races fixed at the source (`AuthService.register()`'s email-uniqueness check, mirroring the existing `outfits.item_set_hash` pattern) and a raw `IllegalArgumentException` in `OutfitCompatibilityScorer` replaced with `BadRequestException`. Full writeup in `docs/chapter-09-theory.md`.


## Project structure

Packages are organized by feature, not by technical layer — `identity`, `catalog`, `outfit`, `feed`, `tryon`, `commerce`, plus `common` for cross-cutting code. Each feature package holds its own controller/DTO/entity/repository/service classes internally. This was a deliberate choice over the more common controller/service/repository top-level split, given the number of distinct feature areas in this project. Cross-feature reads go through a narrow query-facade service owned by the feature whose data it exposes (e.g. `catalog.service.ProductSearchService`) rather than one feature injecting another's repository directly.

## Getting started

Prerequisites: JDK 21+, Maven, PostgreSQL 17 with the `pgvector` extension, [Ollama](https://ollama.com) with both `qwen3-vl:4b` and `qwen3-embedding:4b` pulled (`ollama pull qwen3-vl:4b` and `ollama pull qwen3-embedding:4b`), IntelliJ IDEA (recommended). No OpenAI API key needed yet — see the tech stack note above.

1. Clone the repo.
2. Create a `.env.local` file at the project root (untracked) with your local Postgres connection details, Cloudflare R2 and JWT settings, and the Ollama/catalog batch variables (`OLLAMA_BASE_URL`, `OLLAMA_ENRICHMENT_MODEL`, `OLLAMA_EMBEDDING_MODEL`, `CATALOG_EMBEDDING_CHUNK_SIZE`, and the rest of the `catalog.*` batch settings) — see `application.properties` for the full list of expected variables.
3. Make sure Ollama is running with both models pulled (check for it in the system tray, or launch it — it does not always survive a reboot reliably on Windows).
4. Run the app from IntelliJ, or `mvn spring-boot:run`.
5. Confirm it's up: `GET http://localhost:8080/actuator/health` should return `{"status":"UP"}`.