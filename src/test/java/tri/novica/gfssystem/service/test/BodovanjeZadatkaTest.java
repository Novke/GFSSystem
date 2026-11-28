package tri.novica.gfssystem.service.test;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.PoljeOpcija;
import tri.novica.gfssystem.dto.banka.TipPolja;
import tri.novica.gfssystem.entity.banka.NacinBodovanja;
import tri.novica.gfssystem.service.test.BodovanjeZadatka.Ispravke;
import tri.novica.gfssystem.service.test.BodovanjeZadatka.Izvor;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static tri.novica.gfssystem.entity.banka.NacinBodovanja.PO_POLJIMA;
import static tri.novica.gfssystem.entity.banka.NacinBodovanja.SVE_ILI_NISTA;

class BodovanjeZadatkaTest {

    private static final List<PoljeOpcija> OPCIJE = List.of(
            new PoljeOpcija(1L, "A"), new PoljeOpcija(2L, "B"), new PoljeOpcija(3L, "C"), new PoljeOpcija(4L, "D"));

    private static Polje broj(String k, Double poeni, Boolean konacno) {
        return new Polje(k, k + ")", TipPolja.BROJ, null, null, null, null, null, null, null, poeni, konacno);
    }

    private static Polje tekst(String k) {
        return new Polje(k, k + ")", TipPolja.TEKST, null, null, null, null, null, null);
    }

    private static Polje vise(String k) {
        return new Polje(k, k + ")", TipPolja.VISE_TACNIH, null, null, null, null, null, OPCIJE);
    }

    private static Polje jedan(String k) {
        return new Polje(k, k + ")", TipPolja.JEDAN_TACAN, null, null, null, null, null, OPCIJE);
    }

    private static OdgovorPolja br(double v) {
        return new OdgovorPolja(v, null, null, null);
    }

    /** Novicin primer: A i B dati, C (2) i D (3) koraci, E (5) konačno; tačno C i D, pogrešno E → 5 od 10. */
    @Test
    void novicinPrimer() {
        List<Polje> polja = List.of(broj("c", 2.0, null), broj("d", 3.0, null), broj("e", 5.0, true));
        Map<String, OdgovorPolja> kljuc = Map.of("c", br(12), "d", br(7), "e", br(84));
        var ishod = BodovanjeZadatka.oceni(polja, kljuc, Map.of("c", "12", "d", "7", "e", "80"), PO_POLJIMA, false, 10);
        assertThat(ishod.poeni()).isEqualTo(5.0);
        assertThat(ishod.udeoZadatka()).isEqualTo(0.5);
        assertThat(ishod.polja().get("e").udeo()).isZero();
        assertThat(ishod.polja().get("c").izvor()).isEqualTo(Izvor.AUTO);
    }

    @Test
    void konacnoPriznajeKorakeDajeSveKadJeKrajTacan() {
        List<Polje> polja = List.of(broj("c", 2.0, null), broj("d", 3.0, null), broj("e", 5.0, true));
        Map<String, OdgovorPolja> kljuc = Map.of("c", br(12), "d", br(7), "e", br(84));
        Map<String, Object> uneto = Map.of("c", "", "d", "99", "e", "84");
        assertThat(BodovanjeZadatka.oceni(polja, kljuc, uneto, PO_POLJIMA, true, 10).poeni()).isEqualTo(10.0);
        assertThat(BodovanjeZadatka.oceni(polja, kljuc, uneto, PO_POLJIMA, false, 10).poeni()).isEqualTo(5.0);
        // bez konačnih polja pravilo ne važi
        List<Polje> bezKonacnih = List.of(broj("c", 2.0, null), broj("d", 3.0, null));
        assertThat(BodovanjeZadatka.oceni(bezKonacnih, kljuc, Map.of("c", "12"), PO_POLJIMA, true, 10).poeni()).isEqualTo(4.0);
    }

    @Test
    void sveIliNista() {
        List<Polje> polja = List.of(broj("a", null, null), broj("b", null, null));
        Map<String, OdgovorPolja> kljuc = Map.of("a", br(1), "b", br(2));
        assertThat(BodovanjeZadatka.oceni(polja, kljuc, Map.of("a", "1", "b", "2"), SVE_ILI_NISTA, false, 4).poeni()).isEqualTo(4.0);
        assertThat(BodovanjeZadatka.oceni(polja, kljuc, Map.of("a", "1", "b", "3"), SVE_ILI_NISTA, false, 4).poeni()).isZero();
        assertThat(BodovanjeZadatka.oceni(polja, kljuc, Map.of("a", "1", "b", "3"), PO_POLJIMA, false, 4).poeni()).isEqualTo(2.0);
    }

    @Test
    void viseTacnihDelimicno() {
        Polje p = vise("v");
        OdgovorPolja kljuc = new OdgovorPolja(null, null, null, List.of(1L, 2L, 3L));
        assertThat(BodovanjeZadatka.udeo(p, kljuc, List.of(1, 2, 3))).isEqualTo(1.0);
        assertThat(BodovanjeZadatka.udeo(p, kljuc, List.of(1L, 2L))).isCloseTo(2.0 / 3, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(BodovanjeZadatka.udeo(p, kljuc, List.of(1L, 2L, 4L))).isCloseTo(1.0 / 3, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(BodovanjeZadatka.udeo(p, kljuc, List.of(1L, 4L))).isZero();
        assertThat(BodovanjeZadatka.udeo(p, kljuc, List.of(4L))).isZero();   // nikad ispod nule
        assertThat(BodovanjeZadatka.udeo(p, kljuc, List.of())).isZero();
        assertThat(BodovanjeZadatka.udeo(p, kljuc, List.of("x"))).isZero();
        // 3 poena, dva od tri tačna → 2
        var ishod = BodovanjeZadatka.oceni(List.of(p), Map.of("v", kljuc), Map.of("v", List.of(1L, 2L)), PO_POLJIMA, false, 3);
        assertThat(ishod.poeni()).isEqualTo(2.0);
    }

    @Test
    void zaokruzivanjeNaDveDecimale() {
        List<Polje> polja = List.of(broj("a", null, null), broj("b", null, null), broj("c", null, null));
        Map<String, OdgovorPolja> kljuc = Map.of("a", br(1), "b", br(2), "c", br(3));
        var ishod = BodovanjeZadatka.oceni(polja, kljuc, Map.of("a", "1"), PO_POLJIMA, false, 10);
        assertThat(ishod.poeni()).isEqualTo(3.33);
        assertThat(BodovanjeZadatka.oceni(polja, kljuc, Map.of("a", "1", "b", "2"), PO_POLJIMA, false, 10).poeni()).isEqualTo(6.67);
        assertThat(BodovanjeZadatka.zaokruzi(2.005)).isEqualTo(2.01);
        assertThat(BodovanjeZadatka.poeniPoPolju(polja, 10)).containsEntry("a", 3.33);
    }

    @Test
    void praznoINeocekivanoJeNula() {
        List<Polje> polja = List.of(broj("a", null, null), jedan("j"), tekst("t"));
        Map<String, OdgovorPolja> kljuc = Map.of("a", br(1), "j", new OdgovorPolja(null, null, 2L, null),
                "t", new OdgovorPolja(null, List.of("da"), null, null));
        Map<String, Object> uneto = new HashMap<>();
        uneto.put("a", null);
        uneto.put("j", "2");     // string umesto broja
        uneto.put("t", "   ");
        var ishod = BodovanjeZadatka.oceni(polja, kljuc, uneto, PO_POLJIMA, false, 6);
        assertThat(ishod.poeni()).isZero();
        assertThat(BodovanjeZadatka.oceni(polja, kljuc, null, PO_POLJIMA, false, 6).poeni()).isZero();
        assertThat(BodovanjeZadatka.oceni(polja, kljuc, Map.of("j", 2, "t", "DA"), PO_POLJIMA, false, 6).poeni()).isEqualTo(4.0);
    }

    @Test
    void zadatakBezPoljaNemaAutomatskePoene() {
        var ishod = BodovanjeZadatka.oceni(List.of(), Map.of(), Map.of(), PO_POLJIMA, false, 10);
        assertThat(ishod.poeni()).isNull();
    }

    @Test
    void ispravkePonistiKljucPrihvati() {
        List<Polje> polja = List.of(broj("a", null, null), tekst("t"));
        Map<String, OdgovorPolja> kljuc = Map.of("a", br(5), "t", new OdgovorPolja(null, List.of("tacno"), null, null));
        Map<String, Object> uneto = Map.of("a", "0101", "t", "skoro");

        assertThat(BodovanjeZadatka.oceni(polja, kljuc, uneto, PO_POLJIMA, false, 2).poeni()).isZero();

        var ponisti = new Ispravke(Set.of("t"), Map.of(), Map.of());
        var ishod = BodovanjeZadatka.oceni(polja, kljuc, uneto, PO_POLJIMA, false, 2, ponisti);
        assertThat(ishod.poeni()).isEqualTo(1.0);
        assertThat(ishod.polja().get("t").izvor()).isEqualTo(Izvor.ISPRAVKA);

        // "0101" je kao broj 101: ključ odgovora je "101"
        assertThat(BodovanjeZadatka.kljucOdgovora(polja.get(0), "0101")).isEqualTo("101");
        var noviKljuc = new Ispravke(Set.of(), Map.of("a", br(101)), Map.of());
        assertThat(BodovanjeZadatka.oceni(polja, kljuc, Map.of("a", "101,0"), PO_POLJIMA, false, 2, noviKljuc).poeni()).isEqualTo(1.0);

        var prihvati = new Ispravke(Set.of(), Map.of(), Map.of("t", Set.of(BodovanjeZadatka.kljucOdgovora(polja.get(1), "Skoro"))));
        var ishodP = BodovanjeZadatka.oceni(polja, kljuc, uneto, PO_POLJIMA, false, 2, prihvati);
        assertThat(ishodP.poeni()).isEqualTo(1.0);
        assertThat(ishodP.polja().get("t").izvor()).isEqualTo(Izvor.ISPRAVKA);
    }

    @Test
    void kljucOdgovoraPoTipu() {
        assertThat(BodovanjeZadatka.kljucOdgovora(broj("a", null, null), "0,50")).isEqualTo("0.5");
        assertThat(BodovanjeZadatka.kljucOdgovora(broj("a", null, null), "abc")).isEqualTo("#abc");
        assertThat(BodovanjeZadatka.kljucOdgovora(broj("a", null, null), "  ")).isNull();
        assertThat(BodovanjeZadatka.kljucOdgovora(jedan("j"), 3)).isEqualTo("3");
        assertThat(BodovanjeZadatka.kljucOdgovora(vise("v"), List.of(3, 1, 3))).isEqualTo("1,3");
        assertThat(BodovanjeZadatka.kljucOdgovora(vise("v"), List.of())).isNull();
        assertThat(BodovanjeZadatka.kljucOdgovora(tekst("t"), " Ab C ")).isEqualTo("abc");
    }

    @Test
    void nacinBodovanjaNullJePoPoljima() {
        List<Polje> polja = List.of(broj("a", null, null), broj("b", null, null));
        Map<String, OdgovorPolja> kljuc = Map.of("a", br(1), "b", br(2));
        assertThat(BodovanjeZadatka.oceni(polja, kljuc, Map.of("a", "1"), (NacinBodovanja) null, false, 4).poeni()).isEqualTo(2.0);
    }
}
