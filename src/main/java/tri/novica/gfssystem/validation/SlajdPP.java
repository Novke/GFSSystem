package tri.novica.gfssystem.validation;

import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tri.novica.gfssystem.dto.uzivo.OpcijaCmd;
import tri.novica.gfssystem.dto.uzivo.PitanjeCmd;
import tri.novica.gfssystem.dto.uzivo.Prelaz;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.dto.uzivo.ZadatakSlajda;
import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;
import tri.novica.gfssystem.entity.uzivo.SmerPrelaza;
import tri.novica.gfssystem.entity.uzivo.TekstPrikaz;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipPrelaza;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.utility.Nivo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pravila za {@link SlajdCmd} (spec 4.1 "Validacija SlajdCmd", granice 2.12). Ne dira bazu: postojanje slike proverava
 * servis. Redosled upotrebe: {@code proveri(normalizuj(cmd))}. Dužine se broje u znakovima kao u MySQL koloni (code
 * point), pa nijedna prihvaćena vrednost ne prelazi kolonu.
 */
public final class SlajdPP {

    public static final int MAX_SLAJDOVA = 200;
    public static final int MAX_BELESKE = 20_000;
    public static final String BEZ_PLATNA = "Platno slajd mora imati dokument platna.";
    public static final String TACNO = "Tačno";
    public static final String NETACNO = "Netačno";
    public static final int MIN_PRELAZ_MS = 100;
    public static final int MAX_PRELAZ_MS = 5000;
    public static final int PODRAZUMEVANO_PRELAZ_MS = 500;
    public static final String PRELAZ_TRAJANJE = "Trajanje prelaza mora biti od 0,1 do 5 sekundi.";
    public static final String ZADATAK_BEZ_GRUPE = "Zadatak slajd mora imati grupu zadataka.";
    public static final String BROJ_VARIJANTI = "Broj različitih zadataka mora biti od 1 do 50.";
    public static final int MIN_BROJ_VARIJANTI = 1;
    public static final int MAX_BROJ_VARIJANTI = 50;
    /** Jedini dozvoljen cilj Markdown slike (spec 6.5): medij otpremljen u GFS, relativno prema {@code <base href>}. */
    public static final Pattern DOZVOLJENA_SLIKA =
            Pattern.compile("^api/public/mediji/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    public static final String SLIKA_VAN_GFS = "Slika u tekstu mora biti otpremljena u GFS.";

    private static final int MAX_NASLOV = 300;
    private static final int MAX_SADRZAJ = 20_000;
    private static final int MAX_TEKST_PITANJA = 2_000;
    private static final int MIN_OPCIJA = 2;
    private static final int MAX_OPCIJA = 6;
    private static final int MAX_TEKST_OPCIJE = 300;
    private static final int MIN_VREME = 5;
    private static final int MAX_VREME = 600;
    private static final String VREME_ODGOVORA = "Vreme za odgovor mora biti od 5 do 600 sekundi.";
    private static final int MAX_JEDINICA = 30;
    private static final int MAX_PRIHVATLJIVIH = 20;
    private static final int MAX_PRIHVATLJIV = 100;
    private static final int MAX_OZNAKA_SKALE = 60;
    private static final String PUTANJA_MEDIJA = "api/public/mediji/";
    /**
     * Inline slika koju marked (i CommonMark) sigurno čita kao inline, pa na tom mestu nema referentne slike. Ceo oblik je
     * u jednom redu: natpis bez zagrada, {@code `} i {@code <}; cilj bez razmaka, zagrada i {@code \}; opcioni naslov pod
     * navodnicima bez znakova kojima spoljni oblik može da se završi usred slike ({@code [ ] ( ) * _ ~ | ` < > \}). Spoljni
     * oblik (granica bloka ili reda tabele, ćelija {@code |}, natpis linka {@code ]}, naglašavanje {@code * _ ~}, kod
     * {@code `}) tako može da preseče sliku samo u natpisu, pre njegovog {@code ]}, a odsečak bez {@code ]} nije slika.
     * Šire od ovoga (npr. prelom reda u zagradi) nije greška: tada se proveravaju definicije.
     */
    private static final Pattern SIGURNA_INLINE = Pattern.compile(
            "!\\[(?:\\\\[^\\n]|[^\\[\\]\\\\`<\\n])*+\\]\\([ \\t]*+(?:<[^<>\\n\\\\]++>|[^\\x00-\\x20()<>\\\\]++)"
            + "(?:[ \\t]++(?:\"[^\"\\\\\\n\\[\\]()*_~|`<>]*+\"|'[^'\\\\\\n\\[\\]()*_~|`<>]*+'))?[ \\t]*+\\)");
    /** {@code [oznaka]:} bilo gde u tekstu (i u citatu ili listi), kao oznaka definicije u marked-u. */
    private static final Pattern DEFINICIJA = Pattern.compile("\\[(?:\\\\[\\s\\S]|[^\\[\\]\\\\])++\\]:");

    private SlajdPP() {}

    /**
     * Kopija sa podrazumevanim vrednostima: tekstovi bez razmaka na krajevima, prazan string {@code null}; INFO bez
     * pitanja; polja koja ne važe za tip pitanja su {@code null} (opcije prazna lista); ANKETA bez tačnih,
     * TACNO_NETACNO sa tekstovima "Tačno"/"Netačno", KRATAK_TEKST podrazumevano OBLAK, BROJ odstupanje 0 APSOLUTNO.
     * ZADATAK zadržava samo beleške, prelaz i zadatak (ostali tipovi gube {@code zadatak}). Prelaz (svaki tip) po
     * {@link #normalizuj(Prelaz)}.
     */
    public static SlajdCmd normalizuj(SlajdCmd cmd) {
        if (cmd == null) return null;
        JsonNode platno = cmd.platno() == null || cmd.platno().isNull() || cmd.platno().isMissingNode() ? null : cmd.platno();
        if (cmd.tip() == TipSlajda.PLATNO) {
            return new SlajdCmd(cmd.tip(), null, null, null, prazanUNull(cmd.beleske()), false, null, platno,
                    normalizuj(cmd.prelaz()));
        }
        if (cmd.tip() == TipSlajda.ZADATAK) {
            return new SlajdCmd(cmd.tip(), null, null, null, prazanUNull(cmd.beleske()), false, null, null,
                    normalizuj(cmd.prelaz()), cmd.zadatak());
        }
        PitanjeCmd pitanje = cmd.tip() == TipSlajda.INFO ? null : normalizuj(cmd.pitanje());
        return new SlajdCmd(cmd.tip(), prazanUNull(cmd.naslov()), prazanUNull(cmd.sadrzaj()), prazanUNull(cmd.slikaId()),
                prazanUNull(cmd.beleske()), cmd.postepeno(), pitanje, null, normalizuj(cmd.prelaz()));
    }

    /** Bez efekta -> bez prelaza; FADE nema smer; GURANJE/BRISANJE bez smera -> DESNO; bez trajanja -> 500 ms. */
    public static Prelaz normalizuj(Prelaz p) {
        if (p == null || p.efekat() == null) return null;
        SmerPrelaza smer = p.efekat() == TipPrelaza.FADE ? null : Objects.requireNonNullElse(p.smer(), SmerPrelaza.DESNO);
        return new Prelaz(p.efekat(), smer, Objects.requireNonNullElse(p.trajanjeMs(), PODRAZUMEVANO_PRELAZ_MS));
    }

    private static PitanjeCmd normalizuj(PitanjeCmd p) {
        if (p == null) return null;
        TipPitanja tip = p.tip();
        List<OpcijaCmd> opcije = new ArrayList<>();
        if (imaOpcije(tip) && p.opcije() != null) {
            for (int i = 0; i < p.opcije().size(); i++) {
                OpcijaCmd o = p.opcije().get(i);
                String tekst = o == null ? null : prazanUNull(o.tekst());
                boolean tacna = o != null && o.tacna() && tip != TipPitanja.ANKETA;
                if (tip == TipPitanja.TACNO_NETACNO && i < 2) {
                    tekst = i == 0 ? TACNO : NETACNO;
                }
                opcije.add(new OpcijaCmd(tekst, tacna));
            }
        }
        boolean broj = tip == TipPitanja.BROJ;
        boolean kratakTekst = tip == TipPitanja.KRATAK_TEKST;
        boolean skala = tip == TipPitanja.SKALA;
        return new PitanjeCmd(tip, prazanUNull(p.tekst()), prazanUNull(p.slikaId()), p.vremeSekunde(), opcije,
                broj ? p.brojTacno() : null,
                broj ? Objects.requireNonNullElse(p.brojOdstupanje(), 0.0) : null,
                broj ? Objects.requireNonNullElse(p.odstupanjeTip(), OdstupanjeTip.APSOLUTNO) : null,
                broj ? prazanUNull(p.jedinica()) : null,
                kratakTekst ? Objects.requireNonNullElse(p.tekstPrikaz(), TekstPrikaz.OBLAK) : null,
                kratakTekst ? prihvatljivi(p.prihvatljiviOdgovori()) : null,
                skala ? prazanUNull(p.skalaMinOznaka()) : null,
                skala ? prazanUNull(p.skalaMaxOznaka()) : null);
    }

    /** Baca 400 sa porukom prvog prekršenog pravila. Prazan tekst (samo razmaci) se računa kao da ga nema. */
    public static void proveri(SlajdCmd cmd) {
        if (cmd == null || cmd.tip() == null) {
            throw greska("Tip slajda je obavezan.");
        }
        if (duzina(cmd.naslov()) > MAX_NASLOV) {
            throw greska("Naslov može imati najviše 300 znakova.");
        }
        if (duzina(cmd.sadrzaj()) > MAX_SADRZAJ) {
            throw greska("Tekst slajda može imati najviše 20000 znakova.");
        }
        if (duzina(cmd.beleske()) > MAX_BELESKE) {
            throw greska("Beleške mogu imati najviše 20000 znakova.");
        }
        // prelaz važi za svaki tip; normalizovan prelaz uvek ima trajanje (null samo pre normalizuj)
        if (cmd.prelaz() != null && cmd.prelaz().trajanjeMs() != null
                && (cmd.prelaz().trajanjeMs() < MIN_PRELAZ_MS || cmd.prelaz().trajanjeMs() > MAX_PRELAZ_MS)) {
            throw greska(PRELAZ_TRAJANJE);
        }
        if (cmd.tip() == TipSlajda.PLATNO) {
            if (cmd.platno() == null) {
                throw greska(BEZ_PLATNA);
            }
            return;
        }
        if (cmd.tip() == TipSlajda.INFO) {
            if (prazan(cmd.naslov()) && prazan(cmd.sadrzaj()) && prazan(cmd.slikaId())) {
                throw greska("Info slajd mora imati naslov, tekst ili sliku.");
            }
            return;
        }
        if (cmd.tip() == TipSlajda.ZADATAK) {
            proveri(cmd.zadatak());
            return;
        }
        if (cmd.pitanje() == null) {
            throw greska("Slajd sa pitanjem mora imati pitanje.");
        }
        proveri(cmd.pitanje());
    }

    /**
     * Grupa je obavezna, tajmer 5..600 s ili {@code null}, broj različitih varijanti 1..50 ili {@code null}, nivo 1..3 ili
     * {@code null} ({@link Nivo#GRANICE}).
     */
    private static void proveri(ZadatakSlajda z) {
        if (z == null || z.grupaId() == null) {
            throw greska(ZADATAK_BEZ_GRUPE);
        }
        if (z.vremeSekunde() != null && (z.vremeSekunde() < MIN_VREME || z.vremeSekunde() > MAX_VREME)) {
            throw greska(VREME_ODGOVORA);
        }
        if (z.brojVarijanti() != null && (z.brojVarijanti() < MIN_BROJ_VARIJANTI || z.brojVarijanti() > MAX_BROJ_VARIJANTI)) {
            throw greska(BROJ_VARIJANTI);
        }
        Nivo.proveri(z.nivo());
    }

    private static void proveri(PitanjeCmd p) {
        if (p.tip() == null) {
            throw greska("Tip pitanja je obavezan.");
        }
        if (prazan(p.tekst()) || duzina(p.tekst()) > MAX_TEKST_PITANJA) {
            throw greska("Tekst pitanja je obavezan (najviše 2000 znakova).");
        }
        if (p.vremeSekunde() != null && (p.vremeSekunde() < MIN_VREME || p.vremeSekunde() > MAX_VREME)) {
            throw greska(VREME_ODGOVORA);
        }
        switch (p.tip()) {
            case JEDAN_TACAN, VISE_TACNIH, ANKETA -> proveriOpcije(p);
            case TACNO_NETACNO -> {
                if (p.opcije() == null || p.opcije().size() != 2 || brojTacnih(p.opcije()) != 1) {
                    throw greska("Tačno/netačno pitanje ima tačno dva odgovora, od kojih je jedan tačan.");
                }
            }
            case KRATAK_TEKST -> {
                List<String> prihvatljivi = p.prihvatljiviOdgovori();
                if (prihvatljivi != null && (prihvatljivi.size() > MAX_PRIHVATLJIVIH
                        || prihvatljivi.stream().anyMatch(s -> duzina(s) > MAX_PRIHVATLJIV))) {
                    throw greska("Najviše 20 prihvatljivih odgovora, svaki do 100 znakova.");
                }
            }
            case BROJ -> {
                if (nijeKonacan(p.brojTacno()) || nijeKonacan(p.brojOdstupanje())) {
                    throw greska("Broj nije ispravan.");
                }
                if (p.brojOdstupanje() != null && p.brojOdstupanje() < 0) {
                    throw greska("Odstupanje ne može biti negativno.");
                }
                if (duzina(p.jedinica()) > MAX_JEDINICA) {
                    throw greska("Jedinica može imati najviše 30 znakova.");
                }
            }
            case SKALA -> {
                if (duzina(p.skalaMinOznaka()) > MAX_OZNAKA_SKALE || duzina(p.skalaMaxOznaka()) > MAX_OZNAKA_SKALE) {
                    throw greska("Oznaka skale može imati najviše 60 znakova.");
                }
            }
        }
    }

    private static void proveriOpcije(PitanjeCmd p) {
        List<OpcijaCmd> opcije = p.opcije();
        if (opcije == null || opcije.size() < MIN_OPCIJA || opcije.size() > MAX_OPCIJA) {
            throw greska("Pitanje mora imati od 2 do 6 ponuđenih odgovora.");
        }
        for (OpcijaCmd o : opcije) {
            if (o == null || prazan(o.tekst()) || duzina(o.tekst()) > MAX_TEKST_OPCIJE) {
                throw greska("Ponuđeni odgovor mora imati od 1 do 300 znakova.");
            }
        }
        long tacnih = brojTacnih(opcije);
        if (p.tip() == TipPitanja.JEDAN_TACAN && tacnih != 1) {
            throw greska("Pitanje sa jednim tačnim odgovorom mora imati tačno jedan tačan odgovor.");
        }
        if (p.tip() == TipPitanja.VISE_TACNIH && tacnih < 1) {
            throw greska("Označi bar jedan tačan odgovor.");
        }
    }

    /**
     * Slike iz Markdown-a slajda (spec 6.5): INFO {@code sadrzaj}, PITANJE tekst pitanja i tekstovi opcija. Vraća UUID-ove
     * medija; postojanje i vrstu ({@code SLIKA}) proverava servis. Cilj van GFS-a je 400 {@link #SLIKA_VAN_GFS}.
     */
    public static Set<String> slikeSlajda(SlajdCmd n) {
        Set<String> uuidi = new LinkedHashSet<>();
        if (n == null) return uuidi;
        if (n.tip() == TipSlajda.INFO) {
            uuidi.addAll(slikeIzMarkdowna(n.sadrzaj()));
        }
        if (n.tip() == TipSlajda.PITANJE && n.pitanje() != null) {
            uuidi.addAll(slikeIzMarkdowna(n.pitanje().tekst()));
            if (n.pitanje().opcije() != null) {
                for (OpcijaCmd o : n.pitanje().opcije()) {
                    if (o != null) uuidi.addAll(slikeIzMarkdowna(o.tekst()));
                }
            }
        }
        return uuidi;
    }

    /**
     * UUID-ovi slika iz Markdown teksta; cilj slike sme biti samo {@link #DOZVOLJENA_SLIKA}, inače 400
     * {@link #SLIKA_VAN_GFS}. Proverava sve što bi marked mogao da prikaže kao sliku, i nešto više (sumnjivo je 400):
     * <ul>
     *   <li>inline {@code ![natpis](cilj "naslov")} i {@code ![natpis](<cilj>)}: svaki {@code ](} na kom bi se natpis neke
     *       slike mogao završiti ({@code krajeviNatpisa}); cilj se poredi doslovno, pa {@code \}, {@code %} i entiteti
     *       padaju;</li>
     *   <li>referentni {@code ![natpis][oznaka]}, {@code ![natpis][]}, {@code ![natpis]}: kad tekst ima bar jedan
     *       {@code ![} koji nije sigurno inline ({@code SIGURNA_INLINE}), svaka definicija {@code [oznaka]: cilj} u tekstu
     *       mora biti dozvoljena (i definicija običnog linka).</li>
     * </ul>
     * HTML ({@code <img>}) front ne prikazuje (marked {@code html: ''}), pa ovde ne smeta.
     */
    public static Set<String> slikeIzMarkdowna(String md) {
        Set<String> uuidi = new LinkedHashSet<>();
        if (md == null || md.isEmpty()) return uuidi;
        String s = md.replace("\r\n", "\n").replace('\r', '\n');   // kao marked pre parsiranja
        for (int kraj : krajeviNatpisa(s)) {
            uuidi.add(uuidSlike(ciljInline(s, kraj + 2)));
        }
        boolean referentna = false;
        Matcher sigurna = SIGURNA_INLINE.matcher(s);
        for (int i = s.indexOf("!["); i >= 0 && !referentna; i = s.indexOf("![", i + 1)) {
            referentna = !sigurna.region(i, s.length()).lookingAt();
        }
        if (referentna) {
            Matcher definicija = DEFINICIJA.matcher(s);
            while (definicija.find()) {
                String cilj = ciljDefinicije(s, definicija.end());
                if (cilj == null || !cilj.isEmpty()) uuidi.add(uuidSlike(cilj));
            }
        }
        return uuidi;
    }

    /**
     * Mesta {@code ]} (pre {@code (}) na kojima bi se natpis neke slike mogao završiti, u jednom prolazu po pasusima (prazna
     * linija završava natpis): {@code ]} koji zatvara {@code [} iz {@code ![} (zagrade u ravnoteži, uz {@code \}), a kad
     * posle još nezatvorenog {@code ![} dođe {@code `}, svaki {@code ](} do kraja pasusa, jer kod u natpisu može sakriti
     * zagrade.
     */
    private static List<Integer> krajeviNatpisa(String s) {
        List<Integer> krajevi = new ArrayList<>();
        Deque<Boolean> otvorene = new ArrayDeque<>();   // true: [ iz ![
        int otvorenihSlika = 0;
        boolean kod = false;
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '\\') {
                k++;
            } else if (c == '\n' && praznaLinija(s, k + 1)) {
                otvorene.clear();
                otvorenihSlika = 0;
                kod = false;
            } else if (c == '`') {
                kod |= otvorenihSlika > 0;
            } else if (c == '[') {
                boolean slika = k > 0 && s.charAt(k - 1) == '!';
                otvorene.push(slika);
                if (slika) otvorenihSlika++;
            } else if (c == ']') {
                boolean zatvaraSliku = !otvorene.isEmpty() && otvorene.pop();
                if (zatvaraSliku) otvorenihSlika--;
                if ((zatvaraSliku || kod) && k + 1 < s.length() && s.charAt(k + 1) == '(') {
                    krajevi.add(k);
                }
            }
        }
        return krajevi;
    }

    /**
     * Cilj inline slike posle {@code (}, kao u marked-u: {@code <cilj>} (iza {@code >} sme samo razmak ili {@code )},
     * inače {@code null}), ili niz bez razmaka i kontrolnih znakova do prve {@code )} van ravnoteže.
     */
    private static String ciljInline(String s, int od) {
        int k = od;
        while (k < s.length() && (s.charAt(k) == ' ' || s.charAt(k) == '\t' || s.charAt(k) == '\n')) k++;
        if (k < s.length() && s.charAt(k) == '<') {
            int kraj = k + 1;
            while (kraj < s.length() && "<>\\\n".indexOf(s.charAt(kraj)) < 0) kraj++;
            boolean zatvoren = kraj < s.length() && s.charAt(kraj) == '>'
                    && (kraj + 1 == s.length() || " \t\n)".indexOf(s.charAt(kraj + 1)) >= 0);
            return zatvoren ? s.substring(k + 1, kraj) : null;
        }
        int dubina = 0;
        int kraj = k;
        while (kraj < s.length()) {
            char c = s.charAt(kraj);
            if (c <= ' ' || (c == ')' && dubina == 0)) break;
            if (c == '(') dubina++;
            if (c == ')') dubina--;
            kraj += c == '\\' ? 2 : 1;
        }
        return s.substring(k, Math.min(kraj, s.length()));
    }

    /**
     * Cilj definicije posle {@code [oznaka]:} (razmaci, najviše jedan prelom reda): {@code <cilj>} do prvog {@code >} (bez
     * drugog {@code >} u istom redu, inače {@code null}) ili niz do razmaka; prazan string kad cilja nema (to nije definicija).
     */
    private static String ciljDefinicije(String s, int od) {
        int k = od;
        while (k < s.length() && (s.charAt(k) == ' ' || s.charAt(k) == '\t')) k++;
        if (k < s.length() && s.charAt(k) == '\n') {
            k++;
            while (k < s.length() && (s.charAt(k) == ' ' || s.charAt(k) == '\t')) k++;
        }
        int krajReda = s.indexOf('\n', k);
        if (krajReda < 0) krajReda = s.length();
        if (k < s.length() && s.charAt(k) == '<') {
            int kraj = s.indexOf('>', k + 1);
            if (kraj < 0 || kraj > krajReda || s.lastIndexOf('>', krajReda - 1) != kraj) return null;
            return s.substring(k + 1, kraj);
        }
        int kraj = k;
        while (kraj < s.length() && " \t\n".indexOf(s.charAt(kraj)) < 0) kraj++;
        return s.substring(k, kraj);
    }

    /** Linija od {@code od} je prazna (samo razmaci) ili je kraj teksta. */
    private static boolean praznaLinija(String s, int od) {
        int k = od;
        while (k < s.length() && (s.charAt(k) == ' ' || s.charAt(k) == '\t')) k++;
        return k == s.length() || s.charAt(k) == '\n';
    }

    /** UUID iz dozvoljenog cilja; svaki drugi cilj (i {@code null}) je 400 {@link #SLIKA_VAN_GFS}. */
    private static String uuidSlike(String cilj) {
        if (cilj == null || !DOZVOLJENA_SLIKA.matcher(cilj).matches()) {
            throw greska(SLIKA_VAN_GFS);
        }
        return cilj.substring(PUTANJA_MEDIJA.length());
    }

    /** Pre dodavanja slajda: {@code postojeci} je trenutni broj slajdova prezentacije. */
    public static void proveriBrojSlajdova(long postojeci) {
        if (postojeci >= MAX_SLAJDOVA) {
            throw greska("Prezentacija može imati najviše 200 slajdova.");
        }
    }

    /** Pre uvoza: {@code postojeci} je trenutni broj slajdova prezentacije, {@code novi} broj uvezenih. */
    public static void proveriBrojZaUvoz(long postojeci, int novi) {
        if (postojeci + novi > MAX_SLAJDOVA) {
            throw greska("Prezentacija može imati najviše 200 slajdova (ima " + postojeci + ", uvozi se " + novi + ").");
        }
    }

    /** Tipovi pitanja sa ponuđenim odgovorima. */
    public static boolean imaOpcije(TipPitanja tip) {
        return tip == TipPitanja.JEDAN_TACAN || tip == TipPitanja.VISE_TACNIH || tip == TipPitanja.ANKETA
                || tip == TipPitanja.TACNO_NETACNO;
    }

    private static long brojTacnih(List<OpcijaCmd> opcije) {
        return opcije.stream().filter(o -> o != null && o.tacna()).count();
    }

    private static List<String> prihvatljivi(List<String> lista) {
        if (lista == null) return new ArrayList<>();
        List<String> rezultat = new ArrayList<>();
        for (String s : lista) {
            String t = prazanUNull(s);
            if (t != null) rezultat.add(t);
        }
        return rezultat;
    }

    private static boolean nijeKonacan(Double d) {
        return d != null && !Double.isFinite(d);
    }

    private static boolean prazan(String s) {
        return s == null || s.isBlank();
    }

    private static int duzina(String s) {
        return s == null ? 0 : s.codePointCount(0, s.length());
    }

    private static String prazanUNull(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }

    private static SystemException greska(String poruka) {
        return new SystemException(poruka, HttpStatus.BAD_REQUEST);
    }
}
