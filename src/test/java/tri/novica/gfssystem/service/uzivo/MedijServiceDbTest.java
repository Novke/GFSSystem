package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MedijService} nad pravom MySQL bazom: vrsta se upisuje i čita iz kolone {@code mediji.vrsta}, {@code obrisi} je
 * tih i za red koji ne postoji, a u transakciji briše fajl tek posle commit-a (i nikad posle rollback-a), dok fajl novog
 * medija iz transakcije koja se poništi nestaje (ono što Mockito repozitorijum ne može da potvrdi). Bez
 * {@code @Transactional}: svaki poziv servisa se zaista commit-uje, a test za sobom briše ono što je upisao.
 */
@SpringBootTest
class MedijServiceDbTest {

    static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);
    static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"/>".getBytes(StandardCharsets.UTF_8);

    @Autowired MedijService service;
    @Autowired MedijRepository medijRepository;
    @Autowired TransactionTemplate tx;
    @Value("${gfs.mediji.dir}") String medijiDir;

    boolean postojiFajl(String id) {
        return Files.exists(Path.of(medijiDir).resolve(id));
    }

    @Test
    void uTransakcijiSeFajlBriseTekPosleCommitaANikadPosleRollbacka() {
        String stari = service.sacuvajRender(SVG, "stari.svg").id();
        try {
            // rollback: red i fajl ostaju (red koji pokazuje na obrisan fajl bi se servirao kao 404)
            tx.executeWithoutResult(status -> {
                service.obrisi(stari);
                medijRepository.flush();
                assertTrue(postojiFajl(stari), "fajl se ne briše pre commit-a");
                status.setRollbackOnly();
            });
            assertTrue(medijRepository.existsById(stari));
            assertTrue(postojiFajl(stari));

            // commit: tek tada nestaje i fajl
            tx.executeWithoutResult(status -> {
                service.obrisi(stari);
                medijRepository.flush();
                assertTrue(postojiFajl(stari), "fajl se ne briše pre commit-a");
            });
            assertFalse(medijRepository.existsById(stari));
            assertFalse(postojiFajl(stari));
        } finally {
            service.obrisi(stari);
        }
    }

    @Test
    void fajlNovogMedijaIzPonisteneTransakcijeSeBrise() {
        String[] novi = new String[1];
        tx.executeWithoutResult(status -> {
            novi[0] = service.sacuvajRender(SVG, "novi.svg").id();
            assertTrue(postojiFajl(novi[0]));
            status.setRollbackOnly();
        });
        assertFalse(medijRepository.existsById(novi[0]));
        assertFalse(postojiFajl(novi[0]), "fajl bez reda se nikad ne bi servirao ni obrisao");

        // van transakcije (upload slike) fajl ostaje sa redom kao i do sada
        String van = service.sacuvajRender(SVG, "van.svg").id();
        try {
            assertTrue(postojiFajl(van));
        } finally {
            service.obrisi(van);
            assertFalse(postojiFajl(van));
        }
    }

    @Test
    void slikaPrilogaSeCuvaKaoPrilogSlikaBezMigracije() {
        // vrsta je varchar(20): nova vrednost ne traži migraciju, ddl-auto=validate prolazi, a red se čita nazad
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
        MedijInfo prilog = service.sacuvajPrilog(new MockMultipartFile("fajl", "skica.png", "image/png", png));
        try {
            assertEquals(VrstaMedija.PRILOG_SLIKA, medijRepository.findById(prilog.id()).orElseThrow().getVrsta());
            assertEquals(VrstaMedija.PRILOG_SLIKA, service.ucitaj(prilog.id()).vrsta());
            assertEquals(java.util.Optional.of(VrstaMedija.PRILOG_SLIKA), service.vrsta(prilog.id()));
        } finally {
            service.obrisi(prilog.id());
        }
    }

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
