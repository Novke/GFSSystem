package tri.novica.gfssystem.utility;

/**
 * Predlog ocene po ukupnom broju poena: ispod 51 nije položio, [51, 61) je 6, [61, 71) 7, [71, 81) 8, [81, 91) 9,
 * od 91 naviše 10. Jedino mesto sa pragovima ({@code RezultatiStudentaInfo}, kartice studenta po predmetu).
 */
public final class OcenaPragovi {

    /** Najmanji broj poena za ocene 6, 7, 8, 9 i 10. */
    private static final double[] PRAGOVI = {51, 61, 71, 81, 91};
    private static final int PRVA_PROLAZNA = 6;

    private OcenaPragovi() {
    }

    /** Predlog ocene; null = nije položio. {@code NaN} daje 10, kao stari izraz u {@code izracunajUkupno()}. */
    public static Integer ocena(double ukupno) {
        for (int i = 0; i < PRAGOVI.length; i++) {
            if (ukupno < PRAGOVI[i]) return i == 0 ? null : PRVA_PROLAZNA + i - 1;
        }
        return PRVA_PROLAZNA + PRAGOVI.length - 1;
    }

    /** Koliko poena fali do sledeće ocene (do 6 ako nije položio); null kad je ocena već 10. */
    public static Double doSledece(double ukupno) {
        for (double prag : PRAGOVI) {
            if (ukupno < prag) return prag - ukupno;
        }
        return null;
    }
}
