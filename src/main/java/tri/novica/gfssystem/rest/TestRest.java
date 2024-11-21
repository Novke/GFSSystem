package tri.novica.gfssystem.rest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tri.novica.gfssystem.dto.IdCmd;
import tri.novica.gfssystem.dto.test.*;
import tri.novica.gfssystem.dto.test.tip.CreateTipTestaCmd;
import tri.novica.gfssystem.dto.test.tip.TipTestaInfo;
import tri.novica.gfssystem.service.TestService;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestRest {

    private final TestService testService;

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

}
