package tri.novica.gfssystem.service.test;

import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.entity.banka.NacinBodovanja;
import tri.novica.gfssystem.service.banka.OcenaPolja;
import tri.novica.gfssystem.service.uzivo.Normalizacija;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Bodovanje zadatka online testa (spec online testova 6.1-6.2, plan 2). Čiste funkcije, bez baze i bez stanja: ulaz je
 * snimak zadatka (šema polja, tačne vrednosti, pravila bodovanja, poeni stavke), ono što je student uneo i ispravke ključa
 * tog testa za tu varijantu.
 * <ul>
 *   <li>Udeo polja je između 0 i 1: BROJ, TEKST i JEDAN_TACAN po {@link OcenaPolja#tacno} (1 ili 0), VISE_TACNIH
 *       {@code max(0, (tačno izabrani − pogrešno izabrani) / broj tačnih)}, prazno polje 0.</li>
 *   <li>Udeo zadatka: 1 kad {@code konacnoPriznajeKorake} i sva {@code konacno} polja imaju udeo 1; inače kod
 *       {@code SVE_ILI_NISTA} 1 samo kad su sva polja 1; inače {@code Σ poeni·udeo / Σ poeni} (poeni polja, {@code null} = 1).</li>
 *   <li>Poeni = {@code maxPoena × udeo}, zaokruženo na 2 decimale. Zadatak bez polja (stavka „ručno“) nema automatske poene.</li>
 * </ul>
 */
public final class BodovanjeZadatka {

    private static final double EPS = 1e-9;

    private BodovanjeZadatka() {}

    /** Odakle je udeo polja: iz ključa varijante ({@code AUTO}) ili iz ispravke testa ({@code ISPRAVKA}). */
    public enum Izvor { AUTO, ISPRAVKA }

    /** Udeo jednog polja (0-1) i odakle je. */
    public record UdeoPolja(double udeo, Izvor izvor) {
    }

    /**
     * Važeće (nevraćene) ispravke testa za jednu varijantu, po ključu polja: poništena polja, zamenjeni ključ (poslednja
     * KLJUC ispravka) i prihvaćeni ključevi odgovora ({@link #kljucOdgovora}).
     */
    public record Ispravke(Set<String> ponistena, Map<String, OdgovorPolja> kljucevi, Map<String, Set<String>> prihvaceni) {
        public static final Ispravke NEMA = new Ispravke(Set.of(), Map.of(), Map.of());

        public Ispravke {
            ponistena = ponistena == null ? Set.of() : Set.copyOf(ponistena);
            kljucevi = kljucevi == null ? Map.of() : Map.copyOf(kljucevi);
            prihvaceni = prihvaceni == null ? Map.of() : Map.copyOf(prihvaceni);
        }
    }

    /**
     * Ocena zadatka: udeo po polju (redom šeme), udeo zadatka i automatski poeni ({@code null} kad zadatak nema polja).
     */
    public record Ishod(Map<String, UdeoPolja> polja, double udeoZadatka, Double poeni) {
    }

    /** Poeni polja u šemi: {@code null} ili neispravno = 1. */
    public static double poeniPolja(Polje p) {
        return p.poeni() == null || !Double.isFinite(p.poeni()) || p.poeni() <= 0 ? 1.0 : p.poeni();
    }

    /**
     * Deo poena zadatka koji polje nosi (za prikaz studentu uz polje): {@code maxPoena × poeni / Σ poeni}, 2 decimale.
     */
    public static Map<String, Double> poeniPoPolju(List<Polje> polja, double maxPoena) {
        Map<String, Double> rezultat = new LinkedHashMap<>();
        if (polja == null || polja.isEmpty()) return rezultat;
        double zbir = polja.stream().mapToDouble(BodovanjeZadatka::poeniPolja).sum();
        for (Polje p : polja) {
            rezultat.put(p.kljuc(), zaokruzi(maxPoena * poeniPolja(p) / zbir));
        }
        return rezultat;
    }

    /** Ocena zadatka bez ispravki. */
    public static Ishod oceni(List<Polje> polja, Map<String, OdgovorPolja> odgovori, Map<String, ?> uneto,
                              NacinBodovanja bodovanje, boolean konacnoPriznajeKorake, double maxPoena) {
        return oceni(polja, odgovori, uneto, bodovanje, konacnoPriznajeKorake, maxPoena, Ispravke.NEMA);
    }

    /** Ocena zadatka sa ispravkama ključa testa. Nikad ne baca na čudan unos: sve neočekivano je udeo 0. */
    public static Ishod oceni(List<Polje> polja, Map<String, OdgovorPolja> odgovori, Map<String, ?> uneto,
                              NacinBodovanja bodovanje, boolean konacnoPriznajeKorake, double maxPoena, Ispravke ispravke) {
        Ispravke isp = ispravke == null ? Ispravke.NEMA : ispravke;
        Map<String, UdeoPolja> udeli = new LinkedHashMap<>();
        if (polja == null || polja.isEmpty()) {
            return new Ishod(udeli, 0, null);
        }
        Map<String, OdgovorPolja> ocekivano = odgovori == null ? Map.of() : odgovori;
        Map<String, ?> dato = uneto == null ? Map.of() : uneto;
        for (Polje p : polja) {
            udeli.put(p.kljuc(), udeoPolja(p, ocekivano.get(p.kljuc()), dato.get(p.kljuc()), isp));
        }
        double udeo = udeoZadatka(polja, udeli, bodovanje, konacnoPriznajeKorake);
        return new Ishod(udeli, udeo, zaokruzi(maxPoena * udeo));
    }

    private static UdeoPolja udeoPolja(Polje p, OdgovorPolja ocekivano, Object dato, Ispravke isp) {
        String k = p.kljuc();
        if (isp.ponistena().contains(k)) {
            return new UdeoPolja(1, Izvor.ISPRAVKA);
        }
        Set<String> prihvaceni = isp.prihvaceni().get(k);
        if (prihvaceni != null && !prihvaceni.isEmpty()) {
            String kljuc = kljucOdgovora(p, dato);
            if (kljuc != null && prihvaceni.contains(kljuc)) {
                return new UdeoPolja(1, Izvor.ISPRAVKA);
            }
        }
        OdgovorPolja kljucIspravke = isp.kljucevi().get(k);
        if (kljucIspravke != null) {
            return new UdeoPolja(udeo(p, kljucIspravke, dato), Izvor.ISPRAVKA);
        }
        return new UdeoPolja(udeo(p, ocekivano, dato), Izvor.AUTO);
    }

    private static double udeoZadatka(List<Polje> polja, Map<String, UdeoPolja> udeli, NacinBodovanja bodovanje,
                                      boolean konacnoPriznajeKorake) {
        if (konacnoPriznajeKorake) {
            List<Polje> konacna = polja.stream().filter(p -> Boolean.TRUE.equals(p.konacno())).toList();
            if (!konacna.isEmpty() && konacna.stream().allMatch(p -> pun(udeli.get(p.kljuc())))) {
                return 1;
            }
        }
        if (bodovanje == NacinBodovanja.SVE_ILI_NISTA) {
            return polja.stream().allMatch(p -> pun(udeli.get(p.kljuc()))) ? 1 : 0;
        }
        double zbir = 0;
        double ostvareno = 0;
        for (Polje p : polja) {
            double poeni = poeniPolja(p);
            zbir += poeni;
            ostvareno += poeni * udeli.get(p.kljuc()).udeo();
        }
        return zbir <= 0 ? 0 : Math.min(1, Math.max(0, ostvareno / zbir));
    }

    private static boolean pun(UdeoPolja u) {
        return u != null && u.udeo() >= 1 - EPS;
    }

    /**
     * Udeo jednog polja prema ključu {@code ocekivano}: VISE_TACNIH delimično, ostali tipovi 1 ili 0 po {@link OcenaPolja#tacno}.
     */
    public static double udeo(Polje p, OdgovorPolja ocekivano, Object dato) {
        if (p == null || p.tip() == null || ocekivano == null || prazno(dato)) return 0;
        if (p.tip() == tri.novica.gfssystem.dto.banka.TipPolja.VISE_TACNIH) {
            return udeoViseTacnih(ocekivano, dato);
        }
        return Boolean.TRUE.equals(OcenaPolja.tacno(p, ocekivano, dato)) ? 1 : 0;
    }

    private static double udeoViseTacnih(OdgovorPolja ocekivano, Object dato) {
        if (!(dato instanceof List<?> lista) || ocekivano.tacne() == null || ocekivano.tacne().isEmpty()) return 0;
        Set<Long> tacne = new HashSet<>(ocekivano.tacne());
        Set<Long> izabrane = new HashSet<>();
        for (Object o : lista) {
            Long id = ceo(o);
            if (id == null) return 0;
            izabrane.add(id);
        }
        long tacnoIzabrano = izabrane.stream().filter(tacne::contains).count();
        long pogresno = izabrane.size() - tacnoIzabrano;
        return Math.max(0, (double) (tacnoIzabrano - pogresno) / tacne.size());
    }

    /** Prazan odgovor: ništa, prazan (ili samo razmaci) tekst, prazna lista. */
    public static boolean prazno(Object dato) {
        if (dato == null) return true;
        if (dato instanceof String s) return s.isBlank();
        if (dato instanceof Collection<?> c) return c.isEmpty();
        return false;
    }

    /**
     * Ključ odgovora za grupisanje u pregledu po odgovorima i za „Prihvati i ovaj odgovor“ (isti ključ kao u oceni):
     * TEKST {@link OcenaPolja#kljucOcene}, BROJ normalizovan broj ({@code 0,50} i {@code .5} su {@code 0.5}; tekst koji
     * nije broj dobija prefiks {@code #}), JEDAN_TACAN id opcije, VISE_TACNIH sortirani id-jevi spojeni zarezom.
     * Prazan odgovor je {@code null}.
     */
    public static String kljucOdgovora(Polje p, Object dato) {
        if (p == null || p.tip() == null || prazno(dato)) return null;
        return switch (p.tip()) {
            case TEKST -> dato instanceof String s ? emptyNull(OcenaPolja.kljucOcene(p, s)) : null;
            case BROJ -> {
                if (!(dato instanceof String s)) yield null;
                Double broj = Normalizacija.broj(s);
                if (broj != null && Double.isFinite(broj)) {
                    yield BigDecimal.valueOf(broj).stripTrailingZeros().toPlainString();
                }
                String t = OcenaPolja.kljucTeksta(p, s);
                yield t.isEmpty() ? null : "#" + t;
            }
            case JEDAN_TACAN -> {
                Long id = ceo(dato);
                yield id == null ? null : id.toString();
            }
            case VISE_TACNIH -> {
                if (!(dato instanceof List<?> lista)) yield null;
                TreeSet<Long> ids = new TreeSet<>();
                for (Object o : lista) {
                    Long id = ceo(o);
                    if (id == null) yield null;
                    ids.add(id);
                }
                yield ids.isEmpty() ? null : String.join(",", ids.stream().map(String::valueOf).toList());
            }
        };
    }

    private static String emptyNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static Long ceo(Object o) {
        if (o instanceof Long l) return l;
        if (o instanceof Number n) {
            double d = n.doubleValue();
            return d == Math.rint(d) && Math.abs(d) <= 9_007_199_254_740_992.0 ? n.longValue() : null;
        }
        return null;
    }

    /** Zaokruživanje na 2 decimale (HALF_UP). */
    public static double zaokruzi(double x) {
        if (!Double.isFinite(x)) return 0;
        return BigDecimal.valueOf(x).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
