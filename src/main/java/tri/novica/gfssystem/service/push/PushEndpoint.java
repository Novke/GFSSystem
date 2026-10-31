package tri.novica.gfssystem.service.push;

import tri.novica.gfssystem.service.uzivo.TokenHash;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Pravila za endpoint Web Push pretplate (spec aplikacije 5.2, zaštita od SSRF-a): samo {@code https}, bez korisnika u
 * adresi i bez fragmenta, port podrazumevani (443 ili bez porta), host sa liste poznatih push servisa:
 * {@code fcm.googleapis.com} (Chrome, Samsung Internet, Opera), {@code *.push.apple.com} (Safari, {@code web.push.apple.com}),
 * {@code updates.push.services.mozilla.com} (Firefox) i {@code *.notify.windows.com} (Edge, WNS). Proverava se pri upisu
 * pretplate i ponovo pre svakog slanja. Pun endpoint je tajna adresa: u log ide samo {@link #trag}.
 */
public final class PushEndpoint {

    public static final int MAX_DUZINA = 1000;
    /** Vidljivi ASCII bez razmaka (endpoint je URL koji je browser već kodirao). */
    private static final Pattern ZNACI = Pattern.compile("^[\\x21-\\x7E]+$");
    private static final String LABELE = "(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+";
    private static final Pattern APPLE = Pattern.compile("^" + LABELE + "push\\.apple\\.com$");
    private static final Pattern WINDOWS = Pattern.compile("^" + LABELE + "notify\\.windows\\.com$");

    private PushEndpoint() {}

    /** Ishod provere endpoint-a pri upisu pretplate. */
    public enum Provera { DOZVOLJEN, NEISPRAVAN, NEPODRZAN }

    public static Provera proveri(String endpoint) {
        if (endpoint == null || endpoint.isEmpty() || endpoint.length() > MAX_DUZINA || !ZNACI.matcher(endpoint).matches()) {
            return Provera.NEISPRAVAN;
        }
        URI u;
        try {
            u = new URI(endpoint);
        } catch (URISyntaxException e) {
            return Provera.NEISPRAVAN;
        }
        return dozvoljen(u) ? Provera.DOZVOLJEN : Provera.NEPODRZAN;
    }

    /** Da li se na ovu adresu sme slati (ista pravila pri upisu i pre slanja). */
    public static boolean dozvoljen(URI u) {
        if (u == null || u.isOpaque() || !"https".equals(u.getScheme()) || u.getRawUserInfo() != null
                || u.getRawFragment() != null) {
            return false;
        }
        String host = u.getHost();
        if (host == null || !dozvoljenHost(host)) {
            return false;
        }
        int port = u.getPort();
        if (port != -1 && port != 443) {
            return false;
        }
        // authority mora biti tačno host (ili host:443): ništa što URI parser tumači drugačije od HTTP klijenta
        String authority = u.getRawAuthority();
        return host.equals(authority) || (host + ":443").equals(authority);
    }

    static boolean dozvoljenHost(String host) {
        if (!host.equals(host.toLowerCase(Locale.ROOT))) {
            return false;
        }
        return host.equals("fcm.googleapis.com") || host.equals("updates.push.services.mozilla.com")
                || APPLE.matcher(host).matches() || WINDOWS.matcher(host).matches();
    }

    /** SHA-256 (hex) celog endpoint-a: jedinstven ključ pretplate ({@code endpoint_hes}). */
    public static String hes(String endpoint) {
        return TokenHash.od(endpoint);
    }

    /** Za log: host i prvih 8 hex SHA-256 endpoint-a, nikad pun endpoint. */
    public static String trag(String endpoint) {
        return trag(host(endpoint), hes(endpoint));
    }

    public static String trag(String host, String hes) {
        return (host == null ? "?" : host) + "#" + (hes == null ? "?" : hes.substring(0, Math.min(8, hes.length())));
    }

    static String host(String endpoint) {
        try {
            return new URI(endpoint).getHost();
        } catch (URISyntaxException | NullPointerException e) {
            return null;
        }
    }

    /** Endpoint kao URI kad je dozvoljen; inače prazno. */
    public static Optional<URI> uri(String endpoint) {
        if (proveri(endpoint) != Provera.DOZVOLJEN) {
            return Optional.empty();
        }
        return Optional.of(URI.create(endpoint));
    }
}
