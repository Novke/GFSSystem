package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.entity.uzivo.Faza;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.entity.uzivo.PitanjeRunda;
import tri.novica.gfssystem.entity.uzivo.StatusIzvodjenja;
import tri.novica.gfssystem.repository.uzivo.IzvodjenjeRepository;
import tri.novica.gfssystem.repository.uzivo.PitanjeRundaRepository;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Oporavak i održavanje: posle starta ponovo zakaže zatvaranje otvorenih pitanja sa rokom (prošao rok -> zatvori
 * odmah); svakih sat vremena završi izvođenja aktivna duže od 12 h. Sve kroz {@link IzvodjenjeService} (isto
 * zaključavanje kao komande); greška kod jednog izvođenja ne zaustavlja ostala.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IzvodjenjeOdrzavanje {

    private final IzvodjenjeRepository izvodjenjeRepository;
    private final PitanjeRundaRepository rundaRepository;
    private final IzvodjenjeService izvodjenjeService;
    private final RokPlaner rokPlaner;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    public void posleStarta() {
        for (Izvodjenje iz : izvodjenjeRepository.findAllByStatus(StatusIzvodjenja.AKTIVNO)) {
            if (iz.getFaza() != Faza.OTVORENO || iz.getTrenutnaRundaId() == null) continue;
            try {
                PitanjeRunda r = rundaRepository.findById(iz.getTrenutnaRundaId()).orElse(null);
                if (r == null || r.getRok() == null || r.getZatvoreno() != null) continue;
                if (sada().isBefore(r.getRok().plus(IzvodjenjeService.TOLERANCIJA))) {
                    rokPlaner.zakazi(iz.getId(), r.getId(), r.getRok());
                } else {
                    izvodjenjeService.zatvoriPoRoku(iz.getId(), r.getId());
                }
            } catch (RuntimeException e) {
                log.warn("Oporavak roka nije uspeo: izvodjenje={}", iz.getId(), e);
            }
        }
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void zavrsiStara() {
        LocalDateTime granica = sada().minus(IzvodjenjeService.NAJDUZE_TRAJANJE);
        for (Long id : izvodjenjeRepository.findIdsStarijaOd(StatusIzvodjenja.AKTIVNO, granica)) {
            try {
                izvodjenjeService.zavrsiAutomatski(id);
            } catch (RuntimeException e) {
                log.warn("Automatski završetak nije uspeo: izvodjenje={}", id, e);
            }
        }
    }

    private LocalDateTime sada() {
        return LocalDateTime.now(clock);
    }
}
