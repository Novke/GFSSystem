package tri.novica.gfssystem.service.banka;

import org.springframework.http.HttpStatus;
import tri.novica.gfssystem.exceptions.SystemException;

/** Render servis ne odgovara (veza, timeout, 5xx ili neupotrebljiv odgovor): 503, ne greška nastavnikovog teksta. */
public class RenderNedostupan extends SystemException {
    public static final String PORUKA = "Render servis nije dostupan.";

    public RenderNedostupan() {
        super(PORUKA, HttpStatus.SERVICE_UNAVAILABLE);
    }

    public RenderNedostupan(Throwable uzrok) {
        super(PORUKA, uzrok, HttpStatus.SERVICE_UNAVAILABLE.value());
    }
}
