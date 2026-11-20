package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.entity.uzivo.Medij;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Fajlovi uz prezentaciju: prepoznavanje (bajtovi i ekstenzija moraju da se slože), pomoćne za vrstu i ime. */
@ExtendWith(MockitoExtension.class)
class MedijServiceFajlTest {

    static final byte[] PK = {0x50, 0x4B, 0x03, 0x04, 0x14, 0, 6, 0, 8, 0};
    static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);
    static final String POREKA = "Dozvoljeni fajlovi: DOCX, XLSX, PPTX, PDF i ZIP.";

    @TempDir Path tmp;
    @Mock MedijRepository medijRepository;
    MedijService service;

    @BeforeEach
    void setUp() {
        service = new MedijService(medijRepository, Clock.systemDefaultZone(), tmp.resolve("mediji").toString());
    }

    @ParameterizedTest
    @CsvSource({
            "plan.docx, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "tabela.XLSX, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "slajdovi.pptx, application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "arhiva.zip, application/zip"})
    void pkSaDozvoljenomEkstenzijom(String naziv, String mime) throws Exception {
        MedijInfo info = service.sacuvajFajl(new MockMultipartFile("fajl", naziv, "application/octet-stream", PK));
        assertEquals(mime, info.mime());
        assertEquals(naziv, info.naziv());
        ArgumentCaptor<Medij> red = ArgumentCaptor.forClass(Medij.class);
        verify(medijRepository).save(red.capture());
        assertEquals(VrstaMedija.FAJL_PREZENTACIJE, red.getValue().getVrsta());
        assertArrayEquals(PK, Files.readAllBytes(tmp.resolve("mediji").resolve(info.id())));
    }

    @Test
    void pdfSePrepoznaje() {
        MedijInfo info = service.sacuvajFajl(new MockMultipartFile("fajl", "tekst.pdf", "text/plain", PDF));
        assertEquals("application/pdf", info.mime());
    }

    @Test
    void bajtoviIzMcpUploadaSePrepoznajuIsto() {
        assertEquals("application/zip", service.sacuvajFajl(PK, "x.zip").mime());
        assertThrows(SystemException.class, () -> service.sacuvajFajl(PK, "x.pdf"));
    }

    @ParameterizedTest
    @CsvSource({"makro.docm", "makro.xlsm", "program.exe", "bez-ekstenzije", "slika.png", "x.pdf", "x.doc"})
    void zabranjeneEkstenzijeSeOdbijaju(String naziv) {
        // x.pdf sa PK bajtovima: ekstenzija i bajtovi se ne slažu
        assertOdbijen(naziv, PK);
    }

    @Test
    void lazniPkBezMagicBajtovaSeOdbija() {
        assertOdbijen("lazni.zip", "ovo nije zip".getBytes(StandardCharsets.UTF_8));
        assertOdbijen("lazni.docx", PDF);
    }

    @Test
    void pdfEkstenzijaSaPkJeOdbijenaIPkEkstenzijaSaPdfBajtovima() {
        assertOdbijen("a.pdf", PK);
        assertOdbijen("a.zip", PDF);
    }

    @Test
    void imeSePutanjomSeSkracujeNaIme() {
        assertEquals("plan.docx",
                service.sacuvajFajl(new MockMultipartFile("fajl", "C:\\Users\\ana\\plan.docx", "x", PK)).naziv());
    }

    void assertOdbijen(String naziv, byte[] sadrzaj) {
        SystemException ex = assertThrows(SystemException.class,
                () -> service.sacuvajFajl(new MockMultipartFile("fajl", naziv, "application/octet-stream", sadrzaj)));
        assertEquals(400, ex.getCode());
        assertEquals(POREKA, ex.getMessage());
        verify(medijRepository, never()).save(any());
    }

    @Test
    void vrstaFajlaIzEkstenzije() {
        assertEquals("DOCX", MedijService.vrstaFajla("Vežbe 7.docx"));
        assertEquals("XLSX", MedijService.vrstaFajla("a.XLSX"));
        assertEquals("PPTX", MedijService.vrstaFajla("a.b.pptx"));
        assertEquals("PDF", MedijService.vrstaFajla("a.pdf"));
        assertEquals("ZIP", MedijService.vrstaFajla("a.zip"));
        assertNull(MedijService.vrstaFajla("a.docm"));
        assertNull(MedijService.vrstaFajla("docx"));
        assertNull(MedijService.vrstaFajla(null));
    }

    @Test
    void normalizacijaZadrzavaEkstenzijuOriginala() {
        assertEquals("Vežbe 7.docx", MedijService.normalizujNaziv("Vežbe 7", "polazni.docx"));
        assertEquals("Vežbe 7.docx", MedijService.normalizujNaziv("Vežbe 7.docx", "polazni.docx"));
        assertEquals("Vežbe 7.DOCX", MedijService.normalizujNaziv("Vežbe 7.DOCX", "polazni.docx"));
        assertEquals("izvestaj.pdf.docx", MedijService.normalizujNaziv("izvestaj.pdf", "polazni.docx"));   // druga ekstenzija: dodaje se
        assertEquals("a.xlsx", MedijService.normalizujNaziv("  a  ", "x.xlsx"));
    }

    @Test
    void normalizacijaMenjaZabranjeneZnakove() {
        assertEquals("a_b_c_d_e_f_g_h_i_j.docx", MedijService.normalizujNaziv("a/b\\c:d*e?f\"g<h>i|j", "x.docx"));
        assertEquals("a_b.docx", MedijService.normalizujNaziv("a\nb", "x.docx"));
    }

    @Test
    void normalizacijaPraznoImeJe400() {
        for (String ime : new String[]{null, "", "   ", ".docx"}) {
            SystemException ex = assertThrows(SystemException.class, () -> MedijService.normalizujNaziv(ime, "x.docx"));
            assertEquals(400, ex.getCode());
        }
    }

    @Test
    void normalizacijaDozvoljava200IOdbijaViseOdToga() {
        assertEquals(205, MedijService.normalizujNaziv("a".repeat(200), "x.docx").length());   // ekstenzija se dodaje povrh
        SystemException ex = assertThrows(SystemException.class,
                () -> MedijService.normalizujNaziv("a".repeat(201), "x.docx"));
        assertEquals(400, ex.getCode());
    }
}
