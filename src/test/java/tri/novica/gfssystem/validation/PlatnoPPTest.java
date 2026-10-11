package tri.novica.gfssystem.validation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tri.novica.gfssystem.dto.uzivo.platno.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.uzivo.PlatnoJson;
import tri.novica.gfssystem.service.uzivo.PlatnoTestPodaci;

import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pravila platna, po jedan test za red tabele iz plana: vrednost tačno na granici prolazi, jedna preko pada sa tačnom
 * porukom. Dokumenti se grade kao JSON (ispravan dokument + izmena), pa prolaze isti put kao zahtev: strogi parser,
 * normalizacija, pravila.
 */
class PlatnoPPTest {

    static final JsonMapper MAPPER = JsonMapper.builder().build();
    static final PlatnoJson JSON = new PlatnoJson();
    static final String BOJA = "Boja nije ispravna (#RRGGBB).";

    // slojevi iz PlatnoTestPodaci.ispravno(): 0 t1 TEKST, 1 s1 SLIKA, 2 o1 OBLIK, 3 l1 LINIJA, 4 tb TABELA, 5 f1 FORMULA, 6 c1 CRTEZ

    static ObjectNode dokument() {
        return (ObjectNode) PlatnoTestPodaci.cvor(PlatnoTestPodaci.ispravno());
    }

    static ObjectNode sloj(ObjectNode d, int i) {
        return (ObjectNode) d.get("slojevi").get(i);
    }

    static ObjectNode animacija(ObjectNode d, int i) {
        return (ObjectNode) d.get("animacije").get(i);
    }

    static ObjectNode pasus(ObjectNode d, int i) {
        return (ObjectNode) sloj(d, 0).get("pasusi").get(i);
    }

    static ObjectNode deo(ObjectNode d) {
        return (ObjectNode) pasus(d, 0).get("delovi").get(0);
    }

    static Platno normalizovan(JsonNode d) {
        return PlatnoPP.normalizuj(JSON.procitaj(d));
    }

    static void proveri(JsonNode d) {
        PlatnoPP.proveri(normalizovan(d));
    }

    static void ispravno(Consumer<ObjectNode> izmena) {
        ObjectNode d = dokument();
        izmena.accept(d);
        assertDoesNotThrow(() -> proveri(d));
    }

    static void greska(String poruka, Consumer<ObjectNode> izmena) {
        ObjectNode d = dokument();
        izmena.accept(d);
        SystemException e = assertThrows(SystemException.class, () -> proveri(d));
        assertEquals(poruka, e.getMessage());
        assertEquals(400, e.getCode());
    }

    static String znakova(int n) {
        return "a".repeat(n);
    }

    // --- ispravan dokument ---

    @Test
    void ispravanDokumentProlaziINormalizacijaGaNeMenja() {
        Platno p = PlatnoTestPodaci.ispravno();
        assertDoesNotThrow(() -> PlatnoPP.proveri(p));
        assertEquals(p, PlatnoPP.normalizuj(p));
    }

    @Test
    void nullDokument() {
        SystemException e = assertThrows(SystemException.class, () -> PlatnoPP.proveri(null));
        assertEquals("Platno slajd mora imati dokument platna.", e.getMessage());
        assertNull(PlatnoPP.normalizuj(null));
    }

    @Test
    void verzija() {
        greska("Nepodržana verzija platna.", d -> d.put("verzija", 2));
        greska("Nepodržana verzija platna.", d -> d.putNull("verzija"));
    }

    // --- boje ---

    @Test
    void bojaPozadineIOstale() {
        ispravno(d -> ((ObjectNode) d.get("pozadina")).put("boja", "#a1b2c3"));
        greska(BOJA, d -> ((ObjectNode) d.get("pozadina")).put("boja", "#12345"));
        greska(BOJA, d -> d.putNull("pozadina"));
        greska(BOJA, d -> sloj(d, 2).put("popuna", "crvena"));
        greska(BOJA, d -> deo(d).put("boja", "#GGGGGG"));
        greska(BOJA, d -> sloj(d, 5).put("boja", "#FFF"));
        greska(BOJA, d -> ((ObjectNode) sloj(d, 6).get("potezi").get(0)).put("boja", "red"));
        greska(BOJA, d -> sloj(d, 0).set("senka", MAPPER.createObjectNode().put("boja", "x").put("pomerajX", 1)
                .put("pomerajY", 1).put("zamucenje", 1)));
    }

    // --- granice broja ---

    @Test
    void najviseSlojeva() {
        ispravno(d -> {
            ArrayNode s = (ArrayNode) d.get("slojevi");
            d.set("animacije", MAPPER.createArrayNode());
            while (s.size() < 300) {
                s.add(sloj(d, 3).deepCopy().put("id", "x" + s.size()));
            }
        });
        greska("Slajd može imati najviše 300 slojeva.", d -> {
            ArrayNode s = (ArrayNode) d.get("slojevi");
            while (s.size() < 301) {
                s.add(sloj(d, 3).deepCopy().put("id", "x" + s.size()));
            }
        });
    }

    @Test
    void najviseAnimacija() {
        ispravno(d -> {
            ArrayNode a = (ArrayNode) d.get("animacije");
            while (a.size() < 400) {
                a.add(animacija(d, 0).deepCopy().put("id", "n" + a.size()));
            }
        });
        greska("Slajd može imati najviše 400 animacija.", d -> {
            ArrayNode a = (ArrayNode) d.get("animacije");
            while (a.size() < 401) {
                a.add(animacija(d, 0).deepCopy().put("id", "n" + a.size()));
            }
        });
    }

    // --- id, naziv ---

    @Test
    void idSlojaIAnimacije() {
        String poruka = "Id sloja ili animacije nije ispravan.";
        ispravno(d -> sloj(d, 3).put("id", "abcdefghijkl"));
        ispravno(d -> sloj(d, 3).put("id", "A_-9"));
        greska(poruka, d -> sloj(d, 0).put("id", "abcdefghijklm"));
        greska(poruka, d -> sloj(d, 0).put("id", ""));
        greska(poruka, d -> sloj(d, 0).put("id", "a b"));
        greska(poruka, d -> sloj(d, 0).putNull("id"));
        greska(poruka, d -> animacija(d, 0).put("id", "č"));
        greska(poruka, d -> ((ArrayNode) d.get("slojevi")).addNull());
    }

    @Test
    void duplikatId() {
        String poruka = "Id-jevi slojeva i animacija moraju biti jedinstveni.";
        greska(poruka, d -> sloj(d, 1).put("id", "t1"));
        greska(poruka, d -> animacija(d, 1).put("id", "a1"));
        greska(poruka, d -> animacija(d, 1).put("id", "t1"));
    }

    @Test
    void nazivSloja() {
        ispravno(d -> sloj(d, 0).put("naziv", znakova(60)));
        greska("Naziv sloja može imati najviše 60 znakova.", d -> sloj(d, 0).put("naziv", znakova(61)));
    }

    @Test
    void polozajIVelicina() {
        String poruka = "Položaj ili veličina sloja nije ispravna.";
        ispravno(d -> sloj(d, 0).put("x", -960));
        ispravno(d -> sloj(d, 0).put("x", 1920));
        ispravno(d -> sloj(d, 0).put("y", 1920));
        ispravno(d -> sloj(d, 0).put("sirina", 2880));
        ispravno(d -> sloj(d, 0).put("visina", 0.01));
        greska(poruka, d -> sloj(d, 0).put("x", -960.01));
        greska(poruka, d -> sloj(d, 0).put("x", 1920.01));
        greska(poruka, d -> sloj(d, 0).put("y", -961));
        greska(poruka, d -> sloj(d, 0).put("y", 1921));
        greska(poruka, d -> sloj(d, 0).put("sirina", 2880.01));
        greska(poruka, d -> sloj(d, 0).put("sirina", 0));
        greska(poruka, d -> sloj(d, 0).put("visina", -1));
        greska(poruka, d -> sloj(d, 0).putNull("visina"));
    }

    @Test
    void rotacijaIProvidnost() {
        String poruka = "Rotacija ili providnost sloja nije ispravna.";
        ispravno(d -> sloj(d, 0).put("rotacija", -360));
        ispravno(d -> sloj(d, 0).put("rotacija", 360));
        ispravno(d -> sloj(d, 0).put("providnost", 0));
        ispravno(d -> sloj(d, 0).put("providnost", 1));
        greska(poruka, d -> sloj(d, 0).put("rotacija", 360.01));
        greska(poruka, d -> sloj(d, 0).put("rotacija", -361));
        greska(poruka, d -> sloj(d, 0).put("providnost", 1.01));
        greska(poruka, d -> sloj(d, 0).put("providnost", -0.01));
    }

    @Test
    void senka() {
        String poruka = "Senka nije ispravna.";
        ispravno(d -> sloj(d, 0).set("senka", senka(100, -100, 100)));
        ispravno(d -> sloj(d, 0).set("senka", senka(-100, 100, 0)));
        greska(poruka, d -> sloj(d, 0).set("senka", senka(100.01, 0, 0)));
        greska(poruka, d -> sloj(d, 0).set("senka", senka(0, -100.01, 0)));
        greska(poruka, d -> sloj(d, 0).set("senka", senka(0, 0, 100.01)));
        greska(poruka, d -> sloj(d, 0).set("senka", senka(0, 0, -0.01)));
    }

    static ObjectNode senka(double x, double y, double zamucenje) {
        return MAPPER.createObjectNode().put("boja", "#000000").put("pomerajX", x).put("pomerajY", y)
                .put("zamucenje", zamucenje);
    }

    // --- tekst ---

    @Test
    void okvirTeksta() {
        String poruka = "Okvir teksta nije ispravan.";
        ObjectNode okvir = (ObjectNode) sloj(dokument(), 0).get("okvir");
        assertNotNull(okvir);
        for (String polje : new String[]{"unutraLevo", "unutraGore", "unutraDesno", "unutraDole"}) {
            ispravno(d -> ((ObjectNode) sloj(d, 0).get("okvir")).put(polje, 0));
            ispravno(d -> ((ObjectNode) sloj(d, 0).get("okvir")).put(polje, 200));
            greska(poruka, d -> ((ObjectNode) sloj(d, 0).get("okvir")).put(polje, 200.01));
            greska(poruka, d -> ((ObjectNode) sloj(d, 0).get("okvir")).put(polje, -0.01));
        }
        ispravno(d -> ((ObjectNode) sloj(d, 0).get("okvir")).put("skala", 0.25));
        greska(poruka, d -> ((ObjectNode) sloj(d, 0).get("okvir")).put("skala", 0.24));
        greska(poruka, d -> ((ObjectNode) sloj(d, 0).get("okvir")).put("skala", 1.01));
        ispravno(d -> ((ObjectNode) sloj(d, 0).get("okvir")).put("skalaProreda", 0.5));
        greska(poruka, d -> ((ObjectNode) sloj(d, 0).get("okvir")).put("skalaProreda", 0.49));
        greska(poruka, d -> ((ObjectNode) sloj(d, 0).get("okvir")).put("skalaProreda", 1.01));
    }

    @Test
    void pasus() {
        String poruka = "Pasus nije ispravan.";
        ispravno(d -> pasus(d, 0).put("nivo", 0));
        ispravno(d -> pasus(d, 0).put("nivo", 8));
        greska(poruka, d -> pasus(d, 0).put("nivo", 9));
        greska(poruka, d -> pasus(d, 0).put("nivo", -1));
        ispravno(d -> pasus(d, 0).put("proredProcenat", 50));
        ispravno(d -> pasus(d, 0).put("proredProcenat", 300));
        greska(poruka, d -> pasus(d, 0).put("proredProcenat", 49.99));
        greska(poruka, d -> pasus(d, 0).put("proredProcenat", 300.01));
        ispravno(d -> pasus(d, 0).put("razmakPre", 1000));
        ispravno(d -> pasus(d, 0).put("razmakPosle", 0));
        greska(poruka, d -> pasus(d, 0).put("razmakPre", 1000.01));
        greska(poruka, d -> pasus(d, 0).put("razmakPosle", -0.01));
        ispravno(d -> ((ObjectNode) pasus(d, 0).get("uvlacenje")).put("levo", -1000).put("prviRed", 1000));
        greska(poruka, d -> ((ObjectNode) pasus(d, 0).get("uvlacenje")).put("levo", -1000.01));
        greska(poruka, d -> ((ObjectNode) pasus(d, 0).get("uvlacenje")).put("prviRed", 1000.01));
    }

    @Test
    void tabulatori() {
        String poruka = "Pasus nije ispravan.";
        ispravno(d -> tabulatori(d, 32, 2880));
        ispravno(d -> tabulatori(d, 1, 0));
        greska(poruka, d -> tabulatori(d, 33, 10));
        greska(poruka, d -> tabulatori(d, 1, 2880.01));
        greska(poruka, d -> tabulatori(d, 1, -0.01));
    }

    static void tabulatori(ObjectNode d, int broj, double poz) {
        ArrayNode t = MAPPER.createArrayNode();
        for (int i = 0; i < broj; i++) {
            t.add(MAPPER.createObjectNode().put("poz", poz).put("poravnanje", "LEVO"));
        }
        pasus(d, 0).set("tabulatori", t);
    }

    @Test
    void lista() {
        String poruka = "Lista nije ispravna.";
        ispravno(d -> pasus(d, 0).set("lista", lista("ZNAK", "••", null, null)));
        ispravno(d -> pasus(d, 0).set("lista", lista("BROJ", null, "ARAPSKI_TACKA", 9999)));
        ispravno(d -> pasus(d, 0).set("lista", lista("BROJ", null, "RIMSKI_MALI_TACKA", 1)));
        greska(poruka, d -> pasus(d, 0).set("lista", lista("ZNAK", null, null, null)));
        greska(poruka, d -> pasus(d, 0).set("lista", lista("ZNAK", "", null, null)));
        greska(poruka, d -> pasus(d, 0).set("lista", lista("ZNAK", "abc", null, null)));
        greska(poruka, d -> pasus(d, 0).set("lista", lista("BROJ", null, null, 1)));
        greska(poruka, d -> pasus(d, 0).set("lista", lista("BROJ", null, "ARAPSKI_TACKA", 0)));
        greska(poruka, d -> pasus(d, 0).set("lista", lista("BROJ", null, "ARAPSKI_TACKA", 10_000)));
    }

    static ObjectNode lista(String vrsta, String znak, String format, Integer pocetak) {
        ObjectNode l = MAPPER.createObjectNode().put("vrsta", vrsta);
        if (znak != null) l.put("znak", znak);
        if (format != null) l.put("format", format);
        if (pocetak != null) l.put("pocetak", pocetak);
        return l;
    }

    @Test
    void deoFontIVelicina() {
        for (String font : PlatnoPP.FONTOVI) {
            ispravno(d -> deo(d).put("font", font));
        }
        assertEquals(8, PlatnoPP.FONTOVI.size());
        greska("Font nije podržan: Comic Sans.", d -> deo(d).put("font", "Comic Sans"));
        ispravno(d -> deo(d).put("velicina", 1));
        ispravno(d -> deo(d).put("velicina", 400));
        greska("Veličina slova mora biti od 1 do 400.", d -> deo(d).put("velicina", 0.99));
        greska("Veličina slova mora biti od 1 do 400.", d -> deo(d).put("velicina", 400.01));
    }

    @Test
    void ukupnoZnakovaTeksta() {
        String poruka = "Slajd može imati najviše 50000 znakova teksta.";
        // dva pasusa po 25 000 (TEKST), ostali delovi prazni; ćelije i formula se broje isto
        Consumer<ObjectNode> pun = d -> {
            deo(d).put("tekst", znakova(25_000));
            ((ObjectNode) pasus(d, 1).get("delovi").get(0)).put("tekst", znakova(25_000));
            prazniCelijeIFormulu(d);
        };
        ispravno(pun);
        greska(poruka, pun.andThen(d -> ((ObjectNode) pasus(d, 1).get("delovi").get(0)).put("tekst", znakova(25_001))));
        // ćelije tabele se broje
        greska(poruka, pun.andThen(d -> ((ObjectNode) ((ObjectNode) sloj(d, 4).get("celije").get(0).get(0))
                .get("pasusi").get(0).get("delovi").get(0)).put("tekst", "x")));
        // formula se broji
        greska(poruka, pun.andThen(d -> sloj(d, 5).put("latex", "xx")));
    }

    static void prazniCelijeIFormulu(ObjectNode d) {
        for (JsonNode red : sloj(d, 4).get("celije")) {
            for (JsonNode c : red) {
                ((ObjectNode) c.get("pasusi").get(0).get("delovi").get(0)).put("tekst", "");
            }
        }
        sloj(d, 5).put("latex", "x");
        // formula je 1 znak: smanji prvi pasus da zbir bude tačno 50 000
        ((ObjectNode) pasus(d, 1).get("delovi").get(0)).put("tekst", znakova(24_999));
    }

    // --- ivica ---

    @Test
    void debljinaIvice() {
        String poruka = "Debljina ivice mora biti od 0,25 do 20.";
        ispravno(d -> ((ObjectNode) sloj(d, 2).get("ivica")).put("debljina", 0.25));
        ispravno(d -> ((ObjectNode) sloj(d, 2).get("ivica")).put("debljina", 20));
        greska(poruka, d -> ((ObjectNode) sloj(d, 2).get("ivica")).put("debljina", 0.24));
        greska(poruka, d -> ((ObjectNode) sloj(d, 2).get("ivica")).put("debljina", 20.01));
        greska(poruka, d -> ((ObjectNode) sloj(d, 2).get("ivica")).putNull("debljina"));
    }

    // --- slika, isečak ---

    @Test
    void slikaIsecak() {
        greska("Sloj slike mora imati sliku.", d -> sloj(d, 1).putNull("medijId"));
        greska("Sloj slike mora imati sliku.", d -> sloj(d, 6).putNull("medijId"));
        String poruka = "Isečak slike nije ispravan.";
        ispravno(d -> sloj(d, 1).set("isecak", isecak(0.5, 0.5, 0.49, 0.49)));
        ispravno(d -> sloj(d, 1).set("isecak", isecak(0, 0, 0, 0.99)));
        greska(poruka, d -> sloj(d, 1).set("isecak", isecak(0.5, 0, 0.5, 0)));
        greska(poruka, d -> sloj(d, 1).set("isecak", isecak(0, 0.6, 0, 0.4)));
        greska(poruka, d -> sloj(d, 1).set("isecak", isecak(-0.01, 0, 0, 0)));
        greska(poruka, d -> sloj(d, 1).set("isecak", isecak(0, 1.01, 0, 0)));
    }

    static ObjectNode isecak(double levo, double gore, double desno, double dole) {
        return MAPPER.createObjectNode().put("levo", levo).put("gore", gore).put("desno", desno).put("dole", dole);
    }

    // --- oblik ---

    @Test
    void linijaImaPocetakIKraj() {
        String poruka = "Linija mora imati početak i kraj.";
        // bez od/do normalizacija daje podrazumevane {0, visina/2} -> {sirina, visina/2}
        ispravno(d -> {
            sloj(d, 3).putNull("od");
            sloj(d, 3).putNull("do");
        });
        ispravno(d -> sloj(d, 3).set("od", tacka(-2880, 2880)));
        ispravno(d -> sloj(d, 3).set("do", tacka(2880, -2880)));
        greska(poruka, d -> sloj(d, 3).set("od", tacka(-2880.01, 0)));
        greska(poruka, d -> sloj(d, 3).set("do", tacka(0, 2880.01)));
    }

    static ObjectNode tacka(double x, double y) {
        return MAPPER.createObjectNode().put("x", x).put("y", y);
    }

    // --- tabela ---

    @Test
    void tabela() {
        String poruka = "Tabela nije ispravna.";
        greska(poruka, d -> sloj(d, 4).set("kolone", MAPPER.createArrayNode()));
        greska(poruka, d -> sloj(d, 4).set("redovi", MAPPER.createArrayNode()));
        greska(poruka, d -> ((ArrayNode) sloj(d, 4).get("kolone")).set(0, 0.0));
        greska(poruka, d -> ((ArrayNode) sloj(d, 4).get("redovi")).set(1, -5.0));
        greska(poruka, d -> sloj(d, 4).set("celije", MAPPER.createArrayNode()));
        greska(poruka, d -> ((ArrayNode) sloj(d, 4).get("celije")).remove(1));
        greska(poruka, d -> ((ArrayNode) sloj(d, 4).get("celije").get(0)).remove(1));
        // spoj: 1 dodatna ćelija desno iz poslednje kolone izlazi van
        greska(poruka, d -> celija(d, 0, 1).put("spojDesno", 1));
        greska(poruka, d -> celija(d, 1, 0).put("spojDole", 1));
        greska(poruka, d -> celija(d, 0, 0).put("spojDesno", -1));
        ispravno(d -> celija(d, 0, 0).put("spojDesno", 1).put("spojDole", 1));
    }

    @Test
    void tabelaNajviseTridesetPoTridesetISaTridesetjednom() {
        String poruka = "Tabela nije ispravna.";
        ispravno(d -> velikaTabela(d, 30, 30));
        greska(poruka, d -> velikaTabela(d, 31, 1));
        greska(poruka, d -> velikaTabela(d, 1, 31));
    }

    static void velikaTabela(ObjectNode d, int redova, int kolona) {
        ObjectNode t = sloj(d, 4);
        ArrayNode k = MAPPER.createArrayNode();
        ArrayNode r = MAPPER.createArrayNode();
        ArrayNode celije = MAPPER.createArrayNode();
        for (int j = 0; j < kolona; j++) k.add(10.0);
        for (int i = 0; i < redova; i++) {
            r.add(10.0);
            ArrayNode red = MAPPER.createArrayNode();
            for (int j = 0; j < kolona; j++) {
                red.add(MAPPER.createObjectNode());
            }
            celije.add(red);
        }
        t.set("kolone", k);
        t.set("redovi", r);
        t.set("celije", celije);
    }

    static ObjectNode celija(ObjectNode d, int red, int kolona) {
        return (ObjectNode) sloj(d, 4).get("celije").get(red).get(kolona);
    }

    // --- formula ---

    @Test
    void formula() {
        String poruka = "Formula nije ispravna.";
        ispravno(d -> sloj(d, 5).put("latex", znakova(5000)));
        greska(poruka, d -> sloj(d, 5).put("latex", znakova(5001)));
        greska(poruka, d -> sloj(d, 5).put("latex", ""));
        greska(poruka, d -> sloj(d, 5).putNull("latex"));
        ispravno(d -> sloj(d, 5).put("velicina", 1));
        ispravno(d -> sloj(d, 5).put("velicina", 400));
        greska(poruka, d -> sloj(d, 5).put("velicina", 0.99));
        greska(poruka, d -> sloj(d, 5).put("velicina", 400.01));
    }

    // --- crtež ---

    @Test
    void crtez() {
        String poruka = "Crtež nije ispravan.";
        ispravno(d -> potez(d).put("debljina", 0.25));
        ispravno(d -> potez(d).put("debljina", 20));
        greska(poruka, d -> potez(d).put("debljina", 0.24));
        greska(poruka, d -> potez(d).put("debljina", 20.01));
        greska(poruka, d -> potez(d).set("tacke", MAPPER.createArrayNode().add(1.0).add(2.0).add(3.0)));
        ispravno(d -> sloj(d, 6).set("potezi", potezi(20_000)));
        greska(poruka, d -> sloj(d, 6).set("potezi", potezi(20_001)));
        ispravno(d -> sloj(d, 6).set("potezi", MAPPER.createArrayNode()));
        ispravno(d -> sloj(d, 6).putNull("potezi"));
    }

    static ObjectNode potez(ObjectNode d) {
        return (ObjectNode) sloj(d, 6).get("potezi").get(0);
    }

    /** Dva poteza sa ukupno {@code tacaka} tačaka. */
    static ArrayNode potezi(int tacaka) {
        ArrayNode niz = MAPPER.createArrayNode();
        int ostalo = tacaka;
        while (ostalo > 0) {
            int n = Math.min(ostalo, 10_000);
            ArrayNode t = MAPPER.createArrayNode();
            for (int i = 0; i < n; i++) t.add(1.0).add(2.0);
            niz.add(MAPPER.createObjectNode().put("boja", "#000000").put("debljina", 1).set("tacke", t));
            ostalo -= n;
        }
        return niz;
    }

    // --- animacije ---

    @Test
    void animacijaCiljaSloj() {
        greska("Animacija cilja sloj koji ne postoji.", d -> animacija(d, 0).put("slojId", "nema"));
    }

    @Test
    void animacijaCiljaPasus() {
        String poruka = "Animacija cilja pasus koji ne postoji.";
        ispravno(d -> animacija(d, 0).put("pasus", 1));
        greska(poruka, d -> animacija(d, 0).put("pasus", 2));
        greska(poruka, d -> animacija(d, 0).put("pasus", -1));
        greska(poruka, d -> animacija(d, 1).put("pasus", 0));   // a2 cilja sliku, ne tekst
    }

    @Test
    void trajanjeIKasnjenje() {
        String poruka = "Trajanje i kašnjenje animacije su od 0 do 10000 ms.";
        ispravno(d -> animacija(d, 1).put("trajanjeMs", 10_000).put("kasnjenjeMs", 10_000));
        ispravno(d -> animacija(d, 1).put("trajanjeMs", 0).put("kasnjenjeMs", 0));
        greska(poruka, d -> animacija(d, 1).put("trajanjeMs", 10_001));
        greska(poruka, d -> animacija(d, 1).put("trajanjeMs", -1));
        greska(poruka, d -> animacija(d, 1).put("kasnjenjeMs", 10_001));
        greska(poruka, d -> animacija(d, 1).put("kasnjenjeMs", -1));
    }

    @Test
    void smerOdgovaraEfektu() {
        String poruka = "Smer animacije nije ispravan.";
        for (String smer : new String[]{"LEVO", "DESNO", "GORE", "DOLE"}) {
            for (String efekat : new String[]{"ULET", "BRISANJE", "ISCRTAJ"}) {
                ispravno(d -> animacija(d, 2).put("efekat", efekat).put("smer", smer));
            }
            greska(poruka, d -> animacija(d, 2).put("smer", smer.equals("LEVO") ? "HORIZONTALNO" : "VERTIKALNO"));
        }
        greska(poruka, d -> animacija(d, 2).put("smer", "HORIZONTALNO"));
        greska(poruka, d -> animacija(d, 2).put("smer", "VERTIKALNO"));
        ispravno(d -> animacija(d, 2).put("efekat", "PODELI").put("smer", "HORIZONTALNO"));
        ispravno(d -> animacija(d, 2).put("efekat", "PODELI").put("smer", "VERTIKALNO"));
        greska(poruka, d -> animacija(d, 2).put("efekat", "PODELI").put("smer", "LEVO"));
    }

    // --- veličina ---

    @Test
    void velicinaDokumenta() {
        assertDoesNotThrow(() -> PlatnoPP.proveriVelicinu(1_048_576));
        SystemException e = assertThrows(SystemException.class, () -> PlatnoPP.proveriVelicinu(1_048_577));
        assertEquals("Dokument platna je prevelik (najviše 1 MB).", e.getMessage());
        assertEquals(400, e.getCode());
    }

    // --- medijIds ---

    @Test
    void medijIdsSkupljaPozadinuSlikeICrteze() {
        Platno p = PlatnoPP.normalizuj(PlatnoTestPodaci.ispravno());
        assertEquals(Set.of(PlatnoTestPodaci.SLIKA, PlatnoTestPodaci.CRTEZ), PlatnoPP.medijIds(p));
        ObjectNode d = dokument();
        ((ObjectNode) d.get("pozadina")).put("medijId", "poz-1");
        assertEquals(Set.of(PlatnoTestPodaci.SLIKA, PlatnoTestPodaci.CRTEZ, "poz-1"), PlatnoPP.medijIds(normalizovan(d)));
    }

    // --- normalizacija ---

    @Test
    void normalizacijaPopunjavaPodrazumevaneVrednosti() {
        ObjectNode d = MAPPER.createObjectNode().put("verzija", 1);
        d.set("pozadina", MAPPER.createObjectNode().put("boja", "#ffffff"));
        d.set("slojevi", MAPPER.readTree("""
                [{"tip":"TEKST","id":"t","x":0,"y":0,"sirina":100,"visina":50,
                  "pasusi":[{"delovi":[{"tekst":"a"}]},{}]},
                 {"tip":"SLIKA","id":"s","x":0,"y":0,"sirina":10,"visina":10,"medijId":"m"},
                 {"tip":"OBLIK","id":"o","x":0,"y":0,"sirina":300,"visina":60,"oblik":"LINIJA"},
                 {"tip":"OBLIK","id":"p","x":0,"y":0,"sirina":300,"visina":60,"oblik":"ELIPSA","ivica":{"boja":"#aabbcc","debljina":1}},
                 {"tip":"TABELA","id":"tb","x":0,"y":0,"sirina":10,"visina":10,"kolone":[5],"redovi":[5],"celije":[[{}]]}]"""));
        d.set("animacije", MAPPER.createArrayNode());
        Platno p = normalizovan(d);
        assertDoesNotThrow(() -> PlatnoPP.proveri(p));

        assertEquals("#FFFFFF", p.pozadina().boja());
        TekstSloj t = (TekstSloj) p.slojevi().get(0);
        assertNull(t.naziv());
        assertEquals(0.0, t.rotacija());
        assertEquals(1.0, t.providnost());
        assertFalse(t.zakljucan());
        assertNull(t.senka());
        assertEquals(new Okvir(7.2, 3.6, 7.2, 3.6, Sidro.VRH, true, 1.0, 1.0), t.okvir());
        assertNull(t.popuna());
        assertNull(t.ivica());
        Pasus pasus = t.pasusi().get(0);
        assertEquals(Poravnanje.LEVO, pasus.poravnanje());
        assertEquals(0, pasus.nivo());
        assertNull(pasus.lista());
        assertEquals(new Uvlacenje(0.0, 0.0), pasus.uvlacenje());
        assertEquals(0.0, pasus.razmakPre());
        assertEquals(0.0, pasus.razmakPosle());
        assertEquals(100.0, pasus.proredProcenat());
        assertEquals(java.util.List.of(), pasus.tabulatori());
        assertEquals(new Deo("a", "Carlito", 18.0, "#000000", false, false, false, false, Polozaj.OBICAN),
                pasus.delovi().get(0));
        assertEquals(java.util.List.of(), t.pasusi().get(1).delovi());

        assertEquals(new Isecak(0.0, 0.0, 0.0, 0.0), ((SlikaSloj) p.slojevi().get(1)).isecak());

        OblikSloj linija = (OblikSloj) p.slojevi().get(2);
        assertEquals(new Tacka(0.0, 30.0), linija.od());
        assertEquals(new Tacka(300.0, 30.0), linija.doTacke());
        OblikSloj elipsa = (OblikSloj) p.slojevi().get(3);
        assertNull(elipsa.od());
        assertNull(elipsa.doTacke());
        assertEquals(new Ivica("#AABBCC", 1.0, Crtica.PUNA), elipsa.ivica());

        Celija c = ((TabelaSloj) p.slojevi().get(4)).celije().get(0).get(0);
        assertEquals(java.util.List.of(), c.pasusi());
        assertNull(c.popuna());
        assertEquals(new IviceCelije(null, null, null, null), c.ivice());
        assertEquals(0, c.spojDesno());
        assertEquals(0, c.spojDole());
        assertFalse(c.pokrivena());
        assertEquals(Sidro.VRH, c.sidro());
    }

    @Test
    void normalizacijaZaokruzujeIBojiVelikimSlovima() {
        ObjectNode d = dokument();
        sloj(d, 0).put("x", 12.345);
        sloj(d, 0).put("y", 12.344);
        sloj(d, 0).put("popuna", "#ff00aa");
        deo(d).put("velicina", 17.995);
        ((ObjectNode) d.get("pozadina")).put("boja", "#abcdef");
        Platno p = normalizovan(d);
        TekstSloj t = (TekstSloj) p.slojevi().get(0);
        assertEquals(12.35, t.x());
        assertEquals(12.34, t.y());
        assertEquals("#FF00AA", t.popuna());
        assertEquals(18.0, t.pasusi().get(0).delovi().get(0).velicina());
        assertEquals("#ABCDEF", p.pozadina().boja());
    }

    @Test
    void normalizacijaAnimacija() {
        ObjectNode d = dokument();
        ((ArrayNode) d.get("animacije")).removeAll();
        ArrayNode a = (ArrayNode) d.get("animacije");
        a.add(anim("a1", "POJAVI").put("trajanjeMs", 700).put("smer", "LEVO"));
        a.add(anim("a2", "FADE").put("smer", "LEVO"));
        a.add(anim("a3", "ULET"));
        a.add(anim("a4", "BRISANJE"));
        a.add(anim("a5", "ISCRTAJ"));
        a.add(anim("a6", "PODELI"));
        a.add(anim("a7", "ZUM"));
        Platno p = normalizovan(d);
        assertDoesNotThrow(() -> PlatnoPP.proveri(p));
        java.util.List<Animacija> l = p.animacije();
        assertEquals(0, l.get(0).trajanjeMs());
        assertNull(l.get(0).smer());
        assertEquals(500, l.get(1).trajanjeMs());
        assertNull(l.get(1).smer());
        assertEquals(Smer.DOLE, l.get(2).smer());
        assertEquals(Smer.LEVO, l.get(3).smer());
        assertEquals(Smer.LEVO, l.get(4).smer());
        assertEquals(Smer.VERTIKALNO, l.get(5).smer());
        assertNull(l.get(6).smer());
        for (Animacija x : l) {
            assertNull(x.pasus());
            assertEquals(0, x.kasnjenjeMs());
        }
    }

    static ObjectNode anim(String id, String efekat) {
        return MAPPER.createObjectNode().put("id", id).put("slojId", "t1").put("vrsta", "ULAZ").put("efekat", efekat)
                .put("okidac", "KLIK");
    }
}
