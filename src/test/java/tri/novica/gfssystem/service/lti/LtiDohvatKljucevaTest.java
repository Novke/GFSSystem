package tri.novica.gfssystem.service.lti;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dohvatanje JWKS-a platforme (server sam ide na adresu koju je upisao nastavnik): rok za vezu i čitanje, gornja granica
 * veličine, bez praćenja preusmerenja, bez internih adresa (loopback samo u test režimu {@code gfs.lti.dozvoli-http}).
 */
class LtiDohvatKljucevaTest {

    HttpServer server;
    final AtomicInteger cilj = new AtomicInteger();

    @BeforeEach
    void pokreni() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/mali", x -> posalji(x, 200, "{\"keys\":[]}"));
        server.createContext("/veliki", x -> posalji(x, 200, "x".repeat(20_000)));
        server.createContext("/preusmerenje", x -> {
            x.getResponseHeaders().add("Location", adresa("/cilj"));
            x.sendResponseHeaders(302, -1);
            x.close();
        });
        server.createContext("/cilj", x -> {
            cilj.incrementAndGet();
            posalji(x, 200, "{\"keys\":[]}");
        });
        server.createContext("/sporo", x -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            posalji(x, 200, "{\"keys\":[]}");
        });
        server.start();
    }

    @AfterEach
    void zaustavi() {
        server.stop(0);
    }

    static void posalji(com.sun.net.httpserver.HttpExchange x, int status, String telo) throws IOException {
        byte[] b = telo.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", "application/json");
        x.sendResponseHeaders(status, b.length);
        try (OutputStream o = x.getResponseBody()) {
            o.write(b);
        }
    }

    String adresa(String putanja) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + putanja;
    }

    static URL url(String s) throws Exception {
        return URI.create(s).toURL();
    }

    LtiDohvatKljuceva dohvat(boolean testRezim) {
        return new LtiDohvatKljuceva(1_000, 500, 10_000, testRezim);
    }

    @Test
    void testRezimCitaMaliOdgovorSaLoopbacka() throws Exception {
        assertEquals("{\"keys\":[]}", dohvat(true).retrieveResource(url(adresa("/mali"))).getContent());
    }

    @Test
    void loopbackJeZabranjenVanTestRezima() throws Exception {
        IOException e = assertThrows(IOException.class, () -> dohvat(false).retrieveResource(url(adresa("/mali"))));
        assertTrue(e.getMessage().contains("nije dozvoljena"), e.getMessage());
    }

    @Test
    void httpJeZabranjenVanTestRezima() {
        assertThrows(IOException.class, () -> dohvat(false).retrieveResource(url("http://moodle.example.test/certs")));
    }

    @Test
    void fileSemaJeUvekZabranjena() {
        assertThrows(IOException.class, () -> dohvat(true).retrieveResource(url("file:///etc/passwd")));
    }

    @Test
    void preusmerenjeSeNePrati() throws Exception {
        assertThrows(IOException.class, () -> dohvat(true).retrieveResource(url(adresa("/preusmerenje"))));
        assertEquals(0, cilj.get());
    }

    @Test
    void prevelikOdgovorSeOdbija() {
        assertThrows(IOException.class, () -> dohvat(true).retrieveResource(url(adresa("/veliki"))));
    }

    @Test
    void sporOdgovorPadaNaRokuCitanja() {
        long t0 = System.nanoTime();
        assertThrows(IOException.class, () -> dohvat(true).retrieveResource(url(adresa("/sporo"))));
        assertTrue(System.nanoTime() - t0 < 1_900_000_000L, "rok čitanja nije poštovan");
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "127.1.2.3", "::1", "0.0.0.0", "::", "10.1.2.3", "172.16.0.1", "172.31.255.255",
            "192.168.1.1", "169.254.169.254", "100.64.0.1", "100.127.255.254", "fd00::1", "fc00::1", "fe80::1",
            "fec0::1", "224.0.0.1", "ff02::1", "255.255.255.255", "::ffff:10.0.0.1", "::ffff:127.0.0.1",
            "192.0.0.8", "198.18.0.1", "240.0.0.1", "64:ff9b::a00:1"})
    void interneAdreseSuZabranjene(String ip) throws Exception {
        assertTrue(LtiDohvatKljuceva.zabranjena(InetAddress.getByName(ip), false), ip);
    }

    @ParameterizedTest
    @ValueSource(strings = {"93.184.216.34", "160.99.1.1", "2001:4860:4860::8888", "100.128.0.1", "172.32.0.1"})
    void javneAdreseSuDozvoljene(String ip) throws Exception {
        assertFalse(LtiDohvatKljuceva.zabranjena(InetAddress.getByName(ip), false), ip);
    }

    @Test
    void testRezimDozvoljavaSamoLoopback() throws Exception {
        assertFalse(LtiDohvatKljuceva.zabranjena(InetAddress.getByName("127.0.0.1"), true));
        assertFalse(LtiDohvatKljuceva.zabranjena(InetAddress.getByName("::1"), true));
        assertTrue(LtiDohvatKljuceva.zabranjena(InetAddress.getByName("10.0.0.1"), true));
        assertTrue(LtiDohvatKljuceva.zabranjena(InetAddress.getByName("169.254.169.254"), true));
    }
}
