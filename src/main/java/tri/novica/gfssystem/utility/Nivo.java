package tri.novica.gfssystem.utility;

import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.exceptions.SystemException;

/**
 * Nivo varijante zadatka (spec nivo varijante): 1 = lakša, 2 = srednja, 3 = teža unutar svog tipa (težina tipa je
 * {@code GrupaZadataka.tezina}, druga skala). Front ga prikazuje zvezdicama, a poruke servera ih koriste isto
 * ({@link #oznaka}). Ista provera za varijantu, slajd Zadatak i komandu {@code NIVO}.
 */
public final class Nivo {

    public static final int MIN = 1;
    public static final int MAX = 3;
    /** Nivo nove varijante bez zadatog nivoa i postojećih varijanti posle V15. */
    public static final int PODRAZUMEVANI = 2;
    public static final String GRANICE = "Nivo mora biti od 1 do 3.";

    private Nivo() {
    }

    /** {@code null} prolazi (pozivalac odlučuje šta znači); van {@value #MIN}..{@value #MAX} je 400 {@value #GRANICE}. */
    public static Integer proveri(Integer nivo) {
        if (nivo != null && (nivo < MIN || nivo > MAX)) {
            throw new SystemException(GRANICE, HttpStatus.BAD_REQUEST);
        }
        return nivo;
    }

    /** Oznaka nivoa za poruke: {@code ★}, {@code ★★} ili {@code ★★★}. */
    public static String oznaka(int nivo) {
        return "★".repeat(nivo);
    }
}
