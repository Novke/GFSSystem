package tri.novica.gfssystem.service.uzivo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.entity.uzivo.Medij;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Fajlovi iz {@code mediji}: bajtovi su fajl {@code <gfs.mediji.dir>/<uuid>}, metapodaci red u tabeli. Vrste
 * ({@link VrstaMedija}): slike slajdova i pitanja ({@link #sacuvaj}: PNG, JPEG, GIF, WebP; {@code SLIKA}), prilozi zadataka
 * banke ({@link #sacuvajPrilog}: PNG i JPEG kao {@code PRILOG_SLIKA}, PDF kao {@code PRILOG_PDF}) i SVG koji je renderovao
 * LaTeX ({@link #sacuvajRender}). Tip se prepoznaje po prvim bajtovima (zaglavlje i ekstenziju šalje klijent, pa se ne
 * koriste). Javno se servira samo id u kanonskom UUID obliku, pa putanja na disku ne može izaći iz direktorijuma.
 * <p>
 * <b>Fajl i transakcija:</b> u aktivnoj transakciji {@link #obrisi} briše fajl tek posle commit-a (pad commit-a ostavlja i
 * red i fajl), a fajl novog medija se briše ako se transakcija poništi (red ne postoji, fajl se nikad ne bi obrisao). Van
 * transakcije (upload, čišćenje u testovima) red se upisuje odmah u sopstvenoj transakciji repozitorijuma, pa i fajl odmah.
 */
@Service
@Slf4j
public class MedijService {

    public static final String NEDOZVOLJEN_TIP = "Dozvoljene su samo slike PNG, JPEG, GIF i WebP.";
    public static final String NEDOZVOLJEN_PRILOG = "Dozvoljeni prilozi: PNG, JPEG i PDF.";
    public static final String PRAZAN_FAJL = "Fajl je prazan.";
    public static final String NIJE_PRONADJENA = "Slika nije pronađena.";
    public static final String NIJE_SLIKA = "Medij nije slika.";

    private static final int MAX_NAZIV = 255;
    private static final Pattern UUID_OBLIK =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /** Sadržaj za serviranje: {@code mime}, {@code velicina} i {@code vrsta} iz baze, bajtovi sa diska. */
    public record Fajl(String mime, long velicina, Resource sadrzaj, VrstaMedija vrsta) {
    }

    /** Prepoznat tip fajla po bajtovima. */
    private record Tip(String mime, VrstaMedija vrsta) {
    }

    /** Upisuje sadržaj u (novi) fajl i vraća broj upisanih bajtova. */
    @FunctionalInterface
    private interface Upis {
        long upisi(Path cilj) throws IOException;
    }

    private final MedijRepository medijRepository;
    private final Clock clock;
    private final Path dir;

    public MedijService(MedijRepository medijRepository, Clock clock, @Value("${gfs.mediji.dir}") String dir) {
        this.medijRepository = medijRepository;
        this.clock = clock;
        Path putanja = Path.of(dir).toAbsolutePath().normalize();
        try {
            this.dir = Files.createDirectories(putanja);
        } catch (IOException e) {
            throw new UncheckedIOException("Direktorijum za slike nije dostupan: " + putanja, e);
        }
        if (jePodrazumevani(putanja)) {
            log.warn("Slike se čuvaju u privremenom direktorijumu {} (podrazumevano): brišu se sa njim. Postavi "
                    + "GFS_MEDIJI_DIR (gfs.mediji.dir) na trajni direktorijum (u kontejneru: volume).", putanja);
        }
    }

    /** Da li je direktorijum podrazumevani {@code ${java.io.tmpdir}/gfs-mediji} (nije postavljen GFS_MEDIJI_DIR). */
    static boolean jePodrazumevani(Path putanja) {
        String tmp = System.getProperty("java.io.tmpdir");
        return tmp != null && putanja.equals(Path.of(tmp, "gfs-mediji").toAbsolutePath().normalize());
    }

    /** Slika slajda ili pitanja (PNG, JPEG, GIF, WebP); vrsta {@code SLIKA}. PDF ovuda ne ulazi: vidi {@link #sacuvajPrilog}. */
    public MedijInfo sacuvaj(MultipartFile fajl) {
        return sacuvaj(fajl, MedijService::tipSlike, NEDOZVOLJEN_TIP);
    }

    /**
     * Prilog zadatka banke: PNG i JPEG (vrsta {@code PRILOG_SLIKA}) ili PDF (vrsta {@code PRILOG_PDF}); nijedan se javno
     * ne servira. Bez GIF-a i WebP-a: LaTeX ih ne može uključiti.
     */
    public MedijInfo sacuvajPrilog(MultipartFile fajl) {
        return sacuvaj(fajl, MedijService::tipPriloga, NEDOZVOLJEN_PRILOG);
    }

    /** SVG koji je renderovao LaTeX kontejner (vrsta {@code RENDER_SVG}); bajtove daje server, ne klijent. */
    public MedijInfo sacuvajRender(byte[] svg, String naziv) {
        if (svg == null || svg.length == 0) {
            throw new SystemException(PRAZAN_FAJL, HttpStatus.BAD_REQUEST);
        }
        return upisi(naziv(naziv), new Tip("image/svg+xml", VrstaMedija.RENDER_SVG), cilj -> {
            Files.write(cilj, svg, StandardOpenOption.CREATE_NEW);
            return svg.length;
        });
    }

    private MedijInfo sacuvaj(MultipartFile fajl, Function<byte[], Tip> prepoznaj, String nedozvoljeno) {
        if (fajl == null || fajl.isEmpty()) {
            throw new SystemException(PRAZAN_FAJL, HttpStatus.BAD_REQUEST);
        }
        Tip tip = prepoznaj.apply(zaglavlje(fajl));
        if (tip == null) {
            throw new SystemException(nedozvoljeno, HttpStatus.BAD_REQUEST);
        }
        return upisi(naziv(fajl.getOriginalFilename()), tip, cilj -> {
            try (InputStream in = fajl.getInputStream()) {
                return Files.copy(in, cilj);
            }
        });
    }

    private MedijInfo upisi(String naziv, Tip tip, Upis upis) {
        Medij medij = new Medij();
        medij.setId(UUID.randomUUID().toString());
        medij.setNaziv(naziv);
        medij.setMime(tip.mime());
        medij.setVrsta(tip.vrsta());
        medij.setKreirano(LocalDateTime.now(clock));

        Path cilj = dir.resolve(medij.getId());
        try {
            medij.setVelicina(upis.upisi(cilj));
        } catch (IOException e) {
            obrisiFajl(cilj);
            throw new UncheckedIOException(e);
        }
        try {
            medijRepository.save(medij);
        } catch (RuntimeException e) {
            obrisiFajl(cilj);   // fajl bez reda se nikad ne bi servirao
            throw e;
        }
        if (uTransakciji()) {
            // ni posle poništene transakcije (npr. pad commit-a novog rendera) ne sme ostati fajl bez reda
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        obrisiFajl(cilj);
                    }
                }
            });
        }
        log.info("Medij sačuvan: id={}, vrsta={}, mime={}, velicina={}", medij.getId(), tip.vrsta(), tip.mime(), medij.getVelicina());
        return new MedijInfo(medij.getId(), medij.getNaziv(), tip.mime(), medij.getVelicina());
    }

    public Fajl ucitaj(String id) {
        if (!jeUuid(id)) {
            throw nijePronadjena();
        }
        Medij medij = medijRepository.findById(id).orElseThrow(MedijService::nijePronadjena);
        Path putanja = dir.resolve(id);
        if (!Files.isRegularFile(putanja)) {
            log.warn("Slika {} postoji u bazi, ali ne i na disku", id);
            throw nijePronadjena();
        }
        return new Fajl(medij.getMime(), medij.getVelicina(), new FileSystemResource(putanja), medij.getVrsta());
    }

    /** Ceo sadržaj u memoriji (za slanje priloga renderu, do 10 MB); 404 kao {@link #ucitaj}. */
    public byte[] bajtovi(String id) {
        try {
            return ucitaj(id).sadrzaj().getContentAsByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Briše red i fajl; nepoznat id, id koji nije UUID i fajl koji ne postoji nisu greška. U aktivnoj transakciji fajl se
     * briše tek posle commit-a: ako commit padne (npr. strani ključ na red medija), red i fajl ostaju zajedno.
     */
    public void obrisi(String id) {
        if (!jeUuid(id)) {
            return;
        }
        medijRepository.deleteById(id);   // nepostojeći red se tiho preskače
        Path putanja = dir.resolve(id);
        if (uTransakciji()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    obrisiFajl(putanja);
                }
            });
        } else {
            obrisiFajl(putanja);
        }
    }

    /** Vrsta medija, prazno za nepoznat id ili id koji nije UUID. */
    public Optional<VrstaMedija> vrsta(String id) {
        return jeUuid(id) ? medijRepository.findById(id).map(Medij::getVrsta) : Optional.empty();
    }

    /** Prava transakcija sa sinhronizacijom (ne samo prazan opseg, npr. {@code NOT_SUPPORTED}). */
    private static boolean uTransakciji() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive();
    }

    private static boolean jeUuid(String id) {
        return id != null && UUID_OBLIK.matcher(id).matches();
    }

    private static SystemException nijePronadjena() {
        return new SystemException(NIJE_PRONADJENA, HttpStatus.NOT_FOUND);
    }

    private static byte[] zaglavlje(MultipartFile fajl) {
        try (InputStream in = fajl.getInputStream()) {
            return in.readNBytes(12);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Slika po magic bajtovima (PNG, JPEG, GIF, WebP) ili {@code null}. */
    private static Tip tipSlike(byte[] b) {
        Tip tip = pngIliJpeg(b);
        if (tip != null) return tip;
        // GIF87a, GIF89a
        if (pocinje(b, 0, 0x47, 0x49, 0x46, 0x38, 0x37, 0x61) || pocinje(b, 0, 0x47, 0x49, 0x46, 0x38, 0x39, 0x61)) return slika("image/gif");
        // RIFF????WEBP
        if (pocinje(b, 0, 0x52, 0x49, 0x46, 0x46) && pocinje(b, 8, 0x57, 0x45, 0x42, 0x50)) return slika("image/webp");
        return null;
    }

    /** Prilog po magic bajtovima (PNG i JPEG kao {@code PRILOG_SLIKA}, PDF) ili {@code null}. */
    private static Tip tipPriloga(byte[] b) {
        Tip tip = pngIliJpeg(b);
        if (tip != null) return new Tip(tip.mime(), VrstaMedija.PRILOG_SLIKA);
        // %PDF-
        if (pocinje(b, 0, 0x25, 0x50, 0x44, 0x46, 0x2D)) return new Tip("application/pdf", VrstaMedija.PRILOG_PDF);
        return null;
    }

    private static Tip pngIliJpeg(byte[] b) {
        if (pocinje(b, 0, 0x89, 0x50, 0x4E, 0x47)) return slika("image/png");
        if (pocinje(b, 0, 0xFF, 0xD8, 0xFF)) return slika("image/jpeg");
        return null;
    }

    private static Tip slika(String mime) {
        return new Tip(mime, VrstaMedija.SLIKA);
    }

    private static boolean pocinje(byte[] b, int od, int... ocekivano) {
        if (b.length < od + ocekivano.length) return false;
        for (int i = 0; i < ocekivano.length; i++) {
            if ((b[od + i] & 0xFF) != ocekivano[i]) return false;
        }
        return true;
    }

    /** Ime originala za prikaz nastavniku, najviše 255 znakova (kolona je varchar(255), znakovi a ne UTF-16 jedinice). */
    private static String naziv(String original) {
        String naziv = original == null || original.isBlank() ? "slika" : original.strip();
        return naziv.codePointCount(0, naziv.length()) > MAX_NAZIV
                ? naziv.substring(0, naziv.offsetByCodePoints(0, MAX_NAZIV))
                : naziv;
    }

    private static void obrisiFajl(Path putanja) {
        try {
            Files.deleteIfExists(putanja);
        } catch (IOException e) {
            log.warn("Fajl {} nije obrisan", putanja, e);
        }
    }
}
