package tri.novica.gfssystem.utility;

import java.util.List;

/**
 * Kontakti u vCard 3.0 (RFC 2426), jedan fajl za celu grupu: Android ga uveze kroz aplikaciju Kontakti (ili Google
 * Contacts), iPhone kroz pregled u Safariju. 3.0 zato što ga oba čitaju kao UTF-8 bez parametra CHARSET (ime sa
 * č, ć, đ, š, ž). Redovi se završavaju sa CRLF, kao što traži standard.
 */
public final class VCard {

    /** Jedan kontakt; {@code oznaka} (npr. naziv grupe) ide u sufiks imena i u kategoriju, da se kontakti lako nađu i obrišu. */
    public record Kontakt(String ime, String prezime, String oznaka, String telefon, String email, String beleska) {
    }

    private VCard() {
    }

    public static String zapisi(List<Kontakt> kontakti) {
        StringBuilder sb = new StringBuilder();
        for (Kontakt k : kontakti) {
            String ime = prazno(k.ime());
            String prezime = prazno(k.prezime());
            String oznaka = prazno(k.oznaka());
            red(sb, "BEGIN:VCARD");
            red(sb, "VERSION:3.0");
            red(sb, "N:" + tekst(prezime) + ";" + tekst(ime) + ";;;" + tekst(oznaka));
            red(sb, "FN:" + tekst(String.join(" ", List.of(ime, prezime, oznaka).stream().filter(s -> !s.isEmpty()).toList())));
            if (!prazno(k.telefon()).isEmpty()) {
                red(sb, "TEL;TYPE=CELL:" + tekst(k.telefon()));
            }
            if (!prazno(k.email()).isEmpty()) {
                red(sb, "EMAIL;TYPE=INTERNET:" + tekst(k.email().strip()));
            }
            if (!oznaka.isEmpty()) {
                red(sb, "CATEGORIES:" + tekst(oznaka));
            }
            if (!prazno(k.beleska()).isEmpty()) {
                red(sb, "NOTE:" + tekst(k.beleska()));
            }
            red(sb, "END:VCARD");
        }
        return sb.toString();
    }

    private static void red(StringBuilder sb, String red) {
        sb.append(red).append("\r\n");
    }

    private static String prazno(String s) {
        return s == null ? "" : s.strip();
    }

    /** Escape vrednosti po RFC 2426 (\\, zarez, tačka-zarez, novi red), da ime sa zarezom ne pomeri polja. */
    static String tekst(String s) {
        return s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;")
                .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    }
}
