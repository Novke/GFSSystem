package tri.novica.gfssystem.rest.test;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tri.novica.gfssystem.dto.test.online.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.security.StudentAutentikacija;
import tri.novica.gfssystem.service.test.PolaganjeOnlineTesta;
import tri.novica.gfssystem.utility.KlijentIp;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Online test na nalogu studenta (plan 5, STUDENT u {@code PristupMatrica}, javno kao {@code /api/public/nalog/testovi*}).
 * Student i uređaj se uzimaju samo iz sesije; tuđ ili nepostojeći test je 404 {@code Nije pronađeno.}. Telo čuvanja
 * zadatka je najviše {@value #MAX_TELO} bajtova (413 {@value #PREDUGACKO}), čita se ručno da ni chunked zahtev ne prođe.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class NalogTestoviRest {

    public static final int MAX_TELO = 8192;
    public static final String PREDUGACKO = "Odgovor je predugačak.";

    private final PolaganjeOnlineTesta polaganje;
    private final JsonMapper json;

    @GetMapping("/public/nalog/testovi")
    @ResponseStatus(HttpStatus.OK)
    public List<TestNaNalogu> spisak() {
        return polaganje.spisak(sesija().studentId());
    }

    @PostMapping("/public/nalog/testovi/{testId}/ulaz")
    @ResponseStatus(HttpStatus.OK)
    public PolaganjeStudenta ulaz(@PathVariable Long testId, @RequestBody(required = false) UlazCmd cmd,
                                  HttpServletRequest req) {
        StudentAutentikacija s = sesija();
        return polaganje.ulaz(s.studentId(), s.uredjajId(), testId, cmd, KlijentIp.iz(req));
    }

    @GetMapping("/public/nalog/testovi/{testId}")
    @ResponseStatus(HttpStatus.OK)
    public PolaganjeStudenta stanje(@PathVariable Long testId) {
        StudentAutentikacija s = sesija();
        return polaganje.stanje(s.studentId(), s.uredjajId(), testId);
    }

    @PutMapping(path = "/public/nalog/testovi/{testId}/zadaci/{zadatakId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.OK)
    public Sacuvano sacuvaj(@PathVariable Long testId, @PathVariable Long zadatakId, HttpServletRequest req) {
        StudentAutentikacija s = sesija();
        CuvanjeCmd cmd = procitaj(req);
        return polaganje.sacuvaj(s.studentId(), s.uredjajId(), testId, zadatakId, cmd);
    }

    @PostMapping("/public/nalog/testovi/{testId}/predaj")
    @ResponseStatus(HttpStatus.OK)
    public PolaganjeStudenta predaj(@PathVariable Long testId) {
        StudentAutentikacija s = sesija();
        return polaganje.predaj(s.studentId(), s.uredjajId(), testId);
    }

    @PostMapping("/public/nalog/testovi/{testId}/dogadjaj")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dogadjaj(@PathVariable Long testId, @RequestBody DogadjajCmd cmd) {
        StudentAutentikacija s = sesija();
        polaganje.dogadjaj(s.studentId(), s.uredjajId(), testId, cmd);
    }

    @GetMapping("/public/nalog/testovi/{testId}/rezultat")
    @ResponseStatus(HttpStatus.OK)
    public RezultatTestaStudenta rezultat(@PathVariable Long testId) {
        return polaganje.rezultat(sesija().studentId(), testId);
    }

    @GetMapping("/public/nalog/predmeti/{id}/testovi")
    @ResponseStatus(HttpStatus.OK)
    public List<RezultatTestaKratko> rezultatiPredmeta(@PathVariable Long id) {
        return polaganje.rezultatiPredmeta(sesija().studentId(), id);
    }

    private CuvanjeCmd procitaj(HttpServletRequest req) {
        if (req.getContentLengthLong() > MAX_TELO) throw new SystemException(PREDUGACKO, HttpStatus.CONTENT_TOO_LARGE);
        byte[] telo;
        try (InputStream in = req.getInputStream()) {
            telo = in.readNBytes(MAX_TELO + 1);
        } catch (IOException e) {
            throw new SystemException("Neispravan format podataka.", HttpStatus.BAD_REQUEST);
        }
        if (telo.length > MAX_TELO) throw new SystemException(PREDUGACKO, HttpStatus.CONTENT_TOO_LARGE);
        try {
            CuvanjeCmd cmd = json.readValue(telo, CuvanjeCmd.class);
            if (cmd == null) throw new SystemException("Neispravan format podataka.", HttpStatus.BAD_REQUEST);
            return cmd;
        } catch (JacksonException e) {
            throw new SystemException("Neispravan format podataka.", HttpStatus.BAD_REQUEST);
        }
    }

    /** STUDENT putanje: lanac je već tražio uređaj, pa je ovo uvek prisutno. */
    private static StudentAutentikacija sesija() {
        return StudentAutentikacija.trenutna().orElseThrow(() -> new SystemException("Potrebna je prijava.", 401));
    }
}
