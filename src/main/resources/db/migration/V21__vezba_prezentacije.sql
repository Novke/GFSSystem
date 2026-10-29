-- V21: redni broj vežbe prezentacije (spec docs/superpowers/specs/2026-10-29-redosled-prezentacija-design.md u GFS-deploy):
-- prezentacije.vezba (NULL = bez broja, inače 0-99). Određuje redosled liste, grupisanje po vežbama i predlog sledeće
-- prezentacije za grupu; vrsta prezentacije (gradivo, pitanja, zadaci) se ne čuva, računa se iz slajdova.
-- Broj: V18 je raspored i najava odsustva, V20 studentski portal faze 2-3; V19 je bio rezervisan za ovu granu, ali je portal
-- ušao pre nje, pa je V19 trajno neiskorišćen (kao V5). Proveri origin/staging pre merge-a;
-- scripts/provera-migracija.sh (CI) odbija dupli broj i broj koji nije veći od najvećeg u osnovi PR-a.
-- Aditivno: jedna nullable kolona. Rollback (stara slika, pre V21): ddl-auto=validate ne proverava novu kolonu, Flyway
-- preskače budući V21; stara slika broj ne čita, a njen PUT prezentacije ga ne dira (ne mapira kolonu).
-- Popuna: prezentacija bez broja čiji naziv počinje sa "Vežbe N" / "Vezbe N" dobija N (prvi broj u nazivu, pa
-- "Vežbe 3 i 4 – zadaci" dobija 3); ostale ostaju bez broja. Samo NULL redovi, pa je ponovno izvršavanje bez efekta.
-- Idempotentno kao V2-V18: kolona preko information_schema + PREPARE (MySQL 8.0 nema ADD COLUMN IF NOT EXISTS).

-- ---------------------------------------------------------------- prezentacije: vezba
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'prezentacije' AND COLUMN_NAME = 'vezba');
SET @sql := IF(@ima = 0, 'ALTER TABLE `prezentacije` ADD COLUMN `vezba` int NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- popuna iz naziva
UPDATE `prezentacije`
SET `vezba` = CAST(REGEXP_SUBSTR(`naziv`, '[0-9]+') AS UNSIGNED)
WHERE `vezba` IS NULL
  AND REGEXP_LIKE(`naziv`, '^[[:space:]]*ve[zž]be[[:space:]]+[0-9]{1,2}([^0-9]|$)', 'i');
