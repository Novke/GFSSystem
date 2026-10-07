package tri.novica.gfssystem.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.student.CreateStudentCmd;
import tri.novica.gfssystem.dto.student.StudentInfo;
import tri.novica.gfssystem.dto.student.pregled.StudentNaPredmetuDetails;
import tri.novica.gfssystem.dto.student.pregled.StudentPregledDetails;
import tri.novica.gfssystem.service.StudentService;

import java.util.List;

@RestController
@RequestMapping("/studenti")
@RequiredArgsConstructor
public class StudentRest {

    private final StudentService studentService;

    @GetMapping
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public List<StudentInfo> findAll(){
        return studentService.findAll();
    }
    @GetMapping("{id}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public StudentPregledDetails findById(@PathVariable Long id){
        return studentService.findById(id);
    }

    @PostMapping
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public StudentInfo createStudent(@Valid @RequestBody CreateStudentCmd studentCmd){
        return studentService.create(studentCmd);
    }

    @GetMapping("{studentId}/predmet/{predmetId}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public StudentNaPredmetuDetails findByIdAndPredmet(@PathVariable Long studentId, @PathVariable Long predmetId){
        return studentService.findByIdAndPredmet(studentId, predmetId);
    }
}
