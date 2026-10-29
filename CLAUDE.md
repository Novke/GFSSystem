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
- **Spring AI 2.0.1** MCP server (`spring-ai-starter-mcp-server-webmvc`, MCP SDK 2.0.0, stateless) for Claude, see "MCP za Claude-a";
  **ArchUnit 1.5.1** (tests) guards its access boundary

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
├── mcp/             # MCP server for Claude: core (key filter, errors, patch, budget), alati/ (tools), servis/ (facades), rest/ (upload)
├── repository/      # Spring Data repositories
├── rest/            # REST controllers
├── security/        # Spring Security (SecurityConfig: MCP chain + main chain), PristupMatrica, sessions, CORS
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
- **Only new students:** dedupe by (canonical index, enrollment year) against `studenti`, against pending submissions in
  the same session, on edit and on accept. `IndeksUtil.kanonski` strips whitespace, upper-cases, drops a `/YYYY` suffix equal to the
  year and leading zeros of the number (`"gd 01/2026"` + 2026 -> `GD1`); `POST /studenti` and `PUT /studenti/{id}` store the same form.
  The public form sends `GD` + number and the year separately; the year is proposed from the session's group but the student may change
  it (2000 to current school year + 1, `PrijavaPP.maxGodina`): the accepted student goes into the **session's group** with **their own**
  `godina` (a student who enrolled earlier keeps their real generation, so the index pair stays unique).
- **Phone numbers are E.164** (`utility/Telefon.uE164`): `+...` kept, `00...` -> `+...`, a leading `0` -> `+381` (Serbia), `381` + 8-10 digits
  -> `+381...`; spaces, dashes, dots, slashes and parentheses ignored; anything else is 400 `Broj telefona nije ispravan. ...`. Applied by
  `PrijavaPP` (public form and the teacher's edit of a submission) and `StudentService` (manual add/edit; an unchanged legacy value is kept
  so an old record can still be edited). The vCard export still goes through `Telefon.uMedjunarodni`, which passes E.164 through. V16
  normalizes only the unambiguous stored forms (see "Database").
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
- **Runs of a lecture:** `GET /izvodjenja` takes three optional filters, `prezentacijaId`, `status` and `predavanjeId` (runs bound to that lecture;
  newest first, same `IzvodjenjeInfo` rows). The lecture screen lists "Prezentacije na ovom predavanju" with it. `PredavanjeDetails.predmet` carries
  `{id, naziv}` (the front picks the subject's presentations by that id).
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
- **Question results** (`RezultatBuilder`, pure; `tacanPrikazan` = teacher always, public only after `TACAN` on a closed question):
  `Rezultat.tacnih` (last field) = correct answers among `ukupno` (one correct option; for VISE_TACNIH exactly the set of correct
  options; BROJ within `Ocenjivac.uOdstupanju`; KRATAK_TEKST among the normalized accepted answers), `null` for ANKETA, SKALA, an
  estimate, open KRATAK_TEKST or while correctness is hidden; in the public result it counts only the answers that result shows
  (hidden text groups excluded). BROJ (`RezultatBrojevi.vrednosti`) lists **every** distinct value (no median since 2026-10-28, no
  top-10 cap: at most one per participant, like the text groups; exact double equality, `-0` = `0`), most frequent first, then by
  value, each `BrojStavka.tacan` (`null` while hidden or for an estimate). The order never depends on correctness, so it leaks nothing
  before `TACAN`. `RezultatTekst.tacan` as before. Tests: `RezultatBuilderTest`, `JavnoStanjeTest` (`brojTacnostVrednostiIBrojTacnihTekPosleTacan`,
  `opcijeBrojTacnihTekPosleTacan` and the all-states matrix: no `tacan`/`tacnih` before `TACAN`).
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

### Zadatak slide (banka phase 2, project B)

Slide type `ZADATAK` (spec `docs/superpowers/specs/2026-10-18-mcp-nastava-design.md` 11 and banka spec 4 in the wrapper repo): every
phone gets its own random variant from a node of the subject's PREDAVANJE bank, the student types the answers per field of the
group's schema, the console and projector show results per variant and, on `Shift+R` (`RESENJE`), the solution.
- **Slide:** `slajdovi.grupa_zadataka_id` (FK `ON DELETE SET NULL`), `zadatak_vreme_sekunde` (5-600 or null), `zadatak_broj_varijanti`
  (1-50 or null = all active). `SlajdCmd.zadatak` / `SlajdDetails.zadatak` (`ZadatakSlajdaInfo`: path, leaf or not, candidates, schema
  for a leaf, exclusions, warnings, built by `ZadaciSlajdova` in one batch per request). The group must be of the same subject,
  purpose PREDAVANJE (400 otherwise); a deleted group leaves the slide without one (warning, 409 on opening).
  `ZadatakSlajdaInfo.primerSvgId` (last component) is the text SVG of one candidate for the editor's example: the lowest variant id in
  the first leaf (tree order) that has a candidate of the slide's level (`StabloPredavanja.primer`, from `min(v.id)` of the same
  aggregate query, plus one batch query `svgTeksta` for all slides). It reaches only teacher views (editor, console state); MCP
  (`McpZadatakSazetak`) leaves it out and no phone or projector projection carries it.
- **Candidates and drawing** (`service/banka/KandidatiZadatka`, `service/uzivo/IzborVarijanti`): AKTIVNE variants with a render SVG in the
  leaves under the node, excluded subtrees (`iskljucene_teme`) skipped with a warning; `min(N, candidates)` drawn alternately per leaf.
  Randomness only from the `java.util.Random` bean (tests: `SeedRandom`).
- **Round snapshot** is a union (`SnimakRunde` = `PitanjeSnimak` | `ZadatakSnimak`, JSON `vrsta`; an old snapshot without `vrsta` is
  PITANJE). `ZadatakSnimak` holds only the drawn variants with their schema, correct answers and SVG ids at opening time: grading,
  results and the overview always follow the snapshot, never the current bank. A round whose snapshot does not match the slide type
  (`TokIzvodjenja.odgovaraSlajdu`) is treated as no round.
- **Assignment** (`ZadatakDodelaService`, table `zadatak_dodele`, `UNIQUE(runda_id, ucesnik_id)`, FK to `varijante` RESTRICT): always
  under the locked run row (open, reopen, `PONOVI`, participant join): an empty round deals evenly, a late participant gets a
  least-assigned variant. Opening locks the node's candidates after the run row (`VarijantaRepository.zakljucajKandidate`, ascending
  ids) and draws only those still AKTIVNE, so a variant deleted while the round opens never enters the snapshot (lock order run ->
  variants; deletion takes group -> variants and never the run, so no cycle).
- **Variant level (`nivo`, V15; spec `docs/superpowers/specs/2026-10-21-nivo-varijante-design.md` in the wrapper repo):** `Varijanta.nivo`
  1-3 (`utility/Nivo`: bounds, default 2, 400 `Nivo mora biti od 1 do 3.`) says how hard a variant is inside its type; it is not the group's
  `tezina` 1-5 ("Težina tipa"). REST: `VarijantaCmd.nivo` on `POST` (absent = 2), **ignored on `PUT`** (an old front must not reset it),
  `PATCH /banka/varijante/{id}/nivo` (`NivoCmd`, strict like `PragProlazaCmd`; no render, no status change, also on approved variants),
  `nivo` in `VarijantaInfo`/`VarijantaDetails`/`BankaPretragaStavka`, filter and sort field `nivo` in `GET /banka/pretraga`; copies (duplicate, Claude's
  copy, `KopijaGrupeService`) keep it. Slide `ZadatakSlajda.nivo` (`slajdovi.zadatak_nivo`, null = all levels); `ZadatakSlajdaInfo.brojAktivnihVarijanti`
  counts the slide's level and `brojAktivnihPoNivou` holds exactly three counts (levels 1, 2, 3) so the console can show the count for its own choice;
  warning `Nema aktivnih varijanti nivoa ★★★; odobri nacrte ili izaberi drugi nivo.`. Drawing (`KandidatiZadatka.kandidati(id, nivo)`) keeps only that
  level (counts come from one aggregate query grouped by group and level, `VarijantaRepository.aktivneSaSvgPoGrupiINivou`), so **every student of a round gets the same level**; none is 409
  `Grupa nema aktivnih varijanti nivoa ★★★.`. The round snapshot carries `nivo` (`ZadatakSnimak`, `VarijantaSnimak`), and so do `NastavnickiZadatak`
  (`izmenaNivoa`, `nivoZaOtvaranje`), `VarijantaRunde` and `PregledZadatka` (`nivo`, `nivoi`); phones (`JavnoStanje`, `LicnoStanje`, `PocetnoStanje`) and
  the projector's `javniRezultat` never do (`ZadatakStanjeTest.nivoVidiSamoKonzola`). MCP: `napravi_varijantu` requires `nivo` (facade, 400
  `Nivo je obavezan (1 do 3).` before the budget); a level-only `izmeni_varijantu` changes Claude's own unapproved draft in place and is 409
  `Nivo odobrene ili Novicine varijante menja Novica u banci.` on any other variant (never a copy, no budget); `pretraga_banke` filters by `nivo`.
- **Commands:** `NIVO` (only on a ZADATAK slide in CEKA, `Izvodjenje.zadatakNivo`: `null` = as on the slide, `0` = all levels, `1..3`; 409 `Nivo se menja
  pre otvaranja.` after opening; holds for the next opening and `PONOVI`, cleared on slide change, `TokIzvodjenja.nivo`), `BROJ_VARIJANTI` (only on a ZADATAK slide in CEKA; `null` = as on the slide, `0` = all, `1..50`; cleared on slide
  change) and `RESENJE` (toggle; on needs a closed round and at least one variant with a solution). `TACAN` on a paper-only task
  (no fields) is 409.
- **Answers** (`OdgovorService` + `OcenaZadatka`): STOMP `polja` (`kljuc -> string` for BROJ/TEKST, list of option ids for
  JEDAN_TACAN/VISE_TACNIH); the variant comes from the assignment, never from the message; each field graded by `OcenaPolja`
  against the snapshot, the task is correct when all fields are. Stored in `odgovori.polja` (JSON `kljuc -> {vrednost, tacno}`) and
  `odgovori.varijanta_id`.
- **"Ni ranije" for the task** (`StanjeService`, `ZadatakRezultatBuilder`): phones (`JavnoStanje.zadatak` = `JavniZadatak`,
  `LicnoStanje.zadatak` = `LicniZadatak`, `PocetnoStanje`) never get another participant's variant, a solution SVG id before
  `RESENJE` on a closed round, or correct values before `TACAN` on a closed round; the public state carries no variant content at
  all. The projector (`NastavnickiZadatak.javniRezultat`) follows three independent switches (results, TACAN, RESENJE). The teacher
  API (`/izvodjenja/{id}/stanje`, `/rezultati`, teacher STOMP) carries everything, so on prod the task is only safe while the teacher
  API is protected (see the wrapper README). Overview: `RezultatPitanja.zadatak` (`PregledZadatka`, `slajdId` from the snapshot);
  each `UcesnikZadatka` carries `polja` (`kljuc -> tacno`) and `uneto` (`kljuc -> text` as entered, schema order, built by the pure
  `UnetoZadatka` from the snapshot: BROJ as typed plus the field's unit, TEKST as typed, options by their text joined with `, `,
  an option missing from the snapshot as `#id`). `uneto` exists only in the teacher overview (`GET /izvodjenja/{id}/rezultati`),
  never in a phone projection or `javniRezultat` (`ZadatakStanjeTest.unetoNosiSamoPregled`).
- **Student identity seam:** `security/IdentitetStudenta` (`studentId(request)`, `dostupno()`); `NemaIdentiteta` is the bean only when no
  other exists (`IdentitetAutoKonfig`, `@ConditionalOnMissingBean`). Without the login project a scored run (`PokreniCmd.boduje`) is 409
  `Prijava studenata još nije dostupna.`; on branch `feature/prijava` the seam is live: `NalogIdentitet` (see "Prijava") replaces
  `NemaIdentiteta`, so a scored run starts and its join takes the student from `gfs_student` (403 `Potrebna je prijava studenta.`
  without one).
- **Not done yet (BE-8):** activities in a scored run (attendance and solved tasks written to the lecture) are not implemented, also
  not with the login: a scored run takes the student but records nothing on the lecture yet. When it is added, manual activity
  edits in `PredavanjeService` must lock the lecture row first (plan task BE-8). **That locking is already in place** since the
  check-in work (see "Čekiranje"): every manual activity change locks the lecture row (`PredavanjeRepository.findByIdForUpdate`).
  BE-8 must write an activity only for a student with a **current check-in** on the lecture: removing attendance deletes the check-in
  but keeps the student's participant row in a running scored run (security review 2026-10-28).
- Tests: `ZadatakRundaDbTest` (drawing, assignment, commands, deletion during opening), `ZadatakOdgovorDbTest` (answers, snapshot
  grading, SVG survival, overview), `OcenaZadatkaTest`, `UnetoZadatkaTest`, `ZadatakRezultatBuilderTest`, `ZadatakStanjeTest` (JSON of every phone
  projection per phase has no secret), `KandidatiZadatkaDbTest`, `PrezentacijaZadatakDbTest`, `ZadatakDodelaDbTest`.

## Banka zadataka (faza 1)

Per-subject bank of tasks for lectures and tests: a tree of groups, variants of a task (LaTeX text and solution rendered to SVG
for the phone) and the schema of the answers the student types. Phase 1 is the bank itself (REST); phase 2 is the Zadatak slide
(see "Zadatak slide" under Uživo). `koriscenoNaCasu` (variant and group) is true when a variant has a row in `zadatak_dodele` or is in
a round snapshot (drawn, not yet assigned): such a variant or group is not deleted (409 `Korišćeno na času; arhiviraj.`). The SVGs of a
variant with `ikadOdobrena` are never deleted, neither on a new render nor on deletion: round snapshots keep them by UUID (orphan
`RENDER_SVG` rows are accepted). Candidates of a node: `KandidatiZadatka`. Spec and plan:
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
  takes a 60 MB body), i.e. about 30 MB of files, else 400 `Prilozi su preveliki za render (ukupno najviše oko 30 MB fajlova).`. `PUT` replaces the whole list (an
  existing name just changes its medium).
- **JSON schema of `polja` (`grupe_zadataka.polja`, text column, `[]` = work on paper)** is a list of `Polje {kljuc, oznaka, tip, odstupanje,
  odstupanjeTip, jedinica, zanemariVelicinu, zanemariRazmake, opcije[{id, tekst}]}`; <= 10 fields, `kljuc` `^[a-z][a-z0-9]{0,9}$` and unique,
  `oznaka` 1-200 chars (a plain description, e.g. `Dekadna vrednost`; a list letter `a)` only when the task text itself has parts with
  those letters, never on a one-field schema: the projector and the overview hide the label of a single field, the phone strips a
  leading `a) ` there; the MCP guide `vodic-banka.md` teaches the same, `VodicTest.vodicBankaUciOznakeBezSlova`), `tip` one of `BROJ` | `TEKST` | `JEDAN_TACAN` | `VISE_TACNIH`. `BROJ`: `odstupanje` >= 0 (default 0)
  with `odstupanjeTip` `APSOLUTNO` (default) | `PROCENAT`, `jedinica` <= 30. `TEKST`: `zanemariVelicinu` and `zanemariRazmake` (default `true`),
  `zanemariVodeceNule` (last component of `Polje`, default off; stored only when `true`, so an existing schema column stays the same):
  `01111111` = `1111111` (`OcenaPolja.bezVodecihNula`: zeros at the start, after an optional sign, followed by a letter or digit, so
  `0.101` stays). Only where a leading zero does not change the result (sums, differences, conversions in a base); never for a
  fixed-width representation (complement, 8 bits), where `00000101` is not `101`. It is part of the round snapshot like every
  schema property, so a round graded before it keeps its result until it is regraded.
  `JEDAN_TACAN`/`VISE_TACNIH`: 2-6 `opcije`, text 1-200 (may hold `$formula$`); the option `id` is stable inside the group and an option
  without an id gets the next one above the largest id given in the request **and** the largest stored for the same key (`PUT` of a group,
  under the lock: `BankaPP.normalizujPolja(polja, sacuvana)`), so a client that omits ids (MCP) never gets the id of an option removed in the same
  `PUT`, and an answer that pointed at it is dropped instead of moving to the new option. **Contract for clients (front, MCP agent): a `PUT` of a
  group must echo the `id` of every existing option it keeps.** An option sent without `id` is a NEW option (fresh id above the stored maximum),
  and the old option it may look like is removed together with every answer that pointed at it; `AKTIVNA` variants that lose an answer drop to
  `NACRT` (see "Schema change" below). Renaming an option = same `id`, new `tekst`. A deleted id is not remembered beyond the stored schema: a
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
  the candidates (the stale ones: hash differs or render `CEKA`; **only when there is none**, those whose last render of the same content failed,
  so the multi-call flow after a preamble change renders and counts each failure once, while a manual call with nothing stale retries them), renders at most
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
  mark the whole bank as failed); what was already written stays.
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

## MCP za Claude-a

Claude in Claude Code on novica-dev talks to an MCP server built into this backend: it fills the task bank with drafts (with
a PNG of the render), reads presentations and adds INFO/PITANJE slides; Novica reviews the drafts one by one on the front's
`/banka/pregled`. Spec and plan (project A1): `docs/superpowers/specs/2026-10-18-mcp-nastava-design.md` and
`.../plans/2026-10-18-mcp-banka.md` in the wrapper repo; projects B (slide "Zadatak") and A2 (editing slides) extend it
through the plan's "Tačke proširenja za B i A2".

- **Server:** Spring AI 2.0.1 **stateless** (`protocol=STATELESS`, `type=SYNC`: every `POST /mcp` gets one JSON answer, no
  SSE, so nginx needs nothing), tools only (resources, prompts, completions off). `McpKonfig.StatelessServer` builds the server
  itself (`McpServerStatelessAutoConfiguration` is excluded in `GfsSystemApplication`) with `validateToolInputs(false)` (the
  SDK's schema check would answer in technical English before `McpOmotac`), a transport JSON mapper that keeps `null` in maps
  (merge patch needs `"opis": null`) and `immediateExecution` (the tool runs on the request thread and sees request
  attributes). Server instructions (`initialize`, <= 4 000 chars) are `resources/mcp/uputstva.md`, read as UTF-8 by a bean
  post-processor (Boot reads `.properties` as ISO-8859-1); the `vodic` tool serves `mcp/vodic-{banka,pitanja,pravila}.md`
  (`VodicTema`, <= 16 000 chars each, read at startup).
- **Properties, also in the test `application.properties`** (it shadows the main file; without them a STREAMABLE server or
  nothing starts): `spring.ai.mcp.server.*` (protocol, type, `name=gfs`, version, `streamable-http.mcp-endpoint=/mcp`,
  capabilities off, `request-timeout=120s`), `gfs.mcp.kljucevi=${GFS_MCP_KLJUCEVI:}` (test: the synthetic key's hash) and
  `gfs.okruzenje=${GFS_OKRUZENJE:LOKALNO}` (test `TEST`, compose `PROD`/`STAGING`, returned by `kontekst`).
  `application-server.properties`: `logging.level.io.modelcontextprotocol=WARN` (no message bodies) and
  `org.springframework.ai.mcp=INFO`. Never set `spring.ai.mcp.server.enabled`: `McpUkljucenje` (`META-INF/spring.factories`,
  highest-priority property source) switches MCP on exactly when `gfs.mcp.kljucevi` has entries.
- **Key and filter:** the client key is `gfs_` + 43 base64url chars; the server knows only `oznaka:sha256hex` entries
  (`McpKljucevi`, comma-separated, several = rotation; a bad entry fails the start and the message names only its index).
  `McpKljucFilter` (`FilterRegistrationBean`, patterns `/mcp` and `/mcp/*` from the endpoint property): no keys -> 404 (and no
  MCP route at all), missing or wrong `Authorization: Bearer <key>` -> 401 `{"reason":"Neispravan MCP ključ.","time":...}` with
  `WWW-Authenticate: Bearer` (constant-time compare over all entries); success puts the key label into a request attribute
  (`McpZahtev`). The header is never logged; there is no lockout by IP (256-bit key, `X-Forwarded-For` is chosen by the
  client). Startup check `McpKonfig.mcpProvera`: MCP on with a protocol other than STATELESS, or a `RouterFunction` route outside
  the filtered path, fails the start. Backend paths are only `/mcp`, `/mcp/prilozi`, `/mcp/slike` (nginx `/api/mcp...`),
  **never under `/public/**`**.
- **With the login (Spring Security, `security/`):** MCP is its own access class (`PristupMatrica.mcp()`: exactly those three
  paths, any method, no prefix) with its own chain `SecurityConfig.mcpLanac` (`@Order(1)`: `permitAll`, no teacher session, no
  origin check, CORS and the anonymous filter explicitly disabled (so a later CORS bean never opens `/mcp` to browsers), no CSRF, stateless; the start fails unless the endpoint property is `/mcp`). `McpKljucFilter` (order
  `HIGHEST_PRECEDENCE + 10`) runs before the Spring Security filter (`-100`) and stays the only authority there; any other path
  under `/mcp` goes through the main chain as ADMIN, so the key opens nothing else and a teacher cookie alone never opens MCP
  (`security/McpLanacIT`, `McpLanacIskljucenIT`, real Tomcat).
- **Package `mcp`:** `alati/` (`KontekstAlati`, `BankaAlati`, `VarijanteAlati`, `PrezentacijaAlati`; 28 `@McpTool` methods in
  A1, Serbian snake_case ASCII names, object parameter types (`Long`, `Boolean`), never primitives, every write tool takes a
  required `serija`), `servis/` (facades `McpKontekst`, `McpBanka`, `McpPrezentacije`, the MCP DTO records `Mcp*` and the helpers
  `McpSlika`, `PrezentacijaPregled`, `PlatnoPregled`, `VodicTema`, `ZahtevSlike`), `rest/McpUploadRest`, and the core
  `McpKonfig`, `McpKljucevi`, `McpKljucFilter`, `McpUkljucenje`, `McpOmotac`, `McpGreske`, `McpPoziv`, `McpZahtev`, `McpPatch`,
  `McpOdgovor`, `McpLinkovi`, `McpBudzet`. Tools are thin and **never `@Transactional`**: the body is
  `return McpGreske.izvrsi("<alat>", serija, poziv -> odgovor.json(fasada.metoda(...)))`, where the lambda may also log ids
  (`poziv.id`) and read raw arguments through `McpPatch`, but holds no logic of its own; the facades are the only place MCP
  touches existing services.
- **Access boundary (fail-closed tests):** `McpOpsegTest` (ArchUnit over the main classes): tools depend only on facades and
  the rest of `mcp`, DTOs, exceptions, entity enums and the MCP/Spring AI API; nothing in `mcp` touches repositories,
  `EntityManager`, JDBC, the context or bean factory, non-enum entities or REST controllers; no field or setter injection; every
  call from `mcp` into `service..` is listed in `DOZVOLJENE_METODE` (`Vlasnik.metoda(Tip, Tip)`, service beans) or
  `DOZVOLJENI_POMOCNICI` (static pure helpers), and none of the spec's forbidden methods (`VarijantaService.odobri/arhiviraj/
  vratiUNacrt`, `PrezentacijaService.obrisi`, `MedijService.obrisi/sacuvajRender`, `LatexPreambulaService.postavi`, students,
  grades, tests, ...) may be listed. `McpFasadeTest.METODE` pins the public methods of every facade. Plain data records a service
  returns to a facade live in `dto..` (`SlikeRendera`, `UpisVarijante`, `ProbaSaSlikom`), not in `service..`.
  **`McpAlatiTest.OCEKIVANO`** pins every tool with its annotations (`readOnlyHint`, `destructiveHint`, `idempotentHint`,
  `openWorldHint=false` everywhere, `_meta` `anthropic/requiresUserInteraction` through `TraziPotvrdu` for `obrisi_*`,
  `ponisti_seriju`, `ukljuci_temu`, `izmeni_polja_grupe`), the full name length, the series check, and that the description of
  every non-idempotent write ends with `McpAlati.NE_PONAVLJAJ`. A new tool = a row there, its facade method in
  `McpFasadeTest.METODE` and, for a new service call, a row in `DOZVOLJENE_METODE`.
- **Arguments:** `McpOmotac` wraps every stateless tool spec before Spring AI binds the arguments: a key outside the tool's
  schema or inside an object argument (by the parameter's Java type) is `Nepoznato polje 'slajd.pitanje.x'.`, a wrong type or
  `null` for a primitive is `Neispravan format podataka.`, as is a binding error before the method; an exception outside the
  tool is `Sistemska greška.`; in all these cases the tool is not called (`ishod=neispravni-argumenti`). Inside the tool
  `McpPatch` reads the raw arguments: `promena` is a merge patch (RFC 7386: absent = unchanged, `null` deletes only a deletable
  field, otherwise `Polje 'x' se ne briše.`; value type `utility/Promena`), `strogo` a strict object.
- **Errors and log (`McpGreske.izvrsi`):** a write tool checks `serija` first (`utility/Serija`, `^[a-z0-9-]{3,60}$`, same check
  for REST; otherwise `Serija mora imati 3 do 60 malih slova, cifara ili crtica.` and nothing runs); a `SystemException` becomes
  `isError` with its Serbian message, anything else `Sistemska greška.` (detail only in the ERROR log). **Exactly one INFO line
  per call**, `MCP alat=<alat> kljuc=<oznaka> serija=<s> ids={...} trajanjeMs=<n> ishod=ok|greska-<kod>|sistemska`, never
  arguments or content (LaTeX, slide text); a 401 logs only `MCP zahtev odbijen: neispravan ili bez ključa`; uploads log
  `MCP upload=prilog|slika kljuc=... serija=... id=... vrsta=... velicina=...`. `McpDnevnikDbTest` checks that the output of a 401
  and of a successful write never contains the key pattern `gfs_[A-Za-z0-9_-]{43}`, `Bearer` or content from the arguments.
- **Results and limits:** JSON in the first text block (`McpOdgovor`, the app `JsonMapper`), an optional note in a second text
  block and at most one PNG. A text over `McpOdgovor.MAX_ZNAKOVA` = 28 000 chars logs a WARN: lists are paged (`McpStrana`,
  `strana` 1-based; 25 in `pretraga_banke`, 20 in `domaci_tekstovi`; `prezentacija` pages so that each page fits). MCP DTOs never
  carry `svgId`/`resenjeSvgId` (public URLs) or drawing strokes (`potezi`). Links (`McpLinkovi`, base = first URL of
  `gfs.front.url`): `/banka/varijante/{id}`, `/banka/grupe/{id}`, `/prezentacije/{id}`, `/banka/pregled?serija=<encoded>`. Daily
  budget per key label (`McpBudzet`, in memory, reset at midnight by the `Clock`): 2 000 creations (groups, variants, slides,
  presentations) and 200 MB of uploads, charged before the write; over it 429 `Dnevni limit za Kloda je potrošen.`.
- **Images and the time limit:** the PNG comes from the **same compilation** as the SVG: `gfs-latex` takes
  `formati: ["svg","png"]` (`pdftocairo -png -scale-to 1400`, see `latex-render/README.md`), `LatexRender.renderuj(List<String>
  formati, ...)` and `RenderVarijante.renderuj(..., svg, png)`; a reply without every requested format is `RenderNedostupan`,
  never a partial success. `McpSlika` joins task and solution (grey line between) and scales down to a longer edge of 1568 px;
  the PNG is never stored. An image is returned only with `slika: true`; for a group of `namena` TEST it shows only the task
  unless `slikaResenja: true`, for PREDAVANJE task and solution; `proba_rendera` always includes a sent solution. `varijanta`
  with an image and `proba_rendera` render PNG only, through the 3 probe slots (`VarijantaService.slika`, `probaSaSlikom`,
  nothing written). A tool is designed to finish within `McpPoziv.ROK` = 90 s (the front nginx waits 120 s, and a 504 after a
  write would make Claude retry). No timer enforces it: it holds because a write renders at most 2 x 30 s and an extra image
  render starts only while the call is under 30 s (`imaVremenaZaSliku`); DB lock waits come on top, and a new tool must fit the
  same budget. An image that does not arrive (time, 503, 429, unreadable PNG) is not an error: the tool succeeds and
  the note says `Slika nije stigla na vreme.`.
- **Variants for Claude (`VarijantaService`: `kreiraj(grupaId, cmd, serija, png)`, `izmeniDelimicno`, `dupliraj(id, serija)`,
  `obrisiSopstveniNacrt`):** Claude creates drafts only (`NACRT`, `serija`). Every row these methods create (`kreiraj`,
  `dupliraj`, the copy of `izmeniDelimicno`) gets `izvor` CLAUDE from the service itself, whatever `cmd.izvor()` says
  (`VarijantaServiceMcpDbTest.mcpKreiranjeIDuplikatSuUvekClaude`), so a new MCP facade cannot create a RUCNO draft by passing a
  command without `izvor`; the REST `kreiraj(grupaId, cmd)` keeps `cmd.izvor()` or RUCNO and `dupliraj(id)` keeps the
  original's `izvor`.
  It changes in place and deletes **only its own never-approved draft** (NACRT + CLAUDE + `ikadOdobrena=false`; deleting
  anything else is 409 `Klod briše samo svoje neodobrene nacrte.`). Editing any other variant (Novica's, or one approved
  once) creates a **copy instead**: a new CLAUDE draft of the series at the end of the same group, `zamenjuje` = the original,
  patch applied, original untouched. `odobri` sets `ikadOdobrena` (kept after `vratiUNacrt`), and approving a copy archives the
  original in the same transaction (if it still exists and is not archived). The three steps are those of `izmeni`, but step 3
  decides again under the lock (approved in the meantime = copy) and **never renders under the lock**: content or preamble
  changed during the render is 409 `Varijanta ili preambula je u međuvremenu izmenjena; pokušaj ponovo.` and nothing is written.
- **Groups for Claude (`GrupaZadatakaService`):** `kreiraj(cmd, izvor, serija)` (also under Novica's groups; `izvor` and
  `serija` never change afterwards), `izmeniDelimicno(id, GrupaPatch)` (`null` deletes `opis`, `tezina`, `uputstvo`),
  `izmeniPolja` (tries the schema change first, `isprobaj`: if any variant, archived too, would lose an answer or an AKTIVNA would
  drop to NACRT, 409 `Izmena polja bi pogodila varijante: <ids ascending>.` and nothing is written), `premesti`, `obrisiPraznu`
  (409 `Briše se samo prazna grupa.`). The facade lets Claude change, move and delete only CLAUDE groups (409 `Ovu grupu je
  napravio Novica; izmenu radi on u UI-ju.`). Those methods start with an empty persistence context (`zaboraviProcitano`: with
  open-in-view the facade already read the group in the same request). Two traps for new callers: `kreiraj(cmd, izvor, serija)`
  and `VarijantaService.obrisiSopstveniNacrt` do **not** clear it, so a facade must not read those groups or that variant
  earlier in the same request (or the lock and checks run on stale entities); and no clearing method (`izmeniDelimicno`,
  `izmeniPolja`, `premesti`, `obrisiPraznu`, `KopijaGrupeService.kopiraj`) may run inside an outer transaction, because the
  clear silently drops that transaction's unflushed changes. `KopijaGrupeService.kopiraj` (`kopiraj_grupu`) copies a
  subtree of the same subject as a root of `ciljNamena` or under `ciljRoditeljId` (that parent must be in the same subject and
  in `ciljNamena`, else 400 `Cilj kopije mora biti u istom predmetu i nameni.`), so PREDAVANJE <-> TEST works, in **one
  transaction** (a name clash halfway leaves no row and no file): same fields and option ids, non-archived variants become
  CLAUDE drafts of the series, an `OK` render is copied (new SVG media, same hash), not re-rendered.
- **Series (`serija`):** one label per request of Novica, written on everything Claude creates (groups, variants,
  presentations, slides, excluded topics). `SerijaService.serije(predmetId)` (`GET /banka/serije?predmetId=`, newest first,
  without `predmetId` 400 `Predmet je obavezan.`) and `poslednje(n)` (for `kontekst`). `SerijaService.ponisti(serija, potvrdi)`
  (`POST /banka/serije/{serija}/odbaci`, bank only) deletes the series' never-approved CLAUDE drafts, then its CLAUDE groups that
  are or become empty, each in its own transaction under that service's locks; everything else is listed as skipped with
  `Odobrena ili nije Klodov nacrt.` or `Grupa nije prazna.`; `potvrdi=false` computes the same outcome without locks or writes.
  MCP `ponisti_seriju` (`McpBanka.ponistiSeriju`) joins it with `PrezentacijaService.ponistiSeriju` into one `PonistavanjeInfo`.
  **No slide outside the series is ever deleted:** a never-run presentation of the series goes whole only while every slide in
  it belongs to the series; one with any other slide (Novica added or duplicated it in the UI, or another series added it)
  stays, is skipped with `Prezentacija ima Novicine slajdove.`, and only the series' slides in it go. A run presentation of the
  series is skipped whole (`Prezentacija je izvođena.`, or `Prezentacija se upravo izvodi.` while a run is active); the
  series' slides in other presentations go one by one and are renumbered, except while that presentation is being run
  (skipped, `Prezentacija se upravo izvodi.`). `potvrdi=false` applies the same rules without locks or writes. `GET /banka/pretraga` takes `izvor` and `serija` (bad
  `serija` 400); tree nodes carry `izvor` and `brojClaudeNacrta`, groups and variants `serija`, variants `ikadOdobrena` and
  `zamenjuje`.
- **Excluded topics (`iskljucene_teme`, `IskljuceneTemeService`):** a topic with a group excludes that group and its whole
  subtree (`GrupaCvor`/`GrupaDetails.iskljucena`, `iskljucenjeId` = topic of the nearest such node, own before ancestor); a topic
  without a group only describes something Claude must not create. `GET|POST /predmeti/{id}/iskljucene-teme`, `DELETE
  /iskljucene-teme/{id}`. At most one topic per group (DB `UNIQUE(predmet_id, grupa_id)`, 409 `Ova grupa je već isključena.`)
  and one group-less topic per normalized description (strip, spaces collapsed, case-insensitive; checked under the subject
  lock, 409 `Ova tema je već isključena.`), so a repeated `iskljuci_temu` is 409, never a second row. Deleting a group keeps its
  topic without a group (FK `SET NULL`).
- **Presentations for Claude (`PrezentacijaService`):** `kreiraj(cmd, serija)`, `dodajSlajdIzSerije` (types only from
  `McpPrezentacije.DOZVOLJENI_TIPOVI` = INFO, PITANJE, else 400 `Klod u ovoj verziji dodaje samo INFO i PITANJE slajdove.`;
  under the presentation lock 409 `Prezentacija se upravo izvodi; izmene posle časa.` while a run is active), `ponistiSeriju`,
  and `slajd(id)` behind the new **`GET /slajdovi/{id}`** (full `SlajdDetails` with strokes, 404 `Slajd nije pronađen.`; the
  front uses it from A2; the MCP `slajd` tool strips the strokes). Duplicating a slide or presentation does not copy the series
  (the copy is Novica's). Homework texts as a source: `DomaciService.tekstoviPredmeta` (`DomaciTekst`, newest first).
- **Markdown images only from GFS:** an image in INFO `sadrzaj`, PITANJE text or an option text may only target
  `api/public/mediji/<uuid>` of an existing medium of kind `SLIKA` (`SlajdPP.slikeSlajda`/`slikeIzMarkdowna`: inline and
  reference images and their definitions; `PrezentacijaService` checks existence and kind in one query), else 400 `Slika u
  tekstu mora biti otpremljena u GFS.`. Applies to REST (`PUT /slajdovi/{id}`, `POST /prezentacije/{id}/slajdovi`, import) and
  MCP alike; the front drops any other image and its CSP allows `img-src 'self' data: blob:`.
- **Upload (`McpUploadRest`):** files never go through tool arguments; the wrapper script `gfs-mcp-upload` posts multipart
  `fajl` + `serija` to `POST /mcp/prilozi` (task attachments: PNG/JPEG -> `PRILOG_SLIKA`, PDF -> `PRILOG_PDF`) or
  `POST /mcp/slike` (slide images, `SLIKA`), behind the same key filter, -> 201 `{medijId, vrsta, mime, velicina}`. Type by magic
  bytes (declared MIME and name are ignored), <= 10 MB, budget charged before the write, bad `serija` 400 before the file is read.
- **Schema (`V12__mcp_banka.sql`; the number is provisional, see "Database"):** `grupe_zadataka.izvor` (`varchar(20) NOT NULL
  DEFAULT 'RUCNO'`) and `serija`; `varijante.ikad_odobrena` (`tinyint(1) NOT NULL DEFAULT 0`, set to 1 for rows already AKTIVNA or
  ARHIVIRANA), `zamenjuje_id` (FK to `varijante`, `ON DELETE SET NULL`) and `serija`; `prezentacije.serija`, `slajdovi.serija`
  (every `serija` nullable `varchar(60)` with an index); table `iskljucene_teme` (`predmet_id` FK cascade, `opis` <= 300,
  `grupa_id` FK `SET NULL`, `izvor`, `serija`, `kreirano`). Additive with defaults in the DDL, so the previous image starts and
  writes over it (`flyway-provera.sh` scenario `rollback`).
- **Tests:** `McpTestKlijent` (JSON-RPC over MockMvc with the synthetic key `gfs_TEST000...`: `initialize`, `alati`, `pozovi`,
  raw `posalji`; static readers of text, JSON, `isError`, images) and `McpDbTestOsnova` (`@SpringBootTest` without
  `@Transactional`, `@MockitoBean LatexRender`, MockMvc without printing (it would print the header on a failure), JDBC cleanup
  of everything belonging to the subjects from `noviPredmet`). On MySQL: `McpOmotacDbTest`, `KontekstAlatiDbTest`,
  `BankaAlatiCitanjeDbTest`, `BankaAlatiGrupeDbTest`, `VarijanteAlatiDbTest`, `PrezentacijaAlatiDbTest`, `McpUploadDbTest`,
  `McpAlatiTest`, `McpDnevnikDbTest` (log without secrets), `McpKrajNaKrajDbTest` (`initialize` with "č", `tools/list` =
  `McpAlatiTest.OCEKIVANO`, then `kontekst` -> `napravi_grupu` -> `napravi_varijantu` -> `izmeni_varijantu` on Novica's variant
  (copy) -> `ponisti_seriju` -> `pretraga_banke` by series), `McpIskljucenIT` (no keys: 404 even with a once valid key) and
  `McpRuteIT` (real Tomcat: no route answers 200 without a key, also `/mcp/`, `/mcp;x=1`, `/%6Dcp`, `//mcp`, `/MCP`). Without a DB:
  `McpKljucFilterTest`, `McpKljuceviTest`, `McpPatchTest`, `McpBudzetTest`, `McpGreskeTest`, `McpOpsegTest`, `McpFasadeTest`,
  `McpKonfigTest`, `McpUkljucenjeTest`, `McpLinkoviTest`, `McpSlikaTest`, `PrezentacijaPregledTest`, `VodicTest`. The domain parts
  have their own tests: `V12MigracijaDbTest`, `BankaIzvorDbTest`, `VarijantaServiceMcpDbTest` (copy, 409 without a render under
  the lock), `GrupaZadatakaMcpDbTest`, `KopijaGrupeDbTest`, `SerijaServiceDbTest`/`SerijaServiceTest`, `IskljuceneTemeDbTest`,
  `PrezentacijaServiceMcpDbTest`, `PrezentacijaSlikeDbTest`, `SlajdPPMarkdownTest`, `DomaciTekstoviDbTest` and the
  `@WebMvcTest`s `IskljuceneTemeRestTest`, `SerijaRestTest`, `SlajdRestTest`; `IstiZahtev` runs several service calls on one
  `EntityManager`, like open-in-view in a facade's request. Parallel
  worktrees each use their own database on the throwaway MySQL (e.g. `gftest_mcp`) and their own `DB_PREFIX`/`APP_PORT` for
  `flyway-provera.sh`, so they never drop each other's data.

## Prijava (teacher login, student account without a password, Moodle LTI)

Spec: `docs/superpowers/specs/2026-10-19-prijava-design.md` in the wrapper repo `Novke/GFS-deploy`. The migration is
`V14__prijava.sql` (it only creates tables, all idempotent). Code lives in `security/`, `service|rest|dto|entity|repository/prijava/`, `.../lti/`,
`service/mejl/` and `config/{NastavnikSvojstva,OtvorenEntityManagerConfig}`.

- **Access classes:** `security/PristupMatrica` is the single source of truth: `ANONIMNO`, `STUDENT` and `MCP` are listed,
  **everything else is ADMIN** (a forgotten endpoint is locked, not open). `SecurityConfig` builds the chain rules from it
  (STATELESS, no `HttpSession`, no CSRF token: `ProveraPoreklaFilter` checks `Sec-Fetch-Site`/`Origin` against `gfs.front.url`
  on POST/PUT/PATCH/DELETE; the LTI login and launch are the only exemptions). `PristupMatricaTest` compares every real
  endpoint (including `RouterFunction` routes and the two WebSockets) with the snapshot `src/test/resources/pristup-matrica.txt`:
  **a new endpoint must be added to the snapshot** (the test writes the sorted proposal to `target/pristup-matrica.predlog.txt`;
  rows are sorted by pattern, then method). Nothing teacher-only may live under `/public/**`.
- **Cookies** (`security/Kolacici`; value = 32 chars from `TokenGenerator`, the DB holds only `TokenHash.od(token)`; `HttpOnly; Path=/`,
  `Secure` when `request.isSecure()`): `gfs_nastavnik` `Lax` 90 days, `gfs_student` `Lax` 365 days for a code (`KOD`) device and
  `gfs.nalog.lti-trajanje` (default `PT12H`) for an `LTI` device, `__Host-gfs_lti` `None` (always `Secure`) 600 s, `__Host-gfs_lti_par`
  `Lax` 1800 s (`__Host-` names are always `Secure; Path=/` without `Domain`, also on delete, so a sibling `*.trif.rs` host cannot
  plant a pairing token; the `Secure` rule is in `Kolacici.dodaj`). `GfsAutentikacijaFilter` tries **every** value of a cookie name (a sibling `*.trif.rs` host can
  plant a second one) and the teacher wins over the student. Sessions and devices are extended at most once per 10 minutes; the renewed `gfs_student` cookie gets the lifetime of the device's
  own `nacin` (`StudentskaPrijava.trajanje`, **sliding**: LTI = 12 h from the last use, so a school day of activity keeps it alive and a
  lab PC left overnight is logged out; the Moodle launch is one click away; KOD = 365 days, the student's own phone). The filter does
  not write or delete cookies on `GET|HEAD /public/mediji/**` (publicly cacheable responses must not carry `Set-Cookie`; the cookie is
  renewed or deleted on the next ordinary request).
- **Teacher:** `AuthRest` (`POST /auth/prijava`, `GET /auth/ja`, `POST /auth/odjava`, `GET /auth/sesije`, `DELETE /auth/sesije/{id}`,
  `POST /auth/sesije/odjavi-sve`), user and bcrypt hash from `GFS_NASTAVNIK_KORISNIK` / `GFS_NASTAVNIK_LOZINKA_BCRYPT` (empty =
  login not configured, 503-style message; a non-bcrypt value fails the start), `GFS_NASTAVNIK_MEJL` = address for notification mail.
  10 failed attempts per 15 minutes per IP lock that IP for 15 minutes (`pokusaji`). A login from a browser that already holds a valid teacher session revokes that old session after the new one is created (no orphan sessions; a failed login revokes nothing). IP comes from Tomcat's `RemoteIpValve`, not from headers.
- **`IdentitetStudenta` seam is live** (`security/`; interface, `NemaIdentiteta` and `IdentitetAutoKonfig` come from the Zadatak slide,
  see "Zadatak slide"): `NalogIdentitet` (`@Component`, `@Primary`) replaces `NemaIdentiteta`, which the auto-configuration
  (`@ConditionalOnMissingBean`) then never creates, so the context has exactly one `IdentitetStudenta` (`NalogIdentitetIT`). It gives
  the student of a valid `gfs_student` device and `dostupno()` is true, so "Boduje se" works: the teacher starts a scored run, the join
  `POST /public/uzivo/{kod}/prijava` takes the student from the cookie (typed name ignored), the same student on a second phone is the
  same participant, and without a valid cookie a scored run is 403 `Potrebna je prijava studenta.` (unscored runs need no login;
  `BodovanoUzivoIT`). The teacher never counts as a student (403 there even with a student cookie). **BE-8 is still not in the
  code:** a scored run records no attendance or task activities on the lecture.
- **Student account** (`/public/nalog/*`, anonymous or STUDENT; `kod` and `prijava` answer 409 `Odjavi se kao nastavnik.` when the request carries a valid
  teacher session, because the teacher wins in `GfsAutentikacijaFilter` and a student login would end in a 403 loop; checked before any cost): `KodServis` (code = 6 digits from `SecureRandom`, 10 min, single
  use, 5 wrong entries kill it, a new code voids the old ones; DB keeps `SHA-256(salt + code)`), `StudentskaPrijava` (devices),
  `MaskaMejla` (real mask, or a deterministic fake mask from an HMAC for unknown index / student without mail),
  `PokusajiServis` (counters in table `pokusaji`, survive a restart), `KljuceviAplikacije` (HMAC keys in `kljucevi_aplikacije`,
  created on first use). Limits: send 3/h per student (also per unknown index) and 50/h per IP (a classroom behind one NAT address), wrong code 20/h per IP, "Ne stiže
  mi kod" 1/h per index and 10/h per IP, card code 20/h in total, teacher mail 1/h per (kind, index) and 20/h in total,
  `PREVISE_KODOVA` at most 1 per 24 h per student. Over a limit: 429 `Previše pokušaja. Pokušaj ponovo kasnije.`.
  - **The answer must not reveal whether an account exists:** same body for a student, a student without mail and an unknown
    index; the index is normalized and must match `^[A-Z0-9/.-]{1,30}$` **before** any lookup, and is then compared byte-exact in
    Java (the `utf8mb4_0900_ai_ci` collation equates accents and full-width letters, which would give a variant of a real index
    a real mask and a shared counter). Anything off the whitelist behaves like an unknown index. Counter keys for an index are
    HMACs (`i:<hmac>`; mail counters `<KIND>:i:<hmac>`), never a bare SHA-256 or the plain index.
  - **Latency floor** (`gfs.nalog.minimalno-trajanje-ms`, default 200 ms, 0 in tests; monotonic clock): `posalji`, `proveri` and
    "Ne stiže mi kod" never answer faster, so a few extra writes for a real student do not show in the response time. Exceptions
    (they say nothing about the index): 503 (mail not configured) and 429 per IP return at once. The wait happens **outside any
    transaction**, and the public-path methods of `KodServis` and `ObavestenjeServis.neStizeKod` have no `@Transactional`: every DB step is its own short transaction (`READ COMMITTED`).
  - **OSIV is off for `/public/nalog/**` and `/public/lti/**`** (`OtvorenEntityManagerConfig`, `spring.jpa.open-in-view=false`
    replaced by an interceptor for all other paths): the floor would otherwise hold a pooled connection (Hikari 10) per anonymous
    request. Services for these paths return DTOs built inside their own transaction.
  - **Concurrency:** requests for the same index are serialized by a MySQL named lock on the transaction's own connection
    (`GET_LOCK('gfs-kod:<hmac>')` for sending, `gfs-pomoc:<hmac>` for "Ne stiže mi kod", released in `afterCompletion`; the real lock name is
    prefixed with the schema, `gf:gfs-kod:...`, because MySQL user locks are server-global and prod `gf` and `gf_staging` share
    `shared-mysql`: `KodServis.imeBrave` / `ImenaBrava`, schema from `SELECT DATABASE()` or `gfs.prijava.brava-prefiks`, shortened
    to fit MySQL's 64 characters), then the
    student row (`SELECT ... FOR UPDATE`). **Lock order: index lock (pomoc/kod) -> student row -> mail lock `gfs-mejl-nastavniku`**
    (the global 20/h mail counter is checked and written under it). Wait times: `KodServis.CEKANJE_BRAVE_S` 2 s (the wait holds a pooled connection, Hikari default 10; both index locks use it, a timeout is the existing 429 / silent skip) and
    `ObavestenjeServis.CEKANJE_BRAVE_MEJL_S` 5 s (volatile fields with package-private setters for tests, see `PristupBravama`);
    `previseKodova` holds the student row, so it waits for the mail lock at most 1 s; if the lock is not obtained in that time the teacher mail for that `PREVISE_KODOVA` is skipped (the notice stays) and, because of the 24 h rule, it is not retried.
  - **Mail** (`service/mejl/`): `MejlServis` (`SmtpMejlServis`, `LazniMejlServis` in tests) publishes a `MejlPoruka` event; `MejlPosleCommita`
    sends it **after commit, asynchronously** (`mejlIzvrsilac`, 2 threads, queue 200, drops with a log when full; 3 attempts,
    pauses `gfs.mail.pauze-ms`). SMTP is configured by `GFS_MAIL_HOST/PORT/USER/PASSWORD/STARTTLS/FROM`; empty host or from =
    sending off, and `POST /public/nalog/kod` is 503 `Slanje koda trenutno nije moguće. Javi se nastavniku.` for every index.
    Mail bodies and logs never carry a code, address or name.
- **Notifications** (`ObavestenjeServis`, `ObavestenjeRest` `/obavestenja`, ADMIN): `NE_STIZE_KOD` (public `POST /public/nalog/pomoc`, 1/h per
  index, silent when limited; `podaci` = typed normalized index, `imaMejl`, `ip`, `uredjaj` `ZAPAMCEN|NEPOZNAT`) and `PREVISE_KODOVA`.
  `KodServis.posalji` only publishes `DostignutLimitKodova(studentId, ip)` when the per-index limit is hit for a real student;
  `ObavestenjeServis.naDostignutLimitKodova` handles it `AFTER_COMMIT` + `@Async("obavestenjaIzvrsilac")` (2 threads, queue 100,
  drops with a log when full; `ObavestenjaConfig`), so the 429 does no extra DB work for a real student. Teacher mail
  (`GFS_NASTAVNIK_MEJL`): fixed subjects, no name; **text typed into the public form never goes into the mail**: a known student
  gets the stored index and the card link `studenti/<id>`, an unknown index gets the fixed sentence `NEPOZNAT_INDEKS_TELO`. The
  global 20/h cap can be used up by "Ne stiže mi kod" for unknown indexes from two IPs (accepted: the in-app notification stays).
- **Teacher card "Nalog"** (`StudentNalogRest` `/studenti/{id}/nalog`, `StudentNalogServis`; the student is chosen by id, so the public index
  whitelist does not apply, and an unknown id is 404 `Ne postoji.`): `GET` (`imaMejl`, valid devices, `moodle {povezan, poslednjiUlaz}`,
  `poslednjaPrijava` = last use of any device incl. revoked), `POST posalji-kod` (204; `KodServis.posaljiOdNastavnika`: no per-student limit, 20/h
  in total, 409 `Student nema mejl.`, 503 without SMTP; it **must not run inside an outer transaction** (`Propagation.NEVER`),
  publishes no `DostignutLimitKodova`, and calls `ObavestenjeServis.resiZaStudenta` **last**, after the send committed, because that bulk
  update uses `clearAutomatically`; a failure of the resolving is only logged), `POST opozovi-uredjaje` (`{"opozvano": n}`), `DELETE moodle` (deletes the
  student's `spoljni_identiteti` and revokes their `LTI` devices, so a wrongly linked Moodle account does not stay signed in as them; `KOD`
  devices stay). A student whose index is off the whitelist cannot sign in with a code at all: the card logs a warning.
- **Cleanup** (`CiscenjePrijave`, `@Scheduled(cron = "${gfs.prijava.ciscenje.cron:0 7 * * * *}")` (hourly at :07; `-` disables it, the test properties do, and `CiscenjePrijaveIT` calls `ocisti()` directly), batches of 5000): `pokusaji` > 2 days, `kodovi_za_prijavu` > 1 day, `lti_stanja`
  and `lti_povezivanja` expired (or the pairing used) > 1 hour ago (each `GET /public/lti/prijava` writes a state row and has no app
  rate limit), expired or revoked `nastavnicke_sesije` / `studentski_uredjaji` > 30 days, resolved `obavestenja` > 180 days (open ones never). The 1/h
  teacher-mail rule reads `MEJL_NASTAVNIKU*` rows and the 1/24 h `PREVISE_KODOVA` rule reads resolved notifications, both survive far longer than their windows.
- **Moodle LTI 1.3** (`service/lti/`, `rest/lti/`, `/public/lti/*`; teacher API `GET|POST|PUT|DELETE /lti/registracije`, `GET /lti/registracije/vrednosti`):
  - `GET|POST /public/lti/prijava` (OIDC login initiation, ANONIMNO) answers **302** to the platform's auth URL (or to `/nalog/moodle?greska=<kod>`);
    `POST /public/lti/start` (launch, form `id_token` + `state`) answers **303** to `/nalog` (linked student, new `LTI` device), `/nalog/moodle`
    (pairing needed, cookie `__Host-gfs_lti_par`), `/nalog/moodle?uloga=nastavnik` or `/nalog/moodle?greska=<kod>` (`nepoznata-registracija`, `istekla-sesija`,
    `bez-kolacica`, `neispravan-token`, `nepodrzana-poruka`); never a 500 for a bad request and never a sign-in without a link.
    `GET /public/lti/povezivanje` (ANONIMNO, read-only, needs `__Host-gfs_lti_par`: `{ime}` or 410) and `POST /public/lti/povezi` (STUDENT + `__Host-gfs_lti_par`: 204, 409
    `Ovaj Moodle nalog je već povezan sa drugim studentom. Javi se nastavniku.`, 410, 401). `GET /public/lti/jwks` serves the tool's public key.
  - **State and cookies:** `state` and `nonce` are two separate random values; the DB keeps `SHA-256(state)`, the plain nonce, `SHA-256(__Host-gfs_lti)` and the
    registration, valid 10 minutes. The launch consumes the state **atomically and first** (conditional `UPDATE`), so it is single-use even when the launch then
    fails. `__Host-gfs_lti` (`SameSite=None; Secure; HttpOnly; Path=/`, no `Domain`, as the `__Host-` prefix requires; 600 s) binds the browser; without it the launch is `bez-kolacica` (a browser that blocks third-party cookies gets a
    clear message with a link to `/nalog`). `__Host-gfs_lti_par` (`Lax`, 1800 s, `Secure` always) carries the pairing token (hash in `lti_povezivanja`, the Moodle display name for the screen, with control, format/bidi, separator, private-use and unassigned characters removed and whitespace collapsed).
  - **Token checks** (`LtiProvera`): RS256 only, `iss`, `aud` (+ `azp` rules), `exp` and `iat` required (60 s leeway), `nonce`, `deployment_id`, `sub` (must be a JSON string, 1-255 chars; checked on the raw claims too), message type
    `LtiResourceLinkRequest`, version `1.3.0`; roles: Instructor/Administrator -> teacher message (no cookie), Learner -> student flow, anything else `nepodrzana-poruka`.
  - **JWKS fetch** (`LtiDohvatKljuceva`): https only (http and loopback only with `gfs.lti.dozvoli-http=true`, forbidden with profile `server`), connect 3 s / read 5 s,
    body <= 64 KB, **no redirects**, every resolved address checked against blocked ranges (loopback, link-local incl. 169.254.169.254, RFC1918, CGNAT/tailnet, ULA,
    multicast, NAT64 ...), cache 5 minutes per URL, refresh on unknown `kid` rate-limited. The tool's own key is `lti_kljucevi` (`LtiKljucServis`).
  - **CORS exemption:** a launch is a cross-site `form_post` carrying `Origin: https://moodle...`, which the chain's `CorsFilter` and the MVC CORS mapping
    (`gfs.front.url` only) would reject with 403. `CorsConfig.bezLti` returns no CORS configuration for `ProveraPoreklaFilter.LTI_IZUZECI` (login and launch only), in the chain
    and in MVC; every other path is unchanged. The two are protected by `state`, `nonce` and `__Host-gfs_lti` instead of an origin check.
- **Index in three parts: (index, year)** (branch `feature/prijava-indeks-mejl`, `V16__indeks_telefon_kod.sql`): an index number repeats every
  generation, so a student is the pair (canonical index, `studenti.godina`) and is **never** "the newest generation". study programmes are the enum
  `utility/Smer` (`GEODEZIJA("GD", "Geodezija")`, the only one for now; a new programme = a new constant here plus the same row in the front
  `SMEROVI` in `shared/util/indeks.ts`; no DB table, the programme lives only inside the index; the forms show `GD` locked, no picker).
  `utility/IndeksUtil`: `kanonski(indeks, godina)` =
  normalized, a `/YYYY` suffix equal to `godina` stripped, leading zeros of the number dropped (`gd 05/2026` + 2026 -> `GD5`),
  `jeJavniOblik` = a `Smer` code + 1-3 digits. The public requests `KodCmd`, `PrijavaKodomCmd`, `PomocCmd` carry `{indeks, godina}` (`godina`
  2000-2100, else 400 `Godina upisa nije ispravna.`); `KodServis` finds the student with `StudentRepository.findAllPoIndeksuIGodini`
  (canonical compare in Java) for sending a code, signing in with a code, "Ne stiže mi kod" and the Moodle pairing (which signs in with a
  code). A new-form index that is not `jeJavniOblik` behaves like an unknown index (same answer). Counters, locks and the fake mask are
  keyed by the identity `index/year`. **PRIVREMENO (remove after the first prod release of this branch plus one week, when no cached SPA
  sends the old body):** a request without `godina` is the old single-field form: a `/YYYY` suffix is the year (`IndeksUtil.razdvoji`),
  without one the old behaviour stays (newest generation, `findAllPoNormalizovanomIndeksu`); marked `PRIVREMENO` in `IndeksUtil`,
  `KodServis`, `StudentRepository` and the three DTOs' legacy constructors.
- **Dictated code (plan C):** `POST /studenti/{id}/nalog/kod-za-diktiranje` (ADMIN, in the access snapshot, never under `/public/**`) returns
  `KodZaDiktiranjeInfo {kod, istice, indeks, godina, upozorenje}` once with `Cache-Control: no-store`, sends no mail, works for a student
  without mail and without SMTP, 60/h in total (`VrstaPokusaja.KOD_DIKTIRAN`, 429), 404 for an unknown id. Same table, hash, 10 minutes,
  5 wrong tries and single use as the mail code; column `kodovi_za_prijavu.nacin` (`NacinKoda` `MEJL`|`NASTAVNIK`, V16, default `MEJL`).
  **Relation to the mail code:** a new code voids only active codes of the **same** `nacin`, so "Pošalji ponovo" on the phone never kills a
  code the teacher just dictated (and a new dictated code does not kill the mailed one); a sign-in checks every active code of the student,
  marks the hit one used and voids all others; a wrong entry counts against every active code. Issuing resolves the student's open
  notifications (`StudentNalogServis.kodZaDiktiranje`, `Propagation.NEVER`, resolving last) and is logged with the student id only, never the code.
  `upozorenje` is set when the stored index is not `GD` + 1-3 digits (the student could not type it into the form).
- **Mail look** (`service/mejl/MejlPoruke`, `MejlSablon`, `SmtpMejlServis`): `multipart/alternative` (plain text first, then simple inline-styled
  HTML in a 480 px table), `From` with the display name `gfs.mail.ime` (default `GFS · Građevinski fakultet Subotica`) and `GFS_MAIL_FROM`, `Date`
  from the `Clock`, `Message-ID` `<uuid@host-of-the-first-https-front-url>`, **no `Reply-To`** (a different freemail Reply-To scores as spam, and
  replies to the sending address are fine). Subject `Kod za prijavu u GFS` (constant, never the code: it would show on a lock screen). The
  code mail greets the student by first name (control characters removed, 40 chars max, HTML-escaped), shows the code large, the validity,
  why the mail came (form or teacher) and what to do if it was not requested, plus the faculty signature; the logo is a **hosted** https
  image (`<front>/assets/images/mejl-logo.png`, 66x96, alt `GFS`; no image when the front URL is not https). Teacher notification mails
  keep their text and get the same HTML frame.
- **Logs:** IP and ids only; never a name, mail address, code, token, password or LTI claim value; an index only as the first 8 hex of its SHA-256 (`KodServis.trag`).
- **Tests:** database `gftest_prijava` on the throwaway MySQL (3307): `SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/gftest_prijava ./mvnw -B package`.
  Tests of the security chain use `@SpringBootTest @AutoConfigureMockMvc` with the real filters (`PrijavaTestUtil.kaoNastavnik(...)` gives a teacher cookie plus the
  `Origin` header); **business-logic ITs that are not about access use `@AutoConfigureMockMvc(addFilters = false)`** (the existing service ITs). Helpers in
  `service/prijava` tests: `LazniMejlServis` (fake mail), `PrijavaSatConfig`/`PomerljivSat` (movable `Clock`), `TestStudenti` (invented students; `obrisi()` deletes
  `pokusaji` **only by the student-id key**, so tests that create HMAC-keyed rows track and delete their own keys), `PristupBravama` (lock names and wait times).
  `scripts/flyway-provera.sh` hardcodes `fw_<scenario>` databases: on a shared box run a copy that uses `fw_prijava_<scenario>` and `APP_PORT=18092`.

## Čekiranje (student check-in by QR, studentski deo faza 1)

Spec `docs/superpowers/specs/2026-10-28-studentski-portal-design.md` (deploy repo), section 4; plan next to it. Packages `entity|repository|service|rest|dto/cas`.
- **Tables (V17):** `cekiranja` (one row per lecture and student, `vreme` = first scan, never changed; `uredjaj_hash` = SHA-256 of cookie
  `gfs_uredjaj`), `cekiranja_na_cekanju` (one-time key of a student who scanned before logging in: SHA-256 only, 15 min, `iskorisceno`,
  `uredjaj_hash` of the scanning phone),
  `predavanja.cekiranje_tajna` (32 chars from `TokenGenerator`, `@JsonIgnore`, never leaves the server) and `cekiranje_tajna_od`.
- **Token (`CekiranjeToken`, pure static logic):** `<predavanjeId>.<h>`, `h` = first 22 chars of `base64url(HMAC-SHA256(tajna, id + ":" + w))`,
  `w = floor(epochSeconds / 10)`; windows `w`, `w-1`, `w-2` are accepted (20-30 s), compared with `MessageDigest.isEqual`. The secret is created by
  the teacher view (`GET /predavanja/{id}/cekiranje`) with a compare-and-set update (`PredavanjeRepository.postaviTajnu`), renewed when older than
  5 h, and cleared by `zavrsiPredavanje`.
- **`CekiranjeService`** (`READ_COMMITTED`): `skeniraj(token, studentId?, uredjajHash)` (logged in: check-in + class state; anonymous: a key),
  `potvrdi(kljuc, studentId, uredjajHash)` (consumes the key under `findByKljucHashForUpdate`, **only from the phone that scanned** (same
  `gfs_uredjaj` hash, else 410 and the key stays), so a key posted to a group chat checks nobody in; the check-in gets the scan time and the scan
  device; a new anonymous scan from the same phone deletes that phone's unused keys for the lecture, one pending key per phone), `stanje(studentId)`
  (latest check-in of the last 5 h, finished lecture or not; the newest active run of a live lecture the student was not expelled from),
  `zaNastavnika(predavanjeId)` (token, window end, list with "kasnio" > 15 min after the first check-in and "isti telefon kao"). Group rule:
  `Utility.smeNaNastavuGrupe`; a lecture without a group admits nobody. A check-in adds `PRISUSTVO` only when the student has no activity on the
  lecture (raise, never lower) and bumps `posecenost`.
- **Lock order:** check-in and every manual activity change in `PredavanjeService` (`dodajPrisutnog`, `skloniPrisutnog`, `dodajZadatak`,
  `skloniZadatak`, `dodajZadatakSaZvezdicom`, `updateAktivnostNapomenu`, `zavrsiPredavanje`, and `updatePredavanje`, `updatePosecenost`, `obrisi`,
  because Hibernate writes the whole row and a stale entity would reopen a finished lecture or restore an old secret) lock the lecture row first; check-in **never** locks a run
  row. A manual add of a student who is already present returns the lecture unchanged (no more 400 "vec zabelezeno"); removing attendance deletes the
  check-in too (the phone stops following the class).
- **Uživo:** `UcesnikService.prijavi` gives a logged-in student 403 `Izbačen si iz ovog izvođenja.` when they have an expelled row in that run, and,
  for a scored run with a lecture, 403 `Prvo se čekiraj: skeniraj QR sa table.` without a check-in on that lecture in the last 5 h (also for an
  existing participant); order: `Potrebna je prijava studenta.`, then expelled, then check-in. `NastavnickoStanje` ends with `brojOcekivanih`
  (non-expelled participants plus students on class who are neither participants nor expelled; without a lecture just the participants) and
  `brojCekiranih` (`null` without a lecture). `JavnoStanje` and every phone snapshot are unchanged.
- **REST:** `CasPublicRest` `/public/nalog/cas`: `POST skeniraj` ANONIMNO (409 `Odjavi se kao nastavnik.` with a teacher session), `POST potvrdi`
  and `GET ""` STUDENT; `skeniraj`/`potvrdi` set `gfs_uredjaj` (`Kolacici.UREDJAJ`, 365 days, `Lax`) when missing. `CekiranjeRest`
  `GET /predavanja/{id}/cekiranje` ADMIN. Messages: `QR je istekao. Skeniraj ponovo sa table.` (410), `Čekiranje za ovaj čas je završeno.` (410),
  `Nisi u grupi ovog predavanja. Javi se nastavniku.` (403), `Čekiranje je isteklo. Skeniraj QR ponovo.` (410). Cleanup: `CiscenjePrijave` deletes
  keys expired or used more than 1 h ago.
- **Tests:** `V17MigracijaDbTest`, `CekiranjeTokenTest`, `CekiranjeServiceDbTest` (incl. parallel double tap), `PredavanjeAktivnostiDbTest`
  (lock wait), `CekiranjeUzivoDbTest` (expelled, scored run, `brojOcekivanih`), `CasPublicRestIT` (whole chain, cookie, no secret in JSON); shared
  JDBC fixtures `service/cas/CasPodaci` (times always from Java, never MySQL `now()`).

## Student account, phases 2 and 3 (subjects, history, practice, presentation review, grades)

Branch `feature/studentski-portal-2` (as of 2026-10-29 not on staging). Spec: deploy repo
`docs/superpowers/specs/2026-10-28-studentski-portal-design.md`, sections 5, 6, 7 and **12** (revision 2026-10-29, wins where it differs).
Package `portal` in `entity`, `repository`, `service`, `rest`, `dto`.
- **Who is the student:** only `StudentAutentikacija` (session). His groups only from `service/portal/GrupeStudenta.za` (own group plus groups
  of lectures where he has an activity or a check-in: a repeater sees the younger generation); predmeti = subjects with a lecture of one of
  those groups (`KontekstStudenta`). Never read `student.grupa` in a student query. Anything foreign or missing is the same **404
  `Nije pronađeno.`** (`KontekstStudenta.nema()`), never 403.
- **Opened for practice (`otvorene_teme`, V19):** `TemeZaVezbanje.krajIzvodjenja` is called by `IzvodjenjeService.zavrsi` (ZAVRSI and the 12 h
  auto end) in the same transaction, **before** the "Ne čuvaj" deletion: the leaves of the variants actually drawn in the run's ZADATAK rounds
  (`ZadatakSnimak.varijante[].grupaId`) open for the run's group (`CAS`, insert "if missing" via `INSERT ... SELECT ... ON DUPLICATE KEY`, so a
  deleted leaf never fails the end). Never at round opening (a student would spin "next task" until his class variant). Manual
  (`RUCNO`): `PUT|DELETE /banka/grupe/{id}/otvoreno/{grupaStudenataId}`, `GET /banka/grupe/{id}/otvoreno` (panel per student group: OTVORENO,
  PREKO_TEME, DELIMICNO, ZATVORENO); closing deletes the records of the node **and its whole subtree**; an open ancestor keeps it open. Open
  leaves = for each record of the student's groups, `StabloPredavanja.opis(node).listovi()` (same exclusion rule as the class draw; the node
  itself is never skipped). Only leaves are practised.
- **Shown slides (`prikazani_slajdovi`, V19):** `IzvodjenjeService.idiNa` calls `TemeZaVezbanje.prikazan` for runs with a group (insert "if
  missing", first run kept). A slide is visible to the group only when that run is `ZAVRSENO` (or deleted: `izvodjenje_id` SET NULL).
- **Practice (`VezbanjeStudenta`, table `vezbanja`):** `POST /public/nalog/vezbanje {tipId, nivo?}` draws from `KandidatiZadatka.kandidati`
  (AKTIVNA with SVG, `Random` bean), avoiding the student's last 5 variants of that leaf when there is a choice (and the last one when possible);
  returns SVG + schema **without** answers (`ZadatakVezbanja`). `provera` grades with `OcenaZadatka.oceni` over the **current** variant and
  schema (same 400 messages as in class) and returns only true/false per field; `resenje` returns `resenjeSvgId` and `tacneVrednosti`. Both go
  through `pokusajId` + the session student (`findForUpdate`), and re-check that the variant is still AKTIVNA and its leaf still open (else 404).
  409 `Za ovaj nivo nema zadataka.` / `Ovaj tip trenutno nema zadataka.`. No points, no activities.
- **History (`IstorijaStudenta`):** my tasks from class = `zadatak_dodele` of participants with `student_id` = me in `ZAVRSENO` runs, everything from
  the round snapshot (solution shown even if not released in class); review = current slide version, only visible slides, DTO `SlajdPregleda`
  without `beleske`/`serija`/`izvor` (PITANJE carries the correct answer: run ended, decision 12.9/8; PLATNO without strokes); "Tvoj odgovor" from
  the student's own last answer.
- **Grades (`ObjavaOcenaServis`, tables `objave_ocena` + `objavljene_ocene`):** nothing about grades reaches a student until the teacher
  publishes for subject + group (`PUT /ocenjivanje/predmet/{p}/objava/{g}`; `GET ...?grupa=` state; `DELETE` withdraws). Publish = snapshot per
  student from `OcenjivanjeService.getRezultati` (`SnimakOcene` JSON: proposal, total, pre-exam, activity, homework, points per test type);
  re-publish replaces the group's snapshot. A student sees only his own row of his **own** group's publication.
- **Statistics:** `GET /predmeti/{id}/vezbanje/statistika?grupa=&od=&do=` (`StatistikaVezbanja`), aggregate per leaf only.
- **Student paths** (all STUDENT in `PristupMatrica`, OSIV off under `/public/nalog/**`, DTOs built in the service transaction): `GET
  /public/nalog/predmeti`, `GET /public/nalog/predmeti/{id}/{prisustvo,vezbanje,moji-zadaci,prezentacije,ocena}`, `GET
  /public/nalog/prezentacije/{id}`, `POST /public/nalog/vezbanje`, `POST /public/nalog/vezbanje/{pokusajId}/{provera,resenje}`; `GET
  /public/nalog/ja` now also has `grupa` and `generacija`.
- **Tests:** `PortalDbTest` (three students: two groups and a repeater; nothing open while the run lasts, only drawn leaves, Ne čuvaj, manual
  open/close, practice + IDOR, grades, the V19 first fill run twice, forbidden fields in every student JSON), `NalogPortalRestIT` (whole chain:
  401, 404 text, origin check, teacher paths refuse a student session).

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
  (empty root password, DB `gftest`), `scripts/provera-migracija.sh` against the PR base (see "Database"), `./mvnw -B package` (all tests,
  including the `*IT` classes against real MySQL with the Flyway schema), then `docker build` and `bash latex-render/test/smoke.sh`.
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
  (teacher notes). **V2-V4 and V6-V13 are idempotent** (`CREATE TABLE IF NOT EXISTS`; columns and indexes guarded by
  `information_schema` + `PREPARE`/`EXECUTE`, pattern in `V2__onboarding.sql`).
- **Rule: every new migration must be idempotent.** Staging `reset-db.sh` rebuilds `gf_staging` from the prod schema dump
  and a DB that already has the objects but no Flyway history must still migrate (no "Duplicate key name"/"already exists").
- **Versions:** `V5` is a deliberate permanent gap (never reuse it), `V6` = `prag_prolaza` (nullable `testovi.prag_prolaza`),
  `V7` = uživo (presentations, runs, participants, answers, media), idempotent. `V8` = platno (`slajdovi.platno` JSON, `prezentacije.tema`), idempotent. `V9` = `grupe.wa_link` (nullable), idempotent. `V10` = `prelaz_slajda` (nullable `slajdovi.prelaz_efekat`, `prelaz_smer`, `prelaz_ms`), idempotent. `V11` = `banka_zadataka` (tables `grupe_zadataka`, `varijante`, `varijanta_prilozi`, column `mediji.vrsta` `varchar(20) NOT NULL DEFAULT 'SLIKA'`, nullable `predmeti.latex_preambula`), idempotent. `V12` = `mcp_banka` (`izvor`/`serija` on groups, `ikad_odobrena`,
  `zamenjuje_id` and `serija` on variants, `serija` on presentations and slides, table `iskljucene_teme`; see "MCP za Claude-a"),
  idempotent. `V13` = slide Zadatak (`slajdovi` group/timer/count, `izvodjenja.boduje`/`resenje_prikazano`/`zadatak_broj_varijanti`,
  `ucesnici.student_id`, `odgovori.polja`/`varijanta_id`, table `zadatak_dodele`; see "Zadatak slide"), idempotent. `V14` = prijava
  (`V14__prijava.sql`). `V15` = nivo varijante (`V15__nivo_varijante.sql`: `varijante.nivo` `tinyint NOT NULL DEFAULT 2`, nullable
  `slajdovi.zadatak_nivo` and `izvodjenja.zadatak_nivo`; see "Variant level"), idempotent. `V16` = index, phone and code kind
  (`V16__indeks_telefon_kod.sql`, branch `feature/prijava-indeks-mejl`; re-check the number against `origin/staging` right before the merge):
  `kodovi_za_prijavu.nacin` (`NOT NULL DEFAULT 'MEJL'`); strips a `/YYYY` suffix from `studenti.indeks` when it equals `godina` and the short form
  collides with no other row of that year; phones of `studenti` and `prijave` to E.164 only for unambiguous forms (`+...` without separators, `00...`,
  `0` + 7-10 digits -> `+381`, `381` + 8-10 digits), the rest stays as typed; all idempotent and additive (an older image reads the text columns
  unchanged). `V17` = check-in (`V17__cekiranje.sql`, branch `feature/studentski-portal`; provisional, re-check against `origin/staging`
  before the merge): tables `cekiranja` (`UNIQUE(predavanje_id, student_id)`, index `(student_id, vreme)`, FKs `ON DELETE CASCADE`) and
  `cekiranja_na_cekanju` (PK `kljuc_hash`), nullable `predavanja.cekiranje_tajna` / `cekiranje_tajna_od`; idempotent and additive (an older image
  maps none of it, rollback needs no DB work). `V18` is reserved for the absence notice / timetable work (`feature/najava-odsustva`).
  `V19` = student account phases 2 and 3 (`V19__studentski_portal.sql`, branch `feature/studentski-portal-2`; provisional, re-check against
  `origin/staging`): new tables `otvorene_teme`, `vezbanja`, `prikazani_slajdovi`, `objave_ocena`, `objavljene_ocene` only, plus a one-time first
  fill in SQL (drawn leaves of finished runs; slides up to where a finished run stopped plus slides with a round); additive. **Next free
  version: `V20`.**
- **The version number is decided at the merge into `staging`.** Parallel branches (MCP `feature/mcp-banka` = V12 if it merges
  first; slide "Zadatak" (project B) and the teacher login project ("prijava") take the next free number when they merge)
  cannot see each other, and git merges two files with the same version without a conflict. Before the merge, a branch whose
  number is already taken on `staging` renames its file to the next free version (allowed because a migration of an unmerged
  branch was never applied to a shared DB) and updates the comments that cite it; the PR description says the number is
  provisional.
  `scripts/provera-migracija.sh <ref>` (CI step "Verzije migracija su jedinstvene" against the PR base, `fetch-depth: 0`) fails on
  two migrations with one version, on a new version not above the highest in `<ref>` (Flyway would reject it on staging as
  "resolved locally but not applied") and on a file name that is not `V<n>__<opis>.sql`.
- `sql/views.sql` is historical, the view lives in `V1`.
- `scripts/flyway-provera.sh` checks the migrations against five scenarios: empty DB, `gf` and `gf_staging` schema dumps
  (`/data/tmp/redizajn-schema`), `vec_migrirana` (staging schema with the objects of every migration from V3 on already applied, no history)
  and `rollback` (the new jar migrates a `gf` schema, then the previous image `OLD_IMAGE`, default `gfs-backend:latest`, must start over it
  and pass one `PUT` of a slide; skipped with a message when the image is missing). Run `./mvnw -B -DskipTests package` first; it uses the
  test MySQL on 3307. Parallel worktrees pass their own `DB_PREFIX` and `APP_PORT` (e.g. `DB_PREFIX=fw_mcp_ APP_PORT=18092`): the
  script drops and recreates `${DB_PREFIX}*`.

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
