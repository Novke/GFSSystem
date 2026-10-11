package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.dto.uzivo.platno.*;
import tri.novica.gfssystem.entity.uzivo.Slajd;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
