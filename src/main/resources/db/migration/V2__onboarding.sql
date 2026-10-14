-- V2: onboarding (studenti se sami upisuju preko QR forme): tabele onboarding_sesije i prijave,
-- kolone studenti.email, studenti.datum_rodjenja, studenti.opstina.
-- Idempotentno: na gf_staging (i na bazi gde je ddl-auto=update već napravio ove objekte) je no-op,
-- na `gf` (prod pre onboardinga) i na praznoj bazi posle V1 pravi sve. MySQL 8.0 nema ADD COLUMN IF NOT EXISTS,
-- pa se kolona dodaje preko information_schema + PREPARE. DDL je isti kao u gf_staging (SHOW CREATE TABLE,
-- Hibernate imena ključeva) i kao u staging/seed/schema-delta.sql u deploy repou.

-- ---------------------------------------------------------------- studenti: nove kolone
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'studenti' AND COLUMN_NAME = 'datum_rodjenja');
SET @sql := IF(@ima = 0, 'ALTER TABLE `studenti` ADD COLUMN `datum_rodjenja` date DEFAULT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'studenti' AND COLUMN_NAME = 'email');
SET @sql := IF(@ima = 0, 'ALTER TABLE `studenti` ADD COLUMN `email` varchar(255) DEFAULT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'studenti' AND COLUMN_NAME = 'opstina');
SET @sql := IF(@ima = 0, 'ALTER TABLE `studenti` ADD COLUMN `opstina` varchar(100) DEFAULT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- onboarding_sesije
CREATE TABLE IF NOT EXISTS `onboarding_sesije` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `aktivna` bit(1) NOT NULL,
  `istice` datetime(6) NOT NULL,
  `kreirano` datetime(6) NOT NULL,
  `max_prijava` int NOT NULL,
  `napomena` varchar(255) DEFAULT NULL,
  `token` varchar(32) NOT NULL,
  `grupa_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKaal8igno8xqjlqd7jmpejn5n6` (`token`),
  KEY `FK93555fsvb5ykacopp8ggm594o` (`grupa_id`),
  CONSTRAINT `FK93555fsvb5ykacopp8ggm594o` FOREIGN KEY (`grupa_id`) REFERENCES `grupe` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- prijave
CREATE TABLE IF NOT EXISTS `prijave` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `broj_telefona` varchar(20) NOT NULL,
  `datum_rodjenja` date DEFAULT NULL,
  `email` varchar(120) NOT NULL,
  `godina` int NOT NULL,
  `ime` varchar(60) NOT NULL,
  `indeks` varchar(20) NOT NULL,
  `napomena` varchar(255) DEFAULT NULL,
  `obradjeno` datetime(6) DEFAULT NULL,
  `opstina` varchar(100) DEFAULT NULL,
  `podneto` datetime(6) NOT NULL,
  `prezime` varchar(60) NOT NULL,
  `status` enum('NA_CEKANJU','ODBIJENA','PRIHVACENA') NOT NULL,
  `sesija_id` bigint NOT NULL,
  `student_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK776rvofy3mpcnwxoao3js2cn3` (`sesija_id`),
  KEY `FKa6y1xtfgij6jwvexu3smnes1s` (`student_id`),
  CONSTRAINT `FK776rvofy3mpcnwxoao3js2cn3` FOREIGN KEY (`sesija_id`) REFERENCES `onboarding_sesije` (`id`),
  CONSTRAINT `FKa6y1xtfgij6jwvexu3smnes1s` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
