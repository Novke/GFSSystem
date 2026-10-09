package tri.novica.gfssystem.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.domaci.DomaciFilter;
import tri.novica.gfssystem.dto.domaci.DomaciListItem;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.pregled.*;
import tri.novica.gfssystem.dto.predavanje.PredavanjeFilter;
import tri.novica.gfssystem.dto.predavanje.PredavanjeListItem;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.dto.student.StudentFilter;
import tri.novica.gfssystem.dto.test.TestFilter;
import tri.novica.gfssystem.dto.test.TestListItem;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.repository.DomaciRepository;
import tri.novica.gfssystem.repository.PredavanjeRepository;
import tri.novica.gfssystem.repository.StudentRepository;
import tri.novica.gfssystem.repository.TestRepository;
import tri.novica.gfssystem.repository.spec.DomaciSpecs;
import tri.novica.gfssystem.repository.spec.PredavanjeSpecs;
import tri.novica.gfssystem.repository.spec.TestSpecs;
import tri.novica.gfssystem.utility.PageableUtil;
import tri.novica.gfssystem.utility.SkolskaGodina;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Kontrolna tabla (početna) i globalna pretraga. Liste koriste {@code pretraga} servisa iz lista (isti filteri,
 * mapiranje i brojači), "danas", tekuća nedelja i tekuća školska godina dolaze iz {@link Clock} bean-a.
 * "Čeka na tebe" i broj starijih su ograničeni na tekuću školsku godinu; testovi i domaći u "čeka" samo ako su
 * već održani (datum do danas), budući su u "ova nedelja" i listama.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PregledService {

    static final int MAX_CEKA = 10;
    static final int MAX_U_TOKU = 10;
    static final int MAX_PRETRAGA = 5;
    static final int MIN_DUZINA_UPITA = 2;
    private static final Sort PO_DATUMU = Sort.by(Sort.Order.asc("datum"), Sort.Order.asc("id"));

    private final PredavanjeService predavanjeService;
    private final DomaciService domaciService;
    private final TestService testService;
    private final StudentService studentService;
    private final GrupaService grupaService;
    private final OnboardingService onboardingService;
    private final PredavanjeRepository predavanjeRepository;
    private final DomaciRepository domaciRepository;
    private final TestRepository testRepository;
    private final StudentRepository studentRepository;
    private final Clock clock;

    public KontrolnaTablaInfo kontrolnaTabla() {
        LocalDate danas = LocalDate.now(clock);
        int godina = SkolskaGodina.tekuca(clock);
        List<PredavanjeListItem> uToku = predavanjeService.pretraga(
                new PredavanjeFilter(null, null, null, false, null, danas, danas),
                strana(MAX_U_TOKU, PredavanjeService.SORT_POLJA, PredavanjeService.PODRAZUMEVANI_SORT)).getContent();
        PagedModel<TestListItem> testovi = testService.pretraga(new TestFilter(null, null, godina, false, null, null, danas),
                strana(MAX_CEKA, TestService.SORT_POLJA, TestService.PODRAZUMEVANI_SORT));
        PagedModel<DomaciListItem> domaci = domaciService.pretraga(new DomaciFilter(null, null, godina, false, null, null, danas),
                strana(MAX_CEKA, DomaciService.SORT_POLJA, DomaciService.PODRAZUMEVANI_SORT));
        PagedModel<PredavanjeListItem> nezavrsena = predavanjeService.pretraga(
                new PredavanjeFilter(null, null, godina, false, null, null, danas.minusDays(1)),
                strana(MAX_CEKA, PredavanjeService.SORT_POLJA, PredavanjeService.PODRAZUMEVANI_SORT));
        KontrolnaTablaInfo.Ceka ceka = new KontrolnaTablaInfo.Ceka(
                testovi.getContent(),
                domaci.getContent(),
                onboardingService.saPrijavamaNaCekanju(MAX_CEKA).stream()
                        .map(s -> new CekaStavkaInfo(s.getId(), s.getGrupa(), s.getBrojNaCekanju(), s.getIstice()))
                        .toList(),
                nezavrsena.getContent(),
                ukupno(testovi), ukupno(domaci), onboardingService.brojPrijavaNaCekanju(), ukupno(nezavrsena));
        return new KontrolnaTablaInfo(sledece(godina), uToku, ceka, nedelja(danas));
    }

    /** Ukupan broj redova upita (ne samo prve strane); metadata nikad nije null kad je lista napravljena iz {@code Page}. */
    private static long ukupno(PagedModel<?> strana) {
        return strana.getMetadata() == null ? strana.getContent().size() : strana.getMetadata().totalElements();
    }

    /** Upit se trim-uje; kraći od {@value #MIN_DUZINA_UPITA} znaka (ili null) daje prazne nizove bez upita u bazu. */
    public PretragaRezultatInfo pretraga(String q) {
        String upit = q == null ? "" : q.strip();
        if (upit.length() < MIN_DUZINA_UPITA) return PretragaRezultatInfo.prazno();
        return new PretragaRezultatInfo(
                studentService.pretraga(new StudentFilter(null, null, upit),
                        strana(MAX_PRETRAGA, StudentService.SORT_POLJA, StudentService.PODRAZUMEVANI_SORT)).getContent(),
                predavanjeService.pretraga(new PredavanjeFilter(null, null, null, null, upit, null, null),
                        strana(MAX_PRETRAGA, PredavanjeService.SORT_POLJA, PredavanjeService.PODRAZUMEVANI_SORT)).getContent(),
                testService.pretraga(new TestFilter(null, null, null, null, null, null, null, upit),
                        strana(MAX_PRETRAGA, TestService.SORT_POLJA, TestService.PODRAZUMEVANI_SORT)).getContent(),
                grupaService.pretraga(upit, MAX_PRETRAGA));
    }

    /** Prva strana sa podrazumevanim sortom liste (i {@code id desc} na kraju, kao u REST sloju). */
    private static Pageable strana(int velicina, Set<String> polja, Sort sort) {
        return PageableUtil.proveri(PageRequest.of(0, velicina), polja, sort);
    }

    /**
     * Predmet i grupa poslednjeg predavanja po (datum, id), {@code rb + 1}; null kad predavanja nema. Stariji se broje
     * samo po aktivnostima i polaganjima iz školske godine {@code godina}.
     */
    private SledecePredavanjeInfo sledece(int godina) {
        return predavanjeRepository.findFirstByOrderByDatumDescIdDesc().map(p -> {
            Grupa g = p.getGrupa();
            long brojStudenata = g == null ? 0 : studentRepository.countByGrupaId(g.getId());
            long brojStarijih = g == null || g.getGodinaUpisa() == null ? 0
                    : studentRepository.brojStarijihNaPredmetu(p.getPredmet().getId(), g.getGodinaUpisa(),
                            SkolskaGodina.pocetak(godina), SkolskaGodina.kraj(godina));
            GrupaInfo grupa = g == null ? null : new GrupaInfo(g.getId(), g.getNaziv(), g.getGodinaUpisa(), brojStudenata);
            return new SledecePredavanjeInfo(predmet(p.getPredmet()), grupa, p.getRb() + 1, brojStudenata, brojStarijih);
        }).orElse(null);
    }

    /** Predavanja, domaći i testovi od ponedeljka do nedelje tekuće nedelje; po datumu, pa tipu, pa id-ju. */
    private List<AgendaStavkaInfo> nedelja(LocalDate danas) {
        LocalDate ponedeljak = danas.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate nedelja = ponedeljak.plusDays(6);
        List<AgendaStavkaInfo> stavke = new ArrayList<>();

        predavanjeRepository.findAll(Specification.allOf(PredavanjeSpecs.zaPrikaz(),
                        PredavanjeSpecs.od(ponedeljak), PredavanjeSpecs.doDatuma(nedelja)), PO_DATUMU)
                .forEach(p -> stavke.add(new AgendaStavkaInfo(AgendaStavkaInfo.Tip.PREDAVANJE, p.getId(), p.getDatum(),
                        "Predavanje " + p.getRb(), predmet(p.getPredmet()), grupa(p.getGrupa()))));
        domaciRepository.findAll(Specification.allOf(DomaciSpecs.zaPrikaz(),
                        DomaciSpecs.od(ponedeljak), DomaciSpecs.doDatuma(nedelja)), PO_DATUMU)
                .forEach(d -> stavke.add(new AgendaStavkaInfo(AgendaStavkaInfo.Tip.DOMACI, d.getId(), d.getDatum(),
                        iliPodrazumevano(d.getNaslov(), "Domaći"), predmet(d.getPredmet()), grupa(d.getGrupa()))));
        testRepository.findAll(Specification.allOf(TestSpecs.zaPrikaz(),
                        TestSpecs.od(ponedeljak), TestSpecs.doDatuma(nedelja)), PO_DATUMU)
                .forEach(t -> stavke.add(new AgendaStavkaInfo(AgendaStavkaInfo.Tip.TEST, t.getId(), t.getDatum(),
                        iliPodrazumevano(t.getTipTesta().getNaziv(), "Test"), predmet(t.getPredmet()), grupa(t.getGrupa()))));

        stavke.sort(Comparator.comparing(AgendaStavkaInfo::datum)
                .thenComparing(AgendaStavkaInfo::tip)
                .thenComparing(AgendaStavkaInfo::id));
        return stavke;
    }

    private static PredmetInfo predmet(Predmet p) {
        return new PredmetInfo(p.getId(), p.getNaziv());
    }

    /** Bez broja studenata (agenda ga ne prikazuje); null za stare redove bez grupe. */
    private static GrupaInfo grupa(Grupa g) {
        return g == null ? null : new GrupaInfo(g.getId(), g.getNaziv(), g.getGodinaUpisa());
    }

    private static String iliPodrazumevano(String tekst, String podrazumevano) {
        return tekst == null || tekst.isBlank() ? podrazumevano : tekst;
    }
}
