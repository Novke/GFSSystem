package tri.novica.gfssystem.rest.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.service.uzivo.MedijService;

/**
 * Javno (bez autentifikacije) serviranje slika za telefone studenata; nginx izuzima /api/public/ iz basic-auth-a.
 * Id je UUID, a sadržaj se pod istim id-jem nikad ne menja, pa se kešira trajno. {@code nosniff}: pregledač ne sme da
 * pogađa tip, sadržaj važi samo kao {@code Content-Type} iz baze (proveren po bajtovima pri uploadu).
 * PDF prilozi banke zadataka nisu javni: za njih je odgovor isti kao za nepoznat id, da se ne otkrije da postoje.
 * Render SVG dobija CSP koji zabranjuje sve osim inline stilova (skripte i spoljni resursi nemaju šta da rade u SVG-u).
 */
@RestController
@RequestMapping("/public/mediji")
@RequiredArgsConstructor
public class PublicMedijRest {

    private static final String CSP_SVG = "default-src 'none'; style-src 'unsafe-inline'";

    private final MedijService medijService;

    @GetMapping("{id}")
    public ResponseEntity<Resource> slika(@PathVariable String id) {
        MedijService.Fajl fajl = medijService.ucitaj(id);
        if (fajl.vrsta() == VrstaMedija.PRILOG_PDF) {
            throw new SystemException(MedijService.NIJE_PRONADJENA, HttpStatus.NOT_FOUND);
        }
        ResponseEntity.BodyBuilder odgovor = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fajl.mime()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline");
        if (fajl.vrsta() == VrstaMedija.RENDER_SVG) {
            odgovor.header("Content-Security-Policy", CSP_SVG);
        }
        return odgovor.body(fajl.sadrzaj());
    }
}
