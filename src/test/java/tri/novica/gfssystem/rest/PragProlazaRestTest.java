package tri.novica.gfssystem.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tri.novica.gfssystem.dto.test.PragProlazaCmd;
import tri.novica.gfssystem.dto.test.TestDetails;
import tri.novica.gfssystem.service.TestService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** PATCH /test/{id}/prag-prolaza: ključ je obavezan (nema tihog brisanja praga zbog {} ili greške u kucanju), decimalni broj nije ceo. */
@WebMvcTest(TestRest.class)
class PragProlazaRestTest {

    static final String NEISPRAVAN = "Neispravan format podataka.";

    @Autowired MockMvc mvc;
    @MockitoBean TestService testovi;

    private ResultActions patchJson(String body) throws Exception {
        return mvc.perform(patch("/test/10/prag-prolaza").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void odbijeno(String body) throws Exception {
        patchJson(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value(NEISPRAVAN))
                .andExpect(jsonPath("$.time").exists());
        verify(testovi, never()).postaviPragProlaza(any(), any());
    }

    @Test
    void praznoTeloJe400() throws Exception {
        odbijeno("{}");
    }

    @Test
    void greskaUKucanjuKljucaJe400() throws Exception {
        odbijeno("{\"pragProlazaa\": 20}");
    }

    @Test
    void nepoznatoPoljeUzPragJe400() throws Exception {
        odbijeno("{\"pragProlaza\": 20, \"maxPoena\": 5}");
    }

    @Test
    void decimalniBrojJe400() throws Exception {
        odbijeno("{\"pragProlaza\": 20.5}");
        odbijeno("{\"pragProlaza\": 20.0}");
    }

    @Test
    void tekstJe400() throws Exception {
        odbijeno("{\"pragProlaza\": \"dvadeset\"}");
    }

    @Test
    void negativanPragJe400SaPorukom() throws Exception {
        patchJson("{\"pragProlaza\": -1}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Prag prolaza mora biti između 0 i maksimalnog broja poena."));
        verify(testovi, never()).postaviPragProlaza(any(), any());
    }

    @Test
    void celBrojSePostavlja() throws Exception {
        TestDetails d = new TestDetails();
        d.setPragProlaza(20);
        when(testovi.postaviPragProlaza(eq(10L), any(PragProlazaCmd.class))).thenReturn(d);
        patchJson("{\"pragProlaza\": 20}").andExpect(status().isOk()).andExpect(jsonPath("$.pragProlaza").value(20));
        verify(testovi).postaviPragProlaza(10L, new PragProlazaCmd(20));
    }

    @Test
    void eksplicitniNullUklanjaPrag() throws Exception {
        when(testovi.postaviPragProlaza(eq(10L), any(PragProlazaCmd.class))).thenReturn(new TestDetails());
        patchJson("{\"pragProlaza\": null}").andExpect(status().isOk());
        verify(testovi).postaviPragProlaza(10L, new PragProlazaCmd(null));
    }
}
