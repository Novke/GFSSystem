package tri.novica.gfssystem.service.lti;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.lti.LtiPovezivanjeInfo;
import tri.novica.gfssystem.entity.lti.LtiPovezivanje;
import tri.novica.gfssystem.entity.lti.LtiRegistracija;
import tri.novica.gfssystem.entity.prijava.NacinPrijave;
import tri.novica.gfssystem.entity.prijava.SpoljniIdentitet;
import tri.novica.gfssystem.entity.prijava.VrstaIdentiteta;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.lti.LtiPovezivanjeRepository;
import tri.novica.gfssystem.repository.lti.LtiRegistracijaRepository;
import tri.novica.gfssystem.repository.prijava.SpoljniIdentitetRepository;
import tri.novica.gfssystem.security.Kolacici;
import tri.novica.gfssystem.service.prijava.StudentskaPrijava;
import tri.novica.gfssystem.service.uzivo.TokenHash;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Veza Moodle naloga ({@code iss}, {@code sub}) sa studentom (spec 8.3, 8.4): posle launch-a studenta bez veze nastaje
 * jednokratno povezivanje (30 min, kolačić {@code __Host-gfs_lti_par}); student se prijavi kodom kao na {@code /nalog}, pa
 * {@code POST /public/lti/povezi} upisuje red u {@code spoljni_identiteti}. Svaki sledeći ulaz iz Moodle-a je prijava
 * jednim klikom ({@link #prijaviPovezanog}). Ime iz Moodle-a služi samo za prikaz na ekranu povezivanja i nikad ne ide u
 * log; servis vraća samo DTO-ove (open-in-view je isključen za {@code /public/lti/**}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LtiPovezivanjeServis {

    /** Trajanje povezivanja; isto je Max-Age kolačića {@code __Host-gfs_lti_par}. */
    public static final Duration TRAJANJE = Duration.ofMinutes(30);
    public static final String VEC_POVEZAN = "Ovaj Moodle nalog je već povezan sa drugim studentom. Javi se nastavniku.";
    public static final String ISTEKLO = "Povezivanje je isteklo. Otvori GFS ponovo iz Moodle-a.";

    private final LtiPovezivanjeRepository povezivanja;
    private final LtiRegistracijaRepository registracije;
    private final SpoljniIdentitetRepository identiteti;
    private final StudentRepository studenti;
    private final StudentskaPrijava prijava;
    private final TokenGenerator tokenGenerator;
    private final Clock clock;

    /**
     * Student čiji je Moodle nalog već povezan: nov uređaj (način {@code LTI}) i kolačić {@code gfs_student}; pomera
     * {@code poslednji_ulaz} veze. Prazno kad veze nema.
     */
    @Transactional
    public Optional<Long> prijaviPovezanog(String izdavac, String sub, HttpServletRequest req,
                                           HttpServletResponse res) {
        Optional<SpoljniIdentitet> veza = identiteti.findByVrstaAndIzdavacAndSubjekat(VrstaIdentiteta.LTI, izdavac, sub);
        if (veza.isEmpty()) {
            return Optional.empty();
        }
        SpoljniIdentitet i = veza.get();
        i.setPoslednjiUlaz(LocalDateTime.now(clock));
        Long studentId = i.getStudent().getId();
        prijava.prijavi(studentId, NacinPrijave.LTI, req, res);
        log.info("LTI: prijava povezanog studenta, student={}, identitet={}", studentId, i.getId());
        return Optional.of(studentId);
    }

    /** Learner bez veze: jednokratno povezivanje (30 min) i kolačić {@code __Host-gfs_lti_par} ({@code SameSite=Lax}). */
    @Transactional
    public void zapocni(Long registracijaId, String sub, String ime, HttpServletRequest req, HttpServletResponse res) {
        String token = tokenGenerator.novi();
        LtiPovezivanje p = new LtiPovezivanje();
        p.setTokenHes(TokenHash.od(token));
        p.setRegistracija(registracije.getReferenceById(registracijaId));
        p.setSubjekat(sub);
        p.setIme(ime);
        p.setIstice(LocalDateTime.now(clock).plus(TRAJANJE));
        povezivanja.save(p);
        Kolacici.postavi(req, res, Kolacici.LTI_PAR, token, TRAJANJE, "Lax");
        log.info("LTI: započeto povezivanje, povezivanje={}, registracija={}", p.getId(), registracijaId);
    }

    /** Ime iz ulaska za ekran povezivanja; ne troši povezivanje. 410 kad nijedan kolačić ne važi. */
    @Transactional(readOnly = true)
    public LtiPovezivanjeInfo povezivanje(List<String> tokeniPara) {
        return new LtiPovezivanjeInfo(vazece(tokeniPara, povezivanja::findByTokenHes).getIme());
    }

    /** Kao {@link #povezi(Long, List)} za jednu vrednost kolačića. */
    @Transactional
    public void povezi(Long studentId, String tokenPara) {
        povezi(studentId, tokenPara == null ? Collections.emptyList() : List.of(tokenPara));
    }

    /**
     * Upisuje vezu jednom: prva važeća vrednost kolačića {@code __Host-gfs_lti_par} (red je zaključan, pa se ne troši dvaput).
     * {@code sub} već vezan za drugog studenta: 409 i povezivanje ostaje (pravi student može da ga završi); za istog:
     * ništa se ne menja, povezivanje se troši. Nijedna vrednost ne važi: 410.
     */
    @Transactional
    public void povezi(Long studentId, List<String> tokeniPara) {
        LtiPovezivanje p = vazece(tokeniPara, povezivanja::nadjiZaIzmenu);
        LtiRegistracija r = p.getRegistracija();
        LocalDateTime sada = LocalDateTime.now(clock);
        Optional<SpoljniIdentitet> postoji = identiteti.findByVrstaAndIzdavacAndSubjekat(VrstaIdentiteta.LTI,
                r.getIzdavac(), p.getSubjekat());
        if (postoji.isPresent()) {
            Long vlasnik = postoji.get().getStudent().getId();
            if (!vlasnik.equals(studentId)) {
                log.info("LTI: povezivanje odbijeno, Moodle nalog je vezan za drugog studenta, povezivanje={}, student={}",
                        p.getId(), studentId);
                throw new SystemException(VEC_POVEZAN, 409);
            }
            p.setIskorisceno(sada);
            log.info("LTI: povezivanje već postoji za istog studenta, povezivanje={}, student={}", p.getId(), studentId);
            return;
        }
        SpoljniIdentitet i = new SpoljniIdentitet();
        i.setStudent(studenti.getReferenceById(studentId));
        i.setVrsta(VrstaIdentiteta.LTI);
        i.setIzdavac(r.getIzdavac());
        i.setSubjekat(p.getSubjekat());
        i.setRegistracija(r);
        i.setKreirano(sada);
        i.setPoslednjiUlaz(sada);
        try {
            identiteti.saveAndFlush(i);
        } catch (DataIntegrityViolationException e) {
            // isti sub istovremeno vezan za drugog studenta (UNIQUE vrsta, izdavač, subjekat)
            throw new SystemException(VEC_POVEZAN, 409);
        }
        p.setIskorisceno(sada);
        log.info("LTI: Moodle nalog povezan, student={}, identitet={}, registracija={}", studentId, i.getId(), r.getId());
    }

    /** Prvo važeće povezivanje (nije iskorišćeno ni isteklo) među vrednostima kolačića; inače 410. */
    private LtiPovezivanje vazece(List<String> tokeni, Function<String, Optional<LtiPovezivanje>> nadji) {
        LocalDateTime sada = LocalDateTime.now(clock);
        for (String t : tokeni) {
            if (t == null || !TokenGenerator.FORMAT.matcher(t).matches()) {
                continue;
            }
            Optional<LtiPovezivanje> p = nadji.apply(TokenHash.od(t))
                    .filter(x -> x.getIskorisceno() == null && x.getIstice().isAfter(sada));
            if (p.isPresent()) {
                return p.get();
            }
        }
        throw new SystemException(ISTEKLO, 410);
    }
}
