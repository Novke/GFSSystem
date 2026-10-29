package tri.novica.gfssystem.service.uzivo;

import tri.novica.gfssystem.entity.uzivo.Odgovor;

import java.math.BigDecimal;
import java.util.*;

/**
 * Kratak prikaz odgovora za konzolu (tab "Ovo pitanje", spec rang tačnosti 5): slova izabranih opcija po redu opcija u
 * snimku ("A, C"), broj, kratak tekst (najviše {@value #NAJVISE} znakova), vrednost skale; zadatak kao "Uneto" na
 * pregledu ({@link UnetoZadatka}), polja spojena sa " · ". Samo za nastavnika. Čiste funkcije.
 */
public final class KratakOdgovor {

    static final int NAJVISE = 40;

    private KratakOdgovor() {}

    public static String pitanje(PitanjeSnimak s, Odgovor o) {
        if (o == null) return null;
        return switch (s.tip()) {
            case JEDAN_TACAN, VISE_TACNIH, ANKETA, TACNO_NETACNO -> slova(s, o.getOpcije());
            case BROJ -> o.getBroj() == null ? null : broj(o.getBroj());
            case KRATAK_TEKST -> skrati(o.getTekst());
            case SKALA -> o.getSkala() == null ? null : String.valueOf(o.getSkala());
        };
    }

    public static String zadatak(VarijantaSnimak v, Map<String, PoljeOdgovora> polja) {
        if (v == null) return null;
        Map<String, String> uneto = UnetoZadatka.tekstovi(v, polja);
        return uneto.isEmpty() ? null : skrati(String.join(" · ", uneto.values()));
    }

    private static String slova(PitanjeSnimak s, String csv) {
        if (csv == null || csv.isBlank() || s.opcije() == null) return null;
        Set<Long> izabrane = new HashSet<>();
        for (String deo : csv.split(",")) {
            try {
                izabrane.add(Long.parseLong(deo.strip()));
            } catch (NumberFormatException e) {
                // neispravan id se preskače
            }
        }
        List<OpcijaSnimak> sortirane = s.opcije().stream().sorted(Comparator.comparingInt(OpcijaSnimak::rb)).toList();
        List<String> slova = new ArrayList<>();
        for (int i = 0; i < sortirane.size(); i++) {
            if (izabrane.contains(sortirane.get(i).id())) slova.add(String.valueOf((char) ('A' + i)));
        }
        return slova.isEmpty() ? null : String.join(", ", slova);
    }

    /** Broj bez nepotrebnih nula, decimalni zarez. */
    static String broj(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) return String.valueOf(d);
        return new BigDecimal(Double.toString(d)).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    static String skrati(String t) {
        if (t == null) return null;
        String s = t.strip();
        if (s.isEmpty()) return null;
        return s.codePointCount(0, s.length()) <= NAJVISE ? s
                : s.substring(0, s.offsetByCodePoints(0, NAJVISE - 1)) + "…";
    }
}
