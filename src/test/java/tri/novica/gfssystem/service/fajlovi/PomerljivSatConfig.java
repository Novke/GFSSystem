package tri.novica.gfssystem.service.fajlovi;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** {@code @Primary} {@code Clock} koji test pomera ručno (kod predavanja važi 5 h, kod izvođenja 12 h). */
@TestConfiguration
class PomerljivSatConfig {

    static final class Sat extends Clock {
        private volatile Instant sada = Instant.now();
        private final ZoneId zona = ZoneId.systemDefault();

        void pomeri(Duration d) {
            sada = sada.plus(d);
        }

        @Override public ZoneId getZone() { return zona; }
        @Override public Clock withZone(ZoneId z) { return this; }
        @Override public Instant instant() { return sada; }
    }

    @Bean
    @Primary
    Sat satFajlova() {
        return new Sat();
    }
}
