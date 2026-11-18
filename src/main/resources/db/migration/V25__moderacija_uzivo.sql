-- V25: moderacija uživo (spec docs/superpowers/specs/2026-11-18-moderacija-uzivo-design.md u wrapper repou).
-- ucesnici.utisan: utišan učesnik (PITALICE | SVE) do kraja izvođenja; NULL = sme da odgovara.
-- zabrane_gostiju: SHA-256 tokena iz kolačića gfs_uzivo izbačenog učesnika (gost ili student koji se odjavi), za ovo
-- izvođenje ili celo predavanje; preživljava "Ne čuvaj" (briše učesnike), a nova prijava sa zabranjenim tokenom nosi zabranu
-- na nov token. Čisti se posle 2 dana (kolačić živi 12 h) i sa izvođenjem/predavanjem (CASCADE).
-- oznake_ponasanja: oznake na kartonu studenta (sakriven odgovor, utišan, izbačen); preživljavaju "Ne čuvaj" (koje na
-- kraju briše učesnike i runde) i brisanje izvođenja, predavanja i runde (FK SET NULL). Oznaka IZBACEN_PREDAVANJE je i
-- zabrana ulaska prijavljenog studenta u izvođenja tog predavanja; brisanje oznake je skida.
-- Broj: proveri origin/staging pre merge-a; scripts/provera-migracija.sh (CI) odbija dupli broj.
-- Aditivno: nove kolone imaju DDL DEFAULT (ili su nullable), pa upis stare slike prolazi. Rollback (stara slika, pre V25):
-- ddl-auto=validate ne proverava nove kolone ni tabelu; utišavanje i zabrana do kraja predavanja tada ne važe.
-- Idempotentno kao V2-V24.

-- ---------------------------------------------------------------- ucesnici: utisan
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ucesnici' AND COLUMN_NAME = 'utisan');
SET @sql := IF(@ima = 0, 'ALTER TABLE `ucesnici` ADD COLUMN `utisan` varchar(20) DEFAULT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- oznake_ponasanja
CREATE TABLE IF NOT EXISTS `oznake_ponasanja` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `vrsta` varchar(30) NOT NULL,
  `tekst` varchar(200) DEFAULT NULL,
  `predmet_id` bigint DEFAULT NULL,
  `predavanje_id` bigint DEFAULT NULL,
  `izvodjenje_id` bigint DEFAULT NULL,
  `runda_id` bigint DEFAULT NULL,
  `prezentacija` varchar(255) DEFAULT NULL,
  `kreirano` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_oznake_student` (`student_id`, `kreirano`),
  KEY `idx_oznake_predavanje` (`predavanje_id`, `student_id`),
  KEY `idx_oznake_izvodjenje` (`izvodjenje_id`),
  KEY `idx_oznake_runda` (`runda_id`),
  KEY `idx_oznake_predmet` (`predmet_id`),
  CONSTRAINT `fk_oznake_student` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_oznake_predmet` FOREIGN KEY (`predmet_id`) REFERENCES `predmeti` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_oznake_predavanje` FOREIGN KEY (`predavanje_id`) REFERENCES `predavanja` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_oznake_izvodjenje` FOREIGN KEY (`izvodjenje_id`) REFERENCES `izvodjenja` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_oznake_runda` FOREIGN KEY (`runda_id`) REFERENCES `pitanje_runde` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- zabrane_gostiju
CREATE TABLE IF NOT EXISTS `zabrane_gostiju` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token_hash` char(64) NOT NULL,
  `izvodjenje_id` bigint NOT NULL,
  `predavanje_id` bigint DEFAULT NULL,
  `kreirano` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_zabrane_gostiju_token` (`token_hash`),
  KEY `idx_zabrane_gostiju_kreirano` (`kreirano`),
  CONSTRAINT `fk_zabrane_gostiju_izvodjenje` FOREIGN KEY (`izvodjenje_id`) REFERENCES `izvodjenja` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_zabrane_gostiju_predavanje` FOREIGN KEY (`predavanje_id`) REFERENCES `predavanja` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
