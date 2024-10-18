CREATE VIEW domacipredavanjastudentiview AS
SELECT s.id       AS id,
       d.id       AS domaci_id,
       s.ime      AS ime,
       s.prezime  AS prezime,
       s.indeks   AS indeks,
       s.godina   AS godina,
       a.tip      AS tip,
       a.napomene AS napomene
FROM domaci d
         JOIN grupe g ON d.grupa_id = g.id
         JOIN studenti s ON s.grupa_id = g.id
         LEFT JOIN aktivnosti a ON a.student_id = s.id
    AND a.predavanje_id = d.predavanje_id;


CREATE VIEW DomaciEvidentiranjeView AS
SELECT s.id            AS id,
       d.id            AS domaci_id,
       s.ime           AS ime,
       s.prezime       AS prezime,
       s.indeks        AS indeks,
       s.godina        AS godina,
       a.tip           AS tip,
       a.napomene      AS predavanja_napomene,
       ud.id           AS uradjen_domaci_id,
       ud.bodovi       AS bodovi,
       ud.napomene     AS uradjen_domaci_napomene,
       ud.prepisivanje AS prepisivanje
FROM domaci d
         JOIN grupe g ON d.grupa_id = g.id
         JOIN studenti s ON s.grupa_id = g.id
         LEFT JOIN aktivnosti a ON a.student_id = s.id AND a.predavanje_id = d.predavanje_id
         LEFT JOIN uradjeni_domaci ud ON ud.domaci_id = d.id AND ud.student_id = s.id