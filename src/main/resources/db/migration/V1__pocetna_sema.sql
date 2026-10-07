-- V1: početna šema = šema produkcione baze `gf` pre onboardinga (mysqldump --no-data, 2026-10-07).
-- Očišćeno: bez /*!...*/ SET linija, bez AUTO_INCREMENT=, bez DEFINER/SQL SECURITY; tabele poređane tako da
-- strani ključevi postoje pre upotrebe. Imena ključeva su ista kao u `gf` (Hibernate-ova), da stara slika sa
-- ddl-auto=update posle rollback-a ne pravi duplikate.
-- Na postojećoj bazi (gf, gf_staging) se NE izvršava: Flyway radi baseline na verziji 1 (baseline-on-migrate).
-- Izvršava se samo nad praznom bazom (CI gftest, nova lokalna baza). Ne menjati posle prvog izdanja:
-- svaka izmena šeme ide u novu migraciju (V3, V4, ...).

CREATE TABLE `grupe` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `godina_upisa` int DEFAULT NULL,
  `naziv` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `predmeti` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `naziv` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `studenti` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `broj_telefona` varchar(255) DEFAULT NULL,
  `godina` int NOT NULL,
  `ime` varchar(255) DEFAULT NULL,
  `indeks` varchar(255) DEFAULT NULL,
  `prezime` varchar(255) DEFAULT NULL,
  `grupa_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKa3092kra8w0elrtr1uhegt1ny` (`grupa_id`),
  CONSTRAINT `FKa3092kra8w0elrtr1uhegt1ny` FOREIGN KEY (`grupa_id`) REFERENCES `grupe` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `predavanja` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `datum` date DEFAULT NULL,
  `posecenost` int DEFAULT NULL,
  `rb` int DEFAULT NULL,
  `tema` varchar(255) DEFAULT NULL,
  `grupa_id` bigint DEFAULT NULL,
  `predmet_id` bigint NOT NULL,
  `zavrseno` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK9o97gpkel1w3emenqqpkbkrh2` (`rb`,`grupa_id`),
  UNIQUE KEY `uk_predavanje_rb_grupa` (`rb`,`grupa_id`),
  KEY `FKh2k73j46csr97ha5qiepfwe01` (`grupa_id`),
  KEY `FKgdfb22fstd4ggl38fmj0hst8w` (`predmet_id`),
  CONSTRAINT `FKgdfb22fstd4ggl38fmj0hst8w` FOREIGN KEY (`predmet_id`) REFERENCES `predmeti` (`id`),
  CONSTRAINT `FKh2k73j46csr97ha5qiepfwe01` FOREIGN KEY (`grupa_id`) REFERENCES `grupe` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `aktivnosti` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `napomene` varchar(255) DEFAULT NULL,
  `tip` enum('PRISUSTVO','SA_ZVEZDICOM','ZADATAK') DEFAULT NULL,
  `predavanje_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKm1n3c00y62aey7paq4rly59gt` (`predavanje_id`),
  KEY `FKckyhuuv946xqnn3jdigdb8ej5` (`student_id`),
  CONSTRAINT `FKckyhuuv946xqnn3jdigdb8ej5` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`),
  CONSTRAINT `FKm1n3c00y62aey7paq4rly59gt` FOREIGN KEY (`predavanje_id`) REFERENCES `predavanja` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `domaci` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `datum` date DEFAULT NULL,
  `text` varchar(3000) DEFAULT NULL,
  `predavanje_id` bigint DEFAULT NULL,
  `predmet_id` bigint NOT NULL,
  `grupa_id` bigint DEFAULT NULL,
  `pregledan` bit(1) DEFAULT NULL,
  `naslov` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqmoueeh5l3mb4kgrtbflurx5u` (`predavanje_id`),
  KEY `FKmbpre273swyu2vbrkd3uebj0d` (`predmet_id`),
  KEY `FKiip29r5m5cef92tuhyukotfym` (`grupa_id`),
  CONSTRAINT `FKiip29r5m5cef92tuhyukotfym` FOREIGN KEY (`grupa_id`) REFERENCES `grupe` (`id`),
  CONSTRAINT `FKmbpre273swyu2vbrkd3uebj0d` FOREIGN KEY (`predmet_id`) REFERENCES `predmeti` (`id`),
  CONSTRAINT `FKqmoueeh5l3mb4kgrtbflurx5u` FOREIGN KEY (`predavanje_id`) REFERENCES `predavanja` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `tipovi_testa` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `aktivan` bit(1) DEFAULT NULL,
  `naziv` varchar(255) DEFAULT NULL,
  `predmet_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKa0x9mql7gsq20gnna67vhlgid` (`predmet_id`),
  CONSTRAINT `FKa0x9mql7gsq20gnna67vhlgid` FOREIGN KEY (`predmet_id`) REFERENCES `predmeti` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `koeficijenti_ocenjivanja` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `domaci_flat` double DEFAULT NULL,
  `domaci_varijansa` double DEFAULT NULL,
  `koef_prisustvo` double DEFAULT NULL,
  `koef_zadatak` double DEFAULT NULL,
  `koef_zvezdica` double DEFAULT NULL,
  `koristi_max_rezultat` bit(1) DEFAULT NULL,
  `koristi_normalizaciju` bit(1) DEFAULT NULL,
  `max_aktivnost` double DEFAULT NULL,
  `max_domaci` double DEFAULT NULL,
  `prikazi_zbirno` bit(1) DEFAULT NULL,
  `predmet_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5w9qj018ja9r41v9k1gfd6iei` (`predmet_id`),
  CONSTRAINT `FK92iuo6hx8wwplvym1o4hgdwac` FOREIGN KEY (`predmet_id`) REFERENCES `predmeti` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `koeficijenti_tip_testa` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `koeficijent` double DEFAULT NULL,
  `max_poena` double DEFAULT NULL,
  `koeficijenti_id` bigint NOT NULL,
  `tip_testa_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKtghugn3gj0hnjaqrtlfvctral` (`koeficijenti_id`,`tip_testa_id`),
  KEY `FKcrvxi7vxf2okrv3q9ajs1yll6` (`tip_testa_id`),
  CONSTRAINT `FK32mw5nglmbvcrhttot9cjufyl` FOREIGN KEY (`koeficijenti_id`) REFERENCES `koeficijenti_ocenjivanja` (`id`),
  CONSTRAINT `FKcrvxi7vxf2okrv3q9ajs1yll6` FOREIGN KEY (`tip_testa_id`) REFERENCES `tipovi_testa` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `testovi` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `datum` date DEFAULT NULL,
  `grupe` varchar(255) DEFAULT NULL,
  `max_poena` int DEFAULT NULL,
  `pregledan` bit(1) DEFAULT NULL,
  `grupa_id` bigint NOT NULL,
  `predmet_id` bigint NOT NULL,
  `tip_testa_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKc8v6p9dn06tk3wf2vcgwxy9x` (`grupa_id`),
  KEY `FKerm57ikqy7n1q9ekrsflpj911` (`predmet_id`),
  KEY `FKmxnwmgwijh5fint323t2loy16` (`tip_testa_id`),
  CONSTRAINT `FKc8v6p9dn06tk3wf2vcgwxy9x` FOREIGN KEY (`grupa_id`) REFERENCES `grupe` (`id`),
  CONSTRAINT `FKerm57ikqy7n1q9ekrsflpj911` FOREIGN KEY (`predmet_id`) REFERENCES `predmeti` (`id`),
  CONSTRAINT `FKmxnwmgwijh5fint323t2loy16` FOREIGN KEY (`tip_testa_id`) REFERENCES `tipovi_testa` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `polaganja` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `grupa` tinyint DEFAULT NULL,
  `napomene` varchar(255) DEFAULT NULL,
  `ostvareni_poeni` double DEFAULT NULL,
  `polozio` bit(1) DEFAULT NULL,
  `prepisivao` bit(1) DEFAULT NULL,
  `student_id` bigint NOT NULL,
  `test_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKp9nku7h7oxd0e56j947yjwtoi` (`student_id`),
  KEY `FKcneefwkfipwue2y9ilqdv41ai` (`test_id`),
  CONSTRAINT `FKcneefwkfipwue2y9ilqdv41ai` FOREIGN KEY (`test_id`) REFERENCES `testovi` (`id`),
  CONSTRAINT `FKp9nku7h7oxd0e56j947yjwtoi` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`),
  CONSTRAINT `polaganja_chk_1` CHECK ((`grupa` between 0 and 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `uradjeni_domaci` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bodovi` int NOT NULL,
  `napomene` varchar(255) DEFAULT NULL,
  `prepisivanje` bit(1) NOT NULL,
  `domaci_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `oslobodjen` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKahtldg2qftab8l1pgj6cv23uk` (`domaci_id`),
  KEY `FKimgbwnq6mtcguuy8r1sse8iot` (`student_id`),
  CONSTRAINT `FKahtldg2qftab8l1pgj6cv23uk` FOREIGN KEY (`domaci_id`) REFERENCES `domaci` (`id`),
  CONSTRAINT `FKimgbwnq6mtcguuy8r1sse8iot` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- View za evidentiranje domaćih (entitet DomaciEvidentiranjeView). Telo je iz dump-a baze `gf`.
CREATE OR REPLACE VIEW `domacievidentiranjeview` AS select `s`.`id` AS `id`,`d`.`id` AS `domaci_id`,`s`.`ime` AS `ime`,`s`.`prezime` AS `prezime`,`s`.`indeks` AS `indeks`,`s`.`godina` AS `godina`,`a`.`tip` AS `tip`,`a`.`napomene` AS `predavanja_napomene`,`ud`.`id` AS `uradjen_domaci_id`,`ud`.`bodovi` AS `bodovi`,`ud`.`napomene` AS `uradjen_domaci_napomene`,`ud`.`prepisivanje` AS `prepisivanje`,`ud`.`oslobodjen` AS `oslobodjen` from ((((`domaci` `d` join `grupe` `g` on((`d`.`grupa_id` = `g`.`id`))) join `studenti` `s` on((`s`.`grupa_id` = `g`.`id`))) left join `aktivnosti` `a` on(((`a`.`student_id` = `s`.`id`) and (`a`.`predavanje_id` = `d`.`predavanje_id`)))) left join `uradjeni_domaci` `ud` on(((`ud`.`domaci_id` = `d`.`id`) and (`ud`.`student_id` = `s`.`id`))));
