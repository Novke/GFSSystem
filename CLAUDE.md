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
├── config/          # Configuration (JacksonConfigs, uživo WebSocket/STOMP and scheduling)
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
- **WhatsApp group link:** `grupe.wa_link` (Flyway `V9`), set by the teacher with `PATCH /grupe/{id}/wa-link`
  (`{"waLink": "<link>"|null}`, key required and unknown fields 400 like `PragProlazaCmd`; null/blank removes it). `utility/WaLink`
  accepts what WhatsApp shares (with or without scheme, `www.`, `invite/`, a query) and stores only
  `https://chat.whatsapp.com/<10-40 alnum>`; anything else is 400, because the link is shown on the public form. The public
  `POST /public/upis/{token}` returns it in `PodnetaPrijavaInfo.waLink` (after a successful submit only, never in `GET`), and
  `GrupaPregledInfo.waLink` carries it to the group page.
- **Contacts for the phone:** `GET /grupe/{id}/kontakti.vcf` (behind basic-auth, never under `/public`): one vCard 3.0 file
  (`utility/VCard`, UTF-8, CRLF, RFC 2426 escaping) with every student of the group that has a phone or an email, ordered by
  index; numbers normalized to `+381...` (`utility/Telefon`), the group name as the name suffix and `CATEGORIES`
  ("Ana Petrović GD-2026"), `Content-Disposition: attachment` with an ASCII file name, `Cache-Control: no-store`.
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

## Uživo (live interactive presentation)

Kahoot-like lecture: the teacher builds a presentation (INFO and question slides), starts a run (`izvodjenje`, 6-digit
code), students join from their phones and answer live. Code lives in `entity/uzivo`, `repository/uzivo`, `service/uzivo`,
`rest/uzivo`, `dto/uzivo` and the WebSocket part in `config/`; schema in Flyway `V7__uzivo.sql` (idempotent; originally V5, renumbered after the redesign's V6).
- **Teacher REST** (outside `/public`, behind basic-auth on staging/prod): `/prezentacije`, `/slajdovi`, `/mediji`,
  `/izvodjenja` (commands `POST /izvodjenja/{id}/komande`, moderation, results).
- `NastavnickoStanje.pocetakMs` (epoch ms, zona `Clock`-a) je osnova štoperice u konzoli i daljinskom; vremena u stanju su uvek epoch ms.
- **PLATNO slide (slide builder, phase 1; `V8__platno.sql`):** `TipSlajda.PLATNO` holds a canvas document in `slajdovi.platno`
  (JSON, <= 1 MB): `dto/uzivo/platno/*` (records `Platno`, `Sloj` = TEKST|SLIKA|OBLIK|TABELA|FORMULA|CRTEZ, `Animacija`, ...).
  `service/uzivo/PlatnoJson` is the strict parser (unknown field, wrong subtype, trailing tokens or a string instead of a number
  or a number/boolean/ordinal where a string/enum is expected = 400; floats are NOT accepted as ints; `izKolone` reading the stored column is lenient about unknown fields so a rolled-back image still reads newer data). `validation/PlatnoPP.normalizuj` fills defaults and rounds numbers to 2 decimals, then
  `proveri` enforces limits (300 layers, animation targets must exist, font from the catalog
  `PlatnoPP.FONTOVI`); called from `SlajdPP` / `PrezentacijaService`; existence of every `medijId` (background, image, drawing, formula `slikaIzvora`) is checked by `PrezentacijaService`, not by `PlatnoPP.proveri` (which only collects them in `medijIds`). `SlajdCmd.platno` / `SlajdDetails.platno`: a `PUT` of a PLATNO slide must send `platno` back
  (a `PUT` without it is 400, the server does not keep the old one; an old-flow command on an INFO slide clears it). `SlajdMapper.uDetailsZaIzvodjenje`
  gives the run snapshot without the drawing strokes (`potezi`). Notes limit is 20 000 chars.
- **Drawing strokes for the projector** (phase 5): `GET /slajdovi/{id}/potezi` (`SlajdRest`, behind basic-auth, never under
  `/public/**`) returns `PoteziSloja{slojId, medijId, sirina, visina, potezi}` for every CRTEZ layer in layer order (empty
  list for INFO, PITANJE and PLATNO without drawings; 404 `Slajd nije pronađen.`). Read-only, takes no lock
  (`PrezentacijaService.poteziSlajda`). It exists because `SlajdMapper.uDetailsZaIzvodjenje` strips `potezi` from the run
  snapshot (up to 4 snapshots/s with the current and next slide, a drawing may have 20 000 points), so the audience window
  and the console fetch strokes once per slide; `sirina`/`visina` is the layer size the points belong to (the front scales
  them to the size in the snapshot). The snapshot stays without strokes.
- **Slide transition** (phase 5; `V10__prelaz_slajda.sql`, the plan said V9 but V9 was already `grupe.wa_link`):
  `Slajd.prelaz` is an `@Embedded PrelazSlajda` (nullable columns `slajdovi.prelaz_efekat`, `prelaz_smer`, `prelaz_ms`;
  all null = no transition, Hibernate then returns null) and belongs to the slide, not to the canvas document, so it works
  for every slide type (INFO, PITANJE, PLATNO). DTO `Prelaz(TipPrelaza efekat, SmerPrelaza smer, Integer trajanjeMs)` is
  the last component of `SlajdCmd` and `SlajdDetails` (and so of `NastavnickoStanje.trenutniSlajd`/`sledeciSlajd`);
  `null` = no transition. `TipPrelaza` FADE|GURANJE|BRISANJE, `SmerPrelaza` LEVO|DESNO|GORE|DOLE (the side the new slide
  comes from). `SlajdPP.normalizuj(Prelaz)`: no `efekat` -> whole transition null; FADE -> `smer` null; GURANJE/BRISANJE
  without `smer` -> DESNO; no `trajanjeMs` -> 500. `SlajdPP.proveri`: `trajanjeMs` outside 100..5000 -> 400
  `Trajanje prelaza mora biti od 0,1 do 5 sekundi.` (checked before the type branches, also for PLATNO). Duplicating a
  slide or a presentation copies it, import keeps it. A `PUT` without `prelaz` clears it (full replacement, like
  `platno`), so the old frontend, which does not send it, removes a transition on save (it also never shows one). The
  server only stores it; the frontend plays it (audience window only, one slide forward).
- **Formulas are not interpreted on the server:** the round snapshot (`PitanjeSnimak.od`) copies question and option
  texts with `$...$` (and `\$`) verbatim, also through the JSON round trip (`PitanjeSnimakTest.snimakCuvaFormuleDoslovno`);
  there is no LaTeX check on the server, the front renders them.
- **Import of many slides** (PowerPoint import, phase 2): `POST /prezentacije/{id}/slajdovi/uvoz` (`UvozSlajdovaCmd{posle, slajdovi}`, 1-200 slides, every slide validated before the lock, error `Slajd N: ...`, one transaction, 201 with the whole `PrezentacijaDetails`). `config/UvozConfig` answers 413 for `Content-Length` > 10 MB on that path (Spring/Tomcat have no JSON body limit; chunked requests are stopped by the frontend nginx `client_max_body_size 11m`). `FormatBroja.ARAPSKI` = `1 2 3` without punctuation (`arabicPlain`).
- **Steps:** `TokIzvodjenja.brojKoraka` = gradual INFO list items, or for PLATNO the number of animations with trigger KLIK;
  `NastavnickoStanje.brojStavki` is that step count (name kept for the old frontend). If an edit during a run lowers the
  count, the step is clamped and the phase stays null (no 500).
- **Theme:** `prezentacije.tema` (`Tema` NACRT|BELA|PAPIR|TAMNA|PONOC, default NACRT) is carried in the states (`tema`).
- **Public entry points, exactly these three:** `/public/uzivo/**` (`PublicUzivoRest`: info, `ja`, join with the
  `gfs_uzivo` cookie, HttpOnly, 32-char token, only its SHA-256 in the DB), `/public/mediji/{uuid}` (slide images) and the
  student WebSocket `/public/ws`. Nothing else uzivo-related may go under `/public/**` (nginx exempts `/api/public/` from auth).
- **WebSocket (STOMP, plain WS, no SockJS)**, `WebSocketConfig`: `/ws` teacher, `/public/ws` student; allowed origins =
  `gfs.front.url` (same list as REST CORS); message size 8 KB, heartbeat 10 s / 10 s, simple broker `/topic` + `/queue`,
  app prefix `/app`, user prefix `/user`. The role is bound to the endpoint, never derived from the path text: `/ws` is
  registered with `UzivoHandshakeInterceptor.nastavnik()`, `/public/ws` with `.student(...)`, which needs a valid cookie
  (participant exists, not kicked, run AKTIVNO), otherwise HTTP 403; principal `u-<ucesnikId>` / `n-<uuid>`
  (`UzivoHandshakeHandler`).
- **Authorization** (`UzivoChannelInterceptor`, exact regexes, never `startsWith`): a student (run X) may SUBSCRIBE only
  `/topic/izvodjenja/X/javno`, `/user/queue/licno`, `/user/queue/greske`, `/app/izvodjenja/X/pocetno` and SEND only
  `/app/izvodjenja/X/odgovor`; a teacher may SUBSCRIBE `/topic/izvodjenja/{id}/nastavnik|javno` and
  `/app/izvodjenja/{id}/nastavnik-pocetno` and SEND nothing. Anything else -> ERROR frame and the connection closes.
  Every student frame (CONNECT, SUBSCRIBE, UNSUBSCRIBE, SEND, heartbeat, client DISCONNECT) takes a token from one
  bucket per participant, shared by all their sessions (burst 15, 5/s). Over the limit, SEND and heartbeats are dropped
  silently (as are SENDs of kicked participants, `IzbaceniRegistar`), while CONNECT/SUBSCRIBE/UNSUBSCRIBE get an ERROR
  frame and the connection closes, so the client reconnects instead of sitting without its initial state. DISCONNECT is
  never dropped and never refills; Spring's synthetic DISCONNECT at the end of a connection (no `simpHeartbeat` header)
  is free, so a reconnect costs 5 tokens. A bucket is removed only when full. Do not enable `setPreserveReceiveOrder`:
  Spring then only logs interceptor exceptions (no ERROR frame, connection stays open).
- **Student caps** (same interceptor, tracked from CONNECT until `SessionDisconnectEvent`): per session each destination
  (and each subscription id) at most once and at most 6 subscriptions (the four allowed destinations fit; a duplicate is
  an ERROR); per participant at most 3 active sessions (tabs/phones; a 4th CONNECT gets ERROR `Previše otvorenih veza.`,
  a stale session frees its slot once the server notices the dead socket via heartbeat). Inbound frames run on a pool,
  so a `SessionDisconnectEvent` can arrive before that session's CONNECT/SUBSCRIBE: ended session ids are remembered for
  5 minutes, late frames of an ended session are rejected and reserve nothing, and `pocisti()` (every 60 s) drops any
  slot or subscription still held by an ended session.
- **Lock order (invariant):** presentation row -> izvodjenje row(s) -> slides/rounds. `PrezentacijaService` locks the
  presentation and, through `PrezentacijaPromene.zakljucaj` (implemented by `UzivoPrezentacijaPromene` ->
  `IzvodjenjeService.zakljucajAktivna`), the active runs in ascending id **before** any slide write, so a slide edit
  and a teacher command (which locks only the run) never take the two rows in opposite order. `IzvodjenjeService.obrisi`
  also locks the presentation first. Anything new that touches both must follow the same order.
- **Runs without saving** (`cuvanje=false`): `ZAVRSI` (and the 12 h auto-end) deletes the run's answers, rounds and
  participants; only the `izvodjenja` row stays (no results view). With saving nothing is deleted.
- **Media**: files under `gfs.mediji.dir` (env `GFS_MEDIJI_DIR`; in the container a volume), metadata in `mediji`. The
  default `${java.io.tmpdir}/gfs-mediji` only suits dev; `MedijService` logs a WARN at startup when it is in use.
  `mediji.vrsta` (`VrstaMedija` `SLIKA` | `PRILOG_SLIKA` | `PRILOG_PDF` | `RENDER_SVG`, V11, default `SLIKA`, `varchar(20)` so a new kind needs no
  migration) tells slide images from the bank's attachments and rendered SVGs: the public `GET /public/mediji/{uuid}` serves only `SLIKA` and
  `RENDER_SVG` and answers 404 for anything else (see "Banka zadataka"); slide and question images (`slikaId`) and PLATNO layers accept only
  `SLIKA` (400 `Medij nije slika.` otherwise, checked before the lock with one query for the PLATNO ids), so a re-render never deletes a file a slide uses.
  `MedijService.obrisi` inside a transaction deletes the file only **after commit** (a failed commit keeps row and file together), and the file of
  a medium saved in a transaction that rolls back is deleted (no orphan files); outside a transaction both happen at once.
- **"Ni ranije"** (`StanjeService`): phones get `JavnoStanje`; the projector renders `NastavnickoStanje.javniRezultat`
  and `javnaRangLista`, built by the same methods. The only difference: phones get option texts in the result only when
  text is allowed (PITANJE mode or Detalji); the projector always has them.
- **Publishing** (`UzivoObjavljivac`): event listeners run after commit on the request/STOMP thread and only mark the
  run dirty (never read the DB or wait for a lock there). Reading and sending happen on the publisher thread under a
  per-run lock, one `StanjeService.snimci` read per run: a command, timer, moderation or kick (`IzvodjenjePromenjeno`,
  `UcesnikIzbacen`) triggers it at once on `taskScheduler`; joins (`UcesnikPrijavljen`), answers and connect/disconnect
  wait for the 250 ms `flush` (teacher topic at most 4x/s, a 300-join burst is one read per flush). Personal state does
  not bump `verzija`. `UzivoSchedulingConfig.taskScheduler` has 4 threads (flush, immediate publishing, deadlines,
  maintenance).
- Tests: `config/UzivoChannelInterceptorTest`, `service/uzivo/UzivoObjavljivacTest` (no Spring) and `UzivoWebSocketIT`
  (real STOMP client against `RANDOM_PORT` and MySQL; commits and cleans up its own data). The uživo `*DbTest` classes
  (`IzvodjenjeServiceDbTest`, `PrezentacijaServiceDbTest`, `UcesnikOdgovorDbTest`: locking, cascades, constraints) and the
  IT need a real MySQL (`SPRING_DATASOURCE_URL`, e.g. `jdbc:mysql://localhost:3307/gftest` on novica-dev (throwaway `gfs-redizajn-mysql`, never `shared-mysql`)); the rest are
  plain unit tests.

## Banka zadataka (faza 1)

Per-subject bank of tasks for lectures and tests: a tree of groups, variants of a task (LaTeX text and solution rendered to SVG
for the phone) and the schema of the answers the student types. Phase 1 is the bank itself (REST, no student-facing part, no
assignment to lectures yet: `koriscenoNaCasu` is always `false`, phase 2 fills it from `zadatak_dodele`). Spec and plan:
`docs/superpowers/specs/2026-10-17-banka-zadataka-design.md` and `.../plans/2026-10-17-banka-zadataka.md` in the wrapper repo.

- **Packages:** `entity|repository|dto|rest/banka/` (`GrupaZadataka`, `Varijanta`, `VarijantaPrilog`; `GrupaZadatakaRest`, `VarijantaRest`,
  `PrilogRest`, `PreambulaRest`, `RenderujSveRest`, `BankaPretragaRest`), `service/banka/` (below), `validation/BankaPP`, and the render container
  `latex-render/` at the repo root. `Polje`, `TipPolja`, `OdgovorPolja` live in `dto/banka` (`OdstupanjeTip` is reused from `entity/uzivo`).
  Service classes: `GrupaZadatakaService` (tree), `VarijantaService` (variants, statuses, render flow, attachments, whole-bank render),
  `LatexPreambulaService`, `BankaPretragaService`, and the pure helpers `PoljaJson`, `OcenaPolja`, `LatexDokument`, `LatexPravila`,
  `LatexGreska`, `RenderVarijante` (+ the HTTP client `LatexRender`, `RenderNedostupan`).
- **Tree (`grupe_zadataka`):** one tree per subject **and** `Namena` (`PREDAVANJE` | `TEST`); the root chooses the `namena`, subgroups inherit
  subject and `namena` from the parent (a move stays inside the same subject and `namena`). Depth <= 6 (root = 1), name unique among siblings
  (trim, case-insensitive), `rb` of siblings is 1..n (renumbered on delete, move, reorder). A node is **either a leaf (has variants) or an
  inner node (has subgroups)**: a subgroup under a group with variants and a variant in a group with subgroups are both 409. `tezina` 1-5 is
  `null` for inner nodes and **required for a leaf with variants** (400 on `PUT /banka/grupe/{id}` of a group that has variants, 409 when creating
  a variant in a group without it). A group is deleted only when it has no subgroups (409) and was never used on a lecture (409, phase 2);
  its variants (with attachment rows) and render SVGs go with it, the attachment media stay. `GET /banka/stablo?predmetId&namena` returns the
  whole tree in two queries (`GrupaCvor`: `list`, `brojVarijanti`, `brojAktivnih`, `brojNacrta`, `brojGresaka`; an inner node = sum of its subtree).
- **Variant (`varijante`):** `tekst` (required, <= 20 000 chars) and `resenje` (optional, <= 20 000) are LaTeX **bodies** (no `\documentclass`,
  `\usepackage`, `\begin{document}`), `naziv` <= 120, `odgovori` (JSON, see below), `izvor` `RUCNO` (default) | `CLAUDE` (only at creation),
  `rb` at the end of the group. Status `NACRT` -> `AKTIVNA` (`POST .../odobri`: needs `renderStatus == OK`, else 409 `Varijanta se odobrava tek
  kad je render uspeo.`, and answers complete against the current schema, else 400; also works from `ARHIVIRANA`), `arhiviraj`, `vratiUNacrt`.
  New and duplicated variants are always `NACRT` (a duplicate gets its own render, SVG media belong to one variant). Saving one variant
  (`kreiraj`, `izmeni`, `render`, `dupliraj`) keeps an `AKTIVNA` variant active only if the render succeeds; a failed render drops it to `NACRT`.
  `renderuj-sve` never changes the status: after a bad preamble an `AKTIVNA` variant can be `GRESKA` and keeps showing its last good SVG pair.
  Deleting a variant removes its attachments (media of attachments stay, a duplicate shares them) and its SVG media.
- **Attachments (`varijanta_prilozi`):** <= 10 per variant, `ime` `^[a-z0-9_-]{1,60}$` (the name LaTeX uses: `\includegraphics{ime}`), unique in
  the variant, **`dokument` is reserved** (the render service writes `dokument.tex/.pdf/.log`), the medium must exist and be an attachment
  uploaded through `POST /banka/prilozi` (`PRILOG_SLIKA` PNG/JPEG or `PRILOG_PDF`); a slide image (`SLIKA`, public), GIF, WebP and render SVG are 400
  `Medij nije prilog zadatka.`. All attachments together, as base64 (4/3 of the size) plus text and solution, must stay under 40 MB (the container
  takes a 60 MB body), else 400 `Prilozi su preveliki za render (najviše 40 MB ukupno).` (about 30 MB of files). `PUT` replaces the whole list (an
  existing name just changes its medium).
- **JSON schema of `polja` (`grupe_zadataka.polja`, text column, `[]` = work on paper)** is a list of `Polje {kljuc, oznaka, tip, odstupanje,
  odstupanjeTip, jedinica, zanemariVelicinu, zanemariRazmake, opcije[{id, tekst}]}`; <= 10 fields, `kljuc` `^[a-z][a-z0-9]{0,9}$` and unique,
  `oznaka` 1-200 chars, `tip` one of `BROJ` | `TEKST` | `JEDAN_TACAN` | `VISE_TACNIH`. `BROJ`: `odstupanje` >= 0 (default 0)
  with `odstupanjeTip` `APSOLUTNO` (default) | `PROCENAT`, `jedinica` <= 30. `TEKST`: `zanemariVelicinu` and `zanemariRazmake` (default `true`).
  `JEDAN_TACAN`/`VISE_TACNIH`: 2-6 `opcije`, text 1-200 (may hold `$formula$`); the option `id` is stable inside the group and an option
  without an id gets the next one above the largest id given in the request **and** the largest stored for the same key (`PUT` of a group,
  under the lock: `BankaPP.normalizujPolja(polja, sacuvana)`), so a client that omits ids (MCP) never gets the id of an option removed in the same
  `PUT`, and an answer that pointed at it is dropped instead of moving to the new option. A deleted id is not remembered beyond the stored schema: a
  later `PUT` can reuse an id that no option and no answer of the group refers to any more (the removal already cleared those answers). Whole column <= 65 535 bytes
  (400 `Šema polja je prevelika.`; `odgovori` the same, `Odgovori su preveliki.`).
  **`odgovori` (`varijante.odgovori`, `{}` for a paper group)** is an object `kljuc -> OdgovorPolja {broj | prihvatljivi | tacna | tacne}` with
  exactly the property of the field type: `BROJ` `broj` (finite), `TEKST` `prihvatljivi` (1-10 strings, 1-200 chars), `JEDAN_TACAN` `tacna` (an option
  id), `VISE_TACNIH` `tacne` (non-empty set of option ids). Every key of the schema must be answered and no other key is allowed (400).
  `PoljaJson` reads both **strictly** (own Jackson mapper like `PlatnoJson`: unknown property, `null` element or value, wrong type, duplicate key,
  trailing tokens, number-for-string are 400 `Neispravan format podataka.`; the `@JsonDeserialize` on `GrupaCmd.polja` and `VarijantaCmd.odgovori`
  applies it to requests) and the stored columns **leniently** (unknown properties tolerated after a rollback). `BankaPP` holds all the rules
  (use `proveriPolja(normalizujPolja(polja))`, lengths in code points). `OcenaPolja.tacno(polje, ocekivano, dato)` scores one field (pure, never
  throws): `BROJ` with `Normalizacija.broj` (comma or dot) within the absolute/percent tolerance, `TEKST` through `Normalizacija.tekst`
  (case, Cyrillic to Latin, diacritics, spaces), `JEDAN_TACAN` by id, `VISE_TACNIH` as a set; a task is correct when all its fields are (the caller
  combines them; phase 2 uses it, phase 1 only ships it).
- **Schema change (`PUT /banka/grupe/{id}` with `polja`)**, under the locks, adjusts the group's variants: changing the **type** of an existing
  key is 409 while the group has an `AKTIVNA` variant (nothing is written); otherwise each variant keeps only the answers that are still valid
  for the new field (`BankaPP.proveriOdgovore` per field): a removed field, a changed type and a removed correct option delete the value (in every
  variant, also `ARHIVIRANA`); an `AKTIVNA` variant missing a value afterwards (e.g. an added field) becomes `NACRT`; `NACRT` and `ARHIVIRANA`
  keep their status (an archived variant is never pulled back); the render is untouched. **`polja: null` (or absent) = schema and answers unchanged,
  `[]` clears the schema** (the front does GET then PUT of the whole body, also for a rename). `PUT` of a group with variants needs `tezina`.
- **Render flow.** A variant's text and solution are two standalone documents: `LatexDokument.zaTelefon(preambula, telo)` =
  `\documentclass[preview,border=6pt,varwidth=84mm,11pt]{standalone}` + preamble + `\begin{document}` + body + `\end{document}` (84 mm = phone width;
  CRLF/CR become LF; `pomakLinija` converts a line of the document to a line of the teacher's text). The **default preamble**
  (`PODRAZUMEVANA_PREAMBULA`: T1/utf8, amsmath/amssymb, tikz with `automata,positioning,arrows.meta`, graphicx, booktabs, enumitem) is replaced
  per subject by `predmeti.latex_preambula` (`GET|PUT /predmeti/{id}/latex-preambula`, body `{preambula}`; empty/`null` and a text equal to the
  default (also with CRLF) = default, stored as `null`; <= 50 000 chars; `LatexPreambulaService`). A new non-default value is first **test-rendered**
  (`RenderVarijante.proveriPreambulu`: the phone document with the new preamble and the body `Proba preambule.`, outside any transaction and lock):
  a compile error is 400 `Preambula se ne kompajlira (linija N): <poruka>` (N = line in the preamble; without `(linija N)` when the error is not in
  the preamble), a renderer that is down 503, and nothing is saved in either case; going back to the default is not rendered. A **changed** value
  then runs `VarijantaRepository.ponistiHashPredmeta` (bulk `render_hash = null` for the whole bank of the subject) in the same short write
  transaction, the same value touches nothing. `LatexPravila` is the **deny-list** (`\write18`, `\input@path`, `\input`,
  `\include`, `\openin/out`, `\read`, `\write`, `\immediate`, `\csname`, `\directlua`, `\InputIfFileExists`, `\@input`, `\lstinputlisting`,
  `\verbatiminput`, `\includepdf`, `\includepdfmerge`, `\includegraphics` with anything but a bare attachment name, `file:` URLs, `^^`; body-only: `\usepackage`,
  `\RequirePackage`, `\documentclass`, `\begin/\end{document}`, `\catcode`; the preamble may load packages): **only early help with a clear message**
  (400 `Komanda \x nije dozvoljena.`, checked for text and solution before any call), the security boundary is the container (below).
  `RenderVarijante.renderuj` renders the text, then the solution (only when not blank), each to its own SVG (`format svg`, attachments loaded once and
  sent as `{ime, ekstenzija, sadrzaj base64}`); an error in either part makes the whole render fail with that part and the line relative to it
  (a failed solution still returns the task SVG, which `render-proba` shows; a saved variant does not store it and keeps its last good pair).
  **`render_hash`** = SHA-256 hex over `RenderVarijante.RENDER_VERZIJA` (bump it whenever `LatexDokument.OMOT` or anything else that changes the
  output for the same input changes, so every hash goes stale and `renderuj-sve` re-renders), the preamble actually used (`LatexDokument.preambulaIli`),
  text, solution (`null` = empty) and the attachments sorted by name as `ime:medijId`, joined with `\u0000`; the **cache** rule: same hash as the variant's *own* last render and `OK`
  = no render. `render_status` `CEKA` | `OK` | `GRESKA`; on `GRESKA` the old SVG stays and `render_greska` holds `<deo>:<linija>: <poruka>`
  (`deo` `tekst`|`resenje`, line may be empty, e.g. `resenje:12: Undefined control sequence.`; without a part just the message; message from the
  `dokument.tex:N:` log line via `LatexGreska.izLoga`, else the service's own, else a generic one; a 4xx of the service is a generic message and
  the body goes to the log). Success saves new `RENDER_SVG` media (`zadatak-<id>.svg`, `resenje-<id>.svg`) and only then deletes the old ones
  (their files after commit; a failed commit leaves the variant on the old SVG with its file and removes the new files).
  `LatexRender` is the HTTP client of the container (`gfs.latex.url` = env `GFS_LATEX_URL`, default `http://localhost:8800` for dev; in compose
  `http://latex:8800`; `gfs.latex.timeout-sekunde` 30, the service gets 10 s less, at least 10; HTTP/1.1; no `RestClient.Builder` bean on purpose).
  A failed compilation is **state, not an exception** (`ok=false` -> `GRESKA`); a service that is down, 5xx or answers without content is
  `RenderNedostupan` (503 `Render servis nije dostupan.`) and **aborts the whole request, nothing is written** (the front keeps the input and retries).
- **Render happens before the locks.** Writes (`kreiraj`, `izmeni`, `dupliraj`, `render`) are three steps: (1) check the command and read what is
  rendered in a short read-only transaction, no lock; (2) render outside any transaction (up to 2 x 30 s); (3) a short write transaction under
  the locks that re-checks the group/answer rules and the hash of the locked content with the *current* preamble, writes `render_hash` of what was
  really rendered and swaps the SVG media. If the input changed between (1) and (3) (preamble, concurrent write) the write renders again under the lock
  (rare). Those methods are `@Transactional(propagation = NOT_SUPPORTED)` and open the short transactions through `TransactionTemplate`
  (`citaj`, `pisi`: READ COMMITTED, `em.clear()` first because open-in-view keeps one `EntityManager` per HTTP request); the annotation-driven
  `obrisi/odobri/arhiviraj/vratiUNacrt` do not clear it. Front nginx needs `proxy_read_timeout 120s` for `/api/` (render 2 x 30 s plus upload exceed 60 s;
  create/duplicate are not idempotent, so a 504 plus retry would duplicate).
- **`POST /banka/predmeti/{id}/renderuj-sve`** (after a preamble change): synchronous, one call is one step. `VarijantaService.renderujSve` picks
  the candidates (first the stale ones: hash differs or render `CEKA`; then those whose last render of the same content failed), renders at most
  **200** per call and starts no new one after **40 s** (`Clock`); each variant is its own read, render (no lock) and short write transaction
  (writes only if the locked content still has the rendered hash and nobody refreshed it meanwhile). A failed render writes only `GRESKA` and
  the message: **no status change** (one typo in the preamble fails every variant; the active ones keep their last good SVG). Response
  `{renderovano, neuspelo, preostalo}`: `renderovano` counts successful renders, `neuspelo` the `GRESKA` written in this call, `preostalo` only
  stale variants not reached, so the front calls until `preostalo == 0` and always stops. **One call per subject at a time** (in-memory try-lock
  per `predmetId`, one backend, released in `finally`): a second one is 409 `Render cele banke za ovaj predmet je već u toku.`. Candidates come from one
  fetch-join query (`svePredmeta`: group with ancestors and subject, SVG media, attachments with media). A **bad variant** (a `SystemException`
  that is not 503, e.g. an attachment without a file or a command the deny-list forbade meanwhile, or any other `RuntimeException`) is marked
  `GRESKA` with its message (or `Render nije uspeo.`) and the loop goes on; the mark is skipped if the
  locked variant already has the same hash and `OK`. **Infrastructure errors abort the loop and mark nothing**: `RenderNedostupan` (503),
  `DataAccessException`, `PersistenceException`, `TransactionException`, `UncheckedIOException` (e.g. a full disk while writing an SVG must not
  demote every active variant); what was already written stays.
- **`POST /banka/render-proba`** (`{predmetId, tekst, resenje, prilozi}`): the editor's preview and Claude's check, nothing saved; same rules as a
  write, the subject's preamble, `{ok, svg, resenjeSvg, greska, deo}` with the SVG as text; when only the solution failed it is `ok=false`,
  `deo=resenje` **with** the task `svg`. At most **3 at once per backend** (`Semaphore`, not waiting), the fourth gets 429 `Previše proba u isto
  vreme.` (the container compiles at most 4 at once, so one slot stays free for saves).
- **Media kinds (`mediji.vrsta`, V11, default `SLIKA`, `varchar(20)`):** `SLIKA` (slides, questions via `POST /mediji`: PNG/JPEG/GIF/WebP),
  `PRILOG_SLIKA` and `PRILOG_PDF` (**only** through `POST /banka/prilozi`, multipart `fajl`, PNG/JPEG resp. PDF by magic bytes, <= 10 MB; `POST /mediji`
  still refuses a PDF) and `RENDER_SVG` (written by the server only, `MedijService.sacuvajRender`). The render reads attachments by id whatever the
  kind (only `PRILOG_*` can get there). `GET /public/mediji/{uuid}` (`PublicMedijRest`) is **fail-closed**: a `switch` on `vrsta` serves `SLIKA` as
  before and `RENDER_SVG` with `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; img-src data:` (`data:` images, i.e. raster
  attachments embedded in the SVG, render when the SVG is opened top-level; they cannot run scripts) plus `nosniff`, long cache; any other value
  (`PRILOG_SLIKA`, `PRILOG_PDF`, future kinds) is 404 `Slika nije pronađena.`, the same answer as for an unknown id: attachments are never public
  (an attachment of the solution can show the answer). Slide and question images accept only `SLIKA` (see "Media" above).
- **Lock order (invariant, extends the Uživo one):** **subject row -> group row(s) -> variant row**. `GrupaZadatakaService` takes the subject
  (`PredmetRepository.findByIdForUpdate`) first for every tree change (siblings, `rb`, depth, move are serialised per subject; only the holder of the
  subject row ever holds several group rows) and a group row when the group gets a child, changes or is deleted. `VarijantaService` locks the
  group row (`GrupaZadatakaRepository.findByIdForUpdate`), then the variant row, holds **at most one group, never locks the subject after a group
  and never writes `grupe_zadataka` columns** (not even `izmenjeno`), so the leaf/inner rule and the answers-vs-schema check cannot race with a tree
  change. `LatexPreambulaService.postavi` renders before any lock, then in its write transaction takes the subject row and runs the bulk
  `ponistiHashPredmeta` (`update varijante v join grupe_zadataka g ... where g.predmet_id = ?`), which X-locks the subject's variant rows and reads
  the group rows through the secondary index `idx_grupe_zadataka_stablo` (shared locks on that index, which do not conflict with the X lock
  `findByIdForUpdate` takes on a group's primary key; checked on MySQL 8.0). No cycle: `GrupaZadatakaService` takes the subject row before anything
  else (so it waits holding nothing), and a `VarijantaService` transaction holds at most one group and one variant and never waits for anything
  the preamble transaction holds except that variant. If the bulk update ever reads group rows by primary key (a plan change) or `VarijantaService`
  starts writing group columns, lock the subject's group rows first. Hibernate does not refresh an entity read before a
  lock, so only scalar ids are read before locking (`findPredmetId`, `findGrupaId`) and `VarijantaService` clears the persistence context at the
  start of each of its own transactions. Writes run in READ COMMITTED like the rest.
- **REST surface, all behind basic-auth and none under `/public`** (the staging regex opens `api/public/` automatically): under `/banka`:
  `GET /stablo`, `POST /grupe`, `GET|PUT|DELETE /grupe/{id}`, `POST /grupe/{id}/premesti` (`{roditeljId, rb}`), `PUT /grupe/redosled?predmetId&namena[&roditeljId]`
  (`{ids}` = all children, else 400), `GET|POST /grupe/{id}/varijante`, `GET|PUT|DELETE /varijante/{id}`, `POST /varijante/{id}/{odobri|arhiviraj|vrati-u-nacrt|render|dupliraj}`,
  `POST /render-proba`, `POST /prilozi`, `POST /predmeti/{id}/renderuj-sve`, `GET /pretraga` (variants across the bank: `predmetId`, `namena`, `status`,
  `q` of at least 2 chars in name/text/group name else 400, paging 25/100, `sort` `izmenjeno` (default desc) or `naziv`, each row carries the group path);
  and `GET|PUT /predmeti/{id}/latex-preambula` (the `preambula` key is required, an unknown key is 400, so `{}` cannot wipe it).
  Group/variant DTOs: `GrupaCmd`/`GrupaDetails`/`GrupaCvor`, `VarijantaCmd`/`VarijantaInfo`/`VarijantaDetails` (carries `renderStatus`,
  `renderGreska` + `renderDeo`, `svgId`, `resenjeSvgId`, attachments). `svgId`/`resenjeSvgId` are media ids shown through
  `GET /api/public/mediji/{id}` (an unguessable UUID, like slide images), so a rendered solution is public to whoever has the id. Errors are
  `SystemException`s with Serbian messages.
- **`latex-render/` (the render container `gfs-latex`, spec 3.1):** Debian bookworm-slim + TeX Live + poppler, `server.py` is Python 3 stdlib (no pip,
  no secrets, public repo). `POST /render {format svg|pdf, tex, prilozi, timeoutSekunde}` -> `{ok, svg|pdf, greska{linija, poruka}, log}`, `GET /zdravlje`.
  `pdflatex -no-shell-escape` with `openin_any=p openout_any=p`, `prlimit`, at most 4 compilations at once, `pdftocairo -svg`. Env (all validated, exit 2
  when out of range): `RENDER_PORT` 8800, `RENDER_MAX_TIMEOUT` 60, `RENDER_MAX_OUTPUT` 5 MB, `RENDER_MAX_BODY` 60 MB. **The container is the security
  boundary**, the deploy wrapper runs it with an `internal` network (no internet), `read_only`, `tmpfs /tmp`, `cap_drop ALL`, `no-new-privileges`,
  memory/CPU/pids limits, no volumes, no env file, no host ports. CI runs `bash latex-render/test/smoke.sh` (builds the image and checks API, isolation and
  limits) after `docker build -t gfs-backend-ci .` (the build is about 3 minutes longer for the TeX Live image). Everything about the service, API errors and
  the isolation: `latex-render/README.md`. Locally: `bash latex-render/test/smoke.sh`.
- **Tests:** unit tests need nothing (`LatexDokumentTest`, `LatexPravilaTest`, `LatexGreskaTest`, `LatexRenderTest` with `MockRestServiceServer`,
  `RenderVarijanteTest`, `OcenaPoljaTest`, `PoljaJsonTest`, `GrupaZadatakaServiceTest`, `VarijantaServiceTest` (Mockito with fake repositories over lists),
  `LatexPreambulaServiceTest`, the `rest/banka/*RestTest` `@WebMvcTest`s and `PublicMedijRestTest`). **`BankaRepositoryDbTest`, `GrupaZadatakaServiceDbTest`,
  `VarijantaServiceDbTest`, `LatexPreambulaServiceDbTest` and `BankaPretragaDbTest` need a real MySQL** (`SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/gftest`,
  the throwaway `gfs-redizajn-mysql`, never `shared-mysql`): they are `@SpringBootTest` without `@Transactional`, commit for real, mock only `LatexRender`
  (fixed SVG) and clean up through JDBC; the locking tests use real second transactions.

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
- CI: `.github/workflows/ci.yml`, job `build`, on PR and push to `staging`/`master`: temurin 21, service `mysql:8.0` (from `public.ecr.aws/docker/library/`, like the Dockerfile base images: anonymous Docker Hub pulls hit a per-IP rate limit on GitHub runners)
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
  (teacher notes). **V2-V4 and V6-V11 are idempotent** (`CREATE TABLE IF NOT EXISTS`; columns and indexes guarded by
  `information_schema` + `PREPARE`/`EXECUTE`, pattern in `V2__onboarding.sql`).
- **Rule: every new migration must be idempotent.** Staging `reset-db.sh` rebuilds `gf_staging` from the prod schema dump
  and a DB that already has the objects but no Flyway history must still migrate (no "Duplicate key name"/"already exists").
- **Versions:** `V5` is a deliberate permanent gap (never reuse it), `V6` = `prag_prolaza` (nullable `testovi.prag_prolaza`),
  `V7` = uživo (presentations, runs, participants, answers, media), idempotent. `V8` = platno (`slajdovi.platno` JSON, `prezentacije.tema`), idempotent. `V9` = `grupe.wa_link` (nullable), idempotent. `V10` = `prelaz_slajda` (nullable `slajdovi.prelaz_efekat`, `prelaz_smer`, `prelaz_ms`), idempotent. `V11` = `banka_zadataka` (tables `grupe_zadataka`, `varijante`, `varijanta_prilozi`, column `mediji.vrsta` `varchar(20) NOT NULL DEFAULT 'SLIKA'`, nullable `predmeti.latex_preambula`), idempotent. **Next free version: `V12`.**
- `sql/views.sql` is historical, the view lives in `V1`.
- `scripts/flyway-provera.sh` checks the migrations against four starting states: empty DB, `gf` and `gf_staging` schema dumps
  (`/data/tmp/redizajn-schema`) and `vec_migrirana` (staging schema with the objects of every migration from V3 on already applied, no history). Run
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
  afterwards only by `PATCH /test/{id}/prag-prolaza` with `{"pragProlaza": n|null}` (null clears; works on finished tests too). The key is
  **required**: `{}`, a mistyped key, any extra field, a decimal (`20.5`, `20.0`) or a string is 400 `Neispravan format podataka.`
  (`PragProlazaCmd`: `@JsonCreator` with `required = true`, `@JsonAnySetter` that throws because the mapper skips unknown fields under
  `use-jackson2-defaults`, and `utility/CeoBrojDeserializer`, since the default `Integer` deserializer silently truncates decimals).
  `POST /test` takes `pragProlaza` through the same strict integer (`CreateTestCmd`, 20.5 is 400). `PUT /test/{id}` ignores it (it rejects finished tests and must not erase a threshold).
  No threshold = no pass concept (`procenatProlaznosti`, `brojPolozenih`, `brojPalih` are null). Rule in `utility/Prolaz` (poeni >= prag, not
  `prepisivao`; stored `polozio` is ignored); `PolaganjeRepository.statistikaPoTestu` mirrors it in JPQL, keep them in sync.
  The student overview (`StudentPregledTestInfo`, used by `GET /studenti/{id}`, `/studenti/{id}/predmet/{predmetId}`) carries `pragProlaza`
  (Integer, null = no threshold) and `polozeno` (Boolean from `Prolaz.polozenoIliNull`, null when the test has no threshold **or** `ostvareniPoeni` is null: an ungraded row is neither passed nor failed, same denominator as the test statistics); the stored
  `polozio` is no longer exposed by any DTO (`TestPolaganjeInfo` and `StudentPregledTestInfo` dropped it; the entity column stays).

## Frontend

Angular frontend expected at `http://localhost:4200` (CORS configured).

## Conventions

- DTOs use suffixes: `Info` (list view), `Details` (full view), `Cmd` (commands/inputs)
- Repositories extend Spring Data JPA interfaces
- Services contain business logic, REST controllers are thin
- Serbian language used in domain naming (Predmet, Grupa, Domaci, etc.)
