package tri.novica.gfssystem.rest.banka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tri.novica.gfssystem.dto.banka.RenderujSveInfo;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.banka.RenderNedostupan;
import tri.novica.gfssystem.service.banka.VarijantaService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** {@code POST /banka/predmeti/{id}/renderuj-sve}: jedan korak ponovnog rendera cele banke predmeta. */
@WebMvcTest(RenderujSveRest.class)
class RenderujSveRestTest {

    @Autowired MockMvc mvc;
    @MockitoBean VarijantaService service;

    @Test
    void vraca200IBrojace() throws Exception {
        when(service.renderujSve(7L)).thenReturn(new RenderujSveInfo(12, 3, 30));
        mvc.perform(post("/banka/predmeti/7/renderuj-sve"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.renderovano").value(12))
           .andExpect(jsonPath("$.neuspelo").value(3))
           .andExpect(jsonPath("$.preostalo").value(30));
    }

    @Test
    void drugiIstovremeniPozivJe409() throws Exception {
        when(service.renderujSve(7L)).thenThrow(new SystemException("Render cele banke za ovaj predmet je već u toku.", HttpStatus.CONFLICT));
        mvc.perform(post("/banka/predmeti/7/renderuj-sve"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.reason").value("Render cele banke za ovaj predmet je već u toku."));
    }

    @Test
    void nepoznatPredmetJe404() throws Exception {
        when(service.renderujSve(9L)).thenThrow(new SystemException("Predmet nije pronađen.", HttpStatus.NOT_FOUND));
        mvc.perform(post("/banka/predmeti/9/renderuj-sve"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.reason").value("Predmet nije pronađen."));
    }

    @Test
    void renderServisKojiNeRadiJe503() throws Exception {
        when(service.renderujSve(7L)).thenThrow(new RenderNedostupan());
        mvc.perform(post("/banka/predmeti/7/renderuj-sve"))
           .andExpect(status().isServiceUnavailable())
           .andExpect(jsonPath("$.reason").value("Render servis nije dostupan."));
    }

    @Test
    void neceoIdJe400() throws Exception {
        mvc.perform(post("/banka/predmeti/abc/renderuj-sve"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan parametar: id."));
        verifyNoInteractions(service);
    }
}
