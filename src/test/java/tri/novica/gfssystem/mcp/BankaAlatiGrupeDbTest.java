package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.JsonNode;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.dto.uzivo.CreatePrezentacijaCmd;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.banka.GrupaZadatakaService;
import tri.novica.gfssystem.service.banka.VarijantaService;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Alati banke za grupe i poništavanje serije kroz pravi {@code /mcp} (spec MCP 4.1, 4.3): {@code napravi_grupu} (CLAUDE i
 * serija, i pod Novicinom grupom; jedno pravljenje iz budžeta), {@code izmeni_grupu} (merge patch: izostavljeno ostaje,
 * {@code null} briše samo brisivo, nepoznat ključ je greška), {@code izmeni_polja_grupe} (proba pre upisa, 409 sa id-jevima),
 * {@code premesti_grupu}, {@code obrisi_grupu} (samo prazna), {@code kopiraj_grupu} (budžet po broju stavki) i
 * {@code ponisti_seriju} (banka i prezentacije zajedno, bez potvrde samo spisak, uz {@code linkPregleda}). Novicine grupe
 * (RUCNO) se kroz MCP ne menjaju, ne premeštaju i ne brišu.
 */
class BankaAlatiGrupeDbTest extends McpDbTestOsnova {

    static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M0 0h1\"/></svg>"
            .getBytes(StandardCharsets.UTF_8);
    static final Polje BROJ_A = new Polje("a", "a) binarni u dekadni", TipPolja.BROJ, null, null, null, null, null, null);
    static final Map<String, OdgovorPolja> ODGOVOR_A = Map.of("a", new OdgovorPolja(11.0, null, null, null));
    static final String NOVICINA = "Ovu grupu je napravio Novica; izmenu radi on u UI-ju.";
    static final String SERIJA_PORUKA = "Serija mora imati 3 do 60 malih slova, cifara ili crtica.";

    @Autowired GrupaZadatakaService grupaService;
    @Autowired VarijantaService varijantaService;
    @Autowired PrezentacijaService prezentacijaService;
    @MockitoSpyBean McpBudzet budzet;

    Predmet predmet;
    String serija;
    /** Novicino stablo: unutrašnji koren PREDAVANJE sa listom bez varijanti, i list TEST sa jednom varijantom. */
    GrupaDetails rucniKoren;
    GrupaDetails rucniList;
    GrupaDetails rucniTest;

    @BeforeEach
    void setUp() {
        when(latexRender.renderuj(eq("svg"), anyString(), anyList()))
                .thenReturn(new RenderOdgovor(true, Base64.getEncoder().encodeToString(SVG), null, null, null, 7L, false));
        predmet = noviPredmet("Banka grupe MCP test");
        serija = "grupe-" + UUID.randomUUID().toString().substring(0, 8);
        rucniKoren = grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.PREDAVANJE, "Brojni sistemi", null,
                null, null, null));
        rucniList = grupaService.kreiraj(new GrupaCmd(null, rucniKoren.id(), null, "2 → 10", null, 2, null,
                List.of(BROJ_A)));
        rucniTest = grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.TEST, "Kolokvijum 1", null, 3,
                "Brojevi do 255.", List.of(BROJ_A)));
        varijantaService.kreiraj(rucniTest.id(), cmd("K1", null));
    }

    static VarijantaCmd cmd(String naziv, IzvorVarijante izvor) {
        return new VarijantaCmd(naziv, "Pretvori $1011_{(2)}$ u dekadni.", null, ODGOVOR_A, List.of(), izvor);
    }

    /** Klodova grupa iz serije testa: koren PREDAVANJE (list) sa opisom, težinom, uputstvom i šemom {@link #BROJ_A}. */
    GrupaDetails klodova(String naziv) {
        return grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.PREDAVANJE, naziv, "Opis grupe", 2,
                "Brojevi do 255.", List.of(BROJ_A)), IzvorVarijante.CLAUDE, serija);
    }

    static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    JsonNode uspeh(String alat, Map<String, Object> argumenti) {
        JsonNode r = mcp.pozovi(alat, argumenti);
        assertFalse(McpTestKlijent.greska(r), r.toString());
        return McpTestKlijent.json(r);
    }

    void ocekujGresku(String alat, Map<String, Object> argumenti, String poruka) {
        JsonNode r = mcp.pozovi(alat, argumenti);
        assertTrue(McpTestKlijent.greska(r), r.toString());
        assertEquals(poruka, McpTestKlijent.tekst(r));
    }

    int brojGrupa() {
        Integer n = jdbc.queryForObject("select count(*) from grupe_zadataka where predmet_id = ?", Integer.class,
                predmet.getId());
        return n == null ? 0 : n;
    }

    boolean postoji(String tabela, Long id) {
        Integer n = jdbc.queryForObject("select count(*) from " + tabela + " where id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    /** Grupa iz {@code {grupa, link}} sa proverom linka. */
    static JsonNode grupaSaLinkom(JsonNode r) {
        assertEquals(Set.of("grupa", "link"), new TreeSet<>(r.propertyNames()), r.toString());
        JsonNode grupa = r.path("grupa");
        assertEquals("http://localhost:4200/banka/grupe/" + grupa.path("id").asLong(), r.path("link").asString());
        return grupa;
    }

    // ---------------------------------------------------------------- napravi_grupu

    @Test
    void napraviGrupuKorenaTraziNamenuIPisaKlodovuSeriju() {
        int pre = brojGrupa();
        ocekujGresku("napravi_grupu", mapa("serija", serija, "predmetId", predmet.getId(), "naziv", "Konverzije"),
                "Namena je obavezna za koren.");
        assertEquals(pre, brojGrupa());

        JsonNode g = grupaSaLinkom(uspeh("napravi_grupu", mapa("serija", serija, "predmetId", predmet.getId(),
                "namena", "PREDAVANJE", "naziv", "Konverzije", "opis", "Prevođenje između sistema.")));
        assertEquals("CLAUDE", g.path("izvor").asString());
        assertEquals(serija, g.path("serija").asString());
        assertEquals("PREDAVANJE", g.path("namena").asString());
        assertTrue(g.path("roditeljId").isNull());
        assertEquals("Prevođenje između sistema.", g.path("opis").asString());
        assertEquals(pre + 1, brojGrupa());
        assertEquals(serija, jdbc.queryForObject("select serija from grupe_zadataka where id = ?", String.class,
                g.path("id").asLong()));
    }

    @Test
    void podgrupaPodNovicinomGrupomJeKlodova() {
        JsonNode g = grupaSaLinkom(uspeh("napravi_grupu", mapa("serija", serija, "predmetId", predmet.getId(),
                "roditeljId", rucniKoren.id(), "naziv", "2 → 16", "tezina", 2,
                "polja", List.of(mapa("kljuc", "c", "oznaka", "Paran?", "tip", "JEDAN_TACAN",
                        "opcije", List.of(mapa("tekst", "da"), mapa("tekst", "ne")))))));
        assertEquals("CLAUDE", g.path("izvor").asString());
        assertEquals(rucniKoren.id(), g.path("roditeljId").asLong());
        assertEquals("PREDAVANJE", g.path("namena").asString(), "namena se nasleđuje od roditelja");
        assertEquals(2, g.path("tezina").asInt());
        assertEquals(List.of(1L, 2L), g.path("polja").get(0).path("opcije").valueStream()
                .map(o -> o.path("id").asLong()).toList());
        assertEquals(List.of(rucniKoren.id(), g.path("id").asLong()),
                g.path("putanja").valueStream().map(p -> p.path("id").asLong()).toList());
    }

    @Test
    void napraviGrupuCitaPoljaITezinuStrogo() {
        int pre = brojGrupa();
        ocekujGresku("napravi_grupu", mapa("serija", serija, "predmetId", predmet.getId(), "namena", "TEST",
                "naziv", "Heks", "polja", List.of(mapa("kljuc", "a", "oznaka", "a)", "tip", "BROJ", "x", 1))),
                "Nepoznato polje 'polja[0].x'.");
        ocekujGresku("napravi_grupu", mapa("serija", serija, "predmetId", predmet.getId(), "namena", "TEST",
                "naziv", "Heks", "tezina", 2.5), "Neispravan format podataka.");
        ocekujGresku("napravi_grupu", mapa("serija", "Uvoz-1", "predmetId", predmet.getId(), "namena", "TEST",
                "naziv", "Heks"), SERIJA_PORUKA);
        assertEquals(pre, brojGrupa());
    }

    @Test
    void pravljenjeTrosiBudzet() {
        uspeh("napravi_grupu", mapa("serija", serija, "predmetId", predmet.getId(), "namena", "TEST", "naziv", "Oktalni"));
        verify(budzet, times(1)).trosiPravljenje("test", 1);

        uspeh("kopiraj_grupu", mapa("serija", serija, "grupaId", rucniTest.id(), "ciljNamena", "PREDAVANJE"));
        verify(budzet, times(1)).trosiPravljenje("test", 2);   // grupa i njena varijanta

        int pre = brojGrupa();
        doThrow(new SystemException(McpBudzet.PORUKA, 429)).when(budzet).trosiPravljenje(anyString(), anyInt());
        ocekujGresku("napravi_grupu", mapa("serija", serija, "predmetId", predmet.getId(), "namena", "TEST",
                "naziv", "Heksadekadni"), "Dnevni limit za Kloda je potrošen.");
        ocekujGresku("kopiraj_grupu", mapa("serija", serija, "grupaId", rucniTest.id(), "ciljNamena", "PREDAVANJE"),
                "Dnevni limit za Kloda je potrošen.");
        assertEquals(pre, brojGrupa());
    }

    // ---------------------------------------------------------------- izmene

    @Test
    void novicinaGrupaSeKrozMcpNeMenja() {
        ocekujGresku("izmeni_grupu", mapa("serija", serija, "grupaId", rucniList.id(), "naziv", "Drugo"), NOVICINA);
        ocekujGresku("izmeni_polja_grupe", mapa("serija", serija, "grupaId", rucniList.id(), "polja", List.of()),
                NOVICINA);
        ocekujGresku("premesti_grupu", mapa("serija", serija, "grupaId", rucniList.id()), NOVICINA);
        ocekujGresku("obrisi_grupu", mapa("serija", serija, "grupaId", rucniList.id()), NOVICINA);
        GrupaDetails posle = grupaService.detalji(rucniList.id());
        assertEquals("2 → 10", posle.naziv());
        assertEquals(rucniKoren.id(), posle.roditeljId());
        assertEquals(rucniList.polja(), posle.polja());

        ocekujGresku("izmeni_grupu", mapa("serija", serija, "grupaId", Long.MAX_VALUE, "naziv", "x"),
                "Grupa nije pronađena.");
    }

    @Test
    void izmeniGrupuJeMergePatch() {
        GrupaDetails k = klodova("Konverzije");
        JsonNode g = grupaSaLinkom(uspeh("izmeni_grupu", mapa("serija", serija, "grupaId", k.id(), "naziv", "Pretvaranja")));
        assertEquals("Pretvaranja", g.path("naziv").asString());
        assertEquals("Opis grupe", g.path("opis").asString());
        assertEquals(2, g.path("tezina").asInt());
        assertEquals("Brojevi do 255.", g.path("uputstvo").asString());
        assertEquals("a", g.path("polja").get(0).path("kljuc").asString(), "šema ostaje");

        g = grupaSaLinkom(uspeh("izmeni_grupu", mapa("serija", serija, "grupaId", k.id(), "opis", null)));
        assertTrue(g.path("opis").isNull(), g.toString());
        assertEquals("Brojevi do 255.", g.path("uputstvo").asString());
        assertNull(grupaService.detalji(k.id()).opis());

        ocekujGresku("izmeni_grupu", mapa("serija", serija, "grupaId", k.id(), "naziv", null),
                "Polje 'naziv' se ne briše.");
        ocekujGresku("izmeni_grupu", mapa("serija", serija, "grupaId", k.id(), "nazv", "x"), "Nepoznato polje 'nazv'.");
        ocekujGresku("izmeni_grupu", mapa("serija", serija, "grupaId", k.id(), "tezina", "3"),
                "Neispravan format podataka.");
        assertEquals("Pretvaranja", grupaService.detalji(k.id()).naziv());
        assertEquals(2, grupaService.detalji(k.id()).tezina());
    }

    @Test
    void izmenaPoljaKojaBiSpustilaAktivnuJe409() {
        GrupaDetails k = klodova("Konverzije");
        Long aktivna = varijantaService.kreiraj(k.id(), cmd("V1", null)).id();
        varijantaService.odobri(aktivna);

        ocekujGresku("izmeni_polja_grupe", mapa("serija", serija, "grupaId", k.id(), "polja",
                        List.of(mapa("kljuc", "a", "oznaka", "a) binarni u dekadni", "tip", "BROJ"),
                                mapa("kljuc", "b", "oznaka", "b) heks", "tip", "TEKST"))),
                "Izmena polja bi pogodila varijante: " + aktivna + ".");
        assertEquals(k.polja(), grupaService.detalji(k.id()).polja());

        GrupaDetails prazna = klodova("Heksadekadni");
        JsonNode g = grupaSaLinkom(uspeh("izmeni_polja_grupe", mapa("serija", serija, "grupaId", prazna.id(), "polja",
                List.of(mapa("kljuc", "b", "oznaka", "b) heks", "tip", "TEKST")))));
        assertEquals("b", g.path("polja").get(0).path("kljuc").asString());
        assertEquals("Heksadekadni", g.path("naziv").asString());
        assertEquals(List.of("b"), grupaService.detalji(prazna.id()).polja().stream().map(Polje::kljuc).toList());
    }

    @Test
    void premestiKlodovuGrupuIPodNovicinu() {
        GrupaDetails k = klodova("Konverzije");
        JsonNode g = grupaSaLinkom(uspeh("premesti_grupu", mapa("serija", serija, "grupaId", k.id(),
                "roditeljId", rucniKoren.id(), "rb", 1)));
        assertEquals(rucniKoren.id(), g.path("roditeljId").asLong());
        assertEquals(1, g.path("rb").asInt());
        assertEquals(2, grupaService.detalji(rucniList.id()).rb(), "Novicin list je prenumerisan");

        g = grupaSaLinkom(uspeh("premesti_grupu", mapa("serija", serija, "grupaId", k.id())));
        assertTrue(g.path("roditeljId").isNull());
        assertEquals(1, grupaService.detalji(rucniList.id()).rb());
    }

    @Test
    void obrisiSamoPraznuKlodovuGrupu() {
        GrupaDetails puna = klodova("Konverzije");
        varijantaService.kreiraj(puna.id(), cmd("V1", IzvorVarijante.CLAUDE), serija, false);
        ocekujGresku("obrisi_grupu", mapa("serija", serija, "grupaId", puna.id()), "Briše se samo prazna grupa.");
        assertTrue(postoji("grupe_zadataka", puna.id()));

        GrupaDetails prazna = klodova("Heksadekadni");
        JsonNode r = uspeh("obrisi_grupu", mapa("serija", serija, "grupaId", prazna.id()));
        assertEquals(Set.of("obrisano"), new TreeSet<>(r.propertyNames()));
        assertTrue(r.path("obrisano").asBoolean());
        assertFalse(postoji("grupe_zadataka", prazna.id()));
    }

    // ---------------------------------------------------------------- kopija

    @Test
    void kopirajNovicinuGrupuIzTestaUPredavanje() {
        JsonNode g = grupaSaLinkom(uspeh("kopiraj_grupu", mapa("serija", serija, "grupaId", rucniTest.id(),
                "ciljNamena", "PREDAVANJE")));
        assertNotEquals(rucniTest.id(), g.path("id").asLong());
        assertEquals("Kolokvijum 1", g.path("naziv").asString());
        assertEquals("PREDAVANJE", g.path("namena").asString());
        assertEquals("CLAUDE", g.path("izvor").asString());
        assertEquals(serija, g.path("serija").asString());
        assertEquals("Brojevi do 255.", g.path("uputstvo").asString());
        assertEquals(1, g.path("brojVarijanti").asInt());
        assertEquals(1, g.path("brojNacrta").asInt());
        assertEquals("RUCNO", grupaService.detalji(rucniTest.id()).izvor().name(), "original je netaknut");

        ocekujGresku("kopiraj_grupu", mapa("serija", serija, "grupaId", rucniTest.id(), "ciljNamena", "PREDAVANJE",
                "ciljRoditeljId", rucniTest.id()), "Cilj kopije mora biti u istom predmetu i nameni.");
    }

    // ---------------------------------------------------------------- poništavanje serije

    @Test
    void ponistiSerijuPrvoSpisakPaBrisanje() {
        GrupaDetails klodova = klodova("Konverzije");
        Long nacrt = varijantaService.kreiraj(klodova.id(), cmd("V1", IzvorVarijante.CLAUDE), serija, false)
                .varijanta().id();
        GrupaDetails saOdobrenom = klodova("Heksadekadni");
        Long odobrena = varijantaService.kreiraj(saOdobrenom.id(), cmd("V2", IzvorVarijante.CLAUDE), serija, false)
                .varijanta().id();
        varijantaService.odobri(odobrena);
        Long prezentacija = prezentacijaService.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), "Vežbe 1 – zadaci",
                null, null), serija).id();

        JsonNode spisak = uspeh("ponisti_seriju", mapa("serija", serija, "potvrdi", false));
        assertEquals(Set.of("potvrdjeno", "obrisano", "preskoceno", "linkPregleda"),
                new TreeSet<>(spisak.propertyNames()));
        assertFalse(spisak.path("potvrdjeno").asBoolean(true));
        assertEquals("http://localhost:4200/banka/pregled?serija=" + serija, spisak.path("linkPregleda").asString());
        assertEquals(List.of("VARIJANTA:" + nacrt, "GRUPA:" + klodova.id(), "PREZENTACIJA:" + prezentacija),
                stavke(spisak.path("obrisano")));
        assertEquals(List.of("VARIJANTA:" + odobrena, "GRUPA:" + saOdobrenom.id()), stavke(spisak.path("preskoceno")));
        assertEquals("Odobrena ili nije Klodov nacrt.", spisak.path("preskoceno").get(0).path("razlog").asString());
        assertEquals("Grupa nije prazna.", spisak.path("preskoceno").get(1).path("razlog").asString());
        assertTrue(postoji("varijante", nacrt));
        assertTrue(postoji("grupe_zadataka", klodova.id()));
        assertTrue(postoji("prezentacije", prezentacija));

        JsonNode ishod = uspeh("ponisti_seriju", mapa("serija", serija, "potvrdi", true));
        assertTrue(ishod.path("potvrdjeno").asBoolean());
        assertEquals(stavke(spisak.path("obrisano")), stavke(ishod.path("obrisano")));
        assertEquals(stavke(spisak.path("preskoceno")), stavke(ishod.path("preskoceno")));
        assertFalse(postoji("varijante", nacrt));
        assertFalse(postoji("grupe_zadataka", klodova.id()));
        assertFalse(postoji("prezentacije", prezentacija));
        assertTrue(postoji("varijante", odobrena));
        assertTrue(postoji("grupe_zadataka", saOdobrenom.id()));
        assertTrue(postoji("grupe_zadataka", rucniKoren.id()), "Novicine grupe se ne pominju");
    }

    /** {@code vrsta:id} stavki poništavanja, redom. */
    static List<String> stavke(JsonNode niz) {
        return niz.valueStream().map(s -> s.path("vrsta").asString() + ":" + s.path("id").asLong()).toList();
    }
}
