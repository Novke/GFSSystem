-- V24: Klodove izmene postojećih slajdova i preocena odgovora (spec docs/superpowers/specs/2026-11-04-mcp-nivoi-potvrde-design.md
-- u GFS-deploy). Broj je privremen dok grana ne uđe u staging: V23 drži otvorena grana feature/stariji-student, pa ova ide
-- posle nje; proveri origin/staging pre merge-a (scripts/provera-migracija.sh odbija dupli broj i broj koji nije veći od
-- najvećeg u osnovi PR-a).
-- slajdovi.verzija: broj Klodovih izmena slajda (MCP i vraćanje iz istorije; nastavnikov PUT je ne menja). Editor je šalje uz
-- PUT, pa posle Klodove izmene otvoren editor dobija 409 umesto da je tiho pregazi.
-- slajd_izmene: snimak slajda pre svake Klodove izmene i brisanja, da se izmena vrati (vrati_izmenu). Bez FK prema slajdovi:
-- unos brisanja mora da nadživi slajd. preocene_odgovora: trag svake izmene odgovora posle časa, sa svim što treba za tačan
-- povratak. varijante.zamenjena_kopijom_id: kopija čije je odobrenje arhiviralo original (bez FK: viseća vrednost samo ne
-- pogađa nijednu kopiju), da povlačenje odobrenja ne aktivira ručno arhiviran original.
-- Aditivno: dve kolone (DDL DEFAULT, odnosno nullable) i dve nove tabele. Rollback (stara slika, pre V24): ddl-auto=validate ne gleda
-- višak, Flyway preskače budući V24, a stara slika ne čita ni kolonu ni tabele, pa ne treba rad u bazi.
-- Idempotentno kao V2-V21: kolona preko information_schema + PREPARE (MySQL 8.0 nema ADD COLUMN IF NOT EXISTS).

-- ---------------------------------------------------------------- slajdovi: verzija
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'slajdovi' AND COLUMN_NAME = 'verzija');
SET @sql := IF(@ima = 0, 'ALTER TABLE `slajdovi` ADD COLUMN `verzija` int NOT NULL DEFAULT 0', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- varijante: original zamenjen kopijom
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'varijante' AND COLUMN_NAME = 'zamenjena_kopijom_id');
SET @sql := IF(@ima = 0, 'ALTER TABLE `varijante` ADD COLUMN `zamenjena_kopijom_id` bigint DEFAULT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------- istorija slajdova
CREATE TABLE IF NOT EXISTS `slajd_izmene` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `prezentacija_id` bigint NOT NULL,
  `slajd_id` bigint NOT NULL,
  `rb` int NOT NULL,
  `vrsta` varchar(20) NOT NULL,
  `serija` varchar(60) DEFAULT NULL,
  `opis` varchar(300) NOT NULL,
  `verzija_pre` int DEFAULT NULL,
  `snimak` mediumtext,
  `kreirano` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_slajd_izmene_slajd` (`slajd_id`, `id`),
  KEY `idx_slajd_izmene_prezentacija` (`prezentacija_id`, `id`),
  CONSTRAINT `fk_slajd_izmene_prezentacija` FOREIGN KEY (`prezentacija_id`) REFERENCES `prezentacije` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- trag preocene odgovora
CREATE TABLE IF NOT EXISTS `preocene_odgovora` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `odgovor_id` bigint NOT NULL,
  `staro_tacno` tinyint(1) DEFAULT NULL,
  `novo_tacno` tinyint(1) NOT NULL,
  `stara_polja` text,
  `stari_poeni` int NOT NULL DEFAULT 0,
  `razlog` varchar(300) NOT NULL,
  `serija` varchar(60) DEFAULT NULL,
  `kreirano` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_preocene_odgovor` (`odgovor_id`),
  CONSTRAINT `fk_preocene_odgovor` FOREIGN KEY (`odgovor_id`) REFERENCES `odgovori` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
