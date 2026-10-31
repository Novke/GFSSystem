package tri.novica.gfssystem.service.uzivo;

import tri.novica.gfssystem.dto.uzivo.RangTacnostiStavka;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Rang tačnosti (spec {@code 2026-10-28-rang-tacnosti-design.md}, odeljak 4). Čiste funkcije, bez baze.
 *
 * <p><b>Bodovana runda:</b> poslednja runda svakog slajda (po rundama, kao {@code StanjeService.bodovniOdgovori}), PITANJE
 * sa tačnim odgovorom, ili ZADATAK za učesnika čija dodeljena varijanta ima polja (rad na papiru ne ulazi).
 * <b>Duguje</b> je učesnik koji je odgovorio, ili koji nije a ušao je pre zatvaranja runde; otvorena runda se ne duguje
 * dok se ne zatvori. Propušteno pitanje je netačno. ★: tačno PITANJE 1, tačan ZADATAK nivo varijante (bez nivoa 2).
 *
 * <p><b>Redosled:</b> više ★, pa prosečno vreme rastuće (bez odgovora iza), pa ime. Tačnost je samo podatak u redu (ne
 * rangira). Isto mesto dele redovi sa istim ★ i istim prosekom na 100 ms.
 */
public final class RangTacnosti {

    /** ★ zadatka čiji snimak nema nivo (pre V15): podrazumevani nivo. */
    static final int PODRAZUMEVANI_NIVO = 2;

    private RangTacnosti() {}

    /** Varijanta izvučena u ZADATAK rundi: da li ima polja (inače rad na papiru) i koliko ★ nosi. */
    public record Varijanta(boolean imaPolja, int zvezdice) {
    }

    /**
     * Runda za rang. {@code kljucSlajda}: slajd (ili zamena kad je slajd obrisan), poslednja runda po {@code redniBroj}
     * važi; {@code zatvoreno}: {@code null} dok je otvorena. {@code varijante}: {@code null} za PITANJE, a za ZADATAK
     * izvučene varijante po id-ju; {@code pitanjeSaTacnim} važi samo za PITANJE.
     */
    public record Runda(Long id, Object kljucSlajda, int redniBroj, LocalDateTime zatvoreno, boolean pitanjeSaTacnim,
                        Map<Long, Varijanta> varijante) {
        boolean zadatak() {
            return varijante != null;
        }
    }

    /** Neizbačen učesnik izvođenja. */
    public record Clan(Long ucesnikId, LocalDateTime kreirano) {
    }

    /** Odgovor (učesnik je neizbačen); {@code varijantaId} samo za ZADATAK. */
    public record Odg(Long rundaId, Long ucesnikId, Boolean tacno, long vremeMs, Long varijantaId) {
    }

    /** Sirovi brojevi jednog učesnika (ili zbira više njih); procenat i prosek se računaju tek na kraju. */
    public static final class Zbir {
        int tacnih;
        int dugovanih;
        int odgovorenih;
        long vremeMs;
        int zvezdice;

        public int tacnih() { return tacnih; }
        public int dugovanih() { return dugovanih; }
        public int odgovorenih() { return odgovorenih; }
        public int zvezdice() { return zvezdice; }

        public void dodaj(Zbir z) {
            tacnih += z.tacnih;
            dugovanih += z.dugovanih;
            odgovorenih += z.odgovorenih;
            vremeMs += z.vremeMs;
            zvezdice += z.zvezdice;
        }

        public Integer procenat() {
            return dugovanih == 0 ? null : (int) Math.round(100.0 * tacnih / dugovanih);
        }

        public Long prosekMs() {
            return odgovorenih == 0 ? null : Math.round((double) vremeMs / odgovorenih);
        }
    }

    /**
     * Zbir po učesniku jednog izvođenja. {@code dodele}: runda -> (učesnik -> varijanta); {@code izuzetaRunda}: runda
     * koja se ne računa uopšte (trenutna runda za projektor pre TACAN), ili {@code null}. Učesnik bez ijednog
     * dugovanog pitanja ima prazan zbir.
     */
    public static Map<Long, Zbir> poUcesniku(List<Runda> runde, List<Clan> ucesnici, List<Odg> odgovori,
                                             Map<Long, Map<Long, Long>> dodele, Long izuzetaRunda) {
        Map<Long, Zbir> zbir = new LinkedHashMap<>();
        ucesnici.forEach(c -> zbir.put(c.ucesnikId(), new Zbir()));
        Map<Long, Map<Long, Odg>> poRundi = new HashMap<>();
        for (Odg o : odgovori) {
            if (zbir.containsKey(o.ucesnikId())) {
                poRundi.computeIfAbsent(o.rundaId(), k -> new HashMap<>()).putIfAbsent(o.ucesnikId(), o);
            }
        }
        for (Runda r : poslednje(runde)) {
            if (r.id().equals(izuzetaRunda)) continue;
            if (!r.zadatak() && !r.pitanjeSaTacnim()) continue;
            Map<Long, Odg> odg = poRundi.getOrDefault(r.id(), Map.of());
            Map<Long, Long> dodeleRunde = dodele.getOrDefault(r.id(), Map.of());
            for (Clan c : ucesnici) {
                Odg o = odg.get(c.ucesnikId());
                int zvezdice = 1;
                if (r.zadatak()) {
                    Long varijantaId = o != null && o.varijantaId() != null ? o.varijantaId() : dodeleRunde.get(c.ucesnikId());
                    Varijanta v = varijantaId == null ? null : r.varijante().get(varijantaId);
                    if (v == null || !v.imaPolja()) continue;
                    zvezdice = v.zvezdice();
                }
                Zbir z = zbir.get(c.ucesnikId());
                if (o != null) {
                    z.dugovanih++;
                    z.odgovorenih++;
                    z.vremeMs += Math.max(0, o.vremeMs());
                    if (Boolean.TRUE.equals(o.tacno())) {
                        z.tacnih++;
                        z.zvezdice += zvezdice;
                    }
                } else if (r.zatvoreno() != null && c.kreirano().isBefore(r.zatvoreno())) {
                    z.dugovanih++;
                }
            }
        }
        return zbir;
    }

    /** Poslednja runda svakog slajda (po {@code redniBroj}), redom kojim su runde date. */
    static List<Runda> poslednje(List<Runda> runde) {
        Map<Object, Runda> poslednja = new HashMap<>();
        for (Runda r : runde) {
            poslednja.merge(r.kljucSlajda(), r, (a, b) -> b.redniBroj() > a.redniBroj() ? b : a);
        }
        Set<Runda> vazece = Collections.newSetFromMap(new IdentityHashMap<>());
        vazece.addAll(poslednja.values());
        return runde.stream().filter(vazece::contains).toList();
    }

    /** Red pre rangiranja: ključ identiteta, ime i zbir. */
    public record Kandidat<K>(K kljuc, String ime, Zbir zbir) {
    }

    /**
     * Rangira kandidate sa bar jednim dugovanim pitanjem (ostali se izostavljaju) i pravi stavke; mesto se deli kad su
     * ★ i prosek na 100 ms isti.
     */
    public static <K> List<RangTacnostiStavka> rangiraj(List<Kandidat<K>> kandidati,
                                                        BiFunction<Kandidat<K>, Integer, RangTacnostiStavka> stavka) {
        List<Kandidat<K>> sortirani = kandidati.stream()
                .filter(k -> k.zbir().dugovanih > 0)
                .sorted(redosled())
                .toList();
        List<RangTacnostiStavka> rang = new ArrayList<>(sortirani.size());
        Kandidat<K> prethodni = null;
        int mesto = 0;
        for (int i = 0; i < sortirani.size(); i++) {
            Kandidat<K> k = sortirani.get(i);
            if (prethodni == null || !isto(prethodni.zbir(), k.zbir())) mesto = i + 1;
            rang.add(stavka.apply(k, mesto));
            prethodni = k;
        }
        return rang;
    }

    private static <K> Comparator<Kandidat<K>> redosled() {
        return (a, b) -> {
            int c = Integer.compare(b.zbir().zvezdice, a.zbir().zvezdice);
            if (c != 0) return c;
            c = Comparator.<Long>nullsLast(Comparator.naturalOrder()).compare(prosek100(a.zbir()), prosek100(b.zbir()));
            if (c != 0) return c;
            c = String.CASE_INSENSITIVE_ORDER.compare(a.ime() == null ? "" : a.ime(), b.ime() == null ? "" : b.ime());
            return c != 0 ? c : String.valueOf(a.kljuc()).compareTo(String.valueOf(b.kljuc()));
        };
    }

    private static Long prosek100(Zbir z) {
        Long p = z.prosekMs();
        return p == null ? null : Math.round(p / 100.0);
    }

    private static boolean isto(Zbir a, Zbir b) {
        return a.zvezdice == b.zvezdice && Objects.equals(prosek100(a), prosek100(b));
    }

    /** Ime gosta za spajanje u okviru predavanja: bez razmaka na krajevima, mala slova, bez dijakritika, jedan razmak. */
    public static String kljucImena(String ime) {
        if (ime == null) return "";
        String s = ime.strip().toLowerCase(Locale.ROOT).replace('đ', 'd');
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.replaceAll("\\s+", " ");
    }
}
