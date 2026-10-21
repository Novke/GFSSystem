package tri.novica.gfssystem.service.prijava;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Sat koji test pomera ručno; jedan bean u kontekstu testova prijave ({@link PrijavaSatConfig}). */
final class PomerljivSat extends Clock {
    private volatile Instant sada = Instant.now();
    private final ZoneId zona = ZoneId.systemDefault();

    void postavi(Instant i) {
        sada = i;
    }

    void pomeri(Duration d) {
        sada = sada.plus(d);
    }

    @Override public ZoneId getZone() { return zona; }
    @Override public Clock withZone(ZoneId z) { return this; }
    @Override public Instant instant() { return sada; }
}
