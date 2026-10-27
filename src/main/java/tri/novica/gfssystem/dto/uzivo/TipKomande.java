package tri.novica.gfssystem.dto.uzivo;

/**
 * Nastavnička komanda izvođenju ({@code POST /izvodjenja/{id}/komande}). {@code RESENJE} je prekidač rešenja ZADATAK
 * runde na projektoru; {@code BROJ_VARIJANTI} menja broj različitih zadataka, a {@code NIVO} nivo varijanti za sledeće
 * otvaranje ZADATAK slajda.
 */
public enum TipKomande {
    SLEDECI, PRETHODNI, IDI_NA, OTVORI_ZATVORI, REZULTATI, TACAN, RANG_LISTA, PONOVI, TAJMER, TAJMER_PLUS,
    TAJMER_MINUS, TELEFON_PRIKAZ, DETALJI, EKRAN_CRN, EKRAN_BEO, QR, ZAVRSI, RESENJE, BROJ_VARIJANTI, NIVO
}
