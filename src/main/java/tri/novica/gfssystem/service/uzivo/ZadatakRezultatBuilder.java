package tri.novica.gfssystem.service.uzivo;

import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.PoljeOpcija;
import tri.novica.gfssystem.dto.banka.TipPolja;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.service.banka.OcenaPolja;

import java.util.*;

/**
 * Rezultat runde Zadatak slajda po varijanti, iz snimka runde (šema i tačni odgovori kakvi su bili pri otvaranju), dodela
 * i odgovora. Čiste funkcije. Tri prekidača vidljivosti su nezavisna (banka spec 4.2.4): bez {@code odgovoriVidljivi}
 * nema brojeva ni vrednosti (opcije ostaju sa brojem 0, zbog "tačno:"), bez {@code tacanVidljiv} nema tačnosti ni broja
 * tačnih, bez {@code resenjeVidljivo} nema id-ja SVG-a rešenja. Konzola zove sa sva tri {@code true}.
 */
public final class ZadatakRezultatBuilder {

    static final int MAX_VREDNOSTI = 10;

    /** Odgovor učesnika na zadatak: dodeljena varijanta, polja (iz {@code odgovori.polja}) i tačnost. */
    public record OdgovorZadatka(Long ucesnikId, Long varijantaId, Map<String, PoljeOdgovora> polja, Boolean tacno) {
    }

    private ZadatakRezultatBuilder() {}

    /**
     * @param dodele  učesnik -> dodeljena varijanta (samo učesnici koji se računaju)
     * @param odgovori odgovori runde (samo učesnici koji se računaju)
     */
    public static ZadatakRezultat izgradi(ZadatakSnimak s, Map<Long, Long> dodele, List<OdgovorZadatka> odgovori,
                                          boolean odgovoriVidljivi, boolean tacanVidljiv, boolean resenjeVidljivo) {
        Map<Long, Integer> dodeljeno = new HashMap<>();
        dodele.values().forEach(v -> dodeljeno.merge(v, 1, Integer::sum));
        Map<Long, List<OdgovorZadatka>> poVarijanti = new HashMap<>();
        odgovori.forEach(o -> poVarijanti.computeIfAbsent(o.varijantaId(), k -> new ArrayList<>()).add(o));

        List<RezultatVarijante> varijante = new ArrayList<>();
        int ukupno = 0;
        for (VarijantaSnimak v : s.varijante() == null ? List.<VarijantaSnimak>of() : s.varijante()) {
            List<OdgovorZadatka> ov = poVarijanti.getOrDefault(v.varijantaId(), List.of());
            ukupno += ov.size();
            Integer tacnih = odgovoriVidljivi && tacanVidljiv
                    ? (int) ov.stream().filter(o -> Boolean.TRUE.equals(o.tacno())).count() : null;
            List<RezultatPolja> polja = v.polja() == null ? List.of()
                    : v.polja().stream().map(p -> polje(p, v, ov, odgovoriVidljivi, tacanVidljiv)).toList();
            varijante.add(new RezultatVarijante(v.varijantaId(), v.naziv(), v.svgId(),
                    resenjeVidljivo ? v.resenjeSvgId() : null,
                    odgovoriVidljivi ? dodeljeno.getOrDefault(v.varijantaId(), 0) : 0,
                    odgovoriVidljivi ? ov.size() : 0, tacnih, polja));
        }
        return new ZadatakRezultat(odgovoriVidljivi ? ukupno : 0, List.copyOf(varijante), odgovoriVidljivi);
    }

    /** Konzola: po izvučenoj varijanti dodeljeno, odgovorilo, tačnih (sve vidljivo). */
    public static List<VarijantaRunde> varijanteRunde(ZadatakSnimak s, Map<Long, Long> dodele, List<OdgovorZadatka> odgovori) {
        ZadatakRezultat r = izgradi(s, dodele, odgovori, true, true, true);
        return r.varijante().stream()
                .map(v -> new VarijantaRunde(v.varijantaId(), v.naziv(), v.svgId(), v.resenjeSvgId(), v.dodeljeno(),
                        v.odgovorilo(), v.tacnih() == null ? 0 : v.tacnih(),
                        s.varijanta(v.varijantaId()).map(VarijantaSnimak::nivo).orElse(null)))
                .toList();
    }

    private static RezultatPolja polje(Polje p, VarijantaSnimak v, List<OdgovorZadatka> odgovori, boolean odgovoriVidljivi,
                                       boolean tacanVidljiv) {
        OdgovorPolja tacno = v.odgovori() == null ? null : v.odgovori().get(p.kljuc());
        List<Object> vrednosti = new ArrayList<>();
        for (OdgovorZadatka o : odgovori) {
            PoljeOdgovora po = o.polja() == null ? null : o.polja().get(p.kljuc());
            if (po != null && po.vrednost() != null) {
                vrednosti.add(po.vrednost());
            }
        }
        List<RezultatTekst> tekstovi = null;
        List<RezultatOpcija> opcije = null;
        switch (p.tip()) {
            case BROJ, TEKST -> tekstovi = odgovoriVidljivi ? grupe(p, tacno, vrednosti, tacanVidljiv) : null;
            case JEDAN_TACAN, VISE_TACNIH -> opcije = opcije(p, tacno, vrednosti, odgovoriVidljivi, tacanVidljiv);
        }
        return new RezultatPolja(p.kljuc(), p.oznaka(), p.tip(), p.jedinica(), tekstovi, opcije,
                tacanVidljiv ? tacno : null);
    }

    /** BROJ i TEKST: grupe istih vrednosti (broj po vrednosti, tekst po ključu ocene, {@link OcenaPolja#kljucOcene}), najčešće prve, najviše 10. */
    private static List<RezultatTekst> grupe(Polje p, OdgovorPolja tacno, List<Object> vrednosti, boolean tacanVidljiv) {
        Map<String, Map<String, Integer>> oblici = new LinkedHashMap<>();
        for (Object o : vrednosti) {
            if (!(o instanceof String str)) continue;
            String kljuc = kljuc(p, str);
            if (kljuc == null) continue;
            oblici.computeIfAbsent(kljuc, k -> new HashMap<>()).merge(str.strip(), 1, Integer::sum);
        }
        List<RezultatTekst> lista = new ArrayList<>();
        oblici.forEach((kljuc, m) -> {
            int broj = m.values().stream().mapToInt(Integer::intValue).sum();
            String tekst = m.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey).findFirst().orElse(kljuc);
            Boolean tacan = tacanVidljiv ? OcenaPolja.tacno(p, tacno, tekst) : null;
            lista.add(new RezultatTekst(kljuc, tekst, broj, false, tacan));
        });
        lista.sort(Comparator.comparingInt(RezultatTekst::broj).reversed().thenComparing(RezultatTekst::kljuc));
        return List.copyOf(lista.subList(0, Math.min(MAX_VREDNOSTI, lista.size())));
    }

    private static String kljuc(Polje p, String s) {
        if (p.tip() == TipPolja.BROJ) {
            Double broj = Normalizacija.broj(s);
            return broj == null || !Double.isFinite(broj) ? null : Double.toString(broj);
        }
        String k = OcenaPolja.kljucOcene(p, s);
        return k.isEmpty() ? null : k;
    }

    /** JEDAN_TACAN i VISE_TACNIH: svaka opcija šeme, sa brojem glasova (0 bez vidljivih odgovora) i tačnošću uz TACAN. */
    private static List<RezultatOpcija> opcije(Polje p, OdgovorPolja tacno, List<Object> vrednosti, boolean odgovoriVidljivi,
                                               boolean tacanVidljiv) {
        Map<Long, Integer> glasovi = new HashMap<>();
        for (Object o : vrednosti) {
            if (!(o instanceof List<?> l)) continue;
            new HashSet<>(l).forEach(id -> {
                if (id instanceof Number n) glasovi.merge(n.longValue(), 1, Integer::sum);
            });
        }
        Set<Long> tacne = new HashSet<>();
        if (tacno != null) {
            if (tacno.tacna() != null) tacne.add(tacno.tacna());
            if (tacno.tacne() != null) tacne.addAll(tacno.tacne());
        }
        List<RezultatOpcija> lista = new ArrayList<>();
        for (PoljeOpcija o : p.opcije() == null ? List.<PoljeOpcija>of() : p.opcije()) {
            lista.add(new RezultatOpcija(o.id(), o.tekst(), odgovoriVidljivi ? glasovi.getOrDefault(o.id(), 0) : 0,
                    tacanVidljiv ? tacne.contains(o.id()) : null));
        }
        return List.copyOf(lista);
    }
}
