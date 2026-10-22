package tri.novica.gfssystem.utility;

import java.util.regex.Pattern;

/**
 * Broj telefona. Čuva se u obliku E.164 ({@code +381641234567}): {@link #uE164} je pravilo za upis (javna forma, prijava,
 * student), {@link #uMedjunarodni} blaže pravilo za izvoz kontakata (stari redovi).
 * <p>
 * Šta se prepoznaje (ostalo je neispravno, bolje odbijeno nego pogrešno dopunjeno): razmaci, crtice, tačke, kose crte i
 * zagrade se brišu; {@code +} sme samo na početku; {@code 00} na početku = međunarodni prefiks; vodeća {@code 0} = lokalni
 * broj u Srbiji ({@code 064...} -> {@code +38164...}; front za druge zemlje šalje broj već sa pozivnim brojem);
 * {@code 381...} bez plusa sa bar 11 cifara = srpski broj bez plusa. Broj bez vodeće nule i bez pozivnog broja
 * ({@code 641234567}) je dvosmislen i nije E.164.
 */
public final class Telefon {

    /** E.164: plus, pozivni broj (ne počinje nulom), ukupno 8-15 cifara. */
    public static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    /** Podrazumevani pozivni broj za lokalni broj sa vodećom nulom. */
    public static final String SRBIJA = "381";
    private static final Pattern DOZVOLJENO = Pattern.compile("^\\+?[0-9\\s\\p{Z}().\\-/]+$");

    private Telefon() {
    }

    /** E.164 ili null kad unos nije prepoznat kao broj (prazan unos je takođe null). */
    public static String uE164(String broj) {
        if (broj == null) {
            return null;
        }
        String s = broj.strip();
        if (s.isEmpty() || !DOZVOLJENO.matcher(s).matches()) {
            return null;
        }
        String cifre = s.replaceAll("\\D", "");
        String rezultat;
        if (s.startsWith("+")) {
            rezultat = "+" + cifre;
        } else if (cifre.startsWith("00")) {
            rezultat = "+" + cifre.substring(2);
        } else if (cifre.startsWith("0")) {
            rezultat = "+" + SRBIJA + cifre.substring(1);
        } else if (cifre.startsWith(SRBIJA) && cifre.length() >= 11) {
            rezultat = "+" + cifre;
        } else {
            return null;
        }
        return E164.matcher(rezultat).matches() ? rezultat : null;
    }

    /** Za kontakte (vCard): E.164 kad se može, inače samo cifre (stari redovi). Null kad nema nijedne cifre. */
    public static String uMedjunarodni(String broj) {
        if (broj == null) {
            return null;
        }
        String cifre = broj.replaceAll("\\D", "");
        if (cifre.isEmpty()) {
            return null;
        }
        if (broj.strip().startsWith("+")) {
            return "+" + cifre;
        }
        if (cifre.startsWith("00")) {
            return "+" + cifre.substring(2);
        }
        if (cifre.startsWith(SRBIJA) && cifre.length() >= 11) {
            return "+" + cifre;
        }
        if (cifre.startsWith("0")) {
            return "+" + SRBIJA + cifre.substring(1);
        }
        return cifre;
    }
}
