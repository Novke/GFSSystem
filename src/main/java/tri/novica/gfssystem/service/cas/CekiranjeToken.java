package tri.novica.gfssystem.service.cas;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/**
 * Token QR-a za čekiranje (spec studentskog dela 4.3): {@code <predavanjeId>.<h>}, gde je {@code h} prvih 22 znaka
 * {@code base64url(HMAC-SHA256(tajna, predavanjeId + ":" + w))} za prozor {@code w = floor(epochSekunde / 10)}. Broj
 * predavanja nije tajna (kaže čiju tajnu proveriti); tajna nikad ne napušta server. Važe prozori {@code w}, {@code w-1}
 * i {@code w-2} (20-30 s), budući nikad. Čista logika, vreme dolazi spolja.
 */
public final class CekiranjeToken {

    public static final int PROZOR_S = 10;
    public static final int PRIHVACENIH_PROZORA = 3;
    public static final int DUZINA_HMAC = 22;

    private static final Pattern OBLIK = Pattern.compile("^([1-9][0-9]{0,18})\\.[A-Za-z0-9_-]{22}$");

    private CekiranjeToken() {}

    /** Token tekućeg prozora za {@code epochMs}. */
    public static String token(String tajna, long predavanjeId, long epochMs) {
        return predavanjeId + "." + hmac(tajna, predavanjeId, prozor(epochMs));
    }

    /** Kraj tekućeg prozora (epoch ms): projektor posle njega traži nov token. */
    public static long vaziDoMs(long epochMs) {
        return (prozor(epochMs) + 1) * PROZOR_S * 1000L;
    }

    /** Broj predavanja iz tokena; prazno za sve što nema tačan oblik (i za {@code null}). */
    public static OptionalLong predavanjeIz(String token) {
        if (token == null) return OptionalLong.empty();
        var m = OBLIK.matcher(token);
        if (!m.matches()) return OptionalLong.empty();
        try {
            return OptionalLong.of(Long.parseLong(m.group(1)));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /** Da li je {@code token} token predavanja za jedan od poslednja tri prozora; poređenje u konstantnom vremenu. */
    public static boolean vazi(String tajna, long predavanjeId, String token, long epochMs) {
        if (tajna == null || token == null) return false;
        OptionalLong id = predavanjeIz(token);
        if (id.isEmpty() || id.getAsLong() != predavanjeId) return false;
        byte[] dobijen = token.getBytes(StandardCharsets.US_ASCII);
        long w = prozor(epochMs);
        boolean vazi = false;
        for (int i = 0; i < PRIHVACENIH_PROZORA; i++) {
            byte[] ocekivan = (predavanjeId + "." + hmac(tajna, predavanjeId, w - i)).getBytes(StandardCharsets.US_ASCII);
            vazi |= MessageDigest.isEqual(ocekivan, dobijen);
        }
        return vazi;
    }

    static long prozor(long epochMs) {
        return Math.floorDiv(epochMs, PROZOR_S * 1000L);
    }

    private static String hmac(String tajna, long predavanjeId, long prozor) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tajna.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal((predavanjeId + ":" + prozor).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h).substring(0, DUZINA_HMAC);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 nije dostupan", e);
        }
    }
}
