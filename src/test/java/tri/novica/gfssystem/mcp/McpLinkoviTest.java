package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Linkovi u UI: prvi URL iz {@code gfs.front.url}, bez završne {@code /}. */
class McpLinkoviTest {

    final McpLinkovi linkovi = new McpLinkovi("https://gfs.dev.trif.rs, http://localhost:4200");

    @Test
    void prviUrlJeBaza() {
        assertEquals("https://gfs.dev.trif.rs", linkovi.baza());
        assertEquals("https://gfs.dev.trif.rs/banka/varijante/5", linkovi.varijanta(5L));
        assertEquals("https://gfs.dev.trif.rs/banka/grupe/7", linkovi.grupa(7L));
        assertEquals("https://gfs.dev.trif.rs/prezentacije/9", linkovi.prezentacija(9L));
        assertEquals("https://gfs.dev.trif.rs/banka/pregled?serija=uvoz-kol1-2024", linkovi.pregled("uvoz-kol1-2024"));
    }

    @Test
    void pregledEnkodujeSeriju() {
        assertEquals("https://gfs.dev.trif.rs/banka/pregled?serija=uvoz%20kol", linkovi.pregled("uvoz kol"));
        assertEquals("https://gfs.dev.trif.rs/banka/pregled?serija=a%26b%3Dc", linkovi.pregled("a&b=c"));
    }

    @Test
    void zavrsnaKosaCrtaSeSkida() {
        assertEquals("https://gfs.trif.rs", new McpLinkovi("https://gfs.trif.rs/").baza());
        assertEquals("https://gfs.trif.rs/banka/grupe/1", new McpLinkovi(" https://gfs.trif.rs/ ,http://x").grupa(1L));
    }
}
