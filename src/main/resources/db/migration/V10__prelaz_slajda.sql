-- V10: graditelj slajdova, faza 5 (prelazi između slajdova): slajdovi.prelaz_efekat, prelaz_smer, prelaz_ms.
-- Plan faze 5 je predvideo V9, ali V9 je u međuvremenu postao grupe.wa_link (V9__wa_link_grupe.sql), pa je ovo V10.
-- Aditivno (nullable kolone): stari image (ddl-auto=validate ne zna za kolone, ali mu ne smetaju) radi nad V10.
-- Idempotentno po obrascu iz V2__onboarding.sql i V8__platno.sql (MySQL 8.0 nema ADD COLUMN IF NOT EXISTS).

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slajdovi' AND COLUMN_NAME = 'prelaz_efekat');
SET @sql := IF(@ima = 0, 'ALTER TABLE `slajdovi` ADD COLUMN `prelaz_efekat` varchar(20) NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slajdovi' AND COLUMN_NAME = 'prelaz_smer');
SET @sql := IF(@ima = 0, 'ALTER TABLE `slajdovi` ADD COLUMN `prelaz_smer` varchar(20) NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slajdovi' AND COLUMN_NAME = 'prelaz_ms');
SET @sql := IF(@ima = 0, 'ALTER TABLE `slajdovi` ADD COLUMN `prelaz_ms` int NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
