package tri.novica.gfssystem.service.push;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;

import static org.junit.jupiter.api.Assertions.*;

/** VAPID (RFC 8292): JWT ES256, aud = origin endpoint-a, potpis sirov r||s proverljiv javnim ključem. */
class VapidTest {

    static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void jwtJeIspravanEs256() throws Exception {
        KeyPair par = P256.nov(new SecureRandom());
        String jwt = Vapid.jwt("https://fcm.googleapis.com", 1_900_000_000L, "mailto:nastavnik@example.com",
                (ECPrivateKey) par.getPrivate());
        String[] delovi = jwt.split("\\.");
        assertEquals(3, delovi.length);

        JsonNode zaglavlje = JSON.readTree(new String(Base64Url.dekodiraj(delovi[0]), StandardCharsets.UTF_8));
        assertEquals("JWT", zaglavlje.get("typ").asString());
        assertEquals("ES256", zaglavlje.get("alg").asString());
        JsonNode telo = JSON.readTree(new String(Base64Url.dekodiraj(delovi[1]), StandardCharsets.UTF_8));
        assertEquals("https://fcm.googleapis.com", telo.get("aud").asString());
        assertEquals(1_900_000_000L, telo.get("exp").asLong());
        assertEquals("mailto:nastavnik@example.com", telo.get("sub").asString());

        byte[] potpis = Base64Url.dekodiraj(delovi[2]);
        assertEquals(64, potpis.length, "JWS ES256 potpis je r||s, 64 bajta");
        Signature v = Signature.getInstance("SHA256withECDSAinP1363Format");
        v.initVerify((ECPublicKey) par.getPublic());
        v.update((delovi[0] + "." + delovi[1]).getBytes(StandardCharsets.US_ASCII));
        assertTrue(v.verify(potpis));

        KeyPair drugi = P256.nov(new SecureRandom());
        v.initVerify((ECPublicKey) drugi.getPublic());
        v.update((delovi[0] + "." + delovi[1]).getBytes(StandardCharsets.US_ASCII));
        assertFalse(v.verify(potpis));
    }

    @Test
    void audJeOrigin() {
        assertEquals("https://fcm.googleapis.com", Vapid.aud(URI.create("https://fcm.googleapis.com/fcm/send/abc?x=1")));
        assertEquals("https://web.push.apple.com", Vapid.aud(URI.create("https://web.push.apple.com:443/QGk")));
        assertEquals("https://wns2-am3p.notify.windows.com",
                Vapid.aud(URI.create("https://wns2-am3p.notify.windows.com/w/?token=AQ")));
        assertEquals("http://127.0.0.1:8123", Vapid.aud(URI.create("http://127.0.0.1:8123/p")));
    }

    @Test
    void zaglavljeAutorizacije() {
        assertEquals("vapid t=a.b.c, k=BKEY", Vapid.zaglavlje("a.b.c", "BKEY"));
    }

    @Test
    void kontaktIzMejlaIliFronta() {
        assertEquals("mailto:novica@example.com", PushPosiljalac.kontakt(" novica@example.com ", "https://gfs.trif.rs"));
        assertEquals("https://gfs.trif.rs", PushPosiljalac.kontakt("", "https://gfs.trif.rs/, http://localhost:4200"));
        assertEquals("https://gfs.trif.rs", PushPosiljalac.kontakt("nije mejl", "https://gfs.trif.rs"));
    }
}
