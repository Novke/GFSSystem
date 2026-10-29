package tri.novica.gfssystem.service.push;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Predicate;

/**
 * {@link PushTransport} preko {@code java.net.http.HttpClient}: bez praćenja preusmerenja ({@code NEVER}), 10 s za vezu i
 * za ceo zahtev, telo odgovora se ne čita ({@code discarding}). Pre svakog zahteva adresa se ponovo proverava listom
 * hostova ({@link PushEndpoint#dozvoljen}); zaobilazni konstruktor sa drugim pravilom postoji samo za test prema lokalnom
 * HTTP serveru i Spring ga nikad ne koristi.
 */
public class HttpPushTransport implements PushTransport {

    static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient klijent;
    private final Predicate<URI> dozvoljen;

    public HttpPushTransport() {
        this(PushEndpoint::dozvoljen);
    }

    /** Samo za test: drugo pravilo adrese (npr. {@code http://127.0.0.1:<port>}). */
    HttpPushTransport(Predicate<URI> dozvoljen) {
        this.dozvoljen = dozvoljen;
        this.klijent = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(TIMEOUT)
                .build();
    }

    @Override
    public int posalji(Zahtev z) {
        if (!dozvoljen.test(z.endpoint())) {
            return NEDOZVOLJENO;
        }
        HttpRequest zahtev = HttpRequest.newBuilder(z.endpoint())
                .timeout(TIMEOUT)
                .header("TTL", Long.toString(Math.max(0, z.ttlSekundi())))
                .header("Urgency", z.hitno() ? "high" : "normal")
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("Authorization", z.autorizacija())
                .POST(HttpRequest.BodyPublishers.ofByteArray(z.telo()))
                .build();
        try {
            return klijent.send(zahtev, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException e) {
            return MREZNA_GRESKA;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return MREZNA_GRESKA;
        }
    }
}
