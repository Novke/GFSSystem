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
import tri.novica.gfssystem.dto.aktivnost.UpdateAktivnostNapomenaCmd;
import tri.novica.gfssystem.dto.grupa.GrupaInfo;
import tri.novica.gfssystem.dto.predavanje.*;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.*;
import tri.novica.gfssystem.repository.spec.PredavanjeSpecs;
import tri.novica.gfssystem.utility.Brojaci;
import tri.novica.gfssystem.utility.SkolskaGodina;
import tri.novica.gfssystem.utility.Utility;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PredavanjeService {

    /** Polja po kojima lista predavanja sme da se sortira ({@code PageableUtil.proveri}). */
    public static final Set<String> SORT_POLJA = Set.of("datum", "rb", "tema");
    public static final Sort PODRAZUMEVANI_SORT = Sort.by(Sort.Order.desc("datum"), Sort.Order.desc("rb"));

    private final PredavanjeRepository predavanjeRepository;
    private final PredmetRepository predmetRepository;
    private final GrupaRepository grupaRepository;
    private final StudentRepository studentRepository;
    private final AktivnostRepository aktivnostRepository;
    private final ModelMapper mapper;

    public PredavanjeDetails findById(Long id) {
        return mapper.map(predavanjeRepository.findById(id)
                .orElseThrow(() -> new SystemException("Predavanje ne postoji! ID = " + id, HttpStatus.NOT_FOUND)),
                PredavanjeDetails.class);
    }
    public PredavanjeDetails startPredavanje(StartPredavanjeCmd startPredavanjeCmd) {

        Long predmetId = startPredavanjeCmd.getPredmetId();
        Long grupaId = startPredavanjeCmd.getGrupaId();

        Predmet predmet = predmetRepository.findById(predmetId)
                .orElseThrow(() -> new SystemException("Predmet nije nadjen! ID = " + predmetId, HttpStatus.NOT_FOUND));
        Grupa grupa = grupaRepository.findById(grupaId)
                .orElseThrow(() -> new SystemException("Grupa nije pronadjena! ID = " + grupaId, HttpStatus.NOT_FOUND));

        Predavanje predavanje = new Predavanje();
        predavanje.setPredmet(predmet);
        predavanje.setGrupa(grupa);
        predavanje.setDatum(LocalDate.now());

        Integer redniBroj = predavanjeRepository.findPoslednjiRB();
        if (redniBroj == null || redniBroj == 0) redniBroj = 1;
        else redniBroj++;
        predavanje.setRb(redniBroj);

        return mapper.map(predavanjeRepository.save(predavanje), PredavanjeDetails.class);
    }

    public PredavanjeDetails updatePredavanje(Long id, UpdatePredavanjeCmd cmd) {
        Predavanje predavanje = predavanjeRepository.findById(id)
                .orElseThrow(() -> new SystemException("Predavanje ne postoji! ID = " + id, HttpStatus.NOT_FOUND));

        mapper.map(cmd, predavanje);

        Predavanje updatedPredavanje = predavanjeRepository.save(predavanje);
        return mapper.map(updatedPredavanje, PredavanjeDetails.class);
    }

    public PredavanjeDetails updatePosecenost(Long id){
        Predavanje predavanje = predavanjeRepository.findById(id)
                .orElseThrow(() -> new SystemException("Predavanje ne postoji! ID = " + id, HttpStatus.NOT_FOUND));

        predavanje.setPosecenost(predavanje.getAktivnosti().size());

        Predavanje updatedPredavanje = predavanjeRepository.save(predavanje);
        return mapper.map(updatedPredavanje, PredavanjeDetails.class);
    }


    public PredavanjeDetails dodajPrisutnog(Long predavanjeId, Long studentId) {
        Predavanje predavanje = predavanjeRepository.findById(predavanjeId)
                .orElseThrow(() -> new SystemException("Predavanje ne postoji! ID = " + predavanjeId, HttpStatus.NOT_FOUND));
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + studentId, HttpStatus.NOT_FOUND));

        // predavanje bez grupe (grupa_id je nullable) prima svakoga; inace samo grupu i starije generacije
        Grupa grupa = predavanje.getGrupa();
        if (grupa != null && !Utility.smeNaNastavuGrupe(student, grupa))
            throw new SystemException("Student " + student.getIndeks() + " ne pripada grupi " + grupa.getNaziv(), HttpStatus.BAD_REQUEST);

        Aktivnost aktivnost = new Aktivnost(predavanje, student, TipAktivnosti.PRISUSTVO);
        Set<Aktivnost> aktivnosti = predavanje.getAktivnosti();

        aktivnosti.forEach(a -> {
            if (a.equals(aktivnost)) throw new SystemException("Prisustvo studenta je vec zabelezeno!", HttpStatus.BAD_REQUEST);
        });

        aktivnosti.add(aktivnost);
        predavanje.setAktivnosti(aktivnosti);

        //POSECENOST++
        predavanje.uvecajPosecenost();

        Predavanje saved = predavanjeRepository.save(predavanje);
        return mapper.map(saved, PredavanjeDetails.class);
    }

    public PredavanjeDetails skloniPrisutnog(Long predavanjeId, Long studentId) {
        Predavanje predavanje = predavanjeRepository.findById(predavanjeId)
                .orElseThrow(() -> new SystemException("Predavanje ne postoji! ID = " + predavanjeId, HttpStatus.NOT_FOUND));
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + studentId, HttpStatus.NOT_FOUND));

        Aktivnost aktivnost = predavanje.getAktivnosti()
                .stream()
                .filter(a -> (a.getStudent().equals(student)))
                .findFirst()
                .orElseThrow(() -> new SystemException("Student nije dodat na predavanje!", HttpStatus.BAD_REQUEST));

        predavanje.getAktivnosti().remove(aktivnost);
        aktivnostRepository.delete(aktivnost);
        //POSECENOST--
        predavanje.umanjiPosecenost();

        Predavanje saved = predavanjeRepository.save(predavanje);
        return mapper.map(saved, PredavanjeDetails.class);
    }

    public PredavanjeDetails dodajZadatak(Long predavanjeId, Long studentId) {
        Predavanje predavanje = predavanjeRepository.findById(predavanjeId)
                .orElseThrow(() -> new SystemException("Predavanje ne postoji! ID = " + predavanjeId, HttpStatus.NOT_FOUND));
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + studentId, HttpStatus.NOT_FOUND));

        Aktivnost aktivnost = predavanje.getAktivnosti()
                .stream()
                .filter(a -> (a.getStudent().equals(student)))
                .findFirst()
                .orElseThrow(() -> new SystemException("Student nije dodat na predavanje!", HttpStatus.BAD_REQUEST));

        aktivnost.setTip(TipAktivnosti.ZADATAK);

        Predavanje saved = predavanjeRepository.save(predavanje);
        return mapper.map(saved, PredavanjeDetails.class);
    }

    public PredavanjeDetails skloniZadatak(Long predavanjeId, Long studentId) {
        Predavanje predavanje = predavanjeRepository.findById(predavanjeId)
                .orElseThrow(() -> new SystemException("Predavanje ne postoji! ID = " + predavanjeId, HttpStatus.NOT_FOUND));
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + studentId, HttpStatus.NOT_FOUND));

        Aktivnost aktivnost = predavanje.getAktivnosti()
                .stream()
                .filter(a -> (a.getStudent().equals(student)))
                .findFirst()
                .orElseThrow(() -> new SystemException("Student nije dodat na predavanje!", HttpStatus.BAD_REQUEST));

        aktivnost.setTip(TipAktivnosti.PRISUSTVO);

        Predavanje saved = predavanjeRepository.save(predavanje);
        return mapper.map(saved, PredavanjeDetails.class);
    }

    public PredavanjeDetails dodajZadatakSaZvezdicom(Long predavanjeId, Long studentId) {
        Predavanje predavanje = predavanjeRepository.findById(predavanjeId)
                .orElseThrow(() -> new SystemException("Predavanje ne postoji! ID = " + predavanjeId, HttpStatus.NOT_FOUND));
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + studentId, HttpStatus.NOT_FOUND));

        Aktivnost aktivnost = predavanje.getAktivnosti()
                .stream()
                .filter(a -> (a.getStudent().equals(student)))
                .findFirst()
                .orElseThrow(() -> new SystemException("Student nije dodat na predavanje!", HttpStatus.BAD_REQUEST));

        aktivnost.setTip(TipAktivnosti.SA_ZVEZDICOM);

        Predavanje saved = predavanjeRepository.save(predavanje);
        return mapper.map(saved, PredavanjeDetails.class);
    }

    public PredavanjeAktivnostInfo updateAktivnostNapomenu(Long id, UpdateAktivnostNapomenaCmd cmd) {
        Aktivnost aktivnost = aktivnostRepository.findById(id)
                .orElseThrow(() -> new SystemException("Aktivnost ne postoji!", HttpStatus.NOT_FOUND));

        mapper.map(cmd, aktivnost);
        return mapper.map(aktivnostRepository.save(aktivnost), PredavanjeAktivnostInfo.class);
    }

    public List<PredavanjeInfo> pretraziPredavanja(Long predmetId, Long grupaId) {
        return predavanjeRepository.findAllByGrupaIdAndPredmetIdOrderByDatumAsc(grupaId, predmetId)
                .stream().map(
                        predavanje -> mapper.map(predavanje, PredavanjeInfo.class)
                ).toList();
    }

    public PredavanjeDetails zavrsiPredavanje(Long id) {
        Predavanje predavanje = predavanjeRepository.findById(id)
                .orElseThrow(() -> new SystemException("Predavanje ne postoji! ID = " + id, 404));

        predavanje.setZavrseno(true);
        return mapper.map(predavanje, PredavanjeDetails.class);
    }

    public List<PredavanjeInfo> vratiPredavanjaGrupaPredmet(Long gId, Long pId) {
        predmetRepository.findById(pId)
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + pId, 404));
        grupaRepository.findById(gId)
                .orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + gId, 404));

        List<Predavanje> predavanja = predavanjeRepository.findAllByGrupaIdAndPredmetIdOrderByDatumAsc(gId, pId);
        return predavanja.stream().map(
                p -> mapper.map(p, PredavanjeInfo.class)
        ).toList();
    }

    /**
     * Lista predavanja sa filterima i stranicom. {@code pageable} je već prošao {@code PageableUtil.proveri}
     * (REST sloj). Predmet i grupa dolaze u istom upitu, brojači jednim agregatnim upitom po stranici.
     */
    public PagedModel<PredavanjeListItem> pretraga(PredavanjeFilter f, Pageable pageable) {
        SkolskaGodina.proveri(f.godina());
        Specification<Predavanje> spec = Specification.allOf(
                PredavanjeSpecs.zaPrikaz(),
                PredavanjeSpecs.predmet(f.predmetId()),
                PredavanjeSpecs.grupa(f.grupaId()),
                PredavanjeSpecs.godina(f.godina()),
                PredavanjeSpecs.zavrseno(f.zavrseno()),
                PredavanjeSpecs.tema(f.q()),
                PredavanjeSpecs.od(f.od()),
                PredavanjeSpecs.doDatuma(f.doDatuma()));
        Page<Predavanje> strana = predavanjeRepository.findAll(spec, pageable);

        List<Long> ids = strana.map(Predavanje::getId).toList();
        Set<Long> grupaIds = strana.stream().map(Predavanje::getGrupa).filter(Objects::nonNull)
                .map(Grupa::getId).collect(Collectors.toSet());
        Map<Long, Long> prisutni = Brojaci.poId(ids, aktivnostRepository::brojPrisutnihPoPredavanju);
        Map<Long, Long> studenti = Brojaci.poId(grupaIds, studentRepository::brojStudenataPoGrupi);

        return new PagedModel<>(strana.map(p -> uListItem(p, prisutni, studenti)));
    }

    /** Ručno mapiranje (ModelMapper je STRICT, a brojači nisu polja entiteta). */
    private static PredavanjeListItem uListItem(Predavanje p, Map<Long, Long> prisutni, Map<Long, Long> studenti) {
        Grupa g = p.getGrupa();
        long brojStudenata = g == null ? 0 : studenti.getOrDefault(g.getId(), 0L);
        GrupaInfo grupa = g == null ? null : new GrupaInfo(g.getId(), g.getNaziv(), g.getGodinaUpisa(), brojStudenata);
        PredmetInfo predmet = new PredmetInfo(p.getPredmet().getId(), p.getPredmet().getNaziv());
        return new PredavanjeListItem(p.getId(), p.getRb(), p.getDatum(), p.getTema(), p.getZavrseno(), predmet, grupa,
                prisutni.getOrDefault(p.getId(), 0L), brojStudenata);
    }
}
