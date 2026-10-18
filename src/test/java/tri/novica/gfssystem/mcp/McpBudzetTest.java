package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.exceptions.SystemException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/** Dnevni budžet po oznaci ključa: 2 000 pravljenja i 200 MB upload-a, nula u ponoć, preko granice 429 bez trošenja. */
class McpBudzetTest {

    static final ZoneId BEOGRAD = ZoneId.of("Europe/Belgrade");

    /** Sat koji test pomera. */
    static final class PomerljivSat extends Clock {
        Instant sada;

        PomerljivSat(Instant pocetak) {
            this.sada = pocetak;
        }

        void pomeri(Duration d) {
            sada = sada.plus(d);
        }

        @Override public ZoneId getZone() { return BEOGRAD; }
        @Override public Clock withZone(ZoneId zona) { throw new UnsupportedOperationException(); }
        @Override public Instant instant() { return sada; }
    }

    // 23:50 po beogradskom vremenu
    final PomerljivSat sat = new PomerljivSat(Instant.parse("2026-10-18T21:50:00Z"));
    final McpBudzet budzet = new McpBudzet(sat);

    static void ocekuj429(Runnable r) {
        SystemException e = assertThrows(SystemException.class, r::run);
        assertEquals(429, e.getCode());
        assertEquals("Dnevni limit za Kloda je potrošen.", e.getMessage());
    }

    @Test
    void pravljenjaDoGraniceProlaze() {
        for (int i = 0; i < 1_999; i++) {
            budzet.trosiPravljenje("novica-dev", 1);
        }
        budzet.trosiPravljenje("novica-dev", 1);
        ocekuj429(() -> budzet.trosiPravljenje("novica-dev", 1));
        assertEquals(2_000, McpBudzet.MAX_PRAVLJENJA);
    }

    @Test
    void prekoGraniceSeNistaNeRacuna() {
        budzet.trosiPravljenje("novica-dev", 1_990);
        ocekuj429(() -> budzet.trosiPravljenje("novica-dev", 11));
        budzet.trosiPravljenje("novica-dev", 10);
        ocekuj429(() -> budzet.trosiPravljenje("novica-dev", 1));
    }

    @Test
    void poslePonociJeOpetNula() {
        budzet.trosiPravljenje("novica-dev", 2_000);
        budzet.trosiUpload("novica-dev", McpBudzet.MAX_UPLOAD_BAJTOVA);
        ocekuj429(() -> budzet.trosiPravljenje("novica-dev", 1));
        sat.pomeri(Duration.ofMinutes(9));
        ocekuj429(() -> budzet.trosiPravljenje("novica-dev", 1));
        sat.pomeri(Duration.ofMinutes(2));   // 00:01 sledećeg dana
        budzet.trosiPravljenje("novica-dev", 2_000);
        budzet.trosiUpload("novica-dev", McpBudzet.MAX_UPLOAD_BAJTOVA);
    }

    @Test
    void uploadDo200MbProlazi() {
        assertEquals(200L * 1024 * 1024, McpBudzet.MAX_UPLOAD_BAJTOVA);
        budzet.trosiUpload("novica-dev", 150L * 1024 * 1024);
        budzet.trosiUpload("novica-dev", 50L * 1024 * 1024);
        ocekuj429(() -> budzet.trosiUpload("novica-dev", 1));
    }

    @Test
    void drugaOznakaImaSvojBudzet() {
        budzet.trosiPravljenje("novica-dev", 2_000);
        budzet.trosiUpload("novica-dev", McpBudzet.MAX_UPLOAD_BAJTOVA);
        budzet.trosiPravljenje("novica-dev-prethodni", 2_000);
        budzet.trosiUpload("novica-dev-prethodni", McpBudzet.MAX_UPLOAD_BAJTOVA);
        ocekuj429(() -> budzet.trosiPravljenje("novica-dev-prethodni", 1));
    }

    @Test
    void negativnoTrosenjeNijeDozvoljeno() {
        assertThrows(IllegalArgumentException.class, () -> budzet.trosiPravljenje("novica-dev", -1));
        assertThrows(IllegalArgumentException.class, () -> budzet.trosiUpload("novica-dev", -1));
    }
}
