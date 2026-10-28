-- V17: čekiranje na času (studentski deo, faza 1; spec docs/superpowers/specs/2026-10-28-studentski-portal-design.md, 4.9
-- u deploy repou). Privremen broj: pri merge-u u staging dobija sledeći slobodan (pre merge-a proveriti origin/staging).
-- Aditivno: dve nove tabele i dve nullable kolone predavanja. Starija slika ih ne mapira (ddl-auto=validate prolazi,
-- rollback bez rada na bazi). Idempotentno kao V2-V16 (CREATE TABLE IF NOT EXISTS, kolone preko information_schema).
-- cekiranja: ko se kad čekirao QR-om na kom predavanju (jedan red po studentu i predavanju); uredjaj_hash je SHA-256
-- kolačića gfs_uredjaj (oznaka telefona, "isti telefon kao X"). cekiranja_na_cekanju: jednokratni ključ za studenta koji
-- je skenirao QR pre prijave (u bazi samo SHA-256 ključa, 15 min). predavanja.cekiranje_tajna: HMAC tajna QR-a, briše
-- se kad se predavanje završi; cekiranje_tajna_od: kad je napravljena (posle 5 h se pravi nova).

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'predavanja' AND COLUMN_NAME = 'cekiranje_tajna');
SET @sql := IF(@ima = 0, 'ALTER TABLE `predavanja` ADD COLUMN `cekiranje_tajna` char(32) DEFAULT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'predavanja' AND COLUMN_NAME = 'cekiranje_tajna_od');
SET @sql := IF(@ima = 0, 'ALTER TABLE `predavanja` ADD COLUMN `cekiranje_tajna_od` datetime(6) DEFAULT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

CREATE TABLE IF NOT EXISTS `cekiranja` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `predavanje_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `vreme` datetime(6) NOT NULL,
  `uredjaj_hash` char(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cekiranja_predavanje_student` (`predavanje_id`, `student_id`),
  KEY `idx_cekiranja_student_vreme` (`student_id`, `vreme`),
  CONSTRAINT `fk_cekiranja_predavanje` FOREIGN KEY (`predavanje_id`) REFERENCES `predavanja` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_cekiranja_student` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `cekiranja_na_cekanju` (
  `kljuc_hash` char(64) NOT NULL,
  `predavanje_id` bigint NOT NULL,
  `skenirano` datetime(6) NOT NULL,
  `istice` datetime(6) NOT NULL,
  `iskorisceno` datetime(6) DEFAULT NULL,
  `uredjaj_hash` char(64) NOT NULL,
  PRIMARY KEY (`kljuc_hash`),
  KEY `idx_cekiranja_na_cekanju_uredjaj` (`predavanje_id`, `uredjaj_hash`),
  KEY `idx_cekiranja_na_cekanju_istice` (`istice`),
  CONSTRAINT `fk_cekiranja_na_cekanju_predavanje` FOREIGN KEY (`predavanje_id`) REFERENCES `predavanja` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
