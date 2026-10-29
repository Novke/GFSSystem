package tri.novica.gfssystem.service.raspored;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.raspored.NajavaInfo;
import tri.novica.gfssystem.dto.raspored.PromenaTerminaOdgovor;
import tri.novica.gfssystem.dto.raspored.TerminCmd;
import tri.novica.gfssystem.dto.raspored.TerminInfo;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.raspored.NajavaOdsustva;
import tri.novica.gfssystem.entity.raspored.OtkazanTermin;
import tri.novica.gfssystem.entity.raspored.RasporedStavka;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredavanjeRepository;
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
 * Termini za nastavnika (spec najave odsustva, odeljci 3.2 i 6): pregled termina u prozoru sa najavama (imena i razlozi,
 * samo ovde), otkazivanje i vraćanje termina. Otkazivanje i vraćanje zaključavaju red stavke (isto kao najava), rešavaju
 * obaveštenje {@code MNOGO_NAJAVA} termina i javljaju studentima kroz {@link ObavestavanjeStudenata} (posle commit-a).
 * Ponovljeno otkazivanje menja samo napomenu i ne šalje ništa; vraćanje neotkazanog termina ne radi ništa.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(isolation = Isolation.READ_COMMITTED)
public class TerminService {

    public static final int PODRAZUMEVANO_DANA = 28;
    public static final int MAX_DANA = 120;
    public static final int MAX_NAPOMENA = 200;
    public static final String NE_POSTOJI = "Termin ne postoji.";
    public static final String POCEO = "Termin je već počeo.";

    private final RasporedStavkaRepository stavke;
    private final OtkazanTerminRepository otkazani;
    private final NajavaOdsustvaRepository najave;
    private final PredavanjeRepository predavanja;
    private final StudentRepository studenti;
    private final ObavestenjeServis obavestenja;
    private final ObavestavanjeStudenata obavestavanje;
    private final Clock clock;

    /** Jedan izračunat termin sa izuzecima, najavama i predavanjem (zajedničko za nastavnički pregled i početnu). */
    public record Termin(RasporedStavka stavka, LocalDate datum, OtkazanTermin otkazan, List<NajavaOdsustva> najave,
                         Long predavanjeId) {
    }

    /** Termini od {@code od} (null = danas) narednih {@code dana} (null = 28, 1-120), po datumu pa početku. */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<TerminInfo> termini(LocalDate od, Integer dana) {
        int n = dana == null ? PODRAZUMEVANO_DANA : dana;
        if (n < 1 || n > MAX_DANA) {
            throw new SystemException("Neispravan parametar: dana.", HttpStatus.BAD_REQUEST);
        }
        LocalDate pocetak = od == null ? LocalDate.now(clock) : od;
        LocalDateTime sada = LocalDateTime.now(clock);
        return uProzoru(pocetak, pocetak.plusDays(n - 1L), null).stream().map(t -> info(t, sada)).toList();
    }

    /**
     * Svi termini stavki u prozoru {@code [od, doDatuma]} ({@code samoStavka} null = svih), sa otkazivanjem, najavama
     * (sa studentima) i predavanjem istog predmeta, grupe i datuma. Pet upita, bez N+1.
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<Termin> uProzoru(LocalDate od, LocalDate doDatuma, Long samoStavka) {
        List<RasporedStavka> lista = stavke.uProzoru(od, doDatuma).stream()
                .filter(s -> samoStavka == null || samoStavka.equals(s.getId())).toList();
        if (lista.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new HashSet<>();
        lista.forEach(s -> ids.add(s.getId()));
        Map<List<Object>, OtkazanTermin> otk = new HashMap<>();
        otkazani.uProzoru(ids, od, doDatuma).forEach(o -> otk.put(List.of(o.getStavka().getId(), o.getDatum()), o));
        Map<List<Object>, List<NajavaOdsustva>> naj = new HashMap<>();
        najave.uProzoruSaStudentima(ids, od, doDatuma).forEach(x ->
                naj.computeIfAbsent(List.of(x.getStavka().getId(), x.getDatum()), k -> new ArrayList<>()).add(x));
        Map<List<Object>, Long> pred = new HashMap<>();
        for (Object[] r : predavanja.poPredmetuGrupiIDatumu(od, doDatuma)) {
            pred.put(List.of(((Number) r[0]).longValue(), ((Number) r[1]).longValue(), r[2]), ((Number) r[3]).longValue());
        }
        List<Termin> termini = new ArrayList<>();
        for (RasporedStavka s : lista) {
            for (LocalDate d : Termini.uProzoru(s, od, doDatuma)) {
                List<Object> k = List.of(s.getId(), d);
                termini.add(new Termin(s, d, otk.get(k), naj.getOrDefault(k, List.of()),
                        pred.get(List.of(s.getPredmet().getId(), s.getGrupa().getId(), d))));
            }
        }
        termini.sort(Comparator.comparing(Termin::datum)
                .thenComparing(t -> t.stavka().getPocetak())
                .thenComparing(t -> t.stavka().getPredmet().getNaziv(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(t -> t.stavka().getGrupa().getNaziv(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(t -> t.stavka().getId()));
        return termini;
    }

    /**
     * Otkazuje budući termin (napomena do {@value #MAX_NAPOMENA} znakova, ne mora): rešava {@code MNOGO_NAJAVA} termina i
     * javlja svakom studentu za koga stavka važi. Ponovljeno: samo nova napomena, bez poruka. 404 {@value #NE_POSTOJI},
     * 409 {@value #POCEO}.
     */
    public PromenaTerminaOdgovor otkazi(TerminCmd cmd) {
        String napomena;
        try {
            napomena = Termini.ocisti(cmd.napomena(), MAX_NAPOMENA);
        } catch (IllegalArgumentException e) {
            throw new SystemException("Napomena može imati najviše " + MAX_NAPOMENA + " znakova.", HttpStatus.BAD_REQUEST);
        }
        RasporedStavka s = zakljucaj(cmd);
        LocalDateTime sada = LocalDateTime.now(clock);
        int obavesteno = 0;
        Optional<OtkazanTermin> postojece = otkazani.findByStavkaIdAndDatum(s.getId(), cmd.datum());
        if (postojece.isPresent()) {
            postojece.get().setNapomena(napomena);
        } else {
            OtkazanTermin o = new OtkazanTermin();
            o.setStavka(s);
            o.setDatum(cmd.datum());
            o.setNapomena(napomena);
            o.setOtkazano(sada);
            otkazani.save(o);
            TerminOpis opis = TerminOpis.od(s, cmd.datum());
            obavestenja.resiZaTermin(opis);
            obavesteno = obavestavanje.obavesti(primaoci(s, cmd.datum()),
                    new PromenaTermina(PromenaTermina.Vrsta.OTKAZAN, opis, napomena));
            log.info("Termin otkazan, stavka={}, datum={}, obavesteno={}", s.getId(), cmd.datum(), obavesteno);
        }
        otkazani.flush();
        return new PromenaTerminaOdgovor(jedan(s, cmd.datum(), sada), obavesteno);
    }

    /** Vraća otkazan budući termin i javlja "čas se ipak održava"; neotkazan: ništa. 404 i 409 kao {@link #otkazi}. */
    public PromenaTerminaOdgovor vrati(TerminCmd cmd) {
        RasporedStavka s = zakljucaj(cmd);
        LocalDateTime sada = LocalDateTime.now(clock);
        int obavesteno = 0;
        Optional<OtkazanTermin> postojece = otkazani.findByStavkaIdAndDatum(s.getId(), cmd.datum());
        if (postojece.isPresent()) {
            otkazani.delete(postojece.get());
            otkazani.flush();
            obavesteno = obavestavanje.obavesti(primaoci(s, cmd.datum()),
                    new PromenaTermina(PromenaTermina.Vrsta.VRACEN, TerminOpis.od(s, cmd.datum()), null));
            log.info("Termin vraćen, stavka={}, datum={}, obavesteno={}", s.getId(), cmd.datum(), obavesteno);
        }
        return new PromenaTerminaOdgovor(jedan(s, cmd.datum(), sada), obavesteno);
    }

    private RasporedStavka zakljucaj(TerminCmd cmd) {
        if (cmd == null || cmd.stavkaId() == null || cmd.datum() == null) {
            throw new SystemException(NE_POSTOJI, HttpStatus.NOT_FOUND);
        }
        RasporedStavka s = stavke.findByIdForUpdate(cmd.stavkaId())
                .orElseThrow(() -> new SystemException(NE_POSTOJI, HttpStatus.NOT_FOUND));
        if (!Termini.jeTermin(s, cmd.datum())) {
            throw new SystemException(NE_POSTOJI, HttpStatus.NOT_FOUND);
        }
        if (Termini.poceo(LocalDateTime.now(clock), cmd.datum(), s.getPocetak())) {
            throw new SystemException(POCEO, HttpStatus.CONFLICT);
        }
        return s;
    }

    /** Studenti za koje stavka važi (isto pravilo kao nalog, {@link RasporedStavkaRepository#parovi}). */
    private List<Student> primaoci(RasporedStavka s, LocalDate datum) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Object[] r : stavke.parovi(s.getId(), null, datum)) {
            ids.add(((Number) r[1]).longValue());
        }
        return ids.isEmpty() ? List.of() : studenti.findAllById(ids);
    }

    private TerminInfo jedan(RasporedStavka s, LocalDate datum, LocalDateTime sada) {
        return uProzoru(datum, datum, s.getId()).stream().findFirst().map(t -> info(t, sada))
                .orElseThrow(() -> new SystemException(NE_POSTOJI, HttpStatus.NOT_FOUND));
    }

    static TerminInfo info(Termin t, LocalDateTime sada) {
        RasporedStavka s = t.stavka();
        List<NajavaInfo> n = t.najave().stream().map(x -> {
            Student st = x.getStudent();
            return new NajavaInfo(st.getId(), st.getIme(), st.getPrezime(), st.getIndeks(), st.getGodina(), x.getRazlog(),
                    x.getVreme());
        }).toList();
        return new TerminInfo(s.getId(), s.getRaspored().getId(), t.datum(), s.getPocetak(), s.getKraj(),
                RasporedService.predmet(s.getPredmet()), RasporedService.grupa(s.getGrupa()), t.otkazan() != null,
                t.otkazan() == null ? null : t.otkazan().getNapomena(), Termini.poceo(sada, t.datum(), s.getPocetak()),
                n.size(), s.getRaspored().getPragNajava(), n, t.predavanjeId());
    }
}
