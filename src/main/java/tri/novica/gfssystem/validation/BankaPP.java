package tri.novica.gfssystem.validation;

import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.PoljeOpcija;
import tri.novica.gfssystem.dto.banka.TipPolja;
import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pravila banke zadataka (spec 2.2, limiti iz plana): šema polja, tačni odgovori varijante, grupa, varijanta i ime
 * priloga. Ne dira bazu. Redosled upotrebe za polja: {@code proveriPolja(normalizujPolja(polja))}. Dužine se broje u
 * znakovima kao u MySQL koloni (code point), pa nijedna prihvaćena vrednost ne prelazi kolonu.
 */
public final class BankaPP {

    private static final int MAX_POLJA = 10;
    private static final Pattern KLJUC = Pattern.compile("[a-z][a-z0-9]{0,9}");
    private static final Pattern IME_PRILOGA = Pattern.compile("[a-z0-9_-]{1,60}");
    private static final int MAX_OZNAKA = 200;
    private static final int MIN_OPCIJA = 2;
    private static final int MAX_OPCIJA = 6;
    private static final int MAX_TEKST_OPCIJE = 200;
    private static final int MAX_JEDINICA = 30;
    private static final int MAX_PRIHVATLJIVIH = 10;
    private static final int MAX_PRIHVATLJIV = 200;
    private static final int MAX_NAZIV_GRUPE = 120;
    private static final int MAX_UPUTSTVO = 5_000;
    private static final int MAX_TEKST_ZADATKA = 20_000;
    private static final int MAX_RESENJE = 20_000;
    private static final int MAX_NAZIV_VARIJANTE = 120;

    private BankaPP() {}

    // --- šema polja ---

    /**
     * Kopija sa podrazumevanim vrednostima: tekstovi bez razmaka na krajevima, prazna jedinica {@code null}, odstupanje 0
     * {@code APSOLUTNO}, {@code zanemariVelicinu} i {@code zanemariRazmake} {@code true}, {@code zanemariVodeceNule}
     * {@code true} samo za uključen TEKST, inače {@code null} (= isključeno, kao šema od pre te opcije); opcije samo za JEDAN_TACAN i
     * VISE_TACNIH, a opciji bez id-ja se dodeljuje sledeći slobodan id (iznad najvećeg datog, od 1). Ne proverava pravila.
     * Za izmenu sačuvane šeme vidi {@link #normalizujPolja(List, List)}.
     */
    public static List<Polje> normalizujPolja(List<Polje> polja) {
        return normalizujPolja(polja, null);
    }

    /**
     * Kao {@link #normalizujPolja(List)}, ali opcija bez id-ja dobija id iznad najvećeg datog <b>i</b> najvećeg u sačuvanoj
     * šemi za isti ključ ({@code sacuvana}, šema grupe pre izmene; {@code null} = nema je). Tako ni klijent koji ne šalje
     * id-jeve (MCP) ne dobija id opcije uklonjene u istoj izmeni, pa tačan odgovor koji je na nju pokazivao nestaje umesto
     * da pređe na novu opciju.
     */
    public static List<Polje> normalizujPolja(List<Polje> polja, List<Polje> sacuvana) {
        Map<String, Long> najveciSacuvan = new HashMap<>();
        if (sacuvana != null) {
            for (Polje p : sacuvana) {
                if (p == null || p.kljuc() == null || p.opcije() == null) continue;
                for (PoljeOpcija o : p.opcije()) {
                    if (o != null && o.id() != null) najveciSacuvan.merge(p.kljuc(), o.id(), Math::max);
                }
            }
        }
        List<Polje> rezultat = new ArrayList<>();
        if (polja == null) return rezultat;
        for (Polje p : polja) {
            rezultat.add(p == null ? null : normalizuj(p, najveciSacuvan.getOrDefault(strip(p.kljuc()), 0L)));
        }
        return rezultat;
    }

    private static Polje normalizuj(Polje p, long najveciSacuvan) {
        boolean opcijsko = p.tip() == TipPolja.JEDAN_TACAN || p.tip() == TipPolja.VISE_TACNIH;
        return new Polje(strip(p.kljuc()), strip(p.oznaka()), p.tip(),
                p.odstupanje() == null ? Double.valueOf(0.0) : p.odstupanje(),
                p.odstupanjeTip() == null ? OdstupanjeTip.APSOLUTNO : p.odstupanjeTip(),
                prazanUNull(p.jedinica()),
                p.zanemariVelicinu() == null ? Boolean.TRUE : p.zanemariVelicinu(),
                p.zanemariRazmake() == null ? Boolean.TRUE : p.zanemariRazmake(),
                opcijsko ? normalizujOpcije(p.opcije(), najveciSacuvan) : null,
                p.tip() == TipPolja.TEKST && Boolean.TRUE.equals(p.zanemariVodeceNule()) ? Boolean.TRUE : null);
    }

    private static List<PoljeOpcija> normalizujOpcije(List<PoljeOpcija> opcije, long najveciSacuvan) {
        if (opcije == null) return null;
        long sledeci = opcije.stream().filter(o -> o != null && o.id() != null).mapToLong(PoljeOpcija::id)
                .max().orElse(0L);
        sledeci = Math.max(Math.max(sledeci, najveciSacuvan), 0L) + 1;
        List<PoljeOpcija> rezultat = new ArrayList<>();
        for (PoljeOpcija o : opcije) {
            if (o == null) {
                rezultat.add(null);
            } else {
                rezultat.add(new PoljeOpcija(o.id() != null ? o.id() : Long.valueOf(sledeci++), strip(o.tekst())));
            }
        }
        return rezultat;
    }

    /** Baca 400 sa porukom prvog prekršenog pravila; očekuje normalizovana polja. {@code null} je prazna šema. */
    public static void proveriPolja(List<Polje> polja) {
        if (polja == null) return;
        if (polja.size() > MAX_POLJA) {
            throw greska("Najviše 10 polja.");
        }
        Set<String> kljucevi = new HashSet<>();
        for (Polje p : polja) {
            String k = p == null ? null : p.kljuc();
            if (k == null || !KLJUC.matcher(k).matches()) {
                throw greska("Ključ polja mora biti 1-10 malih slova i cifara i počinjati slovom.");
            }
            if (!kljucevi.add(k)) {
                throw greska("Ključ polja '" + k + "' se ponavlja.");
            }
            if (p.tip() == null) {
                throw greska("Tip polja '" + k + "' je obavezan.");
            }
            if (prazan(p.oznaka()) || duzina(p.oznaka()) > MAX_OZNAKA) {
                throw greska("Oznaka polja mora imati 1 do 200 znakova.");
            }
            // i kad ne važe za tip: NaN i beskonačnost se ne bi dali pročitati nazad iz kolone
            if (p.odstupanje() != null && (!Double.isFinite(p.odstupanje()) || p.odstupanje() < 0)) {
                throw greska("Odstupanje polja '" + k + "' mora biti nenegativan broj.");
            }
            if (duzina(p.jedinica()) > MAX_JEDINICA) {
                throw greska("Jedinica polja '" + k + "' može imati najviše 30 znakova.");
            }
            if (p.tip() == TipPolja.JEDAN_TACAN || p.tip() == TipPolja.VISE_TACNIH) {
                proveriOpcije(k, p.opcije());
            }
        }
    }

    private static void proveriOpcije(String kljuc, List<PoljeOpcija> opcije) {
        if (opcije == null || opcije.size() < MIN_OPCIJA || opcije.size() > MAX_OPCIJA) {
            throw greska("Polje '" + kljuc + "' mora imati 2 do 6 opcija.");
        }
        Set<Long> ids = new HashSet<>();
        for (PoljeOpcija o : opcije) {
            if (o == null || prazan(o.tekst()) || duzina(o.tekst()) > MAX_TEKST_OPCIJE) {
                throw greska("Tekst opcije polja '" + kljuc + "' mora imati 1 do 200 znakova.");
            }
            if (o.id() == null || o.id() < 1) {
                throw greska("Polje '" + kljuc + "' ima neispravan id opcije.");
            }
            if (!ids.add(o.id())) {
                throw greska("Polje '" + kljuc + "' ima dupliran id opcije.");
            }
        }
    }

    // --- tačni odgovori varijante ---

    /**
     * Za svaki ključ šeme mora postojati vrednost pravog oblika (postavljeno je samo ono svojstvo koje tip traži), a
     * ključ van šeme je greška. Prazna šema prihvata {@code null} i prazne odgovore.
     */
    public static void proveriOdgovore(List<Polje> polja, Map<String, OdgovorPolja> odgovori) {
        List<Polje> sema = polja == null ? List.of() : polja;
        for (Polje p : sema) {
            String k = p.kljuc();
            OdgovorPolja o = odgovori == null ? null : odgovori.get(k);
            if (o == null) {
                throw greska("Odgovor za polje '" + k + "' nedostaje.");
            }
            if (p.tip() == null) {
                throw greska("Tip polja '" + k + "' je obavezan.");
            }
            switch (p.tip()) {
                case BROJ -> proveriBroj(k, o);
                case TEKST -> proveriTekst(k, o);
                case JEDAN_TACAN -> proveriJedanTacan(k, p, o);
                case VISE_TACNIH -> proveriViseTacnih(k, p, o);
            }
        }
        if (odgovori != null) {
            Set<String> poznati = sema.stream().map(Polje::kljuc).collect(Collectors.toSet());
            for (String k : odgovori.keySet()) {
                if (!poznati.contains(k)) {
                    throw greska("Nepoznato polje '" + PlatnoPP.zaPoruku(k) + "'.");
                }
            }
        }
    }

    private static void proveriBroj(String k, OdgovorPolja o) {
        if (o.broj() == null || !Double.isFinite(o.broj())
                || o.prihvatljivi() != null || o.tacna() != null || o.tacne() != null) {
            throw greska("Odgovor za polje '" + k + "' mora biti broj.");
        }
    }

    private static void proveriTekst(String k, OdgovorPolja o) {
        List<String> prihvatljivi = o.prihvatljivi();
        if (prihvatljivi == null || prihvatljivi.isEmpty() || prihvatljivi.size() > MAX_PRIHVATLJIVIH
                || prihvatljivi.stream().anyMatch(s -> prazan(s) || duzina(s) > MAX_PRIHVATLJIV)
                || o.broj() != null || o.tacna() != null || o.tacne() != null) {
            throw greska("Polje '" + k + "' traži 1 do 10 prihvatljivih odgovora (1-200 znakova).");
        }
    }

    private static void proveriJedanTacan(String k, Polje p, OdgovorPolja o) {
        if (o.tacna() == null || !idOpcija(p).contains(o.tacna())
                || o.broj() != null || o.prihvatljivi() != null || o.tacne() != null) {
            throw greska("Odgovor za polje '" + k + "' mora biti id jedne od opcija.");
        }
    }

    private static void proveriViseTacnih(String k, Polje p, OdgovorPolja o) {
        List<Long> tacne = o.tacne();
        if (tacne == null || tacne.isEmpty() || !idOpcija(p).containsAll(tacne) || new HashSet<>(tacne).size() != tacne.size()
                || o.broj() != null || o.prihvatljivi() != null || o.tacna() != null) {
            throw greska("Odgovor za polje '" + k + "' mora biti skup id-jeva opcija.");
        }
    }

    private static Set<Long> idOpcija(Polje p) {
        return p.opcije() == null ? Set.of()
                : p.opcije().stream().map(PoljeOpcija::id).collect(Collectors.toSet());
    }

    // --- grupa, varijanta, prilog ---

    /** Težina je opciona (unutrašnji čvor stabla je nema); obaveznost za list sa varijantama proverava servis. */
    public static void proveriGrupu(String naziv, Integer tezina, String uputstvo) {
        if (prazan(naziv) || duzina(naziv) > MAX_NAZIV_GRUPE) {
            throw greska("Naziv grupe mora imati 1 do 120 znakova.");
        }
        if (tezina != null && (tezina < 1 || tezina > 5)) {
            throw greska("Težina mora biti od 1 do 5.");
        }
        if (duzina(uputstvo) > MAX_UPUTSTVO) {
            throw greska("Uputstvo može imati najviše 5 000 znakova.");
        }
    }

    public static void proveriVarijantu(String tekst, String resenje, String naziv) {
        if (prazan(tekst) || duzina(tekst) > MAX_TEKST_ZADATKA) {
            throw greska("Tekst zadatka mora imati 1 do 20 000 znakova.");
        }
        if (duzina(resenje) > MAX_RESENJE) {
            throw greska("Rešenje može imati najviše 20 000 znakova.");
        }
        if (duzina(naziv) > MAX_NAZIV_VARIJANTE) {
            throw greska("Naziv varijante može imati najviše 120 znakova.");
        }
    }

    public static void proveriImePriloga(String ime) {
        if (ime == null || !IME_PRILOGA.matcher(ime).matches()) {
            throw greska("Ime priloga: 1-60 malih slova, cifara, '_' ili '-'.");
        }
    }

    private static boolean prazan(String s) {
        return s == null || s.isBlank();
    }

    private static int duzina(String s) {
        return s == null ? 0 : s.codePointCount(0, s.length());
    }

    private static String strip(String s) {
        return s == null ? null : s.strip();
    }

    private static String prazanUNull(String s) {
        String t = strip(s);
        return t == null || t.isEmpty() ? null : t;
    }

    private static SystemException greska(String poruka) {
        return new SystemException(poruka, HttpStatus.BAD_REQUEST);
    }
}
