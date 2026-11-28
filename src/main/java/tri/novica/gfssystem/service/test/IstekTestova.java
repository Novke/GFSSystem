package tri.novica.gfssystem.service.test;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tri.novica.gfssystem.entity.test.TestSmena;
import tri.novica.gfssystem.repository.test.TestSmenaRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Istek online testova bez otvorenog ekrana (spec 12: ocena ne zavisi od klijenta): na 15 s pronalazi pokrenute,
 * nezatvorene smene čiji je zajednički rok prošao i za svaki test u posebnoj transakciji zaključava istekle primerke i
 * zatvara smenu kad je rok prošao svima ({@link OnlineTestService#obradiIstekTesta}). Isto radi lenjo svako čitanje
 * nastavnika. Cron {@code gfs.testovi.istek.cron} ({@code -} gasi raspored, testovi ga gase).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IstekTestova {

    private final TestSmenaRepository smenaRepository;
    private final OnlineTestService service;
    private final Clock clock;

    @Scheduled(cron = "${gfs.testovi.istek.cron:*/15 * * * * *}")
    public void obradi() {
        List<TestSmena> istekle = smenaRepository.istekle(LocalDateTime.now(clock).minusSeconds(StanjeTesta.TOLERANCIJA_SEKUNDI));
        for (TestSmena s : istekle) {
            try {
                service.obradiIstekTesta(s.getTestId());
            } catch (RuntimeException e) {
                log.warn("Istek online testa nije obrađen: test={}", s.getTestId(), e);
            }
        }
    }
}
