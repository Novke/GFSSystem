package tri.novica.gfssystem.service.push;

import tri.novica.gfssystem.dto.push.PushPretplataCmd;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.UUID;

/**
 * "Browser" za testove push-a: sopstveni P-256 par, {@code auth} i FCM adresa sa slučajnim delom (na listi push servisa;
 * zahtev nikad ne ode na mrežu jer testovi koriste {@link LazniPushTransport}). {@link #desifruj} čita poruku kao
 * service worker.
 */
public final class LazniUredjaj {

    private static final SecureRandom RANDOM = new SecureRandom();

    public final String endpoint;
    private final KeyPair par = P256.nov(RANDOM);
    private final byte[] javni = P256.tacka((ECPublicKey) par.getPublic());
    private final byte[] auth = new byte[16];

    public LazniUredjaj() {
        this("https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID());
    }

    public LazniUredjaj(String endpoint) {
        this.endpoint = endpoint;
        RANDOM.nextBytes(auth);
    }

    public PushPretplataCmd cmd() {
        return new PushPretplataCmd(endpoint, Base64Url.kodiraj(javni), Base64Url.kodiraj(auth));
    }

    public boolean prima(PushTransport.Zahtev z) {
        return z.endpoint().toString().equals(endpoint);
    }

    /** JSON poruke iz tela zahteva (RFC 8291). */
    public String desifruj(PushTransport.Zahtev z) {
        try {
            return new String(WebPushSifraTest.desifruj(z.telo(), (ECPrivateKey) par.getPrivate(), javni, auth),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("Poruka se ne dešifruje", e);
        }
    }
}
