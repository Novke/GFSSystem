-- V11: banka zadataka (faza 1): stablo grupa zadataka po predmetu (namena PREDAVANJE ili TEST, grupa može imati podgrupe),
-- varijante zadataka (LaTeX tekst i rešenje, odgovori, render u SVG) i njihovi prilozi; mediji dobijaju vrstu
-- (SLIKA, PRILOG_PDF, RENDER_SVG), a predmeti LaTeX preambulu koja se dodaje svakom renderu.
-- Aditivno: stara slika ne zna za nove tabele, a nove kolone su nullable ili imaju podrazumevanu vrednost
-- (mediji.vrsta = 'SLIKA'), pa ddl-auto=validate prolazi i rollback radi bez DB posla.
-- Idempotentno kao V2-V10: tabele CREATE TABLE IF NOT EXISTS (redosled poštuje strane ključeve), kolone preko
-- information_schema.COLUMNS + PREPARE (MySQL 8.0 nema ADD COLUMN IF NOT EXISTS).
-- Enum kolone su varchar(20) (entiteti: @Enumerated(STRING) + @JdbcTypeCode(SqlTypes.VARCHAR)). JSON kolone
-- (grupe_zadataka.polja, varijante.odgovori) su običan text sa JSON stringom, bez MySQL JSON tipa.
-- Brisanje: grupa sa podgrupama ili varijantama se ne briše u bazi (RESTRICT, servis briše redom), prilozi
-- nestaju sa varijantom (CASCADE).

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mediji' AND COLUMN_NAME = 'vrsta');
SET @sql := IF(@ima = 0, 'ALTER TABLE `mediji` ADD COLUMN `vrsta` varchar(20) NOT NULL DEFAULT ''SLIKA''', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'predmeti' AND COLUMN_NAME = 'latex_preambula');
SET @sql := IF(@ima = 0, 'ALTER TABLE `predmeti` ADD COLUMN `latex_preambula` mediumtext NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

CREATE TABLE IF NOT EXISTS `grupe_zadataka` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `predmet_id` bigint NOT NULL,
  `roditelj_id` bigint DEFAULT NULL,
  `namena` varchar(20) NOT NULL,
  `naziv` varchar(120) NOT NULL,
  `opis` text DEFAULT NULL,
  `rb` int NOT NULL,
  `tezina` tinyint DEFAULT NULL,
  `uputstvo` text DEFAULT NULL,
  `polja` text NOT NULL,
  `kreirano` datetime(6) NOT NULL,
  `izmenjeno` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_grupe_zadataka_stablo` (`predmet_id`, `roditelj_id`, `rb`),
  CONSTRAINT `fk_grupe_zadataka_predmet` FOREIGN KEY (`predmet_id`) REFERENCES `predmeti` (`id`),
  CONSTRAINT `fk_grupe_zadataka_roditelj` FOREIGN KEY (`roditelj_id`) REFERENCES `grupe_zadataka` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `varijante` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `grupa_id` bigint NOT NULL,
  `rb` int NOT NULL,
  `naziv` varchar(120) DEFAULT NULL,
  `tekst` mediumtext NOT NULL,
  `resenje` mediumtext DEFAULT NULL,
  `odgovori` text NOT NULL,
  `status` varchar(20) NOT NULL,
  `izvor` varchar(20) NOT NULL,
  `render_status` varchar(20) NOT NULL,
  `render_svg_id` varchar(36) DEFAULT NULL,
  `render_resenje_svg_id` varchar(36) DEFAULT NULL,
  `render_greska` text DEFAULT NULL,
  `render_hash` char(64) DEFAULT NULL,
  `kreirano` datetime(6) NOT NULL,
  `izmenjeno` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_varijante_grupa_status` (`grupa_id`, `status`),
  CONSTRAINT `fk_varijante_grupa` FOREIGN KEY (`grupa_id`) REFERENCES `grupe_zadataka` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_varijante_render_svg` FOREIGN KEY (`render_svg_id`) REFERENCES `mediji` (`id`),
  CONSTRAINT `fk_varijante_render_resenje_svg` FOREIGN KEY (`render_resenje_svg_id`) REFERENCES `mediji` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `varijanta_prilozi` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `varijanta_id` bigint NOT NULL,
  `ime` varchar(60) NOT NULL,
  `medij_id` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_varijanta_prilozi_ime` (`varijanta_id`, `ime`),
  CONSTRAINT `fk_varijanta_prilozi_varijanta` FOREIGN KEY (`varijanta_id`) REFERENCES `varijante` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_varijanta_prilozi_medij` FOREIGN KEY (`medij_id`) REFERENCES `mediji` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
