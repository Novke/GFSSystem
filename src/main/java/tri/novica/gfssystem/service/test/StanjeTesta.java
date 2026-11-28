package tri.novica.gfssystem.service.test;

import tri.novica.gfssystem.dto.test.online.FazaTesta;
import tri.novica.gfssystem.dto.test.online.StanjeOnline;
import tri.novica.gfssystem.dto.test.online.StanjePrimerkaKonzole;
import tri.novica.gfssystem.entity.test.StanjePrimerka;
import tri.novica.gfssystem.entity.test.TestPrimerak;
import tri.novica.gfssystem.entity.test.TestSmena;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Stanja online testa i primerka izvedena iz smene i primerka (plan 4.3 i 5); čiste funkcije. */
public final class StanjeTesta {

    /** Izmena stigla do ovoliko posle roka se još prima (spec 12). */
    public static final int TOLERANCIJA_SEKUNDI = 10;

    private StanjeTesta() {}

    public static StanjeOnline stanje(TestSmena s) {
        if (s == null || s.getUlazOtvoren() == null) return StanjeOnline.PRIPREMA;
        if (s.getZatvorena() != null) return StanjeOnline.ZAVRSEN;
        if (s.getPokrenuta() != null) return StanjeOnline.U_TOKU;
        return StanjeOnline.ULAZ;
    }

    /** Rok primerka: zajednički rok smene plus produženje; {@code null} pre „Pokreni“. */
    public static LocalDateTime rok(TestSmena s, TestPrimerak p) {
        if (s == null || s.getRok() == null) return null;
        return s.getRok().plusMinutes(p == null ? 0 : p.getProduzenjeMin());
    }

    /** Da li je primerak prošao rok uz toleranciju (posle toga se zaključava sa onim što je sačuvano). */
    public static boolean istekao(TestSmena s, TestPrimerak p, LocalDateTime sada) {
        LocalDateTime rok = rok(s, p);
        return rok != null && sada.isAfter(rok.plusSeconds(TOLERANCIJA_SEKUNDI));
    }

    public static boolean otvoren(TestPrimerak p) {
        return p.getStanje() == StanjePrimerka.DODELJEN || p.getStanje() == StanjePrimerka.U_TOKU;
    }

    public static StanjePrimerkaKonzole konzola(TestSmena s, TestPrimerak p) {
        return switch (p.getStanje()) {
            case DODELJEN -> StanjePrimerkaKonzole.NIJE_USAO;
            case U_TOKU -> s != null && s.getPokrenuta() != null ? StanjePrimerkaKonzole.RADI : StanjePrimerkaKonzole.CEKA;
            case PREDAT -> StanjePrimerkaKonzole.PREDAO;
            case PONISTEN -> StanjePrimerkaKonzole.PONISTEN;
        };
    }

    public static FazaTesta faza(TestSmena s, TestPrimerak p) {
        if (p.getStanje() == StanjePrimerka.PREDAT || p.getStanje() == StanjePrimerka.PONISTEN) return FazaTesta.PREDATO;
        if (s.getZatvorena() != null) return FazaTesta.ZAVRSEN;
        if (p.getStanje() == StanjePrimerka.U_TOKU) {
            return s.getPokrenuta() != null ? FazaTesta.U_TOKU : FazaTesta.CEKANJE;
        }
        return FazaTesta.ULAZ;
    }

    public static Long ms(LocalDateTime t, Clock clock) {
        return t == null ? null : t.atZone(clock.getZone() == null ? ZoneId.systemDefault() : clock.getZone()).toInstant().toEpochMilli();
    }
}
