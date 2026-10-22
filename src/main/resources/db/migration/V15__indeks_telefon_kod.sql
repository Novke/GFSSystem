-- V15: kanonski indeks, telefoni u E.164 i vrsta koda za prijavu (grana feature/prijava-indeks-mejl).
-- Broj verzije je privremen do merge-a u staging (sledeći slobodan na origin/staging; pre merge-a proveriti).
-- Sve je idempotentno (ponovno izvršavanje ne menja ništa) i aditivno za prethodnu sliku: ona i dalje čita indeks i
-- telefon kao tekst, a novu kolonu ne mapira (DEFAULT 'MEJL' pokriva njene upise).

-- 1) kodovi_za_prijavu.nacin: MEJL (poslat mejlom) | NASTAVNIK (kod za diktiranje). Postojeći redovi su MEJL.
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'kodovi_za_prijavu' AND COLUMN_NAME = 'nacin');
SET @sql := IF(@ima = 0, 'ALTER TABLE kodovi_za_prijavu ADD COLUMN nacin varchar(20) NOT NULL DEFAULT ''MEJL''', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 2) Indeks bez sufiksa godine: "GD99/2026" (i sa razmacima, "GD99 / 2026") uz studenti.godina = 2026 postaje "GD99".
--    Samo kad je sufiks tačno jednak godini upisa i kad skraćeni indeks ne postoji već za tu godinu (ni kod drugog reda
--    koji se ovde menja); sve ostalo ostaje kako jeste (aplikacija i onda poredi kanonski oblik u Javi).
--    Ponovo pokrenuto: nema više redova sa sufiksom jednakim godini, pa ništa.
UPDATE /*+ NO_MERGE(x) */ studenti s
JOIN (
    SELECT k.id, k.novi
    FROM (
        SELECT id, godina,
               SUBSTRING(REPLACE(indeks, ' ', ''), 1, CHAR_LENGTH(REPLACE(indeks, ' ', '')) - 5) AS novi
        FROM studenti
        WHERE REPLACE(indeks, ' ', '') REGEXP '^[^/]+/[0-9]{4}$'
          AND RIGHT(REPLACE(indeks, ' ', ''), 4) = CAST(godina AS CHAR)
    ) k
    WHERE NOT EXISTS (
        SELECT 1 FROM studenti d
        WHERE d.id <> k.id AND d.godina = k.godina
          AND (UPPER(REPLACE(d.indeks, ' ', '')) = UPPER(k.novi)
               OR UPPER(REPLACE(d.indeks, ' ', '')) = UPPER(CONCAT(k.novi, '/', d.godina))))
) x ON x.id = s.id
SET s.indeks = x.novi;

-- 3) Telefoni u E.164, samo nedvosmisleni oblici (posle brisanja razmaka, zagrada, tačaka, kosih crta i crtica):
--    "+..." ostaje sa plusom; "00..." -> "+..."; "0" + 7-10 cifara (lokalni broj u Srbiji) -> "+381" bez vodeće nule;
--    "381" + 8-10 cifara -> "+381...". Ostalo (npr. 9 cifara bez vodeće nule, slova) ostaje kako jeste.
UPDATE studenti
SET broj_telefona = CASE
    WHEN REGEXP_REPLACE(broj_telefona, '[[:space:]()./-]', '') REGEXP '^[+][1-9][0-9]{7,14}$'
        THEN REGEXP_REPLACE(broj_telefona, '[[:space:]()./-]', '')
    WHEN REGEXP_REPLACE(broj_telefona, '[[:space:]()./-]', '') REGEXP '^00[1-9][0-9]{7,14}$'
        THEN CONCAT('+', SUBSTRING(REGEXP_REPLACE(broj_telefona, '[[:space:]()./-]', ''), 3))
    WHEN REGEXP_REPLACE(broj_telefona, '[[:space:]()./-]', '') REGEXP '^0[1-9][0-9]{6,9}$'
        THEN CONCAT('+381', SUBSTRING(REGEXP_REPLACE(broj_telefona, '[[:space:]()./-]', ''), 2))
    WHEN REGEXP_REPLACE(broj_telefona, '[[:space:]()./-]', '') REGEXP '^381[0-9]{8,10}$'
        THEN CONCAT('+', REGEXP_REPLACE(broj_telefona, '[[:space:]()./-]', ''))
    ELSE broj_telefona
END
WHERE broj_telefona IS NOT NULL AND broj_telefona NOT REGEXP '^[+][1-9][0-9]{7,14}$';

UPDATE prijave
SET broj_telefona = CASE
    WHEN REGEXP_REPLACE(broj_telefona, '[[:space:]()./-]', '') REGEXP '^[+][1-9][0-9]{7,14}$'
        THEN REGEXP_REPLACE(broj_telefona, '[[:space:]()./-]', '')
    WHEN REGEXP_REPLACE(broj_telefona, '[[:space:]()./-]', '') REGEXP '^00[1-9][0-9]{7,14}$'
        THEN CONCAT('+', SUBSTRING(REGEXP_REPLACE(broj_telefona, '[[:space:]()./-]', ''), 3))
    WHEN REGEXP_REPLACE(broj_telefona, '[[:space:]()./-]', '') REGEXP '^0[1-9][0-9]{6,9}$'
        THEN CONCAT('+381', SUBSTRING(REGEXP_REPLACE(broj_telefona, '[[:space:]()./-]', ''), 2))
    WHEN REGEXP_REPLACE(broj_telefona, '[[:space:]()./-]', '') REGEXP '^381[0-9]{8,10}$'
        THEN CONCAT('+', REGEXP_REPLACE(broj_telefona, '[[:space:]()./-]', ''))
    ELSE broj_telefona
END
WHERE broj_telefona IS NOT NULL AND broj_telefona NOT REGEXP '^[+][1-9][0-9]{7,14}$';
