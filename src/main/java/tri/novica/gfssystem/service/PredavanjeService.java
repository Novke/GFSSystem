package tri.novica.gfssystem.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.predavanje.PredavanjeDetails;
import tri.novica.gfssystem.dto.predavanje.StartPredavanjeCmd;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.PredavanjeRepository;
import tri.novica.gfssystem.repository.PredmetRepository;
import tri.novica.gfssystem.repository.StudentRepository;

import java.time.LocalDate;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PredavanjeService {

    private final PredavanjeRepository predavanjeRepository;
    private final PredmetRepository predmetRepository;
    private final GrupaRepository grupaRepository;
    private final StudentRepository studentRepository;
    private final ModelMapper mapper;

    public PredavanjeDetails startPredavanje(StartPredavanjeCmd startPredavanjeCmd) {

        Long predmetId = startPredavanjeCmd.getPredmetId();
        Long grupaId = startPredavanjeCmd.getGrupaId();

        Predmet predmet = predmetRepository.findById(predmetId)
                .orElseThrow(() -> new SystemException("Predmet nije nadjen! ID = " + predmetId, HttpStatus.NOT_FOUND.value()));
        Grupa grupa = grupaRepository.findById(grupaId)
                .orElseThrow(() -> new SystemException("Grupa nije pronadjena! ID = " + grupaId, HttpStatus.NOT_FOUND.value()));

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

    public PredavanjeDetails dodajPrisutnog(Long predavanjeId, Long studentId) {
        Predavanje predavanje = predavanjeRepository.findById(predavanjeId)
                .orElseThrow(() -> new SystemException("Predavanje ne postoji! ID = " + predavanjeId, 404));
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + studentId, 404));



        Aktivnost aktivnost = new Aktivnost(predavanje, student, TipAktivnosti.PRISUSTVO);
        Set<Aktivnost> aktivnosti = predavanje.getAktivnosti();

        aktivnosti.forEach(a -> {
            if (a.equals(aktivnost)) throw new SystemException("Prisustvo studenta je vec zabelezeno!", HttpStatus.BAD_REQUEST.value());
        });

        aktivnosti.add(aktivnost);

        Predavanje saved = predavanjeRepository.save(predavanje);
        return mapper.map(saved, PredavanjeDetails.class);
    }
}
