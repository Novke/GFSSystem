package tri.novica.gfssystem.rest.uzivo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tri.novica.gfssystem.dto.uzivo.SlajdDetails;
import tri.novica.gfssystem.entity.uzivo.TipSlajda;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.uzivo.PrezentacijaService;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** {@code GET /slajdovi/{id}}: pun slajd (front ga koristi u A2), 404 poruka servisa. */
@WebMvcTest(SlajdRest.class)
class SlajdRestTest {

    static final SlajdDetails SLAJD = new SlajdDetails(1L, 3, TipSlajda.INFO, "Uvod",
            "![a](api/public/mediji/3f1c2b9a-6d7e-4c1a-9b2f-0a1b2c3d4e5f)", null, "beleška", true, null, null, null);

    @Autowired MockMvc mvc;
    @MockitoBean PrezentacijaService service;

    @Test
    void slajdVraca200() throws Exception {
        when(service.slajd(1L)).thenReturn(SLAJD);
        mvc.perform(get("/slajdovi/1"))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.id").value(1))
           .andExpect(jsonPath("$.rb").value(3))
           .andExpect(jsonPath("$.tip").value("INFO"))
           .andExpect(jsonPath("$.sadrzaj").value(SLAJD.sadrzaj()))
           .andExpect(jsonPath("$.postepeno").value(true))
           .andExpect(jsonPath("$.platno").value(nullValue()));
        verify(service).slajd(1L);
    }

    @Test
    void nepostojeciSlajdJe404() throws Exception {
        when(service.slajd(99L)).thenThrow(new SystemException("Slajd nije pronađen.", HttpStatus.NOT_FOUND));
        mvc.perform(get("/slajdovi/99"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.reason").value("Slajd nije pronađen."));
    }
}
