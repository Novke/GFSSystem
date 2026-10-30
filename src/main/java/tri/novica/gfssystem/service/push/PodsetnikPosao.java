package tri.novica.gfssystem.service.push;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tri.novica.gfssystem.entity.push.VrstaStudentskogObavestenja;
import tri.novica.gfssystem.entity.raspored.NajavaOdsustva;
import tri.novica.gfssystem.entity.raspored.RasporedStavka;
import tri.novica.gfssystem.repository.raspored.NajavaOdsustvaRepository;
import tri.novica.gfssystem.repository.raspored.OtkazanTerminRepository;
import tri.novica.gfssystem.repository.raspored.RasporedStavkaRepository;
import tri.novica.gfssystem.service.portal.PodesavanjaStudentaServis.Prekidac;
import tri.novica.gfssystem.service.push.PushTekstovi.SutrasnjiTermin;
import tri.novica.gfssystem.service.raspored.Termini;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Podsetnik za sutrašnji čas (spec aplikacije 6.1), posao na 5 min ({@code gfs.push.podsetnik.cron}, {@code -} gasi):
 * <ul>
 *   <li>kome: studentu kome sutra ima bar jedan termin, po istom pravilu "kome važi stavka" kao najava
 *       ({@code RasporedStavkaRepository.parovi}); otkazan termin i termin za koji je student javio "Neću doći" se preskaču,
 *       a bez preostalih termina podsetnika nema; samo termini perioda sa uključenim podsetnikom;</li>
 *   <li>kada: u najranije vreme podsetnika među periodima studentovih sutrašnjih termina; ako server tada nije radio (spot),
 *       kasnije, ali najkasnije 3 h posle tog vremena i nikad posle 22:00; inače se preskače;</li>
 *   <li>jednom: red {@code PODSETNIK:<sutra>} u {@code studentska_obavestenja} se upisuje pre slanja ({@code INSERT
 *       IGNORE}), pa ponovljen posao, restart ili dve instance ne šalju dvaput; push samo za sada upisane redove, po
 *       prekidaču {@code push_podsetnik}, TTL do početka prvog časa.</li>
 * </ul>
 */
@Component
@Slf4j
public class PodsetnikPosao {

    static final Duration NADOKNADA = Duration.ofHours(3);
    static final LocalTime GRANICA = LocalTime.of(22, 0);

    private final RasporedStavkaRepository stavke;
    private final OtkazanTerminRepository otkazani;
    private final NajavaOdsustvaRepository najave;
    private final StudentskaObavestenja obavestenja;
    private final TransactionTemplate transakcija;
    private final Clock clock;

    public PodsetnikPosao(RasporedStavkaRepository stavke, OtkazanTerminRepository otkazani, NajavaOdsustvaRepository najave,
                          StudentskaObavestenja obavestenja, PlatformTransactionManager transakcije, Clock clock) {
        this.stavke = stavke;
        this.otkazani = otkazani;
        this.najave = najave;
        this.obavestenja = obavestenja;
        this.transakcija = new TransactionTemplate(transakcije);
        this.transakcija.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.clock = clock;
    }

    @Scheduled(cron = "${gfs.push.podsetnik.cron:0 */5 * * * *}")
    public void zakazano() {
        try {
            posalji();
        } catch (RuntimeException e) {
            log.error("Podsetnik za sutrašnji čas nije obrađen: {}", e.getClass().getSimpleName());
        }
    }

    /** Jedan prolaz; vraća broj studenata kojima je podsetnik sada upisan (i stavljen u red za push po prekidaču). */
    public int posalji() {
        LocalDateTime sada = LocalDateTime.now(clock);
        LocalDate sutra = sada.toLocalDate().plusDays(1);
        Map<Long, LocalDateTime> novi = transakcija.execute(s -> upisi(sada, sutra));
        if (novi == null || novi.isEmpty()) {
            return 0;
        }
        String kljuc = kljuc(sutra);
        // TTL do početka prvog časa svakog studenta: jedno slanje po početku
        Map<LocalDateTime, List<Long>> poPocetku = novi.entrySet().stream().collect(Collectors.groupingBy(
                Map.Entry::getValue, TreeMap::new, Collectors.mapping(Map.Entry::getKey, Collectors.toList())));
        int poruka = 0;
        for (Map.Entry<LocalDateTime, List<Long>> e : poPocetku.entrySet()) {
            poruka += obavestenja.posalji(kljuc, e.getValue(), Prekidac.PUSH_PODSETNIK, Duration.between(sada, e.getKey()),
                    false);
        }
        log.info("Podsetnik za {}: upisano studenata={}, push poruka={}", sutra, novi.size(), poruka);
        return novi.size();
    }

    static String kljuc(LocalDate datum) {
        return "PODSETNIK:" + datum;
    }

    /** U transakciji: studenti kojima podsetnik sada dospeva i kojima je red upisan, sa početkom prvog sutrašnjeg časa. */
    private Map<Long, LocalDateTime> upisi(LocalDateTime sada, LocalDate sutra) {
        LocalDate danas = sada.toLocalDate();
        List<RasporedStavka> sve = stavke.uProzoru(sutra, sutra).stream()
                .filter(s -> s.getRaspored().getPodsetnikVreme() != null && Termini.jeTermin(s, sutra))
                .toList();
        // niko ne dospeva dok nijedno vreme podsetnika nije stiglo (posao radi na 5 min)
        if (sve.stream().noneMatch(s -> !danas.atTime(s.getRaspored().getPodsetnikVreme()).isAfter(sada))) {
            return Map.of();
        }
        List<Long> ids = sve.stream().map(RasporedStavka::getId).toList();
        Set<Long> otkazane = otkazani.uProzoru(ids, sutra, sutra).stream().map(o -> o.getStavka().getId())
                .collect(Collectors.toSet());
        Set<String> najavljeno = new HashSet<>();
        for (NajavaOdsustva n : najave.uProzoruSaStudentima(ids, sutra, sutra)) {
            najavljeno.add(n.getStavka().getId() + ":" + n.getStudent().getId());
        }
        Map<Long, List<RasporedStavka>> poStudentu = new LinkedHashMap<>();
        for (RasporedStavka s : sve) {
            if (otkazane.contains(s.getId())) continue;
            for (Object[] r : stavke.parovi(s.getId(), null, sutra)) {
                Long studentId = ((Number) r[1]).longValue();
                if (!najavljeno.contains(s.getId() + ":" + studentId)) {
                    poStudentu.computeIfAbsent(studentId, k -> new ArrayList<>()).add(s);
                }
            }
        }
        Map<Long, LocalDateTime> novi = new LinkedHashMap<>();
        for (Map.Entry<Long, List<RasporedStavka>> e : poStudentu.entrySet()) {
            List<RasporedStavka> njegove = e.getValue();
            LocalTime vreme = njegove.stream().map(s -> s.getRaspored().getPodsetnikVreme()).min(Comparator.naturalOrder())
                    .orElseThrow();
            if (!dospeva(sada, vreme)) continue;
            List<SutrasnjiTermin> termini = njegove.stream()
                    .sorted(Comparator.comparing(RasporedStavka::getPocetak).thenComparing(s -> s.getPredmet().getNaziv())
                            .thenComparing(RasporedStavka::getId))
                    .map(s -> new SutrasnjiTermin(s.getPocetak(), s.getPredmet().getNaziv()))
                    .distinct()
                    .toList();
            if (obavestenja.upisi(e.getKey(), VrstaStudentskogObavestenja.PODSETNIK, kljuc(sutra),
                    PushTekstovi.podsetnik(sutra, termini))) {
                novi.put(e.getKey(), sutra.atTime(termini.getFirst().pocetak()));
            }
        }
        return novi;
    }

    /** Vreme {@code vreme} danas je stiglo, a nije prošlo više od 3 h ni 22:00. */
    static boolean dospeva(LocalDateTime sada, LocalTime vreme) {
        LocalDateTime t = sada.toLocalDate().atTime(vreme);
        LocalDateTime kraj = t.plus(NADOKNADA);
        LocalDateTime granica = sada.toLocalDate().atTime(GRANICA);
        if (kraj.isAfter(granica)) kraj = granica;
        return !sada.isBefore(t) && !sada.isAfter(kraj);
    }
}
