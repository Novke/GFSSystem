package tri.novica.gfssystem.rest.fajlovi;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;
import tri.novica.gfssystem.service.uzivo.MedijService;

import static org.junit.jupiter.api.Assertions.*;

class PreuzimanjeTest {

    static final byte[] PK = {0x50, 0x4B, 3, 4, 1, 2, 3};

    ResponseEntity<Resource> odgovor(String naziv) {
        return Preuzimanje.odgovor(naziv, new MedijService.Fajl("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                PK.length, new ByteArrayResource(PK), VrstaMedija.FAJL_PREZENTACIJE));
    }

    @Test
    void zaglavljaPreuzimanja() {
        HttpHeaders h = odgovor("plan.docx").getHeaders();
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", h.getContentType().toString());
        assertEquals("nosniff", h.getFirst("X-Content-Type-Options"));
        assertEquals("sandbox", h.getFirst("Content-Security-Policy"));
        assertEquals("no-store", h.getFirst(HttpHeaders.CACHE_CONTROL));
        assertEquals(PK.length, h.getContentLength());
        assertTrue(h.getFirst(HttpHeaders.CONTENT_DISPOSITION).startsWith("attachment;"));
    }

    @Test
    void imeSaDijakriticimaIRazmakomJeUtf8() {
        String disp = odgovor("Vežbe čćžšđ 7.docx").getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disp.contains("filename=\"Vezbe cczsdj 7.docx\""), disp);
        assertTrue(disp.contains("filename*=UTF-8''Ve%C5%BEbe%20%C4%8D%C4%87%C5%BE%C5%A1%C4%91%207.docx"), disp);
        // Spring ga čita nazad kao isto ime
        assertEquals("Vežbe čćžšđ 7.docx", ContentDisposition.parse(disp).getFilename());
    }

    @Test
    void navodniciIKontrolniZnakoviNeLomeZaglavlje() {
        String disp = odgovor("a\"b.docx").getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disp.contains("filename=\"a_b.docx\""), disp);
        assertTrue(disp.contains("filename*=UTF-8''a%22b.docx"), disp);
    }
}
