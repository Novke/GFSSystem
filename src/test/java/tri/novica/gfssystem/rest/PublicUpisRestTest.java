package tri.novica.gfssystem.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.OnboardingService;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PublicUpisRest.class)
class PublicUpisRestTest {

    static final String T = "abcdefghjkmnpqrstuvwxyzABCDEFGH2";
    static final String TELO = """
        {"ime":"Ana","prezime":"Anić","indeks":"GD12","godina":2026,"email":"ana@example.com","brojTelefona":"064123456"}""";

    @Autowired MockMvc mvc;
    @MockBean OnboardingService service;

    @Test
    void validacijaVraca400SaSrpskomPorukom() throws Exception {
        when(service.podnesi(eq(T), any(), any())).thenThrow(new SystemException("Email nije ispravan.", HttpStatus.BAD_REQUEST));
        mvc.perform(post("/public/upis/" + T).contentType(MediaType.APPLICATION_JSON).content(TELO))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Email nije ispravan."));
    }

    @Test
    void losJsonVraca400ANe500() throws Exception {
        mvc.perform(post("/public/upis/" + T).contentType(MediaType.APPLICATION_JSON).content("{\"godina\": \"abc\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan format podataka."));
        mvc.perform(post("/public/upis/" + T).contentType(MediaType.APPLICATION_JSON).content("{\"datumRodjenja\": \"2020-13-45\"}"))
           .andExpect(status().isBadRequest());
        mvc.perform(post("/public/upis/" + T).contentType(MediaType.APPLICATION_JSON).content(""))
           .andExpect(status().isBadRequest());
        mvc.perform(post("/public/upis/" + T).contentType(MediaType.APPLICATION_JSON).content("null"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void zatvorenaSesijaVraca410() throws Exception {
        when(service.podnesi(eq(T), any(), any())).thenThrow(new SystemException(OnboardingService.ZATVORENA, HttpStatus.GONE));
        mvc.perform(post("/public/upis/" + T).contentType(MediaType.APPLICATION_JSON).content(TELO))
           .andExpect(status().isGone());
    }

    @Test
    void nepoznatTokenVraca404() throws Exception {
        when(service.javniInfo(anyString())).thenThrow(new SystemException(OnboardingService.NEPOZNAT_TOKEN, HttpStatus.NOT_FOUND));
        mvc.perform(get("/public/upis/x")).andExpect(status().isNotFound());
    }

    @Test
    void ipIdeIzXForwardedFor() throws Exception {
        when(service.podnesi(eq(T), any(), eq("203.0.113.9"))).thenReturn(new tri.novica.gfssystem.dto.onboarding.PodnetaPrijavaInfo(5L));
        mvc.perform(post("/public/upis/" + T).header("X-Forwarded-For", "203.0.113.9, 172.18.0.3")
                .contentType(MediaType.APPLICATION_JSON).content(TELO))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void neocekivanaGreskaNeOtkrivaPoruku() throws Exception {
        when(service.javniInfo(anyString())).thenThrow(new IllegalStateException("SQL: select * from tajno"));
        mvc.perform(get("/public/upis/" + T))
           .andExpect(status().isInternalServerError())
           .andExpect(jsonPath("$.reason").value("Sistemska greška."))
           .andExpect(content().string(not(containsString("SQL"))));
    }

    @Test
    void nepostojecaJavnaPutanjaVraca404ANe500() throws Exception {
        mvc.perform(get("/public/nesto")).andExpect(status().isNotFound());
    }
}
