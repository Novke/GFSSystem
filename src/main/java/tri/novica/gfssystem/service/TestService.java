package tri.novica.gfssystem.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.dto.test.*;
import tri.novica.gfssystem.dto.test.tip.CreateTipTestaCmd;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.dto.test.tip.UpdateTipTestaCmd;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.*;
import tri.novica.gfssystem.repository.spec.TestSpecs;
import tri.novica.gfssystem.utility.Brojaci;
import tri.novica.gfssystem.utility.SkolskaGodina;
import tri.novica.gfssystem.utility.Prolaz;
import tri.novica.gfssystem.utility.Utility;
import tri.novica.gfssystem.validation.TestPP;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class TestService {

    /** Polja po kojima lista testova sme da se sortira ({@code PageableUtil.proveri}). */
    public static final Set<String> SORT_POLJA = Set.of("datum", "maxPoena");
    public static final Sort PODRAZUMEVANI_SORT = Sort.by(Sort.Order.desc("datum"));

    private final TestRepository testRepository;
    private final TipTestaRepository tipTestaRepository;
    private final PredmetRepository predmetRepository;
    private final GrupaRepository grupaRepository;
    private final StudentRepository studentRepository;
    private final PolaganjeRepository polaganjeRepository;
    private final ModelMapper mapper;
    private final TestPP testPP;

    public TipTestaInfo createTipTesta(CreateTipTestaCmd cmd) {
        Predmet predmet = predmetRepository.findById(cmd.getPredmetId())
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + cmd.getPredmetId(), 404));

        TipTesta tipTesta = mapper.map(cmd, TipTesta.class);
        tipTesta.setPredmet(predmet);
        tipTesta.setAktivan(true);

        return mapper.map(tipTestaRepository.save(tipTesta), TipTestaInfo.class);
    }

    /** Preimenovanje i (de)aktivacija tipa testa; predmet tipa se ne menja. */
    public TipTestaInfo updateTipTesta(Long id, UpdateTipTestaCmd cmd) {
        TipTesta tip = tipTestaRepository.findById(id)
                .orElseThrow(() -> new SystemException("Tip testa ne postoji! ID = " + id, 404));
        tip.setNaziv(cmd.getNaziv().trim());
        tip.setAktivan(cmd.getAktivan());
        return mapper.map(tipTestaRepository.save(tip), TipTestaInfo.class);
    }

    public TestDetails findById(Long id) {
        Test test = testRepository.findByIdFetchPolaganja(id)
                .orElseThrow(() -> new SystemException("Test ne postoji! ID = " + id, 404));

        TestDetails details = mapper.map(test, TestDetails.class);
        details.setStatistika(izracunajStatistiku(test));

        return details;
    }

    private TestStatistikaInfo izracunajStatistiku(Test test) {
        TestStatistikaInfo stat = new TestStatistikaInfo();
        Set<Polaganje> polaganja = test.getPolaganja();

        if (polaganja == null || polaganja.isEmpty()) {
            stat.setUkupnoPolaganja(0);
            return stat;
        }

        // Filtriraj samo polaganja koja imaju ostvarene poene (evidentirana)
        List<Polaganje> evidentirana = polaganja.stream()
                .filter(p -> p.getOstvareniPoeni() != null)
                .toList();

        stat.setUkupnoPolaganja(polaganja.size());

        if (evidentirana.isEmpty()) {
            return stat;
        }

        // Poeni za statistiku
        List<Double> poeniLista = evidentirana.stream()
                .map(Polaganje::getOstvareniPoeni)
                .sorted()
                .toList();

        // Prosek
        double prosek = poeniLista.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        stat.setProsecniPoeni(Math.round(prosek * 100.0) / 100.0);

        // Min/Max
        stat.setMinPoeni(poeniLista.get(0));
        stat.setMaxPoeni(poeniLista.get(poeniLista.size() - 1));

        // Standardna devijacija
        double variance = poeniLista.stream()
                .mapToDouble(p -> Math.pow(p - prosek, 2))
                .average()
                .orElse(0);
        stat.setStandardnaDevijacija(Math.round(Math.sqrt(variance) * 100.0) / 100.0);

        // Prolaznost
        boolean saPragom = test.getPragProlaza() != null;   // bez praga nema pojma prolaza: sve prolaznosti ostaju null
        if (saPragom) {
            int polozenih = (int) evidentirana.stream().filter(Prolaz::polozeno).count();
            stat.setBrojPolozenih(polozenih);
            stat.setBrojPalih(evidentirana.size() - polozenih);
            stat.setProcenatProlaznosti(Math.round((polozenih * 100.0 / evidentirana.size()) * 100.0) / 100.0);
        }

        // Statistika po test grupi (A, B, C, D)
        Map<TestGrupa, List<Polaganje>> poGrupi = evidentirana.stream()
                .filter(p -> p.getGrupa() != null)
                .collect(Collectors.groupingBy(Polaganje::getGrupa));

        List<TestStatistikaPoGrupiInfo> statistikaPoGrupi = new ArrayList<>();
        for (TestGrupa grupa : TestGrupa.values()) {
            List<Polaganje> grupaPolaganja = poGrupi.getOrDefault(grupa, Collections.emptyList());
            if (!grupaPolaganja.isEmpty()) {
                TestStatistikaPoGrupiInfo grupaStat = new TestStatistikaPoGrupiInfo();
                grupaStat.setGrupa(grupa);
                grupaStat.setBrojPolaganja(grupaPolaganja.size());

                double grupaProsek = grupaPolaganja.stream()
                        .mapToDouble(Polaganje::getOstvareniPoeni)
                        .average()
                        .orElse(0);
                grupaStat.setProsecniPoeni(Math.round(grupaProsek * 100.0) / 100.0);

                if (saPragom) {
                    int grupaPolozenih = (int) grupaPolaganja.stream().filter(Prolaz::polozeno).count();
                    grupaStat.setProcenatProlaznosti(Math.round((grupaPolozenih * 100.0 / grupaPolaganja.size()) * 100.0) / 100.0);
                }

                statistikaPoGrupi.add(grupaStat);
            }
        }
        stat.setStatistikaPoGrupi(statistikaPoGrupi);

        return stat;
    }

    public TestInfo createTest(CreateTestCmd cmd) {
        Predmet predmet = predmetRepository.findById(cmd.getPredmetId())
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + cmd.getPredmetId(), 404));
        Grupa grupa = grupaRepository.findById(cmd.getGrupaId())
                .orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + cmd.getGrupaId(), 404));
        TipTesta tipTesta = tipTestaRepository.findById(cmd.getTipTestaId())
//                .orElseThrow(() -> new SystemException("Tip testa ne postoji! ID = " + cmd.getTipTestaId(), 404));
                .orElseGet(() -> {
                    if (cmd.getNovTipTesta() == null || cmd.getNovTipTesta().isBlank())
                        throw new SystemException("Tip testa nije postavljen!", HttpStatus.BAD_REQUEST);
                    else return new TipTesta(cmd.getNovTipTesta(), predmet);
                });

        Test test = mapper.map(cmd, Test.class);
        test.setPredmet(predmet);
        test.setGrupa(grupa);
        test.setTipTesta(tipTesta);
        test.setPregledan(false);
        test.generisiGrupe(cmd.getBrojGrupa());

        testPP.checkCreateTest(test);

        return mapper.map(testRepository.save(test), TestInfo.class);
    }

    public TestDetails updateTest(Long testId, UpdateTestCmd cmd) {
        Test test = testRepository.findByIdFetchPolaganja(testId)
                .orElseThrow(() -> new SystemException("Test ne postoji! ID = " + testId, 404));

        mapper.map(cmd, test);   // prag prolaza se ovde ne menja (PUT ga ne poznaje), vidi postaviPragProlaza

        TipTesta tipTesta = tipTestaRepository.findById(cmd.getTipTestaId())
                        .orElseThrow(() -> new SystemException("Tip testa ne postoji! ID = " + cmd.getTipTestaId(), 404));

        test.setTipTesta(tipTesta);
        testPP.checkUpdateTest(test);

        return mapper.map(testRepository.save(test), TestDetails.class);
    }

    /**
     * Postavlja ili uklanja ({@code null}) prag prolaza. Za razliku od {@link #updateTest}, dozvoljeno je i na
     * pregledanom (završenom) testu, jer prolaznost zavisi samo od praga i poena koji već postoje.
     */
    public TestDetails postaviPragProlaza(Long testId, PragProlazaCmd cmd) {
        Test test = testRepository.findByIdFetchPolaganja(testId)
                .orElseThrow(() -> new SystemException("Test ne postoji! ID = " + testId, 404));
        test.setPragProlaza(cmd.getPragProlaza());
        testPP.checkPragProlaza(test);
        TestDetails details = mapper.map(testRepository.save(test), TestDetails.class);
        details.setStatistika(izracunajStatistiku(test));   // prolaznost se menja zajedno sa pragom
        return details;
    }

    public TestDetails evidentirajIspitanika(EvidentirajPolaganjeCmd cmd, Long testId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new SystemException("Test ne postoji! ID = " + testId, 404));
        Student student = studentRepository.findById(cmd.getStudentId())
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + cmd.getStudentId(), 404));

        Polaganje polaganje = mapper.map(cmd, Polaganje.class);
        polaganje.setTest(test);
        polaganje.setStudent(student);

        testPP.checkCreatePolaganje(polaganje);

        var old = test.getPolaganja().stream().filter(
                p -> p.getStudent().getId().equals(polaganje.getStudent().getId())
        ).findFirst();
        old.ifPresent(p -> {
            test.getPolaganja().remove(p);
            polaganjeRepository.delete(p);
            test.getPolaganja().add(polaganje);
        });

        return mapper.map(testRepository.save(test), TestDetails.class);
    }

    /** Briše test zajedno sa polaganjima (kaskada); studenti i tip testa ostaju. */
    public void obrisi(Long id) {
        Test test = testRepository.findById(id)
                .orElseThrow(() -> new SystemException("Test ne postoji! ID = " + id, 404));
        testRepository.delete(test);
    }

    public TestDetails dodajIspitanika(Long testId, Long studentId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new SystemException("Test ne postoji! ID = " + testId, 404));
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + studentId, HttpStatus.NOT_FOUND));

        if (!Utility.smeNaNastavuGrupe(student, test.getGrupa()))
            throw new SystemException("Student " + student.getIndeks() + " ne pripada grupi " + test.getGrupa().getNaziv(), 400);

        Polaganje polaganje = Polaganje.defaultPolaganje(test, student);

        if (test.getPolaganja().stream().noneMatch(
                p -> p.getStudent().getId().equals(polaganje.getStudent().getId())
        )) test.getPolaganja().add(polaganje);

        return mapper.map(testRepository.save(test), TestDetails.class);
    }

    public TestDetails skloniIspitanika(Long testId, Long studentId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new SystemException("Test ne postoji! ID = " + testId, 404));
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + studentId, HttpStatus.NOT_FOUND));

        Polaganje polaganje = test.getPolaganja()
                .stream()
                .filter(p -> (p.getStudent().equals(student)))
                .findFirst()
                .orElseThrow(() -> new SystemException("Student nije dodat na ispit!", HttpStatus.BAD_REQUEST));

        test.getPolaganja().remove(polaganje);
        polaganjeRepository.delete(polaganje);

        return mapper.map(testRepository.save(test), TestDetails.class);
    }

    public TestDetails zavrsiEvidentiranje(Long testId) {
        Test test = testRepository.findByIdFetchPolaganja(testId)
                .orElseThrow(() -> new SystemException("Test ne postoji! ID = " + testId, 404));

        testPP.checkZavrsiEvidentiranje(test);
        test.setPregledan(true);

        return mapper.map(testRepository.save(test), TestDetails.class);
    }

    public List<TestInfo> vratiTestoveGrupaPredmet(Long gId, Long pId) {
        predmetRepository.findById(pId)
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + pId, 404));
        grupaRepository.findById(gId)
                .orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + gId, 404));

        List<Test> testovi = testRepository.findAllByGrupaIdAndPredmetIdOrderByDatumAsc(gId, pId);
        return testovi.stream().map(
                t -> {
                    var testInfo = mapper.map(t, TestInfo.class);
                    testInfo.setPosecenost(t.getPolaganja().size());
                    return testInfo;
                }
        ).toList();
    }

    /**
     * Lista testova sa filterima i stranicom. {@code pageable} je već prošao {@code PageableUtil.proveri}
     * (REST sloj). Predmet, grupa i tip dolaze u istom upitu, statistika polaganja jednim agregatnim upitom po stranici.
     */
    public PagedModel<TestListItem> pretraga(TestFilter f, Pageable pageable) {
        SkolskaGodina.proveri(f.godina());
        Specification<Test> spec = Specification.allOf(
                TestSpecs.zaPrikaz(),
                TestSpecs.predmet(f.predmetId()),
                TestSpecs.grupa(f.grupaId()),
                TestSpecs.tipTesta(f.tipTestaId()),
                TestSpecs.godina(f.godina()),
                TestSpecs.pregledan(f.pregledan()),
                TestSpecs.q(f.q()),
                TestSpecs.od(f.od()),
                TestSpecs.doDatuma(f.doDatuma()));
        Page<Test> strana = testRepository.findAll(spec, pageable);

        List<Long> ids = strana.map(Test::getId).toList();
        Set<Long> grupaIds = strana.stream().map(t -> t.getGrupa().getId()).collect(Collectors.toSet());
        Map<Long, Object[]> statistika = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] red : polaganjeRepository.statistikaPoTestu(ids)) {
                statistika.put(((Number) red[0]).longValue(), red);
            }
        }
        Map<Long, Long> studenti = Brojaci.poId(grupaIds, studentRepository::brojStudenataPoGrupi);

        return new PagedModel<>(strana.map(t -> uListItem(t, statistika.get(t.getId()), studenti)));
    }

    /** Ručno mapiranje; {@code statistika} je red {@code [testId, brojPolaganja, brojSaPoenima, prosek, brojPolozenih]} ili null. */
    private static TestListItem uListItem(Test t, Object[] statistika, Map<Long, Long> studenti) {
        long brojPolaganja = 0;
        Double prosek = null;
        Double procenatProlaznosti = null;
        if (statistika != null) {
            brojPolaganja = ((Number) statistika[1]).longValue();
            long saPoenima = ((Number) statistika[2]).longValue();
            if (saPoenima > 0) {
                prosek = ((Number) statistika[3]).doubleValue();
                if (t.getPragProlaza() != null) {
                    double procenat = 100.0 * ((Number) statistika[4]).longValue() / saPoenima;
                    procenatProlaznosti = Math.round(procenat * 100.0) / 100.0;   // 2 decimale kao u detaljima testa
                }
            }
        }
        Grupa g = t.getGrupa();
        GrupaInfo grupa = new GrupaInfo(g.getId(), g.getNaziv(), g.getGodinaUpisa(), studenti.getOrDefault(g.getId(), 0L));
        PredmetInfo predmet = new PredmetInfo(t.getPredmet().getId(), t.getPredmet().getNaziv());
        TipTesta tip = t.getTipTesta();
        return new TestListItem(t.getId(), t.getDatum(), new TipTestaInfo(tip.getId(), tip.getNaziv(), tip.getAktivan()), t.getMaxPoena(),
                t.getPragProlaza(), t.getPregledan(), predmet, grupa, brojPolaganja, prosek, procenatProlaznosti);
    }
}
