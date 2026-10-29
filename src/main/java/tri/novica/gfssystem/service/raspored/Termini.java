package tri.novica.gfssystem.service.raspored;

import tri.novica.gfssystem.entity.raspored.Raspored;
import tri.novica.gfssystem.entity.raspored.RasporedStavka;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Termini rasporeda se računaju, ne čuvaju (spec najave odsustva, odeljak 2): termin je (stavka, datum) gde je datum u
 * periodu {@code [vaziOd, vaziDo]} i ima dan stavke. Čiste funkcije; "sada" daje pozivalac iz {@code Clock} bean-a
 * (Europe/Belgrade u kontejneru).
 */
public final class Termini {

    private Termini() {
    }

    /** Da li je {@code datum} termin: u periodu (granice uključene) i ISO dan {@code dan}. */
    public static boolean jeTermin(LocalDate vaziOd, LocalDate vaziDo, int dan, LocalDate datum) {
        return datum != null && !datum.isBefore(vaziOd) && !datum.isAfter(vaziDo) && datum.getDayOfWeek().getValue() == dan;
    }

    public static boolean jeTermin(RasporedStavka s, LocalDate datum) {
        Raspored r = s.getRaspored();
        return jeTermin(r.getVaziOd(), r.getVaziDo(), s.getDan(), datum);
    }

    /** Termin je počeo u trenutku početka (najava i otkazivanje su dozvoljeni samo pre toga). */
    public static boolean poceo(LocalDateTime sada, LocalDate datum, LocalTime pocetak) {
        return !sada.isBefore(datum.atTime(pocetak));
    }

    public static boolean zavrsen(LocalDateTime sada, LocalDate datum, LocalTime kraj) {
        return !sada.isBefore(datum.atTime(kraj));
    }

    /** Datumi termina u prozoru {@code [od, doDatuma]} preseku sa periodom, rastuće; prazno kad se ne seku. */
    public static List<LocalDate> uProzoru(LocalDate vaziOd, LocalDate vaziDo, int dan, LocalDate od, LocalDate doDatuma) {
        LocalDate pocetak = od.isAfter(vaziOd) ? od : vaziOd;
        LocalDate kraj = doDatuma.isBefore(vaziDo) ? doDatuma : vaziDo;
        List<LocalDate> datumi = new ArrayList<>();
        for (LocalDate d = pocetak.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dan))); !d.isAfter(kraj); d = d.plusWeeks(1)) {
            datumi.add(d);
        }
        return datumi;
    }

    public static List<LocalDate> uProzoru(RasporedStavka s, LocalDate od, LocalDate doDatuma) {
        Raspored r = s.getRaspored();
        return uProzoru(r.getVaziOd(), r.getVaziDo(), s.getDan(), od, doDatuma);
    }

    /** Prvih {@code n} termina koji u trenutku {@code sada} nisu počeli (današnji ostaje do početka). */
    public static List<LocalDate> naredni(LocalDate vaziOd, LocalDate vaziDo, int dan, LocalTime pocetak, LocalDateTime sada,
                                          int n) {
        List<LocalDate> datumi = new ArrayList<>(n);
        LocalDate d = (sada.toLocalDate().isAfter(vaziOd) ? sada.toLocalDate() : vaziOd)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dan)));
        for (; datumi.size() < n && !d.isAfter(vaziDo); d = d.plusWeeks(1)) {
            if (!poceo(sada, d, pocetak)) {
                datumi.add(d);
            }
        }
        return datumi;
    }

    public static List<LocalDate> naredni(RasporedStavka s, LocalDateTime sada, int n) {
        Raspored r = s.getRaspored();
        return naredni(r.getVaziOd(), r.getVaziDo(), s.getDan(), s.getPocetak(), sada, n);
    }

    /**
     * Slobodan tekst (razlog studenta, napomena nastavnika): bez kontrolnih i formatnih znakova ({@code \p{Cc}\p{Cf}}),
     * razmaci skupljeni u jedan, prazno = {@code null}. Duže od {@code max} znakova (code point-a, kao varchar u utf8mb4):
     * {@link IllegalArgumentException}.
     */
    public static String ocisti(String tekst, int max) {
        if (tekst == null) {
            return null;
        }
        String s = tekst.replaceAll("[\\p{Cc}&&[^\\s]]|\\p{Cf}", "").replaceAll("\\s+", " ").strip();
        if (s.isEmpty()) {
            return null;
        }
        if (s.codePointCount(0, s.length()) > max) {
            throw new IllegalArgumentException("Tekst je duži od " + max + " znakova.");
        }
        return s;
    }
}
