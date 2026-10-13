package tri.novica.gfssystem.validation;

import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.dto.uzivo.platno.*;
import tri.novica.gfssystem.exceptions.SystemException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Normalizacija i pravila za dokument platna (spec 4.3, ugovor JSON-a u planu). Ne dira bazu: postojanje slika proverava
 * servis ({@code PrezentacijaService}, preko {@link #medijIds}). Redosled upotrebe: {@code proveri(normalizuj(platno))}. {@code normalizuj} popunjava
 * podrazumevane vrednosti, zaokružuje brojke na 2 decimale i boje piše velikim slovima, pa server uvek vraća sva polja.
 */
public final class PlatnoPP {

    public static final Set<String> FONTOVI = Set.of("Carlito", "Caladea", "Arimo", "Tinos", "Cousine",
            "IBM Plex Sans", "IBM Plex Mono", "Atkinson Hyperlegible");
    public static final int MAX_BAJTOVA = 1_048_576;

    static final int MAX_SLOJEVA = 300;
    static final int MAX_ANIMACIJA = 400;
    static final int MAX_ZNAKOVA = 50_000;
    static final int MAX_TACAKA = 20_000;
    static final int MAX_TABELA = 30;
    static final int MAX_TABULATORA = 32;

    private static final Pattern BOJA = Pattern.compile("^#[0-9A-F]{6}$");
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{1,12}$");

    private PlatnoPP() {}

    // ------------------------------------------------------------------ normalizacija

    /** Kopija sa podrazumevanim vrednostima; {@code null} za {@code null}. Nedostajuće liste su prazne (osim {@code potezi} crteža: {@code null} je crtež bez poteza). */
    public static Platno normalizuj(Platno p) {
        if (p == null) return null;
        Pozadina pozadina = p.pozadina() == null ? null
                : new Pozadina(boja(p.pozadina().boja()), p.pozadina().medijId());
        return new Platno(p.verzija(), pozadina, lista(p.slojevi(), PlatnoPP::sloj),
                lista(p.animacije(), PlatnoPP::animacija));
    }

    private static Sloj sloj(Sloj s) {
        if (s == null) return null;
        String naziv = s.naziv();
        Double x = r(s.x());
        Double y = r(s.y());
        Double sirina = r(s.sirina());
        Double visina = r(s.visina());
        Double rotacija = ili(r(s.rotacija()), 0.0);
        Double providnost = ili(r(s.providnost()), 1.0);
        Boolean zakljucan = ili(s.zakljucan(), false);
        Senka senka = senka(s.senka());
        return switch (s) {
            case TekstSloj t -> new TekstSloj(t.id(), naziv, x, y, sirina, visina, rotacija, providnost, zakljucan, senka,
                    lista(t.pasusi(), PlatnoPP::pasus), okvir(t.okvir()), boja(t.popuna()), ivica(t.ivica()));
            case SlikaSloj i -> new SlikaSloj(i.id(), naziv, x, y, sirina, visina, rotacija, providnost, zakljucan, senka,
                    i.medijId(), isecak(i.isecak()));
            case OblikSloj o -> oblik(o, naziv, x, y, sirina, visina, rotacija, providnost, zakljucan, senka);
            case TabelaSloj t -> new TabelaSloj(t.id(), naziv, x, y, sirina, visina, rotacija, providnost, zakljucan, senka,
                    lista(t.kolone(), PlatnoPP::r), lista(t.redovi(), PlatnoPP::r),
                    lista(t.celije(), red -> lista(red, PlatnoPP::celija)));
            case FormulaSloj f -> new FormulaSloj(f.id(), naziv, x, y, sirina, visina, rotacija, providnost, zakljucan, senka,
                    f.latex(), boja(f.boja()), r(f.velicina()), ili(f.poravnanje(), Poravnanje.LEVO), f.slikaIzvora());
            case CrtezSloj c -> new CrtezSloj(c.id(), naziv, x, y, sirina, visina, rotacija, providnost, zakljucan, senka,
                    c.medijId(), c.potezi() == null ? null : lista(c.potezi(), PlatnoPP::potez));
        };
    }

    private static Sloj oblik(OblikSloj o, String naziv, Double x, Double y, Double sirina, Double visina, Double rotacija,
                              Double providnost, Boolean zakljucan, Senka senka) {
        Tacka od = null;
        Tacka doTacke = null;
        if (jeLinija(o.oblik())) {
            od = tacka(o.od());
            doTacke = tacka(o.doTacke());
            if (od == null && visina != null) od = new Tacka(0.0, r(visina / 2));
            if (doTacke == null && sirina != null && visina != null) doTacke = new Tacka(sirina, r(visina / 2));
        }
        return new OblikSloj(o.id(), naziv, x, y, sirina, visina, rotacija, providnost, zakljucan, senka,
                o.oblik(), boja(o.popuna()), ivica(o.ivica()), od, doTacke);
    }

    private static boolean jeLinija(VrstaOblika v) {
        return v == VrstaOblika.LINIJA || v == VrstaOblika.STRELICA || v == VrstaOblika.DVOSTRUKA_STRELICA;
    }

    private static Tacka tacka(Tacka t) {
        return t == null ? null : new Tacka(r(t.x()), r(t.y()));
    }

    private static Senka senka(Senka s) {
        return s == null ? null : new Senka(boja(s.boja()), r(s.pomerajX()), r(s.pomerajY()), r(s.zamucenje()));
    }

    private static Ivica ivica(Ivica i) {
        return i == null ? null : new Ivica(boja(i.boja()), r(i.debljina()), ili(i.crtica(), Crtica.PUNA));
    }

    private static Isecak isecak(Isecak i) {
        if (i == null) return new Isecak(0.0, 0.0, 0.0, 0.0);
        return new Isecak(ili(r(i.levo()), 0.0), ili(r(i.gore()), 0.0), ili(r(i.desno()), 0.0), ili(r(i.dole()), 0.0));
    }

    private static Okvir okvir(Okvir o) {
        if (o == null) return new Okvir(7.2, 3.6, 7.2, 3.6, Sidro.VRH, true, 1.0, 1.0);
        return new Okvir(ili(r(o.unutraLevo()), 7.2), ili(r(o.unutraGore()), 3.6), ili(r(o.unutraDesno()), 7.2),
                ili(r(o.unutraDole()), 3.6), ili(o.sidro(), Sidro.VRH), ili(o.prelom(), true),
                ili(r(o.skala()), 1.0), ili(r(o.skalaProreda()), 1.0));
    }

    private static Pasus pasus(Pasus p) {
        if (p == null) return null;
        Uvlacenje u = p.uvlacenje();
        Uvlacenje uvlacenje = u == null ? new Uvlacenje(0.0, 0.0)
                : new Uvlacenje(ili(r(u.levo()), 0.0), ili(r(u.prviRed()), 0.0));
        return new Pasus(ili(p.poravnanje(), Poravnanje.LEVO), ili(p.nivo(), 0), lista(p.lista()), uvlacenje,
                ili(r(p.razmakPre()), 0.0), ili(r(p.razmakPosle()), 0.0), ili(r(p.proredProcenat()), 100.0),
                lista(p.tabulatori(), t -> t == null ? null : new Tabulator(r(t.poz()), t.poravnanje())),
                lista(p.delovi(), PlatnoPP::deo));
    }

    private static Lista lista(Lista l) {
        if (l == null) return null;
        if (l.vrsta() == VrstaListe.BROJ) {
            return new Lista(l.vrsta(), null, boja(l.boja()), l.format(), ili(l.pocetak(), 1));
        }
        return new Lista(l.vrsta(), l.znak(), boja(l.boja()), null, null);
    }

    private static Deo deo(Deo d) {
        if (d == null) return null;
        return new Deo(ili(d.tekst(), ""), ili(d.font(), "Carlito"), ili(r(d.velicina()), 18.0),
                ili(boja(d.boja()), "#000000"), ili(d.podebljano(), false), ili(d.kurziv(), false),
                ili(d.podvuceno(), false), ili(d.precrtano(), false), ili(d.polozaj(), Polozaj.OBICAN));
    }

    private static Celija celija(Celija c) {
        if (c == null) return null;
        IviceCelije iv = c.ivice();
        IviceCelije ivice = iv == null ? new IviceCelije(null, null, null, null)
                : new IviceCelije(ivica(iv.gore()), ivica(iv.dole()), ivica(iv.levo()), ivica(iv.desno()));
        return new Celija(lista(c.pasusi(), PlatnoPP::pasus), boja(c.popuna()), ivice, ili(c.spojDesno(), 0),
                ili(c.spojDole(), 0), ili(c.pokrivena(), false), ili(c.sidro(), Sidro.VRH));
    }

    private static Potez potez(Potez p) {
        if (p == null) return null;
        return new Potez(boja(p.boja()), r(p.debljina()), lista(p.tacke(), PlatnoPP::r));
    }

    private static Animacija animacija(Animacija a) {
        if (a == null) return null;
        Efekat e = a.efekat();
        Smer smer = null;
        if (e == Efekat.ULET || e == Efekat.BRISANJE || e == Efekat.ISCRTAJ || e == Efekat.PODELI) {
            smer = a.smer() != null ? a.smer() : switch (e) {
                case ULET -> Smer.DOLE;
                case PODELI -> Smer.VERTIKALNO;
                default -> Smer.LEVO;
            };
        }
        Integer trajanje = e == Efekat.POJAVI ? Integer.valueOf(0) : ili(a.trajanjeMs(), 500);
        return new Animacija(a.id(), a.slojId(), a.pasus(), a.vrsta(), e, smer, a.okidac(), trajanje,
                ili(a.kasnjenjeMs(), 0));
    }

    // ------------------------------------------------------------------ pravila

    /** Baca 400 sa porukom prvog prekršenog pravila. Očekuje normalizovan dokument. */
    public static void proveri(Platno p) {
        if (p == null) throw greska("Platno slajd mora imati dokument platna.");
        if (!Integer.valueOf(1).equals(p.verzija())) throw greska("Nepodržana verzija platna.");
        if (p.pozadina() == null) throw greska("Boja nije ispravna (#RRGGBB).");
        proveriBoju(p.pozadina().boja());
        List<Sloj> slojevi = ili(p.slojevi(), List.of());
        List<Animacija> animacije = ili(p.animacije(), List.of());
        if (slojevi.size() > MAX_SLOJEVA) throw greska("Slajd može imati najviše 300 slojeva.");
        if (animacije.size() > MAX_ANIMACIJA) throw greska("Slajd može imati najviše 400 animacija.");

        Set<String> idjevi = new HashSet<>();
        long[] znakova = {0};
        for (Sloj s : slojevi) {
            if (s == null) throw greska(NEISPRAVAN_ID);
            proveriId(s.id(), idjevi);
            proveriSloj(s, znakova);
        }
        for (Animacija a : animacije) {
            if (a == null) throw greska(NEISPRAVAN_ID);
            proveriId(a.id(), idjevi);
        }
        if (znakova[0] > MAX_ZNAKOVA) throw greska("Slajd može imati najviše 50000 znakova teksta.");
        for (Animacija a : animacije) {
            proveriAnimaciju(a, slojevi);
        }
    }

    /** 400 ako je serijalizovan dokument veći od 1 MB. */
    public static void proveriVelicinu(int bajtova) {
        if (bajtova > MAX_BAJTOVA) throw greska("Dokument platna je prevelik (najviše 1 MB).");
    }

    /** Id-jevi slika koje dokument koristi (pozadina, slike, crteži); za proveru postojanja. */
    public static Set<String> medijIds(Platno p) {
        Set<String> ids = new LinkedHashSet<>();
        if (p == null) return ids;
        if (p.pozadina() != null && p.pozadina().medijId() != null) ids.add(p.pozadina().medijId());
        for (Sloj s : ili(p.slojevi(), List.<Sloj>of())) {
            if (s instanceof SlikaSloj i && i.medijId() != null) ids.add(i.medijId());
            if (s instanceof CrtezSloj c && c.medijId() != null) ids.add(c.medijId());
            if (s instanceof FormulaSloj f && f.slikaIzvora() != null) ids.add(f.slikaIzvora());
        }
        return ids;
    }

    /** Vrednost za poruku o grešci: najviše 40 code point-a, bez kontrolnih znakova; null je "null". */
    public static String zaPoruku(String v) {
        if (v == null) return "null";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int i = 0; i < v.length() && n < 40; ) {
            int cp = v.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT) continue;
            sb.appendCodePoint(cp);
            n++;
        }
        return sb.toString();
    }

    private static final String NEISPRAVAN_ID = "Id sloja ili animacije nije ispravan.";

    private static void proveriId(String id, Set<String> videni) {
        if (id == null || !ID.matcher(id).matches()) throw greska(NEISPRAVAN_ID);
        if (!videni.add(id)) throw greska("Id-jevi slojeva i animacija moraju biti jedinstveni.");
    }

    private static void proveriSloj(Sloj s, long[] znakova) {
        if (s.naziv() != null && duzina(s.naziv()) > 60) throw greska("Naziv sloja može imati najviše 60 znakova.");
        if (!u(s.x(), -960, 1920) || !u(s.y(), -960, 1920) || !pozitivno(s.sirina(), 2880) || !pozitivno(s.visina(), 2880)) {
            throw greska("Položaj ili veličina sloja nije ispravna.");
        }
        if (!u(s.rotacija(), -360, 360) || !u(s.providnost(), 0, 1)) {
            throw greska("Rotacija ili providnost sloja nije ispravna.");
        }
        Senka senka = s.senka();
        if (senka != null) {
            if (!u(senka.pomerajX(), -100, 100) || !u(senka.pomerajY(), -100, 100) || !u(senka.zamucenje(), 0, 100)) {
                throw greska("Senka nije ispravna.");
            }
            proveriBoju(senka.boja());
        }
        switch (s) {
            case TekstSloj t -> {
                Okvir o = t.okvir();
                if (o == null || !u(o.unutraLevo(), 0, 200) || !u(o.unutraGore(), 0, 200) || !u(o.unutraDesno(), 0, 200)
                        || !u(o.unutraDole(), 0, 200) || !u(o.skala(), 0.25, 1) || !u(o.skalaProreda(), 0.5, 1)
                        || o.sidro() == null || o.prelom() == null) {
                    throw greska("Okvir teksta nije ispravan.");
                }
                proveriPasuse(t.pasusi(), znakova);
                proveriBojuMoze(t.popuna());
                proveriIvicu(t.ivica());
            }
            case SlikaSloj i -> {
                if (i.medijId() == null || i.medijId().isBlank()) throw greska("Sloj slike mora imati sliku.");
                Isecak c = i.isecak();
                if (c == null || !u(c.levo(), 0, 1) || !u(c.gore(), 0, 1) || !u(c.desno(), 0, 1) || !u(c.dole(), 0, 1)
                        || c.levo() + c.desno() >= 1 || c.gore() + c.dole() >= 1) {
                    throw greska("Isečak slike nije ispravan.");
                }
            }
            case OblikSloj o -> {
                if (o.oblik() == null) throw greska("Oblik sloja nije ispravan.");
                proveriBojuMoze(o.popuna());
                proveriIvicu(o.ivica());
                if (jeLinija(o.oblik())) {
                    if (!tackaU(o.od(), 2880) || !tackaU(o.doTacke(), 2880)) {
                        throw greska("Linija mora imati početak i kraj.");
                    }
                }
            }
            case TabelaSloj t -> proveriTabelu(t, znakova);
            case FormulaSloj f -> {
                int n = f.latex() == null ? 0 : duzina(f.latex());
                if (n == 0 || n > 5000 || !u(f.velicina(), 1, 400)) throw greska("Formula nije ispravna.");
                proveriBoju(f.boja());
                znakova[0] += n;
            }
            case CrtezSloj c -> {
                if (c.medijId() == null || c.medijId().isBlank()) throw greska("Sloj slike mora imati sliku.");
                long tacaka = 0;
                for (Potez z : ili(c.potezi(), List.<Potez>of())) {
                    if (z == null || z.tacke() == null || z.tacke().size() % 2 != 0 || !u(z.debljina(), 0.25, 20)) {
                        throw greska("Crtež nije ispravan.");
                    }
                    for (Double d : z.tacke()) {
                        if (d == null || !Double.isFinite(d)) throw greska("Crtež nije ispravan.");
                    }
                    tacaka += z.tacke().size() / 2;
                    if (tacaka > MAX_TACAKA) throw greska("Crtež nije ispravan.");
                    proveriBoju(z.boja());
                }
            }
        }
    }

    private static void proveriTabelu(TabelaSloj t, long[] znakova) {
        List<Double> kolone = t.kolone();
        List<Double> redovi = t.redovi();
        if (kolone == null || redovi == null || kolone.isEmpty() || redovi.isEmpty()
                || kolone.size() > MAX_TABELA || redovi.size() > MAX_TABELA
                || !kolone.stream().allMatch(d -> pozitivno(d, 2880))
                || !redovi.stream().allMatch(d -> pozitivno(d, 2880))
                || t.celije() == null || t.celije().size() != redovi.size()) {
            throw greska("Tabela nije ispravna.");
        }
        for (int r = 0; r < redovi.size(); r++) {
            List<Celija> red = t.celije().get(r);
            if (red == null || red.size() != kolone.size()) throw greska("Tabela nije ispravna.");
            for (int k = 0; k < red.size(); k++) {
                Celija c = red.get(k);
                if (c == null || c.spojDesno() == null || c.spojDole() == null || c.spojDesno() < 0 || c.spojDole() < 0
                        || c.spojDesno() >= kolone.size() - k || c.spojDole() >= redovi.size() - r
                        || c.sidro() == null || c.pokrivena() == null || c.ivice() == null) {
                    throw greska("Tabela nije ispravna.");
                }
                proveriPasuse(c.pasusi(), znakova);
                proveriBojuMoze(c.popuna());
                proveriIvicu(c.ivice().gore());
                proveriIvicu(c.ivice().dole());
                proveriIvicu(c.ivice().levo());
                proveriIvicu(c.ivice().desno());
            }
        }
    }

    private static void proveriPasuse(List<Pasus> pasusi, long[] znakova) {
        for (Pasus p : ili(pasusi, List.<Pasus>of())) {
            if (p == null || p.poravnanje() == null || p.nivo() == null || p.nivo() < 0 || p.nivo() > 8
                    || !u(p.proredProcenat(), 50, 300) || !u(p.razmakPre(), 0, 1000) || !u(p.razmakPosle(), 0, 1000)
                    || p.uvlacenje() == null || !u(p.uvlacenje().levo(), -1000, 1000)
                    || !u(p.uvlacenje().prviRed(), -1000, 1000)
                    || p.tabulatori() == null || p.tabulatori().size() > MAX_TABULATORA || p.delovi() == null) {
                throw greska("Pasus nije ispravan.");
            }
            for (Tabulator t : p.tabulatori()) {
                if (t == null || t.poravnanje() == null || !u(t.poz(), 0, 2880)) throw greska("Pasus nije ispravan.");
            }
            proveriListu(p.lista());
            for (Deo d : p.delovi()) {
                if (d == null) throw greska("Pasus nije ispravan.");
                if (d.font() == null || !FONTOVI.contains(d.font())) throw greska("Font nije podržan: " + zaPoruku(d.font()) + ".");
                if (!u(d.velicina(), 1, 400)) throw greska("Veličina slova mora biti od 1 do 400.");
                proveriBoju(d.boja());
                znakova[0] += duzina(d.tekst());
            }
        }
    }

    private static void proveriListu(Lista l) {
        if (l == null) return;
        boolean ispravna = l.vrsta() != null && switch (l.vrsta()) {
            case ZNAK -> l.znak() != null && !l.znak().isEmpty() && duzina(l.znak()) <= 2;
            case BROJ -> l.format() != null && l.pocetak() != null && l.pocetak() >= 1 && l.pocetak() <= 9999;
        };
        if (!ispravna) throw greska("Lista nije ispravna.");
        proveriBojuMoze(l.boja());
    }

    private static void proveriIvicu(Ivica i) {
        if (i == null) return;
        if (!u(i.debljina(), 0.25, 20)) throw greska("Debljina ivice mora biti od 0,25 do 20.");
        proveriBoju(i.boja());
    }

    private static void proveriAnimaciju(Animacija a, List<Sloj> slojevi) {
        Sloj cilj = slojevi.stream().filter(s -> s.id().equals(a.slojId())).findFirst().orElse(null);
        if (cilj == null) throw greska("Animacija cilja sloj koji ne postoji.");
        if (a.pasus() != null) {
            if (!(cilj instanceof TekstSloj t) || a.pasus() < 0 || a.pasus() >= ili(t.pasusi(), List.<Pasus>of()).size()) {
                throw greska("Animacija cilja pasus koji ne postoji.");
            }
        }
        if (a.vrsta() == null || a.efekat() == null || a.okidac() == null) throw greska("Animacija nije ispravna.");
        if (a.trajanjeMs() == null || a.kasnjenjeMs() == null || a.trajanjeMs() < 0 || a.trajanjeMs() > 10_000
                || a.kasnjenjeMs() < 0 || a.kasnjenjeMs() > 10_000) {
            throw greska("Trajanje i kašnjenje animacije su od 0 do 10000 ms.");
        }
        Smer smer = a.smer();
        boolean ok = switch (a.efekat()) {
            case ULET, BRISANJE, ISCRTAJ -> smer == Smer.LEVO || smer == Smer.DESNO || smer == Smer.GORE || smer == Smer.DOLE;
            case PODELI -> smer == Smer.HORIZONTALNO || smer == Smer.VERTIKALNO;
            case POJAVI, FADE, ZUM -> smer == null;
        };
        if (!ok) throw greska("Smer animacije nije ispravan.");
    }

    // ------------------------------------------------------------------ pomoćno

    private static void proveriBoju(String boja) {
        if (boja == null || !BOJA.matcher(boja).matches()) throw greska("Boja nije ispravna (#RRGGBB).");
    }

    private static void proveriBojuMoze(String boja) {
        if (boja != null) proveriBoju(boja);
    }

    private static boolean tackaU(Tacka t, double granica) {
        return t != null && u(t.x(), -granica, granica) && u(t.y(), -granica, granica);
    }

    /** {@code min <= v <= max}; {@code null} i NaN nisu u opsegu. */
    private static boolean u(Double v, double min, double max) {
        return v != null && v >= min && v <= max;
    }

    /** {@code 0 < v <= max}. */
    private static boolean pozitivno(Double v, double max) {
        return v != null && v > 0 && v <= max;
    }

    private static int duzina(String s) {
        return s == null ? 0 : s.codePointCount(0, s.length());
    }

    private static Double r(Double d) {
        if (d == null || !Double.isFinite(d)) return d;
        return BigDecimal.valueOf(d).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String boja(String b) {
        return b == null ? null : b.strip().toUpperCase(java.util.Locale.ROOT);
    }

    private static <T> T ili(T v, T podrazumevano) {
        return Objects.requireNonNullElse(v, podrazumevano);
    }

    /** Nova lista; {@code null} lista je prazna, {@code null} element ostaje (proveri ga odbija). */
    private static <T> List<T> lista(List<T> l, UnaryOperator<T> f) {
        List<T> rezultat = new ArrayList<>();
        if (l != null) {
            for (T e : l) rezultat.add(e == null ? null : f.apply(e));
        }
        return rezultat;
    }

    private static SystemException greska(String poruka) {
        return new SystemException(poruka, HttpStatus.BAD_REQUEST);
    }
}
