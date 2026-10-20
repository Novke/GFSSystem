package tri.novica.gfssystem.service.prijava;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Locale;

/**
 * Maska mejla za odgovor na zahtev za kod (spec 5.1): {@code n***@gmail.com}. Za nepostojeći indeks ili studenta bez
 * mejla {@link #lazna} daje masku istog oblika, determinističku po indeksu (HMAC-SHA256 ključem {@code MASKA_HMAC} iz baze),
 * pa se po odgovoru ne vidi da li nalog postoji, a ponovljen zahtev ne otkriva razliku. Bez ključa iz baze se lažna maska
 * ne može izračunati unapred.
 */
@Component
public class MaskaMejla {

    /** Domeni lažne maske; {@code gmail.com} dvaput, jer je najčešći. */
    static final List<String> DOMENI = List.of("gmail.com", "yahoo.com", "hotmail.com", "gmail.com");

    private final KljuceviAplikacije kljucevi;

    public MaskaMejla(KljuceviAplikacije kljucevi) {
        this.kljucevi = kljucevi;
    }

    /** Prvo slovo lokalnog dela + {@code ***@} + domen, malim slovima; {@code null} za prazan mejl. */
    public String maska(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String e = email.trim().toLowerCase(Locale.ROOT);
        int at = e.lastIndexOf('@');
        if (at < 0) {
            return e.charAt(0) + "***";
        }
        String prvi = at == 0 ? "" : String.valueOf(e.charAt(0));
        return prvi + "***@" + e.substring(at + 1);
    }

    /** Lažna maska za normalizovan indeks: slovo {@code a-z} iz prvog bajta HMAC-a, domen po drugom. */
    public String lazna(String normalizovanIndeks) {
        byte[] h = hmac(normalizovanIndeks == null ? "" : normalizovanIndeks);
        char slovo = (char) ('a' + (h[0] & 0xff) % 26);
        return slovo + "***@" + DOMENI.get((h[1] & 0xff) % DOMENI.size());
    }

    private byte[] hmac(String tekst) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(kljucevi.kljuc(KljuceviAplikacije.MASKA_HMAC), "HmacSHA256"));
            return mac.doFinal(tekst.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 nije dostupan", e);
        }
    }
}
