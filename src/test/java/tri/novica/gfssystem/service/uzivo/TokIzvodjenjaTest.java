package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.dto.uzivo.platno.*;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.entity.uzivo.Pitanje;
import tri.novica.gfssystem.entity.uzivo.Slajd;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokIzvodjenjaTest {

    private static Slajd slajd(TipSlajda tip, String sadrzaj, boolean postepeno, String platno) {
        Slajd s = new Slajd();
        s.setTip(tip);
        s.setSadrzaj(sadrzaj);
        s.setPostepeno(postepeno);
        s.setPlatno(platno);
        return s;
    }

    private static Animacija anim(String id, Okidac okidac) {
        return new Animacija(id, "t1", null, VrstaAnimacije.ULAZ, Efekat.FADE, null, okidac, 500, 0);
    }

    private static String platno(List<Animacija> animacije) {
        Platno p = new Platno(1, new Pozadina("#FFFFFF", null), List.of(PlatnoTestPodaci.tekst("t1")), animacije);
        return new PlatnoJson().zapisi(p);
    }

    @Test
    void infoPostepenoBrojiStavke() {
        assertEquals(3, TokIzvodjenja.brojKoraka(slajd(TipSlajda.INFO, "- a\n- b\n- c", true, null)));
    }

    @Test
    void infoBezPostepenoNemaKorake() {
        assertEquals(0, TokIzvodjenja.brojKoraka(slajd(TipSlajda.INFO, "- a\n- b\n- c", false, null)));
    }

    @Test
    void platnoBrojiSamoKlikove() {
        String json = platno(List.of(anim("a", Okidac.SA_PRETHODNIM), anim("b", Okidac.KLIK),
                anim("c", Okidac.POSLE_PRETHODNOG), anim("d", Okidac.KLIK)));
        assertEquals(2, TokIzvodjenja.brojKoraka(slajd(TipSlajda.PLATNO, null, false, json)));
    }

    @Test
    void platnoBezAnimacijaIBezDokumentaNemaKorake() {
        assertEquals(0, TokIzvodjenja.brojKoraka(slajd(TipSlajda.PLATNO, null, false, platno(List.of()))));
        assertEquals(0, TokIzvodjenja.brojKoraka(slajd(TipSlajda.PLATNO, null, false, null)));
    }

    @Test
    void pitanjeINullNemajuKorake() {
        assertEquals(0, TokIzvodjenja.brojKoraka(slajd(TipSlajda.PITANJE, "- a", true, null)));
        assertEquals(0, TokIzvodjenja.brojKoraka(null));
    }

    // ---------------------------------------------------------------- slajdovi sa rundama (PITANJE, ZADATAK)

    private static Slajd saPitanjem() {
        Slajd s = slajd(TipSlajda.PITANJE, null, false, null);
        Pitanje p = new Pitanje();
        p.setTip(TipPitanja.JEDAN_TACAN);
        s.setPitanje(p);
        return s;
    }

    private static Slajd zadatak(Integer brojVarijanti) {
        Slajd s = slajd(TipSlajda.ZADATAK, null, false, null);
        s.setZadatakBrojVarijanti(brojVarijanti);
        return s;
    }

    private static Izvodjenje izmena(Integer zadatakBrojVarijanti) {
        Izvodjenje iz = new Izvodjenje();
        iz.setZadatakBrojVarijanti(zadatakBrojVarijanti);
        return iz;
    }

    @Test
    void saRundamaSamoPitanjeIZadatak() {
        assertFalse(TokIzvodjenja.saRundama(slajd(TipSlajda.INFO, "- a", true, null)));
        assertFalse(TokIzvodjenja.saRundama(slajd(TipSlajda.PLATNO, null, false, platno(List.of()))));
        assertTrue(TokIzvodjenja.saRundama(saPitanjem()));
        assertTrue(TokIzvodjenja.saRundama(zadatak(4)));
        assertTrue(TokIzvodjenja.saRundama(zadatak(null)), "ZADATAK i bez broja i grupe ima runde");
        assertFalse(TokIzvodjenja.saRundama(slajd(TipSlajda.PITANJE, null, false, null)), "PITANJE bez pitanja nema runde");
        assertFalse(TokIzvodjenja.saRundama(null));
    }

    @Test
    void brojVarijantiIzmenaIzKonzoleIliSlajd() {
        assertEquals(4, TokIzvodjenja.brojVarijanti(izmena(null), zadatak(4)), "bez izmene kao na slajdu");
        assertNull(TokIzvodjenja.brojVarijanti(izmena(0), zadatak(4)), "0 = sve aktivne");
        assertEquals(2, TokIzvodjenja.brojVarijanti(izmena(2), zadatak(4)));
        assertNull(TokIzvodjenja.brojVarijanti(izmena(null), zadatak(null)), "slajd bez broja = sve aktivne");
    }

    @Test
    void odgovaraSlajduPoVrstiSnimka() {
        PitanjeSnimak pitanje = new PitanjeSnimak(1L, 2L, TipPitanja.JEDAN_TACAN, "?", null, null, List.of(), null, null,
                null, null, null, null, null, null);
        ZadatakSnimak zadatak = new ZadatakSnimak(2L, 3L, "K", List.of(new GrupaPutanja(3L, "K")), null, List.of());

        assertTrue(TokIzvodjenja.odgovaraSlajdu(pitanje, saPitanjem()));
        assertFalse(TokIzvodjenja.odgovaraSlajdu(zadatak, saPitanjem()));
        assertTrue(TokIzvodjenja.odgovaraSlajdu(zadatak, zadatak(4)));
        assertFalse(TokIzvodjenja.odgovaraSlajdu(pitanje, zadatak(4)));
        assertFalse(TokIzvodjenja.odgovaraSlajdu(pitanje, slajd(TipSlajda.INFO, null, false, null)));
        assertFalse(TokIzvodjenja.odgovaraSlajdu(zadatak, slajd(TipSlajda.PLATNO, null, false, null)));
        assertFalse(TokIzvodjenja.odgovaraSlajdu(null, zadatak(4)));
        assertFalse(TokIzvodjenja.odgovaraSlajdu(zadatak, null));
    }
}
