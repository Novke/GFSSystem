package tri.novica.gfssystem.service.uzivo;

import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.PoljeOpcija;
import tri.novica.gfssystem.dto.banka.TipPolja;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.banka.OcenaPolja;

import java.util.*;

/**
 * Provera i ocena odgovora na Zadatak slajd po šemi polja izvučene varijante (snimak runde, ne trenutna banka). Čiste
 * funkcije, bez baze. Neispravan odgovor je {@link SystemException} 400 sa porukom za učesnika; ocena polja je
 * {@link OcenaPolja#tacno}, a zadatak je tačan kad su tačna sva polja.
 */
public final class OcenaZadatka {

    static final String PAPIR = "Ovaj zadatak se radi na papiru.";
    static final String NEPOZNATO_POLJE = "Nepoznato polje odgovora.";
    static final String POPUNI = "Popuni sva polja.";
    static final int MAX_TEKST = 200;

    /** Ishod: odgovor po polju (redom šeme) i da li je ceo zadatak tačan. */
    public record Ishod(Map<String, PoljeOdgovora> polja, boolean tacno) {
    }

    private OcenaZadatka() {}

    /**
     * Proverava {@code dato} ({@code kljuc -> vrednost} iz STOMP poruke: string za BROJ i TEKST, lista id-jeva za
     * JEDAN_TACAN i VISE_TACNIH) i ocenjuje ga po varijanti {@code v}. Redom: varijanta bez polja (rad na papiru), ključ
     * van šeme, pa polja po redu šeme (prazno, pa oblik po tipu). Baca {@link SystemException} 400 na prvu grešku.
     */
    public static Ishod oceni(VarijantaSnimak v, Map<String, Object> dato) {
        if (v == null || !v.imaPolja()) {
            throw greska(PAPIR);
        }
        Map<String, Object> d = dato == null ? Map.of() : dato;
        Set<String> kljucevi = new HashSet<>();
        v.polja().forEach(p -> kljucevi.add(p.kljuc()));
        for (String k : d.keySet()) {
            if (!kljucevi.contains(k)) {
                throw greska(NEPOZNATO_POLJE);
            }
        }
        Map<String, OdgovorPolja> odgovori = v.odgovori() == null ? Map.of() : v.odgovori();
        Map<String, PoljeOdgovora> polja = new LinkedHashMap<>();
        boolean sve = true;
        for (Polje p : v.polja()) {
            Object vrednost = proveri(p, d.get(p.kljuc()));
            Object zaOcenu = vrednost instanceof List<?> l && p.tip() == TipPolja.JEDAN_TACAN
                    ? l.getFirst() : vrednost;
            boolean tacno = Boolean.TRUE.equals(OcenaPolja.tacno(p, odgovori.get(p.kljuc()), zaOcenu));
            polja.put(p.kljuc(), new PoljeOdgovora(vrednost, tacno));
            sve &= tacno;
        }
        return new Ishod(Collections.unmodifiableMap(polja), sve);
    }

    /** Vrednost za čuvanje (string bez razmaka na krajevima, ili sortirana lista id-jeva), ili 400. */
    private static Object proveri(Polje p, Object dato) {
        if (dato == null || dato instanceof String s && s.isBlank()) {
            throw greska(POPUNI);
        }
        return switch (p.tip()) {
            case BROJ -> {
                Double broj = dato instanceof String s ? Normalizacija.broj(s) : null;
                if (broj == null || !Double.isFinite(broj)) {
                    throw greska(poruka(p, "unesi broj."));
                }
                yield ((String) dato).strip();
            }
            case TEKST -> {
                String t = dato instanceof String s ? s.strip() : null;
                if (t == null || t.isEmpty() || t.length() > MAX_TEKST) {
                    throw greska(poruka(p, "odgovor mora imati od 1 do " + MAX_TEKST + " znakova."));
                }
                yield t;
            }
            case JEDAN_TACAN -> {
                List<Long> ids = opcije(p, dato);
                if (ids == null || ids.size() != 1) {
                    throw greska(poruka(p, "izaberi jedan odgovor."));
                }
                yield ids;
            }
            case VISE_TACNIH -> {
                List<Long> ids = opcije(p, dato);
                if (ids == null || ids.isEmpty() || new HashSet<>(ids).size() != ids.size()) {
                    throw greska(poruka(p, "izaberi bar jedan odgovor."));
                }
                yield ids;
            }
        };
    }

    /** Sortirani id-jevi iz liste celih brojeva koji su opcije polja; drugi oblik ili nepoznat id je {@code null}. */
    private static List<Long> opcije(Polje p, Object dato) {
        if (!(dato instanceof List<?> lista)) {
            return null;
        }
        Set<Long> dozvoljene = new HashSet<>();
        if (p.opcije() != null) {
            p.opcije().stream().map(PoljeOpcija::id).filter(Objects::nonNull).forEach(dozvoljene::add);
        }
        List<Long> ids = new ArrayList<>(lista.size());
        for (Object o : lista) {
            Long id = ceo(o);
            if (id == null || !dozvoljene.contains(id)) {
                return null;
            }
            ids.add(id);
        }
        Collections.sort(ids);
        return ids;
    }

    private static Long ceo(Object o) {
        if (o instanceof Long l) return l;
        if (o instanceof Integer || o instanceof Short || o instanceof Byte) return ((Number) o).longValue();
        if (o instanceof Number n) {
            double x = n.doubleValue();
            return Double.isFinite(x) && x == Math.rint(x) && Math.abs(x) < 9.0e15 ? (long) x : null;
        }
        return null;
    }

    private static String poruka(Polje p, String tekst) {
        String oznaka = p.oznaka() == null || p.oznaka().isBlank() ? p.kljuc() : p.oznaka();
        return "Polje „" + oznaka + "“: " + tekst;
    }

    private static SystemException greska(String poruka) {
        return new SystemException(poruka, HttpStatus.BAD_REQUEST);
    }
}
