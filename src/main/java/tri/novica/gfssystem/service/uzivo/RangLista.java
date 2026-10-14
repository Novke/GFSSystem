package tri.novica.gfssystem.service.uzivo;

import tri.novica.gfssystem.dto.uzivo.RangStavka;
import tri.novica.gfssystem.entity.uzivo.Odgovor;
import tri.novica.gfssystem.entity.uzivo.PitanjeRunda;
import tri.novica.gfssystem.entity.uzivo.Ucesnik;

import java.util.*;

/** Rang-lista takmičenja. Čiste funkcije, bez baze. */
public final class RangLista {

    private RangLista() {}

    /**
     * Rang učesnika koji nisu izbačeni: više poena prvo, pri jednakim poenima manji zbir vremena odgovora, pa ranija
     * prijava. Učesnik bez odgovora ima 0 poena i ide iza onih koji su odgovarali sa istim poenima.
     */
    public static List<RangStavka> izracunaj(List<Ucesnik> ucesnici, List<Odgovor> sviOdgovori) {
        List<Odgovor> vazeci = poslednjeRunde(sviOdgovori);
        Map<Long, Integer> poeni = new HashMap<>();
        Map<Long, Long> vreme = new HashMap<>();
        for (Odgovor o : vazeci) {
            Long id = o.getUcesnik().getId();
            poeni.merge(id, o.getPoeni(), Integer::sum);
            vreme.merge(id, o.getVremeMs(), Long::sum);
        }
        List<Ucesnik> rangirani = ucesnici.stream().filter(u -> !u.isIzbacen())
                .sorted(Comparator
                        .comparingInt((Ucesnik u) -> poeni.getOrDefault(u.getId(), 0)).reversed()
                        .thenComparingLong(u -> vreme.getOrDefault(u.getId(), Long.MAX_VALUE))
                        .thenComparing(Ucesnik::getKreirano)
                        .thenComparing(Ucesnik::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<RangStavka> rang = new ArrayList<>(rangirani.size());
        for (int i = 0; i < rangirani.size(); i++) {
            Ucesnik u = rangirani.get(i);
            rang.add(new RangStavka(i + 1, u.getId(), u.getIme(), poeni.getOrDefault(u.getId(), 0)));
        }
        return rang;
    }

    /** Zbir poena po id-ju učesnika, samo iz poslednje runde svakog slajda. */
    public static Map<Long, Integer> poeniPoUcesniku(List<Odgovor> sviOdgovori) {
        Map<Long, Integer> poeni = new HashMap<>();
        for (Odgovor o : poslednjeRunde(sviOdgovori)) poeni.merge(o.getUcesnik().getId(), o.getPoeni(), Integer::sum);
        return poeni;
    }

    /** Odgovori iz runde sa najvećim {@code redniBroj} za svaki slajd; runda bez slajda važi sama za sebe. */
    private static List<Odgovor> poslednjeRunde(List<Odgovor> sviOdgovori) {
        Map<Object, Integer> najvisi = new HashMap<>();
        for (Odgovor o : sviOdgovori) najvisi.merge(kljuc(o.getRunda()), o.getRunda().getRedniBroj(), Math::max);
        return sviOdgovori.stream()
                .filter(o -> o.getRunda().getRedniBroj() == najvisi.get(kljuc(o.getRunda())))
                .toList();
    }

    /** Slajd (po id-ju) ili sama runda kad nema slajda. */
    private static Object kljuc(PitanjeRunda r) {
        return r.getSlajdId() != null ? r.getSlajdId() : r;
    }
}
