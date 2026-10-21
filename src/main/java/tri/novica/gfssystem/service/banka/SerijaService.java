package tri.novica.gfssystem.service.banka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.banka.PonistavanjeInfo;
import tri.novica.gfssystem.dto.banka.SerijaInfo;
import tri.novica.gfssystem.dto.banka.StavkaPonistavanja;
import tri.novica.gfssystem.entity.banka.IzvorVarijante;
import tri.novica.gfssystem.entity.banka.StatusVarijante;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;
import tri.novica.gfssystem.repository.uzivo.PrezentacijaRepository;
import tri.novica.gfssystem.repository.uzivo.SlajdRepository;
import tri.novica.gfssystem.utility.Serija;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Klodove serije (spec MCP 4.3, 6.1): pregled sa brojačima ({@link #serije} za UI, {@link #poslednje} za MCP
 * {@code kontekst}) i poništavanje serije u banci ({@link #ponisti}: UI "Odbaci celu seriju", i deo MCP
 * {@code ponisti_seriju}, čiji deo u prezentacijama radi {@code PrezentacijaService.ponistiSeriju}).
 * <p>
 * <b>Poništavanje</b> briše ono što se još sme obrisati: prvo varijante serije kroz
 * {@link VarijantaService#obrisiSopstveniNacrt} (samo Klodov neodobren nacrt; 409 je preskočeno
 * {@value #RAZLOG_VARIJANTA}), pa CLAUDE grupe serije od najdubljih kroz {@link GrupaZadatakaService#obrisiPraznu} (409 je
 * preskočeno {@value #RAZLOG_GRUPA}), pa grupa koja tek brisanjem svoje dece ostane prazna i sama ide. RUCNO grupe se ne
 * pominju, kao ni Novicine varijante bez serije (one samo čine grupu nepraznom). Stavka obrisana u međuvremenu (404) se
 * preskače bez pominjanja. Bez potvrde se isti ishod računa iz trenutnog stanja, bez ijednog ključa i upisa.
 * <p>
 * Klasa nije {@code @Transactional}: svaka stavka se briše u transakciji svoje metode, pod njenim ključevima (red
 * zaključavanja tih servisa ostaje netaknut, a 409 jedne stavke ne poništava ostale). {@link #ponisti} je
 * {@code NOT_SUPPORTED}, pa to važi i kad je pozivalac u transakciji. Pre brisanja se čitaju samo vrednosti (id-jevi,
 * statusi, nazivi), nikad entiteti: {@code obrisiSopstveniNacrt} ne prazni persistence context, a entitet učitan pre
 * njegovog ključa (uz open-in-view isti EntityManager traje ceo zahtev) imao bi stanje od pre ključa.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SerijaService {

    static final String RAZLOG_VARIJANTA = "Odobrena ili nije Klodov nacrt.";
    static final String RAZLOG_GRUPA = "Grupa nije prazna.";

    /** Kolona roditelja u redu {@code GrupaZadatakaRepository.zaPonistavanje}; posle nje preci do korena. */
    private static final int PRVI_PREDAK = 3;

    private final VarijantaRepository varijantaRepository;
    private final GrupaZadatakaRepository grupaRepository;
    private final PrezentacijaRepository prezentacijaRepository;
    private final SlajdRepository slajdRepository;
    private final PredmetRepository predmetRepository;
    private final VarijantaService varijantaService;
    private final GrupaZadatakaService grupaZadatakaService;

    // ---------------------------------------------------------------- pregled

    /** Serije predmeta sa brojačima (grupe, varijante, prezentacije i slajdovi u prezentacijama predmeta), najnovije prve. */
    @Transactional(readOnly = true)
    public List<SerijaInfo> serije(Long predmetId) {
        if (predmetId == null) {
            throw new SystemException(GrupaZadatakaService.PREDMET_OBAVEZAN, HttpStatus.BAD_REQUEST);
        }
        if (!predmetRepository.existsById(predmetId)) {
            throw new SystemException(GrupaZadatakaService.PREDMET_NIJE_PRONADJEN, HttpStatus.NOT_FOUND);
        }
        return brojaci(predmetId);
    }

    /** Najviše {@code n} najnovijih serija preko svih predmeta, sa brojačima kao {@link #serije}. */
    @Transactional(readOnly = true)
    public List<SerijaInfo> poslednje(int n) {
        List<SerijaInfo> sve = brojaci(null);
        return List.copyOf(sve.subList(0, Math.min(Math.max(n, 0), sve.size())));
    }

    /**
     * Četiri upita brojača (po jedan po tabeli, grupisano po seriji), spojena po seriji; najnovije prve (pa po imenu).
     * {@code predmetId == null} = svi predmeti.
     */
    private List<SerijaInfo> brojaci(Long predmetId) {
        Map<String, Brojac> po = new HashMap<>();
        for (Object[] r : grupaRepository.brojaciPoSeriji(predmetId)) {
            Brojac b = po.computeIfAbsent((String) r[0], k -> new Brojac());
            b.grupa = broj(r[1]);
            b.vreme((LocalDateTime) r[2]);
        }
        for (Object[] r : varijantaRepository.brojaciPoSeriji(predmetId)) {
            Brojac b = po.computeIfAbsent((String) r[0], k -> new Brojac());
            b.nacrta = broj(r[1]);
            b.odobrenih = broj(r[2]);
            b.vreme((LocalDateTime) r[3]);
        }
        for (Object[] r : prezentacijaRepository.brojaciPoSeriji(predmetId)) {
            Brojac b = po.computeIfAbsent((String) r[0], k -> new Brojac());
            b.prezentacija = broj(r[1]);
            b.vreme((LocalDateTime) r[2]);
        }
        for (Object[] r : slajdRepository.brojaciPoSeriji(predmetId)) {
            Brojac b = po.computeIfAbsent((String) r[0], k -> new Brojac());
            b.slajdova = broj(r[1]);
            b.vreme((LocalDateTime) r[2]);
        }
        return po.entrySet().stream()
                .map(e -> e.getValue().info(e.getKey()))
                .sorted(Comparator.comparing(SerijaInfo::poslednja, Comparator.reverseOrder())
                        .thenComparing(SerijaInfo::serija))
                .toList();
    }

    private static long broj(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    /** Brojači jedne serije dok se spajaju redovi četiri upita. */
    private static final class Brojac {
        long nacrta;
        long odobrenih;
        long grupa;
        long prezentacija;
        long slajdova;
        LocalDateTime poslednja;

        void vreme(LocalDateTime t) {
            if (t != null && (poslednja == null || t.isAfter(poslednja))) {
                poslednja = t;
            }
        }

        SerijaInfo info(String serija) {
            return new SerijaInfo(serija, nacrta, odobrenih, grupa, prezentacija, slajdova, poslednja);
        }
    }

    // ---------------------------------------------------------------- poništavanje

    /**
     * Poništava seriju u banci (vidi opis klase); {@code potvrdi=false} ništa ne menja, a {@code obrisano} je spisak onoga
     * što bi se obrisalo. Stavke idu redom: varijante po id-ju, pa grupe od najdubljih (u istoj dubini po id-ju). Serija
     * van {@link Serija#OBRAZAC} je 400.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PonistavanjeInfo ponisti(String serija, boolean potvrdi) {
        Serija.proveri(serija);
        List<StavkaPonistavanja> obrisano = new ArrayList<>();
        List<StavkaPonistavanja> preskoceno = new ArrayList<>();

        // obrisane varijante serije po grupi (bez potvrde: one koje bi se obrisale), za probu grupa
        Map<Long, Integer> obrisanihPoGrupi = new HashMap<>();
        for (Object[] r : varijantaRepository.zaPonistavanje(serija)) {
            VarijantaSerije v = new VarijantaSerije((Long) r[0], (Integer) r[1], (String) r[2], (Long) r[3], (String) r[4],
                    (StatusVarijante) r[5], (IzvorVarijante) r[6], (Boolean) r[7]);
            // Boolean.valueOf: uz boolean u drugoj grani izraz bi bio boolean, pa bi null (404) pukao pri raspakivanju
            Boolean obrisana = potvrdi ? obrisi(() -> varijantaService.obrisiSopstveniNacrt(v.id()))
                    : Boolean.valueOf(v.sopstveniNacrt());
            if (obrisana == null) {
                continue;   // obrisana u međuvremenu
            }
            if (obrisana) {
                obrisano.add(v.stavka(null));
                obrisanihPoGrupi.merge(v.grupaId(), 1, Integer::sum);
            } else {
                preskoceno.add(v.stavka(RAZLOG_VARIJANTA));
            }
        }

        List<GrupaSerije> grupe = grupaRepository.zaPonistavanje(serija).stream()
                .map(SerijaService::grupaSerije)
                .filter(g -> g.izvor() == IzvorVarijante.CLAUDE)
                .sorted(Comparator.comparingInt(GrupaSerije::dubina).reversed().thenComparing(GrupaSerije::id))
                .toList();
        Proba proba = potvrdi || grupe.isEmpty() ? null : proba(grupe);
        Set<Long> obrisaneGrupe = new HashSet<>();
        for (GrupaSerije g : grupe) {
            Boolean obrisana = potvrdi ? obrisi(() -> grupaZadatakaService.obrisiPraznu(g.id()))
                    : Boolean.valueOf(proba.prazna(g.id(), obrisanihPoGrupi, obrisaneGrupe));
            if (obrisana == null) {
                continue;   // obrisana u međuvremenu
            }
            if (obrisana) {
                obrisaneGrupe.add(g.id());
                obrisano.add(new StavkaPonistavanja(StavkaPonistavanja.GRUPA, g.id(), g.naziv(), null));
            } else {
                preskoceno.add(new StavkaPonistavanja(StavkaPonistavanja.GRUPA, g.id(), g.naziv(), RAZLOG_GRUPA));
            }
        }
        if (potvrdi) {
            log.info("Serija poništena u banci: serija={}, obrisano={}, preskočeno={}", serija, obrisano.size(),
                    preskoceno.size());
        }
        return new PonistavanjeInfo(potvrdi, obrisano, preskoceno);
    }

    /** Brisanje jedne stavke u njenoj transakciji: {@code true} obrisana, {@code false} 409 (preskočena), {@code null} 404. */
    private static Boolean obrisi(Runnable brisanje) {
        try {
            brisanje.run();
            return true;
        } catch (SystemException e) {
            if (Objects.equals(e.getCode(), HttpStatus.NOT_FOUND.value())) {
                return null;
            }
            if (Objects.equals(e.getCode(), HttpStatus.CONFLICT.value())) {
                return false;
            }
            throw e;
        }
    }

    private static GrupaSerije grupaSerije(Object[] r) {
        int dubina = 1;
        for (int i = PRVI_PREDAK; i < r.length && r[i] != null; i++) {
            dubina++;
        }
        return new GrupaSerije((Long) r[0], (String) r[1], (IzvorVarijante) r[2], dubina);
    }

    /** Za probu bez potvrde: ukupan broj varijanti i deca svake grupe serije, po jedan upit. */
    private Proba proba(List<GrupaSerije> grupe) {
        List<Long> ids = grupe.stream().map(GrupaSerije::id).toList();
        Map<Long, Long> varijanti = new HashMap<>();
        for (Object[] r : varijantaRepository.brojaciPoGrupi(ids)) {
            varijanti.merge((Long) r[0], broj(r[3]), Long::sum);
        }
        Map<Long, List<Long>> deca = new HashMap<>();
        for (Object[] r : grupaRepository.decaGrupa(ids)) {
            deca.computeIfAbsent((Long) r[0], k -> new ArrayList<>()).add((Long) r[1]);
        }
        return new Proba(varijanti, deca);
    }

    /** Stanje grupa serije pre poništavanja: broj varijanti po grupi i deca po grupi (sve, i one van serije). */
    private record Proba(Map<Long, Long> varijanti, Map<Long, List<Long>> deca) {

        /**
         * Da li bi grupa bila prazna kad dođe na red: sve njene varijante su među onima koje bi se obrisale, a sva deca
         * među grupama koje bi se obrisale (deca su dublja, pa su već na spisku).
         */
        boolean prazna(Long grupaId, Map<Long, Integer> obrisanihPoGrupi, Set<Long> obrisaneGrupe) {
            long ostaje = varijanti.getOrDefault(grupaId, 0L) - obrisanihPoGrupi.getOrDefault(grupaId, 0);
            return ostaje <= 0 && obrisaneGrupe.containsAll(deca.getOrDefault(grupaId, List.of()));
        }
    }

    private record VarijantaSerije(Long id, Integer rb, String naziv, Long grupaId, String grupaNaziv,
                                   StatusVarijante status, IzvorVarijante izvor, Boolean ikadOdobrena) {

        /** Isto pravilo kao {@code VarijantaService.obrisiSopstveniNacrt}: NACRT, CLAUDE, nikad odobrena. */
        boolean sopstveniNacrt() {
            return status == StatusVarijante.NACRT && izvor == IzvorVarijante.CLAUDE && !Boolean.TRUE.equals(ikadOdobrena);
        }

        /** Naziv stavke: "{@code <grupa>, varijanta <rb>: <naziv>}" (bez naziva varijante bez dvotačke). */
        StavkaPonistavanja stavka(String razlog) {
            String opis = grupaNaziv + ", varijanta " + rb + (naziv == null || naziv.isBlank() ? "" : ": " + naziv);
            return new StavkaPonistavanja(StavkaPonistavanja.VARIJANTA, id, opis, razlog);
        }
    }

    private record GrupaSerije(Long id, String naziv, IzvorVarijante izvor, int dubina) {
    }
}
