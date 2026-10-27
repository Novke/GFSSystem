package tri.novica.gfssystem.service.mejl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sastavlja mejlove aplikacije u zajedničkom izgledu ({@link MejlSablon}): kod za prijavu studentu i obaveštenja nastavniku.
 * Logo je {@code <prvi https URL iz gfs.front.url>/assets/images/mejl-logo.png} (javna putanja fronta; bez https URL-a,
 * npr. lokalno, mejl je bez slike).
 */
@Component
public class MejlPoruke {

    /** Naslov mejla sa kodom: kratak i uvek isti, bez koda (kod u naslovu se vidi na zaključanom ekranu). */
    public static final String NASLOV_KODA = "Kod za prijavu u GFS";
    static final String LOGO = "assets/images/mejl-logo.png";
    private static final int MAX_IME = 40;

    private final String logoUrl;

    public MejlPoruke(@Value("${gfs.front.url:}") String frontUrl) {
        this.logoUrl = logo(frontUrl);
    }

    /**
     * Kod za prijavu studenta. {@code ime} je ime studenta iz baze (samo za pozdrav, nikad u naslovu ni logu);
     * {@code odNastavnika} menja rečenicu o tome zašto je mejl stigao ("Pošalji kod" sa kartice studenta).
     */
    public MejlPoruka kod(String za, String ime, String kod, long minuta, boolean odNastavnika) {
        String i = ime(ime);
        MejlSablon.Sadrzaj s = new MejlSablon.Sadrzaj(
                i == null ? "Zdravo!" : "Zdravo, " + i + "!",
                List.of("Tvoj kod za prijavu u GFS je:"),
                kod,
                List.of(
                        "Kod važi " + minuta + " minuta i može se iskoristiti samo jednom. Upiši ga na stranici za prijavu, "
                                + "odmah posle broja indeksa i godine upisa.",
                        odNastavnika
                                ? "Ovaj kod ti je poslao nastavnik iz aplikacije GFS."
                                : "Ovaj mejl je stigao jer je na stranici za prijavu upisan tvoj broj indeksa.",
                        "Ako nisi ti tražio/la kod, zanemari ovu poruku: bez koda niko ne može da se prijavi kao ti."),
                logoUrl);
        return new MejlPoruka(za, NASLOV_KODA, MejlSablon.tekst(s), MejlSablon.html(s));
    }

    /**
     * Obaveštenje nastavniku: {@code telo} (čist tekst, pasusi odvojeni praznim redom) ostaje kakvo jeste, a HTML deo nosi
     * iste pasuse u zajedničkom izgledu.
     */
    public MejlPoruka nastavniku(String za, String naslov, String telo) {
        List<String> pasusi = new ArrayList<>();
        for (String p : telo.split("\\n\\s*\\n")) {
            if (!p.isBlank()) {
                pasusi.add(p.strip().replaceAll("\\s*\\n\\s*", " · "));
            }
        }
        MejlSablon.Sadrzaj s = new MejlSablon.Sadrzaj(null, pasusi, null, List.of(), logoUrl);
        return new MejlPoruka(za, naslov, telo, MejlSablon.html(s));
    }

    /** Prvo ime, bez kontrolnih i formatnih znakova, najviše {@value #MAX_IME} znakova; null kad ga nema. */
    static String ime(String ime) {
        if (ime == null) {
            return null;
        }
        String s = ime.replaceAll("[\\p{Cc}\\p{Cf}]", "").strip().replaceAll("\\s+", " ");
        if (s.isEmpty()) {
            return null;
        }
        return s.length() > MAX_IME ? s.substring(0, MAX_IME).strip() : s;
    }

    static String logo(String frontUrl) {
        if (frontUrl == null) {
            return null;
        }
        for (String u : frontUrl.split(",")) {
            String s = u.strip();
            if (s.startsWith("https://")) {
                while (s.endsWith("/")) {
                    s = s.substring(0, s.length() - 1);
                }
                return s + "/" + LOGO;
            }
        }
        return null;
    }

    String logoUrl() {
        return logoUrl;
    }
}
