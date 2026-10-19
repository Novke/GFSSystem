package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

/** Čiste funkcije izvlačenja i raspodele varijanti ({@link IzborVarijanti}), bez Spring-a i baze. */
class IzborVarijantiTest {

    /** Lista veličine {@code n} sa elementima {@code prefiks1..prefiksN}: prefiks govori iz kog je lista element. */
    static List<String> list(String prefiks, int n) {
        return IntStream.rangeClosed(1, n).mapToObj(i -> prefiks + i).toList();
    }

    static Map<String, Long> poListovima(List<String> izvuceno) {
        return izvuceno.stream().collect(Collectors.groupingBy(s -> s.substring(0, 1), TreeMap::new, Collectors.counting()));
    }

    @Test
    void naizmenicnoPoListovimaJednakiListovi() {
        for (int seed = 0; seed < 20; seed++) {
            List<String> izvuceno = IzborVarijanti.izvuci(List.of(list("a", 6), list("b", 4)), 4, new Random(seed));
            assertEquals(4, izvuceno.size());
            assertEquals(Map.of("a", 2L, "b", 2L), poListovima(izvuceno), "seed " + seed);
        }
    }

    @Test
    void malLiListDajeSvojuJednuAOstaloIzVelikog() {
        for (int seed = 0; seed < 20; seed++) {
            List<String> izvuceno = IzborVarijanti.izvuci(List.of(list("a", 1), list("b", 5)), 4, new Random(seed));
            assertEquals(Map.of("a", 1L, "b", 3L), poListovima(izvuceno), "seed " + seed);
        }
    }

    @Test
    void manjeOdListovaDajeRazliciteListove() {
        for (int seed = 0; seed < 20; seed++) {
            List<String> izvuceno = IzborVarijanti.izvuci(List.of(list("a", 3), list("b", 3), list("c", 3)), 2, new Random(seed));
            assertEquals(2, izvuceno.size());
            assertEquals(2, poListovima(izvuceno).size(), "dva različita lista, seed " + seed);
        }
    }

    @Test
    void viseOdUkupnogDajeSveBezPonavljanja() {
        List<String> izvuceno = IzborVarijanti.izvuci(List.of(list("a", 6), list("b", 4)), 20, new Random(1));
        assertEquals(10, izvuceno.size());
        assertEquals(10, new HashSet<>(izvuceno).size());
    }

    @Test
    void nullJeSve() {
        List<String> izvuceno = IzborVarijanti.izvuci(List.of(list("a", 2), list("b", 3)), null, new Random(1));
        assertEquals(Set.of("a1", "a2", "b1", "b2", "b3"), new HashSet<>(izvuceno));
        assertEquals(5, izvuceno.size());
    }

    @Test
    void prazanUlazIDobrojNulaNistaNeIzvlace() {
        assertEquals(List.of(), IzborVarijanti.izvuci(List.<List<String>>of(), 3, new Random(1)));
        assertEquals(List.of(), IzborVarijanti.izvuci(List.of(list("a", 3)), 0, new Random(1)));
        assertEquals(List.of(), IzborVarijanti.izvuci(List.<List<String>>of(), null, new Random(1)));
    }

    @Test
    void ulazSeNeMenja() {
        List<List<String>> ulaz = List.of(list("a", 3), list("b", 3));
        IzborVarijanti.izvuci(ulaz, 4, new Random(5));
        assertEquals(List.of(list("a", 3), list("b", 3)), ulaz);
    }

    @Test
    void isteSeedDajeIstiIzlazARazliciteSeedoveRazlicitRedosled() {
        List<List<String>> ulaz = List.of(list("a", 6), list("b", 6), list("c", 6));
        assertEquals(IzborVarijanti.izvuci(ulaz, 8, new Random(7)), IzborVarijanti.izvuci(ulaz, 8, new Random(7)));

        List<String> prvi = IzborVarijanti.izvuci(ulaz, 8, new Random(0));
        long razliciti = IntStream.range(1, 21).filter(seed -> !prvi.equals(IzborVarijanti.izvuci(ulaz, 8, new Random(seed)))).count();
        assertTrue(razliciti > 0, "dva seed-a daju različit redosled bar jednom u 20 pokušaja");
    }

    @Test
    void rasporedUcesnikaRavnomernoDeli() {
        List<Long> ucesnici = ucesnici30();
        List<String> varijante = list("v", 4);
        for (int seed = 0; seed < 20; seed++) {
            Map<Long, String> raspored = IzborVarijanti.rasporedi(ucesnici, varijante, new Random(seed));
            assertEquals(new HashSet<>(ucesnici), raspored.keySet(), "svaki učesnik tačno jednom");
            List<Long> brojevi = varijante.stream()
                    .map(v -> raspored.values().stream().filter(v::equals).count()).sorted(Comparator.reverseOrder()).toList();
            assertEquals(List.of(8L, 8L, 7L, 7L), brojevi, "seed " + seed);
        }
    }

    @Test
    void manjeUcesnikaOdVarijantiDajeRazliciteVarijante() {
        for (int seed = 0; seed < 20; seed++) {
            Map<Long, String> raspored = IzborVarijanti.rasporedi(List.of(11L, 12L, 13L), list("v", 4), new Random(seed));
            assertEquals(3, raspored.size());
            assertEquals(3, new HashSet<>(raspored.values()).size(), "tri različite, seed " + seed);
        }
    }

    @Test
    void rasporedNeZavisiOdRedosledaUlaza() {
        List<String> varijante = list("v", 4);
        List<Long> rastuce = ucesnici30();
        List<Long> obrnuto = new ArrayList<>(rastuce);
        Collections.reverse(obrnuto);
        assertEquals(IzborVarijanti.rasporedi(rastuce, varijante, new Random(3)),
                IzborVarijanti.rasporedi(obrnuto, varijante, new Random(3)));
    }

    @Test
    void rasporedBezUcesnikaJePrazanABezVarijantiGreska() {
        assertTrue(IzborVarijanti.rasporedi(List.of(), list("v", 4), new Random(1)).isEmpty());
        assertTrue(IzborVarijanti.rasporedi(List.of(), List.<String>of(), new Random(1)).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> IzborVarijanti.rasporedi(List.of(1L), List.<String>of(), new Random(1)));
    }

    @Test
    void najmanjeDodeljenaNikadNajvecaIBiraMedjuIzjednacenima() {
        Map<String, Integer> dodele = Map.of("A", 2, "B", 1, "C", 1);
        Set<String> vidjene = new HashSet<>();
        Random r = new Random(42); // jedan generator: prvi nextInt uzastopnih seed-ova je skoro isti
        for (int i = 0; i < 50; i++) {
            String izabrana = IzborVarijanti.najmanjeDodeljena(List.of("A", "B", "C"), dodele, r);
            assertNotEquals("A", izabrana);
            vidjene.add(izabrana);
        }
        assertEquals(Set.of("B", "C"), vidjene, "nasumično među izjednačenima");
    }

    @Test
    void najmanjeDodeljenaBezUnosaJeNula() {
        for (int seed = 0; seed < 20; seed++) {
            assertEquals("C", IzborVarijanti.najmanjeDodeljena(List.of("A", "B", "C"), Map.of("A", 1, "B", 3), new Random(seed)));
        }
        assertThrows(IllegalArgumentException.class, () -> IzborVarijanti.najmanjeDodeljena(List.<String>of(), Map.of(), new Random(1)));
    }

    private static List<Long> ucesnici30() {
        return LongStream.rangeClosed(101, 130).boxed().toList();
    }
}
