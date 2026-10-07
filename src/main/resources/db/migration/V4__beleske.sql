-- V4: beleške nastavnika o studentu (redizajn UI-ja, S6). Nova tabela, ni u gf ni u gf_staging je nema,
-- pa nije potrebna idempotentnost kao u V2. Stara slika (ddl-auto=update) novu tabelu ne dira, pa rollback na nju radi.
CREATE TABLE `beleske` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `tekst` varchar(2000) NOT NULL,
  `kreirano` datetime(6) NOT NULL,
  `izmenjeno` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_beleske_student` (`student_id`),
  CONSTRAINT `fk_beleske_student` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
