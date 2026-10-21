package tri.novica.gfssystem.mcp.servis;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.banka.GrupaPutanja;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.dto.uzivo.OpcijaDetails;
import tri.novica.gfssystem.dto.uzivo.PitanjeDetails;
import tri.novica.gfssystem.dto.uzivo.PredmetKratko;
import tri.novica.gfssystem.dto.uzivo.Prelaz;
import tri.novica.gfssystem.dto.uzivo.PrezentacijaDetails;
import tri.novica.gfssystem.dto.uzivo.SlajdDetails;
import tri.novica.gfssystem.dto.uzivo.ZadatakSlajdaInfo;
import tri.novica.gfssystem.dto.uzivo.platno.*;
import tri.novica.gfssystem.entity.uzivo.SmerPrelaza;
import tri.novica.gfssystem.entity.uzivo.TelefonPrikaz;
import tri.novica.gfssystem.entity.uzivo.Tema;
import tri.novica.gfssystem.entity.uzivo.TipPitanja;
import tri.novica.gfssystem.entity.uzivo.TipPrelaza;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.mcp.McpOdgovor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sažet pregled prezentacije za Kloda (spec MCP 4.4), bez baze. Oblik slajda je po merama šest stvarnih vežbi iz
 * {@code staging/pptx-provera} (77 slajdova: prosečno 23 reči i 6,3 sloja po slajdu, p90 49 reči, najviše 68): PLATNO
 * slajd sa naslovom od 60 znakova, pasusom od 150, slikom, oblikom, formulom, crtežom od 2 000 tačaka i dve KLIK
 * animacije. Šest vežbi od 10 do 24 slajda staje u jednu stranu ispod {@code McpOdgovor.MAX_ZNAKOVA - 2000}; teži slajd
 * (tri pasusa) najviše u dve; strane su pohlepne, iste za isti ulaz, i zajedno daju sve slajdove redom.
 */
class PrezentacijaPregledTest {

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String LINK = "http://localhost:4200/prezentacije/7";
    static final int GRANICA = McpOdgovor.MAX_ZNAKOVA - 2000;
    static final String SLIKA = "3f1c2b9a-6d7e-4c1a-9b2f-0a1b2c3d4e5f";
    static final String CRTEZ = "7a9d0c11-2b34-4f56-8a78-9b0c1d2e3f40";
    static final List<Integer> VEZBE = List.of(10, 14, 18, 20, 22, 24);

    /** Tekst od tačno {@code n} znakova, sa razmacima i dijakriticima kao u stvarnim vežbama. */
    static String tekst(int n) {
        String uzor = "Pretvaranje brojeva iz binarnog u dekadni sistem: težinski zbir cifara i čvorovi. ";
        StringBuilder sb = new StringBuilder();
        while (sb.length() < n) {
            sb.append(uzor);
        }
        return sb.substring(0, n);
    }

    static Deo deo(String tekst, String font, double velicina) {
        return new Deo(tekst, font, velicina, "#000000", false, false, false, false, Polozaj.OBICAN);
    }

    static Pasus pasus(Deo... delovi) {
        return new Pasus(Poravnanje.LEVO, 0, null, new Uvlacenje(0.0, 0.0), 0.0, 0.0, 100.0, List.of(), List.of(delovi));
    }

    static TekstSloj tekstSloj(String id, String naziv, double y, List<Pasus> pasusi) {
        return new TekstSloj(id, naziv, 36.25, y, 887.5, 120.75, 0.0, 1.0, false, null, pasusi,
                new Okvir(7.2, 3.6, 7.2, 3.6, Sidro.VRH, true, 1.0, 1.0), null, null);
    }

    static List<Double> tacke(int n) {
        List<Double> t = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            t.add(100.0 + i % 300);
            t.add(200.5 + i % 120);
        }
        return t;
    }

    /** Šest slojeva kao u izmerenom slajdu vežbi; {@code pasusaTeksta} pasusa od 150 znakova u tekstu. */
    static Platno platno(int pasusaTeksta) {
        List<Pasus> pasusi = new ArrayList<>();
        for (int i = 0; i < pasusaTeksta; i++) {
            pasusi.add(pasus(deo(tekst(150), "Carlito", 20.0)));
        }
        List<Sloj> slojevi = List.of(
                tekstSloj("naslov", "Naslov 1", 28.5, List.of(pasus(deo(tekst(60), "Carlito", 36.0)))),
                tekstSloj("tekst", "Rezervisano mesto za sadržaj 2", 160.25, pasusi),
                new SlikaSloj("slika", "Slika 3", 610.0, 300.0, 300.5, 200.25, 0.0, 1.0, false, null, SLIKA,
                        new Isecak(0.0, 0.0, 0.0, 0.0)),
                new OblikSloj("oblik", "Pravougaonik 4", 40.0, 420.5, 320.0, 80.0, 0.0, 1.0, false, null,
                        VrstaOblika.PRAVOUGAONIK, "#FFEEAA", new Ivica("#333333", 1.0, Crtica.PUNA), null, null),
                new FormulaSloj("formula", "Formula 5", 380.0, 430.0, 200.0, 60.0, 0.0, 1.0, false, null,
                        "N_{(10)}=\\sum_{i=0}^{n-1} c_i \\cdot 2^i", "#000000", 24.0, Poravnanje.CENTAR, null),
                new CrtezSloj("crtez", "Crtež 6", 0.0, 0.0, 960.0, 540.0, 0.0, 1.0, false, null, CRTEZ,
                        List.of(new Potez("#FF0000", 2.0, tacke(1_200)), new Potez("#0000FF", 3.0, tacke(800)))));
        List<Animacija> animacije = List.of(
                new Animacija("a1", "tekst", null, VrstaAnimacije.ULAZ, Efekat.FADE, null, Okidac.KLIK, 500, 0),
                new Animacija("a2", "slika", null, VrstaAnimacije.ULAZ, Efekat.FADE, null, Okidac.SA_PRETHODNIM, 500, 0),
                new Animacija("a3", "oblik", null, VrstaAnimacije.ULAZ, Efekat.ULET, Smer.DOLE, Okidac.KLIK, 500, 0));
        return new Platno(1, new Pozadina("#FFFFFF", null), slojevi, animacije);
    }

    static SlajdDetails platnoSlajd(long id, int rb, Platno platno) {
        return new SlajdDetails(id, rb, TipSlajda.PLATNO, null, null, null, null, false, null, platno, null);
    }

    static List<SlajdDetails> slajdovi(int n, int pasusaTeksta) {
        List<SlajdDetails> s = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            s.add(platnoSlajd(1000L + i, i, platno(pasusaTeksta)));
        }
        return s;
    }

    static PrezentacijaDetails prezentacija(List<SlajdDetails> slajdovi) {
        return new PrezentacijaDetails(7L, "Vežbe 3: brojni sistemi", "Konverzije između sistema.",
                new PredmetKratko(1L, "Uvod u računarstvo"), slajdovi.size(), 0, LocalDateTime.of(2026, 10, 18, 9, 0),
                false, TelefonPrikaz.DUGMAD, true, 0, null, Tema.NACRT, slajdovi);
    }

    static int duzina(Object o) {
        return JSON.writeValueAsString(o).length();
    }

    static List<McpPrezentacijaPregled> sveStrane(PrezentacijaDetails p) {
        McpPrezentacijaPregled prva = PrezentacijaPregled.pregled(p, 1, LINK);
        List<McpPrezentacijaPregled> strane = new ArrayList<>(List.of(prva));
        for (int s = 2; s <= prva.brojStrana(); s++) {
            strane.add(PrezentacijaPregled.pregled(p, s, LINK));
        }
        return strane;
    }

    // ---------------------------------------------------------------- strane

    /**
     * Mera 2026-10-19: 24 slajda daju ~25 900 znakova prema granici od 26 000, oko 1 080 po slajdu (rezerva ~100 znakova).
     * Svako novo ne-null polje sažetka slajda (B, A2: npr. {@code verzija}) traži novu meru: ili kraće polje ili
     * svesno pomeranje ovog testa (24 slajda na dve strane).
     */
    @Test
    void sestVezbiStajeUJednuStranuIspodGranice() {
        for (int n : VEZBE) {
            McpPrezentacijaPregled pregled = PrezentacijaPregled.pregled(prezentacija(slajdovi(n, 1)), 1, LINK);
            assertEquals(1, pregled.brojStrana(), n + " slajdova: " + duzina(pregled) + " znakova, granica " + GRANICA
                    + " (oko " + GRANICA / 24 + " po slajdu za 24)");
            assertEquals(n, pregled.slajdovi().size(), n + " slajdova");
            assertEquals(n, pregled.brojSlajdova());
            assertTrue(duzina(pregled) <= GRANICA, n + " slajdova: " + duzina(pregled) + " znakova");
        }
    }

    @Test
    void teziSlajdoviNajviseUDveStraneSvakaIspodMaksimuma() {
        for (int n : VEZBE) {
            List<McpPrezentacijaPregled> strane = sveStrane(prezentacija(slajdovi(n, 3)));
            if (n <= 18) {
                assertEquals(1, strane.size(), n + " slajdova");
            } else {
                assertTrue(strane.size() <= 2, n + " slajdova: " + strane.size() + " strana");
            }
            for (McpPrezentacijaPregled s : strane) {
                assertTrue(duzina(s) < McpOdgovor.MAX_ZNAKOVA, n + " slajdova, strana " + s.strana() + ": " + duzina(s));
                assertEquals(strane.size(), s.brojStrana());
            }
        }
    }

    @Test
    void osamdesetSlajdovaUViseStranaRedomBezPonavljanja() {
        PrezentacijaDetails p = prezentacija(slajdovi(80, 1));
        List<McpPrezentacijaPregled> strane = sveStrane(p);
        assertTrue(strane.size() >= 3, "strana: " + strane.size());
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < strane.size(); i++) {
            McpPrezentacijaPregled s = strane.get(i);
            assertEquals(i + 1, s.strana());
            assertTrue(s.slajdovi().size() <= PrezentacijaPregled.MAX_SLAJDOVA_PO_STRANI);
            assertFalse(s.slajdovi().isEmpty());
            assertTrue(duzina(s) <= GRANICA, "strana " + s.strana() + ": " + duzina(s));
            assertEquals(80, s.brojSlajdova());
            s.slajdovi().forEach(sl -> ids.add(sl.id()));
        }
        assertEquals(p.slajdovi().stream().map(SlajdDetails::id).toList(), ids);
    }

    @Test
    void istiUlazIstaPodela() {
        PrezentacijaDetails p = prezentacija(slajdovi(80, 3));
        List<List<Long>> prva = sveStrane(p).stream().map(s -> s.slajdovi().stream().map(McpSlajdSazetak::id).toList())
                .toList();
        List<List<Long>> druga = sveStrane(p).stream().map(s -> s.slajdovi().stream().map(McpSlajdSazetak::id).toList())
                .toList();
        assertEquals(prva, druga);
    }

    @Test
    void stranaVanOpsegaJe400() {
        PrezentacijaDetails p = prezentacija(slajdovi(3, 1));
        for (int strana : List.of(0, -1, 2)) {
            SystemException e = assertThrows(SystemException.class, () -> PrezentacijaPregled.pregled(p, strana, LINK));
            assertEquals(400, e.getCode());
            assertEquals("Neispravan parametar: strana.", e.getMessage());
        }
    }

    @Test
    void praznaPrezentacijaImaJednuPraznuStranu() {
        McpPrezentacijaPregled pregled = PrezentacijaPregled.pregled(prezentacija(List.of()), 1, LINK);
        assertEquals(1, pregled.brojStrana());
        assertEquals(List.of(), pregled.slajdovi());
        assertEquals(0, pregled.brojSlajdova());
        assertEquals(LINK, pregled.link());
        assertEquals("Vežbe 3: brojni sistemi", pregled.naziv());
        assertEquals(Tema.NACRT, pregled.tema());
    }

    // ---------------------------------------------------------------- sažetak slajda

    @Test
    void jsonNemaPotezaACrtezImaBrojTacaka() {
        McpPrezentacijaPregled pregled = PrezentacijaPregled.pregled(prezentacija(slajdovi(2, 1)), 1, LINK);
        String json = JSON.writeValueAsString(pregled);
        assertFalse(json.contains("potezi"), json);
        assertFalse(json.contains("tacke"), json);
        JsonNode crtez = JSON.readTree(json).path("slajdovi").path(0).path("slojevi").path(5);
        assertEquals("CRTEZ", crtez.path("tip").asString());
        assertEquals(2000, crtez.path("brojTacaka").asInt());
        assertEquals(CRTEZ, crtez.path("medijId").asString());
    }

    @Test
    void platnoSlojeviUZReduSaOkviromTekstomIPoljimaPoTipu() {
        McpSlajdSazetak s = PrezentacijaPregled.sazetak(platnoSlajd(1L, 1, platno(2)));
        assertEquals(TipSlajda.PLATNO, s.tip());
        assertEquals(2, s.brojKoraka());
        assertNull(s.naslov());
        assertNull(s.pitanje());
        List<McpSlojSazetak> slojevi = s.slojevi();
        assertEquals(List.of("naslov", "tekst", "slika", "oblik", "formula", "crtez"),
                slojevi.stream().map(McpSlojSazetak::id).toList());
        assertEquals(List.of("TEKST", "TEKST", "SLIKA", "OBLIK", "FORMULA", "CRTEZ"),
                slojevi.stream().map(McpSlojSazetak::tip).toList());
        McpSlojSazetak naslov = slojevi.get(0);
        assertEquals("Naslov 1", naslov.naziv());
        assertArrayEquals(new double[]{36.25, 28.5, 887.5, 120.75}, naslov.okvir());
        assertEquals(tekst(60), naslov.tekst());
        assertEquals("Carlito", naslov.font());
        assertEquals(36.0, naslov.velicina());
        assertEquals(tekst(150) + "\n" + tekst(150), slojevi.get(1).tekst());
        assertEquals(SLIKA, slojevi.get(2).medijId());
        assertEquals("PRAVOUGAONIK", slojevi.get(3).oblik());
        assertEquals("N_{(10)}=\\sum_{i=0}^{n-1} c_i \\cdot 2^i", slojevi.get(4).latex());
        assertNull(slojevi.get(4).tekst());
        assertEquals(2000, slojevi.get(5).brojTacaka());
    }

    @Test
    void tekstSlojaSaDvaFontaVracaOnajSaViseZnakova() {
        TekstSloj t = tekstSloj("t1", null, 10.0, List.of(
                pasus(deo("Kratko ", "Arimo", 28.0), deo("ovo je duži deo u drugom fontu", "Tinos", 18.0)),
                pasus(deo("i još", "Arimo", 28.0))));
        McpSlojSazetak s = PlatnoPregled.slojevi(new Platno(1, new Pozadina("#FFFFFF", null), List.of(t), List.of()))
                .getFirst();
        assertEquals("Kratko ovo je duži deo u drugom fontu\ni još", s.tekst());
        assertEquals("Tinos", s.font());
        assertEquals(18.0, s.velicina());
        assertNull(s.naziv());
    }

    @Test
    void tabela2x2JeMrezaTeksta() {
        McpSlojSazetak tabela = PlatnoPregled.slojevi(tri.novica.gfssystem.service.uzivo.PlatnoTestPodaci.ispravno())
                .stream().filter(s -> "TABELA".equals(s.tip())).findFirst().orElseThrow();
        assertEquals(List.of(List.of("A", "B"), List.of("C", "D")), tabela.tabela());
        assertNull(tabela.tekst());
    }

    @Test
    void dugackiTekstSlojaSeSkracuje() {
        TekstSloj t = tekstSloj("t1", null, 10.0, List.of(pasus(deo(tekst(3_000), "Carlito", 18.0))));
        McpSlojSazetak s = PlatnoPregled.slojevi(new Platno(1, new Pozadina("#FFFFFF", null), List.of(t), List.of()))
                .getFirst();
        assertEquals(PlatnoPregled.MAX_TEKST_SLOJA, s.tekst().codePointCount(0, s.tekst().length()));
        assertTrue(s.tekst().endsWith("…"));
        assertTrue(s.tekst().startsWith(tekst(900)));
    }

    @Test
    void infoSadrzajIBeleskeSeSkracujuAKoraciSuStavkeListe() {
        String sadrzaj = "- prva\n- druga\n- treća\n" + tekst(3_000);
        SlajdDetails info = new SlajdDetails(5L, 3, TipSlajda.INFO, "Pozicioni sistemi", sadrzaj,
                new MedijInfo(SLIKA, "a.png", "image/png", 10), tekst(500), true, null, null,
                new Prelaz(TipPrelaza.GURANJE, SmerPrelaza.LEVO, 400));
        McpSlajdSazetak s = PrezentacijaPregled.sazetak(info);
        assertEquals(5L, s.id());
        assertEquals(3, s.rb());
        assertEquals("Pozicioni sistemi", s.naslov());
        assertEquals(PrezentacijaPregled.MAX_SADRZAJ, s.sadrzaj().length());
        assertTrue(s.sadrzaj().startsWith("- prva\n- druga\n- treća\n"));
        assertTrue(s.sadrzaj().endsWith("…"));
        assertEquals(PrezentacijaPregled.MAX_BELESKE, s.beleske().length());
        assertEquals(3, s.brojKoraka());
        assertEquals(new Prelaz(TipPrelaza.GURANJE, SmerPrelaza.LEVO, 400), s.prelaz());
        assertNull(s.slojevi());
        assertNull(s.pitanje());

        McpSlajdSazetak bezPostepeno = PrezentacijaPregled.sazetak(new SlajdDetails(6L, 4, TipSlajda.INFO, "N", sadrzaj,
                null, null, false, null, null, null));
        assertEquals(0, bezPostepeno.brojKoraka());
        assertNull(bezPostepeno.beleske());
        assertFalse(JSON.writeValueAsString(bezPostepeno).contains("\"beleske\""));
    }

    @Test
    void kratakTekstOstajeCeo() {
        SlajdDetails info = new SlajdDetails(5L, 1, TipSlajda.INFO, null, "Kratko.", null, "Beleška.", false, null, null,
                null);
        McpSlajdSazetak s = PrezentacijaPregled.sazetak(info);
        assertEquals("Kratko.", s.sadrzaj());
        assertEquals("Beleška.", s.beleske());
    }

    @Test
    void pitanjeImaTipTekstIOpcijeSaTacnim() {
        PitanjeDetails pitanje = new PitanjeDetails(9L, TipPitanja.JEDAN_TACAN, "Koliko je $1011_{(2)}$?", null, 30,
                List.of(new OpcijaDetails(1L, 1, "9", false), new OpcijaDetails(2L, 2, "11", true)),
                null, null, null, null, null, null, null, null);
        McpSlajdSazetak s = PrezentacijaPregled.sazetak(new SlajdDetails(8L, 2, TipSlajda.PITANJE, null, null, null, null,
                false, pitanje, null, null));
        assertEquals(TipSlajda.PITANJE, s.tip());
        assertEquals(0, s.brojKoraka());
        assertEquals(TipPitanja.JEDAN_TACAN, s.pitanje().tip());
        assertEquals("Koliko je $1011_{(2)}$?", s.pitanje().tekst());
        assertEquals(List.of("9", "11"), s.pitanje().opcije().stream().map(o -> o.tekst()).toList());
        assertEquals(List.of(false, true), s.pitanje().opcije().stream().map(o -> o.tacna()).toList());
        assertNull(s.naslov());

        PitanjeDetails broj = new PitanjeDetails(10L, TipPitanja.BROJ, "Koliko?", null, null, List.of(), 11.0, 0.0,
                null, null, null, null, null, null);
        McpPitanjeSazetak b = PrezentacijaPregled.sazetak(new SlajdDetails(9L, 3, TipSlajda.PITANJE, null, null, null,
                null, false, broj, null, null)).pitanje();
        assertEquals(11.0, b.brojTacno());
        assertNull(b.opcije());
        PitanjeDetails kratak = new PitanjeDetails(11L, TipPitanja.KRATAK_TEKST, "Ime?", null, null, List.of(), null,
                null, null, null, null, List.of("bit", "bajt"), null, null);
        assertEquals(List.of("bit", "bajt"), PrezentacijaPregled.sazetak(new SlajdDetails(10L, 4, TipSlajda.PITANJE,
                null, null, null, null, false, kratak, null, null)).pitanje().prihvatljiviOdgovori());
    }

    // ---------------------------------------------------------------- slajd bez poteza

    @Test
    void brojTacakaPoCrtezuIPlatnoBezPoteza() {
        Platno p = platno(1);
        assertEquals(Map.of("crtez", 2000), PlatnoPregled.brojTacaka(p));
        Platno bez = PlatnoPregled.bezPoteza(p);
        CrtezSloj c = (CrtezSloj) bez.slojevi().get(5);
        assertNull(c.potezi());
        assertEquals(CRTEZ, c.medijId());
        assertEquals(p.slojevi().subList(0, 5), bez.slojevi().subList(0, 5));
        assertEquals(p.animacije(), bez.animacije());
        assertNull(PlatnoPregled.bezPoteza(null));
        assertEquals(Map.of(), PlatnoPregled.brojTacaka(null));
    }

    @Test
    void crtezBezPotezaImaNulaTacaka() {
        CrtezSloj c = new CrtezSloj("c1", null, 0.0, 0.0, 960.0, 540.0, 0.0, 1.0, false, null, CRTEZ, null);
        Platno p = new Platno(1, new Pozadina("#FFFFFF", null), List.of(c), List.of());
        assertEquals(Map.of("c1", 0), PlatnoPregled.brojTacaka(p));
        assertEquals(0, PlatnoPregled.slojevi(p).getFirst().brojTacaka());
    }

    @Test
    void slajdBezPotezaCuvaSvaOstalaPolja() {
        SlajdDetails s = new SlajdDetails(3L, 2, TipSlajda.PLATNO, "N", "S", new MedijInfo(SLIKA, "a", "image/png", 1),
                "B", true, null, platno(1), new Prelaz(TipPrelaza.FADE, null, 500));
        SlajdDetails bez = PrezentacijaPregled.bezPoteza(s);
        assertEquals(new SlajdDetails(3L, 2, TipSlajda.PLATNO, "N", "S", new MedijInfo(SLIKA, "a", "image/png", 1), "B",
                true, null, PlatnoPregled.bezPoteza(platno(1)), new Prelaz(TipPrelaza.FADE, null, 500)), bez);
        ZadatakSlajdaInfo zadatak = new ZadatakSlajdaInfo(3L, 120, 4, "2 → 10", List.of(), true, 3, List.of(), false,
                List.of(), List.of());
        assertEquals(zadatak, PrezentacijaPregled.bezPoteza(new SlajdDetails(3L, 2, TipSlajda.PLATNO, null, null, null,
                null, false, null, platno(1), null, zadatak)).zadatak(), "zadatak ostaje i kad se potezi brišu");
        SlajdDetails info = new SlajdDetails(4L, 1, TipSlajda.INFO, "N", null, null, null, false, null, null, null);
        assertSame(info, PrezentacijaPregled.bezPoteza(info));
        // nova komponenta (A2: verzija) mora ući i u kopiju bez poteza
        assertEquals(12, SlajdDetails.class.getRecordComponents().length,
                "SlajdDetails ima novu komponentu: dopuni PrezentacijaPregled.bezPoteza i ovaj test");
    }

    @Test
    void slojeviSuNepromenljiviIPrazniBezPlatna() {
        assertEquals(List.of(), PlatnoPregled.slojevi(null));
        assertEquals(List.of(), PlatnoPregled.slojevi(new Platno(1, new Pozadina("#FFFFFF", null), null, null)));
        List<McpSlojSazetak> s = PlatnoPregled.slojevi(platno(1));
        assertThrows(UnsupportedOperationException.class, () -> Collections.addAll(s, s.getFirst()));
    }

    @Test
    void zadatakSlajdImaSazetakBezSvg() {
        ZadatakSlajdaInfo z = new ZadatakSlajdaInfo(3L, 120, 4, "2 → 10",
                List.of(new GrupaPutanja(1L, "Konverzije"), new GrupaPutanja(3L, "2 → 10")), true, 6, List.of(), false,
                List.of(), List.of("Grupa nema aktivnih varijanti; odobri nacrte u banci pre časa."));
        SlajdDetails s = new SlajdDetails(9L, 1, TipSlajda.ZADATAK, null, null, null, null, false, null, null, null, z);
        McpSlajdSazetak m = PrezentacijaPregled.sazetak(s);
        assertEquals(0, m.brojKoraka());
        assertEquals(3L, m.zadatak().grupaId());
        assertEquals(6, m.zadatak().brojAktivnih());
        assertEquals(4, m.zadatak().brojVarijanti());
        assertEquals(120, m.zadatak().vremeSekunde());
        assertEquals("2 → 10", m.zadatak().putanja().getLast().naziv());
        String json = JSON.writeValueAsString(m);
        assertFalse(json.toLowerCase().contains("svg"), json);
        assertFalse(json.contains("grupaNaziv"), json);
        assertTrue(json.contains("odobri nacrte"), json);
        assertNull(PrezentacijaPregled.sazetak(platnoSlajd(1L, 1, platno(1))).zadatak());
    }
}
