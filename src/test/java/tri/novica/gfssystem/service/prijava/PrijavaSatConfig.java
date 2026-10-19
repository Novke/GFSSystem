package tri.novica.gfssystem.service.prijava;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Zamenjuje {@code Clock} u testovima prijave pomerljivim satom (bean {@code clock} ostaje, ovaj je {@code @Primary}). */
@TestConfiguration
class PrijavaSatConfig {
    @Bean
    @Primary
    PomerljivSat pomerljivSat() {
        return new PomerljivSat();
    }
}
