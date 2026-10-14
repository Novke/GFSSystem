package tri.novica.gfssystem.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tri.novica.gfssystem.dto.grupa.KontaktiFajl;
import tri.novica.gfssystem.dto.grupa.WaLinkCmd;
import tri.novica.gfssystem.dto.grupa.WaLinkInfo;
import tri.novica.gfssystem.service.GrupaService;
import tri.novica.gfssystem.service.PregledGrupeService;
import tri.novica.gfssystem.service.StudentService;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** PATCH /grupe/{id}/wa-link (ključ obavezan, nepoznata polja 400) i GET /grupe/{id}/kontakti.vcf (vCard za preuzimanje). */
@WebMvcTest(GrupaRest.class)
class GrupaWaKontaktiRestTest {

    @Autowired MockMvc mvc;
    @MockitoBean GrupaService grupe;
    @MockitoBean StudentService studenti;
    @MockitoBean PregledGrupeService pregledi;

    private void odbijeno(String body) throws Exception {
        mvc.perform(patch("/grupe/5/wa-link").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Neispravan format podataka."));
        verify(grupe, never()).postaviWaLink(any(), any());
    }

    @Test
    void teloBezKljucaIliSaNepoznatimPoljemJe400() throws Exception {
        odbijeno("{}");
        odbijeno("{\"walink\": \"https://chat.whatsapp.com/AbCdEfGhIj1234567890Kl\"}");
        odbijeno("{\"waLink\": null, \"naziv\": \"x\"}");
    }

    @Test
    void linkSePostavljaINullGaUklanja() throws Exception {
        when(grupe.postaviWaLink(eq(5L), any())).thenReturn(new WaLinkInfo("https://chat.whatsapp.com/AbCdEfGhIj1234567890Kl"));
        mvc.perform(patch("/grupe/5/wa-link").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"waLink\": \"chat.whatsapp.com/AbCdEfGhIj1234567890Kl\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waLink").value("https://chat.whatsapp.com/AbCdEfGhIj1234567890Kl"));
        verify(grupe).postaviWaLink(5L, new WaLinkCmd("chat.whatsapp.com/AbCdEfGhIj1234567890Kl"));

        when(grupe.postaviWaLink(eq(5L), any())).thenReturn(new WaLinkInfo(null));
        mvc.perform(patch("/grupe/5/wa-link").contentType(MediaType.APPLICATION_JSON).content("{\"waLink\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waLink").doesNotExist());
        verify(grupe).postaviWaLink(5L, new WaLinkCmd(null));
    }

    @Test
    void predugackiLinkJe400SaPorukom() throws Exception {
        mvc.perform(patch("/grupe/5/wa-link").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"waLink\": \"" + "a".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("Link je predugačak."));
        verify(grupe, never()).postaviWaLink(any(), any());
    }

    @Test
    void kontaktiSuVCardZaPreuzimanje() throws Exception {
        when(grupe.kontakti(5L)).thenReturn(new KontaktiFajl("kontakti-GD-2026.vcf", "BEGIN:VCARD\r\nN:Šović;Đorđe;;;\r\nEND:VCARD\r\n"));
        mvc.perform(get("/grupe/5/kontakti.vcf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/vcard;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("kontakti-GD-2026.vcf")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes("BEGIN:VCARD\r\nN:Šović;Đorđe;;;\r\nEND:VCARD\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
