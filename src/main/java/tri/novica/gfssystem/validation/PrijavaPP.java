package tri.novica.gfssystem.validation;

import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.dto.onboarding.PoljaPrijave;
import tri.novica.gfssystem.entity.Prijava;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.utility.IndeksUtil;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Pravila za polja onboarding prijave (spec odluka 4 i 5). Ne dira bazu. */
public final class PrijavaPP {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern TELEFON = Pattern.compile("^[0-9 +\\-/]{6,20}$");
    /** Normalizovan indeks (velika slova), samo latinica: ćirilično "ГД12" ne sme da zaobiđe dedupe protiv "GD12". */
    private static final Pattern INDEKS = Pattern.compile("^[A-Z0-9/.-]{2,20}$");

    private PrijavaPP() {}

    /** Validira i normalizuje polja i upisuje ih u {@code cilj}; pri grešci baca 400 i ne menja {@code cilj}. */
    public static void primeni(PoljaPrijave p, Prijava cilj, Clock clock) {
        List<String> greske = new ArrayList<>();
        String ime = ime(p.getIme(), "Ime je obavezno (najviše 60 znakova).", greske);
        String prezime = ime(p.getPrezime(), "Prezime je obavezno (najviše 60 znakova).", greske);
        String indeks = IndeksUtil.normalizuj(p.getIndeks());
        if (indeks == null || !INDEKS.matcher(indeks).matches()) {
            greske.add("Indeks mora imati od 2 do 20 znakova, latinicom (slova A-Z, cifre, / . -).");
        }
        int ovaGodina = LocalDate.now(clock).getYear();
        Integer godina = p.getGodina();
        if (godina == null || godina < 2000 || godina > ovaGodina + 1) {
            greske.add("Godina upisa mora biti između 2000 i " + (ovaGodina + 1) + ".");
        }
        String email = prazanUNull(p.getEmail());
        if (email == null || email.length() > 120 || !EMAIL.matcher(email).matches()) {
            greske.add("Email nije ispravan.");
        }
        String telefon = prazanUNull(p.getBrojTelefona());
        if (telefon == null || !TELEFON.matcher(telefon).matches()) {
            greske.add("Broj telefona mora imati od 6 do 20 znakova (cifre, razmak, +, -, /).");
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
