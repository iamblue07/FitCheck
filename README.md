# FitCheck

AI-powered outfit discovery and virtual try-on app. Master's thesis project — Spring Boot backend, Flutter frontend (built after the backend).

## What it does

Users build a profile (body measurements, style preferences, budget) and upload two reference photos for virtual try-on. From there:

- An infinite-scroll feed recommends outfits matched to their profile.
- A free-text prompt to an AI can generate a batch of outfits directly — either matched to the user's own profile (gender, budget), or, if they opt out of profile-matching, matched to the prompt alone, with the AI inferring who the outfit is for and their implied budget straight from the text.
- A free-text prompt can also refine a single garment within an existing outfit.
- Any single garment in an outfit can be swapped for alternatives, manually or via prompt.
- Liked outfits can be virtually tried on, generating an image of the user wearing them.
- Outfits can be purchased whole or piece by piece (this feature is deliberately planned last, after the frontend exists).

## Tech stack

- **Backend**: Java 21+, Spring Boot 4.1+, Maven
- **Database**: PostgreSQL 18 with `pgvector` — native installation for local dev, [Aiven](https://aiven.io) (free tier) for hosting; nearest-neighbor candidate retrieval via Spring Data JPA's native vector-search support (`Vector`/`ScoringFunction`/`SearchResults`), backed by an HNSW index on `products.text_embedding`
- **Migrations**: Flyway
- **AI**: Ollama running Qwen3-VL 4B and Qwen3-Embedding-4B locally for one-time catalog enrichment/embedding (Qwen3-Embedding-4B MRL-truncated to 2000 dimensions); Ollama Cloud for structured extraction from AI-prompt requests (`gpt-oss:20b-cloud` by default); DeepInfra for query-time embeddings against the same open-weight embedding model the catalog already uses, so no catalog re-embed was needed. [FASHN AI](https://fashn.ai) for virtual try-on — two models, routed by garment type (`tryon-v1.6` for tops/bottoms/full-body, `tryon-max` for footwear/accessories), chained sequentially for a full outfit.
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
| 10 | Prompt-to-Outfit & Prompt-to-Garment-Refinement | ✅ Done |
| 11 | FASHN AI Integration & Async Job Pipeline | ✅ Done |
| 12 | Likes, Saves & Shares | ⬜ Not started |
| 13 | Logging, Security & API Documentation Audit | ⬜ Not started |
| 14 | Build & Deployment Pipeline | ⬜ Not started |
| 15+ | Frontend (Flutter) | ⬜ Not started |
| 16 | Orders & Checkout (post-frontend) | ⬜ Not started |

## Project structure

Packages are organized by feature, not by technical layer — `identity`, `catalog`, `outfit`, `feed`, `tryon`, `commerce`, plus `common` for genuinely cross-cutting code. This was a deliberate choice over the more common controller/service/repository top-level split, given the number of distinct feature areas in this project.

Within a feature package, subfolders are typed strictly by what they actually hold:

| Folder | Holds |
|---|---|
| `entity` | `@Entity` classes only |
| `enums` | Plain enums |
| `dto` | Records that actually cross the HTTP boundary — request and response bodies, nothing else |
| `domain` | Everything else structured-but-non-wire: value objects, AI-parsed result shapes, cross-feature query projections |
| `service` | `@Service` classes, plus the interface a `@Service` implements when that interface *is* the service's own contract |
| `support` | `@Component` beans that assist a service without being one — resolvers, hashers, guards, codecs |
| `repository` | Repository interfaces |
| `controller` | `@RestController` classes |
| `config` | `@Configuration` classes |
| `properties` | `@ConfigurationProperties` records — split out of `config` since a bean-declaring class and a properties record are different things |
| `pipeline` | `CommandLineRunner`/batch-job classes (established in `catalog`, reused wherever else it recurs) |

A feature only gets the subfolders it actually needs — `outfit` has no `@Configuration` class at all, so it has `properties` and no `config`.

`common`'s own submodules (`security`, `storage`, `taxonomy`, `ai`) follow the same typing once they've grown enough to mix layer-types; `ratelimit`, `logging`, and the un-split part of `persistence` haven't, and don't need it forced on them.

What belongs in `common` isn't decided by who currently calls it — it's decided by what the thing *is*. Connection-level infrastructure for a shared external resource (an AI provider's base URL, API key, HTTP client tuning) stays `common` even with one current caller, since any future feature needing that provider would reuse the same wiring rather than redeclare it; what a feature actually *does* with that access stays in the feature. `OllamaCloudConfig`/`DeepInfraEmbeddingConfig`/`FashnConfig` and their properties live in `common.ai.config`/`common.ai.properties` on that basis, even though each currently has only one calling feature (`outfit`, `outfit`, and `tryon` respectively).

Cross-feature reads go through a narrow query-facade service owned by the feature whose data it exposes (e.g. `catalog.service.ProductSearchService`, `identity.service.UserReferenceQueryService`) rather than one feature injecting another's repository directly.

## What's actually in place right now

- **Foundation**: correlation IDs on every log line, including on background thread pools; one polymorphic exception handler covering every custom exception plus the common Spring/DB failure cases (malformed input, missing params, constraint violations); PostgreSQL + `pgvector` (local and Aiven, switchable via env vars with no code changes) on versioned Flyway migrations; Cloudflare R2 for all user and generated media.
- **Identity**: register/login/refresh/logout with rotating, DB-backed refresh tokens (reuse of a revoked token kills every token for that user); profile CRUD; two body photos uploaded straight to R2 via presigned URLs, bytes never touching the backend; style-tag preferences on their own endpoint.
- **Catalog**: full dataset import with synthetic per-size stock; enrichment against a local vision model, one item at a time or as an unattended batch; local embeddings for every enriched product, truncated and renormalized to a fixed dimension; deterministic garment-role classification from article type.
- **Outfit generation**: a compatibility score blending structured color/layering rules with embedding similarity, persisted in full (not just the blended number); beam-search candidate assembly — not greedy — with a diversity cap so a handful of products can't dominate a batch; concurrency-safe persistence that reuses an identical existing outfit instead of duplicating it.
- **Feed**: `GET /api/v1/feed` — cursor-paginated, ranked by a bounded multiplier on the outfit's own compatibility score, permanent never-repeat backed by a real constraint, background refill that never blocks the request that triggered it.
- **Garment swap**: `GET .../alternatives` (browse) and `POST .../swap` (commit) — candidates matched on the target item's own article type and gender, budget-checked against the whole outfit's total, committed as a new immutable outfit.
- **AI-prompt generation & refinement**: a free-text prompt becomes a batch of up to 50 scored, deduplicated, diversity-capped outfits, each individually traceable back to the query that produced it; an optional profile-independent mode lets the AI infer gender and budget from the prompt text instead of the caller's profile; every attempt is logged, success or failure, with no silent fallback to a generic result. Single-garment refinement works the same way, scoped to one slot, and commits through the swap endpoint above. Both endpoints are rate-limited per user.
- **Virtual try-on**: `POST /api/v1/tryon` validates the outfit, returns a `PENDING` job immediately, and chains FASHN AI calls sequentially on a dedicated background thread pool — one garment at a time, in a fixed order (full-body/bottom/top/outerwear/footwear/accessory), each result feeding forward as the next call's input image. `GET /api/v1/tryon/{id}` polls for `PENDING`/`PROCESSING`/`COMPLETE`/`FAILED`; each garment's own status is tracked separately from the request's overall status, so a partial failure is distinguishable from a total one. The final composited image is downloaded from FASHN and stored in R2 like every other generated asset. Rate-limited per user, reusing the same limiter AI-prompt generation uses.
- **Cross-cutting**: narrow query-facade services for any read that crosses a feature boundary, instead of one feature reaching into another's repository directly.

## Getting started

Prerequisites: JDK 21+, Maven, PostgreSQL 17+ with the `pgvector` extension, [Ollama](https://ollama.com) with both `qwen3-vl:4b` and `qwen3-embedding:4b` pulled locally (`ollama pull qwen3-vl:4b` and `ollama pull qwen3-embedding:4b`), an [Ollama Cloud](https://ollama.com/cloud) API key, a [DeepInfra](https://deepinfra.com) API key, a [FASHN AI](https://fashn.ai) API key, IntelliJ IDEA (recommended).

1. Clone the repo.
2. Create a `.env.local` file at the project root (untracked) with your local Postgres connection details, Cloudflare R2 and JWT settings, the local Ollama/catalog batch variables (`OLLAMA_BASE_URL`, `OLLAMA_ENRICHMENT_MODEL`, `OLLAMA_EMBEDDING_MODEL`, `CATALOG_EMBEDDING_CHUNK_SIZE`, and the rest of the `catalog.*` batch settings), and the AI-provider variables (`OLLAMA_CLOUD_API_KEY`, optionally `OLLAMA_CLOUD_CHAT_MODEL` if you want something other than the `gpt-oss:20b-cloud` default, `DEEPINFRA_API_KEY`, and `FASHN_API_KEY`) — see `application.properties` for the full list of expected variables.
3. Make sure Ollama is running locally with both models pulled (check for it in the system tray, or launch it — it does not always survive a reboot reliably on Windows).
4. Run the app from IntelliJ, or `mvn spring-boot:run`.
5. Confirm it's up: `GET http://localhost:8080/actuator/health` should return `{"status":"UP"}`.