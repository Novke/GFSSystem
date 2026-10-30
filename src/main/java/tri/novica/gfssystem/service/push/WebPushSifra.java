package tri.novica.gfssystem.service.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

/**
 * Šifrovanje Web Push poruke, RFC 8291 sa kodiranjem {@code aes128gcm} iz RFC 8188, samo JDK kriptografijom: ECDH P-256
 * sa efemernim ključem servera, HKDF-SHA256 (RFC 8291 3.4: {@code PRK_key = HMAC(auth, ecdh)}, {@code IKM = HMAC(PRK_key,
 * "WebPush: info" 0x00 || ua_public || as_public || 0x01)}; pa RFC 8188: {@code PRK = HMAC(salt, IKM)}, CEK i NONCE),
 * AES-128-GCM, jedan zapis ({@code rs} = 4096) sa graničnikom poslednjeg zapisa {@code 0x02} i bez dodatne dopune.
 * Telo: {@code salt(16) || rs(4) || idlen(1) = 65 || as_public(65) || šifrat}. Proveren vektorom iz RFC 8291 odeljak 5.
 */
public final class WebPushSifra {

    static final int RS = 4096;
    static final int DUZINA_SOLI = 16;
    static final int DUZINA_AUTH = 16;
    private static final int DUZINA_TAGA = 16;
    /** Najviše bajtova poruke u jednom zapisu: {@code rs - graničnik - tag}. */
    public static final int MAX_PORUKA = RS - 1 - DUZINA_TAGA;

    private static final byte[] INFO_KLJUC = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] INFO_CEK = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] INFO_NONCE = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);

    private WebPushSifra() {}

    /** Šifruje poruku za pretplatu ({@code p256dh}, {@code auth}) sa novim efemernim ključem i solju. */
    public static byte[] sifruj(byte[] uaJavni, byte[] authTajna, byte[] poruka, SecureRandom random) {
        KeyPair efemerni = P256.nov(random);
        byte[] so = new byte[DUZINA_SOLI];
        random.nextBytes(so);
        return sifruj(uaJavni, authTajna, poruka, (ECPrivateKey) efemerni.getPrivate(),
                (ECPublicKey) efemerni.getPublic(), so);
    }

    /** Isto sa zadatim efemernim ključem i solju (test vektor RFC 8291). */
    static byte[] sifruj(byte[] uaJavni, byte[] authTajna, byte[] poruka, ECPrivateKey asPrivatni, ECPublicKey asJavni,
                         byte[] so) {
        if (authTajna == null || authTajna.length != DUZINA_AUTH) {
            throw new IllegalArgumentException("auth nije 16 bajtova");
        }
        if (so == null || so.length != DUZINA_SOLI) {
            throw new IllegalArgumentException("So nije 16 bajtova");
        }
        if (poruka.length > MAX_PORUKA) {
            throw new IllegalArgumentException("Poruka je duža od jednog zapisa");
        }
        ECPublicKey ua = P256.javni(uaJavni);
        byte[] asTacka = P256.tacka(asJavni);
        try {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH");
            ka.init(asPrivatni);
            ka.doPhase(ua, true);
            byte[] ecdh = ka.generateSecret();

            byte[] prkKljuc = hmac(authTajna, ecdh);
            byte[] ikm = hmac(prkKljuc, spoji(INFO_KLJUC, uaJavni, asTacka, new byte[]{1}));
            byte[] prk = hmac(so, ikm);
            byte[] cek = Arrays.copyOf(hmac(prk, spoji(INFO_CEK, new byte[]{1})), 16);
            byte[] nonce = Arrays.copyOf(hmac(prk, spoji(INFO_NONCE, new byte[]{1})), 12);

            byte[] zapis = Arrays.copyOf(poruka, poruka.length + 1);
            zapis[poruka.length] = 0x02;   // graničnik poslednjeg (jedinog) zapisa, bez dopune
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(DUZINA_TAGA * 8, nonce));
            byte[] sifrat = c.doFinal(zapis);

            ByteBuffer telo = ByteBuffer.allocate(DUZINA_SOLI + 4 + 1 + asTacka.length + sifrat.length);
            telo.put(so).putInt(RS).put((byte) asTacka.length).put(asTacka).put(sifrat);
            return telo.array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Šifrovanje push poruke nije uspelo", e);
        }
    }

    static byte[] hmac(byte[] kljuc, byte[] poruka) throws GeneralSecurityException {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(kljuc, "HmacSHA256"));
        return m.doFinal(poruka);
    }

    private static byte[] spoji(byte[]... delovi) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] d : delovi) {
            o.writeBytes(d);
        }
        return o.toByteArray();
    }
}
