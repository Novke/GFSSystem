package tri.novica.gfssystem.service.uzivo;

import tri.novica.gfssystem.exceptions.SystemException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;

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
        proveri(p, v);
        if (!p.imaTacanOdgovor()) return null;
        return switch (p.tip()) {
            case JEDAN_TACAN, TACNO_NETACNO -> p.opcije().stream()
                    .filter(o -> o.id().equals(v.opcije().get(0)))
                    .findFirst().orElseThrow().tacna();
            case VISE_TACNIH -> {
                Set<Long> tacne = p.opcije().stream().filter(OpcijaSnimak::tacna).map(OpcijaSnimak::id)
                        .collect(Collectors.toSet());
                yield tacne.equals(new HashSet<>(v.opcije()));
            }
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
     * Poeni za odgovor: samo tačan odgovor u takmičenju; 1000 bez tajmera, inače od 1000 (odmah) do 500 (na isteku)
     * linearno po brzini.
     */
    public static int poeni(Boolean tacno, boolean takmicenje, Long trajanjeMs, long vremeMs) {
        if (!takmicenje || !Boolean.TRUE.equals(tacno)) return 0;
        if (trajanjeMs == null || trajanjeMs <= 0) return OSNOVA_POENA;
        double udeo = Math.min(Math.max((double) vremeMs, 0.0) / trajanjeMs, 1.0);
        return (int) Math.round(OSNOVA_POENA * (1 - udeo / 2));
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
