-- V12: MCP za Kloda, banka (projekat A1, spec MCP 6.1 i 6.2): ko je napravio grupu (izvor) i kojom serijom Klod upisuje
-- (serija na grupama, varijantama, prezentacijama i slajdovima, da se cela serija pregleda ili poništi), da li je varijanta
-- ikad bila odobrena (ikad_odobrena: Klod tada pravi kopiju umesto izmene, a ne briše je), koju varijantu kopija zamenjuje
-- (zamenjuje_id: odobravanje kopije arhivira original) i isključene teme predmeta (iskljucene_teme: grupa sa podstablom ili
-- samo opis teme koju Klod ne pravi).
-- BROJ JE PRIVREMEN: određuje se pri merge-u u staging (sledeći slobodan; danas V12, projekat B i prijava uzimaju sledeći).
-- scripts/provera-migracija.sh (CI) odbija dupli broj i broj koji nije veći od najvećeg u osnovi PR-a.
-- Aditivno: nove kolone su nullable ili imaju DEFAULT u DDL-u (izvor = 'RUCNO', ikad_odobrena = 0), nova tabela je zasebna.
-- Rollback: stara slika ne zna za nove kolone i tabelu (ddl-auto=validate ih ne proverava, Flyway preskače budući V12), a
-- DEFAULT u DDL-u pušta njene INSERT-e (bez njega bi STRICT mod odbio "Field ... doesn't have a default value"). Strani
-- ključevi prema varijantama i grupama su ON DELETE SET NULL, pa brisanje varijante ili grupe radi i u staroj slici.
-- Proverava scripts/flyway-provera.sh, scenario rollback.
-- Idempotentno kao V2-V11: tabela CREATE TABLE IF NOT EXISTS, kolone, indeksi i strani ključ preko information_schema +
-- PREPARE (MySQL 8.0 nema ADD COLUMN / CREATE INDEX IF NOT EXISTS). Postavljanje ikad_odobrena za već odobrene varijante
-- (AKTIVNA, ARHIVIRANA) je u istom uslovnom bloku kao ADD COLUMN: na bazi koja kolonu već ima se ne ponavlja.

-- ---------------------------------------------------------------- grupe_zadataka: izvor, serija
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grupe_zadataka' AND COLUMN_NAME = 'izvor');
SET @sql := IF(@ima = 0, 'ALTER TABLE `grupe_zadataka` ADD COLUMN `izvor` varchar(20) NOT NULL DEFAULT ''RUCNO''', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grupe_zadataka' AND COLUMN_NAME = 'serija');
SET @sql := IF(@ima = 0, 'ALTER TABLE `grupe_zadataka` ADD COLUMN `serija` varchar(60) NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- varijante: ikad_odobrena, zamenjuje_id, serija
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'varijante' AND COLUMN_NAME = 'ikad_odobrena');
SET @sql := IF(@ima = 0, 'ALTER TABLE `varijante` ADD COLUMN `ikad_odobrena` tinyint(1) NOT NULL DEFAULT 0', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := IF(@ima = 0, 'UPDATE `varijante` SET `ikad_odobrena` = 1 WHERE `status` IN (''AKTIVNA'', ''ARHIVIRANA'')', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'varijante' AND COLUMN_NAME = 'zamenjuje_id');
SET @sql := IF(@ima = 0, 'ALTER TABLE `varijante` ADD COLUMN `zamenjuje_id` bigint NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'varijante' AND CONSTRAINT_TYPE = 'FOREIGN KEY'
               AND CONSTRAINT_NAME = 'fk_varijante_zamenjuje');
SET @sql := IF(@ima = 0, 'ALTER TABLE `varijante` ADD CONSTRAINT `fk_varijante_zamenjuje` FOREIGN KEY (`zamenjuje_id`) REFERENCES `varijante` (`id`) ON DELETE SET NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'varijante' AND COLUMN_NAME = 'serija');
SET @sql := IF(@ima = 0, 'ALTER TABLE `varijante` ADD COLUMN `serija` varchar(60) NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- prezentacije, slajdovi: serija
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'prezentacije' AND COLUMN_NAME = 'serija');
SET @sql := IF(@ima = 0, 'ALTER TABLE `prezentacije` ADD COLUMN `serija` varchar(60) NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slajdovi' AND COLUMN_NAME = 'serija');
SET @sql := IF(@ima = 0, 'ALTER TABLE `slajdovi` ADD COLUMN `serija` varchar(60) NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- indeksi po seriji (pregled i poništavanje serije)
SET @ima := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grupe_zadataka' AND INDEX_NAME = 'idx_grupe_zadataka_serija');
SET @sql := IF(@ima = 0, 'CREATE INDEX idx_grupe_zadataka_serija ON grupe_zadataka (serija)', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'varijante' AND INDEX_NAME = 'idx_varijante_serija');
SET @sql := IF(@ima = 0, 'CREATE INDEX idx_varijante_serija ON varijante (serija)', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'prezentacije' AND INDEX_NAME = 'idx_prezentacije_serija');
SET @sql := IF(@ima = 0, 'CREATE INDEX idx_prezentacije_serija ON prezentacije (serija)', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slajdovi' AND INDEX_NAME = 'idx_slajdovi_serija');
SET @sql := IF(@ima = 0, 'CREATE INDEX idx_slajdovi_serija ON slajdovi (serija)', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- iskljucene_teme
-- Tema sa grupom isključuje tu grupu i celo podstablo; tema bez grupe je samo opis za Kloda. UNIQUE(predmet_id, grupa_id)
-- dozvoljava više tema bez grupe (MySQL ne poredi NULL-ove); brisanje grupe ostavlja temu bez grupe, brisanje predmeta
-- briše njegove teme.
CREATE TABLE IF NOT EXISTS `iskljucene_teme` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `predmet_id` bigint NOT NULL,
  `opis` varchar(300) NOT NULL,
  `grupa_id` bigint DEFAULT NULL,
  `izvor` varchar(20) NOT NULL DEFAULT 'RUCNO',
  `serija` varchar(60) DEFAULT NULL,
  `kreirano` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_iskljucene_teme` (`predmet_id`, `grupa_id`),
  KEY `idx_iskljucene_teme_grupa` (`grupa_id`),
  CONSTRAINT `fk_iskljucene_teme_predmet` FOREIGN KEY (`predmet_id`) REFERENCES `predmeti` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_iskljucene_teme_grupa` FOREIGN KEY (`grupa_id`) REFERENCES `grupe_zadataka` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
