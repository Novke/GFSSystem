-- V3: indeksi za filtere i sort lista (redizajn UI-ja): opseg datuma / školska godina i sort po datumu za
-- predavanja, domaće i testove, pretraga studenata po indeksu i godini upisa.
-- U gf i gf_staging ovih indeksa nema (proveren mysqldump --no-data), pa nije potrebna idempotentnost kao u V2.
-- Stara slika (ddl-auto=update) ih ne dira, pa rollback na nju radi i posle ove migracije.
CREATE INDEX idx_predavanja_datum ON predavanja (datum);
CREATE INDEX idx_domaci_datum ON domaci (datum);
CREATE INDEX idx_testovi_datum ON testovi (datum);
CREATE INDEX idx_studenti_indeks_godina ON studenti (indeks, godina);
