package tri.novica.gfssystem.service.uzivo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Izvlačenje i raspodela varijanti zadatka na času (spec MCP 11.2, banka spec 4.2): čiste funkcije nad {@link Random}
 * iz bean-a ({@code GfsSystemApplication.random()}), pa test sa seed-om daje isti ishod. Ništa ne čita iz baze i ne drži stanje.
 */
public final class IzborVarijanti {

    private IzborVarijanti() {
    }

    /**
     * Izvlači {@code min(n, ukupno)} varijanti bez ponavljanja ({@code n == null} = sve): prvo po jedna iz svakog lista
     * (nasumičnim redom listova), pa dopuna u novim krugovima, uvek nasumično unutar lista, da različiti tipovi zadataka
     * stvarno stignu do studenata. {@code poListovima} se ne menja.
     */
    public static <T> List<T> izvuci(List<List<T>> poListovima, Integer n, Random r) {
        List<List<T>> listovi = new ArrayList<>();
        for (List<T> l : poListovima) {
            List<T> k = new ArrayList<>(l);
            Collections.shuffle(k, r);
            listovi.add(k);
        }
        Collections.shuffle(listovi, r);
        int ukupno = listovi.stream().mapToInt(List::size).sum();
        int cilj = n == null ? ukupno : Math.min(n, ukupno);
        List<T> izvuceno = new ArrayList<>(Math.max(cilj, 0));
        for (int krug = 0; izvuceno.size() < cilj; krug++) {
            for (List<T> l : listovi) {
                if (krug < l.size() && izvuceno.size() < cilj) {
                    izvuceno.add(l.get(krug));
                }
            }
        }
        return izvuceno;
    }

    /**
     * Deli učesnike ravnomerno među varijante (broj učesnika po varijanti se razlikuje najviše za 1, svaki učesnik
     * dobija tačno jednu): id-jevi rastuće (da seed daje isti ishod bez obzira na ulazni redosled), pa nasumično
     * pomešani, pa kružno od nasumične varijante. Bez varijanti nema šta da se dodeli ({@link IllegalArgumentException}).
     */
    public static <T> Map<Long, T> rasporedi(List<Long> ucesnici, List<T> varijante, Random r) {
        Map<Long, T> raspored = new LinkedHashMap<>();
        if (ucesnici.isEmpty()) {
            return raspored;
        }
        if (varijante.isEmpty()) {
            throw new IllegalArgumentException("Nema varijanti za raspodelu.");
        }
        List<Long> redom = new ArrayList<>(ucesnici);
        Collections.sort(redom);
        Collections.shuffle(redom, r);
        int k = varijante.size();
        int pocetak = r.nextInt(k);
        for (int i = 0; i < redom.size(); i++) {
            raspored.put(redom.get(i), varijante.get((pocetak + i) % k));
        }
        return raspored;
    }

    /**
     * Varijanta sa najmanje dodela ({@code brojDodela} bez unosa = 0), nasumično među izjednačenima: učesnik koji uđe
     * posle otvaranja runde ne kvari ravnomernost. Bez varijanti {@link IllegalArgumentException}.
     */
    public static <T> T najmanjeDodeljena(List<T> varijante, Map<T, Integer> brojDodela, Random r) {
        if (varijante.isEmpty()) {
            throw new IllegalArgumentException("Nema varijanti za dodelu.");
        }
        int najmanje = Integer.MAX_VALUE;
        List<T> izjednacene = new ArrayList<>();
        for (T v : varijante) {
            int broj = brojDodela.getOrDefault(v, 0);
            if (broj < najmanje) {
                najmanje = broj;
                izjednacene.clear();
            }
            if (broj == najmanje) {
                izjednacene.add(v);
            }
        }
        return izjednacene.get(r.nextInt(izjednacene.size()));
    }
}
