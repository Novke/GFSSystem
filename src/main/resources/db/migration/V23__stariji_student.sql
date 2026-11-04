-- V23: oznaka "stariji student" (studenti.stariji): student koji je stariji od svoje grupe (promenio smer, pa bi po godini
-- bio u ovoj grupi). Samo podsetnik nastavniku pri ocenjivanju: ne menja grupu, bodove, predlog ocene ni pristup, i nikad
-- ne izlazi na projektor, telefon ili nalog studenta. Postavlja se u izmeni studenta. Nije isto što i "stariji" u brojačima
-- predavanja (student iz druge, starije grupe): ti brojači se računaju iz grupe, ne iz ove kolone.
-- Broj: proveri origin/staging pre merge-a; scripts/provera-migracija.sh (CI) odbija dupli broj i broj koji nije veći od
-- najvećeg u osnovi PR-a.
-- Aditivno: nova kolona ima DDL DEFAULT, pa upis stare slike prolazi. Rollback (stara slika, pre V23): ddl-auto=validate ne
-- proverava novu kolonu, Flyway preskače budući V23; stara slika oznaku ne čita, a njen PUT studenta je ne dira.
-- Idempotentno kao V2-V22: kolona preko information_schema + PREPARE (MySQL 8.0 nema ADD COLUMN IF NOT EXISTS).

-- ---------------------------------------------------------------- studenti: stariji
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'studenti' AND COLUMN_NAME = 'stariji');
SET @sql := IF(@ima = 0, 'ALTER TABLE `studenti` ADD COLUMN `stariji` tinyint(1) NOT NULL DEFAULT 0', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
