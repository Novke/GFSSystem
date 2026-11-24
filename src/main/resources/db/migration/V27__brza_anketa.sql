-- V27: brza anketa na času (spec docs/superpowers/specs/2026-11-23-brza-anketa-design.md, odeljak 3, u deploy repou).
-- Broj je privremen: otvoren PR "Delimično tačno" (GFSSystem #55) takođe traži V27; ko se prvi spoji u staging uzima V27,
-- drugi preimenuje fajl u sledeći slobodan broj (scripts/provera-migracija.sh u CI-ju odbija dupli broj).
-- Aditivno: četiri nove tabele i jedna kolona sa podrazumevanom vrednošću. Starija slika ih ne mapira (ddl-auto=validate
-- prolazi, rollback bez rada na šemi). Ali red u studentska_obavestenja sa vrstom ANKETA ili ANKETA_CAS starija slika ne
-- može da pročita (nepoznata enum konstanta, kao MNOGO_NAJAVA u V18): pre rollback-a preko V27
--   delete from studentska_obavestenja where vrsta in ('ANKETA', 'ANKETA_CAS');
-- Idempotentno kao V2-V26: CREATE TABLE IF NOT EXISTS, kolona preko information_schema + PREPARE (MySQL 8.0 nema
-- ADD COLUMN IF NOT EXISTS).
--
-- ankete_folderi: folderi sačuvanih anketa (redosled rb).
-- ankete_sabloni: sačuvana pitanja (šabloni) i prečice; opcije su JSON niz stringova; folder_id NULL = bez foldera
--   (brisanje foldera ostavlja šablone bez foldera).
-- ankete: pokrenute ankete; pitanje i opcije su snimak u trenutku pokretanja (šablon se posle sme menjati i brisati).
--   Stanje se računa (otvorena = zatvoreno IS NULL, rok nije prošao i, za NA_CASU, predavanje nije završeno), bez
--   zakazanog posla. grupa_id je uvek popunjen (za NA_CASU grupa predavanja); brisanje grupe briše njene ankete.
-- ankete_glasovi: jedan glas po studentu i anketi (jedinstven ključ: dvostruki dodir ne pravi drugi red); opcije su
--   indeksi odgovora rastuće, zarezom ("0,2"). Nijedan nastavnički ni MCP odgovor ne čita student_id odavde.
-- podesavanja_studenta.push_ankete: prekidač push-a za ankete (podrazumevano uključen).

CREATE TABLE IF NOT EXISTS `ankete_folderi` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `naziv` varchar(60) NOT NULL,
  `rb` int NOT NULL,
  `kreirano` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ankete_sabloni` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `folder_id` bigint DEFAULT NULL,
  `pitanje` varchar(300) NOT NULL,
  `opcije` text NOT NULL,
  `vise_odgovora` bit(1) NOT NULL DEFAULT b'0',
  `publika` varchar(20) NOT NULL DEFAULT 'NA_CASU',
  `trajanje_sekunde` int DEFAULT NULL,
  `rok_sati` int DEFAULT NULL,
  `precica` bit(1) NOT NULL DEFAULT b'0',
  `precica_rb` int DEFAULT NULL,
  `ikonica` varchar(16) DEFAULT NULL,
  `boja` varchar(20) DEFAULT NULL,
  `kratak_naziv` varchar(30) DEFAULT NULL,
  `pokreni_odmah` bit(1) NOT NULL DEFAULT b'0',
  `rb` int NOT NULL DEFAULT 0,
  `broj_pokretanja` int NOT NULL DEFAULT 0,
  `poslednje_pokretanje` datetime(6) DEFAULT NULL,
  `kreirano` datetime(6) NOT NULL,
  `izmenjeno` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ankete_sabloni_folder` (`folder_id`, `rb`),
  CONSTRAINT `fk_ankete_sabloni_folder` FOREIGN KEY (`folder_id`) REFERENCES `ankete_folderi` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ankete` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sablon_id` bigint DEFAULT NULL,
  `pitanje` varchar(300) NOT NULL,
  `opcije` text NOT NULL,
  `vise_odgovora` bit(1) NOT NULL DEFAULT b'0',
  `publika` varchar(20) NOT NULL DEFAULT 'NA_CASU',
  `predavanje_id` bigint DEFAULT NULL,
  `grupa_id` bigint NOT NULL,
  `otvoreno` datetime(6) NOT NULL,
  `rok` datetime(6) DEFAULT NULL,
  `zatvoreno` datetime(6) DEFAULT NULL,
  `rezultat_prikazan` bit(1) NOT NULL DEFAULT b'0',
  `izvor` varchar(20) NOT NULL DEFAULT 'NASTAVNIK',
  PRIMARY KEY (`id`),
  KEY `idx_ankete_predavanje` (`predavanje_id`, `otvoreno`),
  KEY `idx_ankete_grupa` (`grupa_id`, `otvoreno`),
  KEY `idx_ankete_otvoreno` (`otvoreno`),
  KEY `idx_ankete_sablon` (`sablon_id`),
  CONSTRAINT `fk_ankete_sablon` FOREIGN KEY (`sablon_id`) REFERENCES `ankete_sabloni` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_ankete_predavanje` FOREIGN KEY (`predavanje_id`) REFERENCES `predavanja` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_ankete_grupa` FOREIGN KEY (`grupa_id`) REFERENCES `grupe` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ankete_glasovi` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `anketa_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `opcije` varchar(40) NOT NULL,
  `vreme` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ankete_glasovi_anketa_student` (`anketa_id`, `student_id`),
  KEY `idx_ankete_glasovi_student` (`student_id`),
  CONSTRAINT `fk_ankete_glasovi_anketa` FOREIGN KEY (`anketa_id`) REFERENCES `ankete` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_ankete_glasovi_student` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- podesavanja_studenta: push za ankete
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'podesavanja_studenta' AND COLUMN_NAME = 'push_ankete');
SET @sql := IF(@ima = 0, 'ALTER TABLE `podesavanja_studenta` ADD COLUMN `push_ankete` bit(1) NOT NULL DEFAULT b''1''', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
