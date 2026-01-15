package tri.novica.gfssystem.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.predmet.PredmetInfo;
import tri.novica.gfssystem.dto.student.CreateStudentCmd;
import tri.novica.gfssystem.dto.student.StudentDetails;
import tri.novica.gfssystem.dto.student.StudentInfo;
import tri.novica.gfssystem.dto.student.pregled.*;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.entity.*;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.*;
import tri.novica.gfssystem.utility.StudentMapper;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final GrupaRepository grupaRepository;
    private final PredmetRepository predmetRepository;
    private final AktivnostRepository aktivnostRepository;
    private final DomaciRepository domaciRepository;
    private final PolaganjeRepository polaganjeRepository;
    private final ModelMapper mapper;

    public List<StudentInfo> findAll() {
        return studentRepository.findAll()
                .stream().map(
                        student -> mapper.map(student, StudentInfo.class)
                ).toList();
    }

    public StudentPregledDetails findById(Long id) {
        Student student = studentRepository.findByIdFetchDetails(id)
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + id, HttpStatus.NOT_FOUND));

        StudentPregledDetails details = StudentMapper.INSTANCE.toPregledDetails(student);
        details.setAktivnosti(student.getAktivnosti().stream().map(StudentMapper.INSTANCE::toAktivnostDetails).toList());
//        details.setAktivnosti(StudentMapper.INSTANCE.toAktivnostDetails(student.getAktivnosti()));
        details.setUradjeniDomaci(student.getUradjeniDomaci().stream().map(StudentMapper.INSTANCE::toDomaciDetails).toList());
//        details.setUradjeniDomaci(StudentMapper.INSTANCE.toDomaciDetails(student.getUradjeniDomaci()));
        details.setPolaganja(student.getPolaganja().stream().map(StudentMapper.INSTANCE::toTestDetails).toList());
//        details.setPolaganja(StudentMapper.INSTANCE.toTestDetails(student.getPolaganja()));


        return details;
    }

    public StudentInfo create(CreateStudentCmd studentCmd) {
        Grupa grupa = findGrupaOrThrow(studentCmd.getGrupaId());

        Student newStudent = mapper.map(studentCmd, Student.class);
        newStudent.setGrupa(grupa);

        return mapper.map(studentRepository.save(newStudent),
                StudentInfo.class
        );

    }

    private Grupa findGrupaOrThrow(Long grupaId) {
        Grupa grupa = grupaRepository.findById(grupaId)
                .orElseThrow(() -> new SystemException("Grupa ne postoji! ID = " + grupaId, HttpStatus.NOT_FOUND));
        return grupa;
    }

    public List<StudentInfo> findAllByGroup(Long id) {
        Grupa grupa = findGrupaOrThrow(id);

        return studentRepository.findByGrupa(grupa)
                .stream().map(
                        student -> mapper.map(student, StudentInfo.class)
                ).toList();
    }

    public StudentNaPredmetuDetails findByIdAndPredmet(Long studentId, Long predmetId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + studentId, HttpStatus.NOT_FOUND));
        Predmet predmet = predmetRepository.findById(predmetId)
                .orElseThrow(() -> new SystemException("Predmet ne postoji! ID = " + predmetId, HttpStatus.NOT_FOUND));

        StudentNaPredmetuDetails details = new StudentNaPredmetuDetails();
        details.setStudent(mapper.map(student, StudentInfo.class));
        details.setPredmet(mapper.map(predmet, PredmetInfo.class));
        details.setGrupaNaziv(student.getGrupa().getNaziv());

        // Aktivnosti za ovaj predmet
        List<Aktivnost> aktivnosti = aktivnostRepository.findAllByStudentAndPredavanjePredmetOrderByPredavanjeDatumAsc(student, predmet);
        details.setAktivnosti(aktivnosti.stream().map(StudentMapper.INSTANCE::toAktivnostDetails).toList());

        // Izračunaj ukupno poena aktivnosti
        double ukupnoAktivnost = 0;
        for (Aktivnost a : aktivnosti) {
            switch (a.getTip()) {
                case PRISUSTVO -> ukupnoAktivnost += 1;
                case ZADATAK -> ukupnoAktivnost += 2;
                case SA_ZVEZDICOM -> ukupnoAktivnost += 4;
            }
        }
        details.setUkupnoPoenaAktivnost(ukupnoAktivnost);

        // Domaći za ovaj predmet
        List<UradjenDomaci> uradjeniDomaci = domaciRepository.findUradjeniDomaciByStudentAndPredmet(student, predmet);
        details.setDomaci(uradjeniDomaci.stream().map(StudentMapper.INSTANCE::toDomaciDetails).toList());

        // Izračunaj ukupno poena domaći
        double ukupnoDomaci = 0;
        for (UradjenDomaci ud : uradjeniDomaci) {
            ukupnoDomaci += 4 + (6 * ud.getBodovi()) / 10.0;
        }
        details.setUkupnoPoenaDomaci(Math.round(ukupnoDomaci * 100.0) / 100.0);

        // Polaganja za ovaj predmet, grupisana po tipu testa
        List<Polaganje> polaganja = polaganjeRepository.findAllByStudentAndPredmet(studentId, predmetId);

        Map<Long, List<Polaganje>> poTipuTesta = polaganja.stream()
                .collect(Collectors.groupingBy(p -> p.getTest().getTipTesta().getId()));

        List<StudentTestoviPoTipuInfo> testoviPoTipu = new ArrayList<>();
        for (Map.Entry<Long, List<Polaganje>> entry : poTipuTesta.entrySet()) {
            List<Polaganje> polaganjaTipa = entry.getValue();
            if (polaganjaTipa.isEmpty()) continue;

            StudentTestoviPoTipuInfo tipInfo = new StudentTestoviPoTipuInfo();
            TipTesta tipTesta = polaganjaTipa.get(0).getTest().getTipTesta();
            tipInfo.setTipTesta(mapper.map(tipTesta, TipTestaInfo.class));

            List<StudentPregledTestInfo> polaganjaInfo = polaganjaTipa.stream()
                    .map(StudentMapper.INSTANCE::toTestDetails)
                    .sorted(Comparator.comparing(StudentPregledTestInfo::getDatum))
                    .toList();
            tipInfo.setPolaganja(polaganjaInfo);

            // Pronađi najbolje polaganje
            polaganjaInfo.stream()
                    .filter(p -> p.getOstvareniPoeni() != null)
                    .max(Comparator.comparing(StudentPregledTestInfo::getOstvareniPoeni))
                    .ifPresent(tipInfo::setNajboljePolaganje);

            testoviPoTipu.add(tipInfo);
        }

        // Sortiraj po nazivu tipa testa
        testoviPoTipu.sort(Comparator.comparing(t -> t.getTipTesta().getNaziv()));
        details.setTestoviPoTipu(testoviPoTipu);

        return details;
    }
}
