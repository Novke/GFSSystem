package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MedijService} nad pravom MySQL bazom: vrsta se upisuje i čita iz kolone {@code mediji.vrsta}, a
 * {@code obrisi} je tih i za red koji ne postoji (ono što Mockito repozitorijum ne može da potvrdi). Bez
 * {@code @Transactional}: svaki poziv servisa se zaista commit-uje, a test za sobom briše ono što je upisao.
 */
@SpringBootTest
class MedijServiceDbTest {

    static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);
    static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"/>".getBytes(StandardCharsets.UTF_8);

    @Autowired MedijService service;
    @Autowired MedijRepository medijRepository;

    @Test
    void prilogIRenderSeCuvajuSaVrstomIBrisuSeTiho() {
        MedijInfo prilog = service.sacuvajPrilog(new MockMultipartFile("fajl", "grafikon.pdf", "application/octet-stream", PDF));
        MedijInfo render = service.sacuvajRender(SVG, "zadatak.svg");
        try {
            assertEquals(VrstaMedija.PRILOG_PDF, medijRepository.findById(prilog.id()).orElseThrow().getVrsta());
            assertEquals(VrstaMedija.RENDER_SVG, medijRepository.findById(render.id()).orElseThrow().getVrsta());
            assertEquals(VrstaMedija.PRILOG_PDF, service.ucitaj(prilog.id()).vrsta());
            assertArrayEquals(PDF, service.bajtovi(prilog.id()));
            assertArrayEquals(SVG, service.bajtovi(render.id()));
        } finally {
            service.obrisi(prilog.id());
            service.obrisi(render.id());
        }

        assertFalse(medijRepository.existsById(prilog.id()));
        assertFalse(medijRepository.existsById(render.id()));
        assertEquals(404, assertThrows(SystemException.class, () -> service.bajtovi(prilog.id())).getCode());

        // drugi put i za id koji nikad nije postojao: bez izuzetka
        assertDoesNotThrow(() -> service.obrisi(prilog.id()));
        assertDoesNotThrow(() -> service.obrisi(UUID.randomUUID().toString()));
    }
}
