package tri.novica.gfssystem.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Raspored za uživo: zatvaranje pitanja po roku ({@code RokPlaner}), održavanje izvođenja i {@code @Scheduled} poslovi.
 * Bean se zove {@code taskScheduler}, pa ga {@code @EnableScheduling} koristi i kad WebSocket broker doda svoj.
 */
@Configuration
@EnableScheduling
@Slf4j
public class UzivoSchedulingConfig {

    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler s = new ThreadPoolTaskScheduler();
        s.setPoolSize(2);
        s.setThreadNamePrefix("uzivo-");
        s.setRemoveOnCancelPolicy(true);
        s.setErrorHandler(t -> log.error("Greška u zakazanom poslu", t));
        return s;
    }
}
