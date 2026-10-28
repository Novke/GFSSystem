package tri.novica.gfssystem.service.uzivo;

import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.PoljeOpcija;

import java.util.*;

/**
 * Šta je učesnik uneo u polja zadatka, kao tekst za nastavnički pregled izvođenja ({@code UcesnikZadatka.uneto}). Čiste
 * funkcije nad snimkom runde: BROJ kako je uneto (bez razmaka na krajevima) i jedinica polja, TEKST kako je uneto,
 * JEDAN_TACAN tekst izabrane opcije, VISE_TACNIH tekstovi izabranih opcija redom šeme, spojeni zarezom (tekst opcije sme
 * da ima {@code $formule$}). Opcija koje u snimku nema je {@code #id}. Redom šeme; polje bez odgovora se izostavlja.
 * Samo za nastavnika: telefoni i projektor ovo nikad ne dobijaju.
 */
public final class UnetoZadatka {

    private UnetoZadatka() {}

    public static Map<String, String> tekstovi(VarijantaSnimak v, Map<String, PoljeOdgovora> polja) {
        Map<String, String> uneto = new LinkedHashMap<>();
        if (v == null || v.polja() == null || polja == null) return uneto;
        for (Polje p : v.polja()) {
            PoljeOdgovora po = polja.get(p.kljuc());
            String t = po == null ? null : tekst(p, po.vrednost());
            if (t != null && !t.isEmpty()) uneto.put(p.kljuc(), t);
        }
        return uneto;
    }

    private static String tekst(Polje p, Object vrednost) {
        return switch (p.tip()) {
            case BROJ -> vrednost instanceof String s
                    ? s.strip() + (p.jedinica() == null || p.jedinica().isBlank() ? "" : " " + p.jedinica().strip()) : null;
            case TEKST -> vrednost instanceof String s ? s.strip() : null;
            case JEDAN_TACAN, VISE_TACNIH -> vrednost instanceof List<?> l ? opcije(p, l) : null;
        };
    }

    private static String opcije(Polje p, List<?> izabrane) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Object o : izabrane) {
            if (o instanceof Number n) ids.add(n.longValue());
        }
        List<String> tekstovi = new ArrayList<>();
        for (PoljeOpcija o : p.opcije() == null ? List.<PoljeOpcija>of() : p.opcije()) {
            if (o.id() != null && ids.remove(o.id())) tekstovi.add(o.tekst());
        }
        ids.forEach(id -> tekstovi.add("#" + id));
        return String.join(", ", tekstovi);
    }
}
