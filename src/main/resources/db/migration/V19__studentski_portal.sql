-- V19: studentski portal, faze 2 i 3 (spec docs/superpowers/specs/2026-10-28-studentski-portal-design.md, odeljci 5, 6
-- i 12 u deploy repou). Privremen broj: V18 uzima najava odsustva; pri merge-u u staging dobija sledeći slobodan (pre
-- merge-a proveriti origin/staging). Aditivno: samo nove tabele. Starija slika ih ne mapira (ddl-auto=validate prolazi,
-- rollback bez rada na bazi). Idempotentno kao V2-V17 (CREATE TABLE IF NOT EXISTS, prvo punjenje "ako ne postoji").
--
-- otvorene_teme: tip (čvor banke PREDAVANJE) otvoren za vežbanje grupi studenata; izvor CAS (kraj izvođenja, listovi
--   izvučenih varijanti) ili RUCNO (Novica u banci). Zapis preživi "Ne čuvaj" (runde se brišu).
-- vezbanja: jedan red po izvučenom zadatku pri vežbanju (bez bodova); provera i rešenje idu preko id-ja reda.
-- prikazani_slajdovi: slajd koji je konzola prikazala izvođenju sa grupom; vidljiv studentu tek kad je to izvođenje
--   završeno (ili obrisano: izvodjenje_id postaje NULL).
-- objave_ocena + objavljene_ocene: Novica objavi predloge ocena za predmet i grupu; snimak po studentu (JSON) u trenutku
--   objave, ponovna objava ga zamenjuje, povlačenje briše objavu.
--
-- Prvo punjenje ide samo ovde, jednom (ne kao Java korak pri startu): restart nikad ne otvori ponovo tip koji je Novica
-- ručno zatvorio, niti otkrije slajd. Za prikazane slajdove uže od spec-a 6.1 (revizija 12.6): slajdovi do mesta gde je
-- izvođenje stalo (sve kad je stalo na kraju) plus slajdovi sa rundom u tom izvođenju.

CREATE TABLE IF NOT EXISTS `otvorene_teme` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `grupa_id` bigint NOT NULL,
  `grupa_zadataka_id` bigint NOT NULL,
  `prvi_put` datetime(6) NOT NULL,
  `izvor` varchar(20) NOT NULL,
  `izvodjenje_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_otvorene_teme_grupa_tema` (`grupa_id`, `grupa_zadataka_id`),
  KEY `idx_otvorene_teme_tema` (`grupa_zadataka_id`),
  CONSTRAINT `fk_otvorene_teme_grupa` FOREIGN KEY (`grupa_id`) REFERENCES `grupe` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_otvorene_teme_tema` FOREIGN KEY (`grupa_zadataka_id`) REFERENCES `grupe_zadataka` (`id`)
    ON DELETE CASCADE,
  CONSTRAINT `fk_otvorene_teme_izvodjenje` FOREIGN KEY (`izvodjenje_id`) REFERENCES `izvodjenja` (`id`)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `vezbanja` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `varijanta_id` bigint NOT NULL,
  `grupa_zadataka_id` bigint DEFAULT NULL,
  `izvuceno` datetime(6) NOT NULL,
  `prva_provera_tacno` bit(1) DEFAULT NULL,
  `broj_provera` int NOT NULL DEFAULT 0,
  `tacno` bit(1) DEFAULT NULL,
  `resenje_prikazano` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_vezbanja_student_tema` (`student_id`, `grupa_zadataka_id`, `izvuceno`),
  KEY `idx_vezbanja_tema` (`grupa_zadataka_id`, `izvuceno`),
  KEY `idx_vezbanja_varijanta` (`varijanta_id`),
  CONSTRAINT `fk_vezbanja_student` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_vezbanja_varijanta` FOREIGN KEY (`varijanta_id`) REFERENCES `varijante` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_vezbanja_tema` FOREIGN KEY (`grupa_zadataka_id`) REFERENCES `grupe_zadataka` (`id`)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `prikazani_slajdovi` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `grupa_id` bigint NOT NULL,
  `slajd_id` bigint NOT NULL,
  `izvodjenje_id` bigint DEFAULT NULL,
  `prvi_put` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prikazani_slajdovi_grupa_slajd` (`grupa_id`, `slajd_id`),
  KEY `idx_prikazani_slajdovi_slajd` (`slajd_id`),
  CONSTRAINT `fk_prikazani_slajdovi_grupa` FOREIGN KEY (`grupa_id`) REFERENCES `grupe` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_prikazani_slajdovi_slajd` FOREIGN KEY (`slajd_id`) REFERENCES `slajdovi` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_prikazani_slajdovi_izvodjenje` FOREIGN KEY (`izvodjenje_id`) REFERENCES `izvodjenja` (`id`)
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `objave_ocena` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `predmet_id` bigint NOT NULL,
  `grupa_id` bigint NOT NULL,
  `objavljeno` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_objave_ocena_predmet_grupa` (`predmet_id`, `grupa_id`),
  KEY `idx_objave_ocena_grupa` (`grupa_id`),
  CONSTRAINT `fk_objave_ocena_predmet` FOREIGN KEY (`predmet_id`) REFERENCES `predmeti` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_objave_ocena_grupa` FOREIGN KEY (`grupa_id`) REFERENCES `grupe` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `objavljene_ocene` (
  `objava_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `snimak` mediumtext NOT NULL,
  PRIMARY KEY (`objava_id`, `student_id`),
  KEY `idx_objavljene_ocene_student` (`student_id`),
  CONSTRAINT `fk_objavljene_ocene_objava` FOREIGN KEY (`objava_id`) REFERENCES `objave_ocena` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_objavljene_ocene_student` FOREIGN KEY (`student_id`) REFERENCES `studenti` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Prvo punjenje, otvorene teme: listovi izvučenih varijanti iz ZADATAK rundi završenih izvođenja sa grupom. Neispravan
-- JSON snimka se preskače (JSON_TABLE dobija '{}'); list koji više ne postoji ne ulazi (JOIN na grupe_zadataka).
INSERT INTO `otvorene_teme` (`grupa_id`, `grupa_zadataka_id`, `prvi_put`, `izvor`, `izvodjenje_id`)
SELECT x.grupa_id, x.tema_id, x.prvi_put, 'CAS', x.izvodjenje_id
FROM (
  SELECT i.grupa_id AS grupa_id, jt.tema_id AS tema_id, MIN(COALESCE(i.kraj, i.pocetak)) AS prvi_put,
         MIN(i.id) AS izvodjenje_id
  FROM `izvodjenja` i
  JOIN `pitanje_runde` r ON r.izvodjenje_id = i.id
  JOIN JSON_TABLE(IF(JSON_VALID(r.snimak), r.snimak, '{}'), '$.varijante[*]'
                  COLUMNS (tema_id bigint PATH '$.grupaId')) jt
  JOIN `grupe_zadataka` gz ON gz.id = jt.tema_id
  WHERE i.status = 'ZAVRSENO' AND i.grupa_id IS NOT NULL
    AND JSON_VALID(r.snimak) AND JSON_UNQUOTE(JSON_EXTRACT(r.snimak, '$.vrsta')) = 'ZADATAK'
  GROUP BY i.grupa_id, jt.tema_id
) x
WHERE NOT EXISTS (SELECT 1 FROM `otvorene_teme` o WHERE o.grupa_id = x.grupa_id AND o.grupa_zadataka_id = x.tema_id);

-- Prvo punjenje, prikazani slajdovi: za završeno izvođenje sa grupom, slajdovi njegove prezentacije do slajda na kome je
-- stalo (prikaz KRAJ = svi; PRIJAVA bez slajda = nijedan), plus slajdovi sa rundom u tom izvođenju.
INSERT INTO `prikazani_slajdovi` (`grupa_id`, `slajd_id`, `izvodjenje_id`, `prvi_put`)
SELECT x.grupa_id, x.slajd_id, x.izvodjenje_id, x.prvi_put
FROM (
  SELECT i.grupa_id AS grupa_id, s.id AS slajd_id, MIN(i.id) AS izvodjenje_id,
         MIN(COALESCE(i.kraj, i.pocetak)) AS prvi_put
  FROM `izvodjenja` i
  JOIN `slajdovi` s ON s.prezentacija_id = i.prezentacija_id
  LEFT JOIN `slajdovi` stao ON stao.id = i.trenutni_slajd_id AND stao.prezentacija_id = i.prezentacija_id
  WHERE i.status = 'ZAVRSENO' AND i.grupa_id IS NOT NULL
    AND (i.prikaz = 'KRAJ'
         OR (stao.id IS NOT NULL AND s.rb <= stao.rb)
         OR EXISTS (SELECT 1 FROM `pitanje_runde` r WHERE r.izvodjenje_id = i.id AND r.slajd_id = s.id))
  GROUP BY i.grupa_id, s.id
) x
WHERE NOT EXISTS (SELECT 1 FROM `prikazani_slajdovi` p WHERE p.grupa_id = x.grupa_id AND p.slajd_id = x.slajd_id);
