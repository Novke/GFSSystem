package tri.novica.gfssystem.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.PoljeOpcija;
import tri.novica.gfssystem.dto.banka.TipPolja;
import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BankaPPTest {

    static void greska(String poruka, Executable e) {
        SystemException ex = assertThrows(SystemException.class, e);
        assertEquals(400, ex.getCode());
        assertEquals(poruka, ex.getMessage());
    }

    /** Polja kroz normalizuj + proveri, kako ih zove servis. */
    static void proveriPolja(Polje... polja) {
        BankaPP.proveriPolja(BankaPP.normalizujPolja(Arrays.asList(polja)));
    }

    static void greskaPolja(String poruka, Polje... polja) {
        greska(poruka, () -> proveriPolja(polja));
    }

    static Polje broj(String kljuc) {
        return new Polje(kljuc, "Oznaka " + kljuc, TipPolja.BROJ, null, null, null, null, null, null);
    }

    static Polje tekst(String kljuc) {
        return new Polje(kljuc, "Oznaka " + kljuc, TipPolja.TEKST, null, null, null, null, null, null);
    }

    static Polje jedan(String kljuc, PoljeOpcija... opcije) {
        return new Polje(kljuc, "Oznaka " + kljuc, TipPolja.JEDAN_TACAN, null, null, null, null, null,
                Arrays.asList(opcije));
    }

    static Polje vise(String kljuc, PoljeOpcija... opcije) {
        return new Polje(kljuc, "Oznaka " + kljuc, TipPolja.VISE_TACNIH, null, null, null, null, null,
                Arrays.asList(opcije));
    }

    static PoljeOpcija op(Long id, String tekst) {
        return new PoljeOpcija(id, tekst);
    }

    static PoljeOpcija op(String tekst) {
        return new PoljeOpcija(null, tekst);
    }

    // --- normalizujPolja ---

    @Test
    void normalizujDajePodrazumevaneVrednosti() {
        List<Polje> n = BankaPP.normalizujPolja(List.of(
                new Polje(" a ", "  Oznaka  ", TipPolja.BROJ, null, null, "  ", null, null, null)));
        assertEquals(List.of(new Polje("a", "Oznaka", TipPolja.BROJ, 0.0, OdstupanjeTip.APSOLUTNO, null, true, true, null)), n);
    }

    @Test
    void normalizujCuvaPostavljeneVrednosti() {
        Polje p = new Polje("a", "x", TipPolja.BROJ, 2.5, OdstupanjeTip.PROCENAT, " cm ", false, false, null);
        assertEquals(List.of(new Polje("a", "x", TipPolja.BROJ, 2.5, OdstupanjeTip.PROCENAT, "cm", false, false, null)),
                BankaPP.normalizujPolja(List.of(p)));
    }

    @Test
    void normalizujBrisePrazneOpcijeKodNeopcijskihTipova() {
        Polje p = new Polje("a", "x", TipPolja.TEKST, null, null, null, null, null, List.of(op(1L, "A"), op(2L, "B")));
        assertNull(BankaPP.normalizujPolja(List.of(p)).get(0).opcije());
        Polje b = new Polje("a", "x", TipPolja.BROJ, null, null, null, null, null, List.of());
        assertNull(BankaPP.normalizujPolja(List.of(b)).get(0).opcije());
    }

    @Test
    void normalizujDodeljujeIdOpcijaPoRedu() {
        Polje n = BankaPP.normalizujPolja(List.of(jedan("a", op(" A "), op("B"), op("C")))).get(0);
        assertEquals(List.of(op(1L, "A"), op(2L, "B"), op(3L, "C")), n.opcije());
    }

    @Test
    void normalizujCuvaDateIdOpcija() {
        Polje n = BankaPP.normalizujPolja(List.of(vise("a", op(5L, "A"), op(7L, "B")))).get(0);
        assertEquals(List.of(op(5L, "A"), op(7L, "B")), n.opcije());
    }

    @Test
    void normalizujNovojOpcijiDajeIdKojiSeNeSudara() {
        // opcija 1 je obrisana, nova ide na kraj: ne sme da dobije id postojeće opcije
        Polje n = BankaPP.normalizujPolja(List.of(jedan("a", op(2L, "B"), op(3L, "C"), op("D")))).get(0);
        assertEquals(List.of(op(2L, "B"), op(3L, "C"), op(4L, "D")), n.opcije());
        BankaPP.proveriPolja(List.of(n));
    }

    @Test
    void normalizujPremaSacuvanojSemiDajeIdIznadNajvecegSacuvanogZaIstiKljuc() {
        // sačuvano a: 1, 2, 3 (3 je u zahtevu uklonjena); b je novo polje; c je sačuvano bez opcija (BROJ)
        List<Polje> sacuvana = BankaPP.normalizujPolja(List.of(jedan("a", op("A"), op("B"), op("C")),
                new Polje("c", "x", TipPolja.BROJ, null, null, null, null, null, null)));
        List<Polje> n = BankaPP.normalizujPolja(List.of(jedan("a", op(1L, "A"), op(2L, "B"), op("D")),
                jedan("b", op("P"), op("Q")), jedan("c", op("R"), op("S"))), sacuvana);

        assertEquals(List.of(op(1L, "A"), op(2L, "B"), op(4L, "D")), n.get(0).opcije());
        assertEquals(List.of(op(1L, "P"), op(2L, "Q")), n.get(1).opcije());
        assertEquals(List.of(op(1L, "R"), op(2L, "S")), n.get(2).opcije());
        assertEquals(BankaPP.normalizujPolja(List.of(jedan("a", op("A")))), BankaPP.normalizujPolja(List.of(jedan("a", op("A"))), null));
    }

    @Test
    void normalizujNeDiraUlaz() {
        Polje p = jedan("a", op("A"), op("B"));
        BankaPP.normalizujPolja(List.of(p));
        assertNull(p.opcije().get(0).id());
    }

    @Test
    void normalizujNullListaJePrazna() {
        assertEquals(List.of(), BankaPP.normalizujPolja(null));
    }

    // --- proveriPolja ---

    @Test
    void prazanSpisakPoljaJeValidan() {
        assertDoesNotThrow(() -> BankaPP.proveriPolja(List.of()));
        assertDoesNotThrow(() -> BankaPP.proveriPolja(null));
    }

    @Test
    void ispravnaPoljaProlaze() {
        assertDoesNotThrow(() -> proveriPolja(broj("a"), tekst("b"), jedan("c", op("A"), op("B $q_0$")),
                vise("d", op("A"), op("B"), op("C"), op("D"), op("E"), op("F"))));
    }

    @Test
    void kljucMoraBitiMalaSlovaICifre() {
        String poruka = "Ključ polja mora biti 1-10 malih slova i cifara i počinjati slovom.";
        greskaPolja(poruka, broj(""));
        greskaPolja(poruka, broj("   "));
        greskaPolja(poruka, broj("A"));
        greskaPolja(poruka, broj("1a"));
        greskaPolja(poruka, broj("a b"));
        greskaPolja(poruka, broj("a_b"));
        greskaPolja(poruka, broj("šta"));
        greskaPolja(poruka, broj("abcdefghijk"));
        greskaPolja(poruka, broj(null));
        greska(poruka, () -> BankaPP.proveriPolja(Collections.singletonList(null)));
        assertDoesNotThrow(() -> proveriPolja(broj("a"), broj("abcdefghij"), broj("a1"), broj("z9")));
    }

    @Test
    void kljucSeNeSmePonavljati() {
        greskaPolja("Ključ polja 'a' se ponavlja.", broj("a"), tekst("b"), tekst("a"));
        // razmaci na krajevima se skidaju pre poređenja
        greskaPolja("Ključ polja 'a' se ponavlja.", broj("a"), broj(" a "));
    }

    @Test
    void najvise10Polja() {
        List<Polje> deset = new ArrayList<>();
        for (int i = 0; i < 10; i++) deset.add(broj("p" + i));
        assertDoesNotThrow(() -> BankaPP.proveriPolja(BankaPP.normalizujPolja(deset)));
        deset.add(broj("p10"));
        greska("Najviše 10 polja.", () -> BankaPP.proveriPolja(BankaPP.normalizujPolja(deset)));
    }

    @Test
    void oznakaImaDo200Znakova() {
        String poruka = "Oznaka polja mora imati 1 do 200 znakova.";
        greskaPolja(poruka, new Polje("a", null, TipPolja.BROJ, null, null, null, null, null, null));
        greskaPolja(poruka, new Polje("a", "   ", TipPolja.BROJ, null, null, null, null, null, null));
        greskaPolja(poruka, new Polje("a", "x".repeat(201), TipPolja.BROJ, null, null, null, null, null, null));
        assertDoesNotThrow(() -> proveriPolja(new Polje("a", "x".repeat(200), TipPolja.BROJ, null, null, null, null, null, null)));
        // znakovi kao u MySQL-u (code point)
        assertDoesNotThrow(() -> proveriPolja(new Polje("a", "😀".repeat(200), TipPolja.BROJ, null, null, null, null, null, null)));
    }

    @Test
    void tipJeObavezan() {
        greskaPolja("Tip polja 'a' je obavezan.", new Polje("a", "x", null, null, null, null, null, null, null));
    }

    @Test
    void opcijskaPoljaImaju2Do6Opcija() {
        String poruka = "Polje 'c' mora imati 2 do 6 opcija.";
        greskaPolja(poruka, jedan("c"));
        greskaPolja(poruka, jedan("c", op("A")));
        greskaPolja(poruka, vise("c", op("A")));
        greskaPolja(poruka, jedan("c", op("1"), op("2"), op("3"), op("4"), op("5"), op("6"), op("7")));
        greskaPolja(poruka, new Polje("c", "x", TipPolja.JEDAN_TACAN, null, null, null, null, null, null));
        assertDoesNotThrow(() -> proveriPolja(jedan("c", op("A"), op("B"))));
    }

    @Test
    void tekstOpcijeImaDo200Znakova() {
        String poruka = "Tekst opcije polja 'c' mora imati 1 do 200 znakova.";
        greskaPolja(poruka, jedan("c", op("A"), op("  ")));
        greskaPolja(poruka, jedan("c", op("A"), op((String) null)));
        greskaPolja(poruka, jedan("c", op("A"), op("x".repeat(201))));
        assertDoesNotThrow(() -> proveriPolja(jedan("c", op("A"), op("x".repeat(200)))));
    }

    @Test
    void idOpcijeSeNeSmePonavljati() {
        greskaPolja("Polje 'c' ima dupliran id opcije.", jedan("c", op(1L, "A"), op(1L, "B")));
        greskaPolja("Polje 'c' ima dupliran id opcije.", vise("c", op(1L, "A"), op(2L, "B"), op(2L, "C")));
    }

    @Test
    void idOpcijeMoraBitiPozitivan() {
        greskaPolja("Polje 'c' ima neispravan id opcije.", jedan("c", op(0L, "A"), op(2L, "B")));
        greskaPolja("Polje 'c' ima neispravan id opcije.", jedan("c", op(-1L, "A"), op(2L, "B")));
    }

    @Test
    void nulaIdOpcijeBezNormalizacijeNijeValidan() {
        Polje p = jedan("c", op("A"), op("B"));
        greska("Polje 'c' ima neispravan id opcije.", () -> BankaPP.proveriPolja(List.of(p)));
    }

    @Test
    void odstupanjeNeSmeBitiNegativnoNiBeskonacno() {
        String poruka = "Odstupanje polja 'a' mora biti nenegativan broj.";
        greskaPolja(poruka, new Polje("a", "x", TipPolja.BROJ, -0.1, null, null, null, null, null));
        greskaPolja(poruka, new Polje("a", "x", TipPolja.BROJ, Double.NaN, null, null, null, null, null));
        greskaPolja(poruka, new Polje("a", "x", TipPolja.BROJ, Double.POSITIVE_INFINITY, null, null, null, null, null));
        assertDoesNotThrow(() -> proveriPolja(new Polje("a", "x", TipPolja.BROJ, 100.0, OdstupanjeTip.PROCENAT, null, null, null, null)));
    }

    @Test
    void jedinicaImaDo30Znakova() {
        greskaPolja("Jedinica polja 'a' može imati najviše 30 znakova.",
                new Polje("a", "x", TipPolja.BROJ, null, null, "m".repeat(31), null, null, null));
        assertDoesNotThrow(() -> proveriPolja(new Polje("a", "x", TipPolja.BROJ, null, null, "m".repeat(30), null, null, null)));
    }

    // --- proveriOdgovore ---

    private static List<Polje> sema() {
        return BankaPP.normalizujPolja(List.of(
                broj("a"), tekst("b"),
                jedan("c", op("A"), op("B"), op("C")),
                vise("d", op("A"), op("B"), op("C"))));
    }

    private static Map<String, OdgovorPolja> ispravniOdgovori() {
        Map<String, OdgovorPolja> m = new LinkedHashMap<>();
        m.put("a", new OdgovorPolja(45.0, null, null, null));
        m.put("b", new OdgovorPolja(null, List.of("10110110", "1011 0110"), null, null));
        m.put("c", new OdgovorPolja(null, null, 2L, null));
        m.put("d", new OdgovorPolja(null, null, null, List.of(1L, 3L)));
        return m;
    }

    private static void greskaOdgovora(String poruka, String kljuc, OdgovorPolja vrednost) {
        Map<String, OdgovorPolja> m = ispravniOdgovori();
        if (vrednost == null && kljuc != null) {
            m.remove(kljuc);
        } else {
            m.put(kljuc, vrednost);
        }
        greska(poruka, () -> BankaPP.proveriOdgovore(sema(), m));
    }

    @Test
    void ispravniOdgovoriProlaze() {
        assertDoesNotThrow(() -> BankaPP.proveriOdgovore(sema(), ispravniOdgovori()));
    }

    @Test
    void praznaSemaPrihvataPrazneOdgovore() {
        assertDoesNotThrow(() -> BankaPP.proveriOdgovore(List.of(), Map.of()));
        assertDoesNotThrow(() -> BankaPP.proveriOdgovore(List.of(), null));
        assertDoesNotThrow(() -> BankaPP.proveriOdgovore(null, null));
    }

    @Test
    void nedostajeOdgovorZaPolje() {
        greskaOdgovora("Odgovor za polje 'c' nedostaje.", "c", null);
        Map<String, OdgovorPolja> sNull = ispravniOdgovori();
        sNull.put("a", null);
        greska("Odgovor za polje 'a' nedostaje.", () -> BankaPP.proveriOdgovore(sema(), sNull));
        greska("Odgovor za polje 'a' nedostaje.", () -> BankaPP.proveriOdgovore(sema(), null));
        greska("Odgovor za polje 'a' nedostaje.", () -> BankaPP.proveriOdgovore(sema(), Map.of()));
    }

    @Test
    void brojMoraBitiKonacanBroj() {
        String poruka = "Odgovor za polje 'a' mora biti broj.";
        greskaOdgovora(poruka, "a", new OdgovorPolja(null, null, null, null));
        greskaOdgovora(poruka, "a", new OdgovorPolja(Double.NaN, null, null, null));
        greskaOdgovora(poruka, "a", new OdgovorPolja(Double.POSITIVE_INFINITY, null, null, null));
        // vrednost pogrešnog oblika uz broj
        greskaOdgovora(poruka, "a", new OdgovorPolja(45.0, List.of("45"), null, null));
        greskaOdgovora(poruka, "a", new OdgovorPolja(null, List.of("45"), null, null));
    }

    @Test
    void tekstTraziPrihvatljiveOdgovore() {
        String poruka = "Polje 'b' traži 1 do 10 prihvatljivih odgovora (1-200 znakova).";
        greskaOdgovora(poruka, "b", new OdgovorPolja(null, null, null, null));
        greskaOdgovora(poruka, "b", new OdgovorPolja(null, List.of(), null, null));
        greskaOdgovora(poruka, "b", new OdgovorPolja(null, List.of("  "), null, null));
        greskaOdgovora(poruka, "b", new OdgovorPolja(null, Collections.singletonList(null), null, null));
        greskaOdgovora(poruka, "b", new OdgovorPolja(null, List.of("x".repeat(201)), null, null));
        greskaOdgovora(poruka, "b", new OdgovorPolja(null, List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"), null, null));
        greskaOdgovora(poruka, "b", new OdgovorPolja(45.0, List.of("x"), null, null));
        Map<String, OdgovorPolja> m = ispravniOdgovori();
        m.put("b", new OdgovorPolja(null, List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10"), null, null));
        assertDoesNotThrow(() -> BankaPP.proveriOdgovore(sema(), m));
        m.put("b", new OdgovorPolja(null, List.of("x".repeat(200)), null, null));
        assertDoesNotThrow(() -> BankaPP.proveriOdgovore(sema(), m));
    }

    @Test
    void jedanTacanMoraBitiIdOpcije() {
        String poruka = "Odgovor za polje 'c' mora biti id jedne od opcija.";
        greskaOdgovora(poruka, "c", new OdgovorPolja(null, null, null, null));
        greskaOdgovora(poruka, "c", new OdgovorPolja(null, null, 9L, null));
        greskaOdgovora(poruka, "c", new OdgovorPolja(null, null, 0L, null));
        greskaOdgovora(poruka, "c", new OdgovorPolja(null, null, 2L, List.of(2L)));
    }

    @Test
    void viseTacnihMoraBitiSkupIdJevaOpcija() {
        String poruka = "Odgovor za polje 'd' mora biti skup id-jeva opcija.";
        greskaOdgovora(poruka, "d", new OdgovorPolja(null, null, null, null));
        greskaOdgovora(poruka, "d", new OdgovorPolja(null, null, null, List.of()));
        greskaOdgovora(poruka, "d", new OdgovorPolja(null, null, null, List.of(1L, 9L)));
        greskaOdgovora(poruka, "d", new OdgovorPolja(null, null, null, List.of(1L, 1L)));
        greskaOdgovora(poruka, "d", new OdgovorPolja(null, null, null, Collections.singletonList(null)));
        greskaOdgovora(poruka, "d", new OdgovorPolja(null, null, 1L, List.of(1L)));
        Map<String, OdgovorPolja> m = ispravniOdgovori();
        m.put("d", new OdgovorPolja(null, null, null, List.of(1L, 2L, 3L)));
        assertDoesNotThrow(() -> BankaPP.proveriOdgovore(sema(), m));
    }

    @Test
    void kljucVanSemeJeNepoznat() {
        Map<String, OdgovorPolja> m = ispravniOdgovori();
        m.put("z", new OdgovorPolja(1.0, null, null, null));
        greska("Nepoznato polje 'z'.", () -> BankaPP.proveriOdgovore(sema(), m));
        greska("Nepoznato polje 'a'.", () -> BankaPP.proveriOdgovore(List.of(), Map.of("a", new OdgovorPolja(1.0, null, null, null))));
    }

    @Test
    void nepoznatKljucUPoruciJeSkracenIBezKontrolnihZnakova() {
        Map<String, OdgovorPolja> m = ispravniOdgovori();
        m.put("x".repeat(500) + "\n", new OdgovorPolja(1.0, null, null, null));
        SystemException ex = assertThrows(SystemException.class, () -> BankaPP.proveriOdgovore(sema(), m));
        assertEquals("Nepoznato polje '" + "x".repeat(40) + "'.", ex.getMessage());
    }

    // --- grupa ---

    @Test
    void nazivGrupeImaOd1Do120Znakova() {
        String poruka = "Naziv grupe mora imati 1 do 120 znakova.";
        greska(poruka, () -> BankaPP.proveriGrupu(null, 3, null));
        greska(poruka, () -> BankaPP.proveriGrupu("   ", 3, null));
        greska(poruka, () -> BankaPP.proveriGrupu("x".repeat(121), 3, null));
        assertDoesNotThrow(() -> BankaPP.proveriGrupu("x".repeat(120), 3, null));
        assertDoesNotThrow(() -> BankaPP.proveriGrupu("😀".repeat(120), 3, null));
    }

    @Test
    void tezinaJeOd1Do5() {
        String poruka = "Težina mora biti od 1 do 5.";
        greska(poruka, () -> BankaPP.proveriGrupu("Grupa", 6, null));
        greska(poruka, () -> BankaPP.proveriGrupu("Grupa", 0, null));
        greska(poruka, () -> BankaPP.proveriGrupu("Grupa", -1, null));
        assertDoesNotThrow(() -> BankaPP.proveriGrupu("Grupa", 1, null));
        assertDoesNotThrow(() -> BankaPP.proveriGrupu("Grupa", 5, null));
        // unutrašnji čvor stabla nema težinu
        assertDoesNotThrow(() -> BankaPP.proveriGrupu("Grupa", null, null));
    }

    @Test
    void uputstvoImaNajvise5000Znakova() {
        String poruka = "Uputstvo može imati najviše 5 000 znakova.";
        greska(poruka, () -> BankaPP.proveriGrupu("Grupa", 3, "x".repeat(5_001)));
        assertDoesNotThrow(() -> BankaPP.proveriGrupu("Grupa", 3, "x".repeat(5_000)));
        assertDoesNotThrow(() -> BankaPP.proveriGrupu("Grupa", 3, null));
    }

    // --- varijanta ---

    @Test
    void tekstZadatkaImaOd1Do20000Znakova() {
        String poruka = "Tekst zadatka mora imati 1 do 20 000 znakova.";
        greska(poruka, () -> BankaPP.proveriVarijantu("x".repeat(20_001), null, null));
        greska(poruka, () -> BankaPP.proveriVarijantu(null, null, null));
        greska(poruka, () -> BankaPP.proveriVarijantu("  \n ", null, null));
        assertDoesNotThrow(() -> BankaPP.proveriVarijantu("x".repeat(20_000), null, null));
        assertDoesNotThrow(() -> BankaPP.proveriVarijantu("😀".repeat(20_000), null, null));
    }

    @Test
    void resenjeImaNajvise20000Znakova() {
        String poruka = "Rešenje može imati najviše 20 000 znakova.";
        greska(poruka, () -> BankaPP.proveriVarijantu("Tekst", "x".repeat(20_001), null));
        assertDoesNotThrow(() -> BankaPP.proveriVarijantu("Tekst", "x".repeat(20_000), null));
        assertDoesNotThrow(() -> BankaPP.proveriVarijantu("Tekst", null, null));
    }

    @Test
    void nazivVarijanteImaNajvise120Znakova() {
        String poruka = "Naziv varijante može imati najviše 120 znakova.";
        greska(poruka, () -> BankaPP.proveriVarijantu("Tekst", null, "x".repeat(121)));
        assertDoesNotThrow(() -> BankaPP.proveriVarijantu("Tekst", null, "x".repeat(120)));
        assertDoesNotThrow(() -> BankaPP.proveriVarijantu("Tekst", null, null));
    }

    // --- prilog ---

    @Test
    void imePrilogaJeMalaSlovaCifreIDonjaCrta() {
        String poruka = "Ime priloga: 1-60 malih slova, cifara, '_' ili '-'.";
        for (String lose : new String[]{null, "", "A", "slika 1", "slika.png", "../x", "š", "x".repeat(61), "a\n", "a/b"}) {
            greska(poruka, () -> BankaPP.proveriImePriloga(lose));
        }
        for (String dobro : new String[]{"a", "slika_1", "dijagram-2", "0", "x".repeat(60)}) {
            assertDoesNotThrow(() -> BankaPP.proveriImePriloga(dobro));
        }
    }
}
