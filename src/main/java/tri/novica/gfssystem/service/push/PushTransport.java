package tri.novica.gfssystem.service.push;

import java.net.URI;

/**
 * Predaja već šifrovane poruke push servisu (RFC 8030). Proizvodna implementacija je {@link HttpPushTransport}; testovi je
 * zamenjuju lažnom (nikad pravi FCM ili Apple).
 */
public interface PushTransport {

    /** Mrežna greška ili istek vremena (nema HTTP odgovora). */
    int MREZNA_GRESKA = -1;
    /** Adresa više nije dozvoljena (lista hostova); pretplata se briše. */
    int NEDOZVOLJENO = -2;
    /** Slanje je isključeno ({@code gfs.push.http=false}, test resursi); pretplata se ne menja. */
    int PRESKOCENO = -3;

    /** Jedan POST na endpoint; vraća HTTP status ili {@link #MREZNA_GRESKA} / {@link #NEDOZVOLJENO} / {@link #PRESKOCENO}. Telo odgovora se ne čita. */
    int posalji(Zahtev zahtev);

    /**
     * @param ttlSekundi  zaglavlje {@code TTL}
     * @param hitno       {@code Urgency: high}, inače {@code normal}
     * @param autorizacija zaglavlje {@code Authorization} ({@code vapid t=..., k=...})
     */
    record Zahtev(URI endpoint, byte[] telo, long ttlSekundi, boolean hitno, String autorizacija) {
    }
}
