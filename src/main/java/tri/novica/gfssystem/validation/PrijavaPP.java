package tri.novica.gfssystem.validation;

import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.dto.onboarding.PoljaPrijave;
import tri.novica.gfssystem.entity.Prijava;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.utility.IndeksUtil;
import tri.novica.gfssystem.utility.SkolskaGodina;
import tri.novica.gfssystem.utility.Telefon;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pravila za polja onboarding prijave (spec odluka 4 i 5). Ne dira bazu. Indeks se čuva kanonski
 * ({@link IndeksUtil#kanonski}: {@code GD05/2026} uz godinu 2026 -> {@code GD5}), telefon u E.164 ({@link Telefon#uE164}).
 * Godinu bira student (forma je predlaže iz grupe sesije, ali je ponovac ili stariji student menja), 2000 do tekuće
 * školske godine + 1; student se upisuje u grupu sesije sa svojom godinom, a dedupe je po (kanonski indeks, godina).
 */
public final class PrijavaPP {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** Normalizovan indeks (velika slova), samo latinica: ćirilično "ГД12" ne sme da zaobiđe dedupe protiv "GD12". */
    private static final Pattern INDEKS = Pattern.compile("^[A-Z0-9/.-]{2,20}$");

    /** Najmanja godina upisa u formi. */
    public static final int MIN_GODINA = 2000;

    private PrijavaPP() {}

    /** Najveća godina upisa: tekuća školska godina + 1 (1. 10. Y do 30. 9. Y+1 je školska godina Y). */
    public static int maxGodina(Clock clock) {
        return SkolskaGodina.tekuca(clock) + 1;
    }

    /** Validira i normalizuje polja i upisuje ih u {@code cilj}; pri grešci baca 400 i ne menja {@code cilj}. */
    public static void primeni(PoljaPrijave p, Prijava cilj, Clock clock) {
        List<String> greske = new ArrayList<>();
        String ime = ime(p.getIme(), "Ime je obavezno (najviše 60 znakova).", greske);
        String prezime = ime(p.getPrezime(), "Prezime je obavezno (najviše 60 znakova).", greske);
        Integer godina = p.getGodina();
        int maxGodina = maxGodina(clock);
        if (godina == null || godina < MIN_GODINA || godina > maxGodina) {
            greske.add("Godina upisa mora biti između " + MIN_GODINA + " i " + maxGodina + ".");
        }
        String normalizovan = IndeksUtil.normalizuj(p.getIndeks());
        // kanonski posle provere godine: neispravna godina ne skida sufiks
        String indeks = IndeksUtil.kanonski(p.getIndeks(), godina != null && greske.isEmpty() ? godina : null);
        if (normalizovan == null || !INDEKS.matcher(normalizovan).matches() || !INDEKS.matcher(indeks).matches()) {
            greske.add("Indeks mora imati od 2 do 20 znakova, latinicom (slova A-Z, cifre, / . -).");
        }
        String email = prazanUNull(p.getEmail());
        if (email == null || email.length() > 120 || !EMAIL.matcher(email).matches()) {
            greske.add("Email nije ispravan.");
        }
        String telefon = Telefon.uE164(p.getBrojTelefona());
        if (telefon == null) {
            greske.add("Broj telefona nije ispravan. Unesi ga sa pozivnim brojem (npr. +381 64 1234567) ili kao 064 1234567.");
        }
        LocalDate datum = p.getDatumRodjenja();
        if (datum != null && (datum.isAfter(LocalDate.now(clock)) || datum.getYear() < 1920)) {
            greske.add("Datum rođenja nije ispravan.");
        }
        String opstina = prazanUNull(p.getOpstina());
        if (opstina != null && opstina.length() > 100) {
            greske.add("Opština može imati najviše 100 znakova.");
        }
        if (!greske.isEmpty()) {
            throw new SystemException(String.join(" ", greske), HttpStatus.BAD_REQUEST);
        }
        cilj.setIme(ime);
        cilj.setPrezime(prezime);
        cilj.setIndeks(indeks);
        cilj.setGodina(godina);
        cilj.setEmail(email.toLowerCase(Locale.ROOT));
        cilj.setBrojTelefona(telefon);
        cilj.setDatumRodjenja(datum);
        cilj.setOpstina(opstina);
    }

    private static String ime(String v, String poruka, List<String> greske) {
        String s = v == null ? "" : v.trim().replaceAll("\\s+", " ");
        if (s.isEmpty() || s.length() > 60) {
            greske.add(poruka);
        }
        return s;
    }

    private static String prazanUNull(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }
}
