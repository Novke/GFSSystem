package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.uzivo.CreatePrezentacijaCmd;
import tri.novica.gfssystem.dto.uzivo.SlajdCmd;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Fajlovi za studente kroz MCP (spec fajlova 7): upload {@code namena=fajl} na {@code /mcp/prilozi} (vrsta
 * {@code FAJL_PREZENTACIJE}, nepoznata namena 400, podrazumevana namena je i dalje prilog banke) i alati
 * {@code fajlovi_prezentacije}, {@code dodaj_fajl}, {@code izmeni_fajl}, {@code obrisi_fajl} i broj fajlova u {@code prezentacija}.
 */
class FajloviAlatiDbTest extends McpDbTestOsnova {

    static final byte[] PK = {0x50, 0x4B, 0x03, 0x04, 0x14, 0, 6, 0, 8, 0};
    static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);
    static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired PrezentacijaService prezentacijaService;

    Predmet predmet;
    String serija;
    Long prezentacija;
    Long slajd;

    @BeforeEach
    void setUp() {
        predmet = noviPredmet("Fajlovi MCP test");
        serija = "fajl-" + UUID.randomUUID().toString().substring(0, 8);
        prezentacija = prezentacijaService.kreiraj(new CreatePrezentacijaCmd(predmet.getId(), "Vežbe 1", null, null)).id();
        slajd = prezentacijaService.dodajSlajd(prezentacija, new SlajdCmd(TipSlajda.INFO, "Prvi", "x", null, null, false,
                null, null, null, null), null).id();
    }

    static Map<String, Object> mapa(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    MockHttpServletResponse upload(String ime, byte[] bajtovi, String namena) throws Exception {
        var zahtev = multipart("/mcp/prilozi").file(new MockMultipartFile("fajl", ime, "application/octet-stream", bajtovi))
                .param("serija", serija).header("Authorization", "Bearer " + McpTestKlijent.KLJUC);
        if (namena != null) {
            zahtev.param("namena", namena);
        }
        MockHttpServletResponse r = mvc.perform(zahtev).andReturn().getResponse();
        r.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return r;
    }

    String otpremi(String ime) throws Exception {
        MockHttpServletResponse r = upload(ime, PK, "fajl");
        JsonNode telo = JSON.readTree(r.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(201, r.getStatus(), telo.toString());
        assertEquals("FAJL_PREZENTACIJE", telo.path("vrsta").asString());
        String id = telo.path("medijId").asString();
        mediji.add(id);
        return id;
    }

    JsonNode uspeh(String alat, Map<String, Object> argumenti) {
        JsonNode r = mcp.pozovi(alat, argumenti);
        assertFalse(McpTestKlijent.greska(r), r.toString());
        return McpTestKlijent.json(r);
    }

    @Test
    void uploadSaNamenomFajlIPodrazumevanaNamena() throws Exception {
        String ime = "mcp-fajl-" + UUID.randomUUID().toString().substring(0, 8);
        String id = otpremi(ime + ".docx");
        assertEquals("FAJL_PREZENTACIJE", jdbc.queryForObject("select vrsta from mediji where id = ?", String.class, id));

        // bez namene (prilog banke): PDF je prilog, ne fajl za studente
        MockHttpServletResponse prilog = upload(ime + "-p.pdf", PDF, null);
        assertEquals(201, prilog.getStatus(), prilog.getContentAsString());
        JsonNode telo = JSON.readTree(prilog.getContentAsString(StandardCharsets.UTF_8));
        mediji.add(telo.path("medijId").asString());
        assertEquals("PRILOG_PDF", telo.path("vrsta").asString());

        MockHttpServletResponse pogresna = upload(ime + "-x.docx", PK, "nesto");
        assertEquals(400, pogresna.getStatus(), pogresna.getContentAsString());
        assertEquals(0, jdbc.queryForObject("select count(*) from mediji where naziv like ?", Integer.class, ime + "-x%"));
    }

    @Test
    void dodajIzmeniObrisiFajl() throws Exception {
        String medij = otpremi("tabela-" + UUID.randomUUID().toString().substring(0, 6) + ".docx");
        JsonNode dodat = uspeh("dodaj_fajl", mapa("serija", serija, "prezentacijaId", prezentacija, "medijId", medij,
                "slajdId", slajd, "naziv", "Vezba"));
        Long fajlId = dodat.path("id").asLong();
        assertEquals("Vezba.docx", dodat.path("naziv").asString());
        assertEquals(slajd.longValue(), dodat.path("slajdId").asLong());

        JsonNode lista = uspeh("fajlovi_prezentacije", mapa("prezentacijaId", prezentacija));
        assertEquals(1, lista.size());
        assertEquals(fajlId.longValue(), lista.get(0).path("id").asLong());

        JsonNode pregled = uspeh("prezentacija", mapa("prezentacijaId", prezentacija));
        assertEquals(1, pregled.path("brojFajlova").asInt(), pregled.toString());

        JsonNode izm = uspeh("izmeni_fajl", mapa("serija", serija, "fajlId", fajlId, "naziv", "Drugo", "slajdId", null));
        assertEquals("Drugo.docx", izm.path("naziv").asString());
        assertTrue(izm.path("slajdId").isNull() || izm.path("slajdId").isMissingNode());

        uspeh("obrisi_fajl", mapa("serija", serija, "fajlId", fajlId));
        assertEquals(0, uspeh("fajlovi_prezentacije", mapa("prezentacijaId", prezentacija)).size());
        assertEquals(0, jdbc.queryForObject("select count(*) from mediji where id = ?", Integer.class, medij));
    }

    @Test
    void zauzetIliTudMedijJe409() throws Exception {
        String medij = otpremi("a-" + UUID.randomUUID().toString().substring(0, 6) + ".docx");
        uspeh("dodaj_fajl", mapa("serija", serija, "prezentacijaId", prezentacija, "medijId", medij));
        JsonNode r = mcp.pozovi("dodaj_fajl", mapa("serija", serija, "prezentacijaId", prezentacija, "medijId", medij));
        assertTrue(McpTestKlijent.greska(r), r.toString());
        assertTrue(r.toString().contains("već vezan"), r.toString());

        MockHttpServletResponse prilog = upload("b-" + UUID.randomUUID().toString().substring(0, 6) + ".pdf", PDF, "prilog");
        String prilogId = JSON.readTree(prilog.getContentAsString(StandardCharsets.UTF_8)).path("medijId").asString();
        mediji.add(prilogId);
        JsonNode r2 = mcp.pozovi("dodaj_fajl", mapa("serija", serija, "prezentacijaId", prezentacija, "medijId", prilogId));
        assertTrue(McpTestKlijent.greska(r2), r2.toString());
    }

    @Test
    void upisniAlatiTrazeSeriju() throws Exception {
        JsonNode r = mcp.pozovi("obrisi_fajl", mapa("fajlId", 1));
        assertTrue(McpTestKlijent.greska(r), r.toString());
    }
}
