package tri.novica.gfssystem.service.uzivo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.entity.uzivo.Medij;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Slike slajdova i pitanja: bajtovi su fajl {@code <gfs.mediji.dir>/<uuid>}, metapodaci red u {@code mediji}.
 * Prihvata samo PNG, JPEG, GIF i WebP, prepoznate po prvim bajtovima (zaglavlje i ekstenziju šalje klijent, pa se ne
 * koriste). Javno se servira samo id u kanonskom UUID obliku, pa putanja na disku ne može izaći iz direktorijuma.
 */
@Service
@Slf4j
public class MedijService {

    public static final String NEDOZVOLJEN_TIP = "Dozvoljene su samo slike PNG, JPEG, GIF i WebP.";
    public static final String PRAZAN_FAJL = "Fajl je prazan.";
    public static final String NIJE_PRONADJENA = "Slika nije pronađena.";

    private static final int MAX_NAZIV = 255;
    private static final Pattern UUID_OBLIK =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /** Sadržaj za serviranje: {@code mime} i {@code velicina} iz baze, bajtovi sa diska. */
    public record Fajl(String mime, long velicina, Resource sadrzaj) {
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
    }

    public MedijInfo sacuvaj(MultipartFile fajl) {
        if (fajl == null || fajl.isEmpty()) {
            throw new SystemException(PRAZAN_FAJL, HttpStatus.BAD_REQUEST);
        }
        String mime = mime(zaglavlje(fajl));
        if (mime == null) {
            throw new SystemException(NEDOZVOLJEN_TIP, HttpStatus.BAD_REQUEST);
        }

        Medij medij = new Medij();
        medij.setId(UUID.randomUUID().toString());
        medij.setNaziv(naziv(fajl.getOriginalFilename()));
        medij.setMime(mime);
        medij.setKreirano(LocalDateTime.now(clock));

        Path cilj = dir.resolve(medij.getId());
        try (InputStream in = fajl.getInputStream()) {
            medij.setVelicina(Files.copy(in, cilj));
        } catch (IOException e) {
            obrisi(cilj);
            throw new UncheckedIOException(e);
        }
        try {
            medijRepository.save(medij);
        } catch (RuntimeException e) {
            obrisi(cilj);   // fajl bez reda se nikad ne bi servirao
            throw e;
        }
        log.info("Slika sačuvana: id={}, mime={}, velicina={}", medij.getId(), mime, medij.getVelicina());
        return new MedijInfo(medij.getId(), medij.getNaziv(), mime, medij.getVelicina());
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
        return new Fajl(medij.getMime(), medij.getVelicina(), new FileSystemResource(putanja));
    }

    public boolean postoji(String id) {
        return jeUuid(id) && medijRepository.existsById(id);
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

    /** Mime po magic bajtovima ili {@code null} kad fajl nije dozvoljena slika. */
    private static String mime(byte[] b) {
        if (pocinje(b, 0, 0x89, 0x50, 0x4E, 0x47)) return "image/png";
        if (pocinje(b, 0, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        // GIF87a, GIF89a
        if (pocinje(b, 0, 0x47, 0x49, 0x46, 0x38, 0x37, 0x61) || pocinje(b, 0, 0x47, 0x49, 0x46, 0x38, 0x39, 0x61)) return "image/gif";
        // RIFF????WEBP
        if (pocinje(b, 0, 0x52, 0x49, 0x46, 0x46) && pocinje(b, 8, 0x57, 0x45, 0x42, 0x50)) return "image/webp";
        return null;
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

    private static void obrisi(Path putanja) {
        try {
            Files.deleteIfExists(putanja);
        } catch (IOException e) {
            log.warn("Fajl {} nije obrisan", putanja, e);
        }
    }
}
