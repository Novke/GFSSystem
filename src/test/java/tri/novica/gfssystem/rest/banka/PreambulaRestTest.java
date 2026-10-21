package tri.novica.gfssystem.rest.banka;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tri.novica.gfssystem.dto.banka.PreambulaCmd;
import tri.novica.gfssystem.dto.banka.PreambulaInfo;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.banka.LatexPreambulaService;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** GET i PUT {@code /predmeti/{id}/latex-preambula}: statusi i stroga telo (ključ je obavezan, nepoznato polje je 400). */
@WebMvcTest(PreambulaRest.class)
class PreambulaRestTest {

    static final String NEISPRAVAN = "Neispravan format podataka.";
    static final String DEFAULT = "\\usepackage{amsmath}";

    @Autowired MockMvc mvc;
    @MockitoBean LatexPreambulaService service;

    private ResultActions putJson(String body) throws Exception {
        return mvc.perform(put("/predmeti/7/latex-preambula").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void odbijeno(String body) throws Exception {
        putJson(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value(NEISPRAVAN))
                .andExpect(jsonPath("$.time").exists());
        verify(service, never()).postavi(any(), any());
    }

    @Test
    void getVraca200IPreambulu() throws Exception {
        when(service.preambula(7L)).thenReturn(new PreambulaInfo("\\usepackage{xcolor}", false, DEFAULT));
        mvc.perform(get("/predmeti/7/latex-preambula"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.preambula").value("\\usepackage{xcolor}"))
           .andExpect(jsonPath("$.podrazumevana").value(false))
           .andExpect(jsonPath("$.podrazumevanaPreambula").value(DEFAULT));
    }

    @Test
    void getBezPreambuleDajeNullIPodrazumevanu() throws Exception {
        when(service.preambula(7L)).thenReturn(new PreambulaInfo(null, true, DEFAULT));
        mvc.perform(get("/predmeti/7/latex-preambula"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.preambula").value(nullValue()))
           .andExpect(jsonPath("$.podrazumevana").value(true));
    }

    @Test
    void nepoznatPredmetJe404ZaGetIPut() throws Exception {
        when(service.preambula(9L)).thenThrow(new SystemException("Predmet nije pronađen.", org.springframework.http.HttpStatus.NOT_FOUND));
        when(service.postavi(eq(9L), any())).thenThrow(new SystemException("Predmet nije pronađen.", org.springframework.http.HttpStatus.NOT_FOUND));
        mvc.perform(get("/predmeti/9/latex-preambula"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.reason").value("Predmet nije pronađen."));
        mvc.perform(put("/predmeti/9/latex-preambula").contentType(MediaType.APPLICATION_JSON).content("{\"preambula\":\"x\"}"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.reason").value("Predmet nije pronađen."));
    }

    @Test
    void putVraca200INovoStanje() throws Exception {
        when(service.postavi(eq(7L), any())).thenReturn(new PreambulaInfo("\\usepackage{xcolor}", false, DEFAULT));
        putJson("{\"preambula\":\"\\\\usepackage{xcolor}\"}")
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.preambula").value("\\usepackage{xcolor}"))
           .andExpect(jsonPath("$.podrazumevana").value(false));
        ArgumentCaptor<PreambulaCmd> cmd = ArgumentCaptor.forClass(PreambulaCmd.class);
        verify(service).postavi(eq(7L), cmd.capture());
        assertEquals("\\usepackage{xcolor}", cmd.getValue().preambula());
    }

    @Test
    void putSaNullomJeVracanjeNaPodrazumevanu() throws Exception {
        when(service.postavi(eq(7L), any())).thenReturn(new PreambulaInfo(null, true, DEFAULT));
        putJson("{\"preambula\":null}")
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.preambula").value(nullValue()))
           .andExpect(jsonPath("$.podrazumevana").value(true));
        ArgumentCaptor<PreambulaCmd> cmd = ArgumentCaptor.forClass(PreambulaCmd.class);
        verify(service).postavi(eq(7L), cmd.capture());
        assertNull(cmd.getValue().preambula());
    }

    @Test
    void greskaServisaStizeKaoJeste() throws Exception {
        when(service.postavi(eq(7L), any())).thenThrow(new SystemException("Komanda \\write18 nije dozvoljena u preambuli.",
                org.springframework.http.HttpStatus.BAD_REQUEST));
        putJson("{\"preambula\":\"\\\\write18{ls}\"}")
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Komanda \\write18 nije dozvoljena u preambuli."));
    }

    @Test
    void nepoznatoPoljeJe400() throws Exception {
        odbijeno("{\"preambula\":\"x\",\"jos\":1}");
    }

    @Test
    void praznoTeloIGreskaUKucanjuKljucaSuBezAkcije400() throws Exception {
        // bez obaveznog ključa {} ili "preambulaa" bi tiho obrisali preambulu (i keš celog predmeta)
        odbijeno("{}");
        odbijeno("{\"preambulaa\":\"x\"}");
    }

    @Test
    void pogresanTipPoljaJe400() throws Exception {
        odbijeno("{\"preambula\":[\"x\"]}");
        odbijeno("{\"preambula\":{\"tekst\":\"x\"}}");
    }
}
