# FitCheck

AI-powered outfit discovery and virtual try-on. Master's thesis project — Spring Boot backend, Flutter frontend to follow.

## Overview

FitCheck recommends outfits assembled from a real product catalog, matched to a user's profile and budget, and lets them try any outfit on virtually before deciding whether they'd actually wear it. Recommendations come from two directions: an algorithmic feed that learns a user's taste over time, and a free-text prompt that generates outfits on demand — either matched to the user's own profile or, if they opt out, matched to the prompt alone with gender and budget inferred straight from the text (useful for "find something for my sister," not just "find something for me").

## Features

- **Profile & body photos** — measurements, style preferences, budget, two reference photos for virtual try-on.
- **Personalized feed** — infinite-scroll outfit recommendations, ranked by a blend of intrinsic outfit quality and personal fit.
- **AI-prompt generation** — a free-text prompt returns a batch of outfits, not just one; an optional profile-independent mode lets the AI infer who the outfit is for from the prompt itself.
- **Garment swap & refinement** — replace any single item in an outfit with an alternative, manually or via a follow-up prompt.
- **Virtual try-on** — generates an image of the user wearing a given outfit, powered by [FASHN AI](https://fashn.ai). Asynchronous: submit, then poll. Resubmitting the same outfit reuses the in-flight or already-rendered result instead of paying for a second render.
- **Likes, saves & shares** — save outfits for later, like them, share a direct link.
- **Purchase** — whole outfit or individual pieces (planned last, once the frontend exists — see Status).

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Java 21+, Spring Boot 4.1+, Maven |
| Database | PostgreSQL 18 + `pgvector` — native install locally, [Aiven](https://aiven.io) free tier for hosting; nearest-neighbor retrieval via Spring Data JPA's native vector search, backed by an HNSW index on `products.text_embedding` |
| Migrations | Flyway — `ddl-auto=validate`, so the schema is always migration-driven |
| Security | Spring Security resource server — HS256 JWTs with `iss`/`aud` validation, DB-backed rotating refresh tokens, per-IP and per-email rate limiting on the auth endpoints |
| AI — catalog | Ollama running Qwen3-VL 4B (enrichment) and Qwen3-Embedding-4B (embeddings) locally, one-time batch jobs |
| AI — prompts | Ollama Cloud for structured extraction (`gpt-oss:20b-cloud` by default); DeepInfra for query-time embeddings against the same open-weight model the catalog uses, so no catalog re-embed was needed |
| AI — try-on | [FASHN AI](https://fashn.ai) — `tryon-v1.6` for tops/bottoms/full-body, `tryon-max` for footwear/accessories |
| Storage | Cloudflare R2 (S3-compatible, zero egress) for user photos and generated try-on images |
| API docs | springdoc OpenAPI — `/swagger-ui.html`, importable into Postman from `/v3/api-docs` |
| Frontend | Flutter (not started — begins once the backend, minus Commerce, is complete) |
| Testing | JUnit 5 + Mockito (automated); `@DataJpaTest` repository tests need a real Postgres with `pgvector`; Postman/Swagger against a real Aiven database (manual) |
| Dev environment | Windows, IntelliJ IDEA |

## API overview

All endpoints are under `/api/v1` and JWT-authenticated (Bearer token) unless noted. Full request/response shapes: `/swagger-ui.html` once running.

| Area | Endpoint | Notes |
|---|---|---|
| Auth | `POST /auth/register`, `/login`, `/refresh`, `/logout` | Public. Refresh tokens rotate on every use. Rate-limited per IP and per email. |
| Profile | `GET`/`PUT /users/me/profile`, `PUT /users/me/style-preferences` | |
| Body photos | `POST /users/me/photos/upload-url`, `/confirm`, `GET /users/me/photos` | Presigned R2 upload — image bytes never touch the backend. |
| Catalog | `GET /style-tags` | |
| Catalog (admin) | `POST /admin/catalog/enrich-next` | `ADMIN` role only. |
| Feed | `GET /feed` | Cursor-paginated. |
| Outfits | `GET /outfits/{outfitId}` | Any authenticated user — outfits have no owner. |
| Outfits | `POST /outfits/prompt`, `POST /outfits/{outfitId}/items/{itemId}/refine` | Rate-limited per user. |
| Garment swap | `GET /outfits/{outfitId}/items/{itemId}/alternatives`, `POST .../swap` | Browse vs. commit. |
| Social | `POST`/`DELETE /outfits/{outfitId}/like`, `/save` | Idempotent toggles. |
| Social | `GET /outfits/saved`, `GET /outfits/interactions/mine?type=` | Paginated list vs. full ID set for client-side state reconciliation. |
| Try-on | `POST /tryon`, `GET /tryon/{requestId}` | Async — submit returns `PENDING` immediately; poll for the result. Ownership-scoped. Returns 400 for an outfit with no try-on-eligible items. |

Every response carries an `X-Correlation-Id` header, and every error body repeats it alongside the status, path and any per-field validation messages — quote it when reporting a problem, since it matches the prefix on the corresponding log lines.

## Project structure

Packages are organized by feature, not by technical layer — `identity`, `catalog`, `outfit`, `feed`, `social`, `tryon`, `commerce`, plus `common` for genuinely cross-cutting code. This was a deliberate choice over the more common controller/service/repository top-level split, given the number of distinct feature areas in this project.

Within a feature package, subfolders are typed strictly by what they actually hold:

| Folder | Holds |
|---|---|
| `entity` | `@Entity` classes only |
| `enums` | Plain enums |
| `dto` | Records that actually cross the HTTP boundary — request and response bodies, nothing else |
| `domain` | Everything else structured-but-non-wire: value objects, AI-parsed result shapes, cross-feature query projections |
| `service` | `@Service` classes, plus the interface a `@Service` implements when that interface *is* the service's own contract |
| `support` | `@Component` beans that assist a service without being one — resolvers, hashers, guards, codecs, assemblers, classifiers |
| `repository` | Repository interfaces |
| `controller` | `@RestController` classes |
| `config` | `@Configuration` classes |
| `properties` | `@ConfigurationProperties` records — split out of `config` since a bean-declaring class and a properties record are different things |
| `pipeline` | `CommandLineRunner` and scheduled batch-job classes (established in `catalog`, reused wherever else it recurs) |
| `filter` | Servlet filters |
| `annotation` | Meta-annotations |
| `openapi` | springdoc configuration and the annotations built on it |

A feature only gets the subfolders it actually needs — `outfit` has no `@Configuration` class at all, so it has `properties` and no `config`.

`common`'s own submodules (`security`, `storage`, `taxonomy`, `ai`, `logging`, `exception`, `openapi`) follow the same typing once they've grown enough to mix layer-types; `ratelimit` and the un-split part of `persistence` haven't, and don't need it forced on them.

What belongs in `common` isn't decided by who currently calls it — it's decided by what the thing *is*. Connection-level infrastructure for a shared external resource (an AI provider's base URL, API key, HTTP client tuning) stays `common` even with one current caller, since any future feature needing that provider would reuse the same wiring rather than redeclare it; what a feature actually *does* with that access stays in the feature. `OllamaCloudConfig`, `DeepInfraEmbeddingConfig`, and `FashnConfig` (plus their properties) all live in `common.ai.config`/`common.ai.properties` on that basis, even though each currently has exactly one caller.

Cross-feature reads go through a narrow, feature-owned query facade (e.g. `catalog.service.ProductSearchService`, `identity.service.UserReferenceQueryService`) rather than one feature injecting another's repository directly.

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
| 12 | Likes, Saves & Shares | ✅ Done |
| 13 | Logging, Security & API Documentation Audit | ✅ Done |
| 14 | Build & Deployment Pipeline | ⬜ Not started |
| 15+ | Frontend (Flutter) | ⬜ Not started |
| 16 | Orders & Checkout (post-frontend) | ⬜ Not started |

The application runs as a single instance by design — rate limiting, the feed refill guard and the try-on stale-job sweeper are all in-memory, traded deliberately against having no Redis dependency.

## Getting started

**Prerequisites:** JDK 21+, Maven, PostgreSQL 17+ with `pgvector`, [Ollama](https://ollama.com) with `qwen3-vl:4b` and `qwen3-embedding:4b` pulled locally, an [Ollama Cloud](https://ollama.com/cloud) API key, a [DeepInfra](https://deepinfra.com) API key, a [FASHN AI](https://fashn.ai) API key, IntelliJ IDEA (recommended).

1. Clone the repo.
2. Create an untracked `.env.local` at the project root with your local Postgres connection details, Cloudflare R2 and JWT settings, the local Ollama/catalog batch variables, and the AI-provider keys (`OLLAMA_CLOUD_API_KEY`, `DEEPINFRA_API_KEY`, `FASHN_API_KEY`) — see `application.properties` for the full list. `JWT_SECRET` must be at least 32 characters, or the application refuses to start. `CORS_ALLOWED_ORIGINS` is empty by default and only needs setting for a browser-based client.
3. Make sure your database user can `CREATE EXTENSION` — Flyway's `V0` enables `pgvector`. Everything else in the schema is created automatically on first boot.
4. Make sure Ollama is running locally with both models pulled (only needed for the one-time catalog load, enrichment and embedding passes — the running application never calls local Ollama).
5. Run from IntelliJ, or `mvn spring-boot:run`.
6. Confirm it's up: `GET http://localhost:8080/actuator/health` → `{"status":"UP"}`. Everything else under `/actuator/**` requires the `ADMIN` role.