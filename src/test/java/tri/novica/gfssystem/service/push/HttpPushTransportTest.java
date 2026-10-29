package tri.novica.gfssystem.service.push;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link HttpPushTransport} prema lokalnom lažnom push serveru (nikad pravi FCM ili Apple). Proizvodna pravila adrese se
 * proveravaju posebno: proizvodni konstruktor lokalnu adresu odbija bez ijednog zahteva.
 */
class HttpPushTransportTest {

    record Primljeno(String metod, String putanja, Map<String, List<String>> zaglavlja, byte[] telo) {
    }

    HttpServer server;
    final List<Primljeno> primljeno = new CopyOnWriteArrayList<>();
    final AtomicInteger status = new AtomicInteger(201);
    final AtomicInteger naPreusmerenje = new AtomicInteger();

    @BeforeEach
    void pokreni() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/push", x -> {
            primljeno.add(new Primljeno(x.getRequestMethod(), x.getRequestURI().getPath(), Map.copyOf(x.getRequestHeaders()),
                    x.getRequestBody().readAllBytes()));
            int s = status.get();
            if (s == 301) {
                x.getResponseHeaders().add("Location", "/cilj");
            }
            byte[] odgovor = "telo koje se ne čita".getBytes();
            x.sendResponseHeaders(s, odgovor.length);
            x.getResponseBody().write(odgovor);
            x.close();
        });
        server.createContext("/cilj", x -> {
            naPreusmerenje.incrementAndGet();
            x.sendResponseHeaders(201, -1);
            x.close();
        });
        server.start();
    }

    @AfterEach
    void zaustavi() {
        server.stop(0);
    }

    URI adresa() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/push/abc");
    }

    HttpPushTransport lokalni() {
        return new HttpPushTransport(u -> "127.0.0.1".equals(u.getHost()));
    }

    @Test
    void saljeZaglavljaITelo() {
        byte[] telo = {1, 2, 3, 4};
        int s = lokalni().posalji(new PushTransport.Zahtev(adresa(), telo, 3600, true, "vapid t=a.b.c, k=K"));
        assertEquals(201, s);
        assertEquals(1, primljeno.size());
        Primljeno p = primljeno.getFirst();
        assertEquals("POST", p.metod());
        assertArrayEquals(telo, p.telo());
        assertEquals("3600", p.zaglavlja().get("Ttl").getFirst());
        assertEquals("high", p.zaglavlja().get("Urgency").getFirst());
        assertEquals("aes128gcm", p.zaglavlja().get("Content-encoding").getFirst());
        assertEquals("application/octet-stream", p.zaglavlja().get("Content-type").getFirst());
        assertEquals("vapid t=a.b.c, k=K", p.zaglavlja().get("Authorization").getFirst());

        lokalni().posalji(new PushTransport.Zahtev(adresa(), telo, 0, false, "vapid t=x, k=y"));
        assertEquals("normal", primljeno.get(1).zaglavlja().get("Urgency").getFirst());
        assertEquals("0", primljeno.get(1).zaglavlja().get("Ttl").getFirst());
    }

    @Test
    void vracaStatusServisa() {
        status.set(410);
        assertEquals(410, lokalni().posalji(new PushTransport.Zahtev(adresa(), new byte[1], 60, false, "v")));
        status.set(500);
        assertEquals(500, lokalni().posalji(new PushTransport.Zahtev(adresa(), new byte[1], 60, false, "v")));
    }

    @Test
    void nePratiPreusmerenje() {
        status.set(301);
        assertEquals(301, lokalni().posalji(new PushTransport.Zahtev(adresa(), new byte[1], 60, false, "v")));
        assertEquals(0, naPreusmerenje.get());
    }

    @Test
    void mreznaGreska() {
        int port = server.getAddress().getPort();
        server.stop(0);
        URI u = URI.create("http://127.0.0.1:" + port + "/push/abc");
        assertEquals(PushTransport.MREZNA_GRESKA, lokalni().posalji(new PushTransport.Zahtev(u, new byte[1], 60, false, "v")));
    }

    @Test
    void proizvodniTransportOdbijaLokalnuAdresuBezZahteva() {
        assertEquals(PushTransport.NEDOZVOLJENO,
                new HttpPushTransport().posalji(new PushTransport.Zahtev(adresa(), new byte[1], 60, false, "v")));
        assertTrue(primljeno.isEmpty());
    }
}
