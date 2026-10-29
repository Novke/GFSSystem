package tri.novica.gfssystem.service.push;

import tri.novica.gfssystem.entity.prijava.VrstaObavestenja;
import tri.novica.gfssystem.service.raspored.PromenaTermina;
import tri.novica.gfssystem.service.raspored.TerminOpis;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Tekstovi push poruka i stavki zvona (spec aplikacije 6.1-6.6). Sastavljaju se <b>samo</b> iz podataka iz baze koje je
 * upisao nastavnik (predmet, grupa, vreme, napomena otkazivanja) i iz brojeva; nikad ocena, ime studenta, razlog odsustva
 * ni tekst koji je student ukucao, jer se poruka vidi na zaključanom ekranu.
 */
public final class PushTekstovi {

    /** Najviše znakova napomene nastavnika u poruci o otkazanom času. */
    static final int MAX_NAPOMENA = 120;
    static final int MAX_NASLOV = 120;
    static final int MAX_TEKST = 400;
    private static final String[] DANI = {"pon", "uto", "sre", "čet", "pet", "sub", "ned"};

    private PushTekstovi() {}

    /** {@code čet 30.10.} */
    public static String danIDatum(LocalDate d) {
        return DANI[d.getDayOfWeek().getValue() - 1] + " " + d.getDayOfMonth() + "." + d.getMonthValue() + ".";
    }

    /** {@code Osnove računarstva, čet 30.10. u 10:15.} */
    static String termin(TerminOpis t) {
        return t.predmet() + ", " + danIDatum(t.datum()) + " u " + TerminOpis.hhmm(t.pocetak()) + ".";
    }

    /** Otkazan čas ili "ipak se održava" (6.2): putanja {@code nalog} (tab Čas), oznaka po terminu (vraćanje zamenjuje otkazivanje). */
    public static PushPoruka promena(PromenaTermina p) {
        TerminOpis t = p.termin();
        String oznaka = "cas-" + t.stavkaId() + "-" + t.datum();
        if (p.vrsta() == PromenaTermina.Vrsta.OTKAZAN) {
            String tekst = termin(t);
            if (p.napomena() != null && !p.napomena().isBlank()) {
                tekst += " " + skrati(p.napomena().strip(), MAX_NAPOMENA);
            }
            return poruka("Čas je otkazan", tekst, "nalog", oznaka);
        }
        return poruka("Čas se ipak održava", termin(t), "nalog", oznaka);
    }

    /** Jedan sutrašnji termin u podsetniku. */
    public record SutrasnjiTermin(LocalTime pocetak, String predmet) {
    }

    /** Podsetnik dan pre časa (6.1): svi sutrašnji termini, bez odsustva i bez roka. */
    public static PushPoruka podsetnik(LocalDate sutra, List<SutrasnjiTermin> termini) {
        StringBuilder b = new StringBuilder();
        for (SutrasnjiTermin t : termini) {
            if (!b.isEmpty()) b.append(" · ");
            b.append(TerminOpis.hhmm(t.pocetak())).append(' ').append(t.predmet());
        }
        String spisak = skrati(b.toString(), MAX_TEKST - " Vidimo se!".length() - 1);
        return poruka(termini.size() == 1 ? "Sutra imaš čas" : "Sutra imaš časove", spisak + ". Vidimo se!", "nalog/cas",
                "podsetnik-" + sutra);
    }

    /** Ocene objavljene ili ažurirane (6.3), bez broja. */
    public static PushPoruka ocene(Long predmetId, String predmet, boolean ponovo) {
        return poruka(ponovo ? "Ocene su ažurirane" : "Ocene su objavljene", predmet + ": pogledaj predlog ocene.",
                "nalog/predmet/" + predmetId, "ocene-" + predmetId);
    }

    /**
     * Zbirno posle časa (6.4): {@code prezentacija} = broj prezentacija sa novim slajdovima, {@code tipova} = broj novih tipova
     * za vežbanje; {@code predmetId} vodi na stranicu predmeta kad je predmet jedan, inače {@code null} (nalog).
     */
    public static PushPoruka posleCasa(LocalDate datum, int prezentacija, int tipova, Long predmetId) {
        String tekst;
        if (prezentacija > 0 && tipova > 0) {
            tekst = prezentacije(prezentacija) + " i " + tipova(tipova) + " za vežbanje.";
        } else if (prezentacija > 0) {
            tekst = prezentacije(prezentacija) + ".";
        } else {
            tekst = tipova == 1 ? "Dostupan je 1 nov tip za vežbanje."
                    : (paukal(tipova) ? "Dostupna su " : "Dostupno je ") + tipova(tipova) + " za vežbanje.";
        }
        return poruka("Posle današnjeg časa", tekst, predmetId == null ? "nalog" : "nalog/predmet/" + predmetId,
                "posle-casa-" + datum);
    }

    private static String prezentacije(int n) {
        return n == 1 ? "Prezentacija je dostupna za ponavljanje" : "Prezentacije su dostupne za ponavljanje";
    }

    /** {@code 1 nov tip}, {@code 2 nova tipa}, {@code 5 novih tipova}, {@code 21 nov tip}. */
    static String tipova(int n) {
        int d = n % 10;
        int s = n % 100;
        String rec = d == 1 && s != 11 ? "nov tip" : paukal(n) ? "nova tipa" : "novih tipova";
        return n + " " + rec;
    }

    private static boolean paukal(int n) {
        int d = n % 10;
        int s = n % 100;
        return d >= 2 && d <= 4 && (s < 12 || s > 14);
    }

    /** Push nastavniku (6.6): bez imena studenta i bez razloga. */
    public static PushPoruka nastavniku(VrstaObavestenja vrsta, TerminOpis termin, long broj, int prag) {
        return switch (vrsta) {
            case MNOGO_NAJAVA -> poruka(TerminOpis.studenata(broj) + " neće doći",
                    termin.predmet() + ", " + termin.grupa() + ", " + danIDatum(termin.datum()) + " u "
                            + TerminOpis.hhmm(termin.pocetak()) + " (prag " + prag + ").",
                    "raspored/termini?od=" + termin.datum(), "mnogo-najava-" + termin.stavkaId() + "-" + termin.datum());
            case NE_STIZE_KOD -> poruka("Studentu ne stiže kod", "Otvori obaveštenja.", "?obavestenja=otvori",
                    "ne-stize-kod");
            case PREVISE_KODOVA -> poruka("Previše zahteva za kod", "Otvori obaveštenja.", "?obavestenja=otvori",
                    "previse-kodova");
        };
    }

    /** Probno obaveštenje (ugovor API-ja): studentu putanja {@code nalog}, nastavniku prazna (kontrolna tabla). */
    public static PushPoruka proba(boolean student) {
        return new PushPoruka("GFS radi", "Obaveštenja na ovom uređaju su uključena.", student ? "nalog" : "", "proba");
    }

    private static PushPoruka poruka(String naslov, String tekst, String putanja, String oznaka) {
        return new PushPoruka(skrati(naslov, MAX_NASLOV), skrati(tekst, MAX_TEKST), putanja, oznaka);
    }

    /** Najviše {@code max} znakova (code point-a), sa {@code …} na kraju kad je skraćeno. */
    static String skrati(String s, int max) {
        if (s.codePointCount(0, s.length()) <= max) {
            return s;
        }
        int kraj = s.offsetByCodePoints(0, max - 1);
        return s.substring(0, kraj).stripTrailing() + "…";
    }
}
