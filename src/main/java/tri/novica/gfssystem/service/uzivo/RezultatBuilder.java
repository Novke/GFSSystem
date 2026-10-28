package tri.novica.gfssystem.service.uzivo;

import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.uzivo.Odgovor;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;

import java.util.*;
import java.util.stream.Collectors;

/** Zbirni rezultat jedne runde iz snimka pitanja i odgovora. Čiste funkcije, bez baze. */
public final class RezultatBuilder {
    private RezultatBuilder() {}

    /**
     * @param javno         rezultat za publiku: sakriveni odgovori (i cela grupa kratkih odgovora sa bar jednim
     *                      sakrivenim) se izostavljaju; nastavnički rezultat ih zadržava i označava
     * @param tacanPrikazan tek tada {@code tacna}/{@code tacan} i {@link Rezultat#tacnih} nisu {@code null} (redosled stavki
     *                      ne zavisi od tačnosti, pa ni on ne odaje tačan odgovor pre toga)
     */
    public static Rezultat izgradi(PitanjeSnimak p, List<Odgovor> odgovori, boolean javno, boolean tacanPrikazan) {
        List<Odgovor> svi = odgovori == null ? List.of() : odgovori;
        Set<String> sakriveniKljucevi = p.tip() == TipPitanja.KRATAK_TEKST
                ? svi.stream().filter(Odgovor::isSakriven).map(o -> kljuc(o.getTekst())).collect(Collectors.toSet())
                : Set.of();
        List<Odgovor> koristi = !javno ? svi : svi.stream()
                .filter(o -> !o.isSakriven())
                .filter(o -> p.tip() != TipPitanja.KRATAK_TEKST || !sakriveniKljucevi.contains(kljuc(o.getTekst())))
                .toList();
        int ukupno = koristi.size();
        Integer tacnih = tacanPrikazan && p.imaTacanOdgovor()
                ? (int) koristi.stream().filter(o -> tacan(p, o)).count() : null;
        return switch (p.tip()) {
            case JEDAN_TACAN, VISE_TACNIH, ANKETA, TACNO_NETACNO ->
                    new Rezultat(p.tip(), ukupno, opcije(p, koristi, tacanPrikazan), null, null, null, tacnih);
            case BROJ -> new Rezultat(p.tip(), ukupno, null, brojevi(p, koristi, tacanPrikazan), null, null, tacnih);
            case KRATAK_TEKST -> new Rezultat(p.tip(), ukupno, null, null,
                    tekstovi(p, koristi, sakriveniKljucevi, tacanPrikazan), null, tacnih);
            case SKALA -> new Rezultat(p.tip(), ukupno, null, null, null, skala(koristi), null);
        };
    }

    /**
     * Da li je odgovor tačan po snimku (isto pravilo kao {@link Ocenjivac#tacno}, ali bez izuzetka za neispravan zapis, koji
     * nije tačan): jedan izbor tačne opcije, kod "više tačnih" tačno skup tačnih, broj u odstupanju, tekst među prihvatljivima.
     */
    static boolean tacan(PitanjeSnimak p, Odgovor o) {
        return switch (p.tip()) {
            case JEDAN_TACAN, TACNO_NETACNO -> {
                Set<Long> ids = izabrane(o.getOpcije());
                yield ids.size() == 1 && tacneOpcije(p).containsAll(ids);
            }
            case VISE_TACNIH -> {
                Set<Long> ids = izabrane(o.getOpcije());
                yield !ids.isEmpty() && ids.equals(tacneOpcije(p));
            }
            case BROJ -> o.getBroj() != null && Boolean.TRUE.equals(Ocenjivac.uOdstupanju(p, o.getBroj()));
            case KRATAK_TEKST -> o.getTekst() != null && prihvatljivi(p).contains(kljuc(o.getTekst()));
            case ANKETA, SKALA -> false;
        };
    }

    private static Set<Long> tacneOpcije(PitanjeSnimak p) {
        return p.opcije() == null ? Set.of()
                : p.opcije().stream().filter(OpcijaSnimak::tacna).map(OpcijaSnimak::id).collect(Collectors.toSet());
    }

    private static Set<String> prihvatljivi(PitanjeSnimak p) {
        return p.prihvatljiviOdgovori() == null ? Set.of()
                : p.prihvatljiviOdgovori().stream().map(Normalizacija::tekst).collect(Collectors.toSet());
    }

    private static List<RezultatOpcija> opcije(PitanjeSnimak p, List<Odgovor> odgovori, boolean tacanPrikazan) {
        Map<Long, Integer> glasovi = new HashMap<>();
        for (Odgovor o : odgovori) {
            for (Long id : izabrane(o.getOpcije())) glasovi.merge(id, 1, Integer::sum);
        }
        boolean prikaziTacnu = tacanPrikazan && p.imaTacanOdgovor();
        return p.opcije().stream()
                .sorted(Comparator.comparingInt(OpcijaSnimak::rb))
                .map(op -> new RezultatOpcija(op.id(), op.tekst(), glasovi.getOrDefault(op.id(), 0),
                        prikaziTacnu ? op.tacna() : null))
                .toList();
    }

    private static Set<Long> izabrane(String opcije) {
        Set<Long> ids = new LinkedHashSet<>();
        if (opcije == null) return ids;
        for (String deo : opcije.split(",")) {
            String t = deo.trim();
            if (t.isEmpty()) continue;
            try {
                ids.add(Long.valueOf(t));
            } catch (NumberFormatException ignorisi) {
                // neispravan zapis se ne broji
            }
        }
        return ids;
    }

    /**
     * Sve različite vrednosti (bez zaokruživanja: 3,14 i 3,141 su dve stavke; {@code -0} je {@code 0}), najčešće prve, pa
     * rastuće po vrednosti. Bez ograničenja broja stavki: najviše jedna po učesniku (do 300), kao i grupe kratkog teksta.
     */
    private static RezultatBrojevi brojevi(PitanjeSnimak p, List<Odgovor> odgovori, boolean tacanPrikazan) {
        Map<Double, Integer> broj = new HashMap<>();
        for (Odgovor o : odgovori) {
            Double v = o.getBroj();
            if (v != null) broj.merge(v == 0 ? 0.0 : v, 1, Integer::sum);
        }
        boolean oceni = tacanPrikazan && p.brojTacno() != null;
        List<BrojStavka> vrednosti = broj.entrySet().stream()
                .sorted(Map.Entry.<Double, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new BrojStavka(e.getKey(), e.getValue(), oceni ? Ocenjivac.uOdstupanju(p, e.getKey()) : null))
                .toList();
        return new RezultatBrojevi(vrednosti);
    }

    private static List<RezultatTekst> tekstovi(PitanjeSnimak p, List<Odgovor> odgovori, Set<String> sakriveniKljucevi,
                                                boolean tacanPrikazan) {
        Map<String, List<String>> grupe = new LinkedHashMap<>();
        for (Odgovor o : odgovori) {
            if (o.getTekst() == null) continue;
            grupe.computeIfAbsent(kljuc(o.getTekst()), k -> new ArrayList<>()).add(o.getTekst().trim());
        }
        Set<String> prihvatljivi = prihvatljivi(p);
        boolean proveriTacnost = tacanPrikazan && !prihvatljivi.isEmpty();
        return grupe.entrySet().stream()
                .map(e -> new RezultatTekst(e.getKey(), najcesciOblik(e.getValue()), e.getValue().size(),
                        sakriveniKljucevi.contains(e.getKey()), proveriTacnost ? prihvatljivi.contains(e.getKey()) : null))
                .sorted(Comparator.comparingInt(RezultatTekst::broj).reversed().thenComparing(RezultatTekst::kljuc))
                .toList();
    }

    /** Najčešći originalni oblik; pri izjednačenju prvi unet. */
    private static String najcesciOblik(List<String> oblici) {
        Map<String, Integer> broj = new LinkedHashMap<>();
        for (String o : oblici) broj.merge(o, 1, Integer::sum);
        String najbolji = null;
        int max = 0;
        for (Map.Entry<String, Integer> e : broj.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                najbolji = e.getKey();
            }
        }
        return najbolji;
    }

    private static RezultatSkala skala(List<Odgovor> odgovori) {
        int[] raspodela = new int[5];
        int zbir = 0;
        int n = 0;
        for (Odgovor o : odgovori) {
            Integer s = o.getSkala();
            if (s == null || s < 1 || s > 5) continue;
            raspodela[s - 1]++;
            zbir += s;
            n++;
        }
        Double prosek = n == 0 ? null : Math.round(100.0 * zbir / n) / 100.0;
        return new RezultatSkala(Arrays.stream(raspodela).boxed().toList(), prosek);
    }

    private static String kljuc(String tekst) {
        return Normalizacija.tekst(tekst);
    }
}
