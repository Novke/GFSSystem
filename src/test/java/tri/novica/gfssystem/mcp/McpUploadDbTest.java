package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.utility.Serija;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Upload fajlova za Kloda kroz {@code POST /mcp/prilozi} i {@code POST /mcp/slike} (spec MCP 4.5), preko MockMvc sa pravim
 * filterom ključa: bez ključa 401; tip po prvim bajtovima, ne po deklarisanom MIME-u ni imenu; serija po obrascu; dnevni
 * budžet upload-a ključa pre upisa. Odbijen upload ne ostavlja red u {@code mediji} (traži se po jedinstvenom imenu fajla).
 */
class McpUploadDbTest extends McpDbTestOsnova {

    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4, 5};
    static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);
    static final byte[] GIF = "GIF89a....".getBytes(StandardCharsets.US_ASCII);
    static final byte[] HTML = "<html><body><script>alert(1)</script></body></html>".getBytes(StandardCharsets.UTF_8);

    static final JsonMapper JSON = JsonMapper.builder().build();

    @MockitoSpyBean McpBudzet budzet;

    String serija;
    /** Jedinstven prefiks imena fajlova ovog testa (red u {@code mediji} se po njemu traži). */
    String ime;

    @BeforeEach
    void setUp() {
        String sufiks = UUID.randomUUID().toString().substring(0, 8);
        serija = "upload-" + sufiks;
        ime = "mcp-upload-" + sufiks;
    }

    MockHttpServletResponse posalji(String putanja, MockMultipartFile fajl, String serija, String kljuc) throws Exception {
        MockMultipartHttpServletRequestBuilder zahtev = multipart(putanja).file(fajl);
        if (serija != null) {
            zahtev.param("serija", serija);
        }
        if (kljuc != null) {
            zahtev.header("Authorization", "Bearer " + kljuc);
        }
        MockHttpServletResponse odgovor = mvc.perform(zahtev).andReturn().getResponse();
        odgovor.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return odgovor;
    }

    MockHttpServletResponse posalji(String putanja, MockMultipartFile fajl, String serija) throws Exception {
        return posalji(putanja, fajl, serija, McpTestKlijent.KLJUC);
    }

    static JsonNode telo(MockHttpServletResponse odgovor) throws Exception {
        return JSON.readTree(odgovor.getContentAsString(StandardCharsets.UTF_8));
    }

    /** Uspešan upload: 201, id se pamti za čišćenje posle testa. */
    JsonNode uspeh(String putanja, MockMultipartFile fajl) throws Exception {
        MockHttpServletResponse odgovor = posalji(putanja, fajl, serija);
        JsonNode telo = telo(odgovor);
        if (telo.hasNonNull("medijId")) {
            mediji.add(telo.get("medijId").asString());
        }
        assertEquals(201, odgovor.getStatus(), odgovor.getContentAsString());
        return telo;
    }

    void odbijeno(MockHttpServletResponse odgovor, int status, String poruka) throws Exception {
        assertEquals(status, odgovor.getStatus(), odgovor.getContentAsString());
        JsonNode telo = telo(odgovor);
        assertEquals(poruka, telo.path("reason").asString());
        assertTrue(telo.hasNonNull("time"), telo.toString());
    }

    /** Redovi u {@code mediji} sa imenom ovog testa (dodaju se u čišćenje, da test koji padne ne ostavi smeće). */
    List<String> redoviTesta() {
        List<String> ids = jdbc.queryForList("select id from mediji where naziv like ?", String.class, ime + "%");
        ids.forEach(id -> {
            if (!mediji.contains(id)) {
                mediji.add(id);
            }
        });
        return ids;
    }

    String vrstaReda(String id) {
        return jdbc.queryForObject("select vrsta from mediji where id = ?", String.class, id);
    }

    @Test
    void bezKljucaIliSaPogresnimJe401INistaSeNeUpisuje() throws Exception {
        for (String putanja : List.of("/mcp/prilozi", "/mcp/slike")) {
            for (String kljuc : new String[]{null, "gfs_x", McpTestKlijent.KLJUC.replace('T', 'X')}) {
                MockHttpServletResponse odgovor = posalji(putanja, new MockMultipartFile("fajl", ime + ".png",
                        "image/png", PNG), serija, kljuc);
                odbijeno(odgovor, 401, "Neispravan MCP ključ.");
                assertEquals("Bearer", odgovor.getHeader("WWW-Authenticate"));
            }
        }
        assertEquals(List.of(), redoviTesta());
        verify(budzet, never()).trosiUpload(anyString(), anyLong());
    }

    @Test
    void pngSaDeklarisanimPdfomJePrilogSlika() throws Exception {
        JsonNode medij = uspeh("/mcp/prilozi", new MockMultipartFile("fajl", ime + ".pdf", "application/pdf", PNG));

        String id = medij.path("medijId").asString();
        assertEquals(id, UUID.fromString(id).toString());
        assertEquals("PRILOG_SLIKA", medij.path("vrsta").asString());
        assertEquals("image/png", medij.path("mime").asString());
        assertEquals(PNG.length, medij.path("velicina").asLong());
        assertEquals(Set.of("medijId", "vrsta", "mime", "velicina"), Set.copyOf(medij.propertyNames()));
        assertEquals("PRILOG_SLIKA", vrstaReda(id));
        assertArrayEquals(PNG, medijService.bajtovi(id));
        verify(budzet).trosiUpload("test", PNG.length);
    }

    @Test
    void pdfJePrilogPdf() throws Exception {
        JsonNode medij = uspeh("/mcp/prilozi", new MockMultipartFile("fajl", ime + ".bin", "application/octet-stream", PDF));

        assertEquals("PRILOG_PDF", medij.path("vrsta").asString());
        assertEquals("application/pdf", medij.path("mime").asString());
        assertEquals(VrstaMedija.PRILOG_PDF.name(), vrstaReda(medij.path("medijId").asString()));
    }

    @Test
    void slikaSlajdaJeSlika() throws Exception {
        JsonNode png = uspeh("/mcp/slike", new MockMultipartFile("fajl", ime + ".txt", "text/plain", PNG));
        JsonNode gif = uspeh("/mcp/slike", new MockMultipartFile("fajl", ime + ".gif", "image/gif", GIF));

        assertEquals("SLIKA", png.path("vrsta").asString());
        assertEquals("image/png", png.path("mime").asString());
        assertEquals("SLIKA", gif.path("vrsta").asString());
        assertEquals("image/gif", gif.path("mime").asString());
        assertEquals("SLIKA", vrstaReda(png.path("medijId").asString()));
        verify(budzet).trosiUpload("test", GIF.length);
    }

    @Test
    void htmlSaDeklarisanomSlikomJe400() throws Exception {
        odbijeno(posalji("/mcp/slike", new MockMultipartFile("fajl", ime + ".png", "image/png", HTML), serija),
                400, "Dozvoljene su samo slike PNG, JPEG, GIF i WebP.");
        // PDF nije slika slajda, GIF nije prilog (LaTeX ga ne uključuje)
        odbijeno(posalji("/mcp/slike", new MockMultipartFile("fajl", ime + ".pdf", "application/pdf", PDF), serija),
                400, "Dozvoljene su samo slike PNG, JPEG, GIF i WebP.");
        odbijeno(posalji("/mcp/prilozi", new MockMultipartFile("fajl", ime + ".gif", "image/gif", GIF), serija),
                400, "Dozvoljeni prilozi: PNG, JPEG i PDF.");
        odbijeno(posalji("/mcp/prilozi", new MockMultipartFile("fajl", ime + ".png", "image/png", new byte[0]), serija),
                400, "Fajl je prazan.");
        assertEquals(List.of(), redoviTesta());
    }

    @Test
    void serijaVanObrascaIliBezNjeJe400BezTroska() throws Exception {
        for (String putanja : List.of("/mcp/prilozi", "/mcp/slike")) {
            for (String losa : new String[]{"X", "ab", "Velika-Slova", "a_b", "a".repeat(61), "", null}) {
                odbijeno(posalji(putanja, new MockMultipartFile("fajl", ime + ".png", "image/png", PNG), losa),
                        400, Serija.PORUKA);
            }
        }
        assertEquals(List.of(), redoviTesta());
        verify(budzet, never()).trosiUpload(anyString(), anyLong());
    }

    @Test
    void bezFajlaJe400() throws Exception {
        MockHttpServletResponse odgovor = posalji("/mcp/prilozi",
                new MockMultipartFile("drugo", ime + ".pdf", "application/pdf", PDF), serija);
        assertEquals(400, odgovor.getStatus(), odgovor.getContentAsString());
        assertEquals(List.of(), redoviTesta());
    }

    @Test
    void potrosenBudzetJe429IFajlNijeUpisan() throws Exception {
        doThrow(new SystemException(McpBudzet.PORUKA, 429)).when(budzet).trosiUpload(anyString(), anyLong());

        odbijeno(posalji("/mcp/prilozi", new MockMultipartFile("fajl", ime + ".pdf", "application/pdf", PDF), serija),
                429, "Dnevni limit za Kloda je potrošen.");
        odbijeno(posalji("/mcp/slike", new MockMultipartFile("fajl", ime + ".png", "image/png", PNG), serija),
                429, "Dnevni limit za Kloda je potrošen.");

        verify(budzet).trosiUpload("test", PDF.length);
        verify(budzet).trosiUpload("test", PNG.length);
        assertEquals(List.of(), redoviTesta());
    }
}
