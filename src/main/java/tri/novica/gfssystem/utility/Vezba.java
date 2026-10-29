package tri.novica.gfssystem.utility;

import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redni broj vežbe prezentacije ({@code prezentacije.vezba}, V19): 0-99, {@code null} = bez broja. Prezentacija koja
 * pokriva više vežbi nosi prvu ("Vežbe 3 i 4 – zadaci" = 3).
 */
public final class Vezba {

    public static final int MIN = 0;
    public static final int MAX = 99;
    public static final String GRANICE = "Broj vežbe mora biti od 0 do 99.";

    /** Isto pravilo kao popuna u {@code V19__vezba_prezentacije.sql}: naziv počinje sa "Vežbe N" ili "Vezbe N". */
    private static final Pattern NAZIV = Pattern.compile("^\\s*ve[zž]be\\s+(\\d{1,2})(?!\\d)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private Vezba() {
    }

    /** {@code null} prolazi (bez broja); van {@value #MIN}..{@value #MAX} je 400 {@value #GRANICE}. */
    public static Integer proveri(Integer vezba) {
        if (vezba != null && (vezba < MIN || vezba > MAX)) {
            throw new SystemException(GRANICE, HttpStatus.BAD_REQUEST);
        }
        return vezba;
    }

    /** Broj iz naziva "Vežbe N ..." (prvi broj), inače {@code null}. */
    public static Integer izNaziva(String naziv) {
        if (naziv == null) {
            return null;
        }
        Matcher m = NAZIV.matcher(naziv);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }
}
