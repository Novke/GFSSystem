package tri.novica.gfssystem.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.uzivo.Prelaz;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.dto.uzivo.ZadatakSlajda;
import tri.novica.gfssystem.entity.uzivo.TipPrelaza;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;

import static org.junit.jupiter.api.Assertions.*;

/** {@link SlajdPP} za slajd ZADATAK: normalizacija (samo beleške, prelaz i zadatak) i granice grupe, vremena i broja varijanti. */
class SlajdPPZadatakTest {

    static final String VREME = "Vreme za odgovor mora biti od 5 do 600 sekundi.";
    static final String BEZ_GRUPE = "Zadatak slajd mora imati grupu zadataka.";
    static final String BROJ = "Broj različitih zadataka mora biti od 1 do 50.";

    static SlajdCmd zadatak(ZadatakSlajda z) {
        return new SlajdCmd(TipSlajda.ZADATAK, null, null, null, null, false, null, null, null, z);
    }

    static SlajdCmd zadatak(Integer vreme, Integer broj) {
        return zadatak(new ZadatakSlajda(3L, vreme, broj));
    }

    static void greska(String poruka, SlajdCmd cmd) {
        Executable e = () -> SlajdPP.proveri(SlajdPP.normalizuj(cmd));
        SystemException ex = assertThrows(SystemException.class, e);
        assertEquals(400, ex.getCode());
        assertEquals(poruka, ex.getMessage());
    }

    static void ispravan(SlajdCmd cmd) {
        assertDoesNotThrow(() -> SlajdPP.proveri(SlajdPP.normalizuj(cmd)));
    }

    @Test
    void normalizacijaZadrzavaSamoBeleskePrelazIZadatak() {
        ZadatakSlajda z = new ZadatakSlajda(3L, 120, 4);
        JsonMapper mapper = JsonMapper.builder().build();
        SlajdCmd n = SlajdPP.normalizuj(new SlajdCmd(TipSlajda.ZADATAK, "Naslov", "Sadržaj", "slika", "  beleška  ", true,
                null, mapper.createObjectNode(), new Prelaz(TipPrelaza.FADE, null, null), z));

        assertEquals(TipSlajda.ZADATAK, n.tip());
        assertNull(n.naslov());
        assertNull(n.sadrzaj());
        assertNull(n.slikaId());
        assertNull(n.pitanje());
        assertNull(n.platno());
        assertFalse(n.postepeno());
        assertEquals("beleška", n.beleske());
        assertEquals(new Prelaz(TipPrelaza.FADE, null, SlajdPP.PODRAZUMEVANO_PRELAZ_MS), n.prelaz());
        assertEquals(z, n.zadatak());
    }

    @Test
    void ostaliTipoviDobijajuZadatakNull() {
        ZadatakSlajda z = new ZadatakSlajda(3L, 120, 4);
        assertNull(SlajdPP.normalizuj(new SlajdCmd(TipSlajda.INFO, "Uvod", null, null, null, false, null, null, null, z))
                .zadatak());
        assertNull(SlajdPP.normalizuj(new SlajdCmd(TipSlajda.PLATNO, null, null, null, null, false, null,
                JsonMapper.builder().build().createObjectNode(), null, z)).zadatak());
    }

    @Test
    void bezZadatkaIliBezGrupeJe400() {
        greska(BEZ_GRUPE, zadatak((ZadatakSlajda) null));
        greska(BEZ_GRUPE, zadatak(new ZadatakSlajda(null, 120, 4)));
    }

    @Test
    void vremeVanGranicaJe400() {
        greska(VREME, zadatak(4, null));
        greska(VREME, zadatak(601, null));
        greska(VREME, zadatak(-1, null));
        ispravan(zadatak(5, null));
        ispravan(zadatak(600, null));
        ispravan(zadatak(null, null));
    }

    @Test
    void brojVarijantiVanGranicaJe400() {
        greska(BROJ, zadatak(null, 0));
        greska(BROJ, zadatak(null, 51));
        greska(BROJ, zadatak(null, -3));
        ispravan(zadatak(null, 1));
        ispravan(zadatak(null, 50));
        ispravan(zadatak(null, null));
    }

    @Test
    void zajednickaPravilaVazeIZaZadatak() {
        greska("Beleške mogu imati najviše 20000 znakova.", new SlajdCmd(TipSlajda.ZADATAK, null, null, null,
                "a".repeat(20_001), false, null, null, null, new ZadatakSlajda(3L, null, null)));
        greska(SlajdPP.PRELAZ_TRAJANJE, new SlajdCmd(TipSlajda.ZADATAK, null, null, null, null, false, null, null,
                new Prelaz(TipPrelaza.FADE, null, 50), new ZadatakSlajda(3L, null, null)));
    }

    @Test
    void zadatakNemaSlikeUTekstu() {
        assertEquals(0, SlajdPP.slikeSlajda(SlajdPP.normalizuj(zadatak(120, 4))).size());
    }

    @Test
    void konstanteIzDogovora() {
        assertEquals(BEZ_GRUPE, SlajdPP.ZADATAK_BEZ_GRUPE);
        assertEquals(BROJ, SlajdPP.BROJ_VARIJANTI);
        assertEquals(1, SlajdPP.MIN_BROJ_VARIJANTI);
        assertEquals(50, SlajdPP.MAX_BROJ_VARIJANTI);
    }
}
