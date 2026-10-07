# GFSSystem

Academic management system for Gradjevinski Fakultet Subotica (GFS).

## Tech Stack

- **Java 17** with **Spring Boot 3.3.4**
- **Spring Data JPA** with **MySQL** database
- **Lombok** for boilerplate reduction
- **MapStruct** and **ModelMapper** for DTO mapping
- **Spring Validation** for input validation
- **Spring AOP** for cross-cutting concerns

## Project Structure

```
src/main/java/tri/novica/gfssystem/
├── advice/          # Exception handlers (ApiExceptionHandler)
├── config/          # Configuration classes (JacksonConfigs)
├── dto/             # Data Transfer Objects
│   ├── aktivnost/   # Activity DTOs
│   ├── domaci/      # Homework DTOs
│   ├── grupa/       # Group DTOs
│   ├── ocenjivanje/ # Grading DTOs
│   ├── onboarding/  # Student onboarding DTOs (sessions, submissions, public form)
│   ├── predavanje/  # Lecture DTOs
│   ├── predmet/     # Subject DTOs
│   ├── student/     # Student DTOs (includes pregled/ subdirectory)
│   └── test/        # Test/Exam DTOs (includes tip/ subdirectory)
├── entity/          # JPA Entities
│   ├── converter/   # JPA converters
│   └── view/        # Database views
├── exceptions/      # Custom exceptions
├── repository/      # Spring Data repositories
├── rest/            # REST controllers
├── security/        # Security config (CORS)
├── service/         # Business logic services
├── utility/         # Utility classes (Mapper, Sorting)
└── validation/      # Custom validators
```

## Domain Entities

- **Predmet** - Subject/Course
- **Grupa** - Student group
- **Student** - Student
- **Predavanje** - Lecture
- **Aktivnost** - Student activity during lecture
- **Domaci** - Homework assignment
- **UradjenDomaci** - Completed homework
- **Test** - Exam/Test
- **TipTesta** - Test type
- **Polaganje** - Test attempt/result
- **TestGrupa** - Test group association
- **OnboardingSesija** - Onboarding session of a group (`onboarding_sesije`): public token, active flag, expiry, max submissions
- **Prijava** - A student's submission in a session (`prijave`), status `StatusPrijave` (`NA_CEKANJU`, `PRIHVACENA`, `ODBIJENA`)

## Onboarding

A teacher opens a session for a group (`POST /grupe/{id}/onboarding`, link/QR with a 32-char token), students submit
the public form, the teacher accepts or rejects (`OnboardingRest`, `/onboarding/{id}/...`; accepting creates the
`Student` in the session's group). Logic lives in `OnboardingService`, field rules in `validation/PrijavaPP`.
- **Public API is only `/public/upis/{token}`** (`PublicUpisRest`: `GET` info, `POST` submit; 404 unknown token,
  410 closed). It has no auth: the frontend nginx serves it as `/api/public/upis/...` and the host nginx exempts only
  `/api/public/` from basic-auth. Never add anything else under `/public/` that is not meant for the internet.
- **Only new students:** dedupe by (normalized index, enrollment year) against `studenti`, against pending submissions in
  the same session, on edit and on accept. `IndeksUtil.normalizuj` strips whitespace and upper-cases (`"gd 1"` -> `GD1`);
  `POST /studenti` uses the same normalization and duplicate check.
- **Concurrency:** every change to a session (submit, accept, reject, accept-all, edit, `PATCH`) first locks the session
  row (`findByTokenForUpdate` / `findByIdForUpdate`, `PESSIMISTIC_WRITE`) inside the same `@Transactional` method.
- **Time** comes from the `Clock` bean in `GfsSystemApplication`; use `LocalDateTime.now(clock)`, tests use `Clock.fixed`.
- **Errors never leak:** `ApiExceptionHandler` maps unreadable JSON to 400 "Neispravan format podataka.", a bad path
  parameter to 400, Spring's own 404/405/415 to their status, and anything unexpected to 500 "Sistemska greška." (the
  exception text goes only to the log). The client IP for the log is the first `X-Forwarded-For` element (log only).
- Tests: `OnboardingServiceTest` (Mockito) and `rest/PublicUpisRestTest` (`@WebMvcTest`, mocked service) run without a DB.

## Build & Run

```bash
# Build
./mvnw clean package

# Run
./mvnw spring-boot:run
```

### Docker

```bash
docker build -t gfs-backend .
```

Run the container with `SPRING_PROFILES_ACTIVE=server`. That profile expects the database at `shared-mysql:3306/gf` (user `gfs`) and takes the password only from the environment variable `SPRING_DATASOURCE_PASSWORD`. The container listens on 8080.

## Branching and CI

- Flow: `feature/* -> staging -> master`. PRs target `staging` by default; a release is a PR `staging -> master` (opened by
  Novica or on request). Never push directly to `master`; a direct push to `staging` is fine for quick experiments.
  `master` is protected: PR plus green check `build` required. This repo is public, so no secrets or real data in it.
- Staging: every push to `staging` is deployed automatically (about a minute) to `https://gfs.dev.trif.rs`
  (basic-auth, synthetic data only). The result shows up as commit status `staging-deploy`. Deploy details live in the
  wrapper repo `Novke/GFS-deploy` (`README.md`).
- CI: `.github/workflows/ci.yml`, job `build`, on PR and push to `staging`/`master`: temurin 17, service `mysql:8.0`
  (empty root password, DB `gftest`), `./mvnw -B package` (runs the `contextLoads` test), then `docker build`.
  To reproduce locally run MySQL on `localhost:3306` with DB `gftest` and root without a password, then `./mvnw -B package`.
  On novica-dev port 3306 is taken by `shared-mysql`, so there use `./mvnw -B -DskipTests package` and rely on CI for the test.

## Database

MySQL database `gf` on localhost:3306. Schema is auto-updated via `hibernate.ddl-auto=update`.

## Frontend

Angular frontend expected at `http://localhost:4200` (CORS configured).

## Conventions

- DTOs use suffixes: `Info` (list view), `Details` (full view), `Cmd` (commands/inputs)
- Repositories extend Spring Data JPA interfaces
- Services contain business logic, REST controllers are thin
- Serbian language used in domain naming (Predmet, Grupa, Domaci, etc.)
