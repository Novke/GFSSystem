package tri.novica.gfssystem.service.uzivo;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.entity.uzivo.Ucesnik;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.uzivo.UcesnikRepository;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Imena učesnika: provera (1-40 znakova posle normalizacije) i jedinstvenost u izvođenju. Isto ime (bez obzira na
 * velika slova) među neizbačenim učesnicima dobija sufiks " 2", " 3", ...; osnova se skraćuje da sufiks stane u 40.
 * Pozivalac drži zaključan red izvođenja, pa dve istovremene prijave istog imena ne dobijaju isto ime.
 */
@Component
public class ImenaUcesnika {

    public static final String NEISPRAVNO_IME = "Ime mora imati od 1 do 40 znakova.";
    static final int MAX = 40;

    private final UcesnikRepository ucesnikRepository;

    public ImenaUcesnika(UcesnikRepository ucesnikRepository) {
        this.ucesnikRepository = ucesnikRepository;
    }

    /**
     * Normalizovano ime ({@link Normalizacija#ime}); 400 kad nije od 1 do 40 znakova (kodnih tačaka) ili nema nijedan
     * vidljiv znak (npr. samo znaci nulte širine ili prazni znaci).
     */
    public String validiraj(String sirovo) {
        String ime = Normalizacija.ime(sirovo);
        int duzina = ime.codePointCount(0, ime.length());
        if (duzina < 1 || duzina > MAX || !Normalizacija.imaVidljivZnak(ime)) {
            throw new SystemException(NEISPRAVNO_IME, HttpStatus.BAD_REQUEST);
        }
        return ime;
    }

    /**
     * {@code ime} ako ga nema među neizbačenim učesnicima izvođenja (osim {@code izuzetId}, npr. onog koji se
     * preimenuje), inače prvo slobodno {@code ime + " n"} za n = 2, 3, ...
     */
    public String jedinstvenoIme(Long izvodjenjeId, String ime, Long izuzetId) {
        Set<String> zauzeta = new HashSet<>();
        for (Ucesnik u : ucesnikRepository.findAllByIzvodjenjeIdOrderByKreiranoAsc(izvodjenjeId)) {
            if (!u.isIzbacen() && !Objects.equals(u.getId(), izuzetId)) {
                zauzeta.add(kljuc(u.getIme()));
            }
        }
        if (!zauzeta.contains(kljuc(ime))) {
            return ime;
        }
        for (int n = 2; ; n++) {
            String sufiks = " " + n;
            String kandidat = skrati(ime, MAX - sufiks.length()) + sufiks;
            if (!zauzeta.contains(kljuc(kandidat))) {
                return kandidat;
            }
        }
    }

    private static String skrati(String s, int max) {
        if (s.codePointCount(0, s.length()) <= max) return s;
        return s.substring(0, s.offsetByCodePoints(0, max)).stripTrailing();
    }

    private static String kljuc(String ime) {
        return ime == null ? "" : ime.toLowerCase(Locale.ROOT);
    }
}
