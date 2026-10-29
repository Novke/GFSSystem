package tri.novica.gfssystem.service.uzivo;

import org.springframework.stereotype.Component;
import tri.novica.gfssystem.dto.uzivo.RangPrikaz;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Koji rang tačnosti je na projektoru dok je {@code rangListaPrikazana} (spec rang tačnosti 6). Samo u memoriji, bez
 * migracije: posle restarta backenda prikaz se vraća na rang izvođenja. Menja se samo pod zaključanim redom izvođenja.
 */
@Component
public class RangPrikazRegistar {

    private final Map<Long, RangPrikaz> prikazi = new ConcurrentHashMap<>();

    /** Prikaz izvođenja; bez zapisa rang izvođenja. */
    public RangPrikaz prikaz(Long izvodjenjeId) {
        return prikazi.getOrDefault(izvodjenjeId, RangPrikaz.IZVODJENJE);
    }

    public void postavi(Long izvodjenjeId, RangPrikaz prikaz) {
        prikazi.put(izvodjenjeId, prikaz);
    }

    public void ukloni(Long izvodjenjeId) {
        prikazi.remove(izvodjenjeId);
    }
}
