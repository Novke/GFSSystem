package tri.novica.gfssystem.rest.prijava;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.prijava.KodCmd;
import tri.novica.gfssystem.dto.prijava.KodOdgovor;
import tri.novica.gfssystem.dto.prijava.NalogStanjeInfo;
import tri.novica.gfssystem.dto.prijava.PomocCmd;
import tri.novica.gfssystem.dto.prijava.PrijavaKodomCmd;
import tri.novica.gfssystem.dto.prijava.StudentJaInfo;
import tri.novica.gfssystem.entity.prijava.NacinPrijave;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.security.Kolacici;
import tri.novica.gfssystem.security.StudentAutentikacija;
import tri.novica.gfssystem.service.prijava.KodServis;
import tri.novica.gfssystem.service.prijava.ObavestenjeServis;
import tri.novica.gfssystem.service.prijava.StudentskaPrijava;
import tri.novica.gfssystem.utility.KlijentIp;

import java.util.Map;

/**
 * Studentski nalog bez lozinke (spec 5.1), javno na internetu ({@code /api/public/nalog/*}): {@code kod}, {@code prijava}
 * i {@code stanje} su ANONIMNO, {@code ja}, {@code odjava} i {@code odjava-svuda} traže studentski kolačić (STUDENT u
 * {@code PristupMatrica}, lanac vraća 401 bez njega). Ništa nastavničko ovde.
 */
@RestController
@RequestMapping("/public/nalog")
@RequiredArgsConstructor
public class NalogPublicRest {

    private final KodServis kodovi;
    private final StudentskaPrijava prijava;
    private final ObavestenjeServis obavestenja;

    /** Uvek isti odgovor, i za indeks koji ne postoji (429 i 503 su isti za sve indekse). */
    @PostMapping("kod")
    @ResponseStatus(HttpStatus.OK)
    public KodOdgovor kod(@Valid @RequestBody KodCmd cmd, HttpServletRequest req) {
        return kodovi.posalji(cmd.indeks(), KlijentIp.iz(req));
    }

    /** Tačan kod: nov uređaj i kolačić {@code gfs_student}; uređaj kojim je ovaj pregledač već bio prijavljen se odjavljuje. */
    @PostMapping("prijava")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void prijava(@Valid @RequestBody PrijavaKodomCmd cmd, HttpServletRequest req, HttpServletResponse res) {
        Long studentId = kodovi.proveri(cmd.indeks(), cmd.kod(), KlijentIp.iz(req));
        StudentAutentikacija.trenutna().ifPresent(s -> prijava.odjaviUredjaj(s.uredjajId()));
        prijava.prijavi(studentId, NacinPrijave.KOD, req, res);
    }

    /**
     * "Ne stiže mi kod": uvek isti odgovor, i za indeks koji ne postoji i za studenta bez mejla (429 samo preko 10/h po
     * IP-u). Kolačić {@code gfs_student} (svi istoimeni, najviše 5) služi samo nastavniku da vidi da li je uređaj poznat.
     */
    @PostMapping("pomoc")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> pomoc(@Valid @RequestBody PomocCmd cmd, HttpServletRequest req) {
        obavestenja.neStizeKod(cmd.indeks(), KlijentIp.iz(req), Kolacici.procitajSve(req, Kolacici.STUDENT));
        return Map.of("poruka", "Javili smo nastavniku.");
    }

    @GetMapping("stanje")
    @ResponseStatus(HttpStatus.OK)
    public NalogStanjeInfo stanje() {
        return new NalogStanjeInfo(StudentAutentikacija.trenutna().isPresent());
    }

    @GetMapping("ja")
    @ResponseStatus(HttpStatus.OK)
    public StudentJaInfo ja() {
        StudentAutentikacija s = trenutni();
        return prijava.ja(s.studentId(), s.uredjajId());
    }

    @PostMapping("odjava")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void odjava(HttpServletRequest req, HttpServletResponse res) {
        prijava.odjaviUredjaj(trenutni().uredjajId());
        Kolacici.obrisi(req, res, Kolacici.STUDENT);
    }

    /** Odjava svih uređaja studenta, i ovog. */
    @PostMapping("odjava-svuda")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Integer> odjavaSvuda(HttpServletRequest req, HttpServletResponse res) {
        int n = prijava.odjaviSve(trenutni().studentId());
        Kolacici.obrisi(req, res, Kolacici.STUDENT);
        return Map.of("odjavljeno", n);
    }

    /** STUDENT putanje: lanac je već tražio uređaj, pa je ovo uvek prisutno. */
    private static StudentAutentikacija trenutni() {
        return StudentAutentikacija.trenutna().orElseThrow(() -> new SystemException("Potrebna je prijava.", 401));
    }
}
