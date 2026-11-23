package tri.novica.gfssystem.service.anketa;

import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.entity.anketa.Anketa;
import tri.novica.gfssystem.entity.anketa.PublikaAnkete;
import tri.novica.gfssystem.exceptions.SystemException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Čista pravila brze ankete (spec brze ankete 3 i 4), bez baze: provera pitanja i odgovora, glas, izračunato stanje
 * (otvorena ili ne, efektivno vreme zatvaranja), zbirovi po odgovoru i pobednik. Dužine se broje u znakovima (code point-ima),
 * kao {@code varchar} u bazi.
 */
public final class AnketaPravila {

    public static final int MAX_PITANJE = 300;
    public static final int MIN_ODGOVORA = 2;
    public static final int MAX_ODGOVORA = 10;
    public static final int MAX_ODGOVOR = 120;
    public static final int MIN_TRAJANJE = 10;
    public static final int MAX_TRAJANJE = 1800;
    public static final int MIN_ROK_SATI = 1;
    public static final int MAX_ROK_SATI = 336;
    public static final int MAX_IKONICA = 16;
    public static final int MAX_KRATAK_NAZIV = 30;
    public static final int MAX_NAZIV_FOLDERA = 60;
    /** Rok ankete za grupu: najviše 14 dana od pokretanja. */
    public static final Duration MAX_ROK = Duration.ofDays(14);
    /**
     * Anketa na času bez roka i bez kraja predavanja ne ostaje otvorena zauvek (zaboravljeno "Završi predavanje"): posle
     * 5 h od pokretanja je zatvorena, isto koliko student ostaje "na času" posle čekiranja ({@code CekiranjeService.NA_CASU}).
     */
    public static final Duration NAJDUZE_NA_CASU = Duration.ofHours(5);
    /** Koliko posle zatvaranja student još vidi prikazan rezultat: na času 2 h, za grupu 24 h. */
    public static final Duration REZULTAT_NA_CASU = Duration.ofHours(2);
    public static final Duration REZULTAT_GRUPA = Duration.ofHours(24);

    public static final String PITANJE = "Pitanje mora imati od 1 do " + MAX_PITANJE + " znakova.";
    public static final String BROJ_ODGOVORA = "Anketa mora imati od " + MIN_ODGOVORA + " do " + MAX_ODGOVORA + " odgovora.";
    public static final String ISTI_ODGOVORI = "Odgovori moraju biti različiti.";
    public static final String TRAJANJE = "Trajanje mora biti od " + MIN_TRAJANJE + " do " + MAX_TRAJANJE + " sekundi.";
    public static final String ROK_SATI = "Rok mora biti od " + MIN_ROK_SATI + " do " + MAX_ROK_SATI + " sati.";
    public static final String IKONICA = "Ikonica može imati najviše " + MAX_IKONICA + " znakova.";
    public static final String KRATAK_NAZIV = "Kratak naziv može imati najviše " + MAX_KRATAK_NAZIV + " znakova.";
    public static final String NAZIV_FOLDERA = "Naziv foldera mora imati od 1 do " + MAX_NAZIV_FOLDERA + " znakova.";
    public static final String JEDAN_ODGOVOR = "Izaberi jedan odgovor.";
    public static final String BAR_JEDAN_ODGOVOR = "Izaberi bar jedan odgovor.";
    public static final String NEISPRAVAN_ODGOVOR = "Neispravan odgovor.";

    private AnketaPravila() {
    }

    /** Stanje ankete: {@code zatvoreno} je efektivno vreme zatvaranja ({@code null} dok je otvorena ili kad se ne zna). */
    public record Stanje(boolean otvorena, LocalDateTime zatvoreno) {
    }

    // ---------------------------------------------------------------- unos

    /** Pitanje posle trim-a, 1 do {@value #MAX_PITANJE} znakova; inače 400. */
    public static String pitanje(String pitanje) {
        String p = pitanje == null ? "" : pitanje.strip();
        if (p.isEmpty() || znakova(p) > MAX_PITANJE) throw los(PITANJE);
        return p;
    }

    /**
     * Odgovori posle trim-a: {@value #MIN_ODGOVORA} do {@value #MAX_ODGOVORA}, svaki 1 do {@value #MAX_ODGOVOR} znakova,
     * međusobno različiti bez obzira na velika i mala slova; inače 400.
     */
    public static List<String> odgovori(List<String> odgovori) {
        if (odgovori == null || odgovori.size() < MIN_ODGOVORA || odgovori.size() > MAX_ODGOVORA) throw los(BROJ_ODGOVORA);
        List<String> rez = new ArrayList<>(odgovori.size());
        Set<String> videni = new HashSet<>();
        for (int i = 0; i < odgovori.size(); i++) {
            String o = odgovori.get(i) == null ? "" : odgovori.get(i).strip();
            if (o.isEmpty() || znakova(o) > MAX_ODGOVOR) {
                throw los("Odgovor " + (i + 1) + " mora imati od 1 do " + MAX_ODGOVOR + " znakova.");
            }
            if (!videni.add(o.toLowerCase(Locale.ROOT))) throw los(ISTI_ODGOVORI);
            rez.add(o);
        }
        return List.copyOf(rez);
    }

    /** Trajanje ankete na času u sekundama: {@code null} (dok je nastavnik ne zatvori) ili 10 do 1800; inače 400. */
    public static Integer trajanje(Integer sekunde) {
        if (sekunde != null && (sekunde < MIN_TRAJANJE || sekunde > MAX_TRAJANJE)) throw los(TRAJANJE);
        return sekunde;
    }

    /** Podrazumevani rok šablona za grupu u satima: {@code null} ili 1 do 336; inače 400. */
    public static Integer rokSati(Integer sati) {
        if (sati != null && (sati < MIN_ROK_SATI || sati > MAX_ROK_SATI)) throw los(ROK_SATI);
        return sati;
    }

    /**
     * Opcioni kratak tekst (ikonica, kratak naziv): posle trim-a prazan je {@code null}, duži od {@code max} znakova ili sa
     * kontrolnim znakom (novi red, tab) 400 {@code poruka}.
     */
    public static String opcioniTekst(String tekst, int max, String poruka) {
        if (tekst == null) return null;
        String t = tekst.strip();
        if (t.isEmpty()) return null;
        if (znakova(t) > max || t.codePoints().anyMatch(c -> Character.getType(c) == Character.CONTROL
                || Character.getType(c) == Character.LINE_SEPARATOR || Character.getType(c) == Character.PARAGRAPH_SEPARATOR)) {
            throw los(poruka);
        }
        return t;
    }

    /** Naziv foldera posle trim-a, 1 do {@value #MAX_NAZIV_FOLDERA} znakova; inače 400. */
    public static String nazivFoldera(String naziv) {
        String n = naziv == null ? "" : naziv.strip();
        if (n.isEmpty() || znakova(n) > MAX_NAZIV_FOLDERA) throw los(NAZIV_FOLDERA);
        return n;
    }

    /**
     * Glas: neprazan skup različitih indeksa u opsegu {@code 0..brojOdgovora-1}, za jedan odgovor tačno jedan. Vraća
     * indekse rastuće; inače 400 ({@value #JEDAN_ODGOVOR}, {@value #BAR_JEDAN_ODGOVOR} ili {@value #NEISPRAVAN_ODGOVOR}).
     */
    public static List<Integer> glas(List<Integer> izbor, int brojOdgovora, boolean viseOdgovora) {
        if (izbor == null || izbor.isEmpty()) throw los(viseOdgovora ? BAR_JEDAN_ODGOVOR : JEDAN_ODGOVOR);
        if (!viseOdgovora && izbor.size() != 1) throw los(JEDAN_ODGOVOR);
        TreeSet<Integer> skup = new TreeSet<>();
        for (Integer i : izbor) {
            if (i == null || i < 0 || i >= brojOdgovora || !skup.add(i)) throw los(NEISPRAVAN_ODGOVOR);
        }
        return List.copyOf(skup);
    }

    /** Indeksi u koloni {@code ankete_glasovi.opcije}: {@code "0,2"}. */
    public static String uKolonu(List<Integer> izbor) {
        StringJoiner j = new StringJoiner(",");
        izbor.forEach(i -> j.add(Integer.toString(i)));
        return j.toString();
    }

    /** Indeksi iz kolone; neispravan deo se preskače (kolonu piše samo {@link #uKolonu}). */
    public static List<Integer> izKolone(String kolona) {
        List<Integer> rez = new ArrayList<>();
        if (kolona == null || kolona.isBlank()) return rez;
        for (String deo : kolona.split(",")) {
            try {
                rez.add(Integer.parseInt(deo.strip()));
            } catch (NumberFormatException e) {
                // preskače se
            }
        }
        return rez;
    }

    // ---------------------------------------------------------------- stanje

    /**
     * Izračunato stanje (spec 3): otvorena je dok {@code zatvoreno} nije upisano i rok nije prošao, a za
     * {@link PublikaAnkete#NA_CASU} još i dok predavanje postoji i nije završeno i nije prošlo {@link #NAJDUZE_NA_CASU} od
     * pokretanja. {@code predavanjeZavrseno} je {@code null} kad predavanja nema (obrisano: anketa na času je tada zatvorena).
     * Efektivno vreme zatvaranja je najranije od upisanog zatvaranja, prošlog roka i isteka {@link #NAJDUZE_NA_CASU}; kad
     * ništa od toga ne važi (predavanje obrisano), {@code null}.
     */
    public static Stanje stanje(Anketa a, Boolean predavanjeZavrseno, LocalDateTime sada) {
        boolean naCasu = a.getPublika() == PublikaAnkete.NA_CASU;
        LocalDateTime najduze = naCasu ? a.getOtvoreno().plus(NAJDUZE_NA_CASU) : null;
        boolean otvorena = a.getZatvoreno() == null
                && (a.getRok() == null || sada.isBefore(a.getRok()))
                && (!naCasu || (Boolean.FALSE.equals(predavanjeZavrseno) && sada.isBefore(najduze)));
        if (otvorena) return new Stanje(true, null);
        LocalDateTime zatvoreno = a.getZatvoreno();
        if (a.getRok() != null && !sada.isBefore(a.getRok())) zatvoreno = ranije(zatvoreno, a.getRok());
        if (najduze != null && !sada.isBefore(najduze)) zatvoreno = ranije(zatvoreno, najduze);
        return new Stanje(false, zatvoreno);
    }

    /**
     * Da li student vidi zatvorenu anketu sa prikazanim rezultatom (spec 5): na času dok predavanje traje
     * ({@code predavanjeZavrseno == FALSE}) i najviše {@link #REZULTAT_NA_CASU} posle efektivnog zatvaranja, za grupu najviše
     * {@link #REZULTAT_GRUPA} posle zatvaranja. Otvorenu vidi uvek.
     */
    public static boolean vidiStudent(Anketa a, Stanje s, Boolean predavanjeZavrseno, LocalDateTime sada) {
        if (s.otvorena()) return true;
        if (!a.isRezultatPrikazan() || s.zatvoreno() == null) return false;
        if (a.getPublika() == PublikaAnkete.NA_CASU) {
            return Boolean.FALSE.equals(predavanjeZavrseno) && !s.zatvoreno().plus(REZULTAT_NA_CASU).isBefore(sada);
        }
        return !s.zatvoreno().plus(REZULTAT_GRUPA).isBefore(sada);
    }

    // ---------------------------------------------------------------- zbirovi

    /** Broj glasova po odgovoru (za više odgovora zbir po opciji); indeks van opsega se preskače. */
    public static List<Integer> poOdgovoru(int brojOdgovora, Collection<String> glasovi) {
        int[] broj = new int[brojOdgovora];
        for (String g : glasovi) {
            for (int i : izKolone(g)) {
                if (i >= 0 && i < brojOdgovora) broj[i]++;
            }
        }
        List<Integer> rez = new ArrayList<>(brojOdgovora);
        for (int b : broj) rez.add(b);
        return rez;
    }

    /** Odgovor sa najviše glasova; {@code null} kad nema glasova ili je više odgovora izjednačeno na vrhu. */
    public static String pobednik(List<String> odgovori, List<Integer> poOdgovoru) {
        int najvise = 0;
        int indeks = -1;
        boolean nereseno = false;
        for (int i = 0; i < poOdgovoru.size(); i++) {
            int b = poOdgovoru.get(i);
            if (b > najvise) {
                najvise = b;
                indeks = i;
                nereseno = false;
            } else if (b == najvise && b > 0) {
                nereseno = true;
            }
        }
        return indeks < 0 || nereseno ? null : odgovori.get(indeks);
    }

    /** Epoch ms lokalnog vremena u zoni sata aplikacije ({@code null} ostaje {@code null}). */
    public static Long ms(LocalDateTime t, ZoneId zona) {
        return t == null ? null : t.atZone(zona).toInstant().toEpochMilli();
    }

    static int znakova(String s) {
        return s.codePointCount(0, s.length());
    }

    private static LocalDateTime ranije(LocalDateTime a, LocalDateTime b) {
        return a == null || b.isBefore(a) ? b : a;
    }

    private static SystemException los(String poruka) {
        return new SystemException(poruka, HttpStatus.BAD_REQUEST);
    }
}
