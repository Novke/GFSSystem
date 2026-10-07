package tri.novica.gfssystem.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.domaci.*;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.entity.view.DomaciEvidentiranjeView;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.*;
import tri.novica.gfssystem.repository.spec.DomaciSpecs;
import tri.novica.gfssystem.utility.Brojaci;
import tri.novica.gfssystem.utility.SkolskaGodina;
import tri.novica.gfssystem.utility.Utility;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DomaciService {

    /** Polja po kojima lista domaćih sme da se sortira ({@code PageableUtil.proveri}). */
    public static final Set<String> SORT_POLJA = Set.of("datum", "naslov");
    public static final Sort PODRAZUMEVANI_SORT = Sort.by(Sort.Order.desc("datum"));

    private final DomaciRepository domaciRepository;
    private final PredavanjeRepository predavanjeRepository;
    private final GrupaRepository grupaRepository;
    private final StudentRepository studentRepository;
    private final PredmetRepository predmetRepository;
    private final DomaciEvidentiranjeRepository domaciEvidentiranjeRepository;
    private final ModelMapper mapper;


    public DomaciId dodajDomaci(DodajDomaciCmd cmd) {
        Predmet predmet = predmetRepository.findById(cmd.getPredmetId())
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + cmd.getPredmetId(), 404));
        Grupa grupa = grupaRepository.findById(cmd.getGrupaId())
                .orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + cmd.getGrupaId(), 404));

        Long predavanjeId = cmd.getPredavanjeId();
        Predavanje predavanje = null;
        if (predavanjeId != null && predavanjeId != 0) {
            predavanje = predavanjeRepository.findById(predavanjeId)
                    .orElseThrow(() -> new SystemException("Predavanje ne postoji! ID = " + predavanjeId, 404));
        }

        Domaci domaci = new Domaci();
        domaci.setPredavanje(predavanje);
        domaci.setPredmet(predmet);
        domaci.setGrupa(grupa);
        domaci.setDatum(LocalDate.now());

        return mapper.map(domaciRepository.save(domaci), DomaciId.class);
    }

    public DomaciDetails getDomaci(Long id) {
        Domaci domaci = domaciRepository.findDomaciPlusGrupaPredmetPredavanje(id)
                .orElseThrow(() -> new SystemException("Domaci ne postoji! ID = " + id, 404));

        List<DomaciEvidentiranjeView> studentiViews = domaciEvidentiranjeRepository.findAllByDomaciId(id);
        DomaciDetails domaciDetails = mapper.map(domaci, DomaciDetails.class);

        studentiViews.forEach(st ->
                domaciDetails.getStudenti().add(mapper.map(st, DomaciStudentiInfo.class))
        );

        domaciDetails.getStudenti().sort((s1, s2) -> {
            return Utility.index2int(s1.getIndeks()) - Utility.index2int(s2.getIndeks());
        });

        return domaciDetails;
    }

    public DomaciDetails dodajEvidentaciju(CreateUradjenDomaciCmd cmd) {
        Domaci domaci = domaciRepository.findDomaciPlusGrupaPredmetPredavanje(cmd.getDomaciId())
                .orElseThrow(() -> new SystemException("Domaci ne postoji! ID = " + cmd.getDomaciId(), 404));
        Student student = studentRepository.findById(cmd.getStudentId())
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + cmd.getStudentId()));


        Optional<UradjenDomaci> existing = domaci.getUradjeniDomaci().stream()
                .filter(ud -> ud.getStudent().getId().equals(student.getId()))
                .findFirst();

        if (existing.isPresent()) {
            mapper.map(cmd, existing.get());
        } else {
            UradjenDomaci entity = mapper.map(cmd, UradjenDomaci.class);
            entity.setStudent(student);
            entity.setDomaci(domaci);
            domaci.getUradjeniDomaci().add(entity);
        }

        domaciRepository.save(domaci);

        return getDomaci(cmd.getDomaciId());
    }

    public DomaciDetails oslobodi(Long id) {
        Domaci domaci = domaciRepository.findDomaciPlusGrupaPredmetPredavanje(id)
                .orElseThrow(() -> new SystemException("Domaci ne postoji! ID = " + id, 404));

        Set<UradjenDomaci> uradjeniDomaci = new HashSet<>();
        Set<UradjenDomaci> existing = domaci.getUradjeniDomaci();

        domaci.getPredavanje().getAktivnosti()
                .stream()
                .filter(a -> a != null && a.getTip() != null && a.getTip() != TipAktivnosti.PRISUSTVO)
                .forEach(a -> {
                    Optional<UradjenDomaci> optional = existing.stream().filter(ud ->
                            a.getStudent().getId().equals(ud.getStudent().getId())
                    ).findFirst();

                    if (optional.isPresent()) {
                        UradjenDomaci uradjenDomaci = optional.get();
                        uradjenDomaci.setBodovi(10);
                        uradjenDomaci.setOslobodjen(true);
                        uradjeniDomaci.add(uradjenDomaci);
                    } else {
                        uradjeniDomaci.add(UradjenDomaci.oslobodjenDomaci(a.getStudent(), domaci));
                    }
                });

        domaci.setUradjeniDomaci(uradjeniDomaci);
        domaciRepository.save(domaci);

        return getDomaci(id);
    }

    public DomaciDetails azuriraj(Long id, UpdateDomaciCmd cmd) {
        Domaci domaci = domaciRepository.findDomaciPlusGrupaPredmetPredavanje(id)
                .orElseThrow(() -> new SystemException("Domaci ne postoji! ID = " + id, 404));

        mapper.map(cmd, domaci);
        domaciRepository.save(domaci);

        return getDomaci(id);
    }

    public void zavrsiPregledanje(Long id) {
        Domaci domaci = domaciRepository.findDomaciPlusGrupaPredmetPredavanje(id)
                .orElseThrow(() -> new SystemException("Domaci ne postoji! ID = " + id, 404));

        domaci.setPregledan(true);
        domaciRepository.save(domaci);
    }

    public List<DomaciInfo> vratiDomaceIzGrupaPredmet(Long gId, Long pId) {
        Predmet predmet = predmetRepository.findById(pId)
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + pId, 404));
        Grupa grupa = grupaRepository.findById(gId)
                .orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + gId, 404));

        List<Domaci> domaci = domaciRepository.findAllByGrupaAndPredmetOrderByDatumAsc(grupa, predmet);
        return domaci.stream().map(
                        d -> mapper.map(d, DomaciInfo.class))
                .toList();
    }

    /**
     * Lista domaćih sa filterima i stranicom. {@code pageable} je već prošao {@code PageableUtil.proveri}
     * (REST sloj). Predmet, grupa i predavanje dolaze u istom upitu, brojači jednim agregatnim upitom po stranici.
     */
    public PagedModel<DomaciListItem> pretraga(DomaciFilter f, Pageable pageable) {
        SkolskaGodina.proveri(f.godina());
        Specification<Domaci> spec = Specification.allOf(
                DomaciSpecs.zaPrikaz(),
                DomaciSpecs.predmet(f.predmetId()),
                DomaciSpecs.grupa(f.grupaId()),
                DomaciSpecs.godina(f.godina()),
                DomaciSpecs.pregledan(f.pregledan()),
                DomaciSpecs.naslov(f.q()),
                DomaciSpecs.od(f.od()),
                DomaciSpecs.doDatuma(f.doDatuma()));
        Page<Domaci> strana = domaciRepository.findAll(spec, pageable);

        List<Long> ids = strana.map(Domaci::getId).toList();
        Set<Long> grupaIds = strana.stream().map(Domaci::getGrupa).filter(Objects::nonNull)
                .map(Grupa::getId).collect(Collectors.toSet());
        Map<Long, Long> uradjeni = Brojaci.poId(ids, domaciRepository::brojUradjenihPoDomacem);
        Map<Long, Long> studenti = Brojaci.poId(grupaIds, studentRepository::brojStudenataPoGrupi);

        return new PagedModel<>(strana.map(d -> uListItem(d, uradjeni, studenti)));
    }

    /** Ručno mapiranje (ModelMapper je STRICT, a brojači nisu polja entiteta). */
    private static DomaciListItem uListItem(Domaci d, Map<Long, Long> uradjeni, Map<Long, Long> studenti) {
        Grupa g = d.getGrupa();
        long brojStudenata = g == null ? 0 : studenti.getOrDefault(g.getId(), 0L);
        GrupaInfo grupa = g == null ? null : new GrupaInfo(g.getId(), g.getNaziv(), g.getGodinaUpisa(), brojStudenata);
        PredmetInfo predmet = new PredmetInfo(d.getPredmet().getId(), d.getPredmet().getNaziv());
        Predavanje p = d.getPredavanje();
        DomaciPredavanjeRef predavanje = p == null ? null : new DomaciPredavanjeRef(p.getId(), p.getRb());
        return new DomaciListItem(d.getId(), d.getNaslov(), d.getDatum(), d.getPregledan(), predmet, grupa, predavanje,
                uradjeni.getOrDefault(d.getId(), 0L), brojStudenata);
    }
}
