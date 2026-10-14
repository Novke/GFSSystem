-- V6: opcioni prag prolaza po testu (redizajn UI-ja): broj poena od kog se polaganje smatra položenim; NULL = nastavnik
-- nije odredio prag, pa za taj test ne postoji pojam prolaza. Kolona je nullable, pa stara slika (ddl-auto=update)
-- i rollback na nju rade i posle ove migracije. V5 je namerno preskočena (nikad se ne koristi).
-- Idempotentno kao V2-V4: reset staging baze gradi šemu iz dump-a produkcije, a baza koja već ima kolonu (bez Flyway
-- istorije) ne sme da pukne na "Duplicate column name". MySQL 8.0 nema ADD COLUMN IF NOT EXISTS, pa preko
-- information_schema.COLUMNS + PREPARE.
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'testovi' AND COLUMN_NAME = 'prag_prolaza');
SET @sql := IF(@ima = 0, 'ALTER TABLE testovi ADD COLUMN prag_prolaza int DEFAULT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
