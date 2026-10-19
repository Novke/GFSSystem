package tri.novica.gfssystem.service.banka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.banka.GrupaCmd;
import tri.novica.gfssystem.dto.banka.GrupaDetails;
import tri.novica.gfssystem.entity.banka.*;
import tri.novica.gfssystem.entity.uzivo.Medij;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.banka.GrupaZadatakaRepository;
import tri.novica.gfssystem.repository.banka.VarijantaRepository;
import tri.novica.gfssystem.repository.uzivo.MedijRepository;
import tri.novica.gfssystem.service.uzivo.MedijService;
import tri.novica.gfssystem.utility.Serija;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Kopija grupe sa celim podstablom (MCP {@code kopiraj_grupu}, spec MCP 4.3): ista tema treba i za predavanja i za testove,
 * pa se podstablo kopira u drugu namenu istog predmeta (ili pod drugog roditelja iste namene). Grupe idu redom (pre-order)
 * kroz {@link GrupaZadatakaService#kreiraj(GrupaCmd, IzvorVarijante, String)} (pravila stabla, CLAUDE, serija): isti naziv,
 * opis, težina, uputstvo i šema polja sa istim id-jevima opcija, pa odgovori važe i u kopiji. Varijante osim arhiviranih
 * postaju Klodovi nacrti (NACRT, CLAUDE, serija, nikad odobrene) sa istim sadržajem i prilozima (isti mediji); uspeo render
 * ({@code OK}) se ne ponavlja nego kopira: bajtovi SVG-a u nove {@code RENDER_SVG} medije (SVG pripada jednoj varijanti)
 * sa istim {@code render_hash} (isti predmet, pa ista preambula i isti ulaz), a svaki drugi čeka render ({@code CEKA}).
 * Sve je jedna transakcija: greška na pola (isto ime u cilju, dubina) ne ostavlja ni red ni fajl.
 * <p>
 * <b>Zaključavanje</b> (red iz {@link GrupaZadatakaService}): red predmeta, pa red cilja, pa redovi grupa izvora (pre-order),
 * pa {@code kreiraj} za svaku kopiju. Ključ predmeta drži stablo nepromenjenim, a ključevi grupa izvora njihove varijante
 * i SVG medije ({@code VarijantaService} pre svake izmene varijante zaključa red njene grupe). Tuđ predmet se odbija pre ključa.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(isolation = Isolation.READ_COMMITTED)
public class KopijaGrupeService {

    static final String CILJ_VAN_STABLA = "Cilj kopije mora biti u istom predmetu i nameni.";

    private final GrupaZadatakaService grupaZadatakaService;
    private final GrupaZadatakaRepository grupaRepository;
    private final VarijantaRepository varijantaRepository;
    private final PredmetRepository predmetRepository;
    private final MedijRepository medijRepository;
    private final MedijService medijService;
    private final Clock clock;

    /** Broj stavki koje bi kopija napravila (budžet pravljenja): grupe podstabla i njihove varijante osim arhiviranih. */
    @Transactional(readOnly = true)
    public int brojStavki(Long grupaId) {
        GrupaZadataka g = grupaRepository.findById(grupaId).orElseThrow(KopijaGrupeService::nijePronadjena);
        List<GrupaZadataka> podstablo = podstablo(g);
        long varijanti = varijantaRepository.brojaciPoGrupi(podstablo.stream().map(GrupaZadataka::getId).toList()).stream()
                .filter(red -> red[1] != StatusVarijante.ARHIVIRANA)
                .mapToLong(red -> ((Number) red[3]).longValue())
                .sum();
        return Math.toIntExact(podstablo.size() + varijanti);
    }

    /**
     * Kopija grupe {@code grupaId} sa podstablom pod {@code ciljRoditeljId} ({@code null} = koren) u nameni {@code ciljNamena}
     * istog predmeta; cilj iz drugog predmeta ili druge namene je 400 {@value #CILJ_VAN_STABLA}. Vraća kopiju korena
     * podstabla, sa brojačima posle kopiranja varijanti.
     */
    public GrupaDetails kopiraj(Long grupaId, Namena ciljNamena, Long ciljRoditeljId, String serija) {
        if (serija != null) {
            Serija.proveri(serija);
        }
        if (ciljNamena == null) {
            throw new SystemException(GrupaZadatakaService.NAMENA_OBAVEZNA, HttpStatus.BAD_REQUEST);
        }
        Long predmetId = predmetGrupe(grupaId);
        if (ciljRoditeljId != null && !predmetId.equals(predmetGrupe(ciljRoditeljId))) {
            throw new SystemException(CILJ_VAN_STABLA, HttpStatus.BAD_REQUEST);
        }
        predmetRepository.findByIdForUpdate(predmetId).orElseThrow(() ->
                new SystemException(GrupaZadatakaService.PREDMET_NIJE_PRONADJEN, HttpStatus.NOT_FOUND));
        if (ciljRoditeljId != null && zakljucaj(ciljRoditeljId).getNamena() != ciljNamena) {
            throw new SystemException(CILJ_VAN_STABLA, HttpStatus.BAD_REQUEST);
        }
        GrupaZadataka izvor = zakljucaj(grupaId);
        List<GrupaZadataka> podstablo = podstablo(izvor);   // pre prve kopije: kopija u sopstveno podstablo se ne kopira ponovo
        podstablo.forEach(g -> zakljucaj(g.getId()));

        LocalDateTime sada = LocalDateTime.now(clock);
        Map<Long, Long> kopijaGrupe = new HashMap<>();
        int varijanti = 0;
        for (GrupaZadataka g : podstablo) {
            Long roditeljId = g.equals(izvor) ? ciljRoditeljId : kopijaGrupe.get(g.getRoditelj().getId());
            GrupaDetails kopija = grupaZadatakaService.kreiraj(new GrupaCmd(predmetId, roditeljId, ciljNamena, g.getNaziv(),
                    g.getOpis(), g.getTezina(), g.getUputstvo(), PoljaJson.izKolone(g.getPolja())), IzvorVarijante.CLAUDE, serija);
            kopijaGrupe.put(g.getId(), kopija.id());
            GrupaZadataka cilj = grupaRepository.getReferenceById(kopija.id());
            int rb = 0;
            for (Varijanta v : varijantaRepository.lista(g.getId(), null)) {
                if (v.getStatus() != StatusVarijante.ARHIVIRANA) {
                    kopirajVarijantu(v, cilj, ++rb, serija, sada);
                    varijanti++;
                }
            }
        }
        Long koren = kopijaGrupe.get(izvor.getId());
        log.info("Grupa zadataka kopirana: id={} -> {}, namena={}, roditelj={}, grupa={}, varijanti={}, serija={}", grupaId,
                koren, ciljNamena, ciljRoditeljId, podstablo.size(), varijanti, serija);
        return grupaZadatakaService.detalji(koren);
    }

    /** Klodov nacrt sa sadržajem i prilozima varijante {@code v} na mestu {@code rb} grupe {@code grupa}; uspeo render se kopira. */
    private void kopirajVarijantu(Varijanta v, GrupaZadataka grupa, int rb, String serija, LocalDateTime sada) {
        Varijanta k = new Varijanta();
        k.setGrupa(grupa);
        k.setRb(rb);
        k.setNaziv(v.getNaziv());
        k.setTekst(v.getTekst());
        k.setResenje(v.getResenje());
        k.setOdgovori(v.getOdgovori());
        k.setStatus(StatusVarijante.NACRT);
        k.setIzvor(IzvorVarijante.CLAUDE);
        k.setSerija(serija);
        k.setRenderStatus(RenderStatus.CEKA);
        k.setKreirano(sada);
        k.setIzmenjeno(sada);
        for (VarijantaPrilog p : v.getPrilozi()) {
            VarijantaPrilog kp = new VarijantaPrilog();
            kp.setVarijanta(k);
            kp.setIme(p.getIme());
            kp.setMedij(p.getMedij());
            k.getPrilozi().add(kp);
        }
        Varijanta nova = varijantaRepository.save(k);   // id je deo naziva SVG medija
        if (v.getRenderStatus() == RenderStatus.OK && v.getRenderSvg() != null) {
            nova.setRenderSvg(kopijaSvg(v.getRenderSvg(), "zadatak-" + nova.getId() + ".svg"));
            nova.setRenderResenjeSvg(v.getRenderResenjeSvg() == null ? null
                    : kopijaSvg(v.getRenderResenjeSvg(), "resenje-" + nova.getId() + ".svg"));
            nova.setRenderHash(v.getRenderHash());
            nova.setRenderStatus(RenderStatus.OK);
        }
    }

    /** Nov {@code RENDER_SVG} medij sa bajtovima postojećeg (fajl nove kopije se briše ako se transakcija poništi). */
    private Medij kopijaSvg(Medij svg, String naziv) {
        return medijRepository.getReferenceById(medijService.sacuvajRender(medijService.bajtovi(svg.getId()), naziv).id());
    }

    /** Grupa i svi njeni potomci, pre-order (roditelj pre dece, braća po {@code rb}), iz jednog upita stabla. */
    private List<GrupaZadataka> podstablo(GrupaZadataka koren) {
        Map<Long, List<GrupaZadataka>> deca = new HashMap<>();
        for (GrupaZadataka g : grupaRepository.findByPredmetIdAndNamenaOrderByRoditeljIdAscRbAsc(koren.getPredmet().getId(),
                koren.getNamena())) {
            if (g.getRoditelj() != null) {
                deca.computeIfAbsent(g.getRoditelj().getId(), k -> new ArrayList<>()).add(g);
            }
        }
        Comparator<GrupaZadataka> poRb = Comparator.comparingInt(GrupaZadataka::getRb).thenComparing(GrupaZadataka::getId);
        List<GrupaZadataka> redom = new ArrayList<>();
        Set<Long> vidjene = new HashSet<>();
        Deque<GrupaZadataka> stek = new ArrayDeque<>(List.of(koren));
        while (!stek.isEmpty()) {
            GrupaZadataka g = stek.pop();
            if (vidjene.add(g.getId())) {
                redom.add(g);
                List<GrupaZadataka> d = new ArrayList<>(deca.getOrDefault(g.getId(), List.of()));
                d.sort(poRb.reversed());   // stek: prvo dete na vrh
                d.forEach(stek::push);
            }
        }
        return redom;
    }

    private Long predmetGrupe(Long grupaId) {
        return grupaRepository.findPredmetId(grupaId).orElseThrow(KopijaGrupeService::nijePronadjena);
    }

    private GrupaZadataka zakljucaj(Long id) {
        return grupaRepository.findByIdForUpdate(id).orElseThrow(KopijaGrupeService::nijePronadjena);
    }

    private static SystemException nijePronadjena() {
        return new SystemException(GrupaZadatakaService.NIJE_PRONADJENA, HttpStatus.NOT_FOUND);
    }
}
