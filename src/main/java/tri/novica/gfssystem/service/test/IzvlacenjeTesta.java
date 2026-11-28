package tri.novica.gfssystem.service.test;

import tri.novica.gfssystem.utility.Nivo;

import java.util.*;
import java.util.function.Function;
import java.util.function.ToIntBiFunction;

/**
 * Izvlačenje zadataka online testa (spec 7 i 8.2, plan 3). Čiste funkcije bez baze: slučajnost samo iz {@link Random}-a
 * koji pozivalac napravi iz semena testa, pa isti ulaz daje isti izbor.
 * <p>
 * Jedinica raspodele {@code J} je grupa testa (režim GRUPE) ili student (POJEDINACNO). Za jednu stavku:
 * <ol>
 *   <li>{@link #izaberiNivo}: jedan nivo za celu stavku i ceo test, među dozvoljenim nivoima koji imaju dovoljno kandidata
 *       (ako takvih nema, nivo sa najviše kandidata);</li>
 *   <li>{@link #tipoviPoMestima}: kad je {@code istiTip}, svako mesto dobija svoj tip, tipovi se smenjuju redom stabla od
 *       slučajnog početka;</li>
 *   <li>{@link #rasporedi}: po mestu, svaka jedinica dobija varijantu: nikad ona koja je već u istom primerku (osim kad
 *       drugačije ne može), pa najmanje dodeljena (ravnomerno, razlika najviše 1), pa sa najmanjom kaznom (varijanta sa
 *       ranijeg testa), pa slučajno.</li>
 * </ol>
 */
public final class IzvlacenjeTesta {

    private IzvlacenjeTesta() {}

    /** Kandidat: AKTIVNA varijanta sa SVG-om, njen tip (list banke) i nivo. */
    public record Kandidat(long varijantaId, long tipId, int nivo) {
    }

    /**
     * Stavka za izvlačenje: {@code poTipu} su kandidati po tipu (listu) redom stabla, svih nivoa; {@code nivoi} dozvoljeni
     * nivoi (prazno = svi).
     */
    public record Stavka(long stavkaId, String naziv, int broj, boolean istiTip, Set<Integer> nivoi,
                         LinkedHashMap<Long, List<Kandidat>> poTipu) {
    }

    /** Mesto u stavci (0..broj-1) za jednu jedinicu. */
    public record Mesto<J>(J jedinica, int mesto) {
    }

    /** Rezultat raspodele jednog mesta: izbor po jedinici, koliko je dobilo varijantu sa kaznom i koliko ponavljanje u primerku. */
    public record Dodela<J>(Map<J, Kandidat> izbor, int ponovljeno, int uPrimerku) {
    }

    /** Dozvoljeni nivoi stavke, rastuće. */
    public static List<Integer> dozvoljeni(Set<Integer> nivoi) {
        if (nivoi == null || nivoi.isEmpty()) {
            List<Integer> svi = new ArrayList<>();
            for (int n = Nivo.MIN; n <= Nivo.MAX; n++) svi.add(n);
            return svi;
        }
        return new TreeSet<>(nivoi).stream().filter(n -> n >= Nivo.MIN && n <= Nivo.MAX).toList();
    }

    /** Broj kandidata stavke po nivou (indeks = nivo - 1). */
    public static int[] poNivou(Stavka s) {
        int[] broj = new int[Nivo.MAX];
        for (List<Kandidat> l : s.poTipu().values()) {
            for (Kandidat k : l) {
                if (k.nivo() >= Nivo.MIN && k.nivo() <= Nivo.MAX) broj[k.nivo() - 1]++;
            }
        }
        return broj;
    }

    /**
     * Jedan nivo za stavku: slučajno među dozvoljenim nivoima sa bar {@code potrebno} kandidata; kad takvih nema, dozvoljeni
     * nivo sa najviše kandidata; {@code null} kad stavka nema nijednog kandidata dozvoljenog nivoa.
     */
    public static Integer izaberiNivo(Stavka s, int potrebno, Random r) {
        int[] broj = poNivou(s);
        List<Integer> dozvoljeni = dozvoljeni(s.nivoi());
        List<Integer> dovoljni = dozvoljeni.stream().filter(n -> broj[n - 1] >= Math.max(1, potrebno)).toList();
        if (!dovoljni.isEmpty()) {
            return dovoljni.get(r.nextInt(dovoljni.size()));
        }
        Integer najbolji = null;
        for (Integer n : dozvoljeni) {
            if (broj[n - 1] > 0 && (najbolji == null || broj[n - 1] > broj[najbolji - 1])) najbolji = n;
        }
        return najbolji;
    }

    /** Kandidati stavke datog nivoa po tipu, redom stabla, bez tipova bez kandidata. */
    public static LinkedHashMap<Long, List<Kandidat>> zaNivo(Stavka s, int nivo) {
        LinkedHashMap<Long, List<Kandidat>> rezultat = new LinkedHashMap<>();
        s.poTipu().forEach((tip, lista) -> {
            List<Kandidat> l = lista.stream().filter(k -> k.nivo() == nivo).toList();
            if (!l.isEmpty()) rezultat.put(tip, l);
        });
        return rezultat;
    }

    /** Tip po mestu: tipovi se smenjuju redom stabla od slučajnog početka; prazno kad tipova nema. */
    public static List<Long> tipoviPoMestima(List<Long> tipovi, int broj, Random r) {
        if (tipovi.isEmpty()) return List.of();
        int pocetak = r.nextInt(tipovi.size());
        List<Long> rezultat = new ArrayList<>(broj);
        for (int m = 0; m < broj; m++) rezultat.add(tipovi.get((pocetak + m) % tipovi.size()));
        return rezultat;
    }

    /**
     * Raspodela jednog mesta: jedinice se obilaze slučajnim redom; svaka dobija varijantu iz svog skupa ({@code pool}) po
     * pravilima iz opisa klase. {@code brojac} (varijanta -> broj dodela na ovom mestu) se menja: pozivalac u njega upiše
     * dodele koje ostaju (delimično ponovno izvlačenje), pa nove idu na najmanje korišćene. {@code zauzeto}: varijante
     * koje su već u primerku jedinice. Jedinica sa praznim skupom ne dobija ništa.
     */
    public static <J> Dodela<J> rasporedi(List<J> jedinice, Function<J, List<Kandidat>> pool, Map<Long, Integer> brojac,
                                          Function<J, Set<Long>> zauzeto, ToIntBiFunction<J, Long> kazna, Random r) {
        List<J> red = new ArrayList<>(jedinice);
        Collections.shuffle(red, r);
        Map<J, Kandidat> izbor = new LinkedHashMap<>();
        int ponovljeno = 0;
        int uPrimerku = 0;
        for (J j : red) {
            List<Kandidat> skup = pool.apply(j);
            if (skup == null || skup.isEmpty()) continue;
            Set<Long> zauzete = zauzeto.apply(j);
            List<Kandidat> slobodni = skup.stream().filter(k -> zauzete == null || !zauzete.contains(k.varijantaId())).toList();
            boolean ponavljanje = slobodni.isEmpty();
            List<Kandidat> moguci = ponavljanje ? skup : slobodni;
            int najmanjeDodela = moguci.stream().mapToInt(k -> brojac.getOrDefault(k.varijantaId(), 0)).min().orElse(0);
            List<Kandidat> najmanje = moguci.stream()
                    .filter(k -> brojac.getOrDefault(k.varijantaId(), 0) == najmanjeDodela).toList();
            int najmanjaKazna = najmanje.stream().mapToInt(k -> kazna.applyAsInt(j, k.varijantaId())).min().orElse(0);
            List<Kandidat> najbolji = najmanje.stream()
                    .filter(k -> kazna.applyAsInt(j, k.varijantaId()) == najmanjaKazna).toList();
            Kandidat k = najbolji.get(r.nextInt(najbolji.size()));
            izbor.put(j, k);
            brojac.merge(k.varijantaId(), 1, Integer::sum);
            if (najmanjaKazna > 0) ponovljeno++;
            if (ponavljanje) uPrimerku++;
        }
        return new Dodela<>(izbor, ponovljeno, uPrimerku);
    }

    /**
     * Ceo plan jedne stavke za zadate jedinice: nivo (dat ili izvučen), tip po mestu i varijanta po (jedinica, mesto).
     * {@code zauzeto} se dopunjuje izabranim varijantama (sledeća stavka ih ne ponavlja u istom primerku).
     * {@code tipFiksan}: mesto -> tip koji je nastavnik izabrao („Izaberi tip“); {@code potrebnoZaNivo}: koliko kandidata
     * treba da bi nivo bio „dovoljan“.
     */
    public static <J> PlanStavke<J> planiraj(Stavka s, Integer nivo, int potrebnoZaNivo, List<J> jedinice,
                                             Map<Integer, Long> tipFiksan, Map<J, Set<Long>> zauzeto,
                                             ToIntBiFunction<J, Long> kazna, Random r) {
        Integer n = nivo != null ? nivo : izaberiNivo(s, potrebnoZaNivo, r);
        Map<Mesto<J>, Kandidat> izbor = new LinkedHashMap<>();
        List<Long> tipovi = new ArrayList<>();
        int ponovljeno = 0;
        int uPrimerku = 0;
        int najmanjiSkup = Integer.MAX_VALUE;
        if (n == null) {
            return new PlanStavke<>(null, List.of(), izbor, 0, 0, 0);
        }
        LinkedHashMap<Long, List<Kandidat>> poTipu = zaNivo(s, n);
        List<Long> redTipova = new ArrayList<>(poTipu.keySet());
        List<Long> poMestima = s.istiTip() ? tipoviPoMestima(redTipova, s.broj(), r) : List.of();
        for (int m = 0; m < s.broj(); m++) {
            Long tip = tipFiksan != null && tipFiksan.containsKey(m) ? tipFiksan.get(m)
                    : (poMestima.isEmpty() ? null : poMestima.get(m));
            tipovi.add(tip);
            Map<J, List<Kandidat>> skupovi = new HashMap<>();
            for (J j : jedinice) {
                List<Kandidat> skup;
                if (tip != null) {
                    skup = poTipu.getOrDefault(tip, List.of());
                } else if (redTipova.isEmpty()) {
                    skup = List.of();
                } else {
                    Set<Long> zauzete = zauzeto.getOrDefault(j, Set.of());
                    List<Long> saSlobodnim = redTipova.stream()
                            .filter(t -> poTipu.get(t).stream().anyMatch(k -> !zauzete.contains(k.varijantaId()))).toList();
                    List<Long> izbori = saSlobodnim.isEmpty() ? redTipova : saSlobodnim;
                    skup = poTipu.get(izbori.get(r.nextInt(izbori.size())));
                }
                skupovi.put(j, skup);
                najmanjiSkup = Math.min(najmanjiSkup, skup.size());
            }
            final int mesto = m;
            Dodela<J> d = rasporedi(jedinice, skupovi::get, new HashMap<>(),
                    j -> zauzeto.getOrDefault(j, Set.of()), kazna, r);
            ponovljeno += d.ponovljeno();
            uPrimerku += d.uPrimerku();
            for (J j : jedinice) {
                Kandidat k = d.izbor().get(j);
                if (k != null) {
                    izbor.put(new Mesto<>(j, mesto), k);
                    zauzeto.computeIfAbsent(j, x -> new HashSet<>()).add(k.varijantaId());
                }
            }
        }
        return new PlanStavke<>(n, tipovi, izbor, ponovljeno, uPrimerku,
                najmanjiSkup == Integer.MAX_VALUE ? 0 : najmanjiSkup);
    }

    /**
     * Plan stavke: nivo ({@code null} = nema kandidata), tip po mestu ({@code null} kad tip nije isti za sve), izbor po
     * (jedinica, mesto), broj ponavljanja sa ranijih testova, broj ponavljanja u primerku i najmanji skup kandidata
     * jednog mesta (za upozorenje „grupe dele varijantu“).
     */
    public record PlanStavke<J>(Integer nivo, List<Long> tipovi, Map<Mesto<J>, Kandidat> izbor, int ponovljeno,
                                int uPrimerku, int najmanjiSkup) {
    }

    /** Studenti u grupe testa: slučajan red, pa redom A, B, C, D (veličine se razlikuju najviše za 1). */
    public static <G> Map<Long, G> uGrupe(List<Long> studenti, List<G> grupe, Random r) {
        List<Long> red = new ArrayList<>(studenti);
        Collections.shuffle(red, r);
        Map<Long, G> rezultat = new LinkedHashMap<>();
        for (int i = 0; i < red.size(); i++) rezultat.put(red.get(i), grupe.get(i % grupe.size()));
        return rezultat;
    }

    /** Grupa za studenta dodatog naknadno: najmanja grupa (prva po redu kad su jednake). */
    public static <G> G najmanjaGrupa(List<G> grupe, Map<G, Integer> velicine) {
        G najmanja = null;
        for (G g : grupe) {
            if (najmanja == null || velicine.getOrDefault(g, 0) < velicine.getOrDefault(najmanja, 0)) najmanja = g;
        }
        return najmanja;
    }
}
