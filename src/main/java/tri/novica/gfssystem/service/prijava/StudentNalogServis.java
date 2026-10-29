package tri.novica.gfssystem.service.prijava;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.prijava.KodZaDiktiranjeInfo;
import tri.novica.gfssystem.dto.prijava.MoodleVezaInfo;
import tri.novica.gfssystem.dto.prijava.StudentNalogInfo;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.prijava.NacinPrijave;
import tri.novica.gfssystem.entity.prijava.SpoljniIdentitet;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.prijava.SpoljniIdentitetRepository;
import tri.novica.gfssystem.repository.prijava.StudentskiUredjajRepository;
import tri.novica.gfssystem.repository.push.PushPretplataRepository;
import tri.novica.gfssystem.utility.IndeksUtil;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Sekcija "Nalog" na kartici studenta (nastavnik, spec 5.3): pregled uređaja i Moodle veze, kod na mejl studenta,
 * opoziv uređaja i raskid Moodle veze. Sve ide po id-ju studenta (nastavnik bira karticu), pa nema pretrage po indeksu
 * i ne važi javna bela lista indeksa; nepostojeći student je 404 {@code Ne postoji.}. Log: samo id-jevi, nikad token,
 * mejl, Moodle identifikatori ni indeks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentNalogServis {

    private final StudentRepository studenti;
    private final StudentskaPrijava prijava;
    private final StudentskiUredjajRepository uredjaji;
    private final PushPretplataRepository pretplate;
    private final SpoljniIdentitetRepository identiteti;
    private final KodServis kodovi;
    private final ObavestenjeServis obavestenja;
    private final Clock clock;

    @Transactional(readOnly = true)
    public StudentNalogInfo pregled(Long studentId) {
        Student s = nadji(studentId);
        List<SpoljniIdentitet> moodle = identiteti.findAllByStudentId(studentId);
        LocalDateTime moodleUlaz = moodle.stream().map(SpoljniIdentitet::getPoslednjiUlaz).filter(Objects::nonNull)
                .max(LocalDateTime::compareTo).orElse(null);
        LocalDateTime poslednja = Stream.of(uredjaji.poslednjiUlaz(studentId).orElse(null), moodleUlaz)
                .filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
        return new StudentNalogInfo(s.getEmail() != null && !s.getEmail().isBlank(),
                prijava.uredjaji(studentId, null), new MoodleVezaInfo(!moodle.isEmpty(), moodleUlaz), poslednja);
    }

    /**
     * Šalje kod za prijavu na mejl studenta ({@link KodServis#posaljiOdNastavnika}: bez limita po studentu, 20/h ukupno) i
     * tek posle toga rešava njegova otvorena obaveštenja ("Ne stiže mi kod", spec 6). Mora da se pozove bez spoljne
     * transakcije ({@code NEVER}): slanje ima svoju transakciju i mejl ide posle njenog commit-a, a rešavanje je masovan
     * update ({@code clearAutomatically}) koji sme tek kad je slanje potvrđeno. Neuspelo slanje (nema mejla, 429, 503)
     * ne rešava ništa. Greška pri rešavanju se samo loguje: kod je već poslat, pa nastavnik ne dobija grešku.
     * <p>
     * Indeks koji nije u obliku forme u tri dela ne može da se unese u formu za prijavu, pa mu kod ne bi koristio: samo upozorenje u logu.
     */
    @Transactional(propagation = Propagation.NEVER)
    public void posaljiKod(Long studentId) {
        Student s = nadji(studentId);
        String indeks = IndeksUtil.kanonski(s.getIndeks(), s.getGodina());
        if (!IndeksUtil.jeJavniOblik(indeks)) {
            log.warn("Kod sa kartice za studenta čiji je indeks mimo bele liste, prijava kodom mu nije moguća, student={}", studentId);
        }
        kodovi.posaljiOdNastavnika(studentId);
        try {
            int resenih = obavestenja.resiZaStudenta(studentId);
            log.info("Obaveštenja studenta rešena slanjem koda sa kartice, student={}, resenih={}", studentId, resenih);
        } catch (RuntimeException e) {
            log.error("Obaveštenja studenta nisu rešena posle koda sa kartice, student={}: {}", studentId, e.getClass().getSimpleName());
        }
    }

    /** Upozorenje uz kod za diktiranje kad indeks studenta nije u obliku forme u tri dela. */
    static final String UPOZORENJE_INDEKS = "Indeks ovog studenta ne može da se unese u formu za prijavu (smer i broj od 1 do 3 cifre). "
            + "Ispravi indeks na kartici studenta, pa izdaj nov kod.";

    /**
     * Kod za diktiranje (plan C, {@link KodServis#kodZaDiktiranje}): bez mejla, i studentu bez mejla; posle izdavanja rešava
     * otvorena obaveštenja studenta (kao {@link #posaljiKod}, isti redosled i isto pravilo: greška pri rešavanju se samo
     * loguje). Vraća kod jednom, sa indeksom i godinom koje student unosi; vrednost koda nikad ne ide u log.
     */
    @Transactional(propagation = Propagation.NEVER)
    public KodZaDiktiranjeInfo kodZaDiktiranje(Long studentId) {
        Student s = nadji(studentId);
        String indeks = IndeksUtil.kanonski(s.getIndeks(), s.getGodina());
        boolean unosiv = IndeksUtil.jeJavniOblik(indeks);
        if (!unosiv) {
            log.warn("Kod za diktiranje za studenta čiji indeks nije u obliku forme, student={}", studentId);
        }
        KodServis.DiktiranKod d = kodovi.kodZaDiktiranje(studentId);
        try {
            int resenih = obavestenja.resiZaStudenta(studentId);
            log.info("Obaveštenja studenta rešena kodom za diktiranje, student={}, resenih={}", studentId, resenih);
        } catch (RuntimeException e) {
            log.error("Obaveštenja studenta nisu rešena posle koda za diktiranje, student={}: {}", studentId, e.getClass().getSimpleName());
        }
        return new KodZaDiktiranjeInfo(d.kod(), d.istice(), indeks, s.getGodina(), unosiv ? null : UPOZORENJE_INDEKS);
    }

    /** Opoziva sve važeće uređaje studenta; vraća koliko ih je bilo. */
    public int opozoviUredjaje(Long studentId) {
        nadji(studentId);
        int n = prijava.odjaviSve(studentId);
        log.info("Uređaji studenta opozvani sa kartice, student={}, opozvano={}", studentId, n);
        return n;
    }

    /**
     * Raskida Moodle vezu: briše spoljne identitete studenta (isti Moodle nalog može posle da se poveže sa drugim studentom)
     * i opoziva njegove uređaje otvorene kroz Moodle, da pogrešno povezan Moodle nalog ne ostane prijavljen kao on. Uređaji
     * otvoreni kodom ostaju. Ponovljeno je bez efekta.
     */
    @Transactional
    public void raskiniMoodle(Long studentId) {
        nadji(studentId);
        int veza = identiteti.obrisiZaStudenta(studentId);
        int uredjajaLti = uredjaji.opozoviPoNacinu(studentId, NacinPrijave.LTI, LocalDateTime.now(clock));
        pretplate.obrisiZaOpozvaneUredjaje(studentId);
        log.info("Moodle veza studenta raskinuta sa kartice, student={}, veza={}, opozvanihUredjaja={}", studentId, veza, uredjajaLti);
    }

    private Student nadji(Long studentId) {
        return studenti.findById(studentId).orElseThrow(() -> new SystemException("Ne postoji.", 404));
    }
}
