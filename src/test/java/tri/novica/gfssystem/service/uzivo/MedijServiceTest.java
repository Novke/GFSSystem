package tri.novica.gfssystem.service.uzivo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.entity.uzivo.Medij;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedijServiceTest {

    static final LocalDateTime SADA = LocalDateTime.of(2026, 10, 7, 12, 0);
    static final Clock CLOCK = Clock.fixed(SADA.atZone(ZoneId.of("Europe/Belgrade")).toInstant(), ZoneId.of("Europe/Belgrade"));
    static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4, 5};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};
    static final byte[] PDF = "%PDF-1.7\n%\u00e2\u00e3\u00cf\u00d3\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1);
    static final byte[] SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"10\" height=\"10\"><rect width=\"10\" height=\"10\"/></svg>"
            .getBytes(StandardCharsets.UTF_8);

    @TempDir Path tmp;
    @Mock MedijRepository medijRepository;

    Path dir;
    MedijService service;

    @BeforeEach
    void setUp() {
        dir = tmp.resolve("mediji");   // ne postoji: servis ga pravi pri startu
        service = new MedijService(medijRepository, CLOCK, dir.toString());
    }

    @Test
    void podrazumevaniDirektorijumJePrivremeni() {
        // na podrazumevanom ${java.io.tmpdir}/gfs-mediji se pri pokretanju loguje WARN (slike nestaju sa tmp-om)
        assertTrue(MedijService.jePodrazumevani(Path.of(System.getProperty("java.io.tmpdir"), "gfs-mediji")));
        assertFalse(MedijService.jePodrazumevani(dir));
    }

    @Test
    void direktorijumSePraviPriStartu() {
        assertTrue(Files.isDirectory(dir));
    }

    @Test
    void pngSeCuvaKaoFajlIRedUBazi() throws IOException {
        // zaglavlje laže (octet-stream), mime se određuje iz bajtova
        MedijInfo info = service.sacuvaj(new MockMultipartFile("fajl", "tabla.png", "application/octet-stream", PNG));

        assertEquals("image/png", info.mime());
        assertEquals("tabla.png", info.naziv());
        assertEquals(PNG.length, info.velicina());
        assertEquals(info.id(), UUID.fromString(info.id()).toString());
        assertArrayEquals(PNG, Files.readAllBytes(dir.resolve(info.id())));

        ArgumentCaptor<Medij> red = ArgumentCaptor.forClass(Medij.class);
        verify(medijRepository).save(red.capture());
        assertEquals(info.id(), red.getValue().getId());
        assertEquals("tabla.png", red.getValue().getNaziv());
        assertEquals("image/png", red.getValue().getMime());
        assertEquals(PNG.length, red.getValue().getVelicina());
        assertEquals(SADA, red.getValue().getKreirano());
        assertEquals(VrstaMedija.SLIKA, red.getValue().getVrsta());
    }

    static Stream<Arguments> dozvoljeneSlike() {
        return Stream.of(
                Arguments.of(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10}, "image/jpeg"),
                Arguments.of("GIF87a....".getBytes(StandardCharsets.US_ASCII), "image/gif"),
                Arguments.of("GIF89a....".getBytes(StandardCharsets.US_ASCII), "image/gif"),
                Arguments.of("RIFF$\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1), "image/webp"));
    }

    @ParameterizedTest
    @MethodSource("dozvoljeneSlike")
    void mimeIzMagicBajtova(byte[] sadrzaj, String mime) {
        assertEquals(mime, service.sacuvaj(new MockMultipartFile("fajl", "x", "text/plain", sadrzaj)).mime());
    }

    @Test
    void svgSeOdbija() throws IOException {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
        SystemException ex = assertThrows(SystemException.class,
                () -> service.sacuvaj(new MockMultipartFile("fajl", "a.svg", "image/svg+xml", svg)));
        assertEquals(400, ex.getCode());
        assertEquals("Dozvoljene su samo slike PNG, JPEG, GIF i WebP.", ex.getMessage());
        assertNiceganNaDisku();
        verify(medijRepository, never()).save(any());
    }

    @Test
    void tekstSaPngEkstenzijomSeOdbija() throws IOException {
        byte[] tekst = "ovo nije slika".getBytes(StandardCharsets.UTF_8);
        SystemException ex = assertThrows(SystemException.class,
                () -> service.sacuvaj(new MockMultipartFile("fajl", "slika.png", "image/png", tekst)));
        assertEquals(400, ex.getCode());
        assertEquals("Dozvoljene su samo slike PNG, JPEG, GIF i WebP.", ex.getMessage());
        assertNiceganNaDisku();
        verify(medijRepository, never()).save(any());
    }

    @Test
    void prazanFajlSeOdbija() throws IOException {
        SystemException ex = assertThrows(SystemException.class,
                () -> service.sacuvaj(new MockMultipartFile("fajl", "a.png", "image/png", new byte[0])));
        assertEquals(400, ex.getCode());
        assertEquals("Fajl je prazan.", ex.getMessage());
        assertNiceganNaDisku();
        verify(medijRepository, never()).save(any());
    }

    @Test
    void dugackiNazivSeSkracujeNa255() {
        String naziv = "a".repeat(300) + ".png";
        assertEquals("a".repeat(255), service.sacuvaj(new MockMultipartFile("fajl", naziv, "image/png", PNG)).naziv());
    }

    @Test
    void greskaBazeBriseFajl() throws IOException {
        when(medijRepository.save(any(Medij.class))).thenThrow(new IllegalStateException("baza"));
        assertThrows(IllegalStateException.class,
                () -> service.sacuvaj(new MockMultipartFile("fajl", "a.png", "image/png", PNG)));
        assertNiceganNaDisku();
    }

    @Test
    void ucitajVracaMimeVelicinuIBajtove() throws IOException {
        MedijInfo info = service.sacuvaj(new MockMultipartFile("fajl", "a.png", "image/png", PNG));
        Medij medij = new Medij();
        medij.setId(info.id());
        medij.setMime("image/png");
        medij.setVelicina(PNG.length);
        when(medijRepository.findById(info.id())).thenReturn(Optional.of(medij));

        MedijService.Fajl fajl = service.ucitaj(info.id());

        assertEquals("image/png", fajl.mime());
        assertEquals(PNG.length, fajl.velicina());
        assertArrayEquals(PNG, fajl.sadrzaj().getContentAsByteArray());
        assertEquals(VrstaMedija.SLIKA, fajl.vrsta());
    }

    @Test
    void ucitajVracaVrstuIzReda() throws IOException {
        MedijInfo info = service.sacuvajRender(SVG, "render.svg");
        Medij medij = new Medij();
        medij.setId(info.id());
        medij.setMime("image/svg+xml");
        medij.setVelicina(SVG.length);
        medij.setVrsta(VrstaMedija.RENDER_SVG);
        when(medijRepository.findById(info.id())).thenReturn(Optional.of(medij));

        MedijService.Fajl fajl = service.ucitaj(info.id());

        assertEquals(VrstaMedija.RENDER_SVG, fajl.vrsta());
        assertEquals("image/svg+xml", fajl.mime());
    }

    @Test
    void ucitajNepostojecegJe404() {
        String id = UUID.randomUUID().toString();
        when(medijRepository.findById(id)).thenReturn(Optional.empty());
        SystemException ex = assertThrows(SystemException.class, () -> service.ucitaj(id));
        assertEquals(404, ex.getCode());
        assertEquals("Slika nije pronađena.", ex.getMessage());
    }

    @Test
    void ucitajIdKojiNijeUuidJe404BezBazeIDiska() throws IOException {
        Files.writeString(tmp.resolve("x"), "tajna van direktorijuma");   // ../x iz dir-a
        for (String id : new String[]{"../x", "..", "x", "", UUID.randomUUID().toString().toUpperCase()}) {
            SystemException ex = assertThrows(SystemException.class, () -> service.ucitaj(id));
            assertEquals(404, ex.getCode());
            assertEquals("Slika nije pronađena.", ex.getMessage());
        }
        assertThrows(SystemException.class, () -> service.ucitaj(null));
        verifyNoInteractions(medijRepository);
    }

    @Test
    void redBezFajlaJe404() {
        String id = UUID.randomUUID().toString();
        Medij medij = new Medij();
        medij.setId(id);
        when(medijRepository.findById(id)).thenReturn(Optional.of(medij));
        SystemException ex = assertThrows(SystemException.class, () -> service.ucitaj(id));
        assertEquals(404, ex.getCode());
    }

    @Test
    void postoji() {
        String id = UUID.randomUUID().toString();
        when(medijRepository.existsById(id)).thenReturn(true);
        assertTrue(service.postoji(id));
        assertFalse(service.postoji("../x"));
        assertFalse(service.postoji(null));
        verify(medijRepository).existsById(id);
        verifyNoMoreInteractions(medijRepository);
    }

    // --- prilozi (PNG, JPEG, PDF), POST /banka/prilozi ---

    @Test
    void pdfPrilogSeCuvaKaoPrilogPdf() throws IOException {
        // zaglavlje laže (octet-stream), vrsta i mime se određuju iz bajtova
        MedijInfo info = service.sacuvajPrilog(new MockMultipartFile("fajl", "grafikon.pdf", "application/octet-stream", PDF));

        assertEquals("application/pdf", info.mime());
        assertEquals("grafikon.pdf", info.naziv());
        assertEquals(PDF.length, info.velicina());
        assertArrayEquals(PDF, Files.readAllBytes(dir.resolve(info.id())));

        ArgumentCaptor<Medij> red = ArgumentCaptor.forClass(Medij.class);
        verify(medijRepository).save(red.capture());
        assertEquals(info.id(), red.getValue().getId());
        assertEquals(VrstaMedija.PRILOG_PDF, red.getValue().getVrsta());
        assertEquals("application/pdf", red.getValue().getMime());
        assertEquals(SADA, red.getValue().getKreirano());
    }

    @Test
    void pngIJpegPrilogSuSlike() {
        MedijInfo png = service.sacuvajPrilog(new MockMultipartFile("fajl", "a.png", "image/png", PNG));
        MedijInfo jpeg = service.sacuvajPrilog(new MockMultipartFile("fajl", "b.jpg", "text/plain", JPEG));

        assertEquals("image/png", png.mime());
        assertEquals("image/jpeg", jpeg.mime());
        ArgumentCaptor<Medij> redovi = ArgumentCaptor.forClass(Medij.class);
        verify(medijRepository, times(2)).save(redovi.capture());
        assertEquals(VrstaMedija.SLIKA, redovi.getAllValues().get(0).getVrsta());
        assertEquals(VrstaMedija.SLIKA, redovi.getAllValues().get(1).getVrsta());
    }

    static Stream<Arguments> nedozvoljeniPrilozi() {
        return Stream.of(
                Arguments.of("gif", "GIF89a....".getBytes(StandardCharsets.US_ASCII)),
                Arguments.of("webp", "RIFF$\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1)),
                Arguments.of("svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8)),
                Arguments.of("tekst", "%PDF je samo tekst".getBytes(StandardCharsets.UTF_8)),
                Arguments.of("kratak", "%PDF".getBytes(StandardCharsets.US_ASCII)));
    }

    @ParameterizedTest(name = "prilog {0} se odbija")
    @MethodSource("nedozvoljeniPrilozi")
    void nedozvoljenPrilogSeOdbija(String ime, byte[] sadrzaj) throws IOException {
        SystemException ex = assertThrows(SystemException.class,
                () -> service.sacuvajPrilog(new MockMultipartFile("fajl", "x." + ime, "application/pdf", sadrzaj)));
        assertEquals(400, ex.getCode());
        assertEquals("Dozvoljeni prilozi: PNG, JPEG i PDF.", ex.getMessage());
        assertNiceganNaDisku();
        verify(medijRepository, never()).save(any());
    }

    @Test
    void prazanPrilogSeOdbija() throws IOException {
        SystemException ex = assertThrows(SystemException.class,
                () -> service.sacuvajPrilog(new MockMultipartFile("fajl", "a.pdf", "application/pdf", new byte[0])));
        assertEquals(400, ex.getCode());
        assertEquals("Fajl je prazan.", ex.getMessage());
        assertThrows(SystemException.class, () -> service.sacuvajPrilog(null));
        assertNiceganNaDisku();
        verify(medijRepository, never()).save(any());
    }

    @Test
    void greskaBazePriPrilogaBriseFajl() throws IOException {
        when(medijRepository.save(any(Medij.class))).thenThrow(new IllegalStateException("baza"));
        assertThrows(IllegalStateException.class,
                () -> service.sacuvajPrilog(new MockMultipartFile("fajl", "a.pdf", "application/pdf", PDF)));
        assertNiceganNaDisku();
    }

    @Test
    void obicanUploadSlikaOstajeSamoZaSlike() throws IOException {
        // POST /mediji ne sme da primi PDF: prilozi idu samo kroz sacuvajPrilog
        SystemException ex = assertThrows(SystemException.class,
                () -> service.sacuvaj(new MockMultipartFile("fajl", "a.pdf", "application/pdf", PDF)));
        assertEquals(400, ex.getCode());
        assertEquals("Dozvoljene su samo slike PNG, JPEG, GIF i WebP.", ex.getMessage());
        assertNiceganNaDisku();
        verify(medijRepository, never()).save(any());
    }

    // --- render (SVG iz LaTeX-a) ---

    @Test
    void renderSeCuvaKaoRenderSvg() throws IOException {
        MedijInfo info = service.sacuvajRender(SVG, "zadatak-1.svg");

        assertEquals("image/svg+xml", info.mime());
        assertEquals("zadatak-1.svg", info.naziv());
        assertEquals(SVG.length, info.velicina());
        assertEquals(info.id(), UUID.fromString(info.id()).toString());
        assertArrayEquals(SVG, Files.readAllBytes(dir.resolve(info.id())));

        ArgumentCaptor<Medij> red = ArgumentCaptor.forClass(Medij.class);
        verify(medijRepository).save(red.capture());
        assertEquals(info.id(), red.getValue().getId());
        assertEquals("zadatak-1.svg", red.getValue().getNaziv());
        assertEquals("image/svg+xml", red.getValue().getMime());
        assertEquals(SVG.length, red.getValue().getVelicina());
        assertEquals(VrstaMedija.RENDER_SVG, red.getValue().getVrsta());
        assertEquals(SADA, red.getValue().getKreirano());
    }

    @Test
    void dugackiNazivRendera() {
        assertEquals("a".repeat(255), service.sacuvajRender(SVG, "a".repeat(300)).naziv());
    }

    @Test
    void prazanRenderSeOdbija() throws IOException {
        for (byte[] prazan : new byte[][]{new byte[0], null}) {
            SystemException ex = assertThrows(SystemException.class, () -> service.sacuvajRender(prazan, "a.svg"));
            assertEquals(400, ex.getCode());
            assertEquals("Fajl je prazan.", ex.getMessage());
        }
        assertNiceganNaDisku();
        verify(medijRepository, never()).save(any());
    }

    @Test
    void greskaBazePriRenderuBriseFajl() throws IOException {
        when(medijRepository.save(any(Medij.class))).thenThrow(new IllegalStateException("baza"));
        assertThrows(IllegalStateException.class, () -> service.sacuvajRender(SVG, "a.svg"));
        assertNiceganNaDisku();
    }

    // --- bajtovi i obrisi (poziva ih servis varijanti) ---

    @Test
    void bajtoviVracajuSadrzajFajla() {
        MedijInfo info = service.sacuvajPrilog(new MockMultipartFile("fajl", "a.pdf", "application/pdf", PDF));
        Medij medij = new Medij();
        medij.setId(info.id());
        medij.setMime("application/pdf");
        medij.setVelicina(PDF.length);
        medij.setVrsta(VrstaMedija.PRILOG_PDF);
        when(medijRepository.findById(info.id())).thenReturn(Optional.of(medij));

        assertArrayEquals(PDF, service.bajtovi(info.id()));
    }

    @Test
    void bajtoviNepostojecegJe404() {
        String id = UUID.randomUUID().toString();
        when(medijRepository.findById(id)).thenReturn(Optional.empty());
        SystemException ex = assertThrows(SystemException.class, () -> service.bajtovi(id));
        assertEquals(404, ex.getCode());
        assertEquals("Slika nije pronađena.", ex.getMessage());
        assertThrows(SystemException.class, () -> service.bajtovi("../x"));
        assertThrows(SystemException.class, () -> service.bajtovi(null));
    }

    @Test
    void obrisiBriseRedIFajlATiho() throws IOException {
        MedijInfo info = service.sacuvajRender(SVG, "a.svg");
        assertTrue(Files.exists(dir.resolve(info.id())));

        service.obrisi(info.id());

        assertNiceganNaDisku();
        verify(medijRepository).deleteById(info.id());

        // drugi put: nema ni fajla, ni reda; bez izuzetka
        assertDoesNotThrow(() -> service.obrisi(info.id()));
        verify(medijRepository, times(2)).deleteById(info.id());
    }

    @Test
    void obrisiNepoznatIdINeUuidJeTiho() throws IOException {
        Files.writeString(tmp.resolve("x"), "van direktorijuma");   // ../x iz dir-a
        assertDoesNotThrow(() -> service.obrisi(UUID.randomUUID().toString()));
        for (String id : new String[]{"../x", "..", "x", "", null}) {
            assertDoesNotThrow(() -> service.obrisi(id));
        }
        assertTrue(Files.exists(tmp.resolve("x")));
        verify(medijRepository, times(1)).deleteById(any());   // samo za pravi UUID, nikad za putanju
    }

    private void assertNiceganNaDisku() throws IOException {
        try (Stream<Path> fajlovi = Files.list(dir)) {
            assertEquals(0, fajlovi.count());
        }
    }
}
