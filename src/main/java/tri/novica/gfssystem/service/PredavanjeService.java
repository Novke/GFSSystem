package tri.novica.gfssystem.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.predavanje.PredavanjeDetails;
import tri.novica.gfssystem.dto.predavanje.StartPredavanjeCmd;
import tri.novica.gfssystem.dto.predavanje.UpdatePredavanjeCmd;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.*;

import java.time.LocalDate;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class PredavanjeService {

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



        Aktivnost aktivnost = new Aktivnost(predavanje, student, TipAktivnosti.PRISUSTVO);
        Set<Aktivnost> aktivnosti = predavanje.getAktivnosti();

        aktivnosti.forEach(a -> {
            if (a.equals(aktivnost)) throw new SystemException("Prisustvo studenta je vec zabelezeno!", HttpStatus.BAD_REQUEST);
        });

        aktivnosti.add(aktivnost);
        predavanje.setAktivnosti(aktivnosti);

        //POSECENOST++
        predavanje.uvecajPOsecenost();

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

}
