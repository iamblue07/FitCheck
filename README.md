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
| 7 | Compatibility Scoring & Candidate Generation | ✅ Done  |
| 8 | Infinite Scroll Feed | ⬜ Not started |
| 9 | Garment Alternatives | ⬜ Not started |
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
- Request correlation IDs on every log line via SLF4J's MDC.
- PostgreSQL running locally with `pgvector` compiled and enabled, and a matching Aiven-hosted instance, switchable via environment variables with no code changes.
- The full schema from `database-schema.md` created via versioned Flyway migrations, applied to both targets.
- Base JPA entity conventions (`BaseEntity` / `AuditableEntity`) covering id generation and audit timestamps.
- Cloudflare R2 integration for media files.
- Authentication & authorization.
- Profile and body photo management, with style-tag preferences.
- Catalog data pipeline: full Kaggle dataset import into `products`, with synthetic variant stock generated at the same time.
- Catalog enrichment pipeline: an admin endpoint for single-item manual testing, and an unattended batch runner for working through the rest — both against a local Qwen3-VL 4B model via Ollama.
- Catalog embeddings pipeline: a separate batch pass generating local embeddings (Qwen3-Embedding-4B via Ollama) for every enriched product, MRL-truncated from its native 2560 dimensions to 2000 and renormalized, with per-item fallback if a batch call fails partway through.
- Deterministic garment-role classification (`GarmentRoleResolver`), keyed on `article_type` alone and verified against all 142 real values in the catalog, applied to existing products via a paginated, idempotent backfill runner.
- First `outfit` package entities (`Outfit`, `OutfitItem`) and repositories, plus two new vector-search repository methods on `ProductRepository`.
- `OutfitCompatibilityScorer`: a weighted, explainable compatibility score combining structured color/layering rules with embedding similarity, returned as a full breakdown rather than a bare number.
- `OutfitCandidateGenerator`: beam-search candidate assembly (not plain greedy) with centroid-based nearest-neighbor retrieval and a local-search polish pass, backed by a dedicated, concurrency-safe `OutfitPersistenceService`.
- Automated test coverage for all chapters, pure Mockito unit tests with no Spring context needed.

Chapter 7's one remaining piece: a debug-only, property-gated runner for exercising the generator against real profile shapes — not built yet.

## Project structure

Packages are organized by feature, not by technical layer — `identity`, `catalog`, `outfit`, `feed`, `tryon`, `commerce`, plus `common` for cross-cutting code. Each feature package holds its own controller/DTO/entity/repository/service classes internally. This was a deliberate choice over the more common controller/service/repository top-level split, given the number of distinct feature areas in this project.

## Getting started

Prerequisites: JDK 21+, Maven, PostgreSQL 17 with the `pgvector` extension, [Ollama](https://ollama.com) with both `qwen3-vl:4b` and `qwen3-embedding:4b` pulled (`ollama pull qwen3-vl:4b` and `ollama pull qwen3-embedding:4b`), IntelliJ IDEA (recommended). No OpenAI API key needed yet — see the tech stack note above.

1. Clone the repo.
2. Create a `.env.local` file at the project root (untracked) with your local Postgres connection details, Cloudflare R2 and JWT settings, and the Ollama/catalog batch variables (`OLLAMA_BASE_URL`, `OLLAMA_ENRICHMENT_MODEL`, `OLLAMA_EMBEDDING_MODEL`, `CATALOG_EMBEDDING_CHUNK_SIZE`, and the rest of the `catalog.*` batch settings) — see `application.properties` for the full list of expected variables.
3. Make sure Ollama is running with both models pulled (check for it in the system tray, or launch it — it does not always survive a reboot reliably on Windows).
4. Run the app from IntelliJ, or `mvn spring-boot:run`.
5. Confirm it's up: `GET http://localhost:8080/actuator/health` should return `{"status":"UP"}`.

## Documentation

Documentation TBD at a later date