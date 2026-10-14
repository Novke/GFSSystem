-- V7: interaktivna prezentacija uživo (cilj 4, prva celina): mediji, prezentacije, slajdovi, pitanja i opcije,
-- izvođenja, učesnici, runde pitanja i odgovori. Samo nove tabele (aditivno; stara slika radi nad ovom šemom).
-- Enum kolone su varchar(20) (entiteti: @Enumerated(STRING) + @JdbcTypeCode(SqlTypes.VARCHAR)).
-- Prvobitno V5, prenumerisana u V7 jer se redizajn (V6 prag_prolaza) spaja pre; V5 ostaje trajna rupa.
-- Idempotentno kao V2-V4 i V6: reset staging baze (reset-db.sh) gradi šemu iz dump-a bez flyway_schema_history, pa
-- Flyway radi baseline 1 i ponovo izvršava V2 i dalje. Baza koja ove tabele već ima ne sme da pukne, zato je svaka
-- tabela CREATE TABLE IF NOT EXISTS (DDL inače isti; redosled poštuje strane ključeve).

CREATE TABLE IF NOT EXISTS `mediji` (
  `id` varchar(36) NOT NULL,
  `naziv` varchar(255) NOT NULL,
  `mime` varchar(100) NOT NULL,
  `velicina` bigint NOT NULL,
  `kreirano` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `prezentacije` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `predmet_id` bigint NOT NULL,
  `naziv` varchar(200) NOT NULL,
  `opis` varchar(1000) DEFAULT NULL,
  `takmicenje` bit(1) NOT NULL,
  `telefon_prikaz` varchar(20) NOT NULL,
  `detalji_dozvoljeni` bit(1) NOT NULL,
  `kreirano` datetime(6) NOT NULL,
  `izmenjeno` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_prezentacije_predmet` (`predmet_id`),
  CONSTRAINT `fk_prezentacije_predmet` FOREIGN KEY (`predmet_id`) REFERENCES `predmeti` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `pitanja` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tip` varchar(20) NOT NULL,
  `tekst` text NOT NULL,
  `slika_id` varchar(36) DEFAULT NULL,
  `vreme_sekunde` int DEFAULT NULL,
  `broj_tacno` double DEFAULT NULL,
  `broj_odstupanje` double DEFAULT NULL,
  `odstupanje_tip` varchar(20) DEFAULT NULL,
  `jedinica` varchar(30) DEFAULT NULL,
  `tekst_prikaz` varchar(20) DEFAULT NULL,
  `prihvatljivi_odgovori` text DEFAULT NULL,
  `skala_min_oznaka` varchar(60) DEFAULT NULL,
  `skala_max_oznaka` varchar(60) DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_pitanja_slika` FOREIGN KEY (`slika_id`) REFERENCES `mediji` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `pitanje_opcije` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pitanje_id` bigint NOT NULL,
  `rb` int NOT NULL,
  `tekst` varchar(300) NOT NULL,
  `tacna` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pitanje_opcije_pitanje` (`pitanje_id`),
  CONSTRAINT `fk_pitanje_opcije_pitanje` FOREIGN KEY (`pitanje_id`) REFERENCES `pitanja` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `slajdovi` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `prezentacija_id` bigint NOT NULL,
  `rb` int NOT NULL,
  `tip` varchar(20) NOT NULL,
  `naslov` varchar(300) DEFAULT NULL,
  `sadrzaj` mediumtext DEFAULT NULL,
  `slika_id` varchar(36) DEFAULT NULL,
  `beleske` text DEFAULT NULL,
  `postepeno` bit(1) NOT NULL,
  `pitanje_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_slajdovi_pitanje` (`pitanje_id`),
  KEY `idx_slajdovi_prezentacija` (`prezentacija_id`, `rb`),
  CONSTRAINT `fk_slajdovi_prezentacija` FOREIGN KEY (`prezentacija_id`) REFERENCES `prezentacije` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_slajdovi_slika` FOREIGN KEY (`slika_id`) REFERENCES `mediji` (`id`),
  CONSTRAINT `fk_slajdovi_pitanje` FOREIGN KEY (`pitanje_id`) REFERENCES `pitanja` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `izvodjenja` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `prezentacija_id` bigint NOT NULL,
  `kod` char(6) NOT NULL,
  `aktivan_kod` char(6) DEFAULT NULL,
  `grupa_id` bigint DEFAULT NULL,
  `predavanje_id` bigint DEFAULT NULL,
  `cuvanje` bit(1) NOT NULL,
  `status` varchar(20) NOT NULL,
  `pocetak` datetime(6) NOT NULL,
  `kraj` datetime(6) DEFAULT NULL,
  `trenutni_slajd_id` bigint DEFAULT NULL,
  `prikaz` varchar(20) NOT NULL,
  `korak` int NOT NULL,
  `faza` varchar(20) DEFAULT NULL,
  `trenutna_runda_id` bigint DEFAULT NULL,
  `rezultati_prikazani` bit(1) NOT NULL,
  `tacan_prikazan` bit(1) NOT NULL,
  `rang_lista_prikazana` bit(1) NOT NULL,
  `ekran` varchar(20) NOT NULL,
  `qr_prikazan` bit(1) NOT NULL,
  `telefon_prikaz` varchar(20) NOT NULL,
  `detalji_dozvoljeni` bit(1) NOT NULL,
  `takmicenje` bit(1) NOT NULL,
  `verzija` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_izvodjenja_aktivan_kod` (`aktivan_kod`),
  KEY `idx_izvodjenja_prezentacija` (`prezentacija_id`),
  KEY `idx_izvodjenja_status` (`status`),
  CONSTRAINT `fk_izvodjenja_prezentacija` FOREIGN KEY (`prezentacija_id`) REFERENCES `prezentacije` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_izvodjenja_grupa` FOREIGN KEY (`grupa_id`) REFERENCES `grupe` (`id`),
  CONSTRAINT `fk_izvodjenja_predavanje` FOREIGN KEY (`predavanje_id`) REFERENCES `predavanja` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `ucesnici` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `izvodjenje_id` bigint NOT NULL,
  `ime` varchar(40) NOT NULL,
  `token_hash` char(64) NOT NULL,
  `izbacen` bit(1) NOT NULL,
  `kreirano` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ucesnici_token_hash` (`token_hash`),
  KEY `idx_ucesnici_izvodjenje` (`izvodjenje_id`),
  CONSTRAINT `fk_ucesnici_izvodjenje` FOREIGN KEY (`izvodjenje_id`) REFERENCES `izvodjenja` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `pitanje_runde` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `izvodjenje_id` bigint NOT NULL,
  `slajd_id` bigint DEFAULT NULL,
  `redni_broj` int NOT NULL,
  `snimak` mediumtext NOT NULL,
  `otvoreno` datetime(6) NOT NULL,
  `zatvoreno` datetime(6) DEFAULT NULL,
  `rok` datetime(6) DEFAULT NULL,
  `preostalo_ms` bigint DEFAULT NULL,
  `trajanje_ms` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pitanje_runde_izvodjenje` (`izvodjenje_id`, `slajd_id`),
  CONSTRAINT `fk_pitanje_runde_izvodjenje` FOREIGN KEY (`izvodjenje_id`) REFERENCES `izvodjenja` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_pitanje_runde_slajd` FOREIGN KEY (`slajd_id`) REFERENCES `slajdovi` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `odgovori` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `runda_id` bigint NOT NULL,
  `ucesnik_id` bigint NOT NULL,
  `opcije` varchar(100) DEFAULT NULL,
  `broj` double DEFAULT NULL,
  `tekst` varchar(200) DEFAULT NULL,
  `skala` int DEFAULT NULL,
  `tacno` bit(1) DEFAULT NULL,
  `poeni` int NOT NULL,
  `vreme_ms` bigint NOT NULL,
  `sakriven` bit(1) NOT NULL,
  `kreirano` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_odgovori_runda_ucesnik` (`runda_id`, `ucesnik_id`),
  KEY `idx_odgovori_ucesnik` (`ucesnik_id`),
  CONSTRAINT `fk_odgovori_runda` FOREIGN KEY (`runda_id`) REFERENCES `pitanje_runde` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_odgovori_ucesnik` FOREIGN KEY (`ucesnik_id`) REFERENCES `ucesnici` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
