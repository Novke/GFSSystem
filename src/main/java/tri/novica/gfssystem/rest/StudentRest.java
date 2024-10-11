package tri.novica.gfssystem.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.student.CreateStudentCmd;
import tri.novica.gfssystem.dto.student.StudentDetails;
import tri.novica.gfssystem.dto.student.StudentInfo;
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
    public StudentDetails findById(@PathVariable Long id){
        return studentService.findById(id);
    }

    @PostMapping
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public StudentInfo createStudent(@RequestBody CreateStudentCmd studentCmd){
        return studentService.create(studentCmd);
    }

}
