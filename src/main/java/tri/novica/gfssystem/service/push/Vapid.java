package tri.novica.gfssystem.service.push;

import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * VAPID (RFC 8292): JWT potpisan sa ES256 ključem aplikacije, {@code aud} = origin endpoint-a, {@code exp} najviše 24 h
 * (koristi se 12 h), {@code sub} = kontakt ({@code mailto:} ili https adresa). Potpis je sirov {@code r || s} (64 bajta,
 * JWS), dobijen direktno algoritmom {@code SHA256withECDSAinP1363Format}, bez DER-a. Zaglavlje zahteva:
 * {@code Authorization: vapid t=<jwt>, k=<javni ključ base64url>}.
 */
public final class Vapid {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ZAGLAVLJE_JWT = Base64Url.kodiraj("{\"typ\":\"JWT\",\"alg\":\"ES256\"}"
            .getBytes(StandardCharsets.UTF_8));

    private Vapid() {}

    /** Origin endpoint-a: {@code https://fcm.googleapis.com} (port samo kad nije podrazumevan). */
    public static String aud(URI endpoint) {
        String sema = endpoint.getScheme().toLowerCase(Locale.ROOT);
        String host = endpoint.getHost().toLowerCase(Locale.ROOT);
        int port = endpoint.getPort();
        boolean podrazumevan = port == -1 || ("https".equals(sema) && port == 443) || ("http".equals(sema) && port == 80);
        return sema + "://" + host + (podrazumevan ? "" : ":" + port);
    }

    /** Potpisan JWT za {@code aud}, ističe u {@code expSekunde} (epoha). */
    public static String jwt(String aud, long expSekunde, String sub, ECPrivateKey kljuc) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("aud", aud);
        claims.put("exp", expSekunde);
        claims.put("sub", sub);
        String telo = Base64Url.kodiraj(JSON.writeValueAsString(claims).getBytes(StandardCharsets.UTF_8));
        String zaPotpis = ZAGLAVLJE_JWT + "." + telo;
        try {
            Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
            s.initSign(kljuc);
            s.update(zaPotpis.getBytes(StandardCharsets.US_ASCII));
            return zaPotpis + "." + Base64Url.kodiraj(s.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("VAPID potpis nije uspeo", e);
        }
    }

    public static String zaglavlje(String jwt, String javniKljucB64) {
        return "vapid t=" + jwt + ", k=" + javniKljucB64;
    }
}
