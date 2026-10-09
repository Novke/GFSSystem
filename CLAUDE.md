# GFSSystem

Academic management system for Gradjevinski Fakultet Subotica (GFS).

## Tech Stack

- **Java 21** with **Spring Boot 4.1.1**
- **Jackson 3** (Boot 4 default) with `spring.jackson.use-jackson2-defaults=true`: keeps the Boot 3 / Jackson 2 JSON
  behavior the frontend and tests rely on (null into a primitive field gives 0, trailing text after the JSON is ignored,
  Jackson 2 property order). Without it a body like `{"x": null}` for an `int` would be a 400 instead of 0.
- **Spring Data JPA** with **MySQL** database, schema owned by **Flyway**
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
  410 closed, 406 for an unacceptable `Accept` before the service runs). It has no auth: the frontend nginx serves it
  as `/api/public/upis/...`, and the only API prefix the host nginx exempts from basic-auth is `/api/public/` (its
  public-path regex also exempts the SPA route `/upis/`, assets and hashed bundles, none of which reach the backend).
  Any new mapping under `/public/**` is therefore unauthenticated on the internet automatically: put only
  student-facing endpoints there.
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
- CI: `.github/workflows/ci.yml`, job `build`, on PR and push to `staging`/`master`: temurin 21, service `mysql:8.0`
  (empty root password, DB `gftest`), `./mvnw -B package` (all tests, including the `*IT` classes against real MySQL
  with the Flyway schema), then `docker build`.
- **Local tests on novica-dev** (3306 is `shared-mysql`, never use it for tests): a throwaway MySQL on 3307,
  `docker run -d --name gfs-redizajn-mysql -p 127.0.0.1:3307:3306 -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -e MYSQL_DATABASE=gftest mysql:8.0`,
  then `SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/gftest ./mvnw -B package` (runs every test, ITs included). If a
  migration changed (checksum mismatch), drop and recreate `gftest` first. The ITs are `@SpringBootTest @Transactional`
  and roll back, so they keep the DB clean; they only assert on rows they insert themselves.

## Database

MySQL database `gf` on localhost:3306. The schema is owned by **Flyway** (`src/main/resources/db/migration`), Hibernate only
checks it (`ddl-auto=validate`), so an entity change needs a new migration `V<n>__opis.sql`; never edit a migration that
is applied to a shared DB (prod `gf`, staging `gf_staging`).
- `V1` = schema of prod `gf` before onboarding (the baseline: an existing DB without `flyway_schema_history` gets a
  baseline at 1 via `baseline-on-migrate`, a fresh DB runs it). `V2` = onboarding, `V3` = search/sort indexes, `V4` = `beleske`
  (teacher notes). **V2-V4 and V6 are idempotent** (`CREATE TABLE IF NOT EXISTS`; columns and indexes guarded by
  `information_schema` + `PREPARE`/`EXECUTE`, pattern in `V2__onboarding.sql`).
- **Rule: every new migration must be idempotent.** Staging `reset-db.sh` rebuilds `gf_staging` from the prod schema dump
  and a DB that already has the objects but no Flyway history must still migrate (no "Duplicate key name"/"already exists").
- **Versions:** `V5` is a deliberate permanent gap (never reuse it), `V6` = `prag_prolaza` (nullable `testovi.prag_prolaza`),
  `V7` = the parallel "uživo" (live) project (it renumbers to V7 and merges after this branch). **Next free version: `V8`.**
- `sql/views.sql` is historical, the view lives in `V1`.
- `scripts/flyway-provera.sh` checks the migrations against four starting states: empty DB, `gf` and `gf_staging` schema dumps
  (`/data/tmp/redizajn-schema`) and `vec_migrirana` (staging schema with V3/V4/V6 objects already applied, no history). Run
  `./mvnw -B -DskipTests package` first; it uses the test MySQL on 3307.

## List and overview API conventions

New list endpoints follow one pattern (see `PredavanjeService.pretraga` as the reference):
- **Paging:** `Pageable` from Spring Data web, defaults/limits in `application.properties` (25 per page, max 100,
  `spring.data.web.pageable.serialization-mode=via-dto`, so a page is JSON `{content, page: {size, number, totalElements,
  totalPages}}` as `PagedModel`). Do **not** add `@EnableSpringDataWebSupport`: it switches off Boot's
  `DataWebAutoConfiguration` and with it these properties. The REST layer calls `PageableUtil.proveri(pageable, <SORT_POLJA>,
  <PODRAZUMEVANI_SORT>)` (whitelisted sort fields, `id desc` tiebreaker, 400 `Neispravan parametar: sort.`) before the service.
- **Filters:** one `*Filter` record per list (query params bound by Spring), turned into JPA `Specification`s in
  `repository/spec/*Specs` (`allOf` of small specs, each null-safe); text search and `LIKE` escaping via `SpecUtil.likeObrazac`.
  The school year filter `godina=Y` means 1 Oct Y - 30 Sep Y+1 (`SkolskaGodina`, also used with the `Clock` bean for "current").
- **Rows:** `*ListItem` DTOs (plain classes, mapped by hand because ModelMapper is STRICT), nested `*Ref` for small references
  (`PredmetInfo`, `GrupaInfo`, `DomaciPredavanjeRef`); overview/dashboard DTOs in `dto/pregled` are Java **records**.
- **Counters without N+1:** per-row counts (attendees, students in group, done homework) come from one aggregate query per
  page returning `[id, count...]` rows, turned into a map by `Brojaci.poId` / `Brojaci.mapa(redovi, kolona)`; an empty id set
  never hits the DB. Example: `AktivnostRepository.brojPrisutnihPoPredavanju` returns `brojPrisutnih` and
  `brojStarijihPrisutnih` (attendees outside the lecture's group, for "31/38 +3") in a single pass.

### Endpoints added by the UI redesign

- Lists: `GET /predavanja/pretraga`, `/domaci/pretraga`, `/test/pretraga`, `/studenti/pretraga` (filters, paging, sort as above).
- Dashboard and search: `GET /pregled/kontrolna-tabla` (next lecture, today's, "waiting for you" with lists capped at 10 plus
  totals `brojTestova`/`brojDomacih`/`brojPrijava`/`brojNezavrsenih`, this week's agenda), `GET /pretraga?q=` (global search, min 2 chars).
- Groups: `GET /grupe/{id}/pregled` (per-student stats for a subject), `GET /grupe/{id}/prisustvo` (attendance matrix).
- Students: `GET /studenti/{id}/predmeti` and `/studenti/{studentId}/predmet/{predmetId}` (per-subject student card);
  notes `GET|POST /studenti/{studentId}/beleske`, `PUT|DELETE /beleske/{id}` (Flyway `V4`).
- Homework: `POST /domaci/evidentiraj`, `POST /domaci/{id}/oslobodi`.
- Pass rate: optional per-test `pragProlaza` (points, `testovi.prag_prolaza`, validated 0..maxPoena): set on create (`POST /test`) and changed
  afterwards only by `PATCH /test/{id}/prag-prolaza` with `{"pragProlaza": n|null}` (null clears; works on finished tests too). `PUT /test/{id}`
  ignores it (it rejects finished tests and must not erase a threshold).
  No threshold = no pass concept (`procenatProlaznosti`, `brojPolozenih`, `brojPalih` are null). Rule in `utility/Prolaz` (poeni >= prag, not
  `prepisivao`; stored `polozio` is ignored); `PolaganjeRepository.statistikaPoTestu` mirrors it in JPQL, keep them in sync.

## Frontend

Angular frontend expected at `http://localhost:4200` (CORS configured).

## Conventions

- DTOs use suffixes: `Info` (list view), `Details` (full view), `Cmd` (commands/inputs)
- Repositories extend Spring Data JPA interfaces
- Services contain business logic, REST controllers are thin
- Serbian language used in domain naming (Predmet, Grupa, Domaci, etc.)
