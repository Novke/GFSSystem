package tri.novica.gfssystem.service.banka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tri.novica.gfssystem.dto.banka.RenderGreskaInfo;
import tri.novica.gfssystem.dto.banka.RenderOdgovor;
import tri.novica.gfssystem.dto.banka.RenderPrilog;
import tri.novica.gfssystem.dto.banka.RenderZahtev;

import java.util.List;

/**
 * HTTP klijent render servisa (kontejner {@code gfs-latex}, spec banke 3.1): {@code POST /render} pretvara LaTeX u SVG ili
 * PDF, {@code GET /zdravlje} proverava da li radi. Vremenske granice veze i čitanja postavlja builder iz
 * {@link tri.novica.gfssystem.config.LatexRenderConfig}.
 * <p>
 * Neuspeh kompilacije nije izuzetak: stiže kao {@code ok=false} sa greškom (servis odgovara 200). Servis koji ne radi
 * (veza, timeout, 5xx, odgovor koji se ne čita) je {@link RenderNedostupan}; 4xx servisa (zahtev odbijen) se vraća kao
 * {@code ok=false} sa telom odgovora kao porukom.
 */
@Service
@Slf4j
public class LatexRender {

    static final String ODBIJEN_ZAHTEV = "Render servis je odbio zahtev.";
    private static final int NAJVISE_ZNAKOVA = 300;
    /** Klijent čeka duže od servisa, da servis uvek prvi javi svoj timeout. */
    private static final int REZERVA_SEKUNDI = 10;
    private static final int NAJMANJE_SEKUNDI = 10;

    private final RestClient klijent;
    private final int timeoutServisa;

    public LatexRender(RestClient.Builder builder,
                       @Value("${gfs.latex.url}") String url,
                       @Value("${gfs.latex.timeout-sekunde}") int timeout) {
        this.klijent = builder.baseUrl(url).build();
        this.timeoutServisa = Math.max(NAJMANJE_SEKUNDI, timeout - REZERVA_SEKUNDI);
    }

    /** Granica kompilacije koju servis dobija u zahtevu (sekunde). */
    int timeoutServisa() {
        return timeoutServisa;
    }

    /**
     * @param format  {@code svg} ili {@code pdf}
     * @param prilozi može {@code null} (kao prazna lista)
     * @throws RenderNedostupan kad servis ne odgovara ili odgovara 5xx / neupotrebljivim telom
     */
    public RenderOdgovor renderuj(String format, String tex, List<RenderPrilog> prilozi) {
        RenderZahtev zahtev = new RenderZahtev(format, tex, prilozi == null ? List.of() : prilozi, timeoutServisa);
        try {
            RenderOdgovor odgovor = klijent.post().uri("/render")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(zahtev)
                    .retrieve()
                    .body(RenderOdgovor.class);
            if (odgovor == null) {
                log.warn("Render servis je vratio prazan odgovor.");
                throw new RenderNedostupan();
            }
            return odgovor;
        } catch (HttpClientErrorException e) {
            log.warn("Render servis je odbio zahtev: HTTP {}", e.getStatusCode().value());
            return new RenderOdgovor(false, null, null, new RenderGreskaInfo(null, porukaOdbijanja(e)), null, null);
        } catch (RestClientException e) {
            log.warn("Render servis nije dostupan: {}", opis(e));
            throw new RenderNedostupan(e);
        }
    }

    /** {@code true} kad {@code GET /zdravlje} vrati 2xx; svaka druga ishod (veza, timeout, status) je {@code false}. */
    public boolean zdrav() {
        try {
            klijent.get().uri("/zdravlje").retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.debug("Render servis nije zdrav: {}", opis(e));
            return false;
        }
    }

    private static String porukaOdbijanja(HttpClientErrorException e) {
        String telo = e.getResponseBodyAsString().strip();
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
