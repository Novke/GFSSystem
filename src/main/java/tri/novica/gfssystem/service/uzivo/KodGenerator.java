package tri.novica.gfssystem.service.uzivo;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.uzivo.IzvodjenjeRepository;

import java.security.SecureRandom;

/** Šestocifreni kod za ulazak u izvođenje, jedinstven među aktivnim izvođenjima ({@code aktivan_kod} je UNIQUE). */
@Component
public class KodGenerator {

    static final String NEMA_KODA = "Trenutno nema slobodnog koda, pokušaj ponovo.";
    private static final int POKUSAJA = 20;

    private final IzvodjenjeRepository izvodjenjeRepository;
    private final SecureRandom random = new SecureRandom();

    public KodGenerator(IzvodjenjeRepository izvodjenjeRepository) {
        this.izvodjenjeRepository = izvodjenjeRepository;
    }

    /** Nasumičan kod {@code 000000}-{@code 999999} koji nije aktivan; posle 20 zauzetih 503. */
    public String novi() {
        for (int i = 0; i < POKUSAJA; i++) {
            String kod = String.format("%06d", random.nextInt(1_000_000));
            if (!izvodjenjeRepository.existsByAktivanKod(kod)) {
                return kod;
            }
        }
        throw new SystemException(NEMA_KODA, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
