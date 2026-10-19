package tri.novica.gfssystem.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tri.novica.gfssystem.dto.banka.*;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.banka.Namena;
import tri.novica.gfssystem.service.banka.GrupaZadatakaService;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Dnevnik MCP-a bez tajni (spec MCP 2, "Dnevnik"): ni odbijen zahtev (bez ključa, sa pogrešnim ključem ispravnog oblika)
 * ni uspešan upis kroz pravi {@code /mcp} ne ostavljaju u izlazu ključ ({@code gfs_} + 43 znaka), zaglavlje
 * {@code Authorization} ({@code Bearer}) ni sadržaj iz argumenata alata (LaTeX teksta i rešenja, naziv); uspeh ostavlja
 * jedan INFO red sa alatom i oznakom ključa.
 */
@ExtendWith(OutputCaptureExtension.class)
class McpDnevnikDbTest extends McpDbTestOsnova {

    static final Pattern KLJUC_U_LOGU = Pattern.compile("gfs_[A-Za-z0-9_-]{43}");
    /** Ispravnog oblika, ali nije u {@code gfs.mcp.kljucevi} testa (drugi ključ iz Ugovora, za rotaciju). */
    static final String POGRESAN_KLJUC = "gfs_DRUGI11111111111111111111111111111111111111";
    static final String TAJNA = "TAJNI-SADRZAJ-42";
    static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes();
    static final Polje BROJ_A = new Polje("a", "a) binarni u dekadni", TipPolja.BROJ, null, null, null, null, null, null);

    @Autowired GrupaZadatakaService grupaService;

    Predmet predmet;
    GrupaDetails list;

    @BeforeEach
    void setUp() {
        when(latexRender.renderuj(anyList(), anyString(), anyList())).thenAnswer(inv -> {
            List<String> formati = inv.getArgument(0);
            String b64 = Base64.getEncoder().encodeToString(SVG);
            return new RenderOdgovor(true, formati.contains("svg") ? b64 : null, null, null, null, 7L, false,
                    formati.contains("png") ? Base64.getEncoder().encodeToString(VarijanteAlatiDbTest.png(20, 10)) : null);
        });
        predmet = noviPredmet("Dnevnik MCP test");
        GrupaDetails koren = grupaService.kreiraj(new GrupaCmd(predmet.getId(), null, Namena.PREDAVANJE, "Brojni sistemi",
                null, null, null, null));
        list = grupaService.kreiraj(new GrupaCmd(null, koren.id(), null, "2 → 10", null, 2, null, List.of(BROJ_A)));
    }

    static void bezTajni(String izlaz) {
        assertFalse(KLJUC_U_LOGU.matcher(izlaz).find(), "ključ u logu");
        assertFalse(izlaz.contains("Bearer"), "zaglavlje Authorization u logu");
        assertFalse(izlaz.contains(TAJNA), "sadržaj argumenata u logu");
    }

    @Test
    void odbijenZahtevNeOstavljaKljuc(CapturedOutput izlaz) {
        Map<String, Object> poziv = Map.of("name", "kontekst", "arguments", Map.of());
        MockHttpServletResponse bez = mcp.posalji("tools/call", poziv, null);
        MockHttpServletResponse pogresan = mcp.posalji("tools/call", poziv, POGRESAN_KLJUC);
        assertEquals(401, bez.getStatus());
        assertEquals(401, pogresan.getStatus());
        assertEquals(2, izlaz.getOut().lines().filter(l -> l.contains("MCP zahtev odbijen")).count(), izlaz.getOut());
        assertFalse(izlaz.getOut().contains("MCP alat="), "odbijen zahtev ne stiže do alata");
        bezTajni(izlaz.getAll());
    }

    @Test
    void uspesanUpisBeleziAlatIOznakuBezSadrzaja(CapturedOutput izlaz) {
        String serija = "dnevnik-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode r = mcp.pozovi("napravi_varijantu", Map.of("serija", serija, "grupaId", list.id(),
                "naziv", "Zbirka " + TAJNA, "tekst", "Pretvori $1011_{(2)}$ u dekadni. " + TAJNA,
                "resenje", "Rešenje: $11$. " + TAJNA, "odgovori", Map.of("a", Map.of("broj", 11))));
        assertFalse(McpTestKlijent.greska(r), r.toString());
        Long id = McpTestKlijent.json(r).path("varijanta").path("id").asLong();
        assertTrue(McpTestKlijent.tekst(r).contains(TAJNA), "sadržaj je u odgovoru Klodu, ne u logu");

        List<String> redovi = izlaz.getOut().lines().filter(l -> l.contains("MCP alat=napravi_varijantu")).toList();
        assertEquals(1, redovi.size(), izlaz.getOut());
        String red = redovi.get(0);
        assertTrue(red.contains("MCP alat=napravi_varijantu kljuc=test"), red);
        assertTrue(red.contains("serija=" + serija), red);
        assertTrue(red.contains("varijanta=" + id), red);
        assertTrue(red.contains("ishod=ok"), red);
        bezTajni(izlaz.getAll());
    }
}
