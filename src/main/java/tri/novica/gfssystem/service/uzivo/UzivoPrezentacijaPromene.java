package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Izmene slajdova tokom izvođenja: {@link PrezentacijaService} poziva ove metode u svojoj transakciji, dok drži
 * zaključanu prezentaciju (redosled zaključavanja prezentacija -> izvođenje, kao i {@code pokreni}).
 */
@Component
@RequiredArgsConstructor
public class UzivoPrezentacijaPromene implements PrezentacijaPromene {

    private final IzvodjenjeService izvodjenjeService;

    @Override
    public void slajdoviPromenjeni(Long prezentacijaId) {
        izvodjenjeService.prezentacijaPromenjena(prezentacijaId);
    }

    @Override
    public void slajdObrisan(Long prezentacijaId, Long slajdId, int stariIndeks) {
        izvodjenjeService.slajdObrisan(prezentacijaId, slajdId, stariIndeks);
    }
}
