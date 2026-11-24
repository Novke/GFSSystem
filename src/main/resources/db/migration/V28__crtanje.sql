-- V28: crtanje u izvođenju i tabla sa predavanja (spec docs/superpowers/specs/2026-11-23-crtanje-uzivo-design.md u GFS-deploy,
-- odeljak 4; ugovor u docs/superpowers/plans/2026-11-23-crtanje-uzivo.md).
-- crtezi_izvodjenja: crtež jedne strane izvođenja; strana = (slajd, pozadina SLAJD | BELA). Red nastaje prvim crtanjem na strani,
--   "Obriši sve" upisuje prazan niz (red ostaje, verzija raste). Brisanje izvođenja (pa i prezentacije) briše redove (CASCADE);
--   brisanje slajda ostavlja crtež u pregledu izvođenja kao "obrisan slajd" (SET NULL). "Ne čuvaj" ih ne briše.
-- table_predavanja: jedna bela tabla po predavanju (dugme "Tabla" na predavanju, bez prezentacije).
-- izvodjenja.bela_slajd_id: slajd na kom je beli ekran (W) uključen; bela strana je (bela_slajd_id, BELA). Bez FK: obrisan
--   slajd samo znači da bela strana više nema slajd (snimak tada nema trenutnu stranu dok se beli ekran ponovo ne uključi).
-- Broj: proveri origin/staging pre merge-a; scripts/provera-migracija.sh (CI) odbija dupli broj.
-- Aditivno: nove tabele i jedna nullable kolona (ddl-auto=validate stare slike ih ne mapira, rollback bez rada na bazi).
-- Idempotentno kao V2-V26 (CREATE TABLE IF NOT EXISTS, kolona kroz information_schema).

CREATE TABLE IF NOT EXISTS `crtezi_izvodjenja` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `izvodjenje_id` bigint NOT NULL,
  `slajd_id` bigint DEFAULT NULL,
  `pozadina` varchar(20) NOT NULL,
  `elementi` mediumtext NOT NULL,
  `verzija` int NOT NULL,
  `izmenjeno` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_crtezi_izvodjenja_strana` (`izvodjenje_id`, `slajd_id`, `pozadina`),
  KEY `idx_crtezi_izvodjenja_slajd` (`slajd_id`),
  CONSTRAINT `fk_crtezi_izvodjenja_izvodjenje` FOREIGN KEY (`izvodjenje_id`) REFERENCES `izvodjenja` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_crtezi_izvodjenja_slajd` FOREIGN KEY (`slajd_id`) REFERENCES `slajdovi` (`id`)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `table_predavanja` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `predavanje_id` bigint NOT NULL,
  `elementi` mediumtext NOT NULL,
  `verzija` int NOT NULL,
  `izmenjeno` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_table_predavanja_predavanje` (`predavanje_id`),
  CONSTRAINT `fk_table_predavanja_predavanje` FOREIGN KEY (`predavanje_id`) REFERENCES `predavanja` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------- izvodjenja: bela_slajd_id
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'izvodjenja' AND COLUMN_NAME = 'bela_slajd_id');
SET @sql := IF(@ima = 0, 'ALTER TABLE `izvodjenja` ADD COLUMN `bela_slajd_id` bigint DEFAULT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
