package tri.novica.gfssystem.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tri.novica.gfssystem.dto.student.CreateStudentCmd;
import tri.novica.gfssystem.dto.student.StudentDetails;
import tri.novica.gfssystem.dto.student.StudentInfo;
import tri.novica.gfssystem.entity.Grupa;
import tri.novica.gfssystem.entity.Student;
import tri.novica.gfssystem.exceptions.SystemException;
import tri.novica.gfssystem.repository.GrupaRepository;
import tri.novica.gfssystem.repository.StudentRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StudentService {
    
    private final StudentRepository studentRepository;
    private final GrupaRepository grupaRepository;
    private final ModelMapper mapper;

    public List<StudentInfo> findAll() {
        return studentRepository.findAll()
                .stream().map(
                        student -> mapper.map(student, StudentInfo.class)
                ).toList();
    }

    public StudentDetails findById(Long id) {
        Student student = studentRepository.findByIdFetchDetails(id)
                .orElseThrow(() -> new SystemException("Student ne postoji! ID = " + id, HttpStatus.NOT_FOUND));

        return mapper.map(student, StudentDetails.class);
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
}
