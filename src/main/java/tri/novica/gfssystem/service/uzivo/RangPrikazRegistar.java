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
    private final Map<Long, Integer> brojevi = new ConcurrentHashMap<>();

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

    /** Koliko prvih redova ranga ide na projektor; {@code null} = svi (do {@link RangTacnostiService#RANG_PROJEKTOR_MAX}). */
    public Integer broj(Long izvodjenjeId) {
        return brojevi.get(izvodjenjeId);
    }

    public void postaviBroj(Long izvodjenjeId, Integer broj) {
        if (broj == null) brojevi.remove(izvodjenjeId);
        else brojevi.put(izvodjenjeId, broj);
    }

    /** Kraj izvođenja: briše i prikaz i broj redova. */
    public void zavrsi(Long izvodjenjeId) {
        prikazi.remove(izvodjenjeId);
        brojevi.remove(izvodjenjeId);
    }
}
