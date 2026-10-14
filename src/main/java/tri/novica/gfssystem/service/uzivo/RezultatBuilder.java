package tri.novica.gfssystem.service.uzivo;

import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.uzivo.Odgovor;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;

import java.util.*;
import java.util.stream.Collectors;

/** Zbirni rezultat jedne runde iz snimka pitanja i odgovora. Čiste funkcije, bez baze. */
public final class RezultatBuilder {
    private static final int NAJCESCE = 10;

    private RezultatBuilder() {}

    /**
     * @param javno         rezultat za publiku: sakriveni odgovori (i cela grupa kratkih odgovora sa bar jednim
     *                      sakrivenim) se izostavljaju; nastavnički rezultat ih zadržava i označava
     * @param tacanPrikazan tek tada {@code tacna}/{@code tacan} nisu {@code null}
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
        return switch (p.tip()) {
            case JEDAN_TACAN, VISE_TACNIH, ANKETA, TACNO_NETACNO ->
                    new Rezultat(p.tip(), ukupno, opcije(p, koristi, tacanPrikazan), null, null, null);
            case BROJ -> new Rezultat(p.tip(), ukupno, null, brojevi(p, koristi), null, null);
            case KRATAK_TEKST -> new Rezultat(p.tip(), ukupno, null, null,
                    tekstovi(p, koristi, sakriveniKljucevi, tacanPrikazan), null);
            case SKALA -> new Rezultat(p.tip(), ukupno, null, null, null, skala(koristi));
        };
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

    private static RezultatBrojevi brojevi(PitanjeSnimak p, List<Odgovor> odgovori) {
        List<Double> vrednosti = odgovori.stream().map(Odgovor::getBroj).filter(Objects::nonNull).sorted().toList();
        Double medijana = null;
        int n = vrednosti.size();
        if (n > 0) medijana = n % 2 == 1 ? vrednosti.get(n / 2) : (vrednosti.get(n / 2 - 1) + vrednosti.get(n / 2)) / 2.0;
        Integer uOdstupanju = null;
        if (p.brojTacno() != null) {
            uOdstupanju = (int) vrednosti.stream().filter(v -> Boolean.TRUE.equals(Ocenjivac.uOdstupanju(p, v))).count();
        }
        Map<Double, Integer> broj = new HashMap<>();
        for (Double v : vrednosti) broj.merge(v, 1, Integer::sum);
        List<BrojStavka> najcesce = broj.entrySet().stream()
                .sorted(Map.Entry.<Double, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(NAJCESCE)
                .map(e -> new BrojStavka(e.getKey(), e.getValue()))
                .toList();
        return new RezultatBrojevi(medijana, uOdstupanju, najcesce);
    }

    private static List<RezultatTekst> tekstovi(PitanjeSnimak p, List<Odgovor> odgovori, Set<String> sakriveniKljucevi,
                                                boolean tacanPrikazan) {
        Map<String, List<String>> grupe = new LinkedHashMap<>();
        for (Odgovor o : odgovori) {
            if (o.getTekst() == null) continue;
            grupe.computeIfAbsent(kljuc(o.getTekst()), k -> new ArrayList<>()).add(o.getTekst().trim());
        }
        Set<String> prihvatljivi = p.prihvatljiviOdgovori() == null ? Set.of()
                : p.prihvatljiviOdgovori().stream().map(Normalizacija::tekst).collect(Collectors.toSet());
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
