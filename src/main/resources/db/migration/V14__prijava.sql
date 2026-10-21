-- V14: prijava (spec 2026-10-19-prijava-design, sekcija 9): sesije nastavnika, uređaji studenata (nalog bez lozinke),
-- jednokratni kodovi za prijavu, spoljni identiteti (Moodle LTI), obaveštenja za nastavnika, pokušaji (limiti koji
-- preživljavaju restart), ključevi aplikacije (HMAC za lažnu masku mejla) i LTI 1.3 tabele (registracije platformi,
-- ključ alata, OIDC stanja, povezivanje Moodle naloga sa studentom).
-- Privremeni broj: pri merge-u u staging dobija sledeći slobodan (spec 13.3).
-- Samo nove tabele (aditivno; stara slika ne zna za njih, ddl-auto=validate prolazi, rollback bez DB posla).
-- Idempotentno kao V2-V11: svaka tabela je CREATE TABLE IF NOT EXISTS, redosled poštuje strane ključeve
-- (lti_registracije pre spoljni_identiteti, lti_stanja i lti_povezivanja).
-- Enum kolone su varchar(30) (obavestenja.vrsta varchar(40)); entiteti: @Enumerated(STRING) + @JdbcTypeCode(VARCHAR).
-- Vremena su datetime(3). Tokeni iz kolačića su u bazi samo kao SHA-256 (hex, char(64), UNIQUE), nikad sami tokeni.
-- Brisanje studenta briše njegove uređaje, kodove, identitete i obaveštenja (CASCADE); brisanje LTI registracije
-- briše njena stanja i povezivanja, a identitetu ostavlja registracija_id = NULL (veza studenta ostaje).
-- izdavac/subjekat spoljnih identiteta i subjekat povezivanja su utf8mb4_bin: LTI sub je neprozirna vrednost koja
-- se poredi bajt po bajt (velika/mala slova i akcenti su različiti identiteti), UNIQUE ključ i upiti isto.
-- Ključevi po vremenu (istice/opozvana/opozvan/iskorisceno/kreirano) služe samo CiscenjePrijave: njeni DELETE-ovi ne skeniraju tabelu.

CREATE TABLE IF NOT EXISTS `nastavnicke_sesije` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token_hes` char(64) NOT NULL,
  `opis` varchar(120) DEFAULT NULL,
  `ip` varchar(64) DEFAULT NULL,
  `kreirano` datetime(3) NOT NULL,
  `poslednji_ulaz` datetime(3) NOT NULL,
  `istice` datetime(3) NOT NULL,
  `opozvana` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_nastavnicke_sesije_token_hes` (`token_hes`),
  KEY `idx_nastavnicke_sesije_istice` (`istice`),
  KEY `idx_nastavnicke_sesije_opozvana` (`opozvana`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `studentski_uredjaji` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `token_hes` char(64) NOT NULL,
  `opis` varchar(120) DEFAULT NULL,
  `nacin` varchar(30) NOT NULL,
  `prvi_ulaz` datetime(3) NOT NULL,
  `poslednji_ulaz` datetime(3) NOT NULL,
  `istice` datetime(3) NOT NULL,
  `opozvan` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_studentski_uredjaji_token_hes` (`token_hes`),
  KEY `idx_studentski_uredjaji_student` (`student_id`),
  KEY `idx_studentski_uredjaji_istice` (`istice`),
  KEY `idx_studentski_uredjaji_opozvan` (`opozvan`),
  CONSTRAINT `fk_studentski_uredjaji_student` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `kodovi_za_prijavu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `kod_hes` char(64) NOT NULL,
  `so` char(32) NOT NULL,
  `kreirano` datetime(3) NOT NULL,
  `istice` datetime(3) NOT NULL,
  `pogresni` int NOT NULL DEFAULT 0,
  `iskoriscen` datetime(3) DEFAULT NULL,
  `ponisten` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_kodovi_za_prijavu_student` (`student_id`, `kreirano`),
  KEY `idx_kodovi_za_prijavu_kreirano` (`kreirano`),
  CONSTRAINT `fk_kodovi_za_prijavu_student` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `lti_registracije` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `naziv` varchar(120) NOT NULL,
  `izdavac` varchar(255) NOT NULL,
  `client_id` varchar(255) NOT NULL,
  `deployment_id` varchar(255) NOT NULL,
  `auth_url` varchar(500) NOT NULL,
  `jwks_url` varchar(500) NOT NULL,
  `aktivna` bit(1) NOT NULL,
  `kreirano` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lti_registracije_platforma` (`izdavac`, `client_id`, `deployment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `spoljni_identiteti` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `vrsta` varchar(30) NOT NULL,
  `izdavac` varchar(255) COLLATE utf8mb4_bin NOT NULL,
  `subjekat` varchar(255) COLLATE utf8mb4_bin NOT NULL,
  `registracija_id` bigint DEFAULT NULL,
  `kreirano` datetime(3) NOT NULL,
  `poslednji_ulaz` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_spoljni_identiteti_vrsta_izdavac_subjekat` (`vrsta`, `izdavac`, `subjekat`),
  KEY `idx_spoljni_identiteti_student` (`student_id`),
  CONSTRAINT `fk_spoljni_identiteti_student` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_spoljni_identiteti_registracija` FOREIGN KEY (`registracija_id`) REFERENCES `lti_registracije` (`id`)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `obavestenja` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `vrsta` varchar(40) NOT NULL,
  `student_id` bigint DEFAULT NULL,
  `podaci` text NOT NULL,
  `kreirano` datetime(3) NOT NULL,
  `procitano` datetime(3) DEFAULT NULL,
  `reseno` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_obavestenja_reseno_kreirano` (`reseno`, `kreirano`),
  KEY `idx_obavestenja_student` (`student_id`),
  CONSTRAINT `fk_obavestenja_student` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `pokusaji` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `vrsta` varchar(30) NOT NULL,
  `kljuc` varchar(128) NOT NULL,
  `vreme` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pokusaji_vrsta_kljuc_vreme` (`vrsta`, `kljuc`, `vreme`),
  KEY `idx_pokusaji_vreme` (`vreme`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `kljucevi_aplikacije` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `naziv` varchar(40) NOT NULL,
  `vrednost` varchar(100) NOT NULL,
  `kreirano` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kljucevi_aplikacije_naziv` (`naziv`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `lti_kljucevi` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `kid` varchar(64) NOT NULL,
  `privatni` text NOT NULL,
  `javni` text NOT NULL,
  `kreirano` datetime(3) NOT NULL,
  `aktivan` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lti_kljucevi_kid` (`kid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `lti_stanja` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `stanje_hes` char(64) NOT NULL,
  `nonce` varchar(64) NOT NULL,
  `kolacic_hes` char(64) NOT NULL,
  `registracija_id` bigint NOT NULL,
  `kreirano` datetime(3) NOT NULL,
  `istice` datetime(3) NOT NULL,
  `iskorisceno` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lti_stanja_stanje_hes` (`stanje_hes`),
  KEY `idx_lti_stanja_registracija` (`registracija_id`),
  KEY `idx_lti_stanja_istice` (`istice`),
  CONSTRAINT `fk_lti_stanja_registracija` FOREIGN KEY (`registracija_id`) REFERENCES `lti_registracije` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `lti_povezivanja` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token_hes` char(64) NOT NULL,
  `registracija_id` bigint NOT NULL,
  `subjekat` varchar(255) COLLATE utf8mb4_bin NOT NULL,
  `ime` varchar(200) DEFAULT NULL,
  `istice` datetime(3) NOT NULL,
  `iskorisceno` datetime(3) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lti_povezivanja_token_hes` (`token_hes`),
  KEY `idx_lti_povezivanja_registracija` (`registracija_id`),
  KEY `idx_lti_povezivanja_istice` (`istice`),
  KEY `idx_lti_povezivanja_iskorisceno` (`iskorisceno`),
  CONSTRAINT `fk_lti_povezivanja_registracija` FOREIGN KEY (`registracija_id`) REFERENCES `lti_registracije` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
