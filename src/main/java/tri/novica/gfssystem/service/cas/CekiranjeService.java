package tri.novica.gfssystem.service.cas;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.cas.*;
import tri.novica.gfssystem.entity.Aktivnost;
import tri.novica.gfssystem.entity.Predavanje;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.entity.TipAktivnosti;
import tri.novica.gfssystem.entity.cas.Cekiranje;
import tri.novica.gfssystem.entity.cas.CekiranjeNaCekanju;
import tri.novica.gfssystem.entity.uzivo.Izvodjenje;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredavanjeRepository;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.cas.CekiranjeNaCekanjuRepository;
import tri.novica.gfssystem.repository.cas.CekiranjeRepository;
import tri.novica.gfssystem.repository.uzivo.IzvodjenjeRepository;
import tri.novica.gfssystem.repository.uzivo.UcesnikRepository;
import tri.novica.gfssystem.service.uzivo.TokenHash;
import tri.novica.gfssystem.utility.TokenGenerator;
import tri.novica.gfssystem.utility.Utility;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Čekiranje QR-om na času (spec studentskog dela, odeljak 4): skeniranje tokena koji se menja na 10 s ({@link CekiranjeToken}),
 * jednokratni ključ za studenta koji skenira pre prijave, stanje časa za telefon i pogled za nastavnika (token i spisak).
 * <p>
 * Upis čekiranja prvo zaključa red predavanja (isto rade ručne izmene aktivnosti u {@code PredavanjeService}), nikad red
 * izvođenja; uz čekiranje ide PRISUSTVO samo kad student nema aktivnost na predavanju (samo podiži, nikad ne spuštaj).
 * {@code READ_COMMITTED}: posle čekanja na zaključavanje upiti vide ono što je drugi zahtev upravo upisao, pa dupli dodir
 * daje jedan red i jedno prisustvo.
 */
@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CekiranjeService {

    /** Koliko je student "na času" posle čekiranja, i koliko živi tajna QR-a. */
    public static final Duration NA_CASU = Duration.ofHours(5);
    public static final Duration KLJUC_TRAJE = Duration.ofMinutes(15);
    public static final Duration KASNIO_POSLE = Duration.ofMinutes(15);

    public static final String QR_ISTEKAO = "QR je istekao. Skeniraj ponovo sa table.";
    public static final String CAS_ZAVRSEN = "Čekiranje za ovaj čas je završeno.";
    public static final String TUDJA_GRUPA = "Nisi u grupi ovog predavanja. Javi se nastavniku.";
    public static final String KLJUC_ISTEKAO = "Čekiranje je isteklo. Skeniraj QR ponovo.";

    private final PredavanjeRepository predavanjeRepository;
    private final StudentRepository studentRepository;
    private final CekiranjeRepository cekiranjeRepository;
    private final CekiranjeNaCekanjuRepository naCekanjuRepository;
    private final IzvodjenjeRepository izvodjenjeRepository;
    private final UcesnikRepository ucesnikRepository;
    private final TokenGenerator tokenGenerator;
    private final Clock clock;

    /**
     * Skeniran QR. Neispravan, istekao ili tuđ token i predavanje bez važeće tajne: 410 {@value #QR_ISTEKAO}; završeno
     * predavanje: 410 {@value #CAS_ZAVRSEN}. Prijavljen student se čekira (403 {@value #TUDJA_GRUPA} van grupe) i dobija
     * stanje časa tog predavanja; neprijavljen dobija jednokratni ključ (15 min, vezan za predavanje, trenutak skeniranja i
     * telefon). Jedan važeći ključ po telefonu i predavanju: novo skeniranje poništava prethodni, pa se ključevi ne prave
     * na gomilu za druge. {@code uredjajHash}: SHA-256 kolačića {@code gfs_uredjaj} (REST ga uvek daje).
     */
    public SkeniranjeOdgovor skeniraj(String token, Optional<Long> studentId, String uredjajHash) {
        OptionalLong id = CekiranjeToken.predavanjeIz(token);
        if (id.isEmpty()) throw istekao();
        Optional<Predavanje> nadjeno = studentId.isPresent()
                ? predavanjeRepository.findByIdForUpdate(id.getAsLong())
                : predavanjeRepository.findById(id.getAsLong());
        Predavanje p = nadjeno.orElseThrow(CekiranjeService::istekao);
        if (zavrseno(p)) throw new SystemException(CAS_ZAVRSEN, HttpStatus.GONE);
        LocalDateTime sada = sada();
        if (!vazecaTajna(p, sada) || !CekiranjeToken.vazi(p.getCekiranjeTajna(), p.getId(), token, clock.millis())) {
            throw istekao();
        }
        if (studentId.isEmpty()) {
            if (uredjajHash == null) throw new IllegalArgumentException("Ključ čekanja traži oznaku uređaja.");
            naCekanjuRepository.obrisiNeiskorisceneZaUredjaj(p.getId(), uredjajHash);
            String kljuc = tokenGenerator.novi();
            CekiranjeNaCekanju k = new CekiranjeNaCekanju();
            k.setKljucHash(TokenHash.od(kljuc));
            k.setPredavanje(p);
            k.setSkenirano(sada);
            k.setIstice(sada.plus(KLJUC_TRAJE));
            k.setUredjajHash(uredjajHash);
            naCekanjuRepository.save(k);
            return new SkeniranjeOdgovor(kljuc, null);
        }
        Cekiranje c = upisi(p, student(studentId.get()), sada, uredjajHash);
        return new SkeniranjeOdgovor(null, stanje(c, studentId.get()));
    }

    /**
     * Čekiranje iz ključa posle prijave: ključ se troši jednom i samo sa telefona koji je skenirao (isti {@code gfs_uredjaj});
     * nepoznat, istekao, potrošen ili sa drugog telefona: 410 {@value #KLJUC_ISTEKAO}, a ključ ostaje. Vreme i oznaka
     * telefona čekiranja su iz skeniranja. Odbijeno čekiranje (tuđa grupa, završen čas) ne troši ključ (rollback).
     */
    public CasStanje potvrdi(String kljuc, long studentId, String uredjajHash) {
        if (kljuc == null || !TokenGenerator.FORMAT.matcher(kljuc).matches()) throw kljucIstekao();
        LocalDateTime sada = sada();
        CekiranjeNaCekanju k = naCekanjuRepository.findByKljucHashForUpdate(TokenHash.od(kljuc))
                .filter(x -> x.getIskorisceno() == null && x.getIstice().isAfter(sada) && x.getUredjajHash().equals(uredjajHash))
                .orElseThrow(CekiranjeService::kljucIstekao);
        k.setIskorisceno(sada);
        Predavanje p = predavanjeRepository.findByIdForUpdate(k.getPredavanje().getId())
                .orElseThrow(CekiranjeService::kljucIstekao);
        if (zavrseno(p)) throw new SystemException(CAS_ZAVRSEN, HttpStatus.GONE);
        Cekiranje c = upisi(p, student(studentId), k.getSkenirano(), k.getUredjajHash());
        return stanje(c, studentId);
    }

    /**
     * Stanje časa: poslednje čekiranje studenta u poslednjih {@link #NA_CASU} (završeno predavanje ili ne) i, dok
     * predavanje nije završeno, najnovije aktivno izvođenje tog predavanja iz koga student nije izbačen. Kad student nije
     * na nezavršenom predavanju, {@code uToku} je najnovije današnje predavanje njegove grupe koje je u toku, a na kome
     * još nije čekiran ({@link PredavanjeRepository#uTokuZaStudenta}).
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public CasStanje stanje(long studentId) {
        CasStanje s = cekiranjeRepository.findPoslednjeNaCasu(studentId, sada().minus(NA_CASU))
                .map(c -> stanje(c, studentId))
                .orElse(CasStanje.NIJE_NA_CASU);
        if (s.predavanje() != null && !s.predavanje().zavrseno()) return s;
        return predavanjeRepository.uTokuZaStudenta(studentId, sada().toLocalDate(), PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(p -> s.saUToku(new CasUToku(p.getPredmet().getNaziv(), p.getTema())))
                .orElse(s);
    }

    /** Najnovije aktivno izvođenje predavanja iz koga student nije izbačen. */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Optional<CasIzvodjenje> aktivnoIzvodjenje(long predavanjeId, long studentId) {
        for (Izvodjenje iz : izvodjenjeRepository.findAktivnaZaPredavanje(predavanjeId)) {
            if (iz.getAktivanKod() != null
                    && !ucesnikRepository.existsByIzvodjenjeIdAndStudentIdAndIzbacenTrue(iz.getId(), studentId)) {
                return Optional.of(new CasIzvodjenje(iz.getAktivanKod(), iz.getPrezentacija().getNaziv()));
            }
        }
        return Optional.empty();
    }

    /**
     * Nastavnički pogled: dok je predavanje živo, token tekućeg prozora (tajna nastaje ovde, prvi put i kad je starija od
     * {@link #NA_CASU}); spisak čekiranih sa oznakom "kasnio" i "isti telefon kao". 404 za nepostojeće predavanje.
     */
    public CekiranjeInfo zaNastavnika(long predavanjeId) {
        Predavanje p = predavanjeRepository.findById(predavanjeId)
                .orElseThrow(() -> new SystemException("Predavanje ne postoji! ID = " + predavanjeId, HttpStatus.NOT_FOUND));
        LocalDateTime sada = sada();
        long sadaMs = clock.millis();
        String token = null;
        Long vaziDoMs = null;
        if (!zavrseno(p)) {
            String tajna = p.getCekiranjeTajna();
            if (!vazecaTajna(p, sada)) {
                // uslovni upis (compare-and-set): dva nastavnička prozora u istoj sekundi ne prave dve tajne
                String nova = tokenGenerator.novi();
                tajna = predavanjeRepository.postaviTajnu(predavanjeId, nova, sada, sada.minus(NA_CASU)) == 1
                        ? nova : predavanjeRepository.tajna(predavanjeId).orElse(null);
            }
            if (tajna != null) {
                token = CekiranjeToken.token(tajna, predavanjeId, sadaMs);
                vaziDoMs = CekiranjeToken.vaziDoMs(sadaMs);
            }
        }
        boolean datumNijeDanas = p.getDatum() != null && !p.getDatum().equals(LocalDate.now(clock));
        return new CekiranjeInfo(token, vaziDoMs, sadaMs, datumNijeDanas, cekirani(predavanjeId));
    }

    private List<CekiranStudent> cekirani(long predavanjeId) {
        List<Cekiranje> sva = cekiranjeRepository.findAllByPredavanjeIdOrderByVremeAsc(predavanjeId);
        if (sva.isEmpty()) return List.of();
        LocalDateTime granica = sva.getFirst().getVreme().plus(KASNIO_POSLE);
        Map<String, Long> prviNaUredjaju = new HashMap<>();
        List<CekiranStudent> rez = new ArrayList<>(sva.size());
        for (Cekiranje c : sva) {
            Student s = c.getStudent();
            Long isti = null;
            if (c.getUredjajHash() != null) {
                Long prvi = prviNaUredjaju.putIfAbsent(c.getUredjajHash(), s.getId());
                if (prvi != null && !prvi.equals(s.getId())) isti = prvi;
            }
            rez.add(new CekiranStudent(s.getId(), s.getIme(), s.getPrezime(), s.getIndeks(), c.getVreme(),
                    c.getVreme().isAfter(granica), isti));
        }
        return rez;
    }

    /**
     * Upis čekiranja; pozivalac drži zaključan red predavanja. Već čekiran student: ništa se ne menja (ni vreme prvog
     * čekiranja). Nov: red u {@code cekiranja} i PRISUSTVO kad student nema aktivnost na predavanju.
     */
    private Cekiranje upisi(Predavanje p, Student s, LocalDateTime vreme, String uredjajHash) {
        if (p.getGrupa() == null || !Utility.smeNaNastavuGrupe(s, p.getGrupa())) {
            throw new SystemException(TUDJA_GRUPA, HttpStatus.FORBIDDEN);
        }
        Optional<Cekiranje> postojece = cekiranjeRepository.findByPredavanjeIdAndStudentId(p.getId(), s.getId());
        if (postojece.isPresent()) return postojece.get();
        Cekiranje c = new Cekiranje();
        c.setPredavanje(p);
        c.setStudent(s);
        c.setVreme(vreme);
        c.setUredjajHash(uredjajHash);
        c = cekiranjeRepository.save(c);
        boolean imaAktivnost = p.getAktivnosti().stream().anyMatch(a -> a.getStudent().getId().equals(s.getId()));
        if (!imaAktivnost) {
            p.getAktivnosti().add(new Aktivnost(p, s, TipAktivnosti.PRISUSTVO));
            p.uvecajPosecenost();
            predavanjeRepository.save(p);
        }
        return c;
    }

    private CasStanje stanje(Cekiranje c, long studentId) {
        Predavanje p = c.getPredavanje();
        boolean zavrseno = zavrseno(p);
        CasPredavanje cp = new CasPredavanje(p.getId(), p.getPredmet().getNaziv(), p.getDatum(), p.getRb(), p.getTema(),
                zavrseno);
        CasIzvodjenje iz = zavrseno ? null : aktivnoIzvodjenje(p.getId(), studentId).orElse(null);
        return new CasStanje(cp, c.getVreme(), iz);
    }

    /** Sada, na mikrosekunde (kolone su {@code datetime(6)}): vreme u odgovoru je isto kao posle čitanja iz baze. */
    private LocalDateTime sada() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private Student student(long studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new SystemException("Potrebna je prijava.", HttpStatus.UNAUTHORIZED));
    }

    private static boolean zavrseno(Predavanje p) {
        return Boolean.TRUE.equals(p.getZavrseno());
    }

    private static boolean vazecaTajna(Predavanje p, LocalDateTime sada) {
        return p.getCekiranjeTajna() != null && p.getCekiranjeTajnaOd() != null
                && !p.getCekiranjeTajnaOd().isBefore(sada.minus(NA_CASU));
    }

    private static SystemException istekao() {
        return new SystemException(QR_ISTEKAO, HttpStatus.GONE);
    }

    private static SystemException kljucIstekao() {
        return new SystemException(KLJUC_ISTEKAO, HttpStatus.GONE);
    }
}
