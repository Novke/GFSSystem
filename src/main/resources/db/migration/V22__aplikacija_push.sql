-- V22: aplikacija na telefonu i push obaveštenja, studentski portal faza 4 (spec
-- docs/superpowers/specs/2026-10-29-aplikacija-i-obavestenja-design.md, odeljci 5, 6 i 6.7, u deploy repou).
-- Broj: V19 je trajno neiskorišćen, V21 je redosled prezentacija; proveri origin/staging pre merge-a
-- (scripts/provera-migracija.sh u CI-ju odbija dupli broj i broj koji nije veći od najvećeg u osnovi PR-a).
-- Aditivno: nove tabele i nove kolone sa podrazumevanom vrednošću ili NULL. Starija slika ih ne mapira (ddl-auto=validate
-- prolazi, rollback bez rada na bazi; push tada prosto prestaje). VAPID ključevi su obični redovi u kljucevi_aplikacije
-- (pravi ih aplikacija pri prvoj upotrebi, ne ova migracija).
-- Idempotentno kao V2-V21: CREATE TABLE IF NOT EXISTS, kolone preko information_schema + PREPARE (MySQL 8.0 nema
-- ADD COLUMN IF NOT EXISTS).
--
-- push_pretplate: pretplata jednog uređaja studenta ILI jedne sesije nastavnika (tačno jedno od dva, proverava kod); jedan
--   uređaj ili sesija ima najviše jednu (jedinstven ključ dozvoljava više NULL-ova), isti endpoint najviše jednom
--   (endpoint_hes = SHA-256 endpoint-a). Brisanje uređaja ili sesije briše pretplatu (CASCADE).
-- studentska_obavestenja: zvono studenta i jedini izvor push poruka studentu (red po događaju i studentu, sadržaj zamrznut u
--   trenutku događaja); kljuc je jedinstven po studentu (PODSETNIK:<datum>, OTKAZAN:<stavka>:<datum>:<n>, ...).
-- podesavanja_studenta: prekidači push-a i zvona po vrsti (podrazumevano uključeni) i vreme poslednjeg otvaranja zvona.
-- podesavanja_nastavnika_push: red po vrsti nastavničkog obaveštenja; bez reda = push uključen.
-- rasporedi.podsetnik_vreme: vreme podsetnika dan pre časa (NULL = isključen); postojeći periodi dobijaju 18:00.
-- posle_casa_poslato: zbirno obaveštenje "posle časa" po grupi i danu: kad dospeva (30 min posle poslednjeg izvođenja) i
--   kad je obrađeno (idempotentnost posle restarta).

CREATE TABLE IF NOT EXISTS `push_pretplate` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `endpoint` varchar(1000) NOT NULL,
  `endpoint_hes` char(64) NOT NULL,
  `p256dh` varchar(100) NOT NULL,
  `auth` varchar(40) NOT NULL,
  `studentski_uredjaj_id` bigint DEFAULT NULL,
  `nastavnicka_sesija_id` bigint DEFAULT NULL,
  `kreirano` datetime(3) NOT NULL,
  `poslednji_uspeh` datetime(3) DEFAULT NULL,
  `uzastopne_greske` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_push_pretplate_endpoint_hes` (`endpoint_hes`),
  UNIQUE KEY `uk_push_pretplate_uredjaj` (`studentski_uredjaj_id`),
  UNIQUE KEY `uk_push_pretplate_sesija` (`nastavnicka_sesija_id`),
  CONSTRAINT `fk_push_pretplate_uredjaj` FOREIGN KEY (`studentski_uredjaj_id`) REFERENCES `studentski_uredjaji` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_push_pretplate_sesija` FOREIGN KEY (`nastavnicka_sesija_id`) REFERENCES `nastavnicke_sesije` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `studentska_obavestenja` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `vrsta` varchar(30) NOT NULL,
  `kljuc` varchar(120) NOT NULL,
  `naslov` varchar(120) NOT NULL,
  `tekst` varchar(400) NOT NULL,
  `putanja` varchar(120) NOT NULL,
  `oznaka` varchar(80) NOT NULL,
  `kreirano` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_studentska_obavestenja_kljuc` (`student_id`, `kljuc`),
  KEY `idx_studentska_obavestenja_student` (`student_id`, `kreirano`),
  KEY `idx_studentska_obavestenja_kreirano` (`kreirano`),
  CONSTRAINT `fk_studentska_obavestenja_student` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `podesavanja_nastavnika_push` (
  `vrsta` varchar(40) NOT NULL,
  `ukljuceno` bit(1) NOT NULL DEFAULT b'1',
  `izmenjeno` datetime(3) NOT NULL,
  PRIMARY KEY (`vrsta`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `posle_casa_poslato` (
  `grupa_id` bigint NOT NULL,
  `datum` date NOT NULL,
  `dospeva` datetime(3) NOT NULL,
  `poslato` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`grupa_id`, `datum`),
  KEY `idx_posle_casa_poslato_dospeva` (`poslato`, `dospeva`),
  CONSTRAINT `fk_posle_casa_poslato_grupa` FOREIGN KEY (`grupa_id`) REFERENCES `grupe` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- podesavanja_studenta: prekidači push-a i zvona
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'podesavanja_studenta' AND COLUMN_NAME = 'push_podsetnik');
SET @sql := IF(@ima = 0, 'ALTER TABLE `podesavanja_studenta` ADD COLUMN `push_podsetnik` bit(1) NOT NULL DEFAULT b''1''', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'podesavanja_studenta' AND COLUMN_NAME = 'push_otkazan_cas');
SET @sql := IF(@ima = 0, 'ALTER TABLE `podesavanja_studenta` ADD COLUMN `push_otkazan_cas` bit(1) NOT NULL DEFAULT b''1''', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'podesavanja_studenta' AND COLUMN_NAME = 'push_ocene');
SET @sql := IF(@ima = 0, 'ALTER TABLE `podesavanja_studenta` ADD COLUMN `push_ocene` bit(1) NOT NULL DEFAULT b''1''', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'podesavanja_studenta' AND COLUMN_NAME = 'push_posle_casa');
SET @sql := IF(@ima = 0, 'ALTER TABLE `podesavanja_studenta` ADD COLUMN `push_posle_casa` bit(1) NOT NULL DEFAULT b''1''', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'podesavanja_studenta' AND COLUMN_NAME = 'zvono_ocene');
SET @sql := IF(@ima = 0, 'ALTER TABLE `podesavanja_studenta` ADD COLUMN `zvono_ocene` bit(1) NOT NULL DEFAULT b''1''', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'podesavanja_studenta' AND COLUMN_NAME = 'zvono_posle_casa');
SET @sql := IF(@ima = 0, 'ALTER TABLE `podesavanja_studenta` ADD COLUMN `zvono_posle_casa` bit(1) NOT NULL DEFAULT b''1''', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'podesavanja_studenta' AND COLUMN_NAME = 'zvono_otvoreno');
SET @sql := IF(@ima = 0, 'ALTER TABLE `podesavanja_studenta` ADD COLUMN `zvono_otvoreno` datetime(3) NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- rasporedi: vreme podsetnika (postojeći dobijaju 18:00)
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rasporedi' AND COLUMN_NAME = 'podsetnik_vreme');
SET @sql := IF(@ima = 0, 'ALTER TABLE `rasporedi` ADD COLUMN `podsetnik_vreme` time NULL DEFAULT ''18:00:00''', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
