package tri.novica.gfssystem.dto.uzivo;

/** Nastavnička komanda izvođenju ({@code POST /izvodjenja/{id}/komande}). */
public enum TipKomande {
    SLEDECI, PRETHODNI, IDI_NA, OTVORI_ZATVORI, REZULTATI, TACAN, RANG_LISTA, PONOVI, TAJMER, TAJMER_PLUS,
    TAJMER_MINUS, TELEFON_PRIKAZ, DETALJI, EKRAN_CRN, EKRAN_BEO, QR, ZAVRSI
}
