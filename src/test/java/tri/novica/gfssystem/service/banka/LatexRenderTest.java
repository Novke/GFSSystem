package tri.novica.gfssystem.service.banka;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tri.novica.gfssystem.config.LatexRenderConfig;
import tri.novica.gfssystem.dto.banka.RenderGreskaInfo;
import tri.novica.gfssystem.dto.banka.RenderOdgovor;
import tri.novica.gfssystem.dto.banka.RenderPrilog;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LatexRenderTest {

    private static final String URL = "http://render.test";
    private static final List<RenderPrilog> PRILOZI = List.of(new RenderPrilog("slika", "png", "iVBORw0="));

    private MockRestServiceServer server;
    private LatexRender render;

    @BeforeEach
    void pripremi() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        render = new LatexRender(builder, URL, 30);
    }

    @Test
    void uspesanSvgSeMapira() {
        server.expect(requestTo(URL + "/render")).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"ok\":true,\"svg\":\"PHN2Zy8+\",\"trajanjeMs\":123}", MediaType.APPLICATION_JSON));

        RenderOdgovor o = render.renderuj("svg", "x^2", PRILOZI);

        assertThat(o).isEqualTo(new RenderOdgovor(true, "PHN2Zy8+", null, null, null, 123L));
        server.verify();
    }

    @Test
    void uspesanPdfSeMapira() {
        server.expect(requestTo(URL + "/render"))
                .andRespond(withSuccess("{\"ok\":true,\"pdf\":\"JVBERi0=\",\"trajanjeMs\":900}", MediaType.APPLICATION_JSON));

        RenderOdgovor o = render.renderuj("pdf", "tekst", List.of());

        assertThat(o.ok()).isTrue();
        assertThat(o.pdf()).isEqualTo("JVBERi0=");
        assertThat(o.svg()).isNull();
    }

    @Test
    void zahtevNosiFormatTexPriloge() {
        server.expect(requestTo(URL + "/render"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.format").value("svg"))
                .andExpect(jsonPath("$.tex").value("\\frac{1}{2}\nčšž"))
                .andExpect(jsonPath("$.prilozi.length()").value(1))
                .andExpect(jsonPath("$.prilozi[0].ime").value("slika"))
                .andExpect(jsonPath("$.prilozi[0].ekstenzija").value("png"))
                .andExpect(jsonPath("$.prilozi[0].sadrzaj").value("iVBORw0="))
                .andExpect(jsonPath("$.timeoutSekunde").value(20))
                .andRespond(withSuccess("{\"ok\":true,\"svg\":\"x\"}", MediaType.APPLICATION_JSON));

        render.renderuj("svg", "\\frac{1}{2}\nčšž", PRILOZI);

        server.verify();
    }

    @Test
    void bezPriloga_saljePraznuListu() {
        server.expect(requestTo(URL + "/render")).andExpect(jsonPath("$.prilozi.length()").value(0))
                .andRespond(withSuccess("{\"ok\":true,\"svg\":\"x\"}", MediaType.APPLICATION_JSON));

        render.renderuj("svg", "x", null);

        server.verify();
    }

    @Test
    void timeoutServisaJeZaDesetSekundiManjiAliNajmanjeDeset() {
        RestClient.Builder b = RestClient.builder();
        assertThat(new LatexRender(b, URL, 30).timeoutServisa()).isEqualTo(20);
        assertThat(new LatexRender(b, URL, 60).timeoutServisa()).isEqualTo(50);
        assertThat(new LatexRender(b, URL, 15).timeoutServisa()).isEqualTo(10);
        assertThat(new LatexRender(b, URL, 5).timeoutServisa()).isEqualTo(10);
    }

    @Test
    void greskaKompilacijeJeOkFalseSaGreskom() {
        server.expect(requestTo(URL + "/render")).andRespond(withSuccess(
                "{\"ok\":false,\"greska\":{\"linija\":3,\"poruka\":\"x\"},\"log\":\"! x\"}", MediaType.APPLICATION_JSON));

        RenderOdgovor o = render.renderuj("svg", "\\foo", List.of());

        assertThat(o).isEqualTo(new RenderOdgovor(false, null, null, new RenderGreskaInfo(3, "x"), "! x", null));
    }

    @Test
    void greskaBezLinijeIVisakPoljaSeIgnorise() {
        server.expect(requestTo(URL + "/render")).andRespond(withSuccess(
                "{\"ok\":false,\"greska\":{\"linija\":null,\"poruka\":\"y\",\"kod\":7},\"nepoznato\":1}", MediaType.APPLICATION_JSON));

        RenderOdgovor o = render.renderuj("svg", "x", List.of());

        assertThat(o.ok()).isFalse();
        assertThat(o.greska()).isEqualTo(new RenderGreskaInfo(null, "y"));
    }

    @Test
    void status500JeNedostupan() {
        server.expect(requestTo(URL + "/render")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("trace"));

        assertThatThrownBy(() -> render.renderuj("svg", "x", List.of()))
                .isInstanceOf(RenderNedostupan.class)
                .hasMessage("Render servis nije dostupan.")
                .extracting(e -> ((RenderNedostupan) e).getCode()).isEqualTo(503);
    }

    @Test
    void status503JeNedostupan() {
        server.expect(requestTo(URL + "/render")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> render.renderuj("svg", "x", List.of())).isInstanceOf(RenderNedostupan.class);
    }

    @Test
    void prekinutaVezaJeNedostupan() {
        server.expect(requestTo(URL + "/render")).andRespond(withException(new IOException("Connection reset")));

        assertThatThrownBy(() -> render.renderuj("svg", "x", List.of()))
                .isInstanceOf(RenderNedostupan.class)
                .hasCauseInstanceOf(org.springframework.web.client.ResourceAccessException.class);
    }

    @Test
    void odgovorKojiNijeJsonJeNedostupan() {
        server.expect(requestTo(URL + "/render")).andRespond(withSuccess("nije json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> render.renderuj("svg", "x", List.of())).isInstanceOf(RenderNedostupan.class);
    }

    @Test
    void praznoTeloUspesnogOdgovoraJeNedostupan() {
        server.expect(requestTo(URL + "/render")).andRespond(withSuccess());

        assertThatThrownBy(() -> render.renderuj("svg", "x", List.of())).isInstanceOf(RenderNedostupan.class);
    }

    @Test
    void status400VracaOkFalseSaTelomKaoPorukom() {
        server.expect(requestTo(URL + "/render")).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.TEXT_PLAIN).body("format mora biti svg ili pdf"));

        RenderOdgovor o = render.renderuj("png", "x", List.of());

        assertThat(o).isEqualTo(new RenderOdgovor(false, null, null,
                new RenderGreskaInfo(null, "format mora biti svg ili pdf"), null, null));
    }

    @Test
    void status413BezTelaDajePodrazumevanuPoruku() {
        server.expect(requestTo(URL + "/render")).andRespond(withStatus(HttpStatus.CONTENT_TOO_LARGE));

        RenderOdgovor o = render.renderuj("svg", "x", List.of());

        assertThat(o.ok()).isFalse();
        assertThat(o.greska()).isEqualTo(new RenderGreskaInfo(null, "Render servis je odbio zahtev."));
    }

    @Test
    void duzeTeloGreskeSeSkracujeNa300() {
        server.expect(requestTo(URL + "/render")).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("a".repeat(1000)));

        RenderOdgovor o = render.renderuj("svg", "x", List.of());

        assertThat(o.greska().poruka()).isEqualTo("a".repeat(300));
    }

    @Test
    void zdravKadJe200() {
        server.expect(requestTo(URL + "/zdravlje")).andExpect(method(HttpMethod.GET)).andRespond(withSuccess());

        assertThat(render.zdrav()).isTrue();
        server.verify();
    }

    @Test
    void nijeZdravKadJe5xxIliVezaPukne() {
        server.expect(requestTo(URL + "/zdravlje")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URL + "/zdravlje")).andRespond(withException(new IOException("Connection refused")));

        assertThat(render.zdrav()).isFalse();
        assertThat(render.zdrav()).isFalse();
    }

    /** Stvarna veza (stdlib HttpServer) kroz builder iz {@link LatexRenderConfig}: prenos, timeout, odbijena veza. */
    @Nested
    class StvarnaVeza {
        private HttpServer http;
        private String url;
        private final AtomicReference<String> primljeno = new AtomicReference<>();
        private final AtomicReference<String> contentType = new AtomicReference<>();

        @BeforeEach
        void pokreni() throws IOException {
            http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            url = "http://127.0.0.1:" + http.getAddress().getPort();
        }

        @AfterEach
        void zaustavi() {
            http.stop(0);
        }

        private LatexRender klijent(int timeoutSekunde) {
            return new LatexRender(new LatexRenderConfig().latexRestClientBuilder(timeoutSekunde), url, timeoutSekunde);
        }

        @Test
        void zahtevIOdgovorPrekoStvarneVeze() {
            http.createContext("/render", ex -> {
                primljeno.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                contentType.set(ex.getRequestHeaders().getFirst("Content-Type"));
                byte[] telo = "{\"ok\":true,\"svg\":\"PHN2Zy8+\",\"trajanjeMs\":5}".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, telo.length);
                ex.getResponseBody().write(telo);
                ex.close();
            });
            http.start();

            RenderOdgovor o = klijent(30).renderuj("svg", "čšž", PRILOZI);

            assertThat(o).isEqualTo(new RenderOdgovor(true, "PHN2Zy8+", null, null, null, 5L));
            assertThat(contentType.get()).startsWith("application/json");
            assertThat(primljeno.get()).contains("\"format\":\"svg\"", "\"tex\":\"čšž\"", "\"timeoutSekunde\":20");
        }

        @Test
        void sporServisPrekoReadTimeoutaJeNedostupan() {
            http.createContext("/render", ex -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                ex.close();
            });
            http.start();

            long pocetak = System.nanoTime();
            assertThatThrownBy(() -> klijent(1).renderuj("svg", "x", List.of())).isInstanceOf(RenderNedostupan.class);
            assertThat((System.nanoTime() - pocetak) / 1_000_000).isLessThan(2500);
        }

        @Test
        void odbijenaVezaJeNedostupanAZdravJeFalse() throws IOException {
            int slobodan;
            try (ServerSocket s = new ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress())) {
                slobodan = s.getLocalPort();
            }
            url = "http://127.0.0.1:" + slobodan;
            LatexRender r = klijent(30);

            assertThatThrownBy(() -> r.renderuj("svg", "x", List.of())).isInstanceOf(RenderNedostupan.class);
            assertThat(r.zdrav()).isFalse();
        }
    }
}
