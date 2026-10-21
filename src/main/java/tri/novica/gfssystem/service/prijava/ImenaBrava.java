package tri.novica.gfssystem.service.prijava;

import tri.novica.gfssystem.service.uzivo.TokenHash;

/**
 * Imena MySQL brava ({@code GET_LOCK}). Korisničke brave važe za ceo MySQL server, a {@code shared-mysql} drži i produkcionu
 * bazu i staging, pa bi se dva okruženja međusobno serijalizovala na istoj bravi. Zato svako ime dobija prefiks šeme
 * ({@code gf:gfs-mejl-nastavniku}). MySQL dozvoljava najviše {@value #MAX_DUZINA} znakova; najduže logičko ime
 * ({@code gfs-pomoc:} + 32 hex) ima 42, pa šema sme da zauzme {@value #MAX_SEMA}: duža se skraćuje na početak plus kratak
 * heš cele šeme (dve različite duge šeme ne dele ime).
 */
final class ImenaBrava {

    /** Najviše znakova u imenu brave koje MySQL prihvata. */
    static final int MAX_DUZINA = 64;
    /** Najviše znakova šeme u prefiksu: {@code MAX_DUZINA - 1 (":") - 42 (najduže logičko ime)} = 21, jedan rezerve. */
    static final int MAX_SEMA = 20;

    private ImenaBrava() {}

    /** {@code <šema>:<logičko ime>}; prazna šema daje samo logičko ime. */
    static String ime(String sema, String logicko) {
        String s = skrati(sema == null ? "" : sema);
        String ime = s.isEmpty() ? logicko : s + ":" + logicko;
        if (ime.length() > MAX_DUZINA) {
            throw new IllegalArgumentException("Ime brave duže od " + MAX_DUZINA + " znakova: " + ime.length());
        }
        return ime;
    }

    static String skrati(String sema) {
        if (sema.length() <= MAX_SEMA) {
            return sema;
        }
        return sema.substring(0, MAX_SEMA - 9) + "~" + TokenHash.od(sema).substring(0, 8);
    }
}
