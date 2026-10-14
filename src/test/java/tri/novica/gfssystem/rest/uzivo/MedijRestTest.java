package tri.novica.gfssystem.rest.uzivo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import tri.novica.gfssystem.advice.UploadExceptionHandler;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.uzivo.MedijService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({MedijRest.class, PublicMedijRest.class, UploadExceptionHandler.class})
class MedijRestTest {

    static final String ID = "3f1c2b9a-6d7e-4c1a-9b2f-0a1b2c3d4e5f";
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @Autowired MockMvc mvc;
    @MockitoBean MedijService service;

    @Test
    void uploadVraca201IJson() throws Exception {
        when(service.sacuvaj(any())).thenReturn(new MedijInfo(ID, "tabla.png", "image/png", PNG.length));
        mvc.perform(multipart("/mediji").file(new MockMultipartFile("fajl", "tabla.png", "image/png", PNG)))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(ID))
           .andExpect(jsonPath("$.naziv").value("tabla.png"))
           .andExpect(jsonPath("$.mime").value("image/png"))
           .andExpect(jsonPath("$.velicina").value(PNG.length));
    }

    @Test
    void getVracaBajtoveSaNosniffIImmutable() throws Exception {
        when(service.ucitaj(ID)).thenReturn(new MedijService.Fajl("image/png", PNG.length, new ByteArrayResource(PNG)));
        mvc.perform(get("/public/mediji/" + ID))
           .andExpect(status().isOk())
           .andExpect(content().contentType("image/png"))
           .andExpect(content().bytes(PNG))
           .andExpect(header().string("X-Content-Type-Options", "nosniff"))
           .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"))
           .andExpect(header().string("Content-Disposition", "inline"));
    }

    @Test
    void nepostojecaSlikaJe404() throws Exception {
        when(service.ucitaj("nema")).thenThrow(new SystemException(MedijService.NIJE_PRONADJENA, HttpStatus.NOT_FOUND));
        mvc.perform(get("/public/mediji/nema"))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.reason").value("Slika nije pronađena."));
    }

    @Test
    void prevelikFajlJe400SaPorukom() throws Exception {
        when(service.sacuvaj(any())).thenThrow(new MaxUploadSizeExceededException(10L * 1024 * 1024));
        mvc.perform(multipart("/mediji").file(new MockMultipartFile("fajl", "velika.png", "image/png", PNG)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Slika je veća od 10 MB."))
           .andExpect(jsonPath("$.time").exists());
    }

    @Test
    void neispravanUploadJe400() throws Exception {
        when(service.sacuvaj(any())).thenThrow(new MultipartException("pokvaren multipart"));
        mvc.perform(multipart("/mediji").file(new MockMultipartFile("fajl", "a.png", "image/png", PNG)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Neispravan upload."));
    }

    @Test
    void uploadBezFajlaJe400ANe500() throws Exception {
        mvc.perform(multipart("/mediji").file(new MockMultipartFile("drugo", "a.png", "image/png", PNG)))
           .andExpect(status().isBadRequest());
    }
}
