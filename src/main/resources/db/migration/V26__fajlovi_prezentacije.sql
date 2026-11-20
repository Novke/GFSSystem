-- V26: fajlovi uz prezentaciju (spec docs/superpowers/specs/2026-11-20-fajlovi-uz-prezentaciju-design.md, odeljak 2).
-- fajlovi_prezentacije: fajl (docx, xlsx, pptx, pdf, zip) okačen uz prezentaciju, opciono uz jedan njen slajd
--   (slajd_id NULL = uz celu prezentaciju; brisanje slajda ostavlja fajl uz celu prezentaciju). Bajtovi su u mediji
--   (vrsta FAJL_PREZENTACIJE, FK RESTRICT: medij se briše tek posle reda). Brisanje prezentacije briše redove (CASCADE),
--   ali medije briše servis pre toga (inače bi ostali siročad).
-- kodovi_fajlova: kratak kod (1000-9999) za /f/<kod> na času, bez prijave. aktivan_kod = kod dok važi, NULL kad istekne
--   (UNIQUE ključ među važećima, istekli broj se oslobađa). Tačno jedan od predavanje_id / izvodjenje_id je popunjen:
--   MySQL ne dozvoljava CHECK nad kolonama sa FK akcijom CASCADE, pa to čuva samo KodoviFajlova.
-- slajdovi_izvodjenja: slajd koji je konzola prikazala izvođenju (svako izvođenje, i bez grupe); fajl uz slajd je na spisku
--   koda tek kad je slajd prikazan. Odvojeno od prikazani_slajdovi (portal, samo izvođenja sa grupom) koja ostaje kakva je.
-- Broj: proveri origin/staging pre merge-a; scripts/provera-migracija.sh (CI) odbija dupli broj.
-- Aditivno: samo nove tabele (ddl-auto=validate stare slike ih ne mapira, rollback bez rada na bazi).
-- Idempotentno kao V2-V25 (CREATE TABLE IF NOT EXISTS).

CREATE TABLE IF NOT EXISTS `fajlovi_prezentacije` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `prezentacija_id` bigint NOT NULL,
  `slajd_id` bigint DEFAULT NULL,
  `medij_id` varchar(36) NOT NULL,
  `naziv` varchar(255) NOT NULL,
  `rb` int NOT NULL,
  `serija` varchar(60) DEFAULT NULL,
  `kreirano` datetime(6) NOT NULL,
  `izmenjeno` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fajlovi_prezentacije_medij` (`medij_id`),
  KEY `idx_fajlovi_prezentacije_prez_rb` (`prezentacija_id`, `rb`),
  KEY `idx_fajlovi_prezentacije_slajd` (`slajd_id`),
  CONSTRAINT `fk_fajlovi_prezentacije_prez` FOREIGN KEY (`prezentacija_id`) REFERENCES `prezentacije` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_fajlovi_prezentacije_slajd` FOREIGN KEY (`slajd_id`) REFERENCES `slajdovi` (`id`)
    ON DELETE SET NULL,
  CONSTRAINT `fk_fajlovi_prezentacije_medij` FOREIGN KEY (`medij_id`) REFERENCES `mediji` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `kodovi_fajlova` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kod` char(4) NOT NULL,
  `aktivan_kod` char(4) DEFAULT NULL,
  `predavanje_id` bigint DEFAULT NULL,
  `izvodjenje_id` bigint DEFAULT NULL,
  `kreirano` datetime(6) NOT NULL,
  `vazi_do` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kodovi_fajlova_aktivan_kod` (`aktivan_kod`),
  UNIQUE KEY `uk_kodovi_fajlova_predavanje` (`predavanje_id`),
  UNIQUE KEY `uk_kodovi_fajlova_izvodjenje` (`izvodjenje_id`),
  CONSTRAINT `fk_kodovi_fajlova_predavanje` FOREIGN KEY (`predavanje_id`) REFERENCES `predavanja` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_kodovi_fajlova_izvodjenje` FOREIGN KEY (`izvodjenje_id`) REFERENCES `izvodjenja` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `slajdovi_izvodjenja` (
  `izvodjenje_id` bigint NOT NULL,
  `slajd_id` bigint NOT NULL,
  `prvi_put` datetime(6) NOT NULL,
  PRIMARY KEY (`izvodjenje_id`, `slajd_id`),
  KEY `idx_slajdovi_izvodjenja_slajd` (`slajd_id`),
  CONSTRAINT `fk_slajdovi_izvodjenja_izvodjenje` FOREIGN KEY (`izvodjenje_id`) REFERENCES `izvodjenja` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_slajdovi_izvodjenja_slajd` FOREIGN KEY (`slajd_id`) REFERENCES `slajdovi` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
