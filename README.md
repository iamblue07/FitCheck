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
- **Database**: PostgreSQL 18 with `pgvector` — native install for local dev, [Aiven](https://aiven.io) (free tier) for hosting
- **Migrations**: Flyway
- **AI**: OpenAI (catalog enrichment, embeddings, prompt-driven outfit generation), [FASHN AI](https://fashn.ai) (virtual try-on)
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
| 6 | Catalog Data Pipeline | ✅ Done |
| 7 | Compatibility Scoring & Candidate Generation | ⬜ Not started |
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
- Base JPA entity conventions (`BaseEntity` / `AuditableEntity`) covering id generation and audit timestamps, ready for the first real entities in Chapter 4.
- Cloudflare R2 Integration for media files.
- Authentication & Authorization.
- Profile configuration
- Catalog pipelines for enrichment using Ollama locally and embeddings using OpenAi API.
 



## Project structure

Packages are organized by feature, not by technical layer — `identity`, `catalog`, `outfit`, `feed`, `tryon`, `commerce`, plus `common` for cross-cutting code. Each feature package holds its own controller/DTO/entity/repository/service classes internally. This was a deliberate choice over the more common controller/service/repository top-level split, given the number of distinct feature areas in this project.

## Getting started

Prerequisites: JDK 21+, Maven, PostgreSQL 17 with the `pgvector` extension, IntelliJ IDEA (recommended).

1. Clone the repo.
2. Create a `.env.local` file at the project root (untracked) with your local Postgres connection details.
3. Run the app from IntelliJ, or `mvn spring-boot:run`.
4. Confirm it's up: `GET http://localhost:8080/actuator/health` should return `{"status":"UP"}`.

## Documentation

Documentation TBD at a later date
