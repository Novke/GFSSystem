package tri.novica.gfssystem.service.banka;

import org.junit.jupiter.api.Test;
import tri.novica.gfssystem.dto.banka.OdgovorPolja;
import tri.novica.gfssystem.dto.banka.Polje;
import tri.novica.gfssystem.dto.banka.PoljeOpcija;
import tri.novica.gfssystem.dto.banka.TipPolja;
import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OcenaPoljaTest {

    private static final List<PoljeOpcija> OPCIJE = List.of(
            new PoljeOpcija(1L, "A"), new PoljeOpcija(2L, "B"), new PoljeOpcija(3L, "C"));

    private static Polje broj(Double odstupanje, OdstupanjeTip tip) {
        return new Polje("a", "a)", TipPolja.BROJ, odstupanje, tip, null, null, null, null);
    }

    private static Polje tekst(Boolean velicina, Boolean razmaci) {
        return new Polje("b", "b)", TipPolja.TEKST, null, null, null, velicina, razmaci, null);
    }

    private static Polje bezNula(Boolean vodeceNule) {
        return new Polje("b", "b)", TipPolja.TEKST, null, null, null, null, null, null, vodeceNule);
    }

    private static Polje opcije(TipPolja tip) {
        return new Polje("c", "c)", tip, null, null, null, null, null, OPCIJE);
    }

    private static OdgovorPolja brojOdg(double v) {
        return new OdgovorPolja(v, null, null, null);
    }

    private static OdgovorPolja tekstOdg(String... prihvatljivi) {
        return new OdgovorPolja(null, List.of(prihvatljivi), null, null);
    }

    // --- BROJ ---

    @Test
    void brojSaTackomIliZarezom() {
        Polje p = broj(0.0, OdstupanjeTip.APSOLUTNO);
        assertThat(OcenaPolja.tacno(p, brojOdg(45), "45")).isTrue();
        assertThat(OcenaPolja.tacno(p, brojOdg(45), "45,0")).isTrue();
        assertThat(OcenaPolja.tacno(p, brojOdg(45), " 45.0 ")).isTrue();
        assertThat(OcenaPolja.tacno(p, brojOdg(45), "46")).isFalse();
    }

    @Test
    void brojBezOdstupanjaPodrazumevaNulu() {
        assertThat(OcenaPolja.tacno(broj(null, null), brojOdg(45), "45")).isTrue();
        assertThat(OcenaPolja.tacno(broj(null, null), brojOdg(45), "45.001")).isFalse();
    }

    @Test
    void apsolutnoOdstupanje() {
        Polje p = broj(0.1, OdstupanjeTip.APSOLUTNO);
        assertThat(OcenaPolja.tacno(p, brojOdg(45), "45.2")).isFalse();
        assertThat(OcenaPolja.tacno(p, brojOdg(45), "45.1")).isTrue();
        assertThat(OcenaPolja.tacno(p, brojOdg(45), "44.9")).isTrue();
    }

    @Test
    void procenatOdstupanja() {
        Polje p = broj(10.0, OdstupanjeTip.PROCENAT);
        assertThat(OcenaPolja.tacno(p, brojOdg(45), "49.5")).isTrue();
        assertThat(OcenaPolja.tacno(p, brojOdg(45), "49.6")).isFalse();
        assertThat(OcenaPolja.tacno(p, brojOdg(-45), "-49.5")).isTrue();
    }

    @Test
    void brojNijeBrojJeNetacan() {
        Polje p = broj(0.0, OdstupanjeTip.APSOLUTNO);
        assertThat(OcenaPolja.tacno(p, brojOdg(45), "abc")).isFalse();
        assertThat(OcenaPolja.tacno(p, brojOdg(45), "")).isFalse();
        assertThat(OcenaPolja.tacno(p, brojOdg(45), "4 5 x")).isFalse();
        assertThat(OcenaPolja.tacno(p, brojOdg(45), "9".repeat(400))).isFalse();
        assertThat(OcenaPolja.tacno(p, new OdgovorPolja(null, null, null, null), "45")).isFalse();
        assertThat(OcenaPolja.tacno(p, brojOdg(45), 45L)).isFalse();
    }

    // --- TEKST ---

    @Test
    void tekstZanemarujeRazmakeKadJeUkljuceno() {
        OdgovorPolja o = tekstOdg("10110110");
        assertThat(OcenaPolja.tacno(tekst(true, true), o, "1011 0110")).isTrue();
        assertThat(OcenaPolja.tacno(tekst(true, false), o, "1011 0110")).isFalse();
        assertThat(OcenaPolja.tacno(tekst(true, false), o, "10110110")).isTrue();
        // razmaci u prihvatljivom odgovoru se tretiraju isto
        assertThat(OcenaPolja.tacno(tekst(true, true), tekstOdg("1011 0110"), "10110110")).isTrue();
        assertThat(OcenaPolja.tacno(tekst(true, false), tekstOdg("1011 0110"), "1011   0110")).isTrue();
    }

    @Test
    void tekstZanemarujeVelicinuSlova() {
        OdgovorPolja o = tekstOdg("A3");
        assertThat(OcenaPolja.tacno(tekst(true, true), o, "a3")).isTrue();
        // bez zastavica važi podrazumevano (zanemari oboje)
        assertThat(OcenaPolja.tacno(tekst(null, null), o, " a 3 ")).isTrue();
        assertThat(OcenaPolja.tacno(tekst(true, true), o, "A4")).isFalse();
    }

    @Test
    void tekstRazlikujeVelicinuKadJeIskljucena() {
        OdgovorPolja o = tekstOdg("A3");
        assertThat(OcenaPolja.tacno(tekst(false, true), o, "A3")).isTrue();
        assertThat(OcenaPolja.tacno(tekst(false, true), o, "a3")).isFalse();
        assertThat(OcenaPolja.tacno(tekst(false, true), tekstOdg("a3", "A3"), "A3")).isTrue();
        assertThat(OcenaPolja.tacno(tekst(false, false), tekstOdg("Ab Cd"), "Ab  Cd")).isTrue();
        assertThat(OcenaPolja.tacno(tekst(false, false), tekstOdg("Ab Cd"), "ab Cd")).isFalse();
    }

    @Test
    void tekstSaVelicinomSlovaIPismomTretiraDigrafKaoSlovo() {
        Polje p = tekst(false, true);
        assertThat(OcenaPolja.tacno(p, tekstOdg("Đorđe"), "Djordje")).isTrue();
        assertThat(OcenaPolja.tacno(p, tekstOdg("Đorđe"), "Ђорђе")).isTrue();
        assertThat(OcenaPolja.tacno(p, tekstOdg("Đorđe"), "djordje")).isFalse();
        assertThat(OcenaPolja.tacno(p, tekstOdg("Đorđe"), "DJORDJE")).isFalse();
    }

    @Test
    void tekstTraziBarJedanOdPrihvatljivih() {
        OdgovorPolja o = tekstOdg("q0", "Q 0", "stanje nula");
        Polje p = tekst(true, true);
        assertThat(OcenaPolja.tacno(p, o, "Stanje Nula")).isTrue();
        assertThat(OcenaPolja.tacno(p, o, "q 0")).isTrue();
        assertThat(OcenaPolja.tacno(p, o, "q1")).isFalse();
    }

    @Test
    void tekstSaCirilicomIKvacicama() {
        OdgovorPolja o = tekstOdg("šest");
        Polje p = tekst(true, true);
        assertThat(OcenaPolja.tacno(p, o, "шест")).isTrue();
        assertThat(OcenaPolja.tacno(p, o, "sest")).isTrue();
    }

    @Test
    void prazanTekstNijeTacan() {
        Polje p = tekst(true, true);
        assertThat(OcenaPolja.tacno(p, tekstOdg("a"), "")).isFalse();
        assertThat(OcenaPolja.tacno(p, tekstOdg("a"), "   ")).isFalse();
        assertThat(OcenaPolja.tacno(p, tekstOdg(" "), " ")).isFalse();
        assertThat(OcenaPolja.tacno(p, new OdgovorPolja(null, null, null, null), "a")).isFalse();
        assertThat(OcenaPolja.tacno(p, new OdgovorPolja(null, List.of(), null, null), "a")).isFalse();
        assertThat(OcenaPolja.tacno(p, tekstOdg("a"), 5L)).isFalse();
    }

    @Test
    void vodeceNuleSeZanemarujuSamoKadJePoljeTakoPodeseno() {
        OdgovorPolja o = tekstOdg("1111111");
        assertThat(OcenaPolja.tacno(bezNula(true), o, "01111111")).isTrue();
        assertThat(OcenaPolja.tacno(bezNula(true), o, "0001111111")).isTrue();
        assertThat(OcenaPolja.tacno(bezNula(true), o, "0 111 1111")).isTrue();
        assertThat(OcenaPolja.tacno(bezNula(true), o, "11111110")).isFalse();
        // i kad prihvatljivi odgovor ima nule (a učenik ih nema)
        assertThat(OcenaPolja.tacno(bezNula(true), tekstOdg("0A3F"), "a3f")).isTrue();
        // bez opcije (podrazumevano i stara šema bez polja): fiksna širina, komplement
        OdgovorPolja komplement = tekstOdg("00000101");
        assertThat(OcenaPolja.tacno(bezNula(null), komplement, "101")).isFalse();
        assertThat(OcenaPolja.tacno(bezNula(false), komplement, "101")).isFalse();
        assertThat(OcenaPolja.tacno(bezNula(false), komplement, "0000 0101")).isTrue();
        assertThat(OcenaPolja.tacno(bezNula(false), o, "01111111")).isFalse();
    }

    @Test
    void vodeceNuleSamoNaPocetkuIIspredCifreIliSlova() {
        assertThat(OcenaPolja.bezVodecihNula("01111111")).isEqualTo("1111111");
        assertThat(OcenaPolja.bezVodecihNula("000")).isEqualTo("0");
        assertThat(OcenaPolja.bezVodecihNula("0")).isEqualTo("0");
        assertThat(OcenaPolja.bezVodecihNula("-007")).isEqualTo("-7");
        assertThat(OcenaPolja.bezVodecihNula("+0a")).isEqualTo("+a");
        assertThat(OcenaPolja.bezVodecihNula("0.101")).isEqualTo("0.101");
        assertThat(OcenaPolja.bezVodecihNula("00,5")).isEqualTo("0,5");
        assertThat(OcenaPolja.bezVodecihNula("1001")).isEqualTo("1001");
        assertThat(OcenaPolja.bezVodecihNula("")).isEmpty();
        // ključ rezultata spaja "01111111" i "1111111" u isti čip
        assertThat(OcenaPolja.kljucTeksta(bezNula(true), "0111 1111")).isEqualTo("1111111");
        assertThat(OcenaPolja.kljucOcene(bezNula(true), "0111 1111")).isEqualTo("1111111");
        assertThat(OcenaPolja.kljucTeksta(bezNula(false), "0111 1111")).isEqualTo("01111111");
    }

    @Test
    void kljucTekstaSazima() {
        assertThat(OcenaPolja.kljucTeksta(tekst(true, true), " 1011   0110 ")).isEqualTo("10110110");
        assertThat(OcenaPolja.kljucTeksta(tekst(true, false), " 1011   0110 ")).isEqualTo("1011 0110");
        assertThat(OcenaPolja.kljucTeksta(tekst(true, true), "A 3")).isEqualTo("a3");
        assertThat(OcenaPolja.kljucTeksta(tekst(null, null), "Šest")).isEqualTo("sest");
        assertThat(OcenaPolja.kljucTeksta(tekst(true, true), null)).isEmpty();
    }

    // --- JEDAN_TACAN ---

    @Test
    void jedanTacan() {
        Polje p = opcije(TipPolja.JEDAN_TACAN);
        OdgovorPolja o = new OdgovorPolja(null, null, 2L, null);
        assertThat(OcenaPolja.tacno(p, o, 2L)).isTrue();
        assertThat(OcenaPolja.tacno(p, o, 1L)).isFalse();
        assertThat(OcenaPolja.tacno(p, o, 2)).isTrue();
        assertThat(OcenaPolja.tacno(p, o, 2.0)).isTrue();
        assertThat(OcenaPolja.tacno(p, o, 2.5)).isFalse();
        assertThat(OcenaPolja.tacno(p, o, "2")).isFalse();
        assertThat(OcenaPolja.tacno(p, new OdgovorPolja(null, null, null, null), 2L)).isFalse();
    }

    // --- VISE_TACNIH ---

    @Test
    void viseTacnihJeSkup() {
        Polje p = opcije(TipPolja.VISE_TACNIH);
        OdgovorPolja o = new OdgovorPolja(null, null, null, List.of(1L, 3L));
        assertThat(OcenaPolja.tacno(p, o, List.of(3L, 1L))).isTrue();
        assertThat(OcenaPolja.tacno(p, o, List.of(1L, 3L))).isTrue();
        assertThat(OcenaPolja.tacno(p, o, List.of(1L))).isFalse();
        assertThat(OcenaPolja.tacno(p, o, List.of(1L, 2L, 3L))).isFalse();
        assertThat(OcenaPolja.tacno(p, o, List.of(1, 3))).isTrue();
        assertThat(OcenaPolja.tacno(p, o, List.of())).isFalse();
        assertThat(OcenaPolja.tacno(p, o, 1L)).isFalse();
        assertThat(OcenaPolja.tacno(p, o, List.of("1", "3"))).isFalse();
        assertThat(OcenaPolja.tacno(p, new OdgovorPolja(null, null, null, null), List.of(1L))).isFalse();
        assertThat(OcenaPolja.tacno(p, new OdgovorPolja(null, null, null, List.of()), List.of())).isFalse();
    }

    // --- nedostajuće vrednosti ---

    @Test
    void nullJeUvekNetacanINikadNeBaca() {
        Polje p = broj(0.0, OdstupanjeTip.APSOLUTNO);
        assertThat(OcenaPolja.tacno(p, brojOdg(45), null)).isFalse();
        assertThat(OcenaPolja.tacno(p, null, "45")).isFalse();
        assertThat(OcenaPolja.tacno(null, brojOdg(45), "45")).isFalse();
        assertThat(OcenaPolja.tacno(new Polje("a", "a", null, null, null, null, null, null, null), brojOdg(45), "45"))
                .isFalse();
        assertThat(OcenaPolja.tacno(opcije(TipPolja.JEDAN_TACAN), new OdgovorPolja(null, null, 1L, null), null)).isFalse();
        assertThat(OcenaPolja.tacno(opcije(TipPolja.VISE_TACNIH), new OdgovorPolja(null, null, null, List.of(1L)), null))
                .isFalse();
    }
}
