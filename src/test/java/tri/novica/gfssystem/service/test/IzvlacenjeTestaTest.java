package tri.novica.gfssystem.service.test;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.service.test.IzvlacenjeTesta.Kandidat;
import tri.novica.gfssystem.service.test.IzvlacenjeTesta.Mesto;
import tri.novica.gfssystem.service.test.IzvlacenjeTesta.PlanStavke;
import tri.novica.gfssystem.service.test.IzvlacenjeTesta.Stavka;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class IzvlacenjeTestaTest {

    /** Tipovi 100, 200 sa varijantama id = tip + i, nivo po zadatom nizu. */
    private static Stavka stavka(int broj, boolean istiTip, Set<Integer> nivoi, int[]... nivoiPoTipu) {
        LinkedHashMap<Long, List<Kandidat>> poTipu = new LinkedHashMap<>();
        for (int t = 0; t < nivoiPoTipu.length; t++) {
            long tip = (t + 1) * 100L;
            List<Kandidat> l = new ArrayList<>();
            for (int i = 0; i < nivoiPoTipu[t].length; i++) l.add(new Kandidat(tip + i, tip, nivoiPoTipu[t][i]));
            poTipu.put(tip, l);
        }
        return new Stavka(1, "S", broj, istiTip, nivoi, poTipu);
    }

    private static <J> PlanStavke<J> plan(Stavka s, List<J> jedinice, long seme) {
        return IzvlacenjeTesta.planiraj(s, null, jedinice.size(), jedinice, Map.of(), new HashMap<>(), (j, v) -> 0,
                new Random(seme));
    }

    @Test
    void istoSemeIstiIzbor() {
        Stavka s = stavka(2, true, Set.of(), new int[]{2, 2, 2, 2}, new int[]{2, 2, 2});
        List<String> grupe = List.of("A", "B", "C");
        assertThat(plan(s, grupe, 7).izbor()).isEqualTo(plan(s, grupe, 7).izbor());
    }

    @Test
    void razliciteVarijantePoGrupiIstiTipPoMestu() {
        Stavka s = stavka(1, true, Set.of(), new int[]{2, 2, 2, 2}, new int[]{2, 2, 2, 2});
        var p = plan(s, List.of("A", "B", "C", "D"), 3);
        Set<Long> varijante = p.izbor().values().stream().map(Kandidat::varijantaId).collect(Collectors.toSet());
        Set<Long> tipovi = p.izbor().values().stream().map(Kandidat::tipId).collect(Collectors.toSet());
        assertThat(varijante).hasSize(4);
        assertThat(tipovi).hasSize(1);
        assertThat(p.najmanjiSkup()).isEqualTo(4);
    }

    @Test
    void premaloKandidataGrupeDeleVarijantu() {
        Stavka s = stavka(1, true, Set.of(), new int[]{2, 2});
        var p = plan(s, List.of("A", "B", "C", "D"), 1);
        assertThat(p.izbor()).hasSize(4);
        assertThat(p.najmanjiSkup()).isEqualTo(2);
        Map<Long, Long> poVarijanti = p.izbor().values().stream()
                .collect(Collectors.groupingBy(Kandidat::varijantaId, Collectors.counting()));
        assertThat(poVarijanti.values()).containsOnly(2L);
    }

    @Test
    void pojedinacnoRavnomerno() {
        Stavka s = stavka(1, true, Set.of(), new int[]{2, 2, 2});
        List<Long> studenti = new ArrayList<>();
        for (long i = 1; i <= 17; i++) studenti.add(i);
        var p = plan(s, studenti, 11);
        Map<Long, Long> poVarijanti = p.izbor().values().stream()
                .collect(Collectors.groupingBy(Kandidat::varijantaId, Collectors.counting()));
        long max = Collections.max(poVarijanti.values());
        long min = Collections.min(poVarijanti.values());
        assertThat(poVarijanti).hasSize(3);
        assertThat(max - min).isLessThanOrEqualTo(1);
    }

    @Test
    void uPrimerkuBezPonavljanjaIViseMesta() {
        // jedan tip sa 3 varijante, 3 mesta: svako mesto drugačija varijanta
        Stavka s = stavka(3, true, Set.of(), new int[]{2, 2, 2});
        var p = plan(s, List.of(1L, 2L), 5);
        for (long st : List.of(1L, 2L)) {
            Set<Long> v = new HashSet<>();
            for (int m = 0; m < 3; m++) v.add(p.izbor().get(new Mesto<>(st, m)).varijantaId());
            assertThat(v).hasSize(3);
        }
        assertThat(p.uPrimerku()).isZero();
    }

    @Test
    void tipoviSeSmenjujuPoMestima() {
        Stavka s = stavka(4, true, Set.of(), new int[]{2, 2}, new int[]{2, 2});
        var p = plan(s, List.of("A"), 9);
        assertThat(p.tipovi()).hasSize(4);
        assertThat(p.tipovi().get(0)).isNotEqualTo(p.tipovi().get(1));
        assertThat(p.tipovi().get(0)).isEqualTo(p.tipovi().get(2));
    }

    @Test
    void izbegavaRanijeVarijanteKadImaIzbora() {
        Stavka s = stavka(1, true, Set.of(), new int[]{2, 2, 2});
        Map<Long, Set<Long>> istorija = Map.of(1L, Set.of(100L, 101L));
        var p = IzvlacenjeTesta.planiraj(s, null, 1, List.of(1L), Map.of(), new HashMap<>(),
                (st, v) -> istorija.getOrDefault(st, Set.of()).contains(v) ? 1 : 0, new Random(2));
        assertThat(p.izbor().get(new Mesto<>(1L, 0)).varijantaId()).isEqualTo(102L);
        assertThat(p.ponovljeno()).isZero();
        // bez izbora: ponavljanje se broji
        Map<Long, Set<Long>> sve = Map.of(1L, Set.of(100L, 101L, 102L));
        var p2 = IzvlacenjeTesta.planiraj(s, null, 1, List.of(1L), Map.of(), new HashMap<>(),
                (st, v) -> sve.get(st).contains(v) ? 1 : 0, new Random(2));
        assertThat(p2.ponovljeno()).isEqualTo(1);
    }

    @Test
    void jedanNivoZaStavkuIDovoljnoKandidata() {
        // nivo 2: 1 kandidat, nivo 3: 4 kandidata; za 4 grupe dovoljno je samo ★★★
        Stavka s = stavka(1, true, Set.of(2, 3), new int[]{2, 3, 3, 3, 3, 1, 1, 1, 1});
        for (long seme = 0; seme < 20; seme++) {
            assertThat(IzvlacenjeTesta.izaberiNivo(s, 4, new Random(seme))).isEqualTo(3);
        }
        var p = plan(s, List.of("A", "B", "C", "D"), 4);
        assertThat(p.nivo()).isEqualTo(3);
        assertThat(p.izbor().values()).allMatch(k -> k.nivo() == 3);
        // nijedan nivo nema dovoljno: uzima se onaj sa najviše
        assertThat(IzvlacenjeTesta.izaberiNivo(s, 9, new Random(1))).isEqualTo(3);
        // nema kandidata dozvoljenog nivoa
        Stavka bez = stavka(1, true, Set.of(2), new int[]{1, 3});
        assertThat(IzvlacenjeTesta.izaberiNivo(bez, 1, new Random(1))).isNull();
        assertThat(plan(bez, List.of("A"), 1).izbor()).isEmpty();
    }

    @Test
    void razlicitTipPoJediniciKadNijeIstiTip() {
        Stavka s = stavka(1, false, Set.of(), new int[]{2, 2, 2, 2}, new int[]{2, 2, 2, 2});
        List<Long> studenti = new ArrayList<>();
        for (long i = 1; i <= 30; i++) studenti.add(i);
        var p = plan(s, studenti, 6);
        Set<Long> tipovi = p.izbor().values().stream().map(Kandidat::tipId).collect(Collectors.toSet());
        assertThat(tipovi).hasSize(2);
        assertThat(p.tipovi()).containsOnlyNulls();
    }

    @Test
    void grupeIRaspodelaStudenata() {
        Map<Long, String> g = IzvlacenjeTesta.uGrupe(List.of(1L, 2L, 3L, 4L, 5L), List.of("A", "B"), new Random(1));
        Map<String, Long> velicine = g.values().stream().collect(Collectors.groupingBy(x -> x, Collectors.counting()));
        assertThat(velicine).containsEntry("A", 3L).containsEntry("B", 2L);
        assertThat(IzvlacenjeTesta.najmanjaGrupa(List.of("A", "B"), Map.of("A", 3, "B", 2))).isEqualTo("B");
    }
}
