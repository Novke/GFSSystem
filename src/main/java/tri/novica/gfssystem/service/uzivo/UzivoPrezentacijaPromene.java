package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Izmene slajdova tokom izvođenja: {@link PrezentacijaService} poziva ove metode u svojoj transakciji, dok drži
 * zaključanu prezentaciju. {@code zakljucaj} odmah posle zaključavanja prezentacije zaključa i aktivna izvođenja, pre
 * upisa slajdova (redosled prezentacija -> izvođenje -> slajd/runda, kao i {@code pokreni} i komande).
 */
@Component
@RequiredArgsConstructor
public class UzivoPrezentacijaPromene implements PrezentacijaPromene {

    private final IzvodjenjeService izvodjenjeService;

    @Override
    public void zakljucaj(Long prezentacijaId) {
        izvodjenjeService.zakljucajAktivna(prezentacijaId);
    }

    @Override
    public void slajdoviPromenjeni(Long prezentacijaId) {
        izvodjenjeService.prezentacijaPromenjena(prezentacijaId);
    }

    @Override
    public void slajdObrisan(Long prezentacijaId, Long slajdId, int stariIndeks) {
        izvodjenjeService.slajdObrisan(prezentacijaId, slajdId, stariIndeks);
    }
}
