-- V3: indeksi za filtere i sort lista (redizajn UI-ja): opseg datuma / školska godina i sort po datumu za
-- predavanja, domaće i testove, pretraga studenata po indeksu i godini upisa.
-- Idempotentno kao V2: reset staging baze (reset-db.sh) gradi šemu iz dump-a produkcije, a baza koja je već
-- imala ove indekse (bez Flyway istorije) ne sme da pukne na "Duplicate key name". MySQL 8.0 nema
-- CREATE INDEX IF NOT EXISTS, pa se indeks pravi preko information_schema.STATISTICS + PREPARE.
-- Stara slika (ddl-auto=update) ih ne dira, pa rollback na nju radi i posle ove migracije.

SET @ima := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'predavanja' AND INDEX_NAME = 'idx_predavanja_datum');
SET @sql := IF(@ima = 0, 'CREATE INDEX idx_predavanja_datum ON predavanja (datum)', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'domaci' AND INDEX_NAME = 'idx_domaci_datum');
SET @sql := IF(@ima = 0, 'CREATE INDEX idx_domaci_datum ON domaci (datum)', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'testovi' AND INDEX_NAME = 'idx_testovi_datum');
SET @sql := IF(@ima = 0, 'CREATE INDEX idx_testovi_datum ON testovi (datum)', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'studenti' AND INDEX_NAME = 'idx_studenti_indeks_godina');
SET @sql := IF(@ima = 0, 'CREATE INDEX idx_studenti_indeks_godina ON studenti (indeks, godina)', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
