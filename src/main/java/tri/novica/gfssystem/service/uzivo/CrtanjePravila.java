package tri.novica.gfssystem.service.uzivo;

import tri.novica.gfssystem.dto.uzivo.ElementCrteza;
import tri.novica.gfssystem.dto.uzivo.TipElementa;
import tri.novica.gfssystem.exceptions.SystemException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Granice crteža (spec crtanja 4.1, plan "Ugovor"): boja {@code #RRGGBB} (u velika slova), debljina konačna 1-40, koordinata
 * konačna u ±2000 (zaokružena na desetinu), OLOVKA 1-5000 tačaka, ostali tipovi tačno 4 broja, najviše 2000 elemenata i 1 MB
 * zapisa. Greška je 400 sa indeksom elementa, bez vrednosti iz ulaza.
 */
public final class CrtanjePravila {

    public static final int MAX_ELEMENATA = 2000;
    public static final int MAX_TACAKA_OLOVKE = 5000;
    public static final double MIN_DEBLJINA = 1;
    public static final double MAX_DEBLJINA = 40;
    public static final double MAX_KOORDINATA = 2000;
    public static final int MAX_BAJTOVA = 1024 * 1024;
    private static final Pattern BOJA = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private CrtanjePravila() {
    }

    /** Proveren i normalizovan niz (nov objekat); {@code null} niz je greška. */
    public static List<ElementCrteza> normalizuj(List<ElementCrteza> elementi) {
        if (elementi == null) {
            throw CrtanjeJson.greska("elementi nedostaju");
        }
        if (elementi.size() > MAX_ELEMENATA) {
            throw CrtanjeJson.greska("najviše " + MAX_ELEMENATA + " elemenata na strani");
        }
        List<ElementCrteza> rez = new ArrayList<>(elementi.size());
        for (int i = 0; i < elementi.size(); i++) {
            rez.add(element(elementi.get(i), i));
        }
        return rez;
    }

    /** Zapis ne sme preći 1 MB (kolona je mediumtext, a telo zahteva je ograničeno i u nginx-u). */
    public static void proveriVelicinu(String zapis) {
        if (zapis.getBytes(StandardCharsets.UTF_8).length > MAX_BAJTOVA) {
            throw new SystemException("Crtež je prevelik (najviše 1 MB po strani).", 400);
        }
    }

    private static ElementCrteza element(ElementCrteza e, int i) {
        String mesto = "elementi[" + i + "]";
        if (e == null || e.tip() == null) {
            throw CrtanjeJson.greska("tip nedostaje (" + mesto + ")");
        }
        if (e.boja() == null || !BOJA.matcher(e.boja()).matches()) {
            throw CrtanjeJson.greska("boja mora biti #RRGGBB (" + mesto + ")");
        }
        Double d = e.debljina();
        if (d == null || !Double.isFinite(d) || d < MIN_DEBLJINA || d > MAX_DEBLJINA) {
            throw CrtanjeJson.greska("debljina mora biti od 1 do 40 (" + mesto + ")");
        }
        List<Double> t = e.tacke();
        if (t == null) {
            throw CrtanjeJson.greska("tačke nedostaju (" + mesto + ")");
        }
        if (e.tip() == TipElementa.OLOVKA) {
            if (t.size() < 2 || t.size() % 2 != 0 || t.size() > 2 * MAX_TACAKA_OLOVKE) {
                throw CrtanjeJson.greska("olovka ima od 1 do " + MAX_TACAKA_OLOVKE + " tačaka (" + mesto + ")");
            }
        } else if (t.size() != 4) {
            throw CrtanjeJson.greska("oblik ima tačno 4 broja (" + mesto + ")");
        }
        List<Double> tacke = new ArrayList<>(t.size());
        for (Double v : t) {
            if (v == null || !Double.isFinite(v) || Math.abs(v) > MAX_KOORDINATA) {
                throw CrtanjeJson.greska("koordinata mora biti broj u ±2000 (" + mesto + ")");
            }
            tacke.add(Math.round(v * 10) / 10.0);
        }
        return new ElementCrteza(e.tip(), e.boja().toUpperCase(Locale.ROOT), d, List.copyOf(tacke));
    }
}
