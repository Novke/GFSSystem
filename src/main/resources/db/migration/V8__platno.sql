-- V8: graditelj slajdova, faza 1 (platno): slajdovi.platno (JSON dokument PLATNO slajda, spec 4.3) i
-- prezentacije.tema (spec 10). Aditivno: stari image radi nad V8 (nullable kolona i kolona sa podrazumevanom vrednošću).
-- Idempotentno po obrascu iz V2__onboarding.sql (MySQL 8.0 nema ADD COLUMN IF NOT EXISTS).

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slajdovi' AND COLUMN_NAME = 'platno');
SET @sql := IF(@ima = 0, 'ALTER TABLE `slajdovi` ADD COLUMN `platno` mediumtext NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'prezentacije' AND COLUMN_NAME = 'tema');
SET @sql := IF(@ima = 0, 'ALTER TABLE `prezentacije` ADD COLUMN `tema` varchar(20) NOT NULL DEFAULT ''NACRT''', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
