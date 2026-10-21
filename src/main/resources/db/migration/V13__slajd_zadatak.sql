-- V13: slajd "Zadatak" u Uživo (projekat B, banka spec 4.1 i MCP spec 11): slajd nad čvorom banke (grupa_zadataka_id,
-- tajmer zadatak_vreme_sekunde, broj različitih varijanti zadatak_broj_varijanti), bodovan run (izvodjenja.boduje),
-- prikaz rešenja na projektoru (izvodjenja.resenje_prikazano), broj varijanti za ovu rundu iz konzole
-- (izvodjenja.zadatak_broj_varijanti: NULL = kao na slajdu, 0 = sve aktivne, n), student iza učesnika (ucesnici.student_id,
-- prijava studenata), odgovor po poljima šeme (odgovori.polja, JSON kljuc -> {vrednost, tacno}) i dodeljena varijanta
-- (odgovori.varijanta_id), te dodela varijante učesniku u rundi (zadatak_dodele: tačno jedna po učesniku i rundi).
-- BROJ JE PRIVREMEN: određuje se pri merge-u u staging (sledeći slobodan posle V12 MCP banke; prijava uzima sledeći).
-- scripts/provera-migracija.sh (CI) odbija dupli broj i broj koji nije veći od najvećeg u osnovi PR-a.
-- Aditivno: nove kolone su nullable ili imaju DEFAULT u DDL-u (boduje = 0, resenje_prikazano = 0), nova tabela je zasebna.
-- Strani ključevi: slajd -> grupa i učesnik -> student su ON DELETE SET NULL (slajd i učesnik ostaju, editor upozorava
-- na obrisanu grupu), odgovor -> varijanta ON DELETE SET NULL, dodela -> runda i -> učesnik ON DELETE CASCADE (brisanje
-- izvođenja briše i dodele), a dodela -> varijanta je RESTRICT (banka spec 4.1: korišćena varijanta se ne briše, arhivira
-- se). Strani ključ prema slajdovima nema (runda već čuva slajd_id sa SET NULL).
-- Rollback (stara slika, pre B): ne zna tip 'ZADATAK' (kao PLATNO: prezentacija sa takvim slajdom se ne učitava);
-- ddl-auto=validate ne proverava nove kolone i tabelu, Flyway preskače budući V13, a DEFAULT u DDL-u pušta njene INSERT-e u
-- izvodjenja (bez njega bi STRICT mod odbio "Field ... doesn't have a default value"), pa stara slika pokreće izvođenja.
-- Njen koriscenoNaCasu je uvek false, pa brisanje korišćene varijante na staroj slici pada na FK RESTRICT (500, ništa
-- obrisano). PITANJE snimke sa "vrsta" čita (nepoznato polje se ignoriše), a pregled izvođenja sa ZADATAK rundama joj ne
-- radi. Brisanje grupe koju koristi slajd prolazi i ostavlja slajd bez grupe. Bodovan run na staroj slici ne upisuje
-- aktivnosti. Stara slika pri izmeni ili brisanju varijante briše njen stari SVG kao pre B, pa slike ranijih ZADATAK
-- rundi te varijante u pregledu mogu nestati. Proverava scripts/flyway-provera.sh, scenario rollback (PUT slajda,
-- pokretanje i završetak izvođenja).
-- Idempotentno kao V2-V12: tabela CREATE TABLE IF NOT EXISTS, kolone, indeksi i strani ključevi preko information_schema +
-- PREPARE (MySQL 8.0 nema ADD COLUMN / CREATE INDEX IF NOT EXISTS).

-- ---------------------------------------------------------------- slajdovi: grupa zadataka, tajmer, broj varijanti
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slajdovi' AND COLUMN_NAME = 'grupa_zadataka_id');
SET @sql := IF(@ima = 0, 'ALTER TABLE `slajdovi` ADD COLUMN `grupa_zadataka_id` bigint NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slajdovi' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
               AND CONSTRAINT_NAME = 'fk_slajdovi_grupa_zadataka');
SET @sql := IF(@ima = 0, 'ALTER TABLE `slajdovi` ADD CONSTRAINT `fk_slajdovi_grupa_zadataka` FOREIGN KEY (`grupa_zadataka_id`) REFERENCES `grupe_zadataka` (`id`) ON DELETE SET NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slajdovi' AND COLUMN_NAME = 'zadatak_vreme_sekunde');
SET @sql := IF(@ima = 0, 'ALTER TABLE `slajdovi` ADD COLUMN `zadatak_vreme_sekunde` int NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slajdovi' AND COLUMN_NAME = 'zadatak_broj_varijanti');
SET @sql := IF(@ima = 0, 'ALTER TABLE `slajdovi` ADD COLUMN `zadatak_broj_varijanti` int NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- izvodjenja: boduje, resenje_prikazano, broj varijanti
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'izvodjenja' AND COLUMN_NAME = 'boduje');
SET @sql := IF(@ima = 0, 'ALTER TABLE `izvodjenja` ADD COLUMN `boduje` bit(1) NOT NULL DEFAULT b''0''', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'izvodjenja' AND COLUMN_NAME = 'resenje_prikazano');
SET @sql := IF(@ima = 0, 'ALTER TABLE `izvodjenja` ADD COLUMN `resenje_prikazano` bit(1) NOT NULL DEFAULT b''0''', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'izvodjenja' AND COLUMN_NAME = 'zadatak_broj_varijanti');
SET @sql := IF(@ima = 0, 'ALTER TABLE `izvodjenja` ADD COLUMN `zadatak_broj_varijanti` int NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- ucesnici: student
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ucesnici' AND COLUMN_NAME = 'student_id');
SET @sql := IF(@ima = 0, 'ALTER TABLE `ucesnici` ADD COLUMN `student_id` bigint NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ucesnici' AND INDEX_NAME = 'idx_ucesnici_student');
SET @sql := IF(@ima = 0, 'CREATE INDEX idx_ucesnici_student ON ucesnici (izvodjenje_id, student_id)', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ucesnici' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
               AND CONSTRAINT_NAME = 'fk_ucesnici_student');
SET @sql := IF(@ima = 0, 'ALTER TABLE `ucesnici` ADD CONSTRAINT `fk_ucesnici_student` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`) ON DELETE SET NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- odgovori: polja, varijanta
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'odgovori' AND COLUMN_NAME = 'polja');
SET @sql := IF(@ima = 0, 'ALTER TABLE `odgovori` ADD COLUMN `polja` text NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'odgovori' AND COLUMN_NAME = 'varijanta_id');
SET @sql := IF(@ima = 0, 'ALTER TABLE `odgovori` ADD COLUMN `varijanta_id` bigint NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'odgovori' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
               AND CONSTRAINT_NAME = 'fk_odgovori_varijanta');
SET @sql := IF(@ima = 0, 'ALTER TABLE `odgovori` ADD CONSTRAINT `fk_odgovori_varijanta` FOREIGN KEY (`varijanta_id`) REFERENCES `varijante` (`id`) ON DELETE SET NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- zadatak_dodele
-- Dodela nastaje pod ključem reda izvođenja (otvaranje runde, prijava učesnika); UNIQUE(runda_id, ucesnik_id) je
-- poslednja brana protiv dvostruke dodele. RESTRICT prema varijanti: varijanta koja je bila na času se ne briše.
CREATE TABLE IF NOT EXISTS `zadatak_dodele` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `runda_id` bigint NOT NULL,
  `ucesnik_id` bigint NOT NULL,
  `varijanta_id` bigint NOT NULL,
  `kreirano` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_zadatak_dodele` (`runda_id`, `ucesnik_id`),
  KEY `idx_zadatak_dodele_ucesnik` (`ucesnik_id`),
  KEY `idx_zadatak_dodele_varijanta` (`varijanta_id`),
  CONSTRAINT `fk_zadatak_dodele_runda` FOREIGN KEY (`runda_id`) REFERENCES `pitanje_runde` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_zadatak_dodele_ucesnik` FOREIGN KEY (`ucesnik_id`) REFERENCES `ucesnici` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_zadatak_dodele_varijanta` FOREIGN KEY (`varijanta_id`) REFERENCES `varijante` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
