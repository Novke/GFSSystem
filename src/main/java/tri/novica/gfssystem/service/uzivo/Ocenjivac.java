package tri.novica.gfssystem.service.uzivo;

import tri.novica.gfssystem.exceptions.SystemException;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;

/** Provera oblika, tačnost i poeni odgovora. Čiste funkcije, bez baze. */
public final class Ocenjivac {
    private static final double EPS = 1e-9;
    private static final int MAX_TEKST = 200;
    private static final int OSNOVA_POENA = 1000;

    private Ocenjivac() {}

    /**
     * {@code true}/{@code false} kad pitanje ima tačan odgovor, {@code null} kad nema (anketa, skala, procena,
     * kratak tekst bez prihvatljivih). Neispravan oblik odgovora baca {@link SystemException} 400.
     */
    public static Boolean tacno(PitanjeSnimak p, OdgovorVrednost v) {
        Double udeo = udeo(p, v);
        return udeo == null ? null : udeo >= 1.0 - EPS;
    }

    /**
     * Udeo tačnosti odgovora od 0 do 1; {@code null} kad pitanje nema tačan odgovor. Samo {@code VISE_TACNIH} daje
     * razlomak: {@code max(0, (tačni izabrani - pogrešni izabrani) / broj tačnih)}, ostali tipovi 1 ili 0.
     * Neispravan oblik odgovora baca {@link SystemException} 400.
     */
    public static Double udeo(PitanjeSnimak p, OdgovorVrednost v) {
        proveri(p, v);
        if (!p.imaTacanOdgovor()) return null;
        if (p.tip() == TipPitanja.VISE_TACNIH) return udeoIzbora(p, v.opcije());
        Boolean t = tacnoBinarno(p, v);
        return t == null ? null : (t ? 1.0 : 0.0);
    }

    /** Udeo izbora za {@code VISE_TACNIH} bez provere oblika; {@code null} kad nema tačnih opcija. */
    public static Double udeoIzbora(PitanjeSnimak p, Collection<Long> izabrane) {
        Set<Long> tacne = p.opcije().stream().filter(OpcijaSnimak::tacna).map(OpcijaSnimak::id)
                .collect(Collectors.toSet());
        if (tacne.isEmpty()) return null;
        Set<Long> izbor = new HashSet<>(izabrane);
        long pogodjene = izbor.stream().filter(tacne::contains).count();
        long pogresne = izbor.size() - pogodjene;
        return Math.max(0.0, (double) (pogodjene - pogresne) / tacne.size());
    }

    private static Boolean tacnoBinarno(PitanjeSnimak p, OdgovorVrednost v) {
        return switch (p.tip()) {
            case JEDAN_TACAN, TACNO_NETACNO -> p.opcije().stream()
                    .filter(o -> o.id().equals(v.opcije().get(0)))
                    .findFirst().orElseThrow().tacna();
            case VISE_TACNIH -> throw new IllegalStateException("VISE_TACNIH ide kroz udeo");
            case KRATAK_TEKST -> {
                String kljuc = Normalizacija.tekst(v.tekst());
                yield p.prihvatljiviOdgovori().stream().anyMatch(a -> Normalizacija.tekst(a).equals(kljuc));
            }
            case BROJ -> uOdstupanju(p, v.broj());
            case ANKETA, SKALA -> null;
        };
    }

    /** Baca {@link SystemException} 400 ako odgovor nema oblik koji pitanje traži. */
    public static void proveri(PitanjeSnimak p, OdgovorVrednost v) {
        switch (p.tip()) {
            case JEDAN_TACAN, TACNO_NETACNO, ANKETA -> {
                List<Long> ids = v == null || v.opcije() == null ? List.of() : v.opcije();
                if (ids.size() != 1) throw greska("Izaberi tačno jedan odgovor.");
                proveriOpcije(p, ids);
            }
            case VISE_TACNIH -> {
                List<Long> ids = v == null || v.opcije() == null ? List.of() : v.opcije();
                if (ids.isEmpty()) throw greska("Izaberi bar jedan odgovor.");
                proveriOpcije(p, ids);
            }
            case KRATAK_TEKST -> {
                String t = v == null || v.tekst() == null ? "" : v.tekst().trim();
                if (t.isEmpty() || t.length() > MAX_TEKST) throw greska("Odgovor mora imati od 1 do 200 znakova.");
            }
            case BROJ -> {
                Double b = v == null ? null : v.broj();
                if (b == null || b.isNaN() || b.isInfinite()) throw greska("Unesi broj.");
            }
            case SKALA -> {
                Integer s = v == null ? null : v.skala();
                if (s == null || s < 1 || s > 5) throw greska("Izaberi vrednost od 1 do 5.");
            }
        }
    }

    /**
     * Da li je broj u dozvoljenom odstupanju od tačne vrednosti (sa tolerancijom {@code 1e-9} zbog plutajućeg zareza);
     * {@code null} kad pitanje nema tačnu vrednost.
     */
    public static Boolean uOdstupanju(PitanjeSnimak p, double broj) {
        if (p.brojTacno() == null) return null;
        double odstupanje = p.brojOdstupanje() == null ? 0 : Math.max(p.brojOdstupanje(), 0);
        double dozvoljeno = p.odstupanjeTip() == OdstupanjeTip.PROCENAT
                ? Math.abs(p.brojTacno()) * odstupanje / 100.0
                : odstupanje;
        return Math.abs(broj - p.brojTacno()) <= dozvoljeno + EPS;
    }

    /**
     * Poeni za odgovor: samo u takmičenju, srazmerno udelu tačnosti; osnovni 1000 bez tajmera, inače od 1000 (odmah)
     * do 500 (na isteku) linearno po brzini, pa {@code round(osnovni * udeo)}. Udeo {@code null} ili 0 daje 0.
     */
    public static int poeni(Double udeo, boolean takmicenje, Long trajanjeMs, long vremeMs) {
        if (!takmicenje || udeo == null || udeo <= 0) return 0;
        double osnovni = OSNOVA_POENA;
        if (trajanjeMs != null && trajanjeMs > 0) {
            double protekli = Math.min(Math.max((double) vremeMs, 0.0) / trajanjeMs, 1.0);
            osnovni = OSNOVA_POENA * (1 - protekli / 2);
        }
        return (int) Math.round(osnovni * udeo);
    }

    private static void proveriOpcije(PitanjeSnimak p, List<Long> ids) {
        Set<Long> poznate = p.opcije() == null ? Set.of()
                : p.opcije().stream().map(OpcijaSnimak::id).collect(Collectors.toSet());
        for (Long id : ids) {
            if (id == null || !poznate.contains(id)) throw greska("Nepoznat odgovor.");
        }
    }

    private static SystemException greska(String poruka) {
        return new SystemException(poruka, 400);
    }
}
