package tri.novica.gfssystem.rest.banka;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.uzivo.MedijService;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PrilogRest.class)
class PrilogRestTest {

    static final String ID = "3f1c2b9a-6d7e-4c1a-9b2f-0a1b2c3d4e5f";
    static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);

    @Autowired MockMvc mvc;
    @MockitoBean MedijService service;

    @Test
    void uploadPrilogaVraca201IMedijInfo() throws Exception {
        when(service.sacuvajPrilog(any())).thenReturn(new MedijInfo(ID, "grafikon.pdf", "application/pdf", PDF.length));
        mvc.perform(multipart("/banka/prilozi").file(new MockMultipartFile("fajl", "grafikon.pdf", "application/pdf", PDF)))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.id").value(ID))
           .andExpect(jsonPath("$.naziv").value("grafikon.pdf"))
           .andExpect(jsonPath("$.mime").value("application/pdf"))
           .andExpect(jsonPath("$.velicina").value(PDF.length));
    }

    @Test
    void nedozvoljenTipJe400SaPorukomServisa() throws Exception {
        when(service.sacuvajPrilog(any())).thenThrow(new SystemException(MedijService.NEDOZVOLJEN_PRILOG, HttpStatus.BAD_REQUEST));
        mvc.perform(multipart("/banka/prilozi").file(new MockMultipartFile("fajl", "a.gif", "image/gif", new byte[]{1, 2, 3})))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").value("Dozvoljeni prilozi: PNG, JPEG i PDF."));
    }

    @Test
    void prevelikPrilogJe400SaJsonom() throws Exception {
        // UploadExceptionHandler nije vezan za putanju: važi i za /banka/prilozi (kao za /mediji)
        when(service.sacuvajPrilog(any())).thenThrow(new MaxUploadSizeExceededException(10L * 1024 * 1024));
        mvc.perform(multipart("/banka/prilozi").file(new MockMultipartFile("fajl", "velika.pdf", "application/pdf", PDF)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.reason").isNotEmpty())
           .andExpect(jsonPath("$.time").exists());
    }

    @Test
    void uploadBezFajlaJe400ANeSaServisom() throws Exception {
        mvc.perform(multipart("/banka/prilozi").file(new MockMultipartFile("drugo", "a.pdf", "application/pdf", PDF)))
           .andExpect(status().isBadRequest());
        verify(service, never()).sacuvajPrilog(any());
    }
}
