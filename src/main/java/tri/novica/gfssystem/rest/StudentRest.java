package tri.novica.gfssystem.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.pregled.StudentPredmetKarticaInfo;
import tri.novica.gfssystem.dto.student.CreateStudentCmd;
import tri.novica.gfssystem.dto.student.StudentFilter;
import tri.novica.gfssystem.dto.student.StudentInfo;
import tri.novica.gfssystem.dto.student.StudentListItem;
import tri.novica.gfssystem.dto.student.UpdateStudentCmd;
import tri.novica.gfssystem.dto.student.pregled.StudentNaPredmetuDetails;
import tri.novica.gfssystem.dto.student.pregled.StudentPregledDetails;
import tri.novica.gfssystem.service.OcenjivanjeService;
import tri.novica.gfssystem.service.StudentService;
import tri.novica.gfssystem.utility.PageableUtil;

import java.util.List;

@RestController
@RequestMapping("/studenti")
@RequiredArgsConstructor
public class StudentRest {

    private final StudentService studentService;
    private final OcenjivanjeService ocenjivanjeService;

    @GetMapping
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public List<StudentInfo> findAll(){
        return studentService.findAll();
    }
    /**
     * Lista studenata za UI: filteri su opcioni, {@code page}/{@code size} (podrazumevano 25, najviše 100) i
     * {@code sort} po {@code prezime}, {@code ime}, {@code indeks}, {@code godina} (podrazumevano prezime pa ime).
     * Literal {@code pretraga} ima prednost nad {@code {id}}.
     */
    @GetMapping("pretraga")
    @ResponseStatus(HttpStatus.OK)
    public PagedModel<StudentListItem> pretraga(@RequestParam(required = false) Long grupaId,
            @RequestParam(required = false) Long starijiOdGrupe, @RequestParam(required = false) String q,
            Pageable pageable) {
        return studentService.pretraga(new StudentFilter(grupaId, starijiOdGrupe, q),
                PageableUtil.proveri(pageable, StudentService.SORT_POLJA, StudentService.PODRAZUMEVANI_SORT));
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

    /** Izmena podataka i premeštanje u drugu grupu ({@code grupaId} u telu). */
    @PutMapping("{id}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public StudentInfo updateStudent(@PathVariable Long id, @Valid @RequestBody UpdateStudentCmd studentCmd){
        return studentService.update(id, studentCmd);
    }

    /** S2: kartice po predmetu (prisutnost, domaći, poeni po tipu testa, ukupno i predlog ocene iz ocenjivanja). */
    @GetMapping("{id}/predmeti")
    @ResponseStatus(HttpStatus.OK)
    public List<StudentPredmetKarticaInfo> kartice(@PathVariable Long id) {
        return ocenjivanjeService.karticeStudenta(id);
    }

    @GetMapping("{studentId}/predmet/{predmetId}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public StudentNaPredmetuDetails findByIdAndPredmet(@PathVariable Long studentId, @PathVariable Long predmetId){
        return studentService.findByIdAndPredmet(studentId, predmetId);
    }
}
