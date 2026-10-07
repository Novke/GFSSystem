package tri.novica.gfssystem.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.IdCmd;
import tri.novica.gfssystem.dto.test.*;
import tri.novica.gfssystem.dto.test.tip.CreateTipTestaCmd;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.service.TestService;
import tri.novica.gfssystem.utility.PageableUtil;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestRest {

    private final TestService testService;

    /**
     * Lista testova za UI: filteri su opcioni, {@code page}/{@code size} (podrazumevano 25, najviše 100) i
     * {@code sort} po {@code datum} ili {@code maxPoena} (podrazumevano {@code datum,desc}).
     * Literal {@code /pretraga} ima prednost nad {@code /{id}}.
     */
    @GetMapping("/pretraga")
    @ResponseStatus(HttpStatus.OK)
    public PagedModel<TestListItem> pretraga(@RequestParam(required = false) Long predmetId,
            @RequestParam(required = false) Long grupaId, @RequestParam(required = false) Integer godina,
            @RequestParam(required = false) Boolean pregledan, @RequestParam(required = false) Long tipTestaId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate od,
            @RequestParam(name = "do", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate doDatuma,
            Pageable pageable) {
        return testService.pretraga(new TestFilter(predmetId, grupaId, godina, pregledan, tipTestaId, od, doDatuma),
                PageableUtil.proveri(pageable, TestService.SORT_POLJA, TestService.PODRAZUMEVANI_SORT));
    }

    @PostMapping("/tip")
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public TipTestaInfo createTipTesta(@RequestBody @Valid CreateTipTestaCmd cmd){
        return testService.createTipTesta(cmd);
    }

    @GetMapping("/{id}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public TestDetails view(@PathVariable Long id){
        return testService.findById(id);
    }

    @PostMapping
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public TestInfo createTest(@RequestBody @Valid CreateTestCmd cmd){
        return testService.createTest(cmd);
    }

    @PutMapping("/{id}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public TestDetails updateTest(@PathVariable(name = "id") Long testId, @RequestBody @Valid UpdateTestCmd cmd){
        return testService.updateTest(testId, cmd);
    }

    @PostMapping("/{id}/polaganje")
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public TestDetails dodajIspitanika(@PathVariable(name = "id") Long testId, @RequestBody IdCmd studentId){
        return testService.dodajIspitanika(testId, studentId.getId());
    }

    @DeleteMapping("/{tId}/polaganje/{sId}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public TestDetails skloniIspitanika(@PathVariable(name = "tId") Long testId, @PathVariable(name = "sId") Long studentId){
        return testService.skloniIspitanika(testId, studentId);
    }

    @PatchMapping("/{id}/polaganje")
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public TestDetails evidentirajIspitanika(@RequestBody @Valid EvidentirajPolaganjeCmd cmd, @PathVariable(name = "id") Long testId){
        return testService.evidentirajIspitanika(cmd, testId);
    }

    @PatchMapping("/{id}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public TestDetails zavrsiEvidentiranje(@PathVariable(name = "id") Long testId){
        return testService.zavrsiEvidentiranje(testId);
    }

    @GetMapping("/grupa/{grupaId}/predmet/{predmetId}")
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public List<TestInfo> vratiTestoveGrupaPredmet(@PathVariable Long grupaId, @PathVariable Long predmetId){
        return testService.vratiTestoveGrupaPredmet(grupaId, predmetId);
    }

}
