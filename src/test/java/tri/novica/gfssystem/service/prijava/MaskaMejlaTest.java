package tri.novica.gfssystem.service.prijava;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Maska mejla i lažna maska (spec 5.1): lažna je stabilna po indeksu i istog oblika kao prava. */
class MaskaMejlaTest {

    static final Pattern OBLIK = Pattern.compile("^[a-z]\\*\\*\\*@[a-z.]+$");

    KljuceviAplikacije kljucevi;
    MaskaMejla maska;

    @BeforeEach
    void setUp() {
        kljucevi = mock(KljuceviAplikacije.class);
        byte[] k = new byte[32];
        for (int i = 0; i < k.length; i++) k[i] = (byte) (i * 7 + 3);
        when(kljucevi.kljuc(KljuceviAplikacije.MASKA_HMAC)).thenReturn(k);
        maska = new MaskaMejla(kljucevi);
    }

    @Test
    void pravaMaska() {
        assertEquals("p***@example.test", maska.maska("pera.peric@example.test"));
        assertEquals("a***@example.test", maska.maska("ana@example.test"));
        // isti oblik kao lažna: mala slova, bez razmaka
        assertEquals("p***@example.test", maska.maska("  Pera@Example.TEST "));
    }

    @Test
    void pravaMaskaNeispravneAdrese() {
        assertNull(maska.maska(null));
        assertNull(maska.maska("  "));
        assertEquals("p***", maska.maska("pera"));
        assertEquals("***@example.test", maska.maska("@example.test"));
    }

    @Test
    void laznaJeStabilnaIIstogOblika() {
        String a = maska.lazna("GD12/2024");
        assertEquals(a, maska.lazna("GD12/2024"));
        assertTrue(OBLIK.matcher(a).matches(), a);
        Set<String> razlicite = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String m = maska.lazna("GD" + i + "/2024");
            assertTrue(OBLIK.matcher(m).matches(), m);
            assertTrue(MaskaMejla.DOMENI.stream().anyMatch(d -> m.endsWith("@" + d)), m);
            razlicite.add(m);
        }
        // 26 slova x nekoliko domena: različiti indeksi najčešće daju različite maske
        assertTrue(razlicite.size() > 40, "samo " + razlicite.size() + " različitih maski");
        assertNotEquals(maska.lazna("GD12/2024"), maska.lazna("GD13/2024"));
    }

    @Test
    void laznaZavisiOdKljuca() {
        java.util.List<String> saPrvim = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) saPrvim.add(maska.lazna("X" + i));
        when(kljucevi.kljuc(KljuceviAplikacije.MASKA_HMAC)).thenReturn(new byte[32]);
        java.util.List<String> saDrugim = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) saDrugim.add(maska.lazna("X" + i));
        // ključ ulazi u HMAC: bez ključa iz baze maska se ne može izračunati unapred
        assertNotEquals(saPrvim, saDrugim);
    }
}
