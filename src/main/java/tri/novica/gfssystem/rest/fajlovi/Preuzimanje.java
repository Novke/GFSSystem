package tri.novica.gfssystem.rest.fajlovi;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tri.novica.gfssystem.entity.fajlovi.FajlPrezentacije;
import tri.novica.gfssystem.service.uzivo.MedijService;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/**
 * Odgovor preuzimanja fajla uz prezentaciju, jedan za sva tri puta (nastavnik, čas po kodu, nalog; spec 5.1):
 * {@code Content-Type} iz baze, {@code attachment} sa {@code filename} (ASCII zamena) i {@code filename*=UTF-8''...},
 * {@code nosniff}, {@code CSP sandbox}, {@code no-store}, {@code Content-Length}. Pravila "ko sme" su u
 * {@code PristupFajlovima}, ovde ih nema.
 */
public final class Preuzimanje {

    private Preuzimanje() {
    }

    public static ResponseEntity<Resource> odgovor(FajlPrezentacije f, MedijService.Fajl fajl) {
        return odgovor(f.getNaziv(), fajl);
    }

    public static ResponseEntity<Resource> odgovor(String naziv, MedijService.Fajl fajl) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fajl.mime()))
                .contentLength(fajl.velicina())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(naziv))
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(fajl.sadrzaj());
    }

    /** {@code attachment; filename="ascii"; filename*=UTF-8''procenat-kodirano}. */
    static String disposition(String naziv) {
        return "attachment; filename=\"" + asciiZamena(naziv) + "\"; filename*=UTF-8''" + rfc5987(naziv);
    }

    /** Slova bez dijakritika (č -> c, đ -> dj), ostalo van ASCII-ja i navodnici postaju {@code _}. */
    static String asciiZamena(String naziv) {
        String s = naziv.replace("đ", "dj").replace("Đ", "Dj");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(c >= 0x20 && c < 0x7F && c != '"' && c != '\\' ? c : '_');
        }
        return sb.toString();
    }

    /** RFC 5987: sve osim {@code attr-char} se kodira kao UTF-8 bajtovi. */
    static String rfc5987(String naziv) {
        StringBuilder sb = new StringBuilder();
        for (byte b : naziv.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            boolean attr = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || "!#$&+-.^_`|~".indexOf(c) >= 0;
            if (attr) sb.append((char) c);
            else sb.append('%').append(String.format("%02X", c));
        }
        return sb.toString();
    }
}
