package tri.novica.gfssystem.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.test.*;
import tri.novica.gfssystem.dto.test.tip.CreateTipTestaCmd;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.*;
import tri.novica.gfssystem.validation.TestPP;

@Service
@Transactional
@RequiredArgsConstructor
public class TestService {

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

    public TestDetails findById(Long id) {
        return mapper.map(testRepository.findById(id)
                        .orElseThrow(() -> new SystemException("Test ne postoji! ID = " + id, 404)),
                TestDetails.class);
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

        mapper.map(cmd, test);

        TipTesta tipTesta = tipTestaRepository.findById(cmd.getTipTestaId())
                        .orElseThrow(() -> new SystemException("Tip testa ne postoji! ID = " + cmd.getTipTestaId(), 404));

        test.setTipTesta(tipTesta);
        testPP.checkUpdateTest(test);

        return mapper.map(testRepository.save(test), TestDetails.class);
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

    public TestDetails dodajIspitanika(Long testId, Long studentId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new SystemException("Test ne postoji! ID = " + testId, 404));
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + studentId, HttpStatus.NOT_FOUND));

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
}
