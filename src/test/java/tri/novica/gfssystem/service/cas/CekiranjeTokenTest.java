package tri.novica.gfssystem.service.cas;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.*;

class CekiranjeTokenTest {

    static final String T = "abcdefghjkmnpqrstuvwxyzABCDEFGHJ";
    static final String T2 = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefgh";

    @Test
    void istiProzorIstiToken() {
        assertEquals(CekiranjeToken.token(T, 7, 100_000), CekiranjeToken.token(T, 7, 109_999));
        assertNotEquals(CekiranjeToken.token(T, 7, 100_000), CekiranjeToken.token(T, 7, 110_000));
        assertEquals(110_000, CekiranjeToken.vaziDoMs(100_000));
        assertEquals(110_000, CekiranjeToken.vaziDoMs(109_999));
    }

    @Test
    void oblik() {
        assertTrue(CekiranjeToken.token(T, 7, 1_761_667_200_000L).matches("^7\\.[A-Za-z0-9_-]{22}$"));
    }

    @Test
    void vaziTriProzora() {
        long t0 = 1_761_667_200_000L;
        String tok = CekiranjeToken.token(T, 7, t0);
        assertTrue(CekiranjeToken.vazi(T, 7, tok, t0));
        assertTrue(CekiranjeToken.vazi(T, 7, tok, t0 + 10_000));
        assertTrue(CekiranjeToken.vazi(T, 7, tok, t0 + 20_000));
        assertFalse(CekiranjeToken.vazi(T, 7, tok, t0 + 30_000));
        assertFalse(CekiranjeToken.vazi(T, 7, tok, t0 - 10_000));
    }

    @Test
    void tudjePredavanjeITajna() {
        long t0 = 1_761_667_200_000L;
        String tok = CekiranjeToken.token(T, 7, t0);
        assertFalse(CekiranjeToken.vazi(T, 8, tok, t0));
        assertFalse(CekiranjeToken.vazi(T2, 7, tok, t0));
        assertFalse(CekiranjeToken.vazi(null, 7, tok, t0));
        assertFalse(CekiranjeToken.vazi(T, 7, null, t0));
        // isti HMAC sa drugim brojem predavanja ispred ne prolazi
        assertFalse(CekiranjeToken.vazi(T, 8, "8" + tok.substring(1), t0));
    }

    @Test
    void predavanjeIz() {
        assertEquals(OptionalLong.of(7), CekiranjeToken.predavanjeIz("7.xxxxxxxxxxxxxxxxxxxxxx"));
        assertTrue(CekiranjeToken.predavanjeIz("07.xxxxxxxxxxxxxxxxxxxxxx").isEmpty());
        assertTrue(CekiranjeToken.predavanjeIz("7").isEmpty());
        assertTrue(CekiranjeToken.predavanjeIz("7.kratko").isEmpty());
        assertTrue(CekiranjeToken.predavanjeIz("a.b").isEmpty());
        assertTrue(CekiranjeToken.predavanjeIz(null).isEmpty());
        assertTrue(CekiranjeToken.predavanjeIz("99999999999999999999.xxxxxxxxxxxxxxxxxxxxxx").isEmpty());
        assertTrue(CekiranjeToken.predavanjeIz("9999999999999999999.xxxxxxxxxxxxxxxxxxxxxx").isEmpty());
    }
}
