package tri.novica.gfssystem.config;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.validation.PlatnoPP;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketConfigTest {

    @Test
    void baferSlanjaStajeUSnimakSaDvaPlatna() {
        // NastavnickoStanje nosi platno tekućeg i sledećeg slajda, svako do MAX_BAJTOVA; prekoračenje zatvara sesiju
        assertTrue(WebSocketConfig.MAX_BAFER_SLANJA >= 2 * PlatnoPP.MAX_BAJTOVA + 1_048_576);
    }

    @Test
    void dolaznaPorukaOstajeOsamKilobajta() {
        assertEquals(8 * 1024, WebSocketConfig.MAX_PORUKA);
    }
}
