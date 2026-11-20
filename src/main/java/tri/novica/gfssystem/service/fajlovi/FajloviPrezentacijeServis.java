package tri.novica.gfssystem.service.fajlovi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tri.novica.gfssystem.dto.fajlovi.FajlPrezentacijeCmd;
import tri.novica.gfssystem.dto.fajlovi.FajlPrezentacijeInfo;
import tri.novica.gfssystem.dto.uzivo.MedijInfo;
import tri.novica.gfssystem.entity.fajlovi.FajlPrezentacije;
import tri.novica.gfssystem.entity.uzivo.Medij;
import tri.novica.gfssystem.entity.uzivo.Prezentacija;
import tri.novica.gfssystem.entity.uzivo.Slajd;
import tri.novica.gfssystem.entity.uzivo.VrstaMedija;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.fajlovi.FajlPrezentacijeRepository;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;
import tri.novica.gfssystem.repository.uzivo.PrezentacijaRepository;
import tri.novica.gfssystem.repository.uzivo.SlajdRepository;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.utility.Serija;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Fajlovi za studente uz prezentaciju, strana nastavnika (spec fajlova, odeljak 5): spisak, upload, izmena imena i slajda,
 * redosled, brisanje i bajtovi. Isti servis koriste REST ({@code FajloviPrezentacijeRest}) i MCP fasada
 * ({@code McpFajlovi}); pravila su zato samo ovde.
 * <p>
 * <b>Odstupanje od slajdova:</b> prezentacija koja se upravo izvodi SME da menja fajlove (Novica kači fajl usred časa, pa
 * ga studenti odmah vide na spisku koda), za razliku od slajdova koje {@code PrezentacijaService} u tom slučaju ne menja
 * (409). Zato se ovde zaključava samo red prezentacije (redosled i najviše {@link #NAJVISE} fajlova ne mogu da se
 * pregaze), a ne i njena aktivna izvođenja.
 * <p>
 * Brisanje fajla briše red i medij ({@link MedijService#obrisi}: fajl sa diska tek posle commit-a). Brisanje cele
 * prezentacije zove {@link #obrisiSve} pre brisanja prezentacije, jer kaskada u bazi ne bi obrisala medije.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FajloviPrezentacijeServis {

    public static final int NAJVISE = 30;
    public static final String NIJE_PRONADJENA = "Prezentacija nije pronađena.";
    public static final String FAJL_NIJE_PRONADJEN = "Fajl nije pronađen.";
    public static final String SLAJD_NIJE_IZ_PREZENTACIJE = "Slajd nije iz ove prezentacije.";
    public static final String PREVISE_FAJLOVA = "Prezentacija već ima " + NAJVISE + " fajlova.";
    public static final String NEISPRAVAN_REDOSLED = "Redosled mora sadržati sve fajlove prezentacije, svaki tačno jednom.";
    public static final String MEDIJ_NIJE_FAJL = "Medij nije fajl za prezentaciju: otpremi ga sa `namena=fajl`.";
    public static final String MEDIJ_ZAUZET = "Taj fajl je već vezan za prezentaciju.";

    /** Fajl za preuzimanje: ime za {@code Content-Disposition} i bajtovi. */
    public record Sadrzaj(String naziv, MedijService.Fajl fajl) {
    }

    private final FajlPrezentacijeRepository fajlovi;
    private final PrezentacijaRepository prezentacije;
    private final SlajdRepository slajdovi;
    private final MedijRepository mediji;
    private final MedijService medijService;
    private final Clock clock;

    // ---------------------------------------------------------------- čitanje

    @Transactional(readOnly = true)
    public List<FajlPrezentacijeInfo> lista(Long prezentacijaId) {
        postoji(prezentacijaId);
        return fajlovi.findAllByPrezentacijaIdOrderByRbAscIdAsc(prezentacijaId).stream().map(FajlMapper::info).toList();
    }

    /**
     * Broj fajlova po slajdu za pregled u MCP-u: slajd -> broj, ključ {@code null} su fajlovi uz celu prezentaciju.
     * {@code HashMap}, jer {@code Map.of} ne dozvoljava {@code null} ključ.
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> brojPoSlajdu(Long prezentacijaId) {
        Map<Long, Integer> broj = new HashMap<>();
        for (FajlPrezentacije f : fajlovi.findAllByPrezentacijaIdOrderByRbAscIdAsc(prezentacijaId)) {
            broj.merge(f.getSlajd() == null ? null : f.getSlajd().getId(), 1, Integer::sum);
        }
        return broj;
    }

    /** Bajtovi za nastavnika (bez ograničenja koda ili naloga). */
    @Transactional(readOnly = true)
    public Sadrzaj sadrzaj(Long fajlId) {
        FajlPrezentacije f = nadji(fajlId);
        return new Sadrzaj(f.getNaziv(), medijService.ucitaj(f.getMedij().getId()));
    }

    // ---------------------------------------------------------------- upis

    /** Upload iz editora: tip po bajtovima i ekstenziji ({@code MedijService.sacuvajFajl}), na kraj spiska. */
    public FajlPrezentacijeInfo dodaj(Long prezentacijaId, MultipartFile fajl, Long slajdId) {
        Prezentacija p = zakljucaj(prezentacijaId);
        Slajd slajd = slajdIz(p, slajdId);
        proveriBroj(prezentacijaId);
        MedijInfo medij = medijService.sacuvajFajl(fajl);
        return upisi(p, slajd, medij.id(), medij.naziv(), null);
    }

    /**
     * Vezivanje već otpremljenog medija (MCP: {@code gfs-mcp-upload} sa {@code namena=fajl}, pa {@code dodaj_fajl}): medij mora
     * biti vrste {@code FAJL_PREZENTACIJE} i još nevezan (409). {@code naziv} {@code null} = ime iz upload-a.
     */
    public FajlPrezentacijeInfo dodajIzMedija(Long prezentacijaId, String medijId, Long slajdId, String naziv, String serija) {
        Serija.proveri(serija);
        Prezentacija p = zakljucaj(prezentacijaId);
        Slajd slajd = slajdIz(p, slajdId);
        Medij medij = medijId == null ? null : mediji.findById(medijId).orElse(null);
        if (medij == null) {
            throw new SystemException("Medij nije pronađen.", HttpStatus.NOT_FOUND);
        }
        if (medij.getVrsta() != VrstaMedija.FAJL_PREZENTACIJE) {
            throw new SystemException(MEDIJ_NIJE_FAJL, HttpStatus.CONFLICT);
        }
        if (fajlovi.existsByMedijId(medijId)) {
            throw new SystemException(MEDIJ_ZAUZET, HttpStatus.CONFLICT);
        }
        proveriBroj(prezentacijaId);
        String ime = naziv == null || naziv.isBlank() ? medij.getNaziv() : MedijService.normalizujNaziv(naziv, medij.getNaziv());
        return upisi(p, slajd, medijId, ime, serija);
    }

    /** Novo ime (ekstenzija originala ostaje) i slajd ({@code null} = uz celu prezentaciju). */
    public FajlPrezentacijeInfo izmeni(Long fajlId, FajlPrezentacijeCmd cmd) {
        if (cmd == null) {
            throw new SystemException("Neispravan zahtev.", HttpStatus.BAD_REQUEST);
        }
        FajlPrezentacije f = nadji(fajlId);
        Prezentacija p = zakljucaj(f.getPrezentacija().getId());
        f.setNaziv(MedijService.normalizujNaziv(cmd.naziv(), f.getNaziv()));
        f.setSlajd(slajdIz(p, cmd.slajdId()));
        f.setIzmenjeno(LocalDateTime.now(clock));
        return FajlMapper.info(fajlovi.saveAndFlush(f));
    }

    /** Novi redosled: {@code ids} su svi fajlovi prezentacije, tačno jednom, inače 400. */
    public List<FajlPrezentacijeInfo> redosled(Long prezentacijaId, List<Long> ids) {
        zakljucaj(prezentacijaId);
        List<FajlPrezentacije> svi = fajlovi.findAllByPrezentacijaIdOrderByRbAscIdAsc(prezentacijaId);
        Set<Long> trazeni = ids == null ? Set.of() : new HashSet<>(ids);
        Set<Long> postojeci = new HashSet<>();
        svi.forEach(f -> postojeci.add(f.getId()));
        if (ids == null || ids.size() != svi.size() || trazeni.size() != ids.size() || !trazeni.equals(postojeci)) {
            throw new SystemException(NEISPRAVAN_REDOSLED, HttpStatus.BAD_REQUEST);
        }
        Map<Long, FajlPrezentacije> poId = new HashMap<>();
        svi.forEach(f -> poId.put(f.getId(), f));
        LocalDateTime sada = LocalDateTime.now(clock);
        int rb = 1;
        for (Long id : ids) {
            FajlPrezentacije f = poId.get(id);
            if (f.getRb() != rb) {
                f.setRb(rb);
                f.setIzmenjeno(sada);
            }
            rb++;
        }
        fajlovi.flush();
        return svi.stream().sorted(Comparator.comparingInt(FajlPrezentacije::getRb)).map(FajlMapper::info).toList();
    }

    /** Briše red i medij (fajl sa diska tek posle commit-a). */
    public void obrisi(Long fajlId) {
        FajlPrezentacije f = nadji(fajlId);
        zakljucaj(f.getPrezentacija().getId());
        String medijId = f.getMedij().getId();
        fajlovi.delete(f);
        fajlovi.flush();   // prvo red (RESTRICT na medij), pa medij
        medijService.obrisi(medijId);
        log.info("Fajl prezentacije obrisan: id={}, prezentacija={}", fajlId, f.getPrezentacija().getId());
    }

    /**
     * Svi fajlovi prezentacije sa medijima, pre brisanja prezentacije ({@code PrezentacijaService}: pod već zaključanom
     * prezentacijom). Bez ovoga kaskada u bazi bi obrisala redove, a medije (i bajtove) ostavila.
     */
    public void obrisiSve(Long prezentacijaId) {
        List<FajlPrezentacije> svi = fajlovi.findAllByPrezentacijaIdOrderByRbAscIdAsc(prezentacijaId);
        if (svi.isEmpty()) return;
        List<String> medijIds = svi.stream().map(f -> f.getMedij().getId()).toList();
        fajlovi.deleteAll(svi);
        fajlovi.flush();
        medijIds.forEach(medijService::obrisi);
        log.info("Fajlovi prezentacije obrisani: prezentacija={}, broj={}", prezentacijaId, medijIds.size());
    }

    // ---------------------------------------------------------------- pomoćno

    private FajlPrezentacijeInfo upisi(Prezentacija p, Slajd slajd, String medijId, String naziv, String serija) {
        LocalDateTime sada = LocalDateTime.now(clock);
        FajlPrezentacije f = new FajlPrezentacije();
        f.setPrezentacija(p);
        f.setSlajd(slajd);
        f.setMedij(mediji.getReferenceById(medijId));
        f.setNaziv(MedijService.normalizujNaziv(naziv, naziv));
        f.setRb(fajlovi.najveciRb(p.getId()) + 1);
        f.setSerija(serija);
        f.setKreirano(sada);
        f.setIzmenjeno(sada);
        f = fajlovi.saveAndFlush(f);
        log.info("Fajl prezentacije dodat: id={}, prezentacija={}, slajd={}, serija={}", f.getId(), p.getId(),
                slajd == null ? null : slajd.getId(), serija);
        return FajlMapper.info(f);
    }

    private void proveriBroj(Long prezentacijaId) {
        if (fajlovi.countByPrezentacijaId(prezentacijaId) >= NAJVISE) {
            throw new SystemException(PREVISE_FAJLOVA, HttpStatus.CONFLICT);
        }
    }

    private Slajd slajdIz(Prezentacija p, Long slajdId) {
        if (slajdId == null) return null;
        Slajd s = slajdovi.findById(slajdId).orElse(null);
        if (s == null || !s.getPrezentacija().getId().equals(p.getId())) {
            throw new SystemException(SLAJD_NIJE_IZ_PREZENTACIJE, HttpStatus.BAD_REQUEST);
        }
        return s;
    }

    private FajlPrezentacije nadji(Long fajlId) {
        return fajlId == null ? thr() : fajlovi.findById(fajlId).orElseGet(FajloviPrezentacijeServis::thr);
    }

    private static FajlPrezentacije thr() {
        throw new SystemException(FAJL_NIJE_PRONADJEN, HttpStatus.NOT_FOUND);
    }

    private void postoji(Long prezentacijaId) {
        if (prezentacijaId == null || !prezentacije.existsById(prezentacijaId)) {
            throw new SystemException(NIJE_PRONADJENA, HttpStatus.NOT_FOUND);
        }
    }

    private Prezentacija zakljucaj(Long prezentacijaId) {
        if (prezentacijaId == null) {
            throw new SystemException(NIJE_PRONADJENA, HttpStatus.NOT_FOUND);
        }
        return prezentacije.findByIdForUpdate(prezentacijaId)
                .orElseThrow(() -> new SystemException(NIJE_PRONADJENA, HttpStatus.NOT_FOUND));
    }
}
