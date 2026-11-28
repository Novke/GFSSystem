-- V29: online testovi, faze 1 i 2 (spec docs/superpowers/specs/2026-11-28-online-testovi-design.md u GFS-deploy, odeljci 6-14
-- i 11; plan i ugovor docs/superpowers/plans/2026-11-28-online-testovi-faze-1-2.md, odeljak 1).
-- Broj je privremen: GFSSystem #55 takođe traži V29; ko se spoji drugi u staging, prenumeriše (scripts/provera-migracija.sh).
--
-- grupe_zadataka + bodovanje (PO_POLJIMA | SVE_ILI_NISTA), konacno_priznaje_korake: kako test boduje zadatak tog tipa
--   (Uživo i vežbanje ih ne čitaju). Poeni i "konačno" po polju žive u JSON šemi polja (bez migracije).
-- testovi + nacin (PAPIR podrazumevano | ONLINE), sablon_id (SET NULL), rezim, trajanje_min, seme, objavljeno, objava_tacni.
-- sabloni_testa, sablon_stavke, sablon_stavka_izvori: šablon (recept) testa; brisanje čvora banke briše red izvora.
-- test_stavke: prepis stavki šablona na testu (izvori kao JSON lista id-jeva, izabrani nivo za ceo test).
-- test_smene: u fazi 1 tačno jedna smena po testu (ulazni kod, ulaz, pokretanje, zajednički rok, zatvaranje).
-- test_izbor: plan izvlačenja (GRUPE: grupa testa x stavka x mesto -> varijanta; POJEDINACNO: stavka x mesto -> tip).
-- test_primerci: test jednog studenta (UNIQUE test+student); test_primerak_zadaci: zadaci primerka sa snimkom varijante,
--   odgovorima (rev), ocenom po polju i poenima. varijanta_id je RESTRICT (varijanta sa testa se ne briše, 409 u servisu).
-- test_dogadjaji: meki znaci (ulazak, drugi uređaj, napuštanje prozora, predaja, produženje).
-- test_ispravke: ispravke ključa nad testom (PRIHVATI, PONISTI, KLJUC), vraćanje ostavlja red sa `vracena`.
-- prijave_racunara (faza 2): zahtev za prijavu računara QR-om; u bazi samo SHA-256 tokena i tajne.
--
-- Aditivno: nove tabele i kolone sa DDL podrazumevanim vrednostima. Stara slika ne čita studentski_uredjaji.nacin = 'RACUNAR'
-- (nepoznata enum vrednost): pre vraćanja preko V29 `delete from studentski_uredjaji where nacin = 'RACUNAR'`.
-- Idempotentno kao V2-V28 (CREATE TABLE IF NOT EXISTS, kolone i FK kroz information_schema).

-- ---------------------------------------------------------------- grupe_zadataka: bodovanje
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grupe_zadataka' AND COLUMN_NAME = 'bodovanje');
SET @sql := IF(@ima = 0, 'ALTER TABLE `grupe_zadataka` ADD COLUMN `bodovanje` varchar(20) NOT NULL DEFAULT ''PO_POLJIMA''', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grupe_zadataka' AND COLUMN_NAME = 'konacno_priznaje_korake');
SET @sql := IF(@ima = 0, 'ALTER TABLE `grupe_zadataka` ADD COLUMN `konacno_priznaje_korake` bit(1) NOT NULL DEFAULT b''0''', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- sabloni_testa
CREATE TABLE IF NOT EXISTS `sabloni_testa` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `predmet_id` bigint NOT NULL,
  `tip_testa_id` bigint DEFAULT NULL,
  `naziv` varchar(120) NOT NULL,
  `opis` text,
  `trajanje_min` int NOT NULL,
  `rezim` varchar(20) NOT NULL,
  `broj_grupa` int NOT NULL,
  `prag_prolaza` int DEFAULT NULL,
  `arhiviran` bit(1) NOT NULL DEFAULT b'0',
  `kreirano` datetime(6) NOT NULL,
  `izmenjeno` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_sabloni_testa_predmet` (`predmet_id`, `arhiviran`),
  KEY `idx_sabloni_testa_tip` (`tip_testa_id`),
  CONSTRAINT `fk_sabloni_testa_predmet` FOREIGN KEY (`predmet_id`) REFERENCES `predmeti` (`id`),
  CONSTRAINT `fk_sabloni_testa_tip` FOREIGN KEY (`tip_testa_id`) REFERENCES `tipovi_testa` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sablon_stavke` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sablon_id` bigint NOT NULL,
  `rb` int NOT NULL,
  `naziv` varchar(120) NOT NULL,
  `poeni` int NOT NULL,
  `broj` int NOT NULL,
  `nivoi` varchar(10) DEFAULT NULL,
  `isti_tip` bit(1) NOT NULL DEFAULT b'1',
  `rucno` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_sablon_stavke_sablon` (`sablon_id`, `rb`),
  CONSTRAINT `fk_sablon_stavke_sablon` FOREIGN KEY (`sablon_id`) REFERENCES `sabloni_testa` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `sablon_stavka_izvori` (
  `stavka_id` bigint NOT NULL,
  `grupa_zadataka_id` bigint NOT NULL,
  PRIMARY KEY (`stavka_id`, `grupa_zadataka_id`),
  KEY `idx_sablon_stavka_izvori_grupa` (`grupa_zadataka_id`),
  CONSTRAINT `fk_sablon_stavka_izvori_stavka` FOREIGN KEY (`stavka_id`) REFERENCES `sablon_stavke` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_sablon_stavka_izvori_grupa` FOREIGN KEY (`grupa_zadataka_id`) REFERENCES `grupe_zadataka` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- testovi: online kolone
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'testovi' AND COLUMN_NAME = 'nacin');
SET @sql := IF(@ima = 0, 'ALTER TABLE `testovi` ADD COLUMN `nacin` varchar(20) NOT NULL DEFAULT ''PAPIR''', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'testovi' AND COLUMN_NAME = 'sablon_id');
SET @sql := IF(@ima = 0, 'ALTER TABLE `testovi` ADD COLUMN `sablon_id` bigint DEFAULT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'testovi' AND COLUMN_NAME = 'rezim');
SET @sql := IF(@ima = 0, 'ALTER TABLE `testovi` ADD COLUMN `rezim` varchar(20) DEFAULT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'testovi' AND COLUMN_NAME = 'trajanje_min');
SET @sql := IF(@ima = 0, 'ALTER TABLE `testovi` ADD COLUMN `trajanje_min` int DEFAULT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'testovi' AND COLUMN_NAME = 'seme');
SET @sql := IF(@ima = 0, 'ALTER TABLE `testovi` ADD COLUMN `seme` bigint DEFAULT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'testovi' AND COLUMN_NAME = 'objavljeno');
SET @sql := IF(@ima = 0, 'ALTER TABLE `testovi` ADD COLUMN `objavljeno` datetime(6) DEFAULT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'testovi' AND COLUMN_NAME = 'objava_tacni');
SET @sql := IF(@ima = 0, 'ALTER TABLE `testovi` ADD COLUMN `objava_tacni` bit(1) NOT NULL DEFAULT b''0''', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'testovi' AND CONSTRAINT_NAME = 'fk_testovi_sablon');
SET @sql := IF(@ima = 0, 'ALTER TABLE `testovi` ADD CONSTRAINT `fk_testovi_sablon` FOREIGN KEY (`sablon_id`) REFERENCES `sabloni_testa` (`id`) ON DELETE SET NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- stavke, smene, izbor
CREATE TABLE IF NOT EXISTS `test_stavke` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `test_id` bigint NOT NULL,
  `rb` int NOT NULL,
  `naziv` varchar(120) NOT NULL,
  `poeni` int NOT NULL,
  `broj` int NOT NULL,
  `nivoi` varchar(10) DEFAULT NULL,
  `isti_tip` bit(1) NOT NULL DEFAULT b'1',
  `rucno` bit(1) NOT NULL DEFAULT b'0',
  `izvori` text NOT NULL,
  `izabrani_nivo` tinyint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_test_stavke_test` (`test_id`, `rb`),
  CONSTRAINT `fk_test_stavke_test` FOREIGN KEY (`test_id`) REFERENCES `testovi` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `test_smene` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `test_id` bigint NOT NULL,
  `rb` int NOT NULL,
  `ulazni_kod` char(4) DEFAULT NULL,
  `ulaz_otvoren` datetime(6) DEFAULT NULL,
  `pokrenuta` datetime(6) DEFAULT NULL,
  `rok` datetime(6) DEFAULT NULL,
  `zatvorena` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_test_smene_rb` (`test_id`, `rb`),
  KEY `idx_test_smene_otvorene` (`zatvorena`, `pokrenuta`),
  CONSTRAINT `fk_test_smene_test` FOREIGN KEY (`test_id`) REFERENCES `testovi` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `test_izbor` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `test_id` bigint NOT NULL,
  `smena_id` bigint NOT NULL,
  `grupa_testa` char(1) DEFAULT NULL,
  `stavka_id` bigint NOT NULL,
  `mesto` int NOT NULL,
  `tip_id` bigint DEFAULT NULL,
  `varijanta_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_test_izbor_test` (`test_id`),
  KEY `idx_test_izbor_smena` (`smena_id`),
  KEY `idx_test_izbor_stavka` (`stavka_id`),
  KEY `idx_test_izbor_tip` (`tip_id`),
  KEY `idx_test_izbor_varijanta` (`varijanta_id`),
  CONSTRAINT `fk_test_izbor_test` FOREIGN KEY (`test_id`) REFERENCES `testovi` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_test_izbor_smena` FOREIGN KEY (`smena_id`) REFERENCES `test_smene` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_test_izbor_stavka` FOREIGN KEY (`stavka_id`) REFERENCES `test_stavke` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_test_izbor_tip` FOREIGN KEY (`tip_id`) REFERENCES `grupe_zadataka` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_test_izbor_varijanta` FOREIGN KEY (`varijanta_id`) REFERENCES `varijante` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- primerci i njihovi zadaci
CREATE TABLE IF NOT EXISTS `test_primerci` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `test_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `smena_id` bigint NOT NULL,
  `grupa_testa` char(1) DEFAULT NULL,
  `stanje` varchar(20) NOT NULL,
  `usao` datetime(6) DEFAULT NULL,
  `produzenje_min` int NOT NULL DEFAULT 0,
  `predato` datetime(6) DEFAULT NULL,
  `nacin_predaje` varchar(20) DEFAULT NULL,
  `poeni` double DEFAULT NULL,
  `prepisivao` bit(1) NOT NULL DEFAULT b'0',
  `aktivni_uredjaj_id` bigint DEFAULT NULL,
  `ip` varchar(64) DEFAULT NULL,
  `napomena` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_test_primerci_student` (`test_id`, `student_id`),
  KEY `idx_test_primerci_student` (`student_id`),
  KEY `idx_test_primerci_smena` (`smena_id`),
  KEY `idx_test_primerci_uredjaj` (`aktivni_uredjaj_id`),
  CONSTRAINT `fk_test_primerci_test` FOREIGN KEY (`test_id`) REFERENCES `testovi` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_test_primerci_student` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_test_primerci_smena` FOREIGN KEY (`smena_id`) REFERENCES `test_smene` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_test_primerci_uredjaj` FOREIGN KEY (`aktivni_uredjaj_id`) REFERENCES `studentski_uredjaji` (`id`)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `test_primerak_zadaci` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `primerak_id` bigint NOT NULL,
  `rb` int NOT NULL,
  `stavka_id` bigint DEFAULT NULL,
  `mesto` int NOT NULL,
  `varijanta_id` bigint NOT NULL,
  `snimak` mediumtext NOT NULL,
  `max_poena` double NOT NULL,
  `odgovori` text,
  `rev` int NOT NULL DEFAULT 0,
  `sacuvano` datetime(6) DEFAULT NULL,
  `ocena` text,
  `poeni` double DEFAULT NULL,
  `rucni_poeni` double DEFAULT NULL,
  `napomena` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_test_primerak_zadaci_rb` (`primerak_id`, `rb`),
  KEY `idx_test_primerak_zadaci_stavka` (`stavka_id`),
  KEY `idx_test_primerak_zadaci_varijanta` (`varijanta_id`),
  CONSTRAINT `fk_test_primerak_zadaci_primerak` FOREIGN KEY (`primerak_id`) REFERENCES `test_primerci` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_test_primerak_zadaci_stavka` FOREIGN KEY (`stavka_id`) REFERENCES `test_stavke` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_test_primerak_zadaci_varijanta` FOREIGN KEY (`varijanta_id`) REFERENCES `varijante` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `test_dogadjaji` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `primerak_id` bigint NOT NULL,
  `vreme` datetime(6) NOT NULL,
  `vrsta` varchar(30) NOT NULL,
  `detalj` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_test_dogadjaji_primerak` (`primerak_id`, `vreme`),
  CONSTRAINT `fk_test_dogadjaji_primerak` FOREIGN KEY (`primerak_id`) REFERENCES `test_primerci` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `test_ispravke` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `test_id` bigint NOT NULL,
  `varijanta_id` bigint NOT NULL,
  `polje` varchar(40) NOT NULL,
  `vrsta` varchar(20) NOT NULL,
  `vrednost` text,
  `kreirano` datetime(6) NOT NULL,
  `vracena` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_test_ispravke_test` (`test_id`, `varijanta_id`),
  CONSTRAINT `fk_test_ispravke_test` FOREIGN KEY (`test_id`) REFERENCES `testovi` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- faza 2: prijava računara QR-om
CREATE TABLE IF NOT EXISTS `prijave_racunara` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token_hes` char(64) NOT NULL,
  `tajna_hes` char(64) NOT NULL,
  `broj` char(4) NOT NULL,
  `ip` varchar(64) DEFAULT NULL,
  `kreirano` datetime(6) NOT NULL,
  `istice` datetime(6) NOT NULL,
  `student_id` bigint DEFAULT NULL,
  `odobreno` datetime(6) DEFAULT NULL,
  `preuzeto` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prijave_racunara_token` (`token_hes`),
  KEY `idx_prijave_racunara_tajna` (`tajna_hes`),
  KEY `idx_prijave_racunara_kreirano` (`kreirano`),
  KEY `idx_prijave_racunara_student` (`student_id`),
  CONSTRAINT `fk_prijave_racunara_student` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
