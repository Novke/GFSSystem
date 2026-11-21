-- V27: delimično tačno (spec docs/superpowers/specs/2026-11-20-delimicno-tacno-design.md u wrapper repou).
-- odgovori.udeo: udeo tačnosti odgovora od 0 do 1 (VISE_TACNIH: max(0, (tačni izabrani - pogrešni izabrani) / broj tačnih),
-- ostali tipovi i zadatak 1 ili 0); NULL kad pitanje nema tačan odgovor, kao tacno. preocene_odgovora.staro_udeo: udeo pre
-- preocene, da vrati_preocenu vrati tačno ono što je bilo. DOUBLE bez zaokruživanja (2/3 ostaje 2/3; prikaz zaokružuje).
-- Broj: proveri origin/staging pre merge-a; scripts/provera-migracija.sh (CI) odbija dupli broj.
-- Popuna unazad (Novica, 2026-11-20: "preračunaj sve retroaktivno"): dira samo redove sa tacno IS NOT NULL i udeo IS NULL
-- (trag: staro_tacno IS NOT NULL i staro_udeo IS NULL), pa je drugo izvršavanje no-op i ne dira poene ponovo:
--   * snimak runde je pitanje VISE_TACNIH i sačuvani tacno je jednak oceni "tačno skup tačnih" iz snimka (odgovor nikad
--     nije preocenjen na drugu vrednost) -> udeo po formuli, i u takmičenju poeni po istom pravilu kao Ocenjivac.poeni:
--     osnovni 1000 bez trajanja, inače 1000 * (1 - min(max(vreme_ms, 0) / trajanje_ms, 1) / 2), pa FLOOR(osnovni * udeo + 0.5)
--     (= Java Math.round; ROUND nad DOUBLE ide na parno). Poeni se preračunavaju samo kad je stara ocena 0 (delimičan ili
--     netačan izbor); potpuno tačan odgovor (udeo 1) zadržava sačuvane poene, jer je bodovan trajanjem runde u tom trenutku,
--     a trajanje_ms se kasnije menja (+-10 s menja trajanje posle ranijih odgovora, ponovno otvaranje runde ga briše na NULL).
--     Trajanje u trenutku odgovora nikad nije sačuvano, pa delimični odgovori u takvim rundama dobijaju poene po konačnom
--     trajanju (NULL = osnovni 1000);
--   * inače (preocenjen, drugi tip, zadatak) udeo = IF(tacno, 1, 0), poeni ostaju.
-- Trag preocene po istom pravilu iz staro_tacno (izbor odgovora se preocenom ne menja), a stari_poeni po formuli u takmičenju,
-- da vraćanje preocene od pre V27 vrati udeo i poene kakve bi dao nov odgovor. Zbir poena se nigde ne čuva (ucesnici i
-- izvodjenja nemaju takvu kolonu; rang i lista poena sabiraju odgovori.poeni pri čitanju), pa nema šta drugo da se preračuna.
-- Aditivno: dve nullable kolone. Rollback (stara slika, pre V27): ddl-auto=validate ne gleda višak i stara slika piše odgovore
-- bez udela (NULL); čitaoci nove slike zato uvek čitaju COALESCE(udeo, IF(tacno, 1, 0)).
-- Idempotentno kao V2-V26: kolone preko information_schema + PREPARE. Bez privremenih tabela i pogleda (CTE unutar UPDATE-a),
-- pa traži samo prava koja su tražile i ranije migracije (ALTER, SELECT, UPDATE), ne CREATE TEMPORARY TABLES.

-- ---------------------------------------------------------------- odgovori: udeo
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'odgovori' AND COLUMN_NAME = 'udeo');
SET @sql := IF(@ima = 0, 'ALTER TABLE `odgovori` ADD COLUMN `udeo` double DEFAULT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- preocene_odgovora: staro_udeo
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'preocene_odgovora' AND COLUMN_NAME = 'staro_udeo');
SET @sql := IF(@ima = 0, 'ALTER TABLE `preocene_odgovora` ADD COLUMN `staro_udeo` double DEFAULT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- popuna unazad
-- Izbor se računa u CTE-u (isti tekst u oba UPDATE-a ispod; čita samo izbor i vreme odgovora, ne udeo, tacno ni poene, pa
-- drugi UPDATE vidi isto što i prvi). brojevi ima GROUP BY, pa se materijalizuje i ne spaja sa ciljnom tabelom odgovori.

-- odgovori: formula gde je tacno jednak oceni skupa; poeni samo u takmičenju i samo za netačan skup (potpuno tačan zadržava
-- svoje poene: trajanje runde je možda menjano posle bodovanja) ...
WITH
-- runde čiji je snimak pitanje VISE_TACNIH ("vrsta" PITANJE ili bez nje, kao stari snimak; zadatak ima "vrsta":"ZADATAK");
-- CASE čuva JSON_EXTRACT od neispravnog JSON-a
runde AS (
  SELECT r.`id` AS `runda_id`, IF(i.`takmicenje`, 1, 0) AS `takmicenje`, r.`trajanje_ms`
  FROM `pitanje_runde` r
  JOIN `izvodjenja` i ON i.`id` = r.`izvodjenje_id`
  WHERE (CASE WHEN JSON_VALID(r.`snimak`)
              THEN JSON_UNQUOTE(JSON_EXTRACT(r.`snimak`, '$.tip')) = 'VISE_TACNIH'
                   AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.`snimak`, '$.vrsta')), 'PITANJE') = 'PITANJE'
              ELSE 0 END) = 1
),
-- opcije snimka tih rundi (OpcijaSnimak: id, tacna); JSON_TABLE dobija '{}' umesto neispravnog JSON-a bez obzira na
-- redosled kojim optimizator primenjuje uslove (kao V20)
opcije AS (
  SELECT vr.`runda_id`, jt.`id` AS `opcija_id`, COALESCE(jt.`tacna`, 0) AS `tacna`
  FROM runde vr
  JOIN `pitanje_runde` r ON r.`id` = vr.`runda_id`
  JOIN JSON_TABLE(IF(JSON_VALID(r.`snimak`), r.`snimak`, '{}'), '$.opcije[*]'
                  COLUMNS (`id` bigint PATH '$.id', `tacna` boolean PATH '$.tacna')) jt
  WHERE jt.`id` IS NOT NULL
),
-- izbor svakog odgovora na te runde (opcije = CSV id-jeva; svaka opcija snimka se broji jednom, kao skup u
-- Ocenjivac.udeoIzbora): ocena skupa, udeo po formuli i poeni po udelu (samo za takmičenje; inače se ne koriste)
brojevi AS (
  SELECT o.`id` AS `odgovor_id`, vr.`takmicenje`, vr.`trajanje_ms`, o.`vreme_ms`,
         SUM(op.`tacna`) AS `tacnih`,
         SUM(IF(op.`tacna` = 1 AND FIND_IN_SET(op.`opcija_id`, COALESCE(o.`opcije`, '')) > 0, 1, 0)) AS `pogodjene`,
         SUM(IF(op.`tacna` = 0 AND FIND_IN_SET(op.`opcija_id`, COALESCE(o.`opcije`, '')) > 0, 1, 0)) AS `pogresne`
  FROM `odgovori` o
  JOIN runde vr ON vr.`runda_id` = o.`runda_id`
  JOIN opcije op ON op.`runda_id` = o.`runda_id`
  WHERE o.`polja` IS NULL
  GROUP BY o.`id`, vr.`takmicenje`, vr.`trajanje_ms`, o.`vreme_ms`
),
udeli AS (
  SELECT b.*, GREATEST(0.0, CAST(b.`pogodjene` - b.`pogresne` AS DOUBLE) / b.`tacnih`) AS `udeo`
  FROM brojevi b
  WHERE b.`tacnih` > 0
),
izbor AS (
  SELECT u.`odgovor_id`, u.`takmicenje`,
         IF(u.`pogodjene` = u.`tacnih` AND u.`pogresne` = 0, 1, 0) AS `ocena_skupa`,
         u.`udeo`,
         IF(u.`udeo` <= 0, 0,
            FLOOR(IF(u.`trajanje_ms` IS NULL OR u.`trajanje_ms` <= 0, 1000.0,
                     1000 * (1 - LEAST(CAST(GREATEST(u.`vreme_ms`, 0) AS DOUBLE) / u.`trajanje_ms`, 1.0) / 2))
                  * u.`udeo` + 0.5)) AS `poeni`
  FROM udeli u
)
UPDATE `odgovori` o
JOIN izbor z ON z.`odgovor_id` = o.`id`
SET o.`poeni` = IF(z.`takmicenje` = 1 AND z.`ocena_skupa` = 0, z.`poeni`, o.`poeni`),
    o.`udeo` = z.`udeo`
WHERE o.`udeo` IS NULL AND o.`tacno` IS NOT NULL AND IF(o.`tacno`, 1, 0) = z.`ocena_skupa`;

-- ... trag preocene po istom pravilu iz staro_tacno (pre popune ostatka odgovora; čita samo izbor) ...
WITH
-- runde čiji je snimak pitanje VISE_TACNIH ("vrsta" PITANJE ili bez nje, kao stari snimak; zadatak ima "vrsta":"ZADATAK");
-- CASE čuva JSON_EXTRACT od neispravnog JSON-a
runde AS (
  SELECT r.`id` AS `runda_id`, IF(i.`takmicenje`, 1, 0) AS `takmicenje`, r.`trajanje_ms`
  FROM `pitanje_runde` r
  JOIN `izvodjenja` i ON i.`id` = r.`izvodjenje_id`
  WHERE (CASE WHEN JSON_VALID(r.`snimak`)
              THEN JSON_UNQUOTE(JSON_EXTRACT(r.`snimak`, '$.tip')) = 'VISE_TACNIH'
                   AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.`snimak`, '$.vrsta')), 'PITANJE') = 'PITANJE'
              ELSE 0 END) = 1
),
-- opcije snimka tih rundi (OpcijaSnimak: id, tacna); JSON_TABLE dobija '{}' umesto neispravnog JSON-a bez obzira na
-- redosled kojim optimizator primenjuje uslove (kao V20)
opcije AS (
  SELECT vr.`runda_id`, jt.`id` AS `opcija_id`, COALESCE(jt.`tacna`, 0) AS `tacna`
  FROM runde vr
  JOIN `pitanje_runde` r ON r.`id` = vr.`runda_id`
  JOIN JSON_TABLE(IF(JSON_VALID(r.`snimak`), r.`snimak`, '{}'), '$.opcije[*]'
                  COLUMNS (`id` bigint PATH '$.id', `tacna` boolean PATH '$.tacna')) jt
  WHERE jt.`id` IS NOT NULL
),
-- izbor svakog odgovora na te runde (opcije = CSV id-jeva; svaka opcija snimka se broji jednom, kao skup u
-- Ocenjivac.udeoIzbora): ocena skupa, udeo po formuli i poeni po udelu (samo za takmičenje; inače se ne koriste)
brojevi AS (
  SELECT o.`id` AS `odgovor_id`, vr.`takmicenje`, vr.`trajanje_ms`, o.`vreme_ms`,
         SUM(op.`tacna`) AS `tacnih`,
         SUM(IF(op.`tacna` = 1 AND FIND_IN_SET(op.`opcija_id`, COALESCE(o.`opcije`, '')) > 0, 1, 0)) AS `pogodjene`,
         SUM(IF(op.`tacna` = 0 AND FIND_IN_SET(op.`opcija_id`, COALESCE(o.`opcije`, '')) > 0, 1, 0)) AS `pogresne`
  FROM `odgovori` o
  JOIN runde vr ON vr.`runda_id` = o.`runda_id`
  JOIN opcije op ON op.`runda_id` = o.`runda_id`
  WHERE o.`polja` IS NULL
  GROUP BY o.`id`, vr.`takmicenje`, vr.`trajanje_ms`, o.`vreme_ms`
),
udeli AS (
  SELECT b.*, GREATEST(0.0, CAST(b.`pogodjene` - b.`pogresne` AS DOUBLE) / b.`tacnih`) AS `udeo`
  FROM brojevi b
  WHERE b.`tacnih` > 0
),
izbor AS (
  SELECT u.`odgovor_id`, u.`takmicenje`,
         IF(u.`pogodjene` = u.`tacnih` AND u.`pogresne` = 0, 1, 0) AS `ocena_skupa`,
         u.`udeo`,
         IF(u.`udeo` <= 0, 0,
            FLOOR(IF(u.`trajanje_ms` IS NULL OR u.`trajanje_ms` <= 0, 1000.0,
                     1000 * (1 - LEAST(CAST(GREATEST(u.`vreme_ms`, 0) AS DOUBLE) / u.`trajanje_ms`, 1.0) / 2))
                  * u.`udeo` + 0.5)) AS `poeni`
  FROM udeli u
)
UPDATE `preocene_odgovora` t
JOIN izbor z ON z.`odgovor_id` = t.`odgovor_id`
SET t.`stari_poeni` = IF(z.`takmicenje` = 1 AND z.`ocena_skupa` = 0, z.`poeni`, t.`stari_poeni`),
    t.`staro_udeo` = z.`udeo`
WHERE t.`staro_udeo` IS NULL AND t.`staro_tacno` IS NOT NULL AND IF(t.`staro_tacno`, 1, 0) = z.`ocena_skupa`;

-- ... a sve ostalo (preocenjeno, drugi tipovi, zadatak) 1 ili 0, poeni ostaju
UPDATE `odgovori` SET `udeo` = IF(`tacno`, 1, 0) WHERE `udeo` IS NULL AND `tacno` IS NOT NULL;
UPDATE `preocene_odgovora` SET `staro_udeo` = IF(`staro_tacno`, 1, 0) WHERE `staro_udeo` IS NULL AND `staro_tacno` IS NOT NULL;
