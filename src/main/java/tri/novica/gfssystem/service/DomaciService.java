package tri.novica.gfssystem.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.domaci.*;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.entity.view.DomaciEvidentiranjeView;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.*;
import tri.novica.gfssystem.utility.Utility;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DomaciService {

    private final DomaciRepository domaciRepository;
    private final PredavanjeRepository predavanjeRepository;
    private final GrupaRepository grupaRepository;
    private final StudentRepository studentRepository;
    private final PredmetRepository predmetRepository;
    private final DomaciEvidentiranjeRepository domaciEvidentiranjeRepository;
    private final ModelMapper mapper;


    public DomaciInfo dodajDomaci(DodajDomaciCmd cmd) {
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

        return mapper.map(domaciRepository.save(domaci), DomaciInfo.class);
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
}
