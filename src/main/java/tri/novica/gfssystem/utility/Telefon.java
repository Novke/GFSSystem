package tri.novica.gfssystem.utility;

/**
 * Broj telefona u međunarodnom obliku za kontakte ({@code +381641234567}), da ga telefon i WhatsApp prepoznaju bez obzira
 * na to kako ga je student upisao ({@code 064 123 4567}, {@code 064/123-45-67}, {@code 00381...}, {@code 381...}).
 * Brojevi bez vodeće nule i bez pozivnog broja ostaju kakvi jesu (samo cifre): bolje nepromenjen nego pogrešno dopunjen.
 */
public final class Telefon {

    private Telefon() {
    }

    /** Null kad nema nijedne cifre. */
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
        if (cifre.startsWith("381") && cifre.length() >= 11) {
            return "+" + cifre;
        }
        if (cifre.startsWith("0")) {
            return "+381" + cifre.substring(1);
        }
        return cifre;
    }
}
