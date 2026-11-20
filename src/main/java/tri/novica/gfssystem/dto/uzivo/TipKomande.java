package tri.novica.gfssystem.dto.uzivo;

/**
 * Nastavnička komanda izvođenju ({@code POST /izvodjenja/{id}/komande}). {@code RESENJE} je prekidač rešenja ZADATAK
 * runde na projektoru; {@code BROJ_VARIJANTI} menja broj različitih zadataka, a {@code NIVO} nivo varijanti za sledeće
 * otvaranje ZADATAK slajda. {@code RANG_LISTA} ({@code L}) je prekidač ranga izvođenja na projektoru (u takmičenju poeni,
 * inače rang tačnosti), a {@code RANG_PREDAVANJA} ({@code Shift+L}) ranga tačnosti celog predavanja; {@code RANG_BROJ}
 * bira koliko prvih redova ranga tačnosti ide na projektor ({@code vrednost} 0 ili {@code null} = svi, 1..30).
 * {@code FAJLOVI} ({@code K}) pali ili gasi traku sa linkom fajlova na trenutnom slajdu ili prijavi, do sledeće promene
 * slajda (409 kad uz prezentaciju nema fajlova ili važećeg koda).
 */
public enum TipKomande {
    SLEDECI, PRETHODNI, IDI_NA, OTVORI_ZATVORI, REZULTATI, TACAN, RANG_LISTA, PONOVI, TAJMER, TAJMER_PLUS,
    TAJMER_MINUS, TELEFON_PRIKAZ, DETALJI, EKRAN_CRN, EKRAN_BEO, QR, ZAVRSI, RESENJE, BROJ_VARIJANTI, NIVO, RANG_PREDAVANJA, RANG_BROJ,
    FAJLOVI
}
