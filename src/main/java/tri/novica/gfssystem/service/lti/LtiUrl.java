package tri.novica.gfssystem.service.lti;

import tri.novica.gfssystem.exceptions.SystemException;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Provera adresa LTI registracije: samo {@code https://} sa hostom, bez korisnika u adresi (i bez fragmenta, razmaka),
 * jer server kasnije sam dohvata JWKS platforme. {@code http://} je dozvoljen samo kad ga test izričito uključi
 * ({@code gfs.lti.dozvoli-http}, podrazumevano isključeno).
 */
final class LtiUrl {

    private LtiUrl() {}

    /** Vraća adresu bez razmaka na krajevima ili baca 400 sa porukom koja počinje nazivom polja. */
    static String proveri(String url, boolean dozvoliHttp, String polje) {
        String u = url == null ? "" : url.trim();
        String greska = polje + " mora biti adresa koja počinje sa https://.";
        if (u.isEmpty() || u.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            throw new SystemException(greska, 400);
        }
        URI uri;
        try {
            uri = new URI(u);
        } catch (URISyntaxException e) {
            throw new SystemException(greska, 400);
        }
        String sema = uri.getScheme();
        boolean semaOk = "https".equalsIgnoreCase(sema) || (dozvoliHttp && "http".equalsIgnoreCase(sema));
        if (!semaOk || uri.getHost() == null || uri.getHost().isEmpty() || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new SystemException(greska, 400);
        }
        return u;
    }

    /** {@code izdavac} bez završnih kosih crta + {@code putanja} (npr. {@code /mod/lti/auth.php}). */
    static String izvedi(String izdavac, String putanja) {
        String osnova = izdavac;
        while (osnova.endsWith("/")) osnova = osnova.substring(0, osnova.length() - 1);
        return osnova + putanja;
    }
}
