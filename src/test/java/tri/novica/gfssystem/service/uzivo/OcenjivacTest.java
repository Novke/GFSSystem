package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;
import tri.novica.gfssystem.entity.uzivo.TekstPrikaz;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OcenjivacTest {

    static PitanjeSnimak pitanje(TipPitanja tip, List<OpcijaSnimak> opcije, Double brojTacno, Double odstupanje,
                                 OdstupanjeTip odstupanjeTip, List<String> prihvatljivi) {
        return new PitanjeSnimak(1L, 2L, tip, "Pitanje?", null, null, opcije, brojTacno, odstupanje, odstupanjeTip, null,
                tip == TipPitanja.KRATAK_TEKST ? TekstPrikaz.OBLAK : null, prihvatljivi, null, null);
    }

    static List<OpcijaSnimak> opcije(long... tacne) {
        // ID-jevi 10, 11, 12, 13; tacne su id-jevi tacnih opcija
        java.util.ArrayList<OpcijaSnimak> l = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            long id = 10 + i;
            boolean t = false;
            for (long x : tacne) if (x == id) t = true;
            l.add(new OpcijaSnimak(id, i + 1, "Opcija " + i, t));
        }
        return l;
    }

    static OdgovorVrednost op(Long... ids) { return new OdgovorVrednost(List.of(ids), null, null, null); }

    static OdgovorVrednost br(Double d) { return new OdgovorVrednost(null, d, null, null); }

    static OdgovorVrednost tx(String s) { return new OdgovorVrednost(null, null, s, null); }

    static OdgovorVrednost sk(Integer s) { return new OdgovorVrednost(null, null, null, s); }

    static void ocekujGresku(String poruka, Runnable r) {
        SystemException e = assertThrows(SystemException.class, r::run);
        assertEquals(poruka, e.getMessage());
        assertEquals(400, e.getCode());
    }

    // --- jedan tacan ---

    @Test void jedanTacan() {
        PitanjeSnimak p = pitanje(TipPitanja.JEDAN_TACAN, opcije(11), null, null, null, null);
        assertEquals(Boolean.TRUE, Ocenjivac.tacno(p, op(11L)));
        assertEquals(Boolean.FALSE, Ocenjivac.tacno(p, op(10L)));
    }

    @Test void jedanTacanDveOpcije() {
        PitanjeSnimak p = pitanje(TipPitanja.JEDAN_TACAN, opcije(11), null, null, null, null);
        ocekujGresku("Izaberi tačno jedan odgovor.", () -> Ocenjivac.tacno(p, op(10L, 11L)));
        ocekujGresku("Izaberi tačno jedan odgovor.", () -> Ocenjivac.proveri(p, op()));
        ocekujGresku("Izaberi tačno jedan odgovor.", () -> Ocenjivac.proveri(p, new OdgovorVrednost(null, null, null, null)));
    }

    @Test void nepoznatId() {
        PitanjeSnimak p = pitanje(TipPitanja.JEDAN_TACAN, opcije(11), null, null, null, null);
        ocekujGresku("Nepoznat odgovor.", () -> Ocenjivac.tacno(p, op(99L)));
        PitanjeSnimak v = pitanje(TipPitanja.VISE_TACNIH, opcije(11, 12), null, null, null, null);
        ocekujGresku("Nepoznat odgovor.", () -> Ocenjivac.tacno(v, op(11L, 99L)));
    }

    // --- vise tacnih ---

    @Test void viseTacnih() {
        PitanjeSnimak p = pitanje(TipPitanja.VISE_TACNIH, opcije(11, 12), null, null, null, null);
        assertEquals(Boolean.TRUE, Ocenjivac.tacno(p, op(12L, 11L)));
        assertEquals(Boolean.FALSE, Ocenjivac.tacno(p, op(11L)));
        assertEquals(Boolean.FALSE, Ocenjivac.tacno(p, op(11L, 12L, 13L)));
    }

    @Test void viseTacnihPrazno() {
        PitanjeSnimak p = pitanje(TipPitanja.VISE_TACNIH, opcije(11, 12), null, null, null, null);
        ocekujGresku("Izaberi bar jedan odgovor.", () -> Ocenjivac.tacno(p, op()));
    }

    @Test void viseTacnihDuplikatiSeSpajaju() {
        PitanjeSnimak p = pitanje(TipPitanja.VISE_TACNIH, opcije(11, 12), null, null, null, null);
        assertEquals(Boolean.TRUE, Ocenjivac.tacno(p, op(11L, 11L, 12L)));
    }

    // --- anketa i tacno/netacno ---

    @Test void anketaNemaTacan() {
        PitanjeSnimak p = pitanje(TipPitanja.ANKETA, opcije(), null, null, null, null);
        assertNull(Ocenjivac.tacno(p, op(10L)));
        ocekujGresku("Nepoznat odgovor.", () -> Ocenjivac.tacno(p, op(99L)));
        assertFalse(p.imaTacanOdgovor());
    }

    @Test void tacnoNetacno() {
        PitanjeSnimak p = new PitanjeSnimak(1L, 2L, TipPitanja.TACNO_NETACNO, "?", null, null,
                List.of(new OpcijaSnimak(10L, 1, "Tačno", true), new OpcijaSnimak(11L, 2, "Netačno", false)),
                null, null, null, null, null, null, null, null);
        assertEquals(Boolean.TRUE, Ocenjivac.tacno(p, op(10L)));
        assertEquals(Boolean.FALSE, Ocenjivac.tacno(p, op(11L)));
        assertTrue(p.imaTacanOdgovor());
    }

    // --- kratak tekst ---

    @Test void kratakTekst() {
        PitanjeSnimak p = pitanje(TipPitanja.KRATAK_TEKST, null, null, null, null, List.of("Čvrstoća"));
        assertEquals(Boolean.TRUE, Ocenjivac.tacno(p, tx("cvrstoca ")));
        assertEquals(Boolean.TRUE, Ocenjivac.tacno(p, tx("ЧВРСТОЋА")));
        assertEquals(Boolean.FALSE, Ocenjivac.tacno(p, tx("tvrdoca")));
    }

    @Test void kratakTekstBezPrihvatljivih() {
        PitanjeSnimak p = pitanje(TipPitanja.KRATAK_TEKST, null, null, null, null, List.of());
        assertNull(Ocenjivac.tacno(p, tx("bilo sta")));
        PitanjeSnimak n = pitanje(TipPitanja.KRATAK_TEKST, null, null, null, null, null);
        assertNull(Ocenjivac.tacno(n, tx("bilo sta")));
        assertFalse(p.imaTacanOdgovor());
        assertFalse(n.imaTacanOdgovor());
    }

    @Test void kratakTekstGranice() {
        PitanjeSnimak p = pitanje(TipPitanja.KRATAK_TEKST, null, null, null, null, List.of("a"));
        String poruka = "Odgovor mora imati od 1 do 200 znakova.";
        ocekujGresku(poruka, () -> Ocenjivac.tacno(p, tx("")));
        ocekujGresku(poruka, () -> Ocenjivac.tacno(p, tx("   ")));
        ocekujGresku(poruka, () -> Ocenjivac.tacno(p, tx(null)));
        ocekujGresku(poruka, () -> Ocenjivac.tacno(p, tx("a".repeat(201))));
        assertDoesNotThrow(() -> Ocenjivac.tacno(p, tx("a".repeat(200))));
    }

    // --- broj ---

    @Test void brojApsolutno() {
        PitanjeSnimak p = pitanje(TipPitanja.BROJ, null, 10.0, 0.5, OdstupanjeTip.APSOLUTNO, null);
        assertEquals(Boolean.TRUE, Ocenjivac.tacno(p, br(10.5)));
        assertEquals(Boolean.TRUE, Ocenjivac.tacno(p, br(9.5)));
        assertEquals(Boolean.FALSE, Ocenjivac.tacno(p, br(10.6)));
    }

    @Test void brojProcenat() {
        PitanjeSnimak p = pitanje(TipPitanja.BROJ, null, 10.0, 5.0, OdstupanjeTip.PROCENAT, null);
        assertEquals(Boolean.TRUE, Ocenjivac.tacno(p, br(10.5)));
        assertEquals(Boolean.FALSE, Ocenjivac.tacno(p, br(10.6)));
    }

    @Test void brojTolerancijaZaPlutajuciZarez() {
        PitanjeSnimak p = pitanje(TipPitanja.BROJ, null, 0.1, 0.2, OdstupanjeTip.APSOLUTNO, null);
        assertEquals(Boolean.TRUE, Ocenjivac.tacno(p, br(0.3)));
    }

    @Test void brojBezOdstupanjaMoraBitiTacan() {
        PitanjeSnimak p = pitanje(TipPitanja.BROJ, null, 10.0, null, null, null);
        assertEquals(Boolean.TRUE, Ocenjivac.tacno(p, br(10.0)));
        assertEquals(Boolean.FALSE, Ocenjivac.tacno(p, br(10.01)));
    }

    @Test void brojProcenaNemaTacan() {
        PitanjeSnimak p = pitanje(TipPitanja.BROJ, null, null, null, null, null);
        assertNull(Ocenjivac.tacno(p, br(42.0)));
        assertFalse(p.imaTacanOdgovor());
    }

    @Test void brojNull() {
        PitanjeSnimak p = pitanje(TipPitanja.BROJ, null, 10.0, 0.0, OdstupanjeTip.APSOLUTNO, null);
        ocekujGresku("Unesi broj.", () -> Ocenjivac.tacno(p, br(null)));
        ocekujGresku("Unesi broj.", () -> Ocenjivac.tacno(p, br(Double.NaN)));
        ocekujGresku("Unesi broj.", () -> Ocenjivac.tacno(p, br(Double.POSITIVE_INFINITY)));
    }

    // --- skala ---

    @Test void skala() {
        PitanjeSnimak p = pitanje(TipPitanja.SKALA, null, null, null, null, null);
        for (int i = 1; i <= 5; i++) assertNull(Ocenjivac.tacno(p, sk(i)));
        ocekujGresku("Izaberi vrednost od 1 do 5.", () -> Ocenjivac.tacno(p, sk(0)));
        ocekujGresku("Izaberi vrednost od 1 do 5.", () -> Ocenjivac.tacno(p, sk(6)));
        ocekujGresku("Izaberi vrednost od 1 do 5.", () -> Ocenjivac.tacno(p, sk(null)));
        assertFalse(p.imaTacanOdgovor());
    }

    // --- poeni ---

    @Test void poeniSaTajmerom() {
        assertEquals(1000, Ocenjivac.poeni(true, true, 20000L, 0));
        assertEquals(750, Ocenjivac.poeni(true, true, 20000L, 10000));
        assertEquals(500, Ocenjivac.poeni(true, true, 20000L, 20000));
        assertEquals(500, Ocenjivac.poeni(true, true, 20000L, 30000));
    }

    @Test void poeniBezTajmera() {
        assertEquals(1000, Ocenjivac.poeni(true, true, null, 5000));
        assertEquals(1000, Ocenjivac.poeni(true, true, 0L, 5000));
    }

    @Test void poeniNula() {
        assertEquals(0, Ocenjivac.poeni(false, true, 20000L, 1000));
        assertEquals(0, Ocenjivac.poeni(null, true, 20000L, 1000));
        assertEquals(0, Ocenjivac.poeni(true, false, 20000L, 1000));
        assertEquals(0, Ocenjivac.poeni(false, true, null, 1000));
    }
}
