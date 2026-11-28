package tri.novica.gfssystem.entity.test;

/**
 * Stanje primerka u bazi: {@code DODELJEN} (student još nije ušao), {@code U_TOKU} (ušao; pre "Pokreni" čeka, posle radi),
 * {@code PREDAT} (sam, istekom ili nastavnik), {@code PONISTEN} (nastavnik poništio, bez polaganja).
 */
public enum StanjePrimerka {
    DODELJEN,
    U_TOKU,
    PREDAT,
    PONISTEN
}
