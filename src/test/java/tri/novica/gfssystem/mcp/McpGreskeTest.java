package tri.novica.gfssystem.mcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tri.novica.gfssystem.exceptions.SystemException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Izvršenje alata: prevod grešaka u {@code isError}, provera serije pre posla, jedan INFO red po pozivu. */
@ExtendWith(OutputCaptureExtension.class)
class McpGreskeTest {

    static final String SERIJA_PORUKA = "Serija mora imati 3 do 60 malih slova, cifara ili crtica.";

    @AfterEach
    void ocisti() {
        RequestContextHolder.resetRequestAttributes();
        McpGreske.ocisti();
    }

    static String tekst(CallToolResult r) {
        return ((TextContent) r.content().getFirst()).text();
    }

    static CallToolResult ok() {
        return CallToolResult.builder().addTextContent("{}").build();
    }

    @Test
    void systemExceptionJeIsErrorSaPorukom(CapturedOutput izlaz) {
        CallToolResult r = McpGreske.izvrsi("probni", "uvoz-1", poziv -> {
            throw new SystemException("X", 409);
        });
        assertTrue(r.isError());
        assertEquals("X", tekst(r));
        assertTrue(izlaz.getOut().contains("alat=probni"), izlaz.getOut());
        assertTrue(izlaz.getOut().contains("ishod=greska-409"), izlaz.getOut());
    }

    @Test
    void drugiIzuzetakJeSistemskaGreskaBezDetaljaUInfoRedu(CapturedOutput izlaz) {
        CallToolResult r = McpGreske.izvrsi("probni", poziv -> {
            throw new IllegalStateException("tajna");
        });
        assertTrue(r.isError());
        assertEquals("Sistemska greška.", tekst(r));
        String infoRed = izlaz.getOut().lines().filter(l -> l.contains("MCP alat=probni kljuc=")).findFirst().orElseThrow();
        assertFalse(infoRed.contains("tajna"), infoRed);
        assertTrue(infoRed.contains("ishod=sistemska"), infoRed);
    }

    @Test
    void uspehBeleziIdJeveIOznakuKljuca(CapturedOutput izlaz) {
        MockHttpServletRequest zahtev = new MockHttpServletRequest();
        zahtev.setAttribute(McpKljucFilter.OZNAKA, "test");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(zahtev));

        CallToolResult r = McpGreske.izvrsi("probni", "uvoz-kol1-2024", poziv -> {
            assertEquals("probni", poziv.alat());
            assertEquals("uvoz-kol1-2024", poziv.serija());
            poziv.id("varijanta", 12);
            return ok();
        });
        assertFalse(r.isError());
        String infoRed = izlaz.getOut().lines().filter(l -> l.contains("MCP alat=probni kljuc=")).findFirst().orElseThrow();
        assertTrue(infoRed.contains("kljuc=test"), infoRed);
        assertTrue(infoRed.contains("serija=uvoz-kol1-2024"), infoRed);
        assertTrue(infoRed.contains("ids={varijanta=12}"), infoRed);
        assertTrue(infoRed.matches(".*trajanjeMs=\\d+ .*"), infoRed);
        assertTrue(infoRed.endsWith("ishod=ok"), infoRed);
        assertEquals(1, izlaz.getOut().lines().filter(l -> l.contains("MCP alat=")).count());
    }

    @Test
    void serijaVanObrascaIliBezNjeNePozivaPosao(CapturedOutput izlaz) {
        AtomicInteger pozivi = new AtomicInteger();
        for (String serija : new String[]{"Uvoz-1", null, "ab", "x".repeat(61), "uvoz 1"}) {
            CallToolResult r = McpGreske.izvrsi("probni", serija, poziv -> {
                pozivi.incrementAndGet();
                return ok();
            });
            assertTrue(r.isError(), String.valueOf(serija));
            assertEquals(SERIJA_PORUKA, tekst(r));
        }
        assertEquals(0, pozivi.get());
        assertTrue(izlaz.getOut().contains("ishod=greska-400"), izlaz.getOut());
        assertFalse(izlaz.getOut().contains("Uvoz-1"), "neispravna serija se ne beleži doslovno");
    }

    @Test
    void ulazakUAlatSeBelezi() {
        assertFalse(McpGreske.usaoUAlat());
        McpGreske.izvrsi("probni", poziv -> ok());
        assertTrue(McpGreske.usaoUAlat());
        McpGreske.ocisti();
        assertFalse(McpGreske.usaoUAlat());
    }

    @Test
    void greskaJeIsErrorSaTekstom() {
        CallToolResult r = McpGreske.greska("Nepoznato polje 'x'.");
        assertTrue(r.isError());
        assertEquals("Nepoznato polje 'x'.", tekst(r));
    }

    @Test
    void pozivMeriProtekloVreme() {
        McpGreske.izvrsi("probni", poziv -> {
            assertTrue(poziv.proteklo().toMillis() >= 0);
            assertTrue(poziv.imaVremenaZaSliku());
            return ok();
        });
        assertEquals(90, McpPoziv.ROK.toSeconds());
        assertEquals(30, McpPoziv.MAX_ZA_DODATNU_SLIKU.toSeconds());
    }
}
