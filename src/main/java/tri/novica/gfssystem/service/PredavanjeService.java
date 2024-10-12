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

    public PredavanjeDetails dodajPrisutnog(Long predavanjeId, Long studentId) {
        Predavanje predavanje = predavanjeRepository.findById(predavanjeId)
                .orElseThrow(() -> new SystemException("Predavanje ne postoji! ID = " + predavanjeId, HttpStatus.NOT_FOUND));
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + studentId, HttpStatus.NOT_FOUND));



        Aktivnost aktivnost = new Aktivnost(predavanje, student, TipAktivnosti.PRISUSTVO);
        Set<Aktivnost> aktivnosti = predavanje.getAktivnosti();

        aktivnosti.forEach(a -> {
            if (a.equals(aktivnost)) throw new SystemException("Prisustvo studenta je vec zabelezeno!", HttpStatus.BAD_REQUEST);
        });

        aktivnosti.add(aktivnost);
        predavanje.setAktivnosti(aktivnosti);

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

        if (TipAktivnosti.SA_ZVEZDICOM.equals(aktivnost.getTip())) throw new SystemException("Student je vec uradio zadatak sa zvezdicom!", HttpStatus.BAD_REQUEST);

        aktivnost.setTip(TipAktivnosti.ZADATAK);

        Predavanje saved = predavanjeRepository.save(predavanje);
        return mapper.map(saved, PredavanjeDetails.class);
    }
}