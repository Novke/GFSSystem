package tri.novica.gfssystem.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tri.novica.gfssystem.dto.beleska.BeleskaInfo;
import tri.novica.gfssystem.dto.beleska.SaveBeleskaCmd;
import tri.novica.gfssystem.entity.Beleska;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.BeleskaRepository;
import tri.novica.gfssystem.repository.StudentRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BeleskaService {

    private final BeleskaRepository beleskaRepository;
    private final StudentRepository studentRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<BeleskaInfo> findByStudent(Long studentId) {
        proveriStudenta(studentId);
        return beleskaRepository.findByStudentIdOrderByKreiranoDescIdDesc(studentId).stream()
                .map(BeleskaService::toInfo).toList();
    }

    @Transactional
    public BeleskaInfo create(Long studentId, SaveBeleskaCmd cmd) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> studentNePostoji(studentId));
        Beleska beleska = new Beleska();
        beleska.setStudent(student);
        beleska.setTekst(cmd.getTekst().strip());
        beleska.setKreirano(LocalDateTime.now(clock));
        return toInfo(beleskaRepository.save(beleska));
    }

    @Transactional
    public BeleskaInfo update(Long id, SaveBeleskaCmd cmd) {
        Beleska beleska = nadji(id);
        beleska.setTekst(cmd.getTekst().strip());
        beleska.setIzmenjeno(LocalDateTime.now(clock));
        return toInfo(beleskaRepository.save(beleska));
    }

    @Transactional
    public void delete(Long id) {
        beleskaRepository.delete(nadji(id));
    }

    private Beleska nadji(Long id) {
        return beleskaRepository.findById(id)
                .orElseThrow(() -> new SystemException("Beleška ne postoji! ID = " + id, HttpStatus.NOT_FOUND));
    }

    private void proveriStudenta(Long studentId) {
        if (!studentRepository.existsById(studentId)) {
            throw studentNePostoji(studentId);
        }
    }

    private static SystemException studentNePostoji(Long id) {
        return new SystemException("Student ne postoji! ID = " + id, HttpStatus.NOT_FOUND);
    }

    private static BeleskaInfo toInfo(Beleska b) {
        return new BeleskaInfo(b.getId(), b.getTekst(), b.getKreirano(), b.getIzmenjeno());
    }
}
