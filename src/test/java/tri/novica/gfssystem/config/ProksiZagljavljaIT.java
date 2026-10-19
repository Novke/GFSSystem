package tri.novica.gfssystem.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.utility.KlijentIp;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pravi Tomcat (RANDOM_PORT) iza "dva nginx-a": {@code server.forward-headers-strategy=native} (RemoteIpValve) ide
 * {@code X-Forwarded-For} zdesna i preskače pouzdane (privatne) proksije, pa je klijent prva javna adresa, šta god je
 * sam upisao levo od nje. Test-kontroler živi samo ovde i registruje se kroz {@code @TestConfiguration} (ne kroz
 * skeniranje), pa ga drugi testovi ne vide.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ProksiZagljavljaIT.ProbaConfig.class)
class ProksiZagljavljaIT {

    static final String PUTANJA = "/test-proksi/klijent";

    @LocalServerPort int port;

    @TestConfiguration
    static class ProbaConfig {
        @Bean
        ProbaKontroler probaKontroler() {
            return new ProbaKontroler();
        }
    }

    @RestController
    static class ProbaKontroler {
        @GetMapping(PUTANJA)
        Map<String, Object> klijent(HttpServletRequest request) {
            return Map.of("ip", request.getRemoteAddr(), "klijentIp", KlijentIp.iz(request), "secure", request.isSecure());
        }
    }

    private JsonNode pozovi(String... zaglavlja) {
        RestClient.RequestHeadersSpec<?> zahtev = RestClient.create("http://127.0.0.1:" + port).get().uri(PUTANJA);
        for (int i = 0; i < zaglavlja.length; i += 2) {
            zahtev = zahtev.header(zaglavlja[i], zaglavlja[i + 1]);
        }
        String telo = zahtev.retrieve().body(String.class);
        return JsonMapper.builder().build().readTree(telo);
    }

    @Test
    void prvaJavnaAdresaZdesnaJeKlijent() {
        JsonNode r = pozovi("X-Forwarded-For", "6.6.6.6, 203.0.113.7, 172.18.0.1", "X-Forwarded-Proto", "https");
        assertEquals("203.0.113.7", r.get("ip").asString());
        assertEquals("203.0.113.7", r.get("klijentIp").asString());
        assertTrue(r.get("secure").asBoolean());
    }

    @Test
    void bezZaglavljaJeAdresaVeze() {
        JsonNode r = pozovi();
        assertEquals("127.0.0.1", r.get("ip").asString());
        assertEquals("127.0.0.1", r.get("klijentIp").asString());
        assertFalse(r.get("secure").asBoolean());
    }

    @Test
    void lazanXForwardedForSePrecrtava() {
        // klijent sam upiše privatnu adresu levo od prave: zdesna se preskače samo pouzdano, prva javna staje
        JsonNode r = pozovi("X-Forwarded-For", "10.0.0.5, 203.0.113.7, 172.18.0.1");
        assertEquals("203.0.113.7", r.get("klijentIp").asString());
    }
}
