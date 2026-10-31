package tri.novica.gfssystem.service.push;

import java.util.Base64;
import java.util.regex.Pattern;

/** base64url (RFC 4648 §5) bez dopune; čitanje prihvata i dopunu {@code =}, ali ne i obične base64 znake {@code + /}. */
public final class Base64Url {

    private static final Pattern OBLIK = Pattern.compile("^[A-Za-z0-9_-]*={0,2}$");
    private static final Base64.Encoder KODER = Base64.getUrlEncoder().withoutPadding();

    private Base64Url() {}

    public static String kodiraj(byte[] b) {
        return KODER.encodeToString(b);
    }

    /** Bajtovi ili {@code null} kad niska nije ispravan base64url (bez izuzetka). */
    public static byte[] dekodiraj(String s) {
        if (s == null || s.isEmpty() || s.length() > 4096 || !OBLIK.matcher(s).matches()) {
            return null;
        }
        try {
            return Base64.getUrlDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
