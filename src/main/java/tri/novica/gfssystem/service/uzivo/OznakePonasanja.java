package tri.novica.gfssystem.service.uzivo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.uzivo.OznakaPonasanjaInfo;
import tri.novica.gfssystem.dto.uzivo.PredmetKratko;
import tri.novica.gfssystem.entity.Predmet;
import tri.novica.gfssystem.entity.uzivo.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.uzivo.OznakaPonasanjaRepository;
import tri.novica.gfssystem.repository.uzivo.ZabranaGostaRepository;
import tri.novica.gfssystem.utility.TokenGenerator;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Moderacija uživo, deo koji se pamti (spec moderacije, V25): oznake ponašanja na kartonu studenta i zabrane ulaska
 * izbačenih telefona. Oznaku dobija samo prijavljen student (gost nema karton); zabranu dobija token svakog izbačenog
 * učesnika, pa ni student koji se odjavi ne ulazi kao gost sa istog telefona. Pozivaoci drže zaključan red izvođenja.
 *
 * <p>Šta znači brisanje oznake na kartonu: samo zapis nestaje; {@link VrstaOznake#IZBACEN_PREDAVANJE} je i zabrana
 * ulaska prijavljenog studenta u izvođenja tog predavanja, pa brisanje skida zabranu za ta izvođenja (izvođenje iz kog je
 * izbačen i dalje ima njegov izbačen red). Brisanje {@link VrstaOznake#SAKRIVEN_ODGOVOR} ne vraća tekst na tablu.
 */
@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class OznakePonasanja {

    public static final String OZNAKA_NIJE_PRONADJENA = "Oznaka ne postoji.";
    public static final String STUDENT_NIJE_PRONADJEN = "Student ne postoji.";

    private final OznakaPonasanjaRepository oznakaRepository;
    private final ZabranaGostaRepository zabranaRepository;
    private final StudentRepository studentRepository;
    private final PredmetRepository predmetRepository;
    private final Clock clock;

    /** Oznaka za studenta iza učesnika; gost: ništa. */
    public void zabelezi(Izvodjenje iz, Ucesnik u, VrstaOznake vrsta, String tekst, Long rundaId) {
        if (u.getStudent() == null) return;
        OznakaPonasanja o = new OznakaPonasanja();
        o.setStudent(u.getStudent());
        o.setVrsta(vrsta);
        o.setTekst(tekst == null ? null : tekst.length() > 200 ? tekst.substring(0, 200) : tekst);
        Prezentacija p = iz.getPrezentacija();
        o.setPredmetId(p.getPredmet() == null ? null : p.getPredmet().getId());
        o.setPredavanjeId(iz.getPredavanje() == null ? null : iz.getPredavanje().getId());
        o.setIzvodjenjeId(iz.getId());
        o.setRundaId(rundaId);
        o.setPrezentacija(p.getNaziv());
        o.setKreirano(LocalDateTime.now(clock));
        oznakaRepository.save(o);
    }

    /** Zabrana telefona učesnika (po hešu tokena) za izvođenje, a sa {@code predavanje} i za njegovo predavanje. */
    public void zabraniToken(Izvodjenje iz, Ucesnik u, boolean predavanje) {
        ZabranaGosta z = new ZabranaGosta();
        z.setTokenHash(u.getTokenHash());
        z.setIzvodjenjeId(iz.getId());
        z.setPredavanjeId(predavanje && iz.getPredavanje() != null ? iz.getPredavanje().getId() : null);
        z.setKreirano(LocalDateTime.now(clock));
        zabranaRepository.save(z);
    }

    /**
     * Da li je učesnik već izbačen do kraja predavanja izvođenja: student samo po oznaci (posle brisanja oznake ponovo
     * izbacivanje mora da napiše novu), gost po zabrani tokena.
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public boolean izbacenSaPredavanja(Izvodjenje iz, Ucesnik u) {
        if (iz.getPredavanje() == null) return false;
        Long pred = iz.getPredavanje().getId();
        if (u.getStudent() != null) return studentIzbacenSaPredavanja(u.getStudent().getId(), pred);
        return zabranaRepository.findAllByTokenHash(u.getTokenHash()).stream()
                .anyMatch(z -> pred.equals(z.getPredavanjeId()));
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public boolean studentIzbacenSaPredavanja(Long studentId, Long predavanjeId) {
        return predavanjeId != null && oznakaRepository.existsByStudentIdAndPredavanjeIdAndVrsta(studentId, predavanjeId,
                VrstaOznake.IZBACEN_PREDAVANJE);
    }

    /** Da li telefon sa ovim tokenom (iz kolačića) ne sme u izvođenje. */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public boolean tokenZabranjen(String token, Izvodjenje iz) {
        if (token == null || !TokenGenerator.FORMAT.matcher(token).matches()) return false;
        return !zabranaRepository.vazece(TokenHash.od(token), iz.getId(),
                iz.getPredavanje() == null ? null : iz.getPredavanje().getId()).isEmpty();
    }

    /**
     * Nova prijava telefona sa zabranjenim starim tokenom: iste zabrane važe i za nov token (kolačić se prepisuje). Kopija
     * zadržava {@code kreirano} originala, pa je čišćenje posle 2 dana briše kao i original.
     */
    public void prenesiZabrane(String stariToken, String noviHash) {
        if (stariToken == null || !TokenGenerator.FORMAT.matcher(stariToken).matches()) return;
        String stari = TokenHash.od(stariToken);
        if (stari.equals(noviHash)) return;
        for (ZabranaGosta z : zabranaRepository.findAllByTokenHash(stari)) {
            ZabranaGosta n = new ZabranaGosta();
            n.setTokenHash(noviHash);
            n.setIzvodjenjeId(z.getIzvodjenjeId());
            n.setPredavanjeId(z.getPredavanjeId());
            n.setKreirano(z.getKreirano());
            zabranaRepository.save(n);
        }
    }

    /** Karton: oznake studenta, najnovije prve; nepoznat student 404. */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public List<OznakaPonasanjaInfo> zaStudenta(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw new SystemException(STUDENT_NIJE_PRONADJEN, HttpStatus.NOT_FOUND);
        }
        List<OznakaPonasanja> oznake = oznakaRepository.findAllByStudentIdOrderByKreiranoDescIdDesc(studentId);
        Set<Long> predmetIds = oznake.stream().map(OznakaPonasanja::getPredmetId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Predmet> predmeti = predmetIds.isEmpty() ? Map.of()
                : predmetRepository.findAllById(predmetIds).stream().collect(Collectors.toMap(Predmet::getId, Function.identity()));
        return oznake.stream().map(o -> {
            Predmet p = o.getPredmetId() == null ? null : predmeti.get(o.getPredmetId());
            return new OznakaPonasanjaInfo(o.getId(), o.getVrsta(), o.getTekst(), o.getKreirano(),
                    p == null ? null : new PredmetKratko(p.getId(), p.getNaziv()), o.getPredavanjeId(),
                    o.getIzvodjenjeId(), o.getPrezentacija());
        }).toList();
    }

    /** Briše oznaku (vidi opis klase za značenje); nepoznata 404. */
    public void obrisi(Long id) {
        OznakaPonasanja o = oznakaRepository.findById(id)
                .orElseThrow(() -> new SystemException(OZNAKA_NIJE_PRONADJENA, HttpStatus.NOT_FOUND));
        oznakaRepository.delete(o);
    }
}
