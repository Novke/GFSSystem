-- V18: raspored časova i najava odsustva (spec docs/superpowers/specs/2026-10-29-najava-odsustva-design.md, odeljak 2,
-- u deploy repou). Privremen broj: pri merge-u u staging dobija sledeći slobodan (pre merge-a proveriti origin/staging).
-- Aditivno: četiri nove tabele i nullable kolona obavestenja.kljuc sa jedinstvenim indeksom (više NULL-ova je dozvoljeno).
-- Starija slika ih ne mapira (ddl-auto=validate prolazi). Rollback: starija slika ne zna vrstu MNOGO_NAJAVA, pa se pre
-- njega brišu ti redovi obaveštenja (README, "Raspored i najava odsustva"). Idempotentno kao V2-V17.
-- Termini se ne čuvaju: termin = (stavka, datum u periodu sa danom stavke). Čuvaju se samo otkazani termini i najave;
-- red čiji datum posle izmene perioda više nije termin samo prestane da se vidi. Stavke nemaju jedinstven ključ u bazi
-- (duplikate proverava servis pod bravom perioda), da zamena vremena dve stavke u jednom PUT-u ne padne na ključu.

CREATE TABLE IF NOT EXISTS `rasporedi` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `naziv` varchar(100) NOT NULL,
  `vazi_od` date NOT NULL,
  `vazi_do` date NOT NULL,
  `prag_najava` int NOT NULL DEFAULT 5,
  `kreirano` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_rasporedi_period` (`vazi_do`, `vazi_od`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `raspored_stavke` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `raspored_id` bigint NOT NULL,
  `predmet_id` bigint NOT NULL,
  `grupa_id` bigint NOT NULL,
  `dan` tinyint NOT NULL,
  `pocetak` time NOT NULL,
  `kraj` time NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_raspored_stavke_raspored` (`raspored_id`),
  KEY `idx_raspored_stavke_grupa` (`grupa_id`, `predmet_id`),
  CONSTRAINT `fk_raspored_stavke_raspored` FOREIGN KEY (`raspored_id`) REFERENCES `rasporedi` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_raspored_stavke_predmet` FOREIGN KEY (`predmet_id`) REFERENCES `predmeti` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_raspored_stavke_grupa` FOREIGN KEY (`grupa_id`) REFERENCES `grupe` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `otkazani_termini` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stavka_id` bigint NOT NULL,
  `datum` date NOT NULL,
  `napomena` varchar(200) DEFAULT NULL,
  `otkazano` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_otkazani_termini` (`stavka_id`, `datum`),
  CONSTRAINT `fk_otkazani_termini_stavka` FOREIGN KEY (`stavka_id`) REFERENCES `raspored_stavke` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `najave_odsustva` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stavka_id` bigint NOT NULL,
  `datum` date NOT NULL,
  `student_id` bigint NOT NULL,
  `razlog` varchar(200) DEFAULT NULL,
  `vreme` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_najave_odsustva` (`stavka_id`, `datum`, `student_id`),
  KEY `idx_najave_odsustva_student` (`student_id`),
  CONSTRAINT `fk_najave_odsustva_stavka` FOREIGN KEY (`stavka_id`) REFERENCES `raspored_stavke` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_najave_odsustva_student` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- obavestenja.kljuc: jedno obaveštenje po ključu (MNOGO_NAJAVA:<stavkaId>:<datum>); stare vrste ga nemaju (NULL).
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'obavestenja' AND COLUMN_NAME = 'kljuc');
SET @sql := IF(@ima = 0, 'ALTER TABLE `obavestenja` ADD COLUMN `kljuc` varchar(100) DEFAULT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'obavestenja' AND INDEX_NAME = 'uk_obavestenja_kljuc');
SET @sql := IF(@ima = 0, 'CREATE UNIQUE INDEX uk_obavestenja_kljuc ON obavestenja (kljuc)', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
