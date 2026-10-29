package tri.novica.gfssystem.service.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.dto.portal.PodesavanjaNaloga;
import tri.novica.gfssystem.dto.push.StavkaZvonaInfo;
import tri.novica.gfssystem.dto.push.ZvonoInfo;
import tri.novica.gfssystem.entity.push.StudentskoObavestenje;
import tri.novica.gfssystem.entity.push.VrstaStudentskogObavestenja;
import tri.novica.gfssystem.repository.push.StudentskoObavestenjeRepository;
import tri.novica.gfssystem.service.portal.PodesavanjaStudentaServis;
import tri.novica.gfssystem.service.portal.PodesavanjaStudentaServis.Prekidac;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Obaveštenja studentu (spec aplikacije 6.8): jedini izvor za zvono na nalogu i za push poruke studentu. Svaki događaj
 * (podsetnik, otkazan čas, ipak se održava, ocene, posle časa) upisuje <b>jedan red po studentu</b> u
 * {@code studentska_obavestenja} u transakciji događaja, sa sadržajem zamrznutim u tom trenutku; ključ je jedinstven po
 * studentu, pa ponovljen događaj ne pravi drugi red ni drugi push. Red nastaje bez obzira na prekidače: prekidači zvona
 * filtriraju prikaz ({@link #zvono}), prekidači push-a slanje ({@link #posalji}).
 */
@Service
@Slf4j
public class StudentskaObavestenja {

    public static final int ZVONO_NAJVISE = 30;
    /** Vrste uvek vidljive u zvonu (ne mogu da se isključe). */
    private static final Set<VrstaStudentskogObavestenja> UVEK = EnumSet.of(VrstaStudentskogObavestenja.OTKAZAN_CAS,
            VrstaStudentskogObavestenja.ODRZAVA_SE);

    private final StudentskoObavestenjeRepository repository;
    private final PodesavanjaStudentaServis podesavanja;
    private final PushPosiljalac posiljalac;
    private final TransactionTemplate citanje;
    private final Clock clock;

    public StudentskaObavestenja(StudentskoObavestenjeRepository repository, PodesavanjaStudentaServis podesavanja,
                                 PushPosiljalac posiljalac, PlatformTransactionManager transakcije, Clock clock) {
        this.repository = repository;
        this.podesavanja = podesavanja;
        this.posiljalac = posiljalac;
        this.citanje = new TransactionTemplate(transakcije);
        this.citanje.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.citanje.setReadOnly(true);
        this.clock = clock;
    }

    /**
     * Isti sadržaj za više studenata, u pozivaočevoj transakciji. Vraća studente kojima je red <b>sada</b> upisan
     * (postojeći ključ se preskače).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Set<Long> upisi(Collection<Long> studentIds, VrstaStudentskogObavestenja vrsta, String kljuc, PushPoruka poruka) {
        Set<Long> novi = new LinkedHashSet<>();
        LocalDateTime sada = sada();
        for (Long id : new LinkedHashSet<>(studentIds)) {
            if (upisi(id, vrsta, kljuc, poruka, sada)) {
                novi.add(id);
            }
        }
        return novi;
    }

    /** Jedan red za jednog studenta (sadržaj po studentu, npr. podsetnik); {@code true} kad je upisan sada. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean upisi(Long studentId, VrstaStudentskogObavestenja vrsta, String kljuc, PushPoruka poruka) {
        return upisi(studentId, vrsta, kljuc, poruka, sada());
    }

    private boolean upisi(Long studentId, VrstaStudentskogObavestenja vrsta, String kljuc, PushPoruka p, LocalDateTime sada) {
        return repository.upisi(studentId, vrsta.name(), kljuc, p.naslov(), p.tekst(), p.putanja(), p.oznaka(), sada) > 0;
    }

    /**
     * Push iz redova ključa {@code kljuc} datim studentima koji nisu isključili {@code prekidac}; zove se van transakcije
     * ili na niti push izvršioca ({@link PushPosiljalac#uPozadiniPosleCommita}), nikad iz {@code afterCommit} niti
     * zahteva. Vraća broj push poruka stavljenih u red.
     */
    public int posalji(String kljuc, Collection<Long> studentIds, Prekidac prekidac, Duration ttl, boolean hitno) {
        if (kljuc == null || studentIds.isEmpty()) {
            return 0;
        }
        Map<Long, PushPoruka> poruke = citanje.execute(s -> {
            Set<Long> ne = podesavanja.iskljucili(prekidac, studentIds);
            Map<Long, PushPoruka> m = new LinkedHashMap<>();
            for (StudentskoObavestenje o : repository.findAllByKljucAndStudentIdIn(kljuc, studentIds)) {
                if (!ne.contains(o.getStudentId())) {
                    m.put(o.getStudentId(), new PushPoruka(o.getNaslov(), o.getTekst(), o.getPutanja(), o.getOznaka()));
                }
            }
            return m;
        });
        return poruke == null || poruke.isEmpty() ? 0 : posiljalac.studentima(poruke, ttl, hitno);
    }

    /** Zvono studenta: poslednjih {@value #ZVONO_NAJVISE} vidljivih stavki i da li ima novih od poslednjeg otvaranja. */
    @Transactional(readOnly = true)
    public ZvonoInfo zvono(long studentId) {
        PodesavanjaNaloga p = podesavanja.za(studentId);
        Set<VrstaStudentskogObavestenja> vrste = EnumSet.copyOf(UVEK);
        if (p.zvonoOcene()) vrste.add(VrstaStudentskogObavestenja.OCENE);
        if (p.zvonoPosleCasa()) vrste.add(VrstaStudentskogObavestenja.POSLE_CASA);
        List<StavkaZvonaInfo> stavke = repository.zaZvono(studentId, vrste, PageRequest.of(0, ZVONO_NAJVISE)).stream()
                .map(o -> new StavkaZvonaInfo(o.getId(), o.getVrsta(), o.getNaslov(), o.getTekst(), o.getPutanja(),
                        o.getKreirano()))
                .toList();
        Optional<LocalDateTime> otvoreno = podesavanja.zvonoOtvorenoU(studentId);
        boolean novo = !stavke.isEmpty() && otvoreno.map(t -> stavke.getFirst().kreirano().isAfter(t)).orElse(true);
        return new ZvonoInfo(novo, stavke);
    }

    /** Student je otvorio zvono: tačka se gasi na svim uređajima. */
    @Transactional
    public void otvoreno(long studentId) {
        podesavanja.zvonoOtvoreno(studentId);
    }

    private LocalDateTime sada() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }
}
