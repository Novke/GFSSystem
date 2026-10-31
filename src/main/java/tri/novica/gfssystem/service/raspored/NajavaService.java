package tri.novica.gfssystem.service.raspored;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.raspored.NaredniCasovi;
import tri.novica.gfssystem.dto.raspored.NajavaCmd;
import tri.novica.gfssystem.dto.raspored.StudentskiTermin;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.raspored.NajavaOdsustva;
import tri.novica.gfssystem.entity.raspored.OtkazanTermin;
import tri.novica.gfssystem.entity.raspored.RasporedStavka;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.raspored.NajavaOdsustvaRepository;
import tri.novica.gfssystem.repository.raspored.OtkazanTerminRepository;
import tri.novica.gfssystem.repository.raspored.RasporedStavkaRepository;
import tri.novica.gfssystem.service.prijava.ObavestenjeServis;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Najava odsustva sa naloga studenta (spec najave odsustva, odeljak 4): naredni časovi, "Neću doći" i "Ipak dolazim".
 * Student dolazi samo iz kolačića (REST), nikad iz tela. Stavka važi za studenta po jednom pravilu
 * ({@link RasporedStavkaRepository#parovi}). Najava i povlačenje zaključavaju red stavke, pa najave jednog termina idu
 * redom: broj je tačan i obaveštenje {@code MNOGO_NAJAVA} nastaje jednom. Odgovor studentu nikad ne nosi broj najava ni
 * tuđa imena. Sve se vraća kao DTO napravljen u transakciji (open-in-view je isključen za {@code /public/nalog/**}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(isolation = Isolation.READ_COMMITTED)
public class NajavaService {

    /** Koliko narednih termina po stavci vidi student (oko mesec dana za čas jednom nedeljno). */
    public static final int NAREDNIH = 4;
    public static final int MAX_RAZLOG = 200;
    public static final String NIJE_TVOJ = "Taj čas nije u tvom rasporedu.";
    public static final String OTKAZAN = "Ovaj čas je otkazan.";
    public static final String POCEO = "Čas je već počeo.";
    public static final String PREDUG_RAZLOG = "Razlog može imati najviše " + MAX_RAZLOG + " znakova.";

    private final RasporedStavkaRepository stavke;
    private final OtkazanTerminRepository otkazani;
    private final NajavaOdsustvaRepository najave;
    private final StudentRepository studenti;
    private final ObavestenjeServis obavestenja;
    private final Clock clock;

    /** Naredna {@value #NAREDNIH} termina svake stavke koja važi za studenta (današnji dok ne počne), po datumu i početku. */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public NaredniCasovi naredni(long studentId) {
        LocalDateTime sada = LocalDateTime.now(clock);
        Set<Long> ids = stavkeStudenta(studentId, sada.toLocalDate());
        if (ids.isEmpty()) {
            return new NaredniCasovi(List.of());
        }
        Long svojaGrupa = studenti.findById(studentId).map(Student::getGrupa).map(Grupa::getId).orElse(null);
        record Par(RasporedStavka s, LocalDate d) {
        }
        List<Par> parovi = new ArrayList<>();
        for (RasporedStavka s : stavke.saDetaljima(ids)) {
            for (LocalDate d : Termini.naredni(s, sada, NAREDNIH)) {
                parovi.add(new Par(s, d));
            }
        }
        if (parovi.isEmpty()) {
            return new NaredniCasovi(List.of());
        }
        LocalDate od = parovi.stream().map(Par::d).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate doDatuma = parovi.stream().map(Par::d).max(Comparator.naturalOrder()).orElseThrow();
        Map<List<Object>, OtkazanTermin> otk = new HashMap<>();
        otkazani.uProzoru(ids, od, doDatuma).forEach(o -> otk.put(List.of(o.getStavka().getId(), o.getDatum()), o));
        Map<List<Object>, NajavaOdsustva> moje = new HashMap<>();
        najave.studentaUProzoru(studentId, ids, od, doDatuma)
                .forEach(n -> moje.put(List.of(n.getStavka().getId(), n.getDatum()), n));
        List<StudentskiTermin> termini = parovi.stream()
                .map(p -> {
                    List<Object> k = List.of(p.s().getId(), p.d());
                    return termin(p.s(), p.d(), svojaGrupa, otk.get(k), moje.get(k));
                })
                .sorted(Comparator.comparing(StudentskiTermin::datum).thenComparing(StudentskiTermin::pocetak)
                        .thenComparing(StudentskiTermin::predmet).thenComparing(StudentskiTermin::stavkaId))
                .toList();
        return new NaredniCasovi(termini);
    }

    /**
     * "Neću doći" (ponovljeno samo menja razlog). 404 {@value #NIJE_TVOJ} za stavku koja ne postoji ili ne važi za
     * studenta, datum koji nije termin i termin van narednih {@value #NAREDNIH} (isti prozor kao kartica), 409 {@value #POCEO} i {@value #OTKAZAN}, 400 za predug razlog. Kad broj najava
     * termina pređe prag perioda: jedno obaveštenje nastavniku ({@link ObavestenjeServis#mnogoNajava}).
     */
    public StudentskiTermin najavi(long studentId, NajavaCmd cmd) {
        String razlog = razlog(cmd.razlog());
        RasporedStavka s = zakljucaj(studentId, cmd);
        NajavaOdsustva n = najave.findByStavkaIdAndDatumAndStudentId(s.getId(), cmd.datum(), studentId).orElseGet(() -> {
            NajavaOdsustva nova = new NajavaOdsustva();
            nova.setStavka(s);
            nova.setDatum(cmd.datum());
            nova.setStudent(studenti.getReferenceById(studentId));
            nova.setVreme(LocalDateTime.now(clock));
            return nova;
        });
        boolean nova = n.getId() == null;
        n.setRazlog(razlog);
        najave.saveAndFlush(n);
        if (nova) {
            long broj = najave.countByStavkaIdAndDatum(s.getId(), cmd.datum());
            int prag = s.getRaspored().getPragNajava();
            log.info("Najava odsustva, stavka={}, datum={}, student={}, broj={}", s.getId(), cmd.datum(), studentId, broj);
            if (broj > prag) {
                obavestenja.mnogoNajava(TerminOpis.od(s, cmd.datum()), broj, prag);
            }
        }
        return termin(s, cmd.datum(), svojaGrupa(studentId), null, n);
    }

    /** "Ipak dolazim": briše najavu (bez najave nije greška). Iste provere kao {@link #najavi}, bez razloga. */
    public StudentskiTermin povuci(long studentId, NajavaCmd cmd) {
        RasporedStavka s = zakljucaj(studentId, cmd);
        if (najave.obrisi(s.getId(), cmd.datum(), studentId) > 0) {
            log.info("Najava odsustva povučena, stavka={}, datum={}, student={}", s.getId(), cmd.datum(), studentId);
        }
        return termin(s, cmd.datum(), svojaGrupa(studentId), null, null);
    }

    /**
     * Provera da stavka važi za studenta (bez brave), pa red stavke {@code FOR UPDATE}, pa termin, početak, prozor
     * narednih {@value #NAREDNIH} i otkazivanje.
     * Redosled brava: red stavke, pa (u {@code mnogoNajava}) MySQL brava mejla nastavniku.
     */
    private RasporedStavka zakljucaj(long studentId, NajavaCmd cmd) {
        LocalDateTime sada = LocalDateTime.now(clock);
        if (cmd.stavkaId() == null || cmd.datum() == null
                || stavke.parovi(cmd.stavkaId(), studentId, sada.toLocalDate()).isEmpty()) {
            throw nijeTvoj();
        }
        RasporedStavka s = stavke.findByIdForUpdate(cmd.stavkaId()).orElseThrow(NajavaService::nijeTvoj);
        if (!Termini.jeTermin(s, cmd.datum())) {
            throw nijeTvoj();
        }
        if (Termini.poceo(sada, cmd.datum(), s.getPocetak())) {
            throw new SystemException(POCEO, HttpStatus.CONFLICT);
        }
        if (!Termini.naredni(s, sada, NAREDNIH).contains(cmd.datum())) {
            // samo termini koje kartica pokazuje (naredna NAREDNIH po stavci), ne bilo koji budući datum perioda
            throw nijeTvoj();
        }
        if (otkazani.existsByStavkaIdAndDatum(s.getId(), cmd.datum())) {
            throw new SystemException(OTKAZAN, HttpStatus.CONFLICT);
        }
        return s;
    }

    private Set<Long> stavkeStudenta(long studentId, LocalDate od) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Object[] red : stavke.parovi(null, studentId, od)) {
            ids.add(((Number) red[0]).longValue());
        }
        return ids;
    }

    private Long svojaGrupa(long studentId) {
        return studenti.findById(studentId).map(Student::getGrupa).map(Grupa::getId).orElse(null);
    }

    private static String razlog(String tekst) {
        try {
            return Termini.ocisti(tekst, MAX_RAZLOG);
        } catch (IllegalArgumentException e) {
            throw new SystemException(PREDUG_RAZLOG, HttpStatus.BAD_REQUEST);
        }
    }

    private static StudentskiTermin termin(RasporedStavka s, LocalDate d, Long svojaGrupa, OtkazanTermin o,
                                           NajavaOdsustva n) {
        return new StudentskiTermin(s.getId(), d, s.getPocetak(), s.getKraj(), s.getPredmet().getNaziv(),
                s.getGrupa().getNaziv(), s.getGrupa().getId().equals(svojaGrupa), o != null,
                o == null ? null : o.getNapomena(), n != null, n == null ? null : n.getRazlog());
    }

    private static SystemException nijeTvoj() {
        return new SystemException(NIJE_TVOJ, HttpStatus.NOT_FOUND);
    }
}
