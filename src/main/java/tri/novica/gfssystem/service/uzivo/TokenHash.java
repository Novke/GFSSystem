package tri.novica.gfssystem.service.uzivo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 (mala slova, hex, 64 znaka) tokena iz kolačića {@code gfs_uzivo}: u bazi je samo heš, nikad token. */
public final class TokenHash {

    private TokenHash() {}

    public static String od(String token) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nije dostupan", e);
        }
    }
}
