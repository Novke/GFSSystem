package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.dto.uzivo.*;
import tri.novica.gfssystem.entity.uzivo.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RezultatBuilderTest {

    static Odgovor odg() {
        Odgovor o = new Odgovor();
        o.setKreirano(LocalDateTime.of(2026, 10, 7, 10, 0));
        return o;
    }

    static Odgovor opc(String opcije) { Odgovor o = odg(); o.setOpcije(opcije); return o; }

    static Odgovor broj(double b) { Odgovor o = odg(); o.setBroj(b); return o; }

    static Odgovor tekst(String t) { Odgovor o = odg(); o.setTekst(t); return o; }

    static Odgovor tekst(String t, boolean sakriven) { Odgovor o = tekst(t); o.setSakriven(sakriven); return o; }

    static Odgovor skala(int s) { Odgovor o = odg(); o.setSkala(s); return o; }

    static PitanjeSnimak snimak(TipPitanja tip, List<OpcijaSnimak> opcije, Double tacno, Double odst, OdstupanjeTip ot,
                                List<String> prihvatljivi) {
        return new PitanjeSnimak(1L, 2L, tip, "?", null, null, opcije, tacno, odst, ot, null, null, prihvatljivi, null, null);
    }

    static List<OpcijaSnimak> tri() {
        // namerno nesortirano po rb
        return List.of(new OpcijaSnimak(12L, 3, "C", false), new OpcijaSnimak(10L, 1, "A", true),
                new OpcijaSnimak(11L, 2, "B", false));
    }

    // --- opcije ---

    @Test void opcijeBrojeGlasoveURedosleduPoRb() {
        PitanjeSnimak p = snimak(TipPitanja.JEDAN_TACAN, tri(), null, null, null, null);
        Rezultat r = RezultatBuilder.izgradi(p, List.of(opc("10"), opc("10"), opc("12")), false, true);
        assertEquals(TipPitanja.JEDAN_TACAN, r.tip());
        assertEquals(3, r.ukupno());
        assertEquals(List.of(10L, 11L, 12L), r.opcije().stream().map(RezultatOpcija::id).toList());
        assertEquals(List.of(2, 0, 1), r.opcije().stream().map(RezultatOpcija::broj).toList());
        assertEquals(List.of("A", "B", "C"), r.opcije().stream().map(RezultatOpcija::tekst).toList());
        assertEquals(Boolean.TRUE, r.opcije().get(0).tacna());
        assertEquals(Boolean.FALSE, r.opcije().get(1).tacna());
        assertNull(r.brojevi());
        assertNull(r.tekstovi());
        assertNull(r.skala());
    }

    @Test void opcijeTacnihJedanIzbor() {
        PitanjeSnimak p = snimak(TipPitanja.JEDAN_TACAN, tri(), null, null, null, null);
        List<Odgovor> l = List.of(opc("10"), opc("10"), opc("12"), opc("x"));
        assertEquals(2, RezultatBuilder.izgradi(p, l, true, true).tacnih());
        assertNull(RezultatBuilder.izgradi(p, l, true, false).tacnih());
    }

    @Test void viseTacnihTacnoSamoCeoSkup() {
        List<OpcijaSnimak> o = List.of(new OpcijaSnimak(10L, 1, "A", true), new OpcijaSnimak(11L, 2, "B", false),
                new OpcijaSnimak(12L, 3, "C", true));
        PitanjeSnimak p = snimak(TipPitanja.VISE_TACNIH, o, null, null, null, null);
        Rezultat r = RezultatBuilder.izgradi(p, List.of(opc("10,12"), opc("12, 10"), opc("10"), opc("10,11,12"), opc("")),
                false, true);
        assertEquals(2, r.tacnih());
        assertEquals(5, r.ukupno());
    }

    @Test void anketaISkalaNemajuTacnih() {
        PitanjeSnimak a = snimak(TipPitanja.ANKETA, List.of(new OpcijaSnimak(10L, 1, "A", false)), null, null, null, null);
        assertNull(RezultatBuilder.izgradi(a, List.of(opc("10")), false, true).tacnih());
        PitanjeSnimak s = snimak(TipPitanja.SKALA, null, null, null, null, null);
        assertNull(RezultatBuilder.izgradi(s, List.of(skala(3)), false, true).tacnih());
    }

    @Test void tacnaNullKadNijePrikazana() {
        PitanjeSnimak p = snimak(TipPitanja.JEDAN_TACAN, tri(), null, null, null, null);
        Rezultat r = RezultatBuilder.izgradi(p, List.of(opc("10")), true, false);
        assertTrue(r.opcije().stream().allMatch(o -> o.tacna() == null));
    }

    @Test void viseTacnihBrojiSvakuIzabranuOpciju() {
        PitanjeSnimak p = snimak(TipPitanja.VISE_TACNIH, tri(), null, null, null, null);
        Rezultat r = RezultatBuilder.izgradi(p, List.of(opc("10,11"), opc("11"), opc("10, 12")), false, false);
        assertEquals(3, r.ukupno());
        assertEquals(List.of(2, 2, 1), r.opcije().stream().map(RezultatOpcija::broj).toList());
    }

    @Test void anketaNikadNemaTacnu() {
        PitanjeSnimak p = snimak(TipPitanja.ANKETA, List.of(new OpcijaSnimak(10L, 1, "A", false),
                new OpcijaSnimak(11L, 2, "B", false)), null, null, null, null);
        Rezultat r = RezultatBuilder.izgradi(p, List.of(opc("10")), false, true);
        assertTrue(r.opcije().stream().allMatch(o -> o.tacna() == null));
    }

    @Test void opcijeIgnorisuNeispravanZapis() {
        PitanjeSnimak p = snimak(TipPitanja.JEDAN_TACAN, tri(), null, null, null, null);
        Rezultat r = RezultatBuilder.izgradi(p, List.of(opc("10"), opc(null), opc("x,,"), opc("")), false, false);
        assertEquals(List.of(1, 0, 0), r.opcije().stream().map(RezultatOpcija::broj).toList());
        assertEquals(4, r.ukupno());
    }

    @Test void prazanSkupOdgovora() {
        PitanjeSnimak p = snimak(TipPitanja.JEDAN_TACAN, tri(), null, null, null, null);
        Rezultat r = RezultatBuilder.izgradi(p, List.of(), false, false);
        assertEquals(0, r.ukupno());
        assertEquals(List.of(0, 0, 0), r.opcije().stream().map(RezultatOpcija::broj).toList());
    }

    // --- broj ---

    @Test void brojSveVrednostiPoBrojuPaPoVrednostiBezMedijane() {
        PitanjeSnimak p = snimak(TipPitanja.BROJ, null, null, null, null, null);
        Rezultat r = RezultatBuilder.izgradi(p, List.of(broj(7), broj(3), broj(3), broj(5), broj(5), broj(9)), false, true);
        assertEquals(List.of(new BrojStavka(3, 2, null), new BrojStavka(5, 2, null), new BrojStavka(7, 1, null),
                new BrojStavka(9, 1, null)), r.brojevi().vrednosti());
        assertEquals(6, r.ukupno());
        assertNull(r.opcije());
        assertNull(r.tacnih(), "procena (bez tačne vrednosti): nema broja tačnih ni oznaka");
    }

    @Test void brojBezOdgovora() {
        PitanjeSnimak p = snimak(TipPitanja.BROJ, null, 10.0, 0.0, OdstupanjeTip.APSOLUTNO, null);
        Rezultat r = RezultatBuilder.izgradi(p, List.of(), false, true);
        assertTrue(r.brojevi().vrednosti().isEmpty());
        assertEquals(0, r.tacnih());
    }

    @Test void brojBezOgranicenjaStavki() {
        // ranije najviše 10 najčešćih: sada su sve vrednosti čipovi (front ograničava prikaz)
        PitanjeSnimak p = snimak(TipPitanja.BROJ, null, null, null, null, null);
        List<Odgovor> l = new ArrayList<>();
        for (int i = 1; i <= 40; i++) l.add(broj(i));
        Rezultat r = RezultatBuilder.izgradi(p, l, false, false);
        assertEquals(40, r.brojevi().vrednosti().size());
        assertEquals(1.0, r.brojevi().vrednosti().get(0).vrednost());
        assertEquals(40.0, r.brojevi().vrednosti().get(39).vrednost());
    }

    @Test void brojNeZaokruzujeIMinusNulaJeNula() {
        // 3,14 i 3,141 su dva različita odgovora (ranije ih je prikaz na dve decimale spajao u "3,14")
        PitanjeSnimak p = snimak(TipPitanja.BROJ, null, null, null, null, null);
        Rezultat r = RezultatBuilder.izgradi(p, List.of(broj(3.14), broj(3.141), broj(-0.0), broj(0)), false, false);
        assertEquals(List.of(new BrojStavka(0, 2, null), new BrojStavka(3.14, 1, null), new BrojStavka(3.141, 1, null)),
                r.brojevi().vrednosti());
    }

    @Test void brojTacanPoVrednostiIBrojTacnihPoOdstupanju() {
        PitanjeSnimak p = snimak(TipPitanja.BROJ, null, 10.0, 1.0, OdstupanjeTip.APSOLUTNO, null);
        Rezultat r = RezultatBuilder.izgradi(p, List.of(broj(10), broj(10.9), broj(11.1), broj(8), broj(10)), false, true);
        assertEquals(List.of(new BrojStavka(10, 2, true), new BrojStavka(8, 1, false), new BrojStavka(10.9, 1, true),
                new BrojStavka(11.1, 1, false)), r.brojevi().vrednosti());
        assertEquals(3, r.tacnih());

        PitanjeSnimak pr = snimak(TipPitanja.BROJ, null, 100.0, 10.0, OdstupanjeTip.PROCENAT, null);
        Rezultat rp = RezultatBuilder.izgradi(pr, List.of(broj(90), broj(110), broj(111)), false, true);
        assertEquals(2, rp.tacnih());
        assertEquals(List.of(true, true, false), rp.brojevi().vrednosti().stream().map(BrojStavka::tacan).toList());
    }

    @Test void brojTacnostSkrivenaDokNijePrikazanaIRedosledIsti() {
        // pre TACAN ni oznake ni broj tačnih, a redosled ne zavisi od tačnosti (ne odaje tačnu vrednost)
        PitanjeSnimak p = snimak(TipPitanja.BROJ, null, 10.0, 0.0, OdstupanjeTip.APSOLUTNO, null);
        List<Odgovor> l = List.of(broj(12), broj(12), broj(10), broj(11));
        Rezultat pre = RezultatBuilder.izgradi(p, l, true, false);
        assertNull(pre.tacnih());
        assertTrue(pre.brojevi().vrednosti().stream().allMatch(v -> v.tacan() == null));
        Rezultat posle = RezultatBuilder.izgradi(p, l, true, true);
        assertEquals(pre.brojevi().vrednosti().stream().map(BrojStavka::vrednost).toList(),
                posle.brojevi().vrednosti().stream().map(BrojStavka::vrednost).toList());
        assertEquals(1, posle.tacnih());
    }

    // --- kratak tekst ---

    @Test void tekstGrupisanjePoNormalizaciji() {
        PitanjeSnimak p = snimak(TipPitanja.KRATAK_TEKST, null, null, null, null, null);
        Rezultat r = RezultatBuilder.izgradi(p, List.of(tekst("Čvrstoća"), tekst(" cvrstoca "), tekst("cvrstoca"),
                tekst("Beton"), tekst("Čelik"), tekst("Чврстоћа")), false, false);
        assertEquals(6, r.ukupno());
        assertEquals(3, r.tekstovi().size());
        RezultatTekst prvi = r.tekstovi().get(0);
        assertEquals("cvrstoca", prvi.kljuc());
        assertEquals(4, prvi.broj());
        assertEquals("cvrstoca", prvi.tekst(), "najcesci originalni oblik (trim): cvrstoca x2");
        // beton i celik po 1 -> abecedno po kljucu
        assertEquals("beton", r.tekstovi().get(1).kljuc());
        assertEquals("celik", r.tekstovi().get(2).kljuc());
        assertEquals("Beton", r.tekstovi().get(1).tekst());
        assertNull(prvi.tacan());
        assertFalse(prvi.sakriven());
        assertNull(r.opcije());
    }

    @Test void tekstSortPoBrojuPaAbecedno() {
        PitanjeSnimak p = snimak(TipPitanja.KRATAK_TEKST, null, null, null, null, null);
        Rezultat r = RezultatBuilder.izgradi(p, List.of(tekst("b"), tekst("a"), tekst("c"), tekst("c")), false, false);
        assertEquals(List.of("c", "a", "b"), r.tekstovi().stream().map(RezultatTekst::kljuc).toList());
    }

    @Test void tekstSakrivenJavnoIzbacenNastavnickiOznacen() {
        PitanjeSnimak p = snimak(TipPitanja.KRATAK_TEKST, null, null, null, null, null);
        List<Odgovor> l = List.of(tekst("ok"), tekst("ruzno", true), tekst("ok"), tekst("drugo"));

        Rezultat javni = RezultatBuilder.izgradi(p, l, true, false);
        assertEquals(3, javni.ukupno());
        assertEquals(List.of("ok", "drugo"), javni.tekstovi().stream().map(RezultatTekst::kljuc).toList());

        Rezultat nastavnik = RezultatBuilder.izgradi(p, l, false, false);
        assertEquals(4, nastavnik.ukupno());
        assertEquals(3, nastavnik.tekstovi().size());
        RezultatTekst ruzno = nastavnik.tekstovi().stream().filter(t -> t.kljuc().equals("ruzno")).findFirst().orElseThrow();
        assertTrue(ruzno.sakriven());
        assertFalse(nastavnik.tekstovi().stream().filter(t -> t.kljuc().equals("ok")).findFirst().orElseThrow().sakriven());
    }

    @Test void sakrivenaGrupaNeCureUJavnoKadStigneNoviOdgovorUIstuGrupu() {
        PitanjeSnimak p = snimak(TipPitanja.KRATAK_TEKST, null, null, null, null, null);
        List<Odgovor> l = List.of(tekst("Ruzno", true), tekst("ruzno"), tekst("ok"));
        Rezultat javni = RezultatBuilder.izgradi(p, l, true, false);
        assertEquals(List.of("ok"), javni.tekstovi().stream().map(RezultatTekst::kljuc).toList());
        assertEquals(1, javni.ukupno());
        Rezultat nastavnik = RezultatBuilder.izgradi(p, l, false, false);
        assertTrue(nastavnik.tekstovi().stream().filter(t -> t.kljuc().equals("ruzno")).findFirst().orElseThrow().sakriven());
    }

    @Test void tekstTacanPoPrihvatljivimSamoKadJePrikazan() {
        PitanjeSnimak p = snimak(TipPitanja.KRATAK_TEKST, null, null, null, null, List.of("Čvrstoća"));
        List<Odgovor> l = List.of(tekst("cvrstoca"), tekst("beton"));
        Rezultat skriven = RezultatBuilder.izgradi(p, l, true, false);
        assertTrue(skriven.tekstovi().stream().allMatch(t -> t.tacan() == null));
        Rezultat prikazan = RezultatBuilder.izgradi(p, l, true, true);
        assertEquals(Boolean.TRUE, prikazan.tekstovi().stream().filter(t -> t.kljuc().equals("cvrstoca")).findFirst().orElseThrow().tacan());
        assertEquals(Boolean.FALSE, prikazan.tekstovi().stream().filter(t -> t.kljuc().equals("beton")).findFirst().orElseThrow().tacan());
        assertNull(skriven.tacnih());
        assertEquals(1, prikazan.tacnih());
    }

    @Test void tekstTacnihBezSakrivenihUJavnom() {
        PitanjeSnimak p = snimak(TipPitanja.KRATAK_TEKST, null, null, null, null, List.of("Njutn"));
        List<Odgovor> l = List.of(tekst("njutn"), tekst("Njutn ", true), tekst("beton"));
        Rezultat javni = RezultatBuilder.izgradi(p, l, true, true);
        assertEquals(1, javni.ukupno());
        assertEquals(0, javni.tacnih(), "cela grupa sa sakrivenim odgovorom je van javnog rezultata, i u broju tačnih");
        Rezultat nastavnik = RezultatBuilder.izgradi(p, l, false, true);
        assertEquals(3, nastavnik.ukupno());
        assertEquals(2, nastavnik.tacnih());
    }

    @Test void tekstBezPrihvatljivihNemaTacan() {
        PitanjeSnimak p = snimak(TipPitanja.KRATAK_TEKST, null, null, null, null, List.of());
        Rezultat r = RezultatBuilder.izgradi(p, List.of(tekst("x")), false, true);
        assertNull(r.tekstovi().get(0).tacan());
        assertNull(r.tacnih());
    }

    // --- skala ---

    @Test void skalaRaspodelaIProsek() {
        PitanjeSnimak p = snimak(TipPitanja.SKALA, null, null, null, null, null);
        Rezultat r = RezultatBuilder.izgradi(p, List.of(skala(1), skala(2), skala(2), skala(5)), false, false);
        assertEquals(List.of(1, 2, 0, 0, 1), r.skala().raspodela());
        assertEquals(2.5, r.skala().prosek());
        assertEquals(4, r.ukupno());
    }

    @Test void skalaProsekNaDveDecimale() {
        PitanjeSnimak p = snimak(TipPitanja.SKALA, null, null, null, null, null);
        Rezultat r = RezultatBuilder.izgradi(p, List.of(skala(1), skala(1), skala(2)), false, false);
        assertEquals(1.33, r.skala().prosek());
    }

    @Test void skalaBezOdgovora() {
        PitanjeSnimak p = snimak(TipPitanja.SKALA, null, null, null, null, null);
        Rezultat r = RezultatBuilder.izgradi(p, List.of(), false, false);
        assertEquals(List.of(0, 0, 0, 0, 0), r.skala().raspodela());
        assertNull(r.skala().prosek());
    }

    // --- sakriveni u javnom ukupno ---

    @Test void ukupnoJavnoBezSakrivenih() {
        PitanjeSnimak p = snimak(TipPitanja.SKALA, null, null, null, null, null);
        Odgovor skriven = skala(5);
        skriven.setSakriven(true);
        List<Odgovor> l = List.of(skala(1), skriven);
        assertEquals(1, RezultatBuilder.izgradi(p, l, true, false).ukupno());
        assertEquals(2, RezultatBuilder.izgradi(p, l, false, false).ukupno());
    }
}
