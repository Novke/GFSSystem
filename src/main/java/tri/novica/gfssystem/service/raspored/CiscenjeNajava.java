package tri.novica.gfssystem.service.raspored;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Privatnost najava odsustva: slobodan tekst studenta ({@code najave_odsustva.razlog}) ne živi duže od
 * {@value #DANA_POSLE_TERMINA} dana posle termina. Red najave ostaje (ko je javio i kada), briše se samo razlog. Jednom na
 * sat u 17. minutu (posle {@code CiscenjePrijave} u 7.), u grupama od {@value #VELICINA_GRUPE} redova (svaka grupa je svoj
 * commit). Greška se samo loguje (klasa izuzetka). Log: samo broj.
 */
@Component
@Slf4j
public class CiscenjeNajava {

    static final int DANA_POSLE_TERMINA = 30;
    static final int VELICINA_GRUPE = 5000;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public CiscenjeNajava(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Briše razlog najava čiji je termin bio pre {@value #DANA_POSLE_TERMINA} i više dana. Cron se čita iz
     * {@code gfs.najave.ciscenje.cron} ({@code -} gasi raspored, testovi ga gase); vraća broj očišćenih redova.
     */
    @Scheduled(cron = "${gfs.najave.ciscenje.cron:0 17 * * * *}")
    public int ocisti() {
        LocalDate granica = LocalDate.now(clock).minusDays(DANA_POSLE_TERMINA);
        int ukupno = 0;
        try {
            int grupa;
            do {
                grupa = jdbc.update("update najave_odsustva set razlog = null where razlog is not null and datum <= ? limit "
                        + VELICINA_GRUPE, granica);
                ukupno += grupa;
            } while (grupa >= VELICINA_GRUPE);
        } catch (RuntimeException e) {
            log.error("Čišćenje razloga najava nije uspelo (očišćeno do greške {}): {}", ukupno, e.getClass().getSimpleName());
        }
        if (ukupno > 0) {
            log.info("Čišćenje razloga najava: {}", ukupno);
        }
        return ukupno;
    }
}
