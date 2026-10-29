package tri.novica.gfssystem.service.push;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** RFC 8291 odeljak 5 (testni vektor) i povratno dešifrovanje kao što radi browser. */
class WebPushSifraTest {

    // RFC 8291, odeljak 5 ("Push Message Encryption Example")
    static final String PORUKA = "When I grow up, I want to be a watermelon";
    static final String AS_PRIVATNI = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    static final String AS_JAVNI = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    static final String UA_JAVNI = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    static final String UA_PRIVATNI = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    static final String AUTH = "BTBZMqHH6r4Tts7J_aSIgg";
    static final String SO = "DGv6ra1nlYgDCS1FRnbzlw";
    static final String TELO = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN";

    @Test
    void rfc8291VektorDajeTacnoIstoTelo() {
        ECPrivateKey asPrivatni = P256.privatni(Base64Url.dekodiraj(AS_PRIVATNI));
        ECPublicKey asJavni = P256.javni(Base64Url.dekodiraj(AS_JAVNI));
        byte[] telo = WebPushSifra.sifruj(Base64Url.dekodiraj(UA_JAVNI), Base64Url.dekodiraj(AUTH),
                PORUKA.getBytes(StandardCharsets.UTF_8), asPrivatni, asJavni, Base64Url.dekodiraj(SO));
        assertEquals(TELO, Base64Url.kodiraj(telo));
    }

    @Test
    void rfc8291VektorSeDesifrujeKljucemBrowsera() throws Exception {
        byte[] poruka = desifruj(Base64Url.dekodiraj(TELO), P256.privatni(Base64Url.dekodiraj(UA_PRIVATNI)),
                Base64Url.dekodiraj(UA_JAVNI), Base64Url.dekodiraj(AUTH));
        assertEquals(PORUKA, new String(poruka, StandardCharsets.UTF_8));
    }

    @Test
    void zaglavljeTelaPoRfc8188() {
        byte[] telo = Base64Url.dekodiraj(TELO);
        ByteBuffer b = ByteBuffer.wrap(telo);
        byte[] so = new byte[16];
        b.get(so);
        assertArrayEquals(Base64Url.dekodiraj(SO), so);
        assertEquals(4096, b.getInt());
        assertEquals(65, b.get());
    }

    @Test
    void browserDesifrujeNasuPoruku() throws Exception {
        // "browser": sopstveni par i auth; server šifruje sa novim efemernim ključem i solju
        SecureRandom r = new SecureRandom();
        KeyPair ua = P256.nov(r);
        byte[] uaJavni = P256.tacka((ECPublicKey) ua.getPublic());
        byte[] auth = new byte[16];
        r.nextBytes(auth);
        byte[] poruka = "{\"naslov\":\"Čas je otkazan\"}".getBytes(StandardCharsets.UTF_8);

        byte[] telo = WebPushSifra.sifruj(uaJavni, auth, poruka, r);
        byte[] dva = WebPushSifra.sifruj(uaJavni, auth, poruka, r);
        assertFalse(Arrays.equals(telo, dva), "svaka poruka ima novu so i efemerni ključ");

        assertArrayEquals(poruka, desifruj(telo, (ECPrivateKey) ua.getPrivate(), uaJavni, auth));
    }

    @Test
    void odbijaPogresneUlaze() {
        SecureRandom r = new SecureRandom();
        byte[] ua = Base64Url.dekodiraj(UA_JAVNI);
        byte[] auth = Base64Url.dekodiraj(AUTH);
        assertThrows(IllegalArgumentException.class, () -> WebPushSifra.sifruj(ua, new byte[15], new byte[1], r));
        byte[] vanKrive = ua.clone();
        vanKrive[64] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> WebPushSifra.sifruj(vanKrive, auth, new byte[1], r));
        assertThrows(IllegalArgumentException.class,
                () -> WebPushSifra.sifruj(ua, auth, new byte[WebPushSifra.MAX_PORUKA + 1], r));
        assertDoesNotThrow(() -> WebPushSifra.sifruj(ua, auth, new byte[WebPushSifra.MAX_PORUKA], r));
    }

    @Test
    void p256TackaIProvere() {
        byte[] t = Base64Url.dekodiraj(AS_JAVNI);
        assertTrue(P256.ispravnaTacka(t));
        assertArrayEquals(t, P256.tacka(P256.javni(t)));
        byte[] kompresovana = t.clone();
        kompresovana[0] = 0x02;
        assertFalse(P256.ispravnaTacka(kompresovana));
        assertFalse(P256.ispravnaTacka(Arrays.copyOf(t, 64)));
        assertFalse(P256.ispravnaTacka(new byte[65]));
        byte[] skalar = Base64Url.dekodiraj(AS_PRIVATNI);
        assertArrayEquals(skalar, P256.skalar(P256.privatni(skalar)));
        assertThrows(IllegalArgumentException.class, () -> P256.privatni(new byte[32]));
    }

    @Test
    void base64UrlStrogo() {
        assertNull(Base64Url.dekodiraj("ab+/"));
        assertNull(Base64Url.dekodiraj(""));
        assertNull(Base64Url.dekodiraj(null));
        assertNull(Base64Url.dekodiraj("a b"));
        assertArrayEquals(new byte[]{(byte) 0xfb, (byte) 0xff}, Base64Url.dekodiraj("-_8"));
        assertArrayEquals(new byte[]{(byte) 0xfb, (byte) 0xff}, Base64Url.dekodiraj("-_8="));
    }

    /** Dešifrovanje kao u browseru (RFC 8291 + 8188), samo za proveru. */
    static byte[] desifruj(byte[] telo, ECPrivateKey uaPrivatni, byte[] uaJavni, byte[] auth) throws Exception {
        ByteBuffer b = ByteBuffer.wrap(telo);
        byte[] so = new byte[16];
        b.get(so);
        int rs = b.getInt();
        assertEquals(4096, rs);
        int idlen = b.get() & 0xff;
        byte[] asJavni = new byte[idlen];
        b.get(asJavni);
        byte[] sifrat = new byte[b.remaining()];
        b.get(sifrat);

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(uaPrivatni);
        ka.doPhase(P256.javni(asJavni), true);
        byte[] ecdh = ka.generateSecret();
        byte[] prkKljuc = WebPushSifra.hmac(auth, ecdh);
        byte[] info = spoji("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaJavni, asJavni, new byte[]{1});
        byte[] ikm = WebPushSifra.hmac(prkKljuc, info);
        byte[] prk = WebPushSifra.hmac(so, ikm);
        byte[] cek = Arrays.copyOf(WebPushSifra.hmac(prk, spoji("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 16);
        byte[] nonce = Arrays.copyOf(WebPushSifra.hmac(prk, spoji("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] zapis = c.doFinal(sifrat);
        assertEquals(2, zapis[zapis.length - 1], "graničnik poslednjeg zapisa");
        return Arrays.copyOf(zapis, zapis.length - 1);
    }

    private static byte[] spoji(byte[]... d) {
        int n = 0;
        for (byte[] x : d) n += x.length;
        byte[] r = new byte[n];
        int i = 0;
        for (byte[] x : d) {
            System.arraycopy(x, 0, r, i, x.length);
            i += x.length;
        }
        return r;
    }
}
