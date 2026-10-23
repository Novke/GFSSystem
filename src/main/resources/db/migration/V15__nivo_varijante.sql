-- V15: nivo varijante (spec docs/superpowers/specs/2026-10-21-nivo-varijante-design.md u GFS-deploy): varijante.nivo
-- (1-3, prikaz ★/★★/★★★, koliko je teška varijanta unutar svog tipa; težina tipa ostaje grupe_zadataka.tezina), nivo
-- ZADATAK slajda (slajdovi.zadatak_nivo: NULL = svi nivoi) i izmena nivoa za sledeće otvaranje iz konzole
-- (izvodjenja.zadatak_nivo: NULL = kao na slajdu, 0 = svi, n), ista semantika kao zadatak_broj_varijanti iz V13.
-- BROJ JE PRIVREMEN: potvrđuje se pri merge-u u staging (sledeći slobodan); scripts/provera-migracija.sh (CI) odbija dupli
-- broj i broj koji nije veći od najvećeg u osnovi PR-a.
-- Aditivno: varijante.nivo ima DEFAULT 2 u DDL-u (postojeće varijante su "srednje", a INSERT stare slike bez kolone
-- prolazi i u STRICT modu), ostale dve kolone su nullable.
-- Rollback (stara slika, pre V15): ddl-auto=validate ne proverava nove kolone, Flyway preskače budući V15; stara slika
-- izvlači sve nivoe (ne čita slajdovi.zadatak_nivo ni izvodjenja.zadatak_nivo), a njen PUT slajda ostavlja zadatak_nivo
-- kakav jeste (ne mapira ga). Nove varijante stare slike dobijaju nivo 2.
-- Idempotentno kao V2-V14: kolone preko information_schema + PREPARE (MySQL 8.0 nema ADD COLUMN IF NOT EXISTS).

-- ---------------------------------------------------------------- varijante: nivo
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'varijante' AND COLUMN_NAME = 'nivo');
SET @sql := IF(@ima = 0, 'ALTER TABLE `varijante` ADD COLUMN `nivo` tinyint NOT NULL DEFAULT 2', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- slajdovi: nivo ZADATAK slajda
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slajdovi' AND COLUMN_NAME = 'zadatak_nivo');
SET @sql := IF(@ima = 0, 'ALTER TABLE `slajdovi` ADD COLUMN `zadatak_nivo` tinyint NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- izvodjenja: nivo iz konzole
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'izvodjenja' AND COLUMN_NAME = 'zadatak_nivo');
SET @sql := IF(@ima = 0, 'ALTER TABLE `izvodjenja` ADD COLUMN `zadatak_nivo` tinyint NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
