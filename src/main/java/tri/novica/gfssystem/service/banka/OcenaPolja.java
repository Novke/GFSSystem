package tri.novica.gfssystem.service.banka;

import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;
import tri.novica.gfssystem.service.uzivo.Normalizacija;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Ocena jednog polja šeme odgovora (spec banke 2.2). Čiste funkcije, bez baze. Zadatak je tačan kad su tačna sva njegova
 * polja; to spaja pozivalac.
 */
public final class OcenaPolja {
    private static final double EPS = 1e-9;
    /** Najveći ceo broj koji double predstavlja tačno; veći "ids" nisu id-jevi opcija. */
    private static final double MAX_CEO = 9_007_199_254_740_992.0;
    private static final Pattern RAZMACI = Pattern.compile("[\\s\\p{Z}]+");

    private OcenaPolja() {}

    /**
     * Da li je {@code dato} tačan odgovor na polje. {@code dato} je {@code String} za BROJ (parsira se kao
     * {@link Normalizacija#broj}, zarez ili tačka) i TEKST, {@code Long} (ili drugi ceo {@code Number}) za JEDAN_TACAN i
     * lista takvih brojeva za VISE_TACNIH (skup). Ništa ili neočekivan oblik je {@code false}; nikad {@code null}, nikad
     * ne baca.
     */
    public static Boolean tacno(Polje polje, OdgovorPolja ocekivano, Object dato) {
        if (polje == null || polje.tip() == null || ocekivano == null || dato == null) return false;
        return switch (polje.tip()) {
            case BROJ -> brojTacan(polje, ocekivano, dato);
            case TEKST -> tekstTacan(polje, ocekivano, dato);
            case JEDAN_TACAN -> {
                Long id = ceo(dato);
                yield id != null && id.equals(ocekivano.tacna());
            }
            case VISE_TACNIH -> viseTacnih(ocekivano, dato);
        };
    }

    /**
     * Ključ za poređenje i grupisanje tekstualnih odgovora: {@link Normalizacija#tekst} (mala slova, ćirilica u latinicu,
     * bez kvačica, razmaci sažeti) i, kad polje zanemaruje razmake (podrazumevano), svi razmaci uklonjeni. {@code null}
     * je prazan ključ.
     */
    public static String kljucTeksta(Polje polje, String s) {
        return kljuc(polje, s, false);
    }

    private static boolean brojTacan(Polje polje, OdgovorPolja ocekivano, Object dato) {
        if (!(dato instanceof String s) || ocekivano.broj() == null || !Double.isFinite(ocekivano.broj())) return false;
        Double broj = Normalizacija.broj(s);
        if (broj == null || !Double.isFinite(broj)) return false;
        double odstupanje = polje.odstupanje() == null || !Double.isFinite(polje.odstupanje())
                ? 0 : Math.max(polje.odstupanje(), 0);
        double dozvoljeno = polje.odstupanjeTip() == OdstupanjeTip.PROCENAT
                ? Math.abs(ocekivano.broj()) * odstupanje / 100.0
                : odstupanje;
        return Math.abs(broj - ocekivano.broj()) <= dozvoljeno + EPS;
    }

    private static boolean tekstTacan(Polje polje, OdgovorPolja ocekivano, Object dato) {
        if (!(dato instanceof String s) || ocekivano.prihvatljivi() == null) return false;
        boolean razlikujVelicinu = Boolean.FALSE.equals(polje.zanemariVelicinu());
        String kljuc = kljuc(polje, s, razlikujVelicinu);
        if (kljuc.isEmpty()) return false;
        return ocekivano.prihvatljivi().stream()
                .anyMatch(a -> a != null && kljuc.equals(kljuc(polje, a, razlikujVelicinu)));
    }

    private static boolean viseTacnih(OdgovorPolja ocekivano, Object dato) {
        if (!(dato instanceof List<?> lista) || ocekivano.tacne() == null || ocekivano.tacne().isEmpty()) return false;
        Set<Long> izabrane = new HashSet<>();
        for (Object o : lista) {
            Long id = ceo(o);
            if (id == null) return false;
            izabrane.add(id);
        }
        return izabrane.equals(new HashSet<>(ocekivano.tacne()));
    }

    /** Ceo broj iz {@code Number}-a (npr. {@code 2.0}); sve drugo i razlomci su {@code null}. */
    private static Long ceo(Object o) {
        if (o instanceof Long l) return l;
        if (o instanceof Number n) {
            double d = n.doubleValue();
            return d == Math.rint(d) && Math.abs(d) <= MAX_CEO ? n.longValue() : null;
        }
        return null;
    }

    /**
     * Normalizovani oblik teksta. Sa {@code sacuvajVelicinu} velika slova ostaju velika posle normalizacije slovo po
     * slovo ({@code Đ} i {@code Dj} su isto), inače je sve malim slovima.
     */
    private static String kljuc(Polje polje, String s, boolean sacuvajVelicinu) {
        if (s == null) return "";
        boolean bezRazmaka = polje == null || !Boolean.FALSE.equals(polje.zanemariRazmake());
        String sazeto = RAZMACI.matcher(s).replaceAll(bezRazmaka ? "" : " ").strip();
        if (!sacuvajVelicinu) return Normalizacija.tekst(sazeto);
        StringBuilder b = new StringBuilder(sazeto.length());
        sazeto.codePoints().forEach(cp -> {
            if (cp == ' ') {
                b.append(' ');
                return;
            }
            String n = Normalizacija.tekst(Character.toString(Character.toLowerCase(cp)));
            if (Character.isUpperCase(cp) && !n.isEmpty()) {
                n = Character.toUpperCase(n.charAt(0)) + n.substring(1);
            }
            b.append(n);
        });
        return b.toString();
    }
}
