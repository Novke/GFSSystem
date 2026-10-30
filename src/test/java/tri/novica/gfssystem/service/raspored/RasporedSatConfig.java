package tri.novica.gfssystem.service.raspored;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.*;

/**
 * Zamenjuje {@code Clock} u testovima rasporeda satom koji test postavlja ({@code @Primary}); termini zavise od dana u
 * nedelji i od toga da li je čas počeo, pa testovi biraju trenutak (daleko u budućnosti, da se ne sudare sa drugim podacima).
 */
@TestConfiguration(proxyBeanMethods = false)
public class RasporedSatConfig {

    @Bean
    @Primary
    Sat rasporedSat() {
        return new Sat();
    }

    /** Sat koji test pomera ručno, u podrazumevanoj zoni JVM-a (kao {@code Clock.systemDefaultZone()}). */
    public static final class Sat extends Clock {
        private volatile Instant sada = Instant.now();
        private final ZoneId zona = ZoneId.systemDefault();

        public void postavi(LocalDateTime lokalno) {
            sada = lokalno.atZone(zona).toInstant();
        }

        @Override public ZoneId getZone() { return zona; }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant() { return sada; }
    }
}
