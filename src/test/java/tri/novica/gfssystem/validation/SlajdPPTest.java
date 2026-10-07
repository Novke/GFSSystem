package tri.novica.gfssystem.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import tri.novica.gfssystem.dto.uzivo.OpcijaCmd;
import tri.novica.gfssystem.dto.uzivo.PitanjeCmd;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.entity.uzivo.OdstupanjeTip;
import tri.novica.gfssystem.entity.uzivo.TekstPrikaz;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static tri.novica.gfssystem.entity.uzivo.TipPitanja.*;

class SlajdPPTest {

    static final String SLIKA = "3f1c2b9a-6d7e-4c1a-9b2f-0a1b2c3d4e5f";

    /** Pitanje koje prolazi proveru; testovi menjaju po jedno polje. */
    static final class P {
        TipPitanja tip = JEDAN_TACAN;
        String tekst = "Koliko je 2 + 2?";
        String slikaId;
        Integer vreme;
        List<OpcijaCmd> opcije = new ArrayList<>(List.of(o("3", false), o("4", true)));
        Double brojTacno, brojOdstupanje;
        OdstupanjeTip odstupanjeTip;
        String jedinica;
        TekstPrikaz tekstPrikaz;
        List<String> prihvatljivi;
        String skalaMin, skalaMax;

        PitanjeCmd cmd() {
            return new PitanjeCmd(tip, tekst, slikaId, vreme, opcije, brojTacno, brojOdstupanje, odstupanjeTip,
                    jedinica, tekstPrikaz, prihvatljivi, skalaMin, skalaMax);
        }
    }

    static OpcijaCmd o(String tekst, boolean tacna) {
        return new OpcijaCmd(tekst, tacna);
    }

    static SlajdCmd pitanje(Consumer<P> izmena) {
        P p = new P();
        izmena.accept(p);
        return new SlajdCmd(TipSlajda.PITANJE, null, null, null, null, false, p.cmd());
    }

    static SlajdCmd info(String naslov, String sadrzaj, String slikaId) {
        return new SlajdCmd(TipSlajda.INFO, naslov, sadrzaj, slikaId, null, false, null);
    }

    static String znakova(int n) {
        return "a".repeat(n);
    }

    static void greska(String poruka, Executable e) {
        SystemException ex = assertThrows(SystemException.class, e);
        assertEquals(400, ex.getCode());
        assertEquals(poruka, ex.getMessage());
    }

    static void greska(String poruka, SlajdCmd cmd) {
        greska(poruka, () -> SlajdPP.proveri(SlajdPP.normalizuj(cmd)));
    }

    static void ispravan(SlajdCmd cmd) {
        assertDoesNotThrow(() -> SlajdPP.proveri(SlajdPP.normalizuj(cmd)));
    }

    // --- slajd ---

    @Test
    void tipSlajdaJeObavezan() {
        greska("Tip slajda je obavezan.", new SlajdCmd(null, "Naslov", null, null, null, false, null));
        greska("Tip slajda je obavezan.", () -> SlajdPP.proveri(null));
    }

    @Test
    void infoSlajdMoraImatiNaslovTekstIliSliku() {
        greska("Info slajd mora imati naslov, tekst ili sliku.", info(null, null, null));
        greska("Info slajd mora imati naslov, tekst ili sliku.", info("  ", "\n\t ", ""));
        // i bez normalizacije: prazan tekst se ne računa
        greska("Info slajd mora imati naslov, tekst ili sliku.", () -> SlajdPP.proveri(info(" ", " ", " ")));
        ispravan(info("Uvod", null, null));
        ispravan(info(null, "- tačka", null));
        ispravan(info(null, null, SLIKA));
    }

    @Test
    void naslovNajvise300Znakova() {
        greska("Naslov može imati najviše 300 znakova.", info(znakova(301), null, null));
        ispravan(info(znakova(300), null, null));
        // znakovi kao u MySQL-u (code point), ne UTF-16 jedinice: 300 emodžija staje u varchar(300)
        ispravan(info("😀".repeat(300), null, null));
        greska("Naslov može imati najviše 300 znakova.",
                new SlajdCmd(TipSlajda.PITANJE, znakova(301), null, null, null, false, new P().cmd()));
    }

    @Test
    void tekstSlajdaNajvise20000Znakova() {
        greska("Tekst slajda može imati najviše 20000 znakova.", info(null, znakova(20_001), null));
        ispravan(info(null, znakova(20_000), null));
    }

    @Test
    void beleskeNajvise5000Znakova() {
        greska("Beleške mogu imati najviše 5000 znakova.",
                new SlajdCmd(TipSlajda.INFO, "Uvod", null, null, znakova(5_001), false, null));
        greska("Beleške mogu imati najviše 5000 znakova.",
                new SlajdCmd(TipSlajda.PITANJE, null, null, null, znakova(5_001), false, new P().cmd()));
        ispravan(new SlajdCmd(TipSlajda.INFO, "Uvod", null, null, znakova(5_000), false, null));
    }

    @Test
    void najvise200Slajdova() {
        greska("Prezentacija može imati najviše 200 slajdova.", () -> SlajdPP.proveriBrojSlajdova(200));
        greska("Prezentacija može imati najviše 200 slajdova.", () -> SlajdPP.proveriBrojSlajdova(250));
        assertDoesNotThrow(() -> SlajdPP.proveriBrojSlajdova(199));
        assertDoesNotThrow(() -> SlajdPP.proveriBrojSlajdova(0));
    }

    // --- pitanje ---

    @Test
    void slajdSaPitanjemMoraImatiPitanje() {
        greska("Slajd sa pitanjem mora imati pitanje.", new SlajdCmd(TipSlajda.PITANJE, "x", null, null, null, false, null));
    }

    @Test
    void tipPitanjaJeObavezan() {
        greska("Tip pitanja je obavezan.", pitanje(p -> p.tip = null));
    }

    @Test
    void tekstPitanjaObavezanINajvise2000() {
        greska("Tekst pitanja je obavezan (najviše 2000 znakova).", pitanje(p -> p.tekst = null));
        greska("Tekst pitanja je obavezan (najviše 2000 znakova).", pitanje(p -> p.tekst = "   "));
        greska("Tekst pitanja je obavezan (najviše 2000 znakova).", pitanje(p -> p.tekst = znakova(2_001)));
        ispravan(pitanje(p -> p.tekst = znakova(2_000)));
        ispravan(pitanje(p -> p.tekst = "?"));
    }

    @Test
    void od2Do6PonudjenihOdgovora() {
        for (TipPitanja tip : List.of(JEDAN_TACAN, VISE_TACNIH, ANKETA)) {
            greska("Pitanje mora imati od 2 do 6 ponuđenih odgovora.", pitanje(p -> {
                p.tip = tip;
                p.opcije = List.of(o("jedini", true));
            }));
            greska("Pitanje mora imati od 2 do 6 ponuđenih odgovora.", pitanje(p -> {
                p.tip = tip;
                p.opcije = null;
            }));
            greska("Pitanje mora imati od 2 do 6 ponuđenih odgovora.", pitanje(p -> {
                p.tip = tip;
                p.opcije = List.of(o("a", true), o("b", false), o("c", false), o("d", false), o("e", false),
                        o("f", false), o("g", false));
            }));
            ispravan(pitanje(p -> {
                p.tip = tip;
                p.opcije = List.of(o("a", true), o("b", false), o("c", false), o("d", false), o("e", false),
                        o("f", false));
            }));
        }
    }

    @Test
    void ponudjeniOdgovorOd1Do300Znakova() {
        greska("Ponuđeni odgovor mora imati od 1 do 300 znakova.", pitanje(p -> p.opcije = List.of(o("  ", false), o("4", true))));
        greska("Ponuđeni odgovor mora imati od 1 do 300 znakova.", pitanje(p -> p.opcije = List.of(o(null, false), o("4", true))));
        greska("Ponuđeni odgovor mora imati od 1 do 300 znakova.",
                pitanje(p -> p.opcije = List.of(o(znakova(301), false), o("4", true))));
        greska("Ponuđeni odgovor mora imati od 1 do 300 znakova.",
                pitanje(p -> p.opcije = Arrays.asList(null, o("4", true))));
        ispravan(pitanje(p -> p.opcije = List.of(o(znakova(300), false), o("4", true))));
    }

    @Test
    void jedanTacanMoraImatiTacnoJedanTacan() {
        greska("Pitanje sa jednim tačnim odgovorom mora imati tačno jedan tačan odgovor.",
                pitanje(p -> p.opcije = List.of(o("3", false), o("4", false))));
        greska("Pitanje sa jednim tačnim odgovorom mora imati tačno jedan tačan odgovor.",
                pitanje(p -> p.opcije = List.of(o("3", true), o("4", true))));
    }

    @Test
    void viseTacnihMoraImatiBarJedanTacan() {
        greska("Označi bar jedan tačan odgovor.", pitanje(p -> {
            p.tip = VISE_TACNIH;
            p.opcije = List.of(o("a", false), o("b", false), o("c", false));
        }));
        ispravan(pitanje(p -> {
            p.tip = VISE_TACNIH;
            p.opcije = List.of(o("a", true), o("b", true), o("c", false));
        }));
    }

    @Test
    void tacnoNetacnoImaDvaOdgovoraIJedanTacan() {
        String poruka = "Tačno/netačno pitanje ima tačno dva odgovora, od kojih je jedan tačan.";
        greska(poruka, pitanje(p -> {
            p.tip = TACNO_NETACNO;
            p.opcije = List.of(o("", true), o("", false), o("", false));
        }));
        greska(poruka, pitanje(p -> {
            p.tip = TACNO_NETACNO;
            p.opcije = List.of(o("", true));
        }));
        greska(poruka, pitanje(p -> {
            p.tip = TACNO_NETACNO;
            p.opcije = List.of(o("", true), o("", true));
        }));
        greska(poruka, pitanje(p -> {
            p.tip = TACNO_NETACNO;
            p.opcije = List.of(o("", false), o("", false));
        }));
        // tekstove postavlja server, klijent ih ne mora slati
        ispravan(pitanje(p -> {
            p.tip = TACNO_NETACNO;
            p.opcije = List.of(o(null, false), o("", true));
        }));
    }

    @Test
    void anketaIgnoriseTacne() {
        ispravan(pitanje(p -> {
            p.tip = ANKETA;
            p.opcije = List.of(o("a", true), o("b", true));
        }));
        ispravan(pitanje(p -> {
            p.tip = ANKETA;
            p.opcije = List.of(o("a", false), o("b", false));
        }));
    }

    @Test
    void vremeOd5Do600Sekundi() {
        greska("Vreme za odgovor mora biti od 5 do 600 sekundi.", pitanje(p -> p.vreme = 4));
        greska("Vreme za odgovor mora biti od 5 do 600 sekundi.", pitanje(p -> p.vreme = 601));
        greska("Vreme za odgovor mora biti od 5 do 600 sekundi.", pitanje(p -> p.vreme = -1));
        ispravan(pitanje(p -> p.vreme = 5));
        ispravan(pitanje(p -> p.vreme = 600));
        ispravan(pitanje(p -> p.vreme = null));
    }

    @Test
    void odstupanjeNeMozeBitiNegativno() {
        greska("Odstupanje ne može biti negativno.", pitanje(p -> {
            p.tip = BROJ;
            p.brojTacno = 9.81;
            p.brojOdstupanje = -0.1;
        }));
        ispravan(pitanje(p -> {
            p.tip = BROJ;
            p.brojTacno = 9.81;
            p.brojOdstupanje = 0.0;
        }));
        // bez tačne vrednosti je procena
        ispravan(pitanje(p -> p.tip = BROJ));
    }

    @Test
    void brojMoraBitiKonacan() {
        greska("Broj nije ispravan.", pitanje(p -> {
            p.tip = BROJ;
            p.brojTacno = Double.POSITIVE_INFINITY;
        }));
        greska("Broj nije ispravan.", pitanje(p -> {
            p.tip = BROJ;
            p.brojTacno = 1.0;
            p.brojOdstupanje = Double.NaN;
        }));
    }

    @Test
    void jedinicaNajvise30Znakova() {
        greska("Jedinica može imati najviše 30 znakova.", pitanje(p -> {
            p.tip = BROJ;
            p.jedinica = znakova(31);
        }));
        ispravan(pitanje(p -> {
            p.tip = BROJ;
            p.jedinica = znakova(30);
        }));
    }

    @Test
    void najvise20PrihvatljivihOdgovoraSvakiDo100() {
        String poruka = "Najviše 20 prihvatljivih odgovora, svaki do 100 znakova.";
        greska(poruka, pitanje(p -> {
            p.tip = KRATAK_TEKST;
            p.prihvatljivi = Collections.nCopies(21, "beton");
        }));
        greska(poruka, pitanje(p -> {
            p.tip = KRATAK_TEKST;
            p.prihvatljivi = List.of(znakova(101));
        }));
        ispravan(pitanje(p -> {
            p.tip = KRATAK_TEKST;
            p.prihvatljivi = Collections.nCopies(20, znakova(100));
        }));
        ispravan(pitanje(p -> {
            p.tip = KRATAK_TEKST;
            p.prihvatljivi = null;
        }));
    }

    @Test
    void oznakaSkaleNajvise60Znakova() {
        String poruka = "Oznaka skale može imati najviše 60 znakova.";
        greska(poruka, pitanje(p -> {
            p.tip = SKALA;
            p.skalaMin = znakova(61);
        }));
        greska(poruka, pitanje(p -> {
            p.tip = SKALA;
            p.skalaMax = znakova(61);
        }));
        ispravan(pitanje(p -> {
            p.tip = SKALA;
            p.skalaMin = znakova(60);
            p.skalaMax = "Potpuno se slažem";
        }));
    }

    // --- normalizacija ---

    @Test
    void normalizujTrimujeIPrazanStringUNull() {
        SlajdCmd n = SlajdPP.normalizuj(new SlajdCmd(TipSlajda.INFO, "  Uvod  ", "   ", "", " beleška \n", true, null));
        assertEquals("Uvod", n.naslov());
        assertNull(n.sadrzaj());
        assertNull(n.slikaId());
        assertEquals("beleška", n.beleske());
        assertTrue(n.postepeno());

        PitanjeCmd p = SlajdPP.normalizuj(pitanje(x -> {
            x.tekst = "  Koliko?  ";
            x.slikaId = "  ";
            x.opcije = List.of(o("  3 ", false), o(" 4", true));
        })).pitanje();
        assertEquals("Koliko?", p.tekst());
        assertNull(p.slikaId());
        assertEquals(List.of(o("3", false), o("4", true)), p.opcije());
    }

    @Test
    void normalizujInfoBrisePitanje() {
        SlajdCmd n = SlajdPP.normalizuj(new SlajdCmd(TipSlajda.INFO, "Uvod", null, null, null, false, new P().cmd()));
        assertNull(n.pitanje());
    }

    @Test
    void normalizujAnketaSveNetacne() {
        PitanjeCmd p = SlajdPP.normalizuj(pitanje(x -> {
            x.tip = ANKETA;
            x.opcije = List.of(o("a", true), o("b", false), o("c", true));
        })).pitanje();
        assertTrue(p.opcije().stream().noneMatch(OpcijaCmd::tacna));
        assertEquals(List.of("a", "b", "c"), p.opcije().stream().map(OpcijaCmd::tekst).toList());
    }

    @Test
    void normalizujTacnoNetacnoPostavljaTekstove() {
        PitanjeCmd p = SlajdPP.normalizuj(pitanje(x -> {
            x.tip = TACNO_NETACNO;
            x.opcije = List.of(o("da", false), o(null, true));
        })).pitanje();
        assertEquals(List.of(o("Tačno", false), o("Netačno", true)), p.opcije());
    }

    @Test
    void normalizujKratakTekst() {
        PitanjeCmd p = SlajdPP.normalizuj(pitanje(x -> {
            x.tip = KRATAK_TEKST;
            x.tekstPrikaz = null;
            x.prihvatljivi = Arrays.asList(" beton ", "", null, "  ", "čelik");
        })).pitanje();
        assertEquals(TekstPrikaz.OBLAK, p.tekstPrikaz());
        assertEquals(List.of(), p.opcije());
        assertEquals(List.of("beton", "čelik"), p.prihvatljiviOdgovori());

        PitanjeCmd lista = SlajdPP.normalizuj(pitanje(x -> {
            x.tip = KRATAK_TEKST;
            x.tekstPrikaz = TekstPrikaz.LISTA;
            x.prihvatljivi = null;
        })).pitanje();
        assertEquals(TekstPrikaz.LISTA, lista.tekstPrikaz());
        assertEquals(List.of(), lista.prihvatljiviOdgovori());
    }

    @Test
    void normalizujBrojPodrazumevaneVrednosti() {
        PitanjeCmd p = SlajdPP.normalizuj(pitanje(x -> {
            x.tip = BROJ;
            x.brojTacno = 9.81;
            x.jedinica = " m/s² ";
        })).pitanje();
        assertEquals(0.0, p.brojOdstupanje());
        assertEquals(OdstupanjeTip.APSOLUTNO, p.odstupanjeTip());
        assertEquals(List.of(), p.opcije());
        assertEquals("m/s²", p.jedinica());

        PitanjeCmd procenat = SlajdPP.normalizuj(pitanje(x -> {
            x.tip = BROJ;
            x.brojOdstupanje = 5.0;
            x.odstupanjeTip = OdstupanjeTip.PROCENAT;
        })).pitanje();
        assertEquals(5.0, procenat.brojOdstupanje());
        assertEquals(OdstupanjeTip.PROCENAT, procenat.odstupanjeTip());
        assertNull(procenat.brojTacno());
    }

    @Test
    void normalizujSkalaBezOpcija() {
        PitanjeCmd p = SlajdPP.normalizuj(pitanje(x -> {
            x.tip = SKALA;
            x.skalaMin = " Ne slažem se ";
        })).pitanje();
        assertEquals(List.of(), p.opcije());
        assertEquals("Ne slažem se", p.skalaMinOznaka());
        assertNull(p.skalaMaxOznaka());
    }

    @Test
    void normalizujBrisePoljaKojaNeVazeZaTip() {
        PitanjeCmd p = SlajdPP.normalizuj(pitanje(x -> {
            x.brojTacno = 1.0;
            x.brojOdstupanje = 2.0;
            x.odstupanjeTip = OdstupanjeTip.PROCENAT;
            x.jedinica = "m";
            x.tekstPrikaz = TekstPrikaz.LISTA;
            x.prihvatljivi = List.of("x");
            x.skalaMin = "malo";
            x.skalaMax = "mnogo";
        })).pitanje();
        assertEquals(JEDAN_TACAN, p.tip());
        assertEquals(2, p.opcije().size());
        assertNull(p.brojTacno());
        assertNull(p.brojOdstupanje());
        assertNull(p.odstupanjeTip());
        assertNull(p.jedinica());
        assertNull(p.tekstPrikaz());
        assertNull(p.prihvatljiviOdgovori());
        assertNull(p.skalaMinOznaka());
        assertNull(p.skalaMaxOznaka());
    }

    @Test
    void normalizujPodnosiNulove() {
        assertNull(SlajdPP.normalizuj(null));
        SlajdCmd bezPitanja = SlajdPP.normalizuj(new SlajdCmd(TipSlajda.PITANJE, null, null, null, null, false, null));
        assertNull(bezPitanja.pitanje());
        PitanjeCmd bezTipa = SlajdPP.normalizuj(pitanje(x -> {
            x.tip = null;
            x.opcije = null;
        })).pitanje();
        assertNull(bezTipa.tip());
    }
}
