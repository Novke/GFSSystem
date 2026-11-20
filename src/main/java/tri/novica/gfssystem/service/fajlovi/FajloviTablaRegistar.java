package tri.novica.gfssystem.service.fajlovi;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ručni prekidač trake sa linkom fajlova na projektoru (taster {@code K}, spec fajlova 6): nastavnik na trenutnom mestu
 * izvođenja (slajd, ili prijava = {@code null}) pali ili gasi traku mimo pravila. Važi samo dok je izvođenje na tom
 * mestu: prelazak na drugi slajd ga briše ({@code IzvodjenjeService.idiNa}), a zapis za drugo mesto se ionako ne
 * primenjuje. Samo u memoriji, bez migracije: posle restarta backenda važi pravilo. Menja se pod zaključanim redom
 * izvođenja.
 */
@Component
public class FajloviTablaRegistar {

    private record Prekidac(Long slajdId, boolean prikazan) {
    }

    private final Map<Long, Prekidac> prekidaci = new ConcurrentHashMap<>();

    /** Ručno stanje za ovo mesto izvođenja; prazno kad nastavnik ovde nije dirao traku. */
    public Optional<Boolean> prikazan(Long izvodjenjeId, Long slajdId) {
        Prekidac p = prekidaci.get(izvodjenjeId);
        return p != null && Objects.equals(p.slajdId(), slajdId) ? Optional.of(p.prikazan()) : Optional.empty();
    }

    public void postavi(Long izvodjenjeId, Long slajdId, boolean prikazan) {
        prekidaci.put(izvodjenjeId, new Prekidac(slajdId, prikazan));
    }

    public void ukloni(Long izvodjenjeId) {
        prekidaci.remove(izvodjenjeId);
    }
}
