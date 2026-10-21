package tri.novica.gfssystem.service.lti;

import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.Resource;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.util.Arrays;

/**
 * Dohvatanje JWKS-a platforme ({@code jwks_url} registracije): server sam ide na adresu koju je upisao nastavnik, pa je
 * dohvat ograničen da ne postane put ka unutrašnjoj mreži (SSRF):
 * <ul>
 *     <li>rok za vezu i za čitanje, gornja granica veličine odgovora (Nimbus {@link DefaultResourceRetriever});</li>
 *     <li>bez praćenja preusmerenja (3xx je greška, cilj preusmerenja se ne dira);</li>
 *     <li>samo {@code https}, a host ne sme da se razreši ni u jednu internu adresu: loopback, privatne mreže, link-local
 *     (i {@code 169.254.169.254}, metapodaci instance), CGNAT/tailnet {@code 100.64/10}, ULA {@code fc00::/7},
 *     multicast, rezervisane opsege. Test režim ({@code gfs.lti.dozvoli-http}) dozvoljava {@code http} i samo loopback.</li>
 * </ul>
 * Provera adrese je pre otvaranja veze; JVM keš DNS-a (podrazumevano 30 s) daje istu adresu i vezi, pa promena DNS
 * odgovora između provere i veze (DNS rebinding) traži da napadač i kontroliše DNS i pogodi taj prozor.
 */
class LtiDohvatKljuceva extends DefaultResourceRetriever {

    private final boolean testRezim;

    LtiDohvatKljuceva(int rokVezeMs, int rokCitanjaMs, int najviseBajtova, boolean testRezim) {
        super(rokVezeMs, rokCitanjaMs, najviseBajtova);
        this.testRezim = testRezim;
    }

    /** Šema i adresa se proveravaju pre bilo kakve veze (i pre Nimbus-ove grane za {@code file:}). */
    @Override
    public Resource retrieveResource(URL url) throws IOException {
        proveri(url, testRezim);
        return super.retrieveResource(url);
    }

    @Override
    protected HttpURLConnection openHTTPConnection(URL url) throws IOException {
        HttpURLConnection veza = super.openHTTPConnection(url);
        veza.setInstanceFollowRedirects(false);
        return veza;
    }

    /** Šema i sve adrese hosta; baca {@link IOException} bez adrese u poruci (poruka ne ide korisniku, ali ni u log). */
    static void proveri(URL url, boolean testRezim) throws IOException {
        String sema = url.getProtocol();
        if (!"https".equalsIgnoreCase(sema) && !(testRezim && "http".equalsIgnoreCase(sema))) {
            throw new IOException("Šema adrese ključeva nije dozvoljena.");
        }
        String host = url.getHost();
        if (host == null || host.isEmpty()) {
            throw new IOException("Adresa ključeva nema host.");
        }
        InetAddress[] adrese = InetAddress.getAllByName(host);
        if (adrese.length == 0 || Arrays.stream(adrese).anyMatch(a -> zabranjena(a, testRezim))) {
            throw new IOException("Adresa ključeva nije dozvoljena (interna mreža).");
        }
    }

    /** Da li je adresa interna (u test režimu je loopback dozvoljen). */
    static boolean zabranjena(InetAddress a, boolean testRezim) {
        if (a.isLoopbackAddress()) {
            return !testRezim;
        }
        if (a.isAnyLocalAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            return zabranjenaIpv4(b);
        }
        if (a instanceof Inet6Address) {
            int prvi = b[0] & 0xff;
            if ((prvi & 0xfe) == 0xfc) return true;                       // ULA fc00::/7
            if (nat64(b)) return zabranjenaIpv4(Arrays.copyOfRange(b, 12, 16));
            boolean ugradjenIpv4 = true;                                   // ::a.b.c.d (zastareo IPv4-compatible)
            for (int i = 0; i < 12; i++) ugradjenIpv4 &= b[i] == 0;
            return ugradjenIpv4 && zabranjenaIpv4(Arrays.copyOfRange(b, 12, 16));
        }
        return true;
    }

    /** 64:ff9b::/96 (NAT64, poslednja 4 bajta su IPv4 adresa). */
    private static boolean nat64(byte[] b) {
        if (b[0] != 0 || b[1] != 0x64 || (b[2] & 0xff) != 0xff || (b[3] & 0xff) != 0x9b) return false;
        for (int i = 4; i < 12; i++) if (b[i] != 0) return false;
        return true;
    }

    private static boolean zabranjenaIpv4(byte[] b) {
        int a0 = b[0] & 0xff;
        int a1 = b[1] & 0xff;
        int a2 = b[2] & 0xff;
        return a0 == 0                                   // 0.0.0.0/8
                || a0 == 10                              // 10/8
                || a0 == 127                             // loopback
                || (a0 == 100 && a1 >= 64 && a1 <= 127)  // CGNAT 100.64/10 (i tailnet)
                || (a0 == 169 && a1 == 254)              // link-local, metapodaci instance
                || (a0 == 172 && a1 >= 16 && a1 <= 31)   // 172.16/12
                || (a0 == 192 && a1 == 168)              // 192.168/16
                || (a0 == 192 && a1 == 0 && a2 == 0)     // 192.0.0.0/24 (IETF)
                || (a0 == 198 && (a1 == 18 || a1 == 19)) // 198.18/15 (benchmark)
                || a0 >= 224;                            // multicast, rezervisano, broadcast
    }
}
