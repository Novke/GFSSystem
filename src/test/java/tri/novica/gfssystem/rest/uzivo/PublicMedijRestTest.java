package tri.novica.gfssystem.rest.uzivo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.uzivo.MedijService;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Javno serviranje po vrsti medija: slika kao do sada, render SVG sa CSP-om, PDF prilog nikad. */
@WebMvcTest(PublicMedijRest.class)
class PublicMedijRestTest {

    static final String ID = "3f1c2b9a-6d7e-4c1a-9b2f-0a1b2c3d4e5f";
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);
    static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"><rect width=\"10\" height=\"10\"/></svg>"
            .getBytes(StandardCharsets.UTF_8);

    @Autowired MockMvc mvc;
    @MockitoBean MedijService service;

    @Test
    void pdfPrilogNijeJavanIJe404KaoNepoznatId() throws Exception {
        when(service.ucitaj(ID)).thenReturn(new MedijService.Fajl("application/pdf", PDF.length, new ByteArrayResource(PDF), VrstaMedija.PRILOG_PDF));
        mvc.perform(get("/public/mediji/" + ID))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.reason").value("Slika nije pronađena."))
           .andExpect(header().doesNotExist("Content-Security-Policy"));
    }

    @Test
    void nepoznatIdImaIstuPorukuKaoPdfPrilog() throws Exception {
        when(service.ucitaj(ID)).thenThrow(new SystemException(MedijService.NIJE_PRONADJENA, HttpStatus.NOT_FOUND));
        mvc.perform(get("/public/mediji/" + ID))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.reason").value("Slika nije pronađena."));
    }

    @Test
    void renderSvgSeServiraSaCspINosniff() throws Exception {
        when(service.ucitaj(ID)).thenReturn(new MedijService.Fajl("image/svg+xml", SVG.length, new ByteArrayResource(SVG), VrstaMedija.RENDER_SVG));
        mvc.perform(get("/public/mediji/" + ID))
           .andExpect(status().isOk())
           .andExpect(content().contentType("image/svg+xml"))
           .andExpect(content().bytes(SVG))
           .andExpect(header().string("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'"))
           .andExpect(header().string("X-Content-Type-Options", "nosniff"))
           .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"))
           .andExpect(header().string("Content-Disposition", "inline"));
    }

    @Test
    void slikaSeServiraKaoDoSadaBezCsp() throws Exception {
        when(service.ucitaj(ID)).thenReturn(new MedijService.Fajl("image/png", PNG.length, new ByteArrayResource(PNG), VrstaMedija.SLIKA));
        mvc.perform(get("/public/mediji/" + ID))
           .andExpect(status().isOk())
           .andExpect(content().contentType("image/png"))
           .andExpect(content().bytes(PNG))
           .andExpect(header().doesNotExist("Content-Security-Policy"))
           .andExpect(header().string("X-Content-Type-Options", "nosniff"))
           .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"))
           .andExpect(header().string("Content-Disposition", "inline"));
    }
}
