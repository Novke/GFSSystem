package tri.novica.gfssystem.service.uzivo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** Sat koji test pomera ručno (tajmer, rok). */
final class MutableClock extends Clock {
    private Instant sada;
    private final ZoneId zona;

    MutableClock(Instant sada, ZoneId zona) {
        this.sada = sada;
        this.zona = zona;
    }

    void pomeri(Duration d) {
        sada = sada.plus(d);
    }

    @Override public ZoneId getZone() { return zona; }
    @Override public Clock withZone(ZoneId z) { return new MutableClock(sada, z); }
    @Override public Instant instant() { return sada; }
}
