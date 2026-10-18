package tri.novica.gfssystem.service.banka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tri.novica.gfssystem.dto.banka.RenderGreskaInfo;
import tri.novica.gfssystem.dto.banka.RenderOdgovor;
import tri.novica.gfssystem.dto.banka.RenderPrilog;
import tri.novica.gfssystem.dto.banka.RenderZahtev;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * HTTP klijent render servisa (kontejner {@code gfs-latex}, spec banke 3.1): {@code POST /render} pretvara LaTeX u SVG ili
 * PDF ({@code GET /zdravlje} servisa koristi healthcheck kontejnera, ne backend). Klijent se gradi ovde (veza 5 s, čitanje {@code gfs.latex.timeout-sekunde}),
 * bez {@code RestClient.Builder} beana: pom nema {@code spring-boot-restclient}, a bean tog tipa bi kasnije zaklonio Bootov.
 * <p>
 * Neuspeh kompilacije nije izuzetak: stiže kao {@code ok=false} sa greškom (servis odgovara 200). Servis koji ne radi
 * (veza, timeout, 5xx, odgovor koji se ne čita ili je bez sadržaja) je {@link RenderNedostupan}; 4xx servisa (zahtev
 * odbijen) se vraća kao {@link RenderOdgovor#odbijen(String)} ({@code ok=false}, {@code odbijen=true}, telo odgovora kao poruka).
 */
@Service
@Slf4j
public class LatexRender {

    static final String ODBIJEN_ZAHTEV = "Render servis je odbio zahtev.";
    private static final int NAJVISE_ZNAKOVA = 300;
    /** Klijent čeka duže od servisa, da servis uvek prvi javi svoj timeout. */
    private static final int REZERVA_SEKUNDI = 10;
    private static final int NAJMANJE_SEKUNDI = 10;
    private static final Duration VEZA = Duration.ofSeconds(5);

    private final RestClient klijent;
    private final int timeoutServisa;

    @Autowired
    public LatexRender(@Value("${gfs.latex.url}") String url, @Value("${gfs.latex.timeout-sekunde}") int timeout) {
        this(RestClient.builder().requestFactory(fabrika(timeout)), url, timeout);
    }

    /** Za testove: builder nosi sopstvenu fabriku zahteva (npr. {@code MockRestServiceServer}). */
    LatexRender(RestClient.Builder builder, String url, int timeout) {
        this.klijent = builder.baseUrl(url).build();
        this.timeoutServisa = Math.max(NAJMANJE_SEKUNDI, timeout - REZERVA_SEKUNDI);
    }

    /** HTTP/1.1 jer {@code HttpClient} na običnom {@code http://} inače šalje {@code Upgrade: h2c}, što servis ne traži. */
    private static ClientHttpRequestFactory fabrika(int timeoutSekunde) {
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(VEZA).build();
        JdkClientHttpRequestFactory fabrika = new JdkClientHttpRequestFactory(http);
        fabrika.setReadTimeout(Duration.ofSeconds(timeoutSekunde));
        return fabrika;
    }

    /** Granica kompilacije koju servis dobija u zahtevu (sekunde). */
    int timeoutServisa() {
        return timeoutServisa;
    }

    /**
     * @param format  {@code svg} ili {@code pdf}
     * @param prilozi može {@code null} (kao prazna lista)
     * @throws RenderNedostupan kad servis ne odgovara, odgovara 5xx ili 200 bez sadržaja (uspeh bez traženog formata,
     *                          neuspeh bez greške)
     */
    public RenderOdgovor renderuj(String format, String tex, List<RenderPrilog> prilozi) {
        RenderZahtev zahtev = new RenderZahtev(format, tex, prilozi == null ? List.of() : prilozi, timeoutServisa);
        try {
            Telo telo = klijent.post().uri("/render")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(zahtev)
                    .retrieve()
                    .body(Telo.class);
            RenderOdgovor odgovor = telo == null ? null : telo.odgovor();
            if (!upotrebljiv(odgovor, format)) {
                log.warn("Render servis je vratio odgovor bez sadržaja.");
                throw new RenderNedostupan();
            }
            return odgovor;
        } catch (HttpClientErrorException e) {
            log.warn("Render servis je odbio zahtev: HTTP {}", e.getStatusCode().value());
            return RenderOdgovor.odbijen(porukaOdbijanja(e));
        } catch (RestClientException e) {
            log.warn("Render servis nije dostupan: {}", opis(e));
            throw new RenderNedostupan(e);
        }
    }

    /** JSON telo odgovora servisa; nepoznata polja se ignorišu, a {@code odbijen} u {@link RenderOdgovor} postavlja samo ovaj klijent. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Telo(boolean ok, String svg, String pdf, RenderGreskaInfo greska, String log, Long trajanjeMs) {
        RenderOdgovor odgovor() {
            return new RenderOdgovor(ok, svg, pdf, greska, log, trajanjeMs, false);
        }
    }

    /** Uspeh nosi traženi format (svg ili pdf), neuspeh nosi grešku; sve drugo je servis koji se loše ponaša. */
    private static boolean upotrebljiv(RenderOdgovor o, String format) {
        if (o == null) return false;
        if (!o.ok()) return o.greska() != null;
        String sadrzaj = "pdf".equals(format) ? o.pdf() : o.svg();
        return sadrzaj != null && !sadrzaj.isBlank();
    }

    /** Servis piše UTF-8; {@code getResponseBodyAsString()} bez charset-a u zaglavlju dekodira kao ISO-8859-1. */
    private static String porukaOdbijanja(HttpClientErrorException e) {
        String telo = new String(e.getResponseBodyAsByteArray(), StandardCharsets.UTF_8).strip();
        if (telo.isEmpty()) return ODBIJEN_ZAHTEV;
        return telo.codePointCount(0, telo.length()) <= NAJVISE_ZNAKOVA
                ? telo : telo.substring(0, telo.offsetByCodePoints(0, NAJVISE_ZNAKOVA));
    }

    /** Za log: status ili uzrok (ConnectException često nema poruku), bez tela odgovora. */
    private static String opis(RestClientException e) {
        if (e instanceof RestClientResponseException r) return "HTTP " + r.getStatusCode().value();
        Throwable uzrok = NestedExceptionUtils.getMostSpecificCause(e);
        return uzrok.getClass().getSimpleName() + (uzrok.getMessage() == null ? "" : ": " + uzrok.getMessage());
    }
}
