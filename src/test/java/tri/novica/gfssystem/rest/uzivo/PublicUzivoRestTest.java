package tri.novica.gfssystem.rest.uzivo;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tri.novica.gfssystem.dto.uzivo.JavnoIzvodjenjeInfo;
import tri.novica.gfssystem.dto.uzivo.UcesnikInfo;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.uzivo.UcesnikService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PublicUzivoRest.class)
class PublicUzivoRestTest {

    static final String KOD = "123456";
    static final String TOKEN = "abcdefghjkmnpqrstuvwxyzABCDEFGH2";
    static final String NEPOZNAT = "Izvođenje sa ovim kodom ne postoji ili je završeno.";

    @Autowired MockMvc mvc;
    @MockitoBean UcesnikService service;

    void prijavaUspeva() {
        when(service.prijavi(KOD, "Ana"))
                .thenReturn(new UcesnikService.Prijavljen(new UcesnikInfo(31L, "Ana", 5L), TOKEN));
    }

    String setCookie(MvcResult r) {
        String c = r.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertNotNull(c);
        return c;
    }

    @Test
    void konstantaKolacica() {
        assertEquals("gfs_uzivo", PublicUzivoRest.KOLACIC);
    }

    @Test
    void infoPoKodu() throws Exception {
        when(service.info(KOD)).thenReturn(new JavnoIzvodjenjeInfo("Statika 1"));
        mvc.perform(get("/public/uzivo/" + KOD))
           .andExpect(status().isOk())
           .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
           .andExpect(jsonPath("$.naziv").value("Statika 1"));
    }

    @Test
    void nepoznatKodJe404SaRazlogom() throws Exception {
        when(service.info(anyString())).thenThrow(new SystemException(NEPOZNAT, HttpStatus.NOT_FOUND));
        mvc.perform(get("/public/uzivo/999999"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.reason").value(NEPOZNAT));
    }

    @Test
    void prijavaPostavljaKolacicBezSecureBezZaglavlja() throws Exception {
        prijavaUspeva();
        MvcResult r = mvc.perform(post("/public/uzivo/" + KOD + "/prijava")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ime\":\"Ana\"}"))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.ucesnikId").value(31))
           .andExpect(jsonPath("$.ime").value("Ana"))
           .andExpect(jsonPath("$.izvodjenjeId").value(5))
           .andExpect(jsonPath("$.token").doesNotExist())
           .andReturn();

        String c = setCookie(r);
        assertTrue(c.startsWith("gfs_uzivo=" + TOKEN + ";"), c);
        assertTrue(c.contains("HttpOnly"), c);
        assertTrue(c.contains("SameSite=Lax"), c);
        assertTrue(c.contains("Path=/;") || c.endsWith("Path=/"), c);
        assertTrue(c.contains("Max-Age=43200"), c);
        assertFalse(c.contains("Secure"), c);
        // token je samo u kolačiću, ne i u telu
        assertFalse(r.getResponse().getContentAsString().contains(TOKEN));
    }

    @Test
    void prijavaPrekoHttpsJeSecure() throws Exception {
        prijavaUspeva();
        MvcResult r = mvc.perform(post("/public/uzivo/" + KOD + "/prijava").header("X-Forwarded-Proto", "https")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ime\":\"Ana\"}"))
           .andExpect(status().isCreated())
           .andReturn();
        String c = setCookie(r);
        assertTrue(c.contains("Secure"), c);
        assertTrue(c.contains("HttpOnly"), c);
    }

    @Test
    void prijavaPrekoHttpNijeSecure() throws Exception {
        prijavaUspeva();
        MvcResult r = mvc.perform(post("/public/uzivo/" + KOD + "/prijava").header("X-Forwarded-Proto", "http")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ime\":\"Ana\"}"))
           .andExpect(status().isCreated())
           .andReturn();
        assertFalse(setCookie(r).contains("Secure"));
    }

    @Test
    void punoIzvodjenjeJe409() throws Exception {
        when(service.prijavi(any(), any())).thenThrow(new SystemException("Izvođenje je popunjeno.", HttpStatus.CONFLICT));
        mvc.perform(post("/public/uzivo/" + KOD + "/prijava")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ime\":\"Ana\"}"))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.reason").value("Izvođenje je popunjeno."))
           .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void prijavaNaNepoznatKodJe404() throws Exception {
        when(service.prijavi(any(), any())).thenThrow(new SystemException(NEPOZNAT, HttpStatus.NOT_FOUND));
        mvc.perform(post("/public/uzivo/999999/prijava")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ime\":\"Ana\"}"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.reason").value(NEPOZNAT))
           .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void losJsonJe400() throws Exception {
        for (String telo : new String[]{"{\"ime\":", "", "null", "{\"ime\": {\"a\": 1}}"}) {
            mvc.perform(post("/public/uzivo/" + KOD + "/prijava")
                            .contentType(MediaType.APPLICATION_JSON).content(telo))
               .andExpect(status().isBadRequest())
               .andExpect(jsonPath("$.reason").value("Neispravan format podataka."));
        }
        verify(service, never()).prijavi(any(), any());
    }

    @Test
    void jaBezKolacicaJe404() throws Exception {
        mvc.perform(get("/public/uzivo/" + KOD + "/ja"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.reason").value("Nisi prijavljen."));
        verify(service, never()).ja(any(), any());
    }

    @Test
    void jaSaNevazecimKolacicemJe404() throws Exception {
        when(service.ja(KOD, TOKEN)).thenReturn(Optional.empty());
        mvc.perform(get("/public/uzivo/" + KOD + "/ja").cookie(new Cookie("gfs_uzivo", TOKEN)))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.reason").value("Nisi prijavljen."));
    }

    @Test
    void jaSaVazecimKolacicem() throws Exception {
        when(service.ja(KOD, TOKEN)).thenReturn(Optional.of(new UcesnikInfo(31L, "Ana", 5L)));
        mvc.perform(get("/public/uzivo/" + KOD + "/ja").cookie(new Cookie("gfs_uzivo", TOKEN)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.ucesnikId").value(31))
           .andExpect(jsonPath("$.ime").value("Ana"))
           .andExpect(jsonPath("$.izvodjenjeId").value(5));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void prijavaSeLogujeSaIpBezImena(CapturedOutput izlaz) throws Exception {
        prijavaUspeva();
        mvc.perform(post("/public/uzivo/" + KOD + "/prijava").header("X-Forwarded-For", "203.0.113.9, 172.18.0.3")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ime\":\"Ana\"}"))
           .andExpect(status().isCreated());
        String linija = izlaz.getOut().lines().filter(l -> l.contains("Uživo prijava:")).findFirst().orElseThrow();
        assertTrue(linija.contains("Uživo prijava: izvodjenje=5, ucesnik=31, ip=203.0.113.9"), linija);
        assertFalse(linija.contains("Ana"), linija);
        assertFalse(linija.contains(TOKEN), linija);
    }
}
