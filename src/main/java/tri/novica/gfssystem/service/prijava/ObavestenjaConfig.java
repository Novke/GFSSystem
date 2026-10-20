package tri.novica.gfssystem.service.prijava;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Izvršilac za obaveštenja koja se upisuju u pozadini ({@link DostignutLimitKodova}). Poseban od {@code mejlIzvrsilac}:
 * njegove niti mogu satima da čekaju SMTP (pauze između pokušaja), a obaveštenje je kratak upis u bazu. Pun red ne sme da
 * baci izuzetak (kao u {@code MejlConfig}): poruka se odbacuje uz zapis u log, a obaveštenje se tada ne upisuje.
 */
@Configuration
@Slf4j
public class ObavestenjaConfig {

    @Bean("obavestenjaIzvrsilac")
    public ThreadPoolTaskExecutor obavestenjaIzvrsilac() {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(2);
        e.setMaxPoolSize(2);
        e.setQueueCapacity(100);
        e.setThreadNamePrefix("obavestenja-");
        e.setWaitForTasksToCompleteOnShutdown(false);
        e.setRejectedExecutionHandler((zadatak, izvrsilac) ->
                log.warn("Obaveštenje nije upisano: red je pun, zadatak je odbačen"));
        return e;
    }
}
