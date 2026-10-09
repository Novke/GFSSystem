package tri.novica.gfssystem.rest.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tri.novica.gfssystem.service.uzivo.MedijService;

/**
 * Javno (bez autentifikacije) serviranje slika za telefone studenata; nginx izuzima /api/public/ iz basic-auth-a.
 * Id je UUID, a sadržaj se pod istim id-jem nikad ne menja, pa se kešira trajno. {@code nosniff}: pregledač ne sme da
 * pogađa tip, sadržaj važi samo kao {@code Content-Type} iz baze (proveren po bajtovima pri uploadu).
 */
@RestController
@RequestMapping("/public/mediji")
@RequiredArgsConstructor
public class PublicMedijRest {

    private final MedijService medijService;

    @GetMapping("{id}")
    public ResponseEntity<Resource> slika(@PathVariable String id) {
        MedijService.Fajl fajl = medijService.ucitaj(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fajl.mime()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(fajl.sadrzaj());
    }
}
